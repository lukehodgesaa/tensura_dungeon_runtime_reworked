package com.mineclawd.tensuradungeon.command;

import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;

import com.mineclawd.tensuradungeon.network.CoreSatchelAccessSync;
import com.mineclawd.tensuradungeon.network.LootSummaryPayload;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelAcquisitionService;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelData;
import com.mineclawd.tensuradungeon.runtime.CoreRecoveryData;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.MonsterCoreRecoveryPolicy;
import com.mineclawd.tensuradungeon.runtime.SoulOrbHaulData;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import java.util.UUID;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class CoreSatchelCommandRegistrar {
    private CoreSatchelCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("satchel")
            .then(Commands.literal("acknowledge")
                .executes(CoreSatchelCommandRegistrar::acknowledge)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(CoreSatchelCommandRegistrar::acknowledgeForPlayer)))
            .then(Commands.literal("testcore")
                .executes(CoreSatchelCommandRegistrar::insertTestCore))
            .then(Commands.literal("testrecovery")
                .executes(CoreSatchelCommandRegistrar::injectTestRecovery))
            .then(Commands.literal("recoverydebug")
                .then(Commands.argument("source", StringArgumentType.word())
                    .then(Commands.argument("floor", IntegerArgumentType.integer(1, 300))
                        .then(Commands.argument("seed", IntegerArgumentType.integer())
                            .then(Commands.argument("firstClear", BoolArgumentType.bool())
                                .executes(CoreSatchelCommandRegistrar::debugRecovery))))))
            .then(Commands.literal("recoverystatus")
                .executes(CoreSatchelCommandRegistrar::recoveryStatus))
            .then(Commands.literal("grant")
                .executes(CoreSatchelCommandRegistrar::grantNormal))
            .then(Commands.literal("status")
                .executes(CoreSatchelCommandRegistrar::satchelStatus)));
    }

    private static int acknowledge(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        return player == null ? 0 : acknowledge(context.getSource(), player);
    }

    private static int acknowledgeForPlayer(CommandContext<CommandSourceStack> context) {
        try {
            return acknowledge(context.getSource(), EntityArgument.getPlayer(context, "player"));
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(
                "Unable to resolve the player for Core Satchel acknowledgement."
            ));
            return 0;
        }
    }

    private static int acknowledge(CommandSourceStack source, ServerPlayer player) {
        CoreSatchelAcquisitionService.AcknowledgementResult result =
            CoreSatchelAcquisitionService.acknowledge(player);
        Component message = Component.literal(switch (result.status()) {
            case SENT_TO_MAILBOX ->
                "Dungeon Shop Welcome Package sent to your Dungeon Mailbox. Claim it to unlock your Normal Core Satchel.";
            case ALREADY_SENT ->
                "Your Dungeon Shop Welcome Package is already waiting in the Dungeon Mailbox or was previously claimed.";
            case ALREADY_OWNED ->
                "You already own a Core Satchel.";
        });

        source.sendSuccess(() -> message, false);
        if (source.getEntity() != player) {
            player.sendSystemMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int insertTestCore(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                "This command requires a player source."
            ));
            return 0;
        }

        CoreSatchelData data = CoreSatchelData.get(player.serverLevel());
        CoreSatchelData.StoreResult result = data.storeFirstAvailable(
            player.getUUID(),
            new CoreSatchelData.CoreRecord(UUID.randomUUID(), 1, 6_000L, 15_000L, 6_000L)
        );
        if (result.status() == CoreSatchelData.StoreStatus.STORED) {
            CoreSatchelAccessSync.send(player);
            context.getSource().sendSuccess(
                () -> Component.literal(
                    "Stored test Tier I Monster Core in Satchel slot " + (result.slot() + 1) + "."
                ),
                false
            );
            return Command.SINGLE_SUCCESS;
        }

        context.getSource().sendFailure(Component.literal(switch (result.status()) {
            case SATCHEL_NOT_OWNED -> "You do not own a Core Satchel.";
            case SATCHEL_FULL -> "Your Core Satchel is full.";
            case DUPLICATE_CORE_ID -> "The generated test Core identity already exists.";
            case INVALID_SLOT -> "The selected Core Satchel slot is invalid.";
            case SLOT_OCCUPIED -> "The selected Core Satchel slot is occupied.";
            case STORED -> throw new IllegalStateException("Stored Core result escaped success handling");
        }));
        return 0;
    }

    private static int injectTestRecovery(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) return 0;

        DungeonRunManager run = DungeonRunManager.get(player.serverLevel());
        if (!run.isRunCompleted()) {
            context.getSource().sendFailure(Component.literal(
                "Complete a dungeon floor before creating a test recovery."
            ));
            return 0;
        }

        String sessionId = run.getRunSessionId();
        int floor = run.getFloorNumber();
        SoulOrbHaulData.Summary summary = SoulOrbHaulData.get(player.serverLevel()).revealForDisplay(
            player.getUUID(),
            sessionId
        );
        if (!summary.available() || summary.floor() != floor) {
            context.getSource().sendFailure(Component.literal(
                "The current completed floor has no matching loot summary."
            ));
            return 0;
        }

        CoreRecoveryData recoveryData = CoreRecoveryData.get(player.serverLevel());
        CoreRecoveryData.TestCandidateResult result = recoveryData.injectTierOneTestCandidate(
            player.getUUID(),
            sessionId,
            floor
        );
        if (result.status() == CoreRecoveryData.TestCandidateStatus.SESSION_NOT_FOUND) {
            context.getSource().sendFailure(Component.literal(
                "The current completed floor has no finalized Core recovery."
            ));
            return 0;
        }
        if (result.status() == CoreRecoveryData.TestCandidateStatus.NO_TIER_ONE_RESULT) {
            context.getSource().sendFailure(Component.literal(
                "Unable to derive a deterministic Tier I test Core for this run."
            ));
            return 0;
        }
        if (result.status() == CoreRecoveryData.TestCandidateStatus.SUMMARY_FINALIZED) {
            context.getSource().sendFailure(Component.literal(
                "This completed-floor Core recovery was already finalized."
            ));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, LootSummaryPayload.from(
            player,
            summary,
            SoulOrbHaulData.ActionStatus.SUCCESS,
            result.status() == CoreRecoveryData.TestCandidateStatus.INJECTED
                ? "Operator test Core added to this completed-floor summary."
                : "The pending operator test Core is already available.",
            true,
            run.isExitPortalActive(),
            run.getNextFloorNumber(),
            result.recovery().pendingCandidates()
        ));
        CoreSatchelData.CoreRecord core = result.core();
        context.getSource().sendSuccess(
            () -> Component.literal(
                result.status() == CoreRecoveryData.TestCandidateStatus.INJECTED
                    ? "Added a pending Tier I test Core and reopened the completed-floor summary."
                    : "Reopened the completed-floor summary with its pending Tier I test Core."
            ),
            false
        );
        return core == null ? 0 : Command.SINGLE_SUCCESS;
    }

    private static int debugRecovery(CommandContext<CommandSourceStack> context) {
        String sourceId = StringArgumentType.getString(context, "source");
        MonsterCoreRecoveryPolicy.EncounterSource source =
            MonsterCoreRecoveryPolicy.EncounterSource.parse(sourceId).orElse(null);
        if (source == null) {
            context.getSource().sendFailure(Component.literal(
                "Unknown recovery source. Use ordinary, elite, or boss."
            ));
            return 0;
        }

        int floor = IntegerArgumentType.getInteger(context, "floor");
        long seed = IntegerArgumentType.getInteger(context, "seed");
        boolean firstClear = BoolArgumentType.getBool(context, "firstClear");
        MonsterCoreRecoveryPolicy.RecoveryRoll roll;
        try {
            roll = MonsterCoreRecoveryPolicy.roll(new MonsterCoreRecoveryPolicy.RecoveryRequest(
                "operator-preview",
                floor,
                source,
                firstClear,
                seed
            ));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }

        if (!roll.recovered()) {
            context.getSource().sendSuccess(
                () -> Component.literal(
                    "Core recovery preview: MISS"
                        + " | source=" + source.id()
                        + " | floor=" + floor
                        + " | recoveryRoll=" + roll.recoveryRollBasisPoints() + "/10000"
                        + " | threshold="
                        + source.recoveryChanceBasisPoints(MonsterCoreRecoveryPolicy.currentTier(floor))
                        + "/10000"
                ),
                false
            );
            return Command.SINGLE_SUCCESS;
        }

        CoreSatchelData.CoreRecord core = roll.core();
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Core recovery preview: RECOVERED"
                    + " | Tier=" + core.tier()
                    + " | Total=" + String.format(Locale.ROOT, "%,d", core.totalCharge()) + " EP"
                    + " | Capacity=" + String.format(Locale.ROOT, "%,d", core.capacity()) + " EP"
                    + " | Natural=" + String.format(Locale.ROOT, "%,d", core.naturalCharge()) + " EP"
                    + " | Natural%=" + String.format(
                        Locale.ROOT, "%.2f", roll.naturalChargeBasisPoints() / 100.0D
                    )
                    + " | guaranteed=" + roll.guaranteed()
            ),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int recoveryStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                "This command requires a player source."
            ));
            return 0;
        }
        DungeonRunManager run = DungeonRunManager.get(player.serverLevel());
        String sessionId = run.getRunSessionId();
        int floor = run.getFloorNumber();
        CoreRecoveryData recovery = CoreRecoveryData.get(player.serverLevel());
        CoreRecoveryData.ActiveRecovery active = recovery.activeSnapshot(
            player.getUUID(), sessionId, floor
        );
        CoreRecoveryData.CompletedRecovery completed = recovery.snapshot(
            player.getUUID(), sessionId, floor
        );
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Core recovery status"
                    + " | session=" + sessionId
                    + " | floor=" + floor
                    + " | activeReceipts=" + active.receipts().size()
                    + " | finalized=" + completed.available()
                    + " | attempts=" + completed.attempts().size()
                    + " | candidates=" + completed.candidates().size()
                    + " | pending=" + completed.pendingCandidates().size()
                    + " | claimed=" + completed.claimedCoreIds().size()
            ),
            false
        );
        for (CoreSatchelData.CoreRecord core : completed.pendingCandidates()) {
            context.getSource().sendSuccess(
                () -> Component.literal(
                    "Candidate " + core.coreId()
                        + " | Tier=" + core.tier()
                        + " | " + String.format(Locale.ROOT, "%,d", core.totalCharge())
                        + " / " + String.format(Locale.ROOT, "%,d", core.capacity()) + " EP"
                        + " | Natural=" + String.format(Locale.ROOT, "%,d", core.naturalCharge()) + " EP"
                ),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int grantNormal(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }

        CoreSatchelData.GrantResult result = CoreSatchelAccessSync.grantNormal(player);
        if (result == CoreSatchelData.GrantResult.GRANTED) {
            context.getSource().sendSuccess(
                () -> Component.literal("Operator test: Normal Core Satchel unlocked."),
                false
            );
        } else {
            context.getSource().sendSuccess(
                () -> Component.literal("Normal Core Satchel was already unlocked."),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int satchelStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }

        CoreSatchelData.AccessSnapshot access = CoreSatchelData.get(player.serverLevel())
            .snapshot(player.getUUID())
            .access();
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Core Satchel: " + (access.acquired() ? access.gradeName() : "NOT ACQUIRED")
                    + " | capacity=" + access.capacity()
            ),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
