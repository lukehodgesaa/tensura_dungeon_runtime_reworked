package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.dungeonsunbound.npc.DungeonNpcDialogues;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateSkillCompat;
import com.mineclawd.tensuradungeon.network.SubordinateDebriefPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SubordinateDebriefController {
    private static final ResourceLocation DUNGEON_DIMENSION =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "dungeon");

    static final double HURT_THRESHOLD = 0.35D;
    private static final double WALK_TRIGGER_DISTANCE = 3.25D;
    private static final double WALK_TRIGGER_DISTANCE_SQUARED =
        WALK_TRIGGER_DISTANCE * WALK_TRIGGER_DISTANCE;
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final long DELIVERY_COOLDOWN_TICKS = 60L;

    private static final Map<DungeonInstanceAllocationData.InstanceKey, Map<UUID, SubordinateDebriefPayload.Issue>> PENDING =
        new HashMap<>();
    private static final Map<DungeonInstanceAllocationData.InstanceKey, Map<UUID, Set<SubordinateDebriefPayload.Issue>>> REPORTED =
        new HashMap<>();
    private static final Map<DungeonInstanceAllocationData.InstanceKey, Long> LAST_DELIVERY_GAME_TIME =
        new HashMap<>();

    private SubordinateDebriefController() {
    }

    public static void onRoomCompleted(ServerLevel level, RoomControllerBlockEntity controller) {
        DungeonInstanceAllocationData.InstanceKey key = controller.getDungeonInstanceKey();
        if (key == null) {
            return;
        }
        DungeonSubordinateTransportData.Binding binding =
            DungeonSubordinateTransportData.get(level).binding(key).orElse(null);
        if (binding == null) {
            return;
        }
        Map<UUID, Set<SubordinateDebriefPayload.Issue>> reported =
            REPORTED.computeIfAbsent(key, ignored -> new HashMap<>());
        Map<UUID, SubordinateDebriefPayload.Issue> pending =
            PENDING.computeIfAbsent(key, ignored -> new HashMap<>());
        for (DungeonSubordinateTransportData.Entry entry : binding.entries()) {
            if (!entry.state().isInstanceBound()) {
                continue;
            }
            LivingEntity subordinate = level.getEntity(entry.entityId()) instanceof LivingEntity entity
                ? entity
                : null;
            if (subordinate == null || !subordinate.isAlive() || subordinate.isRemoved()) {
                continue;
            }
            SubordinateDebriefPayload.Issue issue = issueFor(subordinate);
            if (reported.getOrDefault(entry.entityId(), Set.of()).contains(issue)) {
                continue;
            }
            pending.put(entry.entityId(), issue);
        }
    }

    public static void onCombatActivated(DungeonInstanceAllocationData.InstanceKey key) {
        if (key != null) {
            PENDING.remove(key);
            LAST_DELIVERY_GAME_TIME.remove(key);
        }
    }

    public static void tick(ServerLevel level, RoomControllerBlockEntity controller) {
        DungeonInstanceAllocationData.InstanceKey key = controller.getDungeonInstanceKey();
        if (key == null || PENDING.isEmpty() || !PENDING.containsKey(key)
            || level.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        DungeonSubordinateTransportData.Binding binding =
            DungeonSubordinateTransportData.get(level).binding(key).orElse(null);
        if (binding == null) {
            clear(key);
            return;
        }
        ServerPlayer host = level.getServer().getPlayerList().getPlayer(binding.hostId());
        if (host == null || !host.isAlive() || host.isSpectator()
            || !DUNGEON_DIMENSION.equals(host.level().dimension().location())) {
            clear(key);
            return;
        }

        Map<UUID, SubordinateDebriefPayload.Issue> pending = PENDING.get(key);
        Map<UUID, Set<SubordinateDebriefPayload.Issue>> reported =
            REPORTED.computeIfAbsent(key, ignored -> new HashMap<>());
        long lastDelivery = LAST_DELIVERY_GAME_TIME.getOrDefault(key, Long.MIN_VALUE);
        if (level.getGameTime() - lastDelivery < DELIVERY_COOLDOWN_TICKS) {
            return;
        }

        Iterator<Map.Entry<UUID, SubordinateDebriefPayload.Issue>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SubordinateDebriefPayload.Issue> entry = iterator.next();
            UUID entityId = entry.getKey();
            LivingEntity subordinate = level.getEntity(entityId) instanceof LivingEntity entity
                ? entity
                : null;
            if (subordinate == null || !subordinate.isAlive() || subordinate.isRemoved()) {
                continue;
            }
            if (subordinate.distanceToSqr(host) > WALK_TRIGGER_DISTANCE_SQUARED) {
                if (subordinate instanceof Mob mob) {
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(host, 1.0D);
                    mob.getLookControl().setLookAt(host, 30.0F, 30.0F);
                }
                continue;
            }
            if (!DungeonNpcDialogues.debrief(host, subordinate, entry.getValue().name())) {
                return;
            }
            PacketDistributor.sendToPlayer(host, new SubordinateDebriefPayload(
                subordinate.getId(),
                subordinate.getName().getString(),
                entry.getValue()
            ));
            reported.computeIfAbsent(entityId, ignored -> new HashSet<>()).add(entry.getValue());
            iterator.remove();
            LAST_DELIVERY_GAME_TIME.put(key, level.getGameTime());
            return;
        }
    }

    public static void clear(DungeonInstanceAllocationData.InstanceKey key) {
        if (key == null) {
            return;
        }
        PENDING.remove(key);
        REPORTED.remove(key);
        LAST_DELIVERY_GAME_TIME.remove(key);
    }

    public static SubordinateDebriefPayload.Issue issueFor(LivingEntity entity) {
        if (entity == null) {
            return SubordinateDebriefPayload.Issue.GENERIC;
        }
        TensuraSubordinateSkillCompat.AbilityProjection abilities =
            TensuraSubordinateSkillCompat.currentAbilities(entity);
        if (abilities.available()) {
            for (TensuraSubordinateSkillCompat.AbilitySlot slot : abilities.slots()) {
                if (slot.empty()) {
                    continue;
                }
                TensuraSubordinateSkillCompat.SkillDetails details = slot.details();
                if (details.magiculeCostAvailable() && details.resourceAvailable()) {
                    return selectIssue(
                        true,
                        details.resourceAvailable(),
                        details.enoughMagicule(),
                        entity.getHealth(),
                        entity.getMaxHealth()
                    );
                }
            }
        }
        return selectIssue(
            false,
            false,
            true,
            entity.getHealth(),
            entity.getMaxHealth()
        );
    }

    static SubordinateDebriefPayload.Issue selectIssue(
        boolean activeSpellHasKnownCost,
        boolean resourceAvailable,
        boolean enoughMagicule,
        double health,
        double maxHealth
    ) {
        if (activeSpellHasKnownCost && resourceAvailable && !enoughMagicule) {
            return SubordinateDebriefPayload.Issue.MAGICULE;
        }
        if (maxHealth > 0.0D && health <= maxHealth * HURT_THRESHOLD) {
            return SubordinateDebriefPayload.Issue.HURT;
        }
        return SubordinateDebriefPayload.Issue.GENERIC;
    }
}
