package com.mineclawd.tensuradungeon.runtime;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

public final class DungeonDamageRecursionGuard {
    private static final ThreadLocal<Set<Pair>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    private DungeonDamageRecursionGuard() {}

    public static Scope enter(Entity target, Entity source) {
        if (target == null || source == null) return Scope.INACTIVE;
        Pair pair = new Pair(target.getUUID(), source.getUUID());
        Set<Pair> active = ACTIVE.get();
        if (!active.add(pair)) return Scope.INACTIVE;
        return new Scope(active, pair, true);
    }

    public static boolean isActive(Entity target, Entity source) {
        return target != null
            && source != null
            && ACTIVE.get().contains(new Pair(target.getUUID(), source.getUUID()));
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope INACTIVE = new Scope(null, null, false);
        private final Set<Pair> active;
        private final Pair pair;
        private final boolean entered;
        private boolean closed;

        private Scope(Set<Pair> active, Pair pair, boolean entered) {
            this.active = active;
            this.pair = pair;
            this.entered = entered;
        }

        public boolean entered() {
            return entered;
        }

        @Override
        public void close() {
            if (closed || !entered) return;
            closed = true;
            active.remove(pair);
            if (active.isEmpty()) ACTIVE.remove();
        }
    }

    private record Pair(UUID target, UUID source) {}
}
