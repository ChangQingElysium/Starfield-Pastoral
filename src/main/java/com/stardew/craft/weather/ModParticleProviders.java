package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.model.OilMakerAnimation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import com.stardew.craft.client.particle.WeaponSkillParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/**
 * 粒子渲染提供者注册（客户端）
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModParticleProviders {

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BOMB_FUSE.get(), sprites -> (type, level, x, y, z, phase, dy, dz) -> {
            var particle = new com.stardew.craft.client.particle.BombEffectParticle(
                level, x, y, z, sprites, 5, 53, 0, phase == 4 ? 0.1875f : 0.25f, true);
            if (phase == 0) particle.setColor(1, 0.85f, 0.35f);
            else if (phase == 2) particle.setColor(1, 0.55f, 0.2f);
            return particle;
        });
        // A count-zero particle packet carries frame milliseconds, delay and size in its speed fields.
        event.registerSpriteSet(ModParticles.BOMB_BURST.get(), sprites -> (type, level, x, y, z, frameMs, delayMs, size) ->
            new com.stardew.craft.client.particle.BombEffectParticle(level, x, y, z, sprites, 8,
                frameMs, delayMs, (float) size, true));
        event.registerSpriteSet(ModParticles.BOMB_DUST.get(), sprites -> (type, level, x, y, z, frameMs, delayMs, size) ->
            new com.stardew.craft.client.particle.BombEffectParticle(level, x, y, z, sprites, 8,
                frameMs, delayMs, (float) size, false));
        event.registerSpriteSet(ModParticles.BIG_SLIME_SPLASH.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> {
            var particle=new com.stardew.craft.client.particle.MonsterSpellParticle(level,x,y,z,sprites,10,70);
            particle.tint(type);return particle;
        });
        event.registerSpriteSet(ModParticles.BIG_SLIME_SPLASH_SLOW.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> {
            var particle=new com.stardew.craft.client.particle.MonsterSpellParticle(level,x,y,z,sprites,10,100);
            particle.tint(type);return particle;
        });
        event.registerSpriteSet(ModParticles.SERPENT_PUFF.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.SerpentPuffParticle(level,x,y,z,dx,dy,dz,sprites,70,type));
        event.registerSpriteSet(ModParticles.SERPENT_PUFF_SLOW.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.SerpentPuffParticle(level,x,y,z,dx,dy,dz,sprites,100,type));
        event.registerSpriteSet(ModParticles.REX_DISSOLVE.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.RexDissolveParticle(level,x,y,z,sprites));
        event.registerSpriteSet(ModParticles.REX_BONE_FRAGMENT.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.RexFragmentParticle(level,x,y,z,dx,dy,dz,sprites,true));
        event.registerSpriteSet(ModParticles.REX_BREATH_FRAGMENT.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.RexFragmentParticle(level,x,y,z,dx,dy,dz,sprites,false));
        event.registerSpriteSet(ModParticles.MUMMY_DISSOLVE.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.MummyDissolveParticle(level,x,y,z,sprites));
        event.registerSpriteSet(ModParticles.MONSTER_HEAL.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.MonsterSpellParticle(level,x,y,z,sprites,8,40));
        event.registerSpriteSet(ModParticles.SHAMAN_CURSE_IMPACT.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.MonsterSpellParticle(level,x,y,z,sprites,2,100+level.random.nextInt(50)));
        event.registerSpriteSet(ModParticles.SQUID_DEATH_SPARK.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.MonsterSpellParticle(level,x,y,z,sprites,6,30));
        event.registerSpriteSet(ModParticles.SQUID_FIREBALL_IMPACT.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new com.stardew.craft.client.particle.MonsterSpellParticle(level,x,y,z,sprites,6,30+level.random.nextInt(60)));
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

    /** Source six-frame oil effect; motion is in the sprite, not particle drift. */
    public static class OilBubbleParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        @SuppressWarnings("null")
        protected OilBubbleParticle(ClientLevel level, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 67;
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0;
            // Source Color=Yellow is multiplicative; keep its green/olive pixel groups.
            this.rCol = this.gCol = 1;
            this.bCol = 0;
            // 32-square padded atlas: visible field is one block wide and two high.
            this.quadSize = 1;
            setSprite(sprites.get(0, 5));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (++age >= lifetime) remove();
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float elapsedMillis = (age + partialTick) * 50;
            // Original AlphaFade=.005 at 60 updates/sec, six frames at 80 ms each.
            alpha = OilMakerAnimation.particleAlpha(elapsedMillis);
            if (alpha == 0) return;
            setSprite(sprites.get(OilMakerAnimation.particleFrame(elapsedMillis), 5));
            super.render(buffer, camera, partialTick);
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
