package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.particle.WeaponSkillParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * 粒子渲染提供者注册（客户端）
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModParticleProviders {

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        // 注册橙色秋叶粒子
        event.registerSpriteSet(ModParticles.AUTUMN_LEAF_ORANGE.get(), 
            AutumnLeafParticle.OrangeProvider::new);
        
        // 注册黄色秋叶粒子
        event.registerSpriteSet(ModParticles.AUTUMN_LEAF_YELLOW.get(), 
            AutumnLeafParticle.YellowProvider::new);
        
        // 注册自定义雪花粒子
        event.registerSpriteSet(ModParticles.CUSTOM_SNOWFLAKE.get(), 
            CustomSnowflakeParticle.Provider::new);

        // 注册暗黄色油泡粒子
        event.registerSpriteSet(ModParticles.OIL_BUBBLE.get(),
            OilBubbleParticle.Provider::new);

        // 注册温泉蒸汽粒子
        event.registerSpriteSet(ModParticles.HOT_SPRING_STEAM.get(),
            HotSpringSteamParticle.Provider::new);

        event.registerSpriteSet(ModParticles.MAGIC_WARP_BURST.get(),
            MagicWarpBurstParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MAGIC_WARP_SWEEP.get(),
            MagicWarpSweepParticle.Provider::new);

        event.registerSpriteSet(ModParticles.CRESCENT_IMPACT.get(),
            WeaponSkillParticles.CrescentImpactParticle.Provider::new);
        event.registerSpriteSet(ModParticles.FOREST_LEAF.get(),
            WeaponSkillParticles.ForestLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.FOREST_WISP.get(),
            WeaponSkillParticles.ForestWispParticle.Provider::new);
    }

    /** Six original springobjects frames used by Stardew Valley's MagicWarp effect. */
    public static class MagicWarpBurstParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        protected MagicWarpBurstParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 6 + level.random.nextInt(4);
            this.quadSize = 0.5F;
            this.hasPhysics = false;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.removed) {
                this.setSpriteFromAge(sprites);
            }
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new MagicWarpBurstParticle(level, x, y, z, sprites);
            }
        }
    }

    /** Eight original animation-sheet frames used by MagicWarp's horizontal sweep. */
    public static class MagicWarpSweepParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        protected MagicWarpSweepParticle(ClientLevel level, double x, double y, double z,
                                         double xSpeed, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 8;
            this.quadSize = 0.5F;
            this.hasPhysics = false;
            this.xd = xSpeed;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.removed) {
                this.setSpriteFromAge(sprites);
            }
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new MagicWarpSweepParticle(level, x, y, z, xSpeed, sprites);
            }
        }
    }
    
    /**
     * 秋叶粒子（复用樱花的飘落行为）
     */
    public static class AutumnLeafParticle extends TextureSheetParticle {
        
        @SuppressWarnings("null")
        protected AutumnLeafParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.setSprite(sprites.get(level.random));
            this.gravity = 0.07F;
            this.lifetime = 500;
            this.hasPhysics = true;
            // 稍微旋转飘落
            this.roll = (float)Math.random() * (float)Math.PI * 2.0F;
            this.oRoll = this.roll;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        @Override
        public void tick() {
            super.tick();
            // 飘动效果
            this.xd += (Math.random() - 0.5) * 0.01;
            this.zd += (Math.random() - 0.5) * 0.01;
            
            // 落地立即消失
            if (this.onGround) {
                this.remove();
            }
        }

        public static class OrangeProvider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprite;

            public OrangeProvider(SpriteSet sprites) {
                this.sprite = sprites;
            }

            @Override
            public Particle createParticle(@SuppressWarnings("null") SimpleParticleType type, @SuppressWarnings("null") ClientLevel level, 
                    double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
                AutumnLeafParticle particle = new AutumnLeafParticle(level, x, y, z, this.sprite);
                particle.setParticleSpeed(xSpeed, ySpeed, zSpeed);
                return particle;
            }
        }

        public static class YellowProvider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprite;

            public YellowProvider(SpriteSet sprites) {
                this.sprite = sprites;
            }

            @Override
            public Particle createParticle(@SuppressWarnings("null") SimpleParticleType type, @SuppressWarnings("null") ClientLevel level, 
                    double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
                AutumnLeafParticle particle = new AutumnLeafParticle(level, x, y, z, this.sprite);
                particle.setParticleSpeed(xSpeed, ySpeed, zSpeed);
                return particle;
            }
        }
    }
    
    /**
     * 自定义雪花粒子（确保能够落地）
     */
    public static class CustomSnowflakeParticle extends TextureSheetParticle {
        
        @SuppressWarnings("null")
        protected CustomSnowflakeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.setSprite(sprites.get(level.random));
            this.gravity = 0.05F; // 慢速下落
            this.lifetime = 400;
            this.hasPhysics = true; // 启用物理碰撞
            this.friction = 0.95F; // 稍微减速
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        @Override
        public void tick() {
            super.tick();
            // 飘动效果
            this.xd += (Math.random() - 0.5) * 0.005;
            this.zd += (Math.random() - 0.5) * 0.005;
            
            // 落地立即消失
            if (this.onGround) {
                this.remove();
            }
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprite;

            public Provider(SpriteSet sprites) {
                this.sprite = sprites;
            }

            @Override
            public Particle createParticle(@SuppressWarnings("null") SimpleParticleType type, @SuppressWarnings("null") ClientLevel level, 
                    double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
                CustomSnowflakeParticle particle = new CustomSnowflakeParticle(level, x, y, z, this.sprite);
                particle.setParticleSpeed(xSpeed, ySpeed, zSpeed);
                return particle;
            }
        }
    }

    /**
     * 暗黄色油泡粒子（轻微上浮）
     */
    public static class OilBubbleParticle extends TextureSheetParticle {

        @SuppressWarnings("null")
        protected OilBubbleParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.setSprite(sprites.get(level.random));
            this.gravity = -0.01F;
            this.lifetime = 10 + level.random.nextInt(6);
            this.friction = 0.85F;
            this.hasPhysics = false;
            this.alpha = 0.7F;
            this.rCol = 0.95F;
            this.gCol = 0.82F;
            this.bCol = 0.25F;
            this.quadSize = 0.08F + level.random.nextFloat() * 0.06F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            this.xd += (this.random.nextDouble() - 0.5) * 0.002;
            this.zd += (this.random.nextDouble() - 0.5) * 0.002;
            this.alpha = Math.max(0.0F, this.alpha - 0.02F);
            if (this.alpha <= 0.02F) {
                this.remove();
            }
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprite;

            public Provider(SpriteSet sprites) {
                this.sprite = sprites;
            }

            @Override
            public Particle createParticle(@SuppressWarnings("null") SimpleParticleType type, @SuppressWarnings("null") ClientLevel level, 
                    double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
                return new OilBubbleParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprite);
            }
        }
    }

    /**
     * 斩击轨迹粒子（沿速度方向拉伸的短轨迹）
     */

    /**
     * 温泉蒸汽：白色、半透明软斑，缓慢上漂，进/出有 alpha 渐变。
     * 替代 vanilla CAMPFIRE_COSY_SMOKE 的灰色观感。
     */
    public static class HotSpringSteamParticle extends TextureSheetParticle {

        private static final int LIFE_MIN = 50;
        private static final int LIFE_MAX = 90;
        /** alpha 峰值；过高就糊脸。 */
        private static final float PEAK_ALPHA = 0.45F;

        @SuppressWarnings("null")
        protected HotSpringSteamParticle(ClientLevel level, double x, double y, double z,
                                         double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.setSprite(sprites.get(level.random));
            this.gravity = 0F;
            this.friction = 0.96F;
            this.hasPhysics = false;
            this.lifetime = LIFE_MIN + level.random.nextInt(LIFE_MAX - LIFE_MIN);
            this.alpha = 0F;
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
            this.quadSize = 0.6F + level.random.nextFloat() * 0.7F;
            // 初速来自调用方（一般微弱）
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            // 缓慢上漂 + 微弱横向扰动
            this.yd += 0.0015;
            this.xd += (this.random.nextDouble() - 0.5) * 0.0015;
            this.zd += (this.random.nextDouble() - 0.5) * 0.0015;

            // alpha 三段：前 25% 淡入到峰值，中段维持，后 35% 淡出
            float t = this.age / (float) this.lifetime;
            float a;
            if (t < 0.25F) {
                a = PEAK_ALPHA * (t / 0.25F);
            } else if (t < 0.65F) {
                a = PEAK_ALPHA;
            } else {
                a = PEAK_ALPHA * (1F - (t - 0.65F) / 0.35F);
            }
            this.alpha = Math.max(0F, a);

            // 体积慢慢扩大，像真的蒸汽散开
            this.quadSize += 0.015F;
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprite;

            public Provider(SpriteSet sprites) {
                this.sprite = sprites;
            }

            @Override
            public Particle createParticle(@SuppressWarnings("null") SimpleParticleType type, @SuppressWarnings("null") ClientLevel level,
                    double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
                return new HotSpringSteamParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprite);
            }
        }
    }
}
