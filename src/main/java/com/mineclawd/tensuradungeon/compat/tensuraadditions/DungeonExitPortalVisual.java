package com.mineclawd.tensuradungeon.compat.tensuraadditions;

import com.mineclawd.tensuradungeon.block.FloorExitPortalBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DungeonExitPortalVisual {
    public static final ResourceLocation ENTITY_TYPE_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_additions", "dungeon_exit_portal");

    private static final String OWNER_SESSION_KEY = "TensuraDungeonExitPortalSession";
    private static final String OWNER_X_KEY = "TensuraDungeonExitPortalX";
    private static final String OWNER_Y_KEY = "TensuraDungeonExitPortalY";
    private static final String OWNER_Z_KEY = "TensuraDungeonExitPortalZ";
    private static final PortalEntityAccess REGISTRY_ACCESS = new RegistryPortalEntityAccess();

    private DungeonExitPortalVisual() {
    }

    public static boolean isAvailable() {
        return REGISTRY_ACCESS.isAvailable();
    }

    public static boolean ensurePresent(ServerLevel level, BlockPos pos, String runSessionId) {
        return ensurePresent(level, pos, runSessionId, REGISTRY_ACCESS);
    }

    static boolean ensurePresent(
        ServerLevel level,
        BlockPos pos,
        String runSessionId,
        PortalEntityAccess access
    ) {
        if (level == null || pos == null || access == null) {
            return false;
        }

        List<Entity> owned = ownedEntities(level, pos, runSessionId, access);
        if (!owned.isEmpty()) {
            Entity portal = owned.getFirst();
            owned.stream().skip(1L).forEach(Entity::discard);
            applyOwnership(portal, pos, runSessionId);
            moveToOwnerPosition(portal, pos);
            boolean present = !portal.isRemoved() && portal.blockPosition().equals(pos);
            FloorExitPortalBlock.setVisualEntityPresent(level, pos, present);
            return present;
        }

        if (!access.isAvailable()) {
            FloorExitPortalBlock.setVisualEntityPresent(level, pos, false);
            return false;
        }

        Entity portal = access.create(level);
        if (portal == null) {
            FloorExitPortalBlock.setVisualEntityPresent(level, pos, false);
            return false;
        }

        applyOwnership(portal, pos, runSessionId);
        moveToOwnerPosition(portal, pos);
        if (!access.add(level, portal)) {
            portal.discard();
            FloorExitPortalBlock.setVisualEntityPresent(level, pos, false);
            return false;
        }

        Entity confirmed = findOwned(level, pos, runSessionId, access);
        boolean present = confirmed != null
            && !confirmed.isRemoved()
            && confirmed.blockPosition().equals(pos);
        FloorExitPortalBlock.setVisualEntityPresent(level, pos, present);
        return present;
    }

    public static void remove(ServerLevel level, @Nullable BlockPos pos, String runSessionId) {
        remove(level, pos, runSessionId, REGISTRY_ACCESS);
    }

    static void remove(
        ServerLevel level,
        @Nullable BlockPos pos,
        String runSessionId,
        PortalEntityAccess access
    ) {
        if (level == null || pos == null || access == null) {
            return;
        }
        ownedEntities(level, pos, runSessionId, access).forEach(Entity::discard);
        FloorExitPortalBlock.setVisualEntityPresent(level, pos, false);
    }

    public static boolean containsPlayer(
        ServerLevel level,
        BlockPos pos,
        String runSessionId,
        ServerPlayer player
    ) {
        return containsPlayer(level, pos, runSessionId, player, REGISTRY_ACCESS);
    }

    static boolean containsPlayer(
        ServerLevel level,
        BlockPos pos,
        String runSessionId,
        ServerPlayer player,
        PortalEntityAccess access
    ) {
        Entity portal = findOwned(level, pos, runSessionId, access);
        return portal != null
            && portal.blockPosition().equals(pos)
            && player.getBoundingBox().intersects(portal.getBoundingBox());
    }

    public static boolean isOwnedPortal(Entity entity, BlockPos pos, String runSessionId) {
        return isOwnedPortal(entity, pos, runSessionId, REGISTRY_ACCESS);
    }

    static boolean isOwnedPortal(
        Entity entity,
        BlockPos pos,
        String runSessionId,
        PortalEntityAccess access
    ) {
        return entity != null
            && pos != null
            && access != null
            && access.matches(entity)
            && hasExactOwner(entity, pos, runSessionId)
            && entity.blockPosition().equals(pos);
    }

    private static @Nullable Entity findOwned(
        ServerLevel level,
        BlockPos pos,
        String runSessionId,
        PortalEntityAccess access
    ) {
        List<Entity> entities = ownedEntities(level, pos, runSessionId, access);
        return entities.isEmpty() ? null : entities.getFirst();
    }

    private static List<Entity> ownedEntities(
        ServerLevel level,
        BlockPos pos,
        String runSessionId,
        PortalEntityAccess access
    ) {
        if (level == null || pos == null || access == null || !access.isAvailable()) {
            return List.of();
        }
        Vec3 center = Vec3.atBottomCenterOf(pos);
        AABB lookup = new AABB(center, center).inflate(2.5D, 5.5D, 2.5D);
        return level.getEntitiesOfClass(
            Entity.class,
            lookup,
            entity -> access.matches(entity) && hasExactOwner(entity, pos, runSessionId)
        );
    }

    private static boolean hasExactOwner(Entity entity, BlockPos pos, String runSessionId) {
        if (!normalizeSession(runSessionId).equals(
            entity.getPersistentData().getString(OWNER_SESSION_KEY)
        )) {
            return false;
        }
        if (hasStoredOwnerPosition(entity)) {
            return entity.getPersistentData().getInt(OWNER_X_KEY) == pos.getX()
                && entity.getPersistentData().getInt(OWNER_Y_KEY) == pos.getY()
                && entity.getPersistentData().getInt(OWNER_Z_KEY) == pos.getZ();
        }
        return entity.blockPosition().equals(pos);
    }

    private static boolean hasStoredOwnerPosition(Entity entity) {
        return entity.getPersistentData().contains(OWNER_X_KEY)
            && entity.getPersistentData().contains(OWNER_Y_KEY)
            && entity.getPersistentData().contains(OWNER_Z_KEY);
    }

    private static void applyOwnership(Entity portal, BlockPos pos, String runSessionId) {
        portal.getPersistentData().putString(OWNER_SESSION_KEY, normalizeSession(runSessionId));
        portal.getPersistentData().putInt(OWNER_X_KEY, pos.getX());
        portal.getPersistentData().putInt(OWNER_Y_KEY, pos.getY());
        portal.getPersistentData().putInt(OWNER_Z_KEY, pos.getZ());
    }

    private static void moveToOwnerPosition(Entity portal, BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        portal.moveTo(center.x, center.y, center.z, portal.getYRot(), portal.getXRot());
    }

    private static @Nullable EntityType<?> resolveType() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ENTITY_TYPE_ID).orElse(null);
    }

    private static String normalizeSession(@Nullable String runSessionId) {
        return runSessionId == null || runSessionId.isBlank() ? "NONE" : runSessionId;
    }

    interface PortalEntityAccess {
        boolean isAvailable();

        @Nullable Entity create(ServerLevel level);

        boolean matches(Entity entity);

        boolean add(ServerLevel level, Entity entity);
    }

    private static final class RegistryPortalEntityAccess implements PortalEntityAccess {
        @Override
        public boolean isAvailable() {
            return resolveType() != null;
        }

        @Override
        public @Nullable Entity create(ServerLevel level) {
            EntityType<?> type = resolveType();
            return type == null ? null : type.create(level);
        }

        @Override
        public boolean matches(Entity entity) {
            EntityType<?> type = resolveType();
            return type != null && entity.getType() == type;
        }

        @Override
        public boolean add(ServerLevel level, Entity entity) {
            return level.addFreshEntity(entity);
        }
    }
}
