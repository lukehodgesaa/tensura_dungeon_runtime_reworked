package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelData;
import com.mineclawd.tensuradungeon.runtime.DungeonBankData;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.KurobeForgeData;
import com.mineclawd.tensuradungeon.runtime.KurobeForgeRules;
import com.mineclawd.tensuradungeon.runtime.KurobeForgeWeaponData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class KurobeForgeSnapshotFactory {
    private static final TagKey<Item> ELIGIBLE_WEAPONS = itemTag(
        "tensura_dungeon:core_socketable_weapons"
    );
    private static final TagKey<Item> EXCLUSIONS = itemTag(
        "tensura_dungeon:core_socketable_weapon_exclusions"
    );
    private static final TagKey<Item> PROMOTION_SPECIAL = itemTag(
        "tensura_dungeon:kurobe_promotion_special"
    );
    private static final TagKey<Item> PROMOTION_RARE = itemTag(
        "tensura_dungeon:kurobe_promotion_rare"
    );
    private static final TagKey<Item> PROMOTION_UNIQUE = itemTag(
        "tensura_dungeon:kurobe_promotion_unique"
    );
    private static final TagKey<Item> PROMOTION_LEGEND = itemTag(
        "tensura_dungeon:kurobe_promotion_legend"
    );
    private static final TagKey<Item> PROMOTION_MYTHIC = itemTag(
        "tensura_dungeon:kurobe_promotion_mythic"
    );
    private static final TagKey<Item> GREAT_SWORDS = itemTag("tensura:great_swords");
    private static final TagKey<Item> SPEARS = itemTag("tensura:spears");
    private static final TagKey<Item> KATANAS = itemTag("tensura:katanas");
    private static final TagKey<Item> KODACHIS = itemTag("tensura:kodachis");
    private static final TagKey<Item> ODACHIS = itemTag("tensura:odachis");
    private static final TagKey<Item> TACHIS = itemTag("tensura:tachis");
    private static final TagKey<Item> DAGGERS = itemTag("tensura:daggers");
    private static final TagKey<Item> SHORT_SWORDS = itemTag("tensura:short_swords");
    private static final ResourceLocation EP = ResourceLocation.fromNamespaceAndPath(
        "tensura", "existence_point"
    );
    private static final ResourceLocation MAX_EP = ResourceLocation.fromNamespaceAndPath(
        "tensura", "max_existence_point"
    );

    private KurobeForgeSnapshotFactory() {}

    public static KurobeForgeSnapshotPayload create(
        ServerPlayer player,
        KurobeForgeSnapshotPayload.Service initialService,
        long requestId
    ) {
        return create(player, initialService, requestId, KurobeForgeSnapshotPayload.Status.ready());
    }

    public static KurobeForgeSnapshotPayload create(
        ServerPlayer player,
        KurobeForgeSnapshotPayload.Service initialService,
        long requestId,
        KurobeForgeSnapshotPayload.Status status
    ) {
        Objects.requireNonNull(player, "Kurobe player");
        Objects.requireNonNull(initialService, "Kurobe initial service");
        UUID playerId = player.getUUID();
        KurobeForgeData forgeData = KurobeForgeData.get(player.serverLevel());
        CoreSatchelData satchelData = CoreSatchelData.get(player.serverLevel());
        CoreSatchelData.SatchelSnapshot satchel = satchelData.snapshot(playerId);
        int highestFloor = DungeonFloorProgressData.get(player.serverLevel())
            .getHighestCompletedFloor(playerId);
        boolean dungeonLocked = dungeonServicesLocked(player);
        List<KurobeForgeSnapshotPayload.CoreOption> cores = projectCores(
            satchel, forgeData, playerId
        );
        ProjectionContext context = new ProjectionContext(
            player,
            forgeData,
            satchel,
            cores,
            highestFloor,
            dungeonLocked
        );
        List<KurobeForgeSnapshotPayload.Weapon> weapons = projectWeapons(context);
        KurobeForgeData.PlayerSnapshot forgeSnapshot = forgeData.snapshot(playerId);
        long revision = Integer.toUnsignedLong(Objects.hash(
            weapons,
            cores,
            walletHash(player),
            certificationMaterialHash(player),
            forgeSnapshot,
            satchel,
            highestFloor,
            dungeonLocked
        ));
        KurobeForgeSnapshotPayload.Status projectedStatus = status;
        if (weapons.isEmpty() && "READY".equals(status.code())) {
            projectedStatus = new KurobeForgeSnapshotPayload.Status(
                "NO_ELIGIBLE_WEAPONS",
                "No tagged weapon with native Tensura Equipment EP was found in inventory or hotbar."
            );
        }
        return new KurobeForgeSnapshotPayload(
            KurobeForgeSnapshotPayload.CURRENT_SCHEMA,
            revision,
            requestId,
            true,
            initialService,
            forgeSnapshot.certifiedGrade(),
            dungeonLocked,
            KurobeForgeRules.noWeaponServices(),
            weapons,
            cores,
            projectedStatus
        );
    }

    public static boolean eligible(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.isStackable()
            && stack.is(ELIGIBLE_WEAPONS) && !stack.is(EXCLUSIONS) && equipmentEp(stack) != null;
    }

    public static boolean nativePromotionEligible(
        ItemStack stack,
        KurobeForgeSnapshotPayload.Grade target
    ) {
        if (stack == null || stack.isEmpty() || target == null) return false;
        return switch (target) {
            case NORMAL -> true;
            case SPECIAL -> stack.is(PROMOTION_SPECIAL);
            case RARE -> stack.is(PROMOTION_RARE);
            case UNIQUE -> stack.is(PROMOTION_UNIQUE);
            case LEGEND -> stack.is(PROMOTION_LEGEND);
            case MYTHIC -> stack.is(PROMOTION_MYTHIC);
            case GENESIS -> false;
        };
    }

    public static EpSnapshot equipmentEp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Object currentValue = component(stack, EP);
        Object maximumValue = component(stack, MAX_EP);
        if (!(currentValue instanceof Number current) || !(maximumValue instanceof Number maximum)) {
            return null;
        }
        double currentEp = current.doubleValue();
        double maximumEp = maximum.doubleValue();
        if (!Double.isFinite(currentEp) || !Double.isFinite(maximumEp) || maximumEp <= 0.0D) {
            return null;
        }
        return new EpSnapshot(boundedLong(Math.max(0.0D, currentEp)), boundedLong(maximumEp));
    }

    public static String weaponId(int slot, ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "inventory:" + slot + ":" + itemId;
    }

    public static String fingerprint(ItemStack stack) {
        int hash = 31 * stack.getCount() + stack.getDamageValue();
        hash = 31 * hash + stack.getComponentsPatch().hashCode();
        return Integer.toUnsignedString(hash, 16);
    }

    public static boolean dungeonServicesLocked(ServerPlayer player) {
        DungeonInstanceAllocationData.Allocation allocation = DungeonInstanceAllocationData
            .get(player.serverLevel())
            .forPlayer(player.getUUID())
            .orElse(null);
        if (allocation == null) return false;
        DungeonInstanceAllocationData.LifecycleState state = allocation.lifecycle().state();
        return state == DungeonInstanceAllocationData.LifecycleState.ACTIVE
            || state == DungeonInstanceAllocationData.LifecycleState.SUSPENDED
            || state == DungeonInstanceAllocationData.LifecycleState.COMPLETING;
    }

    private static List<KurobeForgeSnapshotPayload.Weapon> projectWeapons(ProjectionContext context) {
        List<KurobeForgeSnapshotPayload.Weapon> result = new ArrayList<>();
        Inventory inventory = context.player().getInventory();
        int end = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < end; slot++) {
            ItemStack stack = inventory.getItem(slot);
            EpSnapshot ep = equipmentEp(stack);
            if (ep == null || !eligibleWithoutEpRead(stack)) continue;
            KurobeForgeWeaponData.State state = KurobeForgeWeaponData.read(stack);
            CoreSatchelData.CoreRecord core = state.core();
            boolean socketDataValid = !KurobeForgeWeaponData.hasCorruptSocket(stack);
            boolean bindingValid = bindingValid(context, state, socketDataValid);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            long current = Math.min(ep.current(), ep.maximum());
            long accessible = KurobeForgeRules.isActiveGrade(state.grade())
                ? KurobeForgeRules.accessibleCapacity(state.grade(), state.conduitRank())
                : 0L;
            result.add(new KurobeForgeSnapshotPayload.Weapon(
                weaponId(slot, stack),
                stack.getHoverName().getString(),
                state.grade(),
                shape(stack),
                current,
                ep.maximum(),
                state.conduitRank(),
                core == null ? "" : core.displayName(),
                core == null ? "" : core.coreId().toString(),
                core == null ? 0 : core.tier(),
                core == null ? 0L : core.totalCharge(),
                core == null ? 0L : core.naturalCharge(),
                core == null ? accessible : core.capacity(),
                accessible,
                core == null ? -1L : KurobeForgeRules.burstOutput(
                    state.grade(), state.conduitRank(), core.tier()
                ),
                core == null ? -1L : KurobeForgeRules.flowOutput(
                    state.grade(), state.conduitRank(), core.tier()
                ),
                itemId.toString(),
                slot,
                fingerprint(stack),
                serviceStates(context, stack, state, ep, bindingValid, socketDataValid)
            ));
        }
        return List.copyOf(result);
    }

    private static List<KurobeForgeSnapshotPayload.CoreOption> projectCores(
        CoreSatchelData.SatchelSnapshot satchel,
        KurobeForgeData forgeData,
        UUID playerId
    ) {
        if (!satchel.access().acquired()) return List.of();
        boolean tutorialAvailable = forgeData.tutorialSocketAvailable(playerId);
        return satchel.cores().entrySet().stream()
            .filter(entry -> forgeData.binding(entry.getValue().coreId()) == null)
            .sorted(MapEntryComparator.INSTANCE)
            .map(entry -> {
                CoreSatchelData.CoreRecord core = entry.getValue();
                boolean free = tutorialAvailable && core.tier() == 1;
                return new KurobeForgeSnapshotPayload.CoreOption(
                    core.coreId().toString(),
                    core.displayName(),
                    entry.getKey(),
                    core.tier(),
                    core.totalCharge(),
                    core.naturalCharge(),
                    core.capacity(),
                    free,
                    free ? "FREE TUTORIAL SOCKET" : KurobeForgeRules.currencyLabel(
                        KurobeForgeRules.socketPriceBronze(core.tier())
                    )
                );
            })
            .toList();
    }

    private static List<KurobeForgeSnapshotPayload.ServiceState> serviceStates(
        ProjectionContext context,
        ItemStack stack,
        KurobeForgeWeaponData.State state,
        EpSnapshot ep,
        boolean bindingValid,
        boolean socketDataValid
    ) {
        if (!socketDataValid) {
            return java.util.Arrays.stream(KurobeForgeSnapshotPayload.Service.values())
                .map(service -> KurobeForgeRules.unavailable(
                    service, "CORRUPT SOCKET DATA REQUIRES ADMINISTRATIVE REVIEW"
                ))
                .toList();
        }
        if (!KurobeForgeRules.isActiveGrade(state.grade())) {
            return java.util.Arrays.stream(KurobeForgeSnapshotPayload.Service.values())
                .map(service -> KurobeForgeRules.unavailable(
                    service, "GENESIS / GRADE VII IS RESERVED FOR FUTURE CONTENT"
                ))
                .toList();
        }
        List<KurobeForgeSnapshotPayload.ServiceState> services = new ArrayList<>(5);
        services.add(promoteState(context, stack, state, ep));
        services.add(conduitState(context, state));
        services.add(socketState(context, state));
        services.add(rechargeState(context, state, bindingValid));
        services.add(removeState(context, state, bindingValid));
        return List.copyOf(services);
    }

    private static KurobeForgeSnapshotPayload.ServiceState promoteState(
        ProjectionContext context,
        ItemStack stack,
        KurobeForgeWeaponData.State state,
        EpSnapshot ep
    ) {
        KurobeForgeSnapshotPayload.Grade target = KurobeForgeRules.nextGrade(state.grade());
        if (target == null) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.PROMOTE, "MYTHIC EQUIPMENT GRADE IS COMPLETE"
        );
        if (target == KurobeForgeSnapshotPayload.Grade.GENESIS) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.PROMOTE,
                "GENESIS / GRADE VII IS RESERVED FOR FUTURE CONTENT"
            );
        }
        if (context.highestFloor() < KurobeForgeRules.requiredFloor(target)) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.PROMOTE,
                "COMPLETE FLOOR " + KurobeForgeRules.requiredFloor(target) + " FIRST"
            );
        }
        KurobeForgeSnapshotPayload.Grade certified = context.forgeData()
            .certifiedGrade(context.player().getUUID());
        if (certified.ordinal() < target.ordinal()) {
            if (!materialsRegistered(target)) {
                return KurobeForgeRules.unavailable(
                    KurobeForgeSnapshotPayload.Service.PROMOTE,
                    "REQUIRED NATIVE CERTIFICATION MATERIAL IS NOT REGISTERED"
                );
            }
            return KurobeForgeRules.available(
                KurobeForgeSnapshotPayload.Service.PROMOTE,
                target.name() + " CERTIFICATION  /  "
                    + KurobeForgeRules.currencyLabel(KurobeForgeRules.certificationPriceBronze(target))
                    + " + " + KurobeForgeRules.certificationMaterialLabel(target)
            );
        }
        if (ep.current() < ep.maximum()) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.PROMOTE,
                "MAXIMUM NATIVE EQUIPMENT EP IS REQUIRED"
            );
        }
        if (!nativePromotionEligible(stack, target)) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.PROMOTE,
                "NATIVE " + target.name() + "-GRADE MATERIAL OR HIGHER IS REQUIRED"
            );
        }
        return KurobeForgeRules.available(
            KurobeForgeSnapshotPayload.Service.PROMOTE,
            "PROMOTE TO " + target.name() + "  /  "
                + KurobeForgeRules.currencyLabel(KurobeForgeRules.promotionPriceBronze(target))
        );
    }

    private static KurobeForgeSnapshotPayload.ServiceState conduitState(
        ProjectionContext context,
        KurobeForgeWeaponData.State state
    ) {
        if (!KurobeForgeRules.isActiveGrade(state.grade())) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.CONDUIT,
                "GENESIS / GRADE VII IS RESERVED FOR FUTURE CONTENT"
            );
        }
        if (context.forgeData().certifiedGrade(context.player().getUUID()).ordinal()
            < state.grade().ordinal()) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.CONDUIT, "FORGE CERTIFICATION IS REQUIRED"
            );
        }
        if (state.conduitRank() >= 6) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.CONDUIT, "CONDUIT RANK VI IS ALREADY COMPLETE"
        );
        return KurobeForgeRules.available(
            KurobeForgeSnapshotPayload.Service.CONDUIT,
            KurobeForgeRules.conduitPriceLabel(state.grade())
        );
    }

    private static KurobeForgeSnapshotPayload.ServiceState socketState(
        ProjectionContext context,
        KurobeForgeWeaponData.State state
    ) {
        if (context.dungeonLocked()) return dungeonUnavailable(KurobeForgeSnapshotPayload.Service.SOCKET);
        if (state.core() != null) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.SOCKET, "THIS WEAPON SOCKET IS OCCUPIED"
        );
        if (context.forgeData().certifiedGrade(context.player().getUUID()).ordinal()
            < state.grade().ordinal()) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.SOCKET, "FORGE CERTIFICATION IS REQUIRED"
            );
        }
        if (!context.satchel().access().acquired()) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.SOCKET, "CORE SATCHEL IS REQUIRED"
        );
        boolean compatible = context.cores().stream().anyMatch(
            core -> core.tier() <= KurobeForgeRules.maximumCoreTier(state.grade())
        );
        return compatible
            ? KurobeForgeRules.available(KurobeForgeSnapshotPayload.Service.SOCKET, "SELECT A SATCHEL CORE")
            : KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.SOCKET, "NO COMPATIBLE SATCHEL CORE IS AVAILABLE"
            );
    }

    private static KurobeForgeSnapshotPayload.ServiceState rechargeState(
        ProjectionContext context,
        KurobeForgeWeaponData.State state,
        boolean bindingValid
    ) {
        if (context.dungeonLocked()) return dungeonUnavailable(KurobeForgeSnapshotPayload.Service.RECHARGE);
        CoreSatchelData.CoreRecord core = state.core();
        if (core == null) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.RECHARGE, "NO CORE IS SOCKETED"
        );
        if (!bindingValid) return bindingUnavailable(KurobeForgeSnapshotPayload.Service.RECHARGE);
        if (core.totalCharge() >= core.capacity()) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.RECHARGE, "SOCKETED CORE IS ALREADY FULL"
        );
        return KurobeForgeRules.available(
            KurobeForgeSnapshotPayload.Service.RECHARGE,
            KurobeForgeRules.currencyLabel(KurobeForgeRules.rechargePriceBronze(
                core.tier(), core.totalCharge(), core.capacity()
            ))
        );
    }

    private static KurobeForgeSnapshotPayload.ServiceState removeState(
        ProjectionContext context,
        KurobeForgeWeaponData.State state,
        boolean bindingValid
    ) {
        if (context.dungeonLocked()) return dungeonUnavailable(KurobeForgeSnapshotPayload.Service.REMOVE);
        CoreSatchelData.CoreRecord core = state.core();
        if (core == null) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.REMOVE, "NO CORE IS SOCKETED"
        );
        if (!bindingValid) return bindingUnavailable(KurobeForgeSnapshotPayload.Service.REMOVE);
        if (!context.satchel().access().acquired()) return KurobeForgeRules.unavailable(
            KurobeForgeSnapshotPayload.Service.REMOVE, "CORE SATCHEL IS REQUIRED"
        );
        if (context.satchel().cores().size() >= context.satchel().access().capacity()) {
            return KurobeForgeRules.unavailable(
                KurobeForgeSnapshotPayload.Service.REMOVE, "CORE SATCHEL HAS NO FREE SLOT"
            );
        }
        return KurobeForgeRules.available(
            KurobeForgeSnapshotPayload.Service.REMOVE,
            KurobeForgeRules.currencyLabel(KurobeForgeRules.removalPriceBronze(core.tier()))
        );
    }

    private static KurobeForgeSnapshotPayload.ServiceState dungeonUnavailable(
        KurobeForgeSnapshotPayload.Service service
    ) {
        return KurobeForgeRules.unavailable(service, "UNAVAILABLE DURING AN ACTIVE DUNGEON RUN");
    }

    private static KurobeForgeSnapshotPayload.ServiceState bindingUnavailable(
        KurobeForgeSnapshotPayload.Service service
    ) {
        return KurobeForgeRules.unavailable(service, "EXACT CORE BINDING REQUIRES ADMINISTRATIVE REVIEW");
    }

    private static boolean bindingValid(
        ProjectionContext context,
        KurobeForgeWeaponData.State state,
        boolean socketDataValid
    ) {
        if (!socketDataValid) return false;
        if (state.core() == null) return true;
        UUID weaponId = state.weaponId();
        return weaponId != null
            && context.forgeData().bindingMatches(
                state.core().coreId(), context.player().getUUID(), weaponId
            )
            && countWeaponIdentity(context.player().getInventory(), weaponId) == 1;
    }

    private static int countWeaponIdentity(Inventory inventory, UUID weaponId) {
        int count = 0;
        int end = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < end; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!eligible(stack)) continue;
            if (weaponId.equals(KurobeForgeWeaponData.read(stack).weaponId())) count++;
        }
        return count;
    }

    private static boolean materialsRegistered(KurobeForgeSnapshotPayload.Grade grade) {
        return KurobeForgeRules.certificationMaterials(grade).stream().allMatch(
            id -> BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).isPresent()
        );
    }

    private static boolean eligibleWithoutEpRead(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.isStackable()
            && stack.is(ELIGIBLE_WEAPONS) && !stack.is(EXCLUSIONS);
    }

    private static Object component(ItemStack stack, ResourceLocation id) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null);
        return type == null ? null : stack.get(type);
    }

    private static long boundedLong(double value) {
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }

    private static KurobeForgeSnapshotPayload.WeaponShape shape(ItemStack stack) {
        if (stack.is(GREAT_SWORDS)) return KurobeForgeSnapshotPayload.WeaponShape.GREATSWORD;
        if (stack.is(SPEARS)) return KurobeForgeSnapshotPayload.WeaponShape.SPEAR;
        if (stack.is(KATANAS) || stack.is(KODACHIS) || stack.is(ODACHIS) || stack.is(TACHIS)) {
            return KurobeForgeSnapshotPayload.WeaponShape.KATANA;
        }
        if (stack.is(DAGGERS) || stack.is(SHORT_SWORDS)) {
            return KurobeForgeSnapshotPayload.WeaponShape.RAPIER;
        }
        return KurobeForgeSnapshotPayload.WeaponShape.LONGSWORD;
    }

    private static int walletHash(ServerPlayer player) {
        DungeonBankData.Snapshot bank = DungeonBankData.get(player.serverLevel())
            .snapshot(player.getUUID());
        return Objects.hash(
            count(player, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID),
            count(player, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID),
            count(player, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID),
            count(player, DungeonCurrencyPolicy.STELLAR_GOLD_COIN_ITEM_ID),
            bank
        );
    }

    private static int certificationMaterialHash(ServerPlayer player) {
        return KurobeForgeSnapshotPayload.Grade.values().length == 0 ? 0 : java.util.Arrays.stream(
            KurobeForgeSnapshotPayload.Grade.values()
        ).filter(KurobeForgeRules::isActiveGrade)
            .flatMap(grade -> KurobeForgeRules.certificationMaterials(grade).stream())
            .distinct()
            .mapToInt(id -> 31 * id.hashCode() + count(player, id))
            .reduce(1, (left, right) -> 31 * left + right);
    }

    private static int count(ServerPlayer player, String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
        return item == null ? 0 : player.getInventory().countItem(item);
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    public record EpSnapshot(long current, long maximum) {}

    private record ProjectionContext(
        ServerPlayer player,
        KurobeForgeData forgeData,
        CoreSatchelData.SatchelSnapshot satchel,
        List<KurobeForgeSnapshotPayload.CoreOption> cores,
        int highestFloor,
        boolean dungeonLocked
    ) {}

    private enum MapEntryComparator implements Comparator<java.util.Map.Entry<Integer, CoreSatchelData.CoreRecord>> {
        INSTANCE;

        @Override
        public int compare(
            java.util.Map.Entry<Integer, CoreSatchelData.CoreRecord> left,
            java.util.Map.Entry<Integer, CoreSatchelData.CoreRecord> right
        ) {
            return Integer.compare(left.getKey(), right.getKey());
        }
    }
}
