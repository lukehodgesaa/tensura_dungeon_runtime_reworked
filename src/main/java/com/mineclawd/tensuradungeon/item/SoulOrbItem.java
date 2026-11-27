package com.mineclawd.tensuradungeon.item;

import com.mineclawd.tensuradungeon.registry.ModItems;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class SoulOrbItem extends Item {
    private static final String ROOT = "TensuraDungeonSoulOrb";
    private static final String ORB_ID = "OrbId";
    private static final String OWNER_ID = "OwnerId";
    private static final String SESSION_ID = "SessionId";
    private static final String RARITY = "Rarity";
    private static final String BUNDLED_ORB_IDS = "BundledOrbIds";
    public static final int MAX_VISUAL_BUNDLE_SIZE = 64;
    private static final Map<SoulOrbRarity, ResourceLocation> VISUAL_ITEMS = visualItems();

    public SoulOrbItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(UUID orbId, UUID ownerId, String sessionId, SoulOrbRarity rarity) {
        SoulOrbRarity safeRarity = rarity == null ? SoulOrbRarity.COMMON : rarity;
        return createWithVisualItem(
            orbId, ownerId, sessionId, safeRarity,
            resolveVisualItem(
                safeRarity,
                BuiltInRegistries.ITEM::getOptional,
                ModItems.SOUL_ORB::get
            )
        );
    }

    static ItemStack createWithVisualItem(
        UUID orbId,
        UUID ownerId,
        String sessionId,
        SoulOrbRarity rarity,
        Item visualItem
    ) {
        SoulOrbRarity safeRarity = rarity == null ? SoulOrbRarity.COMMON : rarity;
        ItemStack stack = new ItemStack(visualItem);
        CompoundTag payload = new CompoundTag();
        payload.putUUID(ORB_ID, orbId);
        payload.putUUID(OWNER_ID, ownerId);
        payload.putString(SESSION_ID, sessionId == null ? "NONE" : sessionId);
        payload.putString(RARITY, safeRarity.name());
        CompoundTag root = new CompoundTag();
        root.put(ROOT, payload);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.RARITY, safeRarity.itemRarity());
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal(title(safeRarity) + " Soul Orb").withColor(safeRarity.rgb()));
        return stack;
    }

    public static ItemStack displayStack(SoulOrbRarity rarity) {
        return create(new UUID(0L, 0L), new UUID(0L, 0L), "DISPLAY", rarity);
    }

    public static Optional<Identity> identity(ItemStack stack) {
        return identity(stack, BuiltInRegistries.ITEM::getKey);
    }

    static Optional<Identity> identity(ItemStack stack, Function<Item, ResourceLocation> itemIdLookup) {
        if (!isVisualToken(stack, itemIdLookup)) {
            return Optional.empty();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(ROOT)) {
            return Optional.empty();
        }
        CompoundTag payload = root.getCompound(ROOT);
        if (!payload.hasUUID(ORB_ID) || !payload.hasUUID(OWNER_ID)) {
            return Optional.empty();
        }
        UUID primaryOrbId = payload.getUUID(ORB_ID);
        LinkedHashSet<UUID> orbIds = new LinkedHashSet<>();
        orbIds.add(primaryOrbId);
        ListTag bundledIds = payload.getList(BUNDLED_ORB_IDS, Tag.TAG_STRING);
        for (int index = 0; index < bundledIds.size() && orbIds.size() < MAX_VISUAL_BUNDLE_SIZE; index++) {
            try {
                orbIds.add(UUID.fromString(bundledIds.getString(index)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.of(new Identity(
            primaryOrbId,
            payload.getUUID(OWNER_ID),
            payload.getString(SESSION_ID),
            SoulOrbRarity.parse(payload.getString(RARITY)),
            List.copyOf(orbIds)
        ));
    }

    public static Optional<ItemStack> appendVisualBundleMember(ItemStack anchor, UUID orbId) {
        Identity identity = identity(anchor).orElse(null);
        if (identity == null || orbId == null || identity.orbIds().contains(orbId)
            || identity.bundleSize() >= MAX_VISUAL_BUNDLE_SIZE) {
            return Optional.empty();
        }
        ItemStack bundled = anchor.copy();
        CompoundTag root = bundled.get(DataComponents.CUSTOM_DATA).copyTag();
        CompoundTag payload = root.getCompound(ROOT);
        ListTag bundledIds = payload.getList(BUNDLED_ORB_IDS, Tag.TAG_STRING);
        bundledIds.add(StringTag.valueOf(orbId.toString()));
        payload.put(BUNDLED_ORB_IDS, bundledIds);
        root.put(ROOT, payload);
        bundled.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        bundled.set(DataComponents.CUSTOM_NAME, Component.literal(
            title(identity.rarity()) + " Soul Orb \u00d7" + (identity.bundleSize() + 1)
        ).withColor(identity.rarity().rgb()));
        return Optional.of(bundled);
    }

    public static SoulOrbRarity rarity(ItemStack stack) {
        return identity(stack).map(Identity::rarity).orElse(SoulOrbRarity.COMMON);
    }

    public static boolean isVisualToken(ItemStack stack) {
        return isVisualToken(stack, BuiltInRegistries.ITEM::getKey);
    }

    static boolean isVisualToken(ItemStack stack, Function<Item, ResourceLocation> itemIdLookup) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.SOUL_ORB.get())) {
            return true;
        }
        return isVisualItemId(itemIdLookup.apply(stack.getItem()));
    }

    static ResourceLocation visualItemId(SoulOrbRarity rarity) {
        return VISUAL_ITEMS.get(rarity == null ? SoulOrbRarity.COMMON : rarity);
    }

    static boolean isVisualItemId(ResourceLocation itemId) {
        return itemId != null && VISUAL_ITEMS.containsValue(itemId);
    }

    static Item resolveVisualItem(
        SoulOrbRarity rarity,
        Function<ResourceLocation, Optional<Item>> itemLookup,
        Supplier<Item> fallback
    ) {
        return itemLookup.apply(visualItemId(rarity)).orElseGet(fallback);
    }

    private static Map<SoulOrbRarity, ResourceLocation> visualItems() {
        Map<SoulOrbRarity, ResourceLocation> items = new EnumMap<>(SoulOrbRarity.class);
        for (SoulOrbRarity rarity : SoulOrbRarity.values()) {
            items.put(rarity, ResourceLocation.fromNamespaceAndPath(
                "tensura_additions", "soul_orb_" + rarity.name().toLowerCase(java.util.Locale.ROOT)
            ));
        }
        return Map.copyOf(items);
    }

    private static String title(SoulOrbRarity rarity) {
        String lower = rarity.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public record Identity(
        UUID orbId,
        UUID ownerId,
        String sessionId,
        SoulOrbRarity rarity,
        List<UUID> orbIds
    ) {
        public Identity {
            LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
            normalized.add(orbId);
            if (orbIds != null) {
                for (UUID bundledOrbId : orbIds) {
                    if (bundledOrbId != null) normalized.add(bundledOrbId);
                    if (normalized.size() >= MAX_VISUAL_BUNDLE_SIZE) break;
                }
            }
            orbIds = List.copyOf(normalized);
        }

        public Identity(UUID orbId, UUID ownerId, String sessionId, SoulOrbRarity rarity) {
            this(orbId, ownerId, sessionId, rarity, List.of(orbId));
        }

        public int bundleSize() {
            return orbIds.size();
        }
    }
}
