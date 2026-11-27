package dev.luke.tensuradungeon.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Size;
import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
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
import java.util.function.Consumer;

final class DungeonBankLdLib2Screen extends ModularUIScreen {
    private static final int LOGICAL_WIDTH = 960;
    private static final int LOGICAL_HEIGHT = 560;
    private static final int UPGRADE_MODAL_WIDTH = 360;
    private static final int UPGRADE_MODAL_HEIGHT = 232;
    private static final int UPGRADE_MODAL_LEFT = (LOGICAL_WIDTH - UPGRADE_MODAL_WIDTH) / 2;
    private static final int UPGRADE_MODAL_TOP = 156;
    private static final int DEPOSIT_MODAL_WIDTH = 430;
    private static final int DEPOSIT_MODAL_HEIGHT = 284;
    private static final int DEPOSIT_MODAL_LEFT = (LOGICAL_WIDTH - DEPOSIT_MODAL_WIDTH) / 2;
    private static final int DEPOSIT_MODAL_TOP = 138;
    private static final int SLOT_COLUMNS = 9;
    private static final int SLOT_ROWS = 5;
    private static final int BANK_SLOT_SIZE = 38;
    private static final int BANK_SLOT_GAP = 3;
    private static final int INVENTORY_COLUMNS = 5;
    private static final int INVENTORY_ROWS = 4;
    private static final int INVENTORY_SLOT_SIZE = 44;
    private static final int INVENTORY_SLOT_GAP = 4;
    private static final float BANK_ITEM_Z_OFFSET = -140f;
    private static final int CYAN = 0xff49d7ff;
    private static final int GREEN = 0xff76ef75;
    private static final int GOLD = 0xffffc447;
    private static final int ORANGE = 0xffff9f43;
    private static final int TEXT = 0xffe7edf8;
    private static final int MUTED = 0xff9aa8bf;
    private static final int DIM = 0xff647089;
    private static final int BASE = 0xff020b1c;
    private static final int PANEL = 0xff031126;
    private static final int PANEL_RAISED = 0xff071a31;
    private static final int BORDER = 0xff2f7fa8;
    private static final int LOCKED_BORDER = 0xff26364c;
    private static final int LOCKED_SURFACE = 0xff020815;
    private static final int DANGER = 0xffff6575;

    private static final class UiParts {
        final ModularUI modularUI;
        final List<BankSlotElement> slots;
        final WalletStripElement wallet;
        final WalletStripElement bankedWallet;
        final CapacityBarElement capacity;
        final TransferQuoteElement transferQuote;
        final Label bankSummary;
        final Label instructions;
        final Label status;
        final Label upgradeTarget;
        final Label upgradeCost;
        final Button exitButton;
        final Button depositButton;
        final Button previousPageButton;
        final Label pageLabel;
        final Button nextPageButton;
        final Button upgradeButton;
        final UIElement confirmationOverlay;
        final Label confirmationCurrent;
        final Label confirmationResult;
        final Label confirmationFeeWarning;
        final Label confirmationCost;
        final Button confirmationBack;
        final Button confirmationConfirm;
        final List<DepositQuoteElement> depositQuotes;
        final UIElement depositOverlay;
        final Label depositSelection;
        final Label depositFee;
        final Label depositNet;
        final Label depositRemaining;
        final Label depositStatus;
        final Button depositBack;
        final Button depositConfirm;

        UiParts(
            ModularUI modularUI,
            List<BankSlotElement> slots,
            WalletStripElement wallet,
            WalletStripElement bankedWallet,
            CapacityBarElement capacity,
            TransferQuoteElement transferQuote,
            Label bankSummary,
            Label instructions,
            Label status,
            Label upgradeTarget,
            Label upgradeCost,
            Button exitButton,
            Button depositButton,
            Button previousPageButton,
            Label pageLabel,
            Button nextPageButton,
            Button upgradeButton,
            UIElement confirmationOverlay,
            Label confirmationCurrent,
            Label confirmationResult,
            Label confirmationFeeWarning,
            Label confirmationCost,
            Button confirmationBack,
            Button confirmationConfirm,
            List<DepositQuoteElement> depositQuotes,
            UIElement depositOverlay,
            Label depositSelection,
            Label depositFee,
            Label depositNet,
            Label depositRemaining,
            Label depositStatus,
            Button depositBack,
            Button depositConfirm
        ) {
            this.modularUI = modularUI;
            this.slots = slots;
            this.wallet = wallet;
            this.bankedWallet = bankedWallet;
            this.capacity = capacity;
            this.transferQuote = transferQuote;
            this.bankSummary = bankSummary;
            this.instructions = instructions;
            this.status = status;
            this.upgradeTarget = upgradeTarget;
            this.upgradeCost = upgradeCost;
            this.exitButton = exitButton;
            this.depositButton = depositButton;
            this.previousPageButton = previousPageButton;
            this.pageLabel = pageLabel;
            this.nextPageButton = nextPageButton;
            this.upgradeButton = upgradeButton;
            this.confirmationOverlay = confirmationOverlay;
            this.confirmationCurrent = confirmationCurrent;
            this.confirmationResult = confirmationResult;
            this.confirmationFeeWarning = confirmationFeeWarning;
            this.confirmationCost = confirmationCost;
            this.confirmationBack = confirmationBack;
            this.confirmationConfirm = confirmationConfirm;
            this.depositQuotes = depositQuotes;
            this.depositOverlay = depositOverlay;
            this.depositSelection = depositSelection;
            this.depositFee = depositFee;
            this.depositNet = depositNet;
            this.depositRemaining = depositRemaining;
            this.depositStatus = depositStatus;
            this.depositBack = depositBack;
            this.depositConfirm = depositConfirm;
        }
    }

    private static final class BorderedPanelElement extends UIElement {
        private final int surface;
        private final int border;

        BorderedPanelElement(int surface, int border) {
            this.surface = surface;
            this.border = border;
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            fillPixelPanel(
                context,
                left,
                top,
                left + Math.round(getSizeWidth()),
                top + Math.round(getSizeHeight()),
                surface,
                border
            );
        }
    }

    private static final class BorderedButton extends Button {
        private int borderColor;

        BorderedButton(int borderColor) {
            this.borderColor = borderColor;
        }

        void setBorderColor(int borderColor) {
            this.borderColor = borderColor;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            drawPixelOutline(
                context,
                left,
                top,
                left + Math.round(getSizeWidth()),
                top + Math.round(getSizeHeight()),
                borderColor
            );
        }
    }

    private static final class WalletStripElement extends UIElement {
        private DungeonShopView.Wallet wallet = DungeonShopView.Wallet.empty();

        WalletStripElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setWallet(DungeonShopView.Wallet wallet) {
            this.wallet = wallet == null ? DungeonShopView.Wallet.empty() : wallet;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            fillPixelPanel(context, left, top, left + width, top + height, PANEL_RAISED, BORDER);

            DungeonBankView.Currency[] currencies = DungeonBankView.Currency.values();
            long[] values = {
                wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()
            };
            int segmentWidth = width / currencies.length;
            for (int index = 0; index < currencies.length; index++) {
                DungeonBankView.Currency currency = currencies[index];
                int segmentLeft = left + index * segmentWidth;
                if (index > 0) {
                    context.graphics.fill(segmentLeft, top + 8, segmentLeft + 1,
                        top + height - 8, BORDER);
                }
                drawCurrencyItem(context, currency, segmentLeft + 10, top + 13, 22);
                drawScaledText(context, formatCount(values[index]), segmentLeft + 38, top + 12,
                    TEXT, 1.05f);
                drawScaledText(context, currency.label(), segmentLeft + 38, top + 30,
                    currency.color(), 0.72f);
            }
        }
    }

    private static final class CapacityBarElement extends UIElement {
        private int used;
        private int unlocked = 1;

        CapacityBarElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setCapacity(int used, int unlocked) {
            this.used = Math.max(0, used);
            this.unlocked = Math.max(1, unlocked);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            drawScaledText(context, "BANK CAPACITY", left, top, CYAN, 0.78f);
            drawScaledText(context, used + " / " + unlocked + " SLOTS", left, top + 16,
                TEXT, 0.92f);
            int barTop = bottom - 10;
            fillPixelPanel(context, left, barTop, right, bottom, LOCKED_SURFACE, LOCKED_BORDER);
            int innerWidth = Math.max(0, right - left - 2);
            int fillWidth = Math.round(innerWidth * Math.min(1f, used / (float) unlocked));
            if (fillWidth > 0) {
                context.graphics.fill(left + 1, barTop + 1, left + 1 + fillWidth,
                    bottom - 1, CYAN);
            }
        }
    }

    private static final class TransferQuoteElement extends UIElement {
        private @Nullable DungeonBankView.DepositQuote quote;
        private int feeBasisPoints;

        TransferQuoteElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setQuote(@Nullable DungeonBankView.DepositQuote quote, int feeBasisPoints) {
            this.quote = quote;
            this.feeBasisPoints = feeBasisPoints;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            fillPixelPanel(context, left, top, right, bottom, PANEL, BORDER);
            DungeonBankView.DepositQuote current = quote;
            if (current == null) {
                drawScaledText(context, "SELECT AN ON-HAND", left + 12, top + 23,
                    MUTED, 0.82f);
                drawScaledText(context, "COIN STACK", left + 12, top + 39, MUTED, 0.82f);
                return;
            }
            drawCurrencyItem(context, current.currency(), left + 12, top + 12, 28);
            drawScaledText(context, formatCount(current.depositAmount()) + " "
                + current.currency().label(), left + 48, top + 12, TEXT, 0.82f);
            drawScaledText(context, "FROM PLAYER INVENTORY", left + 48, top + 29,
                MUTED, 0.64f);
            drawScaledText(context, "FEE " + depositFeePercentText(feeBasisPoints)
                + "   -" + DungeonBankView.walletText(current.fee()),
                left + 12, top + 54,
                ORANGE, 0.78f);
            drawScaledText(context,
                "BANK RECEIVES   +" + DungeonBankView.walletText(current.bankReceives()),
                left + 12, top + 73, current.depositable() ? GREEN : DANGER, 0.78f);
            drawScaledText(context, current.depositable() ? "SERVER QUOTE READY"
                : current.failureReason(), left + 12, bottom - 17,
                current.depositable() ? CYAN : DANGER, 0.66f);
        }
    }

