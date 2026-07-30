package com.stardew.craft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Small purpose-built primitives used by the first weapon presentation pilots.
 */
public final class WeaponSkillParticles {
    private WeaponSkillParticles() {}

    public static final class CrescentImpactParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        private CrescentImpactParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                SpriteSet sprites
        ) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.sprites = sprites;
            this.lifetime = 4;
            this.quadSize = 0.42f;
            this.hasPhysics = false;
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) {
                float progress = age / (float) lifetime;
                alpha = 1.0f - progress * progress;
                setSpriteFromAge(sprites);
            }
        }

        @Override
        public float getQuadSize(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / lifetime, 0.0f, 1.0f);
            return quadSize * Mth.lerp(progress, 0.88f, 1.04f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        public static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(
                    SimpleParticleType type,
                    ClientLevel level,
                    double x,
                    double y,
                    double z,
                    double xSpeed,
                    double ySpeed,
                    double zSpeed
            ) {
                return new CrescentImpactParticle(
                        level,
                        x,
                        y,
                        z,
                        xSpeed,
                        ySpeed,
                        zSpeed,
                        sprites
                );
            }
        }
    }

    public static final class ForestLeafParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float driftPhase;

        private ForestLeafParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                SpriteSet sprites
        ) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.sprites = sprites;
            this.driftPhase = level.random.nextFloat() * Mth.TWO_PI;
            this.lifetime = 16 + level.random.nextInt(7);
            this.quadSize = 0.07f + level.random.nextFloat() * 0.035f;
            this.friction = 0.94f;
            this.gravity = -0.004f;
            this.hasPhysics = false;
            this.roll = level.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) {
                return;
            }
            double sway = Math.sin(driftPhase + age * 0.58f) * 0.0028;
            this.xd += sway;
            this.zd -= sway * 0.7;
            this.oRoll = this.roll;
            this.roll += 0.11f;
            if (age > lifetime - 6) {
                this.alpha = Math.max(0.0f, (lifetime - age) / 6.0f);
            }
            this.setSpriteFromAge(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        public static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(
                    SimpleParticleType type,
                    ClientLevel level,
                    double x,
                    double y,
                    double z,
                    double xSpeed,
                    double ySpeed,
                    double zSpeed
            ) {
                return new ForestLeafParticle(
                        level,
                        x,
                        y,
                        z,
                        xSpeed,
                        ySpeed,
                        zSpeed,
                        sprites
                );
            }
        }
    }

    public static final class ForestWispParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float driftPhase;

        private ForestWispParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                SpriteSet sprites
        ) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.sprites = sprites;
            this.driftPhase = level.random.nextFloat() * Mth.TWO_PI;
            this.lifetime = 12;
            this.quadSize = 0.095f;
            this.friction = 0.92f;
            this.hasPhysics = false;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) {
                float progress = age / (float) lifetime;
                this.xd += Math.sin(driftPhase + age * 0.44f) * 0.0007;
                this.zd += Math.cos(driftPhase + age * 0.38f) * 0.0007;
                this.alpha = progress < 0.72f
                        ? 0.86f
                        : Math.max(0.0f, (1.0f - progress) / 0.28f);
                this.setSpriteFromAge(sprites);
            }
        }

        @Override
        public float getQuadSize(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / lifetime, 0.0f, 1.0f);
            return quadSize * (0.72f + Mth.sin(progress * Mth.PI) * 0.34f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        public static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(
                    SimpleParticleType type,
                    ClientLevel level,
                    double x,
                    double y,
                    double z,
                    double xSpeed,
                    double ySpeed,
                    double zSpeed
            ) {
                return new ForestWispParticle(
                        level,
                        x,
                        y,
                        z,
                        xSpeed,
                        ySpeed,
                        zSpeed,
                        sprites
                );
            }
        }
    }
}
