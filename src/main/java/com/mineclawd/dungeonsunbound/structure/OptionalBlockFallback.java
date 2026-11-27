package com.mineclawd.dungeonsunbound.structure;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class OptionalBlockFallback {
    private static final Set<String> OPTIONAL_NAMESPACES = Set.of("domum_ornamentum", "iceandfire", "minecolonies");
    private static final String DEFAULT_PILLAR = "minecraft:stone_bricks";

    private OptionalBlockFallback() {
    }

    public static void apply(HolderGetter<Block> blocks, CompoundTag root) {
        if (root.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
            for (int i = 0; i < palettes.size(); i++) {
                ListTag palette = palettes.getList(i);
                for (int j = 0; j < palette.size(); j++) {
                    CompoundTag entry = palette.getCompound(j);
                    if (isMissing(blocks, entry)) {
                        CompoundTag replacement = fallback(blocks, entry, null);
                        if (replacement != null) {
                            palette.set(j, replacement);
                        }
                    }
                }
            }
            return;
        }
        if (!root.contains("palette", Tag.TAG_LIST) || !root.contains("blocks", Tag.TAG_LIST)) {
            return;
        }
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        boolean[] missing = new boolean[palette.size()];
        boolean any = false;
        for (int i = 0; i < palette.size(); i++) {
            missing[i] = isMissing(blocks, palette.getCompound(i));
            any |= missing[i];
        }
        if (!any) {
            return;
        }
        Map<String, Integer> added = new HashMap<>();
        ListTag blockList = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag block = blockList.getCompound(i);
            int state = block.getInt("state");
            if (state < 0 || state >= missing.length || !missing[state]) {
                continue;
            }
            CompoundTag blockNbt = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null;
            CompoundTag replacement = fallback(blocks, palette.getCompound(state), blockNbt);
            if (replacement == null) {
                continue;
            }
            Integer index = added.get(replacement.toString());
            if (index == null) {
                index = palette.size();
                palette.add(replacement);
                added.put(replacement.toString(), index);
            }
            block.putInt("state", index);
            block.remove("nbt");
        }
    }

    private static boolean isMissing(HolderGetter<Block> blocks, CompoundTag entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.getString("Name"));
        return id != null && OPTIONAL_NAMESPACES.contains(id.getNamespace()) && !exists(blocks, id);
    }

    private static boolean exists(HolderGetter<Block> blocks, ResourceLocation id) {
        return blocks.get(ResourceKey.create(Registries.BLOCK, id)).isPresent();
    }

    private static CompoundTag fallback(HolderGetter<Block> blocks, CompoundTag entry, CompoundTag blockNbt) {
        String name = entry.getString("Name");
        CompoundTag properties = entry.getCompound("Properties");
        String facing = properties.contains("facing") ? properties.getString("facing") : "north";
        return switch (name) {
            case "domum_ornamentum:blockpillar", "domum_ornamentum:blockypillar", "domum_ornamentum:squarepillar" ->
                state(pillarMaterial(blocks, blockNbt));
            case "domum_ornamentum:sand_bricks" -> state("minecraft:cut_sandstone");
            case "domum_ornamentum:sand_stone_bricks" -> state("minecraft:smooth_sandstone");
            case "domum_ornamentum:gray_floating_carpet" -> state("minecraft:gray_carpet");
            case "domum_ornamentum:architectscutter" -> state("minecraft:stonecutter", "facing", facing);
            case "domum_ornamentum:blockbarreldeco_onside", "minecolonies:blockminecoloniesrack" ->
                state("minecraft:barrel", "facing", facing, "open", "false");
            case "domum_ornamentum:blockbarreldeco_standing", "minecolonies:barrel_block" ->
                state("minecraft:barrel", "facing", "up", "open", "false");
            case "iceandfire:dragonscale_black" -> state("minecraft:polished_blackstone");
            case "iceandfire:dread_stone_bricks" -> state("minecraft:deepslate_bricks");
            case "iceandfire:gold_pile" -> state("minecraft:light_weighted_pressure_plate");
            case "iceandfire:dread_stone_stairs" -> stairs(properties);
            case "minecolonies:blockhutblacksmith" -> state("minecraft:smithing_table");
            case "minecolonies:blockhutchickenherder" -> state("minecraft:composter");
            case "minecolonies:blockhutshepherd" -> state("minecraft:loom", "facing", facing);
            default -> null;
        };
    }

    private static String pillarMaterial(HolderGetter<Block> blocks, CompoundTag blockNbt) {
        if (blockNbt != null && blockNbt.contains("textureData", Tag.TAG_COMPOUND)) {
            CompoundTag textures = blockNbt.getCompound("textureData");
            for (String key : textures.getAllKeys()) {
                ResourceLocation material = ResourceLocation.tryParse(textures.getString(key));
                if (material != null && exists(blocks, material)) {
                    return material.toString();
                }
            }
        }
        return DEFAULT_PILLAR;
    }

    private static CompoundTag stairs(CompoundTag properties) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (String key : new String[] {"facing", "half", "shape", "waterlogged"}) {
            if (properties.contains(key)) {
                kept.put(key, properties.getString(key));
            }
        }
        return state("minecraft:deepslate_brick_stairs", kept);
    }

    private static CompoundTag state(String name, String... keyValues) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            properties.put(keyValues[i], keyValues[i + 1]);
        }
        return state(name, properties);
    }

    private static CompoundTag state(String name, Map<String, String> properties) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (!properties.isEmpty()) {
            CompoundTag props = new CompoundTag();
            properties.forEach(props::putString);
            tag.put("Properties", props);
        }
        return tag;
    }
}
