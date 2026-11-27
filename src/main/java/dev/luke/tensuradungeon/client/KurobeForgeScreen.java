package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import com.mineclawd.tensuradungeon.client.DungeonHudSuppressingScreen;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
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

final class KurobeForgeScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int W = KurobeForgeLayout.LOGICAL_WIDTH;
    private static final int H = KurobeForgeLayout.LOGICAL_HEIGHT;

    private static final int BODY_X = 14;
    private static final int BODY_Y = 68;
    private static final int BODY_HEIGHT = 456;
    private static final int SERVICE_WIDTH = 124;
    private static final int SELECTOR_X = 145;
    private static final int SELECTOR_WIDTH = 270;
    private static final int WORKSPACE_X = 422;
    private static final int WORKSPACE_WIDTH = 524;
    private static final int PREVIEW_HEIGHT = 219;
    private static final int STATS_Y = 293;
    private static final int STATS_HEIGHT = 98;
    private static final int SUMMARY_Y = 397;
    private static final int SUMMARY_HEIGHT = 77;
    private static final int ACTION_Y = 480;
    private static final int ACTION_HEIGHT = 44;
    private static final int STATUS_Y = 530;

    private static final int ORANGE = 0xFFFF8A2B;
    private static final int GOLD = 0xFFFFC45A;
    private static final int CYAN = 0xFF44DDFF;
    private static final int RED = 0xFFFF6548;
    private static final int TEXT = 0xFFEEE7DC;
    private static final int MUTED = 0xFFA69E94;
    private static final int DIM = 0xFF6D6B6B;
    private static final int BASE = 0xFF090B0F;
    private static final int PANEL = 0xFF11151B;
    private static final int PANEL_RAISED = 0xFF181D24;
    private static final int PANEL_HOVER = 0xFF202730;
    private static final int BORDER_DARK = 0xFF242A31;
    private static final int BAR_TRACK = 0xFF090D12;
    private static final int FORGE_STEEL = 0xFF46505A;
    private static final int FORGE_STEEL_HIGHLIGHT = 0xFF707981;
    private static final int FORGE_BRASS = 0xFF9A6829;

    private static final int PREVIEW_BACKDROP_WIDTH = 512;
    private static final int PREVIEW_BACKDROP_HEIGHT = 216;
    private static final int ANVIL_WIDTH = 320;
    private static final int ANVIL_HEIGHT = 112;
    private static final int WEAPON_PREVIEW_TOP_INSET = 8;
    private static final int WEAPON_PREVIEW_CLEARANCE = 8;
    private static final int WEAPON_PREVIEW_TARGET_SIZE = 78;

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
    private static final ResourceLocation FORGE_DIVIDER_H = kurobeTexture("forge_divider_h.png");
    private static final ResourceLocation FORGE_CORE_SOCKET = kurobeTexture(
        "forge_core_socket.png"
    );
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

    private static final Map<String, ModelBounds> WEAPON_MODEL_BOUNDS = new HashMap<>();

    private final Screen parent;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private KurobeForgeView view;
    private KurobeForgeView.Service activeService;
    private @Nullable KurobeForgeView.Weapon selectedWeapon;
    private @Nullable KurobeForgeView.CoreOption selectedCore;
    private @Nullable PendingConfirmation pendingConfirmation;
    private int weaponScroll;
    private boolean rotatingWeaponPreview;
    private double lastRotationMouseX;
    private float weaponPreviewRotationY;
    private String hoveredHotspotId = "";
    private float uiScale = 1.0F;
    private float uiX;
    private float uiY;

    KurobeForgeScreen(KurobeForgeView view, @Nullable Screen parent) {
        super(Component.literal("Kurobe's Forge"));
        this.view = Objects.requireNonNull(view, "Kurobe Forge view");
        this.parent = parent;
        this.activeService = view.initialService();
        this.selectedWeapon = view.weapons().isEmpty() ? null : view.weapons().getFirst();
        this.selectedCore = firstCompatibleCore();
    }

    void applyView(KurobeForgeView updatedView) {
        String selectedId = selectedWeapon == null ? "" : selectedWeapon.id();
        String selectedCoreId = selectedCore == null ? "" : selectedCore.id();
        view = Objects.requireNonNull(updatedView, "Kurobe Forge view");
        activeService = updatedView.initialService();
        selectedWeapon = updatedView.weapons().stream()
            .filter(weapon -> weapon.id().equals(selectedId))
            .findFirst()
            .orElse(updatedView.weapons().isEmpty() ? null : updatedView.weapons().getFirst());
        selectedCore = updatedView.cores().stream()
            .filter(core -> core.id().equals(selectedCoreId) && coreCompatible(core))
            .findFirst()
            .orElse(firstCompatibleCore());
        weaponScroll = clampWeaponScroll(weaponScroll);
        weaponPreviewRotationY = 0.0F;
        pendingConfirmation = null;
    }

    @Override
    protected void init() {
        super.init();
        updateTransform();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateTransform();
        drawOutsideBackdrop(graphics, partialTick);
        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        hotspots.clear();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(uiX, uiY, 0.0F);
        pose.scale(uiScale, uiScale, 1.0F);

        graphics.fill(0, 0, W, H, BASE);
        graphics.fillGradient(0, 0, W, 70, 0xD80B0D11, 0x00090B0F);
        drawHeader(graphics, vx, vy);
        drawServiceRail(graphics, vx, vy);
        drawWeaponSelector(graphics, vx, vy);
        drawDwarvenForgePreview(graphics);
        drawWeaponStats(graphics);
        drawServiceSummary(graphics);
        drawActionRow(graphics);
        drawStatus(graphics);
        drawForgeOutline(graphics, 0, 0, W, H, FORGE_STEEL);

        pose.popPose();
        updateHoverSound(vx, vy);
    }

    private void updateTransform() {
        uiScale = Math.min(
            Math.max(1, width - 24) / (float) W,
            Math.max(1, height - 24) / (float) H
        );
        uiX = (width - W * uiScale) * 0.5F;
        uiY = (height - H * uiScale) * 0.5F;
    }

    private int virtualX(double mouseX) {
        return (int) Math.floor((mouseX - uiX) / Math.max(0.01F, uiScale));
    }

    private int virtualY(double mouseY) {
        return (int) Math.floor((mouseY - uiY) / Math.max(0.01F, uiScale));
    }

    private void drawOutsideBackdrop(GuiGraphics graphics, float partialTick) {
        if (minecraft != null && minecraft.level != null) {
            renderBlurredBackground(partialTick);
        }
        graphics.fillGradient(0, 0, width, height, 0xB009090B, 0xE0050608);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        blit(graphics, FORGE_CREST, 14, 14, 48, 48, 48, 48);
        drawFit(graphics, "KUROBE'S FORGE", 74, 17, 410, TEXT, 1.88F, 1.30F);
        if (view.preview()) {
            drawCenteredFit(
                graphics,
                "UI PREVIEW",
                786,
                28,
                110,
                ORANGE,
                0.82F,
                0.68F
            );
        }
        int exitX = 852;
        int exitY = 24;
        int exitWidth = 94;
        int exitHeight = 28;
        boolean exitHovered = contains(exitX, exitY, exitWidth, exitHeight, mouseX, mouseY);
        if (exitHovered) {
            graphics.fill(exitX, exitY, exitX + exitWidth, exitY + exitHeight, PANEL_RAISED);
        }
        drawCenteredFit(
            graphics,
            "<  EXIT",
            exitX + exitWidth / 2,
            exitY + 9,
            exitWidth - 12,
            TEXT,
            0.95F,
            0.78F
        );
        addHotspot("exit", exitX, exitY, exitWidth, exitHeight, this::onClose);
    }

    private void drawServiceRail(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCleanForgePanel(
            graphics,
            BODY_X,
            BODY_Y,
            SERVICE_WIDTH,
            BODY_HEIGHT,
            PANEL,
            FORGE_STEEL,
            true
        );
        int tileX = BODY_X + 6;
        int tileWidth = SERVICE_WIDTH - 12;
        int tileHeight = 84;
        int gap = 5;
        KurobeForgeView.Service[] services = KurobeForgeView.Service.values();
        for (int index = 0; index < services.length; index++) {
            KurobeForgeView.Service service = services[index];
            int tileY = BODY_Y + 6 + index * (tileHeight + gap);
            boolean selected = service == activeService;
            boolean hovered = contains(tileX, tileY, tileWidth, tileHeight, mouseX, mouseY);
            int surface = selected ? 0xFF251B14 : hovered ? PANEL_HOVER : PANEL_RAISED;
            int border = selected ? FORGE_BRASS
                : hovered ? FORGE_STEEL_HIGHLIGHT : FORGE_STEEL;
            drawCleanForgePanel(
                graphics,
                tileX,
                tileY,
                tileWidth,
                tileHeight,
                surface,
                border,
                false
            );
            int centerX = tileX + tileWidth / 2;
            blit(graphics, serviceTexture(service), centerX - 8, tileY + 17, 16, 16, 16, 16);
            drawCenteredFit(
                graphics,
                service.label(),
                centerX,
                tileY + tileHeight - 18,
                tileWidth - 12,
                selected ? TEXT : MUTED,
                0.82F,
                0.72F
            );
            addHotspot(
                "service:" + service.name(),
                tileX,
                tileY,
                tileWidth,
                tileHeight,
                () -> selectService(service)
            );
        }
    }

    private void drawWeaponSelector(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCleanForgePanel(
            graphics,
            SELECTOR_X,
            BODY_Y,
            SELECTOR_WIDTH,
            BODY_HEIGHT,
            PANEL,
            FORGE_STEEL,
            true
        );
        drawCenteredFit(
            graphics,
            "ELIGIBLE WEAPONS",
            SELECTOR_X + SELECTOR_WIDTH / 2,
            BODY_Y + 9,
            SELECTOR_WIDTH - 24,
            TEXT,
            1.04F,
            0.88F
        );
        drawCenteredFit(
            graphics,
            "TAGGED INVENTORY + HOTBAR",
            SELECTOR_X + SELECTOR_WIDTH / 2,
            BODY_Y + 31,
            SELECTOR_WIDTH - 24,
            DIM,
            0.72F,
            0.62F
        );
        blit(
            graphics,
            FORGE_DIVIDER_H,
            SELECTOR_X + (SELECTOR_WIDTH - 128) / 2,
            BODY_Y + 47,
            128,
            16,
            128,
            16
        );

        int listX = SELECTOR_X + 7;
        int listY = BODY_Y + 68;
        int listHeight = BODY_HEIGHT - 79;
        int rowPitch = KurobeForgeLayout.WEAPON_ROW_HEIGHT;
        int visibleRows = Math.max(1, listHeight / rowPitch);
        int maxScroll = Math.max(0, view.weapons().size() - visibleRows);
        weaponScroll = Math.max(0, Math.min(weaponScroll, maxScroll));
        int rowWidth = SELECTOR_WIDTH - 26;

        if (view.weapons().isEmpty()) {
            drawCenteredFit(
                graphics,
                "NO TAGGED WEAPONS FOUND",
                SELECTOR_X + SELECTOR_WIDTH / 2,
                listY + 42,
                SELECTOR_WIDTH - 28,
                DIM,
                0.78F,
                0.66F
            );
        } else {
            int end = Math.min(view.weapons().size(), weaponScroll + visibleRows);
            for (int index = weaponScroll; index < end; index++) {
                int rowY = listY + (index - weaponScroll) * rowPitch;
                drawWeaponRow(
                    graphics,
                    view.weapons().get(index),
                    listX,
                    rowY,
                    rowWidth,
                    rowPitch - 4,
                    mouseX,
                    mouseY
                );
            }
        }
        drawWeaponScrollbar(graphics, listY, listHeight, visibleRows, maxScroll);
    }

    private void drawWeaponRow(
        GuiGraphics graphics,
        KurobeForgeView.Weapon weapon,
        int x,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        boolean selected = selectedWeapon != null && selectedWeapon.id().equals(weapon.id());
        boolean hovered = contains(x, y, width, height, mouseX, mouseY);
        int surface = selected ? 0xFF222129 : hovered ? PANEL_HOVER : 0xFF121820;
        int border = selected ? FORGE_BRASS
            : hovered ? FORGE_STEEL_HIGHLIGHT : BORDER_DARK;
        drawCleanForgePanel(graphics, x, y, width, height, surface, border, false);

        int accent = gradeColor(weapon.grade());
        ItemStack stack = resolveWeaponStack(weapon);
        if (stack.isEmpty()) {
            drawWeaponGlyph(graphics, weapon.shape(), x + 7, y + 17, 54, 22, accent);
        } else {
            drawItemIcon(graphics, stack, x + 33, y + height / 2, 34);
        }

        int textX = x + 68;
        int textWidth = width - 76;
        drawFit(graphics, weapon.name(), textX, y + 7, textWidth, TEXT, 0.96F, 0.78F);
        String detail = weapon.forgeDataResolved()
            ? weapon.grade().label() + "  /  " + weapon.conduitLabel()
            : "SLOT " + (weapon.inventorySlot() + 1) + "  /  FORGE DATA PENDING";
        drawFit(
            graphics,
            detail,
            textX,
            y + 25,
            textWidth,
            weapon.forgeDataResolved() ? accent : MUTED,
            0.76F,
            0.64F
        );
        String socket = !weapon.forgeDataResolved()
            ? "EXACT INVENTORY ITEM PREVIEW"
            : weapon.socketed()
                ? "CORE: " + weapon.coreName() + "  /  TIER " + weapon.coreTier()
                : "EMPTY SOCKET";
        drawFit(
            graphics,
            socket,
            textX,
            y + 40,
            textWidth,
            weapon.socketed() ? CYAN : DIM,
            0.76F,
            0.62F
        );
        addHotspot("weapon:" + weapon.id(), x, y, width, height, () -> selectWeapon(weapon));
    }

    private void drawWeaponScrollbar(
        GuiGraphics graphics,
        int y,
        int height,
        int visibleRows,
        int maxScroll
    ) {
        if (maxScroll <= 0) return;
        int x = SELECTOR_X + SELECTOR_WIDTH - 11;
        graphics.fill(x, y, x + 5, y + height, BAR_TRACK);
        int totalRows = Math.max(visibleRows, view.weapons().size());
        int thumbHeight = Math.max(28, height * visibleRows / totalRows);
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + (maxScroll == 0 ? 0 : travel * weaponScroll / maxScroll);
        graphics.fill(x + 1, thumbY, x + 4, thumbY + thumbHeight, FORGE_STEEL);
        graphics.fill(x + 2, thumbY, x + 3, thumbY + thumbHeight, FORGE_STEEL_HIGHLIGHT);
    }

    private void drawDwarvenForgePreview(GuiGraphics graphics) {
        int x = WORKSPACE_X;
        int y = BODY_Y;
        int width = WORKSPACE_WIDTH;
        int height = PREVIEW_HEIGHT;
        int centerX = x + width / 2;
        int artX = centerX - PREVIEW_BACKDROP_WIDTH / 2;
        int artY = y + Math.max(0, (height - PREVIEW_BACKDROP_HEIGHT) / 2);

        graphics.fill(x + 6, y + 6, x + width - 6, y + height - 6, 0xFF0C0E12);
        blit(
            graphics,
            FORGE_PREVIEW_BACKDROP,
            artX,
            artY,
            PREVIEW_BACKDROP_WIDTH,
            PREVIEW_BACKDROP_HEIGHT,
            PREVIEW_BACKDROP_WIDTH,
            PREVIEW_BACKDROP_HEIGHT
        );

        long animationMillis = System.currentTimeMillis();
        double pulse = 0.5D + 0.5D * Math.sin(animationMillis / 360.0D);
        drawForgeFlame(graphics, centerX, artY + 182, pulse, animationMillis);

        int anvilX = centerX - ANVIL_WIDTH / 2;
        int anvilY = artY + PREVIEW_BACKDROP_HEIGHT - ANVIL_HEIGHT;
        blit(
            graphics,
            FORGE_ANVIL_BASE,
            anvilX,
            anvilY,
            ANVIL_WIDTH,
            ANVIL_HEIGHT,
            ANVIL_WIDTH,
            ANVIL_HEIGHT
        );
        int hotlightAlpha = 74 + (int) Math.round(pulse * 112.0D);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, hotlightAlpha / 255.0F);
        blit(
            graphics,
            FORGE_ANVIL_HOTLIGHT,
            anvilX,
            anvilY,
            ANVIL_WIDTH,
            ANVIL_HEIGHT,
            ANVIL_WIDTH,
            ANVIL_HEIGHT
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        drawForgeOutline(graphics, x, y, width, height, FORGE_STEEL);
        drawFit(
            graphics,
            activeService.label(),
            x + 13,
            y + 10,
            120,
            serviceAccent(activeService),
            0.76F,
            0.66F
        );
        drawFit(
            graphics,
            "KUROBE FORGE PREVIEW",
            x + width - 142,
            y + 10,
            128,
            DIM,
            0.62F,
            0.56F
        );
        drawFit(
            graphics,
            "CERT  " + view.certifiedGrade().label(),
            x + 13,
            y + 32,
            120,
            ORANGE,
            0.72F,
            0.62F
        );

        if (selectedWeapon == null) {
            drawCenteredFit(
                graphics,
                "NO ELIGIBLE WEAPON SELECTED",
                centerX,
                y + height / 2,
                width - 120,
                DIM,
                0.78F,
                0.68F
            );
            return;
        }

        int weaponSafeTop = artY + WEAPON_PREVIEW_TOP_INSET;
        int weaponSafeBottom = anvilY - WEAPON_PREVIEW_CLEARANCE;
        int weaponSafeHeight = Math.max(24, weaponSafeBottom - weaponSafeTop);
        ItemStack stack = resolveWeaponStack(selectedWeapon);
        if (stack.isEmpty()) {
            int weaponWidth = Math.max(180, Math.min(340, width - 96));
            int weaponHeight = Math.min(44, weaponSafeHeight);
            drawWeaponGlyph(
                graphics,
                selectedWeapon.shape(),
                centerX - weaponWidth / 2,
                weaponSafeTop + (weaponSafeHeight - weaponHeight) / 2,
                weaponWidth,
                weaponHeight,
                gradeColor(selectedWeapon.grade())
            );
        } else {
            drawRotatingPreviewItem(
                graphics,
                stack,
                selectedWeapon.id(),
                x + 42,
                weaponSafeTop,
                width - 84,
                weaponSafeHeight,
                WEAPON_PREVIEW_TARGET_SIZE,
                weaponPreviewRotationY
            );
            drawFit(
                graphics,
                "DRAG WEAPON TO ROTATE",
                x + width - 170,
                y + height - 16,
                156,
                MUTED,
                0.68F,
                0.60F
            );
        }
    }

    private void drawWeaponStats(GuiGraphics graphics) {
        int x = WORKSPACE_X;
        int y = STATS_Y;
        int width = WORKSPACE_WIDTH;
        int height = STATS_HEIGHT;
        drawCleanForgePanel(graphics, x, y, width, height, PANEL, FORGE_STEEL, true);
        if (selectedWeapon == null) {
            drawFit(
                graphics,
                "SELECT AN ELIGIBLE WEAPON",
                x + 12,
                y + 13,
                width - 24,
                DIM,
                0.82F,
                0.72F
            );
            return;
        }

        KurobeForgeView.Weapon weapon = selectedWeapon;
        int accent = gradeColor(weapon.grade());
        String gradeAndConduit = weapon.forgeDataResolved()
            ? weapon.grade().label() + "  /  " + weapon.conduitLabel()
            : "FORGE DATA PENDING";
        drawFit(graphics, gradeAndConduit, x + 11, y + 8, 168, accent, 0.84F, 0.72F);
        String core = !weapon.forgeDataResolved()
            ? "CORE DATA PENDING"
            : weapon.socketed()
                ? "SOCKETED CORE  /  " + weapon.coreName() + "  /  TIER " + weapon.coreTier()
                : "NO CORE SOCKETED";
        drawFit(
            graphics,
            core,
            x + 190,
            y + 8,
            width - 260,
            weapon.socketed() ? CYAN : DIM,
            weapon.socketed() ? 0.86F : 0.78F,
            0.64F
        );

        int socketX = x + width - 57;
        int socketY = y + 7;
        blit(graphics, FORGE_CORE_SOCKET, socketX, socketY, 48, 48, 48, 48);
        if (weapon.socketed()) {
            graphics.fill(socketX + 20, socketY + 20, socketX + 28, socketY + 28, accent);
            graphics.fill(socketX + 22, socketY + 18, socketX + 26, socketY + 30, CYAN);
        }

        String equipmentEp = weapon.forgeDataResolved()
            ? formatCount(weapon.equipmentEp()) + " / " + formatCount(weapon.equipmentEpTarget())
            : "SERVER SNAPSHOT REQUIRED";
        String totalCharge = weapon.socketed() && weapon.capacity() > 0L
            ? formatCount(weapon.totalCharge()) + " / " + formatCount(weapon.capacity())
            : "--";
        String naturalCharge = weapon.socketed() && weapon.capacity() > 0L
            ? formatCount(weapon.naturalCharge()) + " / " + formatCount(weapon.capacity())
            : "--";
        drawStatText(graphics, "EQUIPMENT EP", equipmentEp, x + 11, x + 155, y + 31, GOLD);
        drawStatText(graphics, "TOTAL CHARGE", totalCharge, x + 11, x + 155, y + 51, CYAN);
        drawStatText(
            graphics,
            "NATURAL CHARGE",
            naturalCharge,
            x + 11,
            x + 155,
            y + 71,
            ORANGE
        );
        String burst = weapon.burstOutput() < 0L ? "--" : formatCount(weapon.burstOutput());
        String flow = weapon.flowOutput() < 0L ? "--" : formatCount(weapon.flowOutput());
        drawFit(
            graphics,
            "ACCESSIBLE  " + formatCount(weapon.accessibleCapacity())
                + "    BURST OUTPUT  " + burst + "    FLOW OUTPUT  " + flow,
            x + 11,
            y + height - 15,
            width - 82,
            MUTED,
            0.78F,
            0.66F
        );
    }

    private void drawServiceSummary(GuiGraphics graphics) {
        int x = WORKSPACE_X;
        int y = SUMMARY_Y;
        int width = WORKSPACE_WIDTH;
        drawCleanForgePanel(
            graphics,
            x,
            y,
            width,
            SUMMARY_HEIGHT,
            PANEL,
            FORGE_STEEL,
            true
        );
        KurobeForgeView.ServiceState state = view.serviceState(activeService, selectedWeapon);
        if (activeService == KurobeForgeView.Service.SOCKET) {
            drawSocketSelector(graphics, x, y, width, state);
            return;
        }
        drawFit(
            graphics,
            activeService.label() + "  /  " + activeService.transactionOwner().label()
                + " AUTHORITY",
            x + 10,
            y + 10,
            width - 20,
            TEXT,
            1.02F,
            0.78F
        );
        drawWrappedText(
            graphics,
            activeService.description(),
            x + 10,
            y + 31,
            width - 20,
            MUTED,
            0.76F,
            2
        );
        drawFit(
            graphics,
            state.available() ? "SERVER QUOTE READY  /  CONFIRMATION REQUIRED" : state.unavailableReason(),
            x + 10,
            y + SUMMARY_HEIGHT - 18,
            width - 20,
            RED,
            0.72F,
            0.62F
        );
    }

    private void drawSocketSelector(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        KurobeForgeView.ServiceState state
    ) {
        drawFit(
            graphics,
            "SOCKET  /  EXACT CORE SATCHEL AUTHORITY",
            x + 10,
            y + 9,
            width - 20,
            TEXT,
            0.96F,
            0.76F
        );
        if (!state.available() || selectedCore == null) {
            drawCenteredFit(
                graphics,
                state.available() ? "NO COMPATIBLE CORE SELECTED" : state.unavailableReason(),
                x + width / 2,
                y + 38,
                width - 80,
                RED,
                0.78F,
                0.62F
            );
            return;
        }

        drawCenteredFit(
            graphics,
            "<",
            x + 24,
            y + 38,
            22,
            CYAN,
            1.18F,
            0.90F
        );
        drawCenteredFit(
            graphics,
            ">",
            x + width - 24,
            y + 38,
            22,
            CYAN,
            1.18F,
            0.90F
        );
        drawCenteredFit(
            graphics,
            selectedCore.name() + "  /  TIER " + roman(selectedCore.tier())
                + "  /  SATCHEL SLOT " + (selectedCore.satchelSlot() + 1),
            x + width / 2,
            y + 29,
            width - 92,
            CYAN,
            0.86F,
            0.68F
        );
        drawCenteredFit(
            graphics,
            "TOTAL " + formatCount(selectedCore.totalCharge()) + " / "
                + formatCount(selectedCore.capacity()) + "    NATURAL "
                + formatCount(selectedCore.naturalCharge()),
            x + width / 2,
            y + 49,
            width - 92,
            MUTED,
            0.72F,
            0.60F
        );
        addHotspot("forge:core-previous", x + 8, y + 26, 34, 40, () -> cycleCore(-1));
        addHotspot("forge:core-next", x + width - 42, y + 26, 34, 40, () -> cycleCore(1));
    }

    private void drawActionRow(GuiGraphics graphics) {
        int x = WORKSPACE_X;
        int width = WORKSPACE_WIDTH;
        drawCleanForgePanel(
            graphics,
            x,
            ACTION_Y,
            width,
            ACTION_HEIGHT,
            PANEL,
            FORGE_STEEL,
            true
        );
        KurobeForgeView.ServiceState state = view.serviceState(activeService, selectedWeapon);
        boolean actionAvailable = state.available()
            && (activeService != KurobeForgeView.Service.SOCKET || selectedCore != null);
        String quote = activeService == KurobeForgeView.Service.SOCKET && selectedCore != null
            ? selectedCore.socketCostLabel()
            : state.costLabel();
        String actionLabel = activeService == KurobeForgeView.Service.PROMOTE
                && state.costLabel().contains("CERTIFICATION")
            ? "PURCHASE CERTIFICATION"
            : activeService.actionLabel();
        int buttonWidth = 200;
        int buttonX = x + width - buttonWidth - 7;
        boolean confirming = pendingConfirmation != null
            && selectedWeapon != null
            && pendingConfirmation.weaponId().equals(selectedWeapon.id())
            && pendingConfirmation.service() == activeService
            && pendingConfirmation.revision() == view.revision()
            && pendingCoreStillSelected();
        drawCenteredFit(
            graphics,
            confirming && selectedWeapon != null
                ? "CONFIRM  " + selectedWeapon.name()
                : quote,
            x + (buttonX - x) / 2,
            ACTION_Y + 17,
            buttonX - x - 18,
            GOLD,
            0.82F,
            0.66F
        );
        if (confirming) {
            int gap = 5;
            int half = (buttonWidth - gap) / 2;
            drawActionButton(graphics, buttonX, half, "CANCEL", MUTED);
            drawActionButton(graphics, buttonX + half + gap, half, "CONFIRM", GOLD);
            addHotspot(
                "forge:cancel",
                buttonX,
                ACTION_Y + 4,
                half,
                ACTION_HEIGHT - 8,
                this::cancelConfirmation
            );
            addHotspot(
                "forge:confirm",
                buttonX + half + gap,
                ACTION_Y + 4,
                half,
                ACTION_HEIGHT - 8,
                this::confirmAction
            );
        } else {
            drawActionButton(
                graphics,
                buttonX,
                buttonWidth,
                actionAvailable ? actionLabel : "NOT AVAILABLE",
                actionAvailable ? GOLD : DIM
            );
            if (actionAvailable && selectedWeapon != null && !view.preview()) {
                addHotspot(
                    "forge:action",
                    buttonX,
                    ACTION_Y + 4,
                    buttonWidth,
                    ACTION_HEIGHT - 8,
                    this::beginConfirmation
                );
            }
        }
    }

    private void drawActionButton(
        GuiGraphics graphics,
        int x,
        int width,
        String label,
        int color
    ) {
        drawCleanForgePanel(
            graphics,
            x,
            ACTION_Y + 4,
            width,
            ACTION_HEIGHT - 8,
            0xFF15181D,
            BORDER_DARK,
            false
        );
        drawCenteredFit(
            graphics,
            label,
            x + width / 2,
            ACTION_Y + 17,
            width - 12,
            color,
            0.90F,
            0.72F
        );
    }

    private void drawStatus(GuiGraphics graphics) {
        String status = pendingConfirmation != null && selectedWeapon != null
            ? "CONFIRM " + activeService.label() + " FOR " + selectedWeapon.name()
            : view.statusMessage();
        if (status == null || status.isBlank()) return;
        drawCenteredFit(graphics, status, W / 2, STATUS_Y + 4, W - 40, MUTED, 0.72F, 0.58F);
    }

    private void selectService(KurobeForgeView.Service service) {
        if (service == activeService) return;
        DungeonClientSoundEffects.playMenuSelect();
        activeService = Objects.requireNonNull(service);
        if (activeService == KurobeForgeView.Service.SOCKET) ensureCompatibleCore();
        pendingConfirmation = null;
    }

    private void selectWeapon(KurobeForgeView.Weapon weapon) {
        if (selectedWeapon != null && selectedWeapon.id().equals(weapon.id())) return;
        DungeonClientSoundEffects.playMenuSelect();
        selectedWeapon = Objects.requireNonNull(weapon);
        ensureCompatibleCore();
        weaponPreviewRotationY = 0.0F;
        pendingConfirmation = null;
    }

    private void beginConfirmation() {
        if (selectedWeapon == null || view.preview()) return;
        KurobeForgeView.ServiceState state = view.serviceState(activeService, selectedWeapon);
        if (!state.available()
                || (activeService == KurobeForgeView.Service.SOCKET && selectedCore == null)) return;
        pendingConfirmation = new PendingConfirmation(
            selectedWeapon.id(),
            activeService,
            view.revision(),
            activeService == KurobeForgeView.Service.SOCKET ? selectedCore.id() : "",
            activeService == KurobeForgeView.Service.SOCKET ? selectedCore.satchelSlot() : -1
        );
        DungeonClientSoundEffects.playShopConfirmationOpen();
    }

    private void cancelConfirmation() {
        if (pendingConfirmation == null) return;
        pendingConfirmation = null;
        DungeonClientSoundEffects.playMenuSelect();
    }

    private void confirmAction() {
        PendingConfirmation pending = pendingConfirmation;
        if (pending == null || selectedWeapon == null
                || !pending.weaponId().equals(selectedWeapon.id())
                || pending.service() != activeService
                || pending.revision() != view.revision()
                || !pendingCoreStillSelected()) {
            pendingConfirmation = null;
            return;
        }
        pendingConfirmation = null;
        DungeonClientSoundEffects.playMenuSelect();
        DungeonRuntimeBridge.submitKurobeForgeAction(
            pending.service(),
            pending.weaponId(),
            pending.revision(),
            pending.coreId(),
            pending.coreSlot()
        );
    }

    private boolean pendingCoreStillSelected() {
        if (pendingConfirmation == null) return true;
        if (pendingConfirmation.service() != KurobeForgeView.Service.SOCKET) {
            return pendingConfirmation.coreId().isBlank() && pendingConfirmation.coreSlot() == -1;
        }
        return selectedCore != null
            && pendingConfirmation.coreId().equals(selectedCore.id())
            && pendingConfirmation.coreSlot() == selectedCore.satchelSlot();
    }

    private void ensureCompatibleCore() {
        if (selectedCore != null && coreCompatible(selectedCore)) return;
        selectedCore = firstCompatibleCore();
    }

    private @Nullable KurobeForgeView.CoreOption firstCompatibleCore() {
        return view.cores().stream().filter(this::coreCompatible).findFirst().orElse(null);
    }

    private boolean coreCompatible(KurobeForgeView.CoreOption core) {
        return core != null && selectedWeapon != null && selectedWeapon.forgeDataResolved()
            && !selectedWeapon.socketed()
            && core.tier() <= selectedWeapon.grade().ordinal() + 1;
    }

    private void cycleCore(int direction) {
        List<KurobeForgeView.CoreOption> compatible = view.cores().stream()
            .filter(this::coreCompatible)
            .toList();
        if (compatible.isEmpty()) {
            selectedCore = null;
            pendingConfirmation = null;
            return;
        }
        int current = selectedCore == null ? -1 : compatible.indexOf(selectedCore);
        int next = Math.floorMod(current + direction, compatible.size());
        selectedCore = compatible.get(next);
        pendingConfirmation = null;
        DungeonClientSoundEffects.playMenuSelect();
    }

    private int clampWeaponScroll(int requested) {
        int listHeight = BODY_HEIGHT - 79;
        int visibleRows = Math.max(1, listHeight / KurobeForgeLayout.WEAPON_ROW_HEIGHT);
        return Math.max(0, Math.min(requested, Math.max(0, view.weapons().size() - visibleRows)));
    }

    private void addHotspot(String id, int x, int y, int width, int height, Runnable action) {
        hotspots.add(new Hotspot(id, x, y, width, height, action));
    }

    private @Nullable Hotspot hovered(int mouseX, int mouseY) {
        for (int index = hotspots.size() - 1; index >= 0; index--) {
            Hotspot hotspot = hotspots.get(index);
            if (hotspot.contains(mouseX, mouseY)) return hotspot;
        }
        return null;
    }

    private void updateHoverSound(int mouseX, int mouseY) {
        Hotspot hovered = hovered(mouseX, mouseY);
        String id = hovered == null ? "" : hovered.id();
        if (!id.isBlank() && !id.equals(hoveredHotspotId)) {
            DungeonClientSoundEffects.playMenuHover();
        }
        hoveredHotspotId = id;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        if (selectedWeapon != null
                && contains(WORKSPACE_X, BODY_Y, WORKSPACE_WIDTH, PREVIEW_HEIGHT, vx, vy)) {
            rotatingWeaponPreview = true;
            lastRotationMouseX = mouseX;
            return true;
        }
        Hotspot hotspot = hovered(vx, vy);
        if (hotspot != null) {
            hotspot.action().run();
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
            double deltaX = (mouseX - lastRotationMouseX) / Math.max(0.01F, uiScale);
            weaponPreviewRotationY = (weaponPreviewRotationY + (float) deltaX * 1.4F) % 360.0F;
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
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        int vx = virtualX(mouseX);
        int vy = virtualY(mouseY);
        if (scrollY != 0.0D
                && activeService == KurobeForgeView.Service.SOCKET
                && contains(WORKSPACE_X, SUMMARY_Y, WORKSPACE_WIDTH, SUMMARY_HEIGHT, vx, vy)) {
            cycleCore(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        if (scrollY != 0.0D
                && contains(SELECTOR_X, BODY_Y, SELECTOR_WIDTH, BODY_HEIGHT, vx, vy)) {
            weaponScroll = clampWeaponScroll(weaponScroll + (scrollY < 0.0D ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (pendingConfirmation != null) {
                cancelConfirmation();
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawForgeFlame(
        GuiGraphics graphics,
        int centerX,
        int baseY,
        double pulse,
        long animationMillis
    ) {
        int expansion = (int) Math.round(pulse * 8.0D);
        int flameHeight = 27 + expansion;
        int outerAlpha = 36 + (int) Math.round(pulse * 34.0D);
        int middleAlpha = 78 + (int) Math.round(pulse * 46.0D);
        int coreAlpha = 132 + (int) Math.round(pulse * 72.0D);

        graphics.fill(
            centerX - 44 - expansion,
            baseY - flameHeight - 7,
            centerX + 44 + expansion,
            baseY + 3,
            (outerAlpha << 24) | 0x00FF5B13
        );
        graphics.fill(
            centerX - 28 - expansion / 2,
            baseY - flameHeight,
            centerX + 28 + expansion / 2,
            baseY,
            (middleAlpha << 24) | 0x00FF7B18
        );
        graphics.fill(
            centerX - 13,
            baseY - flameHeight + 8,
            centerX + 13,
            baseY,
            (coreAlpha << 24) | 0x00FFC54A
        );
        int sparkPhase = (int) ((animationMillis / 95L) % 17L);
        int sparkY = baseY - 34 - sparkPhase;
        graphics.fill(centerX - 36, sparkY, centerX - 32, sparkY + 4, 0xB0FF9B2F);
        graphics.fill(centerX + 30, sparkY + 9, centerX + 33, sparkY + 12, 0x90FFC447);
    }

    private static void drawCleanForgePanel(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int surface,
        int border,
        boolean ornamented
    ) {
        if (width < 8 || height < 8) return;
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, BORDER_DARK);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, border);
        graphics.fill(x + 3, y + 3, right - 3, bottom - 3, surface);
        graphics.fill(x + 4, y + 3, right - 4, y + 4, FORGE_STEEL_HIGHLIGHT);
        if (!ornamented) return;
        graphics.fill(x + 1, y + 1, x + 7, y + 3, FORGE_BRASS);
        graphics.fill(right - 7, y + 1, right - 1, y + 3, FORGE_BRASS);
        graphics.fill(x + 1, bottom - 3, x + 7, bottom - 1, FORGE_BRASS);
        graphics.fill(right - 7, bottom - 3, right - 1, bottom - 1, FORGE_BRASS);
    }

    private static void drawForgeOutline(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int border
    ) {
        if (width < 8 || height < 8) return;
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, y + 2, BORDER_DARK);
        graphics.fill(x, bottom - 2, right, bottom, BORDER_DARK);
        graphics.fill(x, y + 2, x + 2, bottom - 2, BORDER_DARK);
        graphics.fill(right - 2, y + 2, right, bottom - 2, BORDER_DARK);
        graphics.fill(x + 2, y + 2, right - 2, y + 3, border);
        graphics.fill(x + 2, bottom - 3, right - 2, bottom - 2, border);
        graphics.fill(x + 2, y + 3, x + 3, bottom - 3, border);
        graphics.fill(right - 3, y + 3, right - 2, bottom - 3, border);
    }

    private static void drawStatText(
        GuiGraphics graphics,
        String label,
        String value,
        int labelX,
        int valueX,
        int y,
        int color
    ) {
        drawFit(graphics, label, labelX, y, valueX - labelX - 8, TEXT, 0.84F, 0.72F);
        drawFit(graphics, value, valueX, y, 230, color, 0.86F, 0.70F);
    }

    private static void drawWrappedText(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int maximumWidth,
        int color,
        float scale,
        int maximumLines
    ) {
        List<String> lines = wrapText(text, maximumWidth, scale);
        int lineHeight = Math.max(8, Math.round(10.0F * scale));
        int count = Math.min(maximumLines, lines.size());
        for (int index = 0; index < count; index++) {
            String line = lines.get(index);
            if (index == count - 1 && count < lines.size()) {
                line = ellipsize(line + "...", maximumWidth, scale);
            }
            drawScaled(graphics, line, x, y + index * lineHeight, color, scale);
        }
    }

    private static List<String> wrapText(String text, int maximumWidth, float scale) {
        var font = Minecraft.getInstance().font;
        int rawMaximum = Math.max(1, (int) Math.floor(maximumWidth / Math.max(0.01F, scale)));
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : text.strip().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(candidate) > rawMaximum) {
                lines.add(current);
                current = word;
            } else {
                current = candidate;
            }
        }
        if (!current.isEmpty()) lines.add(current);
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static void drawFit(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int maximumWidth,
        int color,
        float preferredScale,
        float minimumScale
    ) {
        var font = Minecraft.getInstance().font;
        int rawWidth = Math.max(1, font.width(text));
        float scale = Math.min(preferredScale, maximumWidth / (float) rawWidth);
        scale = Math.max(minimumScale, scale);
        String fitted = ellipsize(text, maximumWidth, scale);
        drawScaled(graphics, fitted, x, y, color, scale);
    }

    private static void drawCenteredFit(
        GuiGraphics graphics,
        String text,
        int centerX,
        int y,
        int maximumWidth,
        int color,
        float preferredScale,
        float minimumScale
    ) {
        var font = Minecraft.getInstance().font;
        int rawWidth = Math.max(1, font.width(text));
        float scale = Math.min(preferredScale, maximumWidth / (float) rawWidth);
        scale = Math.max(minimumScale, scale);
        String fitted = ellipsize(text, maximumWidth, scale);
        int renderedWidth = Math.round(font.width(fitted) * scale);
        drawScaled(graphics, fitted, centerX - renderedWidth / 2, y, color, scale);
    }

    private static String ellipsize(String text, int maximumWidth, float scale) {
        var font = Minecraft.getInstance().font;
        int rawMaximum = Math.max(1, (int) Math.floor(maximumWidth / Math.max(0.01F, scale)));
        if (font.width(text) <= rawMaximum) return text;
        String suffix = "...";
        int bodyWidth = Math.max(1, rawMaximum - font.width(suffix));
        return font.plainSubstrByWidth(text, bodyWidth) + suffix;
    }

    private static void drawScaled(
        GuiGraphics graphics,
        String text,
        int x,
        int y,
        int color,
        float scale
    ) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static void blit(
        GuiGraphics graphics,
        ResourceLocation texture,
        int x,
        int y,
        int width,
        int height,
        int sourceWidth,
        int sourceHeight
    ) {
        graphics.blit(
            texture,
            x,
            y,
            width,
            height,
            0.0F,
            0.0F,
            sourceWidth,
            sourceHeight,
            sourceWidth,
            sourceHeight
        );
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
        pose.translate(centerX, centerY, 110.0F);
        float scale = size / 16.0F;
        pose.scale(scale, scale, 1.0F);
        pose.translate(-8.0F, -8.0F, 0.0F);
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
            pose.translate(x + width * 0.5F, y + height * 0.5F, 150.0F);
            pose.scale(targetSize, -targetSize, targetSize);
            ModelBounds bounds = WEAPON_MODEL_BOUNDS.computeIfAbsent(
                cacheKey,
                ignored -> findModelBounds(boundsModel, stack)
            );
            Vector3f rawCenter = bounds.center();
            Vector3f centeredGuiPivot = new Vector3f(
                rawCenter.x() - 0.5F,
                rawCenter.y() - 0.5F,
                rawCenter.z() - 0.5F
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
                        pass,
                        stack,
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        pose,
                        consumer
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
        List<BakedQuad> quads = new ArrayList<>();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            quads.addAll(pass.getQuads(null, null, RandomSource.create(42L)));
            for (Direction direction : Direction.values()) {
                quads.addAll(pass.getQuads(null, direction, RandomSource.create(42L)));
            }
        }
        for (BakedQuad quad : quads) {
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

    private static void drawWeaponGlyph(
        GuiGraphics graphics,
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
            graphics.fill(
                bladeLeft + 8,
                centerY - height / 3,
                bladeLeft + Math.max(22, width / 4),
                centerY + height / 3,
                0xFF929AA3
            );
            graphics.fill(
                bladeLeft,
                centerY - height / 5,
                bladeLeft + 10,
                centerY + height / 5,
                0xFFD3D7DB
            );
            graphics.fill(
                bladeLeft + Math.max(28, width / 3),
                centerY - 2,
                x + width - 4,
                centerY + 3,
                0xFF50341E
            );
        } else {
            graphics.fill(
                bladeLeft + 8,
                centerY - bladeHalf,
                bladeRight,
                centerY + bladeHalf + 1,
                0xFFD6D9DC
            );
            graphics.fill(
                bladeLeft + 13,
                centerY - bladeHalf,
                bladeRight - 3,
                centerY - bladeHalf + 2,
                0xFFF4F5F6
            );
            graphics.fill(
                bladeLeft + 2,
                centerY - Math.max(1, bladeHalf - 1),
                bladeLeft + 10,
                centerY + Math.max(2, bladeHalf),
                0xFFB6BCC2
            );
            if (shape == KurobeForgeView.WeaponShape.KATANA) {
                graphics.fill(
                    bladeLeft + 20,
                    centerY + bladeHalf,
                    bladeRight,
                    centerY + bladeHalf + 2,
                    accent
                );
            }
            graphics.fill(
                bladeRight - 1,
                centerY - bladeHalf - 3,
                bladeRight + 3,
                centerY + bladeHalf + 4,
                GOLD
            );
            graphics.fill(
                bladeRight + 3,
                centerY - 2,
                x + width - 4,
                centerY + 3,
                0xFF4B3320
            );
            graphics.fill(x + width - 8, centerY - 4, x + width - 2, centerY + 5, accent);
        }
        int coreX = Math.min(x + width - 20, bladeRight + 8);
        graphics.fill(coreX - 3, centerY - 4, coreX + 4, centerY + 5, BORDER_DARK);
        graphics.fill(coreX - 1, centerY - 2, coreX + 2, centerY + 3, CYAN);
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

    private static int serviceAccent(KurobeForgeView.Service service) {
        return switch (service) {
            case PROMOTE -> GOLD;
            case CONDUIT, SOCKET, RECHARGE -> CYAN;
            case REMOVE -> RED;
        };
    }

    private static int gradeColor(KurobeForgeView.Grade grade) {
        return switch (grade) {
            case NORMAL -> 0xFFD7D7D7;
            case SPECIAL -> 0xFF69D88A;
            case RARE -> 0xFF5AA7FF;
            case UNIQUE -> 0xFFB775FF;
            case LEGEND -> 0xFFFFB43C;
            case MYTHIC -> 0xFFFF6B9E;
            case GENESIS -> 0xFF8C7CFF;
        };
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> "-";
        };
    }

    private static boolean contains(
        int x,
        int y,
        int width,
        int height,
        double mouseX,
        double mouseY
    ) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Hotspot(String id, int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return KurobeForgeScreen.contains(x, y, width, height, mouseX, mouseY);
        }
    }

    private record PendingConfirmation(
        String weaponId,
        KurobeForgeView.Service service,
        long revision,
        String coreId,
        int coreSlot
    ) {}

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
                (minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F
            );
        }
    }
}
