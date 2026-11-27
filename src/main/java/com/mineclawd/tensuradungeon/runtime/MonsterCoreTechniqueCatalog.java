package com.mineclawd.tensuradungeon.runtime;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MonsterCoreTechniqueCatalog {
    private static final List<Definition> DEFINITIONS = List.of(
        new Definition(
            "stormfang", "STORMFANG CORE", "WIND BLADE", "tensura:wind_blade", 0, 10
        ),
        new Definition(
            "embermaw", "EMBERMAW CORE", "FIRE BALL", "tensura:fire_ball", 0, 20
        ),
        new Definition(
            "frosthorn", "FROSTHORN CORE", "ICE BREAKER", "tensura:ice_breaker", 0, 24
        ),
        new Definition(
            "deepcurrent", "DEEPCURRENT CORE", "WATER CUTTER", "tensura:water_cutter", 0, 12
        )
    );

    private MonsterCoreTechniqueCatalog() {}

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition forEncounter(
        String encounterKey,
        MonsterCoreRecoveryPolicy.EncounterSource source
    ) {
        String haystack = ((encounterKey == null ? "" : encounterKey) + " "
            + (source == null ? "" : source.id())).toLowerCase(Locale.ROOT);
        if (containsAny(haystack, "storm", "fang", "wind", "sky", "thunder")) {
            return find("stormfang");
        }
        if (containsAny(haystack, "ember", "flame", "fire", "magma", "ash")) {
            return find("embermaw");
        }
        if (containsAny(haystack, "frost", "horn", "ice", "snow", "glacier")) {
            return find("frosthorn");
        }
        if (containsAny(haystack, "deep", "current", "water", "tide", "river")) {
            return find("deepcurrent");
        }
        return DEFINITIONS.get(Math.floorMod(
            (encounterKey == null ? "" : encounterKey).hashCode()
                + (source == null ? 0 : source.ordinal()),
            DEFINITIONS.size()
        ));
    }

    public static Definition forLegacy(UUID coreId) {
        int hash = coreId == null ? 0 : coreId.hashCode();
        return DEFINITIONS.get(Math.floorMod(hash, DEFINITIONS.size()));
    }

    public static Definition find(String familyId) {
        if (familyId != null) {
            for (Definition definition : DEFINITIONS) {
                if (definition.familyId().equals(familyId.toLowerCase(Locale.ROOT))) {
                    return definition;
                }
            }
        }
        throw new IllegalArgumentException("Unknown Monster Core family: " + familyId);
    }

    public static boolean isKnown(String familyId, String techniqueId) {
        try {
            Definition definition = find(familyId);
            return definition.techniqueId().equals(techniqueId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String displayName(String familyId, String techniqueId) {
        try {
            Definition definition = find(familyId);
            if (definition.techniqueId().equals(techniqueId)) {
                return definition.displayName() + " / " + definition.techniqueName();
            }
            return definition.displayName();
        } catch (IllegalArgumentException ignored) {
            return "UNRESOLVED MONSTER CORE";
        }
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public record Definition(
        String familyId,
        String displayName,
        String techniqueName,
        String techniqueId,
        int mode,
        int cooldownTicks
    ) {
        public Definition {
            if (familyId == null || familyId.isBlank() || displayName == null || displayName.isBlank()
                || techniqueName == null || techniqueName.isBlank() || techniqueId == null
                || techniqueId.isBlank() || mode < 0 || cooldownTicks < 1) {
                throw new IllegalArgumentException("Monster Core technique definition is incomplete");
            }
            familyId = familyId.toLowerCase(Locale.ROOT);
        }
    }
}
