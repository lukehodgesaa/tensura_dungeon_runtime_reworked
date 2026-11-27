package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateCastCompat;
import java.util.UUID;

public final class DungeonSubordinateCastSession {
    public static final int RECOVERY_DELAY_TICKS = 20;

    private final DungeonInstanceAllocationData.InstanceKey instanceKey;
    private final UUID subordinateId;
    private final UUID targetId;
    private final int slot;
    private final String skillId;
    private final int mode;
    private final TensuraSubordinateCastCompat.NativeCastProfile profile;
    private final int expectedCastTicks;
    private final long startedAt;
    private Phase phase;
    private int lastNativeDuration;
    private long releaseRequestedAt = -1L;
    private long recoveryUntil = -1L;
    private boolean nativeStartAccepted;
    private boolean nativeHoldObserved;

    private DungeonSubordinateCastSession(
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        UUID subordinateId,
        UUID targetId,
        TensuraSubordinateCastCompat.NativeCastDescriptor descriptor,
        long startedAt,
        int initialDuration,
        boolean initialHeld
    ) {
        this.instanceKey = instanceKey;
        this.subordinateId = subordinateId;
        this.targetId = targetId;
        this.slot = descriptor.slot();
        this.skillId = descriptor.skillId();
        this.mode = descriptor.mode();
        this.profile = descriptor.profile();
        this.expectedCastTicks = descriptor.castingTicks();
        this.startedAt = startedAt;
        this.lastNativeDuration = Math.max(0, initialDuration);
        this.phase = profile == TensuraSubordinateCastCompat.NativeCastProfile.REPEAT
            ? Phase.CHANNELING
            : Phase.HOLDING;
        this.nativeStartAccepted = true;
        this.nativeHoldObserved = initialHeld;
    }

    public static DungeonSubordinateCastSession begin(
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        UUID subordinateId,
        UUID targetId,
        TensuraSubordinateCastCompat.NativeCastDescriptor descriptor,
        long startedAt,
        int initialDuration,
        boolean initialHeld
    ) {
        if (instanceKey == null || subordinateId == null || targetId == null || descriptor == null) {
            throw new IllegalArgumentException("A subordinate cast session requires exact identity");
        }
        return new DungeonSubordinateCastSession(
            instanceKey, subordinateId, targetId, descriptor, startedAt, initialDuration, initialHeld
        );
    }

    public boolean matches(
        DungeonInstanceAllocationData.InstanceKey currentInstance,
        UUID currentTarget,
        TensuraSubordinateCastCompat.NativeCastDescriptor descriptor
    ) {
        return instanceKey.equals(currentInstance)
            && targetId.equals(currentTarget)
            && descriptor != null
            && slot == descriptor.slot()
            && mode == descriptor.mode()
            && skillId.equals(descriptor.skillId());
    }

    public boolean shouldRelease(long gameTime, int nativeDuration) {
        if (!active() || phase == Phase.RELEASING) {
            return false;
        }
        return nativeDuration >= expectedCastTicks
            || gameTime - startedAt >= expectedCastTicks;
    }

    public void observeHeld(int duration) {
        lastNativeDuration = Math.max(lastNativeDuration, Math.max(0, duration));
        nativeHoldObserved = true;
    }

    public void requestRelease(long gameTime) {
        if (!active() || phase == Phase.RELEASING) {
            return;
        }
        phase = Phase.RELEASING;
        releaseRequestedAt = gameTime;
    }

    public void enterRecovery(long gameTime) {
        phase = Phase.RECOVERY;
        recoveryUntil = Math.max(gameTime, gameTime + RECOVERY_DELAY_TICKS);
    }

    public boolean recoveryComplete(long gameTime) {
        return phase == Phase.RECOVERY && gameTime >= recoveryUntil;
    }

    public boolean releaseWasRequested() {
        return phase == Phase.RELEASING;
    }

    public boolean releaseHasWaitedOneTick(long gameTime) {
        return phase == Phase.RELEASING && releaseRequestedAt >= 0L && gameTime > releaseRequestedAt;
    }

    public boolean active() {
        return phase == Phase.HOLDING
            || phase == Phase.CHANNELING
            || phase == Phase.RELEASING;
    }

    public boolean nativeHoldOwned() {
        return nativeStartAccepted && nativeHoldObserved;
    }

    public DungeonInstanceAllocationData.InstanceKey instanceKey() {
        return instanceKey;
    }

    public UUID subordinateId() {
        return subordinateId;
    }

    public UUID targetId() {
        return targetId;
    }

    public int slot() {
        return slot;
    }

    public String skillId() {
        return skillId;
    }

    public int mode() {
        return mode;
    }

    public TensuraSubordinateCastCompat.NativeCastProfile profile() {
        return profile;
    }

    public int expectedCastTicks() {
        return expectedCastTicks;
    }

    public int lastNativeDuration() {
        return lastNativeDuration;
    }

    public Phase phase() {
        return phase;
    }

    public long recoveryUntil() {
        return recoveryUntil;
    }

    public enum Phase {
        HOLDING,
        CHANNELING,
        RELEASING,
        RECOVERY
    }
}
