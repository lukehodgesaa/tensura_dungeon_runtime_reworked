package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.OverworldRiftPayload;
import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import com.mineclawd.tensuradungeon.runtime.OverworldRiftEventData;
import com.mineclawd.tensuradungeon.runtime.OverworldRiftGeometry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class OverworldRiftEvents {
    private static final long SYNC_INTERVAL_TICKS = 40L;
    private static final long[] VOID_CRACK_AT_TICKS = {44L, 84L, 124L};
    private static final float[] VOID_CRACK_PITCHES = {1.0F, 1.2F, 1.1F};
    private static final long STABILIZE_AT = 140L;
    private static final float[] SONAR_BOOM_PITCHES = {1.0F, 0.95F, 0.9F};

    private OverworldRiftEvents() {
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        OverworldRiftEventData data = OverworldRiftEventData.get(level);
        OverworldRiftEventData.Transition transition = data.tick(level);
        OverworldRiftEventData.Snapshot snapshot = data.snapshot();
        if (transition == OverworldRiftEventData.Transition.OPENED) {
            announceOpened(level, snapshot);
        } else if (transition == OverworldRiftEventData.Transition.CLOSED) {
            announceClosed(level);
        } else if (snapshot.active()) {
            playScheduledRiftAudio(level, snapshot);
        }
        if (transition != OverworldRiftEventData.Transition.NONE
            || level.getGameTime() % SYNC_INTERVAL_TICKS == 0L) {
            syncAll(level, snapshot);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void syncAll(ServerLevel overworld, OverworldRiftEventData.Snapshot snapshot) {
        OverworldRiftPayload payload = payload(snapshot);
        for (ServerPlayer player : overworld.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void announceForcedOpen(ServerLevel overworld) {
        OverworldRiftEventData.Snapshot snapshot = OverworldRiftEventData.get(overworld).snapshot();
        announceOpened(overworld, snapshot);
        syncAll(overworld, snapshot);
    }

    public static void announceForcedClose(ServerLevel overworld) {
        playToOverworld(overworld, ModSoundEvents.RIFT_CLOSE.get(), 0.78F, 1.086F);
        announceClosed(overworld);
        syncAll(overworld, OverworldRiftEventData.get(overworld).snapshot());
    }

    private static void sync(ServerPlayer player) {
        if (Level.OVERWORLD.equals(player.serverLevel().dimension())) {
            PacketDistributor.sendToPlayer(
                player,
                payload(OverworldRiftEventData.get(player.serverLevel()).snapshot())
            );
        } else {
            PacketDistributor.sendToPlayer(player, OverworldRiftPayload.inactive());
        }
    }

    private static OverworldRiftPayload payload(OverworldRiftEventData.Snapshot snapshot) {
        return new OverworldRiftPayload(
            snapshot.active(),
            snapshot.anchor(),
            snapshot.orientationDegrees(),
            snapshot.startedAt(),
            snapshot.endsAt()
        );
    }

    private static void announceOpened(ServerLevel overworld, OverworldRiftEventData.Snapshot snapshot) {
        if (!snapshot.active()) {
            return;
        }
        Component notice = openingNotice();
        for (ServerPlayer player : overworld.players()) {
            player.sendSystemMessage(notice);
        }
        playToOverworld(overworld, ModSoundEvents.RIFT_CRACK.get(), 0.95F, 0.80F);
        playToOverworld(overworld, ModSoundEvents.RIFT_VOID_CRACK_1.get(), 0.95F, 1.3F);
    }

    static Component openingNotice() {
        return Component.empty()
            .append(Component.literal(
                "A catastrophic tear has split the sky, bleeding dense, dark aura into the winds. "
                    + "The ancient "
            ).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("tyrant Jahil").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            .append(Component.literal(
                " has broken the seals of antiquity. Ripping reality asunder, he has dragged a relic "
                    + "of his lost, ruined empire straight into the sky, daring any foolish mortal "
                    + "to step through the rift."
            ).withStyle(ChatFormatting.GRAY));
    }

    private static void announceClosed(ServerLevel overworld) {
        Component notice = Component.literal(
            "The shattered dungeon rift has sealed."
        ).withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : overworld.players()) {
            player.sendSystemMessage(notice);
        }
    }

    private static void playScheduledRiftAudio(
        ServerLevel overworld,
        OverworldRiftEventData.Snapshot snapshot
    ) {
        long now = overworld.getGameTime();
        long age = now - snapshot.startedAt();
        for (int crackIndex = 0; crackIndex < VOID_CRACK_AT_TICKS.length; crackIndex++) {
            if (age != VOID_CRACK_AT_TICKS[crackIndex]) {
                continue;
            }
            SoundEvent voidCrack = switch (crackIndex) {
                case 0 -> ModSoundEvents.RIFT_VOID_CRACK_2.get();
                case 1 -> ModSoundEvents.RIFT_VOID_CRACK_3.get();
                default -> ModSoundEvents.RIFT_VOID_CRACK_4.get();
            };
            playToOverworld(overworld, ModSoundEvents.RIFT_CRACK.get(), 0.90F, 0.85F + crackIndex * 0.10F);
            playToOverworld(overworld, voidCrack, 0.95F, VOID_CRACK_PITCHES[crackIndex]);
        }

        if (age == STABILIZE_AT) {
            playToOverworld(overworld, ModSoundEvents.RIFT_OPEN.get(), 0.76F, 0.92F);
        }

        if (snapshot.endsAt() - now == OverworldRiftGeometry.ENTRANCE_CLOSING_TICKS) {
            playToOverworld(overworld, ModSoundEvents.RIFT_CLOSE.get(), 0.78F, 1.086F);
        }

        for (int waveIndex = 0; waveIndex < OverworldRiftGeometry.SONAR_WAVE_COUNT; waveIndex++) {
            if (age == OverworldRiftGeometry.sonarStartTick(waveIndex)) {
                playToVisiblePlayers(
                    overworld,
                    snapshot,
                    ModSoundEvents.RIFT_VOID_BOOM.get(),
                    0.96F,
                    SONAR_BOOM_PITCHES[waveIndex]
                );
            }
        }
    }

    private static void playToVisiblePlayers(
        ServerLevel overworld,
        OverworldRiftEventData.Snapshot snapshot,
        SoundEvent sound,
        float volume,
        float pitch
    ) {
        double radiusSquared = OverworldRiftGeometry.SONAR_MAX_RADIUS
            * OverworldRiftGeometry.SONAR_MAX_RADIUS;
        for (ServerPlayer player : overworld.players()) {
            if (OverworldRiftGeometry.horizontalDistanceSquared(
                snapshot.anchor(), player.position()
            ) <= radiusSquared) {
                player.playNotifySound(sound, SoundSource.AMBIENT, volume, pitch);
            }
        }
    }

    private static void playToOverworld(
        ServerLevel overworld,
        SoundEvent sound,
        float volume,
        float pitch
    ) {
        for (ServerPlayer player : overworld.players()) {
            player.playNotifySound(sound, SoundSource.AMBIENT, volume, pitch);
        }
    }
}
