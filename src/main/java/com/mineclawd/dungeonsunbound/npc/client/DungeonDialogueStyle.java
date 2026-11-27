package com.mineclawd.dungeonsunbound.npc.client;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public record DungeonDialogueStyle(
    int fill,
    int edge,
    int top,
    int bottom,
    int side,
    int accent,
    int accentDark,
    int text,
    int selectedFill,
    int selectedBorder,
    Motif leftMotif,
    Motif rightMotif
) {
    public enum Motif {
        NONE,
        ANVIL,
        HAMMER,
        SWORD,
        COIN,
        LEDGER,
        LEAF,
        KNOT,
        ROSE,
        SIGIL
    }

    public static DungeonDialogueStyle forSpeaker(String speaker) {
        String normalized = speaker.toLowerCase(Locale.ROOT);
        if (normalized.contains("kaijin")) {
            return new DungeonDialogueStyle(
                0xD07A452F, 0xFF9A6A48, 0xFFF1C18D, 0xFF3A2322, 0xFFC78D5F,
                0xFFD58B42, 0xFF5F3528, 0xFFF7F1E5, 0xD5956E44, 0xFFE7C28B,
                Motif.ANVIL, Motif.HAMMER
            );
        }
        if (normalized.contains("kurobe")) {
            return new DungeonDialogueStyle(
                0xD0212D3F, 0xFF485C6E, 0xFFE6E0D2, 0xFF1B2530, 0xFF9BA9B1,
                0xFFF07B3C, 0xFF6C2C21, 0xFFF7F1E5, 0xD55A3940, 0xFFFF9A4B,
                Motif.SWORD, Motif.HAMMER
            );
        }
        if (normalized.contains("mjollmile") || normalized.contains("gard")) {
            return new DungeonDialogueStyle(
                0xD03D5A60, 0xFF6E9B9A, 0xFFDCE7D0, 0xFF20333B, 0xFFABC5BC,
                0xFFE0B75B, 0xFF6E5228, 0xFFF7F1E5, 0xD5B19758, 0xFFF2D68E,
                Motif.COIN, Motif.LEDGER
            );
        }
        if (normalized.contains("gobta")) {
            return new DungeonDialogueStyle(
                0xD05C632B, 0xFF87923E, 0xFFE3E693, 0xFF33361F, 0xFFBBC77C,
                0xFFB7C74F, 0xFF4B4F26, 0xFFF7F1E5, 0xD5A3AD42, 0xFFE4E38B,
                Motif.LEAF, Motif.KNOT
            );
        }
        if (normalized.contains("testarossa")) {
            return new DungeonDialogueStyle(
                0xD06A243F, 0xFFB44766, 0xFFFFD9D8, 0xFF3F162B, 0xFFE98FA4,
                0xFFE64C6A, 0xFF7A1938, 0xFFF7F1E5, 0xD5A64362, 0xFFFFB7B8,
                Motif.ROSE, Motif.SIGIL
            );
        }
        if (normalized.contains("treyni") || normalized.contains("dryad") || normalized.contains("jura")) {
            return new DungeonDialogueStyle(
                0xD03D7568, 0xFF8FD0B0, 0xFFE2F4D7, 0xFF1B453D, 0xFFB7D9B6,
                0xFF8BD7A6, 0xFF2F735C, 0xFFF3FFF0, 0xD59BCFA7, 0xFFE7FFE0,
                Motif.LEAF, Motif.KNOT
            );
        }
        return new DungeonDialogueStyle(
            0xD06C7775, 0xFF9CA9A7, 0xFFE0D7BF, 0xFF3B4347, 0xFFD0D7C0,
            0xFFB5C6BA, 0xFF4C5556, 0xFFF7F1E5, 0xD58F765D, 0xFFE7D0A5,
            Motif.NONE, Motif.NONE
        );
    }

    public void drawNameplate(GuiGraphics graphics, Font font, String speaker, int x, int y, int width, int height) {
        DungeonDialogueStyle style = this;
        int right = x + width;

        int centerLeft = x + 28;
        int centerRight = right - 28;

        graphics.fill(x + 9, y + 1, right - 9, y + height - 1, style.accentDark);
        graphics.fill(x + 4, y + 6, right - 4, y + height - 6, style.accentDark);
        graphics.fill(x, y + 12, x + 8, y + height - 12, style.accentDark);
        graphics.fill(right - 8, y + 12, right, y + height - 12, style.accentDark);

        graphics.fill(centerLeft - 2, y + 3, centerRight + 2, y + height - 3, style.edge);
        graphics.fill(centerLeft, y + 5, centerRight, y + height - 5, style.fill);
        graphics.fill(centerLeft + 5, y + 8, centerRight - 5, y + height - 8, style.fill);
        graphics.fill(centerLeft, y + 4, centerRight, y + 6, style.top);
        graphics.fill(centerLeft, y + height - 6, centerRight, y + height - 4, style.bottom);
        graphics.fill(centerLeft + 5, y + 7, centerRight - 5, y + 8, style.accentDark);
        graphics.fill(centerLeft + 5, y + height - 9, centerRight - 5, y + height - 8, style.accent);

        graphics.fill(x + 9, y + 2, x + 21, y + height - 2, style.edge);
        graphics.fill(x + 5, y + 6, x + 25, y + height - 6, style.edge);
        graphics.fill(x + 2, y + 11, x + 9, y + height - 11, style.side);
        graphics.fill(x + 10, y + 4, x + 20, y + height - 4, style.fill);
        graphics.fill(x + 6, y + 8, x + 24, y + height - 8, style.fill);
        graphics.fill(x + 10, y + 2, x + 20, y + 4, style.top);
        graphics.fill(x + 10, y + height - 4, x + 20, y + height - 2, style.bottom);
        graphics.fill(x + 2, y + 14, x + 5, y + height - 14, style.accent);

        graphics.fill(right - 21, y + 2, right - 9, y + height - 2, style.edge);
        graphics.fill(right - 25, y + 6, right - 5, y + height - 6, style.edge);
        graphics.fill(right - 9, y + 11, right - 2, y + height - 11, style.side);
        graphics.fill(right - 20, y + 4, right - 10, y + height - 4, style.fill);
        graphics.fill(right - 24, y + 8, right - 6, y + height - 8, style.fill);
        graphics.fill(right - 20, y + 2, right - 10, y + 4, style.top);
        graphics.fill(right - 20, y + height - 4, right - 10, y + height - 2, style.bottom);
        graphics.fill(right - 5, y + 14, right - 2, y + height - 14, style.accent);

        drawLargeIcon(graphics, x + 6, y + 11, leftMotif);
        drawLargeIcon(graphics, right - 26, y + 11, rightMotif);

        int nameWidth = font.width(speaker);
        int nameX = centerLeft + Math.max(5, (centerRight - centerLeft - nameWidth) / 2);
        graphics.drawString(font, speaker, nameX, y + 14, style.text, true);
    }

    private void drawLargeIcon(GuiGraphics graphics, int x, int y, Motif motif) {
        if (motif == Motif.NONE) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        drawIcon(graphics, 0, 0, motif, this);
        graphics.pose().popPose();
    }

    private static void drawIcon(GuiGraphics graphics, int x, int y, Motif motif, DungeonDialogueStyle style) {
        graphics.fill(x, y, x + 16, y + 16, style.accentDark);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, style.fill);

        switch (motif) {
            case ANVIL -> drawAnvilIcon(graphics, x, y, style);
            case HAMMER -> drawHammerIcon(graphics, x, y, style);
            case SWORD -> drawSwordIcon(graphics, x, y, style);
            case COIN -> drawCoinIcon(graphics, x, y, style);
            case LEDGER -> drawLedgerIcon(graphics, x, y, style);
            case LEAF -> drawLeafIcon(graphics, x, y, style);
            case KNOT -> drawKnotIcon(graphics, x, y, style);
            case ROSE -> drawRoseIcon(graphics, x, y, style);
            case SIGIL -> drawSigilIcon(graphics, x, y, style);
            case NONE -> {
            }
        }
    }

    private static void drawAnvilIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 3, 4, 2, 10, style.accentDark);
        box(graphics, x, y, 5, 2, 6, 2, style.top);
        box(graphics, x, y, 4, 4, 8, 3, style.edge);
        box(graphics, x, y, 5, 7, 6, 3, style.accentDark);
        box(graphics, x, y, 3, 10, 10, 2, style.accentDark);
        box(graphics, x, y, 5, 10, 6, 1, style.accent);
        box(graphics, x, y, 2, 12, 12, 2, style.accentDark);
    }

    private static void drawHammerIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 3, 2, 10, 4, style.accentDark);
        box(graphics, x, y, 4, 3, 8, 2, style.edge);
        box(graphics, x, y, 3, 2, 3, 3, style.top);
        box(graphics, x, y, 8, 5, 2, 8, style.accentDark);
        box(graphics, x, y, 7, 6, 2, 7, style.accent);
        box(graphics, x, y, 6, 12, 4, 2, style.accentDark);
        box(graphics, x, y, 7, 12, 2, 1, style.top);
    }

    private static void drawSwordIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 5, 9, 4, 4, style.accentDark);
        box(graphics, x, y, 7, 7, 4, 4, style.accentDark);
        box(graphics, x, y, 9, 5, 4, 4, style.accentDark);
        box(graphics, x, y, 11, 3, 3, 3, style.accentDark);
        box(graphics, x, y, 6, 9, 2, 2, style.edge);
        box(graphics, x, y, 8, 7, 2, 2, style.top);
        box(graphics, x, y, 10, 5, 2, 2, style.top);
        box(graphics, x, y, 12, 3, 2, 2, style.top);
        box(graphics, x, y, 13, 2, 2, 1, style.top);
        box(graphics, x, y, 3, 11, 7, 2, style.accentDark);
        box(graphics, x, y, 4, 11, 5, 1, style.accent);
        box(graphics, x, y, 2, 13, 4, 2, style.accentDark);
        box(graphics, x, y, 3, 13, 2, 1, style.accent);
        box(graphics, x, y, 1, 15, 3, 1, style.accentDark);
    }

    private static void drawCoinIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 5, 2, 6, 1, style.accentDark);
        box(graphics, x, y, 3, 3, 10, 2, style.accentDark);
        box(graphics, x, y, 2, 5, 12, 6, style.accentDark);
        box(graphics, x, y, 3, 11, 10, 2, style.accentDark);
        box(graphics, x, y, 5, 13, 6, 1, style.accentDark);
        box(graphics, x, y, 5, 4, 6, 7, style.accent);
        box(graphics, x, y, 6, 5, 4, 5, style.top);
        box(graphics, x, y, 7, 6, 2, 3, style.accentDark);
    }

    private static void drawLedgerIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 3, 2, 10, 12, style.accentDark);
        box(graphics, x, y, 4, 3, 8, 10, style.top);
        box(graphics, x, y, 5, 5, 6, 1, style.accentDark);
        box(graphics, x, y, 5, 7, 6, 1, style.edge);
        box(graphics, x, y, 5, 9, 4, 1, style.edge);
        box(graphics, x, y, 4, 12, 8, 1, style.accent);
    }

    private static void drawLeafIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 8, 2, 3, 1, style.accentDark);
        box(graphics, x, y, 6, 3, 5, 2, style.accentDark);
        box(graphics, x, y, 4, 5, 7, 4, style.accentDark);
        box(graphics, x, y, 3, 8, 6, 3, style.accentDark);
        box(graphics, x, y, 5, 4, 5, 2, style.accent);
        box(graphics, x, y, 4, 6, 5, 2, style.top);
        box(graphics, x, y, 7, 8, 2, 4, style.accent);
        box(graphics, x, y, 5, 10, 2, 4, style.edge);
        box(graphics, x, y, 4, 13, 2, 2, style.accentDark);
    }

    private static void drawKnotIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 5, 3, 6, 1, style.accentDark);
        box(graphics, x, y, 3, 4, 10, 2, style.accentDark);
        box(graphics, x, y, 2, 6, 12, 5, style.accentDark);
        box(graphics, x, y, 4, 11, 8, 2, style.accentDark);
        box(graphics, x, y, 5, 5, 6, 1, style.accent);
        box(graphics, x, y, 4, 7, 2, 2, style.top);
        box(graphics, x, y, 10, 7, 2, 2, style.edge);
        box(graphics, x, y, 7, 9, 2, 2, style.accent);
    }

    private static void drawRoseIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 5, 2, 6, 1, style.accentDark);
        box(graphics, x, y, 3, 3, 10, 2, style.accentDark);
        box(graphics, x, y, 2, 5, 12, 5, style.accentDark);
        box(graphics, x, y, 4, 10, 8, 3, style.accentDark);
        box(graphics, x, y, 5, 4, 6, 6, style.accent);
        box(graphics, x, y, 4, 6, 2, 2, style.top);
        box(graphics, x, y, 8, 5, 3, 2, style.top);
        box(graphics, x, y, 7, 7, 2, 2, style.accentDark);
        box(graphics, x, y, 7, 12, 2, 2, style.edge);
    }

    private static void drawSigilIcon(GuiGraphics graphics, int x, int y, DungeonDialogueStyle style) {
        box(graphics, x, y, 7, 2, 2, 2, style.top);
        box(graphics, x, y, 5, 4, 6, 2, style.accent);
        box(graphics, x, y, 3, 6, 10, 2, style.accentDark);
        box(graphics, x, y, 5, 8, 6, 2, style.accent);
        box(graphics, x, y, 7, 10, 2, 4, style.top);
        box(graphics, x, y, 4, 13, 8, 1, style.accentDark);
    }

    private static void box(
        GuiGraphics graphics,
        int x,
        int y,
        int gridX,
        int gridY,
        int width,
        int height,
        int color
    ) {
        graphics.fill(x + gridX, y + gridY, x + gridX + width, y + gridY + height, color);
    }

    public static void drawContinueIcon(GuiGraphics graphics, int centerX, int centerY) {
        int radius = 13;
        for (int offset = -radius; offset <= radius; offset++) {
            int span = (int) Math.sqrt(radius * radius - offset * offset);
            graphics.fill(centerX - span, centerY + offset, centerX + span + 1, centerY + offset + 1, 0xE8F1EBDD);
        }

        int dark = 0xFF4C5556;
        graphics.fill(centerX - 5, centerY - 4, centerX - 2, centerY - 1, dark);
        graphics.fill(centerX + 2, centerY - 4, centerX + 5, centerY - 1, dark);
        graphics.fill(centerX - 4, centerY + 4, centerX + 5, centerY + 6, dark);
        graphics.fill(centerX - 1, centerY + 2, centerX + 2, centerY + 5, 0xE8F1EBDD);
    }
}
