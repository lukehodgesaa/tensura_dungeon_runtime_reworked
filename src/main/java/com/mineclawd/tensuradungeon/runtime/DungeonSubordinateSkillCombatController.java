package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateCastCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateSkillCompat;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;

public final class DungeonSubordinateSkillCombatController {
    private static final int ACTIVATION_INTERVAL_TICKS = 10;
    private static final float LOOK_ANGLE = 30.0F;
    private static final int MAX_DIAGNOSTIC_STATES = 512;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<CastKey, DungeonSubordinateCastSession> ACTIVE_CASTS =
        new LinkedHashMap<>();
    private static final Map<String, String> LAST_DIAGNOSTIC_STATES = new LinkedHashMap<>();

    private DungeonSubordinateSkillCombatController() {
    }

    public static void tick(ServerLevel level, RoomControllerBlockEntity controller) {
        if (level == null || controller == null) {
            return;
        }
        DungeonInstanceAllocationData.InstanceKey instanceKey = controller.getDungeonInstanceKey();
        if (instanceKey == null) {
            return;
        }
        if (controller.getRoomState() != RoomControllerBlockEntity.RoomState.ACTIVE) {
            cancelForInstance(level, instanceKey, "ROOM_NOT_ACTIVE");
            return;
        }
        DungeonSubordinateTransportData.Binding binding =
            DungeonSubordinateTransportData.get(level).binding(instanceKey).orElse(null);
        if (binding == null) {
            cancelForInstance(level, instanceKey, "NO_BINDING");
            logDiagnostic(
                "controller=" + controller.getBlockPos(),
                "NO_BINDING",
                "instance=" + instanceKey
            );
            return;
        }

        for (DungeonSubordinateTransportData.Entry entry : binding.entries()) {
            CastKey castKey = new CastKey(instanceKey, entry.entityId());
            DungeonSubordinateCastSession session = ACTIVE_CASTS.get(castKey);
            if (session != null && session.recoveryComplete(level.getGameTime())) {
                ACTIVE_CASTS.remove(castKey);
                session = null;
            }
            String entryKey = "instance=" + instanceKey + " entry=" + entry.entityId();
            Entity entity = level.getEntity(entry.entityId());
            if (!entry.state().isInstanceBound()) {
                cancelSession(
                    level,
                    castKey,
                    entity,
                    "ENTRY_STATE_" + entry.state().name(),
                    false
                );
                logDiagnostic(entryKey, "SKIP_ENTRY_STATE=" + entry.state().name(), "slot=" + entry.slot());
                continue;
            }
            if (!(entity instanceof LivingEntity subordinate)) {
                cancelSession(level, castKey, null, "ENTITY_UNRESOLVED", false);
                logDiagnostic(entryKey, "SKIP_ENTITY_UNRESOLVED", "slot=" + entry.slot());
                continue;
            }
            if (!subordinate.isAlive() || subordinate.isRemoved()) {
                cancelSession(level, castKey, subordinate, "ENTITY_NOT_ALIVE", false);
                logDiagnostic(
                    entryKey,
                    "SKIP_ENTITY_NOT_ALIVE",
                    "name=" + subordinate.getName().getString()
                );
                continue;
            }
            if (!binding.hostId().equals(entry.ownerId())) {
                cancelSession(level, castKey, subordinate, "ENTRY_OWNER_MISMATCH", false);
                logDiagnostic(
                    entryKey,
                    "SKIP_ENTRY_OWNER_MISMATCH",
                    "bindingHost=" + binding.hostId() + " entryOwner=" + entry.ownerId()
                );
                continue;
            }
            if (TensuraCompat.subordinateOwnerId(subordinate)
                .filter(binding.hostId()::equals)
                .isEmpty()) {
                cancelSession(level, castKey, subordinate, "ENTITY_OWNER_MISMATCH", false);
                logDiagnostic(
                    entryKey,
                    "SKIP_ENTITY_OWNER_MISMATCH",
                    "name=" + subordinate.getName().getString()
                );
                continue;
            }

            if (session != null && session.phase() == DungeonSubordinateCastSession.Phase.RECOVERY) {
                continue;
            }
            LivingEntity target = controller.selectDungeonSkillTarget(level, subordinate);
            if (target == null) {
                cancelSession(level, castKey, subordinate, "TARGET_LOST", true);
                logDiagnostic(
                    entryKey,
                    session == null ? "NO_TARGET" : "TARGET_LOST_CANCELLED",
                    "name=" + subordinate.getName().getString()
                        + " controller=" + controller.getBlockPos()
                );
                continue;
            }
            if (session != null && !session.targetId().equals(target.getUUID())) {
                cancelSession(level, castKey, subordinate, "TARGET_CHANGED", true);
                logDiagnostic(
                    entryKey,
                    "TARGET_LOST_CANCELLED",
                    "oldTarget=" + session.targetId() + " newTarget=" + target.getUUID()
                );
                continue;
            }
            aimAt(subordinate, target);

            if (session == null) {
                if (level.getGameTime() % ACTIVATION_INTERVAL_TICKS != 0L) {
                    continue;
                }
                startAssignedCast(level, instanceKey, castKey, entryKey, subordinate, target);
            } else {
                advanceCast(level, castKey, entryKey, subordinate, target, session);
            }
        }
    }

