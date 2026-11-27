package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonBankSnapshotPayload;

public final class DungeonBankProjectionMapper {
    private DungeonBankProjectionMapper() {}

    public static DungeonBankView from(DungeonBankSnapshotPayload snapshot) {
        if (snapshot == null || !snapshot.supportedSchema()) {
            throw new IllegalArgumentException("Supported Dungeon Bank snapshot is required");
        }
        DungeonBankView.Grade grade = DungeonBankView.Grade.valueOf(snapshot.grade().name());
        DungeonBankSnapshotPayload.Upgrade sourceUpgrade = snapshot.upgrade();
        DungeonBankView.Upgrade upgrade = new DungeonBankView.Upgrade(
            sourceUpgrade.offered(),
            DungeonBankView.Grade.valueOf(sourceUpgrade.targetGrade().name()),
            sourceUpgrade.targetGrade().slots(),
            sourceUpgrade.targetGrade().depositFeeBasisPoints(),
            wallet(sourceUpgrade.cost()),
            sourceUpgrade.quoteId(),
            sourceUpgrade.affordable(),
            sourceUpgrade.unavailableReason()
        );
        return new DungeonBankView(
            wallet(snapshot.moneyOnHand()),
            grade,
            snapshot.unlockedSlots(),
            DungeonBankView.PRODUCTION_MAXIMUM_SLOTS,
            snapshot.slots().stream().map(slot -> new DungeonBankView.Slot(
                slot.index(),
                currency(slot.currency().name()),
                slot.amount(),
                slot.withdrawalQuoteId(),
                wallet(slot.fee()),
                wallet(slot.playerReceives()),
                slot.withdrawable(),
                slot.failureReason()
            )).toList(),
            snapshot.depositFeeBasisPoints(),
            snapshot.depositQuotes().stream().map(quote -> new DungeonBankView.DepositQuote(
                quote.quoteId(),
                currency(quote.currency().name()),
                quote.availableAmount(),
                quote.depositAmount(),
                wallet(quote.fee()),
                wallet(quote.bankReceives()),
                quote.walletRemaining(),
                quote.additionalSlots(),
                quote.depositable(),
                quote.failureReason()
            )).toList(),
            upgrade,
            false,
            snapshot.status().message(),
            snapshot.revision()
        );
    }

    private static DungeonBankView.Currency currency(String name) {
        return DungeonBankView.Currency.valueOf(name);
    }

    private static DungeonShopView.Wallet wallet(DungeonBankSnapshotPayload.Wallet wallet) {
        return new DungeonShopView.Wallet(
            wallet.bronze(), wallet.silver(), wallet.gold(), wallet.stellarGold()
        );
    }
}
