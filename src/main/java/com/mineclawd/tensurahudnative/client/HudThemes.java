package com.mineclawd.tensurahudnative.client;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HudThemes {
    public static final String GOTHIC_ID = "gothic";
    public static final String FREY_ID = "frey";
    public static final String RIMURU_ID = "rimuru";
    public static final String BENIMARU_ID = "benimaru";
    public static final String SOUEI_ID = "souei";
    public static final String VANILLA_ID = "vanilla";
    private static final String LEGACY_HARPY_ID = "harpy";
    public static final String GOTHIC_FALLBACK_ID = "gothic_fallback";
    private static final Map<String, HudTheme> THEMES = createThemes();
    private static volatile String runtimeThemeId = "";
    private static volatile String runtimePreviewThemeId = "";

    private HudThemes() {
    }

    public static Set<String> availableThemeIds() {
        return THEMES.keySet();
    }

    public static boolean isAvailable(String id) {
        return THEMES.containsKey(normalize(id));
    }

    public static void setRuntimeTheme(String id) {
        runtimeThemeId = isAvailable(id) ? normalize(id) : "";
    }

    public static void clearRuntimeTheme() {
        runtimeThemeId = "";
    }

    public static void setRuntimePreviewTheme(String id) {
        runtimePreviewThemeId = isAvailable(id) ? normalize(id) : "";
    }

    public static void clearRuntimePreviewTheme() {
        runtimePreviewThemeId = "";
    }

    static HudTheme active(HudSettings settings) {
        String requested;
        if (!runtimePreviewThemeId.isBlank()) {
            requested = runtimePreviewThemeId;
        } else if (settings.testLocalThemeOverride) {
            requested = settings.hudTheme;
        } else if (DungeonRunState.inRun()) {
            requested = runtimeThemeId.isBlank() || VANILLA_ID.equals(runtimeThemeId) ? GOTHIC_ID : runtimeThemeId;
        } else {
            requested = VANILLA_ID;
        }
        return THEMES.getOrDefault(normalize(requested), THEMES.get(VANILLA_ID));
    }

    static HudTheme resolve(String id) {
        return THEMES.getOrDefault(normalize(id), THEMES.get(VANILLA_ID));
    }

    private static Map<String, HudTheme> createThemes() {
        Map<String, HudTheme> themes = new LinkedHashMap<>();
        HudTheme gothic = gothic();
        themes.put(gothic.id(), gothic);
        HudTheme frey = frey(gothic);
        themes.put(frey.id(), frey);
        HudTheme rimuru = rimuru(gothic);
        themes.put(rimuru.id(), rimuru);
        HudTheme benimaru = benimaru(gothic);
        themes.put(benimaru.id(), benimaru);
        HudTheme souei = souei(gothic);
        themes.put(souei.id(), souei);
        HudTheme vanilla = withPolicy(
                benimaru,
                VANILLA_ID,
                "Vanilla + Tensura Default",
                HudTheme.OverlayPolicy.questOnly()
        );
        themes.put(vanilla.id(), vanilla);
        HudTheme gothicFallback = withIdentity(gothic, GOTHIC_FALLBACK_ID, "Temporary Gothic Fallback");
        themes.put(gothicFallback.id(), gothicFallback);
        return Map.copyOf(themes);
    }

    private static HudTheme gothic() {
        String namespace = "tensura_hud_native";
        HudTheme.Texture normalRing = texture(namespace,
                "textures/hud/abilities/skill_ring_frame_unmastered_quiet_cardinal.png", 128, 128);
        HudTheme.Texture ultimateRing = texture(namespace,
                "textures/hud/abilities/skill_ring_frame_mastery_ivory_relic.png", 128, 128);
        HudTheme.Texture voidRing = texture(namespace,
                "textures/hud/abilities/skill_ring_frame_mastery_void_opal.png", 128, 128);
        HudTheme.Texture amethystRing = texture(namespace,
                "textures/hud/abilities/skill_ring_frame_mastery_imperial_amethyst.png", 128, 128);
        HudTheme.Texture expandedPanel = texture(namespace,
                "textures/hud/abilities/expanded_panel_gothic_standard.png", 512, 121);
        HudTheme.Texture ultimateExpandedPanel = texture(namespace,
                "textures/hud/abilities/expanded_panel_gothic_ultimate.png", 512, 140);
        HudTheme.Texture selectedItemFrame = texture(namespace,
                "textures/hud/selected_item/selected_item_gothic_weapon_plaque.png", 120, 86);
        HudTheme.Texture selectedItemNamePlate = texture(namespace,
                "textures/hud/selected_item/selected_item_gothic_nameplate.png", 512, 103);
        HudTheme.Texture healthFrame = texture(namespace,
                "textures/hud/status/status_bar_frame_moon_silver_health.png", 2023, 203);
        HudTheme.Texture resourceFrame = texture(namespace,
                "textures/hud/status/status_bar_frame_moon_silver_resources.png", 1842, 85);
        HudTheme.Texture saturationIcon = texture(namespace,
                "textures/hud/status/status_saturation_droplet_gothic.png", 41, 64);
        HudTheme.Texture defenseIcon = texture(namespace,
                "textures/hud/status/status_defense_shield_gothic.png", 50, 64);
        String questPath = "textures/hud/quest/gothic/";
        HudTheme.QuestChrome questChrome = new HudTheme.QuestChrome(
                texture(namespace, questPath + "quest_ribbon_header.png", 330, 24),
                texture(namespace, questPath + "quest_ribbon_primary.png", 330, 42),
                texture(namespace, questPath + "quest_ribbon_subrow.png", 330, 18)
        );

        return new HudTheme(
                GOTHIC_ID,
                "Moon-Silver Gothic",
                HudTheme.OverlayPolicy.fullReplacement(),
                new HudTheme.Palette(
                        0xFF63DFFF, 0xFFB7F8FF, 0xFF0B6178,
                        0xFF9CFF36, 0xFFFFCD63, 0xFFFF3C32,
                        0xFF7D58E8, 0xFFC26BFF, 0xFFF4FFFF, 0xFFA8C5CC,
                        0xF00A0D10, 0xD71C160D, 0xAA8F7544, 0xFFE8D7AA
                ),
                new HudTheme.Utility(1204, 28, List.of(194, 242, 291), null),
                new HudTheme.Abilities(
                        List.of(
                                new HudTheme.Point(1183, 385),
                                new HudTheme.Point(1163, 439),
                                new HudTheme.Point(1148, 495)
                        ),
                        16, 57, 57,
                        25, 29, 27, 28, 224,
                        0xFF737D87, 0xFF242A31, 0xFF4B555F,
                        0xFF353C45, 0xFF9B5DE5,
                        340, 19, -4, 1268,
                        normalRing, normalRing, ultimateRing, List.of(), 4, voidRing, amethystRing,
                        expandedPanel, ultimateExpandedPanel, null,
                        29, 29, 140, 20, 20
                ),
                new HudTheme.SelectedItem(
                        1047, 540, 40, 40, 1.5F,
                        -40, -12, 120, 86,
                        57, 0, 77,
                        0, 89, 29, 150, 18, 10,
                        selectedItemFrame, selectedItemNamePlate
                ),
                new HudTheme.Hotbar(975, 665, 17, 4),
                new HudTheme.Status(
                        507, 680, 267, 14,
                        -16, -9, 32, 28,
                        14, 4, 130, 2, 128,
                        -12, 20, 24, 0,
                        true,
                        -15,
                        new HudTheme.Point(798, 681),
                        new HudTheme.Point(856, 681),
                        healthFrame, resourceFrame,
                        saturationIcon, defenseIcon
                ),
                new HudTheme.Presentation(
                        new HudTheme.ObjectiveVisual(
                                916, 20, 330,
                                24, 42, 18, 4,
                                2, 3, 0x44000000,
                                0xDC050B12, false,
                                gradient(0xDA101B25, 0xF005090E),
                                gradient(0xE00B1219, 0xF003070B),
                                gradient(0xD0081118, 0xEC020509),
                                0xFF9CFF36, 0xFFA8C5CC,
                                18, 8, 176, text(0xFFB7F8FF, true),
                                239, 294, 11,
                                text(0xFFB7F8FF, true), text(0xFF63DFFF, true),
                                26, 45, 8, 0xFF63DFFF, 0xFFA8C5CC,
                                50, 32, 10,
                                50, 52, 10,
                                14, 24, 5, 10, 1, 8, 0xCC071821,
                                80, 10,
                                text(0xFFF4FFFF, true), text(0xFFADD8E0, true),
                                text(0xFF63DFFF, true), text(0xFFB7F8FF, true),
                                4, 0xD00A1117, 0.48F, 0x38F4FFFF, 0x302E8298,
                                1_500L, 650L,
                                questChrome
                        ),
                        new HudTheme.UtilityVisual(
                                2, 0x22000000, gradient(0xD13B3528, 0xF006090C),
                                2, 0x4AF4FFFF, 5, 0xA0060A0D,
                                5, text(0xFFE8D7AA, true),
                                0xFFE9E1C9, 0xFF806B45,
                                0xDD0B2838, 0xFF102B3B,
                                0xFFF4FFFF, 0x556A5220
                        ),
                        new HudTheme.AbilityVisual(
                                5, 2, 0x4A000000, 0xF003090D,
                                0.96F, 1.0F, 1.0F, 0.98F,
                                0xAA07131A, 0x6672898B,
                                2, 0xBA020304, -4, text(0xFFF4FFFF, true),
                                180, 96, 14, 0, 15, 4,
                                8, 2, 1,
                                gradient(0x8A0A1720, 0xD902070B), 0x8763DFFF,
                                text(0xFFB7F8FF, true),
                                220, 10, 5, text(0xFFF4FFFF, true),
                                17, text(0xFFA7CDD5, false),
                                0xA0000000, 0xFFF4FFFF
                        ),
                        new HudTheme.SelectedItemVisual(
                                gradient(0xE10B1017, 0xF003060A), 7, 0xF005080D, 4, 0, 250,
                                text(0xFFE8D7AA, true), text(0xFFA8C5CC, true, 0.82F),
                                190, text(0xFFF4FFFF, true)
                        ),
                        new HudTheme.HotbarVisual(
                                6, 17, 7, gradient(0xD10D171C, 0xED05090D),
                                0xC7A8C5CC, 0x3DA8C5CC, 2,
                                gradient(0xD1162027, 0xEA070B0E),
                                gradient(0xD2392055, 0xEE10081C),
                                0x7AA8C5CC, 0xFFC26BFF,
                                1, 15.0F / 16.0F, 240,
                                4, text(0xFFA8C5CC, false, 0.72F),
                                0xFFA8C5CC, 0xFFC26BFF
                        ),
                        new HudTheme.StatusVisual(
                                0xFF101614, 0.55F, 0.25F,
                                0xFF59F29A, 0xFFFFCD63, 0xFFFF5A5A,
                                0.62F, 1, 3, 0x45FFFFFF, 0x78E9F7FF,
                                1, 1.0F, 0.48F,
                                text(0xFFB7F8FF, true), text(0xFFF4FFFF, true),
                                12, 18, 15, 1, text(0xFFF4FFFF, true),
                                5, 5, 3, 3, 13, 5, -8,
                                text(0xFFFF3C32, true),
                                0.55F, 0.45F, 115.0D,
                                14, 18, 18, 5, text(0xFFF4FFFF, true)
                        ),
                        new HudTheme.AnnouncementVisual(
                                640, 28, 280, 500, 120, 55,
                                gradient(0xC0081722, 0xE002070A), 0.12F, 0.82F,
                                5, 3, 51, 7, 6, 43, 1.0F,
                                62, 10, 12, text(0xFFB7D7DF, false),
                                112, 17, 7, 27, 21,
                                gradient(0xC5423218, 0xEC07090B), 0xCC9C7C43,
                                29, 33, text(0xFFF4FFFF, true),
                                0xFF63DFFF, 0xFFFF70DA, 0xFF9B7CFF,
                                0xFF74E8FF, 0xFFFFB46A, 0xFFFFC962
                        ),
                        new HudTheme.EvolutionVisual(
                                36, 522, 320, 148, gradient(0xC60A1320, 0xED02060B), 0x8863DFFF,
                                10, 14, 104, 0.92F, 8, 12, 108, 0.32F,
                                124, 180, 16, text(0xFF63DFFF, false),
                                36, text(0xFFF4FFFF, true), 53, text(0xFFFFC962, false),
                                76, text(0xFFA8C5CC, false), 92, text(0xFFB7F8FF, false),
                                112, 174, 4, 0xF00A0D10, 0xFF63DFFF,
                                12, 17, text(0xFFC5D9DE, false),
                                0.18F, 0.78F, 565, 244, 150, 555, 234, 170, 0.42F,
                                640, 420, text(0xFFB7F8FF, true), 438, text(0xFFF4FFFF, true)
                        )
                )
        );
    }

    private static HudTheme frey(HudTheme gothic) {
        String namespace = "tensura_hud_native";
        String root = "textures/hud/harpy/";
        HudTheme.Texture normalRing = texture(namespace,
                root + "abilities/ability_ring_unmastered.png", 128, 128);
        HudTheme.Texture stormOpalRing = texture(namespace,
                root + "abilities/ability_ring_mastery_storm_opal.png", 128, 128);
        HudTheme.Texture skySapphireRing = texture(namespace,
                root + "abilities/ability_ring_mastery_sky_sapphire.png", 128, 128);
        HudTheme.Texture seraphicCrownRing = texture(namespace,
                root + "abilities/ability_ring_mastery_seraphic_crown.png", 128, 128);
        HudTheme.Texture expandedPanel = texture(namespace,
                root + "abilities/expanded_panel_standard.png", 512, 121);
        HudTheme.Texture ultimateExpandedPanel = texture(namespace,
                root + "abilities/expanded_panel_ultimate.png", 512, 140);
        HudTheme.Texture presetPlaque = texture(namespace,
                root + "utility/preset_plaque.png", 512, 80);
        HudTheme.Texture utilityFrame = texture(namespace,
                root + "utility/utility_button_frame.png", 64, 64);
        HudTheme.Texture selectedItemFrame = texture(namespace,
                root + "selected_item/selected_item_socket.png", 120, 86);
        HudTheme.Texture selectedItemNamePlate = texture(namespace,
                root + "selected_item/selected_item_nameplate.png", 512, 100);
        HudTheme.Texture statusFrame = texture(namespace,
                root + "status/status_frame.png", 400, 104);
        HudTheme.Texture saturationIcon = texture(namespace,
                root + "status/saturation_symbol.png", 64, 64);
        HudTheme.Texture defenseIcon = texture(namespace,
                root + "status/defense_symbol.png", 64, 64);
        HudTheme.QuestChrome questChrome = new HudTheme.QuestChrome(
                texture(namespace, root + "quest/quest_ribbon_header.png", 330, 24),
                texture(namespace, root + "quest/quest_ribbon_primary.png", 330, 42),
                texture(namespace, root + "quest/quest_ribbon_subrow.png", 330, 18)
        );

        HudTheme.Palette palette = new HudTheme.Palette(
                0xFF43B9FF, 0xFFC9F1FF, 0xFF0B4D83,
                0xFF9CFF6B, 0xFFFFCA4F, 0xFFFF4B4B,
                0xFF2C8CFF, 0xFFFFC94A, 0xFFF7FBFF, 0xFFB3C8D6,
                0xE908111A, 0xD5091625, 0xFFB78A28, 0xFFF4E7BE
        );

        HudTheme.Presentation gothicVisuals = gothic.presentation();
        HudTheme.Presentation presentation = new HudTheme.Presentation(
                objectiveWithChrome(gothicVisuals.objective(), questChrome),
                new HudTheme.UtilityVisual(
                        2, 0x26000000, gradient(0xD10B1A2B, 0xF0030911),
                        2, 0x65D8F4FF, 5, 0xA0040C16,
                        5, text(0xFFF4E7BE, true),
                        0xFFF5F8FA, 0xFF9BA5AB,
                        0xDD0A3357, 0xFF0B5B96,
                        0xFFF5F8FA, 0x667BA6BF
                ),
                new HudTheme.AbilityVisual(
                        5, 2, 0x4A000000, 0xF0030911,
                        0.96F, 1.0F, 1.0F, 0.98F,
                        0xAA07131A, 0x6672898B,
                        2, 0xBA020304, -4, text(0xFFF7FBFF, true),
                        180, 96, 24, 0, 15, 4,
                        8, 2, 1,
                        gradient(0x8A0A1A2A, 0xD9020811), 0x8743B9FF,
                        text(0xFFC9F1FF, true),
                        220, 5, 4, text(0xFFF7FBFF, true),
                        17, text(0xFF5EDBFF, true),
                        0xA0000000, 0xFFF7FBFF
                ),
                new HudTheme.SelectedItemVisual(
                        gradient(0xE1091728, 0xF0030810), 5, 0xF0040A12, 4, 0, 250,
                        text(0xFFF4E7BE, true), text(0xFFB3C8D6, true, 0.82F),
                        190, text(0xFFF7FBFF, true)
                ),
                new HudTheme.HotbarVisual(
                        6, 17, 7, gradient(0xD10C1B2B, 0xED040A12),
                        0xC7B3C8D6, 0x8062BFFF, 2,
                        gradient(0xD1152330, 0xEA070D13),
                        gradient(0xD2225C9E, 0xEE071A35),
                        0x7AB3C8D6, 0xFF70C8FF,
                        1, 15.0F / 16.0F, 240,
                        4, text(0xFFB3C8D6, false, 0.72F),
                        0xFFB3C8D6, 0xFF7ECBFF
                ),
                new HudTheme.StatusVisual(
                        0xFF0B151B, 0.55F, 0.25F,
                        0xFF59F29A, 0xFFFFCA4F, 0xFFFF5A5A,
                        0.62F, 1, 1, 0x45FFFFFF, 0x78E9F7FF,
                        1, 0.72F, 0.48F,
                        text(0xFFC9F1FF, true), text(0xFFF7FBFF, true),
                        20, 20, 22, 1, text(0xFFF7FBFF, true),
                        5, 5, 3, 3, 13, 5, -8,
                        text(0xFFFF4B4B, true),
                        0.55F, 0.45F, 115.0D,
                        20, 20, 24, 5, text(0xFFF7FBFF, true)
                ),
                gothicVisuals.announcement(),
                gothicVisuals.evolution()
        );

        return new HudTheme(
                FREY_ID,
                "Frey",
                HudTheme.OverlayPolicy.fullReplacement(),
                palette,
                new HudTheme.Utility(1204, 28, List.of(194, 242, 291), utilityFrame),
                new HudTheme.Abilities(
                        gothic.abilities().centers(),
                        16, 57, 57,
                        25, 29, 27, 28, 224,
                        0xFF77838D, 0xFF222B33, 0xFF515D66,
                        0xFF35434E, 0xFF43B9FF,
                        340, 19, -4, 1268,
                        normalRing, normalRing, seraphicCrownRing, List.of(), 4,
                        stormOpalRing, stormOpalRing,
                        expandedPanel, ultimateExpandedPanel, presetPlaque,
                        29, 29, 140, 20, 20
                ),
                new HudTheme.SelectedItem(
                        1051, 549, 34, 39, 1.5F,
                        -28, -10, 90, 65,
                        43, 0, 59,
                        0, 71, 29, 150, 18, 12,
                        selectedItemFrame, selectedItemNamePlate
                ),
                new HudTheme.Hotbar(975, 665, 17, 4),
                new HudTheme.Status(
                        516, 680, 250, 14,
                        -76, -20, 150, 52,
                        18, 4, 122, 8, 122,
                        0, 0, 0, 0,
                        false,
                        -15,
                        new HudTheme.Point(844, 681),
                        new HudTheme.Point(900, 681),
                        statusFrame, statusFrame,
                        saturationIcon, defenseIcon
                ),
                presentation
        );
    }

    private static HudTheme rimuru(HudTheme gothic) {
        String namespace = "tensura_hud_native";
        String root = "textures/hud/rimuru/status/";
        String abilityRoot = "textures/hud/rimuru/abilities/";
        HudTheme.Texture healthFrame = texture(namespace,
                root + "status_health_frame.png", 600, 64);
        HudTheme.Texture resourceFrame = texture(namespace,
                root + "status_resource_frame.png", 600, 32);
        HudTheme.Texture saturationIcon = texture(namespace,
                root + "status_saturation_slime.png", 64, 64);
        HudTheme.Texture defenseIcon = texture(namespace,
                root + "status_defense_slime_shield.png", 64, 64);
        HudTheme.Texture gelOrbitRing = texture(namespace,
                abilityRoot + "ability_ring_unmastered_gel_orbit.png", 128, 128);
        HudTheme.Texture slimeCellRing = texture(namespace,
                abilityRoot + "ability_ring_unmastered_slime_cell.png", 128, 128);
        HudTheme.Texture predatorCurrentMasteryRing = texture(namespace,
                abilityRoot + "ability_ring_mastery_predator_current.png", 128, 128);
        HudTheme.Texture ultimateGelTalonsRing = texture(namespace,
                abilityRoot + "ability_ring_mastery_ultimate_gel_talons.png", 128, 128);
        HudTheme.Texture expandedPanel = texture(namespace,
                abilityRoot + "expanded_panel_standard.png", 512, 121);
        HudTheme.Texture ultimateExpandedPanel = texture(namespace,
                abilityRoot + "expanded_panel_ultimate.png", 512, 140);
        HudTheme.Texture presetPlaque = texture(namespace,
                "textures/hud/rimuru/utility/preset_plaque.png", 512, 80);
        HudTheme.Texture selectedItemFrame = texture(namespace,
                "textures/hud/rimuru/selected_item/selected_item_socket.png", 128, 164);
        HudTheme.Texture selectedItemNamePlate = texture(namespace,
                "textures/hud/rimuru/selected_item/selected_item_nameplate.png", 512, 64);
        HudTheme.QuestChrome questChrome = new HudTheme.QuestChrome(
                texture(namespace, "textures/hud/rimuru/quest/quest_ribbon_header.png", 330, 24),
                texture(namespace, "textures/hud/rimuru/quest/quest_ribbon_primary.png", 330, 42),
                texture(namespace, "textures/hud/rimuru/quest/quest_ribbon_subrow.png", 330, 18)
        );

        HudTheme.Palette palette = new HudTheme.Palette(
                0xFF42D8FF, 0xFFC9F7FF, 0xFF1266A8,
                0xFF48E994, 0xFFFFCB58, 0xFFFF5252,
                0xFF42D8FF, 0xFF9B63FF, 0xFFF5FDFF, 0xFFA9D7E5,
                0xEC071628, 0xD60A1830, 0xFF286E9A, 0xFFD9F8FF
        );

        HudTheme.Presentation gothicVisuals = gothic.presentation();
        HudTheme.Presentation presentation = new HudTheme.Presentation(
                rimuruObjectiveVisual(gothicVisuals.objective(), questChrome),
                gothicVisuals.utility(),
                rimuruAbilityVisual(gothicVisuals.abilities()),
                new HudTheme.SelectedItemVisual(
                        gradient(0xE107182B, 0xF0020B18), 6, 0xF0030D1B, 5, 1, 250,
                        text(0xFFC9F7FF, true, 0.72F), text(0xFFA9D7E5, true, 0.82F),
                        210, text(0xFFF5FDFF, true)
                ),
                gothicVisuals.hotbar(),
                new HudTheme.StatusVisual(
                        0xFF07182A, 0.55F, 0.25F,
                        0xFF48E994, 0xFFFFCB58, 0xFFFF5252,
                        0.58F, 2, 3, 0x55FFFFFF, 0x665DEBFF,
                        1, 0.62F, 0.40F,
                        text(0xFFC9F7FF, true), text(0xFFF5FDFF, true),
                        18, 18, 21, 1, text(0xFFF5FDFF, true),
                        5, 5, 3, 3, 13, 5, -8,
                        text(0xFFFF5252, true),
                        0.55F, 0.45F, 115.0D,
                        18, 18, 22, 5, text(0xFFF5FDFF, true)
                ),
                gothicVisuals.announcement(),
                gothicVisuals.evolution()
        );

        return new HudTheme(
                RIMURU_ID,
                "Rimuru Slime",
                HudTheme.OverlayPolicy.fullReplacement(),
                palette,
                gothic.utility(),
                withAbilityFrames(
                        gothic.abilities(),
                        gelOrbitRing,
                        slimeCellRing,
                        predatorCurrentMasteryRing,
                        ultimateGelTalonsRing,
                        expandedPanel,
                        ultimateExpandedPanel,
                        presetPlaque,
                        46,
                        210,
                        42
                ),
                new HudTheme.SelectedItem(
                        1044, 533, 49, 45, 1.5F,
                        -8, -7, 64, 82,
                        56, 0, 83,
                        0, 95, 24, 150, 16, 8,
                        selectedItemFrame, selectedItemNamePlate
                ),
                gothic.hotbar(),
                new HudTheme.Status(
                        507, 680, 267, 14,
                        -16, -9, 32, 32,
                        19, 4, 130, 2, 128,
                        -4, 17, 8, 10,
                        true,
                        -15,
                        gothic.status().saturation(),
                        gothic.status().defense(),
                        healthFrame, resourceFrame,
                        saturationIcon, defenseIcon
                ),
                presentation
        );
    }

    private static HudTheme benimaru(HudTheme gothic) {
        String namespace = "tensura_hud_native";
        String root = "textures/hud/benimaru/";
        String abilityRoot = root + "abilities/";
        HudTheme.Texture hornedTsuba = texture(namespace,
                abilityRoot + "ability_ring_unmastered_horned_tsuba.png", 128, 128);
        HudTheme.Texture kijinLionseal = texture(namespace,
                abilityRoot + "ability_ring_unmastered_kijin_lionseal.png", 128, 128);
        HudTheme.Texture hornedTsubaMastery = texture(namespace,
                abilityRoot + "ability_ring_mastery_horned_tsuba.png", 128, 128);
        HudTheme.Texture kijinLionsealMastery = texture(namespace,
                abilityRoot + "ability_ring_mastery_kijin_lionseal.png", 128, 128);
        List<HudTheme.Texture> ultimateFrames = List.of(
                texture(namespace, abilityRoot + "ultimate_warlord_ascension_01.png", 128, 128),
                texture(namespace, abilityRoot + "ultimate_warlord_ascension_02.png", 128, 128),
                texture(namespace, abilityRoot + "ultimate_warlord_ascension_03.png", 128, 128),
                texture(namespace, abilityRoot + "ultimate_warlord_ascension_04.png", 128, 128)
        );
        HudTheme.Texture expandedPanel = texture(namespace,
                abilityRoot + "expanded_panel_standard.png", 512, 90);
        HudTheme.Texture ultimateExpandedPanel = texture(namespace,
                abilityRoot + "expanded_panel_ultimate.png", 512, 124);
        HudTheme.Texture presetPlaque = texture(namespace,
                root + "utility/preset_plaque.png", 512, 132);
        HudTheme.Texture selectedItemFrame = texture(namespace,
                root + "selected_item/selected_item_socket.png", 128, 168);
        HudTheme.Texture selectedItemNamePlate = texture(namespace,
                root + "selected_item/selected_item_nameplate.png", 512, 112);
        HudTheme.Texture healthFrame = texture(namespace,
                root + "status/health_frame.png", 512, 108);
        HudTheme.Texture saturationIcon = texture(namespace,
                root + "status/saturation_crimson_feast.png", 64, 64);
        HudTheme.Texture defenseIcon = texture(namespace,
                root + "status/defense_lion_ward.png", 64, 64);
        HudTheme.QuestChrome questChrome = new HudTheme.QuestChrome(
                texture(namespace, root + "quest/quest_ribbon_header.png", 330, 24),
                texture(namespace, root + "quest/quest_ribbon_primary.png", 330, 42),
                texture(namespace, root + "quest/quest_ribbon_subrow.png", 330, 18)
        );

        HudTheme.Palette palette = new HudTheme.Palette(
                0xFFE33C46, 0xFFFFC7A1, 0xFF7E111B,
                0xFF8EFF65, 0xFFFFC857, 0xFFFF3B3B,
                0xFF9D5BE8, 0xFFFF7847, 0xFFFFF2D7, 0xFFC7AE9C,
                0xEE100A0B, 0xDB1A0B0C, 0xFF9A7028, 0xFFFFE5AF
        );

        HudTheme.Presentation gothicVisuals = gothic.presentation();
        HudTheme.Presentation presentation = new HudTheme.Presentation(
                objectiveWithChrome(gothicVisuals.objective(), questChrome),
                gothicVisuals.utility(),
                new HudTheme.AbilityVisual(
                        5, 2, 0x50000000, 0xF0060505,
                        1.0F, 0.98F, 0.96F, 1.0F,
                        0xB00D0909, 0x669A7028,
                        2, 0xC2020202, -4, text(0xFFFFF2D7, true),
                        220, 150, 34,
                        0, 44, 16,
                        10, 8, 8,
                        gradient(0xD0130909, 0xF0040202), 0x9AE33C46,
                        text(0xFFFFE5AF, true),
                        280, 6, 8, text(0xFFFFF2D7, true),
                        18, text(0xFFFFB866, true),
                        0xA0000000, 0xFFFFE5AF
                ),
                new HudTheme.SelectedItemVisual(
                        gradient(0xE014090A, 0xF0040202),
                        7, 0xF0060303, 4, 0, 250,
                        text(0xFFFFE5AF, true, 0.78F),
                        text(0xFFC7AE9C, true, 0.82F),
                        180, text(0xFFFFF2D7, true)
                ),
                new HudTheme.HotbarVisual(
                        6, 17, 7,
                        gradient(0xD1180A0C, 0xEF050203),
                        0xC7B7927A, 0xB8E33C46, 2,
                        gradient(0xD1191110, 0xEA080404),
                        gradient(0xE05C1118, 0xF0190609),
                        0x8AB7927A, 0xFFFF4B55,
                        1, 15.0F / 16.0F, 240,
                        4, text(0xFFC7AE9C, false, 0.72F),
                        0xFFC7AE9C, 0xFFFFD37A
                ),
                new HudTheme.StatusVisual(
                        0xFF100708, 0.55F, 0.25F,
                        0xFFE33C46, 0xFFFFB347, 0xFFFF3038,
                        0.58F, 1, 2, 0x45FFFFFF, 0x66FFD7B0,
                        1, 0.58F, 0.42F,
                        text(0xFFFFE5AF, true), text(0xFFFFF2D7, true),
                        18, 18, 21, 1, text(0xFFFFF2D7, true),
                        5, 5, 3, 3, 13, 5, -8,
                        text(0xFFFF3038, true),
                        0.55F, 0.45F, 115.0D,
                        19, 19, 22, 5, text(0xFFFFF2D7, true)
                ),
                gothicVisuals.announcement(),
                gothicVisuals.evolution()
        );

        return new HudTheme(
                BENIMARU_ID,
                "Benimaru Warlord",
                HudTheme.OverlayPolicy.fullReplacement(),
                palette,
                gothic.utility(),
                new HudTheme.Abilities(
                        gothic.abilities().centers(),
                        16, 57, 61,
                        25, 29, 27, 28, 224,
                        0xFF7C716A, 0xFF2B2221, 0xFF594945,
                        0xFF40302E, 0xFFE33C46,
                        305, 19, -4, 1268,
                        hornedTsuba, kijinLionseal,
                        ultimateFrames.getFirst(), ultimateFrames, 3,
                        kijinLionsealMastery, hornedTsubaMastery,
                        expandedPanel, ultimateExpandedPanel, presetPlaque,
                        36, 48, 190, 24, 26
                ),
                new HudTheme.SelectedItem(
                        1045, 544, 50, 52, 1.5F,
                        -10, -15, 70, 92,
                        65, 107, 61,
                        107, 14, 41, 150, 24, 13,
                        selectedItemFrame, selectedItemNamePlate
                ),
                gothic.hotbar(),
                new HudTheme.Status(
                        532, 651, 217, 16,
                        -42, -15, 83, 63,
                        23, 5, 92, 27, 92,
                        0, 0, 0, 0,
                        true,
                        -19,
                        new HudTheme.Point(820, 654),
                        new HudTheme.Point(886, 654),
                        healthFrame, healthFrame,
                        saturationIcon, defenseIcon
                ),
                presentation
        );
    }

    private static HudTheme souei(HudTheme gothic) {
        String namespace = "tensura_hud_native";
        HudTheme.Texture hotbarFrame = texture(namespace,
                "textures/hud/souei/hotbar/hotbar_border.png", 202, 36);
        HudTheme.Hotbar hotbar = new HudTheme.Hotbar(
                975, 670, 20, 0,
                hotbarFrame, -10, -10, 202, 36, true
        );
        HudTheme.Presentation source = gothic.presentation();
        HudTheme.Presentation presentation = withHotbarPresentation(source,
                new HudTheme.HotbarVisual(
                        0, 0, 0,
                        gradient(0xD10A1524, 0xED040A14),
                        0x00000000, 0x00000000, 0,
                        gradient(0xD11A2A43, 0xEA07111F),
                        gradient(0xE02D819A, 0xEE0B455D),
                        0x00000000, 0x00000000,
                        3, 1.0F, 240,
                        2, text(0xFFB7F8FF, true, 0.72F),
                        0xFFB7F8FF, 0xFFFFFFFF
                ));
        HudTheme.Palette palette = new HudTheme.Palette(
                0xFF4ED9F5, 0xFFC5F7FF, 0xFF123B5D,
                gothic.palette().success(), gothic.palette().warning(), gothic.palette().danger(),
                gothic.palette().magicule(), gothic.palette().aura(),
                0xFFF0FBFF, 0xFFA9C9D8, 0xEE07111B,
                gothic.palette().keyBackground(), 0xFF5AB7D4, 0xFFE4FBFF
        );
        return new HudTheme(
                SOUEI_ID,
                "Souei Shadow Thread",
                HudTheme.OverlayPolicy.fullReplacement(),
                palette,
                gothic.utility(), gothic.abilities(), gothic.selectedItem(), hotbar,
                gothic.status(), presentation
        );
    }

    private static HudTheme withIdentity(HudTheme source, String id, String displayName) {
        return withPolicy(source, id, displayName, source.overlayPolicy());
    }

    private static HudTheme.Presentation withHotbarPresentation(
            HudTheme.Presentation source,
            HudTheme.HotbarVisual hotbar
    ) {
        return new HudTheme.Presentation(
                source.objective(), source.utility(), source.abilities(), source.selectedItem(), hotbar,
                source.status(), source.announcement(), source.evolution()
        );
    }

    private static HudTheme withPolicy(
            HudTheme source, String id, String displayName, HudTheme.OverlayPolicy overlayPolicy
    ) {
        return new HudTheme(
                id, displayName, overlayPolicy, source.palette(), source.utility(), source.abilities(),
                source.selectedItem(), source.hotbar(), source.status(), source.presentation()
        );
    }

    private static HudTheme.Abilities withAbilityFrames(
            HudTheme.Abilities source,
            HudTheme.Texture normalFrame,
            HudTheme.Texture slot2NormalFrame,
            HudTheme.Texture masteryFrame,
            HudTheme.Texture ultimateMasteryFrame,
            HudTheme.Texture expandedPanelFrame,
            HudTheme.Texture ultimateExpandedPanelFrame,
            HudTheme.Texture presetFrame,
            int expandedPanelHeight,
            int expandedPanelMinWidth,
            int expandedPanelTextInset
    ) {
        return new HudTheme.Abilities(
                source.centers(),
                source.iconRadius(),
                source.normalFrameSize(),
                source.masteryFrameSize(),
                source.xpChannelInnerRadius(),
                source.xpChannelOuterRadius(),
                source.xpFillInnerRadius(),
                source.xpFillOuterRadius(),
                source.xpRingSegments(),
                source.xpChannelInnerColor(),
                source.xpChannelRecessColor(),
                source.xpChannelOuterColor(),
                source.xpTrackColor(),
                source.xpFillColor(),
                source.headerY(),
                source.keyOffsetX(),
                source.keyOffsetY(),
                source.keyRightEdge(),
                normalFrame,
                slot2NormalFrame,
                ultimateMasteryFrame,
                List.of(),
                4,
                masteryFrame,
                masteryFrame,
                expandedPanelFrame,
                ultimateExpandedPanelFrame,
                presetFrame,
                expandedPanelHeight,
                expandedPanelHeight,
                expandedPanelMinWidth,
                expandedPanelTextInset,
                expandedPanelTextInset
        );
    }

    private static HudTheme.AbilityVisual rimuruAbilityVisual(HudTheme.AbilityVisual source) {
        return new HudTheme.AbilityVisual(
                source.shadowExtraRadius(), source.shadowOffsetY(), source.shadowColor(), source.orbBackground(),
                source.iconRed(), source.iconGreen(), source.iconBlue(), source.iconAlpha(),
                source.emptyFill(), source.emptyBorder(),
                source.cooldownInset(), source.cooldownColor(),
                source.cooldownTextOffsetY(), source.cooldownText(),
                220, 130, 20,
                source.presetOffsetFromFirstCenter(), 24, 8,
                10, 6, 6,
                gradient(0xD907182A, 0xF0030C18), 0x8742D8FF,
                text(0xFFC9F7FF, true),
                270, 6, 15,
                text(0xFFF5FDFF, true),
                29, text(0xFF42D8FF, true),
                source.xpEndCapShadowColor(), source.xpEndCapHighlightColor()
        );
    }

    private static HudTheme.ObjectiveVisual rimuruObjectiveVisual(
            HudTheme.ObjectiveVisual source, HudTheme.QuestChrome chrome
    ) {
        return new HudTheme.ObjectiveVisual(
                source.x(), source.y(), source.width(),
                source.headerHeight(), source.primaryHeight(), 22, source.maxVisibleLines(),
                source.shadowOffsetX(), source.shadowOffsetY(), source.shadowColor(),
                0xD8071628, false,
                gradient(0xD90A2036, 0xF0030C18),
                gradient(0xE007192C, 0xF0020914),
                gradient(0xD0061627, 0xEC020912),
                0xFF48E994, 0xFFA9D7E5,
                32, source.titleOffsetY(), 176, text(0xFFC9F7FF, true),
                264, 306, 8,
                text(0xFFC9F7FF, true, 0.85F), text(0xFF42D8FF, true, 0.80F),
                source.iconCenterX(), source.iconCenterY(), source.iconRadius(),
                0xFF42D8FF, 0xFFA9D7E5,
                56, 36, 16,
                source.primaryProgressOffsetX(), source.primaryProgressOffsetY(),
                source.primaryProgressRightInset(),
                source.subrowMarkerOffsetX(), source.subrowLabelOffsetX(), 7,
                source.subrowValueRightInset(), source.markerSize(), source.markerSegments(),
                0xCC071B2E, source.minimumLabelWidth(), source.valueGap(),
                text(0xFFF5FDFF, true), text(0xFFC2EAF3, true),
                text(0xFF42D8FF, true), text(0xFFC9F7FF, true),
                source.progressHeight(), 0xD0051424,
                source.progressDarkenFactor(), 0x45F5FDFF, 0x3042D8FF,
                source.holdMs(), source.fadeMs(), chrome
        );
    }

    private static HudTheme.ObjectiveVisual objectiveWithChrome(
            HudTheme.ObjectiveVisual source, HudTheme.QuestChrome chrome
    ) {
        return new HudTheme.ObjectiveVisual(
                source.x(), source.y(), source.width(),
                source.headerHeight(), source.primaryHeight(), source.subrowHeight(), source.maxVisibleLines(),
                source.shadowOffsetX(), source.shadowOffsetY(), source.shadowColor(),
                source.backgroundColor(), false,
                source.headerGradient(), source.primaryGradient(), source.subrowGradient(),
                source.activeColor(), source.inactiveColor(),
                source.titleOffsetX(), source.titleOffsetY(), source.titleMaxWidth(), source.titleText(),
                source.floorCellCenterX(), source.timerCellCenterX(), source.metadataOffsetY(),
                source.floorText(), source.timerText(),
                source.iconCenterX(), source.iconCenterY(), source.iconRadius(),
                source.iconColor(), source.iconMutedColor(),
                source.primaryLabelOffsetX(), source.primaryLabelOffsetY(), source.primaryValueRightInset(),
                source.primaryProgressOffsetX(), source.primaryProgressOffsetY(), source.primaryProgressRightInset(),
                source.subrowMarkerOffsetX(), source.subrowLabelOffsetX(), source.subrowTextOffsetY(),
                source.subrowValueRightInset(), source.markerSize(), source.markerSegments(),
                source.markerFillColor(), source.minimumLabelWidth(), source.valueGap(),
                source.primaryLabelText(), source.secondaryLabelText(),
                source.primaryValueText(), source.secondaryValueText(),
                source.progressHeight(), source.progressTrackColor(),
                source.progressDarkenFactor(), source.progressHighlightColor(), source.emptyRuleColor(),
                source.holdMs(), source.fadeMs(), chrome
        );
    }

    private static HudTheme.Texture texture(String namespace, String path, int width, int height) {
        return new HudTheme.Texture(ResourceLocation.fromNamespaceAndPath(namespace, path), width, height);
    }

    private static HudTheme.Gradient gradient(int start, int end) {
        return new HudTheme.Gradient(start, end);
    }

    private static HudTheme.TextStyle text(int color, boolean shadow) {
        return text(color, shadow, 1.0F);
    }

    private static HudTheme.TextStyle text(int color, boolean shadow, float scale) {
        return new HudTheme.TextStyle(color, shadow, scale);
    }

    private static String normalize(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return LEGACY_HARPY_ID.equals(normalized) ? FREY_ID : normalized;
    }
}
