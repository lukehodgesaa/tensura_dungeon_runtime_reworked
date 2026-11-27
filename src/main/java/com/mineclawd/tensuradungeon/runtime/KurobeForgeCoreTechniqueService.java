package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraAddonCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateSkillCompat;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotFactory;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import java.util.OptionalDouble;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class KurobeForgeCoreTechniqueService {
    public static final int RESERVED_NATIVE_SLOT = 7;
    private static final double EPSILON = 0.0001D;
    private static final Object LOCK = new Object();

    private KurobeForgeCoreTechniqueService() {}

    public static Result activate(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return Result.notHandled();
        }
        KurobeForgeWeaponData.State initial = KurobeForgeWeaponData.read(stack);
        if (initial.core() == null && !KurobeForgeWeaponData.hasCorruptSocket(stack)) {
            return Result.notHandled();
        }
        synchronized (LOCK) {
            return activateLocked(player, stack);
        }
    }

    private static Result activateLocked(ServerPlayer player, ItemStack stack) {
        if (!KurobeForgeSnapshotFactory.eligible(stack)) {
            return Result.failure(ResultStatus.INVALID_WEAPON, "THIS ITEM IS NOT A FORGE WEAPON");
        }
        if (KurobeForgeWeaponData.hasCorruptSocket(stack)) {
            return Result.failure(ResultStatus.INVALID_SOCKET, "CORRUPT CORE SOCKET REQUIRES ADMINISTRATIVE REVIEW");
        }
        KurobeForgeWeaponData.State state = KurobeForgeWeaponData.read(stack);
        CoreSatchelData.CoreRecord core = state.core();
        if (core == null || state.weaponId() == null) {
            return Result.failure(ResultStatus.INVALID_SOCKET, "CORE SOCKET DATA IS INCOMPLETE");
        }
        if (!KurobeForgeRules.isActiveGrade(state.grade())) {
            return Result.failure(ResultStatus.GRADE_LOCKED, "GENESIS / GRADE VII IS RESERVED");
        }
        if (state.conduitRank() <= 0) {
            return Result.failure(ResultStatus.CONDUIT_REQUIRED, "REFINE CONDUIT BEFORE CASTING A CORE TECHNIQUE");
        }
        if (core.tier() > KurobeForgeRules.maximumCoreTier(state.grade())) {
            return Result.failure(ResultStatus.CORE_GRADE_LOCKED, "THIS CORE TIER EXCEEDS THE WEAPON GRADE");
        }
        if (!boundExactlyOnce(player, state)) {
            return Result.failure(ResultStatus.INVALID_BINDING, "EXACT CORE/WEAPON BINDING IS NOT VALID");
        }

        MonsterCoreTechniqueCatalog.Definition definition;
        try {
            definition = MonsterCoreTechniqueCatalog.find(core.familyId());
        } catch (IllegalArgumentException invalidFamily) {
            return Result.failure(ResultStatus.TECHNIQUE_UNAVAILABLE, "CORE TECHNIQUE IS NOT APPROVED");
        }
        if (!definition.techniqueId().equals(core.techniqueId())) {
            return Result.failure(ResultStatus.TECHNIQUE_UNAVAILABLE, "CORE TECHNIQUE ID DOES NOT MATCH ITS FAMILY");
        }

        long now = player.serverLevel().getGameTime();
        if (state.techniqueCooldownUntil() > now) {
            long remaining = state.techniqueCooldownUntil() - now;
            return Result.failure(ResultStatus.COOLDOWN, "CORE TECHNIQUE RECHARGING (" + remaining + " TICKS)");
        }

        TensuraSubordinateSkillCompat.TechniqueProjection technique =
            TensuraSubordinateSkillCompat.inspectCoreTechnique(
                player, definition.techniqueId(), definition.mode()
            );
        if (technique.status() != TensuraSubordinateSkillCompat.TechniqueStatus.AVAILABLE) {
            return Result.failure(
                ResultStatus.TECHNIQUE_UNAVAILABLE,
                "NATIVE TENSURA TECHNIQUE IS UNAVAILABLE: " + technique.status().name()
            );
        }
        long burstBudget = KurobeForgeRules.burstOutput(
            state.grade(), state.conduitRank(), core.tier()
        );
        OptionalDouble currentMagicule = TensuraAddonCompat.readMagicule(player);
        if (currentMagicule.isEmpty()) {
            return Result.failure(ResultStatus.NATIVE_RESOURCE_UNAVAILABLE, "NATIVE MAGICULE RESOURCE IS UNAVAILABLE");
        }
        double originalMagicule = currentMagicule.getAsDouble();
        FundingPlan funding = fundingPlan(
            technique.magiculeCost(), core.totalCharge(), burstBudget, originalMagicule
        );
        if (!funding.valid()) {
            return Result.failure(ResultStatus.TECHNIQUE_UNAVAILABLE, "NATIVE TECHNIQUE HAS NO VALID MAGICULE COST");
        }
        if (!funding.affordable()) {
            return Result.failure(ResultStatus.INSUFFICIENT_MAGICULE, "CORE CHARGE + MAGICULE CANNOT PAY THIS TECHNIQUE");
        }
        long coreContribution = funding.coreContribution();

        double fundedMagicule = originalMagicule + coreContribution;
        if (!TensuraAddonCompat.writeMagicule(player, fundedMagicule)) {
            return Result.failure(ResultStatus.NATIVE_RESOURCE_UNAVAILABLE, "COULD NOT AUTHORIZE MAGICULE CHANNEL");
        }
        TensuraSubordinateSkillCompat.ActivationResult activation =
            TensuraSubordinateSkillCompat.activateCoreTechnique(
                player, definition.techniqueId(), definition.mode(), RESERVED_NATIVE_SLOT
            );
        if (activation.status() != TensuraSubordinateSkillCompat.ActivationStatus.ACTIVATED) {
            TensuraAddonCompat.writeMagicule(player, originalMagicule);
            return Result.failure(
                ResultStatus.NATIVE_REJECTED,
                "NATIVE TENSURA ACTIVATION REJECTED: " + activation.status().name()
            );
        }

        double expectedMagicule = Math.max(0.0D, fundedMagicule - technique.magiculeCost());
        OptionalDouble afterNative = TensuraAddonCompat.readMagicule(player);
        if (afterNative.isEmpty() || !TensuraAddonCompat.writeMagicule(player, expectedMagicule)) {
            TensuraAddonCompat.writeMagicule(player, originalMagicule);
            return Result.failure(ResultStatus.NATIVE_RESOURCE_UNAVAILABLE, "NATIVE MAGICULE SETTLEMENT FAILED");
        }

        long cooldownUntil = Math.addExact(now, definition.cooldownTicks());
        if (!KurobeForgeWeaponData.recordTechniqueCast(stack, coreContribution, cooldownUntil)) {
            TensuraAddonCompat.writeMagicule(player, originalMagicule);
            return Result.failure(ResultStatus.PERSISTENCE_FAILED, "CORE CHARGE COULD NOT BE COMMITTED");
        }
        return Result.success(
            definition.displayName() + " cast: " + definition.techniqueName()
                + "  /  CORE " + coreContribution + "  /  MAGICULE " + format(expectedMagicule)
        );
    }

    private static boolean boundExactlyOnce(ServerPlayer player, KurobeForgeWeaponData.State state) {
        KurobeForgeData forge = KurobeForgeData.get(player.serverLevel());
        if (!forge.bindingMatches(state.core().coreId(), player.getUUID(), state.weaponId())) return false;
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < Math.min(36, inventory.getContainerSize()); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!KurobeForgeSnapshotFactory.eligible(candidate)) continue;
            if (state.weaponId().equals(KurobeForgeWeaponData.read(candidate).weaponId())) count++;
        }
        return count == 1;
    }

    private static long ceilToLong(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) return 0L;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.ceil(value - EPSILON);
    }

    static FundingPlan fundingPlan(
        double nativeCost,
        long availableCoreCharge,
        long burstBudget,
        double currentMagicule
    ) {
        long required = ceilToLong(nativeCost);
        if (required <= 0L || availableCoreCharge < 0L || burstBudget < 0L
            || !Double.isFinite(currentMagicule) || currentMagicule < 0.0D) {
            return new FundingPlan(false, false, 0L, 0L, 0.0D);
        }
        long coreContribution = Math.min(required, Math.min(availableCoreCharge, burstBudget));
        boolean affordable = currentMagicule + coreContribution + EPSILON >= nativeCost;
        double playerContribution = Math.max(0.0D, nativeCost - coreContribution);
        return new FundingPlan(true, affordable, required, coreContribution, playerContribution);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f", Math.max(0.0D, value));
    }

    public enum ResultStatus {
        ACTIVATED,
        NO_SOCKET,
        INVALID_WEAPON,
        INVALID_SOCKET,
        INVALID_BINDING,
        GRADE_LOCKED,
        CONDUIT_REQUIRED,
        CORE_GRADE_LOCKED,
        COOLDOWN,
        TECHNIQUE_UNAVAILABLE,
        NATIVE_RESOURCE_UNAVAILABLE,
        INSUFFICIENT_MAGICULE,
        NATIVE_REJECTED,
        PERSISTENCE_FAILED
    }

    public record Result(boolean handled, ResultStatus status, String message) {
        public Result {
            status = status == null ? ResultStatus.PERSISTENCE_FAILED : status;
            message = message == null ? "" : message;
        }

        static Result notHandled() {
            return new Result(false, ResultStatus.NO_SOCKET, "");
        }

        static Result success(String message) {
            return new Result(true, ResultStatus.ACTIVATED, message);
        }

        static Result failure(ResultStatus status, String message) {
            return new Result(true, status, message);
        }
    }

    record FundingPlan(
        boolean valid,
        boolean affordable,
        long requiredCharge,
        long coreContribution,
        double playerContribution
    ) {}
}
