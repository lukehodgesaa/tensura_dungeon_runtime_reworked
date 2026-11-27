package com.mineclawd.tensurahudnative.client;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

record HudTheme(
        String id,
        String displayName,
        OverlayPolicy overlayPolicy,
        Palette palette,
        Utility utility,
        Abilities abilities,
        SelectedItem selectedItem,
        Hotbar hotbar,
        Status status,
        Presentation presentation
) {
    record OverlayPolicy(
            boolean objective,
            boolean utility,
            boolean abilities,
            boolean selectedItem,
            boolean vitals,
            boolean announcements,
            boolean evolution,
            boolean replaceVanillaHotbar,
            boolean replaceVanillaVitals,
            boolean replaceVanillaArmor,
            boolean replaceVanillaExperience
    ) {
        static OverlayPolicy fullReplacement() {
            return new OverlayPolicy(
                    true, true, true, true, true, true, true,
                    true, true, true, true
            );
        }

        static OverlayPolicy questOnly() {
            return new OverlayPolicy(
                    true, false, false, false, false, false, false,
                    false, false, false, false
            );
        }
    }

    record Palette(
            int accent,
            int accentSoft,
            int accentDark,
            int success,
            int warning,
            int danger,
            int magicule,
            int aura,
            int text,
            int muted,
            int track,
            int keyBackground,
            int keyBorder,
            int keyText
    ) {
    }

    record Point(int x, int y) {
    }

    record Texture(ResourceLocation location, int width, int height) {
    }

    record Utility(int x, int size, List<Integer> yPositions, Texture frame) {
        Utility {
            yPositions = List.copyOf(yPositions);
        }
    }

    record Abilities(
            List<Point> centers,
            int iconRadius,
            int normalFrameSize,
            int masteryFrameSize,
            int xpChannelInnerRadius,
            int xpChannelOuterRadius,
            int xpFillInnerRadius,
            int xpFillOuterRadius,
            int xpRingSegments,
            int xpChannelInnerColor,
            int xpChannelRecessColor,
            int xpChannelOuterColor,
            int xpTrackColor,
            int xpFillColor,
            int headerY,
            int keyOffsetX,
            int keyOffsetY,
            int keyRightEdge,
            Texture normalFrame,
            Texture slot2NormalFrame,
            Texture ultimateMasteryFrame,
            List<Texture> ultimateMasteryAnimationFrames,
            int ultimateMasteryFrameDurationTicks,
            Texture voidOpalMasteryFrame,
            Texture imperialAmethystMasteryFrame,
            Texture expandedPanelFrame,
            Texture ultimateExpandedPanelFrame,
            Texture presetFrame,
            int expandedPanelHeight,
            int ultimateExpandedPanelHeight,
            int expandedPanelMinWidth,
            int expandedPanelTextInset,
            int ultimateExpandedPanelTextInset
    ) {
        Abilities {
            centers = List.copyOf(centers);
            ultimateMasteryAnimationFrames = List.copyOf(ultimateMasteryAnimationFrames);
        }

        Texture unmasteredFrame(int slotIndex) {
            return slotIndex == 1 ? slot2NormalFrame : normalFrame;
        }

        Texture ultimateMasteryFrame(double renderTicks) {
            if (ultimateMasteryAnimationFrames.isEmpty()) {
                return ultimateMasteryFrame;
            }
            int duration = Math.max(1, ultimateMasteryFrameDurationTicks);
            int frame = Math.floorMod(
                    (int) Math.floor(renderTicks / duration),
                    ultimateMasteryAnimationFrames.size()
            );
            return ultimateMasteryAnimationFrames.get(frame);
        }
    }

    record SelectedItem(
            int x,
            int y,
            int previewWidth,
            int previewHeight,
            float itemScale,
            int frameOffsetX,
            int frameOffsetY,
            int frameWidth,
            int frameHeight,
            int keyOffsetY,
            int labelOffsetX,
            int labelOffsetY,
            int namePlateOffsetX,
            int namePlateOffsetY,
            int namePlateHeight,
            int namePlateMinWidth,
            int namePlateTextInset,
            int nameTextOffsetY,
            Texture frame,
            Texture namePlate
    ) {
    }

    record Hotbar(
            int startX,
            int y,
            int slotSize,
            int gap,
            Texture frame,
            int frameOffsetX,
            int frameOffsetY,
            int frameWidth,
            int frameHeight,
            boolean alwaysVisible
    ) {
        Hotbar(int startX, int y, int slotSize, int gap) {
            this(startX, y, slotSize, gap, null, 0, 0, 0, 0, false);
        }
    }

    record Gradient(int startColor, int endColor) {
    }

    record TextStyle(int color, boolean shadow, float scale) {
    }

    record QuestChrome(
            Texture header,
            Texture primary,
            Texture subrow
    ) {
    }

    record Presentation(
            ObjectiveVisual objective,
            UtilityVisual utility,
            AbilityVisual abilities,
            SelectedItemVisual selectedItem,
            HotbarVisual hotbar,
            StatusVisual status,
            AnnouncementVisual announcement,
            EvolutionVisual evolution
    ) {
    }

    record ObjectiveVisual(
            int x, int y, int width,
            int headerHeight, int primaryHeight, int subrowHeight, int maxVisibleLines,
            int shadowOffsetX, int shadowOffsetY, int shadowColor,
            int backgroundColor, boolean fullBandBackgrounds,
            Gradient headerGradient, Gradient primaryGradient, Gradient subrowGradient,
            int activeColor, int inactiveColor,
            int titleOffsetX, int titleOffsetY, int titleMaxWidth, TextStyle titleText,
            int floorCellCenterX, int timerCellCenterX, int metadataOffsetY,
            TextStyle floorText, TextStyle timerText,
            int iconCenterX, int iconCenterY, int iconRadius, int iconColor, int iconMutedColor,
            int primaryLabelOffsetX, int primaryLabelOffsetY, int primaryValueRightInset,
            int primaryProgressOffsetX, int primaryProgressOffsetY, int primaryProgressRightInset,
            int subrowMarkerOffsetX, int subrowLabelOffsetX, int subrowTextOffsetY,
            int subrowValueRightInset, int markerSize, int markerSegments, int markerFillColor,
            int minimumLabelWidth, int valueGap,
            TextStyle primaryLabelText, TextStyle secondaryLabelText,
            TextStyle primaryValueText, TextStyle secondaryValueText,
            int progressHeight, int progressTrackColor,
            float progressDarkenFactor, int progressHighlightColor, int emptyRuleColor,
            long holdMs, long fadeMs,
            QuestChrome chrome
    ) {
    }

    record UtilityVisual(
            int shadowInset, int shadowColor, Gradient backgroundGradient,
            int innerBorderInset, int innerBorderColor, int recessInset, int recessColor,
            int labelOffsetY, TextStyle labelText,
            int bookColor, int bookSpineColor,
            int diamondOuterFill, int diamondInnerFill,
            int powerColor, int powerTrackColor
    ) {
    }

    record AbilityVisual(
            int shadowExtraRadius, int shadowOffsetY, int shadowColor, int orbBackground,
            float iconRed, float iconGreen, float iconBlue, float iconAlpha,
            int emptyFill, int emptyBorder,
            int cooldownInset, int cooldownColor, int cooldownTextOffsetY, TextStyle cooldownText,
            int presetMaxTextWidth, int presetMinWidth, int presetPadding,
            int presetOffsetFromFirstCenter, int presetHeight, int presetTextOffsetY,
            int presetRecessInsetX, int presetRecessInsetTop, int presetRecessInsetBottom,
            Gradient presetGradient,
            int presetRailColor, TextStyle presetText,
            int panelMaxWidth, int panelGap, int nameOffsetY, TextStyle nameText,
            int modeOffsetY, TextStyle modeText,
            int xpEndCapShadowColor, int xpEndCapHighlightColor
    ) {
    }

    record SelectedItemVisual(
            Gradient recessGradient, int medallionRadius, int medallionColor,
            int medallionCenterOffsetY, int keyTextOffsetX, int itemRenderDepth,
            TextStyle keyText, TextStyle labelText, int namePlateMaxWidth, TextStyle nameText
    ) {
    }

    record HotbarVisual(
            int panelTopPadding, int panelBottomPadding, int panelSidePadding,
            Gradient panelGradient, int panelBorderColor, int panelHighlightColor,
            int panelHighlightInset,
            Gradient normalSlotGradient, Gradient selectedSlotGradient,
            int normalSlotBorderColor, int selectedSlotBorderColor,
            int itemInset, float itemScale, int itemRenderDepth,
            int numberOffsetY, TextStyle numberText, int normalNumberColor, int selectedNumberColor
    ) {
    }

    record StatusVisual(
            int healthTrackColor,
            float highThreshold, float midThreshold,
            int healthHighColor, int healthMidColor, int healthLowColor,
            float healthGradientDarken, int healthInset,
            int healthHighlightInset, int healthHighlightColor, int centerDividerColor,
            int resourceInset, float magiculeDarken, float auraDarken,
            TextStyle levelText, TextStyle healthText,
            int saturationIconWidth, int saturationIconHeight,
            int saturationTextOffsetX, int saturationTextOffsetY, TextStyle saturationText,
            int saturationPipCount, int saturationPipSpacing, int saturationPipWidth,
            int saturationPipHeight, int saturationPipOffsetY,
            int saturationEmptyWarningOffsetX, int saturationEmptyWarningOffsetY,
            TextStyle saturationWarningText,
            float saturationEmptyPulseBase, float saturationEmptyPulseAmplitude,
            double saturationEmptyPulseDivisor,
            int defenseIconWidth, int defenseIconHeight,
            int defenseTextOffsetX, int defenseTextOffsetY, TextStyle defenseText
    ) {
    }

    record AnnouncementVisual(
            int centerX, int y, int minWidth, int maxWidth, int nameWidthPadding, int height,
            Gradient backgroundGradient, float fadeInEnd, float fadeOutStart,
            int lineInset, int topLineY, int bottomLineY,
            int visualOffsetX, int visualOffsetY, int visualSize, float visualAlpha,
            int textLeftOffset, int textRightInset, int messageOffsetY, TextStyle messageText,
            int skillNameWidthPadding, int iconSize, int iconGap,
            int iconPlaqueOffsetY, int iconPlaqueHeight, Gradient iconPlaqueGradient, int iconPlaqueBorder,
            int iconOffsetY, int nameOffsetY, TextStyle nameText,
            int commonColor, int ultimateColor, int uniqueColor,
            int resistanceColor, int intrinsicColor, int extraColor
    ) {
    }

    record EvolutionVisual(
            int panelX, int panelY, int panelWidth, int panelHeight,
            Gradient panelGradient, int panelBorder,
            int evolutionSpriteOffsetX, int evolutionSpriteOffsetY, int evolutionSpriteSize, float evolutionSpriteAlpha,
            int glitchSpriteOffsetX, int glitchSpriteOffsetY, int glitchSpriteSize, float glitchSpriteAlpha,
            int textOffsetX, int textMaxWidth,
            int titleOffsetY, TextStyle titleText, int formOffsetY, TextStyle formText,
            int statusOffsetY, TextStyle statusText, int trackingLabelOffsetY, TextStyle trackingLabelText,
            int trackingNameOffsetY, TextStyle trackingNameText,
            int progressOffsetY, int progressWidth, int progressHeight, int progressTrackColor, int progressFillColor,
            int footerOffsetX, int footerBottomOffset, TextStyle footerText,
            float flashFadeInEnd, float flashFadeOutStart,
            int flashEvolutionX, int flashEvolutionY, int flashEvolutionSize,
            int flashGlitchX, int flashGlitchY, int flashGlitchSize, float flashGlitchAlpha,
            int flashCenterX, int flashTitleY, TextStyle flashTitleText,
            int flashFormY, TextStyle flashFormText
    ) {
    }

    record Status(
            int x,
            int y,
            int width,
            int healthHeight,
            int healthFrameOffsetX,
            int healthFrameOffsetY,
            int healthFrameExtraWidth,
            int healthFrameHeight,
            int resourceOffsetY,
            int resourceHeight,
            int magiculeWidth,
            int resourceGap,
            int auraWidth,
            int resourceFrameOffsetX,
            int resourceFrameOffsetY,
            int resourceFrameExtraWidth,
            int resourceFrameHeight,
            boolean resourcesDepleteInward,
            int labelOffsetY,
            Point saturation,
            Point defense,
            Texture healthFrame,
            Texture resourceFrame,
            Texture saturationIcon,
            Texture defenseIcon
    ) {
    }
}
