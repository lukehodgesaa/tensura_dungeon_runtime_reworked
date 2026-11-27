package dev.luke.tensuradungeon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineclawd.tensuradungeon.network.DungeonUiSnapshotPayload;
import com.mineclawd.tensuradungeon.client.DungeonHudSuppressingScreen;
import dev.luke.tensuradungeon.client.DungeonData.Filter;
import dev.luke.tensuradungeon.client.DungeonData.Floor;
import dev.luke.tensuradungeon.client.DungeonData.Kind;
import dev.luke.tensuradungeon.client.DungeonData.Modifier;
import dev.luke.tensuradungeon.client.DungeonData.Reward;
import dev.luke.tensuradungeon.client.DungeonData.Tone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DungeonScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int VIRTUAL_W = 960;
    private static final int VIRTUAL_H = 540;

    private static final int TOPBAR_H = 36;
    private static final int SIDEBAR_X = 10;
    private static final int SIDEBAR_Y = 44;
    private static final int SIDEBAR_W = 118;
    private static final int CONTENT_X = 138;
    private static final int CONTENT_RIGHT = 950;

    private static final int FLOOR_COL_W = 68;
    private static final int DEPTH_COL_W = 142;
    private static final int MODIFIER_COL_W = 250;
    private static final int REWARD_COL_W = 210;
    private static final int EP_COL_W = 132;
    private static final int STATUS_COL_W = 118;
    private static final int ROWS_VISIBLE = 6;
    private static final int FLOORS_PER_RANGE = 10;

    private static final int BG = 0xFF01050C;
    private static final int BG_2 = 0xFF020914;
    private static final int PANEL = 0xF5061225;
    private static final int PANEL_DARK = 0xF5030B18;
    private static final int LINE = 0xFF173B63;
    private static final int LINE_FAINT = 0x7F173B63;
    private static final int BLUE = 0xFF35A9FF;
    private static final int VIOLET = 0xFFAD50FF;
    private static final int GREEN = 0xFF1EE4A0;
    private static final int RED = 0xFFFF4051;
    private static final int ORANGE = 0xFFFF8B20;
    private static final int GOLD = 0xFFFFB730;
    private static final int WHITE = 0xFFF1F4FF;
    private static final int TEXT = 0xFFD9DEEE;
    private static final int MUTED = 0xFF929EB6;
    private static final int DIM = 0xFF64718A;

    private static final ResourceLocation SACRED_RUINS = ResourceLocation.fromNamespaceAndPath(
            DungeonData.MOD_ID, "textures/gui/sacred_ruins.png");
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(
            DungeonData.MOD_ID, "textures/gui/logo.png");
    private static final ResourceLocation BRONZE_COIN = ResourceLocation.fromNamespaceAndPath(
            DungeonData.MOD_ID, "textures/gui/rewards/coin-bronze.png");
    private static final ResourceLocation SILVER_COIN = ResourceLocation.fromNamespaceAndPath(
            DungeonData.MOD_ID, "textures/gui/rewards/coin-silver.png");
    private static final ResourceLocation GOLD_COIN = ResourceLocation.fromNamespaceAndPath(
            DungeonData.MOD_ID, "textures/gui/rewards/coin-gold.png");
    private static final ResourceLocation STELLAR_GOLD_COIN = ResourceLocation.fromNamespaceAndPath(
            DungeonData.MOD_ID, "textures/gui/rewards/coin-stellar-gold.png");

    private enum Page { FLOOR, ACTIVE, REWARDS, RANKINGS, LOG, GUIDE }
    private enum Focus { NONE, FLOOR_SEARCH, GO_FLOOR, GUIDE_SEARCH }

    private Page page = Page.FLOOR;
    private Focus focus = Focus.NONE;
    private int selectedFloor = DungeonData.CURRENT_FLOOR;
    private DungeonUiSnapshotPayload lastSynchronizedSnapshot;
    private int rangeIndex = (DungeonData.CURRENT_FLOOR - 1) / FLOORS_PER_RANGE;
    private int rowScroll;
    private Filter floorFilter = Filter.ALL;
    private String floorSearch = "";
    private String goFloor = String.valueOf(DungeonData.CURRENT_FLOOR);

    private Kind guideFilter;
    private String guideSearch = "";
    private int guideScroll;
    private Modifier selectedGuideModifier = DungeonData.modifierById("swarm");

    private List<Modifier> modifierModal;
    private String modifierModalTitle = "ACTIVE MODIFIERS";
    private int modalScroll;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private String toastText = "";
    private long toastUntil;
    private String lastRuntimeMessage = "";
    private boolean initializedFromRuntime;
    private int runtimeRefreshTicks;

    private float uiScale = 1.0f;
    private float uiX;
    private float uiY;

    public DungeonScreen() {
        super(Component.literal("Dimensional Dungeon"));
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
        DungeonRuntimeBridge.refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        synchronizeRuntimeSnapshot();
        updateTransform();
        graphics.fill(0, 0, width, height, BG);
        drawOutsideBackdrop(graphics);

        int virtualMouseX = virtualX(mouseX);
        int virtualMouseY = virtualY(mouseY);
        hotspots.clear();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(uiX, uiY, 0.0f);
        pose.scale(uiScale, uiScale, 1.0f);

        drawInterfaceBackground(graphics);
        drawTopBar(graphics, virtualMouseX, virtualMouseY);
        drawSidebar(graphics);

        switch (page) {
            case FLOOR -> drawFloorPage(graphics);
            case ACTIVE -> drawActiveRunPage(graphics);
            case REWARDS -> drawRewardsPage(graphics);
            case RANKINGS -> drawRankingsPage(graphics);
            case LOG -> drawDungeonLogPage(graphics);
            case GUIDE -> drawModifierGuidePage(graphics);
        }

        if (modifierModal != null) drawModifierModal(graphics);

        Hotspot hovered = hoveredAt(virtualMouseX, virtualMouseY);
        if (hovered != null && hovered.tooltip != null && !hovered.tooltip.isBlank()) {
            drawTooltip(graphics, hovered.tooltip, virtualMouseX + 7, virtualMouseY + 7);
        }
        if (!toastText.isBlank() && System.currentTimeMillis() < toastUntil) drawToast(graphics, toastText);

        pose.popPose();
    }

    private void updateTransform() {
        uiScale = Math.min(width / (float) VIRTUAL_W, height / (float) VIRTUAL_H);
        uiX = (width - VIRTUAL_W * uiScale) * 0.5f;
        uiY = (height - VIRTUAL_H * uiScale) * 0.5f;
    }

    @Override
    public void tick() {
        super.tick();
        runtimeRefreshTicks++;
        if (runtimeRefreshTicks >= 40) {
            runtimeRefreshTicks = 0;
            DungeonRuntimeBridge.refresh();
        }
    }

    private void synchronizeRuntimeSnapshot() {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null) return;
        if (snapshot != lastSynchronizedSnapshot) {
            int authoritativeFloor = snapshot.progress().selectedFloor();
            int reconciledFloor = clamp(authoritativeFloor, 1, 99);
            if (!initializedFromRuntime || selectedFloor != reconciledFloor) {
                selectedFloor = reconciledFloor;
                goFloor = String.valueOf(selectedFloor);
            }
            rangeIndex = (selectedFloor - 1) / FLOORS_PER_RANGE;
            initializedFromRuntime = true;
            lastSynchronizedSnapshot = snapshot;
        }
        if (!snapshot.message().isBlank() && !snapshot.message().equals(lastRuntimeMessage)) {
            lastRuntimeMessage = snapshot.message();
            showToast(snapshot.message());
        }
    }

    private boolean runtimeUnlocked(Floor floor) {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        return snapshot != null && floor.number() <= snapshot.progress().highestUnlockedFloor();
    }

    private boolean runtimeCurrent(Floor floor) {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        return snapshot != null
                && snapshot.run().active()
                && !snapshot.run().completed()
                && !snapshot.run().failed()
                && floor.number() == snapshot.run().floor();
    }

    private int virtualX(double mouseX) {
        return (int) Math.floor((mouseX - uiX) / uiScale);
    }

    private int virtualY(double mouseY) {
        return (int) Math.floor((mouseY - uiY) / uiScale);
    }

    private void drawOutsideBackdrop(GuiGraphics graphics) {
        int cx = width / 2;
        int cy = height / 2;
        graphics.fillGradient(0, 0, width, height, 0xFF020714, 0xFF00030A);
        graphics.fill(Math.max(0, cx - width / 3), 0, Math.min(width, cx + width / 3), height, 0x170F3D78);
        graphics.fill(0, Math.max(0, cy - height / 3), width, Math.min(height, cy + height / 3), 0x090050AA);
    }

    private void drawInterfaceBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, VIRTUAL_W, VIRTUAL_H, BG);
        for (int x = 0; x < VIRTUAL_W; x += 64) {
            graphics.vLine(x, TOPBAR_H, VIRTUAL_H, 0x101B4E7A);
        }
        for (int y = TOPBAR_H + 1; y < VIRTUAL_H; y += 45) {
            graphics.hLine(0, VIRTUAL_W, y, 0x0C1B4E7A);
        }
        graphics.fill(0, 0, VIRTUAL_W, TOPBAR_H, 0xFA010611);
        graphics.hLine(0, VIRTUAL_W, TOPBAR_H, 0x80436AA4);
    }

    private void drawTopBar(GuiGraphics graphics, int mouseX, int mouseY) {
        blit(graphics, LOGO, 10, 4, 28, 28, 64, 64);
        drawFit(graphics, "DIMENSIONAL DUNGEON", 43, 7, 300, WHITE, 1.08f, 0.85f, false);
        drawFit(graphics, "TENSURA DIMENSIONAL DUNGEON RUNTIME", 43, 22, 330, DIM, 1.0f, 0.8f, false);

        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        DungeonUiSnapshotPayload.CurrencySummary coins = snapshot == null
                ? new DungeonUiSnapshotPayload.CurrencySummary(0, 0, 0, 0)
                : snapshot.currency();
        int closeX = VIRTUAL_W - 38;
        int walletW = 48;
        int gap = 4;
        int bronzeX = closeX - gap - walletW * 4 - gap * 3;
        int silverX = bronzeX + walletW + gap;
        int goldX = silverX + walletW + gap;
        int stellarX = goldX + walletW + gap;

        drawCoinWallet(graphics, bronzeX, 8, walletW, BRONZE_COIN, coins.bronze(), "Bronze");
        drawCoinWallet(graphics, silverX, 8, walletW, SILVER_COIN, coins.silver(), "Silver");
        drawCoinWallet(graphics, goldX, 8, walletW, GOLD_COIN, coins.gold(), "Gold");
        drawCoinWallet(graphics, stellarX, 8, walletW, STELLAR_GOLD_COIN,
                coins.stellarGold(), "Stellar Gold");

        drawButton(graphics, closeX, 7, 28, 21, "X", false, false);
        hotspot(closeX, 7, 28, 21, this::onClose, "Close");
    }

    private void drawCoinWallet(
            GuiGraphics graphics,
            int x,
            int y,
            int w,
            ResourceLocation texture,
            long amount,
            String denomination
    ) {
        fillPanel(graphics, x, y, w, 19, 0xF5030B17, 0xFF1B3556);
        blit(graphics, texture, x + 4, y + 2, 15, 15, 48, 48);
        drawRightFit(graphics, String.format(Locale.ROOT, "%,d", amount),
                x + w - 5, y + 6, w - 25, TEXT, 1.0f, 0.68f, false);
        hotspot(x, y, w, 19, () -> {},
                denomination + " Coins\nAuthoritative inventory count"
                        + "\n100 Bronze = 1 Silver"
                        + "\n100 Silver = 1 Gold"
                        + "\n100 Gold = 1 Stellar Gold");
    }

    private void drawSidebar(GuiGraphics graphics) {
        final int x = SIDEBAR_X;
        final int y = SIDEBAR_Y;
        final int w = SIDEBAR_W;
        final int navH = 27;
        final int h = navH * 6 + 58;
        fillPanel(graphics, x, y, w, h, 0xF7041020, 0xFF173A5E);

        Page[] pages = {Page.FLOOR, Page.ACTIVE, Page.REWARDS, Page.RANKINGS, Page.LOG, Page.GUIDE};
        String[] labels = {"FLOOR SELECT", "ACTIVE RUN", "REWARDS", "RANKINGS", "DUNGEON LOG", "MODIFIER GUIDE"};
        String[] glyphs = {"O", ">", "*", "#", "=", "+"};
        for (int i = 0; i < pages.length; i++) {
            int itemY = y + i * navH;
            boolean active = page == pages[i];
            if (active) {
                graphics.fill(x, itemY, x + w, itemY + navH, 0xD20A3975);
                graphics.fill(x, itemY, x + 2, itemY + navH, BLUE);
                graphics.hLine(x, x + w, itemY, 0xFF238FEF);
                graphics.hLine(x, x + w, itemY + navH - 1, 0xFF173F71);
            }
            drawCentered(graphics, glyphs[i], x + 14, itemY + 9, active ? BLUE : 0xFF91A3CF);
            drawFit(graphics, labels[i], x + 27, itemY + 9, w - 34,
                    active ? WHITE : 0xFFA4AEC6, 1.0f, 0.72f, false);
            final Page target = pages[i];
            hotspot(x, itemY, w, navH, () -> {
                page = target;
                focus = Focus.NONE;
                modifierModal = null;
            }, labels[i]);
        }

        int seasonY = y + navH * 6;
        graphics.hLine(x + 4, x + w - 4, seasonY, LINE_FAINT);
        fillDiamond(graphics, x + 19, seasonY + 29, 15, VIOLET, 0xFF0D1330);
        drawCenteredFit(graphics, "S1", x + 19, seasonY + 24, 24, WHITE, 1.0f, 0.75f);
        drawFit(graphics, "SEASON 1", x + 39, seasonY + 9, w - 45, WHITE, 1.0f, 0.75f, false);
        drawFit(graphics, "Dimensional Cycle", x + 39, seasonY + 22, w - 45, MUTED, 1.0f, 0.68f, false);
        drawFit(graphics, "27d 11h left", x + 39, seasonY + 35, w - 45, MUTED, 1.0f, 0.72f, false);
    }

    private void drawFloorPage(GuiGraphics graphics) {
        Floor floor = DungeonData.floor(selectedFloor);
        drawFloorOverview(graphics, floor);
        drawRunMeta(graphics, floor);
        drawFloorBrowser(graphics);
    }

    private void drawFloorOverview(GuiGraphics graphics, Floor floor) {
        final int x = CONTENT_X;
        final int y = 37;
        final int w = CONTENT_RIGHT - CONTENT_X;
        final int h = 206;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF23578E);

        final int innerX = x + 10;
        final int summaryW = 220;
        final int gap = 10;
        final int modifierW = 390;
        final int difficultyW = w - 20 - summaryW - modifierW - gap * 2;
        final int mx = innerX + summaryW + gap;
        final int dx = mx + modifierW + gap;

        drawFit(graphics,
                "*  FLOOR " + floor.number() + " - " + floor.theme().name().toUpperCase(Locale.ROOT),
                innerX, y + 8, summaryW, WHITE, 1.0f, 0.78f, false);
        blitRegion(graphics, SACRED_RUINS, innerX, y + 23, summaryW, 101,
                0, 73, 1751, 805, 1751, 951);
        graphics.renderOutline(innerX, y + 23, summaryW, 101, 0xFF263E64);

        drawFit(graphics, "POSSIBLE FLOOR REWARDS", innerX + 2, y + 132,
                summaryW - 4, TEXT, 1.0f, 0.76f, false);
        graphics.hLine(innerX + 2, innerX + summaryW, y + 143, 0xFF17385C);
        drawSelectedRewardPool(graphics, connectedRewardPreview(floor),
                innerX, y + 147, summaryW, 49);

        drawFit(graphics, "*  FLOOR MODIFIERS (THIS RUN)", mx, y + 8, modifierW,
                TEXT, 1.0f, 0.8f, false);
        drawTag(graphics, mx, y + 23, 108, "PLAYER MODIFIERS", BLUE);
        drawModifierRow(graphics, floor.playerModifiers(), mx, y + 38, modifierW, 3, false);

        graphics.hLine(mx, mx + modifierW, y + 100, 0x5A8C252E);
        drawTag(graphics, mx, y + 102, 108, "ENEMY MODIFIERS", RED);
        int visibleEnemy = Math.min(6, floor.enemyModifiers().size());
        int remaining = floor.enemyModifiers().size() - visibleEnemy;
        int moreW = remaining > 0 ? 34 : 0;
        int enemyWidth = modifierW - moreW;
        drawModifierRow(graphics, floor.enemyModifiers().subList(0, visibleEnemy),
                mx, y + 117, enemyWidth, visibleEnemy, true);
        if (remaining > 0) {
            int boxX = mx + enemyWidth + 2;
            fillPanel(graphics, boxX, y + 124, 31, 43, 0xFF06101F, 0xFF203A5D);
            drawCenteredFit(graphics, "+" + remaining, boxX + 15, y + 133, 27, WHITE, 1.0f, 0.75f);
            drawCentered(graphics, "more", boxX + 15, y + 148, MUTED);
            List<Modifier> hidden = new ArrayList<>(floor.enemyModifiers().subList(visibleEnemy, floor.enemyModifiers().size()));
            hotspot(boxX, y + 124, 31, 43, () -> openModifierModal("MORE FLOOR MODIFIERS", hidden),
                    remaining + " more modifiers");
        }

        final int dy = y + 22;
        final int cardH = 171;
        fillPanel(graphics, dx, dy, difficultyW, cardH, 0xF4040E1D, 0xFF193452);
        drawCenteredFit(graphics, "FLOOR PROFILE", dx + difficultyW / 2, dy + 10,
                difficultyW - 16, TEXT, 1.0f, 0.75f);

        drawCenteredFit(graphics, "STRATUM I", dx + difficultyW / 2, dy + 34,
                difficultyW - 18, VIOLET, 1.18f, 0.78f);
        drawCenteredFit(graphics, "DEPTH " + floor.number(), dx + difficultyW / 2, dy + 55,
                difficultyW - 18, WHITE, 1.28f, 0.82f);
        drawCenteredFit(graphics, floor.totalModifierCount() + " ACTIVE MODIFIERS",
                dx + difficultyW / 2, dy + 78, difficultyW - 18,
                MUTED, 1.0f, 0.70f);

        graphics.hLine(dx + 9, dx + difficultyW - 10, dy + 102, LINE_FAINT);
        drawCenteredFit(graphics, "RECOMMENDED EP", dx + difficultyW / 2, dy + 116,
                difficultyW - 18, TEXT, 1.0f, 0.76f);
        drawCenteredFit(graphics, format(floor.recommendedEp()), dx + difficultyW / 2, dy + 139,
                difficultyW - 18, GREEN, 1.42f, 0.82f);
    }

    private void drawModifierRow(GuiGraphics graphics, List<Modifier> modifiers, int x, int y,
                                 int totalWidth, int max, boolean compact) {
        int count = Math.min(max, modifiers.size());
        if (count <= 0) return;
        int slotW = Math.max(1, totalWidth / count);
        int iconSize = compact ? 26 : 28;
        for (int i = 0; i < count; i++) {
            Modifier modifier = modifiers.get(i);
            int slotX = x + i * slotW;
            int centerX = slotX + slotW / 2;
            int iconX = centerX - iconSize / 2;
            blit(graphics, modifier.texture(), iconX, y, iconSize, iconSize, 48, 48);

            int nameY = y + iconSize + 3;
            drawCenteredFit(graphics, modifier.name(), centerX, nameY,
                    slotW - 6, WHITE, 1.0f, compact ? 0.62f : 0.76f);

            if (compact) {
                if (slotW >= 70) {
                    drawCenteredFit(graphics, compactModifierEffect(modifier), centerX, nameY + 12,
                            slotW - 16, MUTED, 0.82f, 0.52f);
                }
            } else {
                float descriptionMinScale = 0.72f;
                int wrapWidth = Math.max(slotW - 8,
                        Math.round((slotW - 8) / descriptionMinScale));
                List<String> lines = wrapLimited(modifier.shortDescription(), wrapWidth, 2);
                for (int line = 0; line < lines.size(); line++) {
                    drawCenteredFit(graphics, lines.get(line), centerX, nameY + 11 + line * 9,
                            slotW - 6, MUTED, 1.0f, descriptionMinScale);
                }
            }
            hotspot(slotX, y, slotW, compact ? 64 : 60,
                    () -> openModifierModal(modifier.name(), List.of(modifier)),
                    modifier.name() + "\n" + modifier.shortDescription());
        }
    }

    private void drawRewardStrip(
            GuiGraphics graphics,
            List<Reward> rewards,
            int x,
            int y,
            int width,
            int height,
            boolean showCounts
    ) {
        int icon = 22;
        int slotWidth = 32;
        int horizontalPadding = 4;
        int moreWidth = 18;
        int capacity = Math.max(1, (width - horizontalPadding * 2) / slotWidth);
        boolean hasOverflow = rewards.size() > capacity;
        int availableWidth = width - horizontalPadding * 2 - (hasOverflow ? moreWidth : 0);
        int max = Math.min(rewards.size(), Math.max(1, availableWidth / slotWidth));
        fillPanel(graphics, x, y, width, height, 0xB5030D1B, 0xFF173556);
        for (int i = 0; i < max; i++) {
            Reward reward = rewards.get(i);
            int slotX = x + horizontalPadding + i * slotWidth;
            int centerX = slotX + slotWidth / 2;
            int ix = centerX - icon / 2;
            drawRewardIcon(graphics, reward, ix, y + 2, icon);
            if (showCounts) {
                drawCenteredFit(graphics, reward.value(), centerX, y + height - 9,
                        slotWidth - 4, WHITE, 0.82f, 0.52f);
            }
            if (reward.firstClearOnly()) drawFirstClearBadge(graphics, ix + icon - 3, y + 5);
            hotspot(ix, y + 2, icon, icon,
                    () -> showToast(reward.type().name() + ": " + reward.value()),
                    reward.tooltip());
        }
        if (rewards.size() > max) {
            int moreX = x + width - 20;
            drawCentered(graphics, "...", x + width - 11, y + 8, VIOLET);
            hotspot(moreX, y + 2, 19, height - 4, () -> {},
                    hiddenRewardsTooltip(rewards, max));
        }
    }

    private void drawFirstClearBadge(GuiGraphics graphics, int centerX, int centerY) {
        fillDiamond(graphics, centerX, centerY, 4, GOLD, 0xFF281A05);
        drawCenteredScaled(graphics, "1", centerX, centerY - 2, WHITE, 0.52f);
    }

    private String hiddenRewardsTooltip(List<Reward> rewards, int start) {
        StringBuilder tooltip = new StringBuilder("MORE POTENTIAL REWARDS");
        for (int i = Math.max(0, start); i < rewards.size(); i++) {
            Reward reward = rewards.get(i);
            tooltip.append("\n").append(reward.type().name())
                    .append(" - ").append(rewardRarity(reward));
        }
        return tooltip.toString();
    }

    private void drawRunMeta(GuiGraphics graphics, Floor floor) {
        final int x = CONTENT_X;
        final int y = 246;
        final int w = CONTENT_RIGHT - CONTENT_X;
        final int buttonW = 108;
        final int noteW = w - buttonW - 12;

        drawFit(graphics, "i  Modifiers change every run. Exact rewards are revealed after the floor.",
                x, y + 5, noteW - 8, MUTED, 1.0f, 0.72f, false);

        boolean current = runtimeCurrent(floor);
        boolean unlocked = runtimeUnlocked(floor);
        String label = current ? "VIEW ACTIVE RUN" : unlocked ? "ENTER FLOOR" : "LOCKED";
        boolean disabled = !unlocked;
        int buttonX = x + w - buttonW;
        drawButton(graphics, buttonX, y + 1, buttonW, 18, label,
                current, disabled);
        hotspot(buttonX, y + 1, buttonW, 18, () -> {
            if (!unlocked) {
                showToast("Floor " + floor.number() + " is locked");
                return;
            }
            if (current) {
                page = Page.ACTIVE;
                DungeonUiApi.emit(DungeonUiApi.ActionType.OPEN_ACTIVE_RUN, floor.number(), "");
            } else {
                showToast("Generated-floor entry is not exposed by the runtime yet");
            }
        }, label);
    }

    private void drawFloorBrowser(GuiGraphics graphics) {
        final int x = 10;
        final int y = 267;
        final int w = VIRTUAL_W - 20;
        final int h = 266;
        final int headerH = 38;
        final int tableH = 19;
        final int footerH = 34;
        final int rowH = 29;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF23578E);

        drawScaled(graphics, "FLOOR DEPTH & MODIFIERS", x + 14, y + 8, WHITE, 1.08f, false);
        drawFit(graphics, "Choose a floor depth. Modifiers and rewards scale with difficulty.",
                x + 14, y + 23, 470, MUTED, 1.0f, 0.78f, false);

        int rangeX = x + w - 110;
        int filterX = rangeX - 94;
        int searchX = filterX - 188;
        drawTextField(graphics, searchX, y + 8, 180, 20, floorSearch,
                "Search floor...", focus == Focus.FLOOR_SEARCH);
        hotspot(searchX, y + 8, 180, 20, () -> focus = Focus.FLOOR_SEARCH,
                "Search by floor, theme, modifier, or reward");

        String filterLabel = switch (floorFilter) {
            case ALL -> "FILTER: ALL";
            case UNLOCKED -> "UNLOCKED";
            case BOSS -> "BOSS FLOORS";
        };
        drawButton(graphics, filterX, y + 8, 86, 20, filterLabel, floorFilter != Filter.ALL, false);
        hotspot(filterX, y + 8, 86, 20, this::cycleFilter, "Cycle floor filters");

        int start = rangeIndex * 10 + 1;
        int end = Math.min(99, start + 9);
        drawButton(graphics, rangeX, y + 8, 100, 20, start + " - " + end + "  v", false, false);
        hotspot(rangeX, y + 8, 100, 20, this::nextRange, "Next ten-floor range");

        int headY = y + headerH;
        graphics.hLine(x + 1, x + w - 1, headY, LINE_FAINT);
        int rowX = x + 10;
        drawColumnHeader(graphics, "FLOOR", rowX, headY + 5, FLOOR_COL_W);
        drawColumnHeader(graphics, "DEPTH", rowX + FLOOR_COL_W, headY + 5, DEPTH_COL_W);
        drawColumnHeader(graphics, "MODIFIERS (Preview)", rowX + FLOOR_COL_W + DEPTH_COL_W,
                headY + 5, MODIFIER_COL_W);
        drawColumnHeader(graphics, "REWARDS (Preview)",
                rowX + FLOOR_COL_W + DEPTH_COL_W + MODIFIER_COL_W,
                headY + 5, REWARD_COL_W);
        drawColumnHeader(graphics, "RECOMMENDED EP",
                rowX + FLOOR_COL_W + DEPTH_COL_W + MODIFIER_COL_W + REWARD_COL_W,
                headY + 5, EP_COL_W);
        drawColumnHeader(graphics, "STATUS",
                rowX + FLOOR_COL_W + DEPTH_COL_W + MODIFIER_COL_W + REWARD_COL_W + EP_COL_W,
                headY + 5, STATUS_COL_W);

        List<Floor> floors = browserFloors();
        int maxScroll = Math.max(0, floors.size() - ROWS_VISIBLE);
        rowScroll = clamp(rowScroll, 0, maxScroll);
        int listY = headY + tableH;
        for (int row = 0; row < ROWS_VISIBLE; row++) {
            int index = rowScroll + row;
            if (index >= floors.size()) break;
            drawFloorRow(graphics, floors.get(index), rowX, listY + row * rowH,
                    FLOOR_COL_W + DEPTH_COL_W + MODIFIER_COL_W + REWARD_COL_W + EP_COL_W + STATUS_COL_W,
                    rowH - 1);
        }

        drawListScrollbar(graphics, x + w - 6, listY + 2, 4,
                ROWS_VISIBLE * rowH - 4, floors.size(), ROWS_VISIBLE, rowScroll);
        drawPagination(graphics, x, y + h - footerH, w);
    }

    private List<Floor> browserFloors() {
        Filter dataFilter = floorFilter == Filter.UNLOCKED ? Filter.ALL : floorFilter;
        List<Floor> filtered = new ArrayList<>(DungeonData.findFloors(floorSearch, dataFilter));
        filtered.removeIf(floor -> !runtimeUnlocked(floor));
        if (!floorSearch.isBlank()) return filtered;
        int start = rangeIndex * FLOORS_PER_RANGE + 1;
        int end = Math.min(99, start + FLOORS_PER_RANGE - 1);
        List<Floor> ranged = new ArrayList<>();
        for (Floor floor : filtered) if (floor.number() >= start && floor.number() <= end) ranged.add(floor);
        return ranged;
    }

    private void drawFloorRow(GuiGraphics graphics, Floor floor, int x, int y, int w, int h) {
        boolean selected = floor.number() == selectedFloor;
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        boolean planned = snapshot != null
                && floor.number() == snapshot.progress().selectedFloor();
        boolean unlocked = runtimeUnlocked(floor);
        int fill = selected ? 0xC71B0D39 : 0xB7040E1D;
        int border = selected ? VIOLET : 0xFF173556;
        fillPanel(graphics, x, y, w, h, fill, border);
        if (selected) graphics.fill(x, y, x + 2, y + h, BLUE);

        int floorX = x;
        int depthX = floorX + FLOOR_COL_W;
        int modifierX = depthX + DEPTH_COL_W;
        int rewardX = modifierX + MODIFIER_COL_W;
        int epX = rewardX + REWARD_COL_W;
        int statusX = epX + EP_COL_W;

        fillOctagon(graphics, floorX + 10, y + 2, 27, h - 4, 0xFF071121,
                unlocked ? 0xFF506A92 : 0xFF39475F);
        drawCenteredFit(graphics, String.valueOf(floor.number()), floorX + 23, y + 7,
                24, WHITE, 1.08f, 0.78f);

        drawFit(graphics, "STRATUM I", depthX + 7, y + 6,
                DEPTH_COL_W - 14, VIOLET, 1.0f, 0.72f, false);
        drawFit(graphics, "Depth " + floor.number(), depthX + 7, y + 17,
                DEPTH_COL_W - 14, TEXT, 1.0f, 0.72f, false);

        List<Modifier> preview = floor.previewModifiers();
        int iconSize = 21;
        int iconGap = 5;
        int modStart = modifierX + 9;
        int visible = Math.min(4, preview.size());
        for (int i = 0; i < visible; i++) {
            Modifier modifier = preview.get(i);
            int ix = modStart + i * (iconSize + iconGap);
            blit(graphics, modifier.texture(), ix, y + 4, iconSize, iconSize, 48, 48);
            hotspot(ix, y + 4, iconSize, iconSize,
                    () -> openModifierModal(modifier.name(), List.of(modifier)),
                    modifier.name() + "\n" + modifier.shortDescription());
        }
        int hidden = Math.max(0, preview.size() - visible);
        if (hidden > 0) {
            int hx = modStart + visible * (iconSize + iconGap);
            fillPanel(graphics, hx, y + 5, 25, 19, 0xFF06101F, 0xFF203B5E);
            drawCenteredFit(graphics, "+" + hidden, hx + 12, y + 11, 21,
                    TEXT, 1.0f, 0.72f);
            List<Modifier> hiddenModifiers = new ArrayList<>(preview.subList(visible, preview.size()));
            hotspot(hx, y + 5, 25, 19,
                    () -> openModifierModal("FLOOR " + floor.number() + " - MORE MODIFIERS", hiddenModifiers),
                    hidden + " more preview modifiers");
        }

        int rewardStart = rewardX + 9;
        List<Reward> floorRewards = connectedRewardPreview(floor);
        int rewardVisible = Math.min(4, floorRewards.size());
        for (int i = 0; i < rewardVisible; i++) {
            Reward reward = floorRewards.get(i);
            int ix = rewardStart + i * (iconSize + iconGap);
            drawRewardIcon(graphics, reward, ix, y + 4, iconSize);
            hotspot(ix, y + 4, iconSize, iconSize, () -> {}, rewardPreviewTooltip(reward));
        }
        if (floorRewards.size() > rewardVisible) {
            int moreX = rewardStart + rewardVisible * (iconSize + iconGap);
            drawCentered(graphics, "...", moreX + 8, y + 10, MUTED);
            hotspot(moreX, y + 4, 20, iconSize, () -> {},
                    hiddenRewardsTooltip(floorRewards, rewardVisible));
        }

        drawCenteredFit(graphics, format(floor.recommendedEp()), epX + EP_COL_W / 2,
                y + 10, EP_COL_W - 12, GREEN, 1.0f, 0.72f);

        String status = planned ? "Selected" : unlocked ? "Select  >>" : "Locked";
        boolean disabled = !unlocked || planned;
        drawButton(graphics, statusX + 6, y + 5, STATUS_COL_W - 12, h - 10,
                status, unlocked && !planned, disabled);
        if (unlocked && !planned) {
            hotspot(x, y, w, h, () -> selectFloor(floor.number()),
                    floor.theme().name() + "\nFloor " + floor.number());
        }
    }

    private void drawPagination(GuiGraphics graphics, int x, int y, int w) {
        graphics.hLine(x + 1, x + w - 1, y, LINE_FAINT);
        int maxRange = maxUnlockedRange();
        int bx = x + 268;
        int buttonY = y + 7;
        drawButton(graphics, bx, buttonY, 24, 20, "<<", false, rangeIndex == 0);
        hotspot(bx, buttonY, 24, 20, () -> setRange(0), "First range");
        bx += 28;
        drawButton(graphics, bx, buttonY, 24, 20, "<", false, rangeIndex == 0);
        hotspot(bx, buttonY, 24, 20, () -> setRange(rangeIndex - 1), "Previous range");
        bx += 28;

        List<Integer> ranges = visibleUnlockedRanges(maxRange);
        for (int range : ranges) {
            int start = range * 10 + 1;
            int end = Math.min(99, start + 9);
            int bw = 53;
            drawButton(graphics, bx, buttonY, bw, 20, start + " - " + end,
                    range == rangeIndex, false);
            final int target = range;
            hotspot(bx, buttonY, bw, 20, () -> setRange(target),
                    "Floors " + start + " to " + end);
            bx += bw + 4;
        }
        drawButton(graphics, bx, buttonY, 24, 20, ">", false, rangeIndex == maxRange);
        hotspot(bx, buttonY, 24, 20, () -> setRange(rangeIndex + 1), "Next range");
        bx += 28;
        drawButton(graphics, bx, buttonY, 24, 20, ">>", false, rangeIndex == maxRange);
        hotspot(bx, buttonY, 24, 20, () -> setRange(maxRange), "Last unlocked range");

        int goX = x + w - 177;
        draw(graphics, "Go to Floor", goX, y + 13, MUTED, false);
        drawTextField(graphics, goX + 61, buttonY, 42, 20, goFloor,
                String.valueOf(highestUnlockedFloor()), focus == Focus.GO_FLOOR);
        hotspot(goX + 61, buttonY, 42, 20, () -> focus = Focus.GO_FLOOR,
                "Enter an unlocked floor");
        drawButton(graphics, goX + 108, buttonY, 59, 20, "GO", true, false);
        hotspot(goX + 108, buttonY, 59, 20, this::goToFloor, "Jump to floor");
    }

    private void drawActiveRunPage(GuiGraphics graphics) {
        int x = CONTENT_X, y = 42, w = CONTENT_RIGHT - CONTENT_X, h = 490;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF245A91);
        drawScaled(graphics, "ACTIVE RUN", x + 16, y + 14, WHITE, 1.25f, false);
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null) {
            draw(graphics, "REQUESTING AUTHORITATIVE RUNTIME SNAPSHOT...", x + 16, y + 34, MUTED, false);
            return;
        }
        DungeonUiSnapshotPayload.RunSummary run = snapshot.run();
        if (!run.active()) {
            draw(graphics, "NO ACTIVE DUNGEON RUN", x + 16, y + 34, MUTED, false);
            fillPanel(graphics, x + 80, y + 100, w - 160, 120, PANEL_DARK, LINE);
            drawCenteredScaled(graphics, "RUNTIME IDLE", x + w / 2, y + 128, BLUE, 1.25f);
            drawCenteredFit(graphics,
                    "Start a dungeon through the runtime. This page will update from server state.",
                    x + w / 2, y + 165, w - 220, TEXT, 1.0f, 0.72f);
            drawButton(graphics, x + 28, y + h - 52, 110, 26, "BACK TO FLOORS", false, false);
            hotspot(x + 28, y + h - 52, 110, 26, () -> page = Page.FLOOR, "Back to Floor Select");
            return;
        }
        Floor liveFloor = DungeonData.floor(clamp(run.floor(), 1, 99));
        draw(graphics, "FLOOR " + run.floor() + " - " + liveFloor.theme().name().toUpperCase(Locale.ROOT),
                x + 16, y + 34, VIOLET, false);
        drawRightFit(graphics, "MODE: " + run.mode(), x + w - 18, y + 20,
                220, MUTED, 1.0f, 0.70f, false);

        int mapX = x + 28, mapY = y + 80;
        drawRouteLine(graphics, mapX + 40, mapY + 40, mapX + 150, mapY + 105, BLUE);
        drawRouteLine(graphics, mapX + 150, mapY + 105, mapX + 280, mapY + 65, GREEN);
        drawRouteLine(graphics, mapX + 280, mapY + 65, mapX + 405, mapY + 120, VIOLET);
        drawRouteLine(graphics, mapX + 405, mapY + 120, mapX + 520, mapY + 70, RED);
        int completed = run.completedRooms();
        drawRouteNode(graphics, mapX + 40, mapY + 40, "START", BLUE, true);
        drawRouteNode(graphics, mapX + 150, mapY + 105, "ROOM 1", GREEN, completed >= 1);
        drawRouteNode(graphics, mapX + 280, mapY + 65, "ROOM 2", GREEN, completed >= 2);
        drawRouteNode(graphics, mapX + 405, mapY + 120, "ROOM 3", VIOLET, completed >= 3);
        drawRouteNode(graphics, mapX + 520, mapY + 70, "END", RED, run.completed());

        fillPanel(graphics, x + 28, y + 250, w - 56, 145, PANEL_DARK, LINE);
        draw(graphics, "RUN SUMMARY", x + 42, y + 275, TEXT, false);
        String target = run.targetRooms() > 0 ? String.valueOf(run.targetRooms()) : "?";
        drawStat(graphics, x + 42, y + 296, "ROOM PROGRESS", completed + " / " + target, BLUE);
        drawStat(graphics, x + 170, y + 296, "MODIFIERS", String.valueOf(run.modifierIds().size()), WHITE);
        drawStat(graphics, x + 298, y + 296, "RUN STATUS",
                run.failed() ? "FAILED" : run.completed() ? "COMPLETE" : "ACTIVE",
                run.failed() ? RED : run.completed() ? GREEN : BLUE);
        drawStat(graphics, x + 426, y + 296, "FLOOR SEED", String.valueOf(run.seed()), VIOLET);
        drawStat(graphics, x + 42, y + 342, "EP GROWTH",
                integrationLabel(snapshot.integrations().epGrowthStatus()), BLUE);
        drawStat(graphics, x + 220, y + 342, "CHECKPOINT",
                integrationLabel(snapshot.integrations().checkpointStatus()), VIOLET);
        drawStat(graphics, x + 426, y + 342, "RECORDING CRYSTAL",
                integrationLabel(snapshot.integrations().recordingCrystalStatus()), GOLD);

        drawButton(graphics, x + 28, y + h - 52, 110, 26, "BACK TO FLOORS", false, false);
        hotspot(x + 28, y + h - 52, 110, 26, () -> page = Page.FLOOR, "Back to Floor Select");
        drawButton(graphics, x + w - 138, y + h - 52, 110, 26, "REFRESH", true, false);
        hotspot(x + w - 138, y + h - 52, 110, 26, DungeonRuntimeBridge::refresh,
                "Refresh authoritative runtime state");
    }

    private void drawSelectedRewardPool(
            GuiGraphics graphics,
            List<Reward> rewards,
            int x,
            int y,
            int width,
            int height
    ) {
        final int maxVisible = 4;
        final int gap = 2;
        final int moreWidth = 20;
        final int visibleRewards = Math.min(rewards.size(), maxVisible);
        final boolean overflow = rewards.size() > visibleRewards;
        final int rewardWidth = width - (overflow ? moreWidth + gap : 0);
        final int cellWidth = visibleRewards == 0
                ? rewardWidth
                : (rewardWidth - gap * (visibleRewards - 1)) / visibleRewards;

        for (int i = 0; i < visibleRewards; i++) {
            Reward reward = rewards.get(i);
            int cellX = x + i * (cellWidth + gap);
            drawSelectedRewardCell(graphics, reward, cellX, y, cellWidth, height);
        }

        if (overflow) {
            int cellX = x + width - moreWidth;
            int hidden = rewards.size() - visibleRewards;
            fillPanel(graphics, cellX, y, moreWidth, height, 0xB5030D1B, 0xFF173556);
            drawCenteredFit(graphics, "...", cellX + moreWidth / 2, y + 19,
                    moreWidth - 4, VIOLET, 0.88f, 0.62f);
            hotspot(cellX, y, moreWidth, height, () -> {},
                    hiddenRewardsTooltip(rewards, visibleRewards));
        }
    }

    private void drawSelectedRewardCell(
            GuiGraphics graphics,
            Reward reward,
            int x,
            int y,
            int width,
            int height
    ) {
        fillPanel(graphics, x, y, width, height, 0xB5030D1B, 0xFF173556);
        String rarity = rewardRarity(reward);
        int iconSize = Math.min(18, height - 25);
        int iconX = x + (width - iconSize) / 2;
        drawRewardIcon(graphics, reward, iconX, y + 6, iconSize);
        drawCenteredFit(graphics, rarity, x + width / 2, y + 31,
                width - 6, rewardRarityColor(reward), 0.78f, 0.58f);
        hotspot(x, y, width, height,
                () -> showToast(reward.type().name()),
                rewardPreviewTooltip(reward));
    }

    private String rewardRarity(Reward reward) {
        CrystalTierVisual crystal = CrystalTierVisual.fromRewardId(reward.type().id());
        if (crystal != null) return crystal.tierLabel();
        return switch (reward.type().id()) {
            case "coin-bronze", "magic-crystal-low", "recovery-supplies", "magicule-orb" -> "COMMON";
            case "magic-crystal-medium" -> "UNCOMMON";
            case "coin-silver", "magic-crystal-high", "equipment-cache", "reward-cache",
                    "elemental-core", "recording-crystal" -> "RARE";
            case "coin-gold", "guardian-coffer" -> "EPIC";
            case "coin-stellar-gold", "max-ep-capacity", "skill-ticket" -> "LEGENDARY";
            default -> "COMMON";
        };
    }

    private int rewardRarityColor(Reward reward) {
        CrystalTierVisual crystal = CrystalTierVisual.fromRewardId(reward.type().id());
        if (crystal != null) return crystal.color();
        return switch (rewardRarity(reward)) {
            case "UNCOMMON" -> BLUE;
            case "RARE" -> VIOLET;
            case "EPIC" -> GOLD;
            case "LEGENDARY" -> GOLD;
            default -> WHITE;
        };
    }

    private String rewardPreviewTooltip(Reward reward) {
        if (CrystalTierVisual.fromRewardId(reward.type().id()) != null) return reward.tooltip();
        return reward.type().name() + "\n" + rewardRarity(reward);
    }

    private void drawRouteLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / Math.max(1, steps);
            int y = y1 + (y2 - y1) * i / Math.max(1, steps);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void drawRouteNode(GuiGraphics graphics, int x, int y, String label, int color, boolean done) {
        fillHex(graphics, x - 16, y - 16, 32, 32, 0xFF071225, color);
        drawCentered(graphics, done ? "OK" : "--", x, y - 4, done ? GREEN : color);
        drawCentered(graphics, label, x, y + 20, TEXT);
    }

    private void drawStat(GuiGraphics graphics, int x, int y, String label, String value, int color) {
        draw(graphics, label, x, y, MUTED, false);
        drawScaled(graphics, value, x, y + 15, color, 1.25f, false);
    }

    private void drawRewardsPage(GuiGraphics graphics) {
        int x = CONTENT_X, y = 42, w = CONTENT_RIGHT - CONTENT_X, h = 490;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF245A91);
        drawScaled(graphics, "DUNGEON MAILBOX", x + 16, y + 14, WHITE, 1.25f, false);
        draw(graphics, "Authoritative runtime packages persist through disconnects and full inventories.",
                x + 16, y + 34, MUTED, false);
        drawCrystalTierGallery(graphics, x + 16, y + h - 60, w - 32, 48);
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null) {
            drawCenteredScaled(graphics, "REQUESTING RUNTIME MAILBOX...", x + w / 2, y + 115,
                    MUTED, 1.15f);
            return;
        }
        List<DungeonUiSnapshotPayload.MailboxPackage> packages = snapshot.mailbox();
        long unclaimed = snapshot.unclaimedMailboxCount();
        drawRightFit(graphics, unclaimed + " UNCLAIMED", x + w - 120, y + 17,
                150, GOLD, 1.0f, 0.68f, false);
        drawButton(graphics, x + w - 226, y + 39, 102, 18, "LOOT SUMMARY", true, false);
        hotspot(x + w - 226, y + 39, 102, 18,
                DungeonRuntimeBridge::openLatestLootSummary,
                "Open the latest completed-floor run summary");
        drawButton(graphics, x + w - 112, y + 39, 94, 18, "CLAIM ALL", unclaimed > 0, unclaimed == 0);
        hotspot(x + w - 112, y + 39, 94, 18, () -> {
            if (unclaimed > 0) DungeonRuntimeBridge.claimAll();
        }, "Server validates and claims every package that fits");

        if (packages.isEmpty()) {
            int emptyY = y + 92;
            fillPanel(graphics, x + 80, emptyY, w - 160, 130, PANEL_DARK, LINE);
            drawCenteredScaled(graphics, "NO REGISTERED HAUL", x + w / 2, emptyY + 28,
                    MUTED, 1.15f);
            drawCenteredFit(graphics,
                    "Complete a dungeon floor to register its recovered materials and treasure.",
                    x + w / 2, emptyY + 59, w - 220, TEXT, 1.0f, 0.72f);
            drawCenteredFit(graphics,
                    "First-clear flags are committed only after the package is safely persisted.",
                    x + w / 2, emptyY + 80, w - 220, DIM, 1.0f, 0.68f);
            return;
        }

        int visible = Math.min(5, packages.size());
        for (int i = 0; i < visible; i++) {
            DungeonUiSnapshotPayload.MailboxPackage rewardPackage = packages.get(i);
            int ry = y + 62 + i * 65;
            fillPanel(graphics, x + 16, ry, w - 32, 56,
                    !rewardPackage.claimed() ? 0xB51A0C34 : PANEL_DARK,
                    !rewardPackage.claimed() ? VIOLET : LINE);

            draw(graphics, "FLOOR " + rewardPackage.floor() + " - " + rewardPackage.sourceType(),
                    x + 30, ry + 8, WHITE, false);
            String rewardLine = rewardPackage.rewardTier() + "  |  " + rewardPackage.summary();
            if (rewardPackage.maxEpAmount() > 0) {
                rewardLine += "  |  +" + rewardPackage.maxEpAmount() + " MAX EP";
            }
            drawFit(graphics, rewardLine, x + 30, ry + 22, w - 190,
                    rewardPackage.maxEpAmount() > 0 ? GOLD : MUTED, 1.0f, 0.66f, false);

            String buttonLabel = "PENDING";
            drawButton(graphics, x + w - 120, ry + 17, 88, 22,
                    buttonLabel, false, true);
        }
    }

    private List<Reward> connectedRewardPreview(Floor floor) {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null) return List.of();
        boolean firstClearAvailable = !snapshot.progress().completedFloors().contains(floor.number());
        return DungeonData.rewardsForFloor(floor, firstClearAvailable);
    }

    private void drawRewardIcon(GuiGraphics graphics, Reward reward, int x, int y, int size) {
        CrystalTierVisual crystal = CrystalTierVisual.fromRewardId(reward.type().id());
        if (crystal == null) {
            blit(graphics, reward.type().texture(), x, y, size, size, 48, 48);
            return;
        }
        drawAnimatedCrystal(graphics, crystal, x, y, size);
    }

    private void drawAnimatedCrystal(
            GuiGraphics graphics,
            CrystalTierVisual crystal,
            int x,
            int y,
            int size
    ) {
        int frame = (int) ((System.currentTimeMillis() / 140L + crystal.tier()) % 8L);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                DungeonData.MOD_ID, "textures/gui/rewards/" + crystal.rewardId() + ".png");
        blitRegion(graphics, texture, x, y, size, size,
                0, frame * 48, 48, 48, 48, 384);
    }

    private void drawCrystalTierGallery(GuiGraphics graphics, int x, int y, int width, int height) {
        fillPanel(graphics, x, y, width, height, PANEL_DARK, LINE);
        drawFit(graphics, "CRYSTAL VISUAL PREVIEW - SYSTEM ACTIVATION DEFERRED",
                x + 8, y + 5, width - 16, MUTED, 0.78f, 0.56f, false);
        CrystalTierVisual[] tiers = CrystalTierVisual.values();
        int iconSize = 25;
        int spacing = Math.max(iconSize + 8, (width - 18) / tiers.length);
        int startX = x + (width - spacing * tiers.length) / 2;
        for (int index = 0; index < tiers.length; index++) {
            CrystalTierVisual tier = tiers[index];
            int iconX = startX + index * spacing + (spacing - iconSize) / 2;
            drawAnimatedCrystal(graphics, tier, iconX, y + 17, iconSize);
            hotspot(iconX - 3, y + 15, iconSize + 6, iconSize + 6, () -> {},
                    tier.displayName() + "\n" + tier.plannedBand()
                            + "\nVisual approval preview; no tiered drop logic is active yet.");
        }
    }

    private static String integrationLabel(String status) {
        if (status == null || status.isBlank()) return "UNAVAILABLE";
        if (status.startsWith("UNAVAILABLE")) return "UNAVAILABLE";
        if (status.equals("NOT_IMPLEMENTED")) return "WIP";
        return status.replace('_', ' ');
    }

    private void drawRankingsPage(GuiGraphics graphics) {
        int x = CONTENT_X, y = 42, w = CONTENT_RIGHT - CONTENT_X, h = 490;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF245A91);
        drawScaled(graphics, "LOCAL FLOOR RECORDS", x + 16, y + 14, WHITE, 1.25f, false);
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null) {
            draw(graphics, "REQUESTING AUTHORITATIVE RUNTIME SNAPSHOT...", x + 16, y + 34, MUTED, false);
            return;
        }
        int floor = snapshot.progress().selectedFloor();
        draw(graphics, "Floor " + floor + " - this save/server only", x + 16, y + 34, MUTED, false);
        List<DungeonUiSnapshotPayload.LocalFloorRecord> records = snapshot.localFloorRecords();
        if (records.isEmpty()) {
            fillPanel(graphics, x + 80, y + 100, w - 160, 120, PANEL_DARK, LINE);
            drawCenteredScaled(graphics, "NO LOCAL RECORD YET", x + w / 2, y + 130, MUTED, 1.15f);
            drawCenteredFit(graphics,
                    "Complete this floor to register its first measured clear time.",
                    x + w / 2, y + 167, w - 220, TEXT, 1.0f, 0.72f);
            return;
        }
        int visible = Math.min(7, records.size());
        for (int i = 0; i < visible; i++) {
            DungeonUiSnapshotPayload.LocalFloorRecord record = records.get(i);
            int ry = y + 70 + i * 55;
            boolean currentPlayer = minecraft.player != null
                    && minecraft.player.getUUID().toString().equals(record.playerId());
            fillPanel(graphics, x + 18, ry, w - 36, 45,
                    currentPlayer ? 0xA51B0C39 : PANEL_DARK,
                    currentPlayer ? VIOLET : LINE);
            fillDiamond(graphics, x + 42, ry + 22, 16, i < 3 ? GOLD : BLUE, 0xFF071225);
            drawCentered(graphics, String.valueOf(i + 1), x + 42, ry + 18, WHITE);
            draw(graphics, record.playerName().isBlank() ? "Unknown Player" : record.playerName(),
                    x + 74, ry + 11, WHITE, false);
            draw(graphics, "FLOOR " + record.floor() + "  |  RANK " + record.clearRank()
                            + " (TARGETS PENDING)",
                    x + 74, ry + 26, MUTED, false);
            draw(graphics, formatClearTime(record.clearTicks()), x + w - 115, ry + 17,
                    currentPlayer ? VIOLET : GREEN, false);
        }
    }

    private void drawDungeonLogPage(GuiGraphics graphics) {
        int x = CONTENT_X, y = 42, w = CONTENT_RIGHT - CONTENT_X, h = 490;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF245A91);
        drawScaled(graphics, "DUNGEON LOG", x + 16, y + 14, WHITE, 1.25f, false);
        draw(graphics, "Latest authoritative completed-run summary", x + 16, y + 34, MUTED, false);
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null || !snapshot.latestCompletion().available()) {
            drawCenteredScaled(graphics, "NO COMPLETED RUN SUMMARY", x + w / 2, y + 120,
                    MUTED, 1.15f);
            return;
        }
        DungeonUiSnapshotPayload.CompletionSummary summary = snapshot.latestCompletion();
        DungeonUiSnapshotPayload.CompletionContext context = summary.context();
        DungeonUiSnapshotPayload.CompletionPerformance performance = summary.performance();
        DungeonUiSnapshotPayload.CompletionRewards rewards = summary.rewards();
        String epLine = "EP telemetry: " + integrationLabel(performance.epStatus());
        if ("AVAILABLE".equals(performance.epStatus())) {
            epLine += "  |  +" + Math.round(performance.epGain()) + " EP";
        }
        String[] entries = {
                "Floor " + context.floor() + " completed - " + performance.result(),
                "Clear time: " + ("MEASURED".equals(performance.timingStatus())
                        ? formatClearTime(performance.clearTicks()) : "Unavailable"),
                "Layout: " + context.mode() + "  |  Rooms: " + context.roomCount(),
                context.partySize() > 0
                        ? "Party: " + context.partySize() + "  |  " + context.partyComposition()
                        : "Party telemetry: unavailable",
                "Rank " + performance.clearRank() + " - target boundaries deferred",
                "Currency: " + rewards.totalCurrencyBronze() + " Bronze-equivalent",
                "Drops: " + rewards.commonDrops() + " C / " + rewards.uncommonDrops()
                        + " U / " + rewards.rareDrops() + " R / " + rewards.epicDrops() + " E"
                        + "  |  Crystals: " + rewards.crystalCount(),
                epLine
        };
        for (int i = 0; i < entries.length; i++) {
            int ry = y + 65 + i * 42;
            graphics.fill(x + 18, ry, x + w - 18, ry + 35, i % 2 == 0 ? 0xA5030E1E : 0x78040B18);
            graphics.fill(x + 18, ry, x + 21, ry + 35, i < 5 ? BLUE : VIOLET);
            draw(graphics, String.format(Locale.ROOT, "%02d", i + 1), x + 31, ry + 8, DIM, false);
            draw(graphics, entries[i], x + 82, ry + 8, TEXT, false);
            draw(graphics, i == 0 ? "COMPLETED" : "INFO", x + w - 90, ry + 8,
                    i == 0 ? GREEN : VIOLET, false);
        }
    }

    private static String formatClearTime(long clearTicks) {
        long totalHundredths = Math.max(0L, clearTicks) * 5L;
        long minutes = totalHundredths / 6_000L;
        long seconds = totalHundredths / 100L % 60L;
        long hundredths = totalHundredths % 100L;
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths);
    }

    private void drawModifierGuidePage(GuiGraphics graphics) {
        int x = CONTENT_X, y = 42, w = CONTENT_RIGHT - CONTENT_X, h = 490;
        fillPanel(graphics, x, y, w, h, PANEL, 0xFF245A91);
        drawScaled(graphics, "MODIFIER GUIDE", x + 16, y + 12, WHITE, 1.28f, false);
        drawFit(graphics, "Learn every modifier, its effects, and the best counters.",
                x + 16, y + 32, w - 32, MUTED, 1.0f, 0.78f, false);

        drawTextField(graphics, x + 16, y + 48, 190, 21, guideSearch,
                "Search modifiers...", focus == Focus.GUIDE_SEARCH);
        hotspot(x + 16, y + 48, 190, 21, () -> focus = Focus.GUIDE_SEARCH,
                "Search modifier guide");

        Kind[] filters = {null, Kind.PLAYER, Kind.ENEMY, Kind.ENVIRONMENT, Kind.REWARD, Kind.BOSS};
        String[] labels = {"ALL", "PLAYER", "ENEMY", "ENV", "REWARD", "BOSS"};
        int[] widths = {42, 60, 58, 50, 64, 52};
        int fx = x + 214;
        for (int i = 0; i < filters.length; i++) {
            int bw = widths[i];
            boolean active = guideFilter == filters[i];
            drawButton(graphics, fx, y + 48, bw, 21, labels[i], active, false);
            final Kind filter = filters[i];
            hotspot(fx, y + 48, bw, 21, () -> {
                guideFilter = filter;
                guideScroll = 0;
            }, "Filter: " + labels[i]);
            fx += bw + 5;
        }

        List<Modifier> guide = guideModifiers();
        int gridX = x + 16;
        int gridY = y + 82;
        int detailW = 250;
        int detailGap = 12;
        int gridW = w - 32 - detailW - detailGap;
        int cols = 4;
        int gap = 5;
        int scrollbarW = 5;
        int cardW = (gridW - scrollbarW - gap * (cols - 1) - 5) / cols;
        int cardH = 94;
        int rowsVisible = 4;
        int itemsPerView = cols * rowsVisible;
        guideScroll = clamp(guideScroll, 0, Math.max(0, guide.size() - itemsPerView));
        for (int i = 0; i < itemsPerView; i++) {
            int index = guideScroll + i;
            if (index >= guide.size()) break;
            Modifier modifier = guide.get(index);
            int col = i % cols;
            int row = i / cols;
            int cx = gridX + col * (cardW + gap);
            int cy = gridY + row * (cardH + gap);
            drawModifierCard(graphics, modifier, cx, cy, cardW, cardH,
                    modifier == selectedGuideModifier);
        }
        drawListScrollbar(graphics, gridX + gridW - scrollbarW, gridY,
                scrollbarW, rowsVisible * (cardH + gap) - gap,
                guide.size(), itemsPerView, guideScroll);

        int detailX = gridX + gridW + detailGap;
        drawModifierDetails(graphics, selectedGuideModifier, detailX, gridY,
                detailW, rowsVisible * (cardH + gap) - gap);
    }

    private List<Modifier> guideModifiers() {
        String query = guideSearch.trim().toLowerCase(Locale.ROOT);
        List<Modifier> result = new ArrayList<>();
        for (Modifier modifier : DungeonData.modifiers()) {
            if (guideFilter != null && modifier.kind() != guideFilter) continue;
            if (!query.isEmpty() && !(modifier.name() + " " + modifier.shortDescription() + " " + modifier.description())
                    .toLowerCase(Locale.ROOT).contains(query)) continue;
            result.add(modifier);
        }
        result.sort(Comparator.comparing((Modifier m) -> m.kind().ordinal()).thenComparing(Modifier::minFloor).thenComparing(Modifier::name));
        return result;
    }

    private void drawModifierCard(GuiGraphics graphics, Modifier modifier,
                                  int x, int y, int w, int h, boolean selected) {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        int highestUnlocked = snapshot == null ? 1 : snapshot.progress().highestUnlockedFloor();
        boolean floorReached = modifier.minFloor() <= highestUnlocked;
        boolean live = modifier.status().equals("LIVE") || modifier.status().equals("LEGACY");
        int border = selected ? modifier.tone().accent : live && floorReached ? 0xFF1A3B61 : 0xFF263044;
        int fill = selected ? 0xC51A0D34 : 0xDB040E1C;
        fillPanel(graphics, x, y, w, h, fill, border);
        int icon = 31;
        blit(graphics, modifier.texture(), x + w / 2 - icon / 2, y + 7, icon, icon, 48, 48);
        drawCenteredFit(graphics, modifier.name(), x + w / 2, y + 41,
                w - 10, live ? WHITE : DIM, 1.0f, 0.72f);
        List<String> description = wrapLimited(modifier.shortDescription(), w - 12, 2);
        for (int line = 0; line < description.size(); line++) {
            drawCenteredFit(graphics, description.get(line), x + w / 2,
                    y + 54 + line * 10, w - 10, MUTED, 1.0f, 0.70f);
        }
        String footer = !live ? modifier.status()
                : !floorReached ? "LOCKED: FLOOR " + modifier.minFloor()
                : modifier.tier();
        drawCenteredFit(graphics, footer, x + w / 2, y + h - 14,
                w - 10, live && floorReached ? modifier.tone().accent : DIM, 1.0f, 0.68f);
        hotspot(x, y, w, h, () -> {
            selectedGuideModifier = modifier;
            if (!live) showToast(modifier.name() + " is " + modifier.status());
            else if (!floorReached) showToast("Unlocks at Floor " + modifier.minFloor());
        }, modifier.name() + "\n" + modifier.shortDescription() + "\n" + modifier.status());
    }

    private void drawModifierDetails(GuiGraphics graphics, Modifier modifier,
                                     int x, int y, int w, int h) {
        fillPanel(graphics, x, y, w, h, 0xF5030C19, modifier.tone().accent);
        drawTag(graphics, x + 9, y + 9, 112,
                modifier.kind().name() + " MODIFIER", modifier.tone().accent);
        drawRightFit(graphics, "TIER: " + modifier.tier(), x + w - 10, y + 13,
                112, TEXT, 1.0f, 0.70f, false);
        drawRightFit(graphics, modifier.status(), x + w - 10, y + 27,
                112, modifier.status().equals("LIVE") ? GREEN : MUTED, 1.0f, 0.70f, false);

        int icon = 72;
        blit(graphics, modifier.texture(), x + w / 2 - icon / 2, y + 34,
                icon, icon, 48, 48);
        drawCenteredFit(graphics, modifier.name().toUpperCase(Locale.ROOT),
                x + w / 2, y + 112, w - 24,
                modifier.tone().accent, 1.28f, 0.72f);

        int cursorY = y + 137;
        List<String> description = wrapLimited(modifier.description(), w - 28, 4);
        for (String line : description) {
            drawCenteredFit(graphics, line, x + w / 2, cursorY,
                    w - 24, TEXT, 1.0f, 0.72f);
            cursorY += 10;
        }
        cursorY += 5;
        graphics.hLine(x + 9, x + w - 10, cursorY, LINE_FAINT);
        draw(graphics, "EFFECTS", x + 11, cursorY + 9, modifier.tone().accent, false);
        cursorY += 24;
        cursorY = drawBulletList(graphics, modifier.effects(), x + 15, cursorY,
                w - 30, TEXT, 2, y + h - 115);

        cursorY += 4;
        graphics.hLine(x + 9, x + w - 10, cursorY, LINE_FAINT);
        draw(graphics, "COUNTERS", x + 11, cursorY + 9, BLUE, false);
        cursorY += 24;
        drawBulletList(graphics, modifier.counters(), x + 15, cursorY,
                w - 30, MUTED, 2, y + h - 56);

        drawFit(graphics, "Appears from Floor " + modifier.minFloor() + "+",
                x + 12, y + h - 49, w - 24, modifier.tone().accent,
                1.0f, 0.72f, false);
        drawButton(graphics, x + 20, y + h - 31, w - 40, 21,
                "VIEW MATCHING FLOORS", true, false);
        hotspot(x + 20, y + h - 31, w - 40, 21, () -> {
            page = Page.FLOOR;
            floorSearch = modifier.name();
            rowScroll = 0;
            focus = Focus.NONE;
        }, "Show floors containing " + modifier.name());
    }

    private void drawModifierModal(GuiGraphics graphics) {
        hotspot(0, 0, VIRTUAL_W, VIRTUAL_H, () -> modifierModal = null, "");
        graphics.fill(0, 0, VIRTUAL_W, VIRTUAL_H, 0xC9000208);
        int w = 500, h = 370;
        int x = (VIRTUAL_W - w) / 2;
        int y = (VIRTUAL_H - h) / 2;
        fillPanel(graphics, x, y, w, h, 0xFC040C1A, VIOLET);
        drawFit(graphics, modifierModalTitle, x + 18, y + 15, w - 70,
                WHITE, 1.18f, 0.78f, false);
        draw(graphics, "Active modifier details", x + 18, y + 36, MUTED, false);
        drawButton(graphics, x + w - 36, y + 10, 25, 21, "X", false, false);
        hotspot(x + w - 36, y + 10, 25, 21, () -> modifierModal = null, "Close");

        int cardY = y + 58;
        int visible = 5;
        modalScroll = clamp(modalScroll, 0, Math.max(0, modifierModal.size() - visible));
        for (int i = 0; i < visible; i++) {
            int index = modalScroll + i;
            if (index >= modifierModal.size()) break;
            Modifier modifier = modifierModal.get(index);
            int ry = cardY + i * 55;
            fillPanel(graphics, x + 14, ry, w - 28, 49, 0xE8051122, 0xFF1A3B61);
            blit(graphics, modifier.texture(), x + 24, ry + 6, 37, 37, 48, 48);
            drawFit(graphics, modifier.name(), x + 74, ry + 7, 260,
                    modifier.tone().accent, 1.0f, 0.72f, false);
            List<String> lines = wrapLimited(modifier.description(), w - 160, 2);
            for (int line = 0; line < lines.size(); line++) {
                draw(graphics, lines.get(line), x + 74, ry + 21 + line * 10, TEXT, false);
            }
            drawRightFit(graphics, modifier.tier(), x + w - 24, ry + 9, 95,
                    modifier.tone().accent, 1.0f, 0.72f, false);
        }
        drawListScrollbar(graphics, x + w - 10, cardY, 4,
                visible * 55 - 6, modifierModal.size(), visible, modalScroll);
    }

    private void openModifierModal(String title, List<Modifier> modifiers) {
        modifierModalTitle = title;
        modifierModal = new ArrayList<>(modifiers);
        modalScroll = 0;
        focus = Focus.NONE;
    }

    private void drawTooltip(GuiGraphics graphics, String tooltip, int x, int y) {
        String[] raw = tooltip.split("\\n");
        List<String> lines = new ArrayList<>();
        for (String part : raw) lines.addAll(wrap(part, 185));
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        width += 10;
        int height = lines.size() * 10 + 8;
        x = Math.min(x, VIRTUAL_W - width - 4);
        y = Math.min(y, VIRTUAL_H - height - 4);
        fillPanel(graphics, x, y, width, height, 0xF9050C18, 0xFF315B91);
        for (int i = 0; i < lines.size(); i++) draw(graphics, lines.get(i), x + 5, y + 4 + i * 10, TEXT, false);
    }

    private void drawToast(GuiGraphics graphics, String message) {
        int w = Math.max(180, font.width(message) + 30);
        int x = VIRTUAL_W / 2 - w / 2;
        int y = VIRTUAL_H - 44;
        fillPanel(graphics, x, y, w, 26, 0xF7061325, BLUE);
        drawCentered(graphics, message, VIRTUAL_W / 2, y + 9, WHITE);
    }

    private void showToast(String message) {
        toastText = message;
        toastUntil = System.currentTimeMillis() + 2_500L;
    }

    private void cycleFilter() {
        floorFilter = switch (floorFilter) {
            case ALL, UNLOCKED -> Filter.BOSS;
            case BOSS -> Filter.ALL;
        };
        rowScroll = 0;
    }

    private void nextRange() {
        int maxRange = maxUnlockedRange();
        setRange((rangeIndex + 1) % (maxRange + 1));
    }

    private void setRange(int range) {
        rangeIndex = clamp(range, 0, maxUnlockedRange());
        rowScroll = 0;
        floorSearch = "";
    }

    private boolean selectFloor(int floor) {
        int highestUnlocked = highestUnlockedFloor();
        if (floor < 1 || floor > highestUnlocked) {
            showToast("Floor " + floor + " is locked");
            return false;
        }
        selectedFloor = floor;
        goFloor = String.valueOf(selectedFloor);
        DungeonRuntimeBridge.selectFloor(selectedFloor);
        DungeonUiApi.emit(DungeonUiApi.ActionType.FLOOR_SELECTED, selectedFloor, DungeonData.floor(selectedFloor).theme().name());
        return true;
    }

    private int highestUnlockedFloor() {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        return snapshot == null ? 1 : clamp(snapshot.progress().highestUnlockedFloor(), 1, 99);
    }

    private int maxUnlockedRange() {
        return (highestUnlockedFloor() - 1) / FLOORS_PER_RANGE;
    }

    private List<Integer> visibleUnlockedRanges(int maxRange) {
        List<Integer> ranges = new ArrayList<>();
        if (maxRange <= 4) {
            for (int range = 0; range <= maxRange; range++) ranges.add(range);
            return ranges;
        }
        for (int candidate : new int[] {0, rangeIndex - 1, rangeIndex, rangeIndex + 1, maxRange}) {
            if (candidate >= 0 && candidate <= maxRange && !ranges.contains(candidate)) ranges.add(candidate);
        }
        ranges.sort(Integer::compareTo);
        return ranges;
    }

    private void goToFloor() {
        try {
            int floor = Integer.parseInt(goFloor);
            if (!selectFloor(floor)) return;
            rangeIndex = (floor - 1) / 10;
            rowScroll = clamp((floor - 1) % 10 - 2, 0, 4);
            floorSearch = "";
            focus = Focus.NONE;
        } catch (NumberFormatException exception) {
            showToast("Enter an unlocked floor number");
        }
    }

    private String compactModifierEffect(Modifier modifier) {
        return switch (modifier.id()) {
            case "swarm" -> "More enemies";
            case "armored" -> "DEF increased";
            case "berserker" -> "Low-HP damage";
            case "regenerative" -> "Health regen";
            case "summoner" -> "Summons adds";
            case "molten" -> "Fire damage";
            case "electrified" -> "Chain lightning";
            case "poisonous" -> "Poison attacks";
            case "explosive" -> "Death bursts";
            case "shadowed" -> "Low visibility";
            case "swift" -> "Faster enemies";
            case "elite-vanguard" -> "Elite each room";
            case "boss-touched" -> "Boss chance";
            case "crystal-rich" -> "More crystals";
            default -> shorten(modifier.shortDescription(), 16);
        };
    }

    private void drawColumnHeader(GuiGraphics graphics, String label, int x, int y, int width) {
        drawFit(graphics, label, x + 4, y, width - 8, MUTED, 1.0f, 0.68f, false);
    }

    private int drawBulletList(GuiGraphics graphics, List<String> values, int x, int y,
                               int maxWidth, int color, int maxLinesPerItem, int maxY) {
        int cursor = y;
        for (String value : values) {
            List<String> lines = wrapLimited("- " + value, maxWidth, maxLinesPerItem);
            for (String line : lines) {
                if (cursor + 9 > maxY) return cursor;
                drawFit(graphics, line, x, cursor, maxWidth, color, 1.0f, 0.72f, false);
                cursor += 10;
            }
            cursor += 2;
        }
        return cursor;
    }

    private List<String> wrapLimited(String text, int maxWidth, int maxLines) {
        List<String> lines = wrap(text, Math.max(8, maxWidth));
        if (lines.size() <= maxLines) return lines;
        List<String> limited = new ArrayList<>(lines.subList(0, maxLines));
        String last = limited.get(maxLines - 1);
        limited.set(maxLines - 1, shortenToWidth(last + "...", Math.max(8, maxWidth)));
        return limited;
    }

    private void drawFit(GuiGraphics graphics, String text, int x, int y, int maxWidth,
                         int color, float maxScale, float minScale, boolean shadow) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return;
        String rendered = text;
        if (font.width(rendered) * minScale > maxWidth) {
            rendered = shortenToWidth(rendered, Math.max(1, (int) (maxWidth / minScale)));
        }
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(rendered)));
        scale = Math.max(minScale, scale);
        drawScaled(graphics, rendered, x, y, color, scale, shadow);
    }

    private void drawCenteredFit(GuiGraphics graphics, String text, int centerX, int y, int maxWidth,
                                 int color, float maxScale, float minScale) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return;
        String rendered = text;
        if (font.width(rendered) * minScale > maxWidth) {
            rendered = shortenToWidth(rendered, Math.max(1, (int) (maxWidth / minScale)));
        }
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(rendered)));
        scale = Math.max(minScale, scale);
        drawCenteredScaled(graphics, rendered, centerX, y, color, scale);
    }

    private void drawRightFit(GuiGraphics graphics, String text, int rightX, int y, int maxWidth,
                              int color, float maxScale, float minScale, boolean shadow) {
        if (text == null || text.isEmpty() || maxWidth <= 0) return;
        String rendered = text;
        if (font.width(rendered) * minScale > maxWidth) {
            rendered = shortenToWidth(rendered, Math.max(1, (int) (maxWidth / minScale)));
        }
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(rendered)));
        scale = Math.max(minScale, scale);
        int x = Math.round(rightX - font.width(rendered) * scale);
        drawScaled(graphics, rendered, x, y, color, scale, shadow);
    }

    private void drawCenteredScaledInBox(GuiGraphics graphics, String text,
                                         int x, int y, int w, int h,
                                         int color, float maxScale, float minScale) {
        String rendered = text;
        float scale = Math.min(maxScale, (w - 6) / (float) Math.max(1, font.width(rendered)));
        scale = Math.max(minScale, scale);
        float lineHeight = 9.0f * scale;
        int textY = Math.round(y + (h - lineHeight) / 2.0f);
        drawCenteredScaled(graphics, rendered, x + w / 2, textY, color, scale);
    }

    private void drawTextField(GuiGraphics graphics, int x, int y, int w, int h, String value, String hint, boolean focused) {
        fillPanel(graphics, x, y, w, h, 0xFF030B17, focused ? BLUE : 0xFF1B3658);
        String text = value.isEmpty() ? hint : value;
        draw(graphics, shortenToWidth(text, w - 12), x + 6, y + 6, value.isEmpty() ? DIM : TEXT, false);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cursor = x + 6 + font.width(shortenToWidth(value, w - 16));
            graphics.fill(cursor, y + 4, cursor + 1, y + h - 4, BLUE);
        }
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            String label, boolean active, boolean disabled) {
        int fill = disabled ? 0xD7060C17 : active ? 0xDE102958 : 0xE5061122;
        int border = disabled ? 0xFF1B2A3F : active ? 0xFF2F8EE8 : 0xFF1B3A60;
        fillPanel(graphics, x, y, w, h, fill, border);
        drawCenteredFit(graphics, label, x + w / 2, y + Math.max(3, (h - 8) / 2),
                w - 8, disabled ? DIM : active ? WHITE : TEXT, 1.0f, 0.62f);
    }

    private void drawTag(GuiGraphics graphics, int x, int y, int w, String text, int color) {
        graphics.fill(x, y, x + w, y + 12, withAlpha(color, 45));
        graphics.fill(x, y, x + 2, y + 12, color);
        graphics.hLine(x, x + w, y + 11, withAlpha(color, 100));
        drawFit(graphics, "* " + text, x + 4, y + 3, w - 8,
                color, 1.0f, 0.68f, false);
    }

    private void drawListScrollbar(GuiGraphics graphics, int x, int y, int w, int h, int total, int visible, int scroll) {
        if (total <= visible || total <= 0) return;
        graphics.fill(x, y, x + w, y + h, 0xFF08182B);
        int thumbH = Math.max(16, h * visible / total);
        int maxScroll = total - visible;
        int thumbY = y + (h - thumbH) * scroll / Math.max(1, maxScroll);
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, 0xFF526FA7);
    }

    private void fillPanel(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        graphics.fill(x + 2, y, x + w - 2, y + h, fill);
        graphics.fill(x, y + 2, x + w, y + h - 2, fill);
        graphics.hLine(x + 2, x + w - 3, y, border);
        graphics.hLine(x + 2, x + w - 3, y + h - 1, border);
        graphics.vLine(x, y + 2, y + h - 3, border);
        graphics.vLine(x + w - 1, y + 2, y + h - 3, border);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, border);
        graphics.fill(x + w - 3, y + 1, x + w - 1, y + 3, border);
        graphics.fill(x + 1, y + h - 3, x + 3, y + h - 1, border);
        graphics.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, border);
    }

    private void fillOctagon(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        int cut = Math.max(2, Math.min(w, h) / 5);
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
            graphics.fill(x + w - i - 1, y + h - cut + i, x + w - i, y + h - cut + i + 1, border);
        }
    }

    private void fillHex(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        int cut = Math.max(2, w / 4);
        for (int row = 0; row < h; row++) {
            int inset;
            if (row < h / 2) inset = cut - cut * row / Math.max(1, h / 2);
            else inset = cut * (row - h / 2) / Math.max(1, h - h / 2);
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, fill);
        }
        graphics.hLine(x + cut, x + w - cut, y, border);
        graphics.hLine(x + cut, x + w - cut, y + h - 1, border);
        graphics.vLine(x, y + h / 2 - 1, y + h / 2 + 1, border);
        graphics.vLine(x + w - 1, y + h / 2 - 1, y + h / 2 + 1, border);
    }

    private void fillDiamond(GuiGraphics graphics, int centerX, int centerY, int radius, int border, int fill) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, fill);
            if (half > 0) {
                graphics.fill(centerX - half, centerY + dy, centerX - half + 1, centerY + dy + 1, border);
                graphics.fill(centerX + half, centerY + dy, centerX + half + 1, centerY + dy + 1, border);
            }
        }
    }

    private void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h, int textureW, int textureH) {
        graphics.blit(texture, x, y, w, h, 0.0f, 0.0f, textureW, textureH, textureW, textureH);
    }

    private void blitRegion(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight
    ) {
        graphics.blit(texture, x, y, width, height,
                (float) sourceX, (float) sourceY,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    private void draw(GuiGraphics graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    private void drawCentered(GuiGraphics graphics, String text, int centerX, int y, int color) {
        graphics.drawCenteredString(font, text, centerX, y, color);
    }

    private void drawScaled(GuiGraphics graphics, String text, int x, int y, int color, float scale, boolean shadow) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private void drawCenteredScaled(GuiGraphics graphics, String text, int centerX, int y, int color, float scale) {
        int width = font.width(text);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX - width * scale / 2.0f, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        for (String paragraph : text.split("\\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString());
        }
        return lines;
    }

    private String shorten(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 2)) + "..";
    }

    private String shortenToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String result = text;
        while (!result.isEmpty() && font.width(result + "...") > maxWidth) result = result.substring(0, result.length() - 1);
        return result + "...";
    }

    private String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private String compactNumber(int value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        return format(value);
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void hotspot(int x, int y, int w, int h, Runnable action, String tooltip) {
        hotspots.add(new Hotspot(x, y, w, h, action, tooltip));
    }

    private Hotspot hoveredAt(int x, int y) {
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot hotspot = hotspots.get(i);
            if (hotspot.contains(x, y)) return hotspot;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = virtualX(mouseX);
        int y = virtualY(mouseY);
        Hotspot hotspot = hoveredAt(x, y);
        if (hotspot != null) {
            hotspot.action.run();
            return true;
        }
        focus = Focus.NONE;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int x = virtualX(mouseX);
        int y = virtualY(mouseY);
        int direction = vertical > 0 ? -1 : vertical < 0 ? 1 : 0;
        if (direction == 0) return false;

        if (modifierModal != null) {
            modalScroll = clamp(modalScroll + direction, 0, Math.max(0, modifierModal.size() - 5));
            return true;
        }
        if (page == Page.FLOOR && x >= 10 && x <= 950 && y >= 324 && y <= 498) {
            rowScroll = clamp(rowScroll + direction, 0, Math.max(0, browserFloors().size() - ROWS_VISIBLE));
            return true;
        }
        if (page == Page.GUIDE && x >= 154 && x <= 672 && y >= 124 && y <= 518) {
            List<Modifier> modifiers = guideModifiers();
            guideScroll = clamp(guideScroll + direction * 4, 0, Math.max(0, modifiers.size() - 16));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focus == Focus.NONE) return super.charTyped(codePoint, modifiers);
        if (focus == Focus.FLOOR_SEARCH) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '-' || codePoint == '+') {
                floorSearch = append(floorSearch, codePoint, 40);
                rowScroll = 0;
                return true;
            }
        } else if (focus == Focus.GO_FLOOR) {
            if (Character.isDigit(codePoint)) {
                goFloor = append(goFloor, codePoint, 2);
                return true;
            }
        } else if (focus == Focus.GUIDE_SEARCH) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '-') {
                guideSearch = append(guideSearch, codePoint, 40);
                guideScroll = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (modifierModal != null) {
                modifierModal = null;
                return true;
            }
            if (focus != Focus.NONE) {
                focus = Focus.NONE;
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == 259 && focus != Focus.NONE) {
            switch (focus) {
                case FLOOR_SEARCH -> floorSearch = removeLast(floorSearch);
                case GO_FLOOR -> goFloor = removeLast(goFloor);
                case GUIDE_SEARCH -> guideSearch = removeLast(guideSearch);
                default -> {}
            }
            rowScroll = 0;
            guideScroll = 0;
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && focus != Focus.NONE) {
            if (focus == Focus.GO_FLOOR) goToFloor();
            else focus = Focus.NONE;
            return true;
        }
        if (focus == Focus.NONE && page == Page.FLOOR) {
            if (keyCode == 262) { selectFloor(Math.min(highestUnlockedFloor(), selectedFloor + 1)); return true; }
            if (keyCode == 263) { selectFloor(Math.max(1, selectedFloor - 1)); return true; }
            if (keyCode == 264) { rowScroll = clamp(rowScroll + 1, 0, Math.max(0, browserFloors().size() - ROWS_VISIBLE)); return true; }
            if (keyCode == 265) { rowScroll = clamp(rowScroll - 1, 0, Math.max(0, browserFloors().size() - ROWS_VISIBLE)); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String append(String value, char character, int max) {
        return value.length() >= max ? value : value + character;
    }

    private String removeLast(String value) {
        return value.isEmpty() ? value : value.substring(0, value.length() - 1);
    }

    @Override
    public void onClose() {
        DungeonUiApi.emit(DungeonUiApi.ActionType.CLOSE, selectedFloor, "");
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Hotspot(int x, int y, int w, int h, Runnable action, String tooltip) {
        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
