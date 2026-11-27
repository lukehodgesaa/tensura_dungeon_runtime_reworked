package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.network.DungeonUiRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonUiSnapshotPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationLoadoutSnapshotPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationPartyActionPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationPartySnapshotPayload;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public final class DungeonPreparationScreen extends Screen implements DungeonHudSuppressingScreen {
    private static final int BACKGROUND_TOP = 0xFF05080F;
    private static final int BACKGROUND_BOTTOM = 0xFF070B14;
    private static final int GHOST_GRID = 0x260F1A2B;
    private static final int PANEL = 0xD90A1524;
    private static final int PANEL_SELECTED = 0xEB0C1D31;
    private static final int DIVIDER = 0xFF183B5D;
    private static final int CYAN = 0xFF42D8FF;
    private static final int CYAN_DIM = 0xFF287897;
    private static final int VIOLET = 0xFFC865FF;
    private static final int GREEN = 0xFF54E890;
    private static final int AMBER = 0xFFFFB23D;
    private static final int TEXT = 0xFFE7F1FF;
    private static final int MUTED = 0xFF91A0B5;
    private static final int DISABLED = 0xFF596476;
    private static final int RED = 0xFFFF5F67;
    private static final int ROW_HEIGHT = 28;
    private static final int MODIFIER_ROW_HEIGHT = 34;
    private static final int MAX_SUBORDINATE_ACTIVE_SLOTS = 3;
    private static final int SUBORDINATE_ABILITY_ROW_HEIGHT = 27;
    private static final ResourceLocation FLOOR_ART = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID,
        "textures/gui/preparation/sand_dungeon.png"
    );
    private DungeonUiSnapshotPayload snapshot;
    private final long openingSequence;
    private long observedSequence;
    private DungeonPreparationLoadoutSnapshotPayload loadout;
    private final long openingLoadoutSequence;
    private long observedLoadoutSequence;
    private DungeonPreparationPartySnapshotPayload party;
    private final long openingPartySequence;
    private long observedPartySequence;
    private DungeonPreparationViewModel.ModifierTab modifierTab =
        DungeonPreparationViewModel.ModifierTab.PLAYER;
    private int modifierScroll;
    private int loadoutScroll;
    private boolean jumpVisible;
    private EditBox floorJump;
    private Button floorGo;
    private String localMessage = "";
    private UUID selectedMemberId;
    private int preparationRefreshTicks;

    public DungeonPreparationScreen() {
        super(Component.literal("Dungeon Briefing"));
        openingSequence = DungeonUiSnapshotClient.updateSequence();
        observedSequence = openingSequence;
        snapshot = DungeonUiSnapshotClient.latest();
        openingLoadoutSequence = DungeonPreparationLoadoutClient.updateSequence();
        observedLoadoutSequence = openingLoadoutSequence;
        loadout = DungeonPreparationLoadoutClient.latest();
        openingPartySequence = DungeonPreparationPartyClient.updateSequence();
        observedPartySequence = openingPartySequence;
        party = DungeonPreparationPartyClient.latest();
        Player player = Minecraft.getInstance().player;
        selectedMemberId = player == null ? null : player.getUUID();
    }

    @Override
    protected void init() {
        Layout layout = Layout.create(width, height);
        floorJump = new EditBox(
            font,
            layout.rightX() + 18,
            layout.floorJumpY(),
            Math.max(42, layout.rightWidth() - 76),
            18,
            Component.literal("Floor")
        );
        floorJump.setMaxLength(6);
        floorJump.setFilter(value -> value.matches("\\d{0,6}"));
        floorJump.setHint(Component.literal("Enter floor"));
        floorJump.setVisible(jumpVisible);
        addRenderableWidget(floorJump);

        floorGo = Button.builder(Component.literal("GO"), ignored -> submitFloorJump())
            .bounds(layout.rightX() + layout.rightWidth() - 52, layout.floorJumpY(), 40, 18)
            .build();
        floorGo.visible = jumpVisible;
        addRenderableWidget(floorGo);

        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.REFRESH);
        if (selectedMemberId != null) {
            DungeonPreparationLoadoutClient.refresh(selectedMemberId);
        } else {
            DungeonPreparationLoadoutClient.refresh();
        }
        DungeonPreparationPartyClient.refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (party == null) {
            preparationRefreshTicks = 0;
            return;
        }
        preparationRefreshTicks++;
        if (preparationRefreshTicks >= 20) {
            preparationRefreshTicks = 0;
            if (selectedMemberId != null) {
                DungeonPreparationLoadoutClient.refresh(selectedMemberId);
            }
            if (party.dispatchState()
                == DungeonPreparationPartySnapshotPayload.DispatchState.PREPARING) {
                DungeonPreparationPartyClient.refresh();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshSnapshot();
        refreshLoadout();
        refreshParty();
        graphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        drawBackgroundGrid(graphics);

        Layout layout = Layout.create(width, height);
        drawColumnDividers(graphics, layout);
        if (snapshot == null
            || loadout == null
            || party == null
            || !DungeonPreparationViewModel.hasFreshSnapshot(openingSequence, observedSequence)
            || !DungeonPreparationViewModel.hasFreshSnapshot(
                openingLoadoutSequence,
                observedLoadoutSequence
            )
            || !DungeonPreparationViewModel.hasFreshSnapshot(
                openingPartySequence,
                observedPartySequence
            )) {
            graphics.drawCenteredString(font, "LOADING DUNGEON BRIEFING...", width / 2, height / 2, CYAN);
            renderWidgets(graphics, mouseX, mouseY, partialTick);
            return;
        }

        drawPartyLeftColumn(graphics, layout);
        drawCenterColumn(graphics, layout, mouseX, mouseY);
        drawRightColumn(graphics, layout, mouseX, mouseY);
        drawFooter(graphics, layout, mouseX, mouseY);
        drawMessage(graphics, layout);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        drawHoveredItemTooltip(graphics, layout, mouseX, mouseY);
    }

    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void refreshSnapshot() {
        long sequence = DungeonUiSnapshotClient.updateSequence();
        if (sequence == observedSequence) {
            return;
        }
        observedSequence = sequence;
        snapshot = DungeonUiSnapshotClient.latest();
        modifierScroll = 0;
        if (snapshot != null && floorJump != null && !floorJump.isFocused()) {
            floorJump.setValue(Integer.toString(snapshot.progress().selectedFloor()));
        }
    }

    private void refreshLoadout() {
        long sequence = DungeonPreparationLoadoutClient.updateSequence();
        if (sequence == observedLoadoutSequence) {
            return;
        }
        observedLoadoutSequence = sequence;
        DungeonPreparationLoadoutSnapshotPayload latest =
            DungeonPreparationLoadoutClient.latest();
        if (latest != null
            && selectedMemberId != null
            && !selectedMemberId.equals(latest.targetId())) {
            return;
        }
        loadout = latest;
        loadoutScroll = 0;
        if (loadout != null && !loadout.message().isBlank()) {
            localMessage = loadout.message();
        }
    }

    private void refreshParty() {
        long sequence = DungeonPreparationPartyClient.updateSequence();
        if (sequence == observedPartySequence) {
            return;
        }
        observedPartySequence = sequence;
        party = DungeonPreparationPartyClient.latest();
        if (party != null && (
            selectedMemberId == null
                || party.members().stream().noneMatch(member ->
                    member.id().equals(selectedMemberId)
                )
        )) {
            Player player = Minecraft.getInstance().player;
            selectedMemberId = party.preferredMemberId(
                player == null ? null : player.getUUID()
            );
            loadout = null;
            DungeonPreparationLoadoutClient.refresh(selectedMemberId);
        }
        if (party != null && floorJump != null && !floorJump.isFocused()) {
            floorJump.setValue(Integer.toString(party.selectedFloor()));
        }
        if (party != null && !party.message().isBlank()) {
            localMessage = party.message();
        }
    }

    private void drawBackgroundGrid(GuiGraphics graphics) {
        int grid = Math.max(24, width / 32);
        for (int x = 0; x < width; x += grid) {
            graphics.vLine(x, 0, height, GHOST_GRID);
        }
        for (int y = 0; y < height; y += grid) {
            graphics.hLine(0, width, y, GHOST_GRID);
        }
    }

    private void drawColumnDividers(GuiGraphics graphics, Layout layout) {
        graphics.vLine(layout.leftX() + layout.leftWidth() + 6, 12, layout.footerY() - 8, DIVIDER);
        graphics.vLine(layout.centerX() + layout.centerWidth() + 6, 12, layout.footerY() - 8, DIVIDER);
        graphics.hLine(12, width - 12, layout.footerY() - 4, DIVIDER);
    }

    private void drawPartyLeftColumn(GuiGraphics graphics, Layout layout) {
        int x = layout.leftX();
        int y = 16;
        graphics.drawString(font, "DUNGEON BRIEFING", x, y, TEXT, true);
        y += 22;
        graphics.drawString(
            font,
            "SQUAD  " + party.members().size() + " / 4",
            x,
            y,
            party.partyEligible() ? CYAN : AMBER,
            false
        );
        y += 17;

        for (DungeonPreparationPartySnapshotPayload.Member member : party.members()) {
            boolean selected = member.id().equals(selectedMemberId);
            int cardHeight = selected ? 116 : 48;
            graphics.fill(
                x,
                y,
                x + layout.leftWidth(),
                y + cardHeight,
                selected ? PANEL_SELECTED : PANEL
            );
            outline(
                graphics,
                x,
                y,
                layout.leftWidth(),
                cardHeight,
                member.floorEligible() ? CYAN_DIM : RED
            );
            graphics.fill(x + 8, y + 8, x + 38, y + 38, 0xFF07101D);
            outline(graphics, x + 8, y + 8, 30, 30, member.floorEligible() ? CYAN_DIM : RED);
            drawMemberPortrait(graphics, member, x + 9, y + 9);
            graphics.drawString(
                font,
                truncate(member.name() + (member.host() ? " [HOST]" : ""), layout.leftWidth() - 50),
                x + 46,
                y + 8,
                TEXT,
                true
            );
            graphics.drawString(
                font,
                memberStatus(member),
                x + 46,
                y + 23,
                member.floorEligible() ? (member.ready() ? GREEN : CYAN_DIM) : RED,
                false
            );
            if (party.viewerIsHost() && !member.host()) {
                graphics.drawString(font, "X", x + layout.leftWidth() - 13, y + 8, RED, true);
            }
            if (selected
                && loadout != null
                && member.id().equals(loadout.targetId())
                && layout.leftWidth() >= 150) {
                drawViewerLoadoutSummary(graphics, layout, x, y);
            } else if (selected) {
                graphics.hLine(x + 8, x + layout.leftWidth() - 8, y + 72, DIVIDER);
                graphics.drawCenteredString(
                    font,
                    "LOADOUT ICONS",
                    x + layout.leftWidth() / 2,
                    y + 90,
                    CYAN_DIM
                );
            }
            y += cardHeight + 7;
        }

        int inviteY = partyInviteY();
        graphics.fill(x, inviteY, x + layout.leftWidth(), inviteY + 42, 0x70071220);
        dashedOutline(graphics, x, inviteY, layout.leftWidth(), 42, DISABLED);
        boolean canInvite = party.viewerIsHost() && party.members().size() < 4;
        graphics.drawCenteredString(
            font,
            canInvite ? "+ INVITE MEMBER" : "PARTY ROSTER",
            x + layout.leftWidth() / 2,
            inviteY + 17,
            canInvite ? CYAN_DIM : DISABLED
        );
    }

    private int partyInviteY() {
        int y = 55;
        for (DungeonPreparationPartySnapshotPayload.Member member : party.members()) {
            boolean selected = member.id().equals(selectedMemberId);
            y += (selected ? 116 : 48) + 7;
        }
        return y + 4;
    }

    private String memberStatus(DungeonPreparationPartySnapshotPayload.Member member) {
        if (!member.online()) return "OFFLINE";
        if (!member.floorEligible()) {
            return "LOCKED - CLEAR FLOOR " + Math.max(1, party.selectedFloor() - 1);
        }
        if (member.kind() == DungeonPreparationPartySnapshotPayload.Kind.SUBORDINATE) {
            String hostName = party.members().stream()
                .filter(DungeonPreparationPartySnapshotPayload.Member::host)
                .map(DungeonPreparationPartySnapshotPayload.Member::name)
                .findFirst()
                .orElse("HOST");
            String race = member.raceName().isBlank() ? "Subordinate" : member.raceName();
            return race + " - " + hostName + "'s Subordinate";
        }
        return member.ready() ? "READY" : "NOT READY";
    }

    private void drawViewerLoadoutSummary(
        GuiGraphics graphics,
        Layout layout,
        int x,
        int y
    ) {
        DungeonPreparationLoadoutData.Category[] gearCategories = {
            DungeonPreparationLoadoutData.Category.PRIMARY_SWORD,
            DungeonPreparationLoadoutData.Category.OFF_HAND,
            DungeonPreparationLoadoutData.Category.HELMET,
            DungeonPreparationLoadoutData.Category.CHESTPLATE,
            DungeonPreparationLoadoutData.Category.LEGGINGS,
            DungeonPreparationLoadoutData.Category.BOOTS
        };
        int gearGap = Math.max(18, Math.min(23, (layout.leftWidth() - 20) / 6));
        for (int index = 0; index < gearCategories.length; index++) {
            drawItemSlot(
                graphics,
                loadout.displayedEquipment(gearCategories[index]),
                x + 10 + index * gearGap,
                y + 50,
                18,
                VIOLET
            );
        }
        graphics.hLine(x + 8, x + layout.leftWidth() - 8, y + 72, DIVIDER);
        DungeonPreparationLoadoutData.Category[] compactCategories =
            loadout.targetKind()
                == DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE
                    ? new DungeonPreparationLoadoutData.Category[] {
                        DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET,
                        DungeonPreparationLoadoutData.Category.RETURN_WHISTLE
                    }
                    : new DungeonPreparationLoadoutData.Category[] {
                        DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET,
                        DungeonPreparationLoadoutData.Category.RETURN_WHISTLE,
                        DungeonPreparationLoadoutData.Category.HEALTH_RECOVERY,
                        DungeonPreparationLoadoutData.Category.MAGICULE_RECOVERY,
                        DungeonPreparationLoadoutData.Category.UTILITY,
                        DungeonPreparationLoadoutData.Category.FIELD_RATIONS
                    };
        int compactGap = Math.max(20, Math.min(27, (layout.leftWidth() - 18) / 6));
        for (int index = 0; index < compactCategories.length; index++) {
            DungeonPreparationLoadoutData.Category category = compactCategories[index];
            int slotX = x + 9 + index * compactGap;
            drawCompactCategoryIcon(graphics, category, slotX, y + 80);
            graphics.drawCenteredString(
                font,
                loadout.slotCount(category) + "/" + category.cap(),
                slotX + 8,
                y + 99,
                CYAN_DIM
            );
        }
    }

    private void drawMemberPortrait(
        GuiGraphics graphics,
        DungeonPreparationPartySnapshotPayload.Member member,
        int x,
        int y
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (member.kind() == DungeonPreparationPartySnapshotPayload.Kind.PLAYER) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            PlayerInfo playerInfo = minecraft.getConnection() == null
                ? null
                : minecraft.getConnection().getPlayerInfo(member.id());
            head.set(
                DataComponents.PROFILE,
                new ResolvableProfile(
                    playerInfo == null
                        ? new com.mojang.authlib.GameProfile(member.id(), member.name())
                        : playerInfo.getProfile()
                )
            );
            graphics.pose().pushPose();
            graphics.pose().translate(x + 3, y + 3, 0.0F);
            graphics.pose().scale(1.375F, 1.375F, 1.0F);
            graphics.renderItem(head, 0, 0);
            graphics.pose().popPose();
            return;
        }

        LivingEntity living = findLivingEntity(member.id());
        if (living != null && drawGoblinFacePortrait(graphics, living, x, y)) {
            return;
        }

        graphics.drawCenteredString(font, "S", x + 14, y + 10, CYAN);
    }

    private boolean drawGoblinFacePortrait(
        GuiGraphics graphics,
        LivingEntity living,
        int x,
        int y
    ) {
        return TensuraCompat.goblinPortraitTextures(living).map(textures -> {
            drawFaceLayer(graphics, textures.skin(), x, y);
            drawFaceLayer(graphics, textures.face(), x, y);

            int tint = textures.hairColor();
            graphics.setColor(
                ((tint >> 16) & 0xFF) / 255.0F,
                ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F,
                ((tint >>> 24) & 0xFF) / 255.0F
            );
            drawFaceLayer(graphics, textures.hair(), x, y);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return true;
        }).orElse(false);
    }

    private static void drawFaceLayer(
        GuiGraphics graphics,
        ResourceLocation texture,
        int x,
        int y
    ) {
        graphics.blit(texture, x, y, 28, 28, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(texture, x, y, 28, 28, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    private LivingEntity findLivingEntity(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity.getUUID().equals(id)) {
                return living;
            }
        }
        return null;
    }

    private void drawCenterColumn(
        GuiGraphics graphics,
        Layout layout,
        int mouseX,
        int mouseY
    ) {
        int x = layout.centerX();
        int y = 16;
        DungeonPreparationPartySnapshotPayload.Member selectedMember = selectedPartyMember();
        LivingEntity selectedEntity = selectedMember == null
            ? Minecraft.getInstance().player
            : findLivingEntity(selectedMember.id());
        if (selectedEntity == null && selectedMember != null && selectedMember.host()) {
            selectedEntity = Minecraft.getInstance().player;
        }
        String playerName = selectedMember == null
            ? "PLAYER"
            : selectedMember.name().toUpperCase(Locale.ROOT);
        graphics.drawString(
            font,
            playerName + (loadout.editable() ? " — LOADOUT" : " — INSPECTION"),
            x,
            y,
            CYAN,
            false
        );

        int previewTop = y + 12;
        int previewBottom = Math.min(previewTop + 125, layout.centerEquipmentY() - 5);
        if (selectedEntity != null && previewBottom - previewTop >= 62) {
            int previewLeft = x + 8;
            int previewRight = x + layout.centerWidth() - 8;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                previewLeft,
                previewTop,
                previewRight,
                previewBottom,
                Math.max(30, Math.min(62, (previewBottom - previewTop) / 2)),
                0.0625F,
                mouseX,
                mouseY,
                selectedEntity
            );
        }

        int equipmentY = layout.centerEquipmentY();
        int offHandX = offHandX(layout);
        graphics.drawString(font, "MAIN HAND", x, equipmentY, CYAN, false);
        graphics.drawString(font, "OFF HAND", offHandX, equipmentY, CYAN, false);
        int handSlotY = equipmentY + 13;
        ItemStack mainHand = loadout.displayedEquipment(
            DungeonPreparationLoadoutData.Category.PRIMARY_SWORD
        );
        ItemStack offHand = loadout.displayedEquipment(
            DungeonPreparationLoadoutData.Category.OFF_HAND
        );
        drawItemSlot(graphics, mainHand, x, handSlotY, 24, VIOLET);
        drawItemSlot(graphics, offHand, offHandX, handSlotY, 24, VIOLET);
        graphics.hLine(x, x + layout.centerWidth() - 8, handSlotY + 28, DIVIDER);

        equipmentY = handSlotY + 34;
        graphics.drawString(font, "ARMOR", x, equipmentY, CYAN, false);
        equipmentY += 12;
        DungeonPreparationLoadoutData.Category[] armorCategories = {
            DungeonPreparationLoadoutData.Category.HELMET,
            DungeonPreparationLoadoutData.Category.CHESTPLATE,
            DungeonPreparationLoadoutData.Category.LEGGINGS,
            DungeonPreparationLoadoutData.Category.BOOTS
        };
        int gap = armorSlotGap();
        for (int index = 0; index < armorCategories.length; index++) {
            ItemStack armor = loadout.displayedEquipment(armorCategories[index]);
            drawItemSlot(graphics, armor, x + index * gap, equipmentY, 22, VIOLET);
            if (!armor.isEmpty() && armor.isDamageableItem()) {
                int barWidth = 20;
                int remaining = armor.getMaxDamage() - armor.getDamageValue();
                int fill = Math.max(0, Math.min(barWidth, barWidth * remaining / armor.getMaxDamage()));
                graphics.fill(x + index * gap, equipmentY + 24, x + index * gap + barWidth, equipmentY + 26, DISABLED);
                graphics.fill(x + index * gap, equipmentY + 24, x + index * gap + fill, equipmentY + 26, VIOLET);
            }
        }

        int listTop = equipmentY + 32;
        int listBottom = layout.footerY() - 10;
        drawLoadoutList(graphics, layout, listTop, listBottom);
    }

    private void drawLoadoutList(
        GuiGraphics graphics,
        Layout layout,
        int top,
        int bottom
    ) {
        if (loadout.targetKind()
            == DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE) {
            drawSubordinateLoadoutList(graphics, layout, top, bottom);
            return;
        }
        int x = layout.centerX();
        int widthAvailable = layout.centerWidth() - 14;
        List<LoadoutRow> rows = loadoutRows();
        int contentHeight = rows.stream().mapToInt(row -> row.kind == RowKind.HEADER ? 17 : ROW_HEIGHT).sum();
        int viewportHeight = Math.max(1, bottom - top);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        loadoutScroll = Math.max(0, Math.min(loadoutScroll, maxScroll));

        graphics.enableScissor(x, top, x + layout.centerWidth(), bottom);
        int rowY = top - loadoutScroll;
        for (LoadoutRow row : rows) {
            if (row.kind == RowKind.HEADER) {
                graphics.drawString(font, row.label.toUpperCase(Locale.ROOT), x, rowY + 4, CYAN, false);
                rowY += 17;
                continue;
            }
            graphics.hLine(x, x + widthAvailable, rowY + ROW_HEIGHT - 1, DIVIDER);
            int slotsWidth = row.category.cap() * 18;
            int slotsX = x + widthAvailable - slotsWidth;
            String label = truncate(row.label, Math.max(24, slotsX - x - 16));
            graphics.drawString(font, label, x + 1, rowY + 8, TEXT, false);
            graphics.drawString(font, "-", slotsX - 10, rowY + 8, MUTED, false);
            drawCapacitySlots(graphics, row.category, slotsX, rowY + 5);
            rowY += ROW_HEIGHT;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x + layout.centerWidth() - 5, top, bottom, contentHeight, loadoutScroll);
    }

    private void drawSubordinateLoadoutList(
        GuiGraphics graphics,
        Layout layout,
        int top,
        int bottom
    ) {
        int x = layout.centerX();
        int widthAvailable = layout.centerWidth() - 14;
        int protectionHeight = 17 + ROW_HEIGHT * 2;
        int skillSectionHeight = 8
            + 17
            + MAX_SUBORDINATE_ACTIVE_SLOTS * SUBORDINATE_ABILITY_ROW_HEIGHT
            + 18;
        int contentHeight = protectionHeight + skillSectionHeight;
        int viewportHeight = Math.max(1, bottom - top);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        loadoutScroll = Math.max(0, Math.min(loadoutScroll, maxScroll));

        graphics.enableScissor(x, top, x + layout.centerWidth(), bottom);
        int rowY = top - loadoutScroll;
        graphics.drawString(font, "PROTECTION", x, rowY + 4, CYAN, false);
        rowY += 17;
        DungeonPreparationLoadoutData.Category[] protectionCategories = {
            DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET,
            DungeonPreparationLoadoutData.Category.RETURN_WHISTLE
        };
        String[] protectionLabels = {"Resurrection Bracelet", "Return Whistle"};
        for (int index = 0; index < protectionCategories.length; index++) {
            DungeonPreparationLoadoutData.Category category = protectionCategories[index];
            graphics.hLine(x, x + widthAvailable, rowY + ROW_HEIGHT - 1, DIVIDER);
            int slotsWidth = category.cap() * 18;
            int slotsX = x + widthAvailable - slotsWidth;
            String label = truncate(protectionLabels[index], Math.max(24, slotsX - x - 16));
            graphics.drawString(font, label, x + 1, rowY + 8, TEXT, false);
            graphics.drawString(font, "-", slotsX - 10, rowY + 8, MUTED, false);
            drawCapacitySlots(graphics, category, slotsX, rowY + 5);
            rowY += ROW_HEIGHT;
        }

        int sectionY = rowY + 8;
        graphics.hLine(x, x + widthAvailable, sectionY, DIVIDER);
        graphics.drawString(font, "ABILITIES", x, sectionY + 5, CYAN, false);
        List<DungeonPreparationLoadoutSnapshotPayload.AbilitySlot> abilities =
            loadout.currentAbilities();
        String count = loadout.skillsAvailable()
            ? abilities.stream().filter(ability -> !ability.empty()).count() + "/"
                + MAX_SUBORDINATE_ACTIVE_SLOTS
            : "API";
        graphics.drawString(
            font,
            count,
            x + widthAvailable - font.width(count),
            sectionY + 5,
            loadout.skillsAvailable() ? MUTED : AMBER,
            false
        );
        int slotsY = sectionY + 17;
        for (int index = 0; index < MAX_SUBORDINATE_ACTIVE_SLOTS; index++) {
            DungeonPreparationLoadoutSnapshotPayload.AbilitySlot ability =
                index < abilities.size() ? abilities.get(index) : null;
            drawAbilitySummaryRow(graphics, ability, x, slotsY, widthAvailable);
            slotsY += SUBORDINATE_ABILITY_ROW_HEIGHT;
        }
        if (!loadout.skillsAvailable()) {
            graphics.drawString(font, "SKILL API UNAVAILABLE", x, slotsY + 2, AMBER, false);
        } else {
            graphics.drawString(
                font,
                "CLICK ABILITIES FOR SKILL PROFILE",
                x,
                slotsY + 2,
                MUTED,
                false
            );
        }
        graphics.disableScissor();
        drawScrollbar(
            graphics,
            x + layout.centerWidth() - 5,
            top,
            bottom,
            contentHeight,
            loadoutScroll
        );
    }

    private void drawAbilitySummaryRow(
        GuiGraphics graphics,
        DungeonPreparationLoadoutSnapshotPayload.AbilitySlot ability,
        int x,
        int y,
        int width
    ) {
        graphics.hLine(x, x + width, y + SUBORDINATE_ABILITY_ROW_HEIGHT - 1, DIVIDER);
        int iconSize = 20;
        int iconX = x + 1;
        int iconY = y + 3;
        graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF07101D);
        outline(
            graphics,
            iconX,
            iconY,
            iconSize,
            iconSize,
            ability != null && !ability.empty() ? CYAN_DIM : DISABLED
        );
        if (ability != null && !ability.empty()) {
            drawNativeSkillIcon(graphics, ability.icon(), iconX, iconY, iconSize);
        }
        String label = ability == null || ability.empty()
            ? "EMPTY SKILL SLOT"
            : ability.name().isBlank() ? "UNKNOWN ABILITY" : ability.name();
        graphics.drawString(
            font,
            truncate(label, Math.max(20, width - iconSize - 10)),
            x + iconSize + 8,
            y + 8,
            ability != null && !ability.empty() ? TEXT : MUTED,
            false
        );
    }

    private void drawNativeSkillIcon(
        GuiGraphics graphics,
        String icon,
        int x,
        int y,
        int size
    ) {
        ResourceLocation texture = null;
        try {
            texture = icon == null || icon.isBlank() ? null : ResourceLocation.parse(icon);
        } catch (RuntimeException ignored) {
        }
        if (texture != null && minecraft.getResourceManager().getResource(texture).isPresent()) {
            graphics.blit(texture, x + 2, y + 2, size - 4, size - 4, 0.0F, 0.0F, 32, 32, 32, 32);
        } else {
            graphics.drawCenteredString(font, "?", x + size / 2, y + 5, AMBER);
        }
    }

    private List<LoadoutRow> loadoutRows() {
        List<LoadoutRow> protection = List.of(
            new LoadoutRow("PROTECTION", RowKind.HEADER, null),
            new LoadoutRow(
                "Resurrection Bracelet",
                RowKind.ITEM,
                DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET
            ),
            new LoadoutRow(
                "Return Whistle",
                RowKind.ITEM,
                DungeonPreparationLoadoutData.Category.RETURN_WHISTLE
            )
        );
        if (loadout != null
            && loadout.targetKind()
                == DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE) {
            return protection;
        }
        return List.of(
            protection.get(0),
            protection.get(1),
            protection.get(2),
            new LoadoutRow("SUPPLIES", RowKind.HEADER, null),
            new LoadoutRow(
                "Health Recovery",
                RowKind.ITEM,
                DungeonPreparationLoadoutData.Category.HEALTH_RECOVERY
            ),
            new LoadoutRow(
                "Magicule Recovery",
                RowKind.ITEM,
                DungeonPreparationLoadoutData.Category.MAGICULE_RECOVERY
            ),
            new LoadoutRow(
                "Utility",
                RowKind.ITEM,
                DungeonPreparationLoadoutData.Category.UTILITY
            ),
            new LoadoutRow(
                "Field Rations",
                RowKind.ITEM,
                DungeonPreparationLoadoutData.Category.FIELD_RATIONS
            )
        );
    }

    private void drawRightColumn(
        GuiGraphics graphics,
        Layout layout,
        int mouseX,
        int mouseY
    ) {
        int x = layout.rightX();
        int floor = party.selectedFloor();
        int titleY = 18;
        String floorText = "FLOOR " + floor;
        graphics.drawCenteredString(font, "‹", x + 20, titleY + 7, VIOLET);
        graphics.drawCenteredString(font, floorText, x + layout.rightWidth() / 2, titleY, VIOLET);
        graphics.drawCenteredString(font, "›", x + layout.rightWidth() - 20, titleY + 7, VIOLET);

        int artY = 42;
        int artHeight = Math.max(62, Math.min(100, height / 5));
        graphics.blit(FLOOR_ART, x + 4, artY, layout.rightWidth() - 8, artHeight,
            0.0F, 0.0F, 512, 224, 512, 224);
        graphics.fill(x + 4, artY, x + layout.rightWidth() - 4, artY + artHeight, 0x5A00030A);
        outline(graphics, x + 4, artY, layout.rightWidth() - 8, artHeight, DIVIDER);

        int statusY = artY + artHeight + 6;
        boolean completed = snapshot.progress().completedFloors().contains(floor);
        DungeonPreparationPartySnapshotPayload.Member viewer = viewerPartyMember();
        boolean available = viewer == null || viewer.floorEligible();
        graphics.drawString(
            font,
            (available ? "AVAILABLE" : "LOCKED") + "  |  " + (completed ? "CLEARED" : "UNCLEARED"),
            x + 5,
            statusY,
            available ? GREEN : RED,
            false
        );

        int tabsY = statusY + 19;
        String playerTab = layout.rightWidth() >= 220 ? "PLAYER MODIFIERS" : "PLAYER";
        String dungeonTab = layout.rightWidth() >= 220 ? "DUNGEON MODIFIERS" : "DUNGEON";
        graphics.drawString(font, playerTab, x + 5, tabsY,
            modifierTab == DungeonPreparationViewModel.ModifierTab.PLAYER ? CYAN : DISABLED, false);
        int slashX = x + 9 + font.width(playerTab);
        graphics.drawString(font, "/", slashX, tabsY, MUTED, false);
        graphics.drawString(font, dungeonTab, slashX + 10, tabsY,
            modifierTab == DungeonPreparationViewModel.ModifierTab.DUNGEON ? CYAN : DISABLED, false);
        int underlineStart = modifierTab == DungeonPreparationViewModel.ModifierTab.PLAYER
            ? x + 5
            : slashX + 10;
        int underlineWidth = font.width(
            modifierTab == DungeonPreparationViewModel.ModifierTab.PLAYER ? playerTab : dungeonTab
        );
        graphics.fill(underlineStart, tabsY + 11, underlineStart + underlineWidth, tabsY + 13, CYAN);

        int listTop = tabsY + 18;
        int listBottom = layout.footerY() - 10;
        drawModifierList(graphics, layout, listTop, listBottom);
    }

    private void drawModifierList(
        GuiGraphics graphics,
        Layout layout,
        int top,
        int bottom
    ) {
        int x = layout.rightX() + 4;
        int widthAvailable = layout.rightWidth() - 13;
        List<DungeonPreparationViewModel.ModifierRow> rows =
            DungeonPreparationViewModel.modifiers(
                snapshot,
                modifierTab,
                party.hostileModifierIds(),
                party.boonModifierId()
            );
        int descriptionWidth = Math.max(8, widthAvailable - 32);
        List<ModifierDisplayRow> displayRows = modifierDisplayRows(rows, descriptionWidth);
        int contentHeight = Math.max(
            MODIFIER_ROW_HEIGHT,
            displayRows.stream().mapToInt(ModifierDisplayRow::height).sum()
        );
        int viewportHeight = Math.max(1, bottom - top);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        modifierScroll = Math.max(0, Math.min(modifierScroll, maxScroll));

        graphics.enableScissor(layout.rightX(), top, layout.rightX() + layout.rightWidth(), bottom);
        if (rows.isEmpty()) {
            graphics.drawString(font, "NONE FOR THIS FLOOR", x + 2, top + 12, MUTED, false);
        } else {
            int rowY = top - modifierScroll;
            for (ModifierDisplayRow displayRow : displayRows) {
                DungeonPreparationViewModel.ModifierRow row = displayRow.row();
                int color = modifierColor(row.alignment());
                drawModifierIcon(
                    graphics,
                    row.iconKey(),
                    x + 2,
                    rowY + 6,
                    color,
                    row.displayName()
                );
                graphics.drawString(font, row.displayName().toUpperCase(Locale.ROOT),
                    x + 28, rowY + 5, TEXT, false);
                int descriptionY = rowY + 17;
                for (FormattedCharSequence line : displayRow.descriptionLines()) {
                    graphics.drawString(font, line, x + 28, descriptionY, MUTED, false);
                    descriptionY += font.lineHeight + 1;
                }
                graphics.hLine(x, x + widthAvailable, rowY + displayRow.height() - 1, DIVIDER);
                rowY += displayRow.height();
            }
        }
        graphics.disableScissor();
        drawScrollbar(
            graphics,
            layout.rightX() + layout.rightWidth() - 5,
            top,
            bottom,
            contentHeight,
            modifierScroll
        );
    }

    private List<ModifierDisplayRow> modifierDisplayRows(
        List<DungeonPreparationViewModel.ModifierRow> rows,
        int descriptionWidth
    ) {
        return rows.stream().map(row -> {
            String description = row.description();
            List<FormattedCharSequence> descriptionLines = description == null || description.isBlank()
                ? List.of()
                : font.split(Component.literal(description), descriptionWidth);
            int height = Math.max(
                MODIFIER_ROW_HEIGHT,
                17 + descriptionLines.size() * (font.lineHeight + 1) + 3
            );
            return new ModifierDisplayRow(row, descriptionLines, height);
        }).toList();
    }

    private void drawFooter(
        GuiGraphics graphics,
        Layout layout,
        int mouseX,
        int mouseY
    ) {
        int y = layout.footerY() + 6;
        boolean compact = width < 430;
        int backWidth = compact ? 70 : 96;
        int startWidth = compact ? 100 : Math.min(190, Math.max(130, layout.rightWidth() / 2 + 20));
        int readyWidth = compact ? 72 : 92;
        drawAction(graphics, layout.leftX(), y, backWidth, 27,
            compact ? "BACK" : "BACK / LEAVE", "Close briefing", true);
        int startX = width - 12 - startWidth;
        int readyX = startX - 8 - readyWidth;
        DungeonPreparationPartySnapshotPayload.Member viewer = viewerPartyMember();
        boolean canReady = viewer != null
            && viewer.kind() == DungeonPreparationPartySnapshotPayload.Kind.PLAYER
            && viewer.online()
            && viewer.floorEligible()
            && (party.dispatchState()
                == DungeonPreparationPartySnapshotPayload.DispatchState.IDLE
                || party.dispatchState()
                    == DungeonPreparationPartySnapshotPayload.DispatchState.READY);
        drawAction(
            graphics,
            readyX,
            y,
            readyWidth,
            27,
            viewer != null && viewer.ready() && canReady ? "UNREADY" : "READY",
            viewer != null && viewer.ready()
                ? "Committed"
                : canReady ? "Confirm loadout" : "Floor locked",
            canReady
        );
        boolean soloRoster = party.soloDispatchRoster();
        boolean supportedPreparationRoster = party.subordinatePreparationRoster();
        boolean startReady = party.viewerIsHost()
            && supportedPreparationRoster
            && party.partyEligible()
            && party.allPlayersReady()
            && (party.dispatchState()
                == DungeonPreparationPartySnapshotPayload.DispatchState.IDLE
                || party.dispatchState()
                    == DungeonPreparationPartySnapshotPayload.DispatchState.READY
                || party.dispatchState()
                    == DungeonPreparationPartySnapshotPayload.DispatchState.PENDING_TELEPORT);
        String startLabel = switch (party.dispatchState()) {
            case READY -> compact ? "ENTER" : "ENTER DUNGEON";
            case PENDING_TELEPORT -> compact ? "RETRY" : "RETRY ENTRY";
            case READY_TRANSPORT_PENDING -> compact ? "TRANSPORT NEXT" : "SUBORDINATE TRANSPORT NEXT";
            case PREPARING -> compact ? "PREPARING" : "PREPARING DUNGEON";
            case ACTIVE -> compact ? "ACTIVE" : "DUNGEON ACTIVE";
            case UNSUPPORTED_PARTY -> compact ? "PARTY NEXT" : "PARTY DISPATCH NEXT";
            case FAILED -> compact ? "BLOCKED" : "START BLOCKED";
            case CLEANUP_PENDING -> compact ? "CLEANUP" : "CLEANUP PENDING";
            case IDLE -> compact ? "START" : "START DUNGEON";
        };
        String startDetail;
        if (party.dispatchState()
            == DungeonPreparationPartySnapshotPayload.DispatchState.CLEANUP_PENDING) {
            startDetail = "Previous dungeon cleanup is still running";
        } else if (!supportedPreparationRoster) {
            startDetail = "Invited-player dispatch is not available yet";
        } else if (!party.partyEligible()) {
            startDetail = "Selected floor is locked";
        } else if (!party.allPlayersReady()) {
            startDetail = "Confirm loadout to enter";
        } else {
            startDetail = switch (party.dispatchState()) {
                case READY -> "";
                case PREPARING -> preparationTimingDetail(party);
                case PENDING_TELEPORT -> "Entry transport interrupted - retry";
                case READY_TRANSPORT_PENDING -> "Ready - subordinate transport pending";
                case ACTIVE -> "Dungeon run active";
                case UNSUPPORTED_PARTY -> "Party dispatch is not available yet";
                case FAILED -> party.dispatchPhase().isBlank()
                    ? "Dungeon preparation failed"
                    : truncate(party.dispatchPhase(), Math.max(40, startWidth - 8));
                case CLEANUP_PENDING -> "Previous dungeon cleanup is still running";
                case IDLE -> "Ready to start preparation";
            };
        }
        drawAction(graphics, startX, y, startWidth, 27,
            startLabel,
            startDetail,
            startReady);
    }

    private DungeonPreparationPartySnapshotPayload.Member viewerPartyMember() {
        Player player = Minecraft.getInstance().player;
        if (player == null || party == null) return null;
        return party.members().stream()
            .filter(member -> member.id().equals(player.getUUID()))
            .findFirst()
            .orElse(null);
    }

    private static boolean startReady(DungeonPreparationPartySnapshotPayload party) {
        return party != null
            && party.viewerIsHost()
            && party.subordinatePreparationRoster()
            && party.partyEligible()
            && party.allPlayersReady()
            && (party.dispatchState()
                == DungeonPreparationPartySnapshotPayload.DispatchState.IDLE
                || party.dispatchState()
                    == DungeonPreparationPartySnapshotPayload.DispatchState.READY
                || party.dispatchState()
                    == DungeonPreparationPartySnapshotPayload.DispatchState.PENDING_TELEPORT);
    }

    private static String preparationTimingDetail(
        DungeonPreparationPartySnapshotPayload party
    ) {
        String elapsed = formatDuration(party.preparationElapsedSeconds());
        if (party.estimatedRemainingSeconds() < 0) {
            return "ESTIMATING  |  ELAPSED " + elapsed;
        }
        return "EST. " + formatDuration(party.estimatedRemainingSeconds())
            + "  |  ELAPSED " + elapsed;
    }

    private static String formatDuration(int seconds) {
        int bounded = Math.max(0, seconds);
        return String.format(
            Locale.ROOT,
            "%02d:%02d",
            bounded / 60,
            bounded % 60
        );
    }

    private DungeonPreparationPartySnapshotPayload.Member selectedPartyMember() {
        if (party == null || selectedMemberId == null) {
            return null;
        }
        return party.members().stream()
            .filter(member -> member.id().equals(selectedMemberId))
            .findFirst()
            .orElse(null);
    }

    private void drawMessage(GuiGraphics graphics, Layout layout) {
        String message = !localMessage.isBlank()
            ? localMessage
            : snapshot == null ? "" : snapshot.message();
        if (message.isBlank()) {
            return;
        }
        graphics.drawCenteredString(font, truncate(message, width - 40), width / 2,
            layout.footerY() - 13, AMBER);
    }

    private void drawAction(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        String label,
        String detail,
        boolean enabled
    ) {
        graphics.fill(x, y, x + width, y + height, enabled ? PANEL_SELECTED : 0xAA0A111D);
        outline(graphics, x, y, width, height, enabled ? CYAN_DIM : DISABLED);
        if (detail.isBlank()) {
            graphics.drawCenteredString(
                font,
                label,
                x + width / 2,
                y + (height - font.lineHeight) / 2,
                enabled ? TEXT : DISABLED
            );
            return;
        }
        graphics.drawCenteredString(font, label, x + width / 2, y + 5, enabled ? TEXT : DISABLED);
        graphics.drawCenteredString(font, detail, x + width / 2, y + 16, enabled ? MUTED : DISABLED);
    }

    private void drawHoveredItemTooltip(
        GuiGraphics graphics,
        Layout layout,
        int mouseX,
        int mouseY
    ) {
        if (loadout == null) {
            return;
        }
        int x = layout.centerX();
        int handSlotY = layout.centerEquipmentY() + 13;
        if (loadout.canEdit(DungeonPreparationLoadoutData.Category.PRIMARY_SWORD)
            && inside(mouseX, mouseY, x, handSlotY, 24, 24)) {
            ItemStack mainHand = loadout.displayedEquipment(
                DungeonPreparationLoadoutData.Category.PRIMARY_SWORD
            );
            if (!mainHand.isEmpty()) {
                graphics.renderTooltip(font, mainHand, mouseX, mouseY);
            }
            return;
        }
        int offHandX = offHandX(layout);
        if (loadout.canEdit(DungeonPreparationLoadoutData.Category.OFF_HAND)
            && inside(mouseX, mouseY, offHandX, handSlotY, 24, 24)) {
            ItemStack offHand = loadout.displayedEquipment(
                DungeonPreparationLoadoutData.Category.OFF_HAND
            );
            if (!offHand.isEmpty()) {
                graphics.renderTooltip(font, offHand, mouseX, mouseY);
            }
            return;
        }
        int armorY = handSlotY + 46;
        int gap = armorSlotGap();
        DungeonPreparationLoadoutData.Category[] armorCategories = {
            DungeonPreparationLoadoutData.Category.HELMET,
            DungeonPreparationLoadoutData.Category.CHESTPLATE,
            DungeonPreparationLoadoutData.Category.LEGGINGS,
            DungeonPreparationLoadoutData.Category.BOOTS
        };
        for (int index = 0; index < armorCategories.length; index++) {
            if (inside(mouseX, mouseY, x + index * gap, armorY, 22, 26)) {
                ItemStack armor = loadout.displayedEquipment(armorCategories[index]);
                if (!armor.isEmpty()) {
                    graphics.renderTooltip(font, armor, mouseX, mouseY);
                }
                return;
            }
        }
        if (drawLoadoutCapacityTooltip(graphics, layout, mouseX, mouseY)
            || drawViewerSummaryTooltip(graphics, layout, mouseX, mouseY)) {
            return;
        }
    }

    private boolean drawLoadoutCapacityTooltip(
        GuiGraphics graphics,
        Layout layout,
        int mouseX,
        int mouseY
    ) {
        int x = layout.centerX();
        int handSlotY = layout.centerEquipmentY() + 13;
        int listTop = handSlotY + 46 + 32;
        int listBottom = layout.footerY() - 10;
        if (!inside(mouseX, mouseY, x, listTop, layout.centerWidth(), listBottom - listTop)) {
            return false;
        }
        int contentY = mouseY - listTop + loadoutScroll;
        int cursor = 0;
        int widthAvailable = layout.centerWidth() - 14;
        for (LoadoutRow row : loadoutRows()) {
            int rowHeight = row.kind == RowKind.HEADER ? 17 : ROW_HEIGHT;
            if (row.kind != RowKind.HEADER
                && contentY >= cursor
                && contentY < cursor + rowHeight) {
                int slotsX = x + widthAvailable - row.category.cap() * 18;
                for (int capacityIndex = 0; capacityIndex < row.category.cap(); capacityIndex++) {
                    int slotX = slotsX + capacityIndex * 18;
                    if (inside(mouseX, mouseY, slotX, listTop + cursor - loadoutScroll + 5, 16, 16)) {
                        DungeonPreparationLoadoutSnapshotPayload.Selection selection =
                            capacitySelection(row.category, capacityIndex);
                        if (selection != null && !selection.stack().isEmpty()) {
                            graphics.renderTooltip(font, selection.stack(), mouseX, mouseY);
                        } else {
                            graphics.renderComponentTooltip(
                                font,
                                loadoutHelp(row.category),
                                mouseX,
                                mouseY
                            );
                        }
                        return true;
                    }
                }
                graphics.renderComponentTooltip(
                    font,
                    loadoutHelp(row.category),
                    mouseX,
                    mouseY
                );
                return true;
            }
            cursor += rowHeight;
        }
        return false;
    }

    private boolean drawViewerSummaryTooltip(
        GuiGraphics graphics,
        Layout layout,
        int mouseX,
        int mouseY
    ) {
        if (layout.leftWidth() < 150) {
            return false;
        }
        if (selectedMemberId == null || loadout == null
            || !selectedMemberId.equals(loadout.targetId())) {
            return false;
        }
        int cardY = 55;
        for (DungeonPreparationPartySnapshotPayload.Member member : party.members()) {
            boolean selected = member.id().equals(selectedMemberId);
            if (!selected) {
                cardY += 55;
                continue;
            }
            DungeonPreparationLoadoutData.Category[] gearCategories = {
                DungeonPreparationLoadoutData.Category.PRIMARY_SWORD,
                DungeonPreparationLoadoutData.Category.OFF_HAND,
                DungeonPreparationLoadoutData.Category.HELMET,
                DungeonPreparationLoadoutData.Category.CHESTPLATE,
                DungeonPreparationLoadoutData.Category.LEGGINGS,
                DungeonPreparationLoadoutData.Category.BOOTS
            };
            int gearGap = Math.max(18, Math.min(23, (layout.leftWidth() - 20) / 6));
            for (int index = 0; index < gearCategories.length; index++) {
                int slotX = layout.leftX() + 10 + index * gearGap;
                if (inside(mouseX, mouseY, slotX, cardY + 50, 18, 18)) {
                    ItemStack stack = loadout.displayedEquipment(gearCategories[index]);
                    if (!stack.isEmpty()) {
                        graphics.renderTooltip(font, stack, mouseX, mouseY);
                    }
                    return true;
                }
            }
            DungeonPreparationLoadoutData.Category[] compactCategories =
                loadout.targetKind()
                    == DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE
                        ? new DungeonPreparationLoadoutData.Category[] {
                            DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET,
                            DungeonPreparationLoadoutData.Category.RETURN_WHISTLE
                        }
                        : new DungeonPreparationLoadoutData.Category[] {
                            DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET,
                            DungeonPreparationLoadoutData.Category.RETURN_WHISTLE,
                            DungeonPreparationLoadoutData.Category.HEALTH_RECOVERY,
                            DungeonPreparationLoadoutData.Category.MAGICULE_RECOVERY,
                            DungeonPreparationLoadoutData.Category.UTILITY,
                            DungeonPreparationLoadoutData.Category.FIELD_RATIONS
                        };
            int compactGap = Math.max(20, Math.min(27, (layout.leftWidth() - 18) / 6));
            for (int index = 0; index < compactCategories.length; index++) {
                int slotX = layout.leftX() + 9 + index * compactGap;
                if (inside(mouseX, mouseY, slotX, cardY + 80, 16, 16)) {
                    ItemStack stack = loadout.firstValid(compactCategories[index]);
                    if (!stack.isEmpty()) {
                        graphics.renderTooltip(font, stack, mouseX, mouseY);
                    }
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = Layout.create(width, height);
        if (button == 0) {
            int backWidth = width < 430 ? 70 : 96;
            if (inside(
                mouseX,
                mouseY,
                layout.leftX(),
                layout.footerY() + 6,
                backWidth,
                27
            )) {
                onClose();
                return true;
            }
        }
        if (button == 0 && party != null) {
            int memberY = 55;
            for (DungeonPreparationPartySnapshotPayload.Member member : party.members()) {
                boolean selected = member.id().equals(selectedMemberId);
                int cardHeight = selected ? 116 : 48;
                if (party.viewerIsHost()
                    && !member.host()
                    && inside(
                        mouseX,
                        mouseY,
                        layout.leftX() + layout.leftWidth() - 20,
                        memberY,
                        20,
                        24
                    )) {
                    DungeonPreparationPartyClient.act(
                        DungeonPreparationPartyActionPayload.Action.REMOVE_MEMBER,
                        member.id()
                    );
                    return true;
                }
                if (inside(
                    mouseX,
                    mouseY,
                    layout.leftX(),
                    memberY,
                    layout.leftWidth(),
                    cardHeight
                )) {
                    selectedMemberId = member.id();
                    loadout = null;
                    loadoutScroll = 0;
                    DungeonPreparationLoadoutClient.refresh(selectedMemberId);
                    return true;
                }
                memberY += cardHeight + 7;
            }
            int inviteY = partyInviteY();
            if (party.viewerIsHost()
                && party.members().size() < 4
                && inside(mouseX, mouseY, layout.leftX(), inviteY, layout.leftWidth(), 42)) {
                Minecraft.getInstance().setScreen(new DungeonPreparationPartyBrowserScreen(this));
                return true;
            }
            boolean compact = width < 430;
            int startWidth = compact ? 100 : Math.min(190, Math.max(130, layout.rightWidth() / 2 + 20));
            int readyWidth = compact ? 72 : 92;
            int startX = width - 12 - startWidth;
            int readyX = startX - 8 - readyWidth;
            DungeonPreparationPartySnapshotPayload.Member viewer = viewerPartyMember();
            if (viewer != null
                && viewer.online()
                && viewer.floorEligible()
                && inside(mouseX, mouseY, readyX, layout.footerY() + 6, readyWidth, 27)) {
                DungeonPreparationPartyClient.act(
                    DungeonPreparationPartyActionPayload.Action.TOGGLE_READY,
                    viewer.id()
                );
                return true;
            }
            if (party.viewerIsHost()
                && startReady(party)
                && inside(mouseX, mouseY, startX, layout.footerY() + 6, startWidth, 27)) {
                boolean dispatching = party.dispatchState()
                    == DungeonPreparationPartySnapshotPayload.DispatchState.READY;
                DungeonPreparationPartyClient.act(
                    DungeonPreparationPartyActionPayload.Action.START_DUNGEON,
                    party.hostId()
                );
                if (dispatching) {
                    Minecraft.getInstance().setScreen(null);
                }
                return true;
            }
        }
        if (button == 0 && loadout != null && handleLoadoutClick(mouseX, mouseY, layout)) {
            return true;
        }
        if (button == 0 && snapshot != null) {
            int titleY = 18;
            int selected = party.selectedFloor();
            if (party.viewerIsHost()
                && inside(mouseX, mouseY, layout.rightX() + 5, titleY, 34, 28)) {
                selectFloor(selected - 1);
                return true;
            }
            if (party.viewerIsHost()
                && inside(mouseX, mouseY, layout.rightX() + layout.rightWidth() - 39, titleY, 34, 28)) {
                selectFloor(selected + 1);
                return true;
            }
            int floorTitleWidth = font.width("FLOOR " + selected) + 24;
            if (party.viewerIsHost() && inside(
                mouseX,
                mouseY,
                layout.rightX() + (layout.rightWidth() - floorTitleWidth) / 2,
                titleY - 2,
                floorTitleWidth,
                28
            )) {
                setJumpVisible(!jumpVisible);
                return true;
            }

            int tabsY = 42 + Math.max(62, Math.min(100, height / 5)) + 25;
            String playerTab = layout.rightWidth() >= 220 ? "PLAYER MODIFIERS" : "PLAYER";
            String dungeonTab = layout.rightWidth() >= 220 ? "DUNGEON MODIFIERS" : "DUNGEON";
            int playerWidth = font.width(playerTab);
            int dungeonX = layout.rightX() + 19 + playerWidth;
            if (inside(mouseX, mouseY, layout.rightX() + 5, tabsY, playerWidth, 15)) {
                modifierTab = DungeonPreparationViewModel.ModifierTab.PLAYER;
                modifierScroll = 0;
                return true;
            }
            if (inside(mouseX, mouseY, dungeonX, tabsY, font.width(dungeonTab), 15)) {
                modifierTab = DungeonPreparationViewModel.ModifierTab.DUNGEON;
                modifierScroll = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleLoadoutClick(double mouseX, double mouseY, Layout layout) {
        int x = layout.centerX();
        int handSlotY = layout.centerEquipmentY() + 13;
        if (inside(mouseX, mouseY, x, handSlotY, 24, 24)) {
            openInventoryPicker(DungeonPreparationLoadoutData.Category.PRIMARY_SWORD);
            return true;
        }
        int offHandX = offHandX(layout);
        if (inside(mouseX, mouseY, offHandX, handSlotY, 24, 24)) {
            openInventoryPicker(DungeonPreparationLoadoutData.Category.OFF_HAND);
            return true;
        }
        int armorSlotY = handSlotY + 46;
        int gap = armorSlotGap();
        DungeonPreparationLoadoutData.Category[] armorCategories = {
            DungeonPreparationLoadoutData.Category.HELMET,
            DungeonPreparationLoadoutData.Category.CHESTPLATE,
            DungeonPreparationLoadoutData.Category.LEGGINGS,
            DungeonPreparationLoadoutData.Category.BOOTS
        };
        for (int index = 0; index < armorCategories.length; index++) {
            if (loadout.canEdit(armorCategories[index])
                && inside(mouseX, mouseY, x + index * gap, armorSlotY, 22, 26)) {
                openInventoryPicker(armorCategories[index]);
                return true;
            }
        }

        int listTop = armorSlotY + 32;
        int listBottom = layout.footerY() - 10;
        if (!inside(
            mouseX,
            mouseY,
            x,
            listTop,
            layout.centerWidth(),
            Math.max(0, listBottom - listTop)
        )) {
            return false;
        }
        int contentY = (int)mouseY - listTop + loadoutScroll;
        if (loadout.targetKind()
            == DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE) {
            int abilityStart = 17 + ROW_HEIGHT * 2 + 8;
            int abilityHeight = 17
                + MAX_SUBORDINATE_ACTIVE_SLOTS * SUBORDINATE_ABILITY_ROW_HEIGHT
                + 18;
            if (contentY >= abilityStart && contentY < abilityStart + abilityHeight) {
                openSubordinateProfile();
                return true;
            }
        }
        int cursor = 0;
        for (LoadoutRow row : loadoutRows()) {
            int rowHeight = row.kind == RowKind.HEADER ? 17 : ROW_HEIGHT;
            if (contentY >= cursor && contentY < cursor + rowHeight) {
                if (row.category != null && loadout.canEdit(row.category)) {
                    openInventoryPicker(row.category);
                    return true;
                }
                return false;
            }
            cursor += rowHeight;
        }
        return false;
    }

    private void openSubordinateProfile() {
        DungeonPreparationPartySnapshotPayload.Member member = selectedPartyMember();
        if (member == null || loadout == null
            || loadout.targetKind()
                != DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE) {
            return;
        }
        DungeonClientSoundEffects.playMenuSelect();
        Minecraft.getInstance().setScreen(
            new DungeonPreparationSubordinateProfileScreen(this, member, loadout)
        );
    }

    private void openInventoryPicker(DungeonPreparationLoadoutData.Category category) {
        if (selectedMemberId == null || loadout == null || !loadout.canEdit(category)) {
            return;
        }
        DungeonClientSoundEffects.playMenuSelect();
        Minecraft.getInstance().setScreen(
            new DungeonPreparationInventoryPickerScreen(this, selectedMemberId, category)
        );
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        Layout layout = Layout.create(width, height);
        int amount = (int) Math.round(-scrollY * 18.0D);
        if (mouseX >= layout.rightX()) {
            modifierScroll = Math.max(0, modifierScroll + amount);
            return true;
        }
        if (mouseX >= layout.centerX() && mouseX < layout.centerX() + layout.centerWidth()) {
            loadoutScroll = Math.max(0, loadoutScroll + amount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (jumpVisible && keyCode == 257) {
            submitFloorJump();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submitFloorJump() {
        if (snapshot == null || party == null || !party.viewerIsHost()) {
            return;
        }
        OptionalInt floor = DungeonPreparationViewModel.parseFloor(
            floorJump.getValue(),
            snapshot.progress().highestUnlockedFloor()
        );
        if (floor.isEmpty()) {
            localMessage = "Enter an unlocked floor from 1 to "
                + snapshot.progress().highestUnlockedFloor() + ".";
            return;
        }
        localMessage = "";
        setJumpVisible(false);
        selectFloor(floor.getAsInt());
    }

    private void selectFloor(int floor) {
        if (snapshot == null || party == null || !party.viewerIsHost()) {
            return;
        }
        if (floor < 1 || floor > snapshot.progress().highestUnlockedFloor()) {
            localMessage = "Unlocked floors: 1–" + snapshot.progress().highestUnlockedFloor() + ".";
            return;
        }
        localMessage = "";
        DungeonUiSnapshotClient.request(DungeonUiRequestPayload.Action.SELECT_FLOOR, floor);
    }

    private void setJumpVisible(boolean visible) {
        jumpVisible = visible;
        if (floorJump != null) {
            floorJump.setVisible(visible);
            if (visible && party != null) {
                floorJump.setValue(Integer.toString(party.selectedFloor()));
                floorJump.setFocused(true);
                floorJump.moveCursorToEnd(false);
            } else {
                floorJump.setFocused(false);
            }
        }
        if (floorGo != null) {
            floorGo.visible = visible;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawItemSlot(
        GuiGraphics graphics,
        ItemStack stack,
        int x,
        int y,
        int size,
        int color
    ) {
        graphics.fill(x, y, x + size, y + size, 0xFF07101D);
        outline(graphics, x, y, size, size, stack.isEmpty() ? DISABLED : color);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + (size - 16) / 2, y + (size - 16) / 2);
        }
    }

    private void drawCompactCategoryIcon(
        GuiGraphics graphics,
        DungeonPreparationLoadoutData.Category category,
        int x,
        int y
    ) {
        ItemStack selected = loadout.firstValid(category);
        graphics.fill(x, y, x + 16, y + 16, 0xFF07101D);
        outline(graphics, x, y, 16, 16, selected.isEmpty() ? DISABLED : CYAN_DIM);
        if (!selected.isEmpty()) {
            graphics.renderItem(selected, x, y);
        }
    }

    private void drawCapacitySlots(
        GuiGraphics graphics,
        DungeonPreparationLoadoutData.Category category,
        int x,
        int y
    ) {
        for (int capacityIndex = 0; capacityIndex < category.cap(); capacityIndex++) {
            DungeonPreparationLoadoutSnapshotPayload.Selection selection =
                capacitySelection(category, capacityIndex);
            int slotX = x + capacityIndex * 18;
            graphics.fill(slotX, y, slotX + 16, y + 16, 0xFF07101D);
            outline(
                graphics,
                slotX,
                y,
                16,
                16,
                selection == null ? DISABLED : selection.valid() ? CYAN_DIM : RED
            );
            if (selection != null && !selection.stack().isEmpty()) {
                ItemStack display = selection.stack().copyWithCount(selection.quantity());
                graphics.renderItem(display, slotX, y);
                graphics.renderItemDecorations(font, display, slotX, y);
                if (!selection.valid()) {
                    graphics.fill(slotX, y, slotX + 16, y + 16, 0x550F0204);
                }
            }
        }
    }

    private DungeonPreparationLoadoutSnapshotPayload.Selection capacitySelection(
        DungeonPreparationLoadoutData.Category category,
        int capacityIndex
    ) {
        List<DungeonPreparationLoadoutSnapshotPayload.Selection> selections =
            loadout.category(category);
        return capacityIndex >= 0 && capacityIndex < selections.size()
            ? selections.get(capacityIndex)
            : null;
    }

    private void drawModifierIcon(
        GuiGraphics graphics,
        String iconKey,
        int x,
        int y,
        int color,
        String name
    ) {
        String textureKey = "riftbound".equals(iconKey) ? "summoner" : iconKey;
        if (textureKey != null && textureKey.matches("[a-z0-9_-]{1,64}")) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                TensuraDungeonRuntime.MOD_ID,
                "textures/gui/modifiers/" + textureKey + ".png"
            );
            if (minecraft.getResourceManager().getResource(texture).isPresent()) {
                graphics.blit(texture, x, y, 18, 18, 0.0F, 0.0F, 48, 48, 48, 48);
                return;
            }
        }
        drawModifierFallbackGlyph(graphics, x, y, color, name);
    }

    private void drawModifierFallbackGlyph(
        GuiGraphics graphics,
        int x,
        int y,
        int color,
        String name
    ) {
        graphics.fill(x + 4, y, x + 14, y + 18, color);
        graphics.fill(x, y + 4, x + 18, y + 14, color);
        graphics.fill(x + 3, y + 3, x + 15, y + 15, PANEL);
        String glyph = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.drawCenteredString(font, glyph, x + 9, y + 5, color);
    }

    private void drawScrollbar(
        GuiGraphics graphics,
        int x,
        int top,
        int bottom,
        int contentHeight,
        int scroll
    ) {
        int viewport = Math.max(1, bottom - top);
        graphics.fill(x, top, x + 3, bottom, 0xAA172536);
        if (contentHeight <= viewport) {
            graphics.fill(x, top, x + 3, bottom, CYAN_DIM);
            return;
        }
        int thumbHeight = Math.max(18, viewport * viewport / contentHeight);
        int travel = viewport - thumbHeight;
        int maxScroll = contentHeight - viewport;
        int thumbY = top + (maxScroll <= 0 ? 0 : travel * scroll / maxScroll);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, CYAN);
    }

    private static int modifierColor(String alignment) {
        if (alignment == null) {
            return MUTED;
        }
        return switch (alignment.toUpperCase(Locale.ROOT)) {
            case "BOON" -> CYAN;
            case "REWARD" -> AMBER;
            case "BOSS" -> VIOLET;
            case "HOSTILE" -> RED;
            default -> MUTED;
        };
    }

    private String truncate(String value, int maximumWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return font.plainSubstrByWidth(value, Math.max(8, maximumWidth));
    }

    private int offHandX(Layout layout) {
        return Math.min(
            layout.centerX() + 72,
            layout.centerX() + layout.centerWidth() - 24
        );
    }

    private static int armorSlotGap() {
        return 34;
    }

    private static List<Component> loadoutHelp(
        DungeonPreparationLoadoutData.Category category
    ) {
        return switch (category) {
            case RESURRECTION_BRACELET -> List.of(
                Component.literal("Protects you from losing your carried items"),
                Component.literal("after a lethal dungeon defeat."),
                Component.literal("Without one, ordinary death risks losing all carried items."),
                Component.literal("Optional. 1 slot.")
            );
            case RETURN_WHISTLE -> List.of(
                Component.literal("Voluntary emergency escape."),
                Component.literal("Requires a 5-second uninterrupted casting time."),
                Component.literal("If the cast finishes, you return safely"),
                Component.literal("with your inventory. 1 slot.")
            );
            case HEALTH_RECOVERY -> List.of(
                Component.literal("Healing potions and dungeon healing"),
                Component.literal("vials or flasks. 3 slots.")
            );
            case MAGICULE_RECOVERY -> List.of(
                Component.literal("Dungeon Magicule recovery vials"),
                Component.literal("or flasks. 3 slots.")
            );
            case UTILITY -> List.of(
                Component.literal("Non-healing potions and utility items."),
                Component.literal("2 slots.")
            );
            case FIELD_RATIONS -> List.of(
                Component.literal("Food and dungeon Field Rations."),
                Component.literal("2 slots, with up to 5 items in each.")
            );
            default -> List.of(Component.literal("Select one eligible inventory item."));
        };
    }

    private static boolean inside(
        double mouseX,
        double mouseY,
        int x,
        int y,
        int width,
        int height
    ) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.hLine(x, x + width - 1, y, color);
        graphics.hLine(x, x + width - 1, y + height - 1, color);
        graphics.vLine(x, y, y + height - 1, color);
        graphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static void dashedOutline(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int color
    ) {
        for (int offset = 0; offset < width; offset += 8) {
            graphics.hLine(x + offset, Math.min(x + width - 1, x + offset + 4), y, color);
            graphics.hLine(x + offset, Math.min(x + width - 1, x + offset + 4), y + height - 1, color);
        }
        for (int offset = 0; offset < height; offset += 8) {
            graphics.vLine(x, y + offset, Math.min(y + height - 1, y + offset + 4), color);
            graphics.vLine(x + width - 1, y + offset, Math.min(y + height - 1, y + offset + 4), color);
        }
    }

    private enum RowKind {
        HEADER,
        ITEM
    }

    private record LoadoutRow(
        String label,
        RowKind kind,
        DungeonPreparationLoadoutData.Category category
    ) {}

    private record ModifierDisplayRow(
        DungeonPreparationViewModel.ModifierRow row,
        List<FormattedCharSequence> descriptionLines,
        int height
    ) {}

    private record Layout(
        int leftX,
        int leftWidth,
        int centerX,
        int centerWidth,
        int rightX,
        int rightWidth,
        int footerY,
        int centerEquipmentY,
        int floorJumpY
    ) {
        static Layout create(int width, int height) {
            int margin = width < 500 ? 6 : 12;
            int gap = width < 500 ? 6 : 14;
            int contentWidth = Math.max(1, width - margin * 2 - gap * 2);
            int leftWidth = contentWidth * 29 / 100;
            int centerWidth = contentWidth * 32 / 100;
            int rightWidth = contentWidth - leftWidth - centerWidth;
            int leftX = margin;
            int centerX = leftX + leftWidth + gap;
            int rightX = centerX + centerWidth + gap;
            int footerY = Math.max(220, height - 42);
            int centerEquipmentY = Math.max(118, Math.min(155, height * 30 / 100));
            int floorJumpY = 47;
            return new Layout(
                leftX, leftWidth, centerX, centerWidth, rightX,
                rightWidth,
                footerY, centerEquipmentY, floorJumpY
            );
        }
    }
}
