package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.item.SoulOrbRarity;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelData;
import com.mineclawd.tensuradungeon.runtime.DungeonBankData;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.SoulOrbHaulData;
import com.mineclawd.tensuradungeon.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record LootSummaryPayload(
    int schemaVersion,
    boolean openRequested,
    boolean available,
    String sessionId,
    int floor,
    String lifecycle,
    long revision,
    int common,
    int uncommon,
    int rare,
    int epic,
    boolean canAppraise,
    boolean canKeepAll,
    boolean canQuickSell,
    boolean canSellAll,
    boolean exitOptionsAvailable,
    int nextFloor,
    Wallet wallet,
    RunOverview overview,
    List<Entry> entries,
    List<RecoveredCore> recoveredCores,
    String status,
    String message
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 7;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_RECOVERED_CORES = 48;
    private static final int MAX_ENEMY_DEFEATS = 128;
    private static final List<TagKey<Item>> METAL_TAGS = List.of(
        itemTag("c", "ingots"),
        itemTag("c", "nuggets"),
        itemTag("c", "raw_materials"),
        itemTag("c", "ores")
    );
    public static final Type<LootSummaryPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "loot_summary")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, LootSummaryPayload> STREAM_CODEC =
        StreamCodec.of(LootSummaryPayload::write, LootSummaryPayload::read);

    public LootSummaryPayload {
        schemaVersion = Math.max(0, schemaVersion);
        sessionId = bounded(sessionId, 96);
        floor = Math.max(0, floor);
        lifecycle = bounded(lifecycle, 32);
        revision = Math.max(0L, revision);
        common = Math.max(0, common);
        uncommon = Math.max(0, uncommon);
        rare = Math.max(0, rare);
        epic = Math.max(0, epic);
        nextFloor = exitOptionsAvailable ? Math.max(floor + 1, nextFloor) : 0;
        wallet = wallet == null ? Wallet.empty() : wallet;
        overview = overview == null ? RunOverview.empty() : overview;
        entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(MAX_ENTRIES, entries.size())));
        recoveredCores = recoveredCores == null
            ? List.of()
            : List.copyOf(recoveredCores.subList(0, Math.min(MAX_RECOVERED_CORES, recoveredCores.size())));
        status = bounded(status, 48);
        message = bounded(message, 256);
    }

    public static LootSummaryPayload from(
        SoulOrbHaulData.Summary summary,
        SoulOrbHaulData.ActionStatus status,
        String message,
        boolean openRequested
    ) {
        return from(summary, status, message, openRequested, false, 0, List.of());
    }

    public static LootSummaryPayload from(
        SoulOrbHaulData.Summary summary,
        SoulOrbHaulData.ActionStatus status,
        String message,
        boolean openRequested,
        boolean exitOptionsAvailable,
        int nextFloor
    ) {
        return from(summary, status, message, openRequested, exitOptionsAvailable, nextFloor, List.of());
    }

    public static LootSummaryPayload from(
        SoulOrbHaulData.Summary summary,
        SoulOrbHaulData.ActionStatus status,
        String message,
        boolean openRequested,
        boolean exitOptionsAvailable,
        int nextFloor,
        List<CoreSatchelData.CoreRecord> recoveredCores
    ) {
        return project(
            summary, status, message, openRequested, exitOptionsAvailable, nextFloor,
            recoveredCores, Wallet.empty(), RunOverview.empty()
        );
    }

    public static LootSummaryPayload from(
        ServerPlayer player,
        SoulOrbHaulData.Summary summary,
        SoulOrbHaulData.ActionStatus status,
        String message,
        boolean openRequested,
        boolean exitOptionsAvailable,
        int nextFloor,
        List<CoreSatchelData.CoreRecord> recoveredCores
    ) {
        DungeonFloorProgressData progress = DungeonFloorProgressData.get(player.serverLevel());
        DungeonFloorProgressData.CompletedRunSummary completed = progress.getCompletedRun(
            player.getUUID(), summary.sessionId()
        );
        return project(
            summary, status, message, openRequested, exitOptionsAvailable, nextFloor,
            recoveredCores, Wallet.from(player),
            RunOverview.from(completed, summary, progress.isPersonalBest(completed))
        );
    }

    private static LootSummaryPayload project(
        SoulOrbHaulData.Summary summary,
        SoulOrbHaulData.ActionStatus status,
        String message,
        boolean openRequested,
        boolean exitOptionsAvailable,
        int nextFloor,
        List<CoreSatchelData.CoreRecord> recoveredCores,
        Wallet wallet,
        RunOverview overview
    ) {
        SoulOrbHaulData.Counts counts = summary.counts();
        return new LootSummaryPayload(
            CURRENT_SCHEMA, openRequested, summary.available(), summary.sessionId(), summary.floor(),
            summary.lifecycle().name(), summary.revision(), counts.common(), counts.uncommon(),
            counts.rare(), counts.epic(), summary.canAppraise(), summary.canKeepAll(),
            summary.canQuickSell(), summary.canSellAll(), exitOptionsAvailable, nextFloor,
            wallet, overview,
            java.util.stream.Stream.concat(
                summary.entries().stream()
                    .filter(entry -> !isMonsterCrystal(entry.stack()))
                .map(entry -> new Entry(
                    entry.entryId(), entry.stack(), entry.rarity().name(), entry.disposition().name(),
                    entry.sellable(), entry.unavailableReason(), null
                )),
                recoveredCores.stream().map(core -> new Entry(
                    "core:" + core.coreId(),
                    coreStack(core.tier(), core.displayName()),
                    "CORE",
                    "CORE_PENDING",
                    false,
                    "",
                    RecoveredCore.from(core)
                ))
            ).toList(),
            recoveredCores.stream().map(RecoveredCore::from).toList(),
            status == null ? "SUCCESS" : status.name(), message
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, LootSummaryPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeBoolean(payload.openRequested);
        buffer.writeBoolean(payload.available);
        buffer.writeUtf(payload.sessionId, 96);
        buffer.writeVarInt(payload.floor);
        buffer.writeUtf(payload.lifecycle, 32);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarInt(payload.common);
        buffer.writeVarInt(payload.uncommon);
        buffer.writeVarInt(payload.rare);
        buffer.writeVarInt(payload.epic);
        buffer.writeBoolean(payload.canAppraise);
        buffer.writeBoolean(payload.canKeepAll);
        buffer.writeBoolean(payload.canQuickSell);
        buffer.writeBoolean(payload.canSellAll);
        buffer.writeBoolean(payload.exitOptionsAvailable);
        buffer.writeVarInt(payload.nextFloor);
        payload.wallet.write(buffer);
        payload.overview.write(buffer);
        buffer.writeVarInt(payload.entries.size());
        payload.entries.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(payload.recoveredCores.size());
        payload.recoveredCores.forEach(core -> core.write(buffer));
        buffer.writeUtf(payload.status, 48);
        buffer.writeUtf(payload.message, 256);
    }

    private static LootSummaryPayload read(RegistryFriendlyByteBuf buffer) {
        int schemaVersion = buffer.readVarInt();
        boolean openRequested = buffer.readBoolean();
        boolean available = buffer.readBoolean();
        String sessionId = buffer.readUtf(96);
        int floor = buffer.readVarInt();
        String lifecycle = buffer.readUtf(32);
        long revision = buffer.readVarLong();
        int common = buffer.readVarInt();
        int uncommon = buffer.readVarInt();
        int rare = buffer.readVarInt();
        int epic = buffer.readVarInt();
        boolean canAppraise = buffer.readBoolean();
        boolean canKeepAll = buffer.readBoolean();
        boolean canQuickSell = buffer.readBoolean();
        boolean canSellAll = buffer.readBoolean();
        boolean exitOptionsAvailable = buffer.readBoolean();
        int nextFloor = buffer.readVarInt();
        Wallet wallet = Wallet.read(buffer);
        RunOverview overview = RunOverview.read(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid loot summary entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(Entry.read(buffer));
        int recoveredCoreCount = buffer.readVarInt();
        if (recoveredCoreCount < 0 || recoveredCoreCount > MAX_RECOVERED_CORES) {
            throw new IllegalArgumentException("Invalid recovered Core summary count: " + recoveredCoreCount);
        }
        List<RecoveredCore> recoveredCores = new ArrayList<>(recoveredCoreCount);
        for (int index = 0; index < recoveredCoreCount; index++) {
            recoveredCores.add(RecoveredCore.read(buffer));
        }
        return new LootSummaryPayload(
            schemaVersion, openRequested, available, sessionId, floor, lifecycle, revision,
            common, uncommon, rare, epic, canAppraise, canKeepAll, canQuickSell, canSellAll,
            exitOptionsAvailable, nextFloor, wallet, overview, entries, recoveredCores,
            buffer.readUtf(48), buffer.readUtf(256)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Wallet(int bronze, int silver, int gold, int stellarGold) {
        public Wallet {
            bronze = Math.max(0, bronze);
            silver = Math.max(0, silver);
            gold = Math.max(0, gold);
            stellarGold = Math.max(0, stellarGold);
        }

        static Wallet empty() {
            return new Wallet(0, 0, 0, 0);
        }

        static Wallet from(ServerPlayer player) {
            var bank = DungeonBankData.get(player.serverLevel())
                .snapshot(player.getUUID())
                .balance();
            return new Wallet(
                combined(
                    DungeonUiSnapshotFactory.countItem(player, "tensura:bronze_coin"),
                    bank.bronze()
                ),
                combined(
                    DungeonUiSnapshotFactory.countItem(player, "tensura:silver_coin"),
                    bank.silver()
                ),
                combined(
                    DungeonUiSnapshotFactory.countItem(player, "tensura:gold_coin"),
                    bank.gold()
                ),
                combined(
                    DungeonUiSnapshotFactory.countItem(player, "tensura:stellar_gold_coin"),
                    bank.stellarGold()
                )
            );
        }

        private static int combined(long onHand, long banked) {
            return Math.toIntExact(Math.addExact(onHand, banked));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(bronze);
            buffer.writeVarInt(silver);
            buffer.writeVarInt(gold);
            buffer.writeVarInt(stellarGold);
        }

        private static Wallet read(RegistryFriendlyByteBuf buffer) {
            return new Wallet(
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()
            );
        }
    }

    public record RunOverview(
        boolean available,
        boolean personalBest,
        long clearTicks,
        String timingStatus,
        CombatSummary combat,
        RewardSummary rewards,
        List<EnemyDefeat> enemyDefeats
    ) {
        public RunOverview {
            clearTicks = Math.max(0L, clearTicks);
            timingStatus = bounded(timingStatus, 48);
            combat = combat == null ? CombatSummary.empty() : combat;
            rewards = rewards == null ? RewardSummary.empty() : rewards;
            enemyDefeats = enemyDefeats == null
                ? List.of()
                : List.copyOf(enemyDefeats.subList(
                    0, Math.min(MAX_ENEMY_DEFEATS, enemyDefeats.size())
                ));
        }

        static RunOverview empty() {
            return new RunOverview(
                false, false, 0L, "UNAVAILABLE", CombatSummary.empty(), RewardSummary.empty(), List.of()
            );
        }

        static RunOverview from(
            DungeonFloorProgressData.CompletedRunSummary completed,
            SoulOrbHaulData.Summary haul,
            boolean personalBest
        ) {
            if (completed == null
                || completed.context().floor() <= 0
                || !completed.context().sessionId().equals(haul.sessionId())) {
                return empty();
            }
            DungeonFloorProgressData.RunCombatTelemetry combat = completed.combat();
            List<EnemyDefeat> defeats = combat.enemyDefeats().stream()
                .sorted(Comparator.comparingInt(DungeonFloorProgressData.EnemyDefeat::kills)
                    .reversed()
                    .thenComparing(DungeonFloorProgressData.EnemyDefeat::displayName))
                .map(defeat -> new EnemyDefeat(
                    defeat.entityTypeId(), defeat.displayName(), defeat.kills()
                ))
                .toList();
            return new RunOverview(
                true,
                personalBest,
                completed.performance().clearTicks(),
                completed.performance().timingStatus(),
                new CombatSummary(
                    combat.damageDealt(), combat.damageTaken(), combat.totalDefeated()
                ),
                new RewardSummary(
                    countMetals(haul.entries()),
                    haul.entries().size(),
                    completed.rewards().totalCurrencyBronze(),
                    false,
                    0L,
                    "PRICING_NOT_APPROVED"
                ),
                defeats
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(available);
            buffer.writeBoolean(personalBest);
            buffer.writeVarLong(clearTicks);
            buffer.writeUtf(timingStatus, 48);
            combat.write(buffer);
            rewards.write(buffer);
            buffer.writeVarInt(enemyDefeats.size());
            enemyDefeats.forEach(defeat -> defeat.write(buffer));
        }

        private static RunOverview read(RegistryFriendlyByteBuf buffer) {
            boolean available = buffer.readBoolean();
            boolean personalBest = buffer.readBoolean();
            long clearTicks = buffer.readVarLong();
            String timingStatus = buffer.readUtf(48);
            CombatSummary combat = CombatSummary.read(buffer);
            RewardSummary rewards = RewardSummary.read(buffer);
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENEMY_DEFEATS) {
                throw new IllegalArgumentException("Invalid enemy defeat count: " + count);
            }
            List<EnemyDefeat> defeats = new ArrayList<>(count);
            for (int index = 0; index < count; index++) defeats.add(EnemyDefeat.read(buffer));
            return new RunOverview(
                available, personalBest, clearTicks, timingStatus, combat, rewards, defeats
            );
        }
    }

    public record CombatSummary(double damageDealt, double damageTaken, int totalDefeated) {
        public CombatSummary {
            damageDealt = finiteNonnegative(damageDealt);
            damageTaken = finiteNonnegative(damageTaken);
            totalDefeated = Math.max(0, totalDefeated);
        }

        static CombatSummary empty() {
            return new CombatSummary(0.0D, 0.0D, 0);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeDouble(damageDealt);
            buffer.writeDouble(damageTaken);
            buffer.writeVarInt(totalDefeated);
        }

        private static CombatSummary read(RegistryFriendlyByteBuf buffer) {
            return new CombatSummary(
                buffer.readDouble(), buffer.readDouble(), buffer.readVarInt()
            );
        }
    }

    public record RewardSummary(
        int metalsEarned,
        int dropStacks,
        long currencyEarnedBronze,
        boolean lootValueAvailable,
        long lootValueBronze,
        String lootValueStatus
    ) {
        public RewardSummary {
            metalsEarned = Math.max(0, metalsEarned);
            dropStacks = Math.max(0, dropStacks);
            currencyEarnedBronze = Math.max(0L, currencyEarnedBronze);
            lootValueBronze = lootValueAvailable ? Math.max(0L, lootValueBronze) : 0L;
            lootValueStatus = bounded(lootValueStatus, 48);
        }

        static RewardSummary empty() {
            return new RewardSummary(0, 0, 0L, false, 0L, "UNAVAILABLE");
        }

        public boolean totalRunValueAvailable() {
            return lootValueAvailable;
        }

        public long totalRunValueBronze() {
            if (!lootValueAvailable) return 0L;
            return Long.MAX_VALUE - currencyEarnedBronze < lootValueBronze
                ? Long.MAX_VALUE
                : currencyEarnedBronze + lootValueBronze;
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(metalsEarned);
            buffer.writeVarInt(dropStacks);
            buffer.writeVarLong(currencyEarnedBronze);
            buffer.writeBoolean(lootValueAvailable);
            buffer.writeVarLong(lootValueBronze);
            buffer.writeUtf(lootValueStatus, 48);
        }

        private static RewardSummary read(RegistryFriendlyByteBuf buffer) {
            return new RewardSummary(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readBoolean(),
                buffer.readVarLong(),
                buffer.readUtf(48)
            );
        }
    }

    public record EnemyDefeat(String entityTypeId, String displayName, int kills) {
        public EnemyDefeat {
            entityTypeId = bounded(entityTypeId, 128);
            displayName = bounded(displayName, 80);
            if (displayName.isBlank()) displayName = entityTypeId;
            kills = Math.max(0, kills);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(entityTypeId, 128);
            buffer.writeUtf(displayName, 80);
            buffer.writeVarInt(kills);
        }

        private static EnemyDefeat read(RegistryFriendlyByteBuf buffer) {
            return new EnemyDefeat(
                buffer.readUtf(128), buffer.readUtf(80), buffer.readVarInt()
            );
        }
    }

    public record Entry(
        String entryId,
        ItemStack stack,
        String rarity,
        String disposition,
        boolean sellable,
        String unavailableReason,
        RecoveredCore recoveredCore
    ) {
        public Entry(
            String entryId,
            ItemStack stack,
            String rarity,
            String disposition,
            boolean sellable,
            String unavailableReason
        ) {
            this(entryId, stack, rarity, disposition, sellable, unavailableReason, null);
        }

        public Entry {
            entryId = bounded(entryId, 128);
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            rarity = bounded(rarity, 24);
            disposition = bounded(disposition, 24);
            unavailableReason = bounded(unavailableReason, 96);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(entryId, 128);
            ItemStack.STREAM_CODEC.encode(buffer, stack);
            buffer.writeUtf(rarity, 24);
            buffer.writeUtf(disposition, 24);
            buffer.writeBoolean(sellable);
            buffer.writeUtf(unavailableReason, 96);
            buffer.writeBoolean(recoveredCore != null);
            if (recoveredCore != null) recoveredCore.write(buffer);
        }

        private static Entry read(RegistryFriendlyByteBuf buffer) {
            String entryId = buffer.readUtf(128);
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buffer);
            String rarity = buffer.readUtf(24);
            String disposition = buffer.readUtf(24);
            boolean sellable = buffer.readBoolean();
            String unavailableReason = buffer.readUtf(96);
            RecoveredCore recoveredCore = buffer.readBoolean() ? RecoveredCore.read(buffer) : null;
            return new Entry(
                entryId, stack, rarity, disposition, sellable, unavailableReason, recoveredCore
            );
        }
    }

    public record RecoveredCore(
        UUID coreId,
        int tier,
        long totalCharge,
        long capacity,
        long naturalCharge,
        String displayName
    ) {
        private static final String DEFAULT_DISPLAY_NAME = "MONSTER CORE";

        public RecoveredCore(
            UUID coreId,
            int tier,
            long totalCharge,
            long capacity,
            long naturalCharge
        ) {
            this(coreId, tier, totalCharge, capacity, naturalCharge, DEFAULT_DISPLAY_NAME);
        }

        public RecoveredCore {
            if (coreId == null) throw new IllegalArgumentException("Recovered Core identity is required");
            if (tier < 1 || tier > 6) throw new IllegalArgumentException("Recovered Core Tier must be between I and VI");
            if (capacity <= 0L || totalCharge < 0L || totalCharge > capacity) {
                throw new IllegalArgumentException("Recovered Core Total Charge must be within capacity");
            }
            if (naturalCharge < 0L || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Recovered Core Natural Charge must be within Total Charge");
            }
            displayName = displayName == null || displayName.isBlank()
                ? DEFAULT_DISPLAY_NAME
                : displayName.length() <= 96 ? displayName : displayName.substring(0, 96);
        }

        private static RecoveredCore from(CoreSatchelData.CoreRecord core) {
            return new RecoveredCore(
                core.coreId(), core.tier(), core.totalCharge(), core.capacity(), core.naturalCharge(),
                core.displayName()
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(coreId);
            buffer.writeVarInt(tier);
            buffer.writeLong(totalCharge);
            buffer.writeLong(capacity);
            buffer.writeLong(naturalCharge);
            buffer.writeUtf(displayName, 96);
        }

        private static RecoveredCore read(RegistryFriendlyByteBuf buffer) {
            return new RecoveredCore(
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(96)
            );
        }
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static int countMetals(List<SoulOrbHaulData.EntrySnapshot> entries) {
        long count = 0L;
        for (SoulOrbHaulData.EntrySnapshot entry : entries) {
            ItemStack stack = entry.stack();
            if (stack == null || stack.isEmpty()) continue;
            boolean metal = METAL_TAGS.stream().anyMatch(stack::is);
            if (metal) count += Math.max(0, stack.getCount());
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    private static double finiteNonnegative(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static boolean isMonsterCrystal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return TensuraDungeonRuntime.MOD_ID.equals(id.getNamespace())
            && id.getPath().startsWith("monster_crystal_tier_");
    }

    private static ItemStack coreStack(int tier, String displayName) {
        ItemStack stack = new ItemStack(switch (tier) {
            case 1 -> ModItems.MONSTER_CRYSTAL_TIER_1.get();
            case 2 -> ModItems.MONSTER_CRYSTAL_TIER_2.get();
            case 3 -> ModItems.MONSTER_CRYSTAL_TIER_3.get();
            case 4 -> ModItems.MONSTER_CRYSTAL_TIER_4.get();
            case 5 -> ModItems.MONSTER_CRYSTAL_TIER_5.get();
            case 6 -> ModItems.MONSTER_CRYSTAL_TIER_6.get();
            default -> throw new IllegalArgumentException("Unsupported recovered Core Tier: " + tier);
        });
        stack.set(
            DataComponents.CUSTOM_NAME,
            Component.literal("Tier " + romanTier(tier) + " Monster Core / " + displayName)
                .withColor(coreTierColor(tier))
        );
        return stack;
    }

    private static String romanTier(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> Integer.toString(tier);
        };
    }

    private static int coreTierColor(int tier) {
        return switch (tier) {
            case 1 -> 0x65D8FF;
            case 2 -> 0x65E894;
            case 3 -> 0x4C8DFF;
            case 4 -> 0xB060FF;
            case 5 -> 0xFF6955;
            case 6 -> 0xFFD85C;
            default -> 0xEDE6F7;
        };
    }
}
