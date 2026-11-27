package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.CoreSatchelAccessPayload;
import com.mineclawd.tensuradungeon.network.CoreRecoveryClaimRequestPayload;
import com.mineclawd.tensuradungeon.network.CoreRecoveryClaimResultPayload;
import com.mineclawd.tensuradungeon.network.LootSummaryPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class CoreSatchelInventoryTab {
    private static volatile CoreSatchelView latestView;
    private static volatile String quickStoreMessage = "";

    private CoreSatchelInventoryTab() {}

    public static void accept(CoreSatchelView view) {
        latestView = Objects.requireNonNull(view, "view");
    }

    public static void clear() {
        latestView = null;
        quickStoreMessage = "";
    }

    public static void handle(CoreSatchelAccessPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.acquired()) {
                clear();
                return;
            }
            accept(viewFrom(payload));
        });
    }

    static CoreSatchelView viewFrom(CoreSatchelAccessPayload payload) {
        LinkedHashMap<Integer, CoreSatchelView.CoreEntry> cores = new LinkedHashMap<>();
        payload.cores().stream()
            .sorted(java.util.Comparator.comparingInt(CoreSatchelAccessPayload.CoreEntry::slot))
            .forEach(core -> cores.put(core.slot(), new CoreSatchelView.CoreEntry(
                core.tier(),
                core.totalCharge(),
                core.capacity(),
                core.naturalCharge(),
                core.displayName()
            )));
        return new CoreSatchelView(payload.gradeName(), payload.capacity(), cores);
    }

    static CoreSatchelView latestView() {
        return latestView;
    }

    public static RecoveryPlacementStatus openRecoveryPlacement(
        Screen parent,
        String sessionId,
        int floor,
        LootSummaryPayload.RecoveredCore core
    ) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(core, "core");
        CoreSatchelView view = latestView;
        if (view == null) return RecoveryPlacementStatus.SATCHEL_UNAVAILABLE;
        Minecraft.getInstance().setScreen(new CoreSatchelScreen(
            view,
            parent,
            new CoreSatchelScreen.PendingClaim(
                sessionId,
                floor,
                core.coreId(),
                core.tier(),
                core.totalCharge(),
                core.capacity(),
                core.naturalCharge()
            )
        ));
        return RecoveryPlacementStatus.OPENED;
    }

    public static QuickStoreStatus quickStoreRecovery(
        String sessionId,
        int floor,
        LootSummaryPayload.RecoveredCore core
    ) {
        Objects.requireNonNull(core, "core");
        CoreSatchelView view = latestView;
        if (view == null) return QuickStoreStatus.SATCHEL_UNAVAILABLE;
        if (view.occupiedCount() >= view.capacity()) return QuickStoreStatus.SATCHEL_FULL;
        quickStoreMessage = "";
        PacketDistributor.sendToServer(new CoreRecoveryClaimRequestPayload(
            sessionId,
            floor,
            core.coreId(),
            -1,
            CoreRecoveryClaimRequestPayload.Action.QUICK_STORE
        ));
        return QuickStoreStatus.REQUESTED;
    }

    public static boolean isSatchelFull() {
        CoreSatchelView view = latestView;
        return view != null && view.occupiedCount() >= view.capacity();
    }

    static void handleQuickStoreResult(CoreRecoveryClaimResultPayload result) {
        quickStoreMessage = result.message();
    }

    public static String consumeQuickStoreMessage() {
        String message = quickStoreMessage;
        quickStoreMessage = "";
        return message;
    }

    public enum RecoveryPlacementStatus {
        OPENED,
        SATCHEL_UNAVAILABLE,
        SATCHEL_FULL
    }

    public enum QuickStoreStatus {
        REQUESTED,
        SATCHEL_UNAVAILABLE,
        SATCHEL_FULL
    }

    @SubscribeEvent
    public static void attach(ScreenEvent.Init.Post event) {
        if (event.getScreen().getClass() == InventoryScreen.class) {
            event.addListener(new TabWidget((InventoryScreen) event.getScreen(), CoreSatchelInventoryTab::latestView));
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    static final class TabWidget extends AbstractButton {
        private static final int PANEL = 0xF20A0811;
        private static final int INSET = 0xF0181120;
        private static final int INDIGO = 0xFF3A2653;
        private static final int VIOLET = 0xFF8E4DD1;
        private static final int GOLD = 0xFFD8AE55;
        private static final int TEXT = 0xFFEDE6F7;

        private final InventoryScreen inventoryScreen;
        private final Supplier<CoreSatchelView> viewSupplier;
        private float expansion;
        private long previousRenderNanos;

        TabWidget(InventoryScreen inventoryScreen, Supplier<CoreSatchelView> viewSupplier) {
            super(
                inventoryScreen.getGuiLeft() - CoreSatchelTabLayout.COLLAPSED_WIDTH + 2,
                inventoryScreen.getGuiTop() + 49,
                CoreSatchelTabLayout.COLLAPSED_WIDTH,
                CoreSatchelTabLayout.HEIGHT,
                Component.translatable("screen.tensura_dungeon.core_satchel.tab")
            );
            this.inventoryScreen = inventoryScreen;
            this.viewSupplier = viewSupplier;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            CoreSatchelView view = viewSupplier.get();
            active = view != null;
            long now = System.nanoTime();
            double elapsedSeconds = previousRenderNanos == 0L
                ? 0.0D
                : (now - previousRenderNanos) / 1_000_000_000.0D;
            previousRenderNanos = now;

            if (view == null) {
                expansion = 0.0F;
                return;
            }

            expansion = CoreSatchelTabLayout.advance(expansion, isHovered, elapsedSeconds);
            CoreSatchelTabLayout layout = CoreSatchelTabLayout.create(
                inventoryScreen.getGuiLeft(),
                inventoryScreen.getGuiTop(),
                expansion
            );
            setSize(layout.width(), layout.height());
            setX(layout.x());
            setY(layout.y());
            drawTab(graphics);
        }

        private void drawTab(GuiGraphics graphics) {
            int x = getX();
            int y = getY();
            int right = getRight();
            int bottom = getBottom();

            graphics.enableScissor(x, y, right, bottom);
            graphics.fill(x, y + 2, right, bottom - 2, PANEL);
            graphics.fill(x + 2, y + 4, right - 2, bottom - 4, INSET);
            outline(graphics, x, y, getWidth(), getHeight(), GOLD);
            outline(graphics, x + 2, y + 2, getWidth() - 4, getHeight() - 4, INDIGO);
            graphics.fill(right - 3, y + 4, right - 1, bottom - 4, VIOLET);

            drawSatchelEmblem(graphics, x + 8, y + 8);
            graphics.drawString(
                Minecraft.getInstance().font,
                getMessage(),
                x + 31,
                y + 13,
                isHovered ? GOLD : TEXT,
                true
            );
            graphics.disableScissor();
        }

        @Override
        public void onPress() {
            CoreSatchelView view = viewSupplier.get();
            if (view != null) {
                Minecraft.getInstance().setScreen(new CoreSatchelScreen(view, inventoryScreen));
            }
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return viewSupplier.get() != null && super.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            if (viewSupplier.get() != null) {
                defaultButtonNarrationText(output);
            }
        }

        private static void drawSatchelEmblem(GuiGraphics graphics, int x, int y) {
            graphics.fill(x + 4, y, x + 10, y + 2, GOLD);
            graphics.fill(x + 2, y + 2, x + 4, y + 6, GOLD);
            graphics.fill(x + 10, y + 2, x + 12, y + 6, GOLD);
            graphics.fill(x, y + 5, x + 14, y + 16, INDIGO);
            outline(graphics, x, y + 5, 14, 11, GOLD);
            graphics.fill(x + 5, y + 8, x + 9, y + 12, VIOLET);
            graphics.fill(x + 6, y + 9, x + 8, y + 11, GOLD);
        }

        private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
            if (w <= 0 || h <= 0) return;
            graphics.hLine(x, x + w - 1, y, color);
            graphics.hLine(x, x + w - 1, y + h - 1, color);
            graphics.vLine(x, y, y + h - 1, color);
            graphics.vLine(x + w - 1, y, y + h - 1, color);
        }
    }
}
