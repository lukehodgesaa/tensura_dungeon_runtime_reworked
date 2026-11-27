package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationPartyActionPayload;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DungeonPreparationInviteScreen extends Screen {
    private final UUID hostId;
    private final String hostName;

    DungeonPreparationInviteScreen(UUID hostId, String hostName) {
        super(Component.literal("Dungeon Party Invitation"));
        this.hostId = hostId;
        this.hostName = hostName;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = height / 2 + 20;
        addRenderableWidget(Button.builder(Component.literal("ACCEPT"), button -> {
            DungeonPreparationPartyClient.act(
                DungeonPreparationPartyActionPayload.Action.ACCEPT_INVITE,
                hostId
            );
            onClose();
        }).bounds(center - 104, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DECLINE"), button -> {
            DungeonPreparationPartyClient.act(
                DungeonPreparationPartyActionPayload.Action.DECLINE_INVITE,
                hostId
            );
            onClose();
        }).bounds(center + 6, y, 98, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE6000711);
        int panelX = width / 2 - 130;
        int panelY = height / 2 - 52;
        graphics.fill(panelX, panelY, panelX + 260, panelY + 112, 0xFF071522);
        graphics.renderOutline(panelX, panelY, 260, 112, 0xFF19C7E8);
        graphics.drawCenteredString(font, "DUNGEON PARTY INVITATION", width / 2, panelY + 18, 0xFF9EEBFF);
        graphics.drawCenteredString(
            font,
            hostName + " invited you to a briefing party.",
            width / 2,
            panelY + 42,
            0xFFE8F7FF
        );
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
