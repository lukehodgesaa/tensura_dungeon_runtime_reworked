package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import com.mineclawd.tensuradungeon.client.DungeonHudSuppressingScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class DungeonBankScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int W = 960;
    private static final int H = 560;

    private static final int BASE = 0xff010713;
    private static final int PANEL = 0xff041020;
    private static final int PANEL_RAISED = 0xff07182b;
    private static final int SLOT = 0xff0a1a2a;
    private static final int SLOT_HOVER = 0xff102a40;
    private static final int LOCKED = 0xff020814;
    private static final int LINE = 0xff235d7d;
    private static final int LINE_BRIGHT = 0xff43c9ef;
    private static final int LINE_DIM = 0xff203247;
    private static final int CYAN = 0xff49d7ff;
    private static final int GREEN = 0xff5ee87a;
    private static final int GOLD = 0xffffc447;
    private static final int ORANGE = 0xffff9f43;
    private static final int RED = 0xffff6575;
    private static final int TEXT = 0xffedf3ff;
    private static final int MUTED = 0xff9aa8bf;
    private static final int DIM = 0xff617089;

    private static final int HEADER_X = 18;
    private static final int HEADER_Y = 12;
    private static final int HEADER_W = W - 36;
    private static final int HEADER_H = 68;
    private static final int BODY_Y = 88;
    private static final int BODY_H = 372;
    private static final int INVENTORY_X = 18;
    private static final int INVENTORY_W = 246;
    private static final int TRANSFER_X = 272;
    private static final int TRANSFER_W = 206;
    private static final int BANK_X = 486;
    private static final int BANK_W = 456;
    private static final int FOOTER_Y = 468;
    private static final int FOOTER_H = 72;

    private static final int INVENTORY_COLUMNS = 5;
    private static final int INVENTORY_ROWS = 4;
    private static final int INVENTORY_SLOT = 42;
    private static final int INVENTORY_GAP = 5;
    private static final int INVENTORY_SLOTS_PER_PAGE = INVENTORY_COLUMNS * INVENTORY_ROWS;
    private static final int BANK_COLUMNS = 9;
    private static final int BANK_ROWS = 5;
    private static final int BANK_SLOT = 42;
    private static final int BANK_GAP = 4;

    private final Screen parent;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private DungeonBankView view;
    private @Nullable DungeonBankView.DepositQuote selectedDepositQuote;
    private @Nullable DungeonBankView.Slot selectedWithdrawalSlot;
    private Modal modal = Modal.NONE;
    private int inventoryPage;
    private int currentPage;
    private String hoveredHotspotId = "";
    private String statusOverride = "";
    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;

    DungeonBankScreen(DungeonBankView view, @Nullable Screen parent) {
        super(Component.literal("Dungeon Bank"));
        this.view = Objects.requireNonNull(view, "Dungeon Bank view");
        this.parent = parent;
        selectDefaultDepositQuote();
    }

    void applyAuthoritativeView(DungeonBankView updatedView) {
        String selectedQuoteId = selectedDepositQuote == null
            ? "" : selectedDepositQuote.quoteId();
        view = Objects.requireNonNull(updatedView, "Dungeon Bank view");
        selectedDepositQuote = view.depositQuotes().stream()
            .filter(quote -> quote.quoteId().equals(selectedQuoteId))
            .findFirst()
            .orElse(null);
        selectDefaultDepositQuote();
        selectedWithdrawalSlot = null;
        inventoryPage = Math.max(0, Math.min(inventoryPage, inventoryPageCount() - 1));
        currentPage = Math.max(0, Math.min(currentPage, view.pageCount() - 1));
        modal = Modal.NONE;
        statusOverride = "";
    }

    private void selectDefaultDepositQuote() {
        if (selectedDepositQuote != null) return;
        selectedDepositQuote = view.depositQuotes().stream()
            .filter(DungeonBankView.DepositQuote::depositable)
            .findFirst()
            .orElse(view.depositQuotes().isEmpty() ? null : view.depositQuotes().getFirst());
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateTransform();
        drawOutsideBackdrop(graphics, partialTick);
        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        hotspots.clear();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(uiX, uiY, 0.0F);
        pose.scale(uiScale, uiScale, 1.0F);

        drawBackdrop(graphics);
        drawHeader(graphics, vx, vy);
        drawPhysicalInventory(graphics, vx, vy);
        drawTransferLane(graphics, vx, vy);
        drawBankStorage(graphics, vx, vy);
        drawFooter(graphics, vx, vy);
        drawStatus(graphics);

        if (modal != Modal.NONE) {
            graphics.flush();
            hotspots.clear();
            drawModal(graphics, vx, vy);
        }
        pose.popPose();
        updateHoverSound(vx, vy);
    }

    private void updateTransform() {
        uiScale = Math.min(
            Math.max(1, width - 24) / (float) W,
            Math.max(1, height - 24) / (float) H
        );
        uiX = (width - W * uiScale) * 0.5F;
        uiY = (height - H * uiScale) * 0.5F;
    }

    private int virtualX(double mouseX) {
        return (int) Math.floor((mouseX - uiX) / Math.max(0.01F, uiScale));
    }

    private int virtualY(double mouseY) {
        return (int) Math.floor((mouseY - uiY) / Math.max(0.01F, uiScale));
    }

    private void drawOutsideBackdrop(GuiGraphics graphics, float partialTick) {
        if (minecraft != null && minecraft.level != null) renderBlurredBackground(partialTick);
        graphics.fillGradient(0, 0, width, height, 0xc0030710, 0xe6000207);
    }

    private void drawBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, W, H, BASE);
        for (int x = 0; x < W; x += 64) graphics.vLine(x, 0, H, 0x10194962);
        for (int y = 0; y < H; y += 48) graphics.hLine(0, W, y, 0x0c194962);
        graphics.fillGradient(0, 0, W, 92, 0xe9071428, 0x00030a16);
        drawPixelFrame(graphics, 4, 4, W - 8, H - 8, LINE, 6);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        cutPanel(graphics, HEADER_X, HEADER_Y, HEADER_W, HEADER_H, PANEL, LINE, 6);
        drawBankGlyph(graphics, 31, 25, 38);
        drawFit(graphics, "BANK VAULT", 78, 23, 186, TEXT, 1.52F, 1.12F);
        drawFit(graphics, view.grade().displayLabel(), 79, 51, 180, CYAN, 0.86F, 0.74F);

        graphics.vLine(270, 23, 68, withAlpha(LINE, 160));
        drawFit(graphics, "MONEY ON HAND", 286, 21, 220, CYAN, 0.92F, 0.78F);
        drawFit(graphics, "ELIGIBLE COINS IN PLAYER INVENTORY / OFFHAND", 286, 36,
            330, MUTED, 0.82F, 0.72F);
        drawWalletStrip(graphics, view.inventoryWallet(), 286, 47, 430, 25, false);

        graphics.vLine(732, 23, 68, withAlpha(LINE, 160));
        drawFit(graphics, "BANK CAPACITY", 748, 21, 180, CYAN, 0.92F, 0.78F);
        drawFit(graphics, view.usedSlots() + " / " + view.unlockedSlots() + " SLOTS",
            748, 39, 150, TEXT, 1.00F, 0.82F);
        int barWidth = 170;
        panel(graphics, 748, 58, barWidth, 9, LOCKED, LINE_DIM);
        int usedWidth = Math.round((barWidth - 2)
            * Math.min(1.0F, view.usedSlots() / (float) Math.max(1, view.unlockedSlots())));
        if (usedWidth > 0) graphics.fill(749, 59, 749 + usedWidth, 66, CYAN);

        int closeX = 914;
        boolean closeHovered = contains(closeX, 18, 20, 20, mouseX, mouseY);
        drawButton(graphics, closeX, 18, 20, 20, "X", closeHovered, true, TEXT);
        addHotspot("close", closeX, 18, 20, 20, this::onClose);
    }

    private void drawPhysicalInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        cutPanel(graphics, INVENTORY_X, BODY_Y, INVENTORY_W, BODY_H, PANEL, LINE, 5);
        drawCenteredFit(graphics, "PHYSICAL COIN STACKS", INVENTORY_X + INVENTORY_W / 2,
            BODY_Y + 17, INVENTORY_W - 24, TEXT, 1.10F, 0.88F);
        drawCenteredFit(graphics, "PLAYER INVENTORY / OFFHAND",
            INVENTORY_X + INVENTORY_W / 2, BODY_Y + 38,
            INVENTORY_W - 24, MUTED, 0.84F, 0.72F);

        int inventoryNavY = BODY_Y + 56;
        boolean hasPrevious = inventoryPage > 0;
        boolean hasNext = inventoryPage + 1 < inventoryPageCount();
        boolean previousHovered = hasPrevious
            && contains(INVENTORY_X + 8, inventoryNavY, 48, 20, mouseX, mouseY);
        drawButton(graphics, INVENTORY_X + 8, inventoryNavY, 48, 20, "<",
            previousHovered, hasPrevious, hasPrevious ? CYAN : DIM);
        if (hasPrevious) {
            addHotspot("inventory-previous", INVENTORY_X + 8, inventoryNavY, 48, 20,
                () -> changeInventoryPage(-1));
        }
        drawCenteredFit(graphics,
            "PAGE " + (inventoryPage + 1) + " / " + inventoryPageCount(),
            INVENTORY_X + INVENTORY_W / 2, inventoryNavY + 6, 120, TEXT, 0.72F, 0.64F);
        int inventoryNextX = INVENTORY_X + INVENTORY_W - 56;
        boolean nextHovered = hasNext
            && contains(inventoryNextX, inventoryNavY, 48, 20, mouseX, mouseY);
        drawButton(graphics, inventoryNextX, inventoryNavY, 48, 20, ">",
            nextHovered, hasNext, hasNext ? CYAN : DIM);
        if (hasNext) {
            addHotspot("inventory-next", inventoryNextX, inventoryNavY, 48, 20,
                () -> changeInventoryPage(1));
        }

        int gridX = INVENTORY_X + 8;
        int gridY = BODY_Y + 76;
        int firstQuote = inventoryPage * INVENTORY_SLOTS_PER_PAGE;
        for (int index = 0; index < INVENTORY_SLOTS_PER_PAGE; index++) {
            int column = index % INVENTORY_COLUMNS;
            int row = index / INVENTORY_COLUMNS;
            int x = gridX + column * (INVENTORY_SLOT + INVENTORY_GAP);
            int y = gridY + row * (INVENTORY_SLOT + INVENTORY_GAP);
            int quoteIndex = firstQuote + index;
            if (quoteIndex < view.depositQuotes().size()) {
                DungeonBankView.DepositQuote quote = view.depositQuotes().get(quoteIndex);
                boolean selected = selectedDepositQuote != null
                    && quote.quoteId().equals(selectedDepositQuote.quoteId());
                boolean hovered = contains(x, y, INVENTORY_SLOT, INVENTORY_SLOT, mouseX, mouseY);
                drawInventoryCoinSlot(
                    graphics, quote, quote.currency(), x, y, selected, hovered
                );
                addHotspot("inventory:" + quote.quoteId(), x, y,
                    INVENTORY_SLOT, INVENTORY_SLOT, () -> selectDepositQuote(quote));
            } else {
                panel(graphics, x, y, INVENTORY_SLOT, INVENTORY_SLOT, 0xff06111e, LINE_DIM);
            }
        }

        int totalsY = BODY_Y + 278;
        graphics.hLine(INVENTORY_X + 12, INVENTORY_X + INVENTORY_W - 12,
            totalsY - 10, withAlpha(LINE, 120));
        drawCenteredFit(graphics, "SELECT ONE PHYSICAL STACK",
            INVENTORY_X + INVENTORY_W / 2, totalsY,
            INVENTORY_W - 22, CYAN, 0.90F, 0.76F);
        drawCenteredFit(graphics, "TENSURA CURRENCY ONLY",
            INVENTORY_X + INVENTORY_W / 2, totalsY + 21,
            INVENTORY_W - 22, MUTED, 0.78F, 0.68F);
    }

    private void drawInventoryCoinSlot(
        GuiGraphics graphics,
        @Nullable DungeonBankView.DepositQuote quote,
        DungeonBankView.Currency currency,
        int x,
        int y,
        boolean selected,
        boolean hovered
    ) {
        int border = selected ? GREEN : hovered ? CYAN : quote == null ? LINE_DIM : LINE;
        int surface = selected ? 0xff0b2a27 : hovered ? SLOT_HOVER : quote == null ? LOCKED : SLOT;
        panel(graphics, x, y, INVENTORY_SLOT, INVENTORY_SLOT, surface, border);
        if (quote == null) {
            drawCenteredFit(graphics, "--", x + INVENTORY_SLOT / 2, y + 17,
                INVENTORY_SLOT - 8, DIM, 0.70F, 0.60F);
            return;
        }
        drawCurrencyItem(graphics, currency, x + 6, y + 6, 25);
        drawRightFit(graphics, formatCount(quote.depositAmount()), x + INVENTORY_SLOT - 4,
            y + INVENTORY_SLOT - 10, 28, TEXT, 0.80F, 0.70F);
        if (selected) graphics.fill(x + 4, y + 3, x + INVENTORY_SLOT - 4, y + 5, GREEN);
    }

    private void drawTransferLane(GuiGraphics graphics, int mouseX, int mouseY) {
        cutPanel(graphics, TRANSFER_X, BODY_Y, TRANSFER_W, BODY_H,
            PANEL_RAISED, LINE, 5);
        drawCenteredFit(graphics, "TRANSFER", TRANSFER_X + TRANSFER_W / 2,
            BODY_Y + 17, TRANSFER_W - 24, TEXT, 1.16F, 0.92F);
        drawCenteredFit(graphics, "ON HAND  >  BANKED", TRANSFER_X + TRANSFER_W / 2,
            BODY_Y + 39, TRANSFER_W - 24, CYAN, 0.88F, 0.74F);
        drawArrow(graphics, TRANSFER_X + 77, BODY_Y + 64, CYAN);

        int quoteX = TRANSFER_X + 12;
        int quoteY = BODY_Y + 94;
        int quoteW = TRANSFER_W - 24;
        int quoteH = 122;
        panel(graphics, quoteX, quoteY, quoteW, quoteH, PANEL, LINE);
        DungeonBankView.DepositQuote quote = selectedDepositQuote;
        if (quote == null) {
            drawCenteredFit(graphics, "SELECT A COIN STACK", TRANSFER_X + TRANSFER_W / 2,
                quoteY + 45, quoteW - 18, MUTED, 0.90F, 0.74F);
        } else {
            drawCurrencyItem(graphics, quote.currency(), quoteX + 12, quoteY + 12, 28);
            drawFit(graphics, formatCount(quote.depositAmount()) + " " + quote.currency().label(),
                quoteX + 48, quoteY + 12, quoteW - 58, TEXT, 0.92F, 0.76F);
            drawFit(graphics, "PHYSICAL INVENTORY STACK", quoteX + 48, quoteY + 30,
                quoteW - 58, MUTED, 0.76F, 0.66F);
            graphics.hLine(quoteX + 10, quoteX + quoteW - 10, quoteY + 51,
                withAlpha(LINE, 120));
            drawFit(graphics,
                "DEPOSIT TAX " + feePercent(view.depositFeeBasisPoints())
                    + "  - " + DungeonBankView.walletText(quote.fee()),
                quoteX + 12, quoteY + 62, quoteW - 24, ORANGE, 0.82F, 0.68F);
            drawFit(graphics,
                "BANK RECEIVES  + " + DungeonBankView.walletText(quote.bankReceives()),
                quoteX + 12, quoteY + 84, quoteW - 24,
                quote.depositable() ? GREEN : RED, 0.84F, 0.72F);
            drawCenteredFit(graphics, quote.depositable() ? "SERVER QUOTE READY"
                    : quote.failureReason(), TRANSFER_X + TRANSFER_W / 2,
                quoteY + 106, quoteW - 18, quote.depositable() ? CYAN : RED, 0.78F, 0.68F);
        }

        int depositX = TRANSFER_X + 12;
        int depositY = BODY_Y + 231;
        boolean canDeposit = quote != null && quote.depositable();
        boolean depositHovered = canDeposit
            && contains(depositX, depositY, TRANSFER_W - 24, 42, mouseX, mouseY);
        drawButton(graphics, depositX, depositY, TRANSFER_W - 24, 42,
            canDeposit ? "DEPOSIT SELECTED" : "NO DEPOSIT AVAILABLE",
            depositHovered, canDeposit, canDeposit ? TEXT : DIM);
        if (canDeposit) {
            addHotspot("deposit", depositX, depositY, TRANSFER_W - 24, 42,
                () -> modal = Modal.DEPOSIT);
        }

        drawButton(graphics, depositX, depositY + 52, TRANSFER_W - 24, 34,
            "SELECT BANK STACK TO WITHDRAW", false, false, DIM);
        drawCenteredFit(graphics, "TAX APPLIES TO DEPOSITS AND WITHDRAWALS",
            TRANSFER_X + TRANSFER_W / 2, BODY_Y + 335,
            TRANSFER_W - 22, ORANGE, 0.76F, 0.66F);
    }

    private void drawBankStorage(GuiGraphics graphics, int mouseX, int mouseY) {
        cutPanel(graphics, BANK_X, BODY_Y, BANK_W, BODY_H, PANEL, LINE, 5);
        drawCenteredFit(graphics, "BANKED FUNDS", BANK_X + BANK_W / 2,
            BODY_Y + 15, BANK_W - 24, TEXT, 1.18F, 0.94F);
        drawCenteredFit(graphics, "PERSISTENT  /  SHOP-SPENDABLE",
            BANK_X + BANK_W / 2, BODY_Y + 36, BANK_W - 24, MUTED, 0.84F, 0.72F);
        drawWalletStrip(graphics, bankedFunds(), BANK_X + 14, BODY_Y + 53,
            BANK_W - 28, 31, true);

        int previousX = BANK_X + 14;
        int navY = BODY_Y + 92;
        boolean hasPrevious = currentPage > 0;
        boolean hasNext = currentPage + 1 < view.pageCount();
        boolean previousHovered = hasPrevious && contains(previousX, navY, 82, 24, mouseX, mouseY);
        drawButton(graphics, previousX, navY, 82, 24, "< PREV",
            previousHovered, hasPrevious, hasPrevious ? CYAN : DIM);
        if (hasPrevious) addHotspot("previous", previousX, navY, 82, 24, () -> changePage(-1));
        int nextX = BANK_X + BANK_W - 96;
        boolean nextHovered = hasNext && contains(nextX, navY, 82, 24, mouseX, mouseY);
        drawButton(graphics, nextX, navY, 82, 24, "NEXT >",
            nextHovered, hasNext, hasNext ? CYAN : DIM);
        if (hasNext) addHotspot("next", nextX, navY, 82, 24, () -> changePage(1));
        int firstSlot = currentPage * DungeonBankView.SLOTS_PER_PAGE;
        int lastSlot = Math.min(view.maximumSlots(), firstSlot + DungeonBankView.SLOTS_PER_PAGE);
        drawCenteredFit(graphics,
            "PAGE " + (currentPage + 1) + " / " + view.pageCount()
                + "   SLOTS " + (firstSlot + 1) + "-" + lastSlot,
            BANK_X + BANK_W / 2, navY + 7, 230, TEXT, 0.82F, 0.72F);

        int gridX = BANK_X + 21;
        int gridY = BODY_Y + 126;
        for (int index = 0; index < BANK_COLUMNS * BANK_ROWS; index++) {
            int absoluteIndex = firstSlot + index;
            int column = index % BANK_COLUMNS;
            int row = index / BANK_COLUMNS;
            int x = gridX + column * (BANK_SLOT + BANK_GAP);
            int y = gridY + row * (BANK_SLOT + BANK_GAP);
            boolean exists = absoluteIndex < view.maximumSlots();
            boolean unlocked = exists && absoluteIndex < view.unlockedSlots();
            DungeonBankView.Slot bankSlot = exists ? view.slotAt(absoluteIndex) : null;
            boolean hovered = bankSlot != null
                && contains(x, y, BANK_SLOT, BANK_SLOT, mouseX, mouseY);
            drawBankSlot(graphics, x, y, absoluteIndex, unlocked, bankSlot, hovered);
            if (bankSlot != null) {
                addHotspot(
                    "withdraw:" + bankSlot.withdrawalQuoteId(),
                    x,
                    y,
                    BANK_SLOT,
                    BANK_SLOT,
                    () -> selectWithdrawal(bankSlot)
                );
            }
        }
    }

    private void drawBankSlot(
        GuiGraphics graphics,
        int x,
        int y,
        int index,
        boolean unlocked,
        @Nullable DungeonBankView.Slot slot,
        boolean hovered
    ) {
        panel(graphics, x, y, BANK_SLOT, BANK_SLOT,
            hovered ? SLOT_HOVER : unlocked ? SLOT : LOCKED,
            hovered ? CYAN : unlocked ? LINE : LINE_DIM);
        if (!unlocked) {
            drawLock(graphics, x + 13, y + 12, DIM);
            return;
        }
        if (slot == null) {
            graphics.hLine(x + 17, x + 25, y + 21, withAlpha(CYAN, 75));
            graphics.vLine(x + 21, y + 17, y + 25, withAlpha(CYAN, 75));
            drawRightFit(graphics, Integer.toString(index + 1), x + BANK_SLOT - 4,
                y + BANK_SLOT - 10, 24, MUTED, 0.70F, 0.62F);
            return;
        }
        drawCurrencyItem(graphics, slot.currency(), x + 7, y + 5, 26);
        drawRightFit(graphics, formatCount(slot.amount()), x + BANK_SLOT - 4,
            y + BANK_SLOT - 10, 30, TEXT, 0.82F, 0.70F);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        cutPanel(graphics, 18, FOOTER_Y, W - 36, FOOTER_H, PANEL, LINE, 5);
        boolean exitHovered = contains(30, FOOTER_Y + 15, 96, 42, mouseX, mouseY);
        drawButton(graphics, 30, FOOTER_Y + 15, 96, 42, "< EXIT",
            exitHovered, true, CYAN);
        addHotspot("exit", 30, FOOTER_Y + 15, 96, 42, this::onClose);

        drawBankGlyph(graphics, 150, FOOTER_Y + 18, 34);
        DungeonBankView.Upgrade upgrade = view.upgrade();
        drawFit(graphics, "UPGRADE BANK CAPACITY", 196, FOOTER_Y + 14,
            280, TEXT, 0.98F, 0.82F);
        if (upgrade.offered()) {
            int added = Math.max(0, upgrade.resultSlots() - view.unlockedSlots());
            drawFit(graphics, upgrade.targetGrade().displayLabel() + "   +" + added + " SLOTS",
                196, FOOTER_Y + 34, 310, GREEN, 0.84F, 0.72F);
            drawFit(graphics, "DEPOSIT TAX " + feePercent(view.depositFeeBasisPoints())
                    + "  >  " + feePercent(upgrade.resultDepositFeeBasisPoints()),
                196, FOOTER_Y + 52, 310, ORANGE, 0.80F, 0.70F);
            drawRightFit(graphics, "COST  " + walletCostText(upgrade.cost()), 706,
                FOOTER_Y + 22, 190, GOLD, 0.90F, 0.76F);
        } else {
            drawFit(graphics, "MAXIMUM BANK GRADE", 196, FOOTER_Y + 36,
                310, GREEN, 0.84F, 0.72F);
        }

        int upgradeX = 722;
        int upgradeY = FOOTER_Y + 15;
        int upgradeW = 204;
        boolean canUpgrade = upgrade.offered() && upgrade.affordable();
        boolean upgradeHovered = canUpgrade
            && contains(upgradeX, upgradeY, upgradeW, 42, mouseX, mouseY);
        String label = !upgrade.offered() ? "MAXIMUM GRADE"
            : upgrade.affordable() ? "UPGRADE BANK" : "INSUFFICIENT FUNDS";
        drawButton(graphics, upgradeX, upgradeY, upgradeW, 42, label,
            upgradeHovered, canUpgrade, canUpgrade ? TEXT : DIM);
        if (canUpgrade) {
            addHotspot("upgrade", upgradeX, upgradeY, upgradeW, 42,
                () -> modal = Modal.UPGRADE);
        }
    }

    private void drawStatus(GuiGraphics graphics) {
        String status = !statusOverride.isBlank() ? statusOverride
            : view.statusMessage().isBlank() ? "BANKED FUNDS AVAILABLE TO DUNGEON SHOPS"
            : view.statusMessage();
        drawCenteredFit(graphics, status, W / 2, 546, W - 40,
            view.preview() ? ORANGE : MUTED, 0.80F, 0.70F);
    }

    private void drawModal(GuiGraphics graphics, int mouseX, int mouseY) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 300.0F);
        graphics.fill(0, 0, W, H, 0xf700040b);
        if (modal == Modal.DEPOSIT) drawDepositModal(graphics, mouseX, mouseY);
        else if (modal == Modal.WITHDRAW) drawWithdrawalModal(graphics, mouseX, mouseY);
        else drawUpgradeModal(graphics, mouseX, mouseY);
        pose.popPose();
    }

    private void drawDepositModal(GuiGraphics graphics, int mouseX, int mouseY) {
        DungeonBankView.DepositQuote quote = selectedDepositQuote;
        if (quote == null) {
            modal = Modal.NONE;
            return;
        }
        int x = 270;
        int y = 142;
        int w = 420;
        int h = 276;
        cutPanel(graphics, x, y, w, h, PANEL_RAISED, CYAN, 7);
        drawCenteredFit(graphics, "CONFIRM DEPOSIT", x + w / 2, y + 20,
            w - 40, TEXT, 1.30F, 1.00F);
        drawCenteredFit(graphics, "SERVER-AUTHORED CURRENCY TRANSFER", x + w / 2, y + 44,
            w - 40, MUTED, 0.84F, 0.72F);
        panel(graphics, x + 24, y + 70, w - 48, 112, PANEL, LINE);
        drawCurrencyItem(graphics, quote.currency(), x + 44, y + 88, 36);
        drawFit(graphics, formatCount(quote.depositAmount()) + " " + quote.currency().label(),
            x + 94, y + 87, 270, TEXT, 1.00F, 0.80F);
        drawFit(graphics, "REMOVED FROM MONEY ON HAND", x + 94, y + 108,
            270, MUTED, 0.78F, 0.68F);
        drawFit(graphics,
            "DEPOSIT TAX " + feePercent(view.depositFeeBasisPoints())
                + "  - " + DungeonBankView.walletText(quote.fee()),
            x + 44, y + 139, w - 56, ORANGE, 0.84F, 0.72F);
        drawFit(graphics,
            "BANKED FUNDS RECEIVE  + " + DungeonBankView.walletText(quote.bankReceives()),
            x + 44, y + 160, w - 56, GREEN, 0.84F, 0.72F);
        drawCenteredFit(graphics,
            "MONEY ON HAND REMAINS  " + formatCount(quote.walletRemaining())
                + " " + quote.currency().label(),
            x + w / 2, y + 194, w - 48, MUTED, 0.84F, 0.72F);
        drawCenteredFit(graphics, quote.requiredSlots() + " NEW BANK SLOT"
                + (quote.requiredSlots() == 1 ? "" : "S") + " REQUIRED",
            x + w / 2, y + 211, w - 48, CYAN, 0.80F, 0.70F);

        boolean backHovered = contains(x + 24, y + 230, 174, 34, mouseX, mouseY);
        drawButton(graphics, x + 24, y + 230, 174, 34, "BACK",
            backHovered, true, TEXT);
        addHotspot("modal-back", x + 24, y + 230, 174, 34, () -> modal = Modal.NONE);
        boolean confirmHovered = contains(x + 222, y + 230, 174, 34, mouseX, mouseY);
        drawButton(graphics, x + 222, y + 230, 174, 34, "DEPOSIT",
            confirmHovered, quote.depositable(), quote.depositable() ? TEXT : DIM);
        if (quote.depositable()) {
            addHotspot("modal-deposit", x + 222, y + 230, 174, 34, this::confirmDeposit);
        }
    }

    private void drawWithdrawalModal(GuiGraphics graphics, int mouseX, int mouseY) {
        DungeonBankView.Slot slot = selectedWithdrawalSlot;
        if (slot == null) {
            modal = Modal.NONE;
            return;
        }
        int x = 285;
        int y = 164;
        int w = 390;
        int h = 246;
        cutPanel(graphics, x, y, w, h, PANEL_RAISED, CYAN, 7);
        drawCenteredFit(graphics, "CONFIRM WITHDRAWAL", x + w / 2, y + 20,
            w - 40, TEXT, 1.24F, 0.96F);
        drawCenteredFit(graphics, "SERVER-AUTHORED CURRENCY TRANSFER", x + w / 2, y + 44,
            w - 40, MUTED, 0.84F, 0.72F);
        panel(graphics, x + 24, y + 70, w - 48, 104, PANEL, LINE);
        drawCurrencyItem(graphics, slot.currency(), x + 44, y + 88, 36);
        drawFit(graphics, formatCount(slot.amount()) + " " + slot.currency().label(),
            x + 94, y + 87, 246, TEXT, 1.00F, 0.80F);
        drawFit(graphics, "BANKED FUNDS  >  PLAYER INVENTORY", x + 94, y + 108,
            246, CYAN, 0.78F, 0.68F);
        drawFit(graphics, "WITHDRAWAL TAX", x + 44, y + 127,
            180, MUTED, 0.82F, 0.70F);
        drawRightFit(graphics, DungeonBankView.walletText(slot.fee()), x + w - 44, y + 127,
            150, ORANGE, 0.84F, 0.68F);
        drawFit(graphics, "PLAYER RECEIVES", x + 44, y + 147,
            180, MUTED, 0.82F, 0.70F);
        drawRightFit(graphics, DungeonBankView.walletText(slot.playerReceives()), x + w - 44,
            y + 147, 150, GREEN, 0.84F, 0.68F);

        String readiness = slot.withdrawable() ? "INVENTORY SPACE VERIFIED"
            : slot.failureReason();
        drawCenteredFit(graphics, readiness, x + w / 2, y + 178,
            w - 48, slot.withdrawable() ? GREEN : RED, 0.82F, 0.70F);
        boolean backHovered = contains(x + 24, y + 198, 160, 34, mouseX, mouseY);
        drawButton(graphics, x + 24, y + 198, 160, 34, "BACK",
            backHovered, true, TEXT);
        addHotspot("modal-back", x + 24, y + 198, 160, 34, () -> modal = Modal.NONE);
        boolean confirmHovered = slot.withdrawable()
            && contains(x + 206, y + 198, 160, 34, mouseX, mouseY);
        drawButton(graphics, x + 206, y + 198, 160, 34, "WITHDRAW",
            confirmHovered, slot.withdrawable(), slot.withdrawable() ? TEXT : DIM);
        if (slot.withdrawable()) {
            addHotspot(
                "modal-withdraw", x + 206, y + 198, 160, 34, this::confirmWithdrawal
            );
        }
    }

    private void drawUpgradeModal(GuiGraphics graphics, int mouseX, int mouseY) {
        DungeonBankView.Upgrade upgrade = view.upgrade();
        if (!upgrade.offered() || !upgrade.affordable()) {
            modal = Modal.NONE;
            return;
        }
        int x = 285;
        int y = 154;
        int w = 390;
        int h = 252;
        cutPanel(graphics, x, y, w, h, PANEL_RAISED, CYAN, 7);
        drawCenteredFit(graphics, "UPGRADE BANK CAPACITY", x + w / 2, y + 20,
            w - 40, TEXT, 1.24F, 0.96F);
        graphics.vLine(x + w / 2, y + 66, y + 143, withAlpha(LINE, 170));
        drawFit(graphics, "CURRENT", x + 30, y + 66, 130, MUTED, 0.84F, 0.72F);
        drawFit(graphics, view.grade().displayLabel(), x + 30, y + 88,
            145, TEXT, 0.86F, 0.72F);
        drawFit(graphics, view.unlockedSlots() + " SLOTS", x + 30, y + 111,
            145, TEXT, 0.92F, 0.74F);
        drawFit(graphics, "RESULT", x + w / 2 + 24, y + 66,
            130, GREEN, 0.84F, 0.72F);
        drawFit(graphics, upgrade.targetGrade().displayLabel(), x + w / 2 + 24, y + 88,
            145, GREEN, 0.86F, 0.72F);
        drawFit(graphics, upgrade.resultSlots() + " SLOTS", x + w / 2 + 24, y + 111,
            145, GREEN, 0.92F, 0.74F);
        drawCenteredFit(graphics, "DRAWBACK: DEPOSIT TAX "
                + feePercent(view.depositFeeBasisPoints()) + "  >  "
                + feePercent(upgrade.resultDepositFeeBasisPoints()),
            x + w / 2, y + 151, w - 48, ORANGE, 0.84F, 0.70F);
        drawCenteredFit(graphics, "COST  " + walletCostText(upgrade.cost()),
            x + w / 2, y + 177, w - 48, GOLD, 0.88F, 0.74F);

        boolean backHovered = contains(x + 24, y + 205, 160, 34, mouseX, mouseY);
        drawButton(graphics, x + 24, y + 205, 160, 34, "BACK",
            backHovered, true, TEXT);
        addHotspot("modal-back", x + 24, y + 205, 160, 34, () -> modal = Modal.NONE);
        boolean confirmHovered = contains(x + 206, y + 205, 160, 34, mouseX, mouseY);
        drawButton(graphics, x + 206, y + 205, 160, 34, "UPGRADE",
            confirmHovered, true, TEXT);
        addHotspot("modal-upgrade", x + 206, y + 205, 160, 34, this::confirmUpgrade);
    }

    private void selectDepositQuote(DungeonBankView.DepositQuote quote) {
        if (selectedDepositQuote != null && selectedDepositQuote.quoteId().equals(quote.quoteId())) {
            return;
        }
        selectedDepositQuote = Objects.requireNonNull(quote, "Dungeon Bank deposit quote");
        statusOverride = "";
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void selectWithdrawal(DungeonBankView.Slot slot) {
        selectedWithdrawalSlot = Objects.requireNonNull(slot, "Dungeon Bank withdrawal slot");
        statusOverride = slot.withdrawable() ? "" : slot.failureReason();
        DungeonClientSoundEffects.playMenuSelect();
        modal = Modal.WITHDRAW;
    }

    private void changePage(int direction) {
        int next = currentPage + direction;
        if (next < 0 || next >= view.pageCount()) return;
        currentPage = next;
        DungeonClientSoundEffects.playMenuSelect();
    }

    private int inventoryPageCount() {
        return Math.max(1, (view.depositQuotes().size() + INVENTORY_SLOTS_PER_PAGE - 1)
            / INVENTORY_SLOTS_PER_PAGE);
    }

    private void changeInventoryPage(int direction) {
        int next = inventoryPage + direction;
        if (next < 0 || next >= inventoryPageCount()) return;
        inventoryPage = next;
        DungeonBankView.DepositQuote first = view.depositQuotes().stream()
            .skip((long) inventoryPage * INVENTORY_SLOTS_PER_PAGE)
            .findFirst()
            .orElse(null);
        if (first != null) selectDepositQuote(first);
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void confirmDeposit() {
        DungeonBankView.DepositQuote quote = selectedDepositQuote;
        if (quote == null || !quote.depositable()) return;
        boolean emitted = DungeonBankUiApi.emitDeposit(view, quote);
        modal = Modal.NONE;
        if (!emitted && view.preview()) {
            statusOverride = "UI PREVIEW - NO BANK DEPOSIT WAS SENT";
        }
    }

    private void confirmUpgrade() {
        boolean emitted = DungeonBankUiApi.emitUpgrade(view);
        modal = Modal.NONE;
        if (!emitted && view.preview()) {
            statusOverride = "UI PREVIEW - NO BANK UPGRADE WAS SENT";
        }
    }

    private void confirmWithdrawal() {
        DungeonBankView.Slot slot = selectedWithdrawalSlot;
        if (slot == null || !slot.withdrawable()) return;
        boolean emitted = DungeonBankUiApi.emitWithdrawal(view, slot);
        modal = Modal.NONE;
        if (!emitted && view.preview()) {
            statusOverride = "UI PREVIEW - NO BANK WITHDRAWAL WAS SENT";
        }
    }

    private void addHotspot(String id, int x, int y, int width, int height, Runnable action) {
        hotspots.add(new Hotspot(id, x, y, width, height, action));
    }

    private @Nullable Hotspot hovered(int mouseX, int mouseY) {
        for (int index = hotspots.size() - 1; index >= 0; index--) {
            Hotspot hotspot = hotspots.get(index);
            if (hotspot.contains(mouseX, mouseY)) return hotspot;
        }
        return null;
    }

    private void updateHoverSound(int mouseX, int mouseY) {
        Hotspot hovered = hovered(mouseX, mouseY);
        String id = hovered == null ? "" : hovered.id();
        if (!id.isBlank() && !id.equals(hoveredHotspotId)) {
            DungeonClientSoundEffects.playMenuHover();
        }
        hoveredHotspotId = id;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Hotspot hotspot = hovered(virtualX(mouseX), virtualY(mouseY));
        if (hotspot != null) {
            hotspot.action().run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (modal != Modal.NONE) {
                modal = Modal.NONE;
                DungeonClientSoundEffects.playMenuClose();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        DungeonClientSoundEffects.playMenuClose();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private DungeonShopView.Wallet bankedFunds() {
        long bronze = 0L;
        long silver = 0L;
        long gold = 0L;
        long stellar = 0L;
        for (DungeonBankView.Slot slot : view.slots()) {
            switch (slot.currency()) {
                case BRONZE -> bronze += slot.amount();
                case SILVER -> silver += slot.amount();
                case GOLD -> gold += slot.amount();
                case STELLAR_GOLD -> stellar += slot.amount();
            }
        }
        return new DungeonShopView.Wallet(bronze, silver, gold, stellar);
    }

    private static void drawWalletStrip(
        GuiGraphics graphics,
        DungeonShopView.Wallet wallet,
        int x,
        int y,
        int width,
        int height,
        boolean compact
    ) {
        DungeonBankView.Currency[] currencies = DungeonBankView.Currency.values();
        long[] amounts = {wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()};
        int visibleCount = 0;
        for (long amount : amounts) if (amount > 0L) visibleCount++;
        if (visibleCount == 0) {
            drawFit(graphics, compact ? "NO BANKED FUNDS" : "NO ELIGIBLE INVENTORY COINS",
                x + 5, y + (height - 8) / 2, width - 10, DIM, 0.82F, 0.70F);
            return;
        }
        int segmentWidth = width / visibleCount;
        int visibleIndex = 0;
        for (int index = 0; index < currencies.length; index++) {
            if (amounts[index] <= 0L) continue;
            int segmentX = x + visibleIndex * segmentWidth;
            if (visibleIndex > 0) {
                graphics.vLine(segmentX, y + 3, y + height - 3, withAlpha(LINE, 145));
            }
            int itemSize = compact ? 18 : 16;
            drawCurrencyItem(graphics, currencies[index], segmentX + 5,
                y + (height - itemSize) / 2, itemSize);
            drawFit(graphics, formatCount(amounts[index]), segmentX + 27,
                y + (compact ? 4 : 1), segmentWidth - 32, TEXT,
                compact ? 0.88F : 0.84F, 0.74F);
            drawFit(graphics, currencies[index].label(), segmentX + 27,
                y + (compact ? 17 : 13), segmentWidth - 32,
                currencies[index].color(), compact ? 0.66F : 0.64F, 0.56F);
            visibleIndex++;
        }
    }

    private static void drawCurrencyItem(
        GuiGraphics graphics,
        DungeonBankView.Currency currency,
        int x,
        int y,
        int size
    ) {
        ResourceLocation itemId = currencyItemId(currency);
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(itemId)
            .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            int inset = Math.max(2, size / 4);
            graphics.fill(x + inset, y + inset, x + size - inset, y + size - inset,
                currency.color());
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + size * 0.5F, y + size * 0.5F, 100.0F);
        float scale = size / 16.0F;
        pose.scale(scale, scale, 1.0F);
        pose.translate(-8.0F, -8.0F, 0.0F);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private static ResourceLocation currencyItemId(DungeonBankView.Currency currency) {
        ResourceLocation primary = ResourceLocation.parse(currency.itemId());
        if (currency != DungeonBankView.Currency.STELLAR_GOLD
                || BuiltInRegistries.ITEM.containsKey(primary)) {
            return primary;
        }
        return ResourceLocation.parse("tensura:stellar_coin");
    }

    private static void drawBankGlyph(GuiGraphics graphics, int x, int y, int size) {
        int scale = Math.max(1, size / 16);
        int width = 12 * scale;
        int left = x + (size - width) / 2;
        graphics.fill(left + 2 * scale, y + 3 * scale,
            left + 10 * scale, y + 5 * scale, LINE);
        graphics.fill(left, y + 5 * scale, left + 12 * scale, y + 7 * scale, CYAN);
        for (int column = 0; column < 3; column++) {
            int pillarX = left + (1 + column * 4) * scale;
            graphics.fill(pillarX, y + 7 * scale,
                pillarX + 2 * scale, y + 13 * scale, TEXT);
        }
        graphics.fill(left, y + 13 * scale, left + 12 * scale, y + 15 * scale, CYAN);
    }

    private static void drawLock(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 4, y, x + 12, y + 2, color);
        graphics.fill(x + 2, y + 2, x + 5, y + 8, color);
        graphics.fill(x + 11, y + 2, x + 14, y + 8, color);
        graphics.fill(x + 2, y + 7, x + 14, y + 15, color);
        graphics.fill(x + 7, y + 10, x + 9, y + 14, LOCKED);
    }

    private static void drawArrow(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y + 8, x + 36, y + 12, withAlpha(color, 115));
        graphics.fill(x + 28, y + 2, x + 32, y + 18, withAlpha(color, 150));
        graphics.fill(x + 32, y + 5, x + 36, y + 15, withAlpha(color, 190));
        graphics.fill(x + 36, y + 8, x + 40, y + 12, color);
    }

    private static void drawButton(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        String label,
        boolean hovered,
        boolean active,
        int textColor
    ) {
        int surface = !active ? 0xff07111e : hovered ? 0xff12364b : 0xff0a2436;
        int border = !active ? LINE_DIM : hovered ? CYAN : LINE_BRIGHT;
        panel(graphics, x, y, width, height, surface, border);
        if (active && label.contains("DEPOSIT")) {
            graphics.fill(x + 2, y + 2, x + 5, y + height - 2, GREEN);
        }
        if (active && label.contains("UPGRADE")) {
            graphics.fill(x + 2, y + 2, x + 5, y + height - 2, GOLD);
        }
        drawCenteredFit(graphics, label, x + width / 2,
            y + (height - 8) / 2, width - 14, textColor, 1.00F, 0.74F);
    }

    private static void panel(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int surface,
        int border
    ) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, surface);
    }

    private static void cutPanel(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int surface,
        int border,
        int cut
    ) {
        graphics.fill(x + cut, y, x + width - cut, y + height, border);
        graphics.fill(x, y + cut, x + width, y + height - cut, border);
        graphics.fill(x + cut, y + 1, x + width - cut, y + height - 1, surface);
        graphics.fill(x + 1, y + cut, x + width - 1, y + height - cut, surface);
        graphics.fill(x + 2, y + 2, x + cut, y + cut, surface);
        graphics.fill(x + width - cut, y + 2, x + width - 2, y + cut, surface);
        graphics.fill(x + 2, y + height - cut, x + cut, y + height - 2, surface);
        graphics.fill(x + width - cut, y + height - cut,
            x + width - 2, y + height - 2, surface);
    }

    private static void drawPixelFrame(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int color,
        int cut
    ) {
        graphics.hLine(x + cut, x + width - cut, y, color);
        graphics.hLine(x + cut, x + width - cut, y + height, color);
        graphics.vLine(x, y + cut, y + height - cut, color);
        graphics.vLine(x + width, y + cut, y + height - cut, color);
    }

    private static void drawFit(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int maximumWidth,
        int color,
        float preferredScale,
        float minimumScale
    ) {
        int rawWidth = Math.max(1, Minecraft.getInstance().font.width(text));
        float scale = Math.max(minimumScale,
            Math.min(preferredScale, maximumWidth / (float) rawWidth));
        drawScaled(graphics, ellipsize(text, maximumWidth, scale), x, y, color, scale);
    }

    private static void drawCenteredFit(
        GuiGraphics graphics,
        String text,
        int centerX,
        int y,
        int maximumWidth,
        int color,
        float preferredScale,
        float minimumScale
    ) {
        int rawWidth = Math.max(1, Minecraft.getInstance().font.width(text));
        float scale = Math.max(minimumScale,
            Math.min(preferredScale, maximumWidth / (float) rawWidth));
        String fitted = ellipsize(text, maximumWidth, scale);
        int renderedWidth = Math.round(Minecraft.getInstance().font.width(fitted) * scale);
        drawScaled(graphics, fitted, centerX - renderedWidth / 2, y, color, scale);
    }

    private static void drawRightFit(
        GuiGraphics graphics,
        String text,
        int rightX,
        int y,
        int maximumWidth,
        int color,
        float preferredScale,
        float minimumScale
    ) {
        int rawWidth = Math.max(1, Minecraft.getInstance().font.width(text));
        float scale = Math.max(minimumScale,
            Math.min(preferredScale, maximumWidth / (float) rawWidth));
        String fitted = ellipsize(text, maximumWidth, scale);
        int renderedWidth = Math.round(Minecraft.getInstance().font.width(fitted) * scale);
        drawScaled(graphics, fitted, rightX - renderedWidth, y, color, scale);
    }

    private static String ellipsize(String text, int maximumWidth, float scale) {
        int rawMaximum = Math.max(1,
            (int) Math.floor(maximumWidth / Math.max(0.01F, scale)));
        if (Minecraft.getInstance().font.width(text) <= rawMaximum) return text;
        String suffix = "...";
        int bodyWidth = Math.max(1, rawMaximum - Minecraft.getInstance().font.width(suffix));
        return Minecraft.getInstance().font.plainSubstrByWidth(text, bodyWidth) + suffix;
    }

    private static void drawScaled(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int color,
        float scale
    ) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static String walletCostText(DungeonShopView.Wallet wallet) {
        List<String> parts = new ArrayList<>();
        if (wallet.stellarGold() > 0L) parts.add(formatCount(wallet.stellarGold()) + " STELLAR");
        if (wallet.gold() > 0L) parts.add(formatCount(wallet.gold()) + " GOLD");
        if (wallet.silver() > 0L) parts.add(formatCount(wallet.silver()) + " SILVER");
        if (wallet.bronze() > 0L) parts.add(formatCount(wallet.bronze()) + " BRONZE");
        return parts.isEmpty() ? "FREE" : String.join(" + ", parts);
    }

    private static String feePercent(int basisPoints) {
        int clamped = Math.max(0, Math.min(10_000, basisPoints));
        int whole = clamped / 100;
        int fraction = clamped % 100;
        return fraction == 0 ? whole + "%"
            : whole + "." + String.format(Locale.ROOT, "%02d", fraction) + "%";
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00ffffff);
    }

    private static boolean contains(
        int x,
        int y,
        int width,
        int height,
        double mouseX,
        double mouseY
    ) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Modal {
        NONE,
        DEPOSIT,
        WITHDRAW,
        UPGRADE
    }

    private record Hotspot(String id, int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return DungeonBankScreen.contains(x, y, width, height, mouseX, mouseY);
        }
    }
}
