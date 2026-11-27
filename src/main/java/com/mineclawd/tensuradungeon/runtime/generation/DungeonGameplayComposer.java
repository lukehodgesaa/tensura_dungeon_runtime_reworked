package com.mineclawd.tensuradungeon.runtime.generation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;

public final class DungeonGameplayComposer {
    private static final Set<Integer> MILESTONE_BOSS_FLOORS = Set.of(50, 100, 150, 200, 250, 300);

    private DungeonGameplayComposer() {
    }

    public static Composition compose(int floor, int roomCount, long seed) {
        return compose(floor, roomCount, seed, "");
    }

    public static Composition compose(int floor, int roomCount, long seed, String requiredTheme) {
        int rooms = Math.max(1, roomCount);
        RandomSource random = RandomSource.create(seed);
        List<Beat> beats = new ArrayList<>();
        Map<DungeonRoomRole, Integer> targets = targets(rooms, seed);
        Map<DungeonRoomRole, Integer> used = new EnumMap<>(DungeonRoomRole.class);
        String previousObjective = "NONE";

        for (int index = 1; index <= rooms; index++) {
            DungeonRoomRole role;
            if (index == 1) role = DungeonRoomRole.ENTRY;
            else if (index == rooms) role = DungeonRoomRole.FINALE;
            else role = chooseRole(index, rooms, targets, used, beats, random);
            used.merge(role, 1, Integer::sum);
            int intensity = intensityFor(index, rooms, role);
            String objective = objectiveFor(role, previousObjective, random);
            if (!"NONE".equals(objective)) previousObjective = objective;
            TerminalEncounterPlan terminalEncounter = index == rooms
                ? TerminalEncounterPlan.forFloor(floor, requiredTheme)
                : TerminalEncounterPlan.none();
            beats.add(new Beat(index, role, intensity, objective, purposeFor(role), terminalEncounter));
        }
        return new Composition(floor, rooms, seed, List.copyOf(beats), Map.copyOf(targets), Map.copyOf(used));
    }

    private static Map<DungeonRoomRole, Integer> targets(int rooms, long seed) {
        Map<DungeonRoomRole, Integer> targets = new EnumMap<>(DungeonRoomRole.class);
        int interior = Math.max(0, rooms - 2);
        int traversal = rooms >= 5 ? Math.max(1, rooms >= 8 ? interior / 3 : 1) : 0;
        int structural = rooms >= 7 ? 1 : 0;
        int utility = rooms >= 8 ? Math.max(1, interior / 8) : 0;

        targets.put(DungeonRoomRole.TRAVERSAL, traversal);
        if (structural > 0) {
            DungeonRoomRole selected = allocationIndex(seed, 0x6A09E667F3BCC909L, 2) == 0
                ? DungeonRoomRole.LARGE_COMBAT : DungeonRoomRole.INTERSECTION;
            targets.put(selected, structural);
        }
        if (utility > 0) {
            List<DungeonRoomRole> utilityRoles = rooms >= 10
                ? List.of(DungeonRoomRole.SAFE, DungeonRoomRole.INTERACTION, DungeonRoomRole.TREASURE)
                : List.of(DungeonRoomRole.SAFE, DungeonRoomRole.INTERACTION);
            int start = allocationIndex(seed, 0xBB67AE8584CAA73BL, utilityRoles.size());
            for (int slot = 0; slot < utility; slot++) {
                targets.merge(utilityRoles.get((start + slot) % utilityRoles.size()), 1, Integer::sum);
            }
        }
        targets.put(DungeonRoomRole.COMBAT, interior - traversal - structural - utility);
        return targets;
    }

    private static int allocationIndex(long seed, long salt, int bound) {
        long value = seed ^ salt;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return (int) Math.floorMod(value, bound);
    }

