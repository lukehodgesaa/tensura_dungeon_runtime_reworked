package dev.luke.tensuradungeon.client;

import java.util.Locale;

final class DungeonShopArt {
    private DungeonShopArt() {}

    static String registeredItemId(DungeonShopView.Offer offer) {
        if (!offer.itemId().isBlank() && !hasPackagedItemArt(offer)) {
            return offer.itemId();
        }
        if (offer.icon() != DungeonShopView.Icon.WEAPON) return "";
        String id = offer.id().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "tensura_additions:blacktide_sword",
                 "tensura_additions:gaias_fracture",
                 "tensura_additions:dark_sword",
                 "tensura_additions:brontide_sword",
                 "tensura_additions:celestial_oath",
                 "tensura_additions:venom_sword",
                 "tensura_additions:solar_eclipse_sword",
                 "tensura_additions:violets_sword",
                 "tensura_additions:catastrophe_scythe" -> id;
            default -> "";
        };
    }

    private static boolean hasPackagedItemArt(DungeonShopView.Offer offer) {
        String id = offer.id().toLowerCase(Locale.ROOT);
        if (theme(id) != null || recovery(id) != null) {
            return true;
        }
        return switch (offer.icon()) {
            case SATCHEL -> isActiveSatchelGrade(offer.grade().toLowerCase(Locale.ROOT));
            case BRACELET -> id.contains("resurrection_bracelet");
            case WHISTLE -> id.contains("return_whistle");
            default -> false;
        };
    }

    static WeaponPresentation weaponPresentation(DungeonShopView.Offer offer) {
        String id = registeredItemId(offer);
        return switch (id) {
            case "tensura_additions:blacktide_sword" ->
                new WeaponPresentation("", "", "", 1.00F);
            case "tensura_additions:gaias_fracture" ->
                new WeaponPresentation(
                    "Stone Shot", "60 sec", "tensura.skill.stone_shot", 1.25F
                );
            case "tensura_additions:dark_sword" ->
                new WeaponPresentation(
                    "Icicle Lance", "60 sec", "tensura.skill.icicle_lance", 1.25F
                );
            case "tensura_additions:brontide_sword" ->
                new WeaponPresentation(
                    "Thunder Lance", "60 sec", "tensura.skill.thunder_lance", 1.00F
                );
            case "tensura_additions:celestial_oath" ->
                new WeaponPresentation(
                    "Magic Barrier", "60 sec", "tensura.skill.magic_barrier", 1.25F
                );
            case "tensura_additions:venom_sword" ->
                new WeaponPresentation(
                    "Acid Shell", "60 sec", "tensura.skill.acid_shell", 1.22F
                );
            case "tensura_additions:solar_eclipse_sword" ->
                new WeaponPresentation(
                    "Solar Flare", "60 sec", "tensura.skill.solar_flare", 1.18F
                );
            case "tensura_additions:violets_sword" ->
                new WeaponPresentation(
                    "Hellfire", "60 sec", "tensura.skill.hellfire", 1.18F
                );
            case "tensura_additions:catastrophe_scythe" ->
                new WeaponPresentation(
                    "Chain Explosion", "60 sec", "tensura.skill.chain_explosion", 1.20F
                );
            default -> WeaponPresentation.NONE;
        };
    }

    static Asset resolve(DungeonShopView.Offer offer, Surface surface) {
        Asset theme = theme(offer.id());
        if (theme != null) return theme;

        Asset recovery = recovery(offer.id());
        if (recovery != null) return recovery;

        String grade = offer.grade().toLowerCase(Locale.ROOT);
        if (!isCanonicalGrade(grade)) return null;

        if (offer.icon() == DungeonShopView.Icon.SATCHEL) {
            if (!isActiveSatchelGrade(grade)) return null;
            return texture("items/core_satchel_" + grade + "_item.png", 128, 128);
        }

        String family = switch (offer.icon()) {
            case BRACELET -> "resurrection_bracelet";
            case WHISTLE -> "return_whistle";
            case HEALING -> "healing_potion";
            case MAGICULE -> "magicule_potion";
            default -> "";
        };
        return family.isBlank()
            ? null
            : texture("items/" + family + "_" + grade + "_item.png", 128, 128);
    }

    private static Asset theme(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "hud_theme:gothic", "tensura_dungeon:gothic_hud_theme",
                 "gothic_hud_theme", "daily-gothic-hud" ->
                texture("themes/gothic_hud_shop_preview.png", 236, 102);
            case "hud_theme:frey", "tensura_dungeon:frey_hud_theme",
                 "frey_hud_theme", "weekly-frey-hud" ->
                texture("themes/frey_hud_shop_preview.png", 236, 102);
            default -> null;
        };
    }

    private static Asset recovery(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "tensura_dungeon:healing_vial",
                 "tensura_dungeon:barter_healing_vial",
                 "tensura_additions:healing_vial" ->
                texture("items/healing_vial_item.png", 128, 128);
            case "tensura_dungeon:healing_flask",
                 "tensura_dungeon:barter_healing_flask",
                 "tensura_additions:healing_flask" ->
                texture("items/healing_flask_item.png", 128, 128);
            case "tensura_dungeon:greater_healing_flask",
                 "tensura_dungeon:barter_greater_healing_flask",
                 "tensura_additions:greater_healing_flask" ->
                texture("items/greater_healing_flask_item.png", 128, 128);
            case "tensura_dungeon:magicule_vial",
                 "tensura_dungeon:barter_magicule_vial",
                 "tensura_additions:magicule_vial" ->
                texture("items/magicule_vial_item.png", 128, 128);
            case "tensura_dungeon:magicule_flask",
                 "tensura_dungeon:barter_magicule_flask",
                 "tensura_additions:magicule_flask" ->
                texture("items/magicule_flask_item.png", 128, 128);
            case "tensura_dungeon:greater_magicule_flask",
                 "tensura_dungeon:barter_greater_magicule_flask",
                 "tensura_additions:greater_magicule_flask" ->
                texture("items/greater_magicule_flask_item.png", 128, 128);
            case "tensura_dungeon:field_ration" ->
                texture("items/field_ration_item.png", 128, 128);
            default -> null;
        };
    }

    private static boolean isCanonicalGrade(String grade) {
        return switch (grade) {
            case "normal", "special", "rare", "unique", "legend", "mythic", "genesis" -> true;
            default -> false;
        };
    }

    private static boolean isActiveSatchelGrade(String grade) {
        return switch (grade) {
            case "special", "rare", "unique", "legend", "mythic" -> true;
            default -> false;
        };
    }

    private static Asset texture(String path, int width, int height) {
        return new Asset("textures/gui/shop/" + path, width, height);
    }

    enum Surface {
        ESSENTIAL,
        CARD,
        PREVIEW
    }

    record Asset(String path, int width, int height) {}

    record WeaponPresentation(
        String cast,
        String cooldown,
        String abilityTranslationKey,
        float previewScale
    ) {
        static final WeaponPresentation NONE = new WeaponPresentation("", "", "", 1.0F);

        boolean hasCast() {
            return !cast.isBlank();
        }
    }
}
