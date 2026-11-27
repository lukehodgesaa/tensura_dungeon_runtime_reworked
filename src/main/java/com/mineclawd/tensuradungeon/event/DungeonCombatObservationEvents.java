package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraAddonCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraAddonCompat.NativeDamageMetadata;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.runtime.DungeonCombatObservationService;
import com.mineclawd.tensuradungeon.runtime.DungeonCombatObservationStore.ObservationKey;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCombatRules;
import com.mineclawd.tensuradungeon.runtime.SoulOrbMobIdentity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonCombatObservationEvents {
    private DungeonCombatObservationEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
            || !(event.getEntity() instanceof Mob mob)
            || event.getNewDamage() <= 0.0F) {
            return;
        }
        SoulOrbMobIdentity identity = SoulOrbMobIdentity.read(mob).orElse(null);
        if (identity == null) {
            return;
        }

        DamageSource source = event.getSource();
        UUID attackerId = resolveAttackerId(source.getEntity());
        Optional<NativeDamageMetadata> metadata = TensuraAddonCompat.nativeDamageMetadata(source);
        String abilityId = metadata
            .map(NativeDamageMetadata::abilityId)
            .filter(ability -> !ability.isBlank())
            .orElse("UNKNOWN");
        FamilyResult family = classifyFamily(source, mob);
        String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        String displayName = mob.getType().getDescription().getString();
        if (displayName.isBlank()) {
            displayName = entityTypeId;
        }

        DungeonCombatObservationService.store(level).recordHit(
            new ObservationKey(mob.getUUID(), identity.sessionId(), identity.controllerPos()),
            identity.crystalEncounterClass(),
            entityTypeId,
            displayName,
            level.getGameTime(),
            attackerId,
            event.getNewDamage(),
            abilityId,
            family.family(),
            family.dot()
        );
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)
            || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        SoulOrbMobIdentity identity = SoulOrbMobIdentity.read(mob).orElse(null);
        if (identity == null) {
            return;
        }
        DungeonCombatObservationService.store(level).recordDeath(
            new ObservationKey(mob.getUUID(), identity.sessionId(), identity.controllerPos()),
            level.getGameTime()
        );
    }

    public static void onSpiritualDamage(
        LivingEntity target,
        Entity attacker,
        DamageSource source,
        float amount,
        float resistanceFactor
    ) {
        if (target == null || source == null
            || !(target.level() instanceof ServerLevel level)
            || !(target instanceof Mob mob)) {
            return;
        }
        SoulOrbMobIdentity identity = SoulOrbMobIdentity.read(mob).orElse(null);
        if (identity == null) {
            return;
        }
        double effective = Math.max(0.0D, (double) amount * (1.0D - resistanceFactor));
        if (effective <= 0.0D) {
            return;
        }

        UUID attackerId = resolveAttackerId(attacker);
        String abilityId = TensuraAddonCompat.nativeDamageMetadata(source)
            .map(NativeDamageMetadata::abilityId)
            .filter(ability -> !ability.isBlank())
            .orElse("UNKNOWN");
        String family = spiritualFamily(source);
        String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        String displayName = mob.getType().getDescription().getString();
        if (displayName.isBlank()) {
            displayName = entityTypeId;
        }

        DungeonCombatObservationService.store(level).recordHit(
            new ObservationKey(mob.getUUID(), identity.sessionId(), identity.controllerPos()),
            identity.crystalEncounterClass(),
            entityTypeId,
            displayName,
            level.getGameTime(),
            attackerId,
            effective,
            abilityId,
            family,
            false
        );
    }

    private static UUID resolveAttackerId(Entity attacker) {
        if (attacker instanceof ServerPlayer player) {
            return player.getUUID();
        }
        if (attacker instanceof LivingEntity living) {
            return TensuraCompat.subordinateOwnerId(living).orElse(living.getUUID());
        }
        return new UUID(0L, 0L);
    }

    private static FamilyResult classifyFamily(DamageSource source, LivingEntity target) {
        Optional<NativeDamageMetadata> metadata = TensuraAddonCompat.nativeDamageMetadata(source);
        if (metadata.isPresent()) {
            String family = buildMetadataFamily(metadata.get());
            return new FamilyResult(family.isBlank() ? "NATIVE_MAGIC" : family, false);
        }
        if (source.is(DamageTypes.WITHER)
            || source.is(DamageTypes.ON_FIRE)
            || source.is(DamageTypes.LAVA)
            || source.is(DamageTypes.STARVE)) {
            return new FamilyResult("DOT", true);
        }
        if (DungeonModifierCombatRules.isKnownDirectMelee(source, target)
            || source.is(DamageTypes.PLAYER_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
            return new FamilyResult("PHYSICAL", false);
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return new FamilyResult("MAGIC", false);
        }
        return new FamilyResult("OTHER", false);
    }

    private static String spiritualFamily(DamageSource source) {
        Optional<NativeDamageMetadata> metadata = TensuraAddonCompat.nativeDamageMetadata(source);
        if (metadata.isEmpty()) {
            return "SPIRITUAL";
        }
        String family = buildMetadataFamily(metadata.get());
        if (family.isBlank()) {
            return "SPIRITUAL";
        }
        return family.endsWith("/SPIRITUAL") || "SPIRITUAL".equals(family)
            ? family
            : family + "/SPIRITUAL";
    }

    private static String buildMetadataFamily(NativeDamageMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, metadata.magicType());
        appendPart(builder, metadata.element());
        appendPart(builder, metadata.skillType());
        if (metadata.spiritual()) {
            appendPart(builder, "SPIRITUAL");
        }
        return builder.toString();
    }

    private static void appendPart(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(value.toUpperCase(java.util.Locale.ROOT));
        }
    }

    private record FamilyResult(String family, boolean dot) {
    }
}
