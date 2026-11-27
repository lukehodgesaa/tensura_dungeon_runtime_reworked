package dev.luke.tensuradungeon.client;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record KurobeForgeView(
    List<Weapon> weapons,
    List<ServiceState> services,
    List<CoreOption> cores,
    Grade certifiedGrade,
    boolean dungeonServicesLocked,
    Service initialService,
    boolean preview,
    String statusMessage,
    long revision
) {
    public KurobeForgeView {
        weapons = weapons == null ? List.of() : List.copyOf(weapons);
        services = services == null ? List.of() : List.copyOf(services);
        cores = cores == null ? List.of() : List.copyOf(cores);
        certifiedGrade = Objects.requireNonNull(certifiedGrade, "Kurobe certified grade");
        initialService = Objects.requireNonNull(initialService, "Kurobe initial service");
        statusMessage = bounded(statusMessage, 160);
        if (revision < 0L) {
            throw new IllegalArgumentException("Kurobe Forge revision cannot be negative");
        }

        Set<String> weaponIds = new HashSet<>();
        for (Weapon weapon : weapons) {
            Objects.requireNonNull(weapon, "Kurobe weapon");
            if (!weaponIds.add(weapon.id())) {
                throw new IllegalArgumentException("Kurobe Forge contains duplicate weapon IDs");
            }
        }

        Set<String> coreIds = new HashSet<>();
        Set<Integer> coreSlots = new HashSet<>();
        for (CoreOption core : cores) {
            Objects.requireNonNull(core, "Kurobe Core option");
            if (!coreIds.add(core.id()) || !coreSlots.add(core.satchelSlot())) {
                throw new IllegalArgumentException("Kurobe Forge contains duplicate Core selections");
            }
        }

        EnumSet<Service> serviceIds = EnumSet.noneOf(Service.class);
        for (ServiceState state : services) {
            Objects.requireNonNull(state, "Kurobe service state");
            if (!serviceIds.add(state.service())) {
                throw new IllegalArgumentException("Kurobe Forge contains duplicate services");
            }
        }
        if (!serviceIds.equals(EnumSet.allOf(Service.class))) {
            throw new IllegalArgumentException("Kurobe Forge must project every visible service");
        }
    }

    public ServiceState serviceState(Service service) {
        return services.stream()
            .filter(state -> state.service() == service)
            .findFirst()
            .orElseThrow();
    }

    public ServiceState serviceState(Service service, Weapon weapon) {
        if (weapon != null && !weapon.services().isEmpty()) {
            return weapon.services().stream()
                .filter(state -> state.service() == service)
                .findFirst()
                .orElseThrow();
        }

        return serviceState(service);
    }

    public static KurobeForgeView visualPreview() {
        List<ServiceState> previewServices = List.of(
            ServiceState.preview(Service.PROMOTE),
            ServiceState.preview(Service.CONDUIT),
            ServiceState.preview(Service.SOCKET),
            ServiceState.preview(Service.RECHARGE),
            ServiceState.preview(Service.REMOVE)
        );
        return new KurobeForgeView(
            List.of(
                new Weapon(
                    "preview:longsword", "TEMPEST LONGSWORD", Grade.NORMAL,
                    WeaponShape.LONGSWORD, 12_400L, 18_000L, 2,
                    "STORMFANG CORE", "00000000-0000-0000-0000-000000000001", 1,
                    3_800L, 3_100L, 15_000L, 5_000L, -1L, -1L,
                    "minecraft:iron_sword", -1, "", true, previewServices
                ),
                new Weapon(
                    "preview:greatsword", "BLACKFLAME GREATSWORD", Grade.SPECIAL,
                    WeaponShape.GREATSWORD, 32_800L, 52_000L, 4,
                    "EMBER CORE", "00000000-0000-0000-0000-000000000002", 2,
                    19_700L, 14_200L, 50_000L, 35_000L, -1L, -1L,
                    "tensura:iron_great_sword", -1, "", true, previewServices
                ),
                new Weapon(
                    "preview:spear", "SILVER SPEAR", Grade.NORMAL,
                    WeaponShape.SPEAR, 18_000L, 18_000L, 6,
                    "", "", 0, 0L, 0L, 15_000L, 15_000L, -1L, -1L,
                    "tensura:iron_spear", -1, "", true, previewServices
                ),
                new Weapon(
                    "preview:rapier", "RUNE RAPIER", Grade.RARE,
                    WeaponShape.RAPIER, 88_000L, 225_000L, 1,
                    "TIDAL CORE", "00000000-0000-0000-0000-000000000003", 3,
                    54_000L, 37_500L, 150_000L, 66_667L, -1L, -1L,
                    "minecraft:golden_sword", -1, "", true, previewServices
                ),
                new Weapon(
                    "preview:katana", "OBSIDIAN KATANA", Grade.UNIQUE,
                    WeaponShape.KATANA, 310_000L, 750_000L, 3,
                    "VOID CORE", "00000000-0000-0000-0000-000000000004", 4,
                    120_000L, 82_000L, 400_000L, 275_000L, -1L, -1L,
                    "tensura:iron_katana", -1, "", true, previewServices
                ),
                new Weapon(
                    "preview:hammer", "FORGE HAMMER", Grade.LEGEND,
                    WeaponShape.HAMMER, 750_000L, 750_000L, 6,
                    "EARTHSHAPER CORE", "00000000-0000-0000-0000-000000000005", 5,
                    420_000L, 315_000L, 1_000_000L, 1_000_000L, -1L, -1L,
                    "minecraft:mace", -1, "", true, previewServices
                )
            ),
            previewServices,
            List.of(new CoreOption(
                "00000000-0000-0000-0000-000000000006",
                "UNSOCKETED STORMFANG CORE",
                0,
                1,
                3_800L,
                3_100L,
                15_000L,
                true,
                "FREE TUTORIAL SOCKET"
            )),
            Grade.NORMAL,
            false,
            Service.PROMOTE,
            true,
            "UI PREVIEW DATA - FORGE TRANSACTIONS ARE NOT CONNECTED",
            0L
        );
    }

    public enum Service {
        PROMOTE(
            "PROMOTE",
            "Advance this weapon's Equipment Grade after certification, native EP, material, and currency checks pass.",
            "PROMOTE WEAPON"
        ),
        CONDUIT(
            "CONDUIT",
            "Refine Conduit Rank I-VI to increase the charge a socketed Core Technique can channel.",
            "REFINE CONDUIT"
        ),
        SOCKET(
            "SOCKET",
            "Install one exact Monster Core record after the server revalidates the weapon and Core Satchel entry.",
            "SOCKET CORE"
        ),
        RECHARGE(
            "RECHARGE",
            "Restore usable charge. Recharged magicules never restore Natural Charge or appraisal value.",
            "RECHARGE CORE"
        ),
        REMOVE(
            "REMOVE",
            "Safely remove the exact core through the Socket transaction owner and return it to the Core Satchel.",
            "REMOVE CORE"
        );

        private final String label;
        private final String description;
        private final String actionLabel;

        Service(String label, String description, String actionLabel) {
            this.label = label;
            this.description = description;
            this.actionLabel = actionLabel;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public String actionLabel() {
            return actionLabel;
        }

        public Service transactionOwner() {
            return this == REMOVE ? SOCKET : this;
        }
    }

    public enum Grade {
        NORMAL("NORMAL"),
        SPECIAL("SPECIAL"),
        RARE("RARE"),
        UNIQUE("UNIQUE"),
        LEGEND("LEGEND"),
        MYTHIC("MYTHIC"),
        GENESIS("GENESIS / LOCKED");

        private final String label;

        Grade(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum WeaponShape {
        LONGSWORD,
        GREATSWORD,
        SPEAR,
        RAPIER,
        KATANA,
        HAMMER
    }

    public record ServiceState(
        Service service,
        boolean available,
        String costLabel,
        String unavailableReason
    ) {
        public ServiceState {
            service = Objects.requireNonNull(service, "Kurobe service");
            costLabel = bounded(costLabel, 160);
            unavailableReason = bounded(unavailableReason, 160);
            if (available && costLabel.isBlank()) {
                throw new IllegalArgumentException("Available Kurobe service requires a quote");
            }
        }

        static ServiceState preview(Service service) {
            return new ServiceState(
                service,
                false,
                "AUTHORITATIVE QUOTE REQUIRED",
                "SERVER HOOK PENDING"
            );
        }
    }

    public record CoreOption(
        String id,
        String name,
        int satchelSlot,
        int tier,
        long totalCharge,
        long naturalCharge,
        long capacity,
        boolean tutorialFree,
        String socketCostLabel
    ) {
        public CoreOption {
            id = bounded(id, 36);
            name = bounded(name, 64);
            socketCostLabel = bounded(socketCostLabel, 96);
            if (id.isBlank() || name.isBlank() || socketCostLabel.isBlank()
                    || satchelSlot < 0 || satchelSlot >= 48 || tier < 1 || tier > 6
                    || totalCharge < 0L || capacity <= 0L || totalCharge > capacity
                    || naturalCharge < 0L || naturalCharge > totalCharge
                    || (tutorialFree && tier != 1)) {
                throw new IllegalArgumentException("Kurobe Core option is invalid");
            }
        }
    }

    public record Weapon(
        String id,
        String name,
        Grade grade,
        WeaponShape shape,
        long equipmentEp,
        long equipmentEpTarget,
        int conduitRank,
        String coreName,
        String coreId,
        int coreTier,
        long totalCharge,
        long naturalCharge,
        long capacity,
        long accessibleCapacity,
        long burstOutput,
        long flowOutput,
        String itemId,
        int inventorySlot,
        String stackFingerprint,
        boolean forgeDataResolved,
        List<ServiceState> services
    ) {
        public Weapon {
            id = bounded(id, 96);
            name = bounded(name, 64);
            grade = Objects.requireNonNull(grade, "Kurobe weapon grade");
            shape = Objects.requireNonNull(shape, "Kurobe weapon shape");
            coreName = bounded(coreName, 64);
            coreId = bounded(coreId, 36);
            itemId = bounded(itemId, 128);
            stackFingerprint = bounded(stackFingerprint, 64);
            services = services == null ? List.of() : List.copyOf(services);
            if (id.isBlank() || name.isBlank()) {
                throw new IllegalArgumentException("Kurobe weapon identity cannot be blank");
            }
            if (itemId.isBlank() || inventorySlot < -1 || inventorySlot > 35) {
                throw new IllegalArgumentException("Kurobe inventory identity is invalid");
            }
            if (!forgeDataResolved) {
                if (equipmentEp != -1L || equipmentEpTarget != -1L || conduitRank != 0
                        || !coreName.isBlank() || !coreId.isBlank() || coreTier != 0 || totalCharge != -1L
                        || naturalCharge != -1L || capacity != -1L || accessibleCapacity != -1L
                        || burstOutput != -1L
                        || flowOutput != -1L) {
                    throw new IllegalArgumentException(
                        "Unresolved Kurobe weapons cannot project authoritative forge data"
                    );
                }
            } else {
            if (equipmentEp < 0L || equipmentEpTarget <= 0L || equipmentEp > equipmentEpTarget) {
                throw new IllegalArgumentException("Kurobe weapon EP is invalid");
            }
            if (conduitRank < 0 || conduitRank > 6 || accessibleCapacity < 0L) {
                throw new IllegalArgumentException("Kurobe Conduit Rank must be unrefined or I-VI");
            }
            if (coreTier < 0 || coreTier > 6 || totalCharge < 0L || naturalCharge < 0L
                    || capacity < 0L || totalCharge > capacity || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Kurobe core state is invalid");
            }
            if (coreName.isBlank() != (coreTier == 0) || coreId.isBlank() != (coreTier == 0)) {
                throw new IllegalArgumentException("Kurobe core identity and tier disagree");
            }
            if (coreTier == 0 && (totalCharge != 0L || naturalCharge != 0L)) {
                throw new IllegalArgumentException("Empty Kurobe socket cannot carry charge");
            }
            if (burstOutput < -1L || flowOutput < -1L) {
                throw new IllegalArgumentException("Kurobe output values cannot be below unresolved");
            }
            EnumSet<Service> serviceIds = EnumSet.noneOf(Service.class);
            for (ServiceState service : services) {
                if (!serviceIds.add(service.service())) {
                    throw new IllegalArgumentException("Kurobe weapon has duplicate service quotes");
                }
            }
            if (!serviceIds.equals(EnumSet.allOf(Service.class))) {
                throw new IllegalArgumentException("Resolved Kurobe weapon requires every service quote");
            }
            }
        }

        public boolean socketed() {
            return forgeDataResolved && coreTier > 0;
        }

        public String conduitLabel() {
            return forgeDataResolved
                ? conduitRank == 0 ? "CONDUIT UNREFINED" : "CONDUIT " + roman(conduitRank)
                : "FORGE DATA PENDING";
        }

        private static String roman(int value) {
            return switch (value) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                case 6 -> "VI";
                default -> "-";
            };
        }
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        String stripped = value.strip();
        return stripped.length() <= maximum ? stripped : stripped.substring(0, maximum);
    }
}
