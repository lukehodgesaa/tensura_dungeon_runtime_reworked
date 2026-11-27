package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Size;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class DungeonShopLdLib2Screen extends ModularUIScreen {
    private static final int LOGICAL_WIDTH = 960;
    private static final int LOGICAL_HEIGHT = 560;
    private static final int TOP_REVEAL_ELEMENT_HEIGHT = 15;
    private static final int CONTENT_LEFT_INSET = 14;
    private static final int DETAIL_PANEL_HEIGHT = 386;
    private static final int DETAIL_CONTENT_HEIGHT = 362;
    private static final int DETAIL_ART_HEIGHT = 154;
    private static final int PURCHASE_AREA_HEIGHT = 52;
    private static final int CYAN = 0xff49d7ff;
    private static final int GOLD = 0xffffc447;
    private static final int VIOLET = 0xffc77dff;
    private static final int SUCCESS_GREEN = 0xff65d69a;
    private static final int TEXT = 0xffe7edf8;
    private static final int MUTED = 0xff9aa8bf;
    private static final int DIM = 0xff66738b;
    private static final int GRADE_I_COLOR = 0xff6bd7ff;
    private static final int MATERIAL_NAME_COLOR = 0xffa9d8ff;
    private static final int SHOP_BASE_COLOR = 0xff020c26;
    private static final int ITEM_GROUP_BOX_COLOR = 0xff000e24;
    private static final int PREVIEW_BOX_COLOR = 0xff020c26;
    private static final int HEADER_BOX_COLOR = 0xff020c26;
    private static final int SURFACE_RAISED = 0xff0a1a31;
    private static final int SEARCH_FIELD_COLOR = 0xff06172f;
    private static final int HAIRLINE = 0xff1b617e;
    private static final int SECTION_BORDER_COLOR = 0xff2f7fa8;
    private static final int SECTION_SURFACE = ITEM_GROUP_BOX_COLOR;
    private static final int PREVIEW_SURFACE = PREVIEW_BOX_COLOR;
    private static final int BODY_SURFACE = SHOP_BASE_COLOR;
    private static final int HEADER_SURFACE = HEADER_BOX_COLOR;
    private static final float ROW_ITEM_TEXTURE_SCALE = 0.66f;
    private static final float ROW_ITEM_MAX_SIZE = 28f;
    private static final int PREVIEW_PEDESTAL_SOURCE_WIDTH = 256;
    private static final int PREVIEW_PEDESTAL_SOURCE_HEIGHT = 128;
    private static final float PREVIEW_ITEM_ANCHOR_SOURCE_X = 140f;
    private static final float PREVIEW_ITEM_ANCHOR_SOURCE_Y = 58f;
    private static final float PREVIEW_ITEM_MAX_SIZE = 96f;
    private static final ResourceLocation SHOP_TOP_FOLIAGE_OVERLAY =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/themes/moonlit_elven_foliage_top_v1.png"
        );
    private static final ResourceLocation SHOP_BOTTOM_LEFT_FOLIAGE_OVERLAY =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/themes/moonlit_elven_foliage_bottom_left_front_v1.png"
        );
    private static final ResourceLocation SHOP_BOTTOM_RIGHT_FOLIAGE_OVERLAY =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/themes/moonlit_elven_foliage_bottom_right_back_v1.png"
        );
    private static final ResourceLocation ITEM_SOCKET_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/item_socket_chamfered_frosted_blue.png"
        );
    private static final ResourceLocation PREVIEW_PEDESTAL_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/preview_pedestal_darkstone.png"
        );
    private static final SpriteTexture SHOP_SHELL_FRAME_TEXTURE = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_shell_frame_rune_hook.png"
        )
    ).setBorder(32);
    private static final SpriteTexture SHOP_PANEL_FRAME_TEXTURE = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_panel_frame_darkstone.png"
        )
    ).setBorder(16);
    private static final ResourceLocation SHOP_TITLE_FLANK_LEFT =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_title_flank_handmade_leaf_arrow_left.png"
        );
    private static final ResourceLocation SHOP_TITLE_FLANK_RIGHT =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_title_flank_handmade_leaf_arrow_right.png"
        );
    private static final ResourceLocation SHOP_TITLE_LOGO =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_title_logo_gothic.png"
        );
    private static final ResourceLocation SHOP_TAB_INDICATOR_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_tab_indicator_mask.png"
        );
    private static final SpriteTexture SHOP_PURCHASE_BUTTON_BASE = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_purchase_button_base.png"
        )
    ).setBorder(4);
    private static final SpriteTexture SHOP_PURCHASE_BUTTON_HOVER = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_purchase_button_hover.png"
        )
    ).setBorder(4);
    private static final SpriteTexture SHOP_PURCHASE_BUTTON_PRESSED = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_purchase_button_pressed.png"
        )
    ).setBorder(4);
    private static final SpriteTexture SHOP_PURCHASE_BUTTON_DISABLED = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_purchase_button_disabled.png"
        )
    ).setBorder(4);
    private static final SpriteTexture SHOP_SCROLL_TRACK_V = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_scroll_track_v.png"
        )
    ).setBorder(0, 3, 0, 3);
    private static final SpriteTexture SHOP_SCROLL_THUMB_BASE_V = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_scroll_thumb_base_v.png"
        )
    ).setBorder(0, 3, 0, 3);
    private static final SpriteTexture SHOP_SCROLL_THUMB_HOVER_V = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_scroll_thumb_hover_v.png"
        )
    ).setBorder(0, 3, 0, 3);
    private static final SpriteTexture SHOP_SCROLL_THUMB_PRESSED_V = SpriteTexture.of(
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_scroll_thumb_pressed_v.png"
        )
    ).setBorder(0, 3, 0, 3);
    private static final ResourceLocation SHOP_SCROLL_ARROW_UP_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_scroll_arrow_up_mask.png"
        );
    private static final ResourceLocation SHOP_SCROLL_ARROW_DOWN_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/shop/chrome/shop_scroll_arrow_down_mask.png"
        );

    private static void fillChamferedSurface(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int color
    ) {
        if (right - left < 12 || bottom - top < 12) return;
        context.graphics.fill(left + 6, top, right - 6, bottom, color);
        context.graphics.fill(left + 3, top + 1, right - 3, bottom - 1, color);
        context.graphics.fill(left + 1, top + 3, right - 1, bottom - 3, color);
        context.graphics.fill(left, top + 6, right, bottom - 6, color);
    }

    private static void fillPixelRoundedSurface(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int color
    ) {
        if (right - left < 8 || bottom - top < 8) return;
        context.graphics.fill(left + 3, top, right - 3, bottom, color);
        context.graphics.fill(left + 1, top + 1, right - 1, bottom - 1, color);
        context.graphics.fill(left, top + 3, right, bottom - 3, color);
    }

    private static final String[] GRADE_FILTERS = {
        "ALL", "Normal", "Special", "Rare", "Unique", "Legend", "Mythic", "Genesis"
    };
    private static final String[] TYPE_FILTERS = {
        "ALL", "Recovery", "Food", "Satchel", "Weapon", "Armor", "Accessory",
        "GUI Theme", "Core", "Material", "Currency"
    };
    private static final String[] VENDOR_FILTERS = {
        "ALL", "Dungeon Shop", "Kaijin", "Kurobe"
    };

    private static final class CurrencyStripElement extends UIElement {
        private static final String[] IDS = {
            "tensura:bronze_coin", "tensura:silver_coin", "tensura:gold_coin",
            "tensura:stellar_coin"
        };
        private static final int[] COLORS = {
            0xffe3a15c, 0xffdce5ed, 0xffffd04a, 0xff63d9ff
        };
        private DungeonShopView.Wallet wallet = DungeonShopView.Wallet.empty();

        CurrencyStripElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setWallet(DungeonShopView.Wallet wallet) {
            this.wallet = wallet == null ? DungeonShopView.Wallet.empty() : wallet;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            CompactWalletSummary.Value summary = CompactWalletSummary.summarize(
                wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()
            );
            int cursorX = Math.round(getPositionX());
            int centerY = Math.round(getPositionY() + getSizeHeight() * 0.5f);
            int index = summary.denomination().ordinal();
            String text = summary.displayText();
            ResourceLocation itemId = currencyId(index);
            if (!drawItem(context, itemId, cursorX, centerY - 9, 18)) {
                context.graphics.fill(cursorX + 4, centerY - 5, cursorX + 14, centerY + 5,
                    COLORS[index]);
            }
            drawScaledText(context, text, cursorX + 21, centerY - 5, COLORS[index], 1.2f);
        }

        private static ResourceLocation currencyId(int index) {
            ResourceLocation primary = ResourceLocation.parse(IDS[index]);
            if (index != 3 || BuiltInRegistries.ITEM.containsKey(primary)) return primary;
            return ResourceLocation.parse("tensura:stellar_gold_coin");
        }

        private static boolean drawItem(
            GUIContext context,
            ResourceLocation itemId,
            int x,
            int y,
            int targetSize
        ) {
            return BuiltInRegistries.ITEM.getOptional(itemId).map(item -> {
                PoseStack pose = context.graphics.pose();
                pose.pushPose();
                pose.translate(x + targetSize * 0.5f, y + targetSize * 0.5f, 100f);
                pose.scale(targetSize / 16f, targetSize / 16f, 1f);
                pose.translate(-8f, -8f, 0f);
                context.graphics.renderItem(new ItemStack(item), 0, 0);
                pose.popPose();
                return true;
            }).orElse(false);
        }

        private static void drawScaledText(
            GUIContext context,
            String text,
            int x,
            int y,
            int color,
            float scale
        ) {
            PoseStack pose = context.graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0f);
            pose.scale(scale, scale, 1f);
            context.graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
            pose.popPose();
        }
    }

    private static final class ShopTitleElement extends UIElement {
        ShopTitleElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            context.graphics.blit(
                SHOP_TITLE_FLANK_LEFT,
                left,
                top,
                32,
                32,
                0f,
                0f,
                32,
                32,
                32,
                32
            );
            context.graphics.blit(
                SHOP_TITLE_LOGO,
                left + 38,
                top + 2,
                155,
                29,
                0f,
                0f,
                155,
                29,
                155,
                29
            );
            context.graphics.blit(
                SHOP_TITLE_FLANK_RIGHT,
                left + 198,
                top,
                32,
                32,
                0f,
                0f,
                32,
                32,
                32,
                32
            );
        }
    }

    private static final class ResetGlyphElement extends UIElement {
        ResetGlyphElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int color = CYAN;

            context.graphics.fill(left + 7, top + 2, left + 12, top + 3, color);
            context.graphics.fill(left + 5, top + 3, left + 8, top + 5, color);
            context.graphics.fill(left + 4, top + 5, left + 6, top + 10, color);
            context.graphics.fill(left + 5, top + 10, left + 8, top + 12, color);
            context.graphics.fill(left + 7, top + 12, left + 12, top + 13, color);
            context.graphics.fill(left + 10, top + 10, left + 13, top + 12, color);
        }
    }

    private static final class ShopHeaderElement extends UIElement {
        ShopHeaderElement() {
            style(style -> style
                .backgroundTexture(new ColorRectTexture(0x00000000))
                .overlayTexture(SHOP_PANEL_FRAME_TEXTURE));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            fillChamferedSurface(context, left, top, right, bottom, HEADER_SURFACE);
        }
    }

    private static final class SearchFieldFrameElement extends UIElement {
        SearchFieldFrameElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            fillPixelRoundedSurface(context, left, top, right, bottom, SECTION_BORDER_COLOR);
            fillPixelRoundedSurface(
                context, left + 1, top + 1, right - 1, bottom - 1, SEARCH_FIELD_COLOR);
        }
    }

    private static final class OfferRowElement extends UIElement {
        private final boolean selected;
        private final int accent;
        private boolean hovered;

        OfferRowElement(boolean selected, int accent) {
            this.selected = selected;
            this.accent = accent;
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            addEventListener(UIEvents.MOUSE_ENTER, event -> {
                hovered = true;
                DungeonClientSoundEffects.playMenuHover();
            });
            addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            if (right - left < 12 || bottom - top < 8) return;

            if (selected) {
                context.graphics.fill(left, top, right, bottom, 0xff0d2238);
                context.graphics.fill(left, top, right, top + 1, SECTION_BORDER_COLOR);
                context.graphics.fill(left, bottom - 1, right, bottom, SECTION_BORDER_COLOR);
                context.graphics.fill(left, top, left + 1, bottom, SECTION_BORDER_COLOR);
                context.graphics.fill(right - 1, top, right, bottom, SECTION_BORDER_COLOR);
                context.graphics.fill(left + 1, top + 1, left + 5, bottom - 1, accent);

                int centerY = top + (bottom - top) / 2;
                context.graphics.fill(right - 8, centerY - 2, right - 6, centerY + 3, accent);
                context.graphics.fill(right - 10, centerY - 1, right - 8, centerY + 2, accent);
            } else if (hovered) {
                context.graphics.fill(left, top, right, bottom, 0xff111923);
                context.graphics.fill(left, top + 2, left + 3, bottom - 2, accent);
            } else {
                context.graphics.fill(left, top + 2, left + 3, bottom - 2, accent);
            }
        }
    }

    private static final class BarterDetailsElement extends UIElement {
        private @Nullable DungeonShopView.Offer offer;

        BarterDetailsElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
            setDisplay(false);
        }

        void setOffer(@Nullable DungeonShopView.Offer offer) {
            this.offer = offer;
            setDisplay(offer != null && offer.tab() == DungeonShopView.Tab.BARTER);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            if (offer == null || offer.tab() != DungeonShopView.Tab.BARTER) return;

            DungeonShopBarterText.Presentation presentation = DungeonShopBarterText.parse(offer);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int bottom = top + Math.round(getSizeHeight());
            int lineHeight = 9;
            int y = top + 1;

            if (!presentation.comparison().grade().isBlank()) {
                drawSegments(context, left, y, 0.70f,
                    new TextSegment(presentation.comparison().grade(), GRADE_I_COLOR),
                    new TextSegment("  ·  ", DIM),
                    new TextSegment(
                        presentation.comparison().description().toUpperCase(Locale.ROOT),
                        MUTED
                    ));
                y += lineHeight;
            }

            String heading = presentation.missingMaterials()
                ? "MISSING MATERIALS"
                : "REQUIRED MATERIALS";
            drawSegments(context, left, y, 0.67f,
                new TextSegment(heading,
                    presentation.missingMaterials() ? 0xffff6c7e : CYAN));
            y += lineHeight;

            for (DungeonShopBarterText.Material material : presentation.materials()) {
                if (y + lineHeight > bottom) return;
                drawMaterial(context, left, y, material, "•  ");
                y += lineHeight;
            }

            if (presentation.output() != null && y + lineHeight <= bottom) {
                drawMaterial(context, left, y, presentation.output(), "RECEIVE  ");
                y += lineHeight;
            }

            if (!presentation.status().isBlank()
                && !presentation.status().equals("MISSING MATERIALS")
                && y + lineHeight <= bottom) {
                int color = presentation.unavailable() ? 0xffff6c7e : SUCCESS_GREEN;
                drawSegments(context, left, y, 0.67f,
                    new TextSegment(presentation.status().toUpperCase(Locale.ROOT), color));
            }
        }

        private static void drawMaterial(
            GUIContext context,
            int x,
            int y,
            DungeonShopBarterText.Material material,
            String prefix
        ) {
            drawSegments(context, x, y, 0.69f,
                new TextSegment(prefix, DIM),
                new TextSegment(material.quantity() + "x  ", GOLD),
                new TextSegment(material.name(), MATERIAL_NAME_COLOR));
        }
    }

    private record TextSegment(String text, int color) {}

    private static void drawSegments(
        GUIContext context,
        int x,
        int y,
        float scale,
        TextSegment... segments
    ) {
        PoseStack pose = context.graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(scale, scale, 1f);
        int cursor = 0;
        for (TextSegment segment : segments) {
            if (segment.text().isBlank()) continue;
            context.graphics.drawString(
                Minecraft.getInstance().font,
                segment.text(),
                cursor,
                0,
                segment.color(),
                false
            );
            cursor += Minecraft.getInstance().font.width(segment.text());
        }
        pose.popPose();
    }

    private static final class SectionHeadingElement extends UIElement {
        private String text;
        private int accent;

        SectionHeadingElement(String text, int accent) {
            this.text = text;
            this.accent = accent;
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setText(String text) {
            this.text = text == null ? "" : text;
        }

        void setAccent(int accent) {
            this.accent = accent;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int centerY = Math.round(getPositionY() + getSizeHeight() * 0.5f);
            float scale = 1.25f;
            int textWidth = Math.round(Minecraft.getInstance().font.width(text) * scale);
            PoseStack pose = context.graphics.pose();
            pose.pushPose();
            pose.translate(left, getPositionY() + 1f, 0f);
            pose.scale(scale, scale, 1f);
            context.graphics.drawString(Minecraft.getInstance().font, text, 0, 0, accent, false);
            pose.popPose();
            int lineStart = left + textWidth + 16;
            int lineEnd = Math.round(getPositionX() + getSizeWidth());
            if (lineEnd > lineStart) {
                context.graphics.fill(lineStart, centerY, lineEnd, centerY + 1,
                    0x99000000 | (accent & 0x00ffffff));
            }
        }
    }

    private static final class SectionContainerElement extends UIElement {
        private int accent;

        SectionContainerElement(int accent) {
            this.accent = accent;
            style(style -> style
                .backgroundTexture(new ColorRectTexture(0x00000000))
                .overlayTexture(SHOP_PANEL_FRAME_TEXTURE));
            setAllowHitTest(false);
        }

        void setAccent(int accent) {
            this.accent = accent;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            if (right - left < 12 || bottom - top < 12) return;

            int innerEdge = 0x36000000 | (accent & 0x00ffffff);

            fillChamferedSurface(context, left, top, right, bottom, SECTION_SURFACE);

            int highlightEnd = left + Math.max(28, Math.round(getSizeWidth() * 0.24f));
            context.graphics.fill(left + 8, top + 3, highlightEnd, top + 4, innerEdge);
        }
    }

    private static final class PreviewPanelElement extends UIElement {
        PreviewPanelElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            if (right - left < 16 || bottom - top < 16) return;

            fillChamferedSurface(context, left, top, right, bottom, PREVIEW_SURFACE);
            context.graphics.fill(left, top, right, top + 1, SECTION_BORDER_COLOR);
            context.graphics.fill(left, bottom - 1, right, bottom, SECTION_BORDER_COLOR);
            context.graphics.fill(left, top, left + 1, bottom, SECTION_BORDER_COLOR);
            context.graphics.fill(right - 1, top, right, bottom, SECTION_BORDER_COLOR);
        }
    }

    private static final class ConfirmationPanelElement extends UIElement {
        ConfirmationPanelElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            if (right - left < 16 || bottom - top < 16) return;

            fillChamferedSurface(context, left, top, right, bottom, 0xff050b16);
            context.graphics.fill(left + 4, top, right - 4, top + 1, GOLD);
            context.graphics.fill(left + 4, bottom - 1, right - 4, bottom, HAIRLINE);
            context.graphics.fill(left, top + 4, left + 1, bottom - 4, HAIRLINE);
            context.graphics.fill(right - 1, top + 4, right, bottom - 4, HAIRLINE);
        }
    }

    private static final class UiParts {
        final ModularUI modularUI;
        final UIElement root;
        final List<Button> tabButtons;
        final List<TabIndicatorElement> tabUnderlines;
        final List<Button> filterButtons;
        final TextField searchField;
        final VirtualScrollerView<DungeonShopView.Offer> essentialList;
        final VirtualScrollerView<DungeonShopView.Offer> timedList;
        final SectionHeadingElement essentialHeader;
        final SectionHeadingElement timedHeader;
        final SectionContainerElement essentialGroup;
        final SectionContainerElement timedGroup;
        final Label essentialEmpty;
        final Label timedEmpty;
        final CurrencyStripElement walletLabel;
        final Label resetLabel;
        final Label selectedTitle;
        final Label selectedRarity;
        final ShopArtworkElement selectedArt;
        final Label selectedMeta;
        final Label selectedDescription;
        final BarterDetailsElement barterDetails;
        final Label selectedDetails;
        final Label selectedPrice;
        final Label statusLabel;
        final Button actionButton;
        final Button previewButton;
        final UIElement confirmationOverlay;
        final Label confirmationTitle;
        final Label confirmationName;
        final Label confirmationDescription;
        final Label confirmationPrice;
        final Button confirmationBackButton;
        final Button confirmationConfirmButton;

        UiParts(
            ModularUI modularUI,
            UIElement root,
            List<Button> tabButtons,
            List<TabIndicatorElement> tabUnderlines,
            List<Button> filterButtons,
            TextField searchField,
            VirtualScrollerView<DungeonShopView.Offer> essentialList,
            VirtualScrollerView<DungeonShopView.Offer> timedList,
            SectionHeadingElement essentialHeader,
            SectionHeadingElement timedHeader,
            SectionContainerElement essentialGroup,
            SectionContainerElement timedGroup,
            Label essentialEmpty,
            Label timedEmpty,
            CurrencyStripElement walletLabel,
            Label resetLabel,
            Label selectedTitle,
            Label selectedRarity,
            ShopArtworkElement selectedArt,
            Label selectedMeta,
            Label selectedDescription,
            BarterDetailsElement barterDetails,
            Label selectedDetails,
            Label selectedPrice,
            Label statusLabel,
            Button actionButton,
            Button previewButton,
            UIElement confirmationOverlay,
            Label confirmationTitle,
            Label confirmationName,
            Label confirmationDescription,
            Label confirmationPrice,
            Button confirmationBackButton,
            Button confirmationConfirmButton
        ) {
            this.modularUI = modularUI;
            this.root = root;
            this.tabButtons = tabButtons;
            this.tabUnderlines = tabUnderlines;
            this.filterButtons = filterButtons;
            this.searchField = searchField;
            this.essentialList = essentialList;
            this.timedList = timedList;
            this.essentialHeader = essentialHeader;
            this.timedHeader = timedHeader;
            this.essentialGroup = essentialGroup;
            this.timedGroup = timedGroup;
            this.essentialEmpty = essentialEmpty;
            this.timedEmpty = timedEmpty;
            this.walletLabel = walletLabel;
            this.resetLabel = resetLabel;
            this.selectedTitle = selectedTitle;
            this.selectedRarity = selectedRarity;
            this.selectedArt = selectedArt;
            this.selectedMeta = selectedMeta;
            this.selectedDescription = selectedDescription;
            this.barterDetails = barterDetails;
            this.selectedDetails = selectedDetails;
            this.selectedPrice = selectedPrice;
            this.statusLabel = statusLabel;
            this.actionButton = actionButton;
            this.previewButton = previewButton;
            this.confirmationOverlay = confirmationOverlay;
            this.confirmationTitle = confirmationTitle;
            this.confirmationName = confirmationName;
            this.confirmationDescription = confirmationDescription;
            this.confirmationPrice = confirmationPrice;
            this.confirmationBackButton = confirmationBackButton;
            this.confirmationConfirmButton = confirmationConfirmButton;
        }
    }

    private static final class TabIndicatorElement extends UIElement {
        private boolean active;
        private final int accent;
        private final SpriteTexture activeTexture;
        private final SpriteTexture inactiveTexture;

        TabIndicatorElement(boolean active, int accent) {
            this.active = active;
            this.accent = accent;
            this.activeTexture = SpriteTexture.of(SHOP_TAB_INDICATOR_TEXTURE).setColor(accent);
            this.inactiveTexture = SpriteTexture.of(SHOP_TAB_INDICATOR_TEXTURE).setColor(0xff47536b);
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        void setIndicatorActive(boolean active) {
            this.active = active;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int centerX = Math.round(getPositionX() + getSizeWidth() * 0.5f);
            int y = Math.round(getPositionY());
            int drawWidth = Math.min(208, Math.max(32, Math.round(getSizeWidth() - 12f)));
            context.drawTexture(
                active ? activeTexture : inactiveTexture,
                centerX - drawWidth * 0.5f,
                y,
                drawWidth,
                7
            );
        }
    }

    private static final class ShopIconElement extends UIElement {
        private final DungeonShopView.Offer offer;
        private final @Nullable DungeonShopArt.Asset asset;

        ShopIconElement(DungeonShopView.Offer offer) {
            this.offer = offer;
            this.asset = DungeonShopArt.resolve(
                offer,
                offer.section() == DungeonShopView.Section.ESSENTIAL
                    ? DungeonShopArt.Surface.ESSENTIAL
                    : DungeonShopArt.Surface.CARD
            );
            style(style -> style.backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(PREVIEW_SURFACE),
                SpriteTexture.of(ITEM_SOCKET_TEXTURE)
            )));
            if (asset != null && asset.width() == asset.height()) {
                style(style -> style.backgroundTexture(GuiTextureGroup.of(
                    new ColorRectTexture(PREVIEW_SURFACE),
                    SpriteTexture.of(ITEM_SOCKET_TEXTURE),
                    SpriteTexture.of(ResourceLocation.fromNamespaceAndPath(
                        "tensura_dungeon_ui", asset.path())).scale(ROW_ITEM_TEXTURE_SCALE)
                )));
            }
            setAllowHitTest(false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            if (drawRegisteredItem(context)) return;
            if (asset != null && asset.width() == asset.height()) return;
            if (asset == null || asset.width() <= 0 || asset.height() <= 0) {
                int centerX = Math.round(getPositionX() + getSizeWidth() * 0.5f);
                int centerY = Math.round(getPositionY() + getSizeHeight() * 0.5f);
                int accent = stateAccent(offer);
                context.graphics.fill(centerX - 3, centerY - 1, centerX + 4, centerY + 2, accent);
                context.graphics.fill(centerX - 1, centerY - 3, centerX + 2, centerY + 4, accent);
                return;
            }

            float availableWidth = Math.min(ROW_ITEM_MAX_SIZE, Math.max(1f, getSizeWidth() - 10f));
            float availableHeight = Math.min(ROW_ITEM_MAX_SIZE, Math.max(1f, getSizeHeight() - 10f));
            float scale = Math.min(availableWidth / asset.width(), availableHeight / asset.height());
            int drawWidth = Math.max(1, Math.round(asset.width() * scale));
            int drawHeight = Math.max(1, Math.round(asset.height() * scale));
            int drawX = Math.round(getPositionX() + (getSizeWidth() - drawWidth) * 0.5f);
            int drawY = Math.round(getPositionY() + (getSizeHeight() - drawHeight) * 0.5f);
            context.graphics.blit(
                ResourceLocation.fromNamespaceAndPath("tensura_dungeon_ui", asset.path()),
                drawX,
                drawY,
                drawWidth,
                drawHeight,
                0f,
                0f,
                asset.width(),
                asset.height(),
                asset.width(),
                asset.height()
            );
        }

        private boolean drawRegisteredItem(GUIContext context) {
            String rawItemId = DungeonShopArt.registeredItemId(offer);
            if (rawItemId.isBlank()) return false;
            ResourceLocation itemId;
            try {
                itemId = ResourceLocation.parse(rawItemId);
            } catch (RuntimeException exception) {
                return false;
            }
            return BuiltInRegistries.ITEM.getOptional(itemId).map(item -> {
                float targetSize = Math.max(16f,
                    Math.min(ROW_ITEM_MAX_SIZE,
                        Math.min(getSizeWidth() - 10f, getSizeHeight() - 10f)));
                float scale = targetSize / 16f;
                PoseStack pose = context.graphics.pose();
                pose.pushPose();
                pose.translate(
                    getPositionX() + getSizeWidth() * 0.5f,
                    getPositionY() + getSizeHeight() * 0.5f,
                    100f
                );
                pose.scale(scale, scale, 1f);
                pose.translate(-8f, -8f, 0f);
                context.graphics.renderItem(new ItemStack(item), 0, 0);
                pose.popPose();
                return true;
            }).orElse(false);
        }
    }

    private static final class PreviewItemElement extends UIElement {
        private @Nullable DungeonShopView.Offer offer;

        PreviewItemElement() {
            style(style -> style
                .backgroundTexture(new ColorRectTexture(0x00000000))
                .zIndex(2));
            setAllowHitTest(false);
            setDisplay(false);
        }

        void setOffer(
            @Nullable DungeonShopView.Offer offer,
            @Nullable DungeonShopArt.Asset asset
        ) {
            this.offer = offer;
            if (asset != null && asset.width() > 0 && asset.width() == asset.height()) {
                style(style -> style.backgroundTexture(SpriteTexture.of(
                    ResourceLocation.fromNamespaceAndPath("tensura_dungeon_ui", asset.path()))));
                setDisplay(true);
                return;
            }

            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setDisplay(offer != null && !DungeonShopArt.registeredItemId(offer).isBlank());
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            if (offer == null) return;
            String rawItemId = DungeonShopArt.registeredItemId(offer);
            if (rawItemId.isBlank()) return;
            ResourceLocation itemId;
            try {
                itemId = ResourceLocation.parse(rawItemId);
            } catch (RuntimeException exception) {
                return;
            }
            BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> {
                float presentationScale = DungeonShopArt.weaponPresentation(offer).previewScale();
                float targetSize = Math.min(PREVIEW_ITEM_MAX_SIZE,
                    Math.max(64f, 72f * presentationScale));
                float scale = targetSize / 16f;
                PoseStack pose = context.graphics.pose();
                pose.pushPose();
                pose.translate(
                    getPositionX() + getSizeWidth() * 0.5f,
                    getPositionY() + getSizeHeight() * 0.5f,
                    100f
                );
                pose.scale(scale, scale, 1f);
                pose.translate(-8f, -8f, 0f);
                context.graphics.renderItem(new ItemStack(item), 0, 0);
                pose.popPose();
            });
        }
    }

    private static final class ShopArtworkElement extends UIElement {
        private final UIElement pedestalLayer;
        private final UIElement showcaseLayer;
        private final PreviewItemElement itemLayer;

        ShopArtworkElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(PREVIEW_SURFACE)));
            layout(layout -> layout
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.CENTER)
                .justifyContent(AlignContent.CENTER));

            pedestalLayer = new UIElement()
                .layout(layout -> layout.widthPercent(98).aspectRatio(
                    (float) PREVIEW_PEDESTAL_SOURCE_WIDTH / PREVIEW_PEDESTAL_SOURCE_HEIGHT))
                .style(style -> style.backgroundTexture(SpriteTexture.of(PREVIEW_PEDESTAL_TEXTURE)));
            pedestalLayer.setAllowHitTest(false);

            showcaseLayer = new UIElement()
                .layout(layout -> layout.widthPercent(98).aspectRatio(2f))
                .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            showcaseLayer.setAllowHitTest(false);
            showcaseLayer.setDisplay(false);

            itemLayer = new PreviewItemElement();
            itemLayer.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .width(PREVIEW_ITEM_MAX_SIZE)
                .height(PREVIEW_ITEM_MAX_SIZE)
                .leftPercent(100f * PREVIEW_ITEM_ANCHOR_SOURCE_X
                    / PREVIEW_PEDESTAL_SOURCE_WIDTH)
                .topPercent(45.9f)
                .marginLeft(-PREVIEW_ITEM_MAX_SIZE * 0.5f)
                .marginTop(-PREVIEW_ITEM_MAX_SIZE * 0.5f));
            addChildren(pedestalLayer, showcaseLayer, itemLayer);
        }

        void setOffer(
            @Nullable DungeonShopView.Offer offer,
            @Nullable DungeonShopArt.Asset asset
        ) {
            boolean isShowcase = asset != null && asset.width() > 0 && asset.height() > 0
                && asset.width() != asset.height();
            pedestalLayer.setDisplay(!isShowcase);
            showcaseLayer.setDisplay(isShowcase);
            if (isShowcase) {
                showcaseLayer.layout(layout -> layout.aspectRatio(
                    (float) asset.width() / asset.height()));
                showcaseLayer.style(style -> style.backgroundTexture(SpriteTexture.of(
                    ResourceLocation.fromNamespaceAndPath("tensura_dungeon_ui", asset.path()))));
                itemLayer.setOffer(null, null);
            } else {
                itemLayer.setOffer(offer, asset);
            }
        }
    }

    private final Screen parent;
    private final UIElement root;
    private final List<Button> tabButtons;
    private final List<TabIndicatorElement> tabUnderlines;
    private final List<Button> filterButtons;
    private final TextField searchField;
    private final VirtualScrollerView<DungeonShopView.Offer> essentialList;
    private final VirtualScrollerView<DungeonShopView.Offer> timedList;
    private final SectionHeadingElement essentialHeader;
    private final SectionHeadingElement timedHeader;
    private final SectionContainerElement essentialGroup;
    private final SectionContainerElement timedGroup;
    private final Label essentialEmpty;
    private final Label timedEmpty;
    private final CurrencyStripElement walletLabel;
    private final Label resetLabel;
    private final Label selectedTitle;
    private final Label selectedRarity;
    private final ShopArtworkElement selectedArt;
    private final Label selectedMeta;
    private final Label selectedDescription;
    private final BarterDetailsElement barterDetails;
    private final Label selectedDetails;
    private final Label selectedPrice;
    private final Label statusLabel;
    private final Button actionButton;
    private final Button previewButton;
    private final UIElement confirmationOverlay;
    private final Label confirmationTitle;
    private final Label confirmationName;
    private final Label confirmationDescription;
    private final Label confirmationPrice;
    private final Button confirmationBackButton;
    private final Button confirmationConfirmButton;
    private final Map<DungeonShopView.Tab, DungeonShopView> tabViews =
        new EnumMap<>(DungeonShopView.Tab.class);

    private DungeonShopView view;
    private DungeonShopView.Tab activeTab;
    private DungeonShopView.Offer selectedOffer;
    private DungeonShopView.Offer pendingConfirmation;
    private String gradeFilter = "ALL";
    private String typeFilter = "ALL";
    private String vendorFilter = "ALL";

    DungeonShopLdLib2Screen(DungeonShopView view, @Nullable Screen parent) {
        this(createUi(view), view, parent);
    }

    private DungeonShopLdLib2Screen(UiParts parts, DungeonShopView view, @Nullable Screen parent) {
        super(parts.modularUI, Component.literal("Dungeon Shop"));
        this.parent = parent;
        this.root = parts.root;
        this.tabButtons = parts.tabButtons;
        this.tabUnderlines = parts.tabUnderlines;
        this.filterButtons = parts.filterButtons;
        this.searchField = parts.searchField;
        this.essentialList = parts.essentialList;
        this.timedList = parts.timedList;
        this.essentialHeader = parts.essentialHeader;
        this.timedHeader = parts.timedHeader;
        this.essentialGroup = parts.essentialGroup;
        this.timedGroup = parts.timedGroup;
        this.essentialEmpty = parts.essentialEmpty;
        this.timedEmpty = parts.timedEmpty;
        this.walletLabel = parts.walletLabel;
        this.resetLabel = parts.resetLabel;
        this.selectedTitle = parts.selectedTitle;
        this.selectedRarity = parts.selectedRarity;
        this.selectedArt = parts.selectedArt;
        this.selectedMeta = parts.selectedMeta;
        this.selectedDescription = parts.selectedDescription;
        this.barterDetails = parts.barterDetails;
        this.selectedDetails = parts.selectedDetails;
        this.selectedPrice = parts.selectedPrice;
        this.statusLabel = parts.statusLabel;
        this.actionButton = parts.actionButton;
        this.previewButton = parts.previewButton;
        this.confirmationOverlay = parts.confirmationOverlay;
        this.confirmationTitle = parts.confirmationTitle;
        this.confirmationName = parts.confirmationName;
        this.confirmationDescription = parts.confirmationDescription;
        this.confirmationPrice = parts.confirmationPrice;
        this.confirmationBackButton = parts.confirmationBackButton;
        this.confirmationConfirmButton = parts.confirmationConfirmButton;
        this.view = Objects.requireNonNull(view, "Dungeon Shop view");
        this.activeTab = view.initialTab();
        this.tabViews.put(activeTab, view);
        this.selectedOffer = firstOffer(activeTab, "");

        for (int index = 0; index < tabButtons.size(); index++) {
            DungeonShopView.Tab tab = DungeonShopView.Tab.values()[index];
            tabButtons.get(index).setOnClick(event -> selectTab(tab));
            addHoverSound(tabButtons.get(index));
        }
        filterButtons.get(0).setOnClick(event -> cycleGradeFilter());
        filterButtons.get(1).setOnClick(event -> cycleTypeFilter());
        filterButtons.get(2).setOnClick(event -> cycleVendorFilter());
        filterButtons.forEach(DungeonShopLdLib2Screen::addHoverSound);
        searchField.setTextResponder(value -> refreshOffers());
        essentialList.setItemUIProvider(this::offerRow);
        timedList.setItemUIProvider(this::offerRow);
        actionButton.setOnClick(event -> activateSelectedOffer());
        addHoverSound(actionButton);
        previewButton.setOnClick(event -> previewSelectedHud());
        addHoverSound(previewButton);
        confirmationBackButton.setOnClick(event -> {
            DungeonClientSoundEffects.playMenuClose();
            closeConfirmation();
        });
        addHoverSound(confirmationBackButton);
        confirmationConfirmButton.setOnClick(event -> confirmPendingAction());
        addHoverSound(confirmationConfirmButton);
        refreshFromView();
    }

    private static void addHoverSound(UIElement element) {
        element.addEventListener(
            UIEvents.MOUSE_ENTER,
            event -> DungeonClientSoundEffects.playMenuHover()
        );
    }

    private static UiParts createUi(DungeonShopView view) {
        UIElement root = new UIElement()
            .layout(layout -> layout.width(LOGICAL_WIDTH).height(LOGICAL_HEIGHT))
            .style(style -> style.backgroundTexture(new ColorRectTexture(SHOP_BASE_COLOR)))
            .transform(transform -> transform.scale(0.70f).pivot(0.5f, 0.5f));

        ShopTitleElement title = new ShopTitleElement();
        title.layout(layout -> layout.width(230).height(32));

        CurrencyStripElement walletLabel = new CurrencyStripElement();
        walletLabel.layout(layout -> layout.width(330).height(26));

        Label resetCaption = label("RESETS IN", 18, TEXT, 9.4f);
        resetCaption.layout(layout -> layout.width(70).height(18));
        resetCaption.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        Label resetLabel = label("", 18, CYAN, 10.8f);
        resetLabel.layout(layout -> layout.width(170).height(18));
        resetLabel.textStyle(style -> style.textAlignHorizontal(Horizontal.LEFT));
        UIElement resetSpacer = new UIElement()
            .layout(layout -> layout.width(0).height(1).flexGrow(1).flexBasis(0))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        resetSpacer.setAllowHitTest(false);
        UIElement resetGroup = row(0x00000000, 26);
        resetGroup.layout(layout -> layout
            .width(372)
            .height(26)
            .alignItems(AlignItems.CENTER)
            .paddingRight(16)
            .gapAll(4));
        resetGroup.addChildren(resetSpacer, resetCaption, resetLabel);

        ShopHeaderElement header = new ShopHeaderElement();
        header.layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER)
            .widthPercent(100)
            .height(34)
            .paddingHorizontal(8)
            .gapAll(6));
        header.addChildren(walletLabel, title, resetGroup);

        UIElement tabs = row(0x00000000, 26);
        tabs.layout(layout -> layout.widthPercent(100).gapAll(5));
        List<Button> tabButtons = new java.util.ArrayList<>();
        List<TabIndicatorElement> tabUnderlines = new java.util.ArrayList<>();
        for (DungeonShopView.Tab tab : DungeonShopView.Tab.values()) {
            Button button = new Button().setText(tab.label());
            button.layout(layout -> layout.widthPercent(100).height(19));
            button.textStyle(style -> style
                .textColor(tab == view.initialTab() ? CYAN : MUTED).fontSize(12f));
            button.buttonStyle(style -> style
                .baseTexture(new ColorRectTexture(0x00000000))
                .hoverTexture(new ColorRectTexture(0x22164665))
                .pressedTexture(new ColorRectTexture(0x331b5f7d)));
            int tabAccent = switch (tab) {
                case DAILY -> CYAN;
                case WEEKLY -> GOLD;
                case BARTER -> 0xff42e6b5;
            };
            TabIndicatorElement underline = new TabIndicatorElement(
                tab == view.initialTab(), tabAccent);
            underline.layout(layout -> layout.widthPercent(100).height(7));
            UIElement tabSlot = column(0x00000000, 100);
            tabSlot.layout(layout -> layout
                .flexGrow(1)
                .flexBasis(0)
                .height(26)
                .gapAll(0));
            tabSlot.addChildren(button, underline);
            tabs.addChild(tabSlot);
            tabButtons.add(button);
            tabUnderlines.add(underline);
        }

        TextField searchField = new TextField().setAnyString();
        searchField.layout(layout -> layout.widthPercent(100).height(20));
        searchField.style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        searchField.textFieldStyle(style -> style
            .textColor(TEXT)
            .focusOverlay(new ColorRectTexture(0x14000000 | (CYAN & 0x00ffffff)))
            .placeholder(Component.literal("Search current stock...")));
        UIElement searchSlot = new SearchFieldFrameElement();
        searchSlot.layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER)
            .widthPercent(38)
            .height(24)
            .paddingHorizontal(7));
        searchSlot.addChild(searchField);

        List<Button> filterButtons = new java.util.ArrayList<>();
        filterButtons.add(filterButton("GRADE: ALL"));
        filterButtons.add(filterButton("TYPE: ALL"));
        filterButtons.add(filterButton("VENDOR: ALL"));
        UIElement utility = row(0x00000000, 26);
        utility.layout(layout -> layout.alignItems(AlignItems.CENTER).gapAll(6));
        utility.addChildren(searchSlot, filterButtons.get(0), filterButtons.get(1), filterButtons.get(2));

        SectionHeadingElement essentialHeader = sectionHeading("ESSENTIAL SUPPLIES", CYAN);
        SectionHeadingElement timedHeader = sectionHeading("TODAY'S FINDS", VIOLET);
        Label essentialEmpty = emptyLabel("No Essential Supplies match these filters.");
        Label timedEmpty = emptyLabel("No Today's Finds match these filters.");

        VirtualScrollerView<DungeonShopView.Offer> essentialList = offerList(132);
        VirtualScrollerView<DungeonShopView.Offer> timedList = offerList(132);

        SectionContainerElement essentialGroup = sectionContainer(CYAN);
        essentialGroup.layout(layout -> layout.widthPercent(100).height(144).paddingAll(6).gapAll(0));
        essentialGroup.addChildren(essentialList, essentialEmpty);

        SectionContainerElement timedGroup = sectionContainer(VIOLET);
        timedGroup.layout(layout -> layout.widthPercent(100).height(144).paddingAll(6).gapAll(0));
        timedGroup.addChildren(timedList, timedEmpty);

        Label listFooter = label("FIXED ROTATION  /  OWNED ITEMS REMAIN VISIBLE", 14, DIM, 7.2f);
        listFooter.layout(layout -> layout.widthPercent(100).height(14));
        listFooter.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Label statusLabel = label("", 16, MUTED, 7.2f);
        statusLabel.layout(layout -> layout.widthPercent(100).height(16));
        statusLabel.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        UIElement listSpacer = new UIElement()
            .layout(layout -> layout.widthPercent(100).flexGrow(1).flexBasis(0))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        listSpacer.setAllowHitTest(false);

        UIElement listPanel = column(0x00000000, 100);
        listPanel.layout(layout -> layout
            .flexGrow(64)
            .flexBasis(0)
            .height(405)
            .paddingAll(10)
            .gapAll(4));
        listPanel.addChildren(
            essentialHeader,
            essentialGroup,
            timedHeader,
            timedGroup,
            listSpacer,
            listFooter,
            statusLabel
        );

        ShopArtworkElement selectedArt = new ShopArtworkElement();
        selectedArt.layout(layout -> layout.widthPercent(100).height(DETAIL_ART_HEIGHT));

        Label selectedTitle = label("", 22, GOLD, 13.5f);
        selectedTitle.layout(layout -> layout.widthPercent(100).height(28));
        Label selectedRarity = label("", 16, VIOLET, 9.5f);
        selectedRarity.layout(layout -> layout.widthPercent(100).height(18));
        Label selectedMeta = label("", 18, CYAN, 9f);
        selectedMeta.layout(layout -> layout.widthPercent(100).height(20));
        Label selectedDescription = label("", 54, TEXT, 9.2f);
        selectedDescription.layout(layout -> layout.widthPercent(100).height(54));
        selectedDescription.textStyle(style -> style.textWrap(TextWrap.WRAP));
        BarterDetailsElement barterDetails = new BarterDetailsElement();
        barterDetails.layout(layout -> layout.widthPercent(100).height(0));
        Label selectedDetails = label("", 36, MUTED, 8.2f);
        selectedDetails.layout(layout -> layout.widthPercent(100).height(36));
        selectedDetails.textStyle(style -> style.textWrap(TextWrap.WRAP));
        Label selectedPrice = label("", 16, GOLD, 11f);
        selectedPrice.layout(layout -> layout.widthPercent(72).height(16));
        selectedPrice.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Button actionButton = new Button().setText("SELECT AN OFFER");
        actionButton.layout(layout -> layout
            .flexGrow(1)
            .flexBasis(0)
            .height(34)
            .paddingHorizontal(4));
        actionButton.textStyle(style -> style.textColor(CYAN).fontSize(9.8f));
        applyActionButtonStyle(actionButton, false);

        Button previewButton = new Button().setText("PREVIEW");
        previewButton.layout(layout -> layout
            .flexGrow(1)
            .flexBasis(0)
            .height(34)
            .paddingHorizontal(4));
        previewButton.textStyle(style -> style.textColor(CYAN).fontSize(9.8f));
        applyActionButtonStyle(previewButton, true);
        previewButton.setDisplay(false);

        UIElement actionButtons = row(0x00000000, 34);
        actionButtons.layout(layout -> layout.widthPercent(100).height(34).gapAll(8));
        actionButtons.addChildren(actionButton, previewButton);

        UIElement purchaseArea = new UIElement();
        purchaseArea.layout(layout -> layout
            .flexDirection(FlexDirection.COLUMN)
            .widthPercent(100)
            .height(PURCHASE_AREA_HEIGHT)
            .alignItems(AlignItems.CENTER)
            .gapAll(2));
        purchaseArea.style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        purchaseArea.addChildren(selectedPrice, actionButtons);

        UIElement detailContent = new UIElement()
            .layout(layout -> layout
                .flexDirection(FlexDirection.COLUMN)
                .height(DETAIL_CONTENT_HEIGHT)
                .paddingAll(12)
                .gapAll(4))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        detailContent.addChildren(
            selectedArt,
            selectedTitle,
            selectedRarity,
            selectedDescription,
            barterDetails,
            selectedDetails,
            purchaseArea
        );

        UIElement detailPanel = new PreviewPanelElement();
        detailPanel.layout(layout -> layout
            .flexDirection(FlexDirection.COLUMN)
            .flexGrow(36)
            .flexBasis(0)
            .height(DETAIL_PANEL_HEIGHT));
        detailPanel.addChild(detailContent);

        UIElement body = row(BODY_SURFACE, 425);
        body.layout(layout -> layout.widthPercent(100).gapAll(0));
        body.addChildren(listPanel, detailPanel);

        UIElement content = new UIElement()
            .layout(layout -> layout
                .flexDirection(FlexDirection.COLUMN)
                .widthPercent(100)
                .height(487)
                .paddingTop(14)
                .paddingLeft(CONTENT_LEFT_INSET)
                .paddingRight(0)
                .paddingBottom(0)
                .gapAll(5))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        content.addChildren(tabs, utility, body);

        UIElement shopBaseField = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style.backgroundTexture(new ColorRectTexture(SHOP_BASE_COLOR)));
        shopBaseField.setAllowHitTest(false);

        UIElement topReveal = new UIElement()
            .layout(layout -> layout
                .widthPercent(100)
                .height(TOP_REVEAL_ELEMENT_HEIGHT))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        topReveal.setAllowHitTest(false);

        UIElement topFoliageOverlay = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style.backgroundTexture(SpriteTexture.of(SHOP_TOP_FOLIAGE_OVERLAY)));
        topFoliageOverlay.setAllowHitTest(false);

        UIElement bottomRightFoliageOverlay = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style.backgroundTexture(
                SpriteTexture.of(SHOP_BOTTOM_RIGHT_FOLIAGE_OVERLAY)));
        bottomRightFoliageOverlay.setAllowHitTest(false);

        UIElement bottomLeftFoliageOverlay = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style
                .backgroundTexture(SpriteTexture.of(SHOP_BOTTOM_LEFT_FOLIAGE_OVERLAY))
                .zIndex(9));
        bottomLeftFoliageOverlay.setAllowHitTest(false);

        UIElement shellFrameOverlay = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style
                .backgroundTexture(SHOP_SHELL_FRAME_TEXTURE)
                .zIndex(10));
        shellFrameOverlay.setAllowHitTest(false);

        Label confirmationTitle = label("CONFIRM PURCHASE", 24, GOLD, 13f);
        confirmationTitle.layout(layout -> layout.widthPercent(100).height(24));
        confirmationTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Label confirmationName = label("", 20, CYAN, 11.5f);
        confirmationName.layout(layout -> layout.widthPercent(100).height(20));
        confirmationName.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        Label confirmationDescription = label("", 44, TEXT, 8.8f);
        confirmationDescription.layout(layout -> layout.widthPercent(100).height(44));
        confirmationDescription.textStyle(style -> style.textWrap(TextWrap.WRAP));

        Label confirmationPrice = label("", 20, GOLD, 11f);
        confirmationPrice.layout(layout -> layout.widthPercent(100).height(20));
        confirmationPrice.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));

        UIElement confirmationSpacer = new UIElement()
            .layout(layout -> layout.widthPercent(100).flexGrow(1).flexBasis(0))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        confirmationSpacer.setAllowHitTest(false);

        Button confirmationBackButton = new Button().setText("BACK");
        confirmationBackButton.layout(layout -> layout.flexGrow(1).flexBasis(0).height(34));
        confirmationBackButton.textStyle(style -> style.textColor(TEXT).fontSize(10f));
        confirmationBackButton.buttonStyle(style -> style
            .baseTexture(new ColorRectTexture(0xff06172f))
            .hoverTexture(new ColorRectTexture(0xff0a2740))
            .pressedTexture(new ColorRectTexture(0xff041020)));

        Button confirmationConfirmButton = new Button().setText("CONFIRM");
        confirmationConfirmButton.layout(layout -> layout.flexGrow(1).flexBasis(0).height(34));
        confirmationConfirmButton.textStyle(style -> style.textColor(CYAN).fontSize(10f));
        applyActionButtonStyle(confirmationConfirmButton, true);

        UIElement confirmationButtons = row(0x00000000, 34);
        confirmationButtons.layout(layout -> layout.widthPercent(100).height(34).gapAll(12));
        confirmationButtons.addChildren(confirmationBackButton, confirmationConfirmButton);

        ConfirmationPanelElement confirmationCard = new ConfirmationPanelElement();
        confirmationCard.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .flexDirection(FlexDirection.COLUMN)
            .left(264)
            .top(155)
            .width(432)
            .height(230)
            .paddingAll(24)
            .gapAll(6));
        confirmationCard.addChildren(
            confirmationTitle,
            confirmationName,
            confirmationDescription,
            confirmationPrice,
            confirmationSpacer,
            confirmationButtons
        );

        UIElement confirmationOverlay = new UIElement()
            .layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(LOGICAL_WIDTH)
                .height(LOGICAL_HEIGHT))
            .style(style -> style
                .backgroundTexture(new ColorRectTexture(0xd8000004))
                .zIndex(30));
        confirmationOverlay.addChild(confirmationCard);
        confirmationOverlay.setDisplay(false);

        root.layout(layout -> layout.flexDirection(FlexDirection.COLUMN).gapAll(5));
        root.addChildren(
            shopBaseField,
            topFoliageOverlay,
            bottomRightFoliageOverlay,
            topReveal,
            header,
            content,
            bottomLeftFoliageOverlay,
            shellFrameOverlay,
            confirmationOverlay
        );

        ModularUI modularUI = ModularUI.of(UI.of(root, screenSize -> Size.of(
            Math.max(320, screenSize.width - 24),
            Math.max(300, screenSize.height - 24)
        )));
        return new UiParts(
            modularUI,
            root,
            List.copyOf(tabButtons),
            List.copyOf(tabUnderlines),
            List.copyOf(filterButtons),
            searchField,
            essentialList,
            timedList,
            essentialHeader,
            timedHeader,
            essentialGroup,
            timedGroup,
            essentialEmpty,
            timedEmpty,
            walletLabel,
            resetLabel,
            selectedTitle,
            selectedRarity,
            selectedArt,
            selectedMeta,
            selectedDescription,
            barterDetails,
            selectedDetails,
            selectedPrice,
            statusLabel,
            actionButton,
            previewButton,
            confirmationOverlay,
            confirmationTitle,
            confirmationName,
            confirmationDescription,
            confirmationPrice,
            confirmationBackButton,
            confirmationConfirmButton
        );
    }

    private UIElement offerRow(DungeonShopView.Offer offer) {
        boolean selected = selectedOffer != null && selectedOffer.id().equals(offer.id());
        OfferRowElement row = new OfferRowElement(selected, stateAccent(offer));
        row.layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER)
            .widthPercent(100)
            .height(60)
            .marginVertical(1)
            .paddingLeft(8)
            .paddingRight(7)
            .gapAll(5));
        row.addEventListener(UIEvents.MOUSE_UP, event -> selectOffer(offer));

        ShopIconElement icon = new ShopIconElement(offer);
        icon.layout(layout -> layout.width(46).height(46));

        UIElement nameBlock = column(0x00000000, 100);
        nameBlock.layout(layout -> layout.widthPercent(47).height(52).gapAll(0));
        Label name = label(itemName(offer), 19, rarityColor(offer.grade()), 10f);
        name.layout(layout -> layout.widthPercent(100).height(18));
        Label rarity = label(gradeLabel(offer), 15, gradeColor(offer), 8f);
        rarity.layout(layout -> layout.widthPercent(100).height(15));
        Label metadata = label(rowMetadata(offer), 15, DIM, 7.2f);
        metadata.layout(layout -> layout.widthPercent(100).height(15));
        nameBlock.addChildren(name, rarity, metadata);

        Label price = label(offer.price(), 20, offer.actionable() ? GOLD : DIM, 8f);
        price.layout(layout -> layout.widthPercent(17).height(20));
        price.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));

        Label state = label(offerStatusLabel(offer), 20, stateTextColor(offer), 7.5f);
        state.layout(layout -> layout.widthPercent(19).height(20));
        state.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        row.addChildren(icon, nameBlock, price, state);
        return row;
    }

    void applyAuthoritativeView(DungeonShopView updatedView) {
        closeConfirmation();
        this.view = Objects.requireNonNull(updatedView, "Dungeon Shop view");
        this.activeTab = updatedView.initialTab();
        this.tabViews.put(activeTab, updatedView);
        this.selectedOffer = firstOffer(activeTab, searchText());
        refreshFromView();
    }

    private void refreshFromView() {
        walletLabel.setWallet(view.wallet());
        resetLabel.setText(resetValue(view.resetLabel()));
        statusLabel.setText(view.statusMessage().isBlank() ? "" : view.statusMessage());
        updateFilterButtons();
        refreshOffers();
        updateDetails();
        updateTabStyles();
    }

    private void refreshOffers() {
        List<DungeonShopView.Offer> all = filteredOffers();
        List<DungeonShopView.Offer> essential = all.stream()
            .filter(offer -> offer.section() == DungeonShopView.Section.ESSENTIAL)
            .toList();
        List<DungeonShopView.Offer> timed = activeTab == DungeonShopView.Tab.DAILY
            ? all.stream().filter(offer -> offer.section() == DungeonShopView.Section.TIMED).toList()
            : all;

        if (selectedOffer == null || all.stream().noneMatch(offer -> offer.id().equals(selectedOffer.id()))) {
            selectedOffer = all.isEmpty() ? null : all.getFirst();
        }

        boolean daily = activeTab == DungeonShopView.Tab.DAILY;
        essentialHeader.setDisplay(daily);
        essentialGroup.setDisplay(daily);
        essentialList.setDisplay(daily && !essential.isEmpty());
        essentialEmpty.setDisplay(daily && essential.isEmpty());
        essentialList.setItems(essential);

        timedHeader.setText(sectionTitle(activeTab));
        int timedAccent = activeTab == DungeonShopView.Tab.DAILY ? VIOLET : GOLD;
        timedHeader.setAccent(timedAccent);
        timedGroup.setAccent(timedAccent);
        timedGroup.layout(layout -> layout.height(daily ? 74 : 304));
        timedList.layout(layout -> layout.height(daily ? 62 : 292));
        timedList.setDisplay(!timed.isEmpty());
        timedList.setItems(timed);
        timedEmpty.setText(emptyText(activeTab));
        timedEmpty.setDisplay(timed.isEmpty());
        updateDetails();
    }

    private List<DungeonShopView.Offer> filteredOffers() {
        String search = searchText();
        return view.offers().stream()
            .filter(offer -> offer.tab() == activeTab)
            .filter(offer -> offer.matches(search, gradeFilter, typeFilter, vendorFilter))
            .toList();
    }

    private String searchText() {
        String value = searchField.getValue();
        return value == null ? "" : value;
    }

    private void selectTab(DungeonShopView.Tab tab) {
        DungeonClientSoundEffects.playMenuSelect();
        activeTab = Objects.requireNonNull(tab);
        DungeonShopView cached = tabViews.get(tab);
        if (cached != null) {
            view = cached;
        }
        selectedOffer = firstOffer(tab, searchText());
        refreshFromView();
        DungeonShopUiApi.requestTab(tab);
    }

    private void updateTabStyles() {
        DungeonShopView.Tab[] tabs = DungeonShopView.Tab.values();
        for (int index = 0; index < tabButtons.size(); index++) {
            DungeonShopView.Tab tab = tabs[index];
            Button button = tabButtons.get(index);
            int activeColor = switch (tab) {
                case DAILY -> CYAN;
                case WEEKLY -> GOLD;
                case BARTER -> 0xff42e6b5;
            };
            button.textStyle(style -> style.textColor(tab == activeTab ? activeColor : MUTED));
            button.buttonStyle(style -> style.baseTexture(new ColorRectTexture(0x00000000)));
            tabUnderlines.get(index).setIndicatorActive(tab == activeTab);
        }
    }

    private void selectOffer(DungeonShopView.Offer offer) {
        DungeonClientSoundEffects.playMenuSelect();
        selectedOffer = offer;
        updateDetails();
        refreshOfferLists();
    }

    private void refreshOfferLists() {
        List<DungeonShopView.Offer> all = filteredOffers();
        essentialList.setItems(all.stream()
            .filter(offer -> offer.section() == DungeonShopView.Section.ESSENTIAL).toList());
        timedList.setItems(activeTab == DungeonShopView.Tab.DAILY
            ? all.stream().filter(offer -> offer.section() == DungeonShopView.Section.TIMED).toList()
            : all);
    }

    private void updateDetails() {
        if (selectedOffer == null) {
            selectedArt.setOffer(null, null);
            selectedTitle.setText("NO OFFER SELECTED");
            selectedRarity.setText("");
            selectedMeta.setText("");
            selectedDescription.setText("No offers match the current tab and filters.");
            selectedDescription.setDisplay(true);
            selectedDescription.layout(layout -> layout.widthPercent(100).height(54));
            barterDetails.setOffer(null);
            barterDetails.layout(layout -> layout.widthPercent(100).height(0));
            selectedDetails.setDisplay(true);
            selectedDetails.layout(layout -> layout.widthPercent(100).height(36));
            selectedDetails.setText("");
            selectedPrice.setText("");
            actionButton.setText("UNAVAILABLE");
            actionButton.textStyle(style -> style.textColor(DIM));
            applyActionButtonStyle(actionButton, false);
            actionButton.setActive(false);
            previewButton.setDisplay(false);
            previewButton.setActive(false);
            return;
        }
        DungeonShopArt.Asset asset = DungeonShopArt.resolve(selectedOffer, DungeonShopArt.Surface.PREVIEW);
        selectedArt.setOffer(selectedOffer, asset);
        selectedTitle.setText(itemName(selectedOffer));
        boolean barter = selectedOffer.tab() == DungeonShopView.Tab.BARTER;
        selectedTitle.textStyle(style -> style.textColor(gradeColor(selectedOffer)));
        selectedRarity.setText(gradeLabel(selectedOffer));
        selectedRarity.textStyle(style -> style.textColor(gradeColor(selectedOffer)));
        selectedArt.layout(layout -> layout.widthPercent(100)
            .height(barter ? 104 : DETAIL_ART_HEIGHT));
        selectedTitle.layout(layout -> layout.widthPercent(100)
            .height(barter ? 24 : 28));
        selectedRarity.layout(layout -> layout.widthPercent(100)
            .height(barter ? 16 : 18));
        selectedMeta.setText("");
        selectedDescription.setDisplay(!barter);
        selectedDescription.layout(layout -> layout.widthPercent(100)
            .height(barter ? 0 : 54));
        selectedDescription.setText(selectedOffer.description());
        barterDetails.setOffer(barter ? selectedOffer : null);
        barterDetails.layout(layout -> layout.widthPercent(100)
            .height(barter ? 112 : 0));
        selectedDetails.setDisplay(!barter);
        selectedDetails.layout(layout -> layout.widthPercent(100)
            .height(barter ? 0 : 36));
        selectedDetails.setText(selectedOffer.detail()
            + (selectedOffer.requirements().isBlank() ? "" : "\n" + selectedOffer.requirements()));
        selectedPrice.setText(selectedOffer.price());
        String action = selectedOffer.actionLabel().isBlank()
            ? stateLabel(selectedOffer)
            : selectedOffer.actionLabel();
        actionButton.setText(action);
        actionButton.textStyle(style -> style.textColor(selectedOffer.actionable() ? CYAN : DIM));
        applyActionButtonStyle(actionButton, selectedOffer.actionable());
        actionButton.setActive(selectedOffer.actionable());
        boolean hudTheme = isHudThemeOffer(selectedOffer);
        previewButton.setDisplay(hudTheme);
        previewButton.setActive(hudTheme);
        previewButton.textStyle(style -> style.textColor(hudTheme ? CYAN : DIM));
        applyActionButtonStyle(previewButton, hudTheme);
    }

    private void previewSelectedHud() {
        if (selectedOffer == null || !isHudThemeOffer(selectedOffer)) return;
        DungeonClientSoundEffects.playMenuSelect();
        if (view.preview()) {
            statusLabel.setText("VISUAL PREVIEW — HUD PREVIEW NOT STARTED");
            return;
        }
        if (!DungeonRuntimeBridge.startHudPreview(selectedOffer.id())) {
            statusLabel.setText("HUD PREVIEW UNAVAILABLE");
            return;
        }
        minecraft.setScreen(null);
    }

    private void activateSelectedOffer() {
        if (selectedOffer == null || !selectedOffer.actionable()) return;
        DungeonClientSoundEffects.playMenuSelect();
        if (selectedOffer.state() == DungeonShopView.State.VENDOR_ROUTED) {
            if (view.preview()) {
                statusLabel.setText("VISUAL PREVIEW — VENDOR ROUTE NOT SENT");
                return;
            }
            DungeonShopUiApi.emit(DungeonShopUiApi.ActionType.VISIT_VENDOR, view, selectedOffer);
            statusLabel.setText("ROUTING TO " + selectedOffer.vendor().toUpperCase(Locale.ROOT));
            return;
        }

        pendingConfirmation = selectedOffer;
        confirmationTitle.setText(activeTab == DungeonShopView.Tab.BARTER
            ? "CONFIRM BARTER"
            : "CONFIRM PURCHASE");
        confirmationName.setText(itemName(selectedOffer));
        confirmationName.textStyle(style -> style.textColor(gradeColor(selectedOffer)));
        confirmationDescription.setText(selectedOffer.description());
        confirmationPrice.setText("COST  " + selectedOffer.price());
        confirmationOverlay.setDisplay(true);
        DungeonClientSoundEffects.playShopConfirmationOpen();
    }

    private void closeConfirmation() {
        pendingConfirmation = null;
        confirmationOverlay.setDisplay(false);
    }

    private void confirmPendingAction() {
        DungeonShopView.Offer offer = pendingConfirmation;
        DungeonClientSoundEffects.playMenuSelect();
        closeConfirmation();
        if (offer == null) return;
        if (view.preview()) {
            statusLabel.setText("VISUAL PREVIEW — NO TRANSACTION SENT");
            return;
        }
        DungeonShopUiApi.emit(
            offer.tab() == DungeonShopView.Tab.BARTER
                ? DungeonShopUiApi.ActionType.BARTER
                : DungeonShopUiApi.ActionType.PURCHASE,
            view,
            offer
        );
        statusLabel.setText("AWAITING SERVER CONFIRMATION");
    }

    private void cycleGradeFilter() {
        DungeonClientSoundEffects.playMenuSelect();
        gradeFilter = nextFilter(GRADE_FILTERS, gradeFilter);
        refreshFromView();
    }

    private void cycleTypeFilter() {
        DungeonClientSoundEffects.playMenuSelect();
        typeFilter = nextFilter(TYPE_FILTERS, typeFilter);
        refreshFromView();
    }

    private void cycleVendorFilter() {
        DungeonClientSoundEffects.playMenuSelect();
        vendorFilter = nextFilter(VENDOR_FILTERS, vendorFilter);
        refreshFromView();
    }

    private void updateFilterButtons() {
        filterButtons.get(0).setText("GRADE  " + gradeFilter.toUpperCase(Locale.ROOT) + "  v");
        filterButtons.get(1).setText("TYPE  " + typeFilter.toUpperCase(Locale.ROOT) + "  v");
        filterButtons.get(2).setText("VENDOR  " + vendorFilter.toUpperCase(Locale.ROOT) + "  v");
    }

    private static String nextFilter(String[] values, String current) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equalsIgnoreCase(current)) return values[(index + 1) % values.length];
        }
        return values[0];
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) {
            renderBlurredBackground(partialTick);
        }
        graphics.fillGradient(0, 0, width, height, 0x99020c26, 0xd0020c26);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (pendingConfirmation != null) {
                DungeonClientSoundEffects.playMenuClose();
                closeConfirmation();
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        DungeonClientSoundEffects.playMenuClose();
        minecraft.setScreen(parent);
    }

    private DungeonShopView.Offer firstOffer(DungeonShopView.Tab tab, String search) {
        return view.offers().stream()
            .filter(offer -> offer.tab() == tab)
            .filter(offer -> offer.matches(search, gradeFilter, typeFilter, vendorFilter))
            .findFirst().orElse(null);
    }

    private static String sectionTitle(DungeonShopView.Tab tab) {
        return switch (tab) {
            case DAILY -> "TODAY'S FINDS";
            case WEEKLY -> "WEEKLY SPECIALS";
            case BARTER -> "BARTER EXCHANGE";
        };
    }

    private static String emptyText(DungeonShopView.Tab tab) {
        return switch (tab) {
            case DAILY -> "No Today's Finds match these filters.";
            case WEEKLY -> "No Weekly Specials match these filters.";
            case BARTER -> "No Barter offers match these filters.";
        };
    }

    private static String itemName(DungeonShopView.Offer offer) {
        String name = offer.name();
        String prefix = offer.grade() + " ";
        if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return name.substring(prefix.length());
        }
        return name;
    }

    private static boolean isHudThemeOffer(DungeonShopView.Offer offer) {
        return offer != null
            && offer.icon() == DungeonShopView.Icon.THEME
            && "GUI Theme".equalsIgnoreCase(offer.type());
    }

    private static String rowMetadata(DungeonShopView.Offer offer) {
        if (offer.type().isBlank()) return offer.vendor();
        if (offer.vendor().isBlank()) return offer.type();
        return offer.type() + "  /  " + offer.vendor();
    }

    private static int rarityColor(String grade) {
        return switch (grade.toLowerCase(Locale.ROOT)) {
            case "normal" -> 0xffb8c5d9;
            case "special" -> 0xff39d9d1;
            case "rare" -> 0xff58a9ff;
            case "unique" -> 0xffbf76ff;
            case "legend" -> 0xffffc447;
            case "mythic" -> 0xffff68cc;
            case "genesis" -> 0xffff5d5d;
            default -> MUTED;
        };
    }

    private static String gradeLabel(DungeonShopView.Offer offer) {
        return offer.tab() == DungeonShopView.Tab.BARTER
            ? "GRADE I"
            : offer.rarity().isBlank() ? offer.grade() : offer.rarity();
    }

    private static int gradeColor(DungeonShopView.Offer offer) {
        return offer.tab() == DungeonShopView.Tab.BARTER
            ? GRADE_I_COLOR
            : rarityColor(offer.grade());
    }

    private static int stateAccent(DungeonShopView.Offer offer) {
        return switch (offer.state()) {
            case AVAILABLE, VENDOR_ROUTED -> CYAN;
            case LOCKED, SOLD_OUT -> 0xffff5d6c;
            case PURCHASED, OWNED -> GOLD;
            case DISPLAY_ONLY -> offer.accent();
        };
    }

    private static int stateTextColor(DungeonShopView.Offer offer) {
        return switch (offer.state()) {
            case AVAILABLE -> SUCCESS_GREEN;
            case VENDOR_ROUTED -> CYAN;
            case LOCKED, SOLD_OUT -> 0xffff5d6c;
            case PURCHASED, OWNED -> SUCCESS_GREEN;
            case DISPLAY_ONLY -> DIM;
        };
    }

    private static String stateLabel(DungeonShopView.Offer offer) {
        if (isMissingMaterials(offer)) return "MISSING MATERIALS";
        return switch (offer.state()) {
            case AVAILABLE -> "AVAILABLE";
            case VENDOR_ROUTED -> "VENDOR";
            case PURCHASED, OWNED -> "OWNED";
            case LOCKED -> offer.tab() == DungeonShopView.Tab.BARTER
                ? "MISSING MATERIALS"
                : "LOCKED";
            case SOLD_OUT -> "SOLD OUT";
            case DISPLAY_ONLY -> "DISPLAY";
        };
    }

    private static String offerStatusLabel(DungeonShopView.Offer offer) {
        if (isMissingMaterials(offer)) return "MISSING MATERIALS";
        if (offer.state() == DungeonShopView.State.AVAILABLE) {
            if (offer.unlimited()) {
                return "IN STOCK · UNLIMITED";
            }
            return offer.quantity() > 0 ? "IN STOCK · " + offer.quantity() : "IN STOCK";
        }
        return stateLabel(offer);
    }

    private static boolean isMissingMaterials(DungeonShopView.Offer offer) {
        return offer.tab() == DungeonShopView.Tab.BARTER
            && "MISSING MATERIALS".equalsIgnoreCase(offer.actionLabel());
    }

    private static String walletText(DungeonShopView.Wallet wallet) {
        return String.format(Locale.ROOT, "BRONZE %,d   SILVER %,d   GOLD %,d   STELLAR %,d",
            wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold());
    }

    private static String resetValue(String label) {
        String value = label == null ? "" : label.strip();
        if (value.regionMatches(true, 0, "Resets in", 0, "Resets in".length())) {
            return value.substring("Resets in".length()).stripLeading();
        }
        if (value.regionMatches(true, 0, "Resets", 0, "Resets".length())) {
            return value.substring("Resets".length()).stripLeading();
        }
        return value;
    }

    private static VirtualScrollerView<DungeonShopView.Offer> offerList(float height) {
        VirtualScrollerView<DungeonShopView.Offer> list = new VirtualScrollerView<>();
        list.layout(layout -> layout.widthPercent(100).height(height));
        list.style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        list.viewPort(viewPort -> viewPort.style(style ->
            style.backgroundTexture(new ColorRectTexture(0x00000000))));
        list.scrollerStyle(style -> style
            .mode(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode.VERTICAL)
            .verticalScrollDisplay(ScrollDisplay.AUTO));
        list.verticalScroller(scroller -> {
            scroller.layout(layout -> layout.width(7).gapAll(1));
            scroller.headButton.buttonStyle(style -> style
                .baseTexture(SpriteTexture.of(SHOP_SCROLL_ARROW_UP_TEXTURE).setColor(DIM))
                .hoverTexture(SpriteTexture.of(SHOP_SCROLL_ARROW_UP_TEXTURE).setColor(CYAN))
                .pressedTexture(SpriteTexture.of(SHOP_SCROLL_ARROW_UP_TEXTURE).setColor(TEXT)));
            scroller.tailButton.buttonStyle(style -> style
                .baseTexture(SpriteTexture.of(SHOP_SCROLL_ARROW_DOWN_TEXTURE).setColor(DIM))
                .hoverTexture(SpriteTexture.of(SHOP_SCROLL_ARROW_DOWN_TEXTURE).setColor(CYAN))
                .pressedTexture(SpriteTexture.of(SHOP_SCROLL_ARROW_DOWN_TEXTURE).setColor(TEXT)));
            scroller.scrollContainer.style(style -> style.backgroundTexture(SHOP_SCROLL_TRACK_V));
            scroller.scrollBar.buttonStyle(style -> style
                .baseTexture(SHOP_SCROLL_THUMB_BASE_V)
                .hoverTexture(SHOP_SCROLL_THUMB_HOVER_V)
                .pressedTexture(SHOP_SCROLL_THUMB_PRESSED_V));
        });
        list.virtualScrollerViewStyle(style -> style
            .itemHeightMode(VirtualItemHeightMode.FIXED)
            .estimatedItemHeight(62)
            .overscanPixels(62));
        return list;
    }

    private static Button filterButton(String text) {
        Button button = new Button().setText(text);
        button.layout(layout -> layout.width(112).height(22));
        button.textStyle(style -> style.textColor(MUTED).fontSize(8.3f));
        button.buttonStyle(style -> style
            .baseTexture(new ColorRectTexture(0x00000000))
            .hoverTexture(new ColorRectTexture(0x22164665))
            .pressedTexture(new ColorRectTexture(0x331b5f7d)));
        return button;
    }

    private static void applyActionButtonStyle(Button button, boolean actionable) {
        button.buttonStyle(style -> style
            .baseTexture(actionable ? SHOP_PURCHASE_BUTTON_BASE : SHOP_PURCHASE_BUTTON_DISABLED)
            .hoverTexture(actionable ? SHOP_PURCHASE_BUTTON_HOVER : SHOP_PURCHASE_BUTTON_DISABLED)
            .pressedTexture(actionable ? SHOP_PURCHASE_BUTTON_PRESSED : SHOP_PURCHASE_BUTTON_DISABLED));
    }

    private static SectionHeadingElement sectionHeading(String text, int accent) {
        SectionHeadingElement heading = new SectionHeadingElement(text, accent);
        heading.layout(layout -> layout.widthPercent(100).height(20));
        return heading;
    }

    private static SectionContainerElement sectionContainer(int accent) {
        return new SectionContainerElement(accent);
    }

    private static Label emptyLabel(String text) {
        Label label = label(text, 18, DIM, 7.2f);
        label.layout(layout -> layout.widthPercent(100).height(18));
        label.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        return label;
    }

    private static UIElement row(int color, float height) {
        return new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).height(height))
            .style(style -> style.backgroundTexture(new ColorRectTexture(color)));
    }

    private static UIElement column(int color, float height) {
        return new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.COLUMN).heightPercent(height))
            .style(style -> style.backgroundTexture(new ColorRectTexture(color)));
    }

    private static Label label(String text, float height, int color, float fontSize) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> layout.height(height));
        label.textStyle(style -> style.textColor(color).fontSize(fontSize).textWrap(TextWrap.HIDE));
        label.setAllowHitTest(false);
        return label;
    }
}