    private static final class EmptyInventorySlotElement extends UIElement {
        EmptyInventorySlotElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            fillPixelPanel(context, left, top, left + Math.round(getSizeWidth()),
                top + Math.round(getSizeHeight()), 0xff061321, LOCKED_BORDER);
        }
    }

    private static final class BankSlotElement extends UIElement {
        private int index;
        private boolean unlocked;
        private @Nullable DungeonBankView.Slot slot;
        private boolean hovered;

        BankSlotElement(int index) {
            this.index = index;
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            addEventListener(UIEvents.MOUSE_ENTER, event -> {
                hovered = true;
                if (unlocked) DungeonClientSoundEffects.playMenuHover();
            });
            addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = false);
        }

        void setState(int index, boolean unlocked, @Nullable DungeonBankView.Slot slot) {
            this.index = index;
            this.unlocked = unlocked;
            this.slot = slot;
            setAllowHitTest(unlocked);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            int surface = unlocked ? (hovered ? 0xff0a2540 : PANEL_RAISED) : LOCKED_SURFACE;
            int border = unlocked ? (hovered ? CYAN : BORDER) : LOCKED_BORDER;
            fillPixelPanel(context, left, top, right, bottom, surface, border);

            if (!unlocked) {
                drawLock(context, Math.round((left + right) * 0.5f) - 8,
                    Math.round((top + bottom) * 0.5f) - 8, DIM);
                return;
            }
            if (slot == null) {
                int centerX = (left + right) / 2;
                int centerY = (top + bottom) / 2;
                context.graphics.fill(centerX - 5, centerY, centerX + 6, centerY + 1, 0xff25516a);
                context.graphics.fill(centerX, centerY - 5, centerX + 1, centerY + 6, 0xff25516a);
                String slotNumber = Integer.toString(index + 1);
                float numberScale = 0.78f;
                int numberWidth = Math.round(
                    Minecraft.getInstance().font.width(slotNumber) * numberScale
                );
                drawScaledText(context, slotNumber, right - 5 - numberWidth, bottom - 11,
                    TEXT, numberScale);
                return;
            }

            int itemSize = 26;
            drawCurrencyItem(context, slot.currency(),
                left + (right - left - itemSize) / 2,
                top + 7,
                itemSize);
            String amount = formatCount(slot.amount());
            float amountScale = 0.78f;
            int textWidth = Math.round(Minecraft.getInstance().font.width(amount) * amountScale);
            drawScaledText(context, amount, right - 5 - textWidth, bottom - 11,
                TEXT, amountScale);
        }
    }

    private static final class DepositQuoteElement extends UIElement {
        private final DungeonBankView.Currency currency;
        private @Nullable DungeonBankView.DepositQuote quote;
        private Consumer<DungeonBankView.DepositQuote> selectAction = ignored -> {};
        private boolean selected;
        private boolean hovered;

        DepositQuoteElement(DungeonBankView.Currency currency) {
            this.currency = Objects.requireNonNull(currency, "Dungeon Bank deposit currency");
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            addEventListener(UIEvents.MOUSE_ENTER, event -> {
                hovered = true;
                if (quote != null) DungeonClientSoundEffects.playMenuHover();
            });
            addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = false);
            addEventListener(UIEvents.MOUSE_UP, event -> {
                if (quote != null) selectAction.accept(quote);
            });
        }

        void setSelectAction(Consumer<DungeonBankView.DepositQuote> selectAction) {
            this.selectAction = Objects.requireNonNull(selectAction, "Dungeon Bank deposit selector");
        }

        void setQuote(@Nullable DungeonBankView.DepositQuote quote, boolean selected) {
            this.quote = quote;
            this.selected = selected;
            setAllowHitTest(quote != null);
        }

        DungeonBankView.Currency currency() {
            return currency;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            int border = selected ? GREEN : hovered ? CYAN : quote == null ? LOCKED_BORDER : BORDER;
            int surface = selected ? 0xff0a2432 : quote == null ? LOCKED_SURFACE : PANEL_RAISED;
            fillPixelPanel(context, left, top, right, bottom, surface, border);

            int itemSize = 26;
            drawCurrencyItem(context, currency,
                left + (right - left - itemSize) / 2, top + 6, itemSize);
            if (quote == null) {
                drawScaledText(context, "--", right - 16, bottom - 12, DIM, 0.68f);
                return;
            }
            String amount = formatCount(quote.depositAmount());
            float amountScale = 0.78f;
            int amountWidth = Math.round(Minecraft.getInstance().font.width(amount) * amountScale);
            drawScaledText(context, amount, right - 5 - amountWidth, bottom - 11,
                TEXT, amountScale);
        }
    }

    private static final class BankGlyphElement extends UIElement {
        BankGlyphElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int x = left + Math.max(0, (Math.round(getSizeWidth()) - 32) / 2);
            int y = top + Math.max(0, (Math.round(getSizeHeight()) - 32) / 2);
            context.graphics.fill(x + 4, y + 8, x + 28, y + 11, CYAN);
            context.graphics.fill(x + 7, y + 5, x + 25, y + 8, 0xff2f7fa8);
            context.graphics.fill(x + 10, y + 2, x + 22, y + 5, 0xff194963);
            context.graphics.fill(x + 6, y + 11, x + 9, y + 27, TEXT);
            context.graphics.fill(x + 14, y + 11, x + 17, y + 27, TEXT);
            context.graphics.fill(x + 23, y + 11, x + 26, y + 27, TEXT);
            context.graphics.fill(x + 4, y + 27, x + 28, y + 30, CYAN);
        }
    }

    private final Screen parent;
    private final List<BankSlotElement> slotElements;
    private final WalletStripElement walletElement;
    private final WalletStripElement bankedWalletElement;
    private final CapacityBarElement capacityElement;
    private final TransferQuoteElement transferQuoteElement;
    private final Label bankSummaryLabel;
    private final Label instructionsLabel;
    private final Label statusLabel;
    private final Label upgradeTargetLabel;
    private final Label upgradeCostLabel;
    private final Button exitButton;
    private final Button depositButton;
    private final Button previousPageButton;
    private final Label pageLabel;
    private final Button nextPageButton;
    private final Button upgradeButton;
    private final UIElement confirmationOverlay;
    private final Label confirmationCurrent;
    private final Label confirmationResult;
    private final Label confirmationFeeWarning;
    private final Label confirmationCost;
    private final Button confirmationBack;
    private final Button confirmationConfirm;
    private final List<DepositQuoteElement> depositQuoteElements;
    private final UIElement depositOverlay;
    private final Label depositSelectionLabel;
    private final Label depositFeeLabel;
    private final Label depositNetLabel;
    private final Label depositRemainingLabel;
    private final Label depositStatusLabel;
    private final Button depositBack;
    private final Button depositConfirm;
    private DungeonBankView view;
    private boolean confirmationOpen;
    private boolean depositOpen;
    private @Nullable DungeonBankView.DepositQuote selectedDepositQuote;
    private int currentPage;

    DungeonBankLdLib2Screen(DungeonBankView view, @Nullable Screen parent) {
        this(createUi(view), view, parent);
    }

    private DungeonBankLdLib2Screen(UiParts parts, DungeonBankView view, @Nullable Screen parent) {
        super(parts.modularUI, Component.literal("Dungeon Bank"));
        this.parent = parent;
        this.slotElements = parts.slots;
        this.walletElement = parts.wallet;
        this.bankedWalletElement = parts.bankedWallet;
        this.capacityElement = parts.capacity;
        this.transferQuoteElement = parts.transferQuote;
        this.bankSummaryLabel = parts.bankSummary;
        this.instructionsLabel = parts.instructions;
        this.statusLabel = parts.status;
        this.upgradeTargetLabel = parts.upgradeTarget;
        this.upgradeCostLabel = parts.upgradeCost;
        this.exitButton = parts.exitButton;
        this.depositButton = parts.depositButton;
        this.previousPageButton = parts.previousPageButton;
        this.pageLabel = parts.pageLabel;
        this.nextPageButton = parts.nextPageButton;
        this.upgradeButton = parts.upgradeButton;
        this.confirmationOverlay = parts.confirmationOverlay;
        this.confirmationCurrent = parts.confirmationCurrent;
        this.confirmationResult = parts.confirmationResult;
        this.confirmationFeeWarning = parts.confirmationFeeWarning;
        this.confirmationCost = parts.confirmationCost;
        this.confirmationBack = parts.confirmationBack;
        this.confirmationConfirm = parts.confirmationConfirm;
        this.depositQuoteElements = parts.depositQuotes;
        this.depositOverlay = parts.depositOverlay;
        this.depositSelectionLabel = parts.depositSelection;
        this.depositFeeLabel = parts.depositFee;
        this.depositNetLabel = parts.depositNet;
        this.depositRemainingLabel = parts.depositRemaining;
        this.depositStatusLabel = parts.depositStatus;
        this.depositBack = parts.depositBack;
        this.depositConfirm = parts.depositConfirm;
        this.view = Objects.requireNonNull(view, "Dungeon Bank view");

        exitButton.setOnClick(event -> onClose());
        addHoverSound(exitButton);
        depositButton.setOnClick(event -> openDepositPopup());
        addHoverSound(depositButton);
        previousPageButton.setOnClick(event -> changePage(-1));
        addHoverSound(previousPageButton);
        nextPageButton.setOnClick(event -> changePage(1));
        addHoverSound(nextPageButton);
        upgradeButton.setOnClick(event -> openUpgradeConfirmation());
        addHoverSound(upgradeButton);
        confirmationBack.setOnClick(event -> closeUpgradeConfirmation());
        addHoverSound(confirmationBack);
        confirmationConfirm.setOnClick(event -> confirmUpgrade());
        addHoverSound(confirmationConfirm);
        for (DepositQuoteElement element : depositQuoteElements) {
            element.setSelectAction(this::selectDepositQuote);
        }
        depositBack.setOnClick(event -> closeDepositPopup());
        addHoverSound(depositBack);
        depositConfirm.setOnClick(event -> confirmDeposit());
        addHoverSound(depositConfirm);
        refreshFromView();
    }

    private static UiParts createUi(DungeonBankView initialView) {
        UIElement root = new BorderedPanelElement(BASE, BORDER)
            .layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
                .width(LOGICAL_WIDTH).height(LOGICAL_HEIGHT)
                .paddingAll(14).gapAll(6))
            .transform(transform -> transform.scale(0.70f).pivot(0.5f, 0.5f));

        BankGlyphElement headerGlyph = new BankGlyphElement();
        headerGlyph.layout(layout -> layout.width(52).height(52));
        Label title = label("BANK VAULT", 30, TEXT, 19f);
        Label bankSummary = label("", 18, CYAN, 8f);
        UIElement brandCopy = column(0x00000000, 52);
        brandCopy.layout(layout -> layout.width(184).height(52)
            .justifyContent(AlignContent.CENTER).gapAll(1));
        brandCopy.addChildren(title, bankSummary);
        UIElement brand = row(0x00000000, 62);
        brand.layout(layout -> layout.width(246).height(62)
            .alignItems(AlignItems.CENTER).gapAll(8));
        brand.addChildren(headerGlyph, brandCopy);

        Label walletTitle = label("MONEY ON HAND", 16, CYAN, 9f);
        Label walletSubtitle = label("PHYSICAL COINS IN PLAYER INVENTORY", 12, MUTED, 6.5f);
        WalletStripElement wallet = new WalletStripElement();
        wallet.layout(layout -> layout.widthPercent(100).height(44));
        UIElement walletBlock = column(0x00000000, 72);
        walletBlock.layout(layout -> layout.width(474).height(72).gapAll(1));
        walletBlock.addChildren(walletTitle, walletSubtitle, wallet);

        CapacityBarElement capacity = new CapacityBarElement();
        capacity.layout(layout -> layout.flexGrow(1).flexBasis(0).height(52));
        UIElement capacityBlock = new BorderedPanelElement(PANEL, BORDER);
        capacityBlock.layout(layout -> layout.flexGrow(1).flexBasis(0).height(62)
            .paddingAll(8).alignItems(AlignItems.CENTER));
        capacityBlock.addChild(capacity);

        UIElement header = row(0x00000000, 72);
        header.layout(layout -> layout.widthPercent(100).height(72)
            .alignItems(AlignItems.CENTER).gapAll(10));
        header.addChildren(brand, walletBlock, capacityBlock);

        List<DepositQuoteElement> depositQuotes = new ArrayList<>();
        UIElement inventoryMatrix = column(0x00000000,
            INVENTORY_ROWS * INVENTORY_SLOT_SIZE + (INVENTORY_ROWS - 1) * INVENTORY_SLOT_GAP);
        inventoryMatrix.layout(layout -> layout.width(
            INVENTORY_COLUMNS * INVENTORY_SLOT_SIZE
                + (INVENTORY_COLUMNS - 1) * INVENTORY_SLOT_GAP)
            .gapAll(INVENTORY_SLOT_GAP));
        int inventoryIndex = 0;
        for (int rowIndex = 0; rowIndex < INVENTORY_ROWS; rowIndex++) {
            UIElement inventoryRow = row(0x00000000, INVENTORY_SLOT_SIZE);
            inventoryRow.layout(layout -> layout.height(INVENTORY_SLOT_SIZE)
                .gapAll(INVENTORY_SLOT_GAP));
            for (int columnIndex = 0; columnIndex < INVENTORY_COLUMNS; columnIndex++) {
                UIElement cell;
                if (inventoryIndex < DungeonBankView.Currency.values().length) {
                    DepositQuoteElement quote = new DepositQuoteElement(
                        DungeonBankView.Currency.values()[inventoryIndex]
                    );
                    quote.layout(layout -> layout.width(INVENTORY_SLOT_SIZE)
                        .height(INVENTORY_SLOT_SIZE));
                    depositQuotes.add(quote);
                    cell = quote;
                } else {
                    EmptyInventorySlotElement empty = new EmptyInventorySlotElement();
                    empty.layout(layout -> layout.width(INVENTORY_SLOT_SIZE)
                        .height(INVENTORY_SLOT_SIZE));
                    cell = empty;
                }
                inventoryRow.addChild(cell);
                inventoryIndex++;
            }
            inventoryMatrix.addChild(inventoryRow);
        }
        Label inventoryTitle = label("PHYSICAL COIN STACKS", 20, TEXT, 11f);
        inventoryTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label inventoryFilter = label("COIN STACKS FROM PLAYER INVENTORY", 16, MUTED, 6.8f);
        inventoryFilter.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label inventoryHint = label("SELECT A STACK TO DEPOSIT", 18, CYAN, 7.2f);
        inventoryHint.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        UIElement inventoryPanel = new BorderedPanelElement(PANEL, BORDER);
        inventoryPanel.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
            .width(252).height(324).paddingAll(8).alignItems(AlignItems.CENTER).gapAll(5));
        inventoryPanel.addChildren(inventoryTitle, inventoryFilter, inventoryMatrix, inventoryHint);

        Label transferTitle = label("TRANSFER", 22, TEXT, 11f);
        transferTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label transferSubtitle = label("ON HAND  >  BANKED", 16, CYAN, 7.3f);
        transferSubtitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        TransferQuoteElement transferQuote = new TransferQuoteElement();
        transferQuote.layout(layout -> layout.widthPercent(100).height(112));
        Button depositButton = new BorderedButton(GREEN).setText("DEPOSIT SELECTED");
        depositButton.layout(layout -> layout.widthPercent(100).height(42));
        depositButton.textStyle(style -> style.textColor(TEXT).fontSize(9f));
        applyUpgradeButtonStyle(depositButton, true);
        Button withdrawalButton = new BorderedButton(LOCKED_BORDER)
            .setText("WITHDRAWAL PENDING");
        withdrawalButton.layout(layout -> layout.widthPercent(100).height(34));
        withdrawalButton.textStyle(style -> style.textColor(DIM).fontSize(7.4f));
        withdrawalButton.setActive(false);
        applyQuietButtonStyle(withdrawalButton);
        Label feeNotice = label("DEPOSIT TAX IS SHOWN BEFORE CONFIRM", 20, ORANGE, 6.4f);
        feeNotice.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        UIElement transferPanel = new BorderedPanelElement(PANEL_RAISED, BORDER);
        transferPanel.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
            .width(210).height(324).paddingAll(10).alignItems(AlignItems.CENTER).gapAll(7));
        transferPanel.addChildren(
            transferTitle, transferSubtitle, transferQuote, depositButton, withdrawalButton, feeNotice
        );

        List<BankSlotElement> slots = new ArrayList<>();
        int slotMatrixWidth = SLOT_COLUMNS * BANK_SLOT_SIZE + (SLOT_COLUMNS - 1) * BANK_SLOT_GAP;
        int slotMatrixHeight = SLOT_ROWS * BANK_SLOT_SIZE + (SLOT_ROWS - 1) * BANK_SLOT_GAP;

        Button previousPageButton = new BorderedButton(BORDER).setText("<  PREVIOUS");
        previousPageButton.layout(layout -> layout.width(86).height(24));
        previousPageButton.textStyle(style -> style.textColor(CYAN).fontSize(7f));
        applyQuietButtonStyle(previousPageButton);
        Label pageLabel = label("", 28, TEXT, 9.5f);
        pageLabel.layout(layout -> layout.flexGrow(1).flexBasis(0).height(24));
        pageLabel.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER).fontSize(7.4f));
        Button nextPageButton = new BorderedButton(BORDER).setText("NEXT  >");
        nextPageButton.layout(layout -> layout.width(86).height(24));
        nextPageButton.textStyle(style -> style.textColor(CYAN).fontSize(7f));
        applyQuietButtonStyle(nextPageButton);
        UIElement pageNavigation = row(0x00000000, 28);
        pageNavigation.layout(layout -> layout
            .width(slotMatrixWidth).height(24)
            .alignItems(AlignItems.CENTER)
            .gapAll(8));
        pageNavigation.addChildren(previousPageButton, pageLabel, nextPageButton);

        UIElement slotMatrix = column(0x00000000, slotMatrixHeight);
        slotMatrix.layout(layout -> layout
            .width(slotMatrixWidth)
            .height(slotMatrixHeight)
            .gapAll(BANK_SLOT_GAP));
        for (int rowIndex = 0; rowIndex < SLOT_ROWS; rowIndex++) {
            UIElement slotRow = row(0x00000000, BANK_SLOT_SIZE);
            slotRow.layout(layout -> layout
                .width(slotMatrixWidth)
                .height(BANK_SLOT_SIZE)
                .gapAll(BANK_SLOT_GAP));
            for (int columnIndex = 0; columnIndex < SLOT_COLUMNS; columnIndex++) {
                int slotIndex = rowIndex * SLOT_COLUMNS + columnIndex;
                BankSlotElement slot = new BankSlotElement(slotIndex);
                slot.layout(layout -> layout.width(BANK_SLOT_SIZE).height(BANK_SLOT_SIZE));
                slotRow.addChild(slot);
                slots.add(slot);
            }
            slotMatrix.addChild(slotRow);
        }
        Label bankedTitle = label("BANKED FUNDS", 20, TEXT, 11f);
        bankedTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        WalletStripElement bankedWallet = new WalletStripElement();
        bankedWallet.layout(layout -> layout.widthPercent(100).height(44));
        UIElement grid = new BorderedPanelElement(PANEL, BORDER);
        grid.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
            .flexGrow(1).flexBasis(0).height(324).paddingAll(8)
            .alignItems(AlignItems.CENTER).gapAll(5));
        grid.addChildren(bankedTitle, bankedWallet, pageNavigation, slotMatrix);

        UIElement workspace = row(0x00000000, 324);
        workspace.layout(layout -> layout.widthPercent(100).height(324).gapAll(8));
        workspace.addChildren(inventoryPanel, transferPanel, grid);

        BankGlyphElement bankGlyph = new BankGlyphElement();
        bankGlyph.layout(layout -> layout.width(52).height(52));
        Label upgradeTitle = label("UPGRADE BANK CAPACITY", 16, TEXT, 10f);
        upgradeTitle.layout(layout -> layout.widthPercent(100).height(16));
        upgradeTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label upgradeTarget = label("", 19, GREEN, 10f);
        upgradeTarget.layout(layout -> layout.widthPercent(100).height(19));
        upgradeTarget.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label instructions = label("BANK GRADE IS INDEPENDENT OF PLAYER GRADE", 15, MUTED, 6.8f);
        instructions.layout(layout -> layout.widthPercent(100).height(15));
        instructions.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        UIElement upgradeCopy = column(0x00000000, 64);
        upgradeCopy.layout(layout -> layout.width(330).height(58)
            .alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER).gapAll(2));
        upgradeCopy.addChildren(upgradeTitle, upgradeTarget, instructions);

        Label upgradeCost = label("", 24, TEXT, 9.5f);
        upgradeCost.layout(layout -> layout.flexGrow(1).flexBasis(0).height(24));
        upgradeCost.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Button upgradeButton = new BorderedButton(GREEN).setText("UPGRADE");
        upgradeButton.layout(layout -> layout.width(210).height(48));
        upgradeButton.textStyle(style -> style.textColor(TEXT).fontSize(13f));
        applyUpgradeButtonStyle(upgradeButton, true);

        Button exitButton = new BorderedButton(BORDER).setText("<  EXIT");
        exitButton.layout(layout -> layout.width(104).height(40));
        exitButton.textStyle(style -> style.textColor(CYAN).fontSize(9f));
        applyQuietButtonStyle(exitButton);
        UIElement footer = new BorderedPanelElement(PANEL, BORDER);
        footer.layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .widthPercent(100)
            .height(74)
            .paddingHorizontal(12)
            .alignItems(AlignItems.CENTER)
            .gapAll(10));
        footer.addChildren(exitButton, bankGlyph, upgradeCopy, upgradeCost, upgradeButton);

        Label status = label("", 16, MUTED, 7.4f);
        status.layout(layout -> layout.widthPercent(100).height(16));
        status.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        root.addChildren(header, workspace, footer, status);

        Label modalTitle = label("UPGRADE BANK HOLDINGS", 24, TEXT, 13f);
        modalTitle.layout(layout -> layout.widthPercent(100).height(24));
        modalTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label confirmationCurrent = label("", 52, TEXT, 10f);
        confirmationCurrent.layout(layout -> layout.widthPercent(47).height(52));
        confirmationCurrent.textStyle(style -> style.textWrap(TextWrap.WRAP));
        Label confirmationResult = label("", 52, GREEN, 10f);
        confirmationResult.layout(layout -> layout.widthPercent(47).height(52));
        confirmationResult.textStyle(style -> style.textWrap(TextWrap.WRAP));
        UIElement comparison = row(0x00000000, 52);
        comparison.layout(layout -> layout.widthPercent(100).height(52).gapAll(12));
        comparison.addChildren(confirmationCurrent, confirmationResult);
        Label confirmationFeeWarning = label("", 20, ORANGE, 9.5f);
        confirmationFeeWarning.layout(layout -> layout.widthPercent(100).height(20));
        confirmationFeeWarning.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label confirmationCost = label("", 22, GOLD, 10f);
        confirmationCost.layout(layout -> layout.widthPercent(100).height(22));
        confirmationCost.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Button confirmationBack = new BorderedButton(BORDER).setText("BACK");
        confirmationBack.layout(layout -> layout.flexGrow(1).flexBasis(0).height(36));
        confirmationBack.textStyle(style -> style.textColor(TEXT).fontSize(10f));
        applyQuietButtonStyle(confirmationBack);
        Button confirmationConfirm = new BorderedButton(GREEN).setText("UPGRADE");
        confirmationConfirm.layout(layout -> layout.flexGrow(1).flexBasis(0).height(36));
        confirmationConfirm.textStyle(style -> style.textColor(TEXT).fontSize(10f));
        applyUpgradeButtonStyle(confirmationConfirm, true);
        UIElement modalButtons = row(0x00000000, 36);
        modalButtons.layout(layout -> layout.widthPercent(100).height(36).gapAll(12));
        modalButtons.addChildren(confirmationBack, confirmationConfirm);

        UIElement modalCard = new BorderedPanelElement(PANEL_RAISED, CYAN);
        modalCard.layout(layout -> layout
            .flexDirection(FlexDirection.COLUMN)
            .positionType(TaffyPosition.ABSOLUTE)
            .left(UPGRADE_MODAL_LEFT)
            .top(UPGRADE_MODAL_TOP)
            .width(UPGRADE_MODAL_WIDTH)
            .height(UPGRADE_MODAL_HEIGHT)
            .paddingAll(20)
            .gapAll(8));
        modalCard.addChildren(
            modalTitle, comparison, confirmationFeeWarning, confirmationCost, modalButtons
        );

        UIElement confirmationOverlay = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style
                .backgroundTexture(new ColorRectTexture(0xd9000006))
                .zIndex(30));
        confirmationOverlay.addChild(modalCard);
        confirmationOverlay.setDisplay(false);
        root.addChild(confirmationOverlay);

        Label depositTitle = label("DEPOSIT CURRENCY", 24, TEXT, 13f);
        depositTitle.layout(layout -> layout.widthPercent(100).height(24));
        depositTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label depositSubtitle = label(
            "CONFIRM THE SERVER QUOTE BEFORE MOVING PHYSICAL COINS",
            18,
            MUTED,
            8f
        );
        depositSubtitle.layout(layout -> layout.widthPercent(100).height(18));
        depositSubtitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Label depositSelection = label("", 17, TEXT, 8.8f);
        depositSelection.layout(layout -> layout.widthPercent(100).height(17));
        depositSelection.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label depositFee = label("", 17, ORANGE, 8.8f);
        depositFee.layout(layout -> layout.widthPercent(100).height(17));
        depositFee.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label depositNet = label("", 17, GREEN, 9.2f);
        depositNet.layout(layout -> layout.widthPercent(100).height(17));
        depositNet.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label depositRemaining = label("", 17, MUTED, 8.2f);
        depositRemaining.layout(layout -> layout.widthPercent(100).height(17));
        depositRemaining.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        UIElement depositSummary = new BorderedPanelElement(PANEL, BORDER);
        depositSummary.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
            .widthPercent(100).height(80).paddingAll(5)
            .alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER).gapAll(2));
        depositSummary.addChildren(
            depositSelection, depositFee, depositNet, depositRemaining
        );

        Label depositStatus = label("", 18, MUTED, 8f);
        depositStatus.layout(layout -> layout.widthPercent(100).height(18));
        depositStatus.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Button depositBack = new BorderedButton(BORDER).setText("BACK");
        depositBack.layout(layout -> layout.flexGrow(1).flexBasis(0).height(36));
        depositBack.textStyle(style -> style.textColor(TEXT).fontSize(10f));
        applyQuietButtonStyle(depositBack);
        Button depositConfirm = new BorderedButton(GREEN).setText("DEPOSIT");
        depositConfirm.layout(layout -> layout.flexGrow(1).flexBasis(0).height(36));
        depositConfirm.textStyle(style -> style.textColor(TEXT).fontSize(10f));
        applyUpgradeButtonStyle(depositConfirm, true);
        UIElement depositButtons = row(0x00000000, 36);
        depositButtons.layout(layout -> layout.widthPercent(100).height(36).gapAll(12));
        depositButtons.addChildren(depositBack, depositConfirm);

        UIElement depositCard = new BorderedPanelElement(PANEL_RAISED, CYAN);
        depositCard.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
            .positionType(TaffyPosition.ABSOLUTE)
            .left(DEPOSIT_MODAL_LEFT)
            .top(DEPOSIT_MODAL_TOP)
            .width(DEPOSIT_MODAL_WIDTH)
            .height(DEPOSIT_MODAL_HEIGHT)
            .paddingAll(20)
            .gapAll(8));
        depositCard.addChildren(
            depositTitle,
            depositSubtitle,
            depositSummary,
            depositStatus,
            depositButtons
        );
        UIElement depositOverlay = new UIElement()
            .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).width(LOGICAL_WIDTH).height(LOGICAL_HEIGHT))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0xd9000006)).zIndex(31));
        depositOverlay.addChild(depositCard);
        depositOverlay.setDisplay(false);
        root.addChild(depositOverlay);

        ModularUI modularUI = ModularUI.of(UI.of(root, screenSize -> Size.of(
            Math.max(320, screenSize.width - 24),
            Math.max(300, screenSize.height - 24)
        )));
        return new UiParts(
            modularUI,
            List.copyOf(slots),
            wallet,
            bankedWallet,
            capacity,
            transferQuote,
            bankSummary,
            instructions,
            status,
            upgradeTarget,
            upgradeCost,
            exitButton,
            depositButton,
            previousPageButton,
            pageLabel,
            nextPageButton,
            upgradeButton,
            confirmationOverlay,
            confirmationCurrent,
            confirmationResult,
            confirmationFeeWarning,
            confirmationCost,
            confirmationBack,
            confirmationConfirm,
            List.copyOf(depositQuotes),
            depositOverlay,
            depositSelection,
            depositFee,
            depositNet,
            depositRemaining,
            depositStatus,
            depositBack,
            depositConfirm
        );
    }

    void applyAuthoritativeView(DungeonBankView updatedView) {
        closeUpgradeConfirmation();
        closeDepositPopup();
        this.view = Objects.requireNonNull(updatedView, "Dungeon Bank view");
        refreshFromView();
    }

    private void refreshFromView() {
        walletElement.setWallet(view.inventoryWallet());
        bankedWalletElement.setWallet(bankedFunds(view.slots()));
        capacityElement.setCapacity(view.usedSlots(), view.unlockedSlots());
        bankSummaryLabel.setText(view.grade().displayLabel());
        if (selectedDepositQuote == null
                || view.depositQuotes().stream().noneMatch(quote ->
                    quote.quoteId().equals(selectedDepositQuote.quoteId()))) {
            selectedDepositQuote = view.depositQuotes().stream()
                .filter(DungeonBankView.DepositQuote::depositable)
                .findFirst()
                .orElse(view.depositQuotes().isEmpty() ? null : view.depositQuotes().getFirst());
        }
        boolean hasDepositQuotes = !view.depositQuotes().isEmpty();
        boolean canDeposit = selectedDepositQuote != null && selectedDepositQuote.depositable();
        depositButton.setText(hasDepositQuotes ? "DEPOSIT SELECTED" : "NO COINS ON HAND");
        depositButton.setActive(canDeposit);
        applyDepositButtonStyle(depositButton, canDeposit);
        transferQuoteElement.setQuote(selectedDepositQuote, view.depositFeeBasisPoints());
        for (DepositQuoteElement element : depositQuoteElements) {
            DungeonBankView.DepositQuote quote = view.depositQuoteFor(element.currency());
            element.setQuote(
                quote,
                quote != null && selectedDepositQuote != null
                    && quote.quoteId().equals(selectedDepositQuote.quoteId())
            );
        }
        statusLabel.setText(view.statusMessage().isBlank()
            ? "BANKED FUNDS AVAILABLE IN SHOP" : view.statusMessage());

        int pageCount = view.pageCount();
        currentPage = Math.max(0, Math.min(currentPage, pageCount - 1));
        int firstSlot = currentPage * DungeonBankView.SLOTS_PER_PAGE;
        int lastSlot = Math.min(firstSlot + DungeonBankView.SLOTS_PER_PAGE, view.maximumSlots());
        pageLabel.setText(
            "BANK PAGE " + (currentPage + 1) + " / " + pageCount
                + "   |   SLOTS " + (firstSlot + 1) + "-" + lastSlot
        );
        applyPageButtonState(previousPageButton, currentPage > 0);
        applyPageButtonState(nextPageButton, currentPage + 1 < pageCount);

        for (int localIndex = 0; localIndex < slotElements.size(); localIndex++) {
            int absoluteIndex = firstSlot + localIndex;
            boolean exists = absoluteIndex < view.maximumSlots();
            slotElements.get(localIndex).setState(
                absoluteIndex,
                exists && absoluteIndex < view.unlockedSlots(),
                exists ? view.slotAt(absoluteIndex) : null
            );
        }

        DungeonBankView.Upgrade upgrade = view.upgrade();
        if (!upgrade.offered()) {
            upgradeTargetLabel.setText("MAXIMUM BANK GRADE");
            upgradeCostLabel.setText("");
            upgradeButton.setText("MAXIMUM GRADE");
            upgradeButton.setActive(false);
            applyUpgradeButtonStyle(upgradeButton, false);
            return;
        }
        int addedSlots = Math.max(0, upgrade.resultSlots() - view.unlockedSlots());
        upgradeTargetLabel.setText(
            upgrade.targetGrade().displayLabel() + "   +" + addedSlots + " SLOTS"
        );
        upgradeCostLabel.setText("COST:  " + walletCostText(upgrade.cost()));
        upgradeButton.setText(upgrade.affordable() ? "UPGRADE" : "INSUFFICIENT FUNDS");
        upgradeButton.setActive(upgrade.affordable());
        applyUpgradeButtonStyle(upgradeButton, upgrade.affordable());
    }

    private void openUpgradeConfirmation() {
        DungeonBankView.Upgrade upgrade = view.upgrade();
        if (!upgrade.offered() || !upgrade.affordable()) return;
        int addedSlots = Math.max(0, upgrade.resultSlots() - view.unlockedSlots());
        confirmationCurrent.setText(
            "CURRENT\n" + view.grade().displayLabel() + "\n" + view.unlockedSlots() + " SLOTS"
        );
        confirmationResult.setText(
            "RESULT\n" + upgrade.targetGrade().displayLabel()
                + "\n+" + addedSlots + " SLOTS  /  " + upgrade.resultSlots() + " TOTAL"
        );
        confirmationFeeWarning.setText(
            "DRAWBACK: DEPOSIT FEE " + depositFeePercentText(view.depositFeeBasisPoints())
                + "  ->  " + depositFeePercentText(upgrade.resultDepositFeeBasisPoints())
        );
        confirmationCost.setText("COST:  " + walletCostText(upgrade.cost()));
        confirmationConfirm.setText("UPGRADE");
        confirmationConfirm.setActive(true);
        applyUpgradeButtonStyle(confirmationConfirm, true);
        confirmationOverlay.setDisplay(true);
        confirmationOpen = true;
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void closeUpgradeConfirmation() {
        confirmationOverlay.setDisplay(false);
        confirmationOpen = false;
    }

    private void openDepositPopup() {
        if (selectedDepositQuote == null || !selectedDepositQuote.depositable()) return;
        closeUpgradeConfirmation();
        depositOverlay.setDisplay(true);
        depositOpen = true;
        refreshDepositPopup();
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void selectDepositQuote(DungeonBankView.DepositQuote quote) {
        selectedDepositQuote = Objects.requireNonNull(quote, "Dungeon Bank deposit quote");
        refreshFromView();
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void refreshDepositPopup() {
        for (DepositQuoteElement element : depositQuoteElements) {
            DungeonBankView.DepositQuote quote = view.depositQuoteFor(element.currency());
            element.setQuote(
                quote,
                quote != null && selectedDepositQuote != null
                    && quote.quoteId().equals(selectedDepositQuote.quoteId())
            );
        }
        DungeonBankView.DepositQuote quote = selectedDepositQuote;
        if (quote == null) {
            depositSelectionLabel.setText("SELECT A CURRENCY STACK");
            depositFeeLabel.setText("");
            depositNetLabel.setText("");
            depositRemainingLabel.setText("");
            depositStatusLabel.setText("NO SERVER DEPOSIT QUOTE AVAILABLE");
            depositConfirm.setText("UNAVAILABLE");
            depositConfirm.setActive(false);
            applyUpgradeButtonStyle(depositConfirm, false);
            return;
        }

        String currency = quote.currency().label();
        depositSelectionLabel.setText(
            "INVENTORY STACK:  " + formatCount(quote.depositAmount()) + " " + currency
        );
        depositFeeLabel.setText(
            "DEPOSIT FEE " + depositFeePercentText(view.depositFeeBasisPoints()) + ":  -"
                + DungeonBankView.walletText(quote.fee())
        );
        depositNetLabel.setText(
            "BANK RECEIVES:  " + DungeonBankView.walletText(quote.bankReceives())
        );
        depositRemainingLabel.setText(
            "WALLET REMAINS:  " + formatCount(quote.walletRemaining()) + " " + currency
        );
        depositStatusLabel.setText(
            quote.depositable()
                ? quote.requiredSlots() + " BANK SLOT"
                    + (quote.requiredSlots() == 1 ? "" : "S") + " REQUIRED  /  SERVER QUOTE"
                : quote.failureReason()
        );
        depositConfirm.setText(quote.depositable() ? "DEPOSIT" : "UNAVAILABLE");
        depositConfirm.setActive(quote.depositable());
        applyUpgradeButtonStyle(depositConfirm, quote.depositable());
    }

    private void closeDepositPopup() {
        depositOverlay.setDisplay(false);
        depositOpen = false;
    }

    private void confirmDeposit() {
        DungeonBankView.DepositQuote quote = selectedDepositQuote;
        if (quote == null || !quote.depositable()) return;
        boolean emitted = DungeonBankUiApi.emitDeposit(view, quote);
        closeDepositPopup();
        if (!emitted && view.preview()) {
            statusLabel.setText("UI PREVIEW - NO BANK DEPOSIT WAS SENT");
        }
    }

    private void changePage(int direction) {
        int targetPage = currentPage + direction;
        if (targetPage < 0 || targetPage >= view.pageCount()) return;
        currentPage = targetPage;
        refreshFromView();
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void confirmUpgrade() {
        boolean emitted = DungeonBankUiApi.emitUpgrade(view);
        closeUpgradeConfirmation();
        if (!emitted && view.preview()) {
            statusLabel.setText("UI PREVIEW - NO BANK UPGRADE WAS SENT");
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (depositOpen) {
                closeDepositPopup();
                DungeonClientSoundEffects.playMenuClose();
                return true;
            }
            if (confirmationOpen) {
                closeUpgradeConfirmation();
                DungeonClientSoundEffects.playMenuClose();
                return true;
            }
            onClose();
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) renderBlurredBackground(partialTick);
        graphics.fillGradient(0, 0, width, height, 0x99020b1c, 0xd0020b1c);
    }

    private static void addHoverSound(UIElement element) {
        element.addEventListener(
            UIEvents.MOUSE_ENTER,
            event -> DungeonClientSoundEffects.playMenuHover()
        );
    }

    private static void applyQuietButtonStyle(Button button) {
        button.buttonStyle(style -> style
            .baseTexture(new ColorRectTexture(0xff06172f))
            .hoverTexture(new ColorRectTexture(0xff0a2944))
            .pressedTexture(new ColorRectTexture(0xff041020)));
        if (button instanceof BorderedButton borderedButton) {
            borderedButton.setBorderColor(BORDER);
        }
    }

    private static void applyPageButtonState(Button button, boolean active) {
        button.setActive(active);
        applyQuietButtonStyle(button);
        button.textStyle(style -> style.textColor(active ? CYAN : DIM).fontSize(9f));
        if (button instanceof BorderedButton borderedButton) {
            borderedButton.setBorderColor(active ? CYAN : LOCKED_BORDER);
        }
    }

    private static void applyDepositButtonStyle(Button button, boolean active) {
        button.buttonStyle(style -> style
            .baseTexture(new ColorRectTexture(active ? 0xff06203a : 0xff202b37))
            .hoverTexture(new ColorRectTexture(active ? 0xff0a3150 : 0xff202b37))
            .pressedTexture(new ColorRectTexture(active ? 0xff041628 : 0xff202b37)));
        button.textStyle(style -> style.textColor(active ? CYAN : DIM).fontSize(8f));
        if (button instanceof BorderedButton borderedButton) {
            borderedButton.setBorderColor(active ? CYAN : LOCKED_BORDER);
        }
    }

    private static void applyUpgradeButtonStyle(Button button, boolean active) {
        button.buttonStyle(style -> style
            .baseTexture(new ColorRectTexture(active ? 0xff247a39 : 0xff202b37))
            .hoverTexture(new ColorRectTexture(active ? 0xff319b4b : 0xff202b37))
            .pressedTexture(new ColorRectTexture(active ? 0xff175c2a : 0xff202b37)));
        button.textStyle(style -> style.textColor(active ? TEXT : DIM));
        if (button instanceof BorderedButton borderedButton) {
            borderedButton.setBorderColor(active ? GREEN : LOCKED_BORDER);
        }
    }

    private static void fillPixelPanel(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int surface,
        int border
    ) {
        if (right - left < 3 || bottom - top < 3) return;
        context.graphics.fill(left, top, right, bottom, border);
        context.graphics.fill(left + 1, top + 1, right - 1, bottom - 1, surface);
    }

    private static void drawPixelOutline(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int color
    ) {
        if (right - left < 2 || bottom - top < 2) return;
        context.graphics.fill(left, top, right, top + 1, color);
        context.graphics.fill(left, bottom - 1, right, bottom, color);
        context.graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        context.graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    private static void drawLock(GUIContext context, int x, int y, int color) {
        context.graphics.fill(x + 4, y + 1, x + 12, y + 3, color);
        context.graphics.fill(x + 2, y + 3, x + 5, y + 8, color);
        context.graphics.fill(x + 11, y + 3, x + 14, y + 8, color);
        context.graphics.fill(x + 2, y + 7, x + 14, y + 15, color);
        context.graphics.fill(x + 7, y + 10, x + 9, y + 14, LOCKED_SURFACE);
    }

    private static void drawCurrencyItem(
        GUIContext context,
        DungeonBankView.Currency currency,
        int x,
        int y,
        int targetSize
    ) {
        ResourceLocation itemId = currencyItemId(currency);
        boolean rendered = BuiltInRegistries.ITEM.getOptional(itemId).map(item -> {
            PoseStack pose = context.graphics.pose();
            pose.pushPose();
            pose.translate(
                x + targetSize * 0.5f,
                y + targetSize * 0.5f,
                BANK_ITEM_Z_OFFSET
            );
            pose.scale(targetSize / 16f, targetSize / 16f, 1f);
            pose.translate(-8f, -8f, 0f);
            context.graphics.renderItem(new ItemStack(item), 0, 0);
            pose.popPose();
            return true;
        }).orElse(false);
        if (!rendered) {
            int inset = Math.max(2, targetSize / 4);
            context.graphics.fill(x + inset, y + inset, x + targetSize - inset,
                y + targetSize - inset, currency.color());
        }
    }

    private static ResourceLocation currencyItemId(DungeonBankView.Currency currency) {
        ResourceLocation primary = ResourceLocation.parse(currency.itemId());
        if (currency != DungeonBankView.Currency.STELLAR_GOLD
                || BuiltInRegistries.ITEM.containsKey(primary)) {
            return primary;
        }
        return ResourceLocation.parse("tensura:stellar_coin");
    }

    private static void drawScaledText(
        GUIContext context,
        String text,
        int x,
        int y,
        int color,
        float scale
    ) {
        PoseStack pose = context.graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(scale, scale, 1f);
        context.graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static String walletCostText(DungeonShopView.Wallet wallet) {
        List<String> parts = new ArrayList<>();
        if (wallet.stellarGold() > 0L) parts.add(formatCount(wallet.stellarGold()) + " STELLAR");
        if (wallet.gold() > 0L) parts.add(formatCount(wallet.gold()) + " GOLD");
        if (wallet.silver() > 0L) parts.add(formatCount(wallet.silver()) + " SILVER");
        if (wallet.bronze() > 0L) parts.add(formatCount(wallet.bronze()) + " BRONZE");
        return parts.isEmpty() ? "FREE" : String.join("  +  ", parts);
    }

    private static DungeonShopView.Wallet bankedFunds(List<DungeonBankView.Slot> slots) {
        long bronze = 0L;
        long silver = 0L;
        long gold = 0L;
        long stellar = 0L;
        for (DungeonBankView.Slot slot : slots) {
            switch (slot.currency()) {
                case BRONZE -> bronze += slot.amount();
                case SILVER -> silver += slot.amount();
                case GOLD -> gold += slot.amount();
                case STELLAR_GOLD -> stellar += slot.amount();
            }
        }
        return new DungeonShopView.Wallet(bronze, silver, gold, stellar);
    }

    private static String depositFeeText(int basisPoints) {
        int clamped = Math.max(0, Math.min(10_000, basisPoints));
        if (clamped == 0) return "NO DEPOSIT FEE";
        int whole = clamped / 100;
        int fraction = clamped % 100;
        return fraction == 0
            ? "DEPOSIT FEE " + whole + "%"
            : "DEPOSIT FEE " + whole + "."
                + String.format(Locale.ROOT, "%02d", fraction) + "%";
    }

    private static String depositFeePercentText(int basisPoints) {
        int clamped = Math.max(0, Math.min(10_000, basisPoints));
        int whole = clamped / 100;
        int fraction = clamped % 100;
        return fraction == 0
            ? whole + "%"
            : whole + "." + String.format(Locale.ROOT, "%02d", fraction) + "%";
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static UIElement row(int color, float height) {
        return new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).height(height))
            .style(style -> style.backgroundTexture(new ColorRectTexture(color)));
    }

    private static UIElement column(int color, float height) {
        return new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.COLUMN).height(height))
            .style(style -> style.backgroundTexture(new ColorRectTexture(color)));
    }

    private static Label label(String text, float height, int color, float fontSize) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> layout.height(height));
        label.textStyle(style -> style.textColor(color).fontSize(fontSize).textWrap(TextWrap.HIDE));
        label.setAllowHitTest(false);
        return label;
    }
}
