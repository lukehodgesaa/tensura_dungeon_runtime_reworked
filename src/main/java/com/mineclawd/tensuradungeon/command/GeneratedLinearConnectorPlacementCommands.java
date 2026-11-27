package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import com.mineclawd.tensuradungeon.compat.VisualBarrierHandle;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonRunService;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonDeferredReturnData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceSpatialLayout;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceRecoveryService;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateTransportData;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRepository;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorSession;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorLazyPlacementService;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorWorldWorkScheduler;
import com.mineclawd.tensuradungeon.runtime.GeneratedRoomPlacementOperation;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorCleanupProcessor;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonThemeSelectionPolicy;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class GeneratedLinearConnectorPlacementCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ORIGIN_SCAN_RADIUS = 128;
    private static final int FLOOR_START_HINT_STEP_RADIUS = 256;
    private static final int DEFAULT_ROOM_SPACING = 128;
    private static final int DEFAULT_CLAIM_RADIUS = 120;
    private static final int HUB_GATE_SCAN_RADIUS = 96;
    private static final int HUB_GATE_MAX_VOLUME = 1024;
    private static final Direction REMOTE_PREPARATION_DIRECTION = Direction.EAST;
    private static final int TEMPLATE_ANTI_REPEAT_WINDOW = 2;
    private static final int ARCHETYPE_STREAK_WINDOW = 2;
    private static final int MAX_SAFE_PLAN_ATTEMPTS = 8;
    private static final int FEATURE_LAYOUT_ATTEMPTS = 2;
    private static final int PLACE_AND_START_GATE_RELEASE_DELAY_TICKS = 2;
    private static final long STARTUP_RECOVERY_MIN_TICKS = 40L;
    private static final long STARTUP_RECOVERY_CHECK_TICKS = 10L;
    private static final long STARTUP_RECOVERY_MAX_TICKS = 400L;
    private static final double STARTUP_RECOVERY_MIN_TARGET_MS = 35.0D;
    private static final double STARTUP_RECOVERY_MAX_TARGET_MS = 50.0D;
    private static final double SAFE_CORRIDOR_CHANCE = 0.30D;
    private static final ResourceLocation VOID_BARRIER_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("voidblock", "void_block");
    private static final ResourceLocation FOG_BARRIER_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("tensura_fog_barriers", "golden_fog_barrier");
    private static final Map<ServerLevel, PendingHubGateOpen> PENDING_PLACE_AND_START_GATE_OPENS = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, PendingPlaceAndStartStartup> PENDING_PLACE_AND_START_STARTUPS =
        new ConcurrentHashMap<>();

    private static final List<TemplateRequirement> DEFAULT_ROOM_TEMPLATES = List.of(
        new TemplateRequirement("dwarven_corridor01", "rooms/linear/dwarven_corridor01", TemplateArchetype.CORRIDOR, false, true, false, false),
        new TemplateRequirement("dwarven_corridor03", "rooms/linear/dwarven_corridor03", TemplateArchetype.CORRIDOR, false, true, false, false),
        new TemplateRequirement("dwarven_corridor04", "rooms/linear/dwarven_corridor04", TemplateArchetype.CORRIDOR, true, false, false, false),
        new TemplateRequirement("waterfall_corridor01", "rooms/linear/waterfall_corridor01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("corridor_cave_01", "rooms/linear/corridor_cave_01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("corridor_cave_02", "rooms/linear/corridor_cave_02", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("corridor_magicbrick01", "rooms/linear/corridor_magicbrick01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("grand_corridor01", "rooms/linear/grand_corridor01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("grand_corridor02", "rooms/linear/grand_corridor02", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("grand_corridor_ruins_01", "rooms/linear/grand_corridor_ruins_01", TemplateArchetype.CORRIDOR, true, false, true, false),
        new TemplateRequirement("overgrown_corridor_corner_01", "rooms/linear/overgrown_corridor_corner_01", TemplateArchetype.CORRIDOR, true, false, true, false),
        new TemplateRequirement("rubble_corridor_corner_01", "rooms/linear/rubble_corridor_corner_01", TemplateArchetype.CORRIDOR, true, false, true, false),
        new TemplateRequirement("grand_corner01", "rooms/linear/grand_corner01", TemplateArchetype.CORRIDOR, true, false, true, false),
        new TemplateRequirement("elven_stairs01", "rooms/linear/elven_stairs01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("dwarven_stairs01", "rooms/linear/dwarven_stairs01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("grand_corridor_stairs01", "rooms/linear/grand_corridor_stairs01", TemplateArchetype.CORRIDOR, false, true, true, false),
        new TemplateRequirement("corridor_deadend_01", "rooms/linear/corridor_deadend_01", TemplateArchetype.CORRIDOR, false, true, true, true),
        new TemplateRequirement("colosseum_combat_room_d", "rooms/linear/colosseum_combat_room_d", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("colosseum_combat_room_a", "rooms/linear/colosseum_combat_room_a", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("descend_combat_structure_c", "rooms/linear/descend_combat_structure_c", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("dungeon_double_storage01", "rooms/linear/dungeon_double_storage01", TemplateArchetype.COMBAT, true, false, false, false),
        new TemplateRequirement("rubble_combatzone_f", "rooms/linear/rubble_combatzone_f", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("combatzone_i", "rooms/linear/combatzone_i", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("ruins_entrance01", "rooms/linear/ruins_entrance01", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("standard_combat_a", "rooms/linear/standard_combat_a", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("standard_combat_b", "rooms/linear/standard_combat_b", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("gothic_standard_combat_b", "rooms/linear/gothic_standard_combat_b", TemplateArchetype.COMBAT, false, true, false, false),
        new TemplateRequirement("dungeon_three_way01", "rooms/linear/dungeon_three_way01", TemplateArchetype.CONNECTOR_HEAVY, true, false, false, false)
    );

    private static final Rotation[] ROTATION_OPTIONS = new Rotation[] {
        Rotation.NONE,
        Rotation.CLOCKWISE_90,
        Rotation.CLOCKWISE_180,
        Rotation.COUNTERCLOCKWISE_90
    };

    private GeneratedLinearConnectorPlacementCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerConnectorsCommand() {
        return Commands.literal("connectors")
            .then(Commands.argument("template", StringArgumentType.word())
                .executes(GeneratedLinearConnectorPlacementCommands::inspectTemplateConnectors));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerStatusCommand() {
        return Commands.literal("status")
            .executes(GeneratedLinearConnectorPlacementCommands::generatedFloorStatus);
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerGateCommand() {
        return Commands.literal("gate")
            .then(Commands.literal("preview").executes(GeneratedLinearConnectorPlacementCommands::previewHubGate))
            .then(Commands.literal("close").executes(GeneratedLinearConnectorPlacementCommands::closeHubGateCommand))
            .then(Commands.literal("open").executes(GeneratedLinearConnectorPlacementCommands::openHubGateCommand))
            .then(Commands.literal("debug").executes(GeneratedLinearConnectorPlacementCommands::debugHubGate));
    }
    static LiteralArgumentBuilder<CommandSourceStack> registerClearCommand() {
        return Commands.literal("clear")
            .then(Commands.literal("last").executes(GeneratedLinearConnectorPlacementCommands::clearLastGeneratedPlacement));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerCleanupCommand() {
        return Commands.literal("cleanup")
            .then(Commands.literal("status").executes(GeneratedLinearConnectorPlacementCommands::cleanupStatus));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerSoloTeleporterCommand() {
        return Commands.literal("teleporter")
            .then(Commands.literal("prepare")
                .executes(GeneratedLinearConnectorPlacementCommands::prepareSoloTeleporter)
                .then(Commands.literal("segments")
                    .then(Commands.literal("original")
                        .executes(GeneratedLinearConnectorPlacementCommands::prepareOriginalCompositeSegments)))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(
                        GeneratedLinearConnectorPlacementCommands::prepareSoloTeleporterForPlayer
                    )))
            .then(Commands.literal("status")
                .executes(GeneratedLinearConnectorPlacementCommands::soloTeleporterStatus))
            .then(Commands.literal("cancel")
                .executes(GeneratedLinearConnectorPlacementCommands::cancelSoloTeleporter));
    }

    private static int prepareSoloTeleporter(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        return prepareSoloTeleporter(source, player);
    }

    private static int prepareOriginalCompositeSegments(
        CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        return prepareTeleporter(
            source,
            player,
            List.of(DungeonInstanceAllocationData.FrozenParticipant.host(
                player.getUUID(),
                -1L
            )),
            CompositePlanningOverride.originalSegments()
        );
    }

    private static int prepareSoloTeleporterForPlayer(
        CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        return prepareSoloTeleporter(context.getSource(), EntityArgument.getPlayer(context, "player"));
    }

    private static int prepareSoloTeleporter(
        CommandSourceStack source,
        ServerPlayer player
    ) {
        return prepareTeleporter(
            source,
            player,
            List.of(DungeonInstanceAllocationData.FrozenParticipant.host(
                player.getUUID(),
                -1L
            ))
        );
    }

    public static int prepareFrozenRoster(
        ServerPlayer player,
        List<DungeonInstanceAllocationData.FrozenParticipant> participants
    ) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(2);
        return prepareTeleporter(
            source,
            player,
            participants
        );
    }

    private static int prepareTeleporter(
        CommandSourceStack source,
        ServerPlayer player,
        List<DungeonInstanceAllocationData.FrozenParticipant> participants
    ) {
        return prepareTeleporter(source, player, participants, null);
    }

    private static int prepareTeleporter(
        CommandSourceStack source,
        ServerPlayer player,
        List<DungeonInstanceAllocationData.FrozenParticipant> participants,
        CompositePlanningOverride planningOverride
    ) {
        ServerLevel level = player.serverLevel();
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation existing =
            allocations.forPlayer(player.getUUID()).orElse(null);
        if (existing != null && existing.soloPreparation() != null) {
            if (!frozenParticipantsCompatible(
                existing.soloPreparation().frozenParticipants(),
                participants
            )) {
                DungeonInstanceAllocationData.RevisionRefreshResult refresh =
                    allocations.refreshFrozenParticipantRevisions(existing.key(), participants);
                if (!refresh.accepted() || refresh.allocation() == null) {
                    source.sendFailure(Component.literal(
                        "Dungeon Teleporter: frozen participant roster changed before preparation: "
                            + refresh.reason()
                    ));
                    return 0;
                }
                existing = refresh.allocation();
            }
            emitSoloTeleporterStatus(source, existing, allocations);
            if (existing.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.ALLOCATED) {
                return beginSoloTeleporterStartup(source, player, existing, planningOverride);
            }
            if (planningOverride != null) {
                source.sendFailure(Component.literal(
                    "Composite segment test requires a fresh ALLOCATED preparation; "
                        + "the current allocation is " + existing.lifecycle().state() + "."
                ));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
            if (existing.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.READY
                || existing.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.ACTIVE
                || existing.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.SUSPENDED) {
                return dispatchReadySoloTeleporter(source, player, existing);
            }
            return Command.SINGLE_SUCCESS;
        }

        if (DungeonDeferredReturnData.get(level).forPlayer(player.getUUID()).isPresent()) {
            DungeonInstanceRecoveryService.ResumeResult recovered =
                DungeonInstanceRecoveryService.resumeOnLogin(player);
            if (!recovered.succeeded()) {
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: an earlier dungeon return must be recovered before "
                        + "a new allocation can begin: " + recovered.reason()
                ));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
        }

        DungeonFloorProgressData progress = DungeonFloorProgressData.get(level);
        int requestedFloor = progress.getSelectedFloor(player.getUUID());
        if (!progress.isFloorUnlocked(player.getUUID(), requestedFloor)) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: selected floor " + requestedFloor + " is locked."
            ));
            return 0;
        }
        DungeonRunManager hubManager = DungeonRunManager.get(level);
        BlockPos hubReference = hubManager.getHubReturnAnchorPos();
        if (hubReference == null) {
            hubReference = hubManager.getHubSpawnAnchorPos();
        }
        if (hubReference == null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: authoritative hub return/spawn anchor is missing."
            ));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        DungeonInstanceAllocationData.GridAnchorResolution established =
            allocations.establishedGridAnchor(dimensionKey(level));
        BlockPos gridAnchor;
        if (established.resolved()) {
            gridAnchor = established.gridAnchor();
            if (DungeonInstanceSpatialLayout.requiresRemoteRelocation(
                hubReference,
                gridAnchor,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight()
            )) {
                DungeonInstanceSpatialLayout.Result remote = planRemoteDungeonGrid(
                    level, player, hubReference
                );
                if (!remote.valid()) {
                    source.sendFailure(Component.literal(
                        "Dungeon Teleporter: remote instance layout failed: " + remote.reason()
                    ));
                    source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                    return 0;
                }
                DungeonInstanceAllocationData.GridRelocationResult relocation =
                    allocations.relocateReusableGrid(
                        dimensionKey(level),
                        gridAnchor,
                        remote.gridAnchor(),
                        level.getMinBuildHeight(),
                        level.getMaxBuildHeight()
                    );
                if (relocation.relocated()) {
                    gridAnchor = relocation.gridAnchor();
                    LOGGER.info(
                        "Dungeon reusable grid relocated dimension={} oldAnchor={} newAnchor={} cells={}",
                        dimensionKey(level), established.gridAnchor(), gridAnchor,
                        relocation.retainedCells()
                    );
                } else {
                    LOGGER.info(
                        "Dungeon remote grid migration deferred dimension={} gridAnchor={} reason={}",
                        dimensionKey(level), gridAnchor, relocation.reason()
                    );
                }
            }
        } else {
            DungeonInstanceSpatialLayout.Result remote = planRemoteDungeonGrid(
                level, player, hubReference
            );
            if (!remote.valid()) {
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: remote instance layout failed: " + remote.reason()
                ));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
            gridAnchor = remote.gridAnchor();
            LOGGER.info(
                "Dungeon remote grid established dimension={} hubOrigin={} gridAnchor={} reason={}",
                dimensionKey(level), hubReference, gridAnchor, remote.reason()
            );
        }
        int targetRooms = DungeonRunService.roomTargetForFloor(requestedFloor);
        DungeonInstanceAllocationData.ReservationResult result =
            allocations.requestSoloPreparation(
                dimensionKey(level),
                player.getUUID(),
                gridAnchor,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight(),
                List.of(),
                requestedFloor,
                targetRooms,
                REMOTE_PREPARATION_DIRECTION,
                participants,
                level.getGameTime()
            );
        if (!result.accepted() || result.allocation() == null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: preparation rejected: " + result.reason()
            ));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        if (result.created()) {
            GeneratedFloorCleanupProcessor.reconcileNewAllocationFogPanels(
                level, result.allocation()
            );
        }
        emitSoloTeleporterStatus(source, result.allocation(), allocations);
        source.sendSuccess(() -> Component.literal(
            "Dispatch: BLOCKED_UNTIL_TWO_ROOMS_VERIFIED"
        ), false);
        return beginSoloTeleporterStartup(
            source, player, result.allocation(), planningOverride
        );
    }

    private static DungeonInstanceSpatialLayout.Result planRemoteDungeonGrid(
        ServerLevel level,
        ServerPlayer player,
        BlockPos hubReference
    ) {
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        return DungeonInstanceSpatialLayout.planRemoteGrid(
            hubReference,
            level.getMinBuildHeight(),
            level.getMaxBuildHeight(),
            border.getMinX(),
            border.getMaxX(),
            border.getMinZ(),
            border.getMaxZ(),
            level.getRandom().nextLong() ^ player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits()
        );
    }

    private static boolean frozenParticipantsCompatible(
        List<DungeonInstanceAllocationData.FrozenParticipant> frozen,
        List<DungeonInstanceAllocationData.FrozenParticipant> requested
    ) {
        if (frozen.equals(requested)) return true;
        if (frozen.size() != requested.size()
            || requested.stream().anyMatch(participant -> participant.loadoutRevision() != -1L)) {
            return false;
        }
        for (int index = 0; index < frozen.size(); index++) {
            DungeonInstanceAllocationData.FrozenParticipant left = frozen.get(index);
            DungeonInstanceAllocationData.FrozenParticipant right = requested.get(index);
            if (!left.id().equals(right.id()) || left.kind() != right.kind()
                || !left.ownerId().equals(right.ownerId()) || left.slot() != right.slot()) {
                return false;
            }
        }
        return true;
    }

    private static int soloTeleporterStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        DungeonInstanceAllocationData allocations =
            DungeonInstanceAllocationData.get(player.serverLevel());
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forPlayer(player.getUUID()).orElse(null);
        if (allocation == null) {
            source.sendSuccess(() -> Component.literal(
                "Dungeon Teleporter: IDLE | no solo preparation"
            ), false);
            return Command.SINGLE_SUCCESS;
        }
        emitSoloTeleporterStatus(source, allocation, allocations);
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelSoloTeleporter(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        DungeonInstanceAllocationData.CancellationResult result =
            DungeonInstanceAllocationData.get(player.serverLevel())
                .cancelSoloPreparation(player.getUUID(), player.serverLevel().getGameTime());
        if (!result.canceled()) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: cancellation rejected: " + result.reason()
            ));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "Dungeon Teleporter: preparation canceled before world mutation."
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void emitSoloTeleporterStatus(
        CommandSourceStack source,
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonInstanceAllocationData allocations
    ) {
        DungeonInstanceAllocationData.SoloPreparation preparation =
            allocation.soloPreparation();
        long liveCount = allocations.allocations().stream()
            .filter(candidate -> !candidate.lifecycle().state().reusable())
            .count();
        source.sendSuccess(() -> Component.literal(
            "Preparing Dungeon: instance=" + formatInstanceKey(allocation.key())
                + " | cell=" + allocation.cell().index()
                + " | state=" + allocation.lifecycle().state()
                + " | capacity=" + liveCount + "/"
                + DungeonInstanceAllocationData.MAX_CONCURRENT_SOLO_INSTANCES
        ), false);
        if (preparation == null) {
            source.sendSuccess(() -> Component.literal(
                "Preparation Plan: NOT_REQUESTED"
            ), false);
            return;
        }
        source.sendSuccess(() -> Component.literal(
            "Preparation Plan: floor=" + preparation.requestedFloor()
                + " | rooms=" + preparation.targetRooms()
                + " | direction=" + preparation.entryDirection()
                + " | attachment=" + formatPos(preparation.attachmentTarget())
                + " | seed=" + preparation.planSeed()
        ), false);
        if (allocation.entryDispatch() != null) {
            source.sendSuccess(() -> Component.literal(
                "Entry Dispatch: state=" + allocation.entryDispatch().state()
                    + " | controller=" + formatPos(allocation.entryDispatch().entryController())
                    + " | destination=" + formatPos(allocation.entryDispatch().entryDestination())
            ), false);
        }
    }

    private static int beginSoloTeleporterStartup(
        CommandSourceStack source,
        ServerPlayer player,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        return beginSoloTeleporterStartup(source, player, allocation, null);
    }

    private static int beginSoloTeleporterStartup(
        CommandSourceStack source,
        ServerPlayer player,
        DungeonInstanceAllocationData.Allocation allocation,
        CompositePlanningOverride planningOverride
    ) {
        DungeonInstanceAllocationData.SoloPreparation preparation =
            allocation.soloPreparation();
        if (preparation == null || allocation.lifecycle().state()
            != DungeonInstanceAllocationData.LifecycleState.ALLOCATED) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: allocation is not eligible for startup planning."
            ));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        List<LoadedPlannedRoom> plan = buildConnectorPlan(
            source,
            level,
            preparation.attachmentTarget(),
            preparation.entryDirection(),
            preparation.requestedFloor(),
            preparation.targetRooms(),
            DEFAULT_ROOM_SPACING,
            preparation.planSeed(),
            player,
            planningOverride
        );
        if (plan.size() < 2 || !plan.getFirst().safeRolled()) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: startup planning failed to produce a safe room and successor."
            ));
            return 0;
        }
        String dimension = dimensionKey(level);
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        for (LoadedPlannedRoom planned : plan) {
            String validation = allocations.validateOwnedBounds(
                dimension, allocation.key(), planned.bounds()
            );
            if (!"VALID_INSTANCE_CELL_BOUNDS".equals(validation)) {
                allocations.quarantine(
                    allocation.key(),
                    "SOLO_STARTUP_PLAN_" + validation,
                    level.getGameTime()
                );
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: planned room escaped its instance cell: " + validation
                ));
                return 0;
            }
        }
        String visualTheme = visualThemeForPlan(level, plan);
        if (visualTheme.isBlank()) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: startup plan has no exclusive visual theme."
            ));
            return 0;
        }

        GeneratedFloorRepository repository = GeneratedFloorRepository.get(
            level, dimension, allocation.key()
        );
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (!repository.initializeInstance() || !manager.initializeInstance(allocation.key())) {
            allocations.quarantine(
                allocation.key(), "SOLO_STARTUP_OWNER_INITIALIZATION_FAILED", level.getGameTime()
            );
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: explicit instance owners could not be initialized."
            ));
            return 0;
        }
        GeneratedFloorSession session = repository.hydrate();
        if (!allocation.key().equals(session.instanceKey())) {
            allocations.quarantine(
                allocation.key(), "SOLO_STARTUP_GENERATED_OWNER_MISMATCH", level.getGameTime()
            );
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: generated owner did not retain the allocation identity."
            ));
            return 0;
        }

        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> roomPlans =
            buildGeneratedRoomRuntimePlans(plan);
        var authoredPool =
            com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        var mechanicalPool =
            com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry
                .getForTheme(level, authoredPool, visualTheme);
        var generationContract =
            com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver
                .currentGenerationContract(authoredPool, mechanicalPool);
        var composition =
            com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer.compose(
                preparation.requestedFloor(),
                preparation.targetRooms(),
                preparation.planSeed()
            );

        session.replacePlacement(new GeneratedFloorRuntimeData.LastGeneratedPlacement(
            preparation.attachmentTarget(),
            List.of(plan.getFirst().bounds()),
            List.of(),
            List.copyOf(roomPlans),
            DEFAULT_CLAIM_RADIUS,
            visualTheme
        ));
        session.initializeComposition(composition, roomPlans.size(), generationContract);
        repository.flushPlacement(session);
        repository.flushComposition(session);

        List<RoomControllerBlockEntity.RoomBounds> staleBounds = plan.subList(0, 2).stream()
            .map(LoadedPlannedRoom::bounds)
            .filter(bounds -> countRoomControllers(level, bounds) > 0)
            .toList();
        if (!staleBounds.isEmpty()) {
            session.replaceCleanupState(new GeneratedFloorRuntimeData.CleanupState(
                preparation.attachmentTarget().immutable(),
                List.copyOf(staleBounds),
                0, 0L, 0L, 0L,
                level.getGameTime()
            ));
            repository.flushCleanup(session);
            if (!allocations.transition(
                allocation.key(),
                DungeonInstanceAllocationData.LifecycleState.CLEANING,
                "STALE_REUSED_CELL_STARTUP_CLEANUP",
                level.getGameTime()
            )) {
                allocations.quarantine(
                    allocation.key(), "STALE_REUSED_CELL_CLEANUP_TRANSITION_FAILED", level.getGameTime()
                );
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: stale reused-cell cleanup could not start; the cell was quarantined."
                ));
                return 0;
            }
            level.getServer().overworld().getDataStorage().save();
            source.sendSuccess(() -> Component.literal(
                "Preparing Dungeon: stale geometry was found in the reusable cell; bounded cleanup has started and preparation will resume automatically."
            ), false);
            return Command.SINGLE_SUCCESS;
        }

        allocations.transition(
            allocation.key(),
            DungeonInstanceAllocationData.LifecycleState.PREPARING,
            "SOLO_SAFE_ROOM_PLACEMENT",
            level.getGameTime()
        );
        level.getServer().overworld().getDataStorage().save();

        GeneratedFloorWorldWorkScheduler.requestCriticalStartupBootstrap(
            level, allocation.key(), roomPlans.getFirst().controllerPos()
        );
        source.sendSuccess(() -> Component.literal(
            "Preparing Dungeon: safe room and connected successor queued under shared pacing."
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Dispatch: BLOCKED_UNTIL_TWO_ROOMS_VERIFIED"
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    static int planLinearFloor(CommandContext<CommandSourceStack> context, int spacing) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        int rooms = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rooms");
        ServerLevel level = player.serverLevel();
        BlockPos origin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);

        source.sendSuccess(() -> Component.literal("Generated Linear Floor Plan"), false);
        source.sendSuccess(() -> Component.literal("Requested Rooms: " + rooms), false);
        source.sendSuccess(() -> Component.literal("Room Spacing Fallback: " + spacing), false);

        if (origin == null) {
            source.sendFailure(Component.literal("Generated Linear Plan: FAIL - floor_build_origin marker missing within " + ORIGIN_SCAN_RADIUS + " blocks"));
            source.sendFailure(Component.literal("Place floor_build_origin just outside the hub gate, then rerun this command while facing the intended floor direction."));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }

        GeneratedSpatialConfiguration configuration = resolveGeneratedSpatialConfiguration(level, origin);
        if (!configuration.valid()) {
            return emitGeneratedSpatialConfigurationFailure(source, "Generated Linear Plan", configuration.error());
        }
        Direction direction = configuration.direction();
        source.sendSuccess(() -> Component.literal("Plan Direction: " + direction), false);
        source.sendSuccess(() -> Component.literal("Plan Mutates World: NO"), false);
        source.sendSuccess(() -> Component.literal("Placement Mode: CONNECTOR_AWARE"), false);
        source.sendSuccess(() -> Component.literal("Floor Build Origin: " + formatPos(origin)), false);
        BlockPos attachmentTarget = configuration.attachmentTarget();
        List<LoadedPlannedRoom> plan = buildConnectorPlan(source, level, attachmentTarget, direction, rooms, spacing);
        if (plan.isEmpty()) {
            source.sendFailure(Component.literal("Generated Linear Plan: FAIL - connector plan could not be built"));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }

        for (LoadedPlannedRoom room : plan) {
            source.sendSuccess(() -> Component.literal(room.index() + ". template=" + room.template().resourceId()
                + " | rotation=" + room.rotation()
                + " | placementOrigin=" + formatPos(room.placementOrigin())
                + " | targetConnector=" + formatPos(room.targetConnectorWorldPos())
                + " | selectedConnector=" + room.selectedConnector().summary()
                + " | selectedConnectorWorld=" + formatPos(room.selectedConnectorWorldPos())
                + " | outgoing=" + room.outgoingSummary()
                + " | outgoingChoices=" + room.outgoingConnectorCount()
                + " | playerDirected=" + (room.outgoingConnectorCount() > 1)
                + " | bounds=" + formatBounds(room.bounds())
                + " | layout=" + room.layoutMetrics().summary()), false);
        }

        source.sendSuccess(() -> Component.literal("Generated Linear Plan: PASS"), false);
        source.sendSuccess(() -> Component.literal("Next: /tensuradungeon floor generated place <rooms> [spacing] [claimRadius]"), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int dispatchReadySoloTeleporter(
        CommandSourceStack source,
        ServerPlayer player,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        ServerLevel level = player.serverLevel();
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation live =
            allocations.forPlayer(player.getUUID()).orElse(null);
        if (live == null || !live.key().equals(allocation.key())
            || !live.roster().equals(java.util.Set.of(player.getUUID()))) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: solo allocation ownership changed before dispatch."
            ));
            return 0;
        }
        if (live.entryDispatch() != null
            && live.entryDispatch().state() == DungeonInstanceAllocationData.DispatchState.ADMITTED
            && live.lifecycle().state() == DungeonInstanceAllocationData.LifecycleState.ACTIVE) {
            source.sendSuccess(() -> Component.literal(
                "Dungeon Teleporter: ACTIVE | existing dispatch retained."
            ), false);
            return Command.SINGLE_SUCCESS;
        }

        String dimension = dimensionKey(level);
        GeneratedFloorSession session =
            GeneratedFloorRepository.get(level, dimension, live.key()).hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().size() < 2
            || placement.controllers().size() < 2
            || session.cleanupState() != null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: dispatch blocked because startup ownership is no longer ready."
            ));
            return 0;
        }

        DungeonRunManager manager = DungeonRunManager.get(level);
        GeneratedFloorLazyPlacementService.PlacementResult contractValidation =
            GeneratedFloorLazyPlacementService.validateReadyDispatchContract(
                level, live.key(), placement.roomPlans().getFirst().controllerPos()
            );
        if (contractValidation.failed()) {
            boolean expired = manager.expireUndispatchedReadyInstance(
                live.key(), level, "READY_GENERATION_CONTRACT_EXPIRED"
            );
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: this prepared dungeon expired after the available room pool changed. "
                    + (expired
                        ? "Cleanup has started; prepare a new dungeon after it finishes."
                        : "Automatic cleanup could not start; use the dungeon reset command before retrying.")
            ));
            return 0;
        }
        if (session.generationFailure() != null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: dispatch is blocked by unresolved generation failure "
                    + session.generationFailure().code() + "."
            ));
            return 0;
        }

        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> startupPlans =
            placement.roomPlans().subList(0, 2);
        for (GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan : startupPlans) {
            if (!placement.controllers().contains(roomPlan.controllerPos())
                || !(level.getBlockEntity(roomPlan.controllerPos())
                    instanceof RoomControllerBlockEntity controller)
                || !controller.matchesDungeonInstance(live.key())
                || roomPlan.bounds() == null
                || !roomPlan.bounds().equals(controller.protectionView(level).bounds())) {
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: dispatch blocked by controller or bounds ownership mismatch at "
                        + formatPos(roomPlan.controllerPos()) + "."
                ));
                return 0;
            }
            GeneratedRoomPlacementOperation.SealResult seal =
                GeneratedRoomPlacementOperation.repairRuntimeOutgoingSeals(
                    level, roomPlan, controller
                );
            if (!seal.success()) {
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: dispatch blocked by unverified loading seal at "
                        + formatPos(roomPlan.controllerPos()) + ": " + seal.reason()
                ));
                return 0;
            }
        }

        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan safePlan = startupPlans.getFirst();
        if (!safePlan.safeRolled() || !"SAFE_ROOM".equals(safePlan.effectiveProfile())) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: first prepared room is not the required safe entry room."
            ));
            return 0;
        }
        BlockPos entryController = safePlan.controllerPos();
        if (!(level.getBlockEntity(entryController)
            instanceof RoomControllerBlockEntity safeController)) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: safe-room controller disappeared before entry routing."
            ));
            return 0;
        }
        safeController.scanMarkers(level);
        BlockPos exitAnchor = resolveClaimedAuthoredExitAnchor(
            level, safePlan, safeController, live.cell().bounds()
        );
        if (exitAnchor == null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: safe room has no verified exit_anchor inside its instance cell."
            ));
            return 0;
        }
        BlockPos entryDestination = exitAnchor.above();
        if (!live.cell().bounds().contains(entryDestination)
            || !live.cell().bounds().contains(entryDestination.above())
            || !level.getBlockState(entryDestination).isAir()
            || !level.getBlockState(entryDestination.above()).isAir()) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: safe-room exit_anchor entry checkpoint is obstructed."
            ));
            return 0;
        }
        level.getChunkAt(entryDestination);
        if (!level.hasChunkAt(entryDestination)) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: destination chunk is not ready."
            ));
            return 0;
        }

        BlockPos hubReturnTarget = manager.getHubReturnTarget(live.key());
        if (hubReturnTarget == null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: authoritative hub return anchor is missing."
            ));
            return 0;
        }
        DungeonInstanceAllocationData.DispatchResult checkpoint =
            allocations.prepareSoloDispatch(
                live.key(), entryController, entryDestination, hubReturnTarget,
                level.getGameTime()
            );
        if (!checkpoint.accepted() || checkpoint.allocation() == null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: dispatch checkpoint rejected: " + checkpoint.reason()
            ));
            return 0;
        }
        level.getServer().overworld().getDataStorage().save();

        if (!manager.isActiveRun(live.key()) || manager.isFailed(live.key())) {
            List<BlockPos> order = placement.roomPlans().stream()
                .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::controllerPos)
                .toList();
            List<BlockPos> available = List.copyOf(placement.controllers());
            List<BlockPos> safeControllers = placement.roomPlans().stream()
                .filter(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::safeRolled)
                .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::controllerPos)
                .toList();
            DungeonRunService.GeneratedFloorArmOperationResult activation =
                manager.armGeneratedFloor(
                    live.key(),
                    level,
                    player.getUUID(),
                    new DungeonRunService.GeneratedFloorArmPlan(
                        live.soloPreparation().requestedFloor(),
                        live.soloPreparation().targetRooms(),
                        session.compositionState() != null,
                        order,
                        available,
                        available,
                        safeControllers
                    )
                );
            if (!activation.succeeded()) {
                source.sendFailure(Component.literal(
                    "Dungeon Teleporter: exact instance run activation failed: "
                        + activation.reason()
                ));
                return 0;
            }
        }
        if (!allocations.transition(
            live.key(),
            DungeonInstanceAllocationData.LifecycleState.ACTIVE,
            "SOLO_RUN_ACTIVATED_AWAITING_TELEPORT",
            level.getGameTime()
        )) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: allocation could not enter ACTIVE dispatch state."
            ));
            return 0;
        }
        level.getServer().overworld().getDataStorage().save();

        DungeonInstanceAllocationData.Allocation activeAllocation =
            allocations.forIdentity(live.key()).orElse(null);
        if (activeAllocation == null) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: active allocation disappeared before participant transport."
            ));
            return 0;
        }
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        DungeonSubordinateTransportData.TransportResult hubHold =
            transport.holdNonParticipantFollowersAtHub(level, activeAllocation);
        if (!hubHold.accepted()) {
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: nonparticipant Hub containment rejected: "
                    + hubHold.reason()
            ));
            return 0;
        }
        DungeonSubordinateTransportData.TransportResult subordinateEntry =
            DungeonSubordinateTransportData.get(level).enter(
                level, activeAllocation, entryDestination
            );
        if (!subordinateEntry.accepted()) {
            transport.releaseHubHolds(level, activeAllocation.key());
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: subordinate entry rejected: " + subordinateEntry.reason()
            ));
            return 0;
        }

        DungeonInstanceAllocationData.DispatchResult admitted =
            allocations.markSoloDispatched(live.key(), level.getGameTime());
        if (!admitted.accepted()) {
            transport.returnToHub(level, activeAllocation);
            transport.releaseHubHolds(level, activeAllocation.key());
            source.sendFailure(Component.literal(
                "Dungeon Teleporter: dispatch admission persistence failed; subordinate entry was rolled back: "
                    + admitted.reason()
            ));
            return 0;
        }
        player.teleportTo(
            level,
            entryDestination.getX() + 0.5D,
            entryDestination.getY(),
            entryDestination.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot()
        );
        transport.restoreEnteredFollowers(level, activeAllocation);
        level.getServer().overworld().getDataStorage().save();
        source.sendSuccess(() -> Component.literal(
            "Dungeon Teleporter: ACTIVE | dispatched to remote safe room "
                + formatPos(entryDestination) + " | hub doorway unchanged."
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos resolveClaimedAuthoredExitAnchor(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan safePlan,
        RoomControllerBlockEntity safeController,
        DungeonInstanceAllocationData.CellBounds cellBounds
    ) {
        if (safePlan.placementOrigin() == null || safePlan.templateLabel().isBlank()) {
            return null;
        }
        Rotation rotation;
        try {
            rotation = Rotation.valueOf(safePlan.rotation());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        StructureTemplate template = loadTemplate(
            level,
            ResourceLocation.fromNamespaceAndPath(
                TensuraDungeonRuntime.MOD_ID,
                "rooms/linear/" + safePlan.templateLabel()
            )
        );
        if (template == null) {
            return null;
        }
        StructurePlaceSettings settings = placeSettings(rotation);
        List<BlockPos> authoredExitAnchors = template
            .filterBlocks(BlockPos.ZERO, settings, ModBlocks.EXIT_ANCHOR.get())
            .stream()
            .map(info -> safePlan.placementOrigin().offset(info.pos()))
            .filter(cellBounds::contains)
            .filter(anchor -> safeController.getExitAnchors().contains(anchor))
            .sorted(
                Comparator.comparingDouble(
                    (BlockPos anchor) -> anchor.distSqr(safePlan.controllerPos())
                ).thenComparingLong(BlockPos::asLong)
            )
            .toList();
        return authoredExitAnchors.isEmpty() ? null : authoredExitAnchors.getFirst();
    }

    static int placeLinearFloor(CommandContext<CommandSourceStack> context, int spacing, int claimRadius) {
        return placeLinearFloor(context, spacing, claimRadius, false, null, 0, 0);
    }

    private static int placeLinearFloor(
        CommandContext<CommandSourceStack> context,
        int spacing,
        int claimRadius,
        boolean holdHubGateClosedForStart,
        ServerPlayer targetPlayer,
        int floorOverride,
        int roomOverride
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = targetPlayer == null ? getPlayer(source) : targetPlayer;
        if (player == null) {
            return 0;
        }

        long commandStartedAt = System.nanoTime();
        int rooms = roomOverride > 0
            ? roomOverride
            : com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rooms");
        ServerLevel level = player.serverLevel();
        int floor = floorOverride > 0
            ? floorOverride
            : Math.max(1, DungeonRunManager.get(level).getFloorNumber());
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(level);
        GeneratedFloorSession session = repository.hydrate();
        if (session.hasPendingCleanup()) {
            emitCleanupLockout(source, session.cleanupState());
            return 0;
        }
        if (session.placement() != null) {
            source.sendFailure(Component.literal(
                "Generated Linear Placement: FAIL - a stored generated floor already exists. "
                    + "Return through the exit flow or run /tensuradungeon floor generated clear last first."
            ));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        long phaseStartedAt = System.nanoTime();
        BlockPos origin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        boolean restoreOrigin = false;
        String recoveredOriginSource = "NONE";

        source.sendSuccess(() -> Component.literal("Generated Linear Floor Placement"), false);
        source.sendSuccess(() -> Component.literal("Requested Rooms: " + rooms), false);
        source.sendSuccess(() -> Component.literal("Room Spacing Fallback: " + spacing), false);
        source.sendSuccess(() -> Component.literal("Claim Radius: " + claimRadius), false);

        if (origin == null) {
            ResolvedGeneratedOrigin recoveredOrigin = resolveGeneratedOrigin(level, player.blockPosition());
            if (recoveredOrigin.origin() == null) {
                source.sendFailure(Component.literal("Generated Linear Placement: FAIL - floor_build_origin marker missing within " + ORIGIN_SCAN_RADIUS + " blocks"));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
            if (!recoveredOrigin.validForMutation()) {
                source.sendFailure(Component.literal("Generated Linear Placement: FAIL - recovered floor_build_origin is unsafe for mutation: " + recoveredOrigin.validation()));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
            if (!level.getBlockState(recoveredOrigin.origin()).isAir()) {
                source.sendFailure(Component.literal(
                    "Generated Linear Placement: FAIL - legacy orphaned floor detected at the recovered "
                        + "floor_build_origin, but no crash-durable placement manifest is available."
                ));
                source.sendFailure(Component.literal(
                    "State Mutated: NO - preserve the world and use bounded operator cleanup; unknown blocks "
                        + "will not be deleted automatically."
                ));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
            origin = recoveredOrigin.origin().immutable();
            restoreOrigin = true;
            recoveredOriginSource = recoveredOrigin.source();
        }

        GeneratedSpatialConfiguration configuration = resolveGeneratedSpatialConfiguration(level, origin);
        if (!configuration.valid()) {
            return emitGeneratedSpatialConfigurationFailure(source, "Generated Linear Placement", configuration.error());
        }
        Direction resolvedDirection = configuration.direction();
        BlockPos attachmentTarget = configuration.attachmentTarget();
        source.sendSuccess(() -> Component.literal("Placement Direction: " + resolvedDirection), false);
        source.sendSuccess(() -> Component.literal("Placement Mode: CONNECTOR_AWARE"), false);
        HubGateOperationResult preparationGateClose = null;
        if (holdHubGateClosedForStart) {
            preparationGateClose = closeHubGateForGeneratedFloor(level, origin, "dungeon_checkpoint");
            emitHubGateOperation(source, "Hub Gate Preparation Close", preparationGateClose);
            if (!preparationGateClose.valid()) {
                source.sendFailure(Component.literal("Generated Linear Placement: FAIL - required hub gate could not be held closed before construction."));
                return 0;
            }
        }
        double originResolutionMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        List<LoadedPlannedRoom> plan = buildConnectorPlan(
            source, level, attachmentTarget, resolvedDirection, floor, rooms, spacing, player
        );
        double planBuildMs = elapsedMillis(phaseStartedAt);
        if (plan.isEmpty()) {
            source.sendFailure(Component.literal("Generated Linear Placement: FAIL - preflight failed"));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        String visualTheme = visualThemeForPlan(level, plan);
        if (visualTheme.isBlank()) {
            source.sendFailure(Component.literal(
                "Generated Linear Placement: FAIL - solved plan did not carry one exclusive visual theme."
            ));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Selected Visual Theme: " + visualTheme), false);
        var authoredPool = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        var mechanicalPool = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry.getForTheme(
            level, authoredPool, visualTheme
        );
        var generationContract = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver
            .currentGenerationContract(authoredPool, mechanicalPool);

        int requiredInitialRooms = initialLazyRoomCount(plan);
        int initialLazyRoomCount = holdHubGateClosedForStart
            ? Math.min(1, requiredInitialRooms)
            : requiredInitialRooms;
        List<LoadedPlannedRoom> initialRooms = plan.subList(0, initialLazyRoomCount);
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> roomPlans = buildGeneratedRoomRuntimePlans(plan);
        phaseStartedAt = System.nanoTime();
        for (LoadedPlannedRoom room : initialRooms) {
            int existingControllers = countRoomControllers(level, room.bounds());
            if (existingControllers > 0) {
                source.sendFailure(Component.literal(room.index() + ". Preflight FAIL: target bounds already contain " + existingControllers + " room_controller block(s). Run /tensuradungeon floor generated clear last or manually clear the generated area first. Bounds=" + formatBounds(room.bounds())));
                source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
                return 0;
            }
        }
        double occupancyPreflightMs = elapsedMillis(phaseStartedAt);

        DungeonInstanceAllocationData allocationData = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.ReservationResult reservation = allocationData.reserveSingle(
            dimensionKey(level),
            player.getUUID(),
            origin,
            level.getMinBuildHeight(),
            level.getMaxBuildHeight(),
            plan.stream().map(LoadedPlannedRoom::bounds).toList(),
            level.getGameTime()
        );
        if (!reservation.accepted() || reservation.allocation() == null) {
            source.sendFailure(Component.literal(
                "Generated Linear Placement: FAIL - dungeon instance cell reservation rejected: "
                    + reservation.reason()
            ));
            source.sendSuccess(() -> Component.literal("State Mutated: NO_GENERATED_STRUCTURE"), false);
            return 0;
        }
        DungeonInstanceAllocationData.Allocation allocation = reservation.allocation();
        if (reservation.created()) {
            GeneratedFloorCleanupProcessor.reconcileNewAllocationFogPanels(level, allocation);
        }
        if (!DungeonInstanceAllocationData.bindExistingOwners(level, allocation, session.placement())) {
            allocationData.quarantine(
                allocation.key(),
                "INSTANCE_OWNER_BINDING_MISMATCH",
                level.getGameTime()
            );
            source.sendFailure(Component.literal(
                "Generated Linear Placement: FAIL - existing dungeon owners could not be bound to the reserved instance."
            ));
            source.sendSuccess(() -> Component.literal("State Mutated: NO_GENERATED_STRUCTURE"), false);
            return 0;
        }
        session = repository.hydrate();
        allocationData.transition(
            allocation.key(),
            DungeonInstanceAllocationData.LifecycleState.PREPARING,
            "GENERATED_PLACEMENT_PREPARING",
            level.getGameTime()
        );
        source.sendSuccess(() -> Component.literal(
            "Dungeon Instance: id=" + allocation.key().instanceId()
                + " | epoch=" + allocation.key().epoch()
                + " | cell=" + allocation.cell().index()
                + " | state=PREPARING"
        ), false);

        GeneratedFloorRuntimeData.LastGeneratedPlacement recoveryCheckpoint =
            new GeneratedFloorRuntimeData.LastGeneratedPlacement(
                origin.immutable(),
                initialRooms.stream().map(LoadedPlannedRoom::bounds).toList(),
                List.of(),
                List.copyOf(roomPlans),
                claimRadius,
                visualTheme
            );
        session.replacePlacement(recoveryCheckpoint);
        repository.flushPlacement(session);
        level.getServer().overworld().getDataStorage().save();

        if (restoreOrigin) {
            level.setBlock(origin, ModBlocks.FLOOR_BUILD_ORIGIN.get().defaultBlockState(), 3);
            final String recoveredOriginText = formatPos(origin);
            final String recoveredSource = recoveredOriginSource;
            source.sendSuccess(() -> Component.literal("Recovered Floor Build Origin: " + recoveredOriginText + " | source=" + recoveredSource), false);
        }
        source.sendSuccess(() -> Component.literal("Generated Linear Preflight: PASS"), false);
        final BlockPos placementOriginForDisplay = origin;
        source.sendSuccess(() -> Component.literal("Floor Build Origin: " + formatPos(placementOriginForDisplay)), false);

        List<BlockPos> placedControllers = new ArrayList<>();
        List<RoomControllerBlockEntity.RoomBounds> placedBounds = new ArrayList<>();
        boolean allPlaced = true;

        double structurePlacementTotalMs = 0.0D;
        double itemCleanupTotalMs = 0.0D;
        double controllerScanTotalMs = 0.0D;
        double boundsWriteTotalMs = 0.0D;
        double markerClaimTotalMs = 0.0D;
        double readinessTotalMs = 0.0D;
        double roomPostProcessTotalMs = 0.0D;
        double markerDiagnosticsTotalMs = 0.0D;
        double roomPipelineTotalMs = 0.0D;
        for (LoadedPlannedRoom planned : initialRooms) {
            long roomStartedAt = System.nanoTime();
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan runtimePlan = roomPlans.stream()
                .filter(candidate -> candidate.index() == planned.index())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing runtime plan for room " + planned.index()));
            GeneratedRoomPlacementOperation.Result placementResult = GeneratedRoomPlacementOperation.place(
                level,
                repository,
                session,
                new GeneratedRoomPlacementOperation.Request(
                    runtimePlan,
                    planned.structureTemplate(),
                    claimRadius,
                    false,
                    GeneratedRoomPlacementOperation.SealPolicy.INITIAL_PRESERVE_INCOMING_FACE,
                    GeneratedRoomPlacementOperation.ControllerDiscoveryPolicy.EXPECTED_POSITION,
                    false,
                    false,
                    false,
                    false,
                    planned.index() == 1 ? origin : null
                )
            );
            GeneratedRoomPlacementOperation.Metrics metrics = placementResult.metrics();
            structurePlacementTotalMs += metrics.structurePlaceMs();
            itemCleanupTotalMs += metrics.itemCleanupMs();
            controllerScanTotalMs += metrics.controllerLookupMs();
            boundsWriteTotalMs += metrics.boundsWriteMs();
            markerClaimTotalMs += metrics.markerClaimMs();
            readinessTotalMs += metrics.readinessMs();
            roomPostProcessTotalMs += metrics.postProcessMs();

            boolean roomPass = placementResult.placed();
            allPlaced &= roomPass;
            placedBounds.add(planned.bounds());
            if (placementResult.controllerPos() != null) {
                placedControllers.add(placementResult.controllerPos());
            }

            if (placementResult.failed() && placementResult.controllerPos() == null) {
                if ("STRUCTURE_PLACE_RETURNED_FALSE".equals(placementResult.reason())) {
                    source.sendFailure(Component.literal(planned.index() + ". Placement FAIL: template="
                        + planned.template().resourceId() + " | origin=" + formatPos(planned.placementOrigin())
                        + " | rotation=" + planned.rotation()));
                } else if (placementResult.reason().startsWith("EXPECTED_SINGLE_CONTROLLER_FOUND_")) {
                    String count = placementResult.reason().substring("EXPECTED_SINGLE_CONTROLLER_FOUND_".length());
                    source.sendFailure(Component.literal(planned.index()
                        + ". Placement FAIL: expected exactly one room_controller after placement, found " + count
                        + " | bounds=" + formatBounds(planned.bounds())));
                } else {
                    source.sendFailure(Component.literal(planned.index() + ". Placement FAIL: "
                        + placementResult.reason() + " | template=" + planned.template().resourceId()
                        + " | bounds=" + formatBounds(planned.bounds())));
                }
                double roomPipelineMs = elapsedMillis(roomStartedAt);
                roomPipelineTotalMs += roomPipelineMs;
                LOGGER.warn(
                    "[TD-PERF] generated-initial-room result=FAIL roomIndex={} template={} reason={} structurePlaceMs={} itemCleanupMs={} controllerScanMs={} configurationMs={} boundsWriteMs={} markerClaimMs={} readinessMs={} postProcessMs={} totalMs={} volume={} claimRadius={}",
                    planned.index(), planned.template().resourceId(), placementResult.reason(),
                    metrics.structurePlaceMs(), metrics.itemCleanupMs(), metrics.controllerLookupMs(),
                    metrics.configurationMs(), metrics.boundsWriteMs(), metrics.markerClaimMs(),
                    metrics.readinessMs(), metrics.postProcessMs(), roomPipelineMs,
                    planned.layoutMetrics().volume(), claimRadius
                );
                break;
            }

            if (placementResult.sealResult() != null && !placementResult.sealResult().success()) {
                source.sendFailure(Component.literal(planned.index()
                    + ". Pre-activation seal FAIL: one or more outward openings were not sealed; hub gate will remain closed."
                    + " reason=" + placementResult.sealResult().reason()));
            }

            var claimResult = placementResult.claimResult();
            var readiness = claimResult == null ? null : claimResult.readinessReport();
            source.sendSuccess(() -> Component.literal(planned.index() + ". Placement PASS: template="
                + planned.template().resourceId()
                + " | rotation=" + planned.rotation()
                + " | origin=" + formatPos(planned.placementOrigin())
                + " | targetConnector=" + formatPos(planned.targetConnectorWorldPos())
                + " | selectedConnector=" + planned.selectedConnector().summary()
                + " | selectedConnectorWorld=" + formatPos(planned.selectedConnectorWorldPos())
                + " | outgoing=" + planned.outgoingSummary()
                + " | outgoingChoices=" + planned.outgoingConnectorCount()
                + " | playerDirected=" + (planned.outgoingConnectorCount() > 1)
                + " | controller=" + formatPos(placementResult.controllerPos())
                + " | removedPlacementDrops=" + placementResult.removedPlacementDrops()
                + " | bounds=" + formatBounds(planned.bounds())
                + " | layout=" + planned.layoutMetrics().summary()), false);
            if (claimResult != null) {
                source.sendSuccess(() -> Component.literal("   Generated Bounds Write: "
                    + (claimResult.boundsResult() != null && claimResult.boundsResult().success() ? "PASS" : "FAIL")
                    + " | reason=" + (claimResult.boundsResult() == null ? "NONE" : claimResult.boundsResult().reason())), false);
                if (claimResult.claimResult() != null) {
                    source.sendSuccess(() -> Component.literal("   Generated Bounds Claim: PASS | claimed="
                        + claimResult.claimResult().totalClaimed()
                        + " | skippedOutside=" + claimResult.claimResult().skippedOutsideBounds()
                        + " | skippedOwned=" + claimResult.claimResult().skippedOwnedByOther()
                        + " | summary=" + claimResult.claimResult().summary()), false);
                }
            }
            if (readiness != null) {
                source.sendSuccess(() -> Component.literal("   Structure Readiness: " + readiness.readinessLabel()
                    + " | Jigsaw: " + readiness.jigsawTemplateReadinessLabel()
                    + " | Missing: " + readiness.missingSummary()
                    + " | Warnings: " + readiness.warningsSummary()), false);
            }

            phaseStartedAt = System.nanoTime();
            final boolean finalRoomPass = roomPass;
            final int preservedConnectorMarkers = finalRoomPass
                ? countGeneratedConnectorMarkers(level, planned.bounds()) : 0;
            double markerDiagnosticsMs = elapsedMillis(phaseStartedAt);
            markerDiagnosticsTotalMs += markerDiagnosticsMs;
            double roomPipelineMs = elapsedMillis(roomStartedAt);
            roomPipelineTotalMs += roomPipelineMs;
            LOGGER.info(
                "[TD-PERF] generated-initial-room result={} roomIndex={} template={} reason={} structurePlaceMs={} itemCleanupMs={} controllerScanMs={} configurationMs={} boundsWriteMs={} markerClaimMs={} readinessMs={} postProcessMs={} markerDiagnosticsMs={} totalMs={} volume={} claimRadius={}",
                finalRoomPass ? "PASS" : "READINESS_OR_SEAL_FAILED", planned.index(),
                planned.template().resourceId(), placementResult.reason(), metrics.structurePlaceMs(),
                metrics.itemCleanupMs(), metrics.controllerLookupMs(), metrics.configurationMs(),
                metrics.boundsWriteMs(), metrics.markerClaimMs(), metrics.readinessMs(), metrics.postProcessMs(),
                markerDiagnosticsMs, roomPipelineMs, planned.layoutMetrics().volume(), claimRadius
            );
            source.sendSuccess(() -> Component.literal("   Generated Markers Baked: " + finalRoomPass
                + " | remaining connector markers=" + preservedConnectorMarkers), false);
            if (!finalRoomPass) {
                break;
            }
        }

        phaseStartedAt = System.nanoTime();
        boolean placementPass = allPlaced;
        boolean initialFrontierReady = placementPass && placedControllers.size() >= initialLazyRoomCount;
        HubGateOperationResult gateTransitionResult = holdHubGateClosedForStart
            ? preparationGateClose
            : initialFrontierReady
                ? openHubGateForGeneratedFloor(level, origin)
                : closeHubGateForGeneratedFloor(level, origin);
        boolean commandPass = placementPass && gateTransitionResult.valid();
        GeneratedFloorRuntimeData.LastGeneratedPlacement generatedPlacement =
            new GeneratedFloorRuntimeData.LastGeneratedPlacement(
                origin.immutable(), List.copyOf(placedBounds), List.copyOf(placedControllers),
                List.copyOf(roomPlans), claimRadius, visualTheme
            );
        session.replacePlacement(generatedPlacement);
        repository.flushPlacement(session);
        if (placementPass) {
            long rollingSeed = planSeed(
                origin, resolvedDirection, rooms, spacing, DungeonRunManager.get(level).getRunSeed()
            );
            var composition = com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer.compose(floor, rooms, rollingSeed);
            session.initializeComposition(composition, roomPlans.size(), generationContract);
        } else {
            session.clearComposition();
        }
        repository.flushComposition(session);
        if (!placementPass) {
            session.removePlacement();
            repository.flushPlacement(session);
            startBudgetedCleanup(
                level, source, repository, session, generatedPlacement,
                "Generated Placement Failure Cleanup"
            );
        }
        final int safeRoomCount = countSafeGeneratedRoomPlans(roomPlans);
        source.sendSuccess(() -> Component.literal("Generated Controllers Initially Placed: " + formatControllerList(placedControllers)), false);
        source.sendSuccess(() -> Component.literal("Lazy Placement: ACTIVE | initial=" + initialRooms.size() + " | planned=" + plan.size() + " | lookahead=1"), false);
        source.sendSuccess(() -> Component.literal("Generated Safe Corridor Rolls: " + safeRoomCount + " / " + roomPlans.size() + " | chance=" + (int) (SAFE_CORRIDOR_CHANCE * 100) + "%"), false);
        source.sendSuccess(() -> Component.literal("Generated Linear Placement: " + (
            commandPass ? "PASS" : placementPass ? "FAIL_REQUIRED_HUB_GATE" : "FAIL_WITH_PARTIAL_WORLD_MUTATION"
        )), false);
        source.sendSuccess(() -> Component.literal(placementPass
            ? "Cleanup: /tensuradungeon floor generated clear last"
            : "Cleanup: AUTOMATICALLY_QUEUED_AFTER_PLACEMENT_FAILURE"), false);
        if (placementPass) {
            source.sendSuccess(() -> Component.literal("Next: run /tensuradungeon floor generated start " + rooms + " for the fast stored-placement start path."), false);
            source.sendSuccess(() -> Component.literal("Fallback: /tensuradungeon floor linear claimscan " + rooms + " " + FLOOR_START_HINT_STEP_RADIUS + " then /tensuradungeon floor linear start " + rooms + " chained " + FLOOR_START_HINT_STEP_RADIUS + "."), false);
        }
        String gateTransitionLabel = holdHubGateClosedForStart
            ? "Hub Gate Held Closed For Start"
            : initialFrontierReady ? "Hub Gate Open" : "Hub Gate Kept Closed";
        emitHubGateOperation(source, gateTransitionLabel, gateTransitionResult);
        final int transitionedGateBlockCount = holdHubGateClosedForStart
            ? gateTransitionResult.placedBlockCount()
            : gateTransitionResult.removedBlockCount();
        source.sendSuccess(() -> Component.literal(
            holdHubGateClosedForStart
                ? "Hub Gate Barriers Held: " + transitionedGateBlockCount
                : "Hub Gate Barriers Opened: " + transitionedGateBlockCount
        ), false);
        double finalizationMs = elapsedMillis(phaseStartedAt);
        double totalPlacementMs = elapsedMillis(commandStartedAt);
        final double displayedStructurePlacementMs = structurePlacementTotalMs;
        final double displayedPostPlacementScanMs = boundsWriteTotalMs + markerClaimTotalMs + readinessTotalMs + markerDiagnosticsTotalMs;
        LOGGER.info(
            "[TD-PERF] generated-initial-floor result={} requestedRooms={} initialRooms={} originResolutionMs={} planBuildMs={} occupancyPreflightMs={} structurePlaceMs={} itemCleanupMs={} controllerScanMs={} boundsWriteMs={} markerClaimMs={} readinessMs={} postProcessMs={} markerDiagnosticsMs={} roomPipelineMs={} finalizationMs={} totalMs={} claimRadius={} dimension={}",
            commandPass ? "PASS" : placementPass ? "FAIL_REQUIRED_HUB_GATE" : "FAIL_WITH_PARTIAL_WORLD_MUTATION",
            rooms, initialRooms.size(), originResolutionMs,
            planBuildMs, occupancyPreflightMs, structurePlacementTotalMs, itemCleanupTotalMs, controllerScanTotalMs,
            boundsWriteTotalMs, markerClaimTotalMs, readinessTotalMs, roomPostProcessTotalMs,
            markerDiagnosticsTotalMs, roomPipelineTotalMs, finalizationMs, totalPlacementMs, claimRadius,
            level.dimension().location()
        );
        source.sendSuccess(() -> Component.literal(
            "Stage 20.13 Timing: total=" + formatMillis(totalPlacementMs)
                + " | plan=" + formatMillis(planBuildMs)
                + " | place=" + formatMillis(displayedStructurePlacementMs)
                + " | postPlacementScans=" + formatMillis(displayedPostPlacementScanMs)
        ), false);
        source.sendSuccess(() -> Component.literal(
            "State Mutated: YES - placed the initial rolling horizon, persisted composition state, wrote generated bounds, claimed room markers, and "
                + (gateTransitionResult.valid()
                    ? (holdHubGateClosedForStart ? "held closed for start" : initialFrontierReady ? "opened" : "kept closed")
                    : "failed to transition")
                + " the hub gate"
        ), false);
        return commandPass ? Command.SINGLE_SUCCESS : 0;
    }

    private static double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    private static String formatMillis(double value) {
        return String.format(java.util.Locale.ROOT, "%.2fms", value);
    }

    static int startGeneratedFloorFromStoredPlacement(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        int rooms = RunCommandHandlers.clampRequestedRooms(
            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rooms")
        );
        return startGeneratedFloorFromStoredPlacement(source, player, rooms);
    }

    static int startGeneratedFloorFromStoredPlacement(CommandSourceStack source, ServerPlayer player, int rooms) {
        return startGeneratedFloorFromStoredPlacement(source, player, 1, rooms);
    }

    static int startGeneratedFloorFromStoredPlacement(
        CommandSourceStack source,
        ServerPlayer player,
        int requestedFloor,
        int rooms
    ) {
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (blockIfGeneratedCleanupPending(source, level)) {
            return 0;
        }
        int requestedRooms = RunCommandHandlers.clampRequestedRooms(rooms);
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(level);
        var generatedSession = repository.hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement storedPlacement = generatedSession.placement();
        RunCommandHandlers.StoredGeneratedPresetScanResult storedResult =
            RunCommandHandlers.findStoredGeneratedPresetRoomCandidates(level, requestedRooms);
        if (!storedResult.hasStoredPlacement() || storedPlacement == null) {
            RunCommandHandlers.emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            source.sendFailure(Component.literal("Generated Floor Start: FAIL - no stored generated placement. Run /tensuradungeon floor generated place <rooms> first."));
            return 0;
        }
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> safeEntryPlans = storedPlacement.roomPlans().stream()
            .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::asSafeEntry)
            .toList();
        boolean safeEntryMigrated = !safeEntryPlans.equals(storedPlacement.roomPlans());
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = safeEntryMigrated
            ? storedPlacement.withRuntime(
                storedPlacement.bounds(),
                storedPlacement.controllers(),
                safeEntryPlans,
                storedPlacement.frontier(),
                storedPlacement.retainedRoute(),
                storedPlacement.pruneState()
            )
            : storedPlacement;
        if (safeEntryMigrated) {
            generatedSession.replacePlacement(placement);
            repository.flushPlacement(generatedSession);
            source.sendSuccess(() -> Component.literal("Generated Safe Entry Migration: APPLIED"), false);
        }
        applyStoredGeneratedRoomRuntimePlans(source, placement, storedResult.candidates());
        var rollingState = generatedSession.compositionState();
        boolean rollingPlanReady = rollingState != null && rollingState.roomCount() == requestedRooms
            && placement.roomPlans().size() >= DungeonRunService.requiredInitialGeneratedRooms(requestedRooms)
            && placement.roomPlans().stream().allMatch(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::hasPlacementMetadata);
        boolean lazyPlanReady = rollingPlanReady || (placement.roomPlans().size() >= requestedRooms
            && placement.roomPlans().stream().limit(requestedRooms).allMatch(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::hasPlacementMetadata));
        if (lazyPlanReady && (rollingPlanReady || placement.controllers().size() < requestedRooms)) {
            source.sendSuccess(() -> Component.literal("Generated Lazy Readiness: PASS | physical="
                + storedResult.usableCandidates().size() + " | planned=" + placement.roomPlans().size()), false);
            return armLazyGeneratedFloor(
                source, player, level, manager, requestedFloor, requestedRooms, placement, storedResult.candidates()
            );
        }
        RunCommandHandlers.emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
        if (!storedResult.hasUsableRequestedRooms()) {
            source.sendFailure(Component.literal("Generated Floor Start: FAIL - stored placement has " + storedResult.usableCandidates().size() + " usable controller(s), needed " + requestedRooms + "."));
            return 0;
        }
        return RunCommandHandlers.armPresetFloor(
            source, player, level, manager, requestedFloor, requestedRooms, false, storedResult.candidates(), null
        );
    }

    private static void applyStoredGeneratedRoomRuntimePlans(
        CommandSourceStack source,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        List<RunCommandHandlers.PresetRoomCandidate> candidates
    ) {
        if (placement.roomPlans().isEmpty()) {
            return;
        }
        int eligible = 0;
        int safe = 0;
        int applied = 0;
        for (GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan : placement.roomPlans()) {
            if (!roomPlan.safeEligible()) {
                continue;
            }
            eligible++;
            if (!roomPlan.safeRolled()) {
                continue;
            }
            safe++;
            for (RunCommandHandlers.PresetRoomCandidate candidate : candidates) {
                if (!candidate.pos().equals(roomPlan.controllerPos())) {
                    continue;
                }
                candidate.controller().setRoomProfile(RoomProfile.SAFE_ROOM);
                candidate.controller().setConfiguredObjectiveType(null);
                applied++;
                break;
            }
        }
        final int eligibleCount = eligible;
        final int safeCount = safe;
        final int appliedCount = applied;
        source.sendSuccess(() -> Component.literal("Generated Safe Corridor Plan: safe=" + safeCount + " / eligible=" + eligibleCount + " | applied=" + appliedCount), false);
    }

    private static int armLazyGeneratedFloor(
        CommandSourceStack source,
        ServerPlayer player,
        ServerLevel level,
        DungeonRunManager manager,
        int requestedFloor,
        int requestedRooms,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        List<RunCommandHandlers.PresetRoomCandidate> placedCandidates
    ) {
        List<BlockPos> order = placement.roomPlans().stream()
            .limit(requestedRooms)
            .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::controllerPos)
            .toList();
        boolean rolling = GeneratedFloorRepository.get(level).hydrate().compositionState() != null;
        List<BlockPos> availableControllers = placedCandidates.stream()
            .map(RunCommandHandlers.PresetRoomCandidate::pos)
            .toList();
        List<BlockPos> usableControllers = placedCandidates.stream()
            .filter(RunCommandHandlers.PresetRoomCandidate::usable)
            .map(RunCommandHandlers.PresetRoomCandidate::pos)
            .toList();
        List<BlockPos> safeControllers = placement.roomPlans().stream()
            .filter(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::safeRolled)
            .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::controllerPos)
            .toList();
        DungeonRunService.GeneratedFloorArmOperationResult operation = manager.armGeneratedFloor(
            level,
            player.getUUID(),
            new DungeonRunService.GeneratedFloorArmPlan(
                requestedFloor,
                requestedRooms,
                rolling,
                order,
                availableControllers,
                usableControllers,
                safeControllers
            )
        );
        if ("INCOMPLETE_OR_DUPLICATE_PLAN".equals(operation.reason())) {
            source.sendFailure(Component.literal("Generated Lazy Floor Start: FAIL - planned controller order is incomplete or contains duplicates."));
            return 0;
        }
        if ("INITIAL_CONTROLLER_NOT_READY".equals(operation.reason())) {
            source.sendFailure(Component.literal("Generated Lazy Floor Start: FAIL - initial room "
                + (operation.blockingRoomIndex() + 1) + " is missing or not ready at "
                + formatPos(operation.blockingController()) + "."));
            return 0;
        }
        if ("INITIAL_CONTROLLER_NOT_SAFE".equals(operation.reason())) {
            source.sendFailure(Component.literal("Generated Lazy Floor Start: FAIL - room one is not the required safe entry room at "
                + formatPos(operation.blockingController()) + ". Rebuild the generated floor."));
            return 0;
        }
        if ("INITIAL_CONTROLLER_ACTIVE".equals(operation.reason())) {
            source.sendFailure(Component.literal("Generated Lazy Floor Start: FAIL - initial controller is already active at "
                + formatPos(operation.blockingController()) + "."));
            return 0;
        }
        if ("FLOOR_LOCKED".equals(operation.reason())) {
            source.sendFailure(Component.literal("Floor " + requestedFloor + " is locked. Highest unlocked floor: "
                + operation.floorStart().highestUnlockedFloor() + "."));
            return 0;
        }
        if ("RUN_IN_PROGRESS_ACTIVE_CONTROLLER".equals(operation.reason())) {
            source.sendFailure(Component.literal("Cannot replace an in-progress floor while a generated room is physically active."));
            return 0;
        }
        if (!operation.succeeded()) {
            source.sendFailure(Component.literal("Generated Lazy Floor Start: FAIL - " + operation.reason() + "."));
            return 0;
        }
        if (operation.staleRunCleared()) {
            source.sendSuccess(() -> Component.literal("Cleared stale generated run state before arming the new lazy floor."), false);
        }

        final int preparedControllers = operation.preparedControllers();
        source.sendSuccess(() -> Component.literal("Generated Lazy Floor Start: PASS"), false);
        source.sendSuccess(() -> Component.literal("Planned Rooms: " + operation.order().size()
            + " | Initially Placed: " + preparedControllers + " | Initial Lookahead: 2"), false);
        source.sendSuccess(() -> Component.literal("Preset Expected Controller: " + formatNullablePos(manager.getPresetExpectedControllerPos())), false);
        source.sendSuccess(() -> Component.literal("Future rooms will place when the preceding room activates."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int placeAndStartLinearFloor(CommandContext<CommandSourceStack> context, int spacing, int claimRadius) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        int rooms = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "rooms");
        return placeAndStartLinearFloor(context, player, 0, 1, rooms, spacing, claimRadius);
    }

    static int enterGeneratedFloor(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        return enterGeneratedFloor(context, player);
    }

    static int enterGeneratedFloorForPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return enterGeneratedFloor(context, EntityArgument.getPlayer(context, "player"));
    }

    static int enterSelectedGeneratedFloor(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        return player == null ? 0 : enterSelectedGeneratedFloor(context, player);
    }

    static int enterSelectedGeneratedFloorForPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return enterSelectedGeneratedFloor(context, EntityArgument.getPlayer(context, "player"));
    }

    private static int enterSelectedGeneratedFloor(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        int selectedFloor = DungeonFloorProgressData.get(player.serverLevel()).getSelectedFloor(player.getUUID());
        return enterGeneratedFloor(context, player, selectedFloor);
    }

    private static int enterGeneratedFloor(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        int requestedFloor = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "floor");
        return enterGeneratedFloor(context, player, requestedFloor);
    }

    private static int enterGeneratedFloor(CommandContext<CommandSourceStack> context, ServerPlayer player, int requestedFloor) {
        DungeonRunService.GeneratedFloorEntryValidationResult validation =
            DungeonRunManager.get(player.serverLevel()).validateGeneratedFloorEntry(
                player.serverLevel(), player.getUUID(), requestedFloor
            );
        if ("FLOOR_LOCKED".equals(validation.reason())) {
            context.getSource().sendFailure(Component.literal(
                "Floor " + requestedFloor + " is locked. Highest unlocked floor: "
                    + validation.floorStart().highestUnlockedFloor() + "."
            ));
            return 0;
        }
        if ("RUN_IN_PROGRESS".equals(validation.reason())) {
            context.getSource().sendFailure(Component.literal(
                "Cannot enter another floor while the current dungeon run is in progress."
            ));
            return 0;
        }
        int rooms = DungeonRunService.roomTargetForFloor(requestedFloor);
        context.getSource().sendSuccess(() -> Component.literal(
            "Dungeon NPC Entry: floor=" + requestedFloor + " | generated rooms=" + rooms
        ), false);
        return placeAndStartLinearFloor(
            context, player, requestedFloor, requestedFloor, rooms, DEFAULT_ROOM_SPACING, DEFAULT_CLAIM_RADIUS
        );
    }

    private static int placeAndStartLinearFloor(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player,
        int generationFloor,
        int requestedFloor,
        int rooms,
        int spacing,
        int claimRadius
    ) {
        CommandSourceStack source = context.getSource();
        double baselineTickMs = player.serverLevel().getServer().getAverageTickTimeNanos() / 1_000_000.0D;
        int placementResult = placeLinearFloor(
            context, spacing, claimRadius, true, player, generationFloor, rooms
        );
        if (placementResult != Command.SINGLE_SUCCESS) {
            source.sendFailure(Component.literal("Generated One-Step Start: skipped because placement did not fully pass."));
            return 0;
        }

        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = GeneratedFloorRepository.get(player.serverLevel())
            .hydrate().placement();
        if (placement == null) {
            source.sendFailure(Component.literal("Generated One-Step Start: stored placement disappeared; hub gate remains closed."));
            return 0;
        }

        int requiredInitialRooms = DungeonRunService.requiredInitialGeneratedRooms(rooms);
        if (placement.controllers().size() < requiredInitialRooms) {
            schedulePlaceAndStartStartup(
                player.serverLevel(),
                player.getUUID(),
                requestedFloor,
                rooms,
                placement.floorBuildOrigin(),
                baselineTickMs
            );
            source.sendSuccess(() -> Component.literal(
                "Preparing Dungeon: safe room ready; successor generation is waiting for server recovery."
            ), false);
            source.sendSuccess(() -> Component.literal(
                "Startup Generation: STAGED | minimumDelay=" + STARTUP_RECOVERY_MIN_TICKS
                    + " ticks | maximumDelay=" + STARTUP_RECOVERY_MAX_TICKS + " ticks"
            ), false);
            return Command.SINGLE_SUCCESS;
        }

        return finishPlaceAndStart(source, player, requestedFloor, rooms, placement.floorBuildOrigin());
    }

    private static int finishPlaceAndStart(
        CommandSourceStack source,
        ServerPlayer player,
        int requestedFloor,
        int rooms,
        BlockPos floorBuildOrigin
    ) {
        source.sendSuccess(() -> Component.literal("Generated One-Step Start: using stored generated controller order"), false);
        int startResult = startGeneratedFloorFromStoredPlacement(source, player, requestedFloor, rooms);
        if (startResult != Command.SINGLE_SUCCESS) {
            source.sendFailure(Component.literal("Generated One-Step Start: start failed; hub gate remains closed."));
            return 0;
        }
        schedulePlaceAndStartGateOpen(player.serverLevel(), floorBuildOrigin);
        source.sendSuccess(() -> Component.literal(
            "Hub Gate Release: PENDING_GATE_OPEN | delay=" + PLACE_AND_START_GATE_RELEASE_DELAY_TICKS + " ticks"
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    public static void processPendingPlaceAndStartStartup(ServerLevel level) {
        PendingPlaceAndStartStartup pending = PENDING_PLACE_AND_START_STARTUPS.get(level);
        if (pending == null) {
            reconcileAbandonedPlaceAndStartStartup(level);
            return;
        }
        if (level.getGameTime() < pending.nextCheckGameTime()) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pending.playerId());
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(level);
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || !placement.floorBuildOrigin().equals(pending.floorBuildOrigin())) {
            PENDING_PLACE_AND_START_STARTUPS.remove(level, pending);
            LOGGER.warn(
                "Canceled stale staged generated startup: origin={} placementPresent={} dimension={}",
                pending.floorBuildOrigin(), placement != null, level.dimension().location()
            );
            return;
        }

        long gameTime = level.getGameTime();
        if (player == null || player.serverLevel() != level) {
            if (gameTime < pending.forcedPlacementGameTime()) {
                PENDING_PLACE_AND_START_STARTUPS.replace(
                    level,
                    pending,
                    pending.withNextCheck(gameTime + STARTUP_RECOVERY_CHECK_TICKS)
                );
                return;
            }
            if (PENDING_PLACE_AND_START_STARTUPS.remove(level, pending)) {
                cancelStagedStartup(
                    level, repository, session, placement, "Disconnected Startup Cleanup"
                );
                LOGGER.warn(
                    "Canceled staged generated startup after player absence timeout: origin={} player={} dimension={}",
                    pending.floorBuildOrigin(), pending.playerId(), level.dimension().location()
                );
            }
            return;
        }

        double averageTickMs = level.getServer().getAverageTickTimeNanos() / 1_000_000.0D;
        if (!startupRecoveryReady(
            gameTime,
            pending.earliestPlacementGameTime(),
            pending.forcedPlacementGameTime(),
            averageTickMs,
            pending.baselineTickMs()
        )) {
            PENDING_PLACE_AND_START_STARTUPS.replace(
                level,
                pending,
                pending.withNextCheck(gameTime + STARTUP_RECOVERY_CHECK_TICKS)
            );
            return;
        }

        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> roomPlans = placement.roomPlans();
        if (roomPlans.size() > 1 && !startupConnectorsJoin(
            roomPlans.get(0).outgoingConnectorPos(),
            roomPlans.get(0).outgoingConnectorFacing(),
            roomPlans.get(1).incomingConnectorPos(),
            roomPlans.get(1).incomingConnectorFacing()
        )) {
            PENDING_PLACE_AND_START_STARTUPS.remove(level, pending);
            cancelStagedStartup(
                level, repository, session, placement, "Invalid Startup Connector Cleanup"
            );
            player.sendSystemMessage(Component.literal(
                "Preparing Dungeon failed because the first two room connectors did not join. The generated rooms are being cleaned up."
            ));
            LOGGER.error(
                "Staged generated startup rejected connector mismatch: firstOutgoing={} firstFacing={} secondIncoming={} secondFacing={} dimension={}",
                roomPlans.get(0).outgoingConnectorPos(), roomPlans.get(0).outgoingConnectorFacing(),
                roomPlans.get(1).incomingConnectorPos(), roomPlans.get(1).incomingConnectorFacing(),
                level.dimension().location()
            );
            return;
        }

        int requiredInitialRooms = DungeonRunService.requiredInitialGeneratedRooms(pending.rooms());
        if (placement.controllers().size() < requiredInitialRooms) {
            if (session.generationFailure() != null) {
                PENDING_PLACE_AND_START_STARTUPS.remove(level, pending);
                player.sendSystemMessage(Component.literal(
                    "Preparing Dungeon failed while generating the successor room. The loading barrier remains closed."
                ));
                LOGGER.error(
                    "Staged generated startup failed closed: origin={} reason={} controllers={} required={} averageTickMs={} dimension={}",
                    pending.floorBuildOrigin(), session.generationFailure().code(),
                    placement.controllers().size(), requiredInitialRooms, averageTickMs,
                    level.dimension().location()
                );
                return;
            }
            GeneratedFloorWorldWorkScheduler.requestCriticalStartupLookahead(
                level, placement.controllers().getFirst(),
                gameTime >= pending.forcedPlacementGameTime()
            );
            PENDING_PLACE_AND_START_STARTUPS.replace(
                level, pending, pending.withNextCheck(gameTime + 1L)
            );
            return;
        }

        PENDING_PLACE_AND_START_STARTUPS.remove(level, pending);
        player.sendSystemMessage(Component.literal(
            "Preparing Dungeon: successor ready; validating the run and loading barrier."
        ));
        int startResult = finishPlaceAndStart(
            player.createCommandSourceStack(),
            player,
            pending.requestedFloor(),
            pending.rooms(),
            pending.floorBuildOrigin()
        );
        LOGGER.info(
            "Staged generated startup completed: origin={} result={} waitedTicks={} averageTickMs={} forced={} dimension={}",
            pending.floorBuildOrigin(), startResult == Command.SINGLE_SUCCESS ? "PASS" : "FAIL_CLOSED",
            gameTime - pending.scheduledGameTime(), averageTickMs,
            gameTime >= pending.forcedPlacementGameTime(), level.dimension().location()
        );
    }

    private static void reconcileAbandonedPlaceAndStartStartup(ServerLevel level) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey previousKey = manager.getInstanceKey();
        try {
            for (GeneratedFloorRepository repository : GeneratedFloorRepository.all(level)) {
                reconcileAbandonedPlaceAndStartStartup(level, manager, repository);
            }
        } finally {
            if (previousKey != null && manager.canBindInstance(previousKey)) {
                manager.bindInstance(previousKey);
            }
        }
    }

    private static void reconcileAbandonedPlaceAndStartStartup(
        ServerLevel level,
        DungeonRunManager manager,
        GeneratedFloorRepository repository
    ) {
        GeneratedFloorSession session = repository.hydrate();
        DungeonInstanceAllocationData.InstanceKey instanceKey = repository.scopeIdentity();
        if (instanceKey != null && session.instanceKey() == null) {
            return;
        }
        if (instanceKey != null
            && (!manager.canBindInstance(instanceKey) || !manager.bindInstance(instanceKey))) {
            return;
        }
        boolean runStillInProgress = manager.isActiveRun() && !manager.isRunCompleted();
        GeneratedFloorRuntimeData.HubGateRuntimeState gate = session.hubGateState();
        if (gate != null && "CLOSE_SOLID_CONSTRUCTION".equals(gate.lastOperation())) {
            HubGateOperationResult normalization = runStillInProgress
                ? openHubGateForGeneratedFloor(level, gate.floorBuildOrigin(), repository)
                : closeHubGateForGeneratedFloor(
                    level, gate.floorBuildOrigin(), "dungeon_checkpoint", repository
                );
            LOGGER.warn(
                "Normalized legacy solid construction gate after reload: origin={} result={} activeRun={} dimension={}",
                gate.floorBuildOrigin(), normalization.status(), runStillInProgress, level.dimension().location()
            );
            session = repository.hydrate();
        }
        if (session.hasPendingCleanup() || runStillInProgress) {
            return;
        }
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        var composition = session.compositionState();
        gate = session.hubGateState();
        if (gate == null) {
            return;
        }
        if (placement == null || composition == null
            || !placement.floorBuildOrigin().equals(gate.floorBuildOrigin())) {
            return;
        }
        int requiredInitialRooms = DungeonRunService.requiredInitialGeneratedRooms(composition.roomCount());
        boolean incompleteStagedStartup = placement.controllers().size() < requiredInitialRooms;
        if (!incompleteStagedStartup) {
            return;
        }
        cancelStagedStartup(
            level, repository, session, placement, "Abandoned Startup Recovery Cleanup"
        );
        LOGGER.warn(
            "Recovered abandoned staged startup after reload: origin={} controllers={} required={} dimension={}",
            placement.floorBuildOrigin(), placement.controllers().size(), requiredInitialRooms,
            level.dimension().location()
        );
    }

    private static void cancelStagedStartup(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        String cleanupLabel
    ) {
        session.removePlacement();
        repository.flushPlacement(session);
        session.clearComposition();
        repository.flushComposition(session);
        startBudgetedCleanup(
            level,
            level.getServer().createCommandSourceStack(),
            repository,
            session,
            placement,
            cleanupLabel
        );
    }

    static boolean startupRecoveryReady(
        long gameTime,
        long earliestPlacementGameTime,
        long forcedPlacementGameTime,
        double averageTickMs,
        double baselineTickMs
    ) {
        if (gameTime < earliestPlacementGameTime) {
            return false;
        }
        if (gameTime >= forcedPlacementGameTime) {
            return true;
        }
        double targetTickMs = Math.min(
            STARTUP_RECOVERY_MAX_TARGET_MS,
            Math.max(STARTUP_RECOVERY_MIN_TARGET_MS, baselineTickMs * 1.25D)
        );
        return Double.isFinite(averageTickMs) && averageTickMs <= targetTickMs;
    }

    static boolean startupConnectorsJoin(
        BlockPos firstOutgoing,
        String firstOutgoingFacing,
        BlockPos secondIncoming,
        String secondIncomingFacing
    ) {
        Direction outgoingFacing = parseHorizontalDirection(firstOutgoingFacing);
        Direction incomingFacing = parseHorizontalDirection(secondIncomingFacing);
        return firstOutgoing != null
            && secondIncoming != null
            && outgoingFacing != null
            && incomingFacing == outgoingFacing.getOpposite()
            && secondIncoming.equals(firstOutgoing.relative(outgoingFacing));
    }

    private static Direction parseHorizontalDirection(String name) {
        if (name == null) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction.name().equalsIgnoreCase(name)) {
                return direction;
            }
        }
        return null;
    }

    private static void schedulePlaceAndStartStartup(
        ServerLevel level,
        UUID playerId,
        int requestedFloor,
        int rooms,
        BlockPos floorBuildOrigin,
        double baselineTickMs
    ) {
        long gameTime = level.getGameTime();
        PENDING_PLACE_AND_START_STARTUPS.put(
            level,
            new PendingPlaceAndStartStartup(
                playerId,
                requestedFloor,
                rooms,
                floorBuildOrigin.immutable(),
                gameTime,
                gameTime + STARTUP_RECOVERY_MIN_TICKS,
                gameTime + STARTUP_RECOVERY_MAX_TICKS,
                gameTime + STARTUP_RECOVERY_MIN_TICKS,
                baselineTickMs
            )
        );
    }

    public static void processPendingPlaceAndStartGateOpen(ServerLevel level) {
        PendingHubGateOpen pending = PENDING_PLACE_AND_START_GATE_OPENS.get(level);
        if (pending == null || level.getGameTime() < pending.releaseGameTime()) {
            return;
        }
        if (!PENDING_PLACE_AND_START_GATE_OPENS.remove(level, pending)) {
            return;
        }

        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = GeneratedFloorRepository.get(level).hydrate().placement();
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (placement == null || !placement.floorBuildOrigin().equals(pending.floorBuildOrigin())
            || !manager.isActiveRun() || manager.isRunCompleted()) {
            LOGGER.warn(
                "Canceled deferred place-and-start hub gate release: origin={} placementPresent={} activeRun={} completed={} dimension={}",
                pending.floorBuildOrigin(), placement != null, manager.isActiveRun(), manager.isRunCompleted(),
                level.dimension().location()
            );
            return;
        }

        HubGateOperationResult result = openHubGateForGeneratedFloor(level, pending.floorBuildOrigin());
        if (result.valid()) {
            LOGGER.info(
                "Deferred place-and-start hub gate release completed: origin={} delayTicks={} removed={} dimension={}",
                pending.floorBuildOrigin(), PLACE_AND_START_GATE_RELEASE_DELAY_TICKS, result.removedBlockCount(),
                level.dimension().location()
            );
        } else {
            LOGGER.error(
                "Deferred place-and-start hub gate release failed closed: origin={} result={} dimension={}",
                pending.floorBuildOrigin(), result.status(), level.dimension().location()
            );
        }
    }

    private static void schedulePlaceAndStartGateOpen(ServerLevel level, BlockPos floorBuildOrigin) {
        PENDING_PLACE_AND_START_GATE_OPENS.put(
            level,
            new PendingHubGateOpen(
                floorBuildOrigin.immutable(),
                level.getGameTime() + PLACE_AND_START_GATE_RELEASE_DELAY_TICKS
            )
        );
    }

    private static int inspectTemplateConnectors(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        String templateName = StringArgumentType.getString(context, "template");
        ResourceLocation templateId = findConnectorInspectionTemplate(player.serverLevel(), templateName);
        if (templateId == null) {
            source.sendFailure(Component.literal("Unknown generated template: " + templateName));
            source.sendFailure(Component.literal("Known templates: " + knownTemplateSummary()));
            return 0;
        }

        StructureTemplate loaded = loadTemplate(player.serverLevel(), templateId);
        source.sendSuccess(() -> Component.literal("Generated Template Connectors"), false);
        source.sendSuccess(() -> Component.literal("Template: " + templateId), false);
        if (loaded == null) {
            source.sendFailure(Component.literal("Template Connectors: FAIL - template missing at data/"
                + templateId.getNamespace() + "/structure/" + templateId.getPath() + ".nbt"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Template Size: " + loaded.getSize().getX() + "x" + loaded.getSize().getY() + "x" + loaded.getSize().getZ()), false);
        for (Rotation rotation : ROTATION_OPTIONS) {
            List<TemplateConnector> connectors = collectConnectors(loaded, rotation);
            source.sendSuccess(() -> Component.literal("Rotation " + rotation + " connector count: " + connectors.size()), false);
            for (int i = 0; i < connectors.size(); i++) {
                TemplateConnector connector = connectors.get(i);
                final int displayIndex = i + 1;
                final String connectorSummary = connector.summary();
                source.sendSuccess(() -> Component.literal("  " + displayIndex + ". " + connectorSummary), false);
            }
        }
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearLastGeneratedPlacement(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        DungeonInstanceAllocationData.Allocation instanceAllocation =
            DungeonInstanceAllocationData.get(level).forPlayer(player.getUUID()).orElse(null);
        if (instanceAllocation != null && instanceAllocation.soloPreparation() != null) {
            boolean abandoned = DungeonRunManager.get(level).abandonInstance(
                instanceAllocation.key(), level, player, "GENERATED_LINEAR_CLEAR"
            );
            if (!abandoned) {
                source.sendFailure(Component.literal(
                    "Generated Linear Clear: exact dungeon instance could not be abandoned safely."
                ));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                "Generated Linear Clear: exact instance cleanup started; the next preparation will reroll the floor."
            ), false);
            return Command.SINGLE_SUCCESS;
        }
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(level);
        GeneratedFloorSession session = repository.hydrate();
        if (session.hasPendingCleanup()) {
            emitCleanupLockout(source, session.cleanupState());
            return 0;
        }
        GeneratedFloorRuntimeData.LastGeneratedPlacement last = session.removePlacement();
        repository.flushPlacement(session);
        session.clearComposition();
        repository.flushComposition(session);
        if (last == null) {
            ResolvedGeneratedOrigin recoveredOrigin = resolveGeneratedOrigin(level, player.blockPosition());
            boolean occupiedRecoveredOrigin = recoveredOrigin.origin() != null
                && !level.getBlockState(recoveredOrigin.origin()).isAir()
                && !level.getBlockState(recoveredOrigin.origin()).is(ModBlocks.FLOOR_BUILD_ORIGIN.get());
            if (occupiedRecoveredOrigin) {
                source.sendFailure(Component.literal(
                    "Generated Linear Clear: FAIL_LEGACY_ORPHAN - physical floor blocks survived without "
                        + "LastGeneratedPlacement cleanup ownership."
                ));
                source.sendFailure(Component.literal(
                    "Automatic deletion refused. Use bounded operator cleanup and restore floor_build_origin; "
                        + "new placements write a crash-durable manifest before mutating chunks."
                ));
            } else {
                source.sendFailure(Component.literal("Generated Linear Clear: FAIL - no stored last placement for this dimension."));
                source.sendFailure(Component.literal("Place a generated floor first, or check /tensuradungeon floor generated cleanup status."));
            }
            return 0;
        }
        DungeonFloorProgressData.get(level).clearPreparedModifierPlan(
            player.getUUID(), DungeonRunManager.get(level).getFloorNumber()
        );
        startBudgetedCleanup(level, source, repository, session, last, "Generated Linear Clear");
        return Command.SINGLE_SUCCESS;
    }

    public static boolean cleanupGeneratedFloorAfterHubReturn(ServerLevel level, CommandSourceStack source) {
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(level);
        GeneratedFloorSession session = repository.hydrate();
        if (session.hasPendingCleanup()) {
            source.sendSuccess(() -> Component.literal("Generated Floor Cleanup: ALREADY_PENDING"), false);
            emitCleanupStatus(source, session.cleanupState());
            return true;
        }
        GeneratedFloorRuntimeData.LastGeneratedPlacement last = session.removePlacement();
        repository.flushPlacement(session);
        session.clearComposition();
        repository.flushComposition(session);
        if (last == null) {
            source.sendSuccess(() -> Component.literal("Generated Floor Cleanup: SKIPPED - no stored generated placement found for this dimension."), false);
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return false;
        }
        startBudgetedCleanup(level, source, repository, session, last, "Generated Floor Cleanup");
        return true;
    }
    public static boolean isFirstGeneratedRoom(ServerLevel level, BlockPos controllerPos) {
        return isFirstGeneratedRoom(level, null, controllerPos);
    }

    public static boolean isFirstGeneratedRoom(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos controllerPos
    ) {
        GeneratedFloorRuntimeData.LastGeneratedPlacement last =
            generatedRepository(level, instanceKey).hydrate().placement();
        return last != null && last.roomPlans().stream()
            .anyMatch(plan -> plan.index() == 1 && plan.controllerPos().equals(controllerPos));
    }

    public static boolean closeHubGateAfterFirstRoomEntry(ServerLevel level, BlockPos controllerPos) {
        return closeHubGateAfterFirstRoomEntry(level, null, controllerPos);
    }

    public static boolean closeHubGateAfterFirstRoomEntry(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos controllerPos
    ) {
        GeneratedFloorRuntimeData.LastGeneratedPlacement last =
            generatedRepository(level, instanceKey).hydrate().placement();
        if (last == null || last.roomPlans().stream()
            .noneMatch(plan -> plan.index() == 1 && plan.controllerPos().equals(controllerPos))) {
            return false;
        }
        HubGateOperationResult result = closeHubGateForGeneratedFloor(level, last.floorBuildOrigin(), "dungeon_sealed");
        if (!result.valid()) {
            LOGGER.error(
                "Required Fog hub-gate reseal failed after first-room entry: result={} origin={} controller={}",
                result.status(), last.floorBuildOrigin(), controllerPos
            );
        }
        return result.valid();
    }

    private static GeneratedFloorRepository generatedRepository(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return instanceKey == null
            ? GeneratedFloorRepository.get(level)
            : GeneratedFloorRepository.get(level, dimensionKey(level), instanceKey);
    }
    private static List<LoadedPlannedRoom> buildConnectorPlan(
        CommandSourceStack source,
        ServerLevel level,
        BlockPos origin,
        Direction planDirection,
        int rooms,
        int spacing
    ) {
        int floor = Math.max(1, DungeonRunManager.get(level).getFloorNumber());
        return buildConnectorPlan(source, level, origin, planDirection, floor, rooms, spacing);
    }

    private static List<LoadedPlannedRoom> buildConnectorPlan(
        CommandSourceStack source,
        ServerLevel level,
        BlockPos origin,
        Direction planDirection,
        int floor,
        int rooms,
        int spacing
    ) {
        long seed = planSeed(origin, planDirection, rooms, spacing, DungeonRunManager.get(level).getRunSeed());
        return buildConnectorPlan(
            source, level, origin, planDirection, floor, rooms, spacing, seed
        );
    }

    private static List<LoadedPlannedRoom> buildConnectorPlan(
        CommandSourceStack source,
        ServerLevel level,
        BlockPos origin,
        Direction planDirection,
        int floor,
        int rooms,
        int spacing,
        ServerPlayer targetPlayer
    ) {
        long seed = planSeed(origin, planDirection, rooms, spacing, DungeonRunManager.get(level).getRunSeed());
        return buildConnectorPlan(
            source, level, origin, planDirection, floor, rooms, spacing, seed, targetPlayer
        );
    }

    private static List<LoadedPlannedRoom> buildConnectorPlan(
        CommandSourceStack source,
        ServerLevel level,
        BlockPos origin,
        Direction planDirection,
        int floor,
        int rooms,
        int spacing,
        long seed
    ) {
        return buildConnectorPlan(
            source, level, origin, planDirection, floor, rooms, spacing, seed, null
        );
    }

    private static List<LoadedPlannedRoom> buildConnectorPlan(
        CommandSourceStack source,
        ServerLevel level,
        BlockPos origin,
        Direction planDirection,
        int floor,
        int rooms,
        int spacing,
        long seed,
        ServerPlayer targetPlayer
    ) {
        return buildConnectorPlan(
            source, level, origin, planDirection, floor, rooms, spacing, seed,
            targetPlayer, null
        );
    }

    private static List<LoadedPlannedRoom> buildConnectorPlan(
        CommandSourceStack source,
        ServerLevel level,
        BlockPos origin,
        Direction planDirection,
        int floor,
        int rooms,
        int spacing,
        long seed,
        ServerPlayer targetPlayer,
        CompositePlanningOverride planningOverride
    ) {
        long planningStartedAt = System.nanoTime();
        ServerPlayer planningPlayer = targetPlayer == null ? getPlayer(source) : targetPlayer;
        if (planningPlayer == null) return List.of();
        long phaseStartedAt = System.nanoTime();
        var authored = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        double authoredRegistryMs = elapsedMillis(phaseStartedAt);
        String visualTheme;
        Map<Integer, String> requiredTemplates;
        if (planningOverride == null) {
            DungeonThemeSelectionPolicy.Snapshot themePolicy = DungeonThemeSelectionPolicy.get(level);
            DungeonThemeSelectionPolicy.Selection themeSelection = themePolicy.select(
                authored.visualThemes(),
                DungeonFloorProgressData.get(level).getHighestUnlockedFloor(planningPlayer.getUUID()),
                seed,
                floor
            );
            if (!themeSelection.succeeded()) {
                source.sendFailure(Component.literal(
                    "Rolling Preflight FAIL: theme selection " + themeSelection.reason()
                        + (themePolicy.valid() ? "" : " errors=" + String.join(" | ", themePolicy.errors()))
                ));
                return List.of();
            }
            visualTheme = themeSelection.themeId();
            requiredTemplates = Map.of();
        } else {
            visualTheme = planningOverride.visualTheme();
            requiredTemplates = planningOverride.requiredTemplates(rooms);
            String overrideValidation = planningOverride.validate(authored, rooms);
            if (!"VALID_COMPOSITE_TEST_OVERRIDE".equals(overrideValidation)) {
                source.sendFailure(Component.literal(
                    "Rolling Preflight FAIL: composite segment test override "
                        + overrideValidation
                ));
                return List.of();
            }
            source.sendSuccess(() -> Component.literal(
                "Composite Segment Test Override: theme=" + visualTheme
                    + " | required=" + requiredTemplates
            ), false);
        }
        phaseStartedAt = System.nanoTime();
        com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer.Composition composition =
            com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer.compose(floor, rooms, seed);
        double compositionMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        var mechanical = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry.getForTheme(
            level, authored, visualTheme
        );
        double mechanicalRegistryMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> reserved = new ArrayList<>();
        RoomControllerBlockEntity.RoomBounds hubBounds = resolveHubSafeZoneBounds(level);
        if (hubBounds != null) {
            reserved.add(new com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds(
                hubBounds.min().getX(), hubBounds.min().getY(), hubBounds.min().getZ(),
                hubBounds.max().getX(), hubBounds.max().getY(), hubBounds.max().getZ()
            ));
        }
        double safeZoneMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        var selectionContext = new com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.SelectionContext(
            rooms, List.of(), java.util.Set.of(), requiredTemplates, visualTheme
        );
        var solved = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.solveWindow(
            composition.beats(), authored, mechanical, origin, planDirection,
            level.getMinBuildHeight(), level.getMaxBuildHeight(), seed, reserved, reserved,
            rooms > com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.DEFAULT_HORIZON,
            selectionContext
        );
        double solveMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        var adapted = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter.adapt(
            level, solved, mechanical
        );
        double adaptationMs = elapsedMillis(phaseStartedAt);
        if (!solved.solved() || !adapted.valid()) {
            LOGGER.info("[TD-PERF] generated-planning result=FAIL floor={} rooms={} seed={} theme={} originLong={} direction={} compositionMs={} authoredRegistryMs={} mechanicalRegistryMs={} safeZoneMs={} solveMs={} adaptationMs={} templateLoadMs=0.0 planMaterializationMs=0.0 totalMs={} templates=0 visitedNodes={} backtracks={} reason={} adapterErrors={} dimension={}",
                floor, rooms, seed, visualTheme, origin.asLong(), planDirection, compositionMs, authoredRegistryMs,
                mechanicalRegistryMs, safeZoneMs, solveMs, adaptationMs, elapsedMillis(planningStartedAt),
                solved.visitedNodes(), solved.backtracks(), solved.reason(), adapted.errors(), level.dimension().location());
            source.sendFailure(Component.literal("Rolling Preflight FAIL: reason=" + solved.reason()
                + " | visited=" + solved.visitedNodes() + " | backtracks=" + solved.backtracks()
                + " | adapterErrors=" + String.join(",", adapted.errors())));
            return List.of();
        }

        List<LoadedPlannedRoom> plan = new ArrayList<>();
        double templateLoadMs = 0.0D;
        phaseStartedAt = System.nanoTime();
        for (int i = 0; i < solved.nodes().size(); i++) {
            var node = solved.nodes().get(i);
            var runtimePlan = adapted.plans().get(i);
            TemplateRequirement requirement = requirementFromRollingPlan(node.templateId(), runtimePlan);
            long templateLoadStartedAt = System.nanoTime();
            StructureTemplate loaded = loadTemplate(level, requirement);
            templateLoadMs += elapsedMillis(templateLoadStartedAt);
            if (loaded == null || runtimePlan.bounds() == null) {
                double planMaterializationMs = Math.max(0.0D, elapsedMillis(phaseStartedAt) - templateLoadMs);
                LOGGER.info("[TD-PERF] generated-planning result=MATERIALIZATION_FAIL floor={} rooms={} seed={} originLong={} direction={} compositionMs={} authoredRegistryMs={} mechanicalRegistryMs={} safeZoneMs={} solveMs={} adaptationMs={} templateLoadMs={} planMaterializationMs={} totalMs={} templates={} visitedNodes={} backtracks={} reason={} dimension={}",
                    floor, rooms, seed, origin.asLong(), planDirection, compositionMs, authoredRegistryMs,
                    mechanicalRegistryMs, safeZoneMs, solveMs, adaptationMs, templateLoadMs,
                    planMaterializationMs, elapsedMillis(planningStartedAt), plan.size(), solved.visitedNodes(),
                    solved.backtracks(), loaded == null ? "TEMPLATE_LOAD_FAILED" : "MISSING_RUNTIME_BOUNDS",
                    level.dimension().location());
                return List.of();
            }
            BlockPos incomingLocal = node.incomingConnector().subtract(node.placementOrigin());
            BlockPos outgoingLocal = node.outgoingConnector() == null
                ? null : node.outgoingConnector().subtract(node.placementOrigin());
            TemplateConnector incoming = new TemplateConnector(
                "rolling_incoming", incomingLocal, node.incomingFacing(), true, node.incomingConnector()
            );
            TemplateConnector outgoing = outgoingLocal == null ? null : new TemplateConnector(
                "rolling_outgoing", outgoingLocal, node.outgoingFacing(), true, node.outgoingConnector()
            );
            long volume = Math.max(1L, (long) runtimePlan.bounds().getWidth() * runtimePlan.bounds().getHeight() * runtimePlan.bounds().getDepth());
            LayoutRoomMetrics metrics = new LayoutRoomMetrics(
                runtimePlan.bounds().getWidth(), runtimePlan.bounds().getHeight(), runtimePlan.bounds().getDepth(),
                runtimePlan.bounds().getWidth() * runtimePlan.bounds().getDepth(), volume,
                node.placementCost(), node.placementCost() >= 2, node.placementCost() >= 3,
                node.outgoingFacing() != null && node.outgoingFacing() != planDirection,
                node.outgoingConnector() == null ? 0 : node.outgoingConnector().getY() - node.incomingConnector().getY()
            );
            plan.add(new LoadedPlannedRoom(
                node.roomIndex(), requirement, loaded, loaded.getSize(), node.rotation(), node.placementOrigin(),
                runtimePlan.bounds(), node.incomingConnector(), incoming, outgoing,
                outgoing == null ? "NONE" : outgoing.summary(),
                runtimePlan.safeRolled(), runtimePlan.controllerPos(), metrics, node.outgoingConnectorCount(),
                runtimePlan.gameplayRole(), runtimePlan.effectiveProfile(), runtimePlan.objectiveType()
            ));
        }
        double planMaterializationMs = Math.max(0.0D, elapsedMillis(phaseStartedAt) - templateLoadMs);
        LOGGER.info("[TD-PERF] generated-planning result=PASS floor={} rooms={} seed={} theme={} originLong={} direction={} compositionMs={} authoredRegistryMs={} mechanicalRegistryMs={} safeZoneMs={} solveMs={} adaptationMs={} templateLoadMs={} planMaterializationMs={} totalMs={} templates={} visitedNodes={} backtracks={} reason={} dimension={}",
            floor, rooms, seed, visualTheme, origin.asLong(), planDirection, compositionMs, authoredRegistryMs,
            mechanicalRegistryMs, safeZoneMs, solveMs, adaptationMs, templateLoadMs, planMaterializationMs,
            elapsedMillis(planningStartedAt), plan.size(), solved.visitedNodes(), solved.backtracks(), solved.reason(),
            level.dimension().location());
        source.sendSuccess(() -> Component.literal("Rolling Planning Safety: PASS | horizon=" + plan.size()
            + " / requested=" + rooms + " | visited=" + solved.visitedNodes()
            + " | backtracks=" + solved.backtracks()
            + " | elapsedMs=" + String.format(java.util.Locale.ROOT, "%.2f", solved.elapsedMillis())), false);
        return List.copyOf(plan);
    }

    private static String visualThemeForPlan(ServerLevel level, List<LoadedPlannedRoom> plan) {
        String selected = "";
        var authored = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        for (LoadedPlannedRoom room : plan) {
            var metadata = authored.templates().get(room.template().label());
            if (metadata == null || metadata.visualTheme().isBlank() || "legacy".equals(metadata.visualTheme())) return "";
            if (selected.isBlank()) selected = metadata.visualTheme();
            else if (!selected.equals(metadata.visualTheme())) return "";
        }
        return selected;
    }

    private static RoomControllerBlockEntity.RoomBounds resolveHubSafeZoneBounds(ServerLevel level) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        BlockPos cornerA = manager.getHubSafeZoneCornerA();
        BlockPos cornerB = manager.getHubSafeZoneCornerB();
        if (cornerA == null || cornerB == null) {
            return null;
        }
        return new RoomControllerBlockEntity.RoomBounds(
            new BlockPos(
                Math.min(cornerA.getX(), cornerB.getX()),
                Math.min(cornerA.getY(), cornerB.getY()),
                Math.min(cornerA.getZ(), cornerB.getZ())
            ),
            new BlockPos(
                Math.max(cornerA.getX(), cornerB.getX()),
                Math.max(cornerA.getY(), cornerB.getY()),
                Math.max(cornerA.getZ(), cornerB.getZ())
            )
        );
    }
    private static int initialLazyRoomCount(List<LoadedPlannedRoom> plan) {
        return DungeonRunService.requiredInitialGeneratedRooms(plan.size());
    }

    private static int countOutgoingConnectors(List<TemplateConnector> connectors, TemplateConnector selectedConnector) {
        int preferredCount = 0;
        int fallbackCount = 0;
        for (TemplateConnector connector : connectors) {
            if (connector.localPos().equals(selectedConnector.localPos())
                || connector.facing() == selectedConnector.facing()) {
                continue;
            }
            if (connector.outgoingPreferred()) {
                preferredCount++;
            } else {
                fallbackCount++;
            }
        }
        return preferredCount > 0 ? preferredCount : fallbackCount;
    }
    private static TemplateConnector chooseOutgoingConnector(
        List<TemplateConnector> connectors,
        TemplateConnector selectedConnector,
        BlockPos placementOrigin,
        Direction preferredFacing,
        boolean conservativeRescue,
        RandomSource random
    ) {
        List<TemplateConnector> candidates = new ArrayList<>();
        List<TemplateConnector> fallbackCandidates = new ArrayList<>();
        for (TemplateConnector connector : connectors) {
            if (connector.localPos().equals(selectedConnector.localPos())) {
                continue;
            }
            if (connector.facing() == selectedConnector.facing()) {
                continue;
            }
            TemplateConnector worldConnector = connector.withWorldPos(placementOrigin);
            if (connector.outgoingPreferred()) {
                candidates.add(worldConnector);
            } else {
                fallbackCandidates.add(worldConnector);
            }
        }
        List<TemplateConnector> pool = !candidates.isEmpty() ? candidates : fallbackCandidates;
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private static List<TemplateConnector> collectConnectors(StructureTemplate template, Rotation rotation) {
        StructurePlaceSettings settings = placeSettings(rotation);
        BoundingBox localBounds = template.getBoundingBox(settings, BlockPos.ZERO);
        List<TemplateConnector> connectors = new ArrayList<>();
        addConnectorBlocks(connectors, template, settings, localBounds, ModBlocks.FLOOR_CONNECTOR_ANCHOR.get(), "floor_connector_anchor", true);
        addConnectorBlocks(connectors, template, settings, localBounds, ModBlocks.CONNECTOR_EXIT.get(), "connector_exit", true);
        if (connectors.isEmpty()) {
            addConnectorBlocks(connectors, template, settings, localBounds, ModBlocks.FLOOR_ENTRY_ANCHOR.get(), "floor_entry_anchor", false);
            addConnectorBlocks(connectors, template, settings, localBounds, ModBlocks.EXIT_ANCHOR.get(), "exit_anchor", false);
        }
        return connectors;
    }

    private static void addConnectorBlocks(List<TemplateConnector> connectors, StructureTemplate template, StructurePlaceSettings settings, BoundingBox localBounds, Block markerBlock, String markerName, boolean primary) {
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, markerBlock)) {
            BlockPos connectorPos = info.pos();
            Direction facing = inferHorizontalFacing(connectorPos, localBounds);
            connectors.add(new TemplateConnector(markerName, connectorPos.immutable(), facing, primary, null));
        }
    }

    private static int countGeneratedConnectorMarkers(ServerLevel level, RoomControllerBlockEntity.RoomBounds bounds) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            if (isGeneratedConnectorMarker(level.getBlockState(pos))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isGeneratedConnectorMarker(BlockState state) {
        return state.is(ModBlocks.FLOOR_CONNECTOR_ANCHOR.get())
            || state.is(ModBlocks.CONNECTOR_EXIT.get())
            || state.is(ModBlocks.FLOOR_ENTRY_ANCHOR.get());
    }

    private static Direction inferHorizontalFacing(BlockPos pos, BoundingBox bounds) {
        int west = Math.abs(pos.getX() - bounds.minX());
        int east = Math.abs(pos.getX() - bounds.maxX());
        int north = Math.abs(pos.getZ() - bounds.minZ());
        int south = Math.abs(pos.getZ() - bounds.maxZ());
        int best = Math.min(Math.min(west, east), Math.min(north, south));
        if (west == best) {
            return Direction.WEST;
        }
        if (east == best) {
            return Direction.EAST;
        }
        if (north == best) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    private static StructureTemplate loadTemplate(ServerLevel level, TemplateRequirement template) {
        if (CompositeTemplateDescriptor.isCompositeId(template.label())) {
            CompositeRoomRuntimeRegistry.Resolution composite =
                CompositeRoomRuntimeRegistry.resolve(level, template.label());
            return composite.success() ? composite.template() : null;
        }
        return loadTemplate(level, template.resourceLocation());
    }

    private static StructureTemplate loadTemplate(ServerLevel level, ResourceLocation templateId) {
        String prefix = "rooms/linear/";
        if (templateId != null && templateId.getPath().startsWith(prefix)) {
            String label = templateId.getPath().substring(prefix.length());
            if (CompositeTemplateDescriptor.isCompositeId(label)) {
                CompositeRoomRuntimeRegistry.Resolution composite =
                    CompositeRoomRuntimeRegistry.resolve(level, label);
                return composite.success() ? composite.template() : null;
            }
        }
        Optional<StructureTemplate> loaded = level.getStructureManager().get(templateId);
        return loaded.orElse(null);
    }

    private static StructurePlaceSettings placeSettings(Rotation rotation) {
        return new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation);
    }

    private static RoomControllerBlockEntity.RoomBounds boundsFromTemplate(StructureTemplate template, Rotation rotation, BlockPos placementOrigin) {
        BoundingBox box = template.getBoundingBox(placeSettings(rotation), placementOrigin);
        return new RoomControllerBlockEntity.RoomBounds(
            new BlockPos(box.minX(), box.minY(), box.minZ()),
            new BlockPos(box.maxX(), box.maxY(), box.maxZ())
        );
    }

    private static int countRoomControllers(ServerLevel level, RoomControllerBlockEntity.RoomBounds bounds) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos checkedPos = pos.immutable();
            if (!level.getBlockState(checkedPos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(checkedPos);
            if (blockEntity instanceof RoomControllerBlockEntity) {
                count++;
            }
        }
        return count;
    }

    private static int clearBounds(ServerLevel level, RoomControllerBlockEntity.RoomBounds bounds) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos checkedPos = pos.immutable();
            if (level.getBlockState(checkedPos).isAir()) {
                continue;
            }
            level.setBlock(checkedPos, Blocks.AIR.defaultBlockState(), 3);
            cleared++;
        }
        return cleared;
    }
    private static void startBudgetedCleanup(
        ServerLevel level,
        CommandSourceStack source,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.LastGeneratedPlacement last,
        String label
    ) {
        session.clearGenerationFailure();
        repository.flushGenerationFailure(session);
        GeneratedFloorRuntimeData.CleanupState cleanupState = new GeneratedFloorRuntimeData.CleanupState(
            last.floorBuildOrigin().immutable(),
            List.copyOf(last.bounds()),
            0,
            0L,
            0L,
            0L,
            level.getGameTime()
        );
        session.replaceCleanupState(cleanupState);
        repository.flushCleanup(session);
        String storedBoundsValidation = last.floorBuildOriginValidation(ORIGIN_SCAN_RADIUS);
        boolean plannedOriginOwnership = last.roomPlans().stream()
            .anyMatch(plan -> plan.bounds() != null && plan.bounds().contains(last.floorBuildOrigin()));
        String originValidation = !storedBoundsValidation.startsWith("VALID_") && plannedOriginOwnership
            ? "VALID_STORED_PLACEMENT_PLAN_BOUNDS"
            : storedBoundsValidation;
        boolean originRecoverable = originValidation.startsWith("VALID_")
            && (last.ownsFloorBuildOrigin()
                || plannedOriginOwnership
                || level.getBlockState(last.floorBuildOrigin()).isAir()
                || level.getBlockState(last.floorBuildOrigin()).is(ModBlocks.FLOOR_BUILD_ORIGIN.get()));
        HubGateOperationResult gateCloseResult = null;
        if (originRecoverable) {
            level.setBlock(last.floorBuildOrigin(), ModBlocks.FLOOR_BUILD_ORIGIN.get().defaultBlockState(), 3);
            gateCloseResult = closeHubGateForGeneratedFloor(
                level, last.floorBuildOrigin(), "dungeon_checkpoint", repository
            );
        }
        source.sendSuccess(() -> Component.literal(label + ": PENDING_BUDGETED_CLEANUP"), false);
        source.sendSuccess(() -> Component.literal("Generated Bounds Queued: " + last.bounds().size()), false);
        source.sendSuccess(() -> Component.literal("Cleanup Total Positions: " + cleanupState.totalPositions()), false);
        source.sendSuccess(() -> Component.literal("Cleanup Budget Per Tick: " + GeneratedFloorCleanupProcessor.DEFAULT_BLOCKS_PER_TICK), false);
        source.sendSuccess(() -> Component.literal(originRecoverable
            ? "Restored Floor Build Origin: " + formatPos(last.floorBuildOrigin())
            : "Restored Floor Build Origin: SKIPPED - " + originValidation), false);
        if (gateCloseResult != null) {
            emitHubGateOperation(source, "Hub Gate Close", gateCloseResult);
        }
        final int restoredGateCount = gateCloseResult == null ? 0 : gateCloseResult.placedBlockCount();
        final String gateRecoverySummary = gateCloseResult == null
            ? "skipped unsafe origin recovery for"
            : gateCloseResult.valid() ? "reclosed" : "failed to reclose";
        final String originRecoverySummary = originRecoverable
            ? "restored floor_build_origin"
            : "skipped unsafe floor_build_origin restoration";
        source.sendSuccess(() -> Component.literal("Restored Hub Gate Barriers: " + restoredGateCount), false);
        source.sendSuccess(() -> Component.literal("New Generated Floors Locked Until Cleanup Completes: YES"), false);
        source.sendSuccess(() -> Component.literal(
            "State Mutated: YES - queued generated structure cleanup, " + originRecoverySummary + ", and "
                + gateRecoverySummary + " the hub gate"
        ), false);
    }

    private static int cleanupStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        String dimension = dimensionKey(level);
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level).currentForDimension(dimension).orElse(null);
        GeneratedFloorSession session = allocation == null
            ? GeneratedFloorRepository.get(level).hydrate()
            : GeneratedFloorRepository.get(level, dimension, allocation.key()).hydrate();
        GeneratedFloorRuntimeData.CleanupState cleanupState = session.cleanupState();
        source.sendSuccess(() -> Component.literal("Generated Floor Cleanup Status"), false);
        final String cleanupScope = allocation == null
            ? "DIMENSION_DEFAULT"
            : "EXACT_INSTANCE_" + formatInstanceKey(allocation.key());
        source.sendSuccess(() -> Component.literal("Cleanup Scope: " + cleanupScope), false);
        emitCleanupStatus(source, cleanupState);
        if (allocation != null) {
            source.sendSuccess(() -> Component.literal(
                "Dungeon Instance Lifecycle: " + allocation.lifecycle().state()
            ), false);
        }
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }

    static boolean blockIfGeneratedCleanupPending(CommandSourceStack source, ServerLevel level) {
        GeneratedFloorRuntimeData.CleanupState cleanupState = GeneratedFloorRepository.get(level).hydrate().cleanupState();
        if (cleanupState == null) {
            return false;
        }
        emitCleanupLockout(source, cleanupState);
        return true;
    }

    private static void emitCleanupLockout(CommandSourceStack source, GeneratedFloorRuntimeData.CleanupState cleanupState) {
        source.sendFailure(Component.literal("Generated floor cleanup is still running. New dungeon generation is locked until cleanup completes."));
        emitCleanupStatus(source, cleanupState);
    }

    private static void emitCleanupStatus(CommandSourceStack source, GeneratedFloorRuntimeData.CleanupState cleanupState) {
        if (cleanupState == null) {
            source.sendSuccess(() -> Component.literal("Cleanup Pending: NO"), false);
            return;
        }
        final String originText = formatPos(cleanupState.floorBuildOrigin());
        final int boundsCount = cleanupState.bounds().size();
        final long totalPositions = cleanupState.totalPositions();
        final long visitedPositions = cleanupState.visitedPositions();
        final long clearedBlocks = cleanupState.clearedBlocks();
        final int progressPercent = cleanupState.progressPercent();
        final int currentBoundsIndex = cleanupState.currentBoundsIndex() + 1;
        source.sendSuccess(() -> Component.literal("Cleanup Pending: YES"), false);
        source.sendSuccess(() -> Component.literal("Cleanup Origin: " + originText), false);
        source.sendSuccess(() -> Component.literal("Cleanup Bounds: " + boundsCount + " | current=" + currentBoundsIndex), false);
        source.sendSuccess(() -> Component.literal("Cleanup Progress: " + progressPercent + "% | visited=" + visitedPositions + " / " + totalPositions), false);
        source.sendSuccess(() -> Component.literal("Cleanup Cleared Blocks: " + clearedBlocks), false);
        source.sendSuccess(() -> Component.literal("Cleanup Budget Per Tick: " + GeneratedFloorCleanupProcessor.DEFAULT_BLOCKS_PER_TICK), false);
    }
    private static int generatedFloorStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        String dimension = dimensionKey(level);
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData allocationData = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocationData.currentForDimension(dimension).orElse(null);
        GeneratedFloorSession session = allocation == null
            ? GeneratedFloorRepository.get(level).hydrate()
            : GeneratedFloorRepository.get(level, dimension, allocation.key()).hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement lastPlacement = session.placement();
        GeneratedFloorRuntimeData.HubGateRuntimeState storedGateState = session.hubGateState();
        BlockPos liveOrigin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        ResolvedGeneratedOrigin resolvedOrigin = resolveGeneratedOrigin(level, player.blockPosition());
        source.sendSuccess(() -> Component.literal("Generated Floor Recovery Status"), false);
        source.sendSuccess(() -> Component.literal("Dimension: " + dimension), false);
        emitResolvedOrigin(source, liveOrigin, resolvedOrigin);
        if (lastPlacement == null) {
            source.sendSuccess(() -> Component.literal("Stored Placement: NONE"), false);
        } else {
            final String storedOriginText = formatPos(lastPlacement.floorBuildOrigin());
            final int storedBoundsCount = lastPlacement.bounds().size();
            final int storedControllerCount = lastPlacement.controllers().size();
            final int storedPlannedCount = lastPlacement.roomPlans().size();
            source.sendSuccess(() -> Component.literal("Stored Placement: floorBuildOrigin=" + storedOriginText + " | bounds=" + storedBoundsCount + " | controllersPlaced=" + storedControllerCount + " | roomsPlanned=" + storedPlannedCount), false);
            source.sendSuccess(() -> Component.literal("Stored Placement Controllers: " + formatControllerList(lastPlacement.controllers())), false);
            long multiwayRooms = lastPlacement.roomPlans().stream().filter(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::supportsPlayerDirectedExit).count();
            source.sendSuccess(() -> Component.literal("Player-Directed Multiway: rooms=" + multiwayRooms + " | unresolvedSuccessorsDeferUntilRoomCompletion=YES"), false);
        }
        GeneratedFloorRuntimeData.CleanupState cleanupState = session.cleanupState();
        final String cleanupScope = allocation == null
            ? "DIMENSION_DEFAULT"
            : "EXACT_INSTANCE_" + formatInstanceKey(allocation.key());
        source.sendSuccess(() -> Component.literal("Cleanup Scope: " + cleanupScope), false);
        emitCleanupStatus(source, cleanupState);
        if (allocation == null) {
            source.sendSuccess(() -> Component.literal("Dungeon Instance Allocation: NONE"), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                "Dungeon Instance Allocation: id=" + allocation.key().instanceId()
                    + " | epoch=" + allocation.key().epoch()
                    + " | cell=" + allocation.cell().index()
                    + " | state=" + allocation.lifecycle().state()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "Dungeon Instance Cell: origin=" + formatPos(allocation.cell().origin())
                    + " | min=" + formatPos(allocation.cell().bounds().min())
                    + " | max=" + formatPos(allocation.cell().bounds().max())
                    + " | quarantine=" + (
                        allocation.lifecycle().quarantineReason().isBlank()
                            ? "NONE"
                            : allocation.lifecycle().quarantineReason()
                    )
            ), false);
            DungeonInstanceAllocationData.InstanceKey generatedOwner = session.instanceKey();
            DungeonInstanceAllocationData.InstanceKey runOwner = manager.getInstanceKey();
            source.sendSuccess(() -> Component.literal(
                "Dungeon Instance Owners: generated=" + formatInstanceKey(generatedOwner)
                    + " | run=" + formatInstanceKey(runOwner)
                    + " | match=" + (
                        allocation.key().equals(generatedOwner) && allocation.key().equals(runOwner)
                            ? "YES"
                            : "NO"
                    )
            ), false);
        }
        GeneratedFloorRuntimeData.GenerationFailureState generationFailure = session.generationFailure();
        if (generationFailure == null) {
            source.sendSuccess(() -> Component.literal("Generation Failure: NONE"), false);
        } else {
            final String failureController = formatNullablePos(generationFailure.controllerPos());
            final String failureConnector = formatNullablePos(generationFailure.connectorPos());
            source.sendSuccess(() -> Component.literal("Generation Failure: code=" + generationFailure.code()
                + " | retryable=" + generationFailure.retryable() + " | attempts=" + generationFailure.attempts()), false);
            source.sendSuccess(() -> Component.literal("Generation Failure Context: controller=" + failureController
                + " | connector=" + failureConnector + " | detail=" + generationFailure.detail()), false);
        }
        List<GeneratedFloorRuntimeData.InvalidGeneratedData> invalidData =
            GeneratedFloorRuntimeData.get(level).getInvalidGeneratedData(dimension);
        source.sendSuccess(() -> Component.literal("Invalid Generated Data: "
            + (invalidData.isEmpty() ? "NONE" : "count=" + invalidData.size())), false);
        for (GeneratedFloorRuntimeData.InvalidGeneratedData issue : invalidData) {
            source.sendSuccess(() -> Component.literal("Invalid Generated Data Issue: owner=" + issue.owner()
                + " | key=" + issue.key() + " | reason=" + issue.reason()), false);
        }
        if (storedGateState == null) {
            source.sendSuccess(() -> Component.literal("Stored Gate State: NONE"), false);
        } else {
            final String storedGateOriginText = formatPos(storedGateState.floorBuildOrigin());
            final int ownedCount = storedGateState.ownedPositions().size();
            final String storedBoundsText = storedGateState.bounds() == null ? "NONE" : formatBounds(storedGateState.bounds());
            source.sendSuccess(() -> Component.literal("Stored Gate State: lastOperation=" + storedGateState.lastOperation() + " | trackedOwnedPositions=" + ownedCount + " | floorBuildOrigin=" + storedGateOriginText), false);
            source.sendSuccess(() -> Component.literal("Stored Gate Bounds: " + storedBoundsText), false);
            source.sendSuccess(() -> Component.literal("Stored Gate Metrics: eligibleAir=" + storedGateState.lastEligibleAirCount() + " | placed=" + storedGateState.lastPlacedCount() + " | removed=" + storedGateState.lastRemovedCount() + " | skippedNonAir=" + storedGateState.lastSkippedNonAirCount()), false);
        }
        source.sendSuccess(() -> Component.literal("Run State: active=" + manager.isActiveRun() + " | completed=" + manager.isRunCompleted() + " | failed=" + manager.isFailed() + " | floor=" + manager.getFloorNumber() + " | mode=" + manager.getFloorModeLabel()), false);
        source.sendSuccess(() -> Component.literal("Run Progress: target=" + manager.getTargetRoomCount() + " | completed=" + manager.getCompletedRoomCount() + " | remaining=" + manager.getRemainingRoomCount() + " | nextFloorEligible=" + manager.isNextFloorEligible()), false);
        source.sendSuccess(() -> Component.literal("Run Portal / Hub: exitPortalActive=" + manager.isExitPortalActive() + " | exitPortalState=" + manager.getExitPortalDebugState(level) + " | hubGateState=" + manager.getHubGateState()), false);
        GeneratedFloorWorldWorkScheduler.Snapshot scheduler = GeneratedFloorWorldWorkScheduler.snapshot(level);
        source.sendSuccess(() -> Component.literal(
            "World Work Scheduler: band=" + scheduler.loadBand()
                + " | averageTickMs=" + String.format(java.util.Locale.ROOT, "%.2f", scheduler.averageTickMs())
                + " | phase=" + scheduler.queuePhase()
                + " | urgency=" + scheduler.urgency()
                + " | queueAgeTicks=" + scheduler.queueAgeTicks()
                + " | cleanupBudget=" + scheduler.cleanupBudget()
                + " | lastPlacementMs=" + String.format(java.util.Locale.ROOT, "%.2f", scheduler.lastPlacementMs())
                + " | instance=" + scheduler.instanceId()
                + " | epoch=" + scheduler.allocationEpoch()
        ), false);
        if (manager.isPresetMode()) {
            source.sendSuccess(() -> Component.literal("Preset State: expected=" + formatNullablePos(manager.getPresetExpectedControllerPos()) + " | completed=" + manager.getPresetCompletedCount() + " | remaining=" + manager.getPresetRemainingCount()), false);
            source.sendSuccess(() -> Component.literal("Preset Order Debug: " + manager.getPresetOrderDebugSummary()), false);
        }
        final String nextAction = cleanupState != null
            ? "Wait for generated cleanup to finish before placing or starting another generated floor."
            : !invalidData.isEmpty()
                ? "Repair or replace the reported malformed generated data before placing, gating, or cleaning this dimension."
                : generationFailure != null
                ? (generationFailure.retryable()
                    ? "Repair the reported generated condition, then reactivate the current tail to retry."
                    : "Automatic generated retries are exhausted; inspect the reported condition, then use generated cleanup/rebuild recovery.")
                : recommendedNextAction(manager, resolvedOrigin, lastPlacement, storedGateState);
        source.sendSuccess(() -> Component.literal("Recommended Next Action: " + nextAction), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }
    private static int previewHubGate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos liveOrigin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        ResolvedGeneratedOrigin resolvedOrigin = resolveGeneratedOrigin(level, player.blockPosition());
        if (resolvedOrigin.origin() == null) {
            source.sendFailure(Component.literal("Generated Hub Gate Preview: FAIL - no live or stored floor_build_origin could be resolved for this dimension."));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Generated Hub Gate Preview"), false);
        emitResolvedOrigin(source, liveOrigin, resolvedOrigin);
        emitHubGateOperation(source, "Hub Gate Preview", previewHubGate(level, resolvedOrigin.origin()));
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }
    private static int closeHubGateCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos liveOrigin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        ResolvedGeneratedOrigin resolvedOrigin = resolveGeneratedOrigin(level, player.blockPosition());
        if (resolvedOrigin.origin() == null) {
            source.sendFailure(Component.literal("Generated Hub Gate Close: FAIL - no live or stored floor_build_origin could be resolved for this dimension."));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        if (!resolvedOrigin.validForMutation()) {
            source.sendFailure(Component.literal("Generated Hub Gate Close: FAIL - resolved floor_build_origin is unsafe for mutation: " + resolvedOrigin.validation()));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Generated Hub Gate Close"), false);
        emitResolvedOrigin(source, liveOrigin, resolvedOrigin);
        HubGateOperationResult result = closeHubGateForGeneratedFloor(level, resolvedOrigin.origin());
        emitHubGateOperation(source, "Hub Gate Close", result);
        if (!result.valid()) {
            source.sendFailure(Component.literal("Generated Hub Gate Close: FAIL - required Fog panel was not created; existing gate ownership was retained."));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("State Mutated: YES - closed the generated-floor hub gate with a tracked Fog panel"), false);
        return Command.SINGLE_SUCCESS;
    }
    private static int openHubGateCommand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos liveOrigin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        ResolvedGeneratedOrigin resolvedOrigin = resolveGeneratedOrigin(level, player.blockPosition());
        if (resolvedOrigin.origin() == null) {
            source.sendFailure(Component.literal("Generated Hub Gate Open: FAIL - no live or stored floor_build_origin could be resolved for this dimension."));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        if (!resolvedOrigin.validForMutation()) {
            source.sendFailure(Component.literal("Generated Hub Gate Open: FAIL - resolved floor_build_origin is unsafe for mutation: " + resolvedOrigin.validation()));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Generated Hub Gate Open"), false);
        emitResolvedOrigin(source, liveOrigin, resolvedOrigin);
        emitHubGateOperation(source, "Hub Gate Open", openHubGateForGeneratedFloor(level, resolvedOrigin.origin()));
        source.sendSuccess(() -> Component.literal("State Mutated: YES - opened the generated-floor hub gate by removing tracked or bounded runtime barrier blocks"), false);
        return Command.SINGLE_SUCCESS;
    }
    private static int debugHubGate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos liveOrigin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        ResolvedGeneratedOrigin resolvedOrigin = resolveGeneratedOrigin(level, player.blockPosition());
        source.sendSuccess(() -> Component.literal("Generated Hub Gate Debug"), false);
        emitResolvedOrigin(source, liveOrigin, resolvedOrigin);
        if (resolvedOrigin.origin() != null) {
            emitHubGateOperation(source, "Hub Gate Preview", previewHubGate(level, resolvedOrigin.origin()));
        }
        GeneratedFloorRuntimeData.HubGateRuntimeState stored = GeneratedFloorRepository.get(level).hydrate().hubGateState();
        if (stored == null) {
            source.sendSuccess(() -> Component.literal("Stored Gate State: NONE"), false);
        } else {
            final int ownedCount = stored.ownedPositions().size();
            final String storedBoundsText = stored.bounds() == null ? "NONE" : formatBounds(stored.bounds());
            source.sendSuccess(() -> Component.literal("Stored Gate State: lastOperation=" + stored.lastOperation() + " | trackedOwnedPositions=" + ownedCount), false);
            source.sendSuccess(() -> Component.literal("Stored Gate Bounds: " + storedBoundsText), false);
            source.sendSuccess(() -> Component.literal("Stored Gate Metrics: eligibleAir=" + stored.lastEligibleAirCount() + " | placed=" + stored.lastPlacedCount() + " | removed=" + stored.lastRemovedCount() + " | skippedNonAir=" + stored.lastSkippedNonAirCount()), false);
        }
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }
    private static ResolvedGeneratedOrigin resolveGeneratedOrigin(ServerLevel level, BlockPos center) {
        BlockPos liveOrigin = findNearestFloorBuildOrigin(level, center, ORIGIN_SCAN_RADIUS);
        if (liveOrigin != null) {
            return new ResolvedGeneratedOrigin(liveOrigin.immutable(), "LIVE_MARKER", "VALID_LIVE_MARKER");
        }
        GeneratedFloorSession session = GeneratedFloorRepository.get(level).hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement lastPlacement = session.placement();
        if (lastPlacement != null) {
            return new ResolvedGeneratedOrigin(
                lastPlacement.floorBuildOrigin().immutable(), "STORED_PLACEMENT",
                lastPlacement.floorBuildOriginValidation(ORIGIN_SCAN_RADIUS)
            );
        }
        GeneratedFloorRuntimeData.HubGateRuntimeState storedGateState = session.hubGateState();
        if (storedGateState != null) {
            return new ResolvedGeneratedOrigin(
                storedGateState.floorBuildOrigin().immutable(), "STORED_GATE_STATE",
                storedGateState.floorBuildOriginValidation(ORIGIN_SCAN_RADIUS)
            );
        }
        return new ResolvedGeneratedOrigin(null, "NONE", "INVALID_NO_ORIGIN");
    }
    private static void emitResolvedOrigin(CommandSourceStack source, BlockPos liveOrigin, ResolvedGeneratedOrigin resolvedOrigin) {
        final String liveOriginText = liveOrigin == null ? "MISSING" : formatPos(liveOrigin);
        final String originResolutionSource = resolvedOrigin.source();
        final String originValidation = resolvedOrigin.validation();
        source.sendSuccess(() -> Component.literal("Live Floor Build Origin: " + liveOriginText), false);
        source.sendSuccess(() -> Component.literal("Origin Resolution: " + originResolutionSource), false);
        source.sendSuccess(() -> Component.literal("Origin Validation: " + originValidation), false);
        if (liveOrigin == null && resolvedOrigin.origin() != null) {
            final String recoveredOriginText = formatPos(resolvedOrigin.origin());
            source.sendSuccess(() -> Component.literal("Recovered Floor Build Origin: " + recoveredOriginText), false);
        }
    }
    private static String recommendedNextAction(
        DungeonRunManager manager,
        ResolvedGeneratedOrigin resolvedOrigin,
        GeneratedFloorRuntimeData.LastGeneratedPlacement lastPlacement,
        GeneratedFloorRuntimeData.HubGateRuntimeState storedGateState
    ) {
        if (lastPlacement != null && manager.isActiveRun() && !manager.isRunCompleted()) {
            return "Resume the active generated floor. Use /tensuradungeon run debug or /tensuradungeon floor linear status before clearing anything.";
        }
        if (lastPlacement != null && manager.isRunCompleted() && manager.isExitPortalActive()) {
            return "Use the exit portal to return to hub, or /tensuradungeon floor generated clear last only if you intentionally want to discard the completed generated floor.";
        }
        if (lastPlacement != null) {
            return "Use /tensuradungeon floor generated clear last to remove the stored generated floor and restore floor_build_origin.";
        }
        if (storedGateState != null && "OPEN".equals(storedGateState.lastOperation()) && resolvedOrigin.origin() != null) {
            return "Use /tensuradungeon floor generated gate close to reblock the hub gate if the run was interrupted.";
        }
        if (storedGateState != null && "CLOSE".equals(storedGateState.lastOperation()) && !manager.isActiveRun()) {
            return "State looks clean. Use /tensuradungeon floor generated place <rooms> to create a new generated floor when ready.";
        }
        if (storedGateState != null && "CLOSE".equals(storedGateState.lastOperation()) && manager.isRunCompleted()) {
            return "Generated floor returned and cleaned up. Use /tensuradungeon floor generated place <rooms> to create a new generated floor when ready.";
        }
        if (!manager.isActiveRun()) {
            return "No stored generated floor remains. Use /tensuradungeon floor generated place <rooms> to create one.";
        }
        return "Inspect /tensuradungeon run debug and /tensuradungeon floor linear status to decide whether to resume, advance, or reset the current run.";
    }

    private static HubGateOperationResult previewHubGate(ServerLevel level, BlockPos floorBuildOrigin) {
        return analyzeHubGate(level, floorBuildOrigin, List.of(), "PREVIEW");
    }

    private static HubGateOperationResult openHubGateForGeneratedFloor(ServerLevel level, BlockPos floorBuildOrigin) {
        return openHubGateForGeneratedFloor(
            level, floorBuildOrigin, GeneratedFloorRepository.get(level)
        );
    }

    private static HubGateOperationResult openHubGateForGeneratedFloor(
        ServerLevel level,
        BlockPos floorBuildOrigin,
        GeneratedFloorRepository repository
    ) {
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.HubGateRuntimeState stored = session.hubGateState();
        if (stored != null && (!stored.ownedPositions().isEmpty() || !stored.visualHandles().isEmpty())) {
            VisualRemovalSummary visualRemoval = releaseVisualHandles(stored.visualHandles());
            int removedBlocks = removeOwnedHubGateBlocks(level, stored.ownedPositions());
            int transitionRemoved = removedBlocks + visualRemoval.removed();
            session.replaceHubGateState(new GeneratedFloorRuntimeData.HubGateRuntimeState(
                floorBuildOrigin.immutable(),
                stored.anchorA(),
                stored.anchorB(),
                stored.bounds(),
                List.copyOf(stored.ownedPositions()),
                visualRemoval.retained(),
                stored.lastEligibleAirCount(),
                0,
                transitionRemoved,
                stored.lastSkippedNonAirCount(),
                "OPEN"
            ));
            repository.flushHubGate(session);
            if (transitionRemoved > 0) playHubGateSound(level, floorBuildOrigin, true);
            int anchorsFound = stored.anchorA() != null && stored.anchorB() != null ? 2 : 0;
            String ownership = visualRemoval.retained().isEmpty() ? "STORED_OWNERSHIP" : "PENDING_VISUAL_REMOVAL";
            return new HubGateOperationResult(
                true, anchorsFound, stored.anchorA(), stored.anchorB(), stored.bounds(),
                stored.lastEligibleAirCount(), List.of(), 0, transitionRemoved,
                stored.lastSkippedNonAirCount(), List.copyOf(stored.ownedPositions()), 0,
                ownership, "OPENED"
            );
        }

        HubGateOperationResult analysis = analyzeHubGate(level, floorBuildOrigin, List.of(), "OPEN");
        if (!analysis.valid()) return analysis;
        int removedBlocks = removeOwnedHubGateBlocks(level, analysis.trackedOwnedPositions());
        session.replaceHubGateState(new GeneratedFloorRuntimeData.HubGateRuntimeState(
            floorBuildOrigin.immutable(), analysis.anchorA(), analysis.anchorB(), analysis.bounds(),
            List.copyOf(analysis.trackedOwnedPositions()), List.of(), analysis.eligibleAirCount(),
            0, removedBlocks, analysis.skippedNonAirCount(), "OPEN"
        ));
        repository.flushHubGate(session);
        if (removedBlocks > 0) playHubGateSound(level, floorBuildOrigin, true);
        return new HubGateOperationResult(
            true, analysis.anchorsFound(), analysis.anchorA(), analysis.anchorB(), analysis.bounds(),
            analysis.eligibleAirCount(), List.copyOf(analysis.eligibleAirPositions()), 0, removedBlocks,
            analysis.skippedNonAirCount(), List.copyOf(analysis.trackedOwnedPositions()),
            analysis.runtimeBarrierCountInBounds(), analysis.ownershipSource(), "OPENED"
        );
    }

    private static HubGateOperationResult closeHubGateForGeneratedFloor(ServerLevel level, BlockPos floorBuildOrigin) {
        return closeHubGateForGeneratedFloor(level, floorBuildOrigin, "dungeon_checkpoint");
    }

    private static HubGateOperationResult closeHubGateForGeneratedFloor(
        ServerLevel level,
        BlockPos floorBuildOrigin,
        String barrierType
    ) {
        return closeHubGateForGeneratedFloor(
            level, floorBuildOrigin, barrierType, GeneratedFloorRepository.get(level)
        );
    }

    private static HubGateOperationResult closeHubGateForGeneratedFloor(
        ServerLevel level,
        BlockPos floorBuildOrigin,
        String barrierType,
        GeneratedFloorRepository repository
    ) {
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.HubGateRuntimeState stored = session.hubGateState();
        List<BlockPos> trackedOwned = stored == null ? List.of() : stored.ownedPositions();
        HubGateOperationResult analysis = analyzeHubGate(level, floorBuildOrigin, trackedOwned, "CLOSE");
        if (!analysis.valid()) return analysis;

        Vec3[] panelCorners = hubGatePanelCorners(analysis.bounds());
        if (panelCorners == null) {
            return new HubGateOperationResult(
                false, analysis.anchorsFound(), analysis.anchorA(), analysis.anchorB(), analysis.bounds(),
                analysis.eligibleAirCount(), List.copyOf(analysis.eligibleAirPositions()), 0, 0,
                analysis.skippedNonAirCount(), List.copyOf(analysis.trackedOwnedPositions()),
                analysis.runtimeBarrierCountInBounds(), analysis.ownershipSource(), "FAILED_REQUIRED_FOG_PANEL_GEOMETRY"
            );
        }
        VisualBarrierHandle newVisual = VisualBarrierHandle.createFog(
            level, panelCorners[0], panelCorners[1], barrierType
        );
        if (newVisual == null) {
            return new HubGateOperationResult(
                false, analysis.anchorsFound(), analysis.anchorA(), analysis.anchorB(), analysis.bounds(),
                analysis.eligibleAirCount(), List.copyOf(analysis.eligibleAirPositions()), 0, 0,
                analysis.skippedNonAirCount(), List.copyOf(analysis.trackedOwnedPositions()),
                analysis.runtimeBarrierCountInBounds(), analysis.ownershipSource(), "FAILED_REQUIRED_FOG_PANEL_CREATION"
            );
        }
        VisualRemovalSummary previousVisuals = releaseVisualHandles(
            stored == null ? List.of() : stored.visualHandles()
        );
        List<VisualBarrierHandle> visualHandles = new ArrayList<>(previousVisuals.retained());
        visualHandles.add(newVisual);
        removeOwnedHubGateBlocks(level, analysis.trackedOwnedPositions());
        int transitionPlaced = 1;
        session.replaceHubGateState(new GeneratedFloorRuntimeData.HubGateRuntimeState(
            floorBuildOrigin.immutable(), analysis.anchorA(), analysis.anchorB(), analysis.bounds(),
            List.of(), List.copyOf(visualHandles), analysis.eligibleAirCount(),
            transitionPlaced, 0, analysis.skippedNonAirCount(),
            "CLOSE"
        ));
        repository.flushHubGate(session);
        if (transitionPlaced > 0) playHubGateSound(level, floorBuildOrigin, false);
        String ownershipSource = previousVisuals.retained().isEmpty()
            ? "FOG_PANEL_LIGHT_BACKED"
            : "FOG_PANEL_WITH_PENDING_LEGACY_VISUAL";
        return new HubGateOperationResult(
            true, analysis.anchorsFound(), analysis.anchorA(), analysis.anchorB(), analysis.bounds(),
            analysis.eligibleAirCount(), List.copyOf(analysis.eligibleAirPositions()), transitionPlaced, 0,
            analysis.skippedNonAirCount(), List.of(), analysis.runtimeBarrierCountInBounds(),
            ownershipSource, "CLOSED"
        );
    }

    private static int removeOwnedHubGateBlocks(ServerLevel level, List<BlockPos> positions) {
        int removed = 0;
        for (BlockPos pos : positions) {
            if (isRuntimeBarrierBlockState(level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        return removed;
    }

    private static VisualRemovalSummary releaseVisualHandles(List<VisualBarrierHandle> handles) {
        List<VisualBarrierHandle> retained = new ArrayList<>();
        int removed = 0;
        for (VisualBarrierHandle handle : handles) {
            VisualBarrierHandle.RemovalResult result = handle.remove();
            if (result == VisualBarrierHandle.RemovalResult.REMOVED) removed++;
            if (result == VisualBarrierHandle.RemovalResult.UNAVAILABLE) retained.add(handle);
        }
        return new VisualRemovalSummary(List.copyOf(retained), removed);
    }

    private static Vec3[] hubGatePanelCorners(RoomControllerBlockEntity.RoomBounds bounds) {
        if (bounds == null) return null;
        if (bounds.getWidth() == 1 && bounds.getDepth() > 1) {
            double plane = bounds.min().getX() + 0.5D;
            return new Vec3[] {
                new Vec3(plane, bounds.min().getY(), bounds.min().getZ()),
                new Vec3(plane, bounds.max().getY() + 1.0D, bounds.max().getZ() + 1.0D)
            };
        }
        if (bounds.getDepth() == 1 && bounds.getWidth() > 1) {
            double plane = bounds.min().getZ() + 0.5D;
            return new Vec3[] {
                new Vec3(bounds.min().getX(), bounds.min().getY(), plane),
                new Vec3(bounds.max().getX() + 1.0D, bounds.max().getY() + 1.0D, plane)
            };
        }
        return null;
    }

    private static void playHubGateSound(ServerLevel level, BlockPos floorBuildOrigin, boolean opening) {
        level.playSound(null, floorBuildOrigin, opening ? ModSoundEvents.HUB_GATE_OPEN.get() : ModSoundEvents.HUB_GATE_CLOSE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static HubGateOperationResult analyzeHubGate(ServerLevel level, BlockPos floorBuildOrigin, List<BlockPos> trackedOwnedPositions, String mode) {
        List<BlockPos> anchors = findNearestHubGateAnchors(level, floorBuildOrigin, HUB_GATE_SCAN_RADIUS);
        if (anchors.size() < 2) {
            return new HubGateOperationResult(false, anchors.size(), null, null, null, 0, List.of(), 0, 0, 0, List.of(), 0, "NONE", "SKIPPED_EXPECTED_TWO_ANCHORS");
        }
        BlockPos anchorA = anchors.get(0);
        BlockPos anchorB = anchors.get(1);
        RoomControllerBlockEntity.RoomBounds gateBounds = hubGateBoundsFromAnchors(anchorA, anchorB);
        int volume = gateVolume(gateBounds);
        if (volume > HUB_GATE_MAX_VOLUME) {
            return new HubGateOperationResult(false, anchors.size(), anchorA, anchorB, gateBounds, 0, List.of(), 0, 0, volume, List.of(), 0, "NONE", "SKIPPED_GATE_TOO_LARGE");
        }
        List<BlockPos> eligibleAir = new ArrayList<>();
        List<BlockPos> runtimeBarrierPositions = new ArrayList<>();
        int skippedNonAir = 0;
        for (BlockPos pos : BlockPos.betweenClosed(gateBounds.min(), gateBounds.max())) {
            BlockPos checkedPos = pos.immutable();
            BlockState state = level.getBlockState(checkedPos);
            if (state.isAir()) {
                eligibleAir.add(checkedPos);
            } else {
                if (isRuntimeBarrierBlockState(state)) {
                    runtimeBarrierPositions.add(checkedPos);
                }
                skippedNonAir++;
            }
        }
        List<BlockPos> trackedOwned = new ArrayList<>();
        for (BlockPos pos : trackedOwnedPositions) {
            if (gateBounds.contains(pos)) {
                trackedOwned.add(pos.immutable());
            }
        }
        List<BlockPos> resolvedOwned = new ArrayList<>();
        String ownershipSource = "NONE";
        if (!trackedOwned.isEmpty()) {
            resolvedOwned.addAll(trackedOwned);
            ownershipSource = "TRACKED_STATE";
        } else if (!runtimeBarrierPositions.isEmpty()) {
            resolvedOwned.addAll(runtimeBarrierPositions);
            ownershipSource = "BOUNDED_RUNTIME_BARRIER_SCAN";
        } else if ("CLOSE".equals(mode)) {
            resolvedOwned.addAll(eligibleAir);
            ownershipSource = "GENERATED_FROM_ELIGIBLE_AIR";
        }
        return new HubGateOperationResult(true, anchors.size(), anchorA, anchorB, gateBounds, eligibleAir.size(), List.copyOf(eligibleAir), 0, 0, skippedNonAir, List.copyOf(resolvedOwned), runtimeBarrierPositions.size(), ownershipSource, mode);
    }

    private static void emitHubGateOperation(CommandSourceStack source, String label, HubGateOperationResult result) {
        final int anchorsFound = result.anchorsFound();
        source.sendSuccess(() -> Component.literal(label + ": anchorsFound=" + anchorsFound), false);
        if (result.bounds() != null && result.anchorA() != null && result.anchorB() != null) {
            final String anchorAText = formatPos(result.anchorA());
            final String anchorBText = formatPos(result.anchorB());
            final String boundsText = formatBounds(result.bounds());
            source.sendSuccess(() -> Component.literal(label + ": anchorA=" + anchorAText + " | anchorB=" + anchorBText), false);
            source.sendSuccess(() -> Component.literal(label + ": bounds=" + boundsText), false);
        }
        final int eligibleAir = result.eligibleAirCount();
        final int placed = result.placedBlockCount();
        final int removed = result.removedBlockCount();
        final int skipped = result.skippedNonAirCount();
        final int runtimeBarrierCount = result.runtimeBarrierCountInBounds();
        final String ownershipSource = result.ownershipSource();
        source.sendSuccess(() -> Component.literal(label + ": eligibleAirPositions=" + eligibleAir), false);
        source.sendSuccess(() -> Component.literal(label + ": runtimeBarrierBlocksInBounds=" + runtimeBarrierCount), false);
        source.sendSuccess(() -> Component.literal(label + ": ownershipSource=" + ownershipSource), false);
        source.sendSuccess(() -> Component.literal(label + ": barriersPlaced=" + placed), false);
        source.sendSuccess(() -> Component.literal(label + ": barriersRemoved=" + removed), false);
        source.sendSuccess(() -> Component.literal(label + ": skippedNonAirBlocks=" + skipped), false);
        final String status = result.status();
        source.sendSuccess(() -> Component.literal(label + ": " + status), false);
    }

    private static boolean isRuntimeBarrierBlockState(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return state.is(Blocks.BARRIER) || VOID_BARRIER_BLOCK_ID.equals(key) || FOG_BARRIER_BLOCK_ID.equals(key);
    }

    private static RoomControllerBlockEntity.RoomBounds hubGateBoundsFromAnchors(BlockPos anchorA, BlockPos anchorB) {
        return new RoomControllerBlockEntity.RoomBounds(
            new BlockPos(
                Math.min(anchorA.getX(), anchorB.getX()),
                Math.min(anchorA.getY(), anchorB.getY()),
                Math.min(anchorA.getZ(), anchorB.getZ())
            ),
            new BlockPos(
                Math.max(anchorA.getX(), anchorB.getX()),
                Math.max(anchorA.getY(), anchorB.getY()),
                Math.max(anchorA.getZ(), anchorB.getZ())
            )
        );
    }

    private static int gateVolume(RoomControllerBlockEntity.RoomBounds bounds) {
        return bounds.getWidth() * bounds.getHeight() * bounds.getDepth();
    }

    private static List<BlockPos> findNearestHubGateAnchors(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> anchors = new ArrayList<>();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos checkedPos = pos.immutable();
            if (level.getBlockState(checkedPos).is(ModBlocks.HUB_GATE_ANCHOR.get())) {
                anchors.add(checkedPos);
            }
        }
        anchors.sort((left, right) -> Double.compare(left.distSqr(center), right.distSqr(center)));
        return anchors;
    }

    private static BlockPos findNearestFloorBuildOrigin(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos checkedPos = pos.immutable();
            if (!level.getBlockState(checkedPos).is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
                continue;
            }
            double distance = checkedPos.distSqr(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = checkedPos;
            }
        }
        return nearest;
    }

    private static TemplateRequirement findTemplate(String name) {
        for (TemplateRequirement template : DEFAULT_ROOM_TEMPLATES) {
            if (template.label().equals(name) || template.resourcePath().equals(name) || template.resourceId().equals(name)) {
                return template;
            }
        }
        return null;
    }

    private static TemplateRequirement requirementFromRollingPlan(
        String templateId,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan runtimePlan
    ) {
        TemplateArchetype archetype = switch (runtimePlan.gameplayRole()) {
            case "COMBAT", "LARGE_COMBAT", "ARENA", "FINALE" -> TemplateArchetype.COMBAT;
            case "INTERSECTION" -> TemplateArchetype.CONNECTOR_HEAVY;
            default -> TemplateArchetype.CORRIDOR;
        };
        boolean terminal = runtimePlan.outgoingConnectorPos() == null;
        return new TemplateRequirement(
            templateId,
            "rooms/linear/" + templateId,
            archetype,
            runtimePlan.outgoingConnectorCount() > 1,
            terminal,
            runtimePlan.safeEligible(),
            terminal
        );
    }

    private static ResourceLocation findConnectorInspectionTemplate(ServerLevel level, String name) {
        TemplateRequirement placementTemplate = findTemplate(name);
        if (placementTemplate != null) {
            return placementTemplate.resourceLocation();
        }
        var authored = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        for (String templateId : authored.templates().keySet()) {
            String resourcePath = "rooms/linear/" + templateId;
            ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, resourcePath);
            if (templateId.equals(name) || resourcePath.equals(name) || resourceId.toString().equals(name)) {
                return resourceId;
            }
        }
        return null;
    }

    private static String knownTemplateSummary() {
        List<String> parts = new ArrayList<>();
        for (TemplateRequirement template : DEFAULT_ROOM_TEMPLATES) {
            parts.add(template.label() + " [" + template.metadataSummary() + "]");
        }
        return String.join(", ", parts);
    }

    static long planSeed(BlockPos origin, Direction direction, int rooms, int spacing, long priorRunSeed) {
        long seed = origin.asLong();
        seed ^= ((long) direction.ordinal()) << 48;
        seed ^= ((long) rooms) << 32;
        seed ^= spacing;
        seed ^= priorRunSeed;
        return seed;
    }

    private static Direction horizontalDirection(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? Direction.NORTH : direction;
    }

    private static String dimensionKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Generated linear floor command requires a player context."));
            return null;
        }
    }

    private static BlockPos subtract(BlockPos left, BlockPos right) {
        return new BlockPos(left.getX() - right.getX(), left.getY() - right.getY(), left.getZ() - right.getZ());
    }

    private static GeneratedSpatialConfiguration resolveGeneratedSpatialConfiguration(ServerLevel level, BlockPos origin) {
        List<BlockPos> anchors = findNearestHubGateAnchors(level, origin, HUB_GATE_SCAN_RADIUS);
        if (anchors.size() < 2) {
            return GeneratedSpatialConfiguration.failed("INVALID_GENERATED_CONFIGURATION_GATE_ANCHORS");
        }
        BlockPos first = anchors.get(0);
        BlockPos second = anchors.get(1);
        int gateX = Math.floorDiv(first.getX() + second.getX(), 2);
        int gateZ = Math.floorDiv(first.getZ() + second.getZ(), 2);
        int deltaX = origin.getX() - gateX;
        int deltaZ = origin.getZ() - gateZ;
        Direction direction;
        if (Math.abs(deltaX) >= Math.abs(deltaZ) && deltaX != 0) {
            direction = deltaX > 0 ? Direction.EAST : Direction.WEST;
        } else if (deltaZ != 0) {
            direction = deltaZ > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return GeneratedSpatialConfiguration.failed("INVALID_GENERATED_CONFIGURATION_GATE_DIRECTION_DEGENERATE");
        }
        RoomControllerBlockEntity.RoomBounds hubBounds = resolveHubSafeZoneBounds(level);
        if (hubBounds == null) {
            return GeneratedSpatialConfiguration.failed("INVALID_GENERATED_CONFIGURATION_HUB_SAFE_ZONE_MISSING");
        }
        if (hubBounds.contains(origin)) {
            return GeneratedSpatialConfiguration.failed("INVALID_GENERATED_CONFIGURATION_ORIGIN_INSIDE_HUB_SAFE_ZONE");
        }
        BlockPos attachmentTarget = switch (direction) {
            case EAST, WEST, SOUTH, NORTH -> origin.immutable();
            default -> null;
        };
        return attachmentTarget == null
            ? GeneratedSpatialConfiguration.failed("INVALID_GENERATED_CONFIGURATION_DIRECTION_NONHORIZONTAL")
            : GeneratedSpatialConfiguration.valid(direction, attachmentTarget);
    }

    private static int emitGeneratedSpatialConfigurationFailure(
        CommandSourceStack source,
        String operation,
        String error
    ) {
        source.sendFailure(Component.literal(operation + ": FAIL - " + error));
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return 0;
    }

    private static BlockPos add(BlockPos left, BlockPos right) {
        return new BlockPos(left.getX() + right.getX(), left.getY() + right.getY(), left.getZ() + right.getZ());
    }

    private static String formatNullablePos(BlockPos pos) {
        return pos == null ? "NONE" : formatPos(pos);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String formatInstanceKey(
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return instanceKey == null
            ? "NONE"
            : instanceKey.instanceId() + "@" + instanceKey.epoch();
    }

    private static String formatBounds(RoomControllerBlockEntity.RoomBounds bounds) {
        return formatPos(bounds.min()) + " -> " + formatPos(bounds.max())
            + " (" + bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth() + ")";
    }

    private static List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> buildGeneratedRoomRuntimePlans(List<LoadedPlannedRoom> plan) {
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> roomPlans = new ArrayList<>();
        for (LoadedPlannedRoom room : plan) {
            roomPlans.add(new GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan(
                room.index(),
                room.plannedControllerPos().immutable(),
                room.template().label(),
                room.template().safeEligible(),
                room.safeRolled(),
                room.rotation().name(),
                room.placementOrigin().immutable(),
                room.bounds(),
                room.selectedConnector().worldPos(),
                room.selectedConnector().facing().name(),
                room.outgoingConnector() == null ? null : room.outgoingConnector().worldPos(),
                room.outgoingConnector() == null ? "NONE" : room.outgoingConnector().facing().name(),
                room.outgoingConnectorCount(), room.gameplayRole(), room.effectiveProfile(), room.objectiveType()
            ));
        }
        return roomPlans;
    }
    private static int countSafeGeneratedRoomPlans(List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> roomPlans) {
        int count = 0;
        for (GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan : roomPlans) {
            if (roomPlan.safeRolled()) {
                count++;
            }
        }
        return count;
    }

    private static String formatControllerList(List<BlockPos> controllers) {
        if (controllers.isEmpty()) {
            return "NONE";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < controllers.size(); i++) {
            parts.add((i + 1) + ":" + formatPos(controllers.get(i)));
        }
        return String.join(" | ", parts);
    }

    private record TemplateRequirement(String label, String resourcePath, TemplateArchetype archetype, boolean turnCapable, boolean terminalCapable, boolean safeEligible, boolean deadEndOnly) {
        TemplateRequirement(String label, String resourcePath) {
            this(label, resourcePath, TemplateArchetype.CORRIDOR, false, true, false, false);
        }

        ResourceLocation resourceLocation() {
            return ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, resourcePath);
        }

        String resourceId() {
            return TensuraDungeonRuntime.MOD_ID + ":" + resourcePath;
        }

        String runtimeResourcePath() {
            return "data/" + TensuraDungeonRuntime.MOD_ID + "/structure/" + resourcePath + ".nbt";
        }

        String metadataSummary() {
            return "archetype=" + archetype.name().toLowerCase() + ", turnCapable=" + turnCapable + ", terminalCapable=" + terminalCapable + ", safeEligible=" + safeEligible + ", deadEndOnly=" + deadEndOnly;
        }
    }
    private enum TemplateArchetype {
        CORRIDOR,
        COMBAT,
        CONNECTOR_HEAVY
    }

    private record TemplateConnector(String markerName, BlockPos localPos, Direction facing, boolean primary, BlockPos worldPos) {
        TemplateConnector withWorldPos(BlockPos placementOrigin) {
            return new TemplateConnector(markerName, localPos, facing, primary, add(placementOrigin, localPos));
        }

        boolean incomingPreferred() {
            return "floor_connector_anchor".equals(markerName);
        }

        boolean outgoingPreferred() {
            return "connector_exit".equals(markerName);
        }

        String summary() {
            return markerName + " local=" + formatPos(localPos) + " facing=" + facing + " primary=" + primary + " incomingPreferred=" + incomingPreferred() + " outgoingPreferred=" + outgoingPreferred() + (worldPos == null ? "" : " world=" + formatPos(worldPos));
        }
    }

    private record LayoutRoomMetrics(
        int width,
        int height,
        int depth,
        int footprint,
        long volume,
        int cost,
        boolean large,
        boolean expensive,
        boolean turn,
        int elevationDelta
    ) {
        String summary() {
            return width + "x" + height + "x" + depth
                + ", footprint=" + footprint
                + ", volume=" + volume
                + ", cost=" + cost
                + ", large=" + large
                + ", expensive=" + expensive
                + ", turn=" + turn
                + ", elevation=" + elevationDelta;
        }
    }
    private record LoadedPlannedRoom(
        int index,
        TemplateRequirement template,
        StructureTemplate structureTemplate,
        Vec3i size,
        Rotation rotation,
        BlockPos placementOrigin,
        RoomControllerBlockEntity.RoomBounds bounds,
        BlockPos targetConnectorWorldPos,
        TemplateConnector selectedConnector,
        TemplateConnector outgoingConnector,
        String outgoingSummary,
        boolean safeRolled,
        BlockPos plannedControllerPos,
        LayoutRoomMetrics layoutMetrics,
        int outgoingConnectorCount,
        String gameplayRole,
        String effectiveProfile,
        String objectiveType
    ) {
        BlockPos selectedConnectorWorldPos() {
            return selectedConnector.worldPos();
        }
    }

    private record GeneratedSpatialConfiguration(
        Direction direction,
        BlockPos attachmentTarget,
        String error
    ) {
        static GeneratedSpatialConfiguration valid(Direction direction, BlockPos attachmentTarget) {
            return new GeneratedSpatialConfiguration(direction, attachmentTarget, "NONE");
        }

        static GeneratedSpatialConfiguration failed(String error) {
            return new GeneratedSpatialConfiguration(null, null, error);
        }

        boolean valid() {
            return direction != null && attachmentTarget != null && "NONE".equals(error);
        }
    }

    private record ResolvedGeneratedOrigin(BlockPos origin, String source, String validation) {
        boolean validForMutation() {
            return validation != null && validation.startsWith("VALID_");
        }
    }

    private record VisualRemovalSummary(List<VisualBarrierHandle> retained, int removed) {
    }

    private record HubGateOperationResult(
        boolean valid,
        int anchorsFound,
        BlockPos anchorA,
        BlockPos anchorB,
        RoomControllerBlockEntity.RoomBounds bounds,
        int eligibleAirCount,
        List<BlockPos> eligibleAirPositions,
        int placedBlockCount,
        int removedBlockCount,
        int skippedNonAirCount,
        List<BlockPos> trackedOwnedPositions,
        int runtimeBarrierCountInBounds,
        String ownershipSource,
        String status
    ) {
    }

    private record PendingHubGateOpen(BlockPos floorBuildOrigin, long releaseGameTime) {
    }

    private record CompositePlanningOverride(
        String visualTheme,
        int requiredRoomIndex,
        String requiredTemplateId
    ) {
        private static CompositePlanningOverride originalSegments() {
            return new CompositePlanningOverride(
                "original",
                2,
                CompositeTemplateDescriptor.ID_PREFIX + "original__00"
            );
        }

        private Map<Integer, String> requiredTemplates(int roomCount) {
            return roomCount >= requiredRoomIndex
                ? Map.of(requiredRoomIndex, requiredTemplateId)
                : Map.of();
        }

        private String validate(
            com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.Snapshot authored,
            int roomCount
        ) {
            if (roomCount < 3 || requiredRoomIndex <= 1 || requiredRoomIndex >= roomCount) {
                return "ROOM_COUNT_HAS_NO_INTERIOR_COMPOSITE_SLOT";
            }
            var metadata = authored.templates().get(requiredTemplateId);
            if (metadata == null) return "TEMPLATE_NOT_DISCOVERED_" + requiredTemplateId;
            if (!metadata.enabled()) return "TEMPLATE_DISABLED_" + requiredTemplateId;
            if (!CompositeTemplateDescriptor.isCompositeId(metadata.id())) {
                return "TEMPLATE_NOT_COMPOSITE_" + requiredTemplateId;
            }
            if (!visualTheme.equals(metadata.visualTheme())) {
                return "TEMPLATE_THEME_MISMATCH_" + metadata.visualTheme();
            }
            return "VALID_COMPOSITE_TEST_OVERRIDE";
        }
    }

    private record PendingPlaceAndStartStartup(
        UUID playerId,
        int requestedFloor,
        int rooms,
        BlockPos floorBuildOrigin,
        long scheduledGameTime,
        long earliestPlacementGameTime,
        long forcedPlacementGameTime,
        long nextCheckGameTime,
        double baselineTickMs
    ) {
        PendingPlaceAndStartStartup withNextCheck(long newNextCheckGameTime) {
            return new PendingPlaceAndStartStartup(
                playerId,
                requestedFloor,
                rooms,
                floorBuildOrigin,
                scheduledGameTime,
                earliestPlacementGameTime,
                forcedPlacementGameTime,
                newNextCheckGameTime,
                baselineTickMs
            );
        }
    }
}
