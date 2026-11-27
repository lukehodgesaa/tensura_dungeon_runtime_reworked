package com.mineclawd.tensurahudnative.client;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HudLayout {
    public static final float DESIGN_WIDTH = 1280.0F;
    public static final float DESIGN_HEIGHT = 720.0F;

    public static final Rect QUEST = new Rect(916.0F, 20.0F, 330.0F, 132.0F);
    public static final Rect UTILITY = new Rect(1204.0F, 194.0F, 28.0F, 130.0F);
    public static final Rect SKILLS = new Rect(1125.0F, 385.0F, 90.0F, 155.0F);
    public static final Rect SELECTED = new Rect(1050.0F, 576.0F, 33.0F, 33.0F);
    public static final Rect HOTBAR = new Rect(962.0F, 656.0F, 209.0F, 40.0F);
    public static final Rect STATUS = new Rect(507.0F, 680.0F, 267.0F, 18.0F);
    public static final Point SATURATION = new Point(805.0F, 690.0F);
    public static final Point DEFENSE = new Point(842.0F, 690.0F);
    public static final Point UNLOCK_SIGIL = new Point(792.0F, 108.0F);

    private HudLayout() {
    }

    public static Transform fit(int guiWidth, int guiHeight, float userScale) {
        float safeScale = Math.max(0.65F, Math.min(1.5F, userScale));
        float scale = Math.min(guiWidth / DESIGN_WIDTH, guiHeight / DESIGN_HEIGHT) * safeScale;
        float width = DESIGN_WIDTH * scale;
        float height = DESIGN_HEIGHT * scale;
        return new Transform(scale, (guiWidth - width) * 0.5F, (guiHeight - height) * 0.5F);
    }

    public static Map<String, Rect> namedRects() {
        Map<String, Rect> result = new LinkedHashMap<>();
        result.put("quest", QUEST);
        result.put("utility", UTILITY);
        result.put("skills", SKILLS);
        result.put("selected", SELECTED);
        result.put("hotbar", HOTBAR);
        result.put("status", STATUS);
        return result;
    }

    public record Rect(float x, float y, float width, float height) {
        public float right() {
            return x + width;
        }

        public float bottom() {
            return y + height;
        }
    }

    public record Point(float x, float y) {
    }

    public record Transform(float scale, float offsetX, float offsetY) {
        public Rect apply(Rect rect) {
            return new Rect(offsetX + rect.x * scale, offsetY + rect.y * scale,
                    rect.width * scale, rect.height * scale);
        }
    }
}
