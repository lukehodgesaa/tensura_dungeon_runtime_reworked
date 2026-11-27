package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class DungeonPreparationInventoryPickerScreen extends Screen
    implements DungeonHudSuppressingScreen {
    private static final int BACKGROUND = 0xE9030A14;
    private static final int PANEL = 0xF20A1524;
    private static final int SLOT_ELIGIBLE = 0xFF173452;
    private static final int BORDER = 0xFF246B91;
    private static final int CYAN = 0xFF42D8FF;
    private static final int SELECTED = 0xFFD987FF;
    private static final int MUTED = 0xFF77879B;

    private final DungeonPreparationScreen parent;
    private final DungeonPreparationLoadoutData.Category category;
    private final UUID targetId;
    private int panelX;
    private int panelY;
    private long observedSequence;
    private boolean awaitingUpdate;

    public DungeonPreparationInventoryPickerScreen(
        DungeonPreparationScreen parent,
        UUID targetId,
        DungeonPreparationLoadoutData.Category category
    ) {
        super(Component.literal("Select " + label(category)));
        this.parent = parent;
        this.targetId = targetId;
        this.category = category;
        observedSequence = DungeonPreparationLoadoutClient.updateSequence();
    }

    @Override
    protected void init() {
        panelX = (width - 214) / 2;
        panelY = Math.max(12, (height - 170) / 2);
        addRenderableWidget(Button.builder(Component.literal("CLEAR"), ignored -> {
            DungeonClientSoundEffects.playMenuSelect();
            DungeonPreparationLoadoutClient.clear(targetId, category);
            minecraft.setScreen(parent);
        }).bounds(panelX + 12, panelY + 143, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), ignored -> {
            DungeonClientSoundEffects.playMenuClose();
            minecraft.setScreen(parent);
        }).bounds(panelX + 132, panelY + 143, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(panelX, panelY, panelX + 214, panelY + 170, PANEL);
        outline(graphics, panelX, panelY, 214, 170, BORDER);
        graphics.drawString(font, "SELECT " + label(category), panelX + 12, panelY + 10, CYAN, false);
        graphics.drawString(
            font,
            selectionCapacityText(),
            panelX + 12,
            panelY + 23,
            MUTED,
            false
        );

        List<Candidate> candidates = eligibleCandidates();
        if (candidates != null) {
            if (candidates.isEmpty()) {
                graphics.drawCenteredString(
                    font,
                    "NO ELIGIBLE ITEMS",
                    panelX + 107,
                    panelY + 75,
                    MUTED
                );
            }
            for (int visual = 0; visual < candidates.size(); visual++) {
                int row = visual / 9;
                int column = visual % 9;
                drawCandidate(
                    graphics,
                    candidates.get(visual),
                    panelX + 25 + column * 18,
                    panelY + 40 + row * 18,
                    mouseX,
                    mouseY
                );
            }
        }
        long currentSequence = DungeonPreparationLoadoutClient.updateSequence();
        if (currentSequence != observedSequence) {
            observedSequence = currentSequence;
            awaitingUpdate = false;
        }
        if (awaitingUpdate) {
            graphics.drawCenteredString(font, "UPDATING…", panelX + 107, panelY + 128, CYAN);
        }
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawCandidate(
        GuiGraphics graphics,
        Candidate candidate,
        int x,
        int y,
        int mouseX,
        int mouseY
    ) {
        ItemStack stack = candidate.stack();
        int available = availableFromSlot(candidate);
        ItemStack displayStack = category.cap() > 1
            ? stack.copyWithCount(Math.max(1, available))
            : stack;
        graphics.fill(x, y, x + 16, y + 16, SLOT_ELIGIBLE);
        int selected = selectedFromSlot(candidate.inventorySlot());
        outline(graphics, x, y, 16, 16, selected > 0 ? SELECTED : CYAN);
        graphics.renderItem(displayStack, x, y);
        graphics.renderItemDecorations(font, displayStack, x, y);
        if (selected > 0) {
            graphics.fill(x + 1, y + 1, x + 15, y + 3, SELECTED);
        }
        if (inside(mouseX, mouseY, x, y, 16, 16)) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Candidate> candidates = eligibleCandidates();
        if (button == 0 && candidates != null && !awaitingUpdate) {
            for (int visual = 0; visual < candidates.size(); visual++) {
                int row = visual / 9;
                int column = visual % 9;
                int x = panelX + 25 + column * 18;
                int y = panelY + 40 + row * 18;
                Candidate candidate = candidates.get(visual);
                if (inside(mouseX, mouseY, x, y, 16, 16) && canSelect(candidate)) {
                    DungeonClientSoundEffects.playLoadoutSelection(category);
                    DungeonPreparationLoadoutClient.select(
                        targetId,
                        category,
                        candidate.inventorySlot()
                    );
                    awaitingUpdate = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<Candidate> eligibleCandidates() {
        var snapshot = DungeonPreparationLoadoutClient.latest();
        if (snapshot != null
            && snapshot.targetKind()
                == com.mineclawd.tensuradungeon.network.DungeonPreparationLoadoutSnapshotPayload.TargetKind.SUBORDINATE) {
            return filterCandidates(snapshot.inventoryCandidates().stream()
                .map(candidate -> new Candidate(candidate.inventorySlot(), candidate.stack()))
                .toList());
        }
        Inventory inventory = Minecraft.getInstance().player == null
            ? null
            : Minecraft.getInstance().player.getInventory();
        if (inventory == null) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int inventorySlot = 0; inventorySlot < inventory.getContainerSize(); inventorySlot++) {
            candidates.add(new Candidate(inventorySlot, inventory.getItem(inventorySlot)));
        }
        return filterCandidates(candidates);
    }

    private List<Candidate> filterCandidates(List<Candidate> source) {
        List<Candidate> candidates = new ArrayList<>();
        for (Candidate candidate : source) {
            int inventorySlot = candidate.inventorySlot();
            ItemStack stack = candidate.stack();
            int selected = selectedFromSlot(inventorySlot);
            int available = Math.max(0, stack.getCount() - selected);
            if (category.eligible(stack)
                && !selectedElsewhere(inventorySlot)
                && !reservedByAnotherProfile(inventorySlot)
                && (available > 0 || (category.cap() == 1 && selected > 0))) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private boolean reservedByAnotherProfile(int inventorySlot) {
        var snapshot = DungeonPreparationLoadoutClient.latest();
        return snapshot != null && snapshot.reservedInventorySlots().contains(inventorySlot);
    }

    private boolean selectedElsewhere(int inventorySlot) {
        var snapshot = DungeonPreparationLoadoutClient.latest();
        if (snapshot == null) {
            return false;
        }
        for (DungeonPreparationLoadoutData.Category other
            : DungeonPreparationLoadoutData.Category.values()) {
            if (other == category) {
                continue;
            }
            if (snapshot.category(other).stream()
                .anyMatch(selection -> selection.inventorySlot() == inventorySlot)) {
                return true;
            }
        }
        return false;
    }

    private int selectedSlotCount() {
        var snapshot = DungeonPreparationLoadoutClient.latest();
        return snapshot == null
            ? 0
            : (int)snapshot.category(category).stream().filter(selection -> selection.valid()).count();
    }

    private int selectedFromSlot(int inventorySlot) {
        var snapshot = DungeonPreparationLoadoutClient.latest();
        if (snapshot == null) {
            return 0;
        }
        return snapshot.category(category).stream()
            .filter(selection -> selection.valid() && selection.inventorySlot() == inventorySlot)
            .mapToInt(selection -> selection.quantity())
            .sum();
    }

    private int availableFromSlot(Candidate candidate) {
        return Math.max(0, candidate.stack().getCount() - selectedFromSlot(
            candidate.inventorySlot()
        ));
    }

    private boolean canSelect(Candidate candidate) {
        int selectedFromCandidate = selectedFromSlot(candidate.inventorySlot());
        if (selectedFromCandidate > 0) {
            if (category.cap() == 1) {
                return true;
            }
            if (category.maxQuantityPerSlot() == 1) {
                return selectedSlotCount() < category.cap()
                    && availableFromSlot(candidate) > 0;
            }
            return selectedFromCandidate < category.maxQuantityPerSlot()
                && selectedFromCandidate < candidate.stack().getCount();
        }
        return category.cap() == 1 || selectedSlotCount() < category.cap();
    }

    private String selectionCapacityText() {
        String slots = selectedSlotCount() + "/" + category.cap() + " SLOTS";
        return category.maxQuantityPerSlot() == 1
            ? slots + "  •  1 ITEM EACH"
            : slots + "  •  " + category.maxQuantityPerSlot() + " ITEMS EACH";
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

    private static String label(DungeonPreparationLoadoutData.Category category) {
        if (category == DungeonPreparationLoadoutData.Category.PRIMARY_SWORD) {
            return "MAIN HAND";
        }
        return category == null
            ? "ITEM"
            : category.name().replace('_', ' ').toUpperCase(Locale.ROOT);
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
        graphics.hLine(x, x + width, y, color);
        graphics.hLine(x, x + width, y + height, color);
        graphics.vLine(x, y, y + height, color);
        graphics.vLine(x + width, y, y + height, color);
    }

    private record Candidate(int inventorySlot, ItemStack stack) {}
}
