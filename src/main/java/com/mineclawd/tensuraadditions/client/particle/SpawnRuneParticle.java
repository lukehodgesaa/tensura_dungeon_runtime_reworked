package com.mineclawd.tensuraadditions.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class SpawnRuneParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    public SpawnRuneParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.lifetime = 12 + this.random.nextInt(10);
        this.quadSize = 0.10F + this.random.nextFloat() * 0.12F;
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.xd = xd * 0.5D + (this.random.nextDouble() - 0.5D) * 0.08D;
        this.yd = Math.abs(yd) * 0.3D + 0.08D + this.random.nextDouble() * 0.10D;
        this.zd = zd * 0.5D + (this.random.nextDouble() - 0.5D) * 0.08D;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age >= this.lifetime) {
            this.remove();
            return;
        }
        this.alpha = 1.0F - (float) this.age / (float) this.lifetime;
        this.setSpriteFromAge(this.sprites);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xd,
                double yd,
                double zd) {
            return new SpawnRuneParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
