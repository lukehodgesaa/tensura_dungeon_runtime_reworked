package dev.luke.tensuradungeon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineclawd.tensuradungeon.client.CoreSatchelInventoryTab;
import com.mineclawd.tensuradungeon.client.DungeonHudSuppressingScreen;
import com.mineclawd.tensuradungeon.network.LootSummaryPayload;
import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class DungeonLootSummaryScreen extends Screen implements DungeonHudSuppressingScreen {
    static final int LOOT_PAGE_CAPACITY = 24;
    static final int ENEMY_PAGE_CAPACITY = 4;

    private static final int W = 960;
    private static final int H = 540;

    private static final int BG = 0xFF01050C;
    private static final int PANEL = 0xF2050D1A;
    private static final int PANEL_INSET = 0xE9071424;
    private static final int SLOT = 0xEC07111D;
    private static final int SLOT_ALT = 0xEC091827;
    private static final int LINE = 0xFF285783;
    private static final int LINE_FAINT = 0x8A1B3B5C;
    private static final int CYAN = 0xFF35DFF2;
    private static final int CYAN_DIM = 0xFF1F91B5;
    private static final int GOLD = 0xFFFFB832;
    private static final int GREEN = 0xFF35D98A;
    private static final int RED = 0xFFFF5968;
    private static final int VIOLET = 0xFFB46BEB;
    private static final int WHITE = 0xFFF2F5FB;
    private static final int TEXT = 0xFFD7DDEA;
    private static final int MUTED = 0xFF8793A8;
    private static final int DIM = 0xFF5E6B81;
    private static final ResourceLocation CORE_TOOLTIP_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "tensura_dungeon",
        "textures/gui/core_satchel/core_tooltip_arcane_seal.png"
    );
    private static final int CORE_TOOLTIP_WIDTH = 256;
    private static final int CORE_TOOLTIP_HEIGHT = 80;

    private static final int OVERVIEW_X = 28;
    private static final int OVERVIEW_Y = 82;
    private static final int OVERVIEW_W = 526;
    private static final int OVERVIEW_H = 300;
    private static final int STATS_Y = 390;
    private static final int STATS_H = 58;
    private static final int LOOT_X = 566;
    private static final int LOOT_Y = 82;
    private static final int LOOT_W = 366;
    private static final int LOOT_H = 366;
    private static final int LOOT_GRID_X = 586;
    private static final int LOOT_GRID_Y = 124;
    private static final int LOOT_SLOT_SIZE = 50;
    private static final int LOOT_SLOT_GAP = 5;
    private static final int LOOT_COLUMNS = 6;
    private static final int AWARD_ROW_HEIGHT = 54;
    private static final float MODAL_Z = 500.0F;
    private static final float MODAL_TITLE_SCALE = 1.35F;
    private static final float MODAL_BODY_SCALE = 1.08F;
    private static final float MODAL_WARNING_SCALE = 1.08F;
    private static final float MODAL_HINT_SCALE = 1.02F;

    private final Screen parent;
    private final List<Hotspot> hotspots = new ArrayList<>();

    private LootSummaryPayload summary;
    private String selectedEntryId = "";
    private int lootPage;
    private int enemyPage;
    private int hoveredEntryIndex = -1;
    private String lastClickedEntryId = "";
    private long lastEntryClickMillis;
    private Confirmation confirmation;
    private String toast = "";
    private long toastUntilMillis;

    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;

    public DungeonLootSummaryScreen(Screen parent) {
        super(Component.literal("Run Summary"));
        this.parent = parent;
        applyAuthoritativeSummary(DungeonRuntimeBridge.lootSummary());
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncAuthoritativeSummary();
        updateTransform();
        drawOutsideBackdrop(graphics);

        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        hotspots.clear();
        hoveredEntryIndex = -1;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(uiX, uiY, 0.0F);
        pose.scale(uiScale, uiScale, 1.0F);

        drawBackdrop(graphics);
        if (summary == null || !summary.available()) {
            drawUnavailable(graphics);
        } else {
            drawHeader(graphics);
            drawRunOverview(graphics);
            drawRunStats(graphics);
            drawLootPanel(graphics, vx, vy);
            drawDestinations(graphics);
        }

        if (!toast.isBlank() && Util.getMillis() < toastUntilMillis) {
            drawToast(graphics);
        }

        if (confirmation != null) {
            hotspots.clear();
            hoveredEntryIndex = -1;
            graphics.flush();
            pose.pushPose();
            pose.translate(0.0F, 0.0F, MODAL_Z);
            drawConfirmation(graphics, confirmation);
            pose.popPose();
        } else if (hoveredEntryIndex >= 0 && hoveredEntryIndex < entries().size()) {
            LootSummaryPayload.Entry hoveredEntry = entries().get(hoveredEntryIndex);
            if (hoveredEntry.recoveredCore() != null) {
                drawCoreTooltip(graphics, hoveredEntry.recoveredCore(), vx + 7, vy + 7);
            } else {
                ItemStack stack = hoveredEntry.stack();
                if (!stack.isEmpty()) graphics.renderTooltip(font, stack, vx + 7, vy + 7);
            }
        } else {
            Hotspot hovered = hovered(vx, vy);
            if (hovered != null && !hovered.tooltip().isBlank()) {
                drawTooltip(graphics, hovered.tooltip(), vx + 7, vy + 7);
            }
        }
        pose.popPose();
    }

    private void syncAuthoritativeSummary() {
        LootSummaryPayload latest = DungeonRuntimeBridge.lootSummary();
        if (latest == null) return;
        if (latest != summary) {
            applyAuthoritativeSummary(latest);
        }

        String coreMessage = DungeonRuntimeBridge.consumeCoreQuickStoreMessage();
        if (!coreMessage.isBlank()) showToast(coreMessage);
    }

    private void applyAuthoritativeSummary(LootSummaryPayload updated) {
        if (updated == null) return;
        boolean newSession = summary == null || !updated.sessionId().equals(summary.sessionId());
        summary = updated;

        if (newSession) {
            selectedEntryId = "";
            lootPage = 0;
            enemyPage = 0;
            lastClickedEntryId = "";
            lastEntryClickMillis = 0L;
        }

        if (selectedEntryId.isBlank() || indexOfEntry(selectedEntryId) < 0) {
            selectedEntryId = updated.entries().stream()
                .filter(DungeonLootSummaryScreen::isActionableEntry)
                .map(LootSummaryPayload.Entry::entryId)
                .findFirst()
                .orElseGet(() -> updated.entries().isEmpty() ? "" : updated.entries().getFirst().entryId());
        }

        int selectedIndex = indexOfEntry(selectedEntryId);
        if (selectedIndex >= 0) lootPage = selectedIndex / LOOT_PAGE_CAPACITY;
        lootPage = clampPage(lootPage, updated.entries().size(), LOOT_PAGE_CAPACITY);
        enemyPage = clampPage(
            enemyPage, updated.overview().enemyDefeats().size(), ENEMY_PAGE_CAPACITY
        );

        String statusCopy = statusCopy(updated.status(), updated.message());
        if (!statusCopy.isBlank()) showToast(statusCopy);
    }

    private void updateTransform() {
        uiScale = Math.min(width / (float) W, height / (float) H);
        uiX = (width - W * uiScale) * 0.5F;
        uiY = (height - H * uiScale) * 0.5F;
    }

    private int virtualX(double mouseX) {
        return (int) Math.floor((mouseX - uiX) / Math.max(0.01F, uiScale));
    }

    private int virtualY(double mouseY) {
        return (int) Math.floor((mouseY - uiY) / Math.max(0.01F, uiScale));
    }

    private void drawOutsideBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xFF030711, 0xFF000207);
        int center = width / 2;
        graphics.fill(
            Math.max(0, center - width / 3), 0,
            Math.min(width, center + width / 3), height,
            0x15165B70
        );
    }

    private void drawBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, W, H, BG);
        for (int x = 0; x < W; x += 48) graphics.vLine(x, 0, H, 0x0D1A466B);
        for (int y = 0; y < H; y += 36) graphics.hLine(0, W, y, 0x0A1A466B);
        graphics.fillGradient(0, 0, W, 82, 0xD9020A15, 0x3D071422);
        graphics.fillGradient(0, 330, W, H, 0x00000000, 0xA0000207);
    }

    private void drawHeader(GuiGraphics graphics) {
        drawScaled(graphics, "RUN SUMMARY", 32, 19, WHITE, 2.15F);
        drawScaled(graphics, "FLOOR " + summary.floor() + " COMPLETE", 34, 53, GOLD, 1.05F);

        LootSummaryPayload.Wallet wallet = summary.wallet();
        CompactWalletSummary.Value balance = CompactWalletSummary.summarize(
            wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()
        );
        drawHeaderCard(graphics, 794, 18, 138, "BALANCE", balance.displayText(),
            ResourceLocation.parse(balance.denomination().itemId()),
            balance.denomination().color(),
            "Bank + money on hand in its highest useful denomination");
    }

    private void drawHeaderCard(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        String label,
        String value,
        ResourceLocation itemId,
        int valueColor,
        String tooltip
    ) {
        steppedPanel(graphics, x, y, w, 48, 0xF2071221, LINE, 6);
        drawRegisteredItemIcon(graphics, itemId, x + 20, y + 24);
        drawFit(graphics, label, x + 42, y + 10, w - 49, TEXT, 0.84F);
        drawFit(graphics, value, x + 42, y + 27, w - 49, valueColor, 1.12F);
        addHotspot(x, y, w, 48, tooltip, null);
    }

    private void drawRunOverview(GuiGraphics graphics) {
        steppedPanel(graphics, OVERVIEW_X, OVERVIEW_Y, OVERVIEW_W, OVERVIEW_H, PANEL, LINE, 8);
        sectionHeading(graphics, "RUN OVERVIEW", OVERVIEW_X + OVERVIEW_W / 2,
            OVERVIEW_Y + 10, OVERVIEW_X + 24, OVERVIEW_X + OVERVIEW_W - 24);
        graphics.vLine(287, 113, 367, LINE_FAINT);
        drawEnemyLeaderboard(graphics);
        drawAwards(graphics);
    }

    private void drawEnemyLeaderboard(GuiGraphics graphics) {
        int x = 44;
        int y = 136;
        int w = 236;
        drawCenteredFit(graphics, "ENEMY LEADERBOARD", x + w / 2, 117, w, CYAN, 0.92F);

        panel(graphics, x, y, w, 21, 0xCC071321, LINE_FAINT);
        drawFit(graphics, "RANK", x + 9, y + 7, 50, MUTED, 0.72F);
        drawFit(graphics, "ENEMY", x + 84, y + 7, 100, MUTED, 0.72F);
        drawRightFit(graphics, "KILLS", x + w - 10, y + 7, 45, MUTED, 0.72F);

        List<LootSummaryPayload.EnemyDefeat> enemies = summary.overview().enemyDefeats();
        int start = pageStart(enemyPage, ENEMY_PAGE_CAPACITY);
        for (int row = 0; row < ENEMY_PAGE_CAPACITY; row++) {
            int rowY = y + 21 + row * 34;
            panel(graphics, x, rowY, w, 34, row % 2 == 0 ? SLOT : SLOT_ALT, LINE_FAINT);
            int index = start + row;
            if (index >= enemies.size()) continue;
            LootSummaryPayload.EnemyDefeat enemy = enemies.get(index);
            drawFit(graphics, String.format(Locale.ROOT, "%02d", index + 1),
                x + 10, rowY + 12, 42, CYAN, 0.86F);
            drawFit(graphics, enemy.displayName().toUpperCase(Locale.ROOT),
                x + 84, rowY + 12, 112, TEXT, 0.85F);
            drawRightFit(graphics, formatWhole(enemy.kills()),
                x + w - 12, rowY + 12, 44, CYAN, 0.88F);
        }

        int totalY = y + 160;
        panel(graphics, x, totalY, w, 31, 0xED071321, LINE);
        drawFit(graphics, "TOTAL DEFEATED", x + 11, totalY + 11, 150, WHITE, 0.86F);
        drawRightFit(graphics, formatWhole(summary.overview().combat().totalDefeated()),
            x + w - 12, totalY + 11, 52, CYAN, 0.9F);

        int pages = pageCount(enemies.size(), ENEMY_PAGE_CAPACITY);
        if (pages > 1) {
            drawPager(graphics, x + 48, 334, 140, enemyPage, pages,
                () -> enemyPage = clampPage(enemyPage - 1, enemies.size(), ENEMY_PAGE_CAPACITY),
                () -> enemyPage = clampPage(enemyPage + 1, enemies.size(), ENEMY_PAGE_CAPACITY));
        } else if (enemies.isEmpty()) {
            drawCenteredFit(graphics, "NO ENEMIES RECORDED", x + w / 2, 347, w - 20, DIM, 0.72F);
        }
    }

    private void drawAwards(GuiGraphics graphics) {
        int x = 294;
        int y = 136;
        int w = 244;
        drawCenteredFit(graphics, "AWARDS", x + w / 2, 117, w, CYAN, 0.92F);

        LootSummaryPayload.RewardSummary rewards = summary.overview().rewards();
        drawAwardRow(graphics, x, y, w, "FLOOR CLEARED",
            "+" + formatCurrency(rewards.currencyEarnedBronze()), GOLD, 0);
        drawAwardRow(graphics, x, y + AWARD_ROW_HEIGHT, w, "MONSTER CORES",
            formatWhole(summary.recoveredCores().size()), VIOLET, 1);
        drawAwardRow(graphics, x, y + AWARD_ROW_HEIGHT * 2, w, "METALS EARNED",
            "+" + formatWhole(rewards.metalsEarned()), CYAN, 2);

        int totalY = y + AWARD_ROW_HEIGHT * 3;
        panel(graphics, x, totalY, w, 38, 0xED071321, LINE);
        drawFit(graphics, "TOTAL RUN VALUE", x + 11, totalY + 14, 145, WHITE, 0.84F);
        String total = rewards.totalRunValueAvailable()
            ? formatCurrency(rewards.totalRunValueBronze())
            : "NOT PRICED";
        drawRightFit(graphics, total, x + w - 11, totalY + 14, 94,
            rewards.totalRunValueAvailable() ? GOLD : MUTED, 0.86F);
        if (!rewards.totalRunValueAvailable()) {
            addHotspot(x, totalY, w, 38,
                "Loot prices are not enabled on this server yet", null);
        }
    }

    private void drawAwardRow(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        String label,
        String value,
        int valueColor,
        int row
    ) {
        panel(graphics, x, y, w, AWARD_ROW_HEIGHT,
            row % 2 == 0 ? SLOT : SLOT_ALT, LINE_FAINT);
        drawFit(graphics, label, x + 11, y + 22, 154, TEXT, 0.83F);
        drawRightFit(graphics, value, x + w - 11, y + 22, 85, valueColor, 0.88F);
    }

    private void drawRunStats(GuiGraphics graphics) {
        steppedPanel(graphics, OVERVIEW_X, STATS_Y, OVERVIEW_W, STATS_H, PANEL, LINE, 7);
        sectionHeading(graphics, "RUN STATS", OVERVIEW_X + OVERVIEW_W / 2,
            STATS_Y + 6, OVERVIEW_X + 24, OVERVIEW_X + OVERVIEW_W - 24);
        int baseline = STATS_Y + 36;
        drawTimeStat(graphics, OVERVIEW_X + 20, baseline, 142,
            summary.overview().personalBest()
                ? formatTicks(summary.overview().clearTicks()) + " - PERSONAL BEST!"
                : formatTicks(summary.overview().clearTicks()),
            summary.overview().personalBest() ? GOLD : CYAN);
        graphics.vLine(198, STATS_Y + 28, STATS_Y + 49, LINE_FAINT);
        drawStat(graphics, 215, baseline, 165, "DAMAGE DEALT",
            formatDamage(summary.overview().combat().damageDealt()), CYAN);
        graphics.vLine(389, STATS_Y + 28, STATS_Y + 49, LINE_FAINT);
        drawStat(graphics, 406, baseline, 132, "DAMAGE TAKEN",
            formatDamage(summary.overview().combat().damageTaken()), CYAN);
    }

    private void drawStat(
        GuiGraphics graphics, int x, int y, int w, String label, String value, int valueColor
    ) {
        drawFit(graphics, label, x, y, w / 2, MUTED, 0.78F);
        drawRightFit(graphics, value, x + w, y, w / 2 + 12, valueColor, 0.93F);
    }

    private void drawTimeStat(
        GuiGraphics graphics, int x, int y, int w, String value, int valueColor
    ) {
        drawFit(graphics, "TIME", x, y, 30, MUTED, 0.78F);
        drawRightFit(graphics, value, x + w, y, w - 36, valueColor, 0.93F);
    }

    private void drawLootPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        steppedPanel(graphics, LOOT_X, LOOT_Y, LOOT_W, LOOT_H, PANEL, LINE, 8);
        sectionHeading(graphics, "APPRAISED DROPS", LOOT_X + LOOT_W / 2,
            LOOT_Y + 10, LOOT_X + 23, LOOT_X + LOOT_W - 23);
        drawRightFit(graphics, stackLabel(entries().size()),
            LOOT_X + LOOT_W - 16, LOOT_Y + 22, 75, CYAN, 0.75F);

        drawLootGrid(graphics, mouseX, mouseY);
        drawLootNavigation(graphics);
        drawSelectedDrop(graphics);
    }

    private void drawLootGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<LootSummaryPayload.Entry> entries = entries();
        int start = pageStart(lootPage, LOOT_PAGE_CAPACITY);
        for (int slotIndex = 0; slotIndex < LOOT_PAGE_CAPACITY; slotIndex++) {
            int column = slotIndex % LOOT_COLUMNS;
            int row = slotIndex / LOOT_COLUMNS;
            int x = LOOT_GRID_X + column * (LOOT_SLOT_SIZE + LOOT_SLOT_GAP);
            int y = LOOT_GRID_Y + row * (LOOT_SLOT_SIZE + LOOT_SLOT_GAP);
            int globalIndex = start + slotIndex;
            LootSummaryPayload.Entry entry = globalIndex < entries.size() ? entries.get(globalIndex) : null;
            drawLootSlot(graphics, x, y, entry);
            if (entry != null) {
                addHotspot(x, y, LOOT_SLOT_SIZE, LOOT_SLOT_SIZE,
                    entry.recoveredCore() == null
                        ? "Click to select. Double-click to keep."
                        : "Click to select this Core.",
                    () -> clickEntry(globalIndex));
                if (contains(x, y, LOOT_SLOT_SIZE, LOOT_SLOT_SIZE, mouseX, mouseY)) {
                    hoveredEntryIndex = globalIndex;
                }
            }
        }

        if (entries.isEmpty()) {
            drawCenteredFit(graphics, "NO DROPS THIS RUN", LOOT_X + LOOT_W / 2,
                LOOT_GRID_Y + 101, LOOT_W - 60, DIM, 0.9F);
        }
    }

    private void drawLootSlot(
        GuiGraphics graphics,
        int x,
        int y,
        LootSummaryPayload.Entry entry
    ) {
        boolean selected = entry != null && entry.entryId().equals(selectedEntryId);
        int border = selected ? GOLD : entryBorder(entry);
        int fill = selected ? 0xEE0B1D2A : SLOT;
        steppedPanel(graphics, x, y, LOOT_SLOT_SIZE, LOOT_SLOT_SIZE, fill, border, 4);
        graphics.hLine(x + 6, x + LOOT_SLOT_SIZE - 7, y + 3, 0x483BA5D1);

        if (entry == null || entry.stack().isEmpty()) return;
        ItemStack stack = entry.stack();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 9, y + 8, 100.0F);
        pose.scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();

        String count = formatWhole(stack.getCount());
        drawRightFit(graphics, count, x + LOOT_SLOT_SIZE - 5, y + LOOT_SLOT_SIZE - 11,
            31, WHITE, 0.76F);
        if ("KEPT".equals(entry.disposition())) {
            graphics.fill(x + 5, y + LOOT_SLOT_SIZE - 4, x + LOOT_SLOT_SIZE - 5,
                y + LOOT_SLOT_SIZE - 2, GREEN);
        } else if (entry.recoveredCore() != null) {
            graphics.fill(x + 5, y + LOOT_SLOT_SIZE - 4, x + LOOT_SLOT_SIZE - 5,
                y + LOOT_SLOT_SIZE - 2, VIOLET);
        } else if ("SOLD".equals(entry.disposition()) || "RETIRED".equals(entry.disposition())) {
            graphics.fill(x + 5, y + LOOT_SLOT_SIZE - 4, x + LOOT_SLOT_SIZE - 5,
                y + LOOT_SLOT_SIZE - 2, RED);
        }
    }

    private void drawLootNavigation(GuiGraphics graphics) {
        List<LootSummaryPayload.Entry> entries = entries();
        int pages = pageCount(entries.size(), LOOT_PAGE_CAPACITY);
        drawPager(graphics, LOOT_GRID_X, 346, 174, lootPage, pages,
            () -> setLootPage(lootPage - 1), () -> setLootPage(lootPage + 1));

        boolean reveal = summary.canAppraise();
        String label = reveal ? "REVEAL" : "KEEP ALL";
        boolean active = reveal || summary.canKeepAll();
        drawButton(graphics, 815, 346, 96, 22, label, active, active ? CYAN : DIM);
        if (active) {
            addHotspot(815, 346, 96, 22,
                reveal ? "Reveal the saved drops" : "Move every unresolved drop to your inventory",
                reveal ? this::revealDrops : this::keepAll);
        } else {
            addHotspot(815, 346, 96, 22, "No unresolved drops remain", null);
        }
        drawCenteredFit(graphics, "DOUBLE-CLICK AN ITEM TO KEEP IT",
            LOOT_X + LOOT_W / 2, 370, LOOT_W - 36, DIM, 0.68F);
    }

    private void drawSelectedDrop(GuiGraphics graphics) {
        int x = 578;
        int y = 376;
        int w = 342;
        int h = 62;
        steppedPanel(graphics, x, y, w, h, PANEL_INSET, LINE, 6);
        sectionHeading(graphics, "SELECTED DROP", x + w / 2, y + 5, x + 17, x + w - 17);

        LootSummaryPayload.Entry entry = selectedEntry();
        if (entry == null) {
            drawCenteredFit(graphics, "NO DROP SELECTED", x + w / 2, y + 34,
                w - 30, MUTED, 0.85F);
            return;
        }

        int itemX = x + 10;
        int itemY = y + 21;
        steppedPanel(graphics, itemX, itemY, 39, 34, SLOT, entryBorder(entry), 3);
        if (!entry.stack().isEmpty()) {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(itemX + 4, itemY + 1, 100.0F);
            pose.scale(1.85F, 1.85F, 1.0F);
            graphics.renderItem(entry.stack(), 0, 0);
            pose.popPose();
        }

        String name = entry.stack().isEmpty()
            ? "UNKNOWN DROP"
            : entry.stack().getHoverName().getString().toUpperCase(Locale.ROOT);
        int nameColor = entry.recoveredCore() == null
            ? WHITE
            : coreTierColor(entry.recoveredCore().tier());
        drawFit(graphics, name, x + 58, y + 25, 153, nameColor, 0.86F);
        drawFit(graphics, "X" + formatWhole(entry.stack().getCount()),
            x + 58, y + 43, 45, CYAN, 0.84F);
        drawFit(graphics, selectedMeta(entry), x + 101, y + 43, 110, entryBorder(entry), 0.68F);

        boolean core = entry.recoveredCore() != null;
        boolean primaryActive = core || isUnresolved(entry);
        String primary = core ? "PLACE" : ("KEPT".equals(entry.disposition()) ? "KEPT" : "KEEP");
        drawButton(graphics, x + 232, y + 20, 99, 18, primary, primaryActive,
            core ? VIOLET : (primaryActive ? GOLD : GREEN));
        if (primaryActive) {
            addHotspot(x + 232, y + 20, 99, 18,
                core ? "Choose a Core Satchel slot" : "Move this drop to your inventory",
                this::resolveSelected);
        }

        String secondary = core ? "QUICK STORE" : "SELL";
        boolean secondaryActive = core;
        drawButton(graphics, x + 232, y + 41, 99, 15, secondary, secondaryActive,
            secondaryActive ? CYAN : DIM);
        addHotspot(x + 232, y + 41, 99, 15,
            core ? "Store this Core in the next empty Satchel slot"
                : "Selling is unavailable until server prices are enabled",
            core ? this::quickStoreSelectedCore : null);
    }

    private void drawDestinations(GuiGraphics graphics) {
        int y = 465;
        drawDestinationButton(graphics, 59, y, 252,
            summary.exitOptionsAvailable()
                ? "CONTINUE TO FLOOR " + summary.nextFloor()
                : "CONTINUE",
            ExitAction.CONTINUE,
            summary.exitOptionsAvailable());
        drawDestinationButton(graphics, 354, y, 252,
            "RETURN TO HUB", ExitAction.HUB, summary.exitOptionsAvailable());
        drawDestinationButton(graphics, 649, y, 252,
            "RETURN HOME", ExitAction.HOME, summary.exitOptionsAvailable());
    }

    private void drawDestinationButton(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        String label,
        ExitAction action,
        boolean active
    ) {
        drawButton(graphics, x, y, w, 50, label, active, active ? CYAN : DIM);
        addHotspot(x, y, w, 50,
            active ? destinationTooltip(action) : "Use the floor exit portal to leave",
            active ? () -> confirmation = new Confirmation(action) : null);
    }

    private void drawConfirmation(GuiGraphics graphics, Confirmation prompt) {
        graphics.fill(0, 0, W, H, 0xD9000207);
        int x = 245;
        int y = 154;
        int w = 470;
        int h = 232;
        steppedPanel(graphics, x, y, w, h, 0xFF050C17, GOLD, 9);
        String title = switch (prompt.action()) {
            case CONTINUE -> "CONTINUE TO FLOOR " + summary.nextFloor() + "?";
            case HUB -> "RETURN TO HUB?";
            case HOME -> "RETURN HOME?";
        };
        drawCenteredFit(graphics, title, x + w / 2, y + 24, w - 46, GOLD, MODAL_TITLE_SCALE);

        int bodyY = y + 66;
        drawCenteredFit(graphics, "Items left on this floor are removed after the party leaves.",
            x + w / 2, bodyY, w - 54, TEXT, MODAL_BODY_SCALE);
        drawCenteredFit(graphics, "Your appraised drops are safe.",
            x + w / 2, bodyY + 22, w - 54, GREEN, MODAL_BODY_SCALE);
        if (prompt.action() == ExitAction.HOME) {
            drawCenteredFit(graphics, "This also ends the current dungeon run.",
                x + w / 2, bodyY + 44, w - 54, TEXT, MODAL_BODY_SCALE);
        }

        int pendingCores = summary.recoveredCores().size();
        if (pendingCores > 0) {
            String warning = pendingCores + (pendingCores == 1
                ? " UNSTORED CORE WILL BE DISCARDED."
                : " UNSTORED CORES WILL BE DISCARDED.");
            drawCenteredFit(graphics, warning, x + w / 2, bodyY + 70,
                w - 54, RED, MODAL_WARNING_SCALE);
        } else {
            drawCenteredFit(graphics, "You can still press Escape to stay on this floor.",
                x + w / 2, bodyY + 70, w - 54, MUTED, MODAL_HINT_SCALE);
        }

        drawButton(graphics, x + 26, y + h - 49, 192, 29, "STAY", true, TEXT);
        drawButton(graphics, x + w - 218, y + h - 49, 192, 29,
            confirmLabel(prompt.action()), true, prompt.action() == ExitAction.HOME ? RED : CYAN);
        addHotspot(x + 26, y + h - 49, 192, 29, "Stay on this floor",
            () -> confirmation = null);
        addHotspot(x + w - 218, y + h - 49, 192, 29,
            "Confirm departure", () -> confirmExit(prompt.action()));
    }

    private void drawUnavailable(GuiGraphics graphics) {
        steppedPanel(graphics, 192, 168, 576, 204, PANEL, LINE, 9);
        drawCenteredFit(graphics, "RUN SUMMARY", W / 2, 205, 520, WHITE, 1.65F);
        drawCenteredFit(graphics, "NO COMPLETED RUN IS AVAILABLE", W / 2, 259,
            500, MUTED, 0.96F);
        drawButton(graphics, 350, 310, 260, 36, "CLOSE", true, CYAN);
        addHotspot(350, 310, 260, 36, "Close this screen", this::onClose);
    }

    private void drawPager(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        int page,
        int pages,
        Runnable previous,
        Runnable next
    ) {
        int safePages = Math.max(1, pages);
        boolean hasPrevious = page > 0;
        boolean hasNext = page + 1 < safePages;
        drawButton(graphics, x, y, 28, 18, "<", hasPrevious, hasPrevious ? CYAN : DIM);
        drawButton(graphics, x + w - 28, y, 28, 18, ">", hasNext, hasNext ? CYAN : DIM);
        drawCenteredFit(graphics, "PAGE " + (page + 1) + "/" + safePages,
            x + w / 2, y + 6, w - 64, MUTED, 0.7F);
        if (hasPrevious) addHotspot(x, y, 28, 18, "Previous page", previous);
        if (hasNext) addHotspot(x + w - 28, y, 28, 18, "Next page", next);
    }

    private void drawButton(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        int h,
        String label,
        boolean active,
        int accent
    ) {
        int fill = active ? 0xEE081522 : 0xC908101B;
        int border = active ? accent : 0xFF465063;
        steppedPanel(graphics, x, y, w, h, fill, border, Math.min(5, Math.max(2, h / 5)));
        if (active) {
            graphics.hLine(x + 8, x + w - 9, y + 3, withAlpha(accent, 86));
        }
        drawCenteredFit(graphics, label, x + w / 2,
            y + Math.max(5, (h - 8) / 2), w - 18, active ? WHITE : DIM,
            h >= 40 ? 1.12F : h >= 22 ? 0.84F : 0.68F);
    }

    private void drawToast(GuiGraphics graphics) {
        int w = Math.min(520, Math.max(180, font.width(toast) + 30));
        int x = (W - w) / 2;
        int y = 438;
        steppedPanel(graphics, x, y, w, 23, 0xF20A1721, CYAN_DIM, 4);
        drawCenteredFit(graphics, toast, W / 2, y + 8, w - 20, TEXT, 0.76F);
    }

    private void drawTooltip(GuiGraphics graphics, String value, int x, int y) {
        List<String> lines = wrap(value, 230, 4);
        int w = lines.stream().mapToInt(font::width).max().orElse(0) + 14;
        int h = lines.size() * 11 + 10;
        int drawX = Math.min(x, W - w - 5);
        int drawY = Math.min(y, H - h - 5);
        panel(graphics, drawX, drawY, w, h, 0xF809101B, LINE);
        for (int i = 0; i < lines.size(); i++) {
            draw(graphics, lines.get(i), drawX + 7, drawY + 6 + i * 11, TEXT);
        }
    }

    private void drawCoreTooltip(
        GuiGraphics graphics,
        LootSummaryPayload.RecoveredCore core,
        int mouseX,
        int mouseY
    ) {
        int x = Math.min(mouseX, W - CORE_TOOLTIP_WIDTH - 5);
        int y = Math.min(mouseY, H - CORE_TOOLTIP_HEIGHT - 5);
        x = Math.max(5, x);
        y = Math.max(5, y);

        graphics.blit(
            CORE_TOOLTIP_TEXTURE,
            x,
            y,
            CORE_TOOLTIP_WIDTH,
            CORE_TOOLTIP_HEIGHT,
            0.0F,
            0.0F,
            CORE_TOOLTIP_WIDTH,
            CORE_TOOLTIP_HEIGHT,
            CORE_TOOLTIP_WIDTH,
            CORE_TOOLTIP_HEIGHT
        );

        int tierColor = coreTierColor(core.tier());
        drawFit(
            graphics,
            "TIER " + roman(core.tier()) + " MONSTER CORE",
            x + 14,
            y + 10,
            222,
            tierColor,
            0.88F
        );
        drawFit(graphics, core.displayName(), x + 14, y + 23, 222, WHITE, 0.82F);
        drawFit(
            graphics,
            "TOTAL CHARGE " + formatWhole(core.totalCharge()) + " / " + formatWhole(core.capacity()),
            x + 14,
            y + 37,
            222,
            TEXT,
            0.72F
        );
        drawFit(
            graphics,
            "NATURAL CHARGE " + formatWhole(core.naturalCharge()),
            x + 14,
            y + 49,
            222,
            TEXT,
            0.72F
        );
        drawFit(graphics, "CORE TECHNIQUE SOCKETED", x + 14, y + 62, 222, tierColor, 0.68F);
    }

    private void drawRegisteredItemIcon(
        GuiGraphics graphics,
        ResourceLocation itemId,
        int centerX,
        int centerY
    ) {
        BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(centerX - 12.0F, centerY - 12.0F, 100.0F);
            pose.scale(1.5F, 1.5F, 1.0F);
            graphics.renderItem(new ItemStack(item), 0, 0);
            pose.popPose();
        });
    }

    private void sectionHeading(
        GuiGraphics graphics,
        String title,
        int centerX,
        int y,
        int left,
        int right
    ) {
        int textWidth = Math.round(font.width(title) * 0.9F);
        int gap = textWidth / 2 + 13;
        graphics.hLine(left, centerX - gap, y + 7, LINE);
        graphics.hLine(centerX + gap, right, y + 7, LINE);
        diamond(graphics, centerX - gap - 5, y + 7, 2, CYAN_DIM);
        diamond(graphics, centerX + gap + 5, y + 7, 2, CYAN_DIM);
        drawCenteredScaled(graphics, title, centerX, y + 2, CYAN, 0.98F);
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.hLine(x, x + w - 1, y, border);
        graphics.hLine(x, x + w - 1, y + h - 1, border);
        graphics.vLine(x, y, y + h - 1, border);
        graphics.vLine(x + w - 1, y, y + h - 1, border);
    }

    private void steppedPanel(
        GuiGraphics graphics,
        int x,
        int y,
        int w,
        int h,
        int fill,
        int border,
        int cut
    ) {
        if (w <= cut * 2 || h <= cut * 2) return;
        graphics.fill(x + cut, y, x + w - cut, y + h, fill);
        graphics.fill(x, y + cut, x + w, y + h - cut, fill);
        graphics.fill(x + cut / 2, y + cut / 2,
            x + w - cut / 2, y + h - cut / 2, fill);
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

    private void diamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy,
                centerX + half + 1, centerY + dy + 1, color);
        }
    }

    private void clickEntry(int index) {
        if (index < 0 || index >= entries().size()) return;
        LootSummaryPayload.Entry entry = entries().get(index);
        selectedEntryId = entry.entryId();
        lootPage = index / LOOT_PAGE_CAPACITY;

        long now = Util.getMillis();
        boolean doubleClick = entry.entryId().equals(lastClickedEntryId)
            && now - lastEntryClickMillis <= 350L;
        lastClickedEntryId = entry.entryId();
        lastEntryClickMillis = now;
        if (doubleClick && isUnresolved(entry)) resolveSelected();
    }

    private void revealDrops() {
        if (summary == null || !summary.canAppraise()) return;
        DungeonRuntimeBridge.appraise(summary.sessionId());
        showToast("REVEALING DROPS...");
    }

    private void keepAll() {
        if (summary == null || !summary.canKeepAll()) return;
        DungeonRuntimeBridge.keepAll(summary.sessionId());
    }

    private void resolveSelected() {
        LootSummaryPayload.Entry entry = selectedEntry();
        if (entry == null) return;
        if (entry.recoveredCore() != null) {
            CoreSatchelInventoryTab.RecoveryPlacementStatus status =
                DungeonRuntimeBridge.openCorePlacement(this, summary, entry.recoveredCore());
            if (status == CoreSatchelInventoryTab.RecoveryPlacementStatus.SATCHEL_UNAVAILABLE) {
                showToast("CORE SATCHEL UNAVAILABLE");
            } else if (status == CoreSatchelInventoryTab.RecoveryPlacementStatus.SATCHEL_FULL) {
                showToast("CORE SATCHEL FULL");
            }
            return;
        }
        if (!isUnresolved(entry)) return;
        DungeonRuntimeBridge.keepEntry(summary.sessionId(), entry.entryId());
    }

    private void quickStoreSelectedCore() {
        LootSummaryPayload.Entry entry = selectedEntry();
        if (entry == null || entry.recoveredCore() == null) return;
        CoreSatchelInventoryTab.QuickStoreStatus status =
            DungeonRuntimeBridge.quickStoreCore(summary, entry.recoveredCore());
        switch (status) {
            case REQUESTED -> showToast("STORING CORE...");
            case SATCHEL_UNAVAILABLE -> showToast("CORE SATCHEL UNAVAILABLE");
            case SATCHEL_FULL -> showToast("CORE SATCHEL FULL");
        }
    }

    private void setLootPage(int requested) {
        lootPage = clampPage(requested, entries().size(), LOOT_PAGE_CAPACITY);
        int start = pageStart(lootPage, LOOT_PAGE_CAPACITY);
        if (start < entries().size()) selectedEntryId = entries().get(start).entryId();
    }

    private void confirmExit(ExitAction action) {
        confirmation = null;
        switch (action) {
            case CONTINUE -> DungeonRuntimeBridge.continueToFloor(summary.nextFloor());
            case HUB -> DungeonRuntimeBridge.returnToHub();
            case HOME -> DungeonRuntimeBridge.returnHome();
        }
        minecraft.setScreen(null);
    }

    private List<LootSummaryPayload.Entry> entries() {
        return summary == null ? List.of() : summary.entries();
    }

    private LootSummaryPayload.Entry selectedEntry() {
        int index = indexOfEntry(selectedEntryId);
        return index < 0 ? null : entries().get(index);
    }

    private int indexOfEntry(String entryId) {
        if (entryId == null || entryId.isBlank()) return -1;
        for (int index = 0; index < entries().size(); index++) {
            if (entries().get(index).entryId().equals(entryId)) return index;
        }
        return -1;
    }

    private static boolean isActionableEntry(LootSummaryPayload.Entry entry) {
        return entry != null && (entry.recoveredCore() != null || isUnresolved(entry));
    }

    private static boolean isUnresolved(LootSummaryPayload.Entry entry) {
        return entry != null && "UNRESOLVED".equals(entry.disposition());
    }

    private static int entryBorder(LootSummaryPayload.Entry entry) {
        if (entry == null) return LINE_FAINT;
        if (entry.recoveredCore() != null) return VIOLET;
        return switch (entry.disposition()) {
            case "KEPT" -> GREEN;
            case "SOLD", "RETIRED" -> RED;
            default -> rarityColor(entry.rarity());
        };
    }

    private static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> GREEN;
            case "RARE" -> CYAN;
            case "EPIC", "CORE" -> VIOLET;
            default -> LINE;
        };
    }

    private static String selectedMeta(LootSummaryPayload.Entry entry) {
        if (entry.recoveredCore() != null) {
            return "TIER " + roman(entry.recoveredCore().tier()) + " CORE";
        }
        return switch (entry.disposition()) {
            case "KEPT" -> "KEPT";
            case "SOLD" -> "SOLD";
            case "RETIRED" -> "RESOLVED";
            default -> entry.rarity().isBlank() ? "DROP" : entry.rarity();
        };
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> Integer.toString(value);
        };
    }

    private static int coreTierColor(int tier) {
        return switch (tier) {
            case 1 -> CYAN;
            case 2 -> GREEN;
            case 3 -> 0xFF4C8DFF;
            case 4 -> VIOLET;
            case 5 -> RED;
            case 6 -> GOLD;
            default -> WHITE;
        };
    }

    private static String destinationTooltip(ExitAction action) {
        return switch (action) {
            case CONTINUE -> "Return to the hub with the next floor selected";
            case HUB -> "Return to the Dungeon Hub";
            case HOME -> "End this run and return home";
        };
    }

    private static String confirmLabel(ExitAction action) {
        return switch (action) {
            case CONTINUE -> "CONTINUE";
            case HUB -> "RETURN TO HUB";
            case HOME -> "RETURN HOME";
        };
    }

    private static String statusCopy(String status, String message) {
        if (status == null || status.isBlank()) return message == null ? "" : message;
        if ("SUCCESS".equals(status)) return message == null ? "" : message;
        return switch (status) {
            case "INVENTORY_FULL" -> "NOT ENOUGH INVENTORY SPACE";
            case "STALE_REVISION" -> "LOOT UPDATED - TRY AGAIN";
            case "NOTHING_TO_RESOLVE" -> "ALL DROPS RESOLVED";
            case "ENTRY_NOT_FOUND" -> "THAT DROP IS NO LONGER AVAILABLE";
            case "SELLING_UNAVAILABLE" -> "SELLING IS NOT ENABLED";
            case "SESSION_NOT_FOUND" -> "RUN SUMMARY NO LONGER AVAILABLE";
            default -> message == null || message.isBlank()
                ? status.replace('_', ' ')
                : message;
        };
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        return hours > 0L
            ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
            : String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds);
    }

    private static String formatDamage(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) return "0";
        return String.format(Locale.ROOT, "%,.0f", value);
    }

    private static String formatWhole(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static String stackLabel(int count) {
        int safeCount = Math.max(0, count);
        return safeCount + (safeCount == 1 ? " STACK" : " STACKS");
    }

    private static String formatCurrency(long bronzeValue) {
        long remaining = Math.max(0L, bronzeValue);
        long gold = remaining / DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        remaining %= DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long silver = remaining / DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        long bronze = remaining % DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        if (gold > 0L) {
            return silver > 0L ? formatWhole(gold) + "G " + silver + "S" : formatWhole(gold) + "G";
        }
        if (silver > 0L) {
            return bronze > 0L ? silver + "S " + bronze + "B" : silver + "S";
        }
        return bronze + "B";
    }

    static int pageCount(int total, int capacity) {
        return DungeonLootSummaryPagination.pageCount(total, capacity);
    }

    static int pageStart(int page, int capacity) {
        return DungeonLootSummaryPagination.pageStart(page, capacity);
    }

    static int clampPage(int requested, int total, int capacity) {
        return DungeonLootSummaryPagination.clampPage(requested, total, capacity);
    }

    private void addHotspot(int x, int y, int w, int h, String tooltip, Runnable action) {
        hotspots.add(new Hotspot(x, y, w, h, tooltip == null ? "" : tooltip, action));
    }

    private Hotspot hovered(int mouseX, int mouseY) {
        for (int index = hotspots.size() - 1; index >= 0; index--) {
            Hotspot hotspot = hotspots.get(index);
            if (hotspot.contains(mouseX, mouseY)) return hotspot;
        }
        return null;
    }

    private static boolean contains(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void showToast(String message) {
        if (message == null || message.isBlank()) return;
        toast = message;
        toastUntilMillis = Util.getMillis() + 2_800L;
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
            result.add(font.plainSubstrByWidth(last + "...", maxWidth));
        }
        return result;
    }

    private void draw(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x, y, color, false);
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
        drawScaled(graphics, rendered, x, y, color, Math.max(0.46F, scale));
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
        scale = Math.max(0.46F, scale);
        drawScaled(graphics, rendered,
            rightX - Math.round(font.width(rendered) * scale), y, color, scale);
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
        drawCenteredScaled(graphics, rendered, centerX, y, color, Math.max(0.46F, scale));
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
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
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
        int textWidth = Math.round(font.width(value) * scale);
        drawScaled(graphics, value, centerX - textWidth / 2, y, color, scale);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Hotspot hotspot = hovered(virtualX(mouseX), virtualY(mouseY));
        if (hotspot != null) {
            if (hotspot.action() != null) hotspot.action().run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        if (confirmation != null || summary == null || scrollY == 0.0D) return true;
        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        int direction = scrollY < 0.0D ? 1 : -1;
        if (contains(LOOT_X, LOOT_Y, LOOT_W, LOOT_H, vx, vy)) {
            setLootPage(lootPage + direction);
            return true;
        }
        if (contains(44, 136, 236, 230, vx, vy)) {
            enemyPage = clampPage(enemyPage + direction,
                summary.overview().enemyDefeats().size(), ENEMY_PAGE_CAPACITY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    private record Hotspot(int x, int y, int w, int h, String tooltip, Runnable action) {
        private boolean contains(double mouseX, double mouseY) {
            return DungeonLootSummaryScreen.contains(x, y, w, h, mouseX, mouseY);
        }
    }

    private record Confirmation(ExitAction action) {}

    private enum ExitAction {
        CONTINUE,
        HUB,
        HOME
    }
}
