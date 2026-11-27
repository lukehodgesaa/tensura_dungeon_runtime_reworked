package com.mineclawd.tensurafogbarriers.data;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record FogBarrierPanel(
    int id,
    ResourceLocation dimension,
    Axis axis,
    double plane,
    double minY,
    double maxY,
    double minLateral,
    double maxLateral,
    FogVariant variant,
    FogAnimationStyle style,
    String type,
    long createdGameTime
) {
    public static final double COLLISION_THICKNESS = 0.125D;
    public static final double VISUAL_OVERSCAN = 0.22D;
    private static final double AXIS_EPSILON = 0.01D;
    private static final double MAX_WIDTH = 128.0D;
    private static final double MAX_HEIGHT = 64.0D;

    public FogBarrierPanel {
        if (id <= 0) throw new IllegalArgumentException("Fog barrier id must be positive");
        if (dimension == null || axis == null) throw new IllegalArgumentException("Dimension and axis are required");
        if (!finite(plane, minY, maxY, minLateral, maxLateral)) throw new IllegalArgumentException("Fog barrier coordinates must be finite");
        if (maxY <= minY || maxLateral <= minLateral) throw new IllegalArgumentException("Fog barrier must have positive width and height");
        if (maxY - minY > MAX_HEIGHT || maxLateral - minLateral > MAX_WIDTH) throw new IllegalArgumentException("Fog barrier exceeds maximum size");
        type = sanitizeType(type);
        if (variant == null) variant = FogVariant.fromType(type);
        if (style == null) style = FogAnimationStyle.PORTAL;
    }

    public static FogBarrierPanel fromCorners(
        int id,
        ResourceLocation dimension,
        Vec3 first,
        Vec3 second,
        String type,
        long createdGameTime
    ) {
        if (first == null || second == null) throw new IllegalArgumentException("Both fog barrier corners are required");
        double dx = Math.abs(second.x - first.x);
        double dz = Math.abs(second.z - first.z);
        Axis axis;
        double plane;
        double minLateral;
        double maxLateral;
        if (dx <= AXIS_EPSILON && dz > AXIS_EPSILON) {
            axis = Axis.X;
            plane = (first.x + second.x) * 0.5D;
            minLateral = Math.min(first.z, second.z);
            maxLateral = Math.max(first.z, second.z);
        } else if (dz <= AXIS_EPSILON && dx > AXIS_EPSILON) {
            axis = Axis.Z;
            plane = (first.z + second.z) * 0.5D;
            minLateral = Math.min(first.x, second.x);
            maxLateral = Math.max(first.x, second.x);
        } else {
            throw new IllegalArgumentException("Fog barrier corners must define an axis-aligned vertical plane");
        }
        return new FogBarrierPanel(
            id,
            dimension,
            axis,
            plane,
            Math.min(first.y, second.y),
            Math.max(first.y, second.y),
            minLateral,
            maxLateral,
            FogVariant.fromType(type),
            FogAnimationStyle.PORTAL,
            type,
            createdGameTime
        );
    }

    public double width() {
        return maxLateral - minLateral;
    }

    public FogBarrierPanel withId(int newId) {
        return new FogBarrierPanel(
            newId, dimension, axis, plane, minY, maxY, minLateral, maxLateral, variant, style, type, createdGameTime
        );
    }

    public FogBarrierPanel withVariant(FogVariant newVariant) {
        return new FogBarrierPanel(
            id, dimension, axis, plane, minY, maxY, minLateral, maxLateral, newVariant, style, type, createdGameTime
        );
    }

    public FogBarrierPanel withStyle(FogAnimationStyle newStyle) {
        return new FogBarrierPanel(
            id, dimension, axis, plane, minY, maxY, minLateral, maxLateral, variant, newStyle, type, createdGameTime
        );
    }

    public double height() {
        return maxY - minY;
    }

    public AABB collisionBounds() {
        double half = COLLISION_THICKNESS * 0.5D;
        return axis == Axis.X
            ? new AABB(plane - half, minY, minLateral, plane + half, maxY, maxLateral)
            : new AABB(minLateral, minY, plane - half, maxLateral, maxY, plane + half);
    }

    public AABB visualBounds() {
        double depth = 0.03D;
        return axis == Axis.X
            ? new AABB(plane - depth, minY - VISUAL_OVERSCAN, minLateral - VISUAL_OVERSCAN,
                plane + depth, maxY + VISUAL_OVERSCAN, maxLateral + VISUAL_OVERSCAN)
            : new AABB(minLateral - VISUAL_OVERSCAN, minY - VISUAL_OVERSCAN, plane - depth,
                maxLateral + VISUAL_OVERSCAN, maxY + VISUAL_OVERSCAN, plane + depth);
    }

    public boolean overlapsVerticalAndLateral(AABB entityBounds) {
        if (entityBounds.maxY <= minY || entityBounds.minY >= maxY) return false;
        return axis == Axis.X
            ? entityBounds.maxZ > minLateral && entityBounds.minZ < maxLateral
            : entityBounds.maxX > minLateral && entityBounds.minX < maxLateral;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Dimension", dimension.toString());
        tag.putString("Axis", axis.name());
        tag.putDouble("Plane", plane);
        tag.putDouble("MinY", minY);
        tag.putDouble("MaxY", maxY);
        tag.putDouble("MinLateral", minLateral);
        tag.putDouble("MaxLateral", maxLateral);
        tag.putString("Variant", variant.getSerializedName());
        tag.putString("Style", style.getSerializedName());
        tag.putString("Type", type);
        tag.putLong("CreatedGameTime", createdGameTime);
        return tag;
    }

    public static FogBarrierPanel load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) throw new IllegalArgumentException("Invalid fog barrier dimension");
        Axis axis = Axis.valueOf(tag.getString("Axis").toUpperCase(Locale.ROOT));
        String type = tag.getString("Type");
        FogVariant variant = FogVariant.byName(tag.getString("Variant")).orElseGet(() -> FogVariant.fromType(type));
        FogAnimationStyle style = FogAnimationStyle.byName(tag.getString("Style")).orElse(FogAnimationStyle.PORTAL);
        return new FogBarrierPanel(
            tag.getInt("Id"), dimension, axis, tag.getDouble("Plane"),
            tag.getDouble("MinY"), tag.getDouble("MaxY"),
            tag.getDouble("MinLateral"), tag.getDouble("MaxLateral"),
            variant, style, type, tag.getLong("CreatedGameTime")
        );
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static String sanitizeType(String raw) {
        String value = raw == null ? "golden" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        if (value.isBlank()) value = "golden";
        return value.substring(0, Math.min(64, value.length()));
    }

    public enum Axis {
        X,
        Z
    }
}
