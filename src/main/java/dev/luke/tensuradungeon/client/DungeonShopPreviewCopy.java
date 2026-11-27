package dev.luke.tensuradungeon.client;

import java.util.Locale;

final class DungeonShopPreviewCopy {
    private DungeonShopPreviewCopy() {}

    static String description(DungeonShopView.Offer offer) {
        int restoration = restorationPercent(offer);
        if (restoration > 0 && offer.icon() == DungeonShopView.Icon.HEALING) {
            return "Immediately restores " + restoration
                + "% of maximum health. Healing cannot exceed maximum health.";
        }
        if (restoration > 0 && offer.icon() == DungeonShopView.Icon.MAGICULE) {
            return "Immediately restores " + restoration
                + "% of maximum magicules. Overage can cause magicule poisoning.";
        }
        return offer.description();
    }

    static String disclaimer(DungeonShopView.Offer offer) {
        DepthAccess depth = depthAccess(offer.grade());
        if (depth == null) return offer.requirements();
        return switch (offer.icon()) {
            case BRACELET -> depth.available()
                ? "PROTECTS " + depth.gradeRange() + " DEPTHS  ·  FLOORS 1–" + depth.maximumFloor()
                : "GENESIS PROTECTION DEPTH IS NOT YET AUTHORIZED";
            case WHISTLE -> depth.available()
                ? "EXTRACTS FROM " + depth.gradeRange() + " DEPTHS  ·  FLOORS 1–"
                    + depth.maximumFloor()
                : "GENESIS EXTRACTION DEPTH IS NOT YET AUTHORIZED";
            default -> offer.requirements();
        };
    }

    private static int restorationPercent(DungeonShopView.Offer offer) {
        String identity = (offer.id() + " " + offer.name()).toLowerCase(Locale.ROOT);
        if (identity.contains("greater") && identity.contains("flask")) return 75;
        if (identity.contains("vial")) return 20;
        if (identity.contains("flask")) return 45;

        return switch (offer.grade().toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> 15;
            case "SPECIAL" -> 25;
            case "RARE" -> 40;
            case "UNIQUE" -> 55;
            case "LEGEND" -> 70;
            case "MYTHIC" -> 85;
            case "GENESIS" -> 100;
            default -> 0;
        };
    }

    private static DepthAccess depthAccess(String grade) {
        return switch (grade.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> new DepthAccess("NORMAL", 49, true);
            case "SPECIAL" -> new DepthAccess("NORMAL–SPECIAL", 99, true);
            case "RARE" -> new DepthAccess("NORMAL–RARE", 149, true);
            case "UNIQUE" -> new DepthAccess("NORMAL–UNIQUE", 199, true);
            case "LEGEND" -> new DepthAccess("NORMAL–LEGEND", 249, true);
            case "MYTHIC" -> new DepthAccess("NORMAL–MYTHIC", 300, true);
            case "GENESIS" -> new DepthAccess("GENESIS", 0, false);
            default -> null;
        };
    }

    private record DepthAccess(String gradeRange, int maximumFloor, boolean available) {}
}
