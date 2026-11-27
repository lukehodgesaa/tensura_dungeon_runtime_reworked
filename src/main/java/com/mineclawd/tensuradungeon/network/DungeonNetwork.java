package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.client.DungeonNavigationClient;
import com.mineclawd.tensuradungeon.client.OverworldRiftRenderer;
import com.mineclawd.tensuradungeon.client.DungeonPreparationClient;
import com.mineclawd.tensuradungeon.client.ExpeditionTableDialogueClient;
import com.mineclawd.tensuradungeon.client.DungeonPreparationLoadoutClient;
import com.mineclawd.tensuradungeon.client.DungeonPreparationPartyClient;
import com.mineclawd.tensuradungeon.client.DungeonPreparationReadyClient;
import com.mineclawd.tensuradungeon.client.DungeonCosmeticClient;
import com.mineclawd.tensuradungeon.client.DungeonExitPromptClient;
import com.mineclawd.tensuradungeon.client.DungeonObjectiveClient;
import com.mineclawd.tensuradungeon.client.CoreSatchelInventoryTab;
import com.mineclawd.tensuradungeon.client.CoreRecoveryClaimClient;
import com.mineclawd.tensuradungeon.client.DungeonUiSnapshotClient;
import com.mineclawd.tensuradungeon.client.DungeonShopClient;
import com.mineclawd.tensuradungeon.client.DungeonBankClient;
import com.mineclawd.tensuradungeon.client.KurobeForgeClient;
import com.mineclawd.tensuradungeon.client.LootSummaryClient;
import com.mineclawd.tensuradungeon.client.SoulOrbHudOverlay;
import com.mineclawd.tensuradungeon.client.SubordinateDebriefClient;
import com.mineclawd.tensuradungeon.client.ResurrectionBraceletActivationClient;
import com.mineclawd.tensuradungeon.client.ReturnWhistleUseClient;
import com.mineclawd.tensuradungeon.client.ReturnWhistleActivationClient;
import com.mineclawd.tensuradungeon.client.DungeonHubMusicController;
import com.mineclawd.tensuradungeon.command.GeneratedLinearConnectorPlacementCommands;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonRunService;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.RewardMailboxData;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationPartyData;
import com.mineclawd.tensuradungeon.runtime.DungeonReturnWhistleChannel;
import com.mineclawd.tensuradungeon.runtime.SoulOrbHaulData;
import com.mineclawd.tensuradungeon.runtime.CoreRecoveryData;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelData;
import com.mineclawd.tensuradungeon.runtime.DungeonCosmeticData;
import com.mineclawd.tensuradungeon.runtime.DungeonShopTransactionService;
import com.mineclawd.tensuradungeon.runtime.DungeonBankTransactionService;
import com.mineclawd.tensuradungeon.runtime.KurobeForgeTransactionService;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateSkillCompat;
import com.mineclawd.tensuradungeon.block.ExpeditionTableInteraction;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonNetwork {
    private DungeonNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TensuraDungeonRuntime.MOD_ID).versioned("1");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(DungeonNavigationPayload.TYPE, DungeonNavigationPayload.STREAM_CODEC, DungeonNavigationClient::handle);
            registrar.playToClient(OverworldRiftPayload.TYPE, OverworldRiftPayload.STREAM_CODEC, OverworldRiftRenderer::handle);
            registrar.playToClient(DungeonExitPromptPayload.TYPE, DungeonExitPromptPayload.STREAM_CODEC, DungeonExitPromptClient::handle);
            registrar.playToClient(
                DungeonPreparationOpenPayload.TYPE,
                DungeonPreparationOpenPayload.STREAM_CODEC,
                DungeonPreparationClient::handle
            );
            registrar.playToClient(
                ExpeditionTableDialogueOpenPayload.TYPE,
                ExpeditionTableDialogueOpenPayload.STREAM_CODEC,
                ExpeditionTableDialogueClient::handle
            );
            registrar.playToClient(
                DungeonPreparationLoadoutSnapshotPayload.TYPE,
                DungeonPreparationLoadoutSnapshotPayload.STREAM_CODEC,
                DungeonPreparationLoadoutClient::handle
            );
            registrar.playToClient(
                DungeonPreparationPartySnapshotPayload.TYPE,
                DungeonPreparationPartySnapshotPayload.STREAM_CODEC,
                DungeonPreparationPartyClient::handle
            );
            registrar.playToClient(
                DungeonPreparationReadyPayload.TYPE,
                DungeonPreparationReadyPayload.STREAM_CODEC,
                DungeonPreparationReadyClient::handle
            );
            registrar.playToClient(
                DungeonPreparationInvitePayload.TYPE,
                DungeonPreparationInvitePayload.STREAM_CODEC,
                DungeonPreparationPartyClient::handleInvite
            );
            registrar.playToClient(
                DungeonPreparationProtectionWarningPayload.TYPE,
                DungeonPreparationProtectionWarningPayload.STREAM_CODEC,
                DungeonPreparationPartyClient::handleProtectionWarning
            );
            registrar.playToClient(DungeonUiSnapshotPayload.TYPE, DungeonUiSnapshotPayload.STREAM_CODEC, DungeonUiSnapshotClient::handle);
            registrar.playToClient(
                DungeonShopSnapshotPayload.TYPE,
                DungeonShopSnapshotPayload.STREAM_CODEC,
                DungeonShopClient::handle
            );
            registrar.playToClient(
                DungeonShopActionResultPayload.TYPE,
                DungeonShopActionResultPayload.STREAM_CODEC,
                DungeonShopClient::handleActionResult
            );
            registrar.playToClient(
                DungeonBankSnapshotPayload.TYPE,
                DungeonBankSnapshotPayload.STREAM_CODEC,
                DungeonBankClient::handle
            );
            registrar.playToClient(
                DungeonBankActionResultPayload.TYPE,
                DungeonBankActionResultPayload.STREAM_CODEC,
                DungeonBankClient::handleActionResult
            );
            registrar.playToClient(
                KurobeForgeSnapshotPayload.TYPE,
                KurobeForgeSnapshotPayload.STREAM_CODEC,
                KurobeForgeClient::handle
            );
            registrar.playToClient(
                KurobeForgeActionResultPayload.TYPE,
                KurobeForgeActionResultPayload.STREAM_CODEC,
                KurobeForgeClient::handleActionResult
            );
            registrar.playToClient(SoulOrbHudPayload.TYPE, SoulOrbHudPayload.STREAM_CODEC, SoulOrbHudOverlay::handle);
            registrar.playToClient(DungeonObjectivePayload.TYPE, DungeonObjectivePayload.STREAM_CODEC, DungeonObjectiveClient::handle);
            registrar.playToClient(
                DungeonCosmeticSnapshotPayload.TYPE,
                DungeonCosmeticSnapshotPayload.STREAM_CODEC,
                DungeonCosmeticClient::handle
            );
            registrar.playToClient(LootSummaryPayload.TYPE, LootSummaryPayload.STREAM_CODEC, LootSummaryClient::handle);
            registrar.playToClient(
                CoreSatchelAccessPayload.TYPE,
                CoreSatchelAccessPayload.STREAM_CODEC,
                CoreSatchelInventoryTab::handle
            );
            registrar.playToClient(
                CoreRecoveryClaimResultPayload.TYPE,
                CoreRecoveryClaimResultPayload.STREAM_CODEC,
                CoreRecoveryClaimClient::handle
            );
            registrar.playToClient(
                ResurrectionBraceletActivationPayload.TYPE,
                ResurrectionBraceletActivationPayload.STREAM_CODEC,
                ResurrectionBraceletActivationClient::handle
            );
            registrar.playToClient(
                ReturnWhistleChannelStatePayload.TYPE,
                ReturnWhistleChannelStatePayload.STREAM_CODEC,
                ReturnWhistleUseClient::handle
            );
            registrar.playToClient(
                ReturnWhistleActivationPayload.TYPE,
                ReturnWhistleActivationPayload.STREAM_CODEC,
                ReturnWhistleActivationClient::handle
            );
            registrar.playToClient(
                SubordinateDebriefPayload.TYPE,
                SubordinateDebriefPayload.STREAM_CODEC,
                SubordinateDebriefClient::handle
            );
            registrar.playToClient(
                DungeonHubMusicStatePayload.TYPE,
                DungeonHubMusicStatePayload.STREAM_CODEC,
                DungeonHubMusicController::handle
            );
        } else {
            registrar.playToClient(DungeonNavigationPayload.TYPE, DungeonNavigationPayload.STREAM_CODEC, null);
            registrar.playToClient(OverworldRiftPayload.TYPE, OverworldRiftPayload.STREAM_CODEC, null);
            registrar.playToClient(DungeonExitPromptPayload.TYPE, DungeonExitPromptPayload.STREAM_CODEC, null);
            registrar.playToClient(
                DungeonPreparationOpenPayload.TYPE,
                DungeonPreparationOpenPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                ExpeditionTableDialogueOpenPayload.TYPE,
                ExpeditionTableDialogueOpenPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonPreparationLoadoutSnapshotPayload.TYPE,
                DungeonPreparationLoadoutSnapshotPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonPreparationPartySnapshotPayload.TYPE,
                DungeonPreparationPartySnapshotPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonPreparationReadyPayload.TYPE,
                DungeonPreparationReadyPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonPreparationInvitePayload.TYPE,
                DungeonPreparationInvitePayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonPreparationProtectionWarningPayload.TYPE,
                DungeonPreparationProtectionWarningPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(DungeonUiSnapshotPayload.TYPE, DungeonUiSnapshotPayload.STREAM_CODEC, null);
            registrar.playToClient(DungeonShopSnapshotPayload.TYPE, DungeonShopSnapshotPayload.STREAM_CODEC, null);
            registrar.playToClient(
                DungeonShopActionResultPayload.TYPE,
                DungeonShopActionResultPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonBankSnapshotPayload.TYPE,
                DungeonBankSnapshotPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonBankActionResultPayload.TYPE,
                DungeonBankActionResultPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                KurobeForgeSnapshotPayload.TYPE,
                KurobeForgeSnapshotPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                KurobeForgeActionResultPayload.TYPE,
                KurobeForgeActionResultPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(SoulOrbHudPayload.TYPE, SoulOrbHudPayload.STREAM_CODEC, null);
            registrar.playToClient(DungeonObjectivePayload.TYPE, DungeonObjectivePayload.STREAM_CODEC, null);
            registrar.playToClient(
                DungeonCosmeticSnapshotPayload.TYPE,
                DungeonCosmeticSnapshotPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(LootSummaryPayload.TYPE, LootSummaryPayload.STREAM_CODEC, null);
            registrar.playToClient(CoreSatchelAccessPayload.TYPE, CoreSatchelAccessPayload.STREAM_CODEC, null);
            registrar.playToClient(CoreRecoveryClaimResultPayload.TYPE, CoreRecoveryClaimResultPayload.STREAM_CODEC, null);
            registrar.playToClient(
                ResurrectionBraceletActivationPayload.TYPE,
                ResurrectionBraceletActivationPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                ReturnWhistleChannelStatePayload.TYPE,
                ReturnWhistleChannelStatePayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                ReturnWhistleActivationPayload.TYPE,
                ReturnWhistleActivationPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                SubordinateDebriefPayload.TYPE,
                SubordinateDebriefPayload.STREAM_CODEC,
                null
            );
            registrar.playToClient(
                DungeonHubMusicStatePayload.TYPE,
                DungeonHubMusicStatePayload.STREAM_CODEC,
                null
            );
        }
        registrar.playToServer(DungeonUiRequestPayload.TYPE, DungeonUiRequestPayload.STREAM_CODEC, DungeonNetwork::handleUiRequest);
        registrar.playToServer(
            ExpeditionTablePrepareRequestPayload.TYPE,
            ExpeditionTablePrepareRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleExpeditionTablePrepareRequest
        );
        registrar.playToServer(
            DungeonPreparationLoadoutActionPayload.TYPE,
            DungeonPreparationLoadoutActionPayload.STREAM_CODEC,
            DungeonNetwork::handlePreparationLoadoutAction
        );
        registrar.playToServer(
            DungeonPreparationPartyActionPayload.TYPE,
            DungeonPreparationPartyActionPayload.STREAM_CODEC,
            DungeonNetwork::handlePreparationPartyAction
        );
        registrar.playToServer(
            DungeonShopOpenRequestPayload.TYPE,
            DungeonShopOpenRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleShopOpenRequest
        );
        registrar.playToServer(
            DungeonShopActionRequestPayload.TYPE,
            DungeonShopActionRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleShopActionRequest
        );
        registrar.playToServer(
            DungeonBankOpenRequestPayload.TYPE,
            DungeonBankOpenRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleBankOpenRequest
        );
        registrar.playToServer(
            DungeonBankActionRequestPayload.TYPE,
            DungeonBankActionRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleBankActionRequest
        );
        registrar.playToServer(
            KurobeForgeOpenRequestPayload.TYPE,
            KurobeForgeOpenRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleKurobeForgeOpenRequest
        );
        registrar.playToServer(
            KurobeForgeActionRequestPayload.TYPE,
            KurobeForgeActionRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleKurobeForgeActionRequest
        );
        registrar.playToServer(
            LootSummaryRequestPayload.TYPE, LootSummaryRequestPayload.STREAM_CODEC, DungeonNetwork::handleLootSummaryRequest
        );
        registrar.playToServer(
            CoreRecoveryClaimRequestPayload.TYPE,
            CoreRecoveryClaimRequestPayload.STREAM_CODEC,
            DungeonNetwork::handleCoreRecoveryClaim
        );
        registrar.playToServer(
            DungeonCosmeticActionPayload.TYPE,
            DungeonCosmeticActionPayload.STREAM_CODEC,
            DungeonNetwork::handleCosmeticAction
        );
        registrar.playToServer(
            ReturnWhistleReleasePayload.TYPE,
            ReturnWhistleReleasePayload.STREAM_CODEC,
            DungeonNetwork::handleReturnWhistleRelease
        );
        registrar.playToServer(
            DungeonExitInteractPayload.TYPE,
            DungeonExitInteractPayload.STREAM_CODEC,
            DungeonNetwork::handleExitPortalInteract
        );
    }

    private static void handleExitPortalInteract(
        DungeonExitInteractPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.mineclawd.tensuradungeon.event.DungeonExitPortalInteractionEvents.openMenuFromClientIntent(player);
            }
        });
    }

    private static void handleExpeditionTablePrepareRequest(
        ExpeditionTablePrepareRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ExpeditionTableInteraction.prepareExpedition(player, payload.tablePos());
            }
        });
    }

    private static void handleReturnWhistleRelease(
        ReturnWhistleReleasePayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DungeonReturnWhistleChannel.interruptIfChanneling(
                    player, "use was released"
                );
            }
        });
    }

    private static void handleCosmeticAction(
        DungeonCosmeticActionPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DungeonCosmeticData data = DungeonCosmeticData.get(player.serverLevel());
            DungeonCosmeticData.MutationResult result = switch (payload.action()) {
                case EQUIP -> data.equip(
                    player.getUUID(),
                    payload.cosmetic(),
                    payload.observedRevision()
                );
            };
            PacketDistributor.sendToPlayer(player, DungeonCosmeticSnapshotPayload.from(
                result.snapshot(),
                result.status(),
                cosmeticActionMessage(result.status())
            ));
        });
    }

    private static void handlePreparationLoadoutAction(
        DungeonPreparationLoadoutActionPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DungeonPreparationLoadoutData data = DungeonPreparationLoadoutData.get(
                player.serverLevel()
            );
            PreparationLoadoutTarget target = resolvePreparationLoadoutTarget(
                player,
                payload.targetId()
            );
            if (target == null) {
                target = PreparationLoadoutTarget.player(player, false);
                PacketDistributor.sendToPlayer(
                    player,
                    preparationLoadoutSnapshot(
                        data,
                        target,
                        "That party member is no longer available."
                    )
                );
                return;
            }
            DungeonPreparationLoadoutData.LoadoutSnapshot snapshot;
            String message = "";
            if (payload.action() == DungeonPreparationLoadoutActionPayload.Action.REFRESH) {
                snapshot = snapshotForTarget(data, target);
            } else if (payload.action().isSkillAction()) {
                DungeonPreparationLoadoutData.SkillMutationResult result;
                if (!target.editable()
                    || target.subordinate() == null
                    || !preparationLoadoutMutationAllowed(player, target)) {
                    result = new DungeonPreparationLoadoutData.SkillMutationResult(
                        DungeonPreparationLoadoutData.SkillMutationStatus.INVALID_TARGET
                    );
                } else {
                    result = switch (payload.action()) {
                        case ASSIGN_ACTIVE_SKILL -> data.selectActiveSkill(
                            target.id(), target.subordinate(), payload.skillId(),
                            payload.inventorySlot(), payload.observedRevision()
                        );
                        case CLEAR_ACTIVE_SKILL -> data.clearActiveSkill(
                            target.id(), target.subordinate(), payload.inventorySlot(),
                            payload.observedRevision()
                        );
                        case ASSIGN_PASSIVE_SKILL -> data.selectPassiveSkill(
                            target.id(), target.subordinate(), payload.skillId(),
                            payload.inventorySlot(), payload.observedRevision()
                        );
                        case CLEAR_PASSIVE_SKILL -> data.clearPassiveSkill(
                            target.id(), target.subordinate(), payload.inventorySlot(),
                            payload.observedRevision()
                        );
                        default -> new DungeonPreparationLoadoutData.SkillMutationResult(
                            DungeonPreparationLoadoutData.SkillMutationStatus.INVALID_TARGET
                        );
                    };
                }
                snapshot = snapshotForTarget(data, target);
                message = preparationSkillMessage(result.status());
                if (result.status() == DungeonPreparationLoadoutData.SkillMutationStatus.APPLIED
                    || result.status() == DungeonPreparationLoadoutData.SkillMutationStatus.CLEARED) {
                    DungeonPreparationPartyData.PartyRecord party =
                        DungeonPreparationPartyData.get(player.serverLevel())
                            .invalidateReadyForMember(target.id());
                    if (party != null) {
                        sendPartySnapshotToOnlineMembers(player, party, "");
                    }
                }
            } else {
                boolean subordinateCategoryAllowed = target.kind()
                    != DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE
                    || payload.category()
                        == DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET
                    || payload.category()
                        == DungeonPreparationLoadoutData.Category.RETURN_WHISTLE;
                DungeonPreparationLoadoutData.MutationResult result;
                if (!target.editable() || !subordinateCategoryAllowed) {
                    result = new DungeonPreparationLoadoutData.MutationResult(
                        DungeonPreparationLoadoutData.MutationStatus.INVALID_TARGET,
                        snapshotForTarget(data, target)
                    );
                } else if (!preparationLoadoutMutationAllowed(player, target)) {
                    result = new DungeonPreparationLoadoutData.MutationResult(
                        DungeonPreparationLoadoutData.MutationStatus.INVALID_TARGET,
                        snapshotForTarget(data, target)
                    );
                } else {
                    if (target.subordinate() != null) {
                        result = payload.action()
                            == DungeonPreparationLoadoutActionPayload.Action.SELECT
                                ? data.selectSubordinateProtection(
                                    target.id(), target.inventoryOwner(),
                                    target.loadoutInventory(), payload.category(),
                                    payload.inventorySlot(), payload.observedRevision()
                                )
                                : data.clearSubordinateProtection(
                                    target.id(), target.inventoryOwner(),
                                    target.loadoutInventory(), payload.category(),
                                    payload.observedRevision()
                                );
                    } else {
                        result = payload.action()
                            == DungeonPreparationLoadoutActionPayload.Action.SELECT
                                ? data.select(
                                    target.id(), target.inventoryOwner(), payload.category(),
                                    payload.inventorySlot(), payload.observedRevision()
                                )
                                : data.clear(
                                    target.id(), target.inventoryOwner(), payload.category(),
                                    payload.observedRevision()
                                );
                    }
                }
                snapshot = result.snapshot();
                message = preparationLoadoutMessage(result.status());
                if (result.status() == DungeonPreparationLoadoutData.MutationStatus.SELECTED
                    || result.status() == DungeonPreparationLoadoutData.MutationStatus.CLEARED) {
                    DungeonPreparationPartyData.PartyRecord party =
                        DungeonPreparationPartyData.get(player.serverLevel())
                            .invalidateReadyForMember(target.inventoryOwner().getUUID());
                    if (party != null) {
                        sendPartySnapshotToOnlineMembers(player, party, "");
                    }
                }
            }
            PacketDistributor.sendToPlayer(
                player,
                preparationLoadoutSnapshot(data, target, snapshot, message)
            );
        });
    }

    private static PreparationLoadoutTarget resolvePreparationLoadoutTarget(
        ServerPlayer viewer,
        UUID targetId
    ) {
        if (targetId == null) {
            return null;
        }
        DungeonPreparationPartyData.PartyRecord party =
            DungeonPreparationPartyData.get(viewer.serverLevel())
                .partyFor(viewer.getUUID())
                .orElse(null);
        if (party == null) {
            return targetId.equals(viewer.getUUID())
                ? PreparationLoadoutTarget.player(viewer, true)
                : null;
        }
        DungeonPreparationPartyData.MemberRecord member = party.members().stream()
            .filter(candidate -> candidate.id().equals(targetId))
            .findFirst()
            .orElse(null);
        if (member == null) {
            return null;
        }
        if (member.kind() == DungeonPreparationPartyData.MemberKind.PLAYER) {
            ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(targetId);
            return target == null
                ? null
                : PreparationLoadoutTarget.player(
                    target,
                    targetId.equals(viewer.getUUID())
                );
        }
        ServerLevel partyLevel = viewer.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, party.dimension())
        );
        LivingEntity subordinate = partyLevel != null
            && partyLevel.getEntity(targetId) instanceof LivingEntity living
                ? living
                : null;
        ServerPlayer owner = viewer.getServer().getPlayerList().getPlayer(party.host());
        if (subordinate == null
            || owner == null
            || TensuraCompat.subordinateOwnerId(subordinate)
                .filter(party.host()::equals)
                .isEmpty()) {
            return null;
        }
        Container subordinateInventory = TensuraCompat.subordinateInventory(subordinate)
            .orElse(null);
        if (subordinateInventory == null) {
            return null;
        }
        return new PreparationLoadoutTarget(
            targetId,
            member.name(),
            DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE,
            owner,
            subordinate,
            subordinateInventory,
            targetId,
            viewer.getUUID().equals(party.host())
        );
    }

    private static DungeonPreparationLoadoutSnapshotPayload preparationLoadoutSnapshot(
        DungeonPreparationLoadoutData data,
        PreparationLoadoutTarget target,
        String message
    ) {
        return preparationLoadoutSnapshot(
            data,
            target,
            snapshotForTarget(data, target),
            message
        );
    }

    private static DungeonPreparationLoadoutSnapshotPayload preparationLoadoutSnapshot(
        DungeonPreparationLoadoutData data,
        PreparationLoadoutTarget target,
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot,
        String message
    ) {
        List<DungeonPreparationLoadoutSnapshotPayload.Equipment> equipment =
            target.subordinate() == null
                ? List.of()
                : List.of(
                    equipment(
                        DungeonPreparationLoadoutData.Category.PRIMARY_SWORD,
                        target.subordinate().getMainHandItem()
                    ),
                    equipment(
                        DungeonPreparationLoadoutData.Category.OFF_HAND,
                        target.subordinate().getOffhandItem()
                    ),
                    equipment(
                        DungeonPreparationLoadoutData.Category.HELMET,
                        target.subordinate().getItemBySlot(EquipmentSlot.HEAD)
                    ),
                    equipment(
                        DungeonPreparationLoadoutData.Category.CHESTPLATE,
                        target.subordinate().getItemBySlot(EquipmentSlot.CHEST)
                    ),
                    equipment(
                        DungeonPreparationLoadoutData.Category.LEGGINGS,
                        target.subordinate().getItemBySlot(EquipmentSlot.LEGS)
                    ),
                    equipment(
                        DungeonPreparationLoadoutData.Category.BOOTS,
                        target.subordinate().getItemBySlot(EquipmentSlot.FEET)
                    )
                );
        TensuraSubordinateSkillCompat.SkillProjection skillProjection =
            target.subordinate() == null
                ? new TensuraSubordinateSkillCompat.SkillProjection(true, List.of())
                : TensuraSubordinateSkillCompat.learnedSkills(target.subordinate());
        TensuraSubordinateSkillCompat.AbilityProjection abilityProjection =
            target.subordinate() == null
                ? new TensuraSubordinateSkillCompat.AbilityProjection(true, List.of())
                : TensuraSubordinateSkillCompat.currentAbilities(target.subordinate());
        TensuraCompat.MagiculeSnapshot magicule = target.subordinate() == null
            ? null
            : TensuraCompat.readMagiculeSnapshot(target.subordinate()).orElse(null);
        List<DungeonPreparationLoadoutSnapshotPayload.AbilitySlot> currentAbilities =
            abilityProjection.slots().stream()
                .map(ability -> new DungeonPreparationLoadoutSnapshotPayload.AbilitySlot(
                    ability.id(), ability.name(), ability.icon(), skillDetails(ability.details())
                ))
                .toList();
        List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> learnedSkills =
            skillProjection.skills().stream()
                .map(skill -> new DungeonPreparationLoadoutSnapshotPayload.SkillIcon(
                    skill.id(), skill.name(), skill.icon(), skill.passive(),
                    skillDetails(skill.details())
                ))
                .toList();
        List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> selectedPassiveSkills =
            data.selectedPassiveSkills(target.id()).stream()
                .map(skillId -> learnedSkills.stream()
                    .filter(skill -> skill.id().equals(skillId) && skill.passive())
                    .findFirst()
                    .orElse(null))
                .filter(skill -> skill != null)
                .toList();
        return DungeonPreparationLoadoutSnapshotPayload.from(
            target.id(),
            target.name(),
            target.kind(),
            target.editable(),
            snapshot,
            equipment,
            inventoryCandidates(target),
            reservedInventorySlots(data, target),
            currentAbilities,
            learnedSkills,
            selectedPassiveSkills,
            skillProjection.available() && abilityProjection.available(),
            message,
            magicule == null ? 0.0D : magicule.current(),
            magicule == null ? 0.0D : magicule.maximum(),
            magicule != null
        );
    }

    private static DungeonPreparationLoadoutSnapshotPayload.SkillDetails skillDetails(
        TensuraSubordinateSkillCompat.SkillDetails details
    ) {
        if (details == null) {
            return DungeonPreparationLoadoutSnapshotPayload.SkillDetails.unavailable();
        }
        return new DungeonPreparationLoadoutSnapshotPayload.SkillDetails(
            details.mode(),
            details.magiculeCost(),
            details.cooldownSeconds(),
            details.magiculeCostAvailable(),
            details.cooldownAvailable(),
            details.resourceAvailable(),
            details.enoughMagicule()
        );
    }

    private static DungeonPreparationLoadoutData.LoadoutSnapshot snapshotForTarget(
        DungeonPreparationLoadoutData data,
        PreparationLoadoutTarget target
    ) {
        return target.kind() == DungeonPreparationLoadoutSnapshotPayload.TargetKind.PLAYER
            ? data.snapshot(target.inventoryOwner())
            : data.snapshotSubordinate(
                target.id(), target.inventoryOwner().getUUID(),
                target.inventoryOwner().serverLevel(), target.loadoutInventory()
            );
    }

    private static List<DungeonPreparationLoadoutSnapshotPayload.InventoryCandidate>
    inventoryCandidates(PreparationLoadoutTarget target) {
        if (target.subordinate() == null) {
            return List.of();
        }
        List<DungeonPreparationLoadoutSnapshotPayload.InventoryCandidate> candidates =
            new ArrayList<>();
        for (int slot = 0;
            slot < target.loadoutInventory().getContainerSize()
                && candidates.size() < DungeonPreparationLoadoutSnapshotPayload.MAX_INVENTORY_CANDIDATES;
            slot++) {
            ItemStack stack = target.loadoutInventory().getItem(slot);
            if (!stack.isEmpty()
                && (DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET.eligible(stack)
                    || DungeonPreparationLoadoutData.Category.RETURN_WHISTLE.eligible(stack))) {
                candidates.add(new DungeonPreparationLoadoutSnapshotPayload.InventoryCandidate(
                    slot, stack
                ));
            }
        }
        for (int slot = 0;
            slot < target.inventoryOwner().getInventory().getContainerSize()
                && candidates.size() < DungeonPreparationLoadoutSnapshotPayload.MAX_INVENTORY_CANDIDATES;
            slot++) {
            ItemStack stack = target.inventoryOwner().getInventory().getItem(slot);
            if (!stack.isEmpty()
                && (DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET.eligible(stack)
                    || DungeonPreparationLoadoutData.Category.RETURN_WHISTLE.eligible(stack))) {
                candidates.add(new DungeonPreparationLoadoutSnapshotPayload.InventoryCandidate(
                    DungeonPreparationLoadoutData.sharedPlayerInventorySlot(slot),
                    stack
                ));
            }
            if (candidates.size()
                == DungeonPreparationLoadoutSnapshotPayload.MAX_INVENTORY_CANDIDATES) {
                break;
            }
        }
        return List.copyOf(candidates);
    }

    private static List<Integer> reservedInventorySlots(
        DungeonPreparationLoadoutData data,
        PreparationLoadoutTarget target
    ) {
        if (target.subordinate() != null) {
            return data.reservedProtectionCandidateSlots(
                target.id(),
                target.inventoryOwner().getUUID(),
                target.inventoryOwner().serverLevel()
            ).stream().toList();
        }
        return data.reservedInventorySlotsForParty(
            target.inventoryOwner().getUUID(),
            target.id(),
            DungeonPreparationPartyData.get(target.inventoryOwner().serverLevel())
                .partyFor(target.inventoryOwner().getUUID())
                .map(party -> party.members().stream()
                    .map(DungeonPreparationPartyData.MemberRecord::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .orElse(Set.of(target.inventoryOwner().getUUID()))
        ).stream().toList();
    }

    private static boolean preparationLoadoutMutationAllowed(
        ServerPlayer viewer,
        PreparationLoadoutTarget target
    ) {
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(viewer.serverLevel())
                .forPlayer(target.inventoryOwner().getUUID())
                .orElse(null);
        return allocation == null
            || allocation.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.ALLOCATED;
    }

    private static boolean preparationPartyFrozen(ServerPlayer player) {
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(player.serverLevel())
                .forPlayer(player.getUUID())
                .orElse(null);
        if (allocation == null) {
            return false;
        }
        return allocation.lifecycle().state()
            != DungeonInstanceAllocationData.LifecycleState.ALLOCATED
            && !allocation.lifecycle().state().reusable();
    }

    private static boolean preparationPartyActionAllowedWhileFrozen(
        DungeonPreparationPartyActionPayload.Action action,
        DungeonPreparationPartyData data,
        ServerPlayer player
    ) {
        boolean actorReady = data.partyFor(player.getUUID())
            .flatMap(party -> party.members().stream()
                .filter(member -> member.id().equals(player.getUUID()))
                .filter(member -> member.kind() == DungeonPreparationPartyData.MemberKind.PLAYER)
                .findFirst())
            .map(DungeonPreparationPartyData.MemberRecord::ready)
            .orElse(true);
        return preparationPartyActionAllowedWhileFrozen(action, actorReady);
    }

    static boolean preparationPartyActionAllowedWhileFrozen(
        DungeonPreparationPartyActionPayload.Action action,
        boolean actorReady
    ) {
        return switch (action) {
            case REFRESH, START_DUNGEON -> true;
            case TOGGLE_READY, CONFIRM_READY -> !actorReady;
            default -> false;
        };
    }

    private static String preparationLoadoutValidation(ServerPlayer player, int selectedFloor) {
        DungeonFloorProgressData progress = DungeonFloorProgressData.get(player.serverLevel());
        if (!progress.isFloorUnlocked(player.getUUID(), selectedFloor)) {
            return "Floor " + selectedFloor + " is locked.";
        }
        DungeonPreparationLoadoutData data = DungeonPreparationLoadoutData.get(player.serverLevel());
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot = data.snapshot(player);
        if (!player.getUUID().equals(data.inventoryOwner(player.getUUID()))) {
            return "Loadout ownership could not be verified.";
        }
        boolean invalid = snapshot.selections().values().stream()
            .flatMap(List::stream)
            .anyMatch(selection -> !selection.valid());
        if (!invalid) {
            return "";
        }
        List<String> invalidSelections = new ArrayList<>();
        for (DungeonPreparationLoadoutData.Category category
            : DungeonPreparationLoadoutData.Category.values()) {
            for (DungeonPreparationLoadoutData.SelectionView selection
                : snapshot.category(category)) {
                if (!selection.valid() && invalidSelections.size() < 3) {
                    invalidSelections.add(
                        loadoutCategoryLabel(category)
                            + ": "
                            + selection.stack().getHoverName().getString()
                    );
                }
            }
        }
        String message = "Review loadout: " + String.join("; ", invalidSelections)
            + " changed or is missing.";
        return message.length() <= 155 ? message : message.substring(0, 152) + "...";
    }

    private static String loadoutCategoryLabel(DungeonPreparationLoadoutData.Category category) {
        return switch (category) {
            case PRIMARY_SWORD -> "Main Hand";
            case OFF_HAND -> "Off Hand";
            case HELMET -> "Helmet";
            case CHESTPLATE -> "Chestplate";
            case LEGGINGS -> "Leggings";
            case BOOTS -> "Boots";
            case RESURRECTION_BRACELET -> "Resurrection Bracelet";
            case RETURN_WHISTLE -> "Return Whistle";
            case HEALTH_RECOVERY -> "Health Recovery";
            case MAGICULE_RECOVERY -> "Magicule Recovery";
            case UTILITY -> "Utility";
            case FIELD_RATIONS -> "Field Rations";
        };
    }

    private static String startDungeonPreparation(ServerPlayer player) {
        DungeonPreparationPartyData.PartyRecord party =
            DungeonPreparationPartyData.get(player.serverLevel())
                .partyFor(player.getUUID())
                .orElse(null);
        if (party == null || !party.host().equals(player.getUUID())) {
            return "Only the party host can start the dungeon.";
        }
        if (party.members().stream().anyMatch(member ->
            member.kind() == DungeonPreparationPartyData.MemberKind.PLAYER
                && !member.id().equals(player.getUUID()))) {
            return "Invited-player dispatch is not available yet; this slice supports the host and owned subordinates.";
        }
        if (party.members().stream().anyMatch(member -> !member.ready())) {
            return "Every retained participant must be READY before starting the dungeon.";
        }
        int floor = DungeonFloorProgressData.get(player.serverLevel())
            .getSelectedFloor(player.getUUID());
        FrozenPreparationValidation validation = validateFrozenPreparation(
            player,
            party,
            floor
        );
        if (!validation.failure().isBlank()) {
            return validation.failure();
        }
        DungeonFloorProgressData.get(player.serverLevel())
            .getOrPrepareModifierPlan(player.getUUID(), floor);
        int preparationResult = GeneratedLinearConnectorPlacementCommands.prepareFrozenRoster(
            player,
            validation.participants()
        );
        if (preparationResult == 0) {
            return "Dungeon dispatch was rejected by server validation; see chat for the exact reason.";
        }
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(player.serverLevel())
                .forPlayer(player.getUUID())
                .orElse(null);
        if (allocation == null) {
            return "Dungeon preparation did not retain its instance allocation.";
        }
        return switch (allocation.lifecycle().state()) {
            case PREPARING -> "Preparing Dungeon: safe room and successor queued.";
            case READY -> "Dungeon is READY; press ENTER DUNGEON.";
            case ACTIVE -> "Dungeon dispatch admitted.";
            default -> "Dungeon preparation state: " + allocation.lifecycle().state();
        };
    }

    private static FrozenPreparationValidation validateFrozenPreparation(
        ServerPlayer host,
        DungeonPreparationPartyData.PartyRecord party,
        int floor
    ) {
        String hostFailure = preparationLoadoutValidation(host, floor);
        if (!hostFailure.isBlank()) {
            return FrozenPreparationValidation.failure(hostFailure);
        }
        DungeonPreparationLoadoutData loadouts =
            DungeonPreparationLoadoutData.get(host.serverLevel());
        List<DungeonInstanceAllocationData.FrozenParticipant> participants = new ArrayList<>();
        for (int index = 0; index < party.members().size(); index++) {
            DungeonPreparationPartyData.MemberRecord member = party.members().get(index);
            if (index == 0 && (!member.id().equals(host.getUUID())
                || member.kind() != DungeonPreparationPartyData.MemberKind.PLAYER)) {
                return FrozenPreparationValidation.failure(
                    "The host is not the first authoritative roster participant."
                );
            }
            DungeonPreparationLoadoutData.LoadoutSnapshot snapshot;
            DungeonInstanceAllocationData.FrozenParticipantKind kind;
            UUID ownerId;
            if (member.kind() == DungeonPreparationPartyData.MemberKind.PLAYER) {
                if (!member.id().equals(host.getUUID())) {
                    return FrozenPreparationValidation.failure(
                        "Invited-player dispatch is not available in this slice."
                    );
                }
                snapshot = loadouts.snapshot(host);
                kind = DungeonInstanceAllocationData.FrozenParticipantKind.HUMAN;
                ownerId = host.getUUID();
            } else {
                PreparationLoadoutTarget target = resolvePreparationLoadoutTarget(host, member.id());
                if (target == null || target.subordinate() == null
                    || !target.inventoryOwner().getUUID().equals(host.getUUID())) {
                    return FrozenPreparationValidation.failure(
                        member.name() + " is unavailable or is no longer owned by the host."
                    );
                }
                if (!member.id().equals(loadouts.inventoryOwner(member.id()))) {
                    return FrozenPreparationValidation.failure(
                        member.name() + " has an invalid loadout inventory owner."
                    );
                }
                snapshot = loadouts.snapshotSubordinate(
                    member.id(), host.getUUID(), host.serverLevel(), target.loadoutInventory()
                );
                kind = DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE;
                ownerId = host.getUUID();
                String skillFailure = loadouts.validateSubordinateSkillLoadout(
                    member.id(), target.subordinate()
                );
                if (!skillFailure.isBlank()) {
                    return FrozenPreparationValidation.failure(skillFailure);
                }
            }
            boolean invalidSelection = snapshot.selections().values().stream()
                .flatMap(List::stream)
                .anyMatch(selection -> !selection.valid());
            if (invalidSelection) {
                return FrozenPreparationValidation.failure(
                    "Review " + member.name() + "'s loadout; a selected inventory item changed or is missing."
                );
            }
            participants.add(new DungeonInstanceAllocationData.FrozenParticipant(
                member.id(),
                member.name(),
                kind,
                ownerId,
                index + 1,
                snapshot.revision()
            ));
        }
        return new FrozenPreparationValidation(List.copyOf(participants), "");
    }

    private record FrozenPreparationValidation(
        List<DungeonInstanceAllocationData.FrozenParticipant> participants,
        String failure
    ) {
        private static FrozenPreparationValidation failure(String message) {
            return new FrozenPreparationValidation(List.of(), message);
        }
    }

    private static DungeonPreparationLoadoutSnapshotPayload.Equipment equipment(
        DungeonPreparationLoadoutData.Category category,
        net.minecraft.world.item.ItemStack stack
    ) {
        return new DungeonPreparationLoadoutSnapshotPayload.Equipment(category, stack);
    }

    private record PreparationLoadoutTarget(
        UUID id,
        String name,
        DungeonPreparationLoadoutSnapshotPayload.TargetKind kind,
        ServerPlayer inventoryOwner,
        LivingEntity subordinate,
        Container loadoutInventory,
        UUID inventoryId,
        boolean editable
    ) {
        private static PreparationLoadoutTarget player(
            ServerPlayer player,
            boolean editable
        ) {
            return new PreparationLoadoutTarget(
                player.getUUID(),
                player.getGameProfile().getName(),
                DungeonPreparationLoadoutSnapshotPayload.TargetKind.PLAYER,
                player,
                null,
                player.getInventory(),
                player.getUUID(),
                editable
            );
        }
    }

    private static void handlePreparationPartyAction(
        DungeonPreparationPartyActionPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DungeonPreparationPartyData data = DungeonPreparationPartyData.get(player.serverLevel());
            DungeonPreparationPartyData.MutationResult result = null;
            String message = "";
            if (preparationPartyFrozen(player)
                && !preparationPartyActionAllowedWhileFrozen(
                    payload.action(), data, player
                )) {
                PacketDistributor.sendToPlayer(
                    player,
                    DungeonPreparationPartySnapshotFactory.create(
                        player,
                        "Dungeon preparation is already in progress; the roster and loadout are frozen."
                    )
                );
                return;
            }
            switch (payload.action()) {
                case REFRESH -> {
                    if (data.partyFor(player.getUUID()).isEmpty()) {
                        data.open(player, player.blockPosition());
                    }
                }
                case INVITE_PLAYER -> {
                    result = data.invite(player, payload.targetId(), payload.observedRevision());
                    if (result.status() == DungeonPreparationPartyData.MutationStatus.INVITED) {
                        ServerPlayer target = player.getServer().getPlayerList().getPlayer(payload.targetId());
                        if (target != null) {
                            PacketDistributor.sendToPlayer(
                                target,
                                new DungeonPreparationInvitePayload(
                                    player.getUUID(),
                                    player.getGameProfile().getName()
                                )
                            );
                        }
                    }
                }
                case ADD_SUBORDINATE -> {
                    DungeonPreparationPartySnapshotPayload current =
                        DungeonPreparationPartySnapshotFactory.create(player, "");
                    DungeonPreparationPartySnapshotPayload.Candidate candidate =
                        current.subordinateCandidates().stream()
                            .filter(entry -> entry.id().equals(payload.targetId()))
                            .findFirst()
                            .orElse(null);
                    result = candidate == null
                        ? new DungeonPreparationPartyData.MutationResult(
                            DungeonPreparationPartyData.MutationStatus.INVALID_CANDIDATE,
                            data.partyFor(player.getUUID()).orElse(null)
                        )
                        : data.addSubordinate(
                            player,
                            candidate.id(),
                            candidate.name(),
                            payload.observedRevision()
                        );
                }
                case REMOVE_MEMBER -> result = data.remove(
                    player,
                    payload.targetId(),
                    payload.observedRevision()
                );
                case TOGGLE_READY -> {
                    DungeonPreparationPartyData.PartyRecord party =
                        data.partyFor(player.getUUID()).orElse(null);
                    int floor = party == null
                        ? 1
                        : DungeonFloorProgressData.get(player.serverLevel())
                            .getSelectedFloor(party.host());
                    boolean becomingReady = party != null && party.members().stream()
                        .filter(member -> member.id().equals(player.getUUID()))
                        .findFirst()
                        .map(member -> !member.ready())
                        .orElse(false);
                    String loadoutFailure = becomingReady
                        ? preparationLoadoutValidation(player, floor)
                        : "";
                    if (party != null && party.revision() != payload.observedRevision()) {
                        result = new DungeonPreparationPartyData.MutationResult(
                            DungeonPreparationPartyData.MutationStatus.STALE_REVISION,
                            party
                        );
                    } else if (!loadoutFailure.isBlank()) {
                        message = loadoutFailure;
                    } else if (!becomingReady) {
                        result = data.toggleReady(
                            player,
                            floor,
                            payload.observedRevision(),
                            Set.of()
                        );
                    } else {
                        ProtectionReadiness readiness = protectionReadiness(player, party);
                        if (!readiness.failure().isBlank()) {
                            message = readiness.failure();
                        } else if (readiness.warningRequired()) {
                            PacketDistributor.sendToPlayer(
                                player,
                                readiness.payload(party.revision())
                            );
                            return;
                        } else {
                            result = data.toggleReady(
                                player,
                                floor,
                                payload.observedRevision(),
                                readiness.verifiedSubordinates()
                            );
                        }
                    }
                }
                case CONFIRM_READY -> {
                    DungeonPreparationPartyData.PartyRecord party =
                        data.partyFor(player.getUUID()).orElse(null);
                    if (party == null) {
                        result = new DungeonPreparationPartyData.MutationResult(
                            DungeonPreparationPartyData.MutationStatus.NO_PARTY,
                            null
                        );
                        break;
                    }
                    if (party.revision() != payload.observedRevision()) {
                        result = new DungeonPreparationPartyData.MutationResult(
                            DungeonPreparationPartyData.MutationStatus.STALE_REVISION,
                            party
                        );
                        break;
                    }
                    boolean alreadyReady = party.members().stream()
                        .filter(member -> member.id().equals(player.getUUID()))
                        .findFirst()
                        .map(DungeonPreparationPartyData.MemberRecord::ready)
                        .orElse(false);
                    if (alreadyReady) {
                        message = "Loadout is already confirmed.";
                        break;
                    }
                    int floor = DungeonFloorProgressData.get(player.serverLevel())
                        .getSelectedFloor(party.host());
                    String loadoutFailure = preparationLoadoutValidation(player, floor);
                    if (!loadoutFailure.isBlank()) {
                        message = loadoutFailure;
                        break;
                    }
                    ProtectionReadiness readiness = protectionReadiness(player, party);
                    if (!readiness.failure().isBlank()) {
                        message = readiness.failure();
                    } else if (readiness.warningRequired()
                        && !readiness.confirmationId().equals(payload.targetId())) {
                        PacketDistributor.sendToPlayer(
                            player,
                            readiness.payload(party.revision())
                        );
                        return;
                    } else {
                        result = data.toggleReady(
                            player,
                            floor,
                            payload.observedRevision(),
                            readiness.verifiedSubordinates()
                        );
                    }
                }
                case START_DUNGEON -> message = startDungeonPreparation(player);
                case ACCEPT_INVITE, DECLINE_INVITE -> result = data.respond(
                    player,
                    payload.targetId(),
                    payload.action() == DungeonPreparationPartyActionPayload.Action.ACCEPT_INVITE
                );
            }
            if (result != null && message.isBlank()) {
                message = preparationPartyMessage(result.status());
            }
            if (result != null
                && result.status() == DungeonPreparationPartyData.MutationStatus.REMOVED) {
                DungeonPreparationLoadoutData.get(player.serverLevel())
                    .releaseSubordinateLoadout(payload.targetId(), player.getUUID());
                ServerPlayer removed = player.getServer().getPlayerList().getPlayer(payload.targetId());
                if (removed != null) {
                    data.open(removed, removed.blockPosition());
                    PacketDistributor.sendToPlayer(
                        removed,
                        DungeonPreparationPartySnapshotFactory.create(
                            removed,
                            "You were removed from the briefing party."
                        )
                    );
                }
            }
            DungeonPreparationPartyData.PartyRecord party = result != null
                ? result.party()
                : data.partyFor(player.getUUID()).orElse(null);
            if (party == null) {
                PacketDistributor.sendToPlayer(
                    player,
                    DungeonPreparationPartySnapshotFactory.create(player, message)
                );
                return;
            }
            sendPartySnapshotToOnlineMembers(player, party, message);
        });
    }

    private static void sendPartySnapshotToOnlineMembers(
        ServerPlayer actor,
        DungeonPreparationPartyData.PartyRecord party,
        String actorMessage
    ) {
        for (DungeonPreparationPartyData.MemberRecord member : party.members()) {
            if (member.kind() != DungeonPreparationPartyData.MemberKind.PLAYER) continue;
            ServerPlayer online = actor.getServer().getPlayerList().getPlayer(member.id());
            if (online != null) {
                PacketDistributor.sendToPlayer(
                    online,
                    DungeonPreparationPartySnapshotFactory.create(
                        online,
                        online.getUUID().equals(actor.getUUID()) ? actorMessage : ""
                    )
                );
            }
        }
    }

    private static ProtectionReadiness protectionReadiness(
        ServerPlayer actor,
        DungeonPreparationPartyData.PartyRecord party
    ) {
        if (party == null) {
            return ProtectionReadiness.failure("No active briefing party was found.");
        }
        boolean actorIsHost = actor.getUUID().equals(party.host());
        DungeonPreparationLoadoutData loadouts =
            DungeonPreparationLoadoutData.get(actor.serverLevel());
        Set<UUID> verifiedSubordinates = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder confirmation = new StringBuilder()
            .append(party.revision())
            .append('|')
            .append(actor.getUUID());
        boolean braceletMissing = false;
        boolean whistleMissing = false;

        for (DungeonPreparationPartyData.MemberRecord member : party.members()) {
            boolean actorProfile = member.kind()
                == DungeonPreparationPartyData.MemberKind.PLAYER
                && member.id().equals(actor.getUUID());
            boolean ownedSubordinate = actorIsHost
                && member.kind() == DungeonPreparationPartyData.MemberKind.SUBORDINATE;
            if (!actorProfile && !ownedSubordinate) {
                continue;
            }

            DungeonPreparationLoadoutData.LoadoutSnapshot snapshot;
            if (actorProfile) {
                snapshot = loadouts.snapshot(actor);
            } else {
                PreparationLoadoutTarget target = resolvePreparationLoadoutTarget(
                    actor,
                    member.id()
                );
                if (target == null
                    || target.kind()
                        != DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE) {
                    return ProtectionReadiness.failure(
                        member.name()
                            + " is unavailable or is no longer owned by the host; review the roster."
                    );
                }
                verifiedSubordinates.add(member.id());
                snapshot = loadouts.snapshotSubordinate(
                    member.id(), target.inventoryOwner().getUUID(),
                    target.inventoryOwner().serverLevel(), target.loadoutInventory()
                );
            }

            boolean hasBracelet = hasValidProtection(
                snapshot,
                DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET
            );
            boolean hasWhistle = hasValidProtection(
                snapshot,
                DungeonPreparationLoadoutData.Category.RETURN_WHISTLE
            );
            confirmation.append('|')
                .append(member.id())
                .append(':')
                .append(snapshot.revision())
                .append(':')
                .append(hasBracelet)
                .append(':')
                .append(hasWhistle);
            if (hasBracelet && hasWhistle) {
                continue;
            }
            braceletMissing |= !hasBracelet;
            whistleMissing |= !hasWhistle;
            String missing = !hasBracelet && !hasWhistle
                ? "a Resurrection Bracelet and a Return Whistle"
                : !hasBracelet ? "a Resurrection Bracelet" : "a Return Whistle";
            warnings.add(member.name() + " has not selected " + missing + ".");
        }

        UUID confirmationId = UUID.nameUUIDFromBytes(
            confirmation.toString().getBytes(StandardCharsets.UTF_8)
        );
        if (warnings.isEmpty()) {
            return new ProtectionReadiness(
                confirmationId,
                "",
                verifiedSubordinates,
                ""
            );
        }
        String consequence = (braceletMissing
            ? " A missing Bracelet means no Bracelet death protection."
            : "")
            + (whistleMissing
                ? " A missing Whistle means no seven-second emergency departure option."
                : "");
        String scope = actorIsHost && !verifiedSubordinates.isEmpty()
            ? " Continue to mark you and your retained subordinates Ready, or review the loadout."
            : " Continue to mark yourself Ready, or review the loadout.";
        return new ProtectionReadiness(
            confirmationId,
            String.join(" ", warnings)
                + consequence
                + " Protection is optional."
                + scope,
            verifiedSubordinates,
            ""
        );
    }

    private static boolean hasValidProtection(
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot,
        DungeonPreparationLoadoutData.Category category
    ) {
        return snapshot.category(category).stream()
            .anyMatch(DungeonPreparationLoadoutData.SelectionView::valid);
    }

    private record ProtectionReadiness(
        UUID confirmationId,
        String message,
        Set<UUID> verifiedSubordinates,
        String failure
    ) {
        private ProtectionReadiness {
            verifiedSubordinates = Set.copyOf(verifiedSubordinates);
        }

        private static ProtectionReadiness failure(String failure) {
            return new ProtectionReadiness(
                new UUID(0L, 0L),
                "",
                Set.of(),
                failure
            );
        }

        private boolean warningRequired() {
            return !message.isBlank();
        }

        private DungeonPreparationProtectionWarningPayload payload(long partyRevision) {
            return new DungeonPreparationProtectionWarningPayload(
                confirmationId,
                partyRevision,
                message
            );
        }
    }

    private static String preparationPartyMessage(
        DungeonPreparationPartyData.MutationStatus status
    ) {
        return switch (status) {
            case INVITED -> "Party invitation sent.";
            case DECLINED -> "Party invitation declined.";
            case JOINED -> "Party roster updated.";
            case REMOVED -> "Party member removed.";
            case READY_CHANGED -> "";
            case FLOOR_LOCKED -> "You have not cleared the prerequisite floor.";
            case PARTY_FULL -> "The party is already full (4/4).";
            case INVALID_CANDIDATE -> "That candidate is no longer available nearby.";
            case ALREADY_IN_PARTY -> "That candidate is already in a party.";
            case INVITE_EXPIRED -> "That party invitation is no longer valid.";
            case STALE_REVISION -> "Party state changed; review the refreshed roster and retry.";
            case NO_PARTY, NOT_MEMBER -> "No active Expedition Table party was found.";
        };
    }

    private static String preparationLoadoutMessage(
        DungeonPreparationLoadoutData.MutationStatus status
    ) {
        return switch (status) {
            case SELECTED, CLEARED, NO_CHANGE -> "";
            case STALE_REVISION -> "Loadout changed; review the refreshed selection and retry.";
            case UNKNOWN_CATEGORY -> "That loadout category is unavailable.";
            case INELIGIBLE_ITEM -> "That item is not eligible for this loadout slot.";
            case CATEGORY_FULL -> "That loadout category is already full.";
            case INSUFFICIENT_STACK_COUNT -> "That inventory stack does not contain another item.";
            case ITEM_ALREADY_SELECTED -> "That inventory item is already selected in another slot.";
            case INVENTORY_FULL -> "Make room in your inventory before unequipping that item.";
            case INVALID_TARGET -> "Only the owning player can change this loadout.";
        };
    }

    private static String preparationSkillMessage(
        DungeonPreparationLoadoutData.SkillMutationStatus status
    ) {
        return switch (status) {
            case APPLIED, CLEARED, NO_CHANGE -> "";
            case STALE_REVISION -> "Skill loadout changed; review the refreshed profile and retry.";
            case INVALID_TARGET -> "Only the owning host can change this subordinate's skills.";
            case INVALID_ID -> "That skill is not a valid Tensura ability.";
            case NOT_LEARNED -> "The subordinate has not learned that ability.";
            case INELIGIBLE -> "That ability is not eligible for this loadout tab.";
            case DUPLICATE -> "That ability is already assigned to another loadout slot.";
            case CAPACITY_FULL -> "That skill loadout is already full.";
            case INVALID_SLOT -> "That skill slot is unavailable.";
            case SKILL_API_UNAVAILABLE -> "The Tensura skill API is unavailable; no skill change was made.";
            case FAILED -> "The native Tensura skill loadout rejected that change.";
        };
    }

    private static String cosmeticActionMessage(DungeonCosmeticData.MutationStatus status) {
        return switch (status) {
            case EQUIPPED -> "";
            case NO_CHANGE -> "That cosmetic is already equipped.";
            case NOT_OWNED -> "You do not own that cosmetic.";
            case UNKNOWN_COSMETIC -> "That cosmetic is not available on this server.";
            case STALE_REVISION -> "Cosmetics changed; review the refreshed selection and retry.";
            case GRANTED, ALREADY_OWNED -> "";
        };
    }

    private static void handleShopOpenRequest(
        DungeonShopOpenRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(
                player,
                DungeonShopSnapshotFactory.create(player, payload.tab(), payload.requestId())
            );
        });
    }

    private static void handleShopActionRequest(
        DungeonShopActionRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DungeonShopSnapshotPayload authoritativeSnapshot = DungeonShopSnapshotFactory.create(
                player,
                payload.tab(),
                0L
            );
            DungeonShopTransactionService.Result result = DungeonShopTransactionService.execute(
                player,
                payload,
                authoritativeSnapshot
            );
            if (result.status() == DungeonShopActionResultPayload.ResultStatus.MISSING_INGREDIENTS
                && !result.message().isBlank()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message()));
            }
            DungeonShopSnapshotPayload freshSnapshot = DungeonShopSnapshotFactory.create(
                player,
                payload.tab(),
                payload.requestId(),
                DungeonShopSnapshotFactory.resultStatus(result.status(), result.message())
            );
            PacketDistributor.sendToPlayer(
                player,
                new DungeonShopActionResultPayload(
                    DungeonShopActionResultPayload.CURRENT_SCHEMA,
                    payload.requestId(),
                    result.status(),
                    result.message(),
                    freshSnapshot
                )
            );
        });
    }

    private static void handleBankOpenRequest(
        DungeonBankOpenRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(
                player,
                DungeonBankSnapshotFactory.create(player, payload.requestId())
            );
        });
    }

    private static void handleBankActionRequest(
        DungeonBankActionRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DungeonBankSnapshotPayload authoritativeSnapshot =
                DungeonBankSnapshotFactory.create(player, 0L);
            DungeonBankTransactionService.Result result = DungeonBankTransactionService.execute(
                player, payload, authoritativeSnapshot
            );
            DungeonBankSnapshotPayload freshSnapshot = DungeonBankSnapshotFactory.create(
                player,
                payload.requestId(),
                DungeonBankSnapshotFactory.resultStatus(result.status(), result.message())
            );
            PacketDistributor.sendToPlayer(
                player,
                new DungeonBankActionResultPayload(
                    DungeonBankActionResultPayload.CURRENT_SCHEMA,
                    payload.requestId(),
                    result.status(),
                    result.message(),
                    freshSnapshot
                )
            );
        });
    }

    private static void handleKurobeForgeOpenRequest(
        KurobeForgeOpenRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PacketDistributor.sendToPlayer(
                player,
                KurobeForgeSnapshotFactory.create(
                    player, payload.initialService(), payload.requestId()
                )
            );
        });
    }

    private static void handleKurobeForgeActionRequest(
        KurobeForgeActionRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            KurobeForgeSnapshotPayload authoritativeSnapshot = KurobeForgeSnapshotFactory.create(
                player, payload.service(), 0L
            );
            KurobeForgeTransactionService.Result result = KurobeForgeTransactionService.execute(
                player, payload, authoritativeSnapshot
            );
            KurobeForgeSnapshotPayload freshSnapshot = KurobeForgeSnapshotFactory.create(
                player,
                payload.service(),
                payload.requestId(),
                new KurobeForgeSnapshotPayload.Status(
                    result.success() ? "SUCCESS" : result.status().name(),
                    result.message()
                )
            );
            PacketDistributor.sendToPlayer(
                player,
                new KurobeForgeActionResultPayload(
                    KurobeForgeActionResultPayload.CURRENT_SCHEMA,
                    payload.requestId(),
                    result.status(),
                    result.message(),
                    freshSnapshot
                )
            );
        });
    }

    private static void handleLootSummaryRequest(
        LootSummaryRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SoulOrbHaulData haul = SoulOrbHaulData.get(player.serverLevel());
            SoulOrbHaulData.Summary summary;
            SoulOrbHaulData.ActionStatus status = SoulOrbHaulData.ActionStatus.SUCCESS;
            String message = "";
            if (payload.action() == LootSummaryRequestPayload.Action.OPEN_LOOT_SUMMARY) {
                if (payload.sessionId().isBlank()) {
                    DungeonRunManager run = DungeonRunManager.get(player.serverLevel());
                    String activeSessionId = run.isActiveRun() ? run.getRunSessionId() : "NONE";
                    summary = haul.latestUnresolved(player.getUUID(), activeSessionId);
                } else {
                    summary = haul.snapshot(player.getUUID(), payload.sessionId());
                }
                if (summary.available()) {
                    summary = haul.revealForDisplay(player.getUUID(), summary.sessionId());
                }
                if (!summary.available()) {
                    status = SoulOrbHaulData.ActionStatus.SESSION_NOT_FOUND;
                    message = "No unresolved completed-floor loot summary is available.";
                }
            } else if (payload.action() == LootSummaryRequestPayload.Action.APPRAISE_HAUL) {
                SoulOrbHaulData.ActionResult result = haul.appraise(
                    player.getUUID(), payload.sessionId(), payload.observedRevision()
                );
                summary = result.summary();
                status = result.status();
                message = actionMessage(status);
            } else if (payload.action() == LootSummaryRequestPayload.Action.KEEP_ALL) {
                SoulOrbHaulData.ActionResult result = haul.keepAll(
                    player, payload.sessionId(), payload.observedRevision()
                );
                summary = result.summary();
                status = result.status();
                message = actionMessage(status);
            } else if (payload.action() == LootSummaryRequestPayload.Action.KEEP_ENTRY) {
                SoulOrbHaulData.ActionResult result = haul.keepEntry(
                    player, payload.sessionId(), payload.observedRevision(), payload.entryId()
                );
                summary = result.summary();
                status = result.status();
                message = actionMessage(status);
            } else {
                summary = haul.snapshot(player.getUUID(), payload.sessionId());
                status = SoulOrbHaulData.ActionStatus.SELLING_UNAVAILABLE;
                message = "Selling is unavailable until the server price catalog is approved.";
            }
            DungeonRunManager run = DungeonRunManager.get(player.serverLevel());
            boolean exitOptionsAvailable = summary.available()
                && run.isRunCompleted()
                && run.isExitPortalActive()
                && run.getRunSessionId().equals(summary.sessionId());
            PacketDistributor.sendToPlayer(player, LootSummaryPayload.from(
                player, summary, status, message, true, exitOptionsAvailable, run.getNextFloorNumber(),
                CoreRecoveryData.get(player.serverLevel())
                    .snapshot(player.getUUID(), summary.sessionId(), summary.floor())
                    .pendingCandidates()
            ));
        });
    }

    private static void handleCoreRecoveryClaim(
        CoreRecoveryClaimRequestPayload payload,
        net.neoforged.neoforge.network.handling.IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            CoreRecoveryData recovery = CoreRecoveryData.get(player.serverLevel());
            CoreSatchelData satchel = CoreSatchelData.get(player.serverLevel());
            CoreRecoveryData.ClaimResult result = switch (payload.action()) {
                case REPLACE -> recovery.replaceCandidate(
                    player.getUUID(),
                    payload.sessionId(),
                    payload.floor(),
                    payload.coreId(),
                    payload.slot(),
                    satchel
                );
                case QUICK_STORE -> recovery.claimCandidateFirstAvailable(
                    player.getUUID(),
                    payload.sessionId(),
                    payload.floor(),
                    payload.coreId(),
                    satchel
                );
                case STORE -> recovery.claimCandidate(
                    player.getUUID(),
                    payload.sessionId(),
                    payload.floor(),
                    payload.coreId(),
                    payload.slot(),
                    satchel
                );
            };
            if (result.claimed()) {
                CoreSatchelAccessSync.send(player);
                SoulOrbHaulData.Summary summary = SoulOrbHaulData.get(player.serverLevel()).snapshot(
                    player.getUUID(), payload.sessionId()
                );
                DungeonRunManager run = DungeonRunManager.get(player.serverLevel());
                boolean exitOptionsAvailable = summary.available()
                    && run.isRunCompleted()
                    && run.isExitPortalActive()
                    && run.getRunSessionId().equals(summary.sessionId());
                PacketDistributor.sendToPlayer(player, LootSummaryPayload.from(
                    player,
                    summary,
                    SoulOrbHaulData.ActionStatus.SUCCESS,
                    coreClaimMessage(result),
                    false,
                    exitOptionsAvailable,
                    run.getNextFloorNumber(),
                    result.recovery().pendingCandidates()
                ));
            }
            PacketDistributor.sendToPlayer(player, new CoreRecoveryClaimResultPayload(
                result.status(),
                payload.coreId(),
                result.slot(),
                coreClaimMessage(result)
            ));
        });
    }

    private static String coreClaimMessage(CoreRecoveryData.ClaimResult result) {
        return switch (result.status()) {
            case CLAIMED -> "Monster Core stored in Satchel slot " + (result.slot() + 1) + ".";
            case SESSION_NOT_FOUND -> "This completed run is no longer available.";
            case CANDIDATE_NOT_FOUND -> "That recovered Monster Core is no longer available.";
            case ALREADY_CLAIMED -> "That Monster Core was already claimed.";
            case SATCHEL_NOT_OWNED -> "You do not own a Core Satchel.";
            case SATCHEL_FULL -> "No empty Satchel slots. This Core remains available.";
            case INVALID_SLOT -> "That Satchel slot is invalid.";
            case SLOT_OCCUPIED -> "That Satchel slot is occupied. Select an empty slot.";
            case SUMMARY_FINALIZED -> "This completed-floor Core recovery was already finalized.";
            case REPLACED -> "Monster Core replaced in Satchel slot " + (result.slot() + 1)
                + "; the displaced Core was permanently discarded.";
            case SLOT_EMPTY -> "That Satchel slot is empty. Store the Core there instead.";
        };
    }

    private static String actionMessage(SoulOrbHaulData.ActionStatus status) {
        return switch (status) {
            case SUCCESS -> "";
            case SESSION_NOT_FOUND -> "This completed-floor summary is unavailable.";
            case STALE_REVISION -> "Loot changed; review the refreshed summary and retry.";
            case NOT_READY -> "This floor haul is not ready for appraisal.";
            case NOT_APPRAISED -> "Appraise the captured haul before resolving it.";
            case INVENTORY_FULL -> "Your inventory needs enough space for the requested captured loot.";
            case NOTHING_TO_RESOLVE -> "No unresolved captured loot remains.";
            case ENTRY_NOT_FOUND -> "That captured item is no longer available; review the refreshed summary.";
            case SELLING_UNAVAILABLE -> "Selling is unavailable until the server price catalog is approved.";
        };
    }

    private static void handleUiRequest(DungeonUiRequestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String message = "";
            RewardMailboxData mailbox = RewardMailboxData.get(player.serverLevel());
            DungeonUiSnapshotPayload current = DungeonUiSnapshotFactory.create(player, "");
            if (isStaleMutation(payload, current.revision())) {
                PacketDistributor.sendToPlayer(player,
                    DungeonUiSnapshotFactory.create(player, "Dungeon state changed; review and retry."));
                return;
            }
            if (payload.action() == DungeonUiRequestPayload.Action.CLAIM_OLDEST) {
                message = mailbox.claimOldest(player).message();
                CoreSatchelAccessSync.send(player);
            } else if (payload.action() == DungeonUiRequestPayload.Action.CLAIM_ALL) {
                message = mailbox.claimAll(player).message();
                CoreSatchelAccessSync.send(player);
            } else if (payload.action() == DungeonUiRequestPayload.Action.SELECT_FLOOR) {
                if (preparationPartyFrozen(player)) {
                    message = "Dungeon preparation is already in progress; the floor is frozen.";
                } else {
                    DungeonFloorProgressData.FloorSelectionResult result =
                        DungeonFloorProgressData.get(player.serverLevel())
                            .selectFloor(player.getUUID(), payload.floor());
                    message = result.selected()
                        ? "Floor " + result.requestedFloor() + " selected."
                        : "Floor " + result.requestedFloor() + " is locked. Highest unlocked floor: "
                            + result.highestUnlockedFloor() + ".";
                    if (result.selected()) {
                    DungeonPreparationPartyData partyData =
                        DungeonPreparationPartyData.get(player.serverLevel());
                    DungeonPreparationPartyData.PartyRecord party =
                        partyData.partyFor(player.getUUID()).orElse(null);
                    if (party != null && party.host().equals(player.getUUID())) {
                        party = partyData.invalidateReadyForFloor(player.getUUID());
                        if (party != null) {
                            sendPartySnapshotToOnlineMembers(player, party, "");
                        }
                    }
                }
                }
            } else if (payload.action() == DungeonUiRequestPayload.Action.PORTAL_CONTINUE
                || payload.action() == DungeonUiRequestPayload.Action.PORTAL_RETURN_HOME) {
                net.minecraft.server.level.ServerLevel portalLevel = player.serverLevel();
                DungeonRunManager manager = DungeonRunManager.get(portalLevel);
                DungeonInstanceAllocationData.InstanceKey instanceKey =
                    DungeonInstanceAllocationData.get(portalLevel)
                        .forPlayer(player.getUUID())
                        .map(DungeonInstanceAllocationData.Allocation::key)
                        .orElse(null);
                DungeonRunService.PortalCommandOperationResult result =
                    instanceKey == null
                        ? legacyPortalAction(payload, player, portalLevel, manager)
                        : payload.action() == DungeonUiRequestPayload.Action.PORTAL_CONTINUE
                            ? manager.continueFromPortalCommand(instanceKey, portalLevel, player)
                            : manager.returnHomeFromPortalCommand(instanceKey, portalLevel, player);
                if (!result.succeeded()) {
                    message = "Exit portal action blocked: " + result.reason();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                }
                SoulOrbHaulData.get(player.serverLevel()).syncHud(player);
            }
            PacketDistributor.sendToPlayer(
                player,
                DungeonUiSnapshotFactory.create(player, message)
            );
        });
    }

    private static DungeonRunService.PortalCommandOperationResult legacyPortalAction(
        DungeonUiRequestPayload payload,
        ServerPlayer player,
        net.minecraft.server.level.ServerLevel portalLevel,
        DungeonRunManager manager
    ) {
        Runnable cleanup = () ->
            GeneratedLinearConnectorPlacementCommands.cleanupGeneratedFloorAfterHubReturn(
                portalLevel, player.createCommandSourceStack().withSuppressedOutput()
            );
        return payload.action() == DungeonUiRequestPayload.Action.PORTAL_CONTINUE
            ? manager.continueFromPortalCommand(portalLevel, player, cleanup)
            : manager.returnHomeFromPortalCommand(portalLevel, player, cleanup);
    }

    static boolean isStaleMutation(DungeonUiRequestPayload payload, long currentRevision) {
        return payload.action() != DungeonUiRequestPayload.Action.REFRESH
            && payload.action() != DungeonUiRequestPayload.Action.SELECT_FLOOR
            && payload.action() != DungeonUiRequestPayload.Action.PORTAL_CONTINUE
            && payload.action() != DungeonUiRequestPayload.Action.PORTAL_RETURN_HOME
            && payload.observedRevision() != currentRevision;
    }
}
