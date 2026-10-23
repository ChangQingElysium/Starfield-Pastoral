package com.stardew.craft.platform.mixin.extension.mixin;


import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffect.class)
public interface MobEffectAttributesAccessor {
    @Accessor("attributeModifiers") Map<Holder<Attribute>, ?> stardewcraft$attributeTemplates();
}
