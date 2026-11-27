package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import com.mineclawd.tensuradungeon.item.SoulOrbItem;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.EncounterClass;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyRegistry;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.SoulOrbHaulData;
import com.mineclawd.tensuradungeon.runtime.SoulOrbMobIdentity;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID)
public final class SoulOrbEvents {
    private SoulOrbEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        SoulOrbMobIdentity identity = SoulOrbMobIdentity.read(mob).orElse(null);
        if (identity == null) return;

        Entity responsible = event.getSource().getEntity();
        ServerPlayer killer = DungeonRunTelemetryEvents.creditedPlayer(
            level, mob, identity, responsible
        );
        if (killer == null) {
            event.getDrops().clear();
            return;
        }

        List<ItemStack> rolledDrops = event.getDrops().stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack != null && !stack.isEmpty())
            .map(ItemStack::copy)
            .toList();
        UUID captureId = UUID.randomUUID();

        SoulOrbHaulData haul = SoulOrbHaulData.get(level);
        DungeonRunManager manager = DungeonRunManager.get(level);
        RoomControllerBlockEntity controller = (RoomControllerBlockEntity) level.getBlockEntity(identity.controllerPos());
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).forPlayer(killer.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .orElse(null);
        int floorNumber = manager.getFloorNumber(instanceKey);
        if (!haul.registerPending(
            killer.getUUID(), identity.sessionId(), floorNumber, captureId,
            mob.getUUID(), identity.rarity(), rolledDrops,
            new SoulOrbHaulData.CrystalCapture(
                identity.controllerPos(),
                crystalEncounterClass(
                    floorNumber, controller.getRoomProfile(), identity.crystalEncounterClass()
                ),
                DungeonCrystalEconomyRegistry.get(level),
                level.random::nextInt
            )
        )) {
            return;
        }
        haul.collect(killer.getUUID(), identity.sessionId(), captureId);
        event.getDrops().clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSoulOrbPickup(ItemEntityPickupEvent.Pre event) {
        SoulOrbItem.Identity identity = SoulOrbItem.identity(event.getItemEntity().getItem()).orElse(null);
        if (identity == null) return;
        event.setCanPickup(TriState.FALSE);
        if (!(event.getPlayer() instanceof ServerPlayer player)
            || !player.getUUID().equals(identity.ownerId())) {
            return;
        }
        SoulOrbHaulData haul = SoulOrbHaulData.get(player.serverLevel());
        int collected = haul.collectAll(player.getUUID(), identity.sessionId(), identity.orbIds());
        if (collected > 0) {
            player.take(event.getItemEntity(), collected);
            event.getItemEntity().discard();
            haul.syncHud(player);
        } else if (!event.getItemEntity().isRemoved()) {
            event.getItemEntity().discard();
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SoulOrbHaulData.get(player.serverLevel()).syncHud(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SoulOrbHaulData.get(player.serverLevel()).syncHud(player);
        }
    }

    static EncounterClass crystalEncounterClass(
        int floor,
        RoomProfile profile,
        EncounterClass stampedClass
    ) {
        if (profile != RoomProfile.BOSS_CAPABLE) return stampedClass;
        if (floor == 300) return EncounterClass.GUARDIAN;
        if (floor % 50 == 0) return EncounterClass.CHARYBDIS;
        if (floor % 10 == 0) return EncounterClass.BOSS;
        if (floor % 5 == 0) return EncounterClass.MINIBOSS;
        return stampedClass;
    }
}
