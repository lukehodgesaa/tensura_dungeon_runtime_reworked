package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.DungeonBankActionRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankActionResultPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankSnapshotFactory;
import com.mineclawd.tensuradungeon.network.DungeonBankSnapshotPayload;
import com.mineclawd.tensuradungeon.reward.VanillaInventoryGrant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class DungeonBankTransactionService {
    private static final Object TRANSACTION_LOCK = new Object();
    private static final int MAX_REPLAY_IDS_PER_PLAYER = 128;
    private static final Map<UUID, LinkedHashMap<Long, Boolean>> SEEN_REQUESTS = new HashMap<>();

    private DungeonBankTransactionService() {}

    public static Result execute(
        ServerPlayer player,
        DungeonBankActionRequestPayload request,
        DungeonBankSnapshotPayload authoritativeSnapshot
    ) {
        if (player == null || request == null || authoritativeSnapshot == null) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Dungeon Bank action context is incomplete"
            );
        }
        synchronized (TRANSACTION_LOCK) {
            if (!claimRequest(player.getUUID(), request.requestId())) {
                return Result.failure(
                    DungeonBankActionResultPayload.ResultStatus.REPLAYED_REQUEST,
                    "This Bank request was already processed"
                );
            }
            Result validation = validateRequest(request, authoritativeSnapshot);
            if (!validation.success()) return validation;
            try {
                return switch (request.action()) {
                    case DEPOSIT -> deposit(player, request, authoritativeSnapshot);
                    case WITHDRAW -> withdraw(player, request, authoritativeSnapshot);
                    case UPGRADE -> upgrade(player, request, authoritativeSnapshot);
                };
            } catch (RuntimeException failure) {
                return Result.failure(
                    DungeonBankActionResultPayload.ResultStatus.INTERNAL_ERROR,
                    "Bank transaction could not be completed; no funds were changed"
                );
            }
        }
    }

    static Result validateRequest(
        DungeonBankActionRequestPayload request,
        DungeonBankSnapshotPayload snapshot
    ) {
        if (!request.supportedSchema() || !snapshot.supportedSchema()) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Dungeon Bank protocol version is unsupported"
            );
        }
        if (request.snapshotRevision() != snapshot.revision()) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.STALE_REVISION,
                "Bank holdings changed; review the refreshed Bank and retry"
            );
        }
        return Result.success("Dungeon Bank request accepted");
    }

    private static Result deposit(
        ServerPlayer player,
        DungeonBankActionRequestPayload request,
        DungeonBankSnapshotPayload snapshot
    ) {
        DungeonBankSnapshotPayload.DepositQuote quote = snapshot.depositQuotes().stream()
            .filter(candidate -> candidate.quoteId().equals(request.quoteId()))
            .findFirst()
            .orElse(null);
        if (quote == null) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.QUOTE_EXPIRED,
                "That physical coin stack changed; select it again"
            );
        }
        long receiveValue = value(quote.bankReceives());
        if (!quote.depositable()) {
            return Result.failure(
                receiveValue <= 0L
                    ? DungeonBankActionResultPayload.ResultStatus.INVALID_REQUEST
                    : DungeonBankActionResultPayload.ResultStatus.BANK_FULL,
                quote.failureReason()
            );
        }

        Inventory inventory = player.getInventory();
        ItemStack source = inventory.getItem(quote.sourceSlot());
        if (DungeonBankSnapshotFactory.currencyOf(source) != quote.currency()
            || source.getCount() < quote.depositAmount()
            || !request.quoteId().equals(DungeonBankSnapshotFactory.depositQuoteId(
                snapshot.revision(), quote.sourceSlot(), quote.currency(), source.getCount()
            ))) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.QUOTE_EXPIRED,
                "That physical coin stack changed; select it again"
            );
        }

        List<ItemStack> inventoryBefore = snapshot(inventory);
        DungeonBankData data = DungeonBankData.get(player.serverLevel());
        DungeonBankData.Snapshot bankBefore = data.snapshot(player.getUUID());
        try {
            source.shrink(Math.toIntExact(quote.depositAmount()));
            DungeonBankRules.Balance updated = bankBefore.balance().plus(
                balance(quote.bankReceives())
            );
            DungeonBankData.MutationResult mutation = data.replaceBalances(
                player.getUUID(), bankBefore.revision(), updated
            );
            if (!mutation.applied()) {
                restore(inventory, inventoryBefore);
                return mutation.status() == DungeonBankData.MutationStatus.CAPACITY_EXCEEDED
                    ? Result.failure(
                        DungeonBankActionResultPayload.ResultStatus.BANK_FULL,
                        "Bank capacity is full"
                    )
                    : Result.failure(
                        DungeonBankActionResultPayload.ResultStatus.STALE_REVISION,
                        "Bank holdings changed; review the refreshed Bank and retry"
                    );
            }
            inventory.setChanged();
            return Result.success(
                walletText(quote.bankReceives())
                    + " deposited after " + walletText(quote.fee()) + " tax"
            );
        } catch (RuntimeException failure) {
            data.restore(player.getUUID(), bankBefore);
            restore(inventory, inventoryBefore);
            throw failure;
        }
    }

    private static Result withdraw(
        ServerPlayer player,
        DungeonBankActionRequestPayload request,
        DungeonBankSnapshotPayload snapshot
    ) {
        DungeonBankSnapshotPayload.Slot slot = snapshot.slots().stream()
            .filter(candidate -> candidate.withdrawalQuoteId().equals(request.quoteId()))
            .findFirst()
            .orElse(null);
        if (slot == null) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.QUOTE_EXPIRED,
                "That Bank stack changed; select it again"
            );
        }
        if (!slot.withdrawable()) {
            return Result.failure(
                slot.failureReason().contains("CURRENCY ITEM")
                    ? DungeonBankActionResultPayload.ResultStatus.INTERNAL_ERROR
                    : DungeonBankActionResultPayload.ResultStatus.INVENTORY_FULL,
                slot.failureReason()
            );
        }
        Inventory inventory = player.getInventory();
        DungeonBankData data = DungeonBankData.get(player.serverLevel());
        DungeonBankData.Snapshot bankBefore = data.snapshot(player.getUUID());
        if (bankBefore.balance().amount(slot.currency()) < slot.amount()
            || !request.quoteId().equals(DungeonBankSnapshotFactory.withdrawalQuoteId(
                bankBefore.revision(), slot.index(), slot.currency(), slot.amount()
            ))) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.QUOTE_EXPIRED,
                "That Bank stack changed; select it again"
            );
        }
        DungeonBankRules.Balance fee = DungeonBankRules.withdrawalFeeAsBalance(
            slot.amount(), slot.currency(), bankBefore.grade().withdrawalFeeBasisPoints()
        );
        DungeonBankRules.Balance receives = DungeonBankRules.withdrawalNetAsBalance(
            slot.amount(), slot.currency(), bankBefore.grade().withdrawalFeeBasisPoints()
        );
        List<ItemStack> outputs = DungeonBankSnapshotFactory.itemsFor(receives);
        if (outputs.isEmpty()) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.INTERNAL_ERROR,
                "This currency item is not registered"
            );
        }
        if (!DungeonBankSnapshotFactory.canGrant(player, outputs)) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.INVENTORY_FULL,
                "Make room in your inventory before withdrawing"
            );
        }
        List<ItemStack> inventoryBefore = snapshot(inventory);
        try {
            DungeonBankData.MutationResult mutation = data.replaceBalances(
                player.getUUID(),
                bankBefore.revision(),
                bankBefore.balance().minus(slot.currency(), slot.amount())
            );
            if (!mutation.applied()) {
                return Result.failure(
                    DungeonBankActionResultPayload.ResultStatus.STALE_REVISION,
                    "Bank holdings changed; review the refreshed Bank and retry"
                    );
            }
            VanillaInventoryGrant.INSTANCE.grant(player, outputs);
            return Result.success(
                walletText(DungeonBankSnapshotPayload.Wallet.from(receives))
                    + " withdrawn after "
                    + walletText(DungeonBankSnapshotPayload.Wallet.from(fee))
                    + " withdrawal tax"
            );
        } catch (RuntimeException failure) {
            data.restore(player.getUUID(), bankBefore);
            restore(inventory, inventoryBefore);
            throw failure;
        }
    }

    private static Result upgrade(
        ServerPlayer player,
        DungeonBankActionRequestPayload request,
        DungeonBankSnapshotPayload snapshot
    ) {
        DungeonBankSnapshotPayload.Upgrade upgrade = snapshot.upgrade();
        if (!upgrade.offered()) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.MAXIMUM_GRADE,
                "This Bank is already at Genesis IX"
            );
        }
        if (!upgrade.quoteId().equals(request.quoteId())) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.QUOTE_EXPIRED,
                "That Bank upgrade quote changed; review it again"
            );
        }
        Optional<DungeonShopCurrency.PaymentPlan> payment = DungeonShopCurrency.prepare(
            player, upgrade.costBronze()
        );
        if (payment.isEmpty()) {
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.INSUFFICIENT_FUNDS,
                "Equivalent Bronze/Silver/Gold/Stellar Gold funds are insufficient"
            );
        }

        DungeonShopCurrency.DebitReceipt receipt = null;
        try {
            receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
            DungeonBankData data = DungeonBankData.get(player.serverLevel());
            DungeonBankData.Snapshot afterPayment = data.snapshot(player.getUUID());
            DungeonBankData.MutationResult mutation = data.upgrade(
                player.getUUID(), afterPayment.revision(), upgrade.targetGrade()
            );
            if (!mutation.applied()) {
                receipt.rollback(player);
                return mutation.status() == DungeonBankData.MutationStatus.MAXIMUM_GRADE
                    ? Result.failure(
                        DungeonBankActionResultPayload.ResultStatus.MAXIMUM_GRADE,
                        "This Bank is already at its maximum grade"
                    )
                    : Result.failure(
                        DungeonBankActionResultPayload.ResultStatus.STALE_REVISION,
                        "Bank grade changed; review the refreshed upgrade and retry"
                    );
            }
            return Result.success(upgrade.targetGrade().label() + " Bank capacity unlocked");
        } catch (DungeonShopCurrency.ChangeInventoryException failure) {
            if (receipt != null) receipt.rollback(player);
            return Result.failure(
                DungeonBankActionResultPayload.ResultStatus.INVENTORY_FULL,
                "Make room for currency change before upgrading the Bank"
            );
        } catch (RuntimeException failure) {
            if (receipt != null) receipt.rollback(player);
            throw failure;
        }
    }

    static boolean claimRequest(UUID playerId, long requestId) {
        LinkedHashMap<Long, Boolean> requests = SEEN_REQUESTS.computeIfAbsent(
            playerId, ignored -> new LinkedHashMap<>()
        );
        if (requests.putIfAbsent(requestId, Boolean.TRUE) != null) return false;
        while (requests.size() > MAX_REPLAY_IDS_PER_PLAYER) {
            requests.remove(requests.keySet().iterator().next());
        }
        return true;
    }

    private static DungeonBankRules.Balance balance(
        DungeonBankSnapshotPayload.Wallet wallet
    ) {
        return new DungeonBankRules.Balance(
            wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()
        );
    }

    private static long value(DungeonBankSnapshotPayload.Wallet wallet) {
        return DungeonBankRules.canonicalValue(
            wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()
        );
    }

    private static String walletText(DungeonBankSnapshotPayload.Wallet wallet) {
        StringBuilder text = new StringBuilder();
        append(text, wallet.stellarGold(), "Stellar Gold");
        append(text, wallet.gold(), "Gold");
        append(text, wallet.silver(), "Silver");
        append(text, wallet.bronze(), "Bronze");
        return text.length() == 0 ? "0 Bronze" : text.toString();
    }

    private static void append(StringBuilder text, long amount, String label) {
        if (amount <= 0L) return;
        if (text.length() > 0) text.append(" + ");
        text.append(amount).append(' ').append(label);
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            result.add(inventory.getItem(slot).copy());
        }
        return result;
    }

    private static void restore(Inventory inventory, List<ItemStack> before) {
        for (int slot = 0; slot < before.size(); slot++) {
            inventory.setItem(slot, before.get(slot).copy());
        }
        inventory.setChanged();
    }

    static void resetReplayForTests() {
        synchronized (TRANSACTION_LOCK) {
            SEEN_REQUESTS.clear();
        }
    }

    public record Result(DungeonBankActionResultPayload.ResultStatus status, String message) {
        public Result {
            if (status == null) throw new IllegalArgumentException("Dungeon Bank result status is required");
            message = message == null ? "" : message;
        }

        public boolean success() {
            return status == DungeonBankActionResultPayload.ResultStatus.SUCCESS;
        }

        public static Result success(String message) {
            return new Result(DungeonBankActionResultPayload.ResultStatus.SUCCESS, message);
        }

        public static Result failure(
            DungeonBankActionResultPayload.ResultStatus status,
            String message
        ) {
            return new Result(status, message);
        }
    }
}
