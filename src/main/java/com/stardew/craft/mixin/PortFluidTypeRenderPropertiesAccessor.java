package com.stardew.craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): writes Forge's client-extension slot for {@code RegisterClientExtensionsEvent}. */
@Mixin(value = net.minecraftforge.fluids.FluidType.class, remap = false)
public interface PortFluidTypeRenderPropertiesAccessor {
    @Accessor(value = "renderProperties", remap = false)
    Object stardewcraft$getRenderProperties();

    @Accessor(value = "renderProperties", remap = false)
    void stardewcraft$setRenderProperties(Object value);
}
