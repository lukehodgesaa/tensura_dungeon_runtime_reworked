package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.CoreRecoveryClaimRequestPayload;
import com.mineclawd.tensuradungeon.network.CoreRecoveryClaimResultPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CoreSatchelScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int PANEL = 0xF20A0811;
    private static final int PANEL_INSET = 0xF0141020;
    private static final int INDIGO = 0xFF3A2653;
    private static final int VIOLET = 0xFF8E4DD1;
    private static final int GOLD = 0xFFD8AE55;
    private static final int TEXT = 0xFFEDE6F7;
    private static final int MUTED = 0xFFA99BB7;
    private static final int UPGRADE = 0xFF65E894;
    private static final int DOWNGRADE = 0xFFFF6955;
    private static final int SLOT_BACK = 0xFF09080D;
    private static final int SLOT_INSET = 0xFF17131D;
    private static final int[] TIER_COLORS = {
        0xFF65D8FF, 0xFF65E894, 0xFF4C8DFF, 0xFFB060FF, 0xFFFF6955, 0xFFFFD85C
    };
    private static final ResourceLocation[] CORE_TEXTURES = {
        coreTexture(1), coreTexture(2), coreTexture(3),
        coreTexture(4), coreTexture(5), coreTexture(6)
    };
    private static final ResourceLocation CORE_TOOLTIP_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID,
        "textures/gui/core_satchel/core_tooltip_arcane_seal.png"
    );
    private static final int CORE_TOOLTIP_WIDTH = 256;
    private static final int CORE_TOOLTIP_HEIGHT = 80;

    private final CoreSatchelView view;
    private final Screen parent;
    private final PendingClaim pendingClaim;
    private int selectedSlot;
    private boolean claimRequestPending;
    private CoreRecoveryClaimRequestPayload.Action pendingAction;
    private String placementMessage = "";

    public CoreSatchelScreen(CoreSatchelView view) {
        this(view, null);
    }

    public CoreSatchelScreen(CoreSatchelView view, Screen parent) {
        this(view, parent, null);
    }

    CoreSatchelScreen(CoreSatchelView view, Screen parent, PendingClaim pendingClaim) {
        super(Component.translatable("screen.tensura_dungeon.core_satchel.title", view.gradeName()));
        this.view = view;
        this.parent = parent;
        this.pendingClaim = pendingClaim;
        this.selectedSlot = -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xA8000005);
        CoreSatchelLayout layout = CoreSatchelLayout.create(view.capacity(), width, height);
        drawPanel(graphics, layout);

        int hoveredSlot = -1;
        for (int slot = 0; slot < view.capacity(); slot++) {
            boolean hovered = layout.containsSlot(slot, mouseX, mouseY);
            if (hovered) hoveredSlot = slot;
            drawSlot(graphics, layout, slot, hovered);
        }

        drawHeader(graphics, layout);
        drawFooter(graphics, layout);
        boolean comparingReplacement = pendingClaim != null
            && selectedSlot >= 0
            && view.coreAt(selectedSlot).isPresent();
        if (comparingReplacement) {
            drawReplacementComparison(graphics, layout, selectedSlot, view.coreAt(selectedSlot).orElseThrow());
        } else if (hoveredSlot >= 0) {
            int finalHoveredSlot = hoveredSlot;
            view.coreAt(hoveredSlot).ifPresent(core ->
                drawCoreTooltip(graphics, core, finalHoveredSlot, mouseX, mouseY)
            );
        }
    }

    private void drawPanel(GuiGraphics graphics, CoreSatchelLayout layout) {
        int x = layout.panelX();
        int y = layout.panelY();
        int w = layout.panelWidth();
        int h = layout.panelHeight();

        graphics.fill(x + 3, y, x + w - 3, y + h, PANEL);
        graphics.fill(x, y + 3, x + w, y + h - 3, PANEL);
        graphics.fillGradient(x + 4, y + 4, x + w - 4, y + h - 4, PANEL_INSET, 0xF20A0710);
        outline(graphics, x, y, w, h, GOLD);
        outline(graphics, x + 3, y + 3, w - 6, h - 6, INDIGO);
        graphics.fill(x + 10, y + 4, x + w - 10, y + 6, VIOLET);
        drawCornerOrnaments(graphics, x, y, w, h);
    }

    private void drawHeader(GuiGraphics graphics, CoreSatchelLayout layout) {
        int y = layout.panelY() + 10;
        graphics.drawString(font, title, layout.panelX() + 12, y, GOLD, true);
        String usage = view.occupiedCount() + " / " + view.capacity();
        graphics.drawString(
            font,
            usage,
            layout.panelX() + layout.panelWidth() - 12 - font.width(usage),
            y,
            TEXT,
            true
        );
    }

    private void drawFooter(GuiGraphics graphics, CoreSatchelLayout layout) {
        Component help = pendingClaim == null
            ? Component.translatable("screen.tensura_dungeon.core_satchel.help")
            : Component.literal(claimRequestPending
                ? pendingAction == CoreRecoveryClaimRequestPayload.Action.REPLACE
                    ? "Replacing Core..."
                    : "Storing Core..."
                : placementMessage.isBlank()
                    ? view.occupiedCount() >= view.capacity()
                        ? "Select Core to replace  •  Esc Back"
                        : "Empty: Store  •  Filled: Replace"
                    : placementMessage);
        int y = layout.panelY() + layout.panelHeight() - 15;
        graphics.drawCenteredString(
            font,
            help,
            layout.panelX() + layout.panelWidth() / 2,
            y,
            placementMessage.isBlank() ? MUTED : GOLD
        );
    }

    private void drawSlot(
        GuiGraphics graphics,
        CoreSatchelLayout layout,
        int slot,
        boolean hovered
    ) {
        int x = layout.slotX(slot);
        int y = layout.slotY(slot);
        int size = layout.slotSize();
        boolean selected = slot == selectedSlot;
        boolean placementEmpty = pendingClaim != null && view.coreAt(slot).isEmpty();
        int selectedColor = pendingClaim == null
            ? view.coreAt(slot).map(core -> TIER_COLORS[core.tier() - 1]).orElse(GOLD)
            : TIER_COLORS[pendingClaim.tier() - 1];

        int outer = selected
            ? selectedColor
                : hovered && pendingClaim != null
                ? selectedColor
                : placementEmpty
                    ? dimmed(selectedColor)
                    : hovered && pendingClaim == null ? VIOLET : 0xFF342B3C;
        graphics.fill(x, y, x + size, y + size, SLOT_BACK);
        outline(graphics, x, y, size, size, outer);
        outline(graphics, x + 2, y + 2, size - 4, size - 4, 0xFF211A28);
        graphics.fill(x + 3, y + 3, x + size - 3, y + size - 3, SLOT_INSET);

        view.coreAt(slot).ifPresent(core -> drawCore(graphics, core, x, y, size));
    }

    private void drawCore(
        GuiGraphics graphics,
        CoreSatchelView.CoreEntry core,
        int slotX,
        int slotY,
        int slotSize
    ) {
        int iconSize = Math.min(18, slotSize - 7);
        int iconX = slotX + (slotSize - iconSize) / 2;
        int iconY = slotY + 2;
        int animationFrame = (int) ((System.currentTimeMillis() / 125L) % 8L);
        graphics.blit(
            CORE_TEXTURES[core.tier() - 1],
            iconX,
            iconY,
            iconSize,
            iconSize,
            0.0F,
            animationFrame * 32.0F,
            32,
            32,
            32,
            256
        );

        String tier = romanTier(core.tier());
        graphics.drawString(font, tier, slotX + 3, slotY + 3, TIER_COLORS[core.tier() - 1], true);

        int barX = slotX + 3;
        int barY = slotY + slotSize - 6;
        int barWidth = slotSize - 6;
        int barInnerWidth = barWidth - 2;
        int fillWidth = (int) Math.round(barInnerWidth * core.chargeFraction());
        outline(graphics, barX, barY, barWidth, 4, 0xFF5C5263);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 3, 0xFF211C25);
        if (fillWidth > 0) {
            graphics.fill(
                barX + 1,
                barY + 1,
                barX + 1 + fillWidth,
                barY + 3,
                TIER_COLORS[core.tier() - 1]
            );
        }
    }

    private void drawReplacementComparison(
        GuiGraphics graphics,
        CoreSatchelLayout layout,
        int slot,
        CoreSatchelView.CoreEntry stored
    ) {
        int boxWidth = 230;
        int boxHeight = 104;
        int x = layout.panelX() + layout.panelWidth() + 8;
        if (x + boxWidth > width - 4) {
            x = layout.panelX() - boxWidth - 8;
        }
        int y = layout.panelY() + Math.max(0, (layout.panelHeight() - boxHeight) / 2);
        if (x < 4) {
            x = Math.max(4, (width - boxWidth) / 2);
            y = Math.max(4, layout.panelY() - boxHeight - 8);
        }

        graphics.fill(x, y, x + boxWidth, y + boxHeight, PANEL);
        graphics.fillGradient(
            x + 3, y + 3, x + boxWidth - 3, y + boxHeight - 3, PANEL_INSET, 0xF20A0710
        );
        outline(graphics, x, y, boxWidth, boxHeight, GOLD);
        graphics.drawCenteredString(
            font, "REPLACE SLOT " + (slot + 1) + "?", x + boxWidth / 2, y + 6, GOLD
        );

        int lineY = y + 20;
        graphics.drawString(
            font,
            "RECOVERED  •  TIER " + romanTier(pendingClaim.tier()),
            x + 8,
            lineY,
            TIER_COLORS[pendingClaim.tier() - 1],
            true
        );
        graphics.drawString(
            font,
            "Total " + formatted(pendingClaim.totalCharge()) + " / " + formatted(pendingClaim.capacity()),
            x + 12,
            lineY + 11,
            comparisonColor(pendingClaim.totalCharge(), stored.totalCharge()),
            false
        );
        graphics.drawString(
            font,
            "Natural " + formatted(pendingClaim.naturalCharge()),
            x + 12,
            lineY + 22,
            comparisonColor(pendingClaim.naturalCharge(), stored.naturalCharge()),
            false
        );

        lineY += 39;
        graphics.drawString(
            font,
            "STORED  •  TIER " + romanTier(stored.tier()),
            x + 8,
            lineY,
            TIER_COLORS[stored.tier() - 1],
            true
        );
        graphics.drawString(
            font,
            "Total " + formatted(stored.totalCharge()) + " / " + formatted(stored.capacity()),
            x + 12,
            lineY + 11,
            TEXT,
            false
        );
        graphics.drawString(
            font,
            "Natural " + formatted(stored.naturalCharge()),
            x + 12,
            lineY + 22,
            TEXT,
            false
        );
        graphics.drawCenteredString(
            font,
            "CLICK SELECTED SLOT AGAIN TO CONFIRM",
            x + boxWidth / 2,
            y + boxHeight - 11,
            0xFFFF6955
        );
    }

    private void drawCoreTooltip(
        GuiGraphics graphics,
        CoreSatchelView.CoreEntry core,
        int slot,
        int mouseX,
        int mouseY
    ) {
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(new TooltipLine(
            Component.translatable("screen.tensura_dungeon.core_satchel.core_tier", romanTier(core.tier())),
            TIER_COLORS[core.tier() - 1]
        ));
        lines.add(new TooltipLine(
            Component.translatable(
                "screen.tensura_dungeon.core_satchel.core_technique",
                core.displayName()
            ),
            VIOLET
        ));
        lines.add(new TooltipLine(
            Component.translatable(
                "screen.tensura_dungeon.core_satchel.total_charge",
                formatted(core.totalCharge()),
                formatted(core.capacity())
            ),
            TEXT
        ));
        lines.add(new TooltipLine(
            Component.translatable(
                "screen.tensura_dungeon.core_satchel.natural_charge",
                formatted(core.naturalCharge())
            ),
            TEXT
        ));
        lines.add(new TooltipLine(
            Component.translatable("screen.tensura_dungeon.core_satchel.recharge_warning"),
            GOLD
        ));

        int tooltipWidth = CORE_TOOLTIP_WIDTH;
        int tooltipHeight = CORE_TOOLTIP_HEIGHT;
        int x = Math.min(mouseX + 10, width - tooltipWidth - 4);
        int y = Math.min(mouseY + 10, height - tooltipHeight - 4);
        x = Math.max(4, x);
        y = Math.max(4, y);

        graphics.blit(
            CORE_TOOLTIP_TEXTURE,
            x,
            y,
            tooltipWidth,
            tooltipHeight,
            0.0F,
            0.0F,
            CORE_TOOLTIP_WIDTH,
            CORE_TOOLTIP_HEIGHT,
            CORE_TOOLTIP_WIDTH,
            CORE_TOOLTIP_HEIGHT
        );
        int lineY = y + 11;
        for (TooltipLine line : lines) {
            graphics.drawString(font, line.text(), x + 14, lineY, line.color(), true);
            lineY += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            CoreSatchelLayout layout = CoreSatchelLayout.create(view.capacity(), width, height);
            for (int slot = 0; slot < view.capacity(); slot++) {
                if (layout.containsSlot(slot, mouseX, mouseY)) {
                    if (pendingClaim != null) {
                        if (claimRequestPending) return true;
                        if (view.coreAt(slot).isPresent()) {
                            if (selectedSlot != slot) {
                                selectedSlot = slot;
                                placementMessage = "Click again to confirm";
                                return true;
                            }
                            claimRequestPending = true;
                            pendingAction = CoreRecoveryClaimRequestPayload.Action.REPLACE;
                            placementMessage = "";
                            PacketDistributor.sendToServer(new CoreRecoveryClaimRequestPayload(
                                pendingClaim.sessionId(),
                                pendingClaim.floor(),
                                pendingClaim.coreId(),
                                slot,
                                pendingAction
                            ));
                            return true;
                        }
                        selectedSlot = slot;
                        claimRequestPending = true;
                        pendingAction = CoreRecoveryClaimRequestPayload.Action.STORE;
                        placementMessage = "";
                        PacketDistributor.sendToServer(new CoreRecoveryClaimRequestPayload(
                            pendingClaim.sessionId(),
                            pendingClaim.floor(),
                            pendingClaim.coreId(),
                            slot,
                            pendingAction
                        ));
                        return true;
                    }
                    if (view.coreAt(slot).isPresent()) {
                        selectedSlot = slot;
                        return true;
                    }
                    break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    void handleClaimResult(CoreRecoveryClaimResultPayload result) {
        if (pendingClaim == null || !pendingClaim.coreId().equals(result.coreId())) return;
        claimRequestPending = false;
        pendingAction = null;
        if (result.status() == com.mineclawd.tensuradungeon.runtime.CoreRecoveryData.ClaimStatus.CLAIMED
            || result.status() == com.mineclawd.tensuradungeon.runtime.CoreRecoveryData.ClaimStatus.REPLACED) {
            Minecraft.getInstance().setScreen(parent);
            return;
        }
        placementMessage = result.message();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static ResourceLocation coreTexture(int tier) {
        return ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "textures/item/monster_crystal_tier_" + tier + ".png"
        );
    }

    private static void drawCornerOrnaments(GuiGraphics graphics, int x, int y, int w, int h) {
        int[][] offsets = {{0, 0}, {w - 7, 0}, {0, h - 7}, {w - 7, h - 7}};
        for (int[] offset : offsets) {
            int cornerX = x + offset[0];
            int cornerY = y + offset[1];
            graphics.fill(cornerX, cornerY + 2, cornerX + 7, cornerY + 5, GOLD);
            graphics.fill(cornerX + 2, cornerY, cornerX + 5, cornerY + 7, GOLD);
            graphics.fill(cornerX + 2, cornerY + 2, cornerX + 5, cornerY + 5, PANEL);
        }
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.hLine(x, x + w - 1, y, color);
        graphics.hLine(x, x + w - 1, y + h - 1, color);
        graphics.vLine(x, y, y + h - 1, color);
        graphics.vLine(x + w - 1, y, y + h - 1, color);
    }

    private static String romanTier(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> throw new IllegalArgumentException("Unsupported Core Tier: " + tier);
        };
    }

    private static String formatted(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static int dimmed(int color) {
        int red = ((color >>> 16) & 0xFF) / 2;
        int green = ((color >>> 8) & 0xFF) / 2;
        int blue = (color & 0xFF) / 2;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int comparisonColor(long incoming, long stored) {
        return incoming > stored ? UPGRADE : incoming < stored ? DOWNGRADE : TEXT;
    }

    private record TooltipLine(Component text, int color) {}

    record PendingClaim(
        String sessionId,
        int floor,
        UUID coreId,
        int tier,
        long totalCharge,
        long capacity,
        long naturalCharge
    ) {
        PendingClaim {
            if (sessionId == null || sessionId.isBlank() || coreId == null) {
                throw new IllegalArgumentException("Pending recovered Core identity is required");
            }
            if (floor <= 0 || tier < 1 || tier > 6) {
                throw new IllegalArgumentException("Pending recovered Core provenance is invalid");
            }
            if (capacity <= 0L || totalCharge < 0L || totalCharge > capacity
                || naturalCharge < 0L || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Pending recovered Core charge is invalid");
            }
        }
    }
}
