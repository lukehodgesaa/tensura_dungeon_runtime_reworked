package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class DungeonSoundHooks {
    private DungeonSoundHooks() {
    }

    public static void roomActivated(ServerLevel level, BlockPos pos, boolean safeRoom, boolean finale) {
        if (safeRoom) {
            play(level, pos, ModSoundEvents.SAFE_ROOM.get(), SoundSource.AMBIENT, 0.75F, 1.0F);
            return;
        }
        play(level, pos, ModSoundEvents.ROOM_AWAKEN.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        play(level, pos, ModSoundEvents.ROOM_BARRIER_CLOSE.get(), SoundSource.BLOCKS, 0.85F, 1.0F);
        if (finale) {
            play(level, pos, ModSoundEvents.FLOOR_FINALE.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        } else if (level.random.nextFloat() < 0.55F) {
            play(level, pos, ModSoundEvents.EXPLORATION_AMBIENCE.get(), SoundSource.AMBIENT, 0.28F, 0.95F + level.random.nextFloat() * 0.10F);
        }
        if (level.random.nextFloat() < 0.18F) {
            play(level, pos, ModSoundEvents.CREATURE_AMBIENCE.get(), SoundSource.AMBIENT, 0.32F, 0.92F + level.random.nextFloat() * 0.16F);
        }
    }

    public static void objectiveStarted(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.OBJECTIVE_START.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
    }

    public static void objectiveProgress(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.OBJECTIVE_PROGRESS.get(), SoundSource.PLAYERS, 0.55F, 1.05F);
    }

    public static void objectiveCompleted(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.OBJECTIVE_COMPLETE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    public static void waveIncoming(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.WAVE_INCOMING.get(), SoundSource.HOSTILE, 0.72F, 1.0F);
    }

    public static void eliteRevealed(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.ELITE_REVEAL.get(), SoundSource.HOSTILE, 0.9F, 1.0F);
    }

    public static void roomCompleted(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.ROOM_COMPLETE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        play(level, pos, ModSoundEvents.ROOM_BARRIER_OPEN.get(), SoundSource.BLOCKS, 0.75F, 1.0F);
    }

    public static void floorCompleted(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.FLOOR_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void frontierPreviewed(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.BRANCH_PREVIEW.get(), SoundSource.AMBIENT, 0.48F, 1.0F);
    }

    public static void frontierCommitted(ServerLevel level, BlockPos pos) {
        play(level, pos, ModSoundEvents.OBJECTIVE_PROGRESS.get(), SoundSource.BLOCKS, 0.45F, 0.9F);
    }

    private static void play(ServerLevel level, BlockPos pos, SoundEvent event, SoundSource source, float volume, float pitch) {
        if (level == null || pos == null || event == null) return;
        level.playSound(null, pos, event, source, volume, pitch);
    }
}
