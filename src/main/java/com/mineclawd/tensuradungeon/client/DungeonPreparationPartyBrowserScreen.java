package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationPartyActionPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationPartySnapshotPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DungeonPreparationPartyBrowserScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private final DungeonPreparationScreen parent;
    private boolean subordinates;
    private EditBox search;
    private int scroll;

    DungeonPreparationPartyBrowserScreen(DungeonPreparationScreen parent) {
        super(Component.literal("Invite Party Member"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = width / 2 - 150;
        int panelY = height / 2 - 105;
        search = new EditBox(font, panelX + 14, panelY + 42, 272, 20, Component.literal("Search"));
        search.setHint(Component.literal("Search nearby candidates"));
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> onClose())
            .bounds(panelX + 14, panelY + 184, 70, 20).build());
        DungeonPreparationPartyClient.refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE6000711);
        int panelX = width / 2 - 150;
        int panelY = height / 2 - 105;
        graphics.fill(panelX, panelY, panelX + 300, panelY + 220, 0xFF071522);
        graphics.renderOutline(panelX, panelY, 300, 220, 0xFF19C7E8);
        graphics.drawString(font, "INVITE MEMBER", panelX + 14, panelY + 14, 0xFFE8F7FF, true);
        graphics.drawString(
            font,
            "PLAYERS",
            panelX + 120,
            panelY + 16,
            subordinates ? 0xFF516273 : 0xFF38D9FF
        );
        graphics.drawString(
            font,
            "SUBORDINATES",
            panelX + 184,
            panelY + 16,
            subordinates ? 0xFF38D9FF : 0xFF516273
        );
        List<DungeonPreparationPartySnapshotPayload.Candidate> candidates = candidates();
        int listTop = panelY + 70;
        int listBottom = panelY + 174;
        graphics.enableScissor(panelX + 10, listTop, panelX + 290, listBottom);
        int y = listTop - scroll;
        for (DungeonPreparationPartySnapshotPayload.Candidate candidate : candidates) {
            boolean hovered = mouseX >= panelX + 14 && mouseX < panelX + 286
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
            graphics.fill(
                panelX + 14,
                y,
                panelX + 286,
                y + ROW_HEIGHT - 2,
                hovered ? 0xFF10334A : 0xFF0A1B2A
            );
            graphics.drawString(font, candidate.name(), panelX + 24, y + 7, 0xFFE8F7FF);
            graphics.drawString(font, "INVITE", panelX + 240, y + 7, 0xFF38D9FF);
            y += ROW_HEIGHT;
        }
        if (candidates.isEmpty()) {
            graphics.drawCenteredString(
                font,
                subordinates ? "NO OWNED SUBORDINATES FOUND NEARBY" : "NO PLAYERS FOUND NEARBY",
                width / 2,
                listTop + 38,
                0xFF748495
            );
        }
        graphics.disableScissor();
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = width / 2 - 150;
        int panelY = height / 2 - 105;
        if (button == 0 && mouseY >= panelY + 10 && mouseY < panelY + 36) {
            if (mouseX >= panelX + 110 && mouseX < panelX + 180) {
                subordinates = false;
                scroll = 0;
                return true;
            }
            if (mouseX >= panelX + 180 && mouseX < panelX + 290) {
                subordinates = true;
                scroll = 0;
                return true;
            }
        }
        int listTop = panelY + 70;
        if (button == 0 && mouseX >= panelX + 14 && mouseX < panelX + 286
            && mouseY >= listTop && mouseY < panelY + 174) {
            int index = ((int)mouseY - listTop + scroll) / ROW_HEIGHT;
            List<DungeonPreparationPartySnapshotPayload.Candidate> candidates = candidates();
            if (index >= 0 && index < candidates.size()) {
                DungeonPreparationPartySnapshotPayload.Candidate candidate = candidates.get(index);
                DungeonPreparationPartyClient.act(
                    subordinates
                        ? DungeonPreparationPartyActionPayload.Action.ADD_SUBORDINATE
                        : DungeonPreparationPartyActionPayload.Action.INVITE_PLAYER,
                    candidate.id()
                );
                onClose();
                return true;
            }
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
        int max = Math.max(0, candidates().size() * ROW_HEIGHT - 104);
        scroll = Math.max(0, Math.min(max, scroll + (int)Math.round(-scrollY * ROW_HEIGHT)));
        return true;
    }

    private List<DungeonPreparationPartySnapshotPayload.Candidate> candidates() {
        DungeonPreparationPartySnapshotPayload snapshot = DungeonPreparationPartyClient.latest();
        if (snapshot == null) return List.of();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<DungeonPreparationPartySnapshotPayload.Candidate> source = subordinates
            ? snapshot.subordinateCandidates()
            : snapshot.playerCandidates();
        if (query.isBlank()) return source;
        return source.stream()
            .filter(candidate -> candidate.name().toLowerCase(Locale.ROOT).contains(query))
            .toList();
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
