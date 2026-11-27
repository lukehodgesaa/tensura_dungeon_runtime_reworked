package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraMobWardCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraAddonCompat;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DungeonModifierCatalog {
    public static final int CATALOG_VERSION = 3;
    private static final String TEST_ACTIVATED_REASON = "TEST_ACTIVATED_2026-08-07";
    private static final Set<String> TEST_ACTIVATED_PROOF_IDS = Set.of(
        "glass-cannon", "barbed-hide", "shatterproof", "rallying-cry", "dread-aura",
        "boss-affinity"
    );

    private static final Set<DungeonModifierDefinition> ACCEPTED_LIVE_CORE = EnumSet.of(
        DungeonModifierDefinition.ARMORED,
        DungeonModifierDefinition.REGENERATIVE,
        DungeonModifierDefinition.MOLTEN,
        DungeonModifierDefinition.SUMMONER,
        DungeonModifierDefinition.SWARM
    );
    private static final Entry LEGACY_GENERIC_KIN = legacyGenericKinEntry();
    private static final List<Entry> ENTRIES = buildEntries();

    private DungeonModifierCatalog() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry byId(String id) {
        if (id == null) return null;
        if (id.equalsIgnoreCase("kin")) return LEGACY_GENERIC_KIN;
        return entries().stream().filter(entry -> entry.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public static boolean isRollable(String id) {
        Entry entry = byId(id);
        return entry != null && entry.isRollable();
    }

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        for (DungeonModifierDefinition definition : DungeonModifierDefinition.values()) {
            entries.add(coreEntry(definition));
        }
        entries.addAll(approvedAddonEntries());
        entries.addAll(List.of(
            future("magicule-surge", "Magicule Surge", "BOON", 10,
                "Magicule gains increase by 25% and skill-resource recovery increases by 10%."),
            future("cooldown-reduction", "Cooldown Reduction", "BOON", 10,
                "Skill cooldowns are reduced by 15% and ultimate cooldowns by 8%."),
            future("healing-essence", "Healing Essence", "BOON", 10,
                "Direct healing received increases by 20% and potion potency by 15%."),
            approvedEntry("damage-boost", "Damage Boost", "BOON", "MINOR", 8, 0,
                "Outgoing damage increases by 18%, with another 8% against bosses.",
                List.of("Plan around the server-authored prepared-floor preview"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "PROTOTYPE_NOT_ACCEPTED"),
            approvedEntry("barrier", "Barrier", "BOON", "MINOR", 10, 0,
                "Begin each combat room with a barrier equal to 15% of maximum health.",
                List.of("Plan around the server-authored prepared-floor preview"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "PROTOTYPE_NOT_ACCEPTED"),
            future("resourceful", "Resourceful", "REWARD", 14,
                "Currency quantity increases by 20% and crystal quantity by 12%."),
            future("electrified", "Electrified", "HOSTILE", 13,
                "Enemies resist electricity and direct attacks may arc lightning."),
            future("boss-touched", "Boss-Touched", "BOSS", 30,
                "Eligible rooms may use an explicit miniboss or boss override."),
            future("crystal-rich", "Crystal-Rich", "REWARD", 12,
                "Crystal rewards increase by 35% while eligible elite pressure increases.")
        ));
        addWardEntries(entries);
        return List.copyOf(entries);
    }

    private static Entry coreEntry(DungeonModifierDefinition definition) {
        DungeonModifierDesignStatus designStatus = ACCEPTED_LIVE_CORE.contains(definition)
            ? DungeonModifierDesignStatus.LIVE
            : definition == DungeonModifierDefinition.ELITE_VANGUARD
                ? DungeonModifierDesignStatus.FUTURE
                : DungeonModifierDesignStatus.APPROVED_NOT_LIVE;
        DungeonModifierAvailability availability = designStatus == DungeonModifierDesignStatus.LIVE
            ? DungeonModifierAvailability.AVAILABLE
            : DungeonModifierAvailability.NOT_IMPLEMENTED;
        String reason = designStatus == DungeonModifierDesignStatus.LIVE
            ? "AVAILABLE"
            : designStatus == DungeonModifierDesignStatus.FUTURE
                ? "FUTURE_CARD"
                : "PROTOTYPE_NOT_ACCEPTED";
        return new Entry(
            definition.getId(),
            definition.getDisplayName(),
            definition.getAlignment(),
            definition.isPrimary() ? "PRIMARY" : "MINOR",
            definition.getMinFloor(),
            definition.getWeight(),
            definition.getNotes(),
            countersFor(definition),
            definition.getRewardMultiplier(),
            definition.getIconKey(),
            "",
            "",
            designStatus,
            availability,
            reason
        );
    }

    private static List<Entry> approvedAddonEntries() {
        TensuraAddonCompat.HookReport addonHooks = TensuraAddonCompat.inspect();
        List<Entry> entries = new ArrayList<>(List.of(
            approvedEntry("glass-cannon", "Glass Cannon", "HOSTILE", "MINOR", 1, 10,
                "Affected enemies deal 25% more damage and have 20% less maximum HP.",
                List.of("Burst damage", "Defensive mitigation", "Focused targeting"),
                DungeonModifierAvailability.AVAILABLE, TEST_ACTIVATED_REASON),
            approvedEntry("frostbound", "Frostbound", "HOSTILE", "MINOR", 1, 0,
                "Direct enemy attacks apply a brief Slowness I effect.",
                List.of("Cleansing", "Ranged combat", "Mobility"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "TUNING_DURATION_PENDING"),
            approvedEntry("corrosive-remnant", "Corrosive Remnant", "HOSTILE", "MINOR", 1, 0,
                "A dead affected enemy leaves a visible damaging cloud for five seconds.",
                List.of("Move away from the death location", "Ranged combat", "Target control"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "TUNING_HAZARD_VALUES_PENDING"),
            approvedEntry("reinforcement-bell", "Reinforcement Bell", "HOSTILE", "PRIMARY", 1, 0,
                "If the room is not cleared after a server-defined timer, one eligible ordinary reinforcement arrives; at most two arrive per room.",
                List.of("Clear the room quickly", "Find the reinforcement source", "Population control"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "TUNING_TIMER_PENDING"),
            approvedEntry("pursuit", "Pursuit", "HOSTILE", "MINOR", 1, 0,
                "One existing ordinary mob focuses a player and gains a modest speed bonus.",
                List.of("Kill the Pursuer", "Terrain", "Line of sight"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "TUNING_SPEED_PENDING"),
            approvedEntry("mana-siphon", "Mana Siphon", "HOSTILE", "PRIMARY", 1, 0,
                "Enemy attacks drain a capped amount of Magicules from the player.",
                List.of("Avoid hits", "Distance", "Permitted resource recovery"),
                addonHooks.magicule().availability(), addonHooks.magicule().reason()),
            approvedEntry("aerial-pressure", "Aerial Pressure", "ENVIRONMENT", "PRIMARY", 1, 0,
                "Sustained airborne players receive bounded downward force and movement penalties.",
                List.of("Land", "Use terrain", "Manage flight timing"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "TUNING_PRESSURE_VALUES_PENDING"),
            approvedEntry("barbed-hide", "Barbed Hide", "HOSTILE", "MINOR", 1, 10,
                "Affected enemies reflect 4% of final melee damage received to the direct melee attacker.",
                List.of("Ranged attacks", "Armor", "Spacing"),
                DungeonModifierAvailability.AVAILABLE, TEST_ACTIVATED_REASON),
            approvedEntry("rallying-cry", "Rallying Cry", "HOSTILE", "PRIMARY", 1, 10,
                "One bearer gains 50% more maximum HP and grants every eligible encounter mob Regeneration IV for four seconds every twenty seconds.",
                List.of("Find and kill the bearer", "Burst between pulses", "Damage over time"),
                DungeonModifierAvailability.AVAILABLE, TEST_ACTIVATED_REASON),
            approvedEntry("dread-aura", "Dread Aura", "HOSTILE", "PRIMARY", 1, 10,
                "One carrier periodically applies Weakness II to players within four blocks.",
                List.of("Kill the carrier", "Maintain distance", "Cleanse"),
                DungeonModifierAvailability.AVAILABLE, TEST_ACTIVATED_REASON),
            approvedEntry("spell-dampening", "Spell Dampening", "HOSTILE", "MINOR", 1, 0,
                "Affected enemies take 20% less native Tensura magic damage.",
                List.of("Physical attacks", "Alternative damage families", "Verified bypass"),
                spellDampeningAvailability(addonHooks),
                spellDampeningAvailabilityReason(addonHooks)),
            approvedEntry("shatterproof", "Shatterproof", "HOSTILE", "MINOR", 1, 10,
                "A single incoming damage event cannot remove more than 35% of an affected enemy's maximum HP.",
                List.of("Sustained damage", "Multi-hit abilities", "Damage over time"),
                DungeonModifierAvailability.AVAILABLE, TEST_ACTIVATED_REASON),
            approvedEntry("spell-fatigue", "Spell Fatigue", "ENVIRONMENT", "PRIMARY", 1, 0,
                "Repeated qualifying use of one damage family gradually loses effectiveness.",
                List.of("Rotate damage families", "Pause", "Change targets"),
                addonHooks.damageMetadata().availability(), addonHooks.damageMetadata().reason()),
            approvedEntry("moving-target", "Moving Target", "ENVIRONMENT", "MINOR", 1, 0,
                "A nearly stationary player receives a visible telegraph followed by native Tensura Corrosion if they do not move.",
                List.of("Keep moving", "Leave the telegraph", "Use terrain"),
                addonHooks.corrosion().availability(), addonHooks.corrosion().reason()),
            approvedEntry("drifting-sanctuary", "Drifting Sanctuary", "ENVIRONMENT", "PRIMARY", 1, 0,
                "Players outside the sanctuary take 100% more enemy damage; Phase 1 is stationary and Phase 2 may move between safe positions.",
                List.of("Reposition", "Plan around movement", "Use timing"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "SANCTUARY_TELEGRAPH_PENDING"),
            approvedEntry("cast-echo", "Cast Echo", "ENVIRONMENT", "PRIMARY", 1, 0,
                "A verified Tensura spell cast leaves a delayed hazard at the caster's position.",
                List.of("Cast while moving", "Relocate immediately", "Avoid overlap"),
                DungeonModifierAvailability.COMPATIBILITY_DISABLED, "SUCCESSFUL_CAST_HOOK_UNPROVEN"),
            approvedEntry("guarded-front", "Guarded Front", "HOSTILE", "MINOR", 1, 0,
                "Affected enemies take 20% less incoming damage from a known attacker in their frontal arc.",
                List.of("Flank", "Reposition", "Area damage"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "FRONTAL_ARC_THRESHOLD_PENDING"),
            approvedEntry("boss-affinity", "Boss Affinity", "BOSS", "PRIMARY", 1, 10,
                "A boss slot may prefer an explicitly authored boss matching the prepared Kin family or boss theme.",
                List.of("Read the selected boss preview", "Prepare for authored counters", "Use the neutral fallback when no match exists"),
                DungeonModifierAvailability.AVAILABLE, TEST_ACTIVATED_REASON),
            approvedEntry("death-run", "Death Run", "HOSTILE", "MINOR", 1, 0,
                "Eligible ordinary dungeon enemies move five times faster.",
                List.of("Slow effects", "Crowd control", "Defensive spacing"),
                DungeonModifierAvailability.AVAILABLE, "APPROVED_NOT_LIVE"),
            approvedEntry("pursuer-mark", "Pursuer's Mark", "HOSTILE", "PRIMARY", 1, 0,
                "One eligible ordinary enemy is marked to a selected player and gains 3x movement speed and 3x maximum health.",
                List.of("Kill the marked pursuer", "Break line of sight", "Use terrain"),
                DungeonModifierAvailability.AVAILABLE, "APPROVED_NOT_LIVE"),
            approvedEntry("ambush-puppeteer", "Ambush Puppeteer", "HOSTILE", "PRIMARY", 1, 0,
                "Half of eligible ordinary dungeon enemies are invisible but remain attackable.",
                List.of("Audio cues", "Area control", "Detection"),
                DungeonModifierAvailability.AVAILABLE, "APPROVED_NOT_LIVE"),
            approvedEntry("hot-potato", "Hot Potato", "ENVIRONMENT", "PRIMARY", 1, 0,
                "Holding the same non-empty main-hand item for more than five seconds ignites the player.",
                List.of("Cycle hotbar slots", "Empty your hand", "Keep moving"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "FIRE_DURATION_PENDING"),
            approvedEntry("looming-eclipse", "Looming Eclipse", "ENVIRONMENT", "PRIMARY", 1, 0,
                "A room-scoped darkness event periodically reduces visibility without deleting generated blocks or light sources.",
                List.of("Use night vision", "Watch the telegraph", "Stay aware of enemy audio"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "PULSE_TIMING_PENDING"),
            approvedEntry("death-echo", "Death Echo", "HOSTILE", "PRIMARY", 1, 0,
                "A tracked dungeon mob death emits an effect-only echo that can deal 2% of active players' maximum health.",
                List.of("Keep distance", "Use ranged damage", "Avoid clustered deaths"),
                DungeonModifierAvailability.NOT_IMPLEMENTED, "BLAST_SCOPE_PENDING")
        ));
        entries.addAll(familyKinEntries());
        return List.copyOf(entries);
    }

    private static Entry legacyGenericKinEntry() {
        return approvedEntry(
            "kin",
            "Kin",
            "HOSTILE",
            "PRIMARY",
            1,
            10,
            "Legacy generic Kin ID retained only for old prepared-plan diagnostics; new plans use a named family card.",
            List.of("Use the named family card", "Read the prepared enemy list", "Exploit family weaknesses"),
            DungeonModifierAvailability.NOT_IMPLEMENTED,
            "LEGACY_GENERIC_KIN_CARD"
        );
    }

    private static List<Entry> familyKinEntries() {
        return DungeonEnemyKinCatalog.groups().stream()
            .map(group -> approvedEntry(
                DungeonEnemyKinCatalog.modifierIdForGroup(group),
                "Kin: " + DungeonEnemyKinCatalog.displayNameForGroup(group),
                "HOSTILE",
                "PRIMARY",
                1,
                10,
                DungeonEnemyKinCatalog.describeGroup(group),
                List.of("Role coverage", "Target the family", "Exploit family weaknesses"),
                DungeonModifierAvailability.AVAILABLE,
                TEST_ACTIVATED_REASON
            ))
            .toList();
    }

    private static DungeonModifierAvailability spellDampeningAvailability(
        TensuraAddonCompat.HookReport hooks
    ) {
        return hooks.nativeMagicClassification().available() && hooks.damageMetadata().available()
            ? DungeonModifierAvailability.AVAILABLE
            : DungeonModifierAvailability.COMPATIBILITY_DISABLED;
    }

    private static String spellDampeningAvailabilityReason(TensuraAddonCompat.HookReport hooks) {
        if (!hooks.nativeMagicClassification().available()) {
            return hooks.nativeMagicClassification().reason();
        }
        if (!hooks.damageMetadata().available()) {
            return hooks.damageMetadata().reason();
        }
        return "NATIVE_MAGIC_CLASSIFICATION_AND_METADATA_VERIFIED";
    }

    private static void addWardEntries(List<Entry> entries) {
        List<WardCategory> categories = List.of(
            ward("abnormal-condition", "Abnormal Condition", "abnormal_condition", true),
            ward("cold", "Cold", "cold", true),
            ward("corrosion", "Corrosion", "corrosion", true),
            ward("darkness-attack", "Darkness Attack", "darkness_attack", true),
            ward("earth-attack", "Earth Attack", "earth_attack", true),
            ward("electricity", "Electricity", "electricity", true),
            ward("fire-attack", "Fire Attack", "flame_attack", true),
            ward("gravity-attack", "Gravity Attack", "gravity_attack", true),
            ward("heat", "Heat", "heat", true),
            ward("holy-attack", "Holy Attack", "holy_attack", true),
            ward("light-attack", "Light Attack", "light_attack", true),
            ward("magic", "Magic", "magic", true),
            ward("pain", "Pain", "pain", true),
            ward("paralysis", "Paralysis", "paralysis", true),
            ward("physical-attack", "Physical Attack", "physical_attack", true),
            ward("pierce", "Pierce", "pierce", true),
            ward("poison", "Poison", "poison", true),
            ward("spatial-attack", "Spatial Attack", "spatial_attack", true),
            ward("spiritual-attack", "Spiritual Attack", "spiritual_attack", true),
            ward("thermal-fluctuation", "Thermal Fluctuation", "thermal_fluctuation", true),
            ward("water-attack", "Water Attack", "water_attack", true),
            ward("wind-attack", "Wind Attack", "wind_attack", true)
        );
        for (WardCategory category : categories) {
            entries.add(wardEntry(category, false));
            if (category.hasNullification) entries.add(wardEntry(category, true));
        }
    }

    private static Entry wardEntry(WardCategory category, boolean nullification) {
        String kind = nullification ? "nullification" : "resistance";
        String skillId = "tensura:" + category.registryPath + "_" + kind;
        TensuraMobWardCompat.WardHookInspection inspection = TensuraMobWardCompat.inspectSkill(skillId);
        boolean available = inspection.safeToMutate();
        return new Entry(
            "ward-" + kind + "-" + category.slug,
            category.displayName + " " + (nullification ? "Nullification" : "Resistance"),
            "HOSTILE",
            nullification ? "PRIMARY" : "MINOR",
            nullification ? 30 : 10,
            nullification ? 3 : 10,
            nullification
                ? "Enemies nullify matching Tensura damage unless the attack carries sufficient bypass."
                : "Enemies resist matching Tensura damage through the native Tensura skill.",
            List.of("Matching Tensura bypass", "Alternative damage families", "Physical positioning"),
            nullification ? 1.10D : 1.05D,
            "ward-" + kind + "-" + category.slug,
            skillId,
            category.slug,
            DungeonModifierDesignStatus.LIVE,
            available ? DungeonModifierAvailability.AVAILABLE : DungeonModifierAvailability.COMPATIBILITY_DISABLED,
            available ? "NATIVE_WARD_HOOK_VERIFIED" : inspection.reason()
        );
    }

    private static Entry approvedEntry(
        String id,
        String name,
        String alignment,
        String classification,
        int minFloor,
        int weight,
        String description,
        List<String> counters,
        DungeonModifierAvailability availability,
        String reason
    ) {
        boolean testActivated = TEST_ACTIVATED_PROOF_IDS.contains(id)
            || DungeonEnemyKinCatalog.isKinModifierId(id);
        DungeonModifierDesignStatus designStatus = testActivated
            ? DungeonModifierDesignStatus.LIVE
            : DungeonModifierDesignStatus.APPROVED_NOT_LIVE;
        String effectiveReason = testActivated ? TEST_ACTIVATED_REASON : reason;
        return new Entry(
            id, name, alignment, classification, minFloor, weight, description, counters, 1.0D,
            DungeonEnemyKinCatalog.isKinModifierId(id) ? "kin" : id,
            "", "", designStatus, availability, effectiveReason
        );
    }

    private static Entry future(String id, String name, String alignment, int minFloor, String description) {
        return new Entry(
            id, name, alignment, "MINOR", minFloor, 0, description,
            List.of("See the server-authored card before entering"), 1.0D, id, "", "",
            DungeonModifierDesignStatus.FUTURE, DungeonModifierAvailability.NOT_IMPLEMENTED,
            "FUTURE_CARD"
        );
    }

    private static List<String> countersFor(DungeonModifierDefinition definition) {
        return switch (definition) {
            case ARMORED -> List.of("Armor penetration", "Magic damage", "Defense reduction");
            case REGENERATIVE -> List.of("Burst damage", "Healing reduction", "Target focus");
            case MOLTEN -> List.of("Ranged attacks", "Fire resistance", "Cleanse");
            case SUMMONER, RIFTBOUND -> List.of("Kill the marked caller", "Area damage", "Interrupts");
            case SWARM -> List.of("Area damage", "Crowd control", "Mobility");
            case SWIFT -> List.of("Slows", "Roots", "Defensive spacing");
            case BERSERKER -> List.of("Execute effects", "Burst damage", "Crowd control");
            case POISONOUS -> List.of("Poison resistance", "Cleanse", "Hit-and-run tactics");
            case EXPLOSIVE -> List.of("Spacing", "Ranged finishing blows", "Mobility");
            case SHADOWED -> List.of("Detection", "Close formation", "Audio cues");
            case ELITE_VANGUARD -> List.of("Single-target burst", "Elite control", "Resource conservation");
        };
    }

    private static WardCategory ward(String slug, String displayName, String registryPath, boolean hasNullification) {
        return new WardCategory(slug, displayName, registryPath, hasNullification);
    }

    public record Entry(
        String id,
        String displayName,
        String alignment,
        String classification,
        int minFloor,
        int weight,
        String description,
        List<String> counters,
        double rewardMultiplier,
        String iconKey,
        String nativeSkillId,
        String wardCategory,
        DungeonModifierDesignStatus designStatus,
        DungeonModifierAvailability availability,
        String availabilityReason
    ) {
        public Entry {
            id = bounded(id, 64);
            displayName = bounded(displayName, 64);
            alignment = bounded(alignment, 24).toUpperCase(Locale.ROOT);
            classification = bounded(classification, 24).toUpperCase(Locale.ROOT);
            minFloor = Math.max(1, minFloor);
            weight = Math.max(0, weight);
            description = bounded(description, 256);
            counters = counters == null ? List.of() : List.copyOf(counters.stream().limit(6).toList());
            rewardMultiplier = Math.max(0.0D, rewardMultiplier);
            iconKey = bounded(iconKey, 64);
            nativeSkillId = bounded(nativeSkillId, 96);
            wardCategory = bounded(wardCategory, 48);
            designStatus = designStatus == null ? DungeonModifierDesignStatus.FUTURE : designStatus;
            availability = availability == null ? DungeonModifierAvailability.NOT_IMPLEMENTED : availability;
            availabilityReason = bounded(availabilityReason, 64);
        }

        public String status() {
            return availability == DungeonModifierAvailability.COMPATIBILITY_DISABLED
                ? DungeonModifierAvailability.COMPATIBILITY_DISABLED.name()
                : designStatus.name();
        }

        public boolean isRollable() {
            return designStatus == DungeonModifierDesignStatus.LIVE
                && availability == DungeonModifierAvailability.AVAILABLE;
        }
    }

    private record WardCategory(String slug, String displayName, String registryPath, boolean hasNullification) {}

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
