package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonUiSnapshotClient;
import com.mineclawd.tensuradungeon.client.DungeonCosmeticClient;
import com.mineclawd.tensuradungeon.network.DungeonUiRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonUiSnapshotPayload;
import com.mineclawd.tensuradungeon.client.LootSummaryClient;
import com.mineclawd.tensuradungeon.client.DungeonShopClient;
import com.mineclawd.tensuradungeon.client.DungeonBankClient;
import com.mineclawd.tensuradungeon.client.KurobeForgeClient;
import com.mineclawd.tensuradungeon.client.CoreSatchelInventoryTab;
import com.mineclawd.tensuradungeon.network.LootSummaryPayload;
import com.mineclawd.tensuradungeon.network.LootSummaryRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopActionRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopActionResultPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankSnapshotPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankActionRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankActionResultPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeActionResultPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import net.minecraft.client.gui.screens.Screen;

public final class DungeonRuntimeBridge {
    private DungeonRuntimeBridge() {}

    public static DungeonUiSnapshotPayload snapshot() {
        DungeonUiSnapshotPayload snapshot = DungeonUiSnapshotClient.latest();
        return snapshot != null && snapshot.schemaVersion() == DungeonUiSnapshotPayload.CURRENT_SCHEMA
            ? snapshot
            : null;
    }

