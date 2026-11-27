package com.mineclawd.tensurafogbarriers.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class FogBarrierSavedData extends SavedData {
    public static final int MAX_PANELS_PER_DIMENSION = 2048;
    private static final String DATA_NAME = "tensura_fog_barriers";
    private final Map<Integer, FogBarrierPanel> panels = new LinkedHashMap<>();
    private int nextId = 1;

    public static FogBarrierSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(FogBarrierSavedData::new, FogBarrierSavedData::load), DATA_NAME
        );
    }

    public static FogBarrierSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FogBarrierSavedData data = new FogBarrierSavedData();
        ListTag list = tag.getList("Panels", Tag.TAG_COMPOUND);
        Map<ResourceLocation, Integer> dimensionCounts = new LinkedHashMap<>();
        int highestId = 0;
        boolean discardedExcess = false;
        for (int i = 0; i < list.size(); i++) {
            try {
                FogBarrierPanel panel = FogBarrierPanel.load(list.getCompound(i));
                int count = dimensionCounts.getOrDefault(panel.dimension(), 0);
                if (count >= MAX_PANELS_PER_DIMENSION) {
                    discardedExcess = true;
                    continue;
                }
                dimensionCounts.put(panel.dimension(), count + 1);
                data.panels.put(panel.id(), panel);
                highestId = Math.max(highestId, panel.id());
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.nextId = Math.max(highestId + 1, Math.max(1, tag.getInt("NextId")));
        if (discardedExcess) data.setDirty();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (FogBarrierPanel panel : panels.values()) list.add(panel.save());
        tag.put("Panels", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    public int allocateId() {
        if (nextId == Integer.MAX_VALUE) throw new IllegalStateException("Fog barrier ID space exhausted");
        int allocated = nextId++;
        setDirty();
        return allocated;
    }

    public void put(FogBarrierPanel panel) {
        panels.put(panel.id(), panel);
        nextId = Math.max(nextId, panel.id() + 1);
        setDirty();
    }

    public Optional<FogBarrierPanel> getPanel(int id) {
        return Optional.ofNullable(panels.get(id));
    }

    public FogBarrierPanel remove(int id) {
        FogBarrierPanel removed = panels.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    public int clear(ResourceLocation dimension) {
        int before = panels.size();
        panels.values().removeIf(panel -> panel.dimension().equals(dimension));
        int removed = before - panels.size();
        if (removed > 0) setDirty();
        return removed;
    }

    public List<FogBarrierPanel> forDimension(ResourceLocation dimension) {
        List<FogBarrierPanel> result = new ArrayList<>();
        for (FogBarrierPanel panel : panels.values()) if (panel.dimension().equals(dimension)) result.add(panel);
        return List.copyOf(result);
    }

    public Collection<FogBarrierPanel> all() {
        return List.copyOf(panels.values());
    }
}
