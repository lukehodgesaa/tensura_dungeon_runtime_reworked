package com.mineclawd.tensurahudnative.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NativeHudRenderer {
    private static double lastHealth = Double.NaN;
    private static double lastMagicule = Double.NaN;
    private static double lastAura = Double.NaN;
    private static long lastVitalChangeAt;
    private static final long OBJECTIVE_HOLD_MS = 1_500L;
    private static final long OBJECTIVE_FADE_MS = 650L;
    private static final int MAX_VISIBLE_OBJECTIVE_LINES = 4;
    private static long lastObjectiveActiveAt = Long.MIN_VALUE;
    private static HudSnapshot.Objective lastVisibleObjective = HudSnapshot.Objective.inactive();

    private NativeHudRenderer() {
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker, Minecraft minecraft, HudSnapshot snapshot,
                       HudSettings settings, long now, boolean hotbarVisible, int previewRemainingSeconds) {
        HudLayout.Transform transform = HudLayout.fit(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(),
                settings.hudScale
        );
        double renderTicks = minecraft.level == null
                ? now / 50.0D
                : minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        boolean expanded = Screen.hasAltDown();
        HudTheme theme = HudThemes.active(settings);
        HudTheme.OverlayPolicy policy = theme.overlayPolicy();
        HudTheme.ObjectiveVisual questStyle = QuestThemes.activeObjective(settings, theme);

        graphics.pose().pushPose();
        graphics.pose().translate(transform.offsetX(), transform.offsetY(), 0.0D);
        graphics.pose().scale(transform.scale(), transform.scale(), 1.0F);

        if (policy.objective() && settings.showQuestTracker && DungeonRunState.inDungeon()) {
            renderObjective(graphics, minecraft.font, snapshot.objective(), settings, renderTicks, now, questStyle);
        }
        if (policy.utility() && settings.showUtilityButtons) {
            renderUtility(graphics, minecraft.font, theme);
        }
        if (policy.abilities()) {
            renderAbilities(graphics, minecraft.font, snapshot.abilities(), snapshot.presetName(),
                    expanded, renderTicks, theme);
        }
        if (policy.selectedItem() && settings.showSelectedItem) {
            renderSelectedItem(graphics, minecraft.font, minecraft, snapshot.selectedItem(),
                    expanded || hotbarVisible, theme);
        }
        if (policy.vitals()) {
            renderVitals(graphics, minecraft.font, snapshot.status(), now, theme);
        }
        if (previewRemainingSeconds > 0) {
            renderPreviewCountdown(graphics, minecraft.font, previewRemainingSeconds, theme);
        }
        graphics.pose().popPose();
    }

    private static void renderPreviewCountdown(GuiGraphics graphics, Font font, int remainingSeconds,
                                               HudTheme theme) {
        HudTheme.Status style = theme.status();
        HudTheme.Palette palette = theme.palette();
        int center = style.x() + style.width() / 2;
        int y = style.y() - 31;
        int color = remainingSeconds <= 5 ? palette.warning() : palette.accentSoft();
        drawCentered(graphics, font, "HUD PREVIEW " + remainingSeconds + "s", center, y, color, true);
    }

    private static void renderObjective(GuiGraphics graphics, Font font, HudSnapshot.Objective objective,
                                        HudSettings settings, double renderTicks, long now,
                                        HudTheme.ObjectiveVisual style) {
        if (objective.active()) {
            lastObjectiveActiveAt = now;
            lastVisibleObjective = objective;
        }
        float visibility = objectiveVisibilityAlpha(objective.active(), now, lastObjectiveActiveAt,
                style.holdMs(), style.fadeMs());
        if (visibility <= 0.0F) {
            return;
        }
        HudSnapshot.Objective displayedObjective = objective.active() ? objective : lastVisibleObjective;
        graphics.setColor(1.0F, 1.0F, 1.0F, visibility);
        List<HudSnapshot.ObjectiveLine> lines = objectiveLines(displayedObjective);
        if (lines.isEmpty()) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        int x = style.x();
        int y = style.y();
        int w = style.width();
        int h = objectivePanelHeight(style, lines.size());

        fillChamferedBar(graphics, x + style.shadowOffsetX(), y + style.shadowOffsetY(), w, h, style.shadowColor());
        drawQuestRibbonBackground(graphics, x, y, lines.size(), style);

        HudSnapshot.ObjectiveLine primary = lines.getFirst();
        int primaryTrackX = x + style.primaryProgressOffsetX();
        int primaryTrackY = y + style.primaryProgressOffsetY();
        int primaryTrackRight = x + w - style.primaryProgressRightInset();
        if (primary.target() > 0) {
            int fillW = Math.round((primaryTrackRight - primaryTrackX)
                    * percent(primary.current(), primary.target()));
            fillHorizontalGradientChamfered(
                    graphics,
                    primaryTrackX,
                    primaryTrackY,
                    fillW,
                    style.progressHeight(),
                    darken(style.iconColor(), style.progressDarkenFactor()),
                    style.iconColor()
            );
            if (fillW > 5) {
                graphics.fill(primaryTrackX + 2, primaryTrackY, primaryTrackX + fillW - 2,
                        primaryTrackY + 1, style.progressHighlightColor());
            }
        }
        for (int i = 1; i < lines.size(); i++) {
            HudSnapshot.ObjectiveLine line = lines.get(i);
            if (line.target() <= 0) {
                continue;
            }
            int rowY = y + style.headerHeight() + style.primaryHeight() + (i - 1) * style.subrowHeight();
            int fillW = Math.round((w - style.subrowLabelOffsetX() - style.subrowValueRightInset())
                    * percent(line.current(), line.target()));
            graphics.fill(
                    x + style.subrowLabelOffsetX(),
                    rowY + style.subrowHeight() - 3,
                    x + style.subrowLabelOffsetX() + fillW,
                    rowY + style.subrowHeight() - 2,
                    "lime".equalsIgnoreCase(line.accent()) ? style.activeColor() : style.iconColor()
            );
        }

        int iconColor = displayedObjective.active() ? style.iconColor() : style.iconMutedColor();
        drawObjectiveIcon(
                graphics,
                displayedObjective.objectiveId(),
                x + style.iconCenterX(),
                y + style.iconCenterY(),
                style.iconRadius(),
                iconColor
        );
        drawQuestChrome(graphics, x, y, lines.size(), visibility, style);

        String title = safe(displayedObjective.title(), "Dimensional Dungeon").toUpperCase(Locale.ROOT);
        drawStyled(graphics, font, trim(font, title, style.titleMaxWidth()),
                x + style.titleOffsetX(), y + style.titleOffsetY(), style.titleText());
        if (displayedObjective.floorNumber() > 0) {
            drawCenteredScaled(
                    graphics,
                    font,
                    "F" + compact(displayedObjective.floorNumber()),
                    x + style.floorCellCenterX(),
                    y + style.metadataOffsetY(),
                    style.floorText().color(),
                    style.floorText().shadow(),
                    style.floorText().scale()
            );
        }
        if (displayedObjective.runElapsedTicks() > 0L) {
            drawCenteredScaled(
                    graphics,
                    font,
                    formatRunTime(displayedObjective.runElapsedTicks()),
                    x + style.timerCellCenterX(),
                    y + style.metadataOffsetY(),
                    style.timerText().color(),
                    style.timerText().shadow(),
                    style.timerText().scale()
            );
        }

        drawObjectiveLine(
                graphics,
                font,
                primary,
                x + style.primaryLabelOffsetX(),
                y + style.primaryLabelOffsetY(),
                x + w - style.primaryValueRightInset(),
                style.primaryLabelText(),
                style.primaryValueText(),
                style,
                true
        );
        for (int i = 1; i < lines.size(); i++) {
            HudSnapshot.ObjectiveLine line = lines.get(i);
            int rowY = y + style.headerHeight() + style.primaryHeight() + (i - 1) * style.subrowHeight();
            int accent = "lime".equalsIgnoreCase(line.accent()) ? style.activeColor() : style.iconColor();
            drawObjectiveMarker(
                    graphics,
                    x + style.subrowMarkerOffsetX(),
                    rowY + style.subrowHeight() / 2,
                    line.label(),
                    accent,
                    style
            );
            drawObjectiveLine(
                    graphics,
                    font,
                    line,
                    x + style.subrowLabelOffsetX(),
                    rowY + style.subrowTextOffsetY(),
                    x + w - style.subrowValueRightInset(),
                    style.secondaryLabelText(),
                    style.secondaryValueText(),
                    style,
                    false
            );
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawQuestRibbonBackground(GuiGraphics graphics, int x, int y, int lineCount,
                                                  HudTheme.ObjectiveVisual style) {
        HudTheme.Gradient header = style.headerGradient();
        if (style.fullBandBackgrounds()) {
            graphics.fillGradient(
                    x + 4, y + 2,
                    x + style.width() - 4, y + style.headerHeight() - 1,
                    header.startColor(), header.endColor()
            );
        } else {
            graphics.fillGradient(x + 12, y + 3, x + 198, y + style.headerHeight(),
                    header.startColor(), header.endColor());
            graphics.fillGradient(x + 6, y + 7, x + 205, y + style.headerHeight(),
                    header.startColor(), header.endColor());
            graphics.fillGradient(x + 1, y + 11, x + 219, y + style.headerHeight(),
                    header.startColor(), header.endColor());
            graphics.fillGradient(x + 219, y + 9, x + style.width() - 1, y + style.headerHeight(),
                    header.startColor(), header.endColor());
        }

        int primaryY = y + style.headerHeight();
        HudTheme.Gradient primary = style.primaryGradient();
        int primaryInset = style.fullBandBackgrounds() ? 4 : 2;
        graphics.fillGradient(x + primaryInset, primaryY,
                x + style.width() - primaryInset, primaryY + style.primaryHeight() - 1,
                primary.startColor(), primary.endColor());
        graphics.fill(
                x + style.primaryProgressOffsetX(),
                y + style.primaryProgressOffsetY(),
                x + style.width() - style.primaryProgressRightInset(),
                y + style.primaryProgressOffsetY() + style.progressHeight(),
                style.progressTrackColor()
        );

        HudTheme.Gradient subrow = style.subrowGradient();
        for (int i = 1; i < lineCount; i++) {
            int rowY = primaryY + style.primaryHeight() + (i - 1) * style.subrowHeight();
            int subrowInset = style.fullBandBackgrounds() ? 4 : 3;
            graphics.fillGradient(x + subrowInset, rowY + 1,
                    x + style.width() - subrowInset, rowY + style.subrowHeight() - 1,
                    subrow.startColor(), subrow.endColor());
        }
    }

    private static void drawQuestChrome(GuiGraphics graphics, int x, int y, int lineCount,
                                        float visibility, HudTheme.ObjectiveVisual style) {
        HudTheme.QuestChrome chrome = style.chrome();
        graphics.setColor(1.0F, 1.0F, 1.0F, visibility);
        blitThemeTexture(graphics, chrome.header(), x, y, style.width(), style.headerHeight());
        int primaryY = y + style.headerHeight();
        blitThemeTexture(graphics, chrome.primary(), x, primaryY,
                style.width(), style.primaryHeight());
        for (int i = 1; i < lineCount; i++) {
            int rowY = primaryY + style.primaryHeight() + (i - 1) * style.subrowHeight();
            blitThemeTexture(graphics, chrome.subrow(), x, rowY,
                    style.width(), style.subrowHeight());
        }
    }

    private static void drawObjectiveLine(GuiGraphics graphics, Font font, HudSnapshot.ObjectiveLine line,
                                          int labelX, int y, int valueRight,
                                          HudTheme.TextStyle labelStyle, HudTheme.TextStyle valueStyle,
                                          HudTheme.ObjectiveVisual style, boolean primary) {
        String value = objectiveValue(line);
        int valueWidth = value.isBlank() ? 0 : Math.max(font.width("999 / 999"), font.width(value));
        int labelWidth = Math.max(style.minimumLabelWidth(),
                valueRight - valueWidth - style.valueGap() - labelX);
        drawStyled(graphics, font, trim(font, objectiveDisplayLabel(line.label()), labelWidth),
                labelX, y, labelStyle);
        if (!value.isBlank()) {
            int color = !primary && "lime".equalsIgnoreCase(line.accent())
                    ? style.activeColor()
                    : valueStyle.color();
            graphics.drawString(font, value, valueRight - font.width(value), y, color, valueStyle.shadow());
        }
    }

    private static String objectiveValue(HudSnapshot.ObjectiveLine line) {
        if (line.target() == 0) {
            return "";
        }
        return line.target() < 0
                ? compact(line.current())
                : compact(line.current()) + " / " + compact(line.target());
    }

    private static void drawObjectiveIcon(GuiGraphics graphics, String objectiveId,
                                          int cx, int cy, int radius, int color) {
        String id = safe(objectiveId, "NONE").toUpperCase(Locale.ROOT);
        int arm = Math.max(3, radius - 2);
        switch (id) {
            case "CLEAR_ALL" -> {
                for (int i = -arm; i <= arm; i++) {
                    graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                    graphics.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
                }
                graphics.fill(cx - arm - 1, cy - arm - 1, cx - arm + 1, cy - arm + 1, color);
                graphics.fill(cx + arm, cy - arm - 1, cx + arm + 2, cy - arm + 1, color);
            }
            case "KILL_ELITE" -> {
                drawRing(graphics, cx, cy, arm, 24, 1.0F, color, 0x00000000, 1);
                drawDiamond(graphics, cx, cy, 2, color, color, 1);
            }
            case "SURVIVE_TIMER" -> {
                graphics.fill(cx - arm, cy - arm, cx + arm + 1, cy - arm + 1, color);
                graphics.fill(cx - arm, cy + arm, cx + arm + 1, cy + arm + 1, color);
                for (int i = 0; i <= arm * 2; i++) {
                    int inset = Math.min(i, arm * 2 - i);
                    graphics.fill(cx - arm + inset, cy - arm + i,
                            cx - arm + inset + 1, cy - arm + i + 1, color);
                    graphics.fill(cx + arm - inset, cy - arm + i,
                            cx + arm - inset + 1, cy - arm + i + 1, color);
                }
            }
            case "ACTIVATE_RUNES" -> {
                drawSparkle(graphics, cx, cy, arm, color);
                graphics.fill(cx - arm, cy - arm, cx - arm + 2, cy - arm + 2, color);
                graphics.fill(cx + arm - 1, cy - arm, cx + arm + 1, cy - arm + 2, color);
                graphics.fill(cx - arm, cy + arm - 1, cx - arm + 2, cy + arm + 1, color);
                graphics.fill(cx + arm - 1, cy + arm - 1, cx + arm + 1, cy + arm + 1, color);
            }
            default -> drawDiamond(graphics, cx, cy, arm, 0x00000000, color, 1);
        }
    }

    private static void blitThemeTexture(GuiGraphics graphics, HudTheme.Texture texture,
                                         int x, int y, int width, int height) {
        graphics.blit(
                texture.location(),
                x,
                y,
                width,
                height,
                0.0F,
                0.0F,
                texture.width(),
                texture.height(),
                texture.width(),
                texture.height()
        );
    }

    private static void renderUtility(GuiGraphics graphics, Font font, HudTheme theme) {
        HudTheme.Utility style = theme.utility();
        HudTheme.UtilityVisual visual = theme.presentation().utility();
        HudTheme.Palette palette = theme.palette();
        int x = style.x();
        int size = style.size();
        List<Integer> ys = style.yPositions();
        String[] keys = {"J", "B", "ESC"};
        for (int i = 0; i < ys.size(); i++) {
            int y = ys.get(i);
            graphics.fill(x - visual.shadowInset(), y - visual.shadowInset(),
                    x + size + visual.shadowInset(), y + size + visual.shadowInset(), visual.shadowColor());
            graphics.fillGradient(x, y, x + size, y + size,
                    visual.backgroundGradient().startColor(), visual.backgroundGradient().endColor());
            border(graphics, x, y, size, size, i == 1 ? palette.accent() : palette.keyBorder());
            int inner = visual.innerBorderInset();
            border(graphics, x + inner, y + inner, size - inner * 2, size - inner * 2,
                    visual.innerBorderColor());
            int recess = visual.recessInset();
            graphics.fill(x + recess, y + recess, x + size - recess, y + size - recess, visual.recessColor());
            if (i == 0) {
                graphics.fill(x + 7, y + 8, x + 13, y + 21, visual.bookColor());
                graphics.fill(x + 15, y + 8, x + 21, y + 21, visual.bookColor());
                graphics.fill(x + 13, y + 7, x + 15, y + 22, visual.bookSpineColor());
            } else if (i == 1) {
                drawDiamond(graphics, x + 14, y + 14, 8, visual.diamondOuterFill(), palette.accent(), 1);
                drawDiamond(graphics, x + 14, y + 14, 3, visual.diamondInnerFill(), palette.accentSoft(), 1);
            } else {
                drawRing(graphics, x + 14, y + 14, 8, 32, 0.82F,
                        visual.powerColor(), visual.powerTrackColor(), 1);
                graphics.fill(x + 13, y + 6, x + 15, y + 14, visual.powerColor());
                graphics.fill(x + 14, y + 13, x + 20, y + 15, visual.powerColor());
            }
            if (style.frame() != null) {
                blitThemeTexture(graphics, style.frame(), x, y, size, size);
            }
            drawCenteredScaled(graphics, font, keys[i], x + size / 2, y + size + visual.labelOffsetY(),
                    visual.labelText().color(), visual.labelText().shadow(), visual.labelText().scale());
        }
    }

    private static void renderAbilities(GuiGraphics graphics, Font font, List<HudSnapshot.Ability> abilities,
                                        String presetName, boolean expanded, double renderTicks, HudTheme theme) {
        HudTheme.Abilities style = theme.abilities();
        HudTheme.AbilityVisual visual = theme.presentation().abilities();
        HudTheme.Palette palette = theme.palette();
        if (expanded && presetName != null && !presetName.isBlank()) {
            String label = trim(font, "PRESET - " + presetName.toUpperCase(Locale.ROOT),
                    visual.presetMaxTextWidth());
            int panelW = Math.max(visual.presetMinWidth(), font.width(label) + visual.presetPadding());
            int panelX = style.centers().getFirst().x() - panelW + visual.presetOffsetFromFirstCenter();
            int headerY = style.headerY();
            if (style.presetFrame() == null) {
                graphics.fillGradient(panelX, headerY, panelX + panelW, headerY + visual.presetHeight(),
                        visual.presetGradient().startColor(), visual.presetGradient().endColor());
                graphics.fill(panelX, headerY, panelX + panelW, headerY + 1, visual.presetRailColor());
            } else {
                int recessX = panelX + visual.presetRecessInsetX();
                int recessY = headerY + visual.presetRecessInsetTop();
                int recessWidth = panelW - visual.presetRecessInsetX() * 2;
                int recessHeight = visual.presetHeight()
                        - visual.presetRecessInsetTop() - visual.presetRecessInsetBottom();
                fillHorizontalGradientChamfered(
                        graphics, recessX, recessY, recessWidth, recessHeight,
                        visual.presetGradient().startColor(), visual.presetGradient().endColor());
                blitThemeTexture(graphics, style.presetFrame(), panelX, headerY,
                        panelW, visual.presetHeight());
            }
            drawStyled(graphics, font, label, panelX + visual.presetPadding() / 2,
                    headerY + visual.presetTextOffsetY(),
                    visual.presetText());
        }
        for (int i = 0; i < 3; i++) {
            HudSnapshot.Ability ability = i < abilities.size() ? abilities.get(i) : HudSnapshot.Ability.empty(i == 0 ? "Z" : i == 1 ? "X" : "C");
            HudTheme.Point center = style.centers().get(i);
            drawSkillOrb(graphics, font, ability, center.x(), center.y(),
                    style.iconRadius(), expanded, i, renderTicks, theme);
        }
    }

    private static void drawSkillOrb(GuiGraphics graphics, Font font, HudSnapshot.Ability ability,
                                     int cx, int cy, int radius, boolean expanded, int slotIndex,
                                     double renderTicks, HudTheme theme) {
        HudTheme.Abilities style = theme.abilities();
        HudTheme.AbilityVisual visual = theme.presentation().abilities();
        HudTheme.Palette palette = theme.palette();
        int shadowRadius = radius + visual.shadowExtraRadius();
        int masteryTrack = style.xpTrackColor();
        int masteryActive = style.xpFillColor();
        boolean mastered = isMastered(ability);
        boolean ultimate = isUltimate(ability);
        fillCircle(graphics, cx, cy + visual.shadowOffsetY(), shadowRadius, visual.shadowColor());
        fillCircle(graphics, cx, cy, radius + 1, visual.orbBackground());
        if (!ability.empty() && !mastered && expanded) {
            drawAbilityXpChannel(graphics, cx, cy, style);
            drawAbilityXpArc(graphics, cx, cy, 1.0F, masteryTrack, style);
            float progress = ability.masteryPercent();
            if (progress > 0.0F) {
                drawAbilityXpArc(graphics, cx, cy, progress, masteryActive, style);
                if (progress < 0.999F) {
                    drawAbilityXpEndCap(graphics, cx, cy, progress, masteryActive, style, visual);
                }
            }
        }

        if (!ability.empty() && !ability.icon().isBlank()) {
            ResourceLocation icon = parseTexture(ability.icon());
            if (icon != null) {
                int iconSize = radius * 2;
                int iconX = cx - iconSize / 2;
                int iconY = cy - iconSize / 2;
                ResourceLocation circularIcon = CircularIconTextures.get(icon, 4, 4, 24, iconSize);
                graphics.setColor(visual.iconRed(), visual.iconGreen(), visual.iconBlue(), visual.iconAlpha());
                if (circularIcon.equals(icon)) {
                    graphics.blit(icon, iconX, iconY, iconSize, iconSize,
                            4.0F, 4.0F, 24, 24, 32, 32);
                } else {
                    graphics.blit(circularIcon, iconX, iconY, iconSize, iconSize,
                            0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
                }
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } else {
            drawDiamond(graphics, cx, cy, 4, visual.emptyFill(), visual.emptyBorder(), 1);
        }

        drawPixelLabAbilityFrame(graphics, cx, cy, mastered, ultimate, slotIndex, renderTicks, style);

        if (ability.cooldownTicks() > 0) {
            fillCircle(graphics, cx, cy, radius - visual.cooldownInset(), visual.cooldownColor());
            String cooldown = String.format(Locale.ROOT, "%.1f", ability.cooldownTicks() / 20.0F);
            drawCenteredScaled(graphics, font, cooldown, cx, cy + visual.cooldownTextOffsetY(),
                    visual.cooldownText().color(), visual.cooldownText().shadow(), visual.cooldownText().scale());
        }

        String keyLabel = safe(ability.key(), "?");
        int keyW = Math.max(11, font.width(keyLabel) + 6);
        int keyX = Math.min(cx + radius + style.keyOffsetX(), style.keyRightEdge() - keyW);
        int keyY = cy + style.keyOffsetY();
        graphics.fill(keyX, keyY, keyX + keyW, keyY + 9, palette.keyBackground());
        border(graphics, keyX, keyY, keyW, 9, palette.keyBorder());
        drawCentered(graphics, font, keyLabel, keyX + keyW / 2, keyY + 1, palette.keyText(), true);

        if (expanded) {
            int textInset = ultimate
                    ? style.ultimateExpandedPanelTextInset()
                    : style.expandedPanelTextInset();
            int panelW = Math.min(visual.panelMaxWidth(), Math.max(style.expandedPanelMinWidth(),
                    font.width(ability.name()) + textInset * 2));
            int panelH = ultimate
                    ? style.ultimateExpandedPanelHeight()
                    : style.expandedPanelHeight();
            int panelX = cx - panelW - radius - visual.panelGap();
            int panelY = cy - panelH / 2;
            HudTheme.Texture panelTexture = ultimate
                    ? style.ultimateExpandedPanelFrame()
                    : style.expandedPanelFrame();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(
                    panelTexture.location(),
                    panelX,
                    panelY,
                    panelW,
                    panelH,
                    0.0F,
                    0.0F,
                    panelTexture.width(),
                    panelTexture.height(),
                    panelTexture.width(),
                    panelTexture.height()
            );
            int textWidth = panelW - textInset * 2;
            drawStyled(graphics, font, trim(font, ability.name(), textWidth),
                    panelX + textInset, panelY + visual.nameOffsetY(), visual.nameText());
            String mode = trim(font, safe(ability.modeName(), "Default"), textWidth);
            drawStyled(graphics, font, mode, panelX + textInset,
                    panelY + visual.modeOffsetY(), visual.modeText());
        }
    }

    private static void drawPixelLabAbilityFrame(GuiGraphics graphics, int cx, int cy,
                                                  boolean mastered, boolean ultimate, int slotIndex,
                                                  double renderTicks, HudTheme.Abilities style) {
        int size = mastered ? style.masteryFrameSize() : style.normalFrameSize();
        int x = cx - size / 2;
        int y = cy - size / 2;
        HudTheme.Texture texture = style.unmasteredFrame(slotIndex);
        if (mastered) {
            texture = ultimate
                    ? style.ultimateMasteryFrame(renderTicks)
                    : slotIndex == 1
                    ? style.voidOpalMasteryFrame()
                    : style.imperialAmethystMasteryFrame();
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(
                texture.location(),
                x,
                y,
                size,
                size,
                0.0F,
                0.0F,
                texture.width(),
                texture.height(),
                texture.width(),
                texture.height()
        );
    }

    private static void drawAbilityXpChannel(GuiGraphics graphics, int cx, int cy, HudTheme.Abilities style) {
        int outer = style.xpChannelOuterRadius();
        float innerEdge = style.xpChannelInnerRadius() - 0.5F;
        float innerBevelEdge = style.xpChannelInnerRadius() + 1.0F;
        float outerBevelEdge = style.xpChannelOuterRadius() - 1.0F;
        float outerEdge = style.xpChannelOuterRadius() + 0.5F;

        for (int dy = -outer - 1; dy <= outer + 1; dy++) {
            for (int dx = -outer - 1; dx <= outer + 1; dx++) {
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance < innerEdge || distance > outerEdge) {
                    continue;
                }
                int color;
                if (distance < innerBevelEdge) {
                    color = style.xpChannelInnerColor();
                } else if (distance >= outerBevelEdge) {
                    color = style.xpChannelOuterColor();
                } else {
                    color = style.xpChannelRecessColor();
                }
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
    }

    private static void drawAbilityXpEndCap(GuiGraphics graphics, int cx, int cy,
                                             float progress, int color, HudTheme.Abilities style,
                                             HudTheme.AbilityVisual visual) {
        double angle = (-Math.PI / 2.0D) + Math.PI * 2.0D * progress;
        int middleRadius = (style.xpFillInnerRadius() + style.xpFillOuterRadius()) / 2;
        int px = cx + (int) Math.round(Math.cos(angle) * middleRadius);
        int py = cy + (int) Math.round(Math.sin(angle) * middleRadius);
        graphics.fill(px - 1, py - 1, px + 2, py + 2,
                visual.xpEndCapShadowColor() | (color & 0x00FFFFFF));
        graphics.fill(px, py, px + 1, py + 1, visual.xpEndCapHighlightColor());
    }

    private static void drawAbilityXpArc(GuiGraphics graphics, int cx, int cy,
                                         float progress, int color, HudTheme.Abilities style) {
        for (int radius = style.xpFillInnerRadius();
             radius <= style.xpFillOuterRadius();
             radius++) {
            drawRing(graphics, cx, cy, radius, style.xpRingSegments(), progress,
                    color, 0x00000000, 1);
        }
    }

    static boolean isMastered(HudSnapshot.Ability ability) {
        return ability != null && !ability.empty() && ability.masteryPercent() >= 0.999F;
    }

    static boolean isUltimate(HudSnapshot.Ability ability) {
        return ability != null && !ability.empty()
                && "ultimate".equalsIgnoreCase(ability.category());
    }

    private static void renderSelectedItem(GuiGraphics graphics, Font font, Minecraft minecraft,
                                           HudSnapshot.SelectedItem selected, boolean expanded, HudTheme theme) {
        HudTheme.SelectedItem style = theme.selectedItem();
        HudTheme.SelectedItemVisual visual = theme.presentation().selectedItem();
        HudTheme.Palette palette = theme.palette();
        int x = style.x();
        int y = style.y();
        int previewWidth = style.previewWidth();
        int previewHeight = style.previewHeight();
        int center = x + previewWidth / 2;
        int frameX = x + style.frameOffsetX();
        int frameY = y + style.frameOffsetY();
        int keyY = y + style.keyOffsetY();

        graphics.fillGradient(x, y, x + previewWidth, y + previewHeight,
                visual.recessGradient().startColor(), visual.recessGradient().endColor());
        fillCircle(graphics, center, keyY + visual.medallionCenterOffsetY(),
                visual.medallionRadius(), visual.medallionColor());

        ItemStack stack = selected.stack();
        if (stack != null && !stack.isEmpty()) {
            int renderedItemSize = Math.round(16.0F * style.itemScale());
            int itemX = x + (previewWidth - renderedItemSize) / 2;
            int itemY = y + (previewHeight - renderedItemSize) / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(itemX, itemY, visual.itemRenderDepth());
            graphics.pose().scale(style.itemScale(), style.itemScale(), 1.0F);
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(font, stack, 0, 0, "");
            graphics.pose().popPose();
        }

        HudTheme.Texture frameTexture = style.frame();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(
                frameTexture.location(),
                frameX,
                frameY,
                style.frameWidth(),
                style.frameHeight(),
                0.0F,
                0.0F,
                frameTexture.width(),
                frameTexture.height(),
                frameTexture.width(),
                frameTexture.height()
        );
        drawCenteredScaled(graphics, font, selected.key(), center + visual.keyTextOffsetX(), keyY + 1,
                visual.keyText().color(), visual.keyText().shadow(), visual.keyText().scale());
        drawCenteredScaled(graphics, font, "SELECTED ITEM", center + style.labelOffsetX(),
                y + style.labelOffsetY(),
                visual.labelText().color(), visual.labelText().shadow(), visual.labelText().scale());

        int nameWidth = Math.min(visual.namePlateMaxWidth(), Math.max(style.namePlateMinWidth(),
                font.width(selected.name()) + style.namePlateTextInset() * 2));
        int nameCenter = center + style.namePlateOffsetX();
        int nameX = nameCenter - nameWidth / 2;
        int nameY = y + style.namePlateOffsetY();
        HudTheme.Texture nameTexture = style.namePlate();
        graphics.blit(
                nameTexture.location(),
                nameX,
                nameY,
                nameWidth,
                style.namePlateHeight(),
                0.0F,
                0.0F,
                nameTexture.width(),
                nameTexture.height(),
                nameTexture.width(),
                nameTexture.height()
        );
        drawCentered(graphics, font,
                trim(font, selected.name(), nameWidth - style.namePlateTextInset() * 2),
                nameCenter, nameY + style.nameTextOffsetY(),
                visual.nameText().color(), visual.nameText().shadow());

        if (expanded || theme.hotbar().alwaysVisible()) {
            renderHotbarStrip(graphics, font, minecraft, selected.slotIndex(), theme);
        }
    }

    private static void renderHotbarStrip(GuiGraphics graphics, Font font, Minecraft minecraft,
                                          int selectedSlot, HudTheme theme) {
        HudTheme.Hotbar style = theme.hotbar();
        HudTheme.HotbarVisual visual = theme.presentation().hotbar();
        HudTheme.Palette palette = theme.palette();
        int startX = style.startX();
        int y = style.y();
        int slot = style.slotSize();
        int gap = style.gap();
        int stripEnd = startX + (slot + gap) * 9 - gap;
        boolean textured = style.frame() != null;
        int frameX = textured ? startX + style.frameOffsetX() : 0;
        int frameY = textured ? y + style.frameOffsetY() : 0;
        int frameWidth = textured && style.frameWidth() > 0 ? style.frameWidth()
                : textured ? style.frame().width() : 0;
        int frameHeight = textured && style.frameHeight() > 0 ? style.frameHeight()
                : textured ? style.frame().height() : 0;
        if (!textured) {
            int panelTop = y - visual.panelTopPadding();
            int panelBottom = y + slot + visual.panelBottomPadding();
            int panelLeft = startX - visual.panelSidePadding();
            int panelRight = stripEnd + visual.panelSidePadding();
            graphics.fillGradient(panelLeft, panelTop, panelRight, panelBottom,
                    visual.panelGradient().startColor(), visual.panelGradient().endColor());
            border(graphics, panelLeft, panelTop, panelRight - panelLeft,
                    panelBottom - panelTop, visual.panelBorderColor());
            int highlightInset = visual.panelHighlightInset();
            graphics.fill(panelLeft + highlightInset, panelTop + highlightInset,
                    panelRight - highlightInset, panelTop + highlightInset + 1, visual.panelHighlightColor());
        }
        for (int i = 0; i < 9; i++) {
            int x = startX + i * (slot + gap);
            boolean selected = i == selectedSlot;
            HudTheme.Gradient slotGradient = selected ? visual.selectedSlotGradient() : visual.normalSlotGradient();
            graphics.fillGradient(x, y, x + slot, y + slot,
                    slotGradient.startColor(), slotGradient.endColor());
            if (!textured) {
                border(graphics, x, y, slot, slot,
                        selected ? visual.selectedSlotBorderColor() : visual.normalSlotBorderColor());
            }
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + visual.itemInset(), y + visual.itemInset(), visual.itemRenderDepth());
                graphics.pose().scale(visual.itemScale(), visual.itemScale(), 1.0F);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            }
            if (!textured) {
                drawCenteredScaled(graphics, font, Integer.toString(i + 1), x + slot / 2,
                        y + slot + visual.numberOffsetY(),
                        selected ? visual.selectedNumberColor() : visual.normalNumberColor(),
                        visual.numberText().shadow(), visual.numberText().scale());
            }
        }
        if (textured) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            blitThemeTexture(graphics, style.frame(), frameX, frameY, frameWidth, frameHeight);
            for (int i = 0; i < 9; i++) {
                int x = startX + i * (slot + gap);
                boolean selected = i == selectedSlot;
                drawCenteredScaled(graphics, font, Integer.toString(i + 1), x + slot / 2,
                        y + slot + visual.numberOffsetY(),
                        selected ? visual.selectedNumberColor() : visual.normalNumberColor(),
                        visual.numberText().shadow(), visual.numberText().scale());
            }
        }
    }

    private static void renderVitals(GuiGraphics graphics, Font font, HudSnapshot.Status status,
                                     long now, HudTheme theme) {
        if (changed(status.health(), lastHealth) || changed(status.magicule(), lastMagicule) || changed(status.aura(), lastAura)) {
            lastHealth = status.health();
            lastMagicule = status.magicule();
            lastAura = status.aura();
            lastVitalChangeAt = now;
        }
        float alpha = 1.0F;

        HudTheme.Status style = theme.status();
        HudTheme.StatusVisual visual = theme.presentation().status();
        HudTheme.Palette palette = theme.palette();
        int x = style.x();
        int y = style.y();
        int width = style.width();
        float hp = percent(status.health(), status.maxHealth());
        float magic = percent(status.magicule(), status.maxMagicule());
        float aura = percent(status.aura(), status.maxAura());
        int healthColor = hp > visual.highThreshold() ? visual.healthHighColor()
                : hp > visual.midThreshold() ? visual.healthMidColor() : visual.healthLowColor();

        fillChamferedBar(graphics, x, y, width, style.healthHeight(), color(visual.healthTrackColor(), alpha));
        int healthInset = visual.healthInset();
        int healthWidth = Math.round((width - healthInset * 2) * hp);
        fillHorizontalGradientChamfered(graphics, x + healthInset, y + healthInset, healthWidth,
                style.healthHeight() - healthInset * 2,
                color(darken(healthColor, visual.healthGradientDarken()), alpha), color(healthColor, alpha));
        graphics.fill(x + visual.healthHighlightInset(), y + 1,
                x + width - visual.healthHighlightInset(), y + 2, color(visual.healthHighlightColor(), alpha));
        graphics.fill(x + width / 2, y + 1, x + width / 2 + 1,
                y + style.healthHeight() - 1, color(visual.centerDividerColor(), alpha));

        int lowerY = y + style.resourceOffsetY();
        int leftW = style.magiculeWidth();
        int rightW = style.auraWidth();
        int resourceGap = style.resourceGap();
        int resourceBaseInset = visual.healthHighlightInset();
        fillChamferedBar(graphics, x + resourceBaseInset, lowerY, leftW,
                style.resourceHeight(), color(palette.track(), alpha));
        fillChamferedBar(graphics, x + resourceBaseInset + leftW + resourceGap, lowerY,
                rightW, style.resourceHeight(), color(palette.track(), alpha));
        int resourceInset = visual.resourceInset();
        int magicWidth = Math.round((leftW - resourceInset * 2) * magic);
        int magicX = style.resourcesDepleteInward()
                ? x + resourceBaseInset + resourceInset + (leftW - resourceInset * 2 - magicWidth)
                : x + resourceBaseInset + resourceInset;
        int resourceFillHeight = Math.max(1, style.resourceHeight() - resourceInset * 2);
        fillHorizontalGradientChamfered(graphics, magicX, lowerY + resourceInset, magicWidth, resourceFillHeight,
                color(palette.accentDark(), alpha), color(palette.magicule(), alpha));
        int auraX = x + resourceBaseInset + leftW + resourceGap;
        int auraWidth = Math.round((rightW - resourceInset * 2) * aura);
        int auraFillX = style.resourcesDepleteInward()
                ? auraX + resourceInset
                : auraX + resourceInset + (rightW - resourceInset * 2 - auraWidth);
        fillHorizontalGradientChamfered(graphics, auraFillX, lowerY + resourceInset, auraWidth, resourceFillHeight,
                color(darken(palette.aura(), visual.auraDarken()), alpha), color(palette.aura(), alpha));

        int healthFrameX = x + style.healthFrameOffsetX();
        int healthFrameY = y + style.healthFrameOffsetY();
        int healthFrameWidth = width + style.healthFrameExtraWidth();
        int healthFrameHeight = style.healthFrameHeight();
        HudTheme.Texture healthTexture = style.healthFrame();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(
                healthTexture.location(),
                healthFrameX,
                healthFrameY,
                healthFrameWidth,
                healthFrameHeight,
                0.0F,
                0.0F,
                healthTexture.width(),
                healthTexture.height(),
                healthTexture.width(),
                healthTexture.height()
        );

        if (style.resourceFrameHeight() > 0) {
            int resourceFrameX = x + style.resourceFrameOffsetX();
            int resourceFrameY = y + style.resourceFrameOffsetY();
            int resourceFrameWidth = width + style.resourceFrameExtraWidth();
            HudTheme.Texture resourceTexture = style.resourceFrame();
            graphics.blit(
                    resourceTexture.location(),
                    resourceFrameX,
                    resourceFrameY,
                    resourceFrameWidth,
                    style.resourceFrameHeight(),
                    0.0F,
                    0.0F,
                    resourceTexture.width(),
                    resourceTexture.height(),
                    resourceTexture.width(),
                    resourceTexture.height()
            );
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        String hpText = compact(status.health()) + " / " + compact(status.maxHealth());
        graphics.drawString(font, "LV " + status.level(), x, y + style.labelOffsetY(),
                color(visual.levelText().color(), alpha), visual.levelText().shadow());
        graphics.drawString(font, hpText, x + width - font.width(hpText), y + style.labelOffsetY(),
                color(visual.healthText().color(), alpha), visual.healthText().shadow());

        renderSaturation(graphics, font, status.saturation(), alpha, now, theme);
        renderDefense(graphics, font, status.defense(), alpha, theme);
    }

    private static void renderSaturation(GuiGraphics graphics, Font font, float saturation,
                                         float alpha, long now, HudTheme theme) {
        HudTheme.Status style = theme.status();
        HudTheme.StatusVisual visual = theme.presentation().status();
        HudTheme.Point point = style.saturation();
        HudTheme.Palette palette = theme.palette();
        int x = point.x();
        int y = point.y();
        boolean empty = saturation <= 0.01F;
        float pulse = empty ? visual.saturationEmptyPulseBase()
                + (float) (visual.saturationEmptyPulseAmplitude()
                * Math.sin(now / visual.saturationEmptyPulseDivisor())) : 1.0F;
        HudTheme.Texture icon = style.saturationIcon();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha * pulse);
        graphics.blit(icon.location(), x, y, visual.saturationIconWidth(), visual.saturationIconHeight(),
                0.0F, 0.0F, icon.width(), icon.height(), icon.width(), icon.height());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int textX = x + visual.saturationTextOffsetX();
        graphics.drawString(font, "SAT " + compact(saturation), textX, y + visual.saturationTextOffsetY(),
                color(visual.saturationText().color(), alpha), visual.saturationText().shadow());
        int filledPips = Math.round(percent(saturation, 20.0F) * visual.saturationPipCount());
        int pipColor = empty ? color(palette.danger(), alpha * pulse) : color(palette.aura(), alpha);
        for (int i = 0; i < visual.saturationPipCount(); i++) {
            int pipX = textX + i * visual.saturationPipSpacing();
            graphics.fill(pipX, y + visual.saturationPipOffsetY(),
                    pipX + visual.saturationPipWidth(),
                    y + visual.saturationPipOffsetY() + visual.saturationPipHeight(),
                    i < filledPips ? pipColor : color(palette.track(), alpha));
        }
        if (empty) {
            graphics.drawString(font, "!", x + visual.saturationEmptyWarningOffsetX(),
                    y + visual.saturationEmptyWarningOffsetY(),
                    color(visual.saturationWarningText().color(), alpha * pulse),
                    visual.saturationWarningText().shadow());
        }
    }

    private static void renderDefense(GuiGraphics graphics, Font font, int defenseValue,
                                      float alpha, HudTheme theme) {
        HudTheme.Status style = theme.status();
        HudTheme.StatusVisual visual = theme.presentation().status();
        HudTheme.Point point = style.defense();
        HudTheme.Texture icon = style.defenseIcon();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(icon.location(), point.x(), point.y(), visual.defenseIconWidth(), visual.defenseIconHeight(),
                0.0F, 0.0F, icon.width(), icon.height(), icon.width(), icon.height());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.drawString(font, "DEF " + defenseValue, point.x() + visual.defenseTextOffsetX(),
                point.y() + visual.defenseTextOffsetY(),
                color(visual.defenseText().color(), alpha), visual.defenseText().shadow());
    }

    private static void fillHorizontalGradient(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                               int leftColor, int rightColor) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        int width = x2 - x1;
        int segments = Math.min(24, width);
        for (int segment = 0; segment < segments; segment++) {
            int start = x1 + Math.round(width * (segment / (float) segments));
            int end = x1 + Math.round(width * ((segment + 1) / (float) segments));
            float t = segments <= 1 ? 1.0F : segment / (float) (segments - 1);
            graphics.fill(start, y1, Math.max(start + 1, end), y2, lerpColor(leftColor, rightColor, t));
        }
    }

    private static void fillChamferedBar(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int row = 0; row < height; row++) {
            int inset = chamferInset(row, height, width);
            if (width - inset * 2 > 0) {
                graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            }
        }
    }

    private static void fillHorizontalGradientChamfered(GuiGraphics graphics, int x, int y, int width, int height,
                                                         int leftColor, int rightColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int row = 0; row < height; row++) {
            int inset = chamferInset(row, height, width);
            fillHorizontalGradient(graphics, x + inset, y + row, x + width - inset, y + row + 1,
                    leftColor, rightColor);
        }
    }

    private static int chamferInset(int row, int height, int width) {
        int edgeDistance = Math.min(row, height - 1 - row);
        int requested = edgeDistance == 0 ? 2 : edgeDistance == 1 ? 1 : 0;
        return Math.min(requested, Math.max(0, (width - 1) / 2));
    }

    private static int lerpColor(int first, int second, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(((first >>> 24) & 0xFF) + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * clamped);
        int r = Math.round(((first >>> 16) & 0xFF) + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * clamped);
        int g = Math.round(((first >>> 8) & 0xFF) + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * clamped);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int argb, float factor) {
        float safe = Math.max(0.0F, Math.min(1.0F, factor));
        int alpha = (argb >>> 24) & 0xFF;
        int red = Math.round(((argb >>> 16) & 0xFF) * safe);
        int green = Math.round(((argb >>> 8) & 0xFF) * safe);
        int blue = Math.round((argb & 0xFF) * safe);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int thickness,
                                          int outlineColor, int insideColor) {
        fillCircle(graphics, cx, cy, radius, outlineColor);
        fillCircle(graphics, cx, cy, Math.max(0, radius - Math.max(1, thickness)), insideColor);
    }

    private static void drawRing(GuiGraphics graphics, int cx, int cy, int radius, int segments, float fill,
                                 int fillColor, int trackColor, int thickness) {
        int filled = Math.round(Math.max(0.0F, Math.min(1.0F, fill)) * segments);
        for (int i = 0; i < segments; i++) {
            double angle = (-Math.PI / 2.0D) + (Math.PI * 2.0D * i / segments);
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            int color = i < filled ? fillColor : trackColor;
            if ((color >>> 24) == 0) {
                continue;
            }
            int half = Math.max(0, thickness - 1);
            graphics.fill(px - half, py - half, px + thickness, py + thickness, color);
        }
    }

    private static void drawDiamond(GuiGraphics graphics, int cx, int cy, int radius, int fill, int border, int thickness) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, fill);
        }
        for (int i = 0; i <= radius; i++) {
            int left = cx - i;
            int right = cx + i;
            int topY = cy - radius + i;
            int bottomY = cy + radius - i;
            graphics.fill(left, topY, left + thickness, topY + thickness, border);
            graphics.fill(right, topY, right + thickness, topY + thickness, border);
            graphics.fill(left, bottomY, left + thickness, bottomY + thickness, border);
            graphics.fill(right, bottomY, right + thickness, bottomY + thickness, border);
        }
    }

    private static void drawRightTriangle(GuiGraphics graphics, int x, int cy, int size, int color) {
        for (int dx = 0; dx < size; dx++) {
            graphics.fill(x + dx, cy - dx, x + dx + 1, cy + dx + 1, color);
        }
    }

    private static void drawObjectiveMarker(GuiGraphics graphics, int cx, int cy, String label, int accent,
                                            HudTheme.ObjectiveVisual style) {
        String normalized = safe(label, "").toLowerCase(Locale.ROOT);
        if (normalized.contains("composition") || normalized.contains("budget")) {
            drawDiamond(graphics, cx, cy, style.markerSize(), style.markerFillColor(), accent, 1);
            drawDiamond(graphics, cx, cy, 1, accent, accent, 1);
        } else if (normalized.contains("bonus") || normalized.contains("time")) {
            drawSparkle(graphics, cx, cy, Math.max(1, style.markerSize() - 1), accent);
        } else if (normalized.contains("enemy") || normalized.contains("defeat") || normalized.contains("kill")) {
            drawRing(graphics, cx, cy, style.markerSize(), style.markerSegments(),
                    1.0F, accent, 0x00000000, 1);
            graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, accent);
        } else {
            drawRightTriangle(graphics, cx - style.markerSize() / 2, cy, style.markerSize(), accent);
        }
    }

    private static void drawSparkle(GuiGraphics graphics, int cx, int cy, int arm, int color) {
        graphics.fill(cx - arm, cy, cx + arm + 1, cy + 1, color);
        graphics.fill(cx, cy - arm, cx + 1, cy + arm + 1, color);
        if (arm > 1) {
            int soft = color(color, 0.55F);
            graphics.fill(cx - 1, cy - 1, cx, cy, soft);
            graphics.fill(cx + 1, cy - 1, cx + 2, cy, soft);
            graphics.fill(cx - 1, cy + 1, cx, cy + 2, soft);
            graphics.fill(cx + 1, cy + 1, cx + 2, cy + 2, soft);
        }
    }

    private static void drawGlowLine(GuiGraphics graphics, int x1, int y, int x2, int color, float alpha) {
        graphics.fill(x1 - 3, y - 2, x2 + 3, y + 3, color(color, alpha * 0.06F));
        graphics.fill(x1 - 1, y - 1, x2 + 1, y + 2, color(color, alpha * 0.15F));
        graphics.fill(x1, y, x2, y + 1, color(color, alpha));
    }

    private static void drawTravelingGlowLine(GuiGraphics graphics, int x1, int x2, int y, float progress,
                                               boolean leftToRight, int lineColor, float alpha) {
        float[] window = travelingLineWindow(progress, leftToRight);
        float start = window[0];
        float end = window[1];
        if (end - start <= 0.001F) {
            return;
        }
        int width = x2 - x1;
        int lineStart = x1 + Math.round(width * start);
        int lineEnd = x1 + Math.round(width * end);
        int midpoint = (lineStart + lineEnd) / 2;
        graphics.fill(lineStart - 3, y - 2, lineEnd + 3, y + 3, color(lineColor, alpha * 0.08F));
        graphics.fill(lineStart - 1, y - 1, lineEnd + 1, y + 2, color(lineColor, alpha * 0.20F));
        fillHorizontalGradient(graphics, lineStart, y, midpoint, y + 1,
                color(lineColor, 0.0F), color(lineColor, alpha));
        fillHorizontalGradient(graphics, midpoint, y, lineEnd, y + 1,
                color(lineColor, alpha), color(lineColor, 0.0F));
    }

    static float[] travelingLineWindow(float progress, boolean leftToRight) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        float segmentLength = 0.34F;
        float start = clamped * (1.0F + segmentLength) - segmentLength;
        float end = start + segmentLength;
        start = Math.max(0.0F, Math.min(1.0F, start));
        end = Math.max(0.0F, Math.min(1.0F, end));
        if (!leftToRight) {
            float mirroredStart = 1.0F - end;
            end = 1.0F - start;
            start = mirroredStart;
        }
        return new float[]{start, end};
    }

    private static void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void drawChamferedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 3, y, x + w - 3, y + 1, color);
        graphics.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
        graphics.fill(x, y + 3, x + 1, y + h - 3, color);
        graphics.fill(x + w - 1, y + 3, x + w, y + h - 3, color);
        graphics.fill(x + 1, y + 1, x + 3, y + 2, color);
        graphics.fill(x + w - 3, y + 1, x + w - 1, y + 2, color);
        graphics.fill(x + 1, y + h - 2, x + 3, y + h - 1, color);
        graphics.fill(x + w - 3, y + h - 2, x + w - 1, y + h - 1, color);
    }

    private static ResourceLocation parseTexture(String value) {
        try {
            return value == null || value.isBlank() ? null : ResourceLocation.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int center, int y, int color, boolean shadow) {
        graphics.drawString(font, text, center - font.width(text) / 2, y, color, shadow);
    }

    private static void drawCenteredScaled(GuiGraphics graphics, Font font, String text, int center, int y,
                                           int color, boolean shadow, float scale) {
        float safeScale = Math.max(0.25F, scale);
        float width = font.width(text) * safeScale;
        graphics.pose().pushPose();
        graphics.pose().translate(center - width / 2.0F, y, 0.0D);
        graphics.pose().scale(safeScale, safeScale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawStyled(GuiGraphics graphics, Font font, String text, int x, int y,
                                   HudTheme.TextStyle style) {
        if (style.scale() == 1.0F) {
            graphics.drawString(font, text, x, y, style.color(), style.shadow());
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0D);
        graphics.pose().scale(style.scale(), style.scale(), 1.0F);
        graphics.drawString(font, text, 0, 0, style.color(), style.shadow());
        graphics.pose().popPose();
    }

    private static String trim(Font font, String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "…";
        String clipped = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis)));
        return clipped + ellipsis;
    }

    private static String trimScaled(Font font, String value, int maxWidth, float scale) {
        return trim(font, value, Math.max(1, Math.round(maxWidth / Math.max(0.25F, scale))));
    }

    private static float percent(double value, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0.0D) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) (value / max)));
    }

    private static boolean changed(double current, double previous) {
        return !Double.isFinite(previous) || Math.abs(current - previous) > 0.01D;
    }

    private static int color(int argb, float alpha) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(baseAlpha * Math.max(0.0F, Math.min(1.0F, alpha)))));
        return (argb & 0x00FFFFFF) | (scaled << 24);
    }

    private static int categoryColor(String category, HudTheme.AnnouncementVisual style) {
        if (category == null) {
            return style.commonColor();
        }
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "ultimate" -> style.ultimateColor();
            case "unique" -> style.uniqueColor();
            case "resistance" -> style.resistanceColor();
            case "intrinsic" -> style.intrinsicColor();
            case "extra" -> style.extraColor();
            default -> style.commonColor();
        };
    }

    private static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0D) return format(value / 1_000_000_000.0D) + "B";
        if (abs >= 1_000_000.0D) return format(value / 1_000_000.0D) + "M";
        if (abs >= 1_000.0D) return format(value / 1_000.0D) + "K";
        if (Math.abs(value - Math.rint(value)) < 0.001D) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static List<HudSnapshot.ObjectiveLine> objectiveLines(HudSnapshot.Objective objective) {
        if (!objective.active()) {
            return List.of();
        }
        List<HudSnapshot.ObjectiveLine> result = new ArrayList<>();
        if (!objective.lines().isEmpty()) {
            result.addAll(objective.lines());
        } else {
            result.add(new HudSnapshot.ObjectiveLine(
                    objective.objectiveLabel(),
                    objective.objectiveProgress(),
                    objective.objectiveTarget(),
                    "cyan"
            ));
            String objectiveId = safe(objective.objectiveId(), "NONE").toUpperCase(Locale.ROOT);
            switch (objectiveId) {
                case "CLEAR_ALL" -> result.add(new HudSnapshot.ObjectiveLine(
                        "Enemies Left", objective.enemiesAlive(), -1, "cyan"));
                case "KILL_ELITE" -> result.add(new HudSnapshot.ObjectiveLine(
                        "Targets Left",
                        Math.max(0, objective.objectiveTarget() - objective.objectiveProgress()),
                        -1,
                        "cyan"
                ));
                case "SURVIVE_TIMER" -> result.add(new HudSnapshot.ObjectiveLine(
                        "Seconds Remaining", Math.max(0, (objective.remainingTicks() + 19) / 20), -1, "cyan"));
                case "ACTIVATE_RUNES" -> result.add(new HudSnapshot.ObjectiveLine(
                        "Runes Left",
                        Math.max(0, objective.objectiveTarget() - objective.objectiveProgress()),
                        -1,
                        "cyan"
                ));
                default -> {
                    if (objective.objectiveTarget() > 0) {
                        result.add(new HudSnapshot.ObjectiveLine(
                                "Progress", objective.objectiveProgress(), objective.objectiveTarget(), "cyan"));
                    }
                }
            }
        }
        if (result.size() <= MAX_VISIBLE_OBJECTIVE_LINES) {
            return List.copyOf(result);
        }
        int retained = MAX_VISIBLE_OBJECTIVE_LINES - 1;
        List<HudSnapshot.ObjectiveLine> visible = new ArrayList<>(result.subList(0, retained));
        visible.add(new HudSnapshot.ObjectiveLine(
                "+" + (result.size() - retained) + " more objectives",
                0,
                0,
                "cyan"
        ));
        return List.copyOf(visible);
    }

    static float objectiveVisibilityAlpha(boolean active, long now, long lastActiveAt) {
        return objectiveVisibilityAlpha(active, now, lastActiveAt, OBJECTIVE_HOLD_MS, OBJECTIVE_FADE_MS);
    }

    static int objectivePanelHeight(HudTheme.ObjectiveVisual style, int lineCount) {
        int visibleLines = Math.max(1, Math.min(style.maxVisibleLines(), lineCount));
        return style.headerHeight()
                + style.primaryHeight()
                + Math.max(0, visibleLines - 1) * style.subrowHeight();
    }

    private static float objectiveVisibilityAlpha(boolean active, long now, long lastActiveAt,
                                                  long holdMs, long fadeMs) {
        if (active) return 1.0F;
        if (lastActiveAt == Long.MIN_VALUE) return 0.0F;
        long elapsed = Math.max(0L, now - lastActiveAt);
        if (elapsed <= holdMs) return 1.0F;
        if (elapsed >= holdMs + fadeMs) return 0.0F;
        return 1.0F - (elapsed - holdMs) / (float) fadeMs;
    }

    static String objectiveDisplayLabel(String label) {
        String value = safe(label, "Objective").trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("composition")) {
            return "Composition";
        }
        if (normalized.startsWith("enemies alive")) {
            return "Enemies Alive";
        }
        return value;
    }

    static String formatRunTime(long elapsedTicks) {
        long totalHundredths = Math.max(0L, elapsedTicks) * 5L;
        long minutes = totalHundredths / 6_000L;
        long seconds = totalHundredths / 100L % 60L;
        long hundredths = totalHundredths % 100L;
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths);
    }

    private static String format(double value) {
        if (Math.abs(value) >= 100.0D) return String.format(Locale.ROOT, "%.0f", value);
        if (Math.abs(value) >= 10.0D) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
