package com.mineclawd.tensuradungeon.runtime;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class ForgeMaterialRecoveryPolicy {
    private static final int BASIS_POINTS = 10_000;

    private ForgeMaterialRecoveryPolicy() {}

    public static RecoveryRoll roll(RecoveryRequest request) {
        StableRandom random = new StableRandom(seedFor(request));
        int chanceRoll = random.nextInt(BASIS_POINTS);
        List<MaterialAward> milestone = request.firstClear()
            ? milestoneAwards(request.floor())
            : List.of();
        if (!milestone.isEmpty()) {
            return RecoveryRoll.recovered(true, chanceRoll, -1, milestone);
        }

        MaterialGrade currentGrade = currentGrade(request.floor());
        if (chanceRoll >= request.source().chanceBasisPoints(currentGrade)) {
            return RecoveryRoll.miss(chanceRoll);
        }

        int gradeRoll = random.nextInt(BASIS_POINTS);
        MaterialGrade selectedGrade = selectGrade(currentGrade, gradeRoll, random);
        return RecoveryRoll.recovered(
            false,
            chanceRoll,
            gradeRoll,
            List.of(selectMaterial(selectedGrade, random))
        );
    }

    public static MaterialGrade currentGrade(int floor) {
        if (floor < 1 || floor > 300) {
            throw new IllegalArgumentException("Dungeon floor must be between 1 and 300");
        }
        return MaterialGrade.values()[Math.min(MaterialGrade.values().length - 1, floor / 50)];
    }

    static List<MaterialAward> milestoneAwards(int floor) {
        return switch (floor) {
            case 50 -> List.of(MaterialAward.one("tensura:low_magisteel_ingot"));
            case 100 -> List.of(MaterialAward.one("tensura:high_magisteel_ingot"));
            case 150 -> List.of(MaterialAward.one("tensura:mithril_ingot"));
            case 200 -> List.of(
                MaterialAward.one("tensura:orichalcum_ingot"),
                MaterialAward.one("tensura:pure_magisteel_ingot"),
                MaterialAward.one("tensura:adamantite_ingot")
            );
            case 250 -> List.of(MaterialAward.one("tensura:hihiirokane_ingot"));
            default -> List.of();
        };
    }

    private static MaterialGrade selectGrade(MaterialGrade current, int roll, StableRandom random) {
        int tier = current.ordinal() + 1;
        if (tier == 1) return current;
        if (tier == 2) return roll < 7_500 ? current : MaterialGrade.NORMAL;
        if (tier == 3) {
            if (roll < 6_500) return current;
            if (roll < 9_000) return MaterialGrade.SPECIAL;
            return MaterialGrade.NORMAL;
        }
        if (roll < 6_000) return current;
        if (roll < 8_500) return MaterialGrade.values()[tier - 2];
        if (roll < 9_500) return MaterialGrade.values()[tier - 3];
        return MaterialGrade.values()[random.nextInt(tier - 3)];
    }

    private static MaterialAward selectMaterial(MaterialGrade grade, StableRandom random) {
        if (grade != MaterialGrade.LEGEND) {
            return MaterialAward.one(grade.itemIds().getFirst());
        }
        return MaterialAward.one(grade.itemIds().get(random.nextInt(grade.itemIds().size())));
    }

    private static long seedFor(RecoveryRequest request) {
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = request.runSessionId().getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        hash ^= request.runSeed();
        hash ^= (long) request.floor() << 32;
        hash ^= (long) request.source().ordinal() << 56;
        if (request.firstClear()) hash ^= 0xbb67ae8584caa73bL;
        return mix64(hash);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public enum FloorSource {
        STANDARD(3_000, 1_800, 1_000, 500, 300, 200),
        ELITE(5_500, 4_000, 2_800, 1_700, 1_200, 800),
        BOSS(10_000, 8_500, 6_500, 3_000, 2_000, 1_200);

        private final int[] chances;

        FloorSource(int... chances) {
            this.chances = chances.clone();
        }

        public int chanceBasisPoints(MaterialGrade grade) {
            return chances[grade.ordinal()];
        }

        public static FloorSource classify(int floor, String roomProfile, String objective) {
            currentGrade(floor);
            String profile = normalize(roomProfile);
            String objectiveId = normalize(objective);
            if ("BOSS_CAPABLE".equals(profile) && floor % 10 == 0) {
                return BOSS;
            }
            if ("KILL_ELITE".equals(objectiveId) || "BOSS_CAPABLE".equals(profile)) {
                return ELITE;
            }
            return STANDARD;
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        }
    }

    public enum MaterialGrade {
        NORMAL(List.of("tensura:silver_ingot")),
        SPECIAL(List.of("tensura:low_magisteel_ingot")),
        RARE(List.of("tensura:high_magisteel_ingot")),
        UNIQUE(List.of("tensura:mithril_ingot")),
        LEGEND(List.of(
            "tensura:orichalcum_ingot",
            "tensura:pure_magisteel_ingot",
            "tensura:adamantite_ingot"
        )),
        MYTHIC(List.of("tensura:hihiirokane_ingot"));

        private final List<String> itemIds;

        MaterialGrade(List<String> itemIds) {
            this.itemIds = List.copyOf(itemIds);
        }

        public List<String> itemIds() {
            return itemIds;
        }
    }

    public record RecoveryRequest(
        String runSessionId,
        int floor,
        FloorSource source,
        boolean firstClear,
        long runSeed
    ) {
        public RecoveryRequest {
            if (runSessionId == null || runSessionId.isBlank() || runSessionId.length() > 128) {
                throw new IllegalArgumentException("Forge-material roll requires a 1-128 character run session ID");
            }
            currentGrade(floor);
            if (source == null) {
                throw new IllegalArgumentException("Forge-material floor source is required");
            }
        }
    }

    public record RecoveryRoll(
        boolean recovered,
        boolean guaranteed,
        int chanceRollBasisPoints,
        int gradeRollBasisPoints,
        List<MaterialAward> awards
    ) {
        public RecoveryRoll {
            awards = List.copyOf(awards);
            if (chanceRollBasisPoints < 0 || chanceRollBasisPoints >= BASIS_POINTS) {
                throw new IllegalArgumentException("Forge-material chance roll must be within 0-9,999");
            }
            if (recovered && awards.isEmpty()) {
                throw new IllegalArgumentException("Recovered forge-material roll requires at least one award");
            }
            if (!recovered && (guaranteed || gradeRollBasisPoints != -1 || !awards.isEmpty())) {
                throw new IllegalArgumentException("Missed forge-material roll cannot contain an award");
            }
        }

        private static RecoveryRoll miss(int chanceRoll) {
            return new RecoveryRoll(false, false, chanceRoll, -1, List.of());
        }

        private static RecoveryRoll recovered(
            boolean guaranteed,
            int chanceRoll,
            int gradeRoll,
            List<MaterialAward> awards
        ) {
            return new RecoveryRoll(true, guaranteed, chanceRoll, gradeRoll, awards);
        }
    }

    public record MaterialAward(String itemId, int count) {
        public MaterialAward {
            if (itemId == null || itemId.isBlank() || count <= 0) {
                throw new IllegalArgumentException("Forge-material award requires an item ID and positive count");
            }
        }

        private static MaterialAward one(String itemId) {
            return new MaterialAward(itemId, 1);
        }
    }

    private static final class StableRandom {
        private long state;

        private StableRandom(long seed) {
            state = seed;
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("Random bound must be positive");
            state += 0x9e3779b97f4a7c15L;
            return (int) Long.remainderUnsigned(mix64(state), bound);
        }
    }
}
