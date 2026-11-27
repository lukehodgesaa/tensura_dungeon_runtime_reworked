package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationLoadoutSnapshotPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationPartySnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class DungeonPreparationSubordinateProfileScreen extends Screen
    implements DungeonHudSuppressingScreen {
    private static final int BACKGROUND = 0xE9030A14;
    private static final int PANEL = 0xF20A1524;
    private static final int PANEL_INNER = 0xFF07101D;
    private static final int BORDER = 0xFF287897;
    private static final int CYAN = 0xFF42D8FF;
    private static final int CYAN_DIM = 0xFF287897;
    private static final int VIOLET = 0xFFC865FF;
    private static final int TEXT = 0xFFE7F1FF;
    private static final int MUTED = 0xFF91A0B5;
    private static final int DISABLED = 0xFF596476;
    private static final int AMBER = 0xFFFFB23D;
    private static final int ROW_HEIGHT = 36;
    private static final int ACTIVE_SLOTS = 3;
    private static final int PASSIVE_SLOTS = 2;

    private final DungeonPreparationScreen parent;
    private final DungeonPreparationPartySnapshotPayload.Member member;
    private final DungeonPreparationLoadoutSnapshotPayload openingLoadout;
    private Tab tab = Tab.SKILLS;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int scroll;

    DungeonPreparationSubordinateProfileScreen(
        DungeonPreparationScreen parent,
        DungeonPreparationPartySnapshotPayload.Member member,
        DungeonPreparationLoadoutSnapshotPayload loadout
    ) {
        super(Component.literal("Subordinate Profile"));
        this.parent = parent;
        this.member = member;
        this.openingLoadout = loadout;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(430, Math.max(300, width - 24));
        panelHeight = Math.min(430, Math.max(280, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), ignored -> onClose())
            .bounds(panelX + panelWidth - 78, panelY + panelHeight - 30, 64, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DungeonPreparationLoadoutSnapshotPayload loadout = currentLoadout();
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, BORDER);

        graphics.drawString(font, "SUBORDINATE PROFILE", panelX + 14, panelY + 12, CYAN, true);
        String magiculeLabel = loadout.magiculeAvailable()
            ? "MAGICULE " + formatMagicule(loadout.currentMagicule())
                + " / " + formatMagicule(loadout.maxMagicule())
            : "MAGICULE --";
        graphics.drawString(
            font,
            magiculeLabel,
            panelX + panelWidth - 14 - font.width(magiculeLabel),
            panelY + 12,
            loadout.magiculeAvailable() ? VIOLET : AMBER,
            true
        );
        graphics.drawString(
            font,
            truncate(member.name().toUpperCase(Locale.ROOT), panelWidth - 30),
            panelX + 14,
            panelY + 28,
            TEXT,
            false
        );
        String race = member.raceName().isBlank() ? "SUBORDINATE" : member.raceName();
        graphics.drawString(
            font,
            truncate(race + " | " + (member.host() ? "HOST OWNED" : "PARTY MEMBER"), panelWidth - 30),
            panelX + 14,
            panelY + 41,
            MUTED,
            false
        );

        int tabsY = panelY + 60;
        drawTab(graphics, "SKILLS", panelX + 14, tabsY, tab == Tab.SKILLS);
        drawTab(
            graphics,
            "PASSIVE SKILLS",
            panelX + 92,
            tabsY,
            tab == Tab.PASSIVE_SKILLS
        );
        graphics.hLine(panelX + 12, panelX + panelWidth - 12, tabsY + 18, CYAN_DIM);

        int contentTop = tabsY + 28;
        int contentBottom = panelY + panelHeight - 40;
        if (!loadout.skillsAvailable()) {
            graphics.drawCenteredString(
                font,
                "SKILL API UNAVAILABLE",
                panelX + panelWidth / 2,
                contentTop + 18,
                AMBER
            );
            drawEmptyRows(graphics, contentTop + 42, tab == Tab.SKILLS ? ACTIVE_SLOTS : PASSIVE_SLOTS,
                tab == Tab.SKILLS ? "EMPTY SKILL SLOT" : "EMPTY PASSIVE SLOT", mouseX, mouseY);
        } else if (tab == Tab.SKILLS) {
            drawSkillsTab(graphics, loadout, contentTop, contentBottom, mouseX, mouseY);
        } else {
            drawPassiveSkillsTab(graphics, loadout, contentTop, contentBottom, mouseX, mouseY);
        }

        graphics.drawString(
            font,
            "RIGHT-CLICK A SKILL SCROLL ON THE SUBORDINATE TO TEACH IT",
            panelX + 14,
            panelY + panelHeight - 43,
            MUTED,
            false
        );
        graphics.drawString(
            font,
            "CLICK KNOWN SKILLS TO ASSIGN | RIGHT-CLICK SLOTS TO CLEAR",
            panelX + 14,
            panelY + panelHeight - 29,
            CYAN_DIM,
            false
        );
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawSkillsTab(
        GuiGraphics graphics,
        DungeonPreparationLoadoutSnapshotPayload loadout,
        int top,
        int bottom,
        int mouseX,
        int mouseY
    ) {
        List<DungeonPreparationLoadoutSnapshotPayload.AbilitySlot> slots = activeSlots(loadout);
        long populated = slots.stream().filter(slot -> !slot.empty()).count();
        graphics.drawString(
            font,
            "CURRENT SKILLS  " + populated + "/" + ACTIVE_SLOTS,
            panelX + 14,
            top,
            CYAN,
            false
        );
        graphics.drawString(font, "ACTIVE CAST SLOTS", panelX + 14, top + 14, MUTED, false);
        int y = top + 28;
        for (int index = 0; index < ACTIVE_SLOTS; index++) {
            DungeonPreparationLoadoutSnapshotPayload.AbilitySlot slot = slots.get(index);
            drawSlotRow(
                graphics,
                slot.id().isBlank() ? "" : slot.name(),
                slot.icon(),
                slot.details(),
                slot.empty(),
                false,
                "EMPTY SKILL SLOT",
                "SKILL SLOT " + (index + 1),
                panelX + 14,
                y,
                panelWidth - 28,
                mouseX,
                mouseY
            );
            y += ROW_HEIGHT;
        }

        int learnedTop = y + 8;
        graphics.drawString(font, "KNOWN ACTIVE SKILLS", panelX + 14, learnedTop, CYAN_DIM, false);
        List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> active = loadout.learnedSkills()
            .stream()
            .filter(skill -> !skill.passive())
            .toList();
        drawLearnedRows(graphics, active, learnedTop + 16, bottom, mouseX, mouseY);
    }

    private void drawPassiveSkillsTab(
        GuiGraphics graphics,
        DungeonPreparationLoadoutSnapshotPayload loadout,
        int top,
        int bottom,
        int mouseX,
        int mouseY
    ) {
        List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> passive = loadout.learnedSkills()
            .stream()
            .filter(DungeonPreparationLoadoutSnapshotPayload.SkillIcon::passive)
            .toList();
        List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> selected =
            loadout.selectedPassiveSkills();
        int populated = selected.size();
        graphics.drawString(
            font,
            "PASSIVE SKILLS  " + populated + "/" + PASSIVE_SLOTS,
            panelX + 14,
            top,
            VIOLET,
            false
        );
        graphics.drawString(font, "PASSIVE LOADOUT SLOTS", panelX + 14, top + 14, MUTED, false);
        int y = top + 28;
        for (int index = 0; index < PASSIVE_SLOTS; index++) {
            DungeonPreparationLoadoutSnapshotPayload.SkillIcon skill =
                index < selected.size() ? selected.get(index) : null;
            drawSlotRow(
                graphics,
                skill == null ? "" : skill.name(),
                skill == null ? "" : skill.icon(),
                skill == null ? DungeonPreparationLoadoutSnapshotPayload.SkillDetails.unavailable()
                    : skill.details(),
                skill == null,
                true,
                "EMPTY PASSIVE SLOT",
                "PASSIVE SLOT " + (index + 1),
                panelX + 14,
                y,
                panelWidth - 28,
                mouseX,
                mouseY
            );
            y += ROW_HEIGHT;
        }

        int learnedTop = y + 8;
        graphics.drawString(font, "KNOWN PASSIVE SKILLS", panelX + 14, learnedTop, CYAN_DIM, false);
        drawLearnedRows(graphics, passive, learnedTop + 16, bottom, mouseX, mouseY);
    }

    private void drawEmptyRows(
        GuiGraphics graphics,
        int top,
        int count,
        String emptyLabel,
        int mouseX,
        int mouseY
    ) {
        for (int index = 0; index < count; index++) {
            drawSlotRow(
                graphics,
                "",
                "",
                DungeonPreparationLoadoutSnapshotPayload.SkillDetails.unavailable(),
                true,
                false,
                emptyLabel,
                "SLOT " + (index + 1),
                panelX + 14,
                top + index * ROW_HEIGHT,
                panelWidth - 28,
                mouseX,
                mouseY
            );
        }
    }

    private void drawLearnedRows(
        GuiGraphics graphics,
        List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> skills,
        int top,
        int bottom,
        int mouseX,
        int mouseY
    ) {
        int knownRows = Math.max(1, skills.size());
        int contentHeight = knownRows * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - Math.max(1, bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int rowTop = top - scroll;
        graphics.enableScissor(panelX + 12, top, panelX + panelWidth - 12, bottom);
        for (int index = 0; index < skills.size(); index++) {
            DungeonPreparationLoadoutSnapshotPayload.SkillIcon skill = skills.get(index);
            drawSlotRow(
                graphics,
                skill.name(),
                skill.icon(),
                skill.details(),
                false,
                skill.passive(),
                "",
                "LEARNED SKILL",
                panelX + 14,
                rowTop + index * ROW_HEIGHT,
                panelWidth - 28,
                mouseX,
                mouseY
            );
        }
        if (skills.isEmpty()) {
            graphics.drawString(font, "NO SKILLS LEARNED", panelX + 14, top + 5 - scroll, MUTED, false);
        }
        graphics.disableScissor();
    }

    private void drawSlotRow(
        GuiGraphics graphics,
        String name,
        String icon,
        DungeonPreparationLoadoutSnapshotPayload.SkillDetails details,
        boolean empty,
        boolean passive,
        String emptyLabel,
        String slotLabel,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY
    ) {
        details = details == null
            ? DungeonPreparationLoadoutSnapshotPayload.SkillDetails.unavailable()
            : details;
        boolean hovered = inside(mouseX, mouseY, x, y, width, ROW_HEIGHT - 2);
        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 2, hovered ? 0xFF10273B : PANEL_INNER);
        outline(graphics, x, y, width, ROW_HEIGHT - 2, empty ? DISABLED : CYAN_DIM);
        int iconX = x + 6;
        int iconY = y + 5;
        graphics.fill(iconX, iconY, iconX + 26, iconY + 26, 0xFF05080F);
        outline(graphics, iconX, iconY, 26, 26, empty ? DISABLED : CYAN_DIM);
        if (empty) {
            graphics.drawCenteredString(font, "-", iconX + 13, iconY + 8, DISABLED);
        } else {
            drawNativeSkillIcon(graphics, icon, iconX, iconY, 26);
        }
        graphics.drawString(
            font,
            truncate(empty ? emptyLabel : (name.isBlank() ? "UNKNOWN ABILITY" : name), width - 92),
            x + 42,
            y + 8,
            empty ? MUTED : TEXT,
            false
        );
        if (!empty) {
            String detailLabel = skillDetailLabel(details, passive);
            graphics.drawString(
                font,
                truncate(detailLabel, width - 92),
                x + 42,
                y + 20,
                !passive && details.warning() ? AMBER : details.available() ? CYAN_DIM : DISABLED,
                false
            );
        }
        int slotRight = x + width - 7;
        if (!empty && !passive && details.warning()) {
            int warningX = x + width - 18;
            graphics.fill(warningX, y + 4, warningX + 12, y + 16, 0xFF4A300A);
            outline(graphics, warningX, y + 4, 12, 12, AMBER);
            graphics.drawCenteredString(font, "!", warningX + 6, y + 6, AMBER);
            slotRight = warningX - 4;
        }
        graphics.drawString(
            font,
            slotLabel,
            slotRight - font.width(slotLabel),
            y + 8,
            empty ? DISABLED : CYAN_DIM,
            false
        );
        if (hovered && !empty) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(name));
            if (passive) {
                tooltip.add(Component.literal("Passive native ability"));
            } else {
                if (details.magiculeCostAvailable()) {
                    tooltip.add(Component.literal(
                        "Magicule cost: " + formatMagicule(details.magiculeCost())
                    ));
                }
                if (details.cooldownAvailable()) {
                    tooltip.add(Component.literal(
                        "Cooldown: " + formatCooldown(details.cooldownSeconds())
                    ));
                }
                if (details.warning()) {
                    tooltip.add(Component.literal("Not enough magicule to cast"));
                } else if (!details.resourceAvailable() && details.magiculeCostAvailable()) {
                    tooltip.add(Component.literal("Magicule status unavailable"));
                }
            }
            if (!details.available() && !passive) {
                tooltip.add(Component.literal("Native skill details unavailable"));
            }
            graphics.renderComponentTooltip(
                font,
                tooltip,
                mouseX,
                mouseY
            );
        }
    }

    private void drawTab(GuiGraphics graphics, String label, int x, int y, boolean selected) {
        graphics.drawString(font, label, x, y, selected ? CYAN : MUTED, selected);
        if (selected) {
            graphics.hLine(x, x + font.width(label), y + 12, selected ? CYAN : MUTED);
        }
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
            graphics.blit(texture, x + 3, y + 3, size - 6, size - 6, 0.0F, 0.0F, 32, 32, 32, 32);
        } else {
            graphics.drawCenteredString(font, "?", x + size / 2, y + 7, AMBER);
        }
    }

    private DungeonPreparationLoadoutSnapshotPayload currentLoadout() {
        DungeonPreparationLoadoutSnapshotPayload latest = DungeonPreparationLoadoutClient.latest();
        return latest != null && member.id().equals(latest.targetId()) ? latest : openingLoadout;
    }

    private static List<DungeonPreparationLoadoutSnapshotPayload.AbilitySlot> activeSlots(
        DungeonPreparationLoadoutSnapshotPayload loadout
    ) {
        List<DungeonPreparationLoadoutSnapshotPayload.AbilitySlot> result =
            new ArrayList<>(loadout.currentAbilities());
        while (result.size() < ACTIVE_SLOTS) {
            result.add(new DungeonPreparationLoadoutSnapshotPayload.AbilitySlot("", "", ""));
        }
        return List.copyOf(result.subList(0, ACTIVE_SLOTS));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tabsY = panelY + 60;
        if (button == 0 && inside(mouseX, mouseY, panelX + 10, tabsY - 4, 70, 20)) {
            tab = Tab.SKILLS;
            scroll = 0;
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, panelX + 88, tabsY - 4, 120, 20)) {
            tab = Tab.PASSIVE_SKILLS;
            scroll = 0;
            return true;
        }
        if ((button == 0 || button == 1) && handleSkillClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSkillClick(double mouseX, double mouseY, int button) {
        DungeonPreparationLoadoutSnapshotPayload loadout = currentLoadout();
        if (!loadout.skillsAvailable() || !loadout.editable()) {
            return false;
        }
        int contentTop = panelY + 60 + 28;
        int contentBottom = panelY + panelHeight - 40;
        int slotTop = contentTop + 28;
        if (tab == Tab.SKILLS) {
            List<DungeonPreparationLoadoutSnapshotPayload.AbilitySlot> slots = activeSlots(loadout);
            if (button == 1) {
                for (int index = 0; index < ACTIVE_SLOTS; index++) {
                    DungeonPreparationLoadoutSnapshotPayload.AbilitySlot slot = slots.get(index);
                    if (!slot.empty()
                        && inside(mouseX, mouseY, panelX + 14, slotTop + index * ROW_HEIGHT,
                            panelWidth - 28, ROW_HEIGHT - 2)) {
                        DungeonPreparationLoadoutClient.clearActiveSkill(
                            member.id(), slot.id(), index
                        );
                        return true;
                    }
                }
            } else {
                List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> known = loadout.learnedSkills()
                    .stream()
                    .filter(skill -> !skill.passive())
                    .toList();
                int knownTop = contentTop + 28 + ACTIVE_SLOTS * ROW_HEIGHT + 24 - scroll;
                for (int index = 0; index < known.size(); index++) {
                    int rowTop = knownTop + index * ROW_HEIGHT;
                    if (!inside(mouseX, mouseY, panelX + 14, rowTop, panelWidth - 28, ROW_HEIGHT - 2)
                        || rowTop < contentTop || rowTop + ROW_HEIGHT > contentBottom) {
                        continue;
                    }
                    int targetSlot = -1;
                    for (int slot = 0; slot < ACTIVE_SLOTS; slot++) {
                        if (slots.get(slot).empty()) {
                            targetSlot = slot;
                            break;
                        }
                    }
                    if (targetSlot >= 0) {
                        DungeonPreparationLoadoutClient.assignActiveSkill(
                            member.id(), known.get(index).id(), targetSlot
                        );
                    }
                    return true;
                }
            }
        } else {
            List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> selected =
                loadout.selectedPassiveSkills();
            if (button == 1) {
                for (int index = 0; index < selected.size(); index++) {
                    if (inside(mouseX, mouseY, panelX + 14, slotTop + index * ROW_HEIGHT,
                        panelWidth - 28, ROW_HEIGHT - 2)) {
                        DungeonPreparationLoadoutClient.clearPassiveSkill(
                            member.id(), selected.get(index).id(), index
                        );
                        return true;
                    }
                }
            } else {
                List<DungeonPreparationLoadoutSnapshotPayload.SkillIcon> known = loadout.learnedSkills()
                    .stream()
                    .filter(DungeonPreparationLoadoutSnapshotPayload.SkillIcon::passive)
                    .toList();
                int knownTop = contentTop + 28 + PASSIVE_SLOTS * ROW_HEIGHT + 24 - scroll;
                for (int index = 0; index < known.size(); index++) {
                    int rowTop = knownTop + index * ROW_HEIGHT;
                    if (!inside(mouseX, mouseY, panelX + 14, rowTop, panelWidth - 28, ROW_HEIGHT - 2)
                        || rowTop < contentTop || rowTop + ROW_HEIGHT > contentBottom) {
                        continue;
                    }
                    if (selected.size() < PASSIVE_SLOTS) {
                        DungeonPreparationLoadoutClient.assignPassiveSkill(
                            member.id(), known.get(index).id(), selected.size()
                        );
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        scroll = Math.max(0, scroll + (int)Math.round(-scrollY * ROW_HEIGHT));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    private static String truncate(String value, int maximumWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= maximumWidth ? value : value.substring(0, maximumWidth);
    }

    private static String skillDetailLabel(
        DungeonPreparationLoadoutSnapshotPayload.SkillDetails details,
        boolean passive
    ) {
        if (passive) {
            return details.available() ? "PASSIVE" : "PASSIVE | NATIVE DATA UNAVAILABLE";
        }
        if (!details.available()) {
            return "NATIVE COST / COOLDOWN UNAVAILABLE";
        }
        String cost = details.magiculeCostAvailable()
            ? "M " + formatMagicule(details.magiculeCost())
            : "M --";
        String cooldown = details.cooldownAvailable()
            ? "CD " + formatCooldown(details.cooldownSeconds())
            : "CD --";
        return details.warning() ? cost + " | " + cooldown + " | LOW MAGICULE"
            : cost + " | " + cooldown;
    }

    private static String formatMagicule(double value) {
        double safe = Math.max(0.0D, value);
        if (Math.abs(safe - Math.rint(safe)) < 0.05D) {
            return String.format(Locale.ROOT, "%,.0f", safe);
        }
        return String.format(Locale.ROOT, "%,.1f", safe);
    }

    private static String formatCooldown(int seconds) {
        return String.format(Locale.ROOT, "%.1fs", (double) Math.max(0, seconds));
    }

    private enum Tab {
        SKILLS,
        PASSIVE_SKILLS
    }
}
