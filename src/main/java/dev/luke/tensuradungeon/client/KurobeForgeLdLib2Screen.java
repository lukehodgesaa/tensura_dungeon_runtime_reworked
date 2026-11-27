package dev.luke.tensuradungeon.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualItemHeightMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Size;
import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class KurobeForgeLdLib2Screen extends ModularUIScreen {
    private static final int ORANGE = 0xffff8a2b;
    private static final int GOLD = 0xffffc45a;
    private static final int CYAN = 0xff44ddff;
    private static final int RED = 0xffff6548;
    private static final int TEXT = 0xffeee7dc;
    private static final int MUTED = 0xffa69e94;
    private static final int DIM = 0xff6d6b6b;
    private static final int BASE = 0xff090b0f;
    private static final int PANEL = 0xff11151b;
    private static final int PANEL_RAISED = 0xff181d24;
    private static final int PANEL_HOVER = 0xff202730;
    private static final int BORDER = 0xff3b424b;
    private static final int BORDER_DARK = 0xff242a31;
    private static final int BAR_TRACK = 0xff090d12;
    private static final int FORGE_STEEL = 0xff46505a;
    private static final int FORGE_STEEL_HIGHLIGHT = 0xff707981;
    private static final int FORGE_BRASS = 0xff9a6829;
    private static final int PREVIEW_BACKDROP_WIDTH = 512;
    private static final int PREVIEW_BACKDROP_HEIGHT = 216;
    private static final int ANVIL_WIDTH = 320;
    private static final int ANVIL_HEIGHT = 112;

    private static final ResourceLocation FORGE_PREVIEW_BACKDROP = kurobeTexture(
        "forge_preview_backdrop.png"
    );
    private static final ResourceLocation FORGE_ANVIL_BASE = kurobeTexture(
        "forge_anvil_base.png"
    );
    private static final ResourceLocation FORGE_ANVIL_HOTLIGHT = kurobeTexture(
        "forge_anvil_hotlight_mask.png"
    );
    private static final ResourceLocation FORGE_CREST = kurobeTexture("forge_crest.png");
    private static final ResourceLocation FORGE_SERVICE_PROMOTE = kurobeTexture(
        "forge_service_promote.png"
    );
    private static final ResourceLocation FORGE_SERVICE_CONDUIT = kurobeTexture(
        "forge_service_conduit.png"
    );
    private static final ResourceLocation FORGE_SERVICE_SOCKET = kurobeTexture(
        "forge_service_socket.png"
    );
    private static final ResourceLocation FORGE_SERVICE_RECHARGE = kurobeTexture(
        "forge_service_recharge.png"
    );
    private static final ResourceLocation FORGE_SERVICE_REMOVE = kurobeTexture(
        "forge_service_remove.png"
    );
    private static final ResourceLocation FORGE_CORE_SOCKET = kurobeTexture(
        "forge_core_socket.png"
    );
    private static final ResourceLocation FORGE_ANVIL_HOTLIGHT_TEXTURE =
        FORGE_ANVIL_HOTLIGHT;

    private static final SpriteTexture FORGE_CREST_TEXTURE = SpriteTexture.of(FORGE_CREST);
    private static final SpriteTexture FORGE_BACKDROP_TEXTURE = SpriteTexture.of(
        FORGE_PREVIEW_BACKDROP
    );
    private static final SpriteTexture FORGE_ANVIL_TEXTURE = SpriteTexture.of(FORGE_ANVIL_BASE);
    private static final SpriteTexture FORGE_CORE_SOCKET_TEXTURE = SpriteTexture.of(
        FORGE_CORE_SOCKET
    );
    private static final SpriteTexture FORGE_BUTTON_BASE = SpriteTexture.of(
        kurobeTexture("forge_button_base.png")
    ).setBorder(8);
    private static final SpriteTexture FORGE_BUTTON_HOVER = SpriteTexture.of(
        kurobeTexture("forge_button_hover.png")
    ).setBorder(8);
    private static final SpriteTexture FORGE_BUTTON_PRESSED = SpriteTexture.of(
        kurobeTexture("forge_button_pressed.png")
    ).setBorder(8);
    private static final SpriteTexture FORGE_BUTTON_DISABLED = SpriteTexture.of(
        kurobeTexture("forge_button_disabled.png")
    ).setBorder(8);
    private static final SpriteTexture FORGE_DIVIDER_H = SpriteTexture.of(
        kurobeTexture("forge_divider_h.png")
    );
    private static final class ForgeRootElement extends UIElement {
        ForgeRootElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(BASE)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            drawForgeOutline(
                context,
                left,
                top,
                left + Math.round(getSizeWidth()),
                top + Math.round(getSizeHeight()),
                FORGE_STEEL
            );
        }
    }

    private static final class UiParts {
        final ModularUI modularUI;
        final List<ServiceTileElement> serviceTiles;
        final VirtualScrollerView<KurobeForgeView.Weapon> weaponList;
        final ForgePreviewElement forgePreview;
        final WeaponStatsElement weaponStats;
        final Label serviceTitle;
        final Label serviceDescription;
        final Label serviceWarning;
        final Label costLabel;
        final Label statusLabel;
        final Button exitButton;
        final Button actionButton;

        UiParts(
            ModularUI modularUI,
            List<ServiceTileElement> serviceTiles,
            VirtualScrollerView<KurobeForgeView.Weapon> weaponList,
            ForgePreviewElement forgePreview,
            WeaponStatsElement weaponStats,
            Label serviceTitle,
            Label serviceDescription,
            Label serviceWarning,
            Label costLabel,
            Label statusLabel,
            Button exitButton,
            Button actionButton
        ) {
            this.modularUI = modularUI;
            this.serviceTiles = serviceTiles;
            this.weaponList = weaponList;
            this.forgePreview = forgePreview;
            this.weaponStats = weaponStats;
            this.serviceTitle = serviceTitle;
            this.serviceDescription = serviceDescription;
            this.serviceWarning = serviceWarning;
            this.costLabel = costLabel;
            this.statusLabel = statusLabel;
            this.exitButton = exitButton;
            this.actionButton = actionButton;
        }
    }

    private static final class ForgeCrestElement extends UIElement {
        ForgeCrestElement() {
            style(style -> style.backgroundTexture(FORGE_CREST_TEXTURE));
            setAllowHitTest(false);
        }
    }

    private static final class ServiceTileElement extends UIElement {
        private final KurobeForgeView.Service service;
        private boolean selected;
        private boolean hovered;

        ServiceTileElement(KurobeForgeView.Service service) {
            this.service = Objects.requireNonNull(service);
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            addEventListener(UIEvents.MOUSE_ENTER, event -> hovered = true);
            addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = false);
        }

        void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            int accent = serviceAccent(service);
            int surface = selected ? 0xff251b14 : hovered ? PANEL_HOVER : PANEL_RAISED;
            drawCleanForgePanel(
                context,
                left,
                top,
                right,
                bottom,
                surface,
                selected ? FORGE_BRASS : hovered ? FORGE_STEEL_HIGHLIGHT : FORGE_STEEL,
                false
            );
            int centerX = (left + right) / 2;
            context.drawTexture(
                SpriteTexture.of(serviceTexture(service)),
                centerX - 8,
                top + 16,
                16,
                16
            );
            String label = service.label();
            int textWidth = Math.round(Minecraft.getInstance().font.width(label) * 0.82f);
            drawScaledText(context, label, centerX - textWidth / 2, bottom - 18,
                selected ? TEXT : MUTED, 0.82f);
        }
    }

    private static final class WeaponRowElement extends UIElement {
        private final KurobeForgeView.Weapon weapon;
        private final boolean selected;
        private boolean hovered;

        WeaponRowElement(KurobeForgeView.Weapon weapon, boolean selected) {
            this.weapon = Objects.requireNonNull(weapon);
            this.selected = selected;
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            addEventListener(UIEvents.MOUSE_ENTER, event -> hovered = true);
            addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            int accent = gradeColor(weapon.grade());
            int surface = selected ? 0xff222129 : hovered ? PANEL_HOVER : 0xff121820;
            drawCleanForgePanel(
                context,
                left,
                top,
                right,
                bottom,
                surface,
                selected ? FORGE_BRASS : hovered ? FORGE_STEEL_HIGHLIGHT : BORDER_DARK,
                false
            );
            ItemStack stack = resolveWeaponStack(weapon);
            if (stack.isEmpty()) {
                drawWeaponGlyph(context, weapon.shape(), left + 10, top + 13, 68, 26, accent);
            } else {
                drawItemIcon(context.graphics, stack, left + 42, top + 31, 38);
            }
            drawFittedText(context, weapon.name(), left + 88, top + 8,
                TEXT, 1.00f, right - left - 98, 0.82f);
            String detail = weapon.forgeDataResolved()
                ? weapon.grade().label() + "  /  " + weapon.conduitLabel()
                : "INVENTORY SLOT " + (weapon.inventorySlot() + 1) + "  /  FORGE DATA PENDING";
            drawScaledText(context,
                detail,
                left + 88,
                top + 29,
                weapon.forgeDataResolved() ? accent : MUTED,
                0.74f
            );
            String socket = !weapon.forgeDataResolved()
                ? "EXACT INVENTORY ITEM PREVIEW"
                : weapon.socketed()
                    ? "CORE: " + weapon.coreName() + "  /  TIER " + weapon.coreTier()
                    : "EMPTY SOCKET";
            drawScaledText(context, socket, left + 88, top + 43,
                weapon.socketed() ? CYAN : DIM, 0.76f);
        }
    }

    private static final class ForgePreviewElement extends UIElement {
        private @Nullable KurobeForgeView.Weapon weapon;
        private KurobeForgeView.Service service = KurobeForgeView.Service.PROMOTE;
        private float rotationY;

        ForgePreviewElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setState(
            @Nullable KurobeForgeView.Weapon weapon,
            KurobeForgeView.Service service
        ) {
            String priorId = this.weapon == null ? "" : this.weapon.id();
            this.weapon = weapon;
            this.service = Objects.requireNonNull(service);
            if (weapon == null || !weapon.id().equals(priorId)) rotationY = 0.0f;
        }

        boolean contains(double mouseX, double mouseY) {
            float left = getPositionX();
            float top = getPositionY();
            return mouseX >= left && mouseX < left + getSizeWidth()
                && mouseY >= top && mouseY < top + getSizeHeight();
        }

        void rotate(double deltaX) {
            rotationY = (rotationY + (float) deltaX * 1.4f) % 360.0f;
        }

        boolean hasWeapon() {
            return weapon != null;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            int width = right - left;
            int height = bottom - top;
            int centerX = left + width / 2;
            int artLeft = centerX - PREVIEW_BACKDROP_WIDTH / 2;
            int artTop = top + Math.max(0, (height - PREVIEW_BACKDROP_HEIGHT) / 2);

            context.graphics.fill(left + 6, top + 6, right - 6, bottom - 6, 0xff0c0e12);
            context.drawTexture(
                FORGE_BACKDROP_TEXTURE,
                artLeft,
                artTop,
                PREVIEW_BACKDROP_WIDTH,
                PREVIEW_BACKDROP_HEIGHT
            );

            long animationMillis = System.currentTimeMillis();
            double pulse = 0.5 + 0.5 * Math.sin(animationMillis / 360.0);
            int flameBaseY = artTop + 182;
            drawForgeFlame(context, centerX, flameBaseY, pulse, animationMillis);

            int anvilX = centerX - ANVIL_WIDTH / 2;
            int anvilY = artTop + PREVIEW_BACKDROP_HEIGHT - ANVIL_HEIGHT;
            context.drawTexture(FORGE_ANVIL_TEXTURE, anvilX, anvilY, ANVIL_WIDTH, ANVIL_HEIGHT);
            int hotlightAlpha = 74 + (int) Math.round(pulse * 112.0);
            context.drawTexture(
                SpriteTexture.of(FORGE_ANVIL_HOTLIGHT_TEXTURE)
                    .setColor((hotlightAlpha << 24) | 0x00ffffff),
                anvilX,
                anvilY,
                ANVIL_WIDTH,
                ANVIL_HEIGHT
            );
            drawForgeOutline(context, left, top, right, bottom, FORGE_STEEL);

            drawScaledText(context, service.label(), left + 13, top + 10,
                serviceAccent(service), 0.76f);
            drawScaledText(context, "KUROBE FORGE PREVIEW", right - 142, top + 10,
                DIM, 0.62f);

            if (weapon == null) {
                drawScaledText(context, "NO ELIGIBLE WEAPON SELECTED", centerX - 88,
                    top + height / 2, DIM, 0.72f);
                return;
            }
            ItemStack stack = resolveWeaponStack(weapon);
            if (stack.isEmpty()) {
                int weaponWidth = Math.max(180, Math.min(340, width - 96));
                int weaponX = centerX - weaponWidth / 2;
                int weaponY = artTop + 51;
                drawWeaponGlyph(context, weapon.shape(), weaponX, weaponY, weaponWidth, 58,
                    gradeColor(weapon.grade()));
            } else {
                int targetSize = Math.max(82, Math.min(126, height - 68));
                drawRotatingPreviewItem(context.graphics, stack, weapon.id(),
                    left + 42, artTop + 24, width - 84, 132, targetSize, rotationY);
                drawScaledText(context, "DRAG WEAPON TO ROTATE", right - 170, bottom - 16,
                    MUTED, 0.68f);
            }
        }
    }

    private static final class WeaponStatsElement extends UIElement {
        private @Nullable KurobeForgeView.Weapon weapon;

        WeaponStatsElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
            setAllowHitTest(false);
        }

        void setWeapon(@Nullable KurobeForgeView.Weapon weapon) {
            this.weapon = weapon;
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            drawCleanForgePanel(context, left, top, right, bottom, PANEL, FORGE_STEEL, true);
            if (weapon == null) {
                drawScaledText(context, "SELECT AN ELIGIBLE WEAPON", left + 12, top + 12,
                    DIM, 0.75f);
                return;
            }

            int accent = gradeColor(weapon.grade());
            String gradeAndConduit = weapon.forgeDataResolved()
                ? weapon.grade().label() + "  /  " + weapon.conduitLabel()
                : "FORGE DATA PENDING";
            drawScaledText(context,
                gradeAndConduit,
                left + 11,
                top + 8,
                accent,
                0.84f
            );
            String core = !weapon.forgeDataResolved()
                ? "CORE DATA PENDING"
                : weapon.socketed()
                    ? "SOCKETED CORE  /  " + weapon.coreName() + "  /  TIER " + weapon.coreTier()
                    : "NO CORE SOCKETED";
            drawScaledText(context, core, left + 190, top + 8,
                weapon.socketed() ? CYAN : DIM, weapon.socketed() ? 0.86f : 0.78f);

            int socketX = right - 57;
            int socketY = top + 7;
            context.drawTexture(FORGE_CORE_SOCKET_TEXTURE, socketX, socketY, 48, 48);
            if (weapon.socketed()) {
                int coreColor = gradeColor(weapon.grade());
                context.graphics.fill(socketX + 20, socketY + 20, socketX + 28, socketY + 28,
                    coreColor);
                context.graphics.fill(socketX + 22, socketY + 18, socketX + 26, socketY + 30,
                    CYAN);
            }

            int valueX = left + 154;
            String equipmentEp = weapon.forgeDataResolved()
                ? formatCount(weapon.equipmentEp()) + " / " + formatCount(weapon.equipmentEpTarget())
                : "SERVER SNAPSHOT REQUIRED";
            String totalCharge = weapon.forgeDataResolved() && weapon.capacity() > 0L
                ? formatCount(weapon.totalCharge()) + " / " + formatCount(weapon.capacity()) : "--";
            String naturalCharge = weapon.forgeDataResolved() && weapon.capacity() > 0L
                ? formatCount(weapon.naturalCharge()) + " / " + formatCount(weapon.capacity()) : "--";
            drawStatText(context, "EQUIPMENT EP", equipmentEp,
                left + 11, valueX, top + 31, GOLD);
            drawStatText(context, "TOTAL CHARGE", totalCharge,
                left + 11, valueX, top + 51, CYAN);
            drawStatText(context, "NATURAL CHARGE", naturalCharge,
                left + 11, valueX, top + 71, ORANGE);

            String burst = weapon.burstOutput() < 0L ? "--" : formatCount(weapon.burstOutput());
            String flow = weapon.flowOutput() < 0L ? "--" : formatCount(weapon.flowOutput());
            drawScaledText(context, "BURST OUTPUT  " + burst + "    FLOW OUTPUT  " + flow,
                left + 11, bottom - 15, MUTED, 0.78f);
        }
    }

    private static final class FramedPanelElement extends UIElement {
        private final int accent;

        FramedPanelElement(int accent) {
            this.accent = accent;
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.round(getSizeWidth());
            int bottom = top + Math.round(getSizeHeight());
            drawCleanForgePanel(context, left, top, right, bottom, PANEL, FORGE_STEEL, true);
        }
    }

    private static final class ForgeRailElement extends UIElement {
        ForgeRailElement() {
            style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            drawCleanForgePanel(
                context,
                left,
                top,
                left + width,
                top + height,
                PANEL,
                FORGE_STEEL,
                true
            );
        }
    }

    private final Screen parent;
    private final List<ServiceTileElement> serviceTiles;
    private final VirtualScrollerView<KurobeForgeView.Weapon> weaponList;
    private final ForgePreviewElement forgePreview;
    private final WeaponStatsElement weaponStats;
    private final Label serviceTitle;
    private final Label serviceDescription;
    private final Label serviceWarning;
    private final Label costLabel;
    private final Label statusLabel;
    private final Button actionButton;
    private KurobeForgeView view;
    private KurobeForgeView.Service activeService;
    private @Nullable KurobeForgeView.Weapon selectedWeapon;
    private boolean rotatingWeaponPreview;
    private double lastRotationMouseX;

    KurobeForgeLdLib2Screen(KurobeForgeView view, @Nullable Screen parent) {
        this(createUi(), view, parent);
    }

    private KurobeForgeLdLib2Screen(
        UiParts parts,
        KurobeForgeView view,
        @Nullable Screen parent
    ) {
        super(parts.modularUI, Component.literal("Kurobe's Forge"));
        this.parent = parent;
        this.serviceTiles = parts.serviceTiles;
        this.weaponList = parts.weaponList;
        this.forgePreview = parts.forgePreview;
        this.weaponStats = parts.weaponStats;
        this.serviceTitle = parts.serviceTitle;
        this.serviceDescription = parts.serviceDescription;
        this.serviceWarning = parts.serviceWarning;
        this.costLabel = parts.costLabel;
        this.statusLabel = parts.statusLabel;
        this.actionButton = parts.actionButton;
        this.view = Objects.requireNonNull(view, "Kurobe Forge view");
        this.activeService = view.initialService();
        this.selectedWeapon = view.weapons().isEmpty() ? null : view.weapons().getFirst();

        parts.exitButton.setOnClick(event -> onClose());
        addHoverSound(parts.exitButton);
        for (int index = 0; index < serviceTiles.size(); index++) {
            KurobeForgeView.Service service = KurobeForgeView.Service.values()[index];
            ServiceTileElement tile = serviceTiles.get(index);
            tile.addEventListener(UIEvents.MOUSE_UP, event -> selectService(service));
            addHoverSound(tile);
        }
        weaponList.setItemUIProvider(this::weaponRow);
        actionButton.setOnClick(event -> previewOnlyAction());
        addHoverSound(actionButton);
        refreshFromView();
    }

    private static UiParts createUi() {
        UIElement root = new ForgeRootElement()
            .layout(layout -> layout
                .flexDirection(FlexDirection.COLUMN)
                .width(KurobeForgeLayout.LOGICAL_WIDTH)
                .height(KurobeForgeLayout.LOGICAL_HEIGHT)
                .paddingAll(KurobeForgeLayout.OUTER_PADDING)
                .gapAll(6))
            .transform(transform -> transform.scale(0.70f).pivot(0.5f, 0.5f));

        ForgeCrestElement crest = new ForgeCrestElement();
        crest.layout(layout -> layout.width(48).height(48));
        Label title = label("KUROBE'S FORGE", 25, TEXT, 17f);
        title.layout(layout -> layout.widthPercent(100).height(25).paddingLeft(14));
        Label subtitle = label(
            "WEAPON PROMOTION  /  SPELL CONDUITS  /  MONSTER CORE SERVICES",
            14,
            ORANGE,
            7.1f
        );
        subtitle.layout(layout -> layout.widthPercent(100).height(14).paddingLeft(14));
        UIElement titleBlock = column(0x00000000, 48);
        titleBlock.layout(layout -> layout.width(320).height(48).paddingTop(3).gapAll(1));
        titleBlock.addChildren(title, subtitle);
        UIElement headerSpacer = new UIElement()
            .layout(layout -> layout.flexGrow(1).flexBasis(0).height(1))
            .style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        Label previewBadge = label("UI PREVIEW", 18, ORANGE, 8f);
        previewBadge.layout(layout -> layout.width(90).height(18));
        previewBadge.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Button exitButton = new Button().setText("<  EXIT");
        exitButton.layout(layout -> layout.width(94).height(28));
        exitButton.textStyle(style -> style.textColor(TEXT).fontSize(9f));
        applyBorderlessExitStyle(exitButton);
        UIElement header = row(0x00000000, KurobeForgeLayout.HEADER_HEIGHT);
        header.layout(layout -> layout.widthPercent(100)
            .height(KurobeForgeLayout.HEADER_HEIGHT).alignItems(AlignItems.CENTER).gapAll(7));
        header.addChildren(crest, titleBlock, headerSpacer, previewBadge, exitButton);

        List<ServiceTileElement> serviceTiles = new ArrayList<>();
        ForgeRailElement serviceRail = new ForgeRailElement();
        serviceRail.layout(layout -> layout.widthPercent(13).heightPercent(100)
            .flexDirection(FlexDirection.COLUMN).paddingAll(5).paddingRight(9).gapAll(5));
        for (KurobeForgeView.Service service : KurobeForgeView.Service.values()) {
            ServiceTileElement tile = new ServiceTileElement(service);
            tile.layout(layout -> layout.widthPercent(100).flexGrow(1).flexBasis(0));
            serviceRail.addChild(tile);
            serviceTiles.add(tile);
        }

        Label selectorTitle = label("ELIGIBLE WEAPONS", 20, TEXT, 10f);
        selectorTitle.layout(layout -> layout.widthPercent(100).height(20).paddingTop(3));
        selectorTitle.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Label selectorHint = label("TAGGED INVENTORY + HOTBAR", 14, DIM, 6.5f);
        selectorHint.layout(layout -> layout.widthPercent(100).height(14));
        selectorHint.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        UIElement selectorDivider = new UIElement()
            .layout(layout -> layout.width(128).height(16).alignSelf(AlignItems.CENTER))
            .style(style -> style.backgroundTexture(FORGE_DIVIDER_H));
        selectorDivider.setAllowHitTest(false);
        VirtualScrollerView<KurobeForgeView.Weapon> weaponList = weaponList();
        weaponList.layout(layout -> layout.widthPercent(100).flexGrow(1).flexBasis(0));
        FramedPanelElement selector = new FramedPanelElement(BORDER);
        selector.layout(layout -> layout.widthPercent(29).heightPercent(100)
            .flexDirection(FlexDirection.COLUMN).paddingAll(6).gapAll(4));
        selector.addChildren(selectorTitle, selectorHint, selectorDivider, weaponList);

        ForgePreviewElement forgePreview = new ForgePreviewElement();
        forgePreview.layout(layout -> layout.widthPercent(100).flexGrow(1).flexBasis(0));
        WeaponStatsElement weaponStats = new WeaponStatsElement();
        weaponStats.layout(layout -> layout.widthPercent(100).height(98));

        Label serviceTitle = label("", 17, TEXT, 9.5f);
        serviceTitle.layout(layout -> layout.widthPercent(100).height(17));
        Label serviceDescription = label("", 31, MUTED, 7.1f);
        serviceDescription.layout(layout -> layout.widthPercent(100).height(31));
        serviceDescription.textStyle(style -> style.textWrap(TextWrap.WRAP));
        Label serviceWarning = label("", 15, RED, 6.7f);
        serviceWarning.layout(layout -> layout.widthPercent(100).height(15));
        FramedPanelElement serviceSummary = new FramedPanelElement(BORDER);
        serviceSummary.layout(layout -> layout.flexDirection(FlexDirection.COLUMN)
            .widthPercent(100).height(77).paddingHorizontal(9)
            .paddingTop(8).paddingBottom(3).gapAll(1));
        serviceSummary.addChildren(serviceTitle, serviceDescription, serviceWarning);

        Label costLabel = label("", 18, GOLD, 8f);
        costLabel.layout(layout -> layout.flexGrow(1).flexBasis(0).height(18));
        costLabel.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        Button actionButton = new Button().setText("SERVER HOOK PENDING");
        actionButton.layout(layout -> layout.widthPercent(38).height(42));
        actionButton.textStyle(style -> style.textColor(DIM).fontSize(9f));
        actionButton.setActive(false);
        applyActionButtonStyle(actionButton, false);
        FramedPanelElement actionRow = new FramedPanelElement(BORDER);
        actionRow.layout(layout -> layout.widthPercent(100).height(44)
            .flexDirection(FlexDirection.ROW).paddingHorizontal(7)
            .alignItems(AlignItems.CENTER).gapAll(8));
        actionRow.addChildren(costLabel, actionButton);

        UIElement workspace = column(0x00000000, 100);
        workspace.layout(layout -> layout.flexGrow(1).flexBasis(0)
            .heightPercent(100).gapAll(6));
        workspace.addChildren(forgePreview, weaponStats, serviceSummary, actionRow);

        UIElement body = row(0x00000000, 100);
        body.layout(layout -> layout.widthPercent(100).flexGrow(1).flexBasis(0).gapAll(7));
        body.addChildren(serviceRail, selector, workspace);

        Label statusLabel = label("", KurobeForgeLayout.STATUS_HEIGHT, MUTED, 6.8f);
        statusLabel.layout(layout -> layout.widthPercent(100)
            .height(KurobeForgeLayout.STATUS_HEIGHT));
        statusLabel.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        root.addChildren(header, body, statusLabel);

        ModularUI modularUI = ModularUI.of(UI.of(root, screenSize -> Size.of(
            Math.max(320, screenSize.width - 24),
            Math.max(300, screenSize.height - 24)
        )));
        return new UiParts(
            modularUI,
            List.copyOf(serviceTiles),
            weaponList,
            forgePreview,
            weaponStats,
            serviceTitle,
            serviceDescription,
            serviceWarning,
            costLabel,
            statusLabel,
            exitButton,
            actionButton
        );
    }

    void applyView(KurobeForgeView updatedView) {
        String selectedId = selectedWeapon == null ? "" : selectedWeapon.id();
        this.view = Objects.requireNonNull(updatedView, "Kurobe Forge view");
        this.activeService = updatedView.initialService();
        this.selectedWeapon = updatedView.weapons().stream()
            .filter(weapon -> weapon.id().equals(selectedId))
            .findFirst()
            .orElse(updatedView.weapons().isEmpty() ? null : updatedView.weapons().getFirst());
        refreshFromView();
    }

    private UIElement weaponRow(KurobeForgeView.Weapon weapon) {
        boolean selected = selectedWeapon != null && selectedWeapon.id().equals(weapon.id());
        WeaponRowElement row = new WeaponRowElement(weapon, selected);
        row.layout(layout -> layout.widthPercent(100)
            .height(KurobeForgeLayout.WEAPON_ROW_HEIGHT).marginVertical(1));
        row.addEventListener(UIEvents.MOUSE_UP, event -> selectWeapon(weapon));
        addHoverSound(row);
        return row;
    }

    private void selectService(KurobeForgeView.Service service) {
        if (activeService == service) return;
        DungeonClientSoundEffects.playMenuSelect();
        activeService = Objects.requireNonNull(service);
        refreshSelection();
    }

    private void selectWeapon(KurobeForgeView.Weapon weapon) {
        if (selectedWeapon != null && selectedWeapon.id().equals(weapon.id())) return;
        DungeonClientSoundEffects.playMenuSelect();
        selectedWeapon = Objects.requireNonNull(weapon);
        weaponList.setItems(view.weapons());
        refreshSelection();
    }

    private void refreshFromView() {
        weaponList.setItems(view.weapons());
        statusLabel.setText(view.statusMessage().isBlank()
            ? "AWAITING AUTHORITATIVE KUROBE SNAPSHOT" : view.statusMessage());
        refreshSelection();
    }

    private void refreshSelection() {
        for (int index = 0; index < serviceTiles.size(); index++) {
            serviceTiles.get(index).setSelected(
                KurobeForgeView.Service.values()[index] == activeService
            );
        }
        forgePreview.setState(selectedWeapon, activeService);
        weaponStats.setWeapon(selectedWeapon);

        KurobeForgeView.ServiceState state = view.serviceState(activeService, selectedWeapon);
        serviceTitle.setText(activeService.label() + "  /  "
            + activeService.transactionOwner().label() + " AUTHORITY");
        serviceDescription.setText(activeService.description());
        serviceWarning.setText(state.available()
            ? "ACTION BRIDGE NOT CONNECTED"
            : state.unavailableReason());
        costLabel.setText(state.costLabel());

        actionButton.setText(state.available()
            ? activeService.actionLabel()
            : "SERVER HOOK PENDING");
        actionButton.setActive(false);
        applyActionButtonStyle(actionButton, false);
    }

    private void previewOnlyAction() {
        statusLabel.setText("UI PREVIEW - NO FORGE TRANSACTION WAS SENT");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && forgePreview.hasWeapon() && forgePreview.contains(mouseX, mouseY)) {
            rotatingWeaponPreview = true;
            lastRotationMouseX = mouseX;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
        double mouseX,
        double mouseY,
        int button,
        double dragX,
        double dragY
    ) {
        if (button == 0 && rotatingWeaponPreview) {
            forgePreview.rotate(mouseX - lastRotationMouseX);
            lastRotationMouseX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && rotatingWeaponPreview) {
            rotatingWeaponPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        DungeonClientSoundEffects.playMenuClose();
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) renderBlurredBackground(partialTick);
        graphics.fillGradient(0, 0, width, height, 0xb009090b, 0xe0050608);
    }

    private static VirtualScrollerView<KurobeForgeView.Weapon> weaponList() {
        VirtualScrollerView<KurobeForgeView.Weapon> list = new VirtualScrollerView<>();
        list.style(style -> style.backgroundTexture(new ColorRectTexture(0x00000000)));
        list.viewPort(viewPort -> viewPort.style(style ->
            style.backgroundTexture(new ColorRectTexture(0x00000000))));
        list.scrollerStyle(style -> style
            .mode(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode.VERTICAL)
            .verticalScrollDisplay(ScrollDisplay.AUTO));
        list.verticalScroller(scroller -> {
            scroller.layout(layout -> layout.width(10).gapAll(1));
            scroller.headButton.buttonStyle(style -> style
                .baseTexture(new ColorRectTexture(BORDER_DARK))
                .hoverTexture(new ColorRectTexture(FORGE_STEEL))
                .pressedTexture(new ColorRectTexture(FORGE_BRASS)));
            scroller.tailButton.buttonStyle(style -> style
                .baseTexture(new ColorRectTexture(BORDER_DARK))
                .hoverTexture(new ColorRectTexture(FORGE_STEEL))
                .pressedTexture(new ColorRectTexture(FORGE_BRASS)));
            scroller.scrollContainer.style(style -> style
                .backgroundTexture(new ColorRectTexture(BAR_TRACK)));
            scroller.scrollBar.buttonStyle(style -> style
                .baseTexture(new ColorRectTexture(FORGE_STEEL))
                .hoverTexture(new ColorRectTexture(FORGE_STEEL_HIGHLIGHT))
                .pressedTexture(new ColorRectTexture(FORGE_BRASS)));
        });
        list.virtualScrollerViewStyle(style -> style
            .itemHeightMode(VirtualItemHeightMode.FIXED)
            .estimatedItemHeight(KurobeForgeLayout.WEAPON_ROW_HEIGHT)
            .overscanPixels(KurobeForgeLayout.WEAPON_ROW_HEIGHT));
        return list;
    }

    private static void drawForgeFlame(
        GUIContext context,
        int centerX,
        int baseY,
        double pulse,
        long animationMillis
    ) {
        int expansion = (int) Math.round(pulse * 8.0);
        int flameHeight = 27 + expansion;
        int outerAlpha = 36 + (int) Math.round(pulse * 34.0);
        int middleAlpha = 78 + (int) Math.round(pulse * 46.0);
        int coreAlpha = 132 + (int) Math.round(pulse * 72.0);

        context.graphics.fill(
            centerX - 44 - expansion,
            baseY - flameHeight - 7,
            centerX + 44 + expansion,
            baseY + 3,
            (outerAlpha << 24) | 0x00ff5b13
        );
        context.graphics.fill(
            centerX - 28 - expansion / 2,
            baseY - flameHeight,
            centerX + 28 + expansion / 2,
            baseY,
            (middleAlpha << 24) | 0x00ff7b18
        );
        context.graphics.fill(
            centerX - 13,
            baseY - flameHeight + 8,
            centerX + 13,
            baseY,
            (coreAlpha << 24) | 0x00ffc54a
        );

        int sparkPhase = (int) ((animationMillis / 95L) % 17L);
        int sparkY = baseY - 34 - sparkPhase;
        context.graphics.fill(centerX - 36, sparkY, centerX - 32, sparkY + 4, 0xb0ff9b2f);
        context.graphics.fill(centerX + 30, sparkY + 9, centerX + 33, sparkY + 12,
            0x90ffc447);
    }

    private static ResourceLocation serviceTexture(KurobeForgeView.Service service) {
        return switch (service) {
            case PROMOTE -> FORGE_SERVICE_PROMOTE;
            case CONDUIT -> FORGE_SERVICE_CONDUIT;
            case SOCKET -> FORGE_SERVICE_SOCKET;
            case RECHARGE -> FORGE_SERVICE_RECHARGE;
            case REMOVE -> FORGE_SERVICE_REMOVE;
        };
    }

    private static ResourceLocation kurobeTexture(String filename) {
        return ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon_ui",
            "textures/gui/kurobe/" + filename
        );
    }

    private static void drawStatText(
        GUIContext context,
        String label,
        String value,
        int labelX,
        int valueX,
        int y,
        int color
    ) {
        drawScaledText(context, label, labelX, y, TEXT, 0.84f);
        drawScaledText(context, value, valueX, y, color, 0.86f);
    }

    private static ItemStack resolveWeaponStack(KurobeForgeView.Weapon weapon) {
        Minecraft minecraft = Minecraft.getInstance();
        if (weapon.inventorySlot() >= 0 && minecraft.player != null
                && weapon.inventorySlot() < minecraft.player.getInventory().getContainerSize()) {
            ItemStack current = minecraft.player.getInventory().getItem(weapon.inventorySlot());
            return KurobeForgeInventoryProjection.matches(current, weapon)
                ? current.copy() : ItemStack.EMPTY;
        }
        try {
            ResourceLocation itemId = ResourceLocation.parse(weapon.itemId());
            return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static void drawItemIcon(
        GuiGraphics graphics,
        ItemStack stack,
        int centerX,
        int centerY,
        int size
    ) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 110.0f);
        float scale = size / 16.0f;
        pose.scale(scale, scale, 1.0f);
        pose.translate(-8.0f, -8.0f, 0.0f);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private static void drawRotatingPreviewItem(
        GuiGraphics graphics,
        ItemStack stack,
        String cacheKey,
        int x,
        int y,
        int width,
        int height,
        int targetSize,
        float rotationY
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer renderer = minecraft.getItemRenderer();
        BakedModel model = renderer.getModel(stack, minecraft.level, minecraft.player, 0);
        BakedModel boundsModel = model;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        boolean flatLighting = !model.usesBlockLight();
        try {
            pose.translate(x + width * 0.5f, y + height * 0.5f, 150.0f);
            pose.scale(targetSize, -targetSize, targetSize);
            ModelBounds bounds = WEAPON_MODEL_BOUNDS.computeIfAbsent(
                cacheKey,
                ignored -> findModelBounds(boundsModel, stack)
            );
            Vector3f rawCenter = bounds.center();
            Vector3f centeredGuiPivot = new Vector3f(
                rawCenter.x() - 0.5f,
                rawCenter.y() - 0.5f,
                rawCenter.z() - 0.5f
            );
            PoseStack guiTransform = new PoseStack();
            ClientHooks.handleCameraTransforms(
                guiTransform,
                model,
                ItemDisplayContext.GUI,
                false
            );
            Vector3f displayedPivot = guiTransform.last().pose()
                .transformPosition(new Vector3f(centeredGuiPivot));
            pose.translate(-displayedPivot.x(), -displayedPivot.y(), -displayedPivot.z());
            model = ClientHooks.handleCameraTransforms(
                pose,
                model,
                ItemDisplayContext.GUI,
                false
            );
            pose.translate(centeredGuiPivot.x(), centeredGuiPivot.y(), centeredGuiPivot.z());
            pose.mulPose(Axis.YP.rotationDegrees(rotationY));
            pose.translate(-rawCenter.x(), -rawCenter.y(), -rawCenter.z());
            if (flatLighting) Lighting.setupForFlatItems();
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                for (var renderType : pass.getRenderTypes(stack, true)) {
                    var consumer = ItemRenderer.getFoilBufferDirect(
                        graphics.bufferSource(), renderType, true, stack.hasFoil()
                    );
                    renderer.renderModelLists(
                        pass, stack, 15728880, OverlayTexture.NO_OVERLAY, pose, consumer
                    );
                }
            }
            graphics.flush();
        } finally {
            if (flatLighting) Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    private static ModelBounds findModelBounds(BakedModel model, ItemStack stack) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new ArrayList<>();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            quads.addAll(pass.getQuads(null, null, RandomSource.create(42L)));
            for (Direction direction : Direction.values()) {
                quads.addAll(pass.getQuads(null, direction, RandomSource.create(42L)));
            }
        }
        for (var quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                float vx = Float.intBitsToFloat(vertices[offset]);
                float vy = Float.intBitsToFloat(vertices[offset + 1]);
                float vz = Float.intBitsToFloat(vertices[offset + 2]);
                minX = Math.min(minX, vx);
                minY = Math.min(minY, vy);
                minZ = Math.min(minZ, vz);
                maxX = Math.max(maxX, vx);
                maxY = Math.max(maxY, vy);
                maxZ = Math.max(maxZ, vz);
            }
        }
        return quads.isEmpty()
            ? ModelBounds.UNIT
            : new ModelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void drawServiceGlyph(
        GUIContext context,
        KurobeForgeView.Service service,
        int x,
        int y,
        int color
    ) {
        switch (service) {
            case PROMOTE -> {
                context.graphics.fill(x + 9, y, x + 13, y + 19, color);
                context.graphics.fill(x + 4, y + 5, x + 18, y + 9, color);
                context.graphics.fill(x + 1, y + 9, x + 21, y + 12, color);
            }
            case CONDUIT -> {
                context.graphics.fill(x + 6, y + 2, x + 16, y + 4, color);
                context.graphics.fill(x + 3, y + 5, x + 6, y + 16, color);
                context.graphics.fill(x + 16, y + 5, x + 19, y + 16, color);
                context.graphics.fill(x + 6, y + 17, x + 16, y + 19, color);
                context.graphics.fill(x + 9, y + 7, x + 13, y + 14, CYAN);
            }
            case SOCKET -> {
                context.graphics.fill(x + 3, y + 3, x + 19, y + 18, BORDER);
                context.graphics.fill(x + 6, y + 6, x + 16, y + 15, color);
                context.graphics.fill(x + 9, y + 8, x + 13, y + 13, CYAN);
            }
            case RECHARGE -> {
                context.graphics.fill(x + 9, y + 1, x + 16, y + 9, color);
                context.graphics.fill(x + 5, y + 8, x + 12, y + 20, color);
                context.graphics.fill(x + 11, y + 7, x + 17, y + 10, color);
            }
            case REMOVE -> {
                context.graphics.fill(x + 3, y + 3, x + 7, y + 7, color);
                context.graphics.fill(x + 15, y + 3, x + 19, y + 7, color);
                context.graphics.fill(x + 6, y + 6, x + 16, y + 16, color);
                context.graphics.fill(x + 3, y + 15, x + 7, y + 19, color);
                context.graphics.fill(x + 15, y + 15, x + 19, y + 19, color);
            }
        }
    }

    private static void drawWeaponGlyph(
        GUIContext context,
        KurobeForgeView.WeaponShape shape,
        int x,
        int y,
        int width,
        int height,
        int accent
    ) {
        int centerY = y + height / 2;
        int handleWidth = Math.max(18, width / 5);
        int bladeLeft = x + 5;
        int bladeRight = x + width - handleWidth;
        int bladeHalf = switch (shape) {
            case GREATSWORD, HAMMER -> Math.max(5, height / 4);
            case SPEAR, RAPIER, KATANA -> Math.max(2, height / 9);
            case LONGSWORD -> Math.max(3, height / 7);
        };

        if (shape == KurobeForgeView.WeaponShape.HAMMER) {
            context.graphics.fill(bladeLeft + 8, centerY - height / 3,
                bladeLeft + Math.max(22, width / 4), centerY + height / 3, 0xff929aa3);
            context.graphics.fill(bladeLeft, centerY - height / 5,
                bladeLeft + 10, centerY + height / 5, 0xffd3d7db);
            context.graphics.fill(bladeLeft + Math.max(22, width / 4), centerY - height / 5,
                bladeLeft + Math.max(32, width / 3), centerY + height / 5, 0xff6e747b);
            context.graphics.fill(bladeLeft + Math.max(28, width / 3), centerY - 2,
                x + width - 4, centerY + 3, 0xff50341e);
        } else {
            context.graphics.fill(bladeLeft + 8, centerY - bladeHalf,
                bladeRight, centerY + bladeHalf + 1, 0xffd6d9dc);
            context.graphics.fill(bladeLeft + 13, centerY - bladeHalf,
                bladeRight - 3, centerY - bladeHalf + 2, 0xfff4f5f6);
            context.graphics.fill(bladeLeft + 2, centerY - Math.max(1, bladeHalf - 1),
                bladeLeft + 10, centerY + Math.max(2, bladeHalf), 0xffb6bcc2);
            if (shape == KurobeForgeView.WeaponShape.KATANA) {
                context.graphics.fill(bladeLeft + 20, centerY + bladeHalf,
                    bladeRight, centerY + bladeHalf + 2, accent);
            }
            context.graphics.fill(bladeRight - 1, centerY - bladeHalf - 3,
                bladeRight + 3, centerY + bladeHalf + 4, GOLD);
            context.graphics.fill(bladeRight + 3, centerY - 2,
                x + width - 4, centerY + 3, 0xff4b3320);
            context.graphics.fill(x + width - 8, centerY - 4,
                x + width - 2, centerY + 5, accent);
        }
        int coreX = Math.min(x + width - 20, bladeRight + 8);
        context.graphics.fill(coreX - 3, centerY - 4, coreX + 4, centerY + 5, BORDER_DARK);
        context.graphics.fill(coreX - 1, centerY - 2, coreX + 2, centerY + 3, CYAN);
    }

    private static int serviceAccent(KurobeForgeView.Service service) {
        return switch (service) {
            case PROMOTE -> GOLD;
            case CONDUIT, SOCKET, RECHARGE -> CYAN;
            case REMOVE -> RED;
        };
    }

    private static int gradeColor(KurobeForgeView.Grade grade) {
        return switch (grade) {
            case NORMAL -> 0xffd7d7d7;
            case SPECIAL -> 0xff69d88a;
            case RARE -> 0xff5aa7ff;
            case UNIQUE -> 0xffb775ff;
            case LEGEND -> 0xffffb43c;
            case MYTHIC -> 0xffff6b9e;
            case GENESIS -> 0xff8c7cff;
        };
    }

    private static void addHoverSound(UIElement element) {
        element.addEventListener(
            UIEvents.MOUSE_ENTER,
            event -> DungeonClientSoundEffects.playMenuHover()
        );
    }

    private static void applyBorderlessExitStyle(Button button) {
        button.buttonStyle(style -> style
            .baseTexture(new ColorRectTexture(0x00000000))
            .hoverTexture(new ColorRectTexture(PANEL_RAISED))
            .pressedTexture(new ColorRectTexture(PANEL_HOVER)));
    }

    private static void applyActionButtonStyle(Button button, boolean active) {
        button.buttonStyle(style -> style
            .baseTexture(active ? FORGE_BUTTON_BASE : FORGE_BUTTON_DISABLED)
            .hoverTexture(active ? FORGE_BUTTON_HOVER : FORGE_BUTTON_DISABLED)
            .pressedTexture(active ? FORGE_BUTTON_PRESSED : FORGE_BUTTON_DISABLED));
        button.textStyle(style -> style.textColor(active ? TEXT : DIM));
    }

    private static void drawCleanForgePanel(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int surface,
        int border,
        boolean ornamented
    ) {
        if (right - left < 8 || bottom - top < 8) return;
        context.graphics.fill(left, top, right, bottom, BORDER_DARK);
        context.graphics.fill(left + 1, top + 1, right - 1, bottom - 1, border);
        context.graphics.fill(left + 3, top + 3, right - 3, bottom - 3, surface);
        context.graphics.fill(left + 4, top + 3, right - 4, top + 4,
            FORGE_STEEL_HIGHLIGHT);
        if (!ornamented) return;

        context.graphics.fill(left + 1, top + 1, left + 7, top + 3, FORGE_BRASS);
        context.graphics.fill(right - 7, top + 1, right - 1, top + 3, FORGE_BRASS);
        context.graphics.fill(left + 1, bottom - 3, left + 7, bottom - 1, FORGE_BRASS);
        context.graphics.fill(right - 7, bottom - 3, right - 1, bottom - 1, FORGE_BRASS);
    }

    private static void drawForgeOutline(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int border
    ) {
        if (right - left < 8 || bottom - top < 8) return;
        context.graphics.fill(left, top, right, top + 2, BORDER_DARK);
        context.graphics.fill(left, bottom - 2, right, bottom, BORDER_DARK);
        context.graphics.fill(left, top + 2, left + 2, bottom - 2, BORDER_DARK);
        context.graphics.fill(right - 2, top + 2, right, bottom - 2, BORDER_DARK);
        context.graphics.fill(left + 2, top + 2, right - 2, top + 3, border);
        context.graphics.fill(left + 2, bottom - 3, right - 2, bottom - 2, border);
        context.graphics.fill(left + 2, top + 3, left + 3, bottom - 3, border);
        context.graphics.fill(right - 3, top + 3, right - 2, bottom - 3, border);
    }

    private static void fillPixelPanel(
        GUIContext context,
        int left,
        int top,
        int right,
        int bottom,
        int surface,
        int border
    ) {
        if (right - left < 8 || bottom - top < 8) return;
        context.graphics.fill(left + 3, top, right - 3, bottom, border);
        context.graphics.fill(left, top + 3, right, bottom - 3, border);
        context.graphics.fill(left + 3, top + 1, right - 3, bottom - 1, surface);
        context.graphics.fill(left + 1, top + 3, right - 1, bottom - 3, surface);
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

    private static void drawFittedText(
        GUIContext context,
        String text,
        int x,
        int y,
        int color,
        float preferredScale,
        int maximumWidth,
        float minimumScale
    ) {
        int rawWidth = Math.max(1, Minecraft.getInstance().font.width(text));
        float scale = Math.max(minimumScale, Math.min(preferredScale, maximumWidth / (float) rawWidth));
        drawScaledText(context, text, x, y, color, scale);
    }

    private static final Map<String, ModelBounds> WEAPON_MODEL_BOUNDS = new HashMap<>();

    private record ModelBounds(
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ
    ) {
        private static final ModelBounds UNIT = new ModelBounds(0, 0, 0, 1, 1, 1);

        Vector3f center() {
            return new Vector3f(
                (minX + maxX) * 0.5f,
                (minY + maxY) * 0.5f,
                (minZ + maxZ) * 0.5f
            );
        }
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static UIElement row(int color, float height) {
        return new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.ROW).height(height))
            .style(style -> style.backgroundTexture(new ColorRectTexture(color)));
    }

    private static UIElement column(int color, float height) {
        return new UIElement()
            .layout(layout -> layout.flexDirection(FlexDirection.COLUMN).height(height))
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
