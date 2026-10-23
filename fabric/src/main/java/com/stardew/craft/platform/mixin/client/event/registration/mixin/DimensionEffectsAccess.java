package com.stardew.craft.platform.mixin.client.event.registration.mixin;


import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DimensionSpecialEffects.class)
public interface DimensionEffectsAccess {
    @Accessor("EFFECTS") static Object2ObjectMap<ResourceLocation, DimensionSpecialEffects> stardewcraft$effects() { throw new AssertionError("Dimension effects accessor mixin missing"); }
}
