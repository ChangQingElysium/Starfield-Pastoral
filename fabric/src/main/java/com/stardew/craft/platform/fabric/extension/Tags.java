package com.stardew.craft.platform.fabric.extension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public final class Tags {
    private Tags() {}
    public static final class DamageTypes {
        public static final TagKey<DamageType> IS_POISON = TagKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("neoforge", "is_poison"));
        private DamageTypes() {}
    }
}
