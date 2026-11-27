package com.mineclawd.tensuradungeon.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record CoreSatchelView(String gradeName, int capacity, Map<Integer, CoreEntry> cores) {
    public CoreSatchelView {
        Objects.requireNonNull(gradeName, "gradeName");
        Objects.requireNonNull(cores, "cores");
        if (gradeName.isBlank()) {
            throw new IllegalArgumentException("gradeName must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        LinkedHashMap<Integer, CoreEntry> copy = new LinkedHashMap<>();
        cores.forEach((slot, core) -> {
            if (slot == null || slot < 0 || slot >= capacity) {
                throw new IllegalArgumentException("Core slot is outside Satchel capacity: " + slot);
            }
            copy.put(slot, Objects.requireNonNull(core, "core"));
        });
        cores = Map.copyOf(copy);
    }

    public Optional<CoreEntry> coreAt(int slot) {
        return Optional.ofNullable(cores.get(slot));
    }

    public int occupiedCount() {
        return cores.size();
    }

    public int firstOccupiedSlot() {
        return cores.keySet().stream().mapToInt(Integer::intValue).min().orElse(-1);
    }

    public record CoreEntry(
        int tier,
        long totalCharge,
        long capacity,
        long naturalCharge,
        String displayName
    ) {
        private static final String DEFAULT_DISPLAY_NAME = "MONSTER CORE";

        public CoreEntry(int tier, long totalCharge, long capacity, long naturalCharge) {
            this(tier, totalCharge, capacity, naturalCharge, DEFAULT_DISPLAY_NAME);
        }

        public CoreEntry {
            if (tier < 1 || tier > 6) {
                throw new IllegalArgumentException("Core Tier must be between I and VI");
            }
            if (capacity <= 0L) {
                throw new IllegalArgumentException("Core capacity must be positive");
            }
            if (totalCharge < 0L || totalCharge > capacity) {
                throw new IllegalArgumentException("Total Charge must be within Core capacity");
            }
            if (naturalCharge < 0L || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Natural Charge must be within Total Charge");
            }
            displayName = displayName == null || displayName.isBlank()
                ? DEFAULT_DISPLAY_NAME
                : displayName.length() <= 96 ? displayName : displayName.substring(0, 96);
        }

        public double chargeFraction() {
            return totalCharge / (double) capacity;
        }
    }
}
