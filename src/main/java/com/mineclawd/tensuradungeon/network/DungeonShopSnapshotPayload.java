package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonShopSnapshotPayload(
    int schemaVersion,
    long revision,
    long requestId,
    boolean openRequested,
    Tab activeTab,
    ResetWindow resets,
    Wallet wallet,
    List<Offer> offers,
    Status status
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 5;
    public static final int MAX_OFFERS = 96;
    public static final Type<DungeonShopSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_shop_snapshot")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonShopSnapshotPayload> STREAM_CODEC =
        StreamCodec.of(DungeonShopSnapshotPayload::write, DungeonShopSnapshotPayload::read);

    public DungeonShopSnapshotPayload {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("Dungeon Shop schema version cannot be negative");
        }
        if (revision < 0L || requestId < 0L) {
            throw new IllegalArgumentException("Dungeon Shop revision and request identity cannot be negative");
        }
        activeTab = Objects.requireNonNull(activeTab, "Dungeon Shop active tab");
        resets = resets == null ? ResetWindow.unavailable() : resets;
        wallet = wallet == null ? Wallet.empty() : wallet;
        offers = offers == null ? List.of() : List.copyOf(offers);
        status = status == null ? Status.ready() : status;
        if (offers.size() > MAX_OFFERS) {
            throw new IllegalArgumentException("Dungeon Shop snapshot exceeds " + MAX_OFFERS + " offers");
        }
        Set<String> ids = new HashSet<>();
        for (Offer offer : offers) {
            if (offer == null) {
                throw new IllegalArgumentException("Dungeon Shop offers cannot contain null");
            }
            if (offer.tab() != activeTab) {
                throw new IllegalArgumentException("Dungeon Shop offer tab does not match the active tab");
            }
            if (!ids.add(offer.id())) {
                throw new IllegalArgumentException("Dungeon Shop snapshot contains duplicate offer id " + offer.id());
            }
        }
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonShopSnapshotPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarLong(payload.requestId);
        buffer.writeBoolean(payload.openRequested);
        buffer.writeEnum(payload.activeTab);
        payload.resets.write(buffer);
        payload.wallet.write(buffer);
        buffer.writeVarInt(payload.offers.size());
        payload.offers.forEach(offer -> offer.write(buffer));
        payload.status.write(buffer);
    }

    private static DungeonShopSnapshotPayload read(FriendlyByteBuf buffer) {
        int schemaVersion = buffer.readVarInt();
        long revision = buffer.readVarLong();
        long requestId = buffer.readVarLong();
        boolean openRequested = buffer.readBoolean();
        Tab activeTab = buffer.readEnum(Tab.class);
        ResetWindow resets = ResetWindow.read(buffer);
        Wallet wallet = Wallet.read(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_OFFERS) {
            throw new IllegalArgumentException("Invalid Dungeon Shop offer count: " + count);
        }
        List<Offer> offers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            offers.add(Offer.read(buffer));
        }
        return new DungeonShopSnapshotPayload(
            schemaVersion,
            revision,
            requestId,
            openRequested,
            activeTab,
            resets,
            wallet,
            offers,
            Status.read(buffer)
        );
    }

    public enum Tab {
        DAILY,
        WEEKLY,
        BARTER
    }

    public enum Section {
        ESSENTIAL,
        TIMED,
        BARTER
    }

    public enum Icon {
        BRACELET,
        WHISTLE,
        HEALING,
        MAGICULE,
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

    public enum AvailabilityState {
        AVAILABLE,
        PURCHASED,
        OWNED,
        LOCKED,
        SOLD_OUT,
        VENDOR_ROUTED,
        DISPLAY_ONLY
    }

    public enum Action {
        PURCHASE,
        BARTER,
        VISIT_KUROBE,
        VISIT_KAIJIN,
        NONE
    }

    public record ResetWindow(
        String dailyKey,
        String dailyLabel,
        String weeklyKey,
        String weeklyLabel
    ) {
        public ResetWindow {
            dailyKey = text(dailyKey, 64, "daily reset key");
            dailyLabel = text(dailyLabel, 48, "daily reset label");
            weeklyKey = text(weeklyKey, 64, "weekly reset key");
            weeklyLabel = text(weeklyLabel, 48, "weekly reset label");
        }

        public static ResetWindow unavailable() {
            return new ResetWindow("", "Not configured", "", "Not configured");
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(dailyKey, 64);
            buffer.writeUtf(dailyLabel, 48);
            buffer.writeUtf(weeklyKey, 64);
            buffer.writeUtf(weeklyLabel, 48);
        }

        private static ResetWindow read(FriendlyByteBuf buffer) {
            return new ResetWindow(
                buffer.readUtf(64),
                buffer.readUtf(48),
                buffer.readUtf(64),
                buffer.readUtf(48)
            );
        }
    }

    public record Wallet(long bronze, long silver, long gold, long stellarGold) {
        public Wallet {
            if (bronze < 0L || silver < 0L || gold < 0L || stellarGold < 0L) {
                throw new IllegalArgumentException("Dungeon Shop wallet values cannot be negative");
            }
        }

        public static Wallet empty() {
            return new Wallet(0L, 0L, 0L, 0L);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(bronze);
            buffer.writeVarLong(silver);
            buffer.writeVarLong(gold);
            buffer.writeVarLong(stellarGold);
        }

        private static Wallet read(FriendlyByteBuf buffer) {
            return new Wallet(
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong()
            );
        }
    }

    public record Offer(
        String id,
        CosmeticKey cosmetic,
        Tab tab,
        Section section,
        Display display,
        Price price,
        Availability availability,
        Routing routing,
        Detail detail
    ) {
        public Offer {
            id = required(id, 64, "offer id");
            tab = Objects.requireNonNull(tab, "Dungeon Shop offer tab");
            section = Objects.requireNonNull(section, "Dungeon Shop offer section");
            display = Objects.requireNonNull(display, "Dungeon Shop offer display");
            price = price == null ? Price.none() : price;
            availability = Objects.requireNonNull(availability, "Dungeon Shop offer availability");
            routing = Objects.requireNonNull(routing, "Dungeon Shop offer routing");
            detail = detail == null ? Detail.empty() : detail;
            if (display.icon() == Icon.THEME) {
                if (cosmetic == null || !cosmetic.cataloged()) {
                    throw new IllegalArgumentException("Theme offers require a cataloged typed cosmetic key");
                }
                if (!id.equals(approvedThemeCatalogId(cosmetic))) {
                    throw new IllegalArgumentException(
                        "Theme offer id must equal its approved Shop catalog identity"
                    );
                }
            } else if (cosmetic != null) {
                throw new IllegalArgumentException("Only theme offers may carry a cosmetic identity");
            }
            if (tab == Tab.BARTER && section != Section.BARTER) {
                throw new IllegalArgumentException("Barter offers must use the Barter section");
            }
            if (tab != Tab.BARTER && section == Section.BARTER) {
                throw new IllegalArgumentException("Only Barter offers may use the Barter section");
            }
            if (availability.state() == AvailabilityState.VENDOR_ROUTED
                && routing.action() != Action.VISIT_KUROBE
                && routing.action() != Action.VISIT_KAIJIN) {
                throw new IllegalArgumentException("Vendor-routed offers require an explicit supported vendor action");
            }
            if (availability.state() != AvailabilityState.VENDOR_ROUTED
                && (routing.action() == Action.VISIT_KUROBE || routing.action() == Action.VISIT_KAIJIN)) {
                throw new IllegalArgumentException("Vendor actions require vendor-routed availability");
            }
            if (tab == Tab.BARTER && routing.action() != Action.BARTER
                && routing.action() != Action.NONE) {
                throw new IllegalArgumentException("Barter offers cannot use a purchase or vendor action");
            }
            if (tab != Tab.BARTER && routing.action() == Action.BARTER) {
                throw new IllegalArgumentException("Only Barter offers may use the barter action");
            }
        }

        private static String approvedThemeCatalogId(CosmeticKey key) {
            if (key.equals(CosmeticKey.HUD_GOTHIC)) {
                return "tensura_dungeon:gothic_hud_theme";
            }
            if (key.equals(CosmeticKey.HUD_FREY)) {
                return "tensura_dungeon:frey_hud_theme";
            }
            if (key.equals(CosmeticKey.QUEST_GOTHIC)) {
                return "tensura_dungeon:gothic_quest_hud_theme";
            }
            throw new IllegalArgumentException(
                "Cosmetic has no approved Dungeon Shop catalog identity: " + key.canonicalId()
            );
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeBoolean(cosmetic != null);
            if (cosmetic != null) {
                DungeonCosmeticSnapshotPayload.writeKey(buffer, cosmetic);
            }
            buffer.writeEnum(tab);
            buffer.writeEnum(section);
            display.write(buffer);
            price.write(buffer);
            availability.write(buffer);
            routing.write(buffer);
            detail.write(buffer);
        }

        private static Offer read(FriendlyByteBuf buffer) {
            return new Offer(
                buffer.readUtf(64),
                buffer.readBoolean() ? DungeonCosmeticSnapshotPayload.readKey(buffer) : null,
                buffer.readEnum(Tab.class),
                buffer.readEnum(Section.class),
                Display.read(buffer),
                Price.read(buffer),
                Availability.read(buffer),
                Routing.read(buffer),
                Detail.read(buffer)
            );
        }
    }

    public record Display(
        Icon icon,
        String name,
        String rarity,
        String grade,
        String type,
        int accent,
        String itemId
    ) {
        public Display(
            Icon icon,
            String name,
            String rarity,
            String grade,
            String type,
            int accent
        ) {
            this(icon, name, rarity, grade, type, accent, "");
        }

        public Display {
            icon = Objects.requireNonNull(icon, "Dungeon Shop offer icon");
            name = required(name, 64, "offer name");
            rarity = text(rarity, 24, "offer rarity");
            grade = text(grade, 24, "offer grade");
            type = text(type, 24, "offer type");
            accent |= 0xFF000000;
            itemId = text(itemId, 64, "offer display item id");
            if (!itemId.isBlank() && ResourceLocation.tryParse(itemId) == null) {
                throw new IllegalArgumentException(
                    "Dungeon Shop display item id must be a namespaced resource id"
                );
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(icon);
            buffer.writeUtf(name, 64);
            buffer.writeUtf(rarity, 24);
            buffer.writeUtf(grade, 24);
            buffer.writeUtf(type, 24);
            buffer.writeInt(accent);
            buffer.writeUtf(itemId, 64);
        }

        private static Display read(FriendlyByteBuf buffer) {
            return new Display(
                buffer.readEnum(Icon.class),
                buffer.readUtf(64),
                buffer.readUtf(24),
                buffer.readUtf(24),
                buffer.readUtf(24),
                buffer.readInt(),
                buffer.readUtf(64)
            );
        }
    }

    public record Price(
        String label,
        long bronze,
        long silver,
        long gold,
        long stellarGold
    ) {
        public Price {
            label = text(label, 48, "offer price label");
            if (bronze < 0L || silver < 0L || gold < 0L || stellarGold < 0L) {
                throw new IllegalArgumentException("Dungeon Shop price values cannot be negative");
            }
        }

        public static Price none() {
            return new Price("", 0L, 0L, 0L, 0L);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(label, 48);
            buffer.writeVarLong(bronze);
            buffer.writeVarLong(silver);
            buffer.writeVarLong(gold);
            buffer.writeVarLong(stellarGold);
        }

        private static Price read(FriendlyByteBuf buffer) {
            return new Price(
                buffer.readUtf(48),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong()
            );
        }
    }

    public record Availability(
        AvailabilityState state,
        int quantity,
        String reason,
        boolean unlimited
    ) {
        public Availability(AvailabilityState state, int quantity, String reason) {
            this(state, quantity, reason, false);
        }

        public Availability {
            state = Objects.requireNonNull(state, "Dungeon Shop availability state");
            if (quantity < 0 || quantity > 999_999) {
                throw new IllegalArgumentException("Dungeon Shop quantity is outside 0-999999");
            }
            reason = text(reason, 96, "offer availability reason");
            if (unlimited && state != AvailabilityState.AVAILABLE) {
                throw new IllegalArgumentException("Only available offers may be unlimited");
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(state);
            buffer.writeVarInt(quantity);
            buffer.writeUtf(reason, 96);
            buffer.writeBoolean(unlimited);
        }

        private static Availability read(FriendlyByteBuf buffer) {
            return new Availability(
                buffer.readEnum(AvailabilityState.class),
                buffer.readVarInt(),
                buffer.readUtf(96),
                buffer.readBoolean()
            );
        }
    }

    public record Routing(String vendor, Action action, String actionLabel) {
        public Routing {
            vendor = text(vendor, 32, "offer vendor");
            action = Objects.requireNonNull(action, "Dungeon Shop offer action");
            actionLabel = text(actionLabel, 32, "offer action label");
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(vendor, 32);
            buffer.writeEnum(action);
            buffer.writeUtf(actionLabel, 32);
        }

        private static Routing read(FriendlyByteBuf buffer) {
            return new Routing(buffer.readUtf(32), buffer.readEnum(Action.class), buffer.readUtf(32));
        }
    }

    public record Detail(String description, String comparison, String requirements) {
        public Detail {
            description = text(description, 256, "offer description");
            comparison = text(comparison, 96, "offer comparison");
            requirements = text(requirements, 128, "offer requirements");
        }

        public static Detail empty() {
            return new Detail("", "", "");
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(description, 256);
            buffer.writeUtf(comparison, 96);
            buffer.writeUtf(requirements, 128);
        }

        private static Detail read(FriendlyByteBuf buffer) {
            return new Detail(buffer.readUtf(256), buffer.readUtf(96), buffer.readUtf(128));
        }
    }

    public record Status(String code, String message) {
        public Status {
            code = required(code, 48, "status code");
            message = text(message, 256, "status message");
        }

        public static Status ready() {
            return new Status("READY", "");
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(code, 48);
            buffer.writeUtf(message, 256);
        }

        private static Status read(FriendlyByteBuf buffer) {
            return new Status(buffer.readUtf(48), buffer.readUtf(256));
        }
    }

    private static String required(String value, int maximum, String field) {
        String checked = text(value, maximum, field);
        if (checked.isBlank()) {
            throw new IllegalArgumentException("Dungeon Shop " + field + " is required");
        }
        return checked;
    }

    private static String text(String value, int maximum, String field) {
        String checked = value == null ? "" : value;
        if (checked.length() > maximum) {
            throw new IllegalArgumentException(
                "Dungeon Shop " + field + " exceeds " + maximum + " characters"
            );
        }
        return checked;
    }
}