    public static void refresh() {
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.REFRESH);
    }

    public static void claimOldest() {
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.CLAIM_OLDEST);
    }

    public static void claimAll() {
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.CLAIM_ALL);
    }

    public static void selectFloor(int floor) {
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.SELECT_FLOOR, floor);
    }

    public static LootSummaryPayload lootSummary() {
        LootSummaryPayload summary = LootSummaryClient.latest();
        return summary != null && summary.schemaVersion() == LootSummaryPayload.CURRENT_SCHEMA
            ? summary : null;
    }

    public static boolean consumeLootSummaryOpenRequest() {
        return LootSummaryClient.consumeOpenRequested();
    }

    public static DungeonShopSnapshotPayload shopSnapshot() {
        DungeonShopSnapshotPayload snapshot = DungeonShopClient.latest();
        return snapshot != null && snapshot.supportedSchema() ? snapshot : null;
    }

    public static DungeonShopActionResultPayload shopActionResult() {
        DungeonShopActionResultPayload result = DungeonShopClient.latestActionResult();
        return result != null && result.supportedSchema() ? result : null;
    }

    public static boolean consumeShopOpenRequest() {
        return DungeonShopClient.consumeOpenRequested();
    }

    public static DungeonBankSnapshotPayload bankSnapshot() {
        DungeonBankSnapshotPayload snapshot = DungeonBankClient.latest();
        return snapshot != null && snapshot.supportedSchema() ? snapshot : null;
    }

    public static DungeonBankActionResultPayload bankActionResult() {
        DungeonBankActionResultPayload result = DungeonBankClient.latestActionResult();
        return result != null && result.supportedSchema() ? result : null;
    }

    public static boolean consumeBankOpenRequest() {
        return DungeonBankClient.consumeOpenRequested();
    }

    public static void openBank() {
        DungeonBankClient.requestOpen();
    }

    public static void submitBankAction(DungeonBankUiApi.Action action) {
        if (action == null || action.snapshotRevision() < 0L || action.quoteId().isBlank()) {
            return;
        }
        DungeonBankClient.requestAction(
            DungeonBankActionRequestPayload.Action.valueOf(action.type().name()),
            action.quoteId(),
            action.snapshotRevision()
        );
    }

    public static KurobeForgeSnapshotPayload kurobeForgeSnapshot() {
        KurobeForgeSnapshotPayload snapshot = KurobeForgeClient.latest();
        return snapshot != null && snapshot.supportedSchema() ? snapshot : null;
    }

    public static KurobeForgeActionResultPayload kurobeForgeActionResult() {
        KurobeForgeActionResultPayload result = KurobeForgeClient.latestActionResult();
        return result != null && result.supportedSchema() ? result : null;
    }

    public static boolean consumeKurobeForgeOpenRequest() {
        return KurobeForgeClient.consumeOpenRequested();
    }

    public static void openKurobeForge(KurobeForgeView.Service service) {
        if (service == null) return;
        KurobeForgeClient.requestOpen(KurobeForgeSnapshotPayload.Service.valueOf(service.name()));
    }

    public static void submitKurobeForgeAction(
        KurobeForgeView.Service service,
        String weaponId,
        long observedRevision,
        String coreId,
        int coreSlot
    ) {
        if (service == null || weaponId == null || weaponId.isBlank() || observedRevision < 0L) {
            return;
        }
        KurobeForgeClient.requestAction(
            KurobeForgeSnapshotPayload.Service.valueOf(service.name()),
            weaponId,
            observedRevision,
            coreId == null ? "" : coreId,
            coreSlot
        );
    }

    public static void openShopTab(DungeonShopView.Tab tab) {
        DungeonShopClient.requestOpen(switch (tab) {
            case DAILY -> DungeonShopSnapshotPayload.Tab.DAILY;
            case WEEKLY -> DungeonShopSnapshotPayload.Tab.WEEKLY;
            case BARTER -> DungeonShopSnapshotPayload.Tab.BARTER;
        });
    }

    public static void submitShopAction(DungeonShopUiApi.Action action) {
        if (action == null) {
            return;
        }
        if (action.type() == DungeonShopUiApi.ActionType.VISIT_VENDOR) {
            if ("Kurobe".equalsIgnoreCase(action.vendor())) {
                openKurobeForge(KurobeForgeView.Service.PROMOTE);
            }
            return;
        }
        if ((action.type() != DungeonShopUiApi.ActionType.PURCHASE
                && action.type() != DungeonShopUiApi.ActionType.BARTER)
                || action.tab() == null || action.offerId().isBlank()
                || action.snapshotRevision() < 0L) {
            return;
        }
        DungeonShopClient.requestAction(
            switch (action.tab()) {
                case DAILY -> DungeonShopSnapshotPayload.Tab.DAILY;
                case WEEKLY -> DungeonShopSnapshotPayload.Tab.WEEKLY;
                case BARTER -> DungeonShopSnapshotPayload.Tab.BARTER;
            },
            action.type() == DungeonShopUiApi.ActionType.BARTER
                ? DungeonShopActionRequestPayload.Action.BARTER
                : DungeonShopActionRequestPayload.Action.PURCHASE,
            action.offerId(),
            action.snapshotRevision()
        );
    }

    public static boolean startHudPreview(String offerId) {
        return offerId != null && DungeonCosmeticClient.startHudPreview(offerId);
    }

    public static void openLatestLootSummary() {
        LootSummaryClient.request(LootSummaryRequestPayload.Action.OPEN_LOOT_SUMMARY, "", "");
    }

    public static void appraise(String sessionId) {
        LootSummaryClient.request(LootSummaryRequestPayload.Action.APPRAISE_HAUL, sessionId, "");
    }

    public static void keepAll(String sessionId) {
        LootSummaryClient.request(LootSummaryRequestPayload.Action.KEEP_ALL, sessionId, "");
    }

    public static void keepEntry(String sessionId, String entryId) {
        LootSummaryClient.request(LootSummaryRequestPayload.Action.KEEP_ENTRY, sessionId, entryId);
    }

    public static CoreSatchelInventoryTab.RecoveryPlacementStatus openCorePlacement(
        Screen parent,
        LootSummaryPayload summary,
        LootSummaryPayload.RecoveredCore core
    ) {
        return CoreSatchelInventoryTab.openRecoveryPlacement(
            parent, summary.sessionId(), summary.floor(), core
        );
    }

    public static CoreSatchelInventoryTab.QuickStoreStatus quickStoreCore(
        LootSummaryPayload summary,
        LootSummaryPayload.RecoveredCore core
    ) {
        return CoreSatchelInventoryTab.quickStoreRecovery(
            summary.sessionId(), summary.floor(), core
        );
    }

    public static boolean isCoreSatchelFull() {
        return CoreSatchelInventoryTab.isSatchelFull();
    }

    public static String consumeCoreQuickStoreMessage() {
        return CoreSatchelInventoryTab.consumeQuickStoreMessage();
    }

    public static void returnToHub() {
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.PORTAL_CONTINUE);
    }

    public static void continueToFloor(int floor) {
        selectFloor(floor);
        returnToHub();
    }

    public static void returnHome() {
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.PORTAL_RETURN_HOME);
    }
}
