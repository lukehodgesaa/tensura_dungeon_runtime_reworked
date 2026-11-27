package com.mineclawd.tensuradungeon.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class CoreSatchelScoreboardSync {
    public static final String OBJECTIVE_NAME = "tensura_dungeon_satchel";

    private CoreSatchelScoreboardSync() {
    }

    public static void ensureObjective(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        if (scoreboard.getObjective(OBJECTIVE_NAME) == null) {
            scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("Core Satchel"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            );
        }
    }

    public static void removeObjective(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }

    public static void sync(ServerPlayer player) {
    }
}
