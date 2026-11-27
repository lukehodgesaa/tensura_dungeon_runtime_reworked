package com.mineclawd.tensuradungeon.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

public final class FogBarriersCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "tensura_fog_barriers";
    private static final String LIVE_FAILURE_ENABLED_PROPERTY = "tensura_dungeon.fb048.live_failure_enabled";
    private static final String LIVE_FAILURE_STAGE_PROPERTY = "tensura_dungeon.fb048.live_failure_stage";
    private static Method createPanel;
    private static Method setStyle;
    private static Method setVariant;
    private static Method setSolid;
    private static Method removeNow;
    private static Method panels;
    private static boolean resolved;
    private static boolean warned;
    private static boolean panelEnumerationWarned;
    private static final double PANEL_CELL_MARGIN = 2.0D;

    private FogBarriersCompat() {
    }

    public static int createSystemPanel(ServerLevel level, Vec3 first, Vec3 second, String type) {
        if (!resolve()) return -1;
        int id = -1;
        try {
            id = (int) createPanel.invoke(null, level, first, second, type, null);
            if (id < 0) {
                LOGGER.error("Required Fog barrier creation returned {} for type {}", id, type);
                return id;
            }
            boolean variantApplied = (boolean) setVariant.invoke(null, id, variantForType(type));
            if (variantApplied && injectLiveFailureAfter("VARIANT")) variantApplied = false;
            boolean styleApplied = variantApplied && (boolean) setStyle.invoke(null, id, "runes");
            if (styleApplied && injectLiveFailureAfter("STYLE")) styleApplied = false;
            if (variantApplied && styleApplied) return id;
            removeCreatedPanel(id);
            LOGGER.error("Fog barrier #{} rejected the required dungeon Rune Drift appearance", id);
            return -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (id >= 0) removeCreatedPanel(id);
            warn(exception);
            return -1;
        }
    }

    public static boolean setDungeonAppearance(int id, String type) {
        if (id < 0 || !resolve()) return false;
        try {
            return (boolean) setVariant.invoke(null, id, variantForType(type))
                && (boolean) setStyle.invoke(null, id, "runes");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    public static String variantForType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (normalized.contains("black") || normalized.contains("sealed")
            || normalized.contains("frontier") || normalized.contains("unavailable")
            || normalized.contains("unexplored")) return "black";
        if (normalized.contains("boss") || normalized.contains("violet")) return "violet";
        if (normalized.contains("safe") || normalized.contains("checkpoint")
            || normalized.contains("teal")) return "teal";
        if (normalized.contains("combat") || normalized.contains("elite")
            || normalized.contains("crimson")) return "crimson";
        return "gold";
    }
    public static boolean setRuneStyle(int id) {
        if (id < 0 || !resolve()) return false;
        try {
            return (boolean) setStyle.invoke(null, id, "runes");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    public static boolean setSolid(int id, boolean solid) {
        if (id < 0 || !resolve()) return false;
        try {
            return (boolean) setSolid.invoke(null, id, solid);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    public static VisualBarrierHandle.RemovalResult remove(int id) {
        if (id < 0) return VisualBarrierHandle.RemovalResult.ABSENT;
        if (!resolve()) return VisualBarrierHandle.RemovalResult.UNAVAILABLE;
        try {
            return (boolean) removeNow.invoke(null, id)
                ? VisualBarrierHandle.RemovalResult.REMOVED
                : VisualBarrierHandle.RemovalResult.ABSENT;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return VisualBarrierHandle.RemovalResult.UNAVAILABLE;
        }
    }

    public static int removeDungeonPanelsInBounds(
        ServerLevel level,
        BlockPos min,
        BlockPos max
    ) {
        return removeDungeonPanelsInBounds(level, min, max, true, Long.MIN_VALUE);
    }

    public static int removeStaleDungeonPanelsInBounds(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        long createdBeforeGameTime
    ) {
        return removeDungeonPanelsInBounds(level, min, max, false, createdBeforeGameTime);
    }

    private static int removeDungeonPanelsInBounds(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        boolean removeAll,
        long createdBeforeGameTime
    ) {
        if (min == null || max == null || !resolve() || panels == null) return 0;
        int removed = 0;
        for (PanelSnapshot panel : panelSnapshots(level)) {
            if (!panel.type().toLowerCase(Locale.ROOT).startsWith("dungeon_")
                || !sameDimension(level, panel.dimension())
                || !intersectsCell(panel, min, max)
                || !removeAll && panel.createdGameTime() >= createdBeforeGameTime) {
                continue;
            }
            if (remove(panel.id()) == VisualBarrierHandle.RemovalResult.REMOVED) {
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info(
                "Removed {} orphaned dungeon Fog barrier panel(s) in bounds min={} max={} mode={}",
                removed, min, max, removeAll ? "ALL" : "STALE"
            );
        }
        return removed;
    }

    private static List<PanelSnapshot> panelSnapshots(ServerLevel level) {
        if (panels == null) return List.of();
        try {
            Object result = panels.invoke(null, level);
            if (!(result instanceof Iterable<?> entries)) return List.of();
            List<PanelSnapshot> snapshots = new ArrayList<>();
            for (Object entry : entries) {
                PanelSnapshot snapshot = readPanelSnapshot(entry);
                if (snapshot != null) snapshots.add(snapshot);
            }
            return List.copyOf(snapshots);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnPanelEnumeration(exception);
            return List.of();
        }
    }

    private static PanelSnapshot readPanelSnapshot(Object panel) {
        if (panel == null) return null;
        try {
            Object id = panelAccessor(panel, "id");
            Object dimension = panelAccessor(panel, "dimension");
            Object axis = panelAccessor(panel, "axis");
            Object type = panelAccessor(panel, "type");
            Object plane = panelAccessor(panel, "plane");
            Object minY = panelAccessor(panel, "minY");
            Object maxY = panelAccessor(panel, "maxY");
            Object minLateral = panelAccessor(panel, "minLateral");
            Object maxLateral = panelAccessor(panel, "maxLateral");
            Object createdGameTime = panelAccessor(panel, "createdGameTime");
            if (!(id instanceof Number idValue)
                || dimension == null
                || axis == null
                || type == null
                || !(plane instanceof Number planeValue)
                || !(minY instanceof Number minYValue)
                || !(maxY instanceof Number maxYValue)
                || !(minLateral instanceof Number minLateralValue)
                || !(maxLateral instanceof Number maxLateralValue)
                || !(createdGameTime instanceof Number createdGameTimeValue)) {
                return null;
            }
            return new PanelSnapshot(
                idValue.intValue(),
                dimension.toString(),
                axis.toString(),
                type.toString(),
                planeValue.doubleValue(),
                minYValue.doubleValue(),
                maxYValue.doubleValue(),
                minLateralValue.doubleValue(),
                maxLateralValue.doubleValue(),
                createdGameTimeValue.longValue()
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnPanelEnumeration(exception);
            return null;
        }
    }

    private static Object panelAccessor(Object panel, String name) throws ReflectiveOperationException {
        return panel.getClass().getMethod(name).invoke(panel);
    }

    private static boolean sameDimension(ServerLevel level, String panelDimension) {
        return level == null || level.dimension().location().toString().equals(panelDimension);
    }

    private static boolean intersectsCell(PanelSnapshot panel, BlockPos min, BlockPos max) {
        double minX = min.getX() - PANEL_CELL_MARGIN;
        double maxX = max.getX() + 1.0D + PANEL_CELL_MARGIN;
        double minY = min.getY() - PANEL_CELL_MARGIN;
        double maxY = max.getY() + 1.0D + PANEL_CELL_MARGIN;
        double minZ = min.getZ() - PANEL_CELL_MARGIN;
        double maxZ = max.getZ() + 1.0D + PANEL_CELL_MARGIN;
        if (!rangeOverlaps(panel.minY(), panel.maxY(), minY, maxY)) return false;
        if ("X".equalsIgnoreCase(panel.axis())) {
            return panel.plane() >= minX && panel.plane() <= maxX
                && rangeOverlaps(panel.minLateral(), panel.maxLateral(), minZ, maxZ);
        }
        if ("Z".equalsIgnoreCase(panel.axis())) {
            return panel.plane() >= minZ && panel.plane() <= maxZ
                && rangeOverlaps(panel.minLateral(), panel.maxLateral(), minX, maxX);
        }
        return false;
    }

    private static boolean rangeOverlaps(double firstMin, double firstMax, double secondMin, double secondMax) {
        return firstMax >= secondMin && secondMax >= firstMin;
    }

    public static boolean isAvailable() {
        return resolve();
    }

    private static void removeCreatedPanel(int id) {
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if ((boolean) removeNow.invoke(null, id)) return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure == null) {
            LOGGER.error("Required Fog barrier #{} could not be removed after Rune Drift setup failed", id);
        } else {
            LOGGER.error("Required Fog barrier #{} cleanup failed after Rune Drift setup failed", id, lastFailure);
        }
    }

    private static boolean injectLiveFailureAfter(String stage) {
        if (!Boolean.getBoolean(LIVE_FAILURE_ENABLED_PROPERTY)) return false;
        String configuredStage = System.getProperty(LIVE_FAILURE_STAGE_PROPERTY, "").trim();
        if (!stage.equalsIgnoreCase(configuredStage)) return false;
        System.clearProperty(LIVE_FAILURE_ENABLED_PROPERTY);
        LOGGER.warn("FB-048 live failure injection active after Fog {} setup", stage);
        return true;
    }

    private static boolean resolve() {
        if (resolved) {
            return createPanel != null && setStyle != null && setVariant != null
                && setSolid != null && removeNow != null;
        }
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        try {
            Class<?> api = Class.forName("com.mineclawd.tensurafogbarriers.api.FogBarrierAPI");
            createPanel = api.getMethod(
                "createPanel", ServerLevel.class, Vec3.class, Vec3.class, String.class,
                net.minecraft.server.level.ServerPlayer.class
            );
            setStyle = api.getMethod("setStyle", int.class, String.class);
            setVariant = api.getMethod("setVariant", int.class, String.class);
            setSolid = api.getMethod("setSolid", int.class, boolean.class);
            removeNow = api.getMethod("removeNow", int.class);
            try {
                panels = api.getMethod("panels", ServerLevel.class);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                panels = null;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn(exception);
            return false;
        }
    }

    private static void warn(Throwable exception) {
        if (warned) return;
        warned = true;
        LOGGER.error("Required Tensura Fog Barriers compatibility is unavailable: {}", exception.toString());
    }

    private static void warnPanelEnumeration(Throwable exception) {
        if (panelEnumerationWarned) return;
        panelEnumerationWarned = true;
        LOGGER.warn("Tensura dungeon could not enumerate persisted Fog barrier panels for orphan cleanup: {}",
            exception.toString());
    }

    private record PanelSnapshot(
        int id,
        String dimension,
        String axis,
        String type,
        double plane,
        double minY,
        double maxY,
        double minLateral,
        double maxLateral,
        long createdGameTime
    ) {
    }
}
