package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.ExpeditionTablePrepareRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

final class ExpeditionTableDialogueScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int PANEL_MAX_WIDTH = 430;
    private static final int PANEL_HEIGHT = 166;
    private static final int BUTTON_HEIGHT = 22;
    private static final int ACCENT = 0xFF64D9FF;
    private static final int TEXT = 0xFFF1F7FF;
    private static final int MUTED = 0xFF9FB1C8;
    private static final int PLACEHOLDER = 0xFFFFC767;

    private final BlockPos tablePos;
    private Page page = Page.MAIN;

    ExpeditionTableDialogueScreen(BlockPos tablePos) {
        super(Component.translatable("gui.tensura_dungeon.expedition_table.dialogue.title"));
        this.tablePos = tablePos.immutable();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, 18, 0xC8000000);
        graphics.fill(0, height - 18, width, height, 0xC8000000);
        graphics.fill(0, 18, width, height - 18, 0x28000000);

        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - 20);
        int x = (width - panelWidth) / 2;
        int y = height - PANEL_HEIGHT - 24;
        graphics.fill(x, y, x + panelWidth, y + PANEL_HEIGHT, 0xE5070C12);
        outline(graphics, x, y, panelWidth, PANEL_HEIGHT, 0xFF29485F);
        graphics.fill(x, y, x + panelWidth, y + 2, ACCENT);
        graphics.drawString(font,
            Component.translatable("gui.tensura_dungeon.expedition_table.dialogue.speaker"),
            x + 12, y + 10, ACCENT, false);

        if (page == Page.MAIN) {
            renderMain(graphics, x, y, panelWidth, mouseX, mouseY);
        } else {
            renderPlaceholder(graphics, x, y, panelWidth, mouseX, mouseY);
        }
    }

    private void renderMain(
        GuiGraphics graphics,
        int x,
        int y,
        int panelWidth,
        int mouseX,
        int mouseY
    ) {
        drawWrapped(graphics,
            Component.translatable("gui.tensura_dungeon.expedition_table.dialogue.greeting"),
            x + 12, y + 28, panelWidth - 24, TEXT);

        int buttonX = x + 12;
        int buttonWidth = panelWidth - 24;
        int buttonY = y + 78;
        drawButton(graphics, buttonX, buttonY, buttonWidth,
            Component.translatable("gui.tensura_dungeon.expedition_table.option.prepare"),
            ACCENT, contains(buttonX, buttonY, buttonWidth, mouseX, mouseY));
        drawButton(graphics, buttonX, buttonY + 27, buttonWidth,
            Component.translatable("gui.tensura_dungeon.expedition_table.option.daily"),
            PLACEHOLDER, contains(buttonX, buttonY + 27, buttonWidth, mouseX, mouseY));
        drawButton(graphics, buttonX, buttonY + 54, buttonWidth,
            Component.translatable("gui.tensura_dungeon.expedition_table.option.weekly"),
            PLACEHOLDER, contains(buttonX, buttonY + 54, buttonWidth, mouseX, mouseY));
    }

    private void renderPlaceholder(
        GuiGraphics graphics,
        int x,
        int y,
        int panelWidth,
        int mouseX,
        int mouseY
    ) {
        Component heading = Component.translatable(page.headingKey);
        Component body = Component.translatable(page.bodyKey);
        graphics.drawString(font, heading, x + 12, y + 31, PLACEHOLDER, false);
        drawWrapped(graphics, body, x + 12, y + 51, panelWidth - 24, TEXT);
        graphics.drawString(font,
            Component.translatable("gui.tensura_dungeon.expedition_table.placeholder.note"),
            x + 12, y + 91, MUTED, false);
        int buttonX = x + 12;
        int buttonY = y + 126;
        int buttonWidth = panelWidth - 24;
        drawButton(graphics, buttonX, buttonY, buttonWidth,
            Component.translatable("gui.tensura_dungeon.expedition_table.option.back"),
            ACCENT, contains(buttonX, buttonY, buttonWidth, mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - 20);
        int x = (width - panelWidth) / 2;
        int y = height - PANEL_HEIGHT - 24;
        int buttonX = x + 12;
        int buttonWidth = panelWidth - 24;
        if (page != Page.MAIN) {
            if (contains(buttonX, y + 126, buttonWidth, mouseX, mouseY)) {
                ExpeditionTableDialogueClient.playDialogueVoiceCue(
                    Component.translatable("gui.tensura_dungeon.expedition_table.option.back").getString()
                );
                page = Page.MAIN;
                return true;
            }
            return true;
        }

        int buttonY = y + 78;
        if (contains(buttonX, buttonY, buttonWidth, mouseX, mouseY)) {
            ExpeditionTableDialogueClient.playDialogueVoiceCue(
                Component.translatable("gui.tensura_dungeon.expedition_table.option.prepare").getString()
            );
            ExpeditionTableDialogueClient.stopCinematic();
            Minecraft.getInstance().setScreen(null);
            PacketDistributor.sendToServer(new ExpeditionTablePrepareRequestPayload(tablePos));
            return true;
        }
        if (contains(buttonX, buttonY + 27, buttonWidth, mouseX, mouseY)) {
            ExpeditionTableDialogueClient.playDialogueVoiceCue(
                Component.translatable("gui.tensura_dungeon.expedition_table.option.daily").getString()
            );
            page = Page.DAILY;
            return true;
        }
        if (contains(buttonX, buttonY + 54, buttonWidth, mouseX, mouseY)) {
            ExpeditionTableDialogueClient.playDialogueVoiceCue(
                Component.translatable("gui.tensura_dungeon.expedition_table.option.weekly").getString()
            );
            page = Page.WEEKLY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        ExpeditionTableDialogueClient.stopCinematic();
        super.onClose();
    }

    @Override
    public void removed() {
        ExpeditionTableDialogueClient.stopCinematic();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawWrapped(
        GuiGraphics graphics,
        Component text,
        int x,
        int y,
        int maxWidth,
        int color
    ) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += 11;
        }
    }

    private void drawButton(
        GuiGraphics graphics,
        int x,
        int y,
        int buttonWidth,
        Component label,
        int accent,
        boolean hovered
    ) {
        graphics.fill(x, y, x + buttonWidth, y + BUTTON_HEIGHT,
            hovered ? 0xFF153147 : 0xFF0B1823);
        outline(graphics, x, y, buttonWidth, BUTTON_HEIGHT, accent);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 7,
            hovered ? 0xFFFFFFFF : accent);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.hLine(x, x + w - 1, y, color);
        graphics.hLine(x, x + w - 1, y + h - 1, color);
        graphics.vLine(x, y, y + h - 1, color);
        graphics.vLine(x + w - 1, y, y + h - 1, color);
    }

    private static boolean contains(
        int x,
        int y,
        int buttonWidth,
        double mouseX,
        double mouseY
    ) {
        return mouseX >= x && mouseX < x + buttonWidth
            && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private enum Page {
        MAIN("", ""),
        DAILY(
            "gui.tensura_dungeon.expedition_table.daily.title",
            "gui.tensura_dungeon.expedition_table.daily.placeholder"
        ),
        WEEKLY(
            "gui.tensura_dungeon.expedition_table.weekly.title",
            "gui.tensura_dungeon.expedition_table.weekly.placeholder"
        );

        private final String headingKey;
        private final String bodyKey;

        Page(String headingKey, String bodyKey) {
            this.headingKey = headingKey;
            this.bodyKey = bodyKey;
        }
    }
}
