package com.mineclawd.tensuradungeon.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DungeonHubMusicCatalog {
    static final int MAX_WEIGHT = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonHubMusicCatalog.class);

    public record Track(
        String key,
        String displayName,
        String soundId,
        double durationSeconds,
        int defaultWeight,
        Set<String> avoidAfter
    ) {
        public Track {
            Objects.requireNonNull(key, "hub music key");
            Objects.requireNonNull(displayName, "hub music display name");
            Objects.requireNonNull(soundId, "hub music sound id");
            Objects.requireNonNull(avoidAfter, "hub music avoid-after set");
            if (key.isBlank() || displayName.isBlank() || soundId.isBlank()) {
                throw new IllegalArgumentException("Hub music track fields cannot be blank");
            }
            if (durationSeconds <= 0.0D) {
                throw new IllegalArgumentException("Hub music duration must be positive");
            }
            if (defaultWeight <= 0) {
                throw new IllegalArgumentException("Hub music weight must be positive");
            }
            key = key.toLowerCase(Locale.ROOT);
            avoidAfter = Set.copyOf(avoidAfter);
        }
    }

    private static final List<Track> TRACKS = List.of(
        new Track(
            "echoes",
            "Echoes Beneath the Orrery",
            "tensura_dungeon:hub_music_echoes",
            133.966D,
            3,
            Set.of("inner_hall")
        ),
        new Track(
            "inner_hall",
            "Echoes Beneath the Orrery - Inner Hall",
            "tensura_dungeon:hub_music_inner_hall",
            47.640D,
            1,
            Set.of("echoes")
        ),
        new Track(
            "labyrinth",
            "Labyrinth of Returning Light",
            "tensura_dungeon:hub_music_labyrinth",
            139.406D,
            3,
            Set.of("ascendant")
        ),
        new Track(
            "ascendant",
            "Labyrinth of Returning Light - Ascendant",
            "tensura_dungeon:hub_music_ascendant",
            48.764D,
            1,
            Set.of("labyrinth")
        ),
        new Track(
            "moment",
            "A Moment Between Descents",
            "tensura_dungeon:hub_music_moment",
            43.824D,
            1,
            Set.of()
        ),
        new Track(
            "sanctum",
            "Sanctum of Forgotten Stars",
            "tensura_dungeon:hub_music_sanctum",
            85.519D,
            3,
            Set.of()
        )
    );

    private static final Map<String, Track> BY_KEY = buildKeyIndex();
    private static final Map<String, Integer> DEFAULT_WEIGHTS = buildDefaultWeights();

    private DungeonHubMusicCatalog() {
    }

    public static List<Track> tracks() {
        return TRACKS;
    }

    public static Track byKey(String key) {
        if (key == null) {
            return null;
        }
        return BY_KEY.get(key.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> defaultRotationEntries() {
        return TRACKS.stream()
            .map(track -> track.key() + ":" + track.defaultWeight())
            .toList();
    }

    public static Map<String, Integer> defaultWeights() {
        return DEFAULT_WEIGHTS;
    }

    public static Map<String, Integer> parseWeights(List<? extends String> entries) {
        if (entries == null || entries.isEmpty()) {
            return DEFAULT_WEIGHTS;
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            String key = entry;
            int weight = 1;
            int separator = entry.lastIndexOf(':');
            if (separator >= 0) {
                key = entry.substring(0, separator).trim();
                try {
                    weight = Integer.parseInt(entry.substring(separator + 1).trim());
                } catch (NumberFormatException ignored) {
                    weight = 0;
                }
            }
            Track track = byKey(key);
            if (track == null || weight <= 0) {
                LOGGER.warn("[TD-MUSIC] Ignoring invalid hub music rotation entry '{}'", raw);
                continue;
            }
            result.put(track.key(), Math.min(weight, MAX_WEIGHT));
        }
        return result.isEmpty() ? DEFAULT_WEIGHTS : Map.copyOf(result);
    }

    public static Track nextTrack(
        String previousKey,
        Map<String, Integer> weights,
        RandomSource random
    ) {
        Selection selection = selection(previousKey, weights);
        return choose(selection, random.nextInt(selection.total()));
    }

    public static Track chooseTrack(
        String previousKey,
        Map<String, Integer> weights,
        int roll
    ) {
        Selection selection = selection(previousKey, weights);
        return choose(selection, Math.floorMod(roll, selection.total()));
    }

    private static Track choose(Selection selection, int roll) {
        int cumulative = 0;
        for (Track track : selection.candidates()) {
            cumulative += weightOf(track, selection.weights());
            if (roll < cumulative) {
                return track;
            }
        }
        return selection.candidates().getLast();
    }

    private static List<Track> eligibleTracks(String previousKey) {
        List<Track> withoutPrevious = TRACKS.stream()
            .filter(track -> !track.key().equals(previousKey))
            .toList();
        List<Track> preferred = withoutPrevious.stream()
            .filter(track -> previousKey == null || !track.avoidAfter().contains(previousKey))
            .toList();
        return preferred.isEmpty() ? withoutPrevious : preferred;
    }

    private static Selection selection(String previousKey, Map<String, Integer> weights) {
        List<Track> eligible = eligibleTracks(previousKey);
        List<Track> weighted = eligible.stream()
            .filter(track -> weightOf(track, weights) > 0)
            .toList();
        if (!weighted.isEmpty()) {
            return new Selection(
                weighted,
                weights == null ? DEFAULT_WEIGHTS : weights
            );
        }
        Map<String, Integer> effective = weights == null ? DEFAULT_WEIGHTS : weights;
        List<Track> configured = TRACKS.stream()
            .filter(track -> weightOf(track, effective) > 0)
            .toList();
        List<Track> withoutPrevious = configured.stream()
            .filter(track -> !track.key().equals(previousKey))
            .toList();
        List<Track> relaxed = withoutPrevious.isEmpty() ? configured : withoutPrevious;
        if (!relaxed.isEmpty()) {
            return new Selection(relaxed, effective);
        }
        return new Selection(eligible, DEFAULT_WEIGHTS);
    }

    private static int weightOf(Track track, Map<String, Integer> weights) {
        if (weights == null) {
            return track.defaultWeight();
        }
        Integer configured = weights.get(track.key());
        if (configured != null) {
            return Math.max(1, Math.min(MAX_WEIGHT, configured));
        }
        return 0;
    }

    private record Selection(List<Track> candidates, Map<String, Integer> weights) {
        private Selection {
            candidates = List.copyOf(candidates);
            weights = Map.copyOf(weights);
        }

        private int total() {
            int total = 0;
            for (Track track : candidates) {
                total = Math.addExact(total, weightOf(track, weights));
            }
            return total;
        }
    }

    private static Map<String, Track> buildKeyIndex() {
        Map<String, Track> index = new LinkedHashMap<>();
        for (Track track : TRACKS) {
            index.put(track.key(), track);
        }
        return Map.copyOf(index);
    }

    private static Map<String, Integer> buildDefaultWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Track track : TRACKS) {
            weights.put(track.key(), track.defaultWeight());
        }
        return Map.copyOf(weights);
    }
}
