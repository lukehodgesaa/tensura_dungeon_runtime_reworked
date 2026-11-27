package com.mineclawd.tensuraadditions.compat.moreskills;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BerettaOverlayPayload(
        int entityId,
        ResourceLocation attackId,
        String animation,
        int durationTicks,
        double originX,
        double originY,
        double originZ,
        double impactX,
        double impactY,
        double impactZ,
        double directionX,
        double directionZ,
        BerettaMeteorStormProfile meteorStorm,
        int seed
) implements CustomPacketPayload {
    public static final Type<BerettaOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAdditions.MOD_ID, "beretta_overlay")
    );

    public static final StreamCodec<FriendlyByteBuf, BerettaOverlayPayload> STREAM_CODEC =
            StreamCodec.of(BerettaOverlayPayload::write, BerettaOverlayPayload::read);

    public BerettaOverlayPayload {
        if (entityId < 0) {
            throw new IllegalArgumentException("entityId cannot be negative");
        }
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        if (animation == null) {
            animation = "";
        }
        if (meteorStorm == null) {
            meteorStorm = BerettaMeteorStormProfile.none();
        }
    }

    public net.minecraft.world.phys.Vec3 origin() {
        return new net.minecraft.world.phys.Vec3(originX, originY, originZ);
    }

    public net.minecraft.world.phys.Vec3 direction() {
        return new net.minecraft.world.phys.Vec3(directionX, 0.0D, directionZ);
    }

    public net.minecraft.world.phys.Vec3 impactPoint() {
        return new net.minecraft.world.phys.Vec3(impactX, impactY, impactZ);
    }

    private static void write(FriendlyByteBuf buffer, BerettaOverlayPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeResourceLocation(payload.attackId);
        buffer.writeUtf(payload.animation, 256);
        buffer.writeVarInt(payload.durationTicks);
        buffer.writeDouble(payload.originX);
        buffer.writeDouble(payload.originY);
        buffer.writeDouble(payload.originZ);
        buffer.writeDouble(payload.impactX);
        buffer.writeDouble(payload.impactY);
        buffer.writeDouble(payload.impactZ);
        buffer.writeDouble(payload.directionX);
        buffer.writeDouble(payload.directionZ);
        writeMeteorStorm(buffer, payload.meteorStorm);
        buffer.writeInt(payload.seed);
    }

    private static BerettaOverlayPayload read(FriendlyByteBuf buffer) {
        return new BerettaOverlayPayload(
                buffer.readVarInt(),
                buffer.readResourceLocation(),
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                readMeteorStorm(buffer),
                buffer.readInt()
        );
    }

    private static void writeMeteorStorm(FriendlyByteBuf buffer, BerettaMeteorStormProfile profile) {
        buffer.writeBoolean(profile.enabled());
        if (!profile.enabled()) {
            return;
        }
        buffer.writeDouble(profile.width());
        buffer.writeDouble(profile.length());
        buffer.writeDouble(profile.dropHeight());
        buffer.writeVarInt(profile.phaseCount());
        buffer.writeVarInt(profile.phaseDurationTicks());
        buffer.writeVarInt(profile.meteorIntervalTicks());
        buffer.writeVarInt(profile.meteorsPerWave());
        buffer.writeVarInt(profile.fallTicks());
        buffer.writeDouble(profile.previewRadius());
        buffer.writeDouble(profile.impactRadius());
        buffer.writeDouble(profile.phaseShift());
    }

    private static BerettaMeteorStormProfile readMeteorStorm(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return BerettaMeteorStormProfile.none();
        }
        return new BerettaMeteorStormProfile(
                true,
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
