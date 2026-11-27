package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.item.SoulOrbRarity;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.EncounterClass;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

public record SoulOrbMobIdentity(
    BlockPos controllerPos,
    String sessionId,
    SoulOrbRarity rarity,
    EncounterClass crystalEncounterClass
) {
    private static final String ROOT = "TensuraDungeonSoulOrbSource";

    public static void stamp(Mob mob, BlockPos controllerPos, String sessionId, SoulOrbRarity rarity) {
        stamp(mob, controllerPos, sessionId, rarity, EncounterClass.ORDINARY);
    }

    public static void stamp(
        Mob mob,
        BlockPos controllerPos,
        String sessionId,
        SoulOrbRarity rarity,
        EncounterClass crystalEncounterClass
    ) {
        if (mob == null || controllerPos == null || sessionId == null || sessionId.isBlank()
            || "NONE".equalsIgnoreCase(sessionId)) return;
        CompoundTag identity = new CompoundTag();
        identity.putLong("ControllerPos", controllerPos.asLong());
        identity.putString("SessionId", sessionId);
        identity.putString("Rarity", (rarity == null ? SoulOrbRarity.COMMON : rarity).name());
        identity.putString("CrystalEncounterClass", (
            crystalEncounterClass == null ? EncounterClass.ORDINARY : crystalEncounterClass
        ).name());
        mob.getPersistentData().put(ROOT, identity);
    }

    public static void inherit(Mob source, Mob summoned) {
        read(source).ifPresent(identity -> stamp(
            summoned, identity.controllerPos, identity.sessionId, identity.rarity, identity.crystalEncounterClass
        ));
    }

    public static Optional<SoulOrbMobIdentity> read(Mob mob) {
        if (mob == null || !mob.getPersistentData().contains(ROOT)) return Optional.empty();
        CompoundTag identity = mob.getPersistentData().getCompound(ROOT);
        String sessionId = identity.getString("SessionId");
        if (!identity.contains("ControllerPos") || sessionId.isBlank()) return Optional.empty();
        return Optional.of(new SoulOrbMobIdentity(
            BlockPos.of(identity.getLong("ControllerPos")),
            sessionId,
            SoulOrbRarity.parse(identity.getString("Rarity")),
            parseEncounterClass(identity.getString("CrystalEncounterClass"))
        ));
    }

    private static EncounterClass parseEncounterClass(String value) {
        try {
            return EncounterClass.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return EncounterClass.ORDINARY;
        }
    }
}
