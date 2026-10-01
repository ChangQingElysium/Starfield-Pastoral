package com.stardew.craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): writes Forge's client-extension slot for {@code RegisterClientExtensionsEvent}. */
@Mixin(net.minecraft.world.level.block.Block.class)
public interface PortBlockRenderPropertiesAccessor {
    @Accessor(value = "renderProperties", remap = false)
    Object stardewcraft$getRenderProperties();

    @Accessor(value = "renderProperties", remap = false)
    void stardewcraft$setRenderProperties(Object value);
}
