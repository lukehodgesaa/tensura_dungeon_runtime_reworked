package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record KurobeForgeSnapshotPayload(
    int schemaVersion,
    long revision,
    long requestId,
    boolean openRequested,
    Service initialService,
    Grade certifiedGrade,
    boolean dungeonServicesLocked,
    List<ServiceState> services,
    List<Weapon> weapons,
    List<CoreOption> cores,
    Status status
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 3;
    public static final int MAX_WEAPONS = 36;
    public static final int MAX_CORES = 48;
    public static final Type<KurobeForgeSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "kurobe_forge_snapshot")
    );
    public static final StreamCodec<FriendlyByteBuf, KurobeForgeSnapshotPayload> STREAM_CODEC =
        StreamCodec.of(KurobeForgeSnapshotPayload::write, KurobeForgeSnapshotPayload::read);

    public KurobeForgeSnapshotPayload {
        if (schemaVersion < 0 || revision < 0L || requestId < 0L) {
            throw new IllegalArgumentException("Kurobe Forge snapshot identity is invalid");
        }
        initialService = Objects.requireNonNull(initialService, "Kurobe initial service");
        certifiedGrade = Objects.requireNonNull(certifiedGrade, "Kurobe certification grade");
        services = checkedServices(services, "snapshot");
        weapons = weapons == null ? List.of() : List.copyOf(weapons);
        cores = cores == null ? List.of() : List.copyOf(cores);
        status = status == null ? Status.ready() : status;
        if (weapons.size() > MAX_WEAPONS || cores.size() > MAX_CORES) {
            throw new IllegalArgumentException("Kurobe Forge snapshot exceeds bounded inventory data");
        }
        Set<String> weaponIds = new HashSet<>();
        for (Weapon weapon : weapons) {
            Objects.requireNonNull(weapon, "Kurobe weapon");
            if (!weaponIds.add(weapon.id())) {
                throw new IllegalArgumentException("Duplicate Kurobe weapon id " + weapon.id());
            }
        }
        Set<String> coreIds = new HashSet<>();
        Set<Integer> coreSlots = new HashSet<>();
        for (CoreOption core : cores) {
            Objects.requireNonNull(core, "Kurobe Core option");
            if (!coreIds.add(core.id()) || !coreSlots.add(core.satchelSlot())) {
                throw new IllegalArgumentException("Duplicate Kurobe Core identity or Satchel slot");
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

    public Weapon weapon(String id) {
        if (id == null) return null;
        return weapons.stream().filter(weapon -> weapon.id().equals(id)).findFirst().orElse(null);
    }

    public CoreOption core(String id, int slot) {
        if (id == null || slot < 0) return null;
        return cores.stream()
            .filter(core -> core.id().equals(id) && core.satchelSlot() == slot)
            .findFirst()
            .orElse(null);
    }

    private static void write(FriendlyByteBuf buffer, KurobeForgeSnapshotPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarLong(payload.requestId);
        buffer.writeBoolean(payload.openRequested);
        buffer.writeEnum(payload.initialService);
        buffer.writeEnum(payload.certifiedGrade);
        buffer.writeBoolean(payload.dungeonServicesLocked);
        writeServices(buffer, payload.services);
        buffer.writeVarInt(payload.weapons.size());
        payload.weapons.forEach(weapon -> weapon.write(buffer));
        buffer.writeVarInt(payload.cores.size());
        payload.cores.forEach(core -> core.write(buffer));
        payload.status.write(buffer);
    }

    private static KurobeForgeSnapshotPayload read(FriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        long revision = buffer.readVarLong();
        long requestId = buffer.readVarLong();
        boolean openRequested = buffer.readBoolean();
        Service initialService = buffer.readEnum(Service.class);
        Grade certifiedGrade = buffer.readEnum(Grade.class);
        boolean dungeonServicesLocked = buffer.readBoolean();
        List<ServiceState> services = readServices(buffer);
        int weaponCount = boundedCount(buffer.readVarInt(), MAX_WEAPONS, "weapon");
        List<Weapon> weapons = new ArrayList<>(weaponCount);
        for (int index = 0; index < weaponCount; index++) weapons.add(Weapon.read(buffer));
        int coreCount = boundedCount(buffer.readVarInt(), MAX_CORES, "Core");
        List<CoreOption> cores = new ArrayList<>(coreCount);
        for (int index = 0; index < coreCount; index++) cores.add(CoreOption.read(buffer));
        return new KurobeForgeSnapshotPayload(
            schema, revision, requestId, openRequested, initialService, certifiedGrade,
            dungeonServicesLocked, services, weapons, cores, Status.read(buffer)
        );
    }

    private static int boundedCount(int count, int maximum, String owner) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid Kurobe " + owner + " count " + count);
        }
        return count;
    }

    private static void writeServices(FriendlyByteBuf buffer, List<ServiceState> services) {
        buffer.writeVarInt(services.size());
        services.forEach(service -> service.write(buffer));
    }

    private static List<ServiceState> readServices(FriendlyByteBuf buffer) {
        int count = boundedCount(buffer.readVarInt(), Service.values().length, "service");
        List<ServiceState> services = new ArrayList<>(count);
        for (int index = 0; index < count; index++) services.add(ServiceState.read(buffer));
        return services;
    }

    private static List<ServiceState> checkedServices(List<ServiceState> input, String owner) {
        List<ServiceState> checked = input == null ? List.of() : List.copyOf(input);
        EnumSet<Service> ids = EnumSet.noneOf(Service.class);
        for (ServiceState state : checked) {
            Objects.requireNonNull(state, "Kurobe service state");
            if (!ids.add(state.service())) {
                throw new IllegalArgumentException("Duplicate Kurobe " + owner + " service " + state.service());
            }
        }
        if (!ids.equals(EnumSet.allOf(Service.class))) {
            throw new IllegalArgumentException("Kurobe " + owner + " must project every service");
        }
        return checked;
    }

    public enum Service { PROMOTE, CONDUIT, SOCKET, RECHARGE, REMOVE }
    public enum Grade { NORMAL, SPECIAL, RARE, UNIQUE, LEGEND, MYTHIC, GENESIS }
    public enum WeaponShape { LONGSWORD, GREATSWORD, SPEAR, RAPIER, KATANA, HAMMER }

    public record ServiceState(
        Service service,
        boolean available,
        String costLabel,
        String unavailableReason
    ) {
        public ServiceState {
            service = Objects.requireNonNull(service, "Kurobe service");
            costLabel = text(costLabel, 160, "service cost");
            unavailableReason = text(unavailableReason, 160, "service reason");
            if (available && costLabel.isBlank()) {
                throw new IllegalArgumentException("Available Kurobe service requires a quote");
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(service);
            buffer.writeBoolean(available);
            buffer.writeUtf(costLabel, 160);
            buffer.writeUtf(unavailableReason, 160);
        }

        private static ServiceState read(FriendlyByteBuf buffer) {
            return new ServiceState(
                buffer.readEnum(Service.class), buffer.readBoolean(), buffer.readUtf(160),
                buffer.readUtf(160)
            );
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
        List<ServiceState> services
    ) {
        public Weapon {
            id = required(id, 160, "weapon id");
            name = required(name, 64, "weapon name");
            grade = Objects.requireNonNull(grade, "Kurobe weapon grade");
            shape = Objects.requireNonNull(shape, "Kurobe weapon shape");
            coreName = text(coreName, 64, "core name");
            coreId = text(coreId, 36, "core id");
            itemId = required(itemId, 128, "weapon item id");
            stackFingerprint = required(stackFingerprint, 64, "weapon fingerprint");
            services = checkedServices(services, "weapon");
            if (inventorySlot < 0 || inventorySlot > 35) {
                throw new IllegalArgumentException("Kurobe inventory slot is invalid");
            }
            if (equipmentEp < 0L || equipmentEpTarget <= 0L || equipmentEp > equipmentEpTarget) {
                throw new IllegalArgumentException("Kurobe weapon EP is invalid");
            }
            if (conduitRank < 0 || conduitRank > 6 || accessibleCapacity < 0L) {
                throw new IllegalArgumentException("Kurobe Conduit state is invalid");
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
                throw new IllegalArgumentException("Kurobe output values are invalid");
            }
        }

        public ServiceState serviceState(Service service) {
            return services.stream().filter(state -> state.service() == service).findFirst().orElseThrow();
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 160);
            buffer.writeUtf(name, 64);
            buffer.writeEnum(grade);
            buffer.writeEnum(shape);
            buffer.writeVarLong(equipmentEp);
            buffer.writeVarLong(equipmentEpTarget);
            buffer.writeVarInt(conduitRank);
            buffer.writeUtf(coreName, 64);
            buffer.writeUtf(coreId, 36);
            buffer.writeVarInt(coreTier);
            buffer.writeVarLong(totalCharge);
            buffer.writeVarLong(naturalCharge);
            buffer.writeVarLong(capacity);
            buffer.writeVarLong(accessibleCapacity);
            buffer.writeVarLong(burstOutput + 1L);
            buffer.writeVarLong(flowOutput + 1L);
            buffer.writeUtf(itemId, 128);
            buffer.writeVarInt(inventorySlot);
            buffer.writeUtf(stackFingerprint, 64);
            writeServices(buffer, services);
        }

        private static Weapon read(FriendlyByteBuf buffer) {
            return new Weapon(
                buffer.readUtf(160), buffer.readUtf(64), buffer.readEnum(Grade.class),
                buffer.readEnum(WeaponShape.class), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(36), buffer.readVarInt(),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readVarLong() - 1L, buffer.readVarLong() - 1L, buffer.readUtf(128),
                buffer.readVarInt(), buffer.readUtf(64), readServices(buffer)
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
            id = required(id, 36, "Core id");
            name = required(name, 64, "Core name");
            socketCostLabel = required(socketCostLabel, 96, "Core socket quote");
            if (satchelSlot < 0 || satchelSlot >= MAX_CORES || tier < 1 || tier > 6
                || totalCharge < 0L || capacity <= 0L || totalCharge > capacity
                || naturalCharge < 0L || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Kurobe Satchel Core state is invalid");
            }
            if (tutorialFree && tier != 1) {
                throw new IllegalArgumentException("Only a Tier I Core may use tutorial socketing");
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 36);
            buffer.writeUtf(name, 64);
            buffer.writeVarInt(satchelSlot);
            buffer.writeVarInt(tier);
            buffer.writeVarLong(totalCharge);
            buffer.writeVarLong(naturalCharge);
            buffer.writeVarLong(capacity);
            buffer.writeBoolean(tutorialFree);
            buffer.writeUtf(socketCostLabel, 96);
        }

        private static CoreOption read(FriendlyByteBuf buffer) {
            return new CoreOption(
                buffer.readUtf(36), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readBoolean(), buffer.readUtf(96)
            );
        }
    }

    public record Status(String code, String message) {
        public Status {
            code = required(code, 48, "status code");
            message = text(message, 256, "status message");
        }

        public static Status ready() { return new Status("READY", ""); }

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
        if (checked.isBlank()) throw new IllegalArgumentException("Kurobe " + field + " is required");
        return checked;
    }

    private static String text(String value, int maximum, String field) {
        String checked = value == null ? "" : value;
        if (checked.length() > maximum) {
            throw new IllegalArgumentException("Kurobe " + field + " exceeds " + maximum + " characters");
        }
        return checked;
    }
}
