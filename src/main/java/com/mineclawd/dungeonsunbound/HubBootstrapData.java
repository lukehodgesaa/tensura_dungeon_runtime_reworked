package com.mineclawd.dungeonsunbound;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class HubBootstrapData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeons_unbound_hub";

    public enum State {
        NONE,
        PLACING_BLOCKS,
        PLACING_ENTITIES,
        DONE,
        SKIPPED_EXISTING_HUB
    }

    private State state = State.NONE;
    private int nextChunk;
    private int nextEntityChunk;

    public static HubBootstrapData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(HubBootstrapData::new, HubBootstrapData::load),
            DATA_NAME
        );
    }

    private static HubBootstrapData load(CompoundTag tag, HolderLookup.Provider registries) {
        HubBootstrapData data = new HubBootstrapData();
        try {
            data.state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException exception) {
            data.state = State.NONE;
        }
        data.nextChunk = tag.getInt("NextChunk");
        data.nextEntityChunk = tag.getInt("NextEntityChunk");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("State", state.name());
        tag.putInt("NextChunk", nextChunk);
        tag.putInt("NextEntityChunk", nextEntityChunk);
        return tag;
    }

    public State state() {
        return state;
    }

    public int nextChunk() {
        return nextChunk;
    }

    public int nextEntityChunk() {
        return nextEntityChunk;
    }

    public void setState(State state) {
        this.state = state;
        setDirty();
    }

    public void setNextChunk(int nextChunk) {
        this.nextChunk = nextChunk;
        setDirty();
    }

    public void setNextEntityChunk(int nextEntityChunk) {
        this.nextEntityChunk = nextEntityChunk;
        setDirty();
    }

    public void restart() {
        state = State.PLACING_BLOCKS;
        nextChunk = 0;
        nextEntityChunk = 0;
        setDirty();
    }
}
