package dev.luke.tensuradungeon.client;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record DungeonShopView(
    Wallet wallet,
    String resetLabel,
    List<Offer> offers,
    boolean preview,
    Tab initialTab,
    String statusMessage,
    long revision
) {
    public DungeonShopView(
        String goldBalance,
        String resetLabel,
        List<Offer> offers,
        boolean preview
    ) {
        this(Wallet.fromLegacyGoldLabel(goldBalance), resetLabel, offers, preview, Tab.DAILY, "", 0L);
    }

    public DungeonShopView(
        Wallet wallet,
        String resetLabel,
        List<Offer> offers,
        boolean preview
    ) {
        this(wallet, resetLabel, offers, preview, Tab.DAILY, "", 0L);
    }

    public DungeonShopView {
        wallet = wallet == null ? Wallet.empty() : wallet;
        resetLabel = bounded(resetLabel, 48);
        offers = offers == null ? List.of() : List.copyOf(offers);
        initialTab = Objects.requireNonNull(initialTab, "Dungeon Shop initial tab");
        statusMessage = bounded(statusMessage, 256);
        if (revision < 0L) {
            throw new IllegalArgumentException("Dungeon Shop revision cannot be negative");
        }
        if (offers.size() > 96) {
            throw new IllegalArgumentException("Dungeon Shop view cannot contain more than 96 offers");
        }
        long distinctIds = offers.stream().map(Offer::id).distinct().count();
        if (distinctIds != offers.size()) {
            throw new IllegalArgumentException("Dungeon Shop view contains duplicate offer ids");
        }
    }

    public static DungeonShopView visualPreview() {
        return new DungeonShopView(
            new Wallet(48L, 12L, 1_250L, 3L),
            "23:47:12",
            List.of(
                new Offer(
                    "daily-bracelet", Tab.DAILY, Section.ESSENTIAL, Icon.BRACELET,
                    "Resurrection Bracelet", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "350 GOLD", "Automatically prevents one lethal dungeon hit and preserves inventory.",
                    "Single use", "Normal depths · Floors 1–49", "", "PURCHASE",
                    State.AVAILABLE, 1, 0xFF38D7FF
                ),
                new Offer(
                    "daily-whistle", Tab.DAILY, Section.ESSENTIAL, Icon.WHISTLE,
                    "Return Whistle", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "30 SILVER", "Channels for 5 seconds to leave an active dungeon run.",
                    "Single use", "Normal depths · Floors 1–49", "", "PURCHASE",
                    State.AVAILABLE, 1, 0xFF47C8FF
                ),
                new Offer(
                    "tensura_dungeon:healing_vial", Tab.DAILY, Section.ESSENTIAL, Icon.HEALING,
                    "Healing Vial", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "TBD", "Immediately restores 20% of maximum health without overhealing.",
                    "1.0 sec · Stock TBD", "Shared cooldown: 10 sec", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFFFF4C62
                ),
                new Offer(
                    "tensura_dungeon:healing_flask", Tab.DAILY, Section.ESSENTIAL, Icon.HEALING,
                    "Healing Flask", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "TBD", "Immediately restores 45% of maximum health without overhealing.",
                    "1.6 sec · Stock TBD", "Shared cooldown: 10 sec", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFFFF4C62
                ),
                new Offer(
                    "tensura_dungeon:greater_healing_flask",
                    Tab.DAILY, Section.ESSENTIAL, Icon.HEALING,
                    "Greater Healing Flask", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "TBD", "Immediately restores 75% of maximum health without overhealing.",
                    "2.4 sec · Stock TBD", "Shared cooldown: 10 sec", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFFFF4C62
                ),
                new Offer(
                    "tensura_dungeon:magicule_vial", Tab.DAILY, Section.ESSENTIAL, Icon.MAGICULE,
                    "Magicule Vial", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "TBD", "Immediately restores 20% of maximum magicules.",
                    "1.0 sec · Stock TBD", "Shared cooldown: 10 sec", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFF35A9FF
                ),
                new Offer(
                    "tensura_dungeon:magicule_flask", Tab.DAILY, Section.ESSENTIAL, Icon.MAGICULE,
                    "Magicule Flask", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "TBD", "Immediately restores 45% of maximum magicules.",
                    "1.6 sec · Stock TBD", "Shared cooldown: 10 sec", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFF35A9FF
                ),
                new Offer(
                    "tensura_dungeon:greater_magicule_flask",
                    Tab.DAILY, Section.ESSENTIAL, Icon.MAGICULE,
                    "Greater Magicule Flask", "NORMAL", "Normal", "Recovery", "Dungeon Shop",
                    "TBD", "Immediately restores 75% of maximum magicules.",
                    "2.4 sec · Stock TBD", "Shared cooldown: 10 sec", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFF35A9FF
                ),
                new Offer(
                    "tensura_dungeon:field_ration", Tab.DAILY, Section.ESSENTIAL, Icon.RATION,
                    "Field Ration", "NORMAL", "Normal", "Food", "Dungeon Shop",
                    "TBD",
                    "Grants Well Fed for 30 seconds. Natural regeneration does not consume "
                        + "hunger or saturation while active.",
                    "Grants Well Fed · 30 sec", "Dungeon loadout cap: 2", "Preview only",
                    "BALANCE PENDING", State.LOCKED, 1, 0xFFD6A552
                ),
                new Offer(
                    "daily-satchel", Tab.DAILY, Section.TIMED, Icon.SATCHEL,
                    "Special Core Satchel", "SPECIAL", "Special", "Satchel", "Dungeon Shop",
                    "500 GOLD", "An upgraded Satchel forged to carry twice as many Monster Cores.",
                    "6 slots  →  12 slots", "PURCHASE", State.AVAILABLE, 1, 0xFF30E8E8
                ),
                previewWeapon(
                    "tensura_additions:blacktide_sword", "Blacktide", "Normal",
                    0xFFB7C0CC, "Water-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:gaias_fracture", "Gaia's Fracture", "Special",
                    0xFF35D5CF, "Earth-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:dark_sword", "Runic Frostblade", "Rare",
                    0xFF4B9DFF, "Ice-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:brontide_sword", "Brontide", "Rare",
                    0xFF4B9DFF, "Lightning-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:celestial_oath", "Celestial Oath", "Rare",
                    0xFF4B9DFF, "Celestial dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:venom_sword", "Viper's Malice", "Unique",
                    0xFFB968F0, "Corrosion-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:solar_eclipse_sword", "Solar Eclipse", "Legend",
                    0xFFFFB52E, "Solar-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:violets_sword", "Violet's Edict", "Mythic",
                    0xFFE05DDB, "Flame-attuned dungeon weapon. Final combat values are pending."
                ),
                previewWeapon(
                    "tensura_additions:catastrophe_scythe", "Catastrophe", "Mythic",
                    0xFFE05DDB, "Destructive dungeon scythe. Final combat values are pending."
                ),
                new Offer(
                    "weekly-armor", Tab.WEEKLY, Section.TIMED, Icon.ARMOR,
                    "Tideguard Cuirass", "RARE", "Rare", "Armor", "Kaijin",
                    "1,800 GOLD", "A Weekly Special fulfilled at Kaijin's armor workshop.",
                    "Resets Monday", "VISIT KAIJIN", State.VENDOR_ROUTED, 1, 0xFF47B8FF
                ),
                new Offer(
                    "weekly-charm", Tab.WEEKLY, Section.TIMED, Icon.BRACELET,
                    "Guardian Charm", "RARE", "Rare", "Accessory", "Dungeon Shop",
                    "900 GOLD", "A protective accessory in this week's fixed rotation.",
                    "Once this week", "PURCHASED", State.PURCHASED, 1, 0xFFFFCB45
                ),
                new Offer(
                    "weekly-gothic-quest-hud", Tab.WEEKLY, Section.TIMED, Icon.THEME,
                    "Gothic Quest HUD", "RARE", "Rare", "GUI Theme", "Dungeon Shop",
                    "1,100 GOLD", "A separate permanent Quest HUD theme in the Weekly rotation.",
                    "Permanent unlock", "PURCHASE", State.AVAILABLE, 1, 0xFF9E68FF
                ),
                new Offer(
                    "barter-core-stone", Tab.BARTER, Section.BARTER, Icon.TRADE,
                    "Core Refinement", "NORMAL", "Normal", "Core", "Dungeon Shop",
                    "3 CORES", "Exchange eligible Monster Cores for enhancement materials.",
                    "Lowest Natural Charge selected first", "REVIEW BARTER", State.AVAILABLE, 1,
                    0xFF39DDB7
                ),
                new Offer(
                    "barter-core-cache", Tab.BARTER, Section.BARTER, Icon.TRADE,
                    "Rare Material Cache", "RARE", "Rare", "Material", "Dungeon Shop",
                    "2 CORES", "Exchange higher-tier Cores for a rare material cache.",
                    "High-charge Cores require warning", "REVIEW BARTER", State.AVAILABLE, 1,
                    0xFFB467FF
                ),
                new Offer(
                    "barter-core-coins", Tab.BARTER, Section.BARTER, Icon.TRADE,
                    "Core Coin Exchange", "NORMAL", "Normal", "Currency", "Dungeon Shop",
                    "1 CORE", "Barter an eligible Monster Core for dungeon currency.",
                    "Value reflects Natural Charge", "REVIEW BARTER", State.AVAILABLE, 1,
                    0xFFFFB730
                )
            ),
            true
        );
    }

    public record Wallet(long bronze, long silver, long gold, long stellarGold) {
        public Wallet {
            bronze = Math.max(0L, bronze);
            silver = Math.max(0L, silver);
            gold = Math.max(0L, gold);
            stellarGold = Math.max(0L, stellarGold);
        }

        public static Wallet empty() {
            return new Wallet(0L, 0L, 0L, 0L);
        }

        static Wallet fromLegacyGoldLabel(String label) {
            if (label == null || label.isBlank()) return empty();
            String digits = label.replaceAll("[^0-9]", "");
            if (digits.isBlank()) return empty();
            try {
                return new Wallet(0L, 0L, Long.parseLong(digits), 0L);
            } catch (NumberFormatException exception) {
                return empty();
            }
        }
    }

    private static Offer previewWeapon(
        String id,
        String name,
        String grade,
        int accent,
        String description
    ) {
        return new Offer(
            id,
            Tab.WEEKLY,
            Section.TIMED,
            Icon.WEAPON,
            name,
            grade.toUpperCase(Locale.ROOT),
            grade,
            "Weapon",
            "Kurobe",
            "TBD",
            description,
            "Equipment EP compatible",
            "Combat tuning pending",
            "Preview only",
            "BALANCE PENDING",
            State.LOCKED,
            1,
            accent
        );
    }

    public enum Tab {
        DAILY("DAILY"),
        WEEKLY("WEEKLY"),
        BARTER("BARTER");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Section {
        ESSENTIAL,
        TIMED,
        BARTER
    }

    public enum State {
        AVAILABLE,
        DISPLAY_ONLY,
        PURCHASED,
        OWNED,
        LOCKED,
        SOLD_OUT,
        VENDOR_ROUTED
    }

    public enum Icon {
        BRACELET,
        WHISTLE,
        HEALING,
        MAGICULE,
        RATION,
        SATCHEL,
        CLOAK,
        STONE,
        KEY,
        WEAPON,
        ARMOR,
        CHEST,
        THEME,
        TRADE
    }

    public record Offer(
        String id,
        Tab tab,
        Section section,
        Icon icon,
        String name,
        String rarity,
        String grade,
        String type,
        String vendor,
        String price,
        String description,
        String detail,
        String requirements,
        String availabilityReason,
        String actionLabel,
        State state,
        int quantity,
        int accent,
        boolean unlimited,
        String itemId
    ) {
        public Offer(
            String id,
            Tab tab,
            Section section,
            Icon icon,
            String name,
            String rarity,
            String grade,
            String type,
            String vendor,
            String price,
            String description,
            String detail,
            String actionLabel,
            State state,
            int quantity,
            int accent
        ) {
            this(
                id, tab, section, icon, name, rarity, grade, type, vendor, price,
                description, detail, "", "", actionLabel, state, quantity, accent, false, ""
            );
        }

        public Offer(
            String id,
            Tab tab,
            Section section,
            Icon icon,
            String name,
            String rarity,
            String grade,
            String type,
            String vendor,
            String price,
            String description,
            String detail,
            String requirements,
            String availabilityReason,
            String actionLabel,
            State state,
            int quantity,
            int accent
        ) {
            this(
                id, tab, section, icon, name, rarity, grade, type, vendor, price,
                description, detail, requirements, availabilityReason, actionLabel,
                state, quantity, accent, false, ""
            );
        }

        public Offer(
            String id,
            Tab tab,
            Section section,
            Icon icon,
            String name,
            String rarity,
            String grade,
            String type,
            String vendor,
            String price,
            String description,
            String detail,
            String requirements,
            String availabilityReason,
            String actionLabel,
            State state,
            int quantity,
            int accent,
            boolean unlimited
        ) {
            this(
                id, tab, section, icon, name, rarity, grade, type, vendor, price,
                description, detail, requirements, availabilityReason, actionLabel,
                state, quantity, accent, unlimited, ""
            );
        }

        public Offer {
            id = required(id, "offer id", 64);
            tab = Objects.requireNonNull(tab, "offer tab");
            section = Objects.requireNonNull(section, "offer section");
            icon = Objects.requireNonNull(icon, "offer icon");
            name = required(name, "offer name", 64);
            rarity = bounded(rarity, 24);
            grade = bounded(grade, 24);
            type = bounded(type, 24);
            vendor = bounded(vendor, 32);
            price = bounded(price, 32);
            description = bounded(description, 256);
            detail = bounded(detail, 96);
            requirements = bounded(requirements, 128);
            availabilityReason = bounded(availabilityReason, 96);
            actionLabel = bounded(actionLabel, 32);
            state = Objects.requireNonNull(state, "offer state");
            quantity = Math.max(0, quantity);
            accent |= 0xFF000000;
            itemId = bounded(itemId, 64);
            if (tab == Tab.BARTER && section != Section.BARTER) {
                throw new IllegalArgumentException("Barter offers must use the Barter section");
            }
            if (tab != Tab.BARTER && section == Section.BARTER) {
                throw new IllegalArgumentException("Only Barter-tab offers may use the Barter section");
            }
        }

        public boolean matches(String search, String gradeFilter, String typeFilter, String vendorFilter) {
            String query = normalized(search);
            return (query.isEmpty()
                    || normalized(name).contains(query)
                    || normalized(rarity).contains(query)
                    || normalized(type).contains(query)
                    || normalized(vendor).contains(query))
                && filterMatches(gradeFilter, grade)
                && filterMatches(typeFilter, type)
                && filterMatches(vendorFilter, vendor);
        }

        public boolean actionable() {
            return state == State.AVAILABLE
                || state == State.VENDOR_ROUTED
                || (state == State.LOCKED && tab == Tab.BARTER);
        }

        private static boolean filterMatches(String filter, String value) {
            return filter == null || filter.isBlank() || filter.equalsIgnoreCase("ALL")
                || value.equalsIgnoreCase(filter);
        }
    }

    private static String required(String value, String field, int maximum) {
        String bounded = bounded(value, maximum);
        if (bounded.isBlank()) throw new IllegalArgumentException("Dungeon Shop " + field + " is required");
        return bounded;
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
