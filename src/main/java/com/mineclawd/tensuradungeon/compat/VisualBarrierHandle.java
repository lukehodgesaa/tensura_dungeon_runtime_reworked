package com.mineclawd.tensuradungeon.compat;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class VisualBarrierHandle {
    private final VisualBarrierBackend backend;
    private final int id;
    private final String persistedBackendName;

    public VisualBarrierHandle(VisualBarrierBackend backend, int id) {
        if (backend == null || id < 0) throw new IllegalArgumentException("Invalid visual barrier handle");
        this.backend = backend;
        this.id = id;
        this.persistedBackendName = backend.name();
    }

    private VisualBarrierHandle(String persistedBackendName, int id) {
        this.backend = null;
        this.id = id;
        this.persistedBackendName = persistedBackendName == null ? "" : persistedBackendName;
    }

    @Nullable
    public VisualBarrierBackend backend() {
        return backend;
    }

    public int id() {
        return id;
    }

    public String persistedBackendName() {
        return persistedBackendName;
    }

    public boolean isResolved() {
        return backend != null && id >= 0;
    }

    public RemovalResult remove() {
        if (!isResolved()) return RemovalResult.UNRESOLVED;
        if (!backend.isAvailable()) return RemovalResult.UNAVAILABLE;
        return backend.remove(id);
    }

    public boolean ensureDungeonAppearance(String type) {
        return isResolved() && (backend != VisualBarrierBackend.FOG || FogBarriersCompat.setDungeonAppearance(id, type));
    }

    public boolean ensureDungeonAppearance() {
        return isResolved() && (backend != VisualBarrierBackend.FOG || FogBarriersCompat.setRuneStyle(id));
    }

    public boolean setSolid(boolean solid) {
        return isResolved() && backend == VisualBarrierBackend.FOG && FogBarriersCompat.setSolid(id, solid);
    }

    public boolean removeNow() {
        return remove() == RemovalResult.REMOVED;
    }

    @Nullable
    public static VisualBarrierHandle createFog(ServerLevel level, Vec3 first, Vec3 second, String type) {
        int id = VisualBarrierBackend.FOG.create(level, first, second, type);
        return id < 0 ? null : new VisualBarrierHandle(VisualBarrierBackend.FOG, id);
    }

    public enum RemovalResult {
        REMOVED,
        ABSENT,
        UNAVAILABLE,
        UNRESOLVED
    }

    @Nullable
    public static VisualBarrierHandle parse(String backendName, int id) {
        VisualBarrierHandle handle = parsePersisted(backendName, id);
        return handle.isResolved() ? handle : null;
    }

    public static VisualBarrierHandle parsePersisted(String backendName, int id) {
        if (id >= 0 && backendName != null && !backendName.isBlank()) {
            try {
                VisualBarrierHandle handle = new VisualBarrierHandle(
                    VisualBarrierBackend.valueOf(backendName.toUpperCase(Locale.ROOT)), id
                );
                handle.ensureDungeonAppearance();
                return handle;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new VisualBarrierHandle(backendName, id);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VisualBarrierHandle handle)) return false;
        return id == handle.id
            && backend == handle.backend
            && persistedBackendName.equals(handle.persistedBackendName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backend, id, persistedBackendName);
    }

    @Override
    public String toString() {
        return persistedBackendName + ":" + id;
    }
}
