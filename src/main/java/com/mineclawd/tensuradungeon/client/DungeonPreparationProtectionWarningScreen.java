package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationProtectionWarningPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class DungeonPreparationProtectionWarningScreen extends Screen
    implements DungeonHudSuppressingScreen {
    private static final int BACKGROUND = 0xE6000711;
    private static final int PANEL = 0xFF071522;
    private static final int BORDER = 0xFFFFB341;
    private static final int TITLE = 0xFFFFD27A;
    private static final int TEXT = 0xFFE8F7FF;

    private final Screen parent;
    private final DungeonPreparationProtectionWarningPayload payload;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    DungeonPreparationProtectionWarningScreen(
        Screen parent,
        DungeonPreparationProtectionWarningPayload payload
    ) {
        super(Component.literal("Ready Without All Protection?"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(390, Math.max(280, width - 24));
        panelHeight = Math.min(210, Math.max(160, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int buttonY = panelY + panelHeight - 32;
        int gap = 10;
        int buttonWidth = Math.min(160, (panelWidth - 34 - gap) / 2);
        int left = panelX + (panelWidth - buttonWidth * 2 - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal("CONTINUE AND READY"), ignored -> {
            DungeonPreparationPartyClient.confirmReady(payload);
            minecraft.setScreen(parent);
        }).bounds(left, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REVIEW LOADOUT"), ignored ->
            minecraft.setScreen(parent)
        ).bounds(left + buttonWidth + gap, buttonY, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(
            panelX,
            panelY,
            panelX + panelWidth,
            panelY + panelHeight,
            PANEL
        );
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, BORDER);
        graphics.drawCenteredString(
            font,
            "READY WITHOUT ALL PROTECTION?",
            width / 2,
            panelY + 16,
            TITLE
        );
        List<FormattedCharSequence> lines = font.split(
            Component.literal(payload.message()),
            panelWidth - 28
        );
        int y = panelY + 39;
        int maxY = panelY + panelHeight - 43;
        for (FormattedCharSequence line : lines) {
            if (y + font.lineHeight > maxY) {
                break;
            }
            graphics.drawString(font, line, panelX + 14, y, TEXT, false);
            y += font.lineHeight + 2;
        }
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
