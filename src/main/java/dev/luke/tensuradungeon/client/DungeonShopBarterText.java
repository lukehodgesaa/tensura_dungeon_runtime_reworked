package dev.luke.tensuradungeon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DungeonShopBarterText {
    private DungeonShopBarterText() {}

    static Presentation parse(DungeonShopView.Offer offer) {
        if (offer == null || offer.tab() != DungeonShopView.Tab.BARTER) {
            return Presentation.empty();
        }

        Exchange exchange = parseExchange(offer.description());
        List<Material> missing = parseMaterials(afterPrefix(offer.requirements(), "Missing:"));
        boolean hasMissing = !missing.isEmpty()
            || offer.requirements().trim().toLowerCase(Locale.ROOT).startsWith("missing:");
        boolean ready = offer.requirements().trim().toLowerCase(Locale.ROOT)
            .startsWith("ingredients ready");
        List<Material> visibleMaterials = hasMissing ? missing : exchange.inputs();
        String status = hasMissing
            ? "MISSING MATERIALS"
            : ready
                ? "READY"
                : offer.requirements().isBlank() ? "" : offer.requirements();
        return new Presentation(
            parseComparison(offer.detail()),
            visibleMaterials,
            exchange.output(),
            status,
            hasMissing,
            !ready && !hasMissing && !offer.requirements().isBlank()
        );
    }

    private static Exchange parseExchange(String raw) {
        String normalized = normalize(raw);
        int arrow = normalized.indexOf("->");
        if (arrow < 0) {
            return new Exchange(List.of(), null);
        }
        List<Material> inputs = parseMaterials(normalized.substring(0, arrow));
        Material output = parseMaterial(normalized.substring(arrow + 2));
        return new Exchange(inputs, output);
    }

    private static List<Material> parseMaterials(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) return List.of();
        List<Material> materials = new ArrayList<>();
        for (String part : normalized.split("\\s*(?:\\+|;)\\s*")) {
            Material material = parseMaterial(part);
            if (material != null) materials.add(material);
        }
        return List.copyOf(materials);
    }

    private static Material parseMaterial(String raw) {
        String value = normalize(raw);
        int marker = value.indexOf('x');
        if (marker <= 0 || marker + 1 >= value.length()) return null;
        String quantityText = value.substring(0, marker).trim();
        if (quantityText.isBlank()) return null;
        for (int index = 0; index < quantityText.length(); index++) {
            if (!Character.isDigit(quantityText.charAt(index))) return null;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException exception) {
            return null;
        }
        String name = value.substring(marker + 1).trim();
        return name.isBlank() ? null : new Material(quantity, name);
    }

    private static Comparison parseComparison(String raw) {
        String value = normalize(raw);
        if (value.startsWith("Grade ")) {
            int gradeEnd = value.indexOf(' ', "Grade ".length());
            if (gradeEnd > 0 && gradeEnd + 1 < value.length()) {
                return new Comparison(
                    value.substring(0, gradeEnd).toUpperCase(Locale.ROOT),
                    value.substring(gradeEnd + 1)
                );
            }
        }
        return new Comparison("", value);
    }

    private static String afterPrefix(String value, String prefix) {
        String normalized = normalize(value);
        return normalized.regionMatches(true, 0, prefix, 0, prefix.length())
            ? normalized.substring(prefix.length()).trim()
            : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ')
            .replaceAll("\\s+", " ").trim();
    }

    record Material(int quantity, String name) {}

    record Comparison(String grade, String description) {}

    record Presentation(
        Comparison comparison,
        List<Material> materials,
        Material output,
        String status,
        boolean missingMaterials,
        boolean unavailable
    ) {
        Presentation {
            comparison = comparison == null ? new Comparison("", "") : comparison;
            materials = materials == null ? List.of() : List.copyOf(materials);
            status = status == null ? "" : status;
        }

        static Presentation empty() {
            return new Presentation(new Comparison("", ""), List.of(), null, "", false, false);
        }
    }

    private record Exchange(List<Material> inputs, Material output) {}
}
