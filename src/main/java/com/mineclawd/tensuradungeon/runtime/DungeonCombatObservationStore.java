package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.EncounterClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class DungeonCombatObservationStore {
    public static final int WINDOW_TICKS = 30;
    private static final int MAX_TARGETS = 1024;
    private static final int MAX_ATTACKERS_PER_TARGET = 16;

    private final int maxTargets;
    private final LinkedHashMap<ObservationKey, TargetData> targets;

    public DungeonCombatObservationStore() {
        this(MAX_TARGETS);
    }

    public DungeonCombatObservationStore(int maxTargets) {
        if (maxTargets <= 0) {
            throw new IllegalArgumentException("maxTargets must be positive: " + maxTargets);
        }
        this.maxTargets = maxTargets;
        this.targets = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ObservationKey, TargetData> eldest) {
                return size() > DungeonCombatObservationStore.this.maxTargets;
            }
        };
    }

    public synchronized void recordHit(
        ObservationKey key,
        EncounterClass encounterClass,
        String entityTypeId,
        String entityDisplayName,
        long gameTime,
        UUID attackerId,
        double amount,
        String abilityId,
        String damageFamily,
        boolean dot
    ) {
        if (key == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }
        String normalizedAbilityId = isBlank(abilityId) ? "UNKNOWN" : abilityId;
        String normalizedFamily = isBlank(damageFamily) ? "OTHER" : damageFamily;
        UUID normalizedAttacker = attackerId == null ? new UUID(0L, 0L) : attackerId;

        TargetData target = targets.get(key);
        if (target == null) {
            target = new TargetData(
                encounterClass,
                entityTypeId,
                entityDisplayName,
                gameTime,
                gameTime
            );
            targets.put(key, target);
        } else {
            target.lastSeenGameTime = gameTime;
        }

        target.totalDamage += amount;
        target.totalHits++;
        if (dot) {
            target.dotDamage += amount;
        }
        target.damageByAbility.merge(normalizedAbilityId, amount, Double::sum);
        target.damageByFamily.merge(normalizedFamily, amount, Double::sum);

        WindowData window = currentWindow(target, normalizedAttacker);
        if (window == null || gameTime >= window.windowEndTick) {
            if (window == null && target.windows.size() >= MAX_ATTACKERS_PER_TARGET) {
                evictOldestWindow(target);
            }
            window = new WindowData(normalizedAttacker, gameTime, gameTime + WINDOW_TICKS);
            target.windows.add(window);
        }
        window.damage += amount;
        window.hitCount++;
        if (dot) {
            window.dotDamage += amount;
        }
        window.abilityIds.add(normalizedAbilityId);
        window.families.add(normalizedFamily);
    }

    public synchronized long recordDeath(ObservationKey key, long gameTime) {
        if (key == null) {
            return -1L;
        }
        TargetData target = targets.get(key);
        if (target == null) {
            return -1L;
        }
        target.deathGameTime = gameTime;
        return gameTime - target.firstSeenGameTime;
    }

    public synchronized int targetCount() {
        return targets.size();
    }

    public synchronized List<TargetSummary> summaries() {
        List<TargetSummary> result = new ArrayList<>(targets.size());
        for (Map.Entry<ObservationKey, TargetData> entry : targets.entrySet()) {
            TargetData target = entry.getValue();
            List<WindowSummary> windows = new ArrayList<>(target.windows.size());
            for (WindowData window : target.windows) {
                windows.add(new WindowSummary(
                    window.attackerId,
                    window.windowStartTick,
                    window.windowEndTick,
                    window.damage,
                    window.hitCount,
                    window.dotDamage,
                    Collections.unmodifiableSet(new LinkedHashSet<>(window.abilityIds)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(window.families))
                ));
            }
            result.add(new TargetSummary(
                entry.getKey(),
                target.encounterClass,
                target.entityTypeId,
                target.entityDisplayName,
                target.firstSeenGameTime,
                target.lastSeenGameTime,
                target.deathGameTime < 0L ? 0L : target.deathGameTime,
                target.deathGameTime < 0L ? 0L : target.deathGameTime - target.firstSeenGameTime,
                target.totalDamage,
                target.totalHits,
                target.dotDamage,
                Collections.unmodifiableMap(new TreeMap<>(target.damageByAbility)),
                Collections.unmodifiableMap(new TreeMap<>(target.damageByFamily)),
                Collections.unmodifiableList(windows)
            ));
        }
        result.sort(Comparator.comparingLong(TargetSummary::firstSeenGameTime));
        return Collections.unmodifiableList(result);
    }

    public synchronized void clear() {
        targets.clear();
    }

    private static void evictOldestWindow(TargetData target) {
        WindowData evict = null;
        long smallestEndTick = Long.MAX_VALUE;
        for (WindowData window : target.windows) {
            if (window.windowEndTick < smallestEndTick) {
                smallestEndTick = window.windowEndTick;
                evict = window;
            }
        }
        if (evict != null) {
            target.windows.remove(evict);
        }
    }

    private static WindowData currentWindow(TargetData target, UUID attackerId) {
        for (int index = target.windows.size() - 1; index >= 0; index--) {
            WindowData window = target.windows.get(index);
            if (attackerId.equals(window.attackerId)) {
                return window;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ObservationKey(UUID targetUuid, String sessionId, BlockPos controllerPos) {
        public ObservationKey {
            if (targetUuid == null) {
                throw new IllegalArgumentException("targetUuid must not be null");
            }
            sessionId = sessionId == null ? "" : sessionId;
            controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos;
        }
    }

    public record TargetSummary(
        ObservationKey key,
        EncounterClass encounterClass,
        String entityTypeId,
        String entityDisplayName,
        long firstSeenGameTime,
        long lastSeenGameTime,
        long deathGameTime,
        long ttkTicks,
        double totalDamage,
        int totalHits,
        double dotDamage,
        Map<String, Double> damageByAbility,
        Map<String, Double> damageByFamily,
        List<WindowSummary> windows
    ) {
    }

    public record WindowSummary(
        UUID attackerId,
        long windowStartTick,
        long windowEndTick,
        double damage,
        int hitCount,
        double dotDamage,
        Set<String> abilityIds,
        Set<String> families
    ) {
    }

    private static final class TargetData {
        private final EncounterClass encounterClass;
        private final String entityTypeId;
        private final String entityDisplayName;
        private final long firstSeenGameTime;
        private long lastSeenGameTime;
        private long deathGameTime = -1L;
        private double totalDamage;
        private int totalHits;
        private double dotDamage;
        private final Map<String, Double> damageByAbility = new TreeMap<>();
        private final Map<String, Double> damageByFamily = new TreeMap<>();
        private final List<WindowData> windows = new ArrayList<>();

        private TargetData(
            EncounterClass encounterClass,
            String entityTypeId,
            String entityDisplayName,
            long firstSeenGameTime,
            long lastSeenGameTime
        ) {
            this.encounterClass = encounterClass;
            this.entityTypeId = entityTypeId;
            this.entityDisplayName = entityDisplayName;
            this.firstSeenGameTime = firstSeenGameTime;
            this.lastSeenGameTime = lastSeenGameTime;
        }
    }

    private static final class WindowData {
        private final UUID attackerId;
        private final long windowStartTick;
        private long windowEndTick;
        private double damage;
        private int hitCount;
        private double dotDamage;
        private final Set<String> abilityIds = new LinkedHashSet<>();
        private final Set<String> families = new LinkedHashSet<>();

        private WindowData(UUID attackerId, long windowStartTick, long windowEndTick) {
            this.attackerId = attackerId;
            this.windowStartTick = windowStartTick;
            this.windowEndTick = windowEndTick;
        }
    }
}
