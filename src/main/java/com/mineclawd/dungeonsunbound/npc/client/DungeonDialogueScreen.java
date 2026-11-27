package com.mineclawd.dungeonsunbound.npc.client;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.mineclawd.tensuradungeon.client.NpcInteractionClient;
import com.mineclawd.tensurahudnative.client.HudPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class DungeonDialogueScreen extends Screen {
    private static final int THEMED_TEXT = 0xFFF4F0E8;
    private static final int VANILLA_TEXT = 0xFFE0E0E0;
    private static final int VANILLA_NAME = 0xFFFFFF55;
    private static final int HINT_COLOR = 0xA0C9C3B8;
    private static final float CHARS_PER_TICK = 2.0F;
    private static final int SLIDE_TICKS = 6;
    private static final int OPTION_FADE_TICKS = 4;

    private final UUID sessionId;
    private final DialogueDefinition definition;
    private final String speaker;
    private final DungeonDialogueStyle style;
    private final int choiceColor;
    private final boolean themed;

    private String nodeId;
    private DialogueDefinition.Node node;
    private String text = "";
    private List<String> lines = List.of();
    private int wrappedWidth = -1;
    private float revealed;
    private List<Integer> visibleChoices = List.of();
    private List<Boolean> enabledChoices = List.of();
    private int selected;
    private boolean waiting = true;
    private int age;
    private int revealedAge;
    private boolean pendingVoice;

    public DungeonDialogueScreen(UUID sessionId, DialogueDefinition definition) {
        super(Component.empty());
        this.sessionId = sessionId;
        this.definition = definition;
        this.speaker = resolve(definition.npc_name);
        this.style = DungeonDialogueStyle.forSpeaker(speaker);
        this.choiceColor = readable(parseColor(definition.layout == null ? null : definition.layout.choice_color));
        this.themed = HudPreferences.themedDialogue();
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String speaker() {
        return speaker;
    }

    void showNode(String id, List<Boolean> availability) {
        DialogueDefinition.Node next = definition.nodes.get(id);
        if (next == null) {
            return;
        }
        boolean sameNode = id.equals(nodeId);
        nodeId = id;
        node = next;
        waiting = false;
        enabledChoices = availability == null ? List.of() : List.copyOf(availability);
        List<Integer> visible = new ArrayList<>();
        if (isChoiceNode() && next.choices != null) {
            for (int index = 0; index < next.choices.size(); index++) {
                DialogueDefinition.Choice choice = next.choices.get(index);
                if (isEnabled(index) || (choice != null && !"hide".equalsIgnoreCase(choice.when_unavailable))) {
                    visible.add(index);
                }
            }
        }
        visibleChoices = visible;
        selected = Mth.clamp(selected, 0, Math.max(0, visible.size() - 1));
        if (sameNode) {
            return;
        }
        selected = 0;
        text = expand(resolve(next.line));
        wrappedWidth = -1;
        revealed = 0.0F;
        revealedAge = 0;
        pendingVoice = true;
    }

    @Override
    public void tick() {
        age++;
        if (pendingVoice && node != null) {
            pendingVoice = false;
            NpcInteractionClient.playDialogueVoice(speaker, text);
        }
        if (node == null) {
            return;
        }
        if (revealed < text.length()) {
            revealed = Math.min(text.length(), revealed + CHARS_PER_TICK);
        } else {
            revealedAge++;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout(partialTick);
        if (themed) {
            renderThemedFrame(graphics, layout);
        } else {
            renderVanillaFrame(graphics, layout);
        }
        drawHint(graphics, layout);
        drawText(graphics, layout, partialTick);
        if (node == null || !fullyRevealed()) {
            return;
        }
        if (isChoiceNode()) {
            drawOptions(graphics, layout, mouseX, mouseY, partialTick);
        } else if (!waiting) {
            float bob = Mth.sin((age + partialTick) * 0.25F) * 2.0F;
            if (themed) {
                DungeonDialogueStyle.drawContinueIcon(graphics, layout.right - 5, Math.round(height - 30 + bob));
            } else {
                graphics.drawString(font, "▼", layout.right - 10, Math.round(layout.bottom - 14 + bob), VANILLA_NAME, true);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderThemedFrame(GuiGraphics graphics, Layout layout) {
        graphics.fillGradient(0, layout.top - 70, width, layout.top, 0x00000000, 0x58000000);
        graphics.fill(0, layout.top, width, height, 0xC4000000);
        graphics.fill(0, layout.top, width, layout.top + 2, 0xE0D9D4C8);
        graphics.fill(0, layout.top + 2, width, layout.top + 3, style.accent());
        graphics.fill(0, layout.top + 3, width, layout.top + 5, 0x604A535D);
        graphics.fill(0, height - 2, width, height, 0xC02E3338);
        String name = speaker.isBlank() ? "..." : speaker;
        int plateWidth = Math.min(Math.max(220, font.width(name) + 92), Math.max(250, width / 3));
        style.drawNameplate(graphics, font, name, layout.textLeft - 10, layout.top - 34, plateWidth, 42);
    }

    private void renderVanillaFrame(GuiGraphics graphics, Layout layout) {
        graphics.fillGradient(0, layout.top - 50, width, height, 0x00000000, 0x60000000);
        int left = layout.textLeft - 8;
        int right = layout.right + 8;
        int top = layout.top;
        int bottom = layout.bottom;
        graphics.fill(left + 1, top, right - 1, bottom, 0xF0100010);
        graphics.fill(left, top + 1, left + 1, bottom - 1, 0xF0100010);
        graphics.fill(right - 1, top + 1, right, bottom - 1, 0xF0100010);
        graphics.fillGradient(left + 1, top + 1, left + 2, bottom - 1, 0x505000FF, 0x5028007F);
        graphics.fillGradient(right - 2, top + 1, right - 1, bottom - 1, 0x505000FF, 0x5028007F);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, 0x505000FF);
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0x5028007F);
        String name = speaker.isBlank() ? "..." : speaker;
        graphics.drawString(font, name, layout.textLeft, top + 7, VANILLA_NAME, true);
        graphics.fill(layout.textLeft, top + 18, layout.textLeft + font.width(name), top + 19, 0x80FFFF55);
    }

    private void drawText(GuiGraphics graphics, Layout layout, float partialTick) {
        int shown = fullyRevealed()
            ? Integer.MAX_VALUE
            : (int) Math.min(text.length(), revealed + partialTick * CHARS_PER_TICK);
        int color = themed ? THEMED_TEXT : VANILLA_TEXT;
        int y = layout.textTop;
        for (String line : lines) {
            if (shown <= 0) {
                break;
            }
            String visible = line.length() <= shown ? line : line.substring(0, shown);
            graphics.drawString(font, visible, layout.textLeft, y, color, true);
            shown -= line.length() + 1;
            y += layout.lineHeight;
        }
    }

    private void drawOptions(GuiGraphics graphics, Layout layout, int mouseX, int mouseY, float partialTick) {
        float fade = Mth.clamp((revealedAge + partialTick) / OPTION_FADE_TICKS, 0.0F, 1.0F);
        int hovered = optionAt(layout, mouseX, mouseY);
        int x = layout.textLeft;
        int right = layout.right;
        for (int row = 0; row < visibleChoices.size(); row++) {
            int choiceIndex = visibleChoices.get(row);
            DialogueDefinition.Choice choice = node.choices.get(choiceIndex);
            int rowY = layout.optionsTop + row * layout.optionHeight + Math.round((1.0F - fade) * 4.0F);
            boolean active = !waiting && (hovered == row || (hovered < 0 && selected == row));
            boolean enabled = isEnabled(choiceIndex) && !waiting;
            String number = Integer.toString(row + 1);
            if (themed) {
                int h = layout.optionHeight;
                int fill = withAlpha(active ? style.selectedFill() : 0xA84D4B49, fade);
                int border = withAlpha(active ? style.selectedBorder() : 0x907C8588, fade);
                graphics.fill(x, rowY, right, rowY + h - 2, fill);
                graphics.fill(x, rowY, right, rowY + 1, border);
                graphics.fill(x, rowY + h - 3, right, rowY + h - 2, border);
                graphics.fill(x, rowY, x + 2, rowY + h - 2, border);
                graphics.fill(right - 2, rowY, right, rowY + h - 2, border);
                graphics.fill(x + 5, rowY + 3, x + 19, rowY + h - 5, withAlpha(active ? style.accent() : 0xC0302E2C, fade));
                graphics.drawString(font, number, x + 12 - font.width(number) / 2, rowY + 6, withAlpha(THEMED_TEXT, fade), true);
                String title = trimToWidth(expand(resolve(choice)), right - x - 34);
                graphics.drawString(font, title, x + 26, rowY + 6, withAlpha(enabled ? THEMED_TEXT : 0xFF8C8780, fade), true);
            } else {
                if (active) {
                    graphics.fill(x - 3, rowY - 2, right + 3, rowY + layout.optionHeight - 3, withAlpha(0x30FFFFFF, fade));
                }
                String prefix = (active ? "> " : "  ") + number + ". ";
                int color = !enabled ? 0xFF808080 : active ? 0xFFFFFFA0 : choiceColor;
                String title = trimToWidth(expand(resolve(choice)), right - x - font.width(prefix));
                graphics.drawString(font, prefix + title, x, rowY, withAlpha(color, fade), true);
            }
        }
    }

    private void drawHint(GuiGraphics graphics, Layout layout) {
        String hint;
        if (node == null || waiting) {
            hint = "Esc: skip";
        } else if (!fullyRevealed()) {
            hint = "Click: show all   Esc: skip";
        } else if (isChoiceNode()) {
            hint = "Click or 1-" + Math.max(1, visibleChoices.size()) + ": choose   Esc: skip";
        } else {
            hint = "Click: continue   Esc: skip";
        }
        graphics.pose().pushPose();
        graphics.pose().translate(layout.right, layout.top + (themed ? 12 : 8), 0.0F);
        graphics.pose().scale(0.75F, 0.75F, 1.0F);
        graphics.drawString(font, hint, -font.width(hint), 0, HINT_COLOR, false);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || node == null || waiting) {
            return true;
        }
        if (!fullyRevealed()) {
            revealAll();
            return true;
        }
        if (isChoiceNode()) {
            int row = optionAt(layout(1.0F), mouseX, mouseY);
            if (row >= 0) {
                choose(row);
            }
            return true;
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (node == null || waiting) {
            return true;
        }
        boolean confirm = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
            || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_E;
        if (!fullyRevealed()) {
            if (confirm) {
                revealAll();
            }
            return true;
        }
        if (isChoiceNode()) {
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                int row = keyCode - GLFW.GLFW_KEY_1;
                if (row < visibleChoices.size()) {
                    choose(row);
                }
            } else if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
                moveSelection(-1);
            } else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
                moveSelection(1);
            } else if (confirm && !visibleChoices.isEmpty()) {
                choose(selected);
            }
            return true;
        }
        if (confirm) {
            advance();
        }
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (node != null && isChoiceNode() && fullyRevealed()) {
            int row = optionAt(layout(1.0F), mouseX, mouseY);
            if (row >= 0) {
                selected = row;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        DungeonDialogueClient.screenRemoved(this);
    }

    private void choose(int row) {
        int choiceIndex = visibleChoices.get(row);
        if (!isEnabled(choiceIndex)) {
            return;
        }
        selected = row;
        waiting = true;
        DungeonDialogueClient.choose(nodeId, choiceIndex);
    }

    private void advance() {
        waiting = true;
        DungeonDialogueClient.advance(nodeId);
    }

    private void moveSelection(int direction) {
        if (!visibleChoices.isEmpty()) {
            selected = Math.floorMod(selected + direction, visibleChoices.size());
        }
    }

    private void revealAll() {
        revealed = text.length();
    }

    private boolean fullyRevealed() {
        return revealed >= text.length();
    }

    private boolean isChoiceNode() {
        return node != null && "choice".equalsIgnoreCase(node.type);
    }

    private boolean isEnabled(int choiceIndex) {
        return choiceIndex < enabledChoices.size() && Boolean.TRUE.equals(enabledChoices.get(choiceIndex));
    }

    private int optionAt(Layout layout, double mouseX, double mouseY) {
        if (mouseX < layout.textLeft - 3 || mouseX > layout.right + 3 || mouseY < layout.optionsTop - 2) {
            return -1;
        }
        int row = (int) ((mouseY - layout.optionsTop + 2) / layout.optionHeight);
        return row >= 0 && row < visibleChoices.size() ? row : -1;
    }

    private Layout layout(float partialTick) {
        int optionCount = isChoiceNode() ? visibleChoices.size() : 0;
        float slide = Mth.clamp((age + partialTick) / SLIDE_TICKS, 0.0F, 1.0F);
        float remaining = 1.0F - slide;
        float eased = remaining * remaining * remaining;
        if (themed) {
            int left = Math.max(28, width / 20);
            int textLeft = left + 22;
            int right = Math.max(textLeft + 160, width - left - 22);
            wrapTo(right - textLeft);
            int lineHeight = 13;
            int optionHeight = 22;
            int optionArea = optionCount == 0 ? 40 : optionCount * optionHeight + 24;
            int desired = 49 + lines.size() * lineHeight + optionArea;
            int panelHeight = Mth.clamp(desired, Math.min(150, height - 48), Math.max(96, height - 48));
            int offset = Math.round(eased * (panelHeight + 40));
            int top = height - panelHeight + offset;
            int optionsTop = height - 16 - optionCount * optionHeight + offset;
            return new Layout(textLeft, right, top, height + offset, top + 49, lineHeight, optionsTop, optionHeight);
        }
        int boxWidth = Math.min(width - 24, 430);
        int textLeft = (width - boxWidth) / 2 + 10;
        int right = textLeft + boxWidth - 20;
        wrapTo(right - textLeft);
        int lineHeight = 11;
        int optionHeight = 13;
        int optionArea = optionCount == 0 ? 14 : optionCount * optionHeight + 10;
        int boxHeight = Math.min(height - 24, 26 + lines.size() * lineHeight + optionArea + 8);
        int offset = Math.round(eased * (boxHeight + 20));
        int bottom = height - 10 + offset;
        int top = bottom - boxHeight;
        int optionsTop = bottom - 8 - optionCount * optionHeight;
        return new Layout(textLeft, right, top, bottom, top + 24, lineHeight, optionsTop, optionHeight);
    }

    private void wrapTo(int textWidth) {
        if (wrappedWidth != textWidth) {
            lines = wrap(text, font, textWidth);
            wrappedWidth = textWidth;
        }
    }

    private static int withAlpha(int color, float fade) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * fade);
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private static int parseColor(String value) {
        if (value != null && value.startsWith("#") && value.length() == 7) {
            try {
                return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        return VANILLA_TEXT;
    }

    private static int readable(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (0.299F * r + 0.587F * g + 0.114F * b >= 110.0F) {
            return color;
        }
        r = r + (255 - r) * 3 / 5;
        g = g + (255 - g) * 3 / 5;
        b = b + (255 - b) * 3 / 5;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String resolve(DialogueDefinition.Line line) {
        if (line == null) {
            return "";
        }
        if (line.literal != null) {
            return line.literal;
        }
        return line.text == null ? "" : I18n.get(line.text);
    }

    private static String resolve(DialogueDefinition.Choice choice) {
        if (choice == null) {
            return "";
        }
        if (choice.literal != null) {
            return choice.literal;
        }
        return choice.text == null ? "" : I18n.get(choice.text);
    }

    private static String expand(String value) {
        Minecraft minecraft = Minecraft.getInstance();
        String player = minecraft.player != null ? minecraft.player.getGameProfile().getName() : minecraft.getUser().getName();
        return value.replace("[player]", player).replace("[br]", "\n").replace("\\n", "\n");
    }

    private static List<String> wrap(String source, Font font, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String paragraph : source.split("\n", -1)) {
            if (paragraph.isBlank()) {
                result.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.trim().split(" +")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && font.width(candidate) > maxWidth) {
                    result.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            result.add(current.toString());
        }
        return result;
    }

    private String trimToWidth(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String result = value;
        while (result.length() > 1 && font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.stripTrailing() + "...";
    }

    private record Layout(
        int textLeft,
        int right,
        int top,
        int bottom,
        int textTop,
        int lineHeight,
        int optionsTop,
        int optionHeight
    ) {
    }
}
