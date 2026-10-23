package com.stardew.craft.platform.fabric.extension;

import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public final class PoisonDamage {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("neoforge", "poison"));
    private PoisonDamage() {}
    public static DamageSource source(RegistryAccess registries, Supplier<DamageSource> nativeMagic) {
        return registries.registryOrThrow(Registries.DAMAGE_TYPE).getHolder(TYPE)
                .map(holder -> new DamageSource(holder)).orElseGet(nativeMagic);
    }
}