    public static void cancelForInstance(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        String reason
    ) {
        if (instanceKey == null) {
            return;
        }
        for (CastKey castKey : java.util.List.copyOf(ACTIVE_CASTS.keySet())) {
            if (instanceKey.equals(castKey.instanceKey())) {
                Entity entity = level == null ? null : level.getEntity(castKey.subordinateId());
                cancelSession(level, castKey, entity instanceof LivingEntity living ? living : null,
                    reason, false);
            }
        }
    }

    private static void startAssignedCast(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        CastKey castKey,
        String entryKey,
        LivingEntity subordinate,
        LivingEntity target
    ) {
        TensuraSubordinateCastCompat.CastProbe selected = null;
        for (int slot = 0; slot < TensuraSubordinateSkillCompat.ACTIVE_SLOT_CAPACITY; slot++) {
            TensuraSubordinateCastCompat.CastProbe probe =
                TensuraSubordinateCastCompat.inspectAssignedSkill(subordinate, slot);
            if (probe.status() == TensuraSubordinateCastCompat.ProbeStatus.AVAILABLE) {
                selected = probe;
                break;
            }
            if (probe.status() == TensuraSubordinateCastCompat.ProbeStatus.UNSUPPORTED) {
                logDiagnostic(
                    entryKey,
                    "UNSUPPORTED_NATIVE_CAST_PROFILE",
                    "slot=" + slot + " detail=" + probe.detail()
                );
            } else if (probe.status() == TensuraSubordinateCastCompat.ProbeStatus.FAILED) {
                logDiagnostic(entryKey, "NATIVE_COMPAT_FAILURE", "detail=" + probe.detail());
            }
        }
        if (selected == null || selected.descriptor() == null) {
            logDiagnostic(entryKey, "NO_CASTABLE_ASSIGNED_SKILL", "target=" + target.getName().getString());
            return;
        }
        TensuraSubordinateCastCompat.NativeCastDescriptor descriptor = selected.descriptor();
        TensuraSubordinateCastCompat.StartResult started =
            TensuraSubordinateCastCompat.start(subordinate, descriptor);
        if (started.status() != TensuraSubordinateCastCompat.StartStatus.ACCEPTED) {
            logDiagnostic(
                entryKey,
                startOutcome(started.status()),
                "slot=" + descriptor.slot() + " skill=" + descriptor.skillId()
                    + " mode=" + descriptor.mode() + " detail=" + started.detail()
            );
            return;
        }

        TensuraSubordinateCastCompat.NativeHeldSnapshot initialHeld =
            TensuraSubordinateCastCompat.held(subordinate, descriptor);
        DungeonSubordinateCastSession session = DungeonSubordinateCastSession.begin(
            instanceKey,
            subordinate.getUUID(),
            target.getUUID(),
            descriptor,
            level.getGameTime(),
            initialHeld.status() == TensuraSubordinateCastCompat.HeldStatus.PRESENT
                ? initialHeld.duration()
                : 0,
            initialHeld.status() == TensuraSubordinateCastCompat.HeldStatus.PRESENT
        );
        ACTIVE_CASTS.put(castKey, session);
        logDiagnostic(
            entryKey,
            "START_ACCEPTED",
            "slot=" + descriptor.slot() + " skill=" + descriptor.skillId()
                + " mode=" + descriptor.mode() + " target=" + target.getName().getString()
                + " expectedCastTicks=" + descriptor.castingTicks()
                + " detail=" + started.detail()
        );
        if (initialHeld.status() == TensuraSubordinateCastCompat.HeldStatus.PRESENT) {
            logDiagnostic(
                entryKey,
                "HELD_CONFIRMED",
                "duration=" + initialHeld.duration() + " detail=" + initialHeld.detail()
            );
            session.observeHeld(initialHeld.duration());
        } else if (initialHeld.status() == TensuraSubordinateCastCompat.HeldStatus.ABSENT) {
            session.enterRecovery(level.getGameTime());
            logDiagnostic(
                entryKey,
                "NATIVE_HELD_TERMINATED",
                "afterStart=true detail=" + initialHeld.detail()
            );
        } else {
            session.enterRecovery(level.getGameTime());
            logDiagnostic(entryKey, "NATIVE_COMPAT_FAILURE", "phase=held detail=" + initialHeld.detail());
        }
    }

