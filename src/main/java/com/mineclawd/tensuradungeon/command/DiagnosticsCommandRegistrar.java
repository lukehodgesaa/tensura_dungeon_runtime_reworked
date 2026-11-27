package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity.ValidationReport;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomDebugSnapshot;
import com.mineclawd.tensuradungeon.block.entity.RoomObjectiveType;
import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import com.mineclawd.tensuradungeon.block.entity.RoomProtectionView;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessReport;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessValidator;
import com.mineclawd.tensuradungeon.compat.TensuraSkillCompatHooks;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompatStatus;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraEnergyDebugHelper;
import com.mineclawd.tensuradungeon.event.ActiveRoomProtectionEvents;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mineclawd.tensuradungeon.runtime.DungeonBudgetEngine;
import com.mineclawd.tensuradungeon.runtime.DungeonCombatObservationService;
import com.mineclawd.tensuradungeon.runtime.DungeonCombatObservationStore;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonHudHooks;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRepository;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData;
import com.mineclawd.tensuradungeon.runtime.RewardMailboxData;
import com.mineclawd.tensuradungeon.runtime.RoomBudgetResult;
import com.mineclawd.tensuradungeon.runtime.TensuraMaxEnergyProofData;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyPoolRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.findNearestController;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatDecimal;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatNullablePos;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatPos;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;
final class DiagnosticsCommandRegistrar {
    private static final String RUNTIME_VERSION = "0.20.8-stage20_8_explicit_room_bounds";

