package com.mineclawd.tensuraadditions.compat.moreskills;

public interface BerettaAttackExecutor {
    default void start(BerettaAttackContext context) {
    }

    void tick(BerettaAttackContext context);

    default void finish(BerettaAttackContext context) {
    }
}
