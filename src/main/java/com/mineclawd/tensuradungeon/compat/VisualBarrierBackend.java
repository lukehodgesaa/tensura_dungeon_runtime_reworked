package com.mineclawd.tensuradungeon.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public enum VisualBarrierBackend {
    FOG {
        @Override
        public int create(ServerLevel level, Vec3 first, Vec3 second, String type) {
            return FogBarriersCompat.createSystemPanel(level, first, second, type);
        }

        @Override
        public VisualBarrierHandle.RemovalResult remove(int id) {
            return FogBarriersCompat.remove(id);
        }

        @Override
        public boolean isAvailable() {
            return FogBarriersCompat.isAvailable();
        }
    },
    TENSURA {
        @Override
        public VisualBarrierHandle.RemovalResult remove(int id) {
            return TensuraBarriersCompat.remove(id);
        }

        @Override
        public boolean isAvailable() {
            return TensuraBarriersCompat.isAvailable();
        }
    };

    public int create(ServerLevel level, Vec3 first, Vec3 second, String type) {
        return -1;
    }

    public abstract VisualBarrierHandle.RemovalResult remove(int id);

    public abstract boolean isAvailable();
}
