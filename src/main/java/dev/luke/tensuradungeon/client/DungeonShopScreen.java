package dev.luke.tensuradungeon.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mineclawd.tensuradungeon.client.DungeonHudSuppressingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DungeonShopScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int W = DungeonShopLayout.VIRTUAL_WIDTH;
    private static final int H = DungeonShopLayout.VIRTUAL_HEIGHT;

    private static final int BG = 0xFF01050C;
    private static final int LINE = 0xFF173B63;
    private static final int LINE_FAINT = 0x80173B63;
    private static final int CYAN = 0xFF31E4EA;
    private static final int GOLD = 0xFFFFB730;
    private static final int VIOLET = 0xFFB267E8;
    private static final int GREEN = 0xFF2DE39D;
    private static final int RED = 0xFFFF5264;
    private static final int WHITE = 0xFFF3F5FF;
    private static final int TEXT = 0xFFD9DEEE;
    private static final int MUTED = 0xFF929EB6;
    private static final int DIM = 0xFF64718A;
    private static final int GRADE_I_COLOR = 0xFF6BD7FF;
    private static final int MATERIAL_NAME_COLOR = 0xFFA9D8FF;
    private static final int COIN_TEXTURE_SIZE = 48;
    private static final int COIN_CROP_X = 9;
    private static final int COIN_CROP_Y = 12;
    private static final int COIN_CROP_WIDTH = 30;
    private static final int COIN_CROP_HEIGHT = 24;

    private static final String[] GRADE_FILTERS = {
        "ALL", "Normal", "Special", "Rare", "Unique", "Legend", "Mythic"
    };

    private DungeonShopView view;
    private final Screen parent;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<String, ModelBounds> weaponModelBounds = new HashMap<>();

    private DungeonShopView.Tab tab;
    private String selectedOfferId;
    private String search = "";
    private String gradeFilter = "ALL";
    private boolean searchFocused;
    private int essentialScroll;
    private int timedScroll;
    private DungeonShopView.Offer confirmation;
    private boolean rotatingWeaponPreview;
    private double lastRotationMouseX;
    private boolean weaponPreviewInspecting;
    private String inspectionWeaponId = "";
    private float weaponPreviewRotationY;
    private String toast = "";
    private long toastUntil;

    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;

    public DungeonShopScreen(DungeonShopView view, Screen parent) {
        super(Component.literal("Dungeon Shop"));
        this.view = view;
        this.parent = parent;
        this.tab = view.initialTab();
        DungeonShopView.Offer first = DungeonShopLayout.firstVisible(
            view, tab, "", "ALL", "ALL", "ALL"
        );
        selectedOfferId = first == null ? "" : first.id();
    }

    void applyAuthoritativeView(DungeonShopView updatedView) {
        view = java.util.Objects.requireNonNull(updatedView);
        tab = view.initialTab();
        confirmation = null;
        searchFocused = false;
        essentialScroll = 0;
        timedScroll = 0;
        toast = "";
        toastUntil = 0L;
        DungeonShopView.Offer first = DungeonShopLayout.firstVisible(
            view, tab, search, gradeFilter, "ALL", "ALL"
        );
        selectedOfferId = first == null ? "" : first.id();
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateTransform();
        graphics.fill(0, 0, width, height, BG);
        drawOutsideBackdrop(graphics);

        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        hotspots.clear();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(uiX, uiY, 0.0F);
        pose.scale(uiScale, uiScale, 1.0F);

        drawBackdrop(graphics);
        drawHeader(graphics);
        drawTabs(graphics);
        drawUtilityRow(graphics);
        drawShopBody(graphics);

        if (!toast.isBlank() && System.currentTimeMillis() < toastUntil) {
            drawToast(graphics);
        }
        if (confirmation != null) {
            drawConfirmation(graphics, confirmation);
        }

        DungeonShopView.Offer selected = selectedOffer();
        boolean abilityHover = confirmation == null && isAbilityHover(selected, vx, vy);
        boolean weaponHover = confirmation == null && !abilityHover
            && !rotatingWeaponPreview && isWeaponPreviewHover(selected, vx, vy);
        Hotspot hovered = hovered(vx, vy);
        if (abilityHover) {
            drawTensuraAbilityTooltip(
                graphics,
                DungeonShopArt.weaponPresentation(selected),
                vx + 7,
                vy + 7
            );
        } else if (weaponHover) {
            itemStack(selected).ifPresent(stack ->
                graphics.renderTooltip(font, stack, vx + 7, vy + 7)
            );
        } else if (hovered != null && !hovered.tooltip().isBlank() && confirmation == null) {
            drawTooltip(graphics, hovered.tooltip(), vx + 7, vy + 7);
        }
        pose.popPose();
    }

    private void updateTransform() {
        uiScale = Math.min(width / (float) W, height / (float) H);
        uiX = (width - W * uiScale) * 0.5F;
        uiY = (height - H * uiScale) * 0.5F;
    }

    private int virtualX(double mouseX) {
        return (int) Math.floor((mouseX - uiX) / uiScale);
    }

    private int virtualY(double mouseY) {
        return (int) Math.floor((mouseY - uiY) / uiScale);
    }

    private void drawOutsideBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xFF020714, 0xFF000208);
        int center = width / 2;
        graphics.fill(Math.max(0, center - width / 3), 0,
            Math.min(width, center + width / 3), height, 0x141A6677);
    }

    private void drawBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, W, H, BG);
        for (int x = 0; x < W; x += 64) {
            graphics.vLine(x, 0, H, 0x0C1B4E7A);
        }
        for (int y = 0; y < H; y += 45) {
            graphics.hLine(0, W, y, 0x091B4E7A);
        }
        graphics.fillGradient(0, 0, W, 63, 0xFC020814, 0xF807111E);
        graphics.fillGradient(0, 63, W, H, 0x00000000, 0x85000000);
    }

    private void drawHeader(GuiGraphics graphics) {
        cutPanel(graphics, 18, 10, W - 36, 50, 0xF8050B14, 0xFF6E5320, 8);
        drawFit(graphics, "CURRENCIES", 30, 18, 220, TEXT, 0.7F);
        drawCurrencyStrip(graphics, 30, 35, 232);

        drawCenteredFit(graphics, "DUNGEON SHOP", W / 2, 23, 430, GOLD, 1.68F);

        drawHourglass(graphics, W - 190, 25);
        drawFit(graphics, "RESETS IN", W - 164, 21, 130, TEXT, 0.78F);
        drawFit(graphics, view.resetLabel(), W - 164, 37, 130, CYAN, 0.9F);

        drawButton(graphics, W - 43, 22, 20, 20, "X", false, false, WHITE);
        addHotspot(W - 43, 22, 20, 20, "Close", this::onClose);
    }

    private void drawTabs(GuiGraphics graphics) {
        int y = 68;
        int tabW = 176;
        int gap = 34;
        int groupWidth = tabW * 3 + gap * 2;
        int groupX = (W - groupWidth) / 2;
        graphics.hLine(28, W - 28, y + 27, 0x4D173B63);
        DungeonShopView.Tab[] tabs = DungeonShopView.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            DungeonShopView.Tab candidate = tabs[i];
            int x = groupX + i * (tabW + gap);
            boolean active = candidate == tab;
            if (active) {
                graphics.fill(x + 28, y + 25, x + tabW - 28, y + 28, CYAN);
                graphics.fill(x + 51, y + 28, x + tabW - 51, y + 29, withAlpha(CYAN, 100));
            }
            drawCenteredFit(graphics, candidate.label(), x + tabW / 2, y + 7,
                tabW - 24, active ? CYAN : 0xFF8C819D, active ? 1.12F : 0.98F);
            addHotspot(x, y, tabW, 29, candidate.label() + " offers", () -> selectTab(candidate));
        }
    }

    private void drawUtilityRow(GuiGraphics graphics) {
        int y = 101;
        drawTextField(graphics, 28, y, 388, 20);
        addHotspot(28, y, 388, 20, "Search the active tab", () -> searchFocused = true);

        drawFilter(graphics, 430, y, 200, "GRADE", gradeFilter,
            () -> cycleGrade(1));
    }

    private void drawTextField(GuiGraphics graphics, int x, int y, int w, int h) {
        panel(graphics, x, y, w, h, 0xFF030914, searchFocused ? CYAN : LINE);
        draw(graphics, "⌕", x + 7, y + 5, searchFocused ? CYAN : MUTED);
        String value = search.isBlank() ? "Search current stock..." : search;
        drawFit(graphics, value, x + 22, y + 5, w - 30,
            search.isBlank() ? DIM : TEXT, 0.73F);
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cursorX = Math.min(x + w - 7, x + 22 + font.width(search));
            graphics.fill(cursorX, y + 4, cursorX + 1, y + h - 4, CYAN);
        }
    }

    private void drawFilter(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        String label,
        String value,
        Runnable action
    ) {
        panel(graphics, x, y, w, 19, 0xFF030914, LINE);
        drawFit(graphics, label, x + 7, y + 5, 56, DIM, 0.62F);
        drawRightFit(graphics, value + "  ▾", x + w - 7, y + 5, w - 63,
            value.equals("ALL") ? MUTED : CYAN, 0.7F);
        addHotspot(x, y, w, 19, "Cycle " + label.toLowerCase(Locale.ROOT) + " filter", action);
    }

    private void drawShopBody(GuiGraphics graphics) {
        graphics.fill(
            DungeonShopLayout.LEFT_X,
            DungeonShopLayout.BODY_TOP,
            DungeonShopLayout.LEFT_X + DungeonShopLayout.LEFT_WIDTH,
            518,
            0xB8030A15
        );
        graphics.hLine(
            DungeonShopLayout.LEFT_X,
            DungeonShopLayout.LEFT_X + DungeonShopLayout.LEFT_WIDTH,
            DungeonShopLayout.BODY_TOP,
            LINE_FAINT
        );
        panel(graphics, DungeonShopLayout.PREVIEW_X, DungeonShopLayout.BODY_TOP,
            DungeonShopLayout.PREVIEW_WIDTH, 392, 0xF3060C17, 0xFF6D5629);

        switch (tab) {
            case DAILY -> drawDaily(graphics);
            case WEEKLY -> drawWeekly(graphics);
            case BARTER -> drawBarter(graphics);
        }
        drawPreview(graphics, selectedOffer());
    }

    private void drawDaily(GuiGraphics graphics) {
        List<DungeonShopView.Offer> essentials = offers(DungeonShopView.Section.ESSENTIAL);
        List<DungeonShopView.Offer> finds = offers(DungeonShopView.Section.TIMED);
        essentialScroll = DungeonShopLayout.clampScroll(
            essentialScroll, essentials.size(), DungeonShopLayout.ESSENTIAL_VISIBLE
        );

        drawSectionHeader(graphics, "ESSENTIAL SUPPLIES", 30, 139, 598, CYAN);
        drawPageArrow(graphics, 573, 135, -1, essentialScroll > 0, () ->
            essentialScroll = DungeonShopLayout.pageScroll(
                essentialScroll, -1, essentials.size(), DungeonShopLayout.ESSENTIAL_VISIBLE
            )
        );
        drawPageArrow(graphics, 603, 135, 1,
            essentialScroll + DungeonShopLayout.ESSENTIAL_VISIBLE < essentials.size(), () ->
                essentialScroll = DungeonShopLayout.pageScroll(
                    essentialScroll, 1, essentials.size(), DungeonShopLayout.ESSENTIAL_VISIBLE
                )
        );
        int rowY = 158;
        int rowH = 55;
        int gap = 3;
        int visible = Math.min(DungeonShopLayout.ESSENTIAL_VISIBLE,
            Math.max(0, essentials.size() - essentialScroll));
        for (int index = 0; index < visible; index++) {
            DungeonShopView.Offer offer = essentials.get(essentialScroll + index);
            drawEssentialRow(graphics, offer, 30, rowY + index * (rowH + gap), 598, rowH);
        }
        if (essentials.isEmpty()) {
            drawEmpty(graphics, "No Essential Supplies match this search.", 30, rowY, 598, 171);
        } else {
            int firstShown = essentialScroll + 1;
            int lastShown = Math.min(essentials.size(),
                essentialScroll + DungeonShopLayout.ESSENTIAL_VISIBLE);
            drawRightFit(graphics,
                firstShown + "-" + lastShown + " / " + essentials.size(),
                555, 143, 70, DIM, 0.55F);
        }

        drawSectionHeader(graphics, "TODAY'S FINDS", 30, 347, 598, VIOLET);
        int findY = 371;
        if (!finds.isEmpty()) {
            drawOfferRow(graphics, finds.getFirst(), 30, findY, 598, 112);
        }
        if (finds.isEmpty()) {
            drawEmpty(graphics, "No Today's Finds in this rotation.", 30, findY, 598, 112);
        }
        drawFit(graphics, "DAILY ROTATION  ·  STOCK PERSISTS UNTIL RESET",
            31, 501, 440, DIM, 0.58F);
    }

    private void drawWeekly(GuiGraphics graphics) {
        List<DungeonShopView.Offer> weekly = offers(DungeonShopView.Section.TIMED);
        timedScroll = DungeonShopLayout.clampScroll(
            timedScroll, weekly.size(), DungeonShopLayout.TIMED_VISIBLE
        );
        drawSectionHeader(graphics, "WEEKLY SPECIALS", 30, 141, 598, VIOLET);
        drawPageArrow(graphics, 573, 137, -1, timedScroll > 0, () ->
            timedScroll = DungeonShopLayout.pageScroll(
                timedScroll, -1, weekly.size(), DungeonShopLayout.TIMED_VISIBLE
            )
        );
        drawPageArrow(graphics, 603, 137, 1,
            timedScroll + DungeonShopLayout.TIMED_VISIBLE < weekly.size(), () ->
                timedScroll = DungeonShopLayout.pageScroll(
                    timedScroll, 1, weekly.size(), DungeonShopLayout.TIMED_VISIBLE
                )
        );
        int rowY = 164;
        int rowH = 75;
        int gap = 4;
        int shown = Math.min(
            DungeonShopLayout.TIMED_VISIBLE,
            Math.max(0, weekly.size() - timedScroll)
        );
        for (int index = 0; index < shown; index++) {
            drawOfferRow(graphics, weekly.get(timedScroll + index), 30,
                rowY + index * (rowH + gap), 598, rowH);
        }
        if (weekly.isEmpty()) {
            drawEmpty(graphics, "No Weekly Specials in this rotation.", 30, rowY, 598, 312);
        }
        drawFit(graphics, "WEEKLY ROTATION  ·  RESETS MONDAY AT SERVER-LOCAL MIDNIGHT",
            31, 501, 500, DIM, 0.58F);
    }

    private void drawBarter(GuiGraphics graphics) {
        List<DungeonShopView.Offer> barter = offers(DungeonShopView.Section.BARTER);
        drawSectionHeader(graphics, "CORE BARTER", 30, 141, 598, GREEN);
        int rowY = 169;
        int shown = Math.min(4, barter.size());
        for (int index = 0; index < shown; index++) {
            drawBarterRow(graphics, barter.get(index), 30, rowY + index * 76, 598, 67);
        }
        if (barter.isEmpty()) {
            drawEmpty(graphics, "No barter recipes are currently approved.", 30, rowY, 598, 290);
        }
        drawFit(graphics, "BARTER REMAINS READ-ONLY UNTIL ATOMIC RECIPES ARE APPROVED",
            31, 501, 500, DIM, 0.58F);
    }

    private void drawSectionHeader(
        GuiGraphics graphics,
        String label,
        int x,
        int y,
        int width,
        int color
    ) {
        int labelW = Math.min(220, font.width(label) + 12);
        drawFit(graphics, label, x, y, labelW, color, 0.78F);
        graphics.hLine(x + labelW + 10, x + width, y + 5, withAlpha(color, 82));
    }

    private void drawPageArrow(
        GuiGraphics graphics,
        int x,
        int y,
        int direction,
        boolean enabled,
        Runnable action
    ) {
        int color = enabled ? CYAN : DIM;
        drawCentered(graphics, direction < 0 ? "‹" : "›", x + 10, y + 3, color);
        if (enabled) {
            addHotspot(x, y, 20, 16,
                direction < 0 ? "Previous page" : "Next page", action);
        }
    }

    private void drawEssentialRow(
        GuiGraphics graphics,
        DungeonShopView.Offer offer,
        int x,
        int y,
        int w,
        int h
    ) {
        boolean selected = offer.id().equals(selectedOfferId);
        int selectionColor = offer.accent();
        graphics.fill(x, y, x + w, y + h, selected ? 0xD90A1923 : 0x46050B15);
        graphics.fill(x, y + 5, x + 3, y + h - 5,
            selected ? selectionColor : withAlpha(selectionColor, 105));
        drawOfferArt(graphics, offer, DungeonShopArt.Surface.ESSENTIAL,
            x + 8, y + 6, 45, 43);
        drawFit(graphics, offer.name().toUpperCase(Locale.ROOT), x + 66, y + 10,
            218, offerResolved(offer) ? DIM : TEXT, 0.76F);
        drawFit(graphics, offer.rarity().toUpperCase(Locale.ROOT), x + 66, y + 31,
            95, offer.accent(), 0.61F);
        drawFit(graphics, offer.unlimited() ? "STOCK  UNLIMITED" : "STOCK  x" + offer.quantity(), x + 184, y + 31,
            100, MUTED, 0.61F);
        drawFit(graphics, offer.type().toUpperCase(Locale.ROOT), x + 302, y + 20,
            105, DIM, 0.58F);
        graphics.vLine(x + w - 162, y + 9, y + h - 9, LINE_FAINT);
        drawCoin(graphics, x + w - 137, y + h / 2, 6, GOLD);
        drawCenteredFit(graphics, statePrice(offer), x + w - 75, y + 20,
            112, priceColor(offer), 0.76F);
        graphics.hLine(x + 8, x + w, y + h - 1, 0x65173B63);
        addOfferHotspot(offer, x, y, w, h);
    }

    private void drawOfferRow(
        GuiGraphics graphics,
        DungeonShopView.Offer offer,
        int x,
        int y,
        int w,
        int h
    ) {
        boolean selected = offer.id().equals(selectedOfferId);
        int selectionColor = offer.accent();
        graphics.fill(x, y, x + w, y + h, selected ? 0xDE0A1923 : 0x52050B15);
        graphics.fill(x, y + 6, x + 3, y + h - 6,
            selected ? selectionColor : withAlpha(selectionColor, 115));
        int artSize = Math.min(h - 12, 86);
        drawOfferArt(graphics, offer, DungeonShopArt.Surface.CARD,
            x + 8, y + (h - artSize) / 2, artSize, artSize);
        int textX = x + artSize + 24;
        int priceX = x + w - 156;
        drawFit(graphics, offer.name().toUpperCase(Locale.ROOT), textX, y + 13,
            priceX - textX - 18, stateTextColor(offer), 0.82F);
        drawFit(graphics,
            offer.rarity().toUpperCase(Locale.ROOT) + "  ·  "
                + offer.type().toUpperCase(Locale.ROOT),
            textX, y + 34, priceX - textX - 18, offer.accent(), 0.61F);
        drawFit(graphics, offer.detail(), textX, y + h - 22,
            priceX - textX - 18, MUTED, 0.60F);
        graphics.vLine(priceX, y + 11, y + h - 11, LINE_FAINT);
        drawCoin(graphics, priceX + 20, y + h / 2, 7, GOLD);
        drawCenteredFit(graphics, statePrice(offer), priceX + 92, y + h / 2 - 5,
            108, priceColor(offer), 0.79F);
        graphics.hLine(x + 8, x + w, y + h - 1, 0x65173B63);
        addOfferHotspot(offer, x, y, w, h);
    }

    private void drawBarterRow(
        GuiGraphics graphics,
        DungeonShopView.Offer offer,
        int x,
        int y,
        int w,
        int h
    ) {
        boolean selected = offer.id().equals(selectedOfferId);
        int selectionColor = offer.accent();
        graphics.fill(x, y, x + w, y + h, selected ? 0xDE0A1923 : 0x52050B15);
        graphics.fill(x, y + 6, x + 3, y + h - 6,
            selected ? selectionColor : withAlpha(selectionColor, 115));
        drawOfferIcon(graphics, DungeonShopView.Icon.TRADE, x + 35, y + h / 2, 20, offer.accent());
        drawFit(graphics, offer.price(), x + 69, y + 15, 120, GOLD, 0.8F);
        drawFit(graphics, "YOU GIVE", x + 69, y + 35, 120, DIM, 0.62F);
        drawCentered(graphics, "→", x + 238, y + 27, CYAN);
        drawFit(graphics, offer.name().toUpperCase(Locale.ROOT), x + 278, y + 14,
            210, stateTextColor(offer), 0.76F);
        drawFit(graphics, "YOU RECEIVE  ·  " + offer.rarity(), x + 278, y + 37,
            220, offer.accent(), 0.61F);
        drawCenteredFit(graphics, barterActionLabel(offer), x + w - 48, y + 27,
            92, offer.tab() == DungeonShopView.Tab.BARTER
                && isMissingMaterials(offer) ? RED : CYAN, 0.65F);
        graphics.hLine(x + 8, x + w, y + h - 1, 0x65173B63);
        addOfferHotspot(offer, x, y, w, h);
    }

    private void addOfferHotspot(DungeonShopView.Offer offer, int x, int y, int w, int h) {
        String tooltip = switch (offer.state()) {
            case PURCHASED -> "Purchased for this rotation";
            case OWNED -> "Permanently owned · remains in the rotation pool";
            case LOCKED -> offer.tab() == DungeonShopView.Tab.BARTER
                ? "Missing materials"
                : "Requirements not met";
            case SOLD_OUT -> "No stock remaining";
            default -> "Preview " + offer.name();
        };
        addHotspot(x, y, w, h, tooltip, () -> selectedOfferId = offer.id());
    }

    private void drawPreview(GuiGraphics graphics, DungeonShopView.Offer offer) {
        int x = DungeonShopLayout.PREVIEW_X;
        int w = DungeonShopLayout.PREVIEW_WIDTH;
        if (view.preview()) {
            drawRightFit(graphics, "◇ VISUAL REVIEW", x + w - 17, 136,
                120, withAlpha(GOLD, 175), 0.55F);
        }
        if (offer == null) {
            String emptyMessage = view.statusMessage().isBlank()
                ? "NO MATCHING OFFER"
                : view.statusMessage().toUpperCase(Locale.ROOT);
            drawCenteredFit(graphics, emptyMessage, x + w / 2, 250,
                w - 40, MUTED, 0.9F);
            String guidance = view.statusMessage().isBlank()
                ? "Clear or adjust the active filters."
                : "No offers are available in this authoritative tab.";
            drawCenteredFit(graphics, guidance, x + w / 2, 274,
                w - 50, DIM, 0.68F);
            return;
        }

        graphics.hLine(x + 18, x + w - 18, 153, withAlpha(offer.accent(), 130));
        drawCenteredFit(graphics, offer.name().toUpperCase(Locale.ROOT), x + w / 2,
            161, w - 34, stateTextColor(offer), 1.03F);
        drawCenteredFit(graphics, offer.rarity() + "  ·  " + offer.vendor().toUpperCase(Locale.ROOT),
            x + w / 2, 181, w - 38, offer.accent(), 0.65F);

        drawOfferArt(graphics, offer, DungeonShopArt.Surface.PREVIEW,
            x + 28, 202, w - 56, 102);
        if (offer.icon() == DungeonShopView.Icon.WEAPON) {
            drawRightFit(
                graphics,
                weaponPreviewInspecting ? "Y-AXIS 360" : "DRAG TO INSPECT",
                x + w - 34,
                291,
                110, DIM, 0.52F);
            addHotspot(x + 28, 202, w - 56, 102, "", () -> {});
        }
        if (offer.tab() == DungeonShopView.Tab.BARTER) {
            drawBarterDetails(graphics, offer, x, w);
        } else {
        boolean weapon = offer.icon() == DungeonShopView.Icon.WEAPON;
        DungeonShopArt.WeaponPresentation weaponPresentation =
            DungeonShopArt.weaponPresentation(offer);
        drawCenteredFit(graphics, offer.detail(), x + w / 2, weapon ? 309 : 324,
            w - 40, CYAN, weapon ? 0.66F : 0.73F);
        if (weapon) {
            String cast = weaponPresentation.hasCast() ? weaponPresentation.cast() : "None";
            drawFit(graphics, "CASTS", x + 24, 326, 56, DIM, 0.59F);
            drawFit(graphics, cast.toUpperCase(Locale.ROOT), x + 80, 325,
                w - 104, weaponPresentation.hasCast() ? CYAN : MUTED, 0.67F);
            if (weaponPresentation.hasCast()) {
                addHotspot(x + 22, 322, w - 44, 14, "", () -> {});
            }
        }
        int dividerY = 343;
        graphics.hLine(x + 24, x + w - 24, dividerY, LINE_FAINT);
        List<String> description = wrap(
            DungeonShopPreviewCopy.description(offer), w - 48, weapon ? 2 : 3
        );
        int descriptionY = 355;
        for (int line = 0; line < description.size(); line++) {
            drawFit(graphics, description.get(line), x + 24, descriptionY + line * 12,
                w - 48, TEXT, 0.7F);
        }
        if (weapon) {
            String status = String.join("  ·  ",
                offer.requirements().isBlank() ? "Combat tuning pending" : offer.requirements(),
                offer.availabilityReason().isBlank() ? "Preview only" : offer.availabilityReason()
            );
            drawCenteredFit(graphics, status.toUpperCase(Locale.ROOT), x + w / 2, 389,
                w - 48, GOLD, 0.56F);
        } else {
            String disclaimer = DungeonShopPreviewCopy.disclaimer(offer);
            if (!disclaimer.isBlank()) {
                drawCenteredFit(graphics, disclaimer, x + w / 2, 383,
                    w - 48, GOLD, 0.61F);
            }
            if (!offer.availabilityReason().isBlank()) {
                drawCenteredFit(graphics, offer.availabilityReason(), x + w / 2, 392,
                    w - 48, RED, 0.58F);
            }
        }
        }
        drawFit(graphics, "GRADE  " + offer.grade().toUpperCase(Locale.ROOT), x + 24, 400,
            120, DIM, 0.61F);
        drawRightFit(graphics, "TYPE  " + offer.type().toUpperCase(Locale.ROOT),
            x + w - 24, 400, 140, DIM, 0.61F);

        panel(graphics, x + 22, 420, w - 44, 30, 0xFF030812, 0xFF6D5629);
        if (!offerResolved(offer)) {
            drawCoin(graphics, x + 47, 435, 8, GOLD);
        }
        drawCenteredFit(graphics, statePrice(offer), x + w / 2 + 7, 428,
            w - 82, priceColor(offer), 0.93F);

        boolean disabled = !offer.actionable();
        String action = switch (offer.state()) {
            case PURCHASED -> "PURCHASED";
            case OWNED -> "OWNED";
            case LOCKED -> offer.tab() == DungeonShopView.Tab.BARTER
                ? "MISSING MATERIALS"
                : "LOCKED";
            case SOLD_OUT -> "SOLD OUT";
            default -> offer.actionLabel();
        };
        drawButton(graphics, x + 22, 461, w - 44, 38, action,
            offer.state() == DungeonShopView.State.VENDOR_ROUTED, disabled,
            offer.state() == DungeonShopView.State.VENDOR_ROUTED ? GOLD : CYAN);
        if (!disabled) {
            addHotspot(x + 22, 461, w - 44, 38, actionOfferTooltip(offer),
                () -> activateOffer(offer));
        }
    }

    private void activateOffer(DungeonShopView.Offer offer) {
        searchFocused = false;
        if (offer.state() == DungeonShopView.State.VENDOR_ROUTED) {
            if (view.preview()) {
                showToast("VISUAL PREVIEW — VENDOR ROUTE NOT SENT");
                return;
            }
            DungeonShopUiApi.emit(DungeonShopUiApi.ActionType.VISIT_VENDOR, view, offer);
            showToast("ROUTE TO " + offer.vendor().toUpperCase(Locale.ROOT));
            return;
        }
        confirmation = offer;
    }

    private void drawConfirmation(GuiGraphics graphics, DungeonShopView.Offer offer) {
        graphics.fill(0, 0, W, H, 0xD8000004);
        int x = 264;
        int y = 155;
        int w = 432;
        int h = 230;
        cutPanel(graphics, x, y, w, h, 0xFF050B16, GOLD, 8);
        drawCenteredFit(graphics,
            offer.tab() == DungeonShopView.Tab.BARTER ? "CONFIRM BARTER" : "CONFIRM PURCHASE",
            x + w / 2, y + 22, w - 40, GOLD, 1.05F);
        drawOfferIcon(graphics, offer.icon(), x + 78, y + 95, 35, offer.accent());
        drawFit(graphics, offer.name().toUpperCase(Locale.ROOT), x + 132, y + 66,
            w - 155, CYAN, 0.9F);
        drawFit(graphics, offer.description(), x + 132, y + 91,
            w - 155, TEXT, 0.67F);
        drawFit(graphics, "COST", x + 132, y + 125, 50, DIM, 0.63F);
        drawFit(graphics, offer.price(), x + 178, y + 124, w - 205, GOLD, 0.85F);
        drawCenteredFit(graphics, "This action is irreversible after the server confirms it.",
            x + w / 2, y + 153, w - 40, MUTED, 0.65F);

        drawButton(graphics, x + 26, y + 181, 180, 30, "BACK", false, false, TEXT);
        drawButton(graphics, x + 226, y + 181, 180, 30, "CONFIRM", true, false, CYAN);
        addHotspot(x + 26, y + 181, 180, 30, "Return to the offer", () -> confirmation = null);
        addHotspot(x + 226, y + 181, 180, 30, "Submit this transaction", () -> confirm(offer));
    }

    private void confirm(DungeonShopView.Offer offer) {
        confirmation = null;
        if (view.preview()) {
            showToast("VISUAL PREVIEW — NO TRANSACTION SENT");
            return;
        }
        DungeonShopUiApi.emit(
            offer.tab() == DungeonShopView.Tab.BARTER
                ? DungeonShopUiApi.ActionType.BARTER
                : DungeonShopUiApi.ActionType.PURCHASE,
            view,
            offer
        );
        showToast("AWAITING SERVER CONFIRMATION");
    }

    private void drawOfferIcon(
        GuiGraphics graphics,
        DungeonShopView.Icon icon,
        int centerX,
        int centerY,
        int radius,
        int color
    ) {
        graphics.fill(centerX - radius, centerY - radius,
            centerX + radius + 1, centerY + radius + 1, 0x33000000);
        diamond(graphics, centerX, centerY, radius, withAlpha(color, 42), withAlpha(color, 185));
        String glyph = switch (icon) {
            case BRACELET -> "◉";
            case WHISTLE -> "◁";
            case HEALING -> "♥";
            case MAGICULE -> "✦";
            case RATION -> "▰";
            case SATCHEL -> "▣";
            case CLOAK -> "♠";
            case STONE -> "◆";
            case KEY -> "⚿";
            case WEAPON -> "†";
            case ARMOR -> "⬟";
            case CHEST -> "▤";
            case THEME -> "▧";
            case TRADE -> "⇄";
        };
        float scale = Math.max(0.9F, radius / 17.0F);
        drawCenteredScaled(graphics, glyph, centerX, centerY - Math.round(4 * scale), WHITE, scale);
    }

    private void drawOfferArt(
        GuiGraphics graphics,
        DungeonShopView.Offer offer,
        DungeonShopArt.Surface surface,
        int x,
        int y,
        int width,
        int height
    ) {
        if (surface == DungeonShopArt.Surface.PREVIEW) {
            drawArtSlot(graphics, x, y, width, height, offer.accent());
        } else {
            graphics.fill(x, y, x + width, y + height, 0x22000000);
        }
        String registeredItemId = DungeonShopArt.registeredItemId(offer);
        if (!registeredItemId.isBlank() && drawRegisteredItem(
            graphics, offer, surface, registeredItemId, x, y, width, height
        )) {
            return;
        }
        DungeonShopArt.Asset asset = DungeonShopArt.resolve(offer, surface);
        if (asset == null) {
            drawCenteredFit(graphics, "IMAGE SLOT", x + width / 2, y + height / 2 - 4,
                width - 14, DIM, 0.52F);
            return;
        }

        int innerWidth = Math.max(1, width - 6);
        int innerHeight = Math.max(1, height - 6);
        float scale = Math.min(
            innerWidth / (float) asset.width(),
            innerHeight / (float) asset.height()
        );
        int drawWidth = Math.max(1, Math.round(asset.width() * scale));
        int drawHeight = Math.max(1, Math.round(asset.height() * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        graphics.blit(
            ResourceLocation.fromNamespaceAndPath("tensura_dungeon_ui", asset.path()),
            drawX,
            drawY,
            drawWidth,
            drawHeight,
            0.0F,
            0.0F,
            asset.width(),
            asset.height(),
            asset.width(),
            asset.height()
        );
    }

    private boolean drawRegisteredItem(
        GuiGraphics graphics,
        DungeonShopView.Offer offer,
        DungeonShopArt.Surface surface,
        String rawItemId,
        int x,
        int y,
        int width,
        int height
    ) {
        ResourceLocation itemId;
        try {
            itemId = ResourceLocation.parse(rawItemId);
        } catch (RuntimeException exception) {
            return false;
        }
        return BuiltInRegistries.ITEM.getOptional(itemId).map(item -> {
            ItemStack stack = new ItemStack(item);
            float previewScale = surface == DungeonShopArt.Surface.PREVIEW
                ? DungeonShopArt.weaponPresentation(offer).previewScale()
                : 1.0F;
            int containedSize = Math.max(16, Math.min(width - 10, height - 10));
            int targetSize = Math.min(
                height + 28,
                Math.max(16, Math.round(containedSize * previewScale))
            );
            if (surface == DungeonShopArt.Surface.PREVIEW) {
                prepareWeaponPreview(offer.id());
                drawRotatingPreviewItem(
                    graphics,
                    stack,
                    rawItemId,
                    x,
                    y,
                    width,
                    height,
                    targetSize
                );
                return true;
            }
            float scale = targetSize / 16.0F;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(
                x + width * 0.5F,
                y + height * 0.5F,
                100.0F
            );
            pose.scale(scale, scale, 1.0F);
            pose.translate(-8.0F, -8.0F, 0.0F);
            graphics.renderItem(stack, 0, 0);
            pose.popPose();
            return true;
        }).orElse(false);
    }

    private void drawRotatingPreviewItem(
        GuiGraphics graphics,
        ItemStack stack,
        String itemId,
        int x,
        int y,
        int width,
        int height,
        int targetSize
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer renderer = minecraft.getItemRenderer();
        BakedModel model = renderer.getModel(stack, minecraft.level, minecraft.player, 0);
        BakedModel boundsModel = model;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        boolean flatLighting = !model.usesBlockLight();
        try {
            pose.translate(x + width * 0.5F, y + height * 0.5F, 150.0F);
            pose.scale(targetSize, -targetSize, targetSize);
            if (weaponPreviewInspecting) {
                ModelBounds bounds = weaponModelBounds.computeIfAbsent(
                    itemId,
                    ignored -> findModelBounds(boundsModel, stack)
                );
                Vector3f rawCenter = bounds.center();
                Vector3f centeredGuiPivot = new Vector3f(
                    rawCenter.x() - 0.5F,
                    rawCenter.y() - 0.5F,
                    rawCenter.z() - 0.5F
                );
                PoseStack guiTransform = new PoseStack();
                ClientHooks.handleCameraTransforms(
                    guiTransform,
                    model,
                    ItemDisplayContext.GUI,
                    false
                );
                Vector3f displayedPivot = guiTransform.last().pose()
                    .transformPosition(new Vector3f(centeredGuiPivot));

                pose.translate(
                    -displayedPivot.x(),
                    -displayedPivot.y(),
                    -displayedPivot.z()
                );
                model = ClientHooks.handleCameraTransforms(
                    pose,
                    model,
                    ItemDisplayContext.GUI,
                    false
                );
                pose.translate(
                    centeredGuiPivot.x(),
                    centeredGuiPivot.y(),
                    centeredGuiPivot.z()
                );
                pose.mulPose(Axis.YP.rotationDegrees(weaponPreviewRotationY));
                pose.translate(-rawCenter.x(), -rawCenter.y(), -rawCenter.z());
            } else {
                model = ClientHooks.handleCameraTransforms(
                    pose,
                    model,
                    ItemDisplayContext.GUI,
                    false
                );
                pose.translate(-0.5F, -0.5F, -0.5F);
            }
            if (flatLighting) {
                Lighting.setupForFlatItems();
            }
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                for (var renderType : pass.getRenderTypes(stack, true)) {
                    var consumer = ItemRenderer.getFoilBufferDirect(
                        graphics.bufferSource(),
                        renderType,
                        true,
                        stack.hasFoil()
                    );
                    renderer.renderModelLists(
                        pass,
                        stack,
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        pose,
                        consumer
                    );
                }
            }
            graphics.flush();
        } finally {
            if (flatLighting) {
                Lighting.setupFor3DItems();
            }
            pose.popPose();
        }
    }

    private ModelBounds findModelBounds(BakedModel model, ItemStack stack) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        List<BakedQuad> quads = new ArrayList<>();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            quads.addAll(pass.getQuads(null, null, RandomSource.create(42L)));
            for (Direction direction : Direction.values()) {
                quads.addAll(pass.getQuads(null, direction, RandomSource.create(42L)));
            }
        }
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                float vx = Float.intBitsToFloat(vertices[offset]);
                float vy = Float.intBitsToFloat(vertices[offset + 1]);
                float vz = Float.intBitsToFloat(vertices[offset + 2]);
                minX = Math.min(minX, vx);
                minY = Math.min(minY, vy);
                minZ = Math.min(minZ, vz);
                maxX = Math.max(maxX, vx);
                maxY = Math.max(maxY, vy);
                maxZ = Math.max(maxZ, vz);
            }
        }
        if (quads.isEmpty()) return ModelBounds.UNIT;
        return new ModelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void prepareWeaponPreview(String offerId) {
        if (offerId.equals(inspectionWeaponId)) return;
        inspectionWeaponId = offerId;
        weaponPreviewInspecting = false;
        rotatingWeaponPreview = false;
        weaponPreviewRotationY = 0.0F;
    }

    private void drawArtSlot(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int accent
    ) {
        cutPanel(graphics, x, y, width, height, 0xFF02060C, withAlpha(accent, 105), 3);
        panel(graphics, x + 3, y + 3, width - 6, height - 6, 0xFF040A12, 0xFF0B1724);
        int corner = Math.min(9, Math.max(4, Math.min(width, height) / 8));
        int marker = withAlpha(accent, 145);
        graphics.hLine(x + 6, x + 6 + corner, y + 6, marker);
        graphics.vLine(x + 6, y + 6, y + 6 + corner, marker);
        graphics.hLine(x + width - 7 - corner, x + width - 7, y + height - 7, marker);
        graphics.vLine(x + width - 7, y + height - 7 - corner, y + height - 7, marker);
    }

    private void drawBarterDetails(
        GuiGraphics graphics,
        DungeonShopView.Offer offer,
        int panelX,
        int panelWidth
    ) {
        DungeonShopBarterText.Presentation presentation = DungeonShopBarterText.parse(offer);
        int x = panelX + 24;
        int width = panelWidth - 48;
        int y = 309;
        if (!presentation.comparison().grade().isBlank()) {
            drawFit(graphics, presentation.comparison().grade(), x, y, 58,
                GRADE_I_COLOR, 0.62F);
            drawFit(graphics, "  ·  " + presentation.comparison().description().toUpperCase(Locale.ROOT),
                x + 56, y, width - 56, MUTED, 0.56F);
            y += 10;
        }

        String heading = presentation.missingMaterials()
            ? "MISSING MATERIALS" : "REQUIRED MATERIALS";
        drawFit(graphics, heading, x, y, width,
            presentation.missingMaterials() ? RED : CYAN, 0.61F);
        y += 10;
        for (DungeonShopBarterText.Material material : presentation.materials()) {
            drawBarterMaterial(graphics, x, y, width, material, "•  ");
            y += 9;
        }
        if (presentation.output() != null) {
            drawBarterMaterial(graphics, x, y, width, presentation.output(), "RECEIVE  ");
        }
    }

    private void drawBarterMaterial(
        GuiGraphics graphics,
        int x,
        int y,
        int maxWidth,
        DungeonShopBarterText.Material material,
        String prefix
    ) {
        String quantity = material.quantity() + "x  ";
        String full = prefix + quantity + material.name();
        float scale = Math.min(0.62F,
            maxWidth / (float) Math.max(1, font.width(full)));
        scale = Math.max(0.48F, scale);
        drawScaled(graphics, prefix, x, y, DIM, scale);
        int cursor = x + Math.round(font.width(prefix) * scale);
        drawScaled(graphics, quantity, cursor, y, GOLD, scale);
        cursor += Math.round(font.width(quantity) * scale);
        drawScaled(graphics, material.name(), cursor, y, MATERIAL_NAME_COLOR, scale);
    }

    private static boolean isMissingMaterials(DungeonShopView.Offer offer) {
        return offer.tab() == DungeonShopView.Tab.BARTER
            && (offer.state() == DungeonShopView.State.LOCKED
                || "MISSING MATERIALS".equalsIgnoreCase(offer.actionLabel()));
    }

    private static String barterActionLabel(DungeonShopView.Offer offer) {
        if (offer.tab() == DungeonShopView.Tab.BARTER) {
            return isMissingMaterials(offer) ? "MISSING MATERIALS" : "BARTER";
        }
        return offer.actionLabel().isBlank() ? "REVIEW" : offer.actionLabel();
    }

    private void drawCoin(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int width = Math.max(10, radius * 2 + 4);
        int height = Math.max(8, Math.round(width * COIN_CROP_HEIGHT / (float) COIN_CROP_WIDTH));
        drawCoinTexture(graphics, "gold", centerX - width / 2, centerY - height / 2, width, height);
    }

    private void drawCurrencyStrip(GuiGraphics graphics, int x, int centerY, int maxWidth) {
        CurrencyEntry[] entries = {
            new CurrencyEntry("bronze", view.wallet().bronze(), 0xFFD88B58),
            new CurrencyEntry("silver", view.wallet().silver(), 0xFFDCE5ED),
            new CurrencyEntry("gold", view.wallet().gold(), GOLD),
            new CurrencyEntry("stellar-gold", view.wallet().stellarGold(), 0xFFFFE28A)
        };
        String[] labels = new String[entries.length];
        int iconWidth = 15;
        int gap = 5;
        int padding = 5;
        float textScale = 0.68F;
        int naturalWidth = 0;
        for (int index = 0; index < entries.length; index++) {
            labels[index] = String.format(Locale.ROOT, "%,d", entries[index].amount());
            naturalWidth += iconWidth + gap
                + Math.round(font.width(labels[index]) * textScale) + padding;
        }
        if (naturalWidth > maxWidth) {
            textScale = Math.max(0.48F, textScale * maxWidth / (float) naturalWidth);
        }
        int cursorX = x;
        for (int index = 0; index < entries.length; index++) {
            CurrencyEntry entry = entries[index];
            int textWidth = Math.round(font.width(labels[index]) * textScale);
            int entryWidth = iconWidth + gap + textWidth + padding;
            drawCoinTexture(
                graphics,
                entry.textureName(),
                cursorX,
                centerY - 7,
                iconWidth,
                12
            );
            drawScaled(
                graphics,
                labels[index],
                cursorX + iconWidth + gap,
                centerY - 4,
                entry.color(),
                textScale
            );
            cursorX += entryWidth;
        }
    }

    private void drawCoinTexture(
        GuiGraphics graphics,
        String textureName,
        int x,
        int y,
        int width,
        int height
    ) {
        graphics.blit(
            ResourceLocation.fromNamespaceAndPath(
                "tensura_dungeon_ui",
                "textures/gui/rewards/coin-" + textureName + ".png"
            ),
            x,
            y,
            width,
            height,
            COIN_CROP_X,
            COIN_CROP_Y,
            COIN_CROP_WIDTH,
            COIN_CROP_HEIGHT,
            COIN_TEXTURE_SIZE,
            COIN_TEXTURE_SIZE
        );
    }

    private void drawHourglass(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x, x + 13, y - 7, GOLD);
        graphics.hLine(x, x + 13, y + 9, GOLD);
        graphics.fill(x + 2, y - 5, x + 12, y - 3, 0xFF4EC8E8);
        graphics.fill(x + 5, y - 2, x + 9, y + 4, 0xFF113447);
        graphics.fill(x + 3, y + 5, x + 11, y + 7, 0xFF4EC8E8);
    }

    private void drawButton(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        int h,
        String label,
        boolean emphasized,
        boolean disabled,
        int color
    ) {
        int fill = disabled ? 0xFF090C13 : emphasized ? 0xFF10284A : 0xFF071326;
        int border = disabled ? 0xFF292D37 : emphasized ? GOLD : withAlpha(color, 210);
        cutPanel(graphics, x, y, w, h, fill, border, Math.min(5, h / 4));
        drawCenteredFit(graphics, label, x + w / 2, y + Math.max(4, (h - 8) / 2),
            w - 16, disabled ? DIM : color, h >= 30 ? 0.9F : 0.7F);
    }

    private void drawEmpty(GuiGraphics graphics, String message, int x, int y, int w, int h) {
        panel(graphics, x, y, w, h, 0x8A030812, LINE_FAINT);
        drawCenteredFit(graphics, message, x + w / 2, y + h / 2 - 4,
            w - 30, MUTED, 0.72F);
    }

    private void drawToast(GuiGraphics graphics) {
        int width = Math.min(520, Math.max(230, font.width(toast) + 34));
        int x = (W - width) / 2;
        int y = 112;
        panel(graphics, x, y, width, 24, 0xFB071324, CYAN);
        drawCenteredFit(graphics, toast, W / 2, y + 8, width - 24, WHITE, 0.72F);
    }

    private void drawTooltip(GuiGraphics graphics, String value, int x, int y) {
        int w = Math.min(270, font.width(value) + 12);
        x = Math.min(x, W - w - 4);
        y = Math.min(y, H - 20);
        panel(graphics, x, y, w, 17, 0xFA050A13, LINE);
        drawFit(graphics, value, x + 6, y + 5, w - 12, TEXT, 0.64F);
    }

    private void drawTensuraAbilityTooltip(
        GuiGraphics graphics,
        DungeonShopArt.WeaponPresentation presentation,
        int x,
        int y
    ) {
        String key = presentation.abilityTranslationKey();
        if (key.isBlank()) return;
        TensuraShopAbilityBridge.AbilityMetadata ability =
            TensuraShopAbilityBridge.resolve(key);
        String name = ability.name().getString();
        String description = ability.description().getString();
        List<String> descriptionLines = wrap(description, 238, 4);
        TensuraShopAbilityBridge.AbilityMetrics metrics =
            TensuraShopAbilityBridge.metrics(key, Minecraft.getInstance().player);
        int w = 254;
        int descriptionBottom = 31 + descriptionLines.size() * 11;
        int metricRows = metrics.available() ? 2 : 0;
        int h = descriptionBottom + 24 + metricRows * 12;
        x = Math.max(4, Math.min(x, W - w - 4));
        y = Math.max(4, Math.min(y, H - h - 4));
        cutPanel(graphics, x, y, w, h, 0xFC040916, VIOLET, 4);
        graphics.hLine(x + 7, x + w - 8, y + 20, withAlpha(CYAN, 110));
        int nameX = x + 8;
        if (ability.hasIcon()) {
            graphics.blit(
                ability.icon(),
                x + 7,
                y + 3,
                16,
                16,
                0.0F,
                0.0F,
                32,
                32,
                32,
                32
            );
            nameX = x + 28;
        }
        drawFit(graphics, name.toUpperCase(Locale.ROOT), nameX, y + 7,
            x + w - 72 - nameX, CYAN, 0.73F);
        drawRightFit(graphics, "TENSURA", x + w - 8, y + 7,
            62, VIOLET, 0.56F);
        for (int line = 0; line < descriptionLines.size(); line++) {
            drawFit(graphics, descriptionLines.get(line), x + 8, y + 26 + line * 11,
                w - 16, TEXT, 0.64F);
        }
        int ruleY = y + descriptionBottom;
        graphics.hLine(x + 7, x + w - 8, ruleY - 3, withAlpha(GOLD, 90));
        if (metrics.available()) {
            drawFit(graphics, "RAW OUTPUT", x + 8, ruleY + 4,
                76, DIM, 0.58F);
            drawRightFit(graphics, metrics.rawOutput(), x + w - 8, ruleY + 3,
                w - 96, WHITE, 0.61F);
            drawFit(graphics, "CURRENT COST", x + 8, ruleY + 16,
                82, DIM, 0.58F);
            drawRightFit(graphics, metrics.magiculeCost(), x + w - 8, ruleY + 15,
                w - 102, CYAN, 0.61F);
            ruleY += 24;
        }
        drawFit(graphics, "WEAPON COOLDOWN", x + 8, ruleY + 4,
            100, DIM, 0.58F);
        drawRightFit(graphics, presentation.cooldown().toUpperCase(Locale.ROOT),
            x + w - 8, ruleY + 3, 80, GOLD, 0.68F);
    }

    private boolean isAbilityHover(DungeonShopView.Offer offer, int mouseX, int mouseY) {
        if (offer == null || offer.icon() != DungeonShopView.Icon.WEAPON) return false;
        if (!DungeonShopArt.weaponPresentation(offer).hasCast()) return false;
        int x = DungeonShopLayout.PREVIEW_X;
        int w = DungeonShopLayout.PREVIEW_WIDTH;
        return mouseX >= x + 22 && mouseX < x + w - 22
            && mouseY >= 322 && mouseY < 336;
    }

    private boolean isWeaponPreviewHover(
        DungeonShopView.Offer offer,
        int mouseX,
        int mouseY
    ) {
        if (offer == null || offer.icon() != DungeonShopView.Icon.WEAPON) return false;
        int x = DungeonShopLayout.PREVIEW_X;
        int w = DungeonShopLayout.PREVIEW_WIDTH;
        return mouseX >= x + 28 && mouseX < x + w - 28
            && mouseY >= 202 && mouseY < 304;
    }

    private Optional<ItemStack> itemStack(DungeonShopView.Offer offer) {
        if (offer == null) return Optional.empty();
        String rawItemId = DungeonShopArt.registeredItemId(offer);
        if (rawItemId.isBlank()) return Optional.empty();
        try {
            ResourceLocation itemId = ResourceLocation.parse(rawItemId);
            return BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String statePrice(DungeonShopView.Offer offer) {
        if (isMissingMaterials(offer)) return "MISSING MATERIALS";
        return switch (offer.state()) {
            case PURCHASED -> "PURCHASED";
            case OWNED -> "OWNED";
            case LOCKED -> offer.tab() == DungeonShopView.Tab.BARTER
                ? "MISSING MATERIALS"
                : "LOCKED";
            case SOLD_OUT -> "SOLD OUT";
            default -> offer.price();
        };
    }

    private int priceColor(DungeonShopView.Offer offer) {
        if (isMissingMaterials(offer)) return RED;
        return switch (offer.state()) {
            case PURCHASED, OWNED -> GREEN;
            case LOCKED, SOLD_OUT -> DIM;
            default -> GOLD;
        };
    }

    private int stateTextColor(DungeonShopView.Offer offer) {
        if (isMissingMaterials(offer)) return RED;
        return offerResolved(offer) ? DIM : CYAN;
    }

    private boolean offerResolved(DungeonShopView.Offer offer) {
        return offer.state() == DungeonShopView.State.PURCHASED
            || offer.state() == DungeonShopView.State.OWNED;
    }

    private String actionOfferTooltip(DungeonShopView.Offer offer) {
        if (offer.state() == DungeonShopView.State.VENDOR_ROUTED) {
            return "Close the shop and guide me to " + offer.vendor();
        }
        return offer.tab() == DungeonShopView.Tab.BARTER
            ? "Review eligible Monster Cores"
            : "Review purchase confirmation";
    }

    private List<DungeonShopView.Offer> offers(DungeonShopView.Section section) {
        return DungeonShopLayout.visibleOffers(
            view, tab, section, search, gradeFilter, "ALL", "ALL"
        );
    }

    private DungeonShopView.Offer selectedOffer() {
        DungeonShopView.Offer selected = view.offers().stream()
            .filter(offer -> offer.id().equals(selectedOfferId))
            .filter(offer -> offer.tab() == tab)
            .filter(offer -> offer.matches(search, gradeFilter, "ALL", "ALL"))
            .findFirst()
            .orElse(null);
        if (selected != null) return selected;
        DungeonShopView.Offer first = DungeonShopLayout.firstVisible(
            view, tab, search, gradeFilter, "ALL", "ALL"
        );
        selectedOfferId = first == null ? "" : first.id();
        return first;
    }

    private void selectTab(DungeonShopView.Tab selected) {
        if (!view.preview() && selected != view.initialTab()) {
            DungeonShopUiApi.requestTab(selected);
            showToast("REQUESTING " + selected.label() + " STOCK");
            return;
        }
        tab = selected;
        confirmation = null;
        searchFocused = false;
        essentialScroll = 0;
        timedScroll = 0;
        DungeonShopView.Offer first = DungeonShopLayout.firstVisible(
            view, tab, search, gradeFilter, "ALL", "ALL"
        );
        selectedOfferId = first == null ? "" : first.id();
    }

    private void cycleGrade(int direction) {
        gradeFilter = nextFilter(GRADE_FILTERS, gradeFilter, direction);
        filtersChanged();
    }

    private void filtersChanged() {
        searchFocused = false;
        essentialScroll = 0;
        timedScroll = 0;
        selectedOffer();
    }

    private static String nextFilter(String[] values, String current, int direction) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        return values[Math.floorMod(index + direction, values.length)];
    }

    private void addHotspot(int x, int y, int w, int h, String tooltip, Runnable action) {
        hotspots.add(new Hotspot(x, y, w, h, tooltip == null ? "" : tooltip, action));
    }

    private Hotspot hovered(int x, int y) {
        for (int index = hotspots.size() - 1; index >= 0; index--) {
            Hotspot hotspot = hotspots.get(index);
            if (hotspot.contains(x, y)) return hotspot;
        }
        return null;
    }

    private void showToast(String message) {
        toast = message == null ? "" : message;
        toastUntil = System.currentTimeMillis() + 2_600L;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        if (confirmation == null && selectedOffer() != null
            && selectedOffer().icon() == DungeonShopView.Icon.WEAPON
            && vx >= DungeonShopLayout.PREVIEW_X + 28
            && vx < DungeonShopLayout.PREVIEW_X + DungeonShopLayout.PREVIEW_WIDTH - 28
            && vy >= 202 && vy < 304) {
            weaponPreviewInspecting = true;
            rotatingWeaponPreview = true;
            lastRotationMouseX = mouseX;
            return true;
        }
        Hotspot hotspot = hovered(vx, vy);
        if (hotspot != null) {
            if (confirmation != null
                && !(hotspot.tooltip().equals("Return to the offer")
                || hotspot.tooltip().equals("Submit this transaction"))) {
                return true;
            }
            hotspot.action().run();
            return true;
        }
        searchFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
        double mouseX,
        double mouseY,
        int button,
        double dragX,
        double dragY
    ) {
        if (button == 0 && rotatingWeaponPreview) {
            double deltaX = (mouseX - lastRotationMouseX) / Math.max(0.01F, uiScale);
            weaponPreviewRotationY =
                (weaponPreviewRotationY + (float) deltaX * 1.4F) % 360.0F;
            lastRotationMouseX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && rotatingWeaponPreview) {
            rotatingWeaponPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (confirmation != null) return true;
        if (tab == DungeonShopView.Tab.DAILY) {
            essentialScroll = DungeonShopLayout.clampScroll(
                essentialScroll + (scrollY < 0 ? 1 : -1),
                offers(DungeonShopView.Section.ESSENTIAL).size(),
                DungeonShopLayout.ESSENTIAL_VISIBLE
            );
            return true;
        }
        if (tab == DungeonShopView.Tab.WEEKLY) {
            timedScroll = DungeonShopLayout.clampScroll(
                timedScroll + (scrollY < 0 ? 1 : -1),
                offers(DungeonShopView.Section.TIMED).size(),
                DungeonShopLayout.TIMED_VISIBLE
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && confirmation == null && !Character.isISOControl(codePoint)
            && search.length() < 48) {
            search += codePoint;
            essentialScroll = 0;
            timedScroll = 0;
            selectedOffer();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (confirmation != null) {
                confirmation = null;
                return true;
            }
            onClose();
            return true;
        }
        if (searchFocused && confirmation == null) {
            if (keyCode == 259 && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                essentialScroll = 0;
                timedScroll = 0;
                selectedOffer();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.hLine(x, x + w - 1, y, border);
        graphics.hLine(x, x + w - 1, y + h - 1, border);
        graphics.vLine(x, y, y + h - 1, border);
        graphics.vLine(x + w - 1, y, y + h - 1, border);
    }

    private void cutPanel(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        int h,
        int fill,
        int border,
        int cut
    ) {
        graphics.fill(x + cut, y, x + w - cut, y + h, fill);
        graphics.fill(x, y + cut, x + w, y + h - cut, fill);
        graphics.fill(x + cut / 2, y + cut / 2, x + w - cut / 2, y + h - cut / 2, fill);
        graphics.hLine(x + cut, x + w - cut - 1, y, border);
        graphics.hLine(x + cut, x + w - cut - 1, y + h - 1, border);
        graphics.vLine(x, y + cut, y + h - cut - 1, border);
        graphics.vLine(x + w - 1, y + cut, y + h - cut - 1, border);
        for (int i = 0; i < cut; i++) {
            graphics.fill(x + cut - i - 1, y + i, x + cut - i, y + i + 1, border);
            graphics.fill(x + w - cut + i, y + i, x + w - cut + i + 1, y + i + 1, border);
            graphics.fill(x + i, y + h - cut + i, x + i + 1, y + h - cut + i + 1, border);
            graphics.fill(x + w - i - 1, y + h - cut + i,
                x + w - i, y + h - cut + i + 1, border);
        }
    }

    private void diamond(
        GuiGraphics graphics,
        int centerX,
        int centerY,
        int radius,
        int fill,
        int border
    ) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, fill);
            graphics.fill(centerX - half, centerY + dy, centerX - half + 1, centerY + dy + 1, border);
            graphics.fill(centerX + half, centerY + dy, centerX + half + 1, centerY + dy + 1, border);
        }
    }

    private List<String> wrap(String value, int maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        String remaining = value == null ? "" : value.trim();
        while (!remaining.isEmpty() && result.size() < maxLines) {
            String line = font.plainSubstrByWidth(remaining, maxWidth);
            if (line.isEmpty()) break;
            if (line.length() < remaining.length()) {
                int breakAt = line.lastIndexOf(' ');
                if (breakAt > 0) line = line.substring(0, breakAt);
            }
            result.add(line);
            remaining = remaining.substring(Math.min(remaining.length(), line.length())).trim();
        }
        if (!remaining.isEmpty() && !result.isEmpty()) {
            String last = result.removeLast();
            result.add(font.plainSubstrByWidth(last + "…", maxWidth));
        }
        return result;
    }

    private void draw(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, false);
    }

    private void drawCentered(GuiGraphics graphics, String value, int centerX, int y, int color) {
        graphics.drawCenteredString(font, value, centerX, y, color);
    }

    private void drawFit(
        GuiGraphics graphics,
        String value,
        int x,
        int y,
        int maxWidth,
        int color,
        float preferredScale
    ) {
        String rendered = value == null ? "" : value;
        float scale = Math.min(preferredScale,
            maxWidth / (float) Math.max(1, font.width(rendered)));
        drawScaled(graphics, rendered, x, y, color, Math.max(0.48F, scale));
    }

    private void drawCenteredFit(
        GuiGraphics graphics,
        String value,
        int centerX,
        int y,
        int maxWidth,
        int color,
        float preferredScale
    ) {
        String rendered = value == null ? "" : value;
        float scale = Math.min(preferredScale,
            maxWidth / (float) Math.max(1, font.width(rendered)));
        drawCenteredScaled(graphics, rendered, centerX, y, color, Math.max(0.48F, scale));
    }

    private void drawRightFit(
        GuiGraphics graphics,
        String value,
        int rightX,
        int y,
        int maxWidth,
        int color,
        float preferredScale
    ) {
        String rendered = value == null ? "" : value;
        float scale = Math.min(preferredScale,
            maxWidth / (float) Math.max(1, font.width(rendered)));
        scale = Math.max(0.48F, scale);
        int x = rightX - Math.round(font.width(rendered) * scale);
        drawScaled(graphics, rendered, x, y, color, scale);
    }

    private void drawScaled(
        GuiGraphics graphics,
        String value,
        int x,
        int y,
        int color,
        float scale
    ) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, value, 0, 0, color, false);
        pose.popPose();
    }

    private void drawCenteredScaled(
        GuiGraphics graphics,
        String value,
        int centerX,
        int y,
        int color,
        float scale
    ) {
        int width = Math.round(font.width(value) * scale);
        drawScaled(graphics, value, centerX - width / 2, y, color, scale);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private record Hotspot(int x, int y, int width, int height, String tooltip, Runnable action) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record ModelBounds(
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ
    ) {
        private static final ModelBounds UNIT = new ModelBounds(0, 0, 0, 1, 1, 1);

        Vector3f center() {
            return new Vector3f(
                (minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F
            );
        }
    }

    private record CurrencyEntry(String textureName, long amount, int color) {}
}
