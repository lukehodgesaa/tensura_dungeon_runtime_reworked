package com.mineclawd.tensuradungeon.runtime;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class CoreSatchelAcquisitionService {
    private CoreSatchelAcquisitionService() {}

    public static AcknowledgementResult acknowledge(ServerPlayer player) {
        Objects.requireNonNull(player, "Core Satchel acknowledgement player is required");
        return acknowledge(
            CoreSatchelData.get(player.serverLevel()),
            RewardMailboxData.get(player.serverLevel()),
            player.getUUID(),
            player.serverLevel().getGameTime()
        );
    }

    static AcknowledgementResult acknowledge(
        CoreSatchelData satchels,
        RewardMailboxData mailbox,
        UUID playerId,
        long gameTime
    ) {
        Objects.requireNonNull(satchels, "Core Satchel data is required");
        Objects.requireNonNull(mailbox, "Reward mailbox is required");
        Objects.requireNonNull(playerId, "Core Satchel acknowledgement player is required");

        if (satchels.access(playerId).acquired()) {
            return new AcknowledgementResult(AcknowledgementStatus.ALREADY_OWNED, null);
        }

        RewardMailboxData.CoreSatchelWelcomeResult delivery =
            mailbox.createCoreSatchelWelcome(playerId, gameTime);
        AcknowledgementStatus status = "CREATED".equals(delivery.status())
            ? AcknowledgementStatus.SENT_TO_MAILBOX
            : AcknowledgementStatus.ALREADY_SENT;
        return new AcknowledgementResult(status, delivery.rewardPackage());
    }

    public enum AcknowledgementStatus {
        SENT_TO_MAILBOX,
        ALREADY_SENT,
        ALREADY_OWNED
    }

    public record AcknowledgementResult(
        AcknowledgementStatus status,
        RewardMailboxData.RewardPackage rewardPackage
    ) {
    }
}
