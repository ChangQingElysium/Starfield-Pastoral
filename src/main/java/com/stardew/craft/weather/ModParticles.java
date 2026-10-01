package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义粒子类型注册
 */
public class ModParticles {
    
    @SuppressWarnings("null")
    public static final DeferredRegister<ParticleType<?>> PARTICLES = 
        DeferredRegister.create(Registries.PARTICLE_TYPE, StardewCraft.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BOMB_FUSE =
        PARTICLES.register("bomb_fuse", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BOMB_BURST =
        PARTICLES.register("bomb_burst", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BOMB_DUST =
        PARTICLES.register("bomb_dust", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, ParticleType<com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption>> BIG_SLIME_SPLASH = PARTICLES.register("big_slime_splash", ModParticles::colorParticle);
    public static final DeferredHolder<ParticleType<?>, ParticleType<com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption>> BIG_SLIME_SPLASH_SLOW = PARTICLES.register("big_slime_splash_slow", ModParticles::colorParticle);
    private static ParticleType<com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption> colorParticle(){
        return new ParticleType<>(false){
            @Override public com.mojang.serialization.MapCodec<com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption> codec(){return com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption.codec(this);}
            @Override public com.stardew.craft.port.net.minecraft.network.codec.StreamCodec<? super com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf,com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption> streamCodec(){return com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption.streamCodec(this);}
        };
    }

    public static final DeferredHolder<ParticleType<?>, ParticleType<com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption>> SERPENT_PUFF = PARTICLES.register("serpent_puff", ModParticles::colorParticle);
    public static final DeferredHolder<ParticleType<?>, ParticleType<com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption>> SERPENT_PUFF_SLOW = PARTICLES.register("serpent_puff_slow", ModParticles::colorParticle);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REX_DISSOLVE = PARTICLES.register("rex_dissolve", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REX_BONE_FRAGMENT = PARTICLES.register("rex_bone_fragment", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REX_BREATH_FRAGMENT = PARTICLES.register("rex_breath_fragment", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUMMY_DISSOLVE = PARTICLES.register("mummy_dissolve", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MONSTER_HEAL = PARTICLES.register("monster_heal", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHAMAN_CURSE_IMPACT = PARTICLES.register("shaman_curse_impact", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SQUID_DEATH_SPARK = PARTICLES.register("squid_death_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SQUID_FIREBALL_IMPACT = PARTICLES.register("squid_fireball_impact", () -> new SimpleParticleType(false));
    // 橙色秋叶粒子
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AUTUMN_LEAF_ORANGE = 
        PARTICLES.register("autumn_leaf_orange", () -> new SimpleParticleType(false));

    // 黄色秋叶粒子
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AUTUMN_LEAF_YELLOW = 
        PARTICLES.register("autumn_leaf_yellow", () -> new SimpleParticleType(false));
    
    // 自定义雪花粒子
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CUSTOM_SNOWFLAKE = 
        PARTICLES.register("custom_snowflake", () -> new SimpleParticleType(false));

    // 暗黄色油泡粒子
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> OIL_BUBBLE =
        PARTICLES.register("oil_bubble", () -> new SimpleParticleType(false));

    // 温泉蒸汽（白色、半透明、缓慢上漂）
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOT_SPRING_STEAM =
        PARTICLES.register("hot_spring_steam", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MAGIC_WARP_BURST =
        PARTICLES.register("magic_warp_burst", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MAGIC_WARP_SWEEP =
        PARTICLES.register("magic_warp_sweep", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRESCENT_IMPACT =
        PARTICLES.register("crescent_impact", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOREST_LEAF =
        PARTICLES.register("forest_leaf", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOREST_WISP =
        PARTICLES.register("forest_wisp", () -> new SimpleParticleType(false));
}