    private static void advanceCast(
        ServerLevel level,
        CastKey castKey,
        String entryKey,
        LivingEntity subordinate,
        LivingEntity target,
        DungeonSubordinateCastSession session
    ) {
        TensuraSubordinateCastCompat.CastProbe probe =
            TensuraSubordinateCastCompat.inspectAssignedSkill(subordinate, session.slot());
        if (probe.status() != TensuraSubordinateCastCompat.ProbeStatus.AVAILABLE
            || !session.matches(session.instanceKey(), target.getUUID(), probe.descriptor())) {
            cancelSession(level, castKey, subordinate, "ASSIGNED_SKILL_CHANGED", true);
            logDiagnostic(entryKey, "LIFECYCLE_CANCELLED", "reason=ASSIGNED_SKILL_CHANGED");
            return;
        }
        TensuraSubordinateCastCompat.NativeCastDescriptor descriptor = probe.descriptor();
        if (session.phase() == DungeonSubordinateCastSession.Phase.RELEASING) {
            TensuraSubordinateCastCompat.NativeHeldSnapshot held =
                TensuraSubordinateCastCompat.held(subordinate, descriptor);
            if (held.status() == TensuraSubordinateCastCompat.HeldStatus.ABSENT) {
                session.enterRecovery(level.getGameTime());
                logDiagnostic(entryKey, "RELEASE_CONFIRMED", "detail=" + held.detail());
            } else if (session.releaseHasWaitedOneTick(level.getGameTime())) {
                cancelSession(level, castKey, subordinate, "RELEASE_HELD_REMAINS", true);
                logDiagnostic(entryKey, "NATIVE_COMPAT_FAILURE", "reason=RELEASE_HELD_REMAINS");
            }
            return;
        }

        TensuraSubordinateCastCompat.NativeHeldSnapshot held =
            TensuraSubordinateCastCompat.held(subordinate, descriptor);
        if (held.status() == TensuraSubordinateCastCompat.HeldStatus.FAILED) {
            cancelSession(level, castKey, subordinate, "HELD_READ_FAILED", true);
            logDiagnostic(entryKey, "NATIVE_COMPAT_FAILURE", "phase=held detail=" + held.detail());
            return;
        }
        if (held.status() == TensuraSubordinateCastCompat.HeldStatus.ABSENT) {
            session.enterRecovery(level.getGameTime());
            logDiagnostic(entryKey, "NATIVE_HELD_TERMINATED", "duration=" + session.lastNativeDuration());
            return;
        }
        session.observeHeld(held.duration());
        if (held.duration() == 1 || held.duration() % 10 == 0) {
            logDiagnostic(
                entryKey,
                "HELD_PROGRESS",
                "duration=" + held.duration() + " expected=" + session.expectedCastTicks()
            );
        }
        if (!session.shouldRelease(level.getGameTime(), held.duration())) {
            return;
        }
        TensuraSubordinateCastCompat.ReleaseResult released =
            TensuraSubordinateCastCompat.release(subordinate, descriptor);
        if (released.status() != TensuraSubordinateCastCompat.ReleaseStatus.REQUESTED) {
            cancelSession(level, castKey, subordinate, "RELEASE_FAILED", true);
            logDiagnostic(entryKey, "NATIVE_COMPAT_FAILURE", "phase=release detail=" + released.detail());
            return;
        }
        session.requestRelease(level.getGameTime());
        logDiagnostic(
            entryKey,
            "RELEASE_REQUESTED",
            "duration=" + held.duration() + " expected=" + session.expectedCastTicks()
                + " detail=" + released.detail()
        );
    }

