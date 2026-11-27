package com.mineclawd.tensuradungeon.item;

import java.util.List;
import java.util.Optional;

public final class SubordinateSkillScrollCatalog {
    public static final int ACTIVE_SLOT_CAPACITY = 3;
    public static final int PASSIVE_SLOT_CAPACITY = 2;

    private static final List<Definition> APPROVED = List.of(
        definition("abnormal_condition_resistance", "Abnormal Condition Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("abnormal_condition_nullification", "Abnormal Condition Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("corrosion_resistance", "Corrosion Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("corrosion_nullification", "Corrosion Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("magic_resistance", "Magic Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("magic_nullification", "Magic Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("paralysis_resistance", "Paralysis Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("paralysis_nullification", "Paralysis Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("physical_attack_resistance", "Physical Attack Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("physical_attack_nullification", "Physical Attack Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("pierce_resistance", "Pierce Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("pierce_nullification", "Pierce Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("poison_resistance", "Poison Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("poison_nullification", "Poison Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("spiritual_attack_resistance", "Spiritual Attack Resistance", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),
        definition("spiritual_attack_nullification", "Spiritual Attack Nullification", Category.DEFENSIVE_PASSIVE, LoadoutKind.PASSIVE),

        definition("acid_shell", "Acid Shell", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("aerial_blade", "Aerial Blade", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("blizzard", "Blizzard", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("burden", "Burden", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("chain_explosion", "Chain Explosion", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("confusion", "Confusion", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("curse_bind", "Curse Bind", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("dark_cube", "Dark Cube", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("darkness", "Darkness", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("darkness_cannon", "Darkness Cannon", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("dimension_cutter", "Dimension Cutter", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("earth_jail", "Earth Jail", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("earth_spikes", "Earth Spikes", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("electro_blast", "Electro Blast", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("explosion", "Explosion", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("fire_ball", "Fire Ball", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("fire_lance", "Fire Lance", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("fire_storm", "Fire Storm", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("hellfire", "Hellfire", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("ice_breaker", "Ice Breaker", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("lightning_lance", "Lightning Lance", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("magma_surge", "Magma Surge", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("mud_hand", "Mud Hand", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("mud_spears", "Mud Spears", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("shadow_bind", "Shadow Bind", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("solar_flare", "Solar Flare", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("stone_shot", "Stone Shot", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("thunder_lance", "Thunder Lance", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("tornado_blade", "Tornado Blade", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("water_cutter", "Water Cutter", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("water_jail", "Water Jail", Category.COMBAT, LoadoutKind.ACTIVE),
        definition("wind_blade", "Wind Blade", Category.COMBAT, LoadoutKind.ACTIVE),

        definition("antidote", "Antidote", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("barrier", "Barrier", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("full_recovery", "Full Recovery", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("healing", "Healing", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("healing_rain", "Healing Rain", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("magic_barrier", "Magic Barrier", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("protection", "Protection", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("recovery", "Recovery", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("reinforced_barrier", "Reinforced Barrier", Category.SUPPORT, LoadoutKind.ACTIVE),
        definition("wind_protection", "Wind Protection", Category.SUPPORT, LoadoutKind.ACTIVE),

        definition("agility", "Agility", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("chant_annulment", "Chant Annulment", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("danger_sense", "Danger Sense", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("godwolf_sense", "Godwolf Sense", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("magic_aura", "Magic Aura", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("magic_sense", "Magic Sense", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("mana_manipulation", "Mana Manipulation", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("multilayer_barrier", "Multilayer Barrier", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("steel_strength", "Steel Strength", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("strengthen_body", "Strengthen Body", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("thought_acceleration", "Thought Acceleration", Category.UTILITY, LoadoutKind.PASSIVE),
        definition("ultra_instinct", "Ultra-Instinct", Category.UTILITY, LoadoutKind.PASSIVE)
    );

    private SubordinateSkillScrollCatalog() {
    }

    public static List<Definition> approved() {
        return APPROVED;
    }

    public static Optional<Definition> findSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        return APPROVED.stream().filter(definition -> definition.skillId().equals(skillId)).findFirst();
    }

    public static boolean isApprovedSkill(String skillId) {
        return findSkill(skillId).isPresent();
    }

    private static Definition definition(
        String slug,
        String name,
        Category category,
        LoadoutKind loadoutKind
    ) {
        return new Definition(slug, name, category, loadoutKind);
    }

    public enum Category {
        DEFENSIVE_PASSIVE,
        COMBAT,
        SUPPORT,
        UTILITY
    }

    public enum LoadoutKind {
        ACTIVE,
        PASSIVE
    }

    public record Definition(String slug, String name, Category category, LoadoutKind loadoutKind) {
        public Definition {
            if (slug == null || slug.isBlank() || slug.contains(":")) {
                throw new IllegalArgumentException("Invalid subordinate scroll slug");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Invalid subordinate scroll name");
            }
            if (category == null || loadoutKind == null) {
                throw new IllegalArgumentException("Subordinate scroll classification is required");
            }
        }

        public String skillId() {
            return "tensura:" + slug;
        }

        public String itemId() {
            return "subordinate_" + slug + "_scroll";
        }

        public boolean passive() {
            return loadoutKind == LoadoutKind.PASSIVE;
        }
    }
}
