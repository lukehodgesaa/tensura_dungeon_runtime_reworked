package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonExitPromptPayload;
import com.mineclawd.tensuradungeon.network.DungeonUiRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DungeonExitPromptScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 194;
    private static final int BUTTON_WIDTH = 122;
    private static final int BUTTON_HEIGHT = 28;
    private static final int BLUE = 0xFF35A9FF;
    private static final int GREEN = 0xFF1EE4A0;
    private static final int TEXT = 0xFFD4E6FF;
    private static final int MUTED = 0xFF8297B4;

    private final DungeonExitPromptPayload prompt;
    private ExitChoice pendingExit = ExitChoice.NONE;

    DungeonExitPromptScreen(DungeonExitPromptPayload prompt) {
        super(Component.literal("Floor Complete"));
        this.prompt = prompt;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB8000309);
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xF7051020);
        outline(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF245A91);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 3, BLUE);

        if (pendingExit != ExitChoice.NONE) {
            renderConfirmation(graphics, x, y, mouseX, mouseY);
            return;
        }

        graphics.drawCenteredString(font, "FLOOR " + prompt.completedFloor() + " COMPLETE", width / 2, y + 22, 0xFFFFB730);
        graphics.drawCenteredString(font, "Choose your destination", width / 2, y + 52, TEXT);
        graphics.drawCenteredString(font, "Floor " + prompt.nextFloor() + " is ready from the dungeon guide.", width / 2, y + 70, MUTED);

        int buttonY = y + 116;
        int hubX = x + 20;
        int homeX = x + PANEL_WIDTH - 20 - BUTTON_WIDTH;
        drawButton(graphics, hubX, buttonY, "RETURN TO HUB", GREEN, contains(hubX, buttonY, mouseX, mouseY));
        drawButton(graphics, homeX, buttonY, "RETURN HOME", BLUE, contains(homeX, buttonY, mouseX, mouseY));
        graphics.drawCenteredString(font, "ESC to remain in the completed floor", width / 2, y + 166, MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        if (pendingExit != ExitChoice.NONE) {
            int buttonY = panelY + 146;
            if (contains(x + 20, buttonY, mouseX, mouseY)) {
                pendingExit = ExitChoice.NONE;
                return true;
            }
            if (contains(x + PANEL_WIDTH - 20 - BUTTON_WIDTH, buttonY, mouseX, mouseY)) {
                DungeonUiSnapshotClient.request(pendingExit.action);
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            return true;
        }
        int y = panelY + 116;
        if (contains(x + 20, y, mouseX, mouseY)) {
            pendingExit = ExitChoice.HUB;
            return true;
        }
        if (contains(x + PANEL_WIDTH - 20 - BUTTON_WIDTH, y, mouseX, mouseY)) {
            pendingExit = ExitChoice.HOME;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderConfirmation(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawCenteredString(font,
            pendingExit == ExitChoice.HOME ? "RETURN HOME?" : "RETURN TO HUB?",
            width / 2, y + 20, 0xFFFFB730);
        graphics.drawCenteredString(font,
            pendingExit == ExitChoice.HOME ? "This ends your active dungeon run." : "You will leave this completed floor.",
            width / 2, y + 47, TEXT);
        graphics.drawCenteredString(font, "Items left behind cannot be collected.", width / 2, y + 67, TEXT);
        graphics.drawCenteredString(font, "Physical drops are removed after", width / 2, y + 82, MUTED);
        graphics.drawCenteredString(font, "the last party member leaves.", width / 2, y + 98, MUTED);
        graphics.drawCenteredString(font, "Secured dungeon-haul rewards are safe.", width / 2, y + 115, GREEN);

        int buttonY = y + 146;
        int stayX = x + 20;
        int confirmX = x + PANEL_WIDTH - 20 - BUTTON_WIDTH;
        drawButton(graphics, stayX, buttonY, "STAY", GREEN, contains(stayX, buttonY, mouseX, mouseY));
        drawButton(graphics, confirmX, buttonY,
            pendingExit == ExitChoice.HOME ? "RETURN HOME" : "RETURN TO HUB",
            BLUE, contains(confirmX, buttonY, mouseX, mouseY));
    }

    private void drawButton(GuiGraphics graphics, int x, int y, String label, int accent, boolean hovered) {
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? 0xFF102B45 : 0xFF081829);
        outline(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, accent);
        graphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2, y + 10, hovered ? 0xFFFFFFFF : accent);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.hLine(x, x + w - 1, y, color);
        graphics.hLine(x, x + w - 1, y + h - 1, color);
        graphics.vLine(x, y, y + h - 1, color);
        graphics.vLine(x + w - 1, y, y + h - 1, color);
    }

    private static boolean contains(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    @Override
    public void onClose() {
        if (pendingExit != ExitChoice.NONE) {
            pendingExit = ExitChoice.NONE;
            return;
        }
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum ExitChoice {
        NONE(null),
        HUB(DungeonUiRequestPayload.Action.PORTAL_CONTINUE),
        HOME(DungeonUiRequestPayload.Action.PORTAL_RETURN_HOME);

        private final DungeonUiRequestPayload.Action action;

        ExitChoice(DungeonUiRequestPayload.Action action) {
            this.action = action;
        }
    }
}