    private static void aimAt(LivingEntity subordinate, LivingEntity target) {
        if (subordinate instanceof Mob mob) {
            mob.setTarget(target);
            mob.getLookControl().setLookAt(target, LOOK_ANGLE, LOOK_ANGLE);
        }
        subordinate.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
    }

    private static String startOutcome(TensuraSubordinateCastCompat.StartStatus status) {
        return switch (status) {
            case REJECTED_RESOURCE -> "START_REJECTED_RESOURCE";
            case REJECTED_COOLDOWN -> "START_REJECTED_COOLDOWN";
            case REJECTED_NATIVE -> "START_REJECTED_NATIVE";
            default -> "NATIVE_COMPAT_FAILURE";
        };
    }

    private static void cancelSession(
        ServerLevel level,
        CastKey castKey,
        Entity entity,
        String reason,
        boolean keepRecovery
    ) {
        DungeonSubordinateCastSession session = ACTIVE_CASTS.get(castKey);
        if (session == null) {
            return;
        }
        if (entity instanceof LivingEntity subordinate && session.nativeHoldOwned()) {
            TensuraSubordinateCastCompat.NativeCastDescriptor descriptor = new TensuraSubordinateCastCompat.NativeCastDescriptor(
                session.skillId(),
                session.slot(),
                session.mode(),
                session.expectedCastTicks(),
                session.profile(),
                null
            );
            TensuraSubordinateCastCompat.CleanupResult cleanup =
                TensuraSubordinateCastCompat.cancelIfOwned(subordinate, descriptor);
            if (cleanup.status() == TensuraSubordinateCastCompat.CleanupStatus.UNRELATED) {
                logDiagnostic(
                    "subordinate=" + subordinate.getUUID(),
                    "UNRELATED_NATIVE_HOLD_BLOCKED",
                    "reason=" + reason + " detail=" + cleanup.detail()
                );
            }
        }
        if (keepRecovery && level != null) {
            session.enterRecovery(level.getGameTime());
        } else {
            ACTIVE_CASTS.remove(castKey);
        }
        logDiagnostic(
            "subordinate=" + castKey.subordinateId(),
            "LIFECYCLE_CANCELLED",
            "reason=" + reason
        );
    }

    private static void logDiagnostic(String key, String state, String detail) {
        String previous = LAST_DIAGNOSTIC_STATES.put(key, state);
        if (state.equals(previous)) {
            return;
        }
        while (LAST_DIAGNOSTIC_STATES.size() > MAX_DIAGNOSTIC_STATES) {
            Iterator<String> iterator = LAST_DIAGNOSTIC_STATES.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        LOGGER.info("[TD-SPELL] {} outcome={} {}", key, state, detail == null ? "" : detail);
    }

    private record CastKey(
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        UUID subordinateId
    ) {
    }
}
