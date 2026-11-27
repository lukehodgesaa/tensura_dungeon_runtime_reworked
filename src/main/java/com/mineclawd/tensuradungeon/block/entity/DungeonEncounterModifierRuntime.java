package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.runtime.DungeonModifierCombatRules;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCatalog;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

final class DungeonEncounterModifierRuntime {
    private static final ResourceLocation GLASS_CANNON_HEALTH_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "glass_cannon_health");
    private static final ResourceLocation RISING_HEAT_DAMAGE_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "rising_heat_damage");
    private static final ResourceLocation RISING_HEAT_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "rising_heat_speed");
    private static final ResourceLocation RALLYING_CRY_HEALTH_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "rallying_cry_health");
    private static final ResourceLocation DEATH_RUN_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "death_run_speed");
    private static final ResourceLocation PURSUER_MARK_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "pursuer_mark_speed");
    private static final ResourceLocation PURSUER_MARK_HEALTH_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "pursuer_mark_health");
    private static final String RALLY_GLOW_KEY = "TensuraDungeonRallyingCryGlow";
    private static final String DREAD_GLOW_KEY = "TensuraDungeonDreadAuraGlow";
    private static final String PURSUER_GLOW_KEY = "TensuraDungeonPursuerMarkGlow";
    private static final String AMBUSH_MARKER_KEY = "TensuraDungeonAmbushPuppeteer";
    private static final String RALLY_ROLE_LABEL = "Rallying Cry Bearer";
    private static final String DREAD_ROLE_LABEL = "Dread Aura Carrier";
    private static final int RALLY_PULSE_TICKS = 400;
    private static final int RALLY_EFFECT_TICKS = 80;
    private static final int DREAD_REFRESH_TICKS = 40;
    private static final int DREAD_EFFECT_TICKS = 60;
    private static final int MAX_LEASES = 128;
    private static final int MAX_HOT_POTATO_STATES = 16;

    private UUID rallyBearerUuid;
    private String rallyBearerRole = "";
    private int rallyPulseTicks;
    private UUID dreadCarrierUuid;
    private String dreadCarrierRole = "";
    private int dreadPulseTicks;
    private int risingHeatElapsedTicks;
    private int risingHeatStage;
    private UUID pursuerMobUuid;
    private UUID pursuerTargetUuid;
    private final Map<UUID, HeldItemState> hotPotatoStates = new HashMap<>();
    private final Map<LeaseKey, EffectLease> effectLeases = new HashMap<>();

    void reset() {
        rallyBearerUuid = null;
        rallyBearerRole = "";
        rallyPulseTicks = 0;
        dreadCarrierUuid = null;
        dreadCarrierRole = "";
        dreadPulseTicks = 0;
        risingHeatElapsedTicks = 0;
        risingHeatStage = 0;
        pursuerMobUuid = null;
        pursuerTargetUuid = null;
        hotPotatoStates.clear();
        effectLeases.clear();
    }

    void applySpawnModifiers(
        ServerLevel level,
        Mob mob,
        Set<String> activeModifierIds,
        boolean summoned,
        boolean boss
    ) {
        if (mob == null || boss || summoned) return;
        if (isRollable(activeModifierIds, "death-run")) {
            addPermanentMultiplier(
                mob.getAttribute(Attributes.MOVEMENT_SPEED),
                DEATH_RUN_SPEED_ID,
                DungeonModifierCombatRules.multipliedBaseAmount(
                    DungeonModifierCombatRules.DEATH_RUN_SPEED_MULTIPLIER
                )
            );
        }
        if (isRollable(activeModifierIds, "ambush-puppeteer")
            && DungeonModifierCombatRules.isAmbushMarked(mob.getUUID())) {
            mob.getPersistentData().putBoolean(AMBUSH_MARKER_KEY, true);
            applyEffectLease(mob, EffectKind.INVISIBILITY, 40, 0);
        }
        if (activeModifierIds.contains("glass-cannon")) {
            AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && maxHealth.getModifier(GLASS_CANNON_HEALTH_ID) == null) {
                maxHealth.addPermanentModifier(new AttributeModifier(
                    GLASS_CANNON_HEALTH_ID,
                    DungeonModifierCombatRules.GLASS_CANNON_HEALTH_MULTIPLIER - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
                mob.setHealth(mob.getMaxHealth());
            }
        }
        if (activeModifierIds.contains("rising-heat")) {
            applyRisingHeatStage(mob, risingHeatStage);
        }
    }

    boolean update(
        ServerLevel level,
        Set<String> activeModifierIds,
        Collection<UUID> trackedMobUuids,
        Set<UUID> summonedMobUuids,
        UUID eliteMobUuid,
        Set<UUID> occupiedCarrierUuids,
        List<ServerPlayer> activePlayers,
        Predicate<Mob> bossPredicate
    ) {
        boolean changed = updateEffectLeases(level);
        changed |= updateRisingHeat(
            level, activeModifierIds, trackedMobUuids, summonedMobUuids, bossPredicate
        );
        changed |= updateRallyingCry(
            level, activeModifierIds, trackedMobUuids, summonedMobUuids, eliteMobUuid,
            occupiedCarrierUuids, bossPredicate
        );
        changed |= updateDreadAura(
            level, activeModifierIds, trackedMobUuids, summonedMobUuids, eliteMobUuid,
            occupiedCarrierUuids, activePlayers, bossPredicate
        );
        changed |= updatePursuerMark(
            level, activeModifierIds, trackedMobUuids, summonedMobUuids, eliteMobUuid,
            occupiedCarrierUuids, activePlayers, bossPredicate
        );
        changed |= updateAmbushPuppeteer(
            level, activeModifierIds, trackedMobUuids, summonedMobUuids, eliteMobUuid,
            bossPredicate
        );
        changed |= updateHotPotato(activeModifierIds, activePlayers);
        return changed;
    }

    void clear(ServerLevel level, Collection<UUID> trackedMobUuids) {
        Set<UUID> ids = new HashSet<>(trackedMobUuids);
        if (rallyBearerUuid != null) ids.add(rallyBearerUuid);
        if (dreadCarrierUuid != null) ids.add(dreadCarrierUuid);
        if (pursuerMobUuid != null) ids.add(pursuerMobUuid);
        for (UUID uuid : ids) {
            if (level.getEntity(uuid) instanceof Mob mob) {
                removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), RALLYING_CRY_HEALTH_ID);
                removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), GLASS_CANNON_HEALTH_ID);
                removeModifier(mob.getAttribute(Attributes.ATTACK_DAMAGE), RISING_HEAT_DAMAGE_ID);
                removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), RISING_HEAT_SPEED_ID);
                removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), DEATH_RUN_SPEED_ID);
                removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), PURSUER_MARK_SPEED_ID);
                removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), PURSUER_MARK_HEALTH_ID);
                releaseGlow(mob, RALLY_GLOW_KEY);
                releaseGlow(mob, DREAD_GLOW_KEY);
                releaseGlow(mob, PURSUER_GLOW_KEY);
                mob.getPersistentData().remove(AMBUSH_MARKER_KEY);
            }
        }
        restoreAllEffectLeases(level);
        reset();
    }

    CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        if (rallyBearerUuid != null) {
            tag.putUUID("RallyBearer", rallyBearerUuid);
            tag.putString("RallyBearerRole", RALLY_ROLE_LABEL);
        }
        if (rallyPulseTicks > 0) tag.putInt("RallyPulseTicks", rallyPulseTicks);
        if (dreadCarrierUuid != null) {
            tag.putUUID("DreadCarrier", dreadCarrierUuid);
            tag.putString("DreadCarrierRole", DREAD_ROLE_LABEL);
        }
        if (dreadPulseTicks > 0) tag.putInt("DreadPulseTicks", dreadPulseTicks);
        if (risingHeatElapsedTicks > 0) tag.putInt("RisingHeatElapsedTicks", risingHeatElapsedTicks);
        if (risingHeatStage > 0) tag.putInt("RisingHeatStage", risingHeatStage);
        if (pursuerMobUuid != null) tag.putUUID("PursuerMarkMob", pursuerMobUuid);
        if (pursuerTargetUuid != null) tag.putUUID("PursuerMarkTarget", pursuerTargetUuid);
        if (!hotPotatoStates.isEmpty()) {
            ListTag heldItems = new ListTag();
            hotPotatoStates.values().stream().limit(MAX_HOT_POTATO_STATES).forEach(state -> {
                CompoundTag stateTag = new CompoundTag();
                stateTag.putUUID("Player", state.playerUuid());
                stateTag.putString("Item", state.itemKey());
                stateTag.putInt("Ticks", state.heldTicks());
                heldItems.add(stateTag);
            });
            tag.put("HotPotatoStates", heldItems);
        }
        if (!effectLeases.isEmpty()) {
            ListTag leases = new ListTag();
            effectLeases.values().stream().limit(MAX_LEASES).forEach(lease -> {
                CompoundTag leaseTag = new CompoundTag();
                leaseTag.putUUID("Entity", lease.entityUuid());
                leaseTag.putString("Effect", lease.kind().name());
                leaseTag.putInt("Remaining", Math.max(0, lease.remainingTicks()));
                leaseTag.putInt("DesiredAmplifier", lease.desiredAmplifier());
                if (lease.previousEffect() != null && lease.previousEffect().save() instanceof CompoundTag previous) {
                    leaseTag.put("Previous", previous);
                }
                leases.add(leaseTag);
            });
            tag.put("EffectLeases", leases);
        }
        return tag;
    }

    void loadState(CompoundTag tag) {
        reset();
        if (tag == null || !isValidStateTag(tag)) return;
        rallyBearerUuid = tag.hasUUID("RallyBearer") ? tag.getUUID("RallyBearer") : null;
        rallyBearerRole = rallyBearerUuid == null ? "" : RALLY_ROLE_LABEL;
        rallyPulseTicks = tag.contains("RallyPulseTicks", Tag.TAG_INT)
            ? tag.getInt("RallyPulseTicks") : 0;
        dreadCarrierUuid = tag.hasUUID("DreadCarrier") ? tag.getUUID("DreadCarrier") : null;
        dreadCarrierRole = dreadCarrierUuid == null ? "" : DREAD_ROLE_LABEL;
        dreadPulseTicks = tag.contains("DreadPulseTicks", Tag.TAG_INT)
            ? tag.getInt("DreadPulseTicks") : 0;
        risingHeatElapsedTicks = tag.contains("RisingHeatElapsedTicks", Tag.TAG_INT)
            ? tag.getInt("RisingHeatElapsedTicks") : 0;
        risingHeatStage = tag.contains("RisingHeatStage", Tag.TAG_INT)
            ? tag.getInt("RisingHeatStage") : 0;
        pursuerMobUuid = tag.hasUUID("PursuerMarkMob") ? tag.getUUID("PursuerMarkMob") : null;
        pursuerTargetUuid = tag.hasUUID("PursuerMarkTarget") ? tag.getUUID("PursuerMarkTarget") : null;
        ListTag heldItems = tag.getList("HotPotatoStates", Tag.TAG_COMPOUND);
        for (int index = 0; index < heldItems.size(); index++) {
            CompoundTag stateTag = heldItems.getCompound(index);
            UUID playerUuid = stateTag.getUUID("Player");
            hotPotatoStates.put(playerUuid, new HeldItemState(
                playerUuid, stateTag.getString("Item"), stateTag.getInt("Ticks")
            ));
        }
        ListTag leases = tag.getList("EffectLeases", Tag.TAG_COMPOUND);
        for (int index = 0; index < leases.size(); index++) {
            CompoundTag leaseTag = leases.getCompound(index);
            if (!leaseTag.hasUUID("Entity")) return;
            EffectKind kind;
            try {
                kind = EffectKind.valueOf(leaseTag.getString("Effect"));
            } catch (IllegalArgumentException ex) {
                return;
            }
            MobEffectInstance previous = null;
            if (leaseTag.contains("Previous", Tag.TAG_COMPOUND)) {
                previous = MobEffectInstance.load(leaseTag.getCompound("Previous"));
            }
            int remaining = leaseTag.getInt("Remaining");
            int desiredAmplifier = leaseTag.getInt("DesiredAmplifier");
            LeaseKey key = new LeaseKey(leaseTag.getUUID("Entity"), kind);
            effectLeases.put(key, new EffectLease(
                key.entityUuid(), kind, previous, remaining, desiredAmplifier
            ));
        }
    }

    private boolean isValidStateTag(CompoundTag tag) {
        if (!validIntField(tag, "RallyPulseTicks")
            || !validIntField(tag, "DreadPulseTicks")
            || !validIntField(tag, "RisingHeatElapsedTicks")
            || !validIntField(tag, "RisingHeatStage")) {
            return false;
        }
        if (tag.contains("RallyBearer") && !tag.hasUUID("RallyBearer")) return false;
        if (tag.contains("DreadCarrier") && !tag.hasUUID("DreadCarrier")) return false;
        if (tag.contains("PursuerMarkMob") && !tag.hasUUID("PursuerMarkMob")) return false;
        if (tag.contains("PursuerMarkTarget") && !tag.hasUUID("PursuerMarkTarget")) return false;
        if (tag.contains("PursuerMarkTarget") && !tag.hasUUID("PursuerMarkMob")) return false;
        if (!validRoleField(tag, "RallyBearerRole", "RallyBearer", RALLY_ROLE_LABEL)
            || !validRoleField(tag, "DreadCarrierRole", "DreadCarrier", DREAD_ROLE_LABEL)) {
            return false;
        }

        int rallyTicks = tag.contains("RallyPulseTicks", Tag.TAG_INT)
            ? tag.getInt("RallyPulseTicks") : 0;
        int dreadTicks = tag.contains("DreadPulseTicks", Tag.TAG_INT)
            ? tag.getInt("DreadPulseTicks") : 0;
        int elapsed = tag.contains("RisingHeatElapsedTicks", Tag.TAG_INT)
            ? tag.getInt("RisingHeatElapsedTicks") : 0;
        int stage = tag.contains("RisingHeatStage", Tag.TAG_INT)
            ? tag.getInt("RisingHeatStage") : 0;
        if (rallyTicks < 1 || rallyTicks >= RALLY_PULSE_TICKS) {
            if (tag.contains("RallyPulseTicks")) return false;
        }
        if (dreadTicks < 1 || dreadTicks >= DREAD_REFRESH_TICKS) {
            if (tag.contains("DreadPulseTicks")) return false;
        }
        int maxElapsed = DungeonModifierCombatRules.RISING_HEAT_STAGE_INTERVAL_TICKS * 3;
        if (elapsed < 0 || elapsed > maxElapsed
            || stage < 0 || stage > DungeonModifierCombatRules.RISING_HEAT_MAX_STAGE
            || DungeonModifierCombatRules.risingHeatStage(elapsed).stage() != stage) {
            return false;
        }
        if (tag.contains("EffectLeases") && !tag.contains("EffectLeases", Tag.TAG_LIST)) return false;
        if (tag.contains("HotPotatoStates") && !tag.contains("HotPotatoStates", Tag.TAG_LIST)) return false;
        if (tag.get("EffectLeases") instanceof ListTag rawLeases
            && rawLeases.size() > 0
            && rawLeases.getElementType() != Tag.TAG_COMPOUND) {
            return false;
        }
        if (tag.get("HotPotatoStates") instanceof ListTag rawHeldItems
            && rawHeldItems.size() > 0
            && rawHeldItems.getElementType() != Tag.TAG_COMPOUND) {
            return false;
        }
        ListTag heldItems = tag.getList("HotPotatoStates", Tag.TAG_COMPOUND);
        if (heldItems.size() > MAX_HOT_POTATO_STATES) return false;
        Set<UUID> heldPlayers = new HashSet<>();
        for (int index = 0; index < heldItems.size(); index++) {
            CompoundTag state = heldItems.getCompound(index);
            String itemKey = state.getString("Item");
            if (!state.hasUUID("Player")
                || itemKey.isBlank()
                || itemKey.length() > 96
                || !state.contains("Item", Tag.TAG_STRING)
                || !state.contains("Ticks", Tag.TAG_INT)
                || state.getInt("Ticks") < 1
                || state.getInt("Ticks") > DungeonModifierCombatRules.HOT_POTATO_HOLD_TICKS
                || !heldPlayers.add(state.getUUID("Player"))) {
                return false;
            }
        }
        ListTag leases = tag.getList("EffectLeases", Tag.TAG_COMPOUND);
        if (leases.size() > MAX_LEASES) return false;
        Set<LeaseKey> seen = new HashSet<>();
        for (int index = 0; index < leases.size(); index++) {
            CompoundTag lease = leases.getCompound(index);
            if (!lease.hasUUID("Entity")
                || !lease.contains("Effect", Tag.TAG_STRING)
                || !lease.contains("Remaining", Tag.TAG_INT)
                || !lease.contains("DesiredAmplifier", Tag.TAG_INT)) {
                return false;
            }
            EffectKind kind;
            try {
                kind = EffectKind.valueOf(lease.getString("Effect"));
            } catch (IllegalArgumentException ex) {
                return false;
            }
            if (lease.getInt("Remaining") < 1
                || lease.getInt("Remaining") > Math.max(RALLY_EFFECT_TICKS, DREAD_EFFECT_TICKS)
                || lease.getInt("DesiredAmplifier") < 0
                || lease.getInt("DesiredAmplifier") > 3
                || !seen.add(new LeaseKey(lease.getUUID("Entity"), kind))) {
                return false;
            }
            if (lease.contains("Previous") && !lease.contains("Previous", Tag.TAG_COMPOUND)) return false;
        }
        return true;
    }

    private static boolean validIntField(CompoundTag tag, String key) {
        return !tag.contains(key) || tag.contains(key, Tag.TAG_INT);
    }

    private static boolean validRoleField(
        CompoundTag tag, String roleKey, String assignmentKey, String expectedRole
    ) {
        return !tag.contains(roleKey)
            || (tag.hasUUID(assignmentKey)
                && tag.contains(roleKey, Tag.TAG_STRING)
                && expectedRole.equals(tag.getString(roleKey)));
    }

    private boolean updateRisingHeat(
        ServerLevel level,
        Set<String> activeModifierIds,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        Predicate<Mob> bossPredicate
    ) {
        if (!activeModifierIds.contains("rising-heat")) {
            if (risingHeatElapsedTicks == 0 && risingHeatStage == 0) return false;
            removeRisingHeatModifiers(level, tracked);
            risingHeatElapsedTicks = 0;
            risingHeatStage = 0;
            return true;
        }
        int previousStage = risingHeatStage;
        int previousElapsedTicks = risingHeatElapsedTicks;
        risingHeatElapsedTicks = Math.min(
            DungeonModifierCombatRules.RISING_HEAT_STAGE_INTERVAL_TICKS * 3,
            risingHeatElapsedTicks + 1
        );
        risingHeatStage = DungeonModifierCombatRules.risingHeatStage(risingHeatElapsedTicks).stage();
        if (previousStage != risingHeatStage) {
            forEachEligibleMob(level, tracked, summoned, bossPredicate, mob -> {
                applyRisingHeatStage(mob, risingHeatStage);
            });
            return true;
        }
        return previousElapsedTicks != risingHeatElapsedTicks;
    }

    private boolean updateRallyingCry(
        ServerLevel level,
        Set<String> activeModifierIds,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        UUID elite,
        Set<UUID> occupied,
        Predicate<Mob> bossPredicate
    ) {
        if (!activeModifierIds.contains("rallying-cry")) {
            return clearRallyingCry(level);
        }
        Set<UUID> excluded = new HashSet<>(occupied);
        if (dreadCarrierUuid != null) excluded.add(dreadCarrierUuid);
        Mob bearer = entityAsMob(level, rallyBearerUuid);
        if (!isEligibleCarrier(bearer, rallyBearerUuid, tracked, summoned, elite, excluded, bossPredicate)) {
            releaseRallyBearer(level);
            rallyBearerUuid = sortedEligibleMobIds(level, tracked, summoned, elite, excluded, bossPredicate)
                .stream().findFirst().orElse(null);
            rallyBearerRole = rallyBearerUuid == null ? "" : RALLY_ROLE_LABEL;
            bearer = entityAsMob(level, rallyBearerUuid);
        }
        if (bearer == null) return false;
        boolean changed = claimGlow(bearer, RALLY_GLOW_KEY);
        AttributeInstance maxHealth = bearer.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(RALLYING_CRY_HEALTH_ID) == null) {
            maxHealth.addPermanentModifier(new AttributeModifier(
                RALLYING_CRY_HEALTH_ID, 0.50D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
            bearer.setHealth(bearer.getMaxHealth());
            changed = true;
        }
        rallyPulseTicks++;
        changed = true;
        if (rallyPulseTicks < RALLY_PULSE_TICKS) return changed;
        rallyPulseTicks = 0;
        forEachTrackedMob(level, tracked, mob -> {
            if (!bossPredicate.test(mob)) {
                applyEffectLease(mob, EffectKind.REGENERATION, RALLY_EFFECT_TICKS, 3);
            }
        });
        return true;
    }

    private boolean updateDreadAura(
        ServerLevel level,
        Set<String> activeModifierIds,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        UUID elite,
        Set<UUID> occupied,
        List<ServerPlayer> activePlayers,
        Predicate<Mob> bossPredicate
    ) {
        if (!activeModifierIds.contains("dread-aura")) {
            return clearDreadAura(level);
        }
        Set<UUID> excluded = new HashSet<>(occupied);
        if (rallyBearerUuid != null) excluded.add(rallyBearerUuid);
        Mob carrier = entityAsMob(level, dreadCarrierUuid);
        if (!isEligibleCarrier(carrier, dreadCarrierUuid, tracked, summoned, elite, excluded, bossPredicate)) {
            releaseDreadCarrier(level);
            dreadCarrierUuid = sortedEligibleMobIds(level, tracked, summoned, elite, excluded, bossPredicate)
                .stream().findFirst().orElse(null);
            dreadCarrierRole = dreadCarrierUuid == null ? "" : DREAD_ROLE_LABEL;
            carrier = entityAsMob(level, dreadCarrierUuid);
        }
        if (carrier == null) return false;
        boolean changed = claimGlow(carrier, DREAD_GLOW_KEY);
        dreadPulseTicks++;
        changed = true;
        if (dreadPulseTicks < DREAD_REFRESH_TICKS) return changed;
        dreadPulseTicks = 0;
        for (ServerPlayer player : activePlayers == null ? List.<ServerPlayer>of() : activePlayers) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(carrier) > 16.0D) continue;
            applyEffectLease(player, EffectKind.WEAKNESS, DREAD_EFFECT_TICKS, 1);
        }
        return true;
    }

    private boolean updatePursuerMark(
        ServerLevel level,
        Set<String> activeModifierIds,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        UUID elite,
        Set<UUID> occupied,
        List<ServerPlayer> activePlayers,
        Predicate<Mob> bossPredicate
    ) {
        if (!isRollable(activeModifierIds, "pursuer-mark")) {
            return clearPursuerMark(level);
        }
        List<ServerPlayer> eligiblePlayers = (activePlayers == null ? List.<ServerPlayer>of() : activePlayers)
            .stream()
            .filter(player -> player != null && player.isAlive() && !player.isSpectator())
            .sorted(Comparator.comparing(player -> player.getUUID().toString()))
            .toList();
        if (eligiblePlayers.isEmpty()) return clearPursuerMark(level);

        Set<UUID> excluded = new HashSet<>(occupied);
        Mob pursuer = entityAsMob(level, pursuerMobUuid);
        if (!isEligibleCarrier(pursuer, pursuerMobUuid, tracked, summoned, elite, excluded, bossPredicate)) {
            releasePursuerMark(level);
            pursuerMobUuid = sortedEligibleMobIds(
                level, tracked, summoned, elite, excluded, bossPredicate
            ).stream().findFirst().orElse(null);
            pursuer = entityAsMob(level, pursuerMobUuid);
        }
        if (pursuer == null) return false;

        ServerPlayer target = eligiblePlayers.stream()
            .filter(player -> player.getUUID().equals(pursuerTargetUuid))
            .findFirst()
            .orElse(eligiblePlayers.getFirst());
        boolean changed = !target.getUUID().equals(pursuerTargetUuid);
        pursuerTargetUuid = target.getUUID();
        AttributeInstance speed = pursuer.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(PURSUER_MARK_SPEED_ID) == null) {
            addPermanentMultiplier(
                speed,
                PURSUER_MARK_SPEED_ID,
                DungeonModifierCombatRules.multipliedBaseAmount(
                    DungeonModifierCombatRules.PURSUER_MARK_SPEED_MULTIPLIER
                )
            );
            changed = true;
        }
        AttributeInstance maxHealth = pursuer.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(PURSUER_MARK_HEALTH_ID) == null) {
            maxHealth.addPermanentModifier(new AttributeModifier(
                PURSUER_MARK_HEALTH_ID,
                DungeonModifierCombatRules.multipliedBaseAmount(
                    DungeonModifierCombatRules.PURSUER_MARK_HEALTH_MULTIPLIER
                ),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
            pursuer.setHealth(pursuer.getMaxHealth());
            changed = true;
        }
        changed |= claimGlow(pursuer, PURSUER_GLOW_KEY);
        if (pursuer.getTarget() != target) {
            pursuer.setTarget(target);
            changed = true;
        }
        return changed;
    }

    private boolean updateAmbushPuppeteer(
        ServerLevel level,
        Set<String> activeModifierIds,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        UUID elite,
        Predicate<Mob> bossPredicate
    ) {
        boolean active = isRollable(activeModifierIds, "ambush-puppeteer");
        boolean changed = false;
        for (UUID uuid : List.copyOf(tracked)) {
            if (!(level.getEntity(uuid) instanceof Mob mob)) continue;
            boolean eligible = mob.isAlive()
                && !mob.isRemoved()
                && !summoned.contains(uuid)
                && !uuid.equals(elite)
                && !bossPredicate.test(mob);
            boolean marked = active && eligible && DungeonModifierCombatRules.isAmbushMarked(uuid);
            if (marked) {
                if (!mob.getPersistentData().getBoolean(AMBUSH_MARKER_KEY)) {
                    mob.getPersistentData().putBoolean(AMBUSH_MARKER_KEY, true);
                    changed = true;
                }
                applyEffectLease(mob, EffectKind.INVISIBILITY, 40, 0);
            } else if (mob.getPersistentData().contains(AMBUSH_MARKER_KEY)) {
                mob.getPersistentData().remove(AMBUSH_MARKER_KEY);
                releaseEffectLease(level, mob, EffectKind.INVISIBILITY);
                changed = true;
            }
        }
        if (!active) restoreLeases(level, EffectKind.INVISIBILITY);
        return changed;
    }

    private boolean updateHotPotato(
        Set<String> activeModifierIds,
        List<ServerPlayer> activePlayers
    ) {
        if (!isRollable(activeModifierIds, "hot-potato")) {
            if (hotPotatoStates.isEmpty()) return false;
            hotPotatoStates.clear();
            return true;
        }
        boolean changed = false;
        Set<UUID> seenPlayers = new HashSet<>();
        for (ServerPlayer player : activePlayers == null ? List.<ServerPlayer>of() : activePlayers) {
            if (player == null || !player.isAlive() || player.isSpectator()) continue;
            UUID playerUuid = player.getUUID();
            seenPlayers.add(playerUuid);
            String itemKey = heldItemKey(player.getMainHandItem());
            if (itemKey.isBlank()) {
                changed |= hotPotatoStates.remove(playerUuid) != null;
                continue;
            }
            HeldItemState previous = hotPotatoStates.get(playerUuid);
            int ticks = previous != null && itemKey.equals(previous.itemKey())
                ? Math.min(DungeonModifierCombatRules.HOT_POTATO_HOLD_TICKS + 1, previous.heldTicks() + 1)
                : 1;
            if (ticks > DungeonModifierCombatRules.HOT_POTATO_HOLD_TICKS) {
                player.setRemainingFireTicks(Math.max(
                    player.getRemainingFireTicks(), DungeonModifierCombatRules.HOT_POTATO_FIRE_TICKS
                ));
                hotPotatoStates.remove(playerUuid);
            } else {
                hotPotatoStates.put(playerUuid, new HeldItemState(playerUuid, itemKey, ticks));
            }
            changed = true;
        }
        changed |= hotPotatoStates.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
        return changed;
    }

    private boolean clearPursuerMark(ServerLevel level) {
        boolean changed = pursuerMobUuid != null || pursuerTargetUuid != null;
        releasePursuerMark(level);
        return changed;
    }

    private void releasePursuerMark(ServerLevel level) {
        if (pursuerMobUuid == null) {
            pursuerTargetUuid = null;
            return;
        }
        if (level.getEntity(pursuerMobUuid) instanceof Mob mob) {
            removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), PURSUER_MARK_SPEED_ID);
            removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), PURSUER_MARK_HEALTH_ID);
            if (mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
            releaseGlow(mob, PURSUER_GLOW_KEY);
        }
        pursuerMobUuid = null;
        pursuerTargetUuid = null;
    }

    private boolean clearRallyingCry(ServerLevel level) {
        boolean changed = rallyBearerUuid != null || rallyPulseTicks > 0;
        releaseRallyBearer(level);
        restoreLeases(level, EffectKind.REGENERATION);
        rallyPulseTicks = 0;
        return changed;
    }

    private boolean clearDreadAura(ServerLevel level) {
        boolean changed = dreadCarrierUuid != null || dreadPulseTicks > 0;
        releaseDreadCarrier(level);
        restoreLeases(level, EffectKind.WEAKNESS);
        dreadPulseTicks = 0;
        return changed;
    }

    private void releaseRallyBearer(ServerLevel level) {
        if (rallyBearerUuid == null) return;
        if (level.getEntity(rallyBearerUuid) instanceof Mob mob) {
            removeModifier(mob.getAttribute(Attributes.MAX_HEALTH), RALLYING_CRY_HEALTH_ID);
            releaseGlow(mob, RALLY_GLOW_KEY);
        }
        rallyBearerUuid = null;
        rallyBearerRole = "";
    }

    private void releaseDreadCarrier(ServerLevel level) {
        if (dreadCarrierUuid == null) return;
        if (level.getEntity(dreadCarrierUuid) instanceof Mob mob) releaseGlow(mob, DREAD_GLOW_KEY);
        dreadCarrierUuid = null;
        dreadCarrierRole = "";
    }

    String rolePresentationSummary(ServerLevel level) {
        StringBuilder summary = new StringBuilder();
        appendRolePresentation(summary, level, rallyBearerUuid, rallyBearerRole, RALLY_ROLE_LABEL);
        appendRolePresentation(summary, level, dreadCarrierUuid, dreadCarrierRole, DREAD_ROLE_LABEL);
        return summary.length() == 0 ? "NONE" : summary.toString();
    }

    private static void appendRolePresentation(
        StringBuilder summary, ServerLevel level, UUID entityUuid, String storedRole, String fallbackRole
    ) {
        if (entityUuid == null) return;
        if (summary.length() > 0) summary.append(" | ");
        String role = storedRole == null || storedRole.isBlank() ? fallbackRole : storedRole;
        String currentName = level != null && level.getEntity(entityUuid) instanceof Mob mob
            ? mob.getName().getString() : "UNLOADED";
        if (currentName == null || currentName.isBlank()) currentName = "UNKNOWN";
        if (currentName.length() > 64) currentName = currentName.substring(0, 64);
        summary.append(role).append(": ").append(currentName);
    }

    private List<UUID> sortedEligibleMobIds(
        ServerLevel level,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        UUID elite,
        Set<UUID> occupied,
        Predicate<Mob> bossPredicate
    ) {
        return tracked.stream()
            .sorted(Comparator.comparing(UUID::toString))
            .filter(id -> isEligibleCarrier(
                entityAsMob(level, id), id, tracked, summoned, elite, occupied, bossPredicate
            ))
            .toList();
    }

    private boolean isEligibleCarrier(
        Mob mob,
        UUID id,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        UUID elite,
        Set<UUID> occupied,
        Predicate<Mob> bossPredicate
    ) {
        return mob != null
            && id != null
            && tracked.contains(id)
            && mob.isAlive()
            && !mob.isRemoved()
            && !summoned.contains(id)
            && !id.equals(elite)
            && !occupied.contains(id)
            && !bossPredicate.test(mob);
    }

    private void forEachTrackedMob(ServerLevel level, Collection<UUID> tracked, java.util.function.Consumer<Mob> action) {
        for (UUID uuid : List.copyOf(tracked)) {
            if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) action.accept(mob);
        }
    }

    private void forEachEligibleMob(
        ServerLevel level,
        Collection<UUID> tracked,
        Set<UUID> summoned,
        Predicate<Mob> bossPredicate,
        java.util.function.Consumer<Mob> action
    ) {
        forEachTrackedMob(level, tracked, mob -> {
            if (!summoned.contains(mob.getUUID()) && !bossPredicate.test(mob)) action.accept(mob);
        });
    }

    private void applyRisingHeatStage(Mob mob, int stage) {
        removeModifier(mob.getAttribute(Attributes.ATTACK_DAMAGE), RISING_HEAT_DAMAGE_ID);
        removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), RISING_HEAT_SPEED_ID);
        DungeonModifierCombatRules.RisingHeatStage values =
            DungeonModifierCombatRules.risingHeatStage(stage * DungeonModifierCombatRules.RISING_HEAT_STAGE_INTERVAL_TICKS);
        if (stage <= 0) return;
        addPermanentMultiplier(
            mob.getAttribute(Attributes.ATTACK_DAMAGE), RISING_HEAT_DAMAGE_ID,
            values.damageMultiplier() - 1.0D
        );
        addPermanentMultiplier(
            mob.getAttribute(Attributes.MOVEMENT_SPEED), RISING_HEAT_SPEED_ID,
            values.speedMultiplier() - 1.0D
        );
    }

    private void removeRisingHeatModifiers(ServerLevel level, Collection<UUID> tracked) {
        forEachTrackedMob(level, tracked, mob -> {
            removeModifier(mob.getAttribute(Attributes.ATTACK_DAMAGE), RISING_HEAT_DAMAGE_ID);
            removeModifier(mob.getAttribute(Attributes.MOVEMENT_SPEED), RISING_HEAT_SPEED_ID);
        });
    }

    private boolean updateEffectLeases(ServerLevel level) {
        if (effectLeases.isEmpty()) return false;
        boolean changed = false;
        for (LeaseKey key : List.copyOf(effectLeases.keySet())) {
            EffectLease lease = effectLeases.get(key);
            Entity entity = level.getEntity(key.entityUuid());
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
                effectLeases.remove(key);
                changed = true;
                continue;
            }
            int remaining = lease.remainingTicks() - 1;
            if (remaining <= 0) {
                restoreLease(living, lease);
                effectLeases.remove(key);
            } else {
                effectLeases.put(key, lease.withRemainingTicks(remaining));
            }
            changed = true;
        }
        return changed;
    }

    private void applyEffectLease(LivingEntity entity, EffectKind kind, int duration, int amplifier) {
        LeaseKey key = new LeaseKey(entity.getUUID(), kind);
        EffectLease existing = effectLeases.get(key);
        MobEffectInstance current = entity.getEffect(kind.holder());
        if (existing == null) {
            if (current != null
                && (current.getAmplifier() > amplifier
                    || (current.getAmplifier() == amplifier
                        && (current.getDuration() < 0 || current.getDuration() >= duration)))) {
                return;
            }
            if (effectLeases.size() >= MAX_LEASES) return;
        } else if (current != null
            && current.getAmplifier() > amplifier) {
            effectLeases.remove(key);
            return;
        } else if (current != null
            && current.getAmplifier() == amplifier
            && (current.getDuration() < 0 || current.getDuration() > duration + 1)) {
            effectLeases.remove(key);
            return;
        }
        MobEffectInstance previous = existing == null
            ? current
            : existing.previousEffect();
        effectLeases.put(key, new EffectLease(
            entity.getUUID(), kind, previous == null ? null : new MobEffectInstance(previous),
            duration, amplifier
        ));
        entity.addEffect(new MobEffectInstance(kind.holder(), duration, amplifier, false, true, true));
    }

    private void restoreAllEffectLeases(ServerLevel level) {
        for (EffectLease lease : List.copyOf(effectLeases.values())) {
            if (level.getEntity(lease.entityUuid()) instanceof LivingEntity living) restoreLease(living, lease);
        }
        effectLeases.clear();
    }

    private void restoreLeases(ServerLevel level, EffectKind kind) {
        for (LeaseKey key : List.copyOf(effectLeases.keySet())) {
            if (key.kind() != kind) continue;
            EffectLease lease = effectLeases.remove(key);
            if (level.getEntity(key.entityUuid()) instanceof LivingEntity living) restoreLease(living, lease);
        }
    }

    private void releaseEffectLease(ServerLevel level, LivingEntity entity, EffectKind kind) {
        if (entity == null) return;
        EffectLease lease = effectLeases.remove(new LeaseKey(entity.getUUID(), kind));
        if (lease != null) restoreLease(entity, lease);
    }

    private void restoreLease(LivingEntity living, EffectLease lease) {
        MobEffectInstance current = living.getEffect(lease.kind().holder());
        if (current == null) {
            if (lease.previousEffect() != null) living.addEffect(new MobEffectInstance(lease.previousEffect()));
            return;
        }
        if (current.getAmplifier() == lease.desiredAmplifier()
            && (current.getDuration() < 0 || current.getDuration() > lease.remainingTicks() + 1)) {
            return;
        }
        if (current.getAmplifier() == lease.desiredAmplifier()) {
            living.removeEffect(lease.kind().holder());
            if (lease.previousEffect() != null) living.addEffect(new MobEffectInstance(lease.previousEffect()));
        }
    }

    private boolean claimGlow(Mob mob, String key) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(key)) data.putBoolean(key, mob.hasGlowingTag());
        boolean changed = !mob.hasGlowingTag();
        mob.setGlowingTag(true);
        return changed;
    }

    private void releaseGlow(Mob mob, String key) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(key)) return;
        boolean original = data.getBoolean(key);
        data.remove(key);
        mob.setGlowingTag(original);
    }

    private Mob entityAsMob(ServerLevel level, UUID uuid) {
        return uuid != null && level.getEntity(uuid) instanceof Mob mob ? mob : null;
    }

    private static boolean isRollable(Set<String> activeModifierIds, String id) {
        return activeModifierIds != null
            && activeModifierIds.contains(id)
            && DungeonModifierCatalog.isRollable(id);
    }

    private static String heldItemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static void addPermanentMultiplier(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null || attribute.getModifier(id) != null || amount == 0.0D) return;
        attribute.addPermanentModifier(new AttributeModifier(
            id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        ));
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null && attribute.getModifier(id) != null) attribute.removeModifier(id);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum EffectKind {
        REGENERATION(MobEffects.REGENERATION),
        WEAKNESS(MobEffects.WEAKNESS),
        INVISIBILITY(MobEffects.INVISIBILITY);

        private final Holder<MobEffect> holder;

        EffectKind(Holder<MobEffect> holder) {
            this.holder = holder;
        }

        Holder<MobEffect> holder() {
            return holder;
        }
    }

    private record LeaseKey(UUID entityUuid, EffectKind kind) {}

    private record HeldItemState(UUID playerUuid, String itemKey, int heldTicks) {}

    private record EffectLease(
        UUID entityUuid,
        EffectKind kind,
        MobEffectInstance previousEffect,
        int remainingTicks,
        int desiredAmplifier
    ) {
        EffectLease withRemainingTicks(int ticks) {
            return new EffectLease(entityUuid, kind, previousEffect, ticks, desiredAmplifier);
        }
    }
}