    private static DungeonRoomRole chooseRole(
        int index,
        int rooms,
        Map<DungeonRoomRole, Integer> targets,
        Map<DungeonRoomRole, Integer> used,
        List<Beat> beats,
        RandomSource random
    ) {
        List<DungeonRoomRole> candidates = new ArrayList<>();
        for (Map.Entry<DungeonRoomRole, Integer> entry : targets.entrySet()) {
            if (used.getOrDefault(entry.getKey(), 0) < entry.getValue()) candidates.add(entry.getKey());
        }
        if (candidates.isEmpty()) return DungeonRoomRole.TRAVERSAL;
        DungeonRoomRole previous = beats.isEmpty() ? null : beats.get(beats.size() - 1).role();
        int progress = (index * 100) / Math.max(1, rooms);
        DungeonRoomRole best = candidates.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (DungeonRoomRole candidate : candidates) {
            int remaining = targets.getOrDefault(candidate, 0) - used.getOrDefault(candidate, 0);
            int score = remaining * 30 + random.nextInt(20);
            if (candidate == previous) score -= 55;
            if (isPressure(candidate) && previous != null && isPressure(previous)) score -= 35;
            if (candidate == DungeonRoomRole.LARGE_COMBAT) score += progress >= 45 && progress <= 80 ? 90 : -40;
            if (candidate == DungeonRoomRole.SAFE) score += previous != null && isPressure(previous) ? 80 : -20;
            if (candidate == DungeonRoomRole.INTERSECTION) score += progress >= 20 && progress <= 75 ? 45 : -25;
            if (candidate == DungeonRoomRole.TREASURE) score += progress >= 35 ? 35 : -20;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isPressure(DungeonRoomRole role) {
        return role == DungeonRoomRole.COMBAT || role == DungeonRoomRole.LARGE_COMBAT || role == DungeonRoomRole.ARENA || role == DungeonRoomRole.FINALE;
    }

    private static int intensityFor(int index, int rooms, DungeonRoomRole role) {
        if (role == DungeonRoomRole.SAFE || role == DungeonRoomRole.ENTRY) return 0;
        if (role == DungeonRoomRole.FINALE) return 5;
        int base = 1 + (index * 3 / Math.max(1, rooms));
        if (role == DungeonRoomRole.LARGE_COMBAT || role == DungeonRoomRole.ARENA) base++;
        return Math.min(4, base);
    }

    private static String objectiveFor(DungeonRoomRole role, String previous, RandomSource random) {
        List<String> choices = switch (role) {
            case COMBAT -> List.of("CLEAR_ALL", "KILL_ELITE", "SURVIVE_TIMER", "ACTIVATE_RUNES");
            case LARGE_COMBAT, ARENA -> List.of("KILL_ELITE", "SURVIVE_TIMER", "CLEAR_ALL");
            case INTERACTION -> List.of("ACTIVATE_RUNES");
            case TREASURE -> List.of("CLEAR_ALL");
            case FINALE -> List.of("KILL_ELITE");
            default -> List.of("NONE");
        };
        if (choices.size() <= 1) return choices.get(0);
        List<String> filtered = choices.stream().filter(choice -> !choice.equals(previous)).toList();
        List<String> pool = filtered.isEmpty() ? choices : filtered;
        return pool.get(random.nextInt(pool.size()));
    }

    private static String purposeFor(DungeonRoomRole role) {
        return switch (role) {
            case ENTRY -> "orient_and_build_anticipation";
            case COMBAT -> "core_encounter";
            case LARGE_COMBAT, ARENA -> "set_piece";
            case INTERSECTION -> "meaningful_route_choice";
            case INTERACTION -> "non_kill_objective";
            case TREASURE -> "risk_reward";
            case SAFE -> "relief_and_recovery";
            case FINALE -> "floor_climax";
            default -> "movement_and_atmosphere";
        };
    }

    public enum TerminalEncounterRole {
        NONE,
        ELITE,
        BOSS;

        public static TerminalEncounterRole parse(String value) {
            if (value == null) return NONE;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NONE;
            }
        }
    }

    public record TerminalEncounterPlan(
        TerminalEncounterRole role,
        String requiredTheme,
        boolean milestoneBoss
    ) {
        public TerminalEncounterPlan {
            role = role == null ? TerminalEncounterRole.NONE : role;
            requiredTheme = requiredTheme == null
                ? "" : requiredTheme.trim().toLowerCase(Locale.ROOT);
            milestoneBoss = role == TerminalEncounterRole.BOSS && milestoneBoss;
            if (role == TerminalEncounterRole.NONE) requiredTheme = "";
        }

        public static TerminalEncounterPlan none() {
            return new TerminalEncounterPlan(TerminalEncounterRole.NONE, "", false);
        }

        public static TerminalEncounterPlan forFloor(int floor, String requiredTheme) {
            int plannedFloor = Math.max(1, floor);
            TerminalEncounterRole role = plannedFloor % 10 == 0
                ? TerminalEncounterRole.BOSS : TerminalEncounterRole.ELITE;
            return new TerminalEncounterPlan(
                role, requiredTheme, role == TerminalEncounterRole.BOSS
                    && MILESTONE_BOSS_FLOORS.contains(plannedFloor)
            );
        }

        public boolean reserved() {
            return role != TerminalEncounterRole.NONE;
        }
    }

    public record Beat(
        int index,
        DungeonRoomRole role,
        int intensity,
        String objective,
        String purpose,
        TerminalEncounterPlan terminalEncounter
    ) {
        public Beat {
            terminalEncounter = terminalEncounter == null
                ? TerminalEncounterPlan.none() : terminalEncounter;
        }

        public Beat(int index, DungeonRoomRole role, int intensity, String objective, String purpose) {
            this(index, role, intensity, objective, purpose, TerminalEncounterPlan.none());
        }
    }

    public record Composition(
        int floor,
        int roomCount,
        long seed,
        List<Beat> beats,
        Map<DungeonRoomRole, Integer> targets,
        Map<DungeonRoomRole, Integer> actual
    ) {
        public Composition withTerminalTheme(String requiredTheme) {
            if (beats.isEmpty()) return this;
            List<Beat> themedBeats = new ArrayList<>(beats);
            Beat terminal = themedBeats.getLast();
            themedBeats.set(themedBeats.size() - 1, new Beat(
                terminal.index(), terminal.role(), terminal.intensity(), terminal.objective(),
                terminal.purpose(), TerminalEncounterPlan.forFloor(floor, requiredTheme)
            ));
            return new Composition(floor, roomCount, seed, List.copyOf(themedBeats), targets, actual);
        }
    }
}
