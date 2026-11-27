package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.compat.VisualBarrierHandle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

interface BarrierBackend {
    @Nullable
    default VisualBarrierHandle createVisual(ServerLevel level, Vec3 first, Vec3 second, String type) {
        return null;
    }

    default void releasePartialVisual(VisualBarrierHandle handle) {
        handle.removeNow();
    }

    default String visualMaterialSummary(String type) {
        return "NONE";
    }
}

final class BarrierBackendChain {
    private final List<BarrierBackend> backends;

    BarrierBackendChain(List<BarrierBackend> backends) {
        this.backends = List.copyOf(backends);
    }

    static BarrierBackendChain production() {
        return new BarrierBackendChain(List.of(
            new VisualPanelBarrierBackend(com.mineclawd.tensuradungeon.compat.VisualBarrierBackend.FOG)
        ));
    }

    VisualPlacement createVisuals(ServerLevel level, List<VisualPanel> panels, String type) {
        if (backends.isEmpty()) return VisualPlacement.none();
        BarrierBackend backend = backends.get(0);
        List<VisualBarrierHandle> created = new ArrayList<>();
        for (VisualPanel panel : panels) {
            VisualBarrierHandle handle = backend.createVisual(level, panel.first(), panel.second(), type);
            if (handle == null) {
                created.forEach(backend::releasePartialVisual);
                return VisualPlacement.none();
            }
            created.add(handle);
        }
        return created.isEmpty()
            ? VisualPlacement.none()
            : new VisualPlacement(List.copyOf(created), backend.visualMaterialSummary(type));
    }

    @Nullable
    VisualBarrierHandle createPreferredVisual(ServerLevel level, Vec3 first, Vec3 second, String type) {
        return backends.isEmpty() ? null : backends.get(0).createVisual(level, first, second, type);
    }

    String preferredMaterialSummary(String type) {
        return backends.isEmpty() ? "NONE" : backends.get(0).visualMaterialSummary(type);
    }

    record VisualPanel(Vec3 first, Vec3 second) {
    }

    record VisualPlacement(List<VisualBarrierHandle> handles, String materialSummary) {
        static VisualPlacement none() {
            return new VisualPlacement(List.of(), "NONE");
        }
    }
}

final class VisualPanelBarrierBackend implements BarrierBackend {
    private final com.mineclawd.tensuradungeon.compat.VisualBarrierBackend backend;

    VisualPanelBarrierBackend(com.mineclawd.tensuradungeon.compat.VisualBarrierBackend backend) {
        this.backend = backend;
    }

    @Override
    public VisualBarrierHandle createVisual(ServerLevel level, Vec3 first, Vec3 second, String type) {
        int id = backend.create(level, first, second, type);
        return id < 0 ? null : new VisualBarrierHandle(backend, id);
    }

    @Override
    public String visualMaterialSummary(String type) {
        return backend.name() + "_" + type.toUpperCase();
    }
}

final class LegacyRuntimeBarrierBlocks {
    private static final ResourceLocation VOID_BARRIER_BLOCK_ID =
        ResourceLocation.fromNamespaceAndPath("voidblock", "void_block");
    private static final ResourceLocation FOG_BARRIER_BLOCK_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_fog_barriers", "golden_fog_barrier");

    private LegacyRuntimeBarrierBlocks() {
    }

    static boolean recognizes(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return state.is(Blocks.BARRIER) || VOID_BARRIER_BLOCK_ID.equals(key) || FOG_BARRIER_BLOCK_ID.equals(key);
    }
}
