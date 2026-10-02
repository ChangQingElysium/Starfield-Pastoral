package com.stardew.craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.light.data.QuadLightData", remap = false)
public interface SodiumRingLightDataAccessor {
    @Accessor("lm") int[] stardewcraft$getLightmap();
}
