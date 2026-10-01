package com.stardew.craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): writes Forge's client-extension slot for {@code RegisterClientExtensionsEvent}. */
@Mixin(net.minecraft.world.effect.MobEffect.class)
public interface PortMobEffectRenderPropertiesAccessor {
    @Accessor(value = "effectRenderer", remap = false)
    Object stardewcraft$getEffectRenderer();

    @Accessor(value = "effectRenderer", remap = false)
    void stardewcraft$setEffectRenderer(Object value);
}
