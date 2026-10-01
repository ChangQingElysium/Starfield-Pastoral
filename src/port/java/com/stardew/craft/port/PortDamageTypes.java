package com.stardew.craft.port;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * NeoForge 21.1 poison damage: {@code neoforge:poison} damage type (same properties as {@code minecraft:magic}),
 * tag {@code neoforge:is_poison} ({@code Tags.DamageTypes.IS_POISON}) and the vanilla damage tags that list it
 * (the four of them that exist in 1.20.1). The data lives in {@code src/port/resources/data/{neoforge,minecraft}};
 * {@code PortPoisonDamageMixin} makes the poison effect deal it, exactly like NeoForge's {@code PoisonMobEffect}.
 */
public final class PortDamageTypes {
    public static final ResourceKey<DamageType> POISON_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("neoforge", "poison"));
    /** NeoForge {@code Tags.DamageTypes.IS_POISON}. */
    public static final TagKey<DamageType> IS_POISON =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("neoforge", "is_poison"));

    private PortDamageTypes() {
    }

    /** NeoForge {@code PoisonMobEffect#applyEffectTick} source: {@code neoforge:poison}, falling back to magic. */
    public static DamageSource poison(LivingEntity entity) {
        Registry<DamageType> types = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> type = types.getHolder(POISON_DAMAGE)
                .<Holder<DamageType>>map(holder -> holder)
                .orElseGet(() -> types.getHolderOrThrow(DamageTypes.MAGIC));
        return new DamageSource(type);
    }
}