    private DiagnosticsCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("tensura")
            .then(Commands.literal("debug").executes(DiagnosticsCommandRegistrar::debugTensuraCompat))
            .then(Commands.literal("hooks").executes(DiagnosticsCommandRegistrar::debugTensuraHooks))
            .then(Commands.literal("energydebug").executes(DiagnosticsCommandRegistrar::debugTensuraEnergy))
            .then(Commands.literal("maxenergydebug").executes(DiagnosticsCommandRegistrar::debugTensuraMaxEnergy))
            .then(Commands.literal("sourcedebug").executes(DiagnosticsCommandRegistrar::debugTensuraSource))
            .then(Commands.literal("driftsave").executes(DiagnosticsCommandRegistrar::saveTensuraDriftSnapshot))
            .then(Commands.literal("driftcompare").executes(DiagnosticsCommandRegistrar::compareTensuraDriftSnapshot))
            .then(Commands.literal("drifttimeline").executes(DiagnosticsCommandRegistrar::debugTensuraDriftTimeline))
            .then(Commands.literal("granttest")
                .then(Commands.literal("magicule")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(DiagnosticsCommandRegistrar::grantTestMagicule)))
                .then(Commands.literal("aura")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(DiagnosticsCommandRegistrar::grantTestAura))))
            .then(Commands.literal("maxgranttest")
                .then(Commands.literal("ep")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(DiagnosticsCommandRegistrar::grantTestMaxEp))))
            .then(Commands.literal("maxpersist")
                .then(Commands.literal("save").executes(DiagnosticsCommandRegistrar::saveTensuraMaxPersistenceSnapshot))
                .then(Commands.literal("compare").executes(DiagnosticsCommandRegistrar::compareTensuraMaxPersistenceSnapshot))
                .then(Commands.literal("clear").executes(DiagnosticsCommandRegistrar::clearTensuraMaxPersistenceSnapshot)))
            .then(Commands.literal("combat")
                .then(Commands.literal("summary").executes(DiagnosticsCommandRegistrar::debugCombatObservationSummary))
                .then(Commands.literal("clear").executes(DiagnosticsCommandRegistrar::clearCombatObservation))));

        root.then(Commands.literal("enemy")
            .then(Commands.literal("debug").executes(DiagnosticsCommandRegistrar::debugEnemyPool)));

        root.then(Commands.literal("structure")
            .then(Commands.literal("inspect").executes(RoomCommandRegistrar::inspectRoomReadiness))
            .then(Commands.literal("markers").executes(RoomCommandRegistrar::inspectStructureMarkers))
            .then(Commands.literal("validate").executes(RoomCommandRegistrar::validateStructureReadiness))
            .then(Commands.literal("readiness").executes(RoomCommandRegistrar::validateStructureReadiness))
            .then(Commands.literal("boundsdebug").executes(RoomCommandRegistrar::debugStructureBounds))
            .then(Commands.literal("preset")
                .then(Commands.literal("scan").executes(RunCommandHandlers::scanPresetFloor))));

        root.then(Commands.literal("runtime")
            .then(Commands.literal("debug").executes(DiagnosticsCommandRegistrar::debugRuntime)));

        root.then(Commands.literal("dungeon")
            .then(Commands.literal("enter").executes(DiagnosticsCommandRegistrar::enterDungeon))
            .then(Commands.literal("leave").executes(DiagnosticsCommandRegistrar::leaveDungeon))
            .then(Commands.literal("debug").executes(DiagnosticsCommandRegistrar::debugDungeon)));

        root.then(Commands.literal("hud")
            .then(Commands.literal("snapshot").executes(DiagnosticsCommandRegistrar::debugHudSnapshot)));
    }

    static int debugTensuraCompat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        TensuraCompatStatus status = TensuraCompat.getStatus();
        com.mineclawd.tensuradungeon.reward.RewardPreviewSource.RewardPreview preview =
            com.mineclawd.tensuradungeon.compat.tensura.TensuraRewardPreviewSource.INSTANCE.preview(player, 1, "BASIC", "COMPAT_DEBUG");
        source.sendSuccess(() -> Component.literal("Tensura Compat Debug"), false);
        source.sendSuccess(() -> Component.literal("Tensura Mod Present: " + status.tensuraModPresent()), false);
        source.sendSuccess(() -> Component.literal("Tensura Mod Id: " + status.primaryModId()), false);
        source.sendSuccess(() -> Component.literal("Tensura Mod Ids Detected: " + status.detectedModIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Tensura Version: " + status.tensuraVersion()), false);
        source.sendSuccess(() -> Component.literal("Compat Initialized: " + status.compatInitialized()), false);
        source.sendSuccess(() -> Component.literal("Skill Grief Hook Registered: " + status.skillGriefHookRegistered()), false);
        source.sendSuccess(() -> Component.literal("EP Hook Available: " + status.epRewardHookAvailable()), false);
        source.sendSuccess(() -> Component.literal("Magicule Hook Available: " + status.magiculeRewardHookAvailable()), false);
        source.sendSuccess(() -> Component.literal("Aura Hook Available: " + status.auraRewardHookAvailable()), false);
        source.sendSuccess(() -> Component.literal("Battlewill Hook Available: " + status.battlewillRewardHookAvailable()), false);
        source.sendSuccess(() -> Component.literal("Skill Reward Hook Available: " + status.skillRewardHookAvailable()), false);
        source.sendSuccess(() -> Component.literal("Player Data API Available: " + status.playerDataApiAvailable()), false);
        source.sendSuccess(() -> Component.literal("Optional Addons Detected: none"), false);
        source.sendSuccess(() -> Component.literal("Related Loaded Mods Count: " + status.detectedModIds().size()), false);
        source.sendSuccess(() -> Component.literal("Related Loaded Mods: " + status.detectedModIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Known Core Modules Count: " + status.detectedModIds().size()), false);
        source.sendSuccess(() -> Component.literal("Known Core Modules: " + status.detectedModIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Optional Addon Candidates Count: 0"), false);
        source.sendSuccess(() -> Component.literal("Optional Addon Candidates: none"), false);
        source.sendSuccess(() -> Component.literal("Unknown Related Mods Count: 0"), false);
        source.sendSuccess(() -> Component.literal("Unknown Related Mods: none"), false);
        source.sendSuccess(() -> Component.literal("Class Probe Candidates Count: 0"), false);
        source.sendSuccess(() -> Component.literal("Hook Candidate Count: 0"), false);
        source.sendSuccess(() -> Component.literal("Hook Candidates Summary: none"), false);
        source.sendSuccess(() -> Component.literal("Hook Evidence Summary: " + status.hookEvidenceSummary()), false);
        source.sendSuccess(() -> Component.literal("Aura Hook Candidate Count: 0"), false);
        source.sendSuccess(() -> Component.literal("Aura Hook Candidate Summary: none"), false);
        source.sendSuccess(() -> Component.literal("Battlewill Hook Candidate Count: 0"), false);
        source.sendSuccess(() -> Component.literal("Battlewill Hook Candidate Summary: none"), false);
        source.sendSuccess(() -> Component.literal("Addon-Aware Probe Summary: none"), false);
        source.sendSuccess(() -> Component.literal("Safe Fallback Mode: " + status.safeFallbackMode()), false);
        source.sendSuccess(() -> Component.literal("Last Probe Error: " + status.lastProbeErrorSummary()), false);
        source.sendSuccess(() -> Component.literal("EP Preview Status: " + preview.epStatus()), false);
        source.sendSuccess(() -> Component.literal("Magicule Preview Status: " + preview.magiculeStatus()), false);
        source.sendSuccess(() -> Component.literal("Skill Preview Status: " + preview.skillStatus()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugTensuraHooks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        TensuraCompatStatus status = TensuraCompat.getStatus();
        source.sendSuccess(() -> Component.literal("Tensura Hook Evidence Map"), false);
        source.sendSuccess(() -> Component.literal("Integration State: " + status.integrationState()), false);
        source.sendSuccess(() -> Component.literal("Supported Contract: tensura=" + TensuraCompat.SUPPORTED_TENSURA_VERSION + ", manascore=" + TensuraCompat.SUPPORTED_MANASCORE_VERSION), false);
        source.sendSuccess(() -> Component.literal("Addon Candidates Detected But Not Trusted: none"), false);
        source.sendSuccess(() -> Component.literal("Any Mutation-Safe Hook Evidence: " + status.anyMutationSafeEvidence()), false);
        source.sendSuccess(() -> Component.literal("Global Safe Fallback Mode: " + status.safeFallbackMode()), false);
        return Command.SINGLE_SUCCESS;
    }
    static int debugTensuraEnergy(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        TensuraEnergyDebugHelper.EnergyDebugSnapshot snapshot = TensuraEnergyDebugHelper.inspect(player);
        source.sendSuccess(() -> Component.literal("Tensura Energy Debug Proof"), false);
        source.sendSuccess(() -> Component.literal("Current Aura: " + TensuraDungeonCommands.formatDecimal(snapshot.aura())), false);
        source.sendSuccess(() -> Component.literal("Current Magicule: " + TensuraDungeonCommands.formatDecimal(snapshot.magicule())), false);
        source.sendSuccess(() -> Component.literal("Derived EP: " + TensuraDungeonCommands.formatDecimal(snapshot.ep())), false);
        source.sendSuccess(() -> Component.literal("Max Aura: " + (snapshot.maxAura() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxAura()))), false);
        source.sendSuccess(() -> Component.literal("Max Magicule: " + (snapshot.maxMagicule() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxMagicule()))), false);
        source.sendSuccess(() -> Component.literal("Existence Storage Read Succeeded: " + snapshot.existenceAvailable()), false);
        source.sendSuccess(() -> Component.literal("Required Max Aura Attribute Appears Present: " + snapshot.auraAttributePresent()), false);
        source.sendSuccess(() -> Component.literal("Required Max Magicule Attribute Appears Present: " + snapshot.magiculeAttributePresent()), false);
        source.sendSuccess(() -> Component.literal("Safe Fallback Mode: " + snapshot.safeFallbackMode()), false);
        source.sendSuccess(() -> Component.literal("Normal Rewards Placeholder-Only: " + snapshot.placeholderRewardOnly()), false);
        source.sendSuccess(() -> Component.literal("Energy Read Error: " + snapshot.readError()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int grantTestMagicule(CommandContext<CommandSourceStack> context) {
        return runEnergyGrantTest(context, "magicule");
    }

    static int grantTestAura(CommandContext<CommandSourceStack> context) {
        return runEnergyGrantTest(context, "aura");
    }

    private static int runEnergyGrantTest(CommandContext<CommandSourceStack> context, String kind) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        TensuraEnergyDebugHelper.EnergyGrantResult result = "magicule".equals(kind)
            ? TensuraEnergyDebugHelper.grantMagicule(player, amount)
            : TensuraEnergyDebugHelper.grantAura(player, amount);
        source.sendSuccess(() -> Component.literal("Tensura Grant Test: " + kind.toUpperCase(java.util.Locale.ROOT)), false);
        source.sendSuccess(() -> Component.literal("Amount Requested: " + TensuraDungeonCommands.formatDecimal(result.amountRequested())), false);
        source.sendSuccess(() -> Component.literal("Helper Path Called: " + result.helperCalled()), false);
        source.sendSuccess(() -> Component.literal("Value Changed: " + result.valueChanged()), false);
        source.sendSuccess(() -> Component.literal("Aura Before: " + TensuraDungeonCommands.formatDecimal(result.before().aura())), false);
        source.sendSuccess(() -> Component.literal("Aura After: " + TensuraDungeonCommands.formatDecimal(result.after().aura())), false);
        source.sendSuccess(() -> Component.literal("Aura Delta: " + TensuraDungeonCommands.formatDecimal(result.auraDelta())), false);
        source.sendSuccess(() -> Component.literal("Magicule Before: " + TensuraDungeonCommands.formatDecimal(result.before().magicule())), false);
        source.sendSuccess(() -> Component.literal("Magicule After: " + TensuraDungeonCommands.formatDecimal(result.after().magicule())), false);
        source.sendSuccess(() -> Component.literal("Magicule Delta: " + TensuraDungeonCommands.formatDecimal(result.magiculeDelta())), false);
        source.sendSuccess(() -> Component.literal("EP Before: " + TensuraDungeonCommands.formatDecimal(result.before().ep())), false);
        source.sendSuccess(() -> Component.literal("EP After: " + TensuraDungeonCommands.formatDecimal(result.after().ep())), false);
        source.sendSuccess(() -> Component.literal("EP Delta: " + TensuraDungeonCommands.formatDecimal(result.epDelta())), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Untouched: " + result.mailboxUntouched()), false);
        source.sendSuccess(() -> Component.literal("Normal Rewards Placeholder-Only: " + result.after().placeholderRewardOnly()), false);
        source.sendSuccess(() -> Component.literal("Grant Test Error: " + result.error()), false);
        return Command.SINGLE_SUCCESS;
    }
    static int debugTensuraMaxEnergy(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        TensuraEnergyDebugHelper.EnergyDebugSnapshot snapshot = TensuraEnergyDebugHelper.inspect(player);
        source.sendSuccess(() -> Component.literal("Tensura Max Energy Debug Proof"), false);
        source.sendSuccess(() -> Component.literal("Current Aura: " + TensuraDungeonCommands.formatDecimal(snapshot.aura())), false);
        source.sendSuccess(() -> Component.literal("Current Magicule: " + TensuraDungeonCommands.formatDecimal(snapshot.magicule())), false);
        source.sendSuccess(() -> Component.literal("Derived EP: " + TensuraDungeonCommands.formatDecimal(snapshot.ep())), false);
        source.sendSuccess(() -> Component.literal("Max EP: " + (snapshot.maxEp() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxEp()))), false);
        source.sendSuccess(() -> Component.literal("Max Aura: " + (snapshot.maxAura() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxAura()))), false);
        source.sendSuccess(() -> Component.literal("Max Magicule: " + (snapshot.maxMagicule() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxMagicule()))), false);
        source.sendSuccess(() -> Component.literal("Existence Storage Read Succeeded: " + snapshot.existenceAvailable()), false);
        source.sendSuccess(() -> Component.literal("Required Max Aura Attribute Appears Present: " + snapshot.auraAttributePresent()), false);
        source.sendSuccess(() -> Component.literal("Required Max Magicule Attribute Appears Present: " + snapshot.magiculeAttributePresent()), false);
        source.sendSuccess(() -> Component.literal("Safe Fallback Mode: " + snapshot.safeFallbackMode()), false);
        source.sendSuccess(() -> Component.literal("Normal Rewards Placeholder-Only: " + snapshot.placeholderRewardOnly()), false);
        source.sendSuccess(() -> Component.literal("Energy Read Error: " + snapshot.readError()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int grantTestMaxEp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(context, "amount");
        TensuraEnergyDebugHelper.MaxEnergyProgressionResult result = TensuraEnergyDebugHelper.increaseMaxEp(player, amount);
        source.sendSuccess(() -> Component.literal("Tensura Max EP Durable Probe"), false);
        source.sendSuccess(() -> Component.literal("Amount Requested: " + TensuraDungeonCommands.formatDecimal(result.amountRequested())), false);
        source.sendSuccess(() -> Component.literal("Mutation Path: " + result.mutationPath()), false);
        source.sendSuccess(() -> Component.literal("Mutation Applied: " + result.helperCalled()), false);
        source.sendSuccess(() -> Component.literal("Value Changed: " + result.valueChanged()), false);
        source.sendSuccess(() -> Component.literal("Aura Before: " + TensuraDungeonCommands.formatDecimal(result.before().aura())), false);
        source.sendSuccess(() -> Component.literal("Aura After: " + TensuraDungeonCommands.formatDecimal(result.after().aura())), false);
        source.sendSuccess(() -> Component.literal("Magicule Before: " + TensuraDungeonCommands.formatDecimal(result.before().magicule())), false);
        source.sendSuccess(() -> Component.literal("Magicule After: " + TensuraDungeonCommands.formatDecimal(result.after().magicule())), false);
        source.sendSuccess(() -> Component.literal("Max EP Before: " + (result.before().maxEp() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(result.before().maxEp()))), false);
        source.sendSuccess(() -> Component.literal("Max EP After: " + (result.after().maxEp() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(result.after().maxEp()))), false);
        source.sendSuccess(() -> Component.literal("Max EP Delta: " + TensuraDungeonCommands.formatDecimal(result.maxEpDelta())), false);
        source.sendSuccess(() -> Component.literal("Max Aura Before: " + (result.before().maxAura() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(result.before().maxAura()))), false);
        source.sendSuccess(() -> Component.literal("Max Aura After: " + (result.after().maxAura() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(result.after().maxAura()))), false);
        source.sendSuccess(() -> Component.literal("Max Aura Delta: " + TensuraDungeonCommands.formatDecimal(result.maxAuraDelta())), false);
        source.sendSuccess(() -> Component.literal("Max Magicule Before: " + (result.before().maxMagicule() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(result.before().maxMagicule()))), false);
        source.sendSuccess(() -> Component.literal("Max Magicule After: " + (result.after().maxMagicule() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(result.after().maxMagicule()))), false);
        source.sendSuccess(() -> Component.literal("Max Magicule Delta: " + TensuraDungeonCommands.formatDecimal(result.maxMagiculeDelta())), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Untouched: " + result.mailboxUntouched()), false);
        source.sendSuccess(() -> Component.literal("Normal Rewards Placeholder-Only: " + result.after().placeholderRewardOnly()), false);
        source.sendSuccess(() -> Component.literal("Durable Probe Error: " + result.error()), false);
        source.sendSuccess(() -> Component.literal("Next Step: run /tensuradungeon tensura maxpersist save before relog, then /tensuradungeon tensura maxpersist compare after relog"), false);
        return Command.SINGLE_SUCCESS;
    }
    static int debugTensuraSource(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        TensuraEnergyDebugHelper.SourceDebugSnapshot snapshot = TensuraEnergyDebugHelper.inspectSource(player);
        source.sendSuccess(() -> Component.literal("Tensura Energy Source-of-Truth Debug"), false);
        source.sendSuccess(() -> Component.literal("Storage Aura: " + TensuraDungeonCommands.formatDecimal(snapshot.storageAura())), false);
        source.sendSuccess(() -> Component.literal("Storage Magicule: " + TensuraDungeonCommands.formatDecimal(snapshot.storageMagicule())), false);
        source.sendSuccess(() -> Component.literal("Storage Derived EP: " + TensuraDungeonCommands.formatDecimal(snapshot.storageEp())), false);
        source.sendSuccess(() -> Component.literal("Existence Storage Available: " + snapshot.existenceAvailable()), false);
        source.sendSuccess(() -> Component.literal("EnergyHelper Max Aura: " + formatNullableDecimal(snapshot.energyHelperMaxAura())), false);
        source.sendSuccess(() -> Component.literal("EnergyHelper Max Magicule: " + formatNullableDecimal(snapshot.energyHelperMaxMagicule())), false);
        source.sendSuccess(() -> Component.literal("EnergyHelper Max EP: " + formatNullableDecimal(snapshot.energyHelperMaxEp())), false);
        emitAttributeSnapshot(source, snapshot.maxAuraAttribute(), "MAX_AURA");
        emitAttributeSnapshot(source, snapshot.maxMagiculeAttribute(), "MAX_MAGICULE");
        emitAttributeSnapshot(source, snapshot.limitedSpiritualMaxAura(), "LIMITED_SPIRITUAL_MAX_AURA");
        emitAttributeSnapshot(source, snapshot.limitedSpiritualMaxMagicule(), "LIMITED_SPIRITUAL_MAX_MAGICULE");
        emitLifecycleSnapshot(source, snapshot.lifecycle());
        emitRaceSnapshot(source, snapshot.race());
        emitSkillSnapshot(source, snapshot.skills());
        source.sendSuccess(() -> Component.literal("Reward Mailbox Count: " + snapshot.rewardMailboxCount()), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Untouched: " + snapshot.rewardMailboxUntouched()), false);
        source.sendSuccess(() -> Component.literal("Normal Rewards Placeholder-Only: " + snapshot.placeholderRewardOnly()), false);
        source.sendSuccess(() -> Component.literal("Safe Fallback Mode: " + snapshot.safeFallbackMode()), false);
        source.sendSuccess(() -> Component.literal("Energy Read Error: " + snapshot.readError()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int saveTensuraDriftSnapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        TensuraEnergyDebugHelper.SourceDebugSnapshot snapshot = TensuraEnergyDebugHelper.inspectSource(player);
        TensuraMaxEnergyProofData.get(level).saveSourceSnapshot(player.getUUID(), snapshot, level.getGameTime(), level.dimension().location().toString(), "NATURAL_RELOAD_BASELINE");
        source.sendSuccess(() -> Component.literal("Tensura Drift Source Snapshot Saved"), false);
        source.sendSuccess(() -> Component.literal("Snapshot Kind: NATURAL_RELOAD_BASELINE"), false);
        source.sendSuccess(() -> Component.literal("Saved Dimension: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Saved Storage Aura: " + TensuraDungeonCommands.formatDecimal(snapshot.storageAura())), false);
        source.sendSuccess(() -> Component.literal("Saved Storage Magicule: " + TensuraDungeonCommands.formatDecimal(snapshot.storageMagicule())), false);
        source.sendSuccess(() -> Component.literal("Saved Storage Derived EP: " + TensuraDungeonCommands.formatDecimal(snapshot.storageEp())), false);
        source.sendSuccess(() -> Component.literal("Saved EnergyHelper Max Aura: " + formatNullableDecimal(snapshot.energyHelperMaxAura())), false);
        source.sendSuccess(() -> Component.literal("Saved EnergyHelper Max Magicule: " + formatNullableDecimal(snapshot.energyHelperMaxMagicule())), false);
        source.sendSuccess(() -> Component.literal("Saved EnergyHelper Max EP: " + formatNullableDecimal(snapshot.energyHelperMaxEp())), false);
        source.sendSuccess(() -> Component.literal("Saved Reward Mailbox Count: " + snapshot.rewardMailboxCount()), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Untouched: " + snapshot.rewardMailboxUntouched()), false);
        source.sendSuccess(() -> Component.literal("Next Step: relog/reload, then run /tensuradungeon tensura sourcedebug and /tensuradungeon tensura driftcompare"), false);
        return Command.SINGLE_SUCCESS;
    }

    static int compareTensuraDriftSnapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        TensuraMaxEnergyProofData proofData = TensuraMaxEnergyProofData.get(level);
        java.util.Optional<TensuraMaxEnergyProofData.SourceSnapshotRecord> stored = proofData.getSourceSnapshot(player.getUUID());
        if (stored.isEmpty()) {
            source.sendFailure(Component.literal("No saved drift snapshot found. Run /tensuradungeon tensura driftsave first."));
            return 0;
        }

        TensuraMaxEnergyProofData.SourceSnapshotRecord baseline = stored.get();
        TensuraEnergyDebugHelper.SourceDebugSnapshot current = TensuraEnergyDebugHelper.inspectSource(player);
        boolean maxAttributeBaseChanged = attributeBaseChanged(baseline.maxAuraAttribute(), current.maxAuraAttribute()) || attributeBaseChanged(baseline.maxMagiculeAttribute(), current.maxMagiculeAttribute());
        boolean maxAttributeFinalChanged = attributeFinalChanged(baseline.maxAuraAttribute(), current.maxAuraAttribute()) || attributeFinalChanged(baseline.maxMagiculeAttribute(), current.maxMagiculeAttribute());
        boolean storageCurrentChanged = !approximatelyEqual(baseline.storageAura(), current.storageAura()) || !approximatelyEqual(baseline.storageMagicule(), current.storageMagicule()) || !approximatelyEqual(baseline.storageEp(), current.storageEp());
        boolean limitedSpiritualChanged = attributeBaseChanged(baseline.limitedSpiritualMaxAura(), current.limitedSpiritualMaxAura()) || attributeFinalChanged(baseline.limitedSpiritualMaxAura(), current.limitedSpiritualMaxAura()) || attributeBaseChanged(baseline.limitedSpiritualMaxMagicule(), current.limitedSpiritualMaxMagicule()) || attributeFinalChanged(baseline.limitedSpiritualMaxMagicule(), current.limitedSpiritualMaxMagicule());
        boolean raceChanged = !safeEquals(baseline.race().raceId(), current.race().raceId()) || !safeEquals(baseline.race().raceClass(), current.race().raceClass()) || !safeEquals(baseline.race().raceDisplay(), current.race().raceDisplay());
        boolean skillChanged = baseline.skills().learnedSkillCount() != current.skills().learnedSkillCount() || !approximatelyEqualIntNullable(baseline.skills().temporarySkillCount(), current.skills().temporarySkillCount()) || !approximatelyEqualIntNullable(baseline.skills().toggledSkillCount(), current.skills().toggledSkillCount());
        boolean mailboxUntouched = baseline.rewardMailboxCount() == current.rewardMailboxCount();
        boolean naturalReloadDrift = maxAttributeBaseChanged || maxAttributeFinalChanged || storageCurrentChanged || limitedSpiritualChanged || raceChanged || skillChanged;
        String classification = naturalReloadDrift ? "NATURAL_RELOAD_DRIFT" : "NO_DRIFT_DETECTED";

        source.sendSuccess(() -> Component.literal("Tensura Reload Drift Compare"), false);
        source.sendSuccess(() -> Component.literal("Baseline Game Time: " + baseline.recordedGameTime()), false);
        source.sendSuccess(() -> Component.literal("Baseline Dimension: " + baseline.dimensionKey()), false);
        source.sendSuccess(() -> Component.literal("Snapshot Kind: " + baseline.snapshotKind()), false);
        source.sendSuccess(() -> Component.literal("Natural Reload Drift Detected: " + naturalReloadDrift), false);
        source.sendSuccess(() -> Component.literal("Max Attribute Base Changed: " + maxAttributeBaseChanged), false);
        source.sendSuccess(() -> Component.literal("Max Attribute Final Changed: " + maxAttributeFinalChanged), false);
        source.sendSuccess(() -> Component.literal("Storage Current Changed: " + storageCurrentChanged), false);
        source.sendSuccess(() -> Component.literal("Limited Spiritual Clamp Changed: " + limitedSpiritualChanged), false);
        source.sendSuccess(() -> Component.literal("Race Summary Changed: " + raceChanged), false);
        source.sendSuccess(() -> Component.literal("Skill Count Changed: " + skillChanged), false);
        source.sendSuccess(() -> Component.literal("Mailbox Untouched: " + mailboxUntouched), false);
        source.sendSuccess(() -> Component.literal("Drift Classification: " + classification), false);
        source.sendSuccess(() -> Component.literal("Storage Aura Delta: saved=" + TensuraDungeonCommands.formatDecimal(baseline.storageAura()) + ", current=" + TensuraDungeonCommands.formatDecimal(current.storageAura())), false);
        source.sendSuccess(() -> Component.literal("Storage Magicule Delta: saved=" + TensuraDungeonCommands.formatDecimal(baseline.storageMagicule()) + ", current=" + TensuraDungeonCommands.formatDecimal(current.storageMagicule())), false);
        source.sendSuccess(() -> Component.literal("Storage Derived EP Delta: saved=" + TensuraDungeonCommands.formatDecimal(baseline.storageEp()) + ", current=" + TensuraDungeonCommands.formatDecimal(current.storageEp())), false);
        source.sendSuccess(() -> Component.literal("Max Aura Base Delta: saved=" + formatNullableDecimal(baseline.maxAuraAttribute().baseValue()) + ", current=" + formatNullableDecimal(current.maxAuraAttribute().baseValue())), false);
        source.sendSuccess(() -> Component.literal("Max Aura Final Delta: saved=" + formatNullableDecimal(baseline.maxAuraAttribute().finalValue()) + ", current=" + formatNullableDecimal(current.maxAuraAttribute().finalValue())), false);
        source.sendSuccess(() -> Component.literal("Max Magicule Base Delta: saved=" + formatNullableDecimal(baseline.maxMagiculeAttribute().baseValue()) + ", current=" + formatNullableDecimal(current.maxMagiculeAttribute().baseValue())), false);
        source.sendSuccess(() -> Component.literal("Max Magicule Final Delta: saved=" + formatNullableDecimal(baseline.maxMagiculeAttribute().finalValue()) + ", current=" + formatNullableDecimal(current.maxMagiculeAttribute().finalValue())), false);
        source.sendSuccess(() -> Component.literal("Current Read Error: " + current.readError()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugTensuraDriftTimeline(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Tensura Drift Timeline Guidance"), false);
        source.sendSuccess(() -> Component.literal("1. Run /tensuradungeon tensura sourcedebug immediately after login/reload."), false);
        source.sendSuccess(() -> Component.literal("2. Wait about 5 seconds, then run /tensuradungeon tensura sourcedebug again."), false);
        source.sendSuccess(() -> Component.literal("3. Wait about 30 seconds, then run /tensuradungeon tensura sourcedebug a third time."), false);
        source.sendSuccess(() -> Component.literal("4. If values shift without any grant, classify the behavior as delayed reload drift rather than durable progression."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void emitAttributeSnapshot(CommandSourceStack source, TensuraEnergyDebugHelper.AttributeSnapshot snapshot, String label) {
        source.sendSuccess(() -> Component.literal(label + " Present: " + snapshot.present()), false);
        source.sendSuccess(() -> Component.literal(label + " Base Value: " + formatNullableDecimal(snapshot.baseValue())), false);
        source.sendSuccess(() -> Component.literal(label + " Final Value: " + formatNullableDecimal(snapshot.finalValue())), false);
        source.sendSuccess(() -> Component.literal(label + " Modifier Count: " + snapshot.modifiers().size()), false);
        int limit = Math.min(snapshot.modifiers().size(), 10);
        for (int i = 0; i < limit; i++) {
            TensuraEnergyDebugHelper.AttributeModifierSnapshot modifier = snapshot.modifiers().get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(label + " Modifier " + index + ": id=" + modifier.id() + ", name=" + modifier.label() + ", amount=" + formatNullableDecimal(modifier.amount()) + ", operation=" + modifier.operation() + ", error=" + modifier.readError()), false);
        }
        source.sendSuccess(() -> Component.literal(label + " Read Error: " + snapshot.readError()), false);
    }

    private static void emitLifecycleSnapshot(CommandSourceStack source, TensuraEnergyDebugHelper.LifecycleSnapshot snapshot) {
        source.sendSuccess(() -> Component.literal("Lifecycle isSkippingEPDrop: " + formatNullableBoolean(snapshot.skippingEpDrop())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Spawn Type: " + snapshot.spawnType()), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Gained EP: " + formatNullableDecimal(snapshot.gainedEp())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Human Kill: " + formatNullableDecimal(snapshot.humanKill())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Sleep Mode Time: " + formatNullableDecimal(snapshot.sleepModeTime())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Spiritual Form: " + formatNullableBoolean(snapshot.spiritualForm())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Demon Lord Seed: " + formatNullableBoolean(snapshot.demonLordSeed())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle True Demon Lord: " + formatNullableBoolean(snapshot.trueDemonLord())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Hero Egg: " + formatNullableBoolean(snapshot.heroEgg())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle True Hero: " + formatNullableBoolean(snapshot.trueHero())), false);
        source.sendSuccess(() -> Component.literal("Lifecycle Read Error: " + snapshot.readError()), false);
    }

    private static void emitRaceSnapshot(CommandSourceStack source, TensuraEnergyDebugHelper.RaceSnapshot snapshot) {
        source.sendSuccess(() -> Component.literal("Race Present: " + snapshot.present()), false);
        source.sendSuccess(() -> Component.literal("Race ID: " + snapshot.raceId()), false);
        source.sendSuccess(() -> Component.literal("Race Class: " + snapshot.raceClass()), false);
        source.sendSuccess(() -> Component.literal("Race Display: " + snapshot.raceDisplay()), false);
        source.sendSuccess(() -> Component.literal("Race Read Error: " + snapshot.readError()), false);
    }

    private static void emitSkillSnapshot(CommandSourceStack source, TensuraEnergyDebugHelper.SkillSnapshot snapshot) {
        source.sendSuccess(() -> Component.literal("Learned Skill Count: " + snapshot.learnedSkillCount()), false);
        source.sendSuccess(() -> Component.literal("Temporary Skill Count: " + formatNullableInt(snapshot.temporarySkillCount())), false);
        source.sendSuccess(() -> Component.literal("Toggled Skill Count: " + formatNullableInt(snapshot.toggledSkillCount())), false);
        int limit = Math.min(snapshot.learnedSkillIds().size(), 10);
        for (int i = 0; i < limit; i++) {
            int index = i + 1;
            String skillId = snapshot.learnedSkillIds().get(i);
            source.sendSuccess(() -> Component.literal("Learned Skill " + index + ": " + skillId), false);
        }
        source.sendSuccess(() -> Component.literal("Skill Read Error: " + snapshot.readError()), false);
    }

    static int saveTensuraMaxPersistenceSnapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        int rewardsBefore = mailbox.getMailbox(player.getUUID()).size();
        TensuraEnergyDebugHelper.EnergyDebugSnapshot snapshot = TensuraEnergyDebugHelper.inspect(player);
        TensuraMaxEnergyProofData.get(level).saveSnapshot(player.getUUID(), snapshot, level.getGameTime());
        source.sendSuccess(() -> Component.literal("Tensura Max Energy Persistence Snapshot Saved"), false);
        source.sendSuccess(() -> Component.literal("Saved Aura: " + TensuraDungeonCommands.formatDecimal(snapshot.aura())), false);
        source.sendSuccess(() -> Component.literal("Saved Magicule: " + TensuraDungeonCommands.formatDecimal(snapshot.magicule())), false);
        source.sendSuccess(() -> Component.literal("Saved Derived EP: " + TensuraDungeonCommands.formatDecimal(snapshot.ep())), false);
        source.sendSuccess(() -> Component.literal("Saved Max EP: " + (snapshot.maxEp() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxEp()))), false);
        source.sendSuccess(() -> Component.literal("Saved Max Aura: " + (snapshot.maxAura() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxAura()))), false);
        source.sendSuccess(() -> Component.literal("Saved Max Magicule: " + (snapshot.maxMagicule() == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(snapshot.maxMagicule()))), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Untouched: " + (mailbox.getMailbox(player.getUUID()).size() == rewardsBefore)), false);
        source.sendSuccess(() -> Component.literal("Next Step: relog/reload, then run /tensuradungeon tensura maxpersist compare"), false);
        return Command.SINGLE_SUCCESS;
    }

    static int compareTensuraMaxPersistenceSnapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        int rewardsBefore = mailbox.getMailbox(player.getUUID()).size();
        TensuraMaxEnergyProofData proofData = TensuraMaxEnergyProofData.get(level);
        java.util.Optional<TensuraMaxEnergyProofData.SnapshotRecord> stored = proofData.getSnapshot(player.getUUID());
        if (stored.isEmpty()) {
            source.sendFailure(Component.literal("No saved max-energy persistence snapshot found. Run /tensuradungeon tensura maxpersist save first."));
            return 0;
        }

        TensuraMaxEnergyProofData.SnapshotRecord baseline = stored.get();
        TensuraEnergyDebugHelper.EnergyDebugSnapshot current = TensuraEnergyDebugHelper.inspect(player);
        boolean auraMatch = approximatelyEqual(baseline.aura(), current.aura());
        boolean magiculeMatch = approximatelyEqual(baseline.magicule(), current.magicule());
        boolean epMatch = approximatelyEqual(baseline.ep(), current.ep());
        boolean maxEpMatch = approximatelyEqualNullable(baseline.maxEp(), current.maxEp());
        boolean maxAuraMatch = approximatelyEqualNullable(baseline.maxAura(), current.maxAura());
        boolean maxMagiculeMatch = approximatelyEqualNullable(baseline.maxMagicule(), current.maxMagicule());
        boolean overallPass = auraMatch && magiculeMatch && epMatch && maxEpMatch && maxAuraMatch && maxMagiculeMatch && mailbox.getMailbox(player.getUUID()).size() == rewardsBefore;

        source.sendSuccess(() -> Component.literal("Tensura Max Energy Persistence Compare"), false);
        source.sendSuccess(() -> Component.literal("Baseline Recorded Game Time: " + baseline.recordedGameTime()), false);
        source.sendSuccess(() -> Component.literal("Aura Persisted: " + auraMatch + " (saved=" + TensuraDungeonCommands.formatDecimal(baseline.aura()) + ", current=" + TensuraDungeonCommands.formatDecimal(current.aura()) + ")"), false);
        source.sendSuccess(() -> Component.literal("Magicule Persisted: " + magiculeMatch + " (saved=" + TensuraDungeonCommands.formatDecimal(baseline.magicule()) + ", current=" + TensuraDungeonCommands.formatDecimal(current.magicule()) + ")"), false);
        source.sendSuccess(() -> Component.literal("Derived EP Persisted: " + epMatch + " (saved=" + TensuraDungeonCommands.formatDecimal(baseline.ep()) + ", current=" + TensuraDungeonCommands.formatDecimal(current.ep()) + ")"), false);
        source.sendSuccess(() -> Component.literal("Max EP Persisted: " + maxEpMatch + " (saved=" + formatNullableDecimal(baseline.maxEp()) + ", current=" + formatNullableDecimal(current.maxEp()) + ")"), false);
        source.sendSuccess(() -> Component.literal("Max Aura Persisted: " + maxAuraMatch + " (saved=" + formatNullableDecimal(baseline.maxAura()) + ", current=" + formatNullableDecimal(current.maxAura()) + ")"), false);
        source.sendSuccess(() -> Component.literal("Max Magicule Persisted: " + maxMagiculeMatch + " (saved=" + formatNullableDecimal(baseline.maxMagicule()) + ", current=" + formatNullableDecimal(current.maxMagicule()) + ")"), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Untouched: " + (mailbox.getMailbox(player.getUUID()).size() == rewardsBefore)), false);
        source.sendSuccess(() -> Component.literal("Persistence Compare Overall: " + (overallPass ? "PASS" : "FAIL")), false);
        return overallPass ? Command.SINGLE_SUCCESS : 0;
    }

    static int clearTensuraMaxPersistenceSnapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        boolean removed = TensuraMaxEnergyProofData.get(player.serverLevel()).clearSnapshot(player.getUUID());
        source.sendSuccess(() -> Component.literal(removed ? "Tensura max-energy persistence snapshot cleared." : "No tensura max-energy persistence snapshot was present."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugCombatObservationSummary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        DungeonCombatObservationStore store =
            DungeonCombatObservationService.store(player.serverLevel());
        List<DungeonCombatObservationStore.TargetSummary> summaries = store.summaries();
        source.sendSuccess(() -> Component.literal("Combat Observation Summary"), false);
        if (summaries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Tracked Targets: 0"), false);
            return Command.SINGLE_SUCCESS;
        }

        double totalDamage = 0.0D;
        int totalHits = 0;
        double totalDot = 0.0D;
        Map<String, Double> abilityTotals = new TreeMap<>();
        Map<String, Double> familyTotals = new TreeMap<>();
        for (DungeonCombatObservationStore.TargetSummary summary : summaries) {
            totalDamage += summary.totalDamage();
            totalHits += summary.totalHits();
            totalDot += summary.dotDamage();
            summary.damageByAbility().forEach(
                (ability, damage) -> abilityTotals.merge(ability, damage, Double::sum)
            );
            summary.damageByFamily().forEach(
                (family, damage) -> familyTotals.merge(family, damage, Double::sum)
            );
        }
        double dotSharePercent = totalDamage > 0.0D ? (totalDot / totalDamage) * 100.0D : 0.0D;
        int trackedTargets = summaries.size();
        double finalTotalDamage = totalDamage;
        int finalTotalHits = totalHits;
        double finalTotalDot = totalDot;
        double finalDotSharePercent = dotSharePercent;
        source.sendSuccess(() -> Component.literal("Tracked Targets: " + trackedTargets), false);
        source.sendSuccess(() -> Component.literal("Total Damage: " + formatDecimal(finalTotalDamage)), false);
        source.sendSuccess(() -> Component.literal("Total Hits: " + finalTotalHits), false);
        source.sendSuccess(() -> Component.literal("DoT Damage: " + formatDecimal(finalTotalDot)), false);
        source.sendSuccess(() -> Component.literal("DoT Share: " + formatDecimal(finalDotSharePercent) + "%"), false);

        List<Map.Entry<String, Double>> topAbilities = topEntries(abilityTotals, 10);
        source.sendSuccess(() -> Component.literal("Top Abilities:"), false);
        for (Map.Entry<String, Double> entry : topAbilities) {
            source.sendSuccess(
                () -> Component.literal("  " + entry.getKey() + ": " + formatDecimal(entry.getValue())),
                false
            );
        }
        List<Map.Entry<String, Double>> topFamilies = topEntries(familyTotals, 10);
        source.sendSuccess(() -> Component.literal("Top Families:"), false);
        for (Map.Entry<String, Double> entry : topFamilies) {
            source.sendSuccess(
                () -> Component.literal("  " + entry.getKey() + ": " + formatDecimal(entry.getValue())),
                false
            );
        }

        int limit = Math.min(summaries.size(), 50);
        for (int index = 0; index < limit; index++) {
            DungeonCombatObservationStore.TargetSummary summary = summaries.get(index);
            source.sendSuccess(
                () -> Component.literal(
                    "[" + summary.encounterClass() + "] " + summary.entityTypeId()
                        + " target=" + summary.key().targetUuid()
                        + " damage=" + formatDecimal(summary.totalDamage())
                        + " hits=" + summary.totalHits()
                        + " ttk=" + summary.ttkTicks() + "t"
                        + " dot=" + formatDecimal(summary.dotDamage())
                ),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    static int clearCombatObservation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        DungeonCombatObservationService.clear(player.serverLevel());
        source.sendSuccess(() -> Component.literal("Combat Observation data cleared."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static List<Map.Entry<String, Double>> topEntries(
        Map<String, Double> totals,
        int limit
    ) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(totals.entrySet());
        entries.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        return entries.size() <= limit ? entries : new ArrayList<>(entries.subList(0, limit));
    }

    private static boolean attributeBaseChanged(TensuraMaxEnergyProofData.AttributeRecord baseline, TensuraEnergyDebugHelper.AttributeSnapshot current) {
        return !approximatelyEqualNullable(baseline.baseValue(), current.baseValue());
    }

    private static boolean attributeFinalChanged(TensuraMaxEnergyProofData.AttributeRecord baseline, TensuraEnergyDebugHelper.AttributeSnapshot current) {
        return !approximatelyEqualNullable(baseline.finalValue(), current.finalValue());
    }

    private static boolean approximatelyEqualIntNullable(Integer left, Integer right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.intValue() == right.intValue();
    }

    private static boolean safeEquals(String left, String right) {
        return java.util.Objects.equals(left, right);
    }

    private static String formatNullableBoolean(Boolean value) {
        return value == null ? "UNKNOWN" : value.toString();
    }

    private static String formatNullableInt(Integer value) {
        return value == null ? "UNKNOWN" : Integer.toString(value.intValue());
    }

    private static boolean approximatelyEqual(double left, double right) {
        return Math.abs(left - right) <= 0.0001D;
    }

    private static boolean approximatelyEqualNullable(Double left, Double right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return approximatelyEqual(left.doubleValue(), right.doubleValue());
    }

    private static String formatNullableDecimal(Double value) {
        return value == null ? "UNKNOWN" : TensuraDungeonCommands.formatDecimal(value.doubleValue());
    }

    static int debugEnemyPool(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        DungeonEnemyPoolRegistry.initialize();
        source.sendSuccess(() -> Component.literal("Enemy Pool Debug"), false);
        source.sendSuccess(() -> Component.literal("Registry Initialized: " + DungeonEnemyPoolRegistry.isInitialized()), false);
        source.sendSuccess(() -> Component.literal("Total Definitions: " + DungeonEnemyPoolRegistry.getDefinitions().size()), false);
        source.sendSuccess(() -> Component.literal("Enabled Definitions: " + DungeonEnemyPoolRegistry.getEnabledDefinitionCount()), false);
        source.sendSuccess(() -> Component.literal("Fallback Definitions: " + DungeonEnemyPoolRegistry.getFallbackDefinitionCount()), false);
        source.sendSuccess(() -> Component.literal("Related Candidate Definitions: " + DungeonEnemyPoolRegistry.getRelatedDefinitionCount()), false);
        source.sendSuccess(() -> Component.literal("Definitions By Family: " + DungeonEnemyPoolRegistry.getFamilyCounts()), false);
        source.sendSuccess(() -> Component.literal("Definitions By Role: " + DungeonEnemyPoolRegistry.getRoleCounts()), false);
        if (controller != null) {
            source.sendSuccess(() -> Component.literal("Last Room Spawn Source: " + controller.getLastEnemyPoolSpawnSource()), false);
            source.sendSuccess(() -> Component.literal("Last Selected Entity: " + controller.getLastEnemyPoolEntitySelected()), false);
            source.sendSuccess(() -> Component.literal("Last Fallback Reason: " + controller.getLastEnemyPoolFallbackReason()), false);
        } else {
            source.sendSuccess(() -> Component.literal("Last Room Spawn Source: NONE - no nearby room_controller"), false);
            source.sendSuccess(() -> Component.literal("Last Selected Entity: NONE - no nearby room_controller"), false);
            source.sendSuccess(() -> Component.literal("Last Fallback Reason: NONE - no nearby room_controller"), false);
        }
        return Command.SINGLE_SUCCESS;
    }
    static int debugRuntime(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        source.sendSuccess(() -> Component.literal("Tensura Dungeon Runtime Debug"), false);
        source.sendSuccess(() -> Component.literal("Current Dimension: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Is Dungeon Dimension: " + ActiveRoomProtectionEvents.isDungeonDimension(level)), false);
        source.sendSuccess(() -> Component.literal("Protection Mode: " + ActiveRoomProtectionEvents.getProtectionMode(level, pos)), false);
        source.sendSuccess(() -> Component.literal("Global Dungeon Protection Enabled: " + ActiveRoomProtectionEvents.isFutureDungeonDimensionWideProtectionEnabled()), false);
        source.sendSuccess(() -> Component.literal("Environmental Protection Active: " + ActiveRoomProtectionEvents.isEnvironmentalProtectionActive()), false);
        source.sendSuccess(() -> Component.literal("Active Registered Protection Controllers: " + ActiveRoomProtectionEvents.getRegisteredProtectionControllerCount(level)), false);
        source.sendSuccess(() -> Component.literal("Current Position Protected: " + ActiveRoomProtectionEvents.isBlockProtected(level, pos)), false);
        source.sendSuccess(() -> Component.literal("Current Position: " + formatPos(pos)), false);
        return Command.SINGLE_SUCCESS;
    }

    static int enterDungeon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel targetLevel = source.getServer().getLevel(ModDimensions.DUNGEON);
        if (targetLevel == null) {
            source.sendFailure(Component.literal("Dungeon dimension is not available."));
            return 0;
        }

        BlockPos platformCenter = new BlockPos(0, 79, 0);
        ensureDungeonDebugPlatform(targetLevel, platformCenter);
        player.teleportTo(targetLevel, 0.5D, 80.0D, 0.5D, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to tensura_dungeon:dungeon at 0 80 0."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int leaveDungeon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel targetLevel = source.getServer().getLevel(Level.OVERWORLD);
        if (targetLevel == null) {
            source.sendFailure(Component.literal("Overworld is not available."));
            return 0;
        }

        BlockPos spawnPos = targetLevel.getSharedSpawnPos();
        player.teleportTo(targetLevel, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to the overworld spawn."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugDungeon(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        source.sendSuccess(() -> Component.literal("Tensura Dungeon Dimension Debug"), false);
        source.sendSuccess(() -> Component.literal("Current Dimension: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Is Dungeon Dimension: " + ActiveRoomProtectionEvents.isDungeonDimension(level)), false);
        source.sendSuccess(() -> Component.literal("Dungeon Protection Active: " + ActiveRoomProtectionEvents.isDungeonDimensionProtectionActive(level)), false);
        source.sendSuccess(() -> Component.literal("Tensura Compat Initialized: " + TensuraSkillCompatHooks.isInitialized()), false);
        source.sendSuccess(() -> Component.literal("Tensura Skill Grief Hook Registered: " + TensuraSkillCompatHooks.isSkillGriefHookRegistered()), false);
        source.sendSuccess(() -> Component.literal("Tensura Incapacitation Skill Hooks Registered: " + TensuraSkillCompatHooks.areIncapacitationSkillHooksRegistered()), false);
        source.sendSuccess(() -> Component.literal("Tensura Compat Init Status: " + TensuraSkillCompatHooks.getLastInitializationStatus()), false);
        source.sendSuccess(() -> Component.literal("Tensura Skill Grief Events Seen: " + TensuraSkillCompatHooks.getSkillGriefEventsSeen()), false);
        source.sendSuccess(() -> Component.literal("Tensura Skill Grief Blocked: " + TensuraSkillCompatHooks.getSkillGriefBlocked()), false);
        source.sendSuccess(() -> Component.literal("Last Tensura Skill Grief Decision: " + TensuraSkillCompatHooks.getLastSkillGriefDecision()), false);
        source.sendSuccess(() -> Component.literal("Dimension Key: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Dungeon Dimension Id: " + ModDimensions.DUNGEON.location()), false);
        source.sendSuccess(() -> Component.literal("Current Position: " + formatPos(player.blockPosition())), false);
        return Command.SINGLE_SUCCESS;
    }
    private static void ensureDungeonDebugPlatform(ServerLevel level, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floorPos = center.offset(x, 0, z);
                level.setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(floorPos.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(floorPos.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    static int debugHudSnapshot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        DungeonHudHooks.DungeonHudSnapshot snapshot = DungeonHudHooks.current(player.serverLevel(), player);
        source.sendSuccess(() -> Component.literal("Dungeon HUD Snapshot"), false);
        source.sendSuccess(() -> Component.literal("Floor / Room State: " + snapshot.floorNumber() + " / " + snapshot.roomState()), false);
        source.sendSuccess(() -> Component.literal("Controller: " + (snapshot.controllerPos() == null ? "NONE" : formatPos(snapshot.controllerPos()))), false);
        source.sendSuccess(() -> Component.literal("Objective: " + snapshot.objectiveId() + " - " + snapshot.objectiveLabel()), false);
        source.sendSuccess(() -> Component.literal("Objective Progress: " + snapshot.objectiveProgressSummary()), false);
        source.sendSuccess(() -> Component.literal("Objective Timer: " + snapshot.objectiveRemainingTicks() + " remaining / " + snapshot.objectiveDurationTicks()), false);
        source.sendSuccess(() -> Component.literal("Enemies Alive / Spawned: " + snapshot.enemiesAlive() + " / " + snapshot.enemiesSpawned()), false);
        source.sendSuccess(() -> Component.literal("Enemy Modifiers: " + snapshot.enemyModifierSummary()), false);
        source.sendSuccess(() -> Component.literal("Composition: " + snapshot.compositionSummary()), false);
        source.sendSuccess(() -> Component.literal("Encounter Waves: " + snapshot.waveSummary()), false);
        source.sendSuccess(() -> Component.literal("Snapshot Revision: " + snapshot.revisionGameTime()), false);
        return 1;
    }
}
