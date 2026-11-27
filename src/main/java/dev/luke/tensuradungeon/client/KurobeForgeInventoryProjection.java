package dev.luke.tensuradungeon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class KurobeForgeInventoryProjection {
    static final TagKey<Item> CORE_SOCKETABLE_WEAPONS = itemTag(
        "tensura_dungeon:core_socketable_weapons"
    );
    static final TagKey<Item> CORE_SOCKETABLE_WEAPON_EXCLUSIONS = itemTag(
        "tensura_dungeon:core_socketable_weapon_exclusions"
    );

    private static final TagKey<Item> GREAT_SWORDS = itemTag("tensura:great_swords");
    private static final TagKey<Item> SPEARS = itemTag("tensura:spears");
    private static final TagKey<Item> KATANAS = itemTag("tensura:katanas");
    private static final TagKey<Item> KODACHIS = itemTag("tensura:kodachis");
    private static final TagKey<Item> ODACHIS = itemTag("tensura:odachis");
    private static final TagKey<Item> TACHIS = itemTag("tensura:tachis");
    private static final TagKey<Item> DAGGERS = itemTag("tensura:daggers");
    private static final TagKey<Item> SHORT_SWORDS = itemTag("tensura:short_swords");

    private KurobeForgeInventoryProjection() {}

    static KurobeForgeView visualPreview() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return KurobeForgeView.visualPreview();

        Inventory inventory = minecraft.player.getInventory();
        List<KurobeForgeView.Weapon> weapons = new ArrayList<>();
        int inventoryEnd = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < inventoryEnd; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!eligible(stack)) continue;

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String fingerprint = fingerprint(stack);
            weapons.add(new KurobeForgeView.Weapon(
                "inventory:" + slot + ":" + itemId + ":" + fingerprint,
                stack.getHoverName().getString(),
                KurobeForgeView.Grade.NORMAL,
                shape(stack),
                -1L,
                -1L,
                0,
                "",
                "",
                0,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                itemId.toString(),
                slot,
                fingerprint,
                false,
                List.of()
            ));
        }

        List<KurobeForgeView.ServiceState> services = List.of(
            KurobeForgeView.ServiceState.preview(KurobeForgeView.Service.PROMOTE),
            KurobeForgeView.ServiceState.preview(KurobeForgeView.Service.CONDUIT),
            KurobeForgeView.ServiceState.preview(KurobeForgeView.Service.SOCKET),
            KurobeForgeView.ServiceState.preview(KurobeForgeView.Service.RECHARGE),
            KurobeForgeView.ServiceState.preview(KurobeForgeView.Service.REMOVE)
        );
        String status = weapons.isEmpty()
            ? "NO TAGGED WEAPONS FOUND IN INVENTORY OR HOTBAR"
            : "LOCAL INVENTORY DISPLAY - SERVER FORGE SNAPSHOT PENDING";
        return new KurobeForgeView(
            weapons,
            services,
            List.of(),
            KurobeForgeView.Grade.NORMAL,
            false,
            KurobeForgeView.Service.PROMOTE,
            true,
            status,
            0L
        );
    }

    static boolean matches(ItemStack stack, KurobeForgeView.Weapon weapon) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.toString().equals(weapon.itemId())
            && fingerprint(stack).equals(weapon.stackFingerprint());
    }

    private static boolean eligible(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && stack.is(CORE_SOCKETABLE_WEAPONS)
            && !stack.is(CORE_SOCKETABLE_WEAPON_EXCLUSIONS);
    }

    private static KurobeForgeView.WeaponShape shape(ItemStack stack) {
        if (stack.is(GREAT_SWORDS)) return KurobeForgeView.WeaponShape.GREATSWORD;
        if (stack.is(SPEARS)) return KurobeForgeView.WeaponShape.SPEAR;
        if (stack.is(KATANAS) || stack.is(KODACHIS) || stack.is(ODACHIS) || stack.is(TACHIS)) {
            return KurobeForgeView.WeaponShape.KATANA;
        }
        if (stack.is(DAGGERS) || stack.is(SHORT_SWORDS)) {
            return KurobeForgeView.WeaponShape.RAPIER;
        }
        return KurobeForgeView.WeaponShape.LONGSWORD;
    }

    private static String fingerprint(ItemStack stack) {
        int hash = 31 * stack.getCount() + stack.getDamageValue();
        hash = 31 * hash + stack.getComponentsPatch().hashCode();
        return Integer.toHexString(hash);
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }
}
