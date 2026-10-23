package com.stardew.craft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render-context field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(Minecraft.class)
public interface TownDoorMinecraftAccessor {
    @Accessor("mainRenderTarget") @Mutable
    void stardewcraft$setMainRenderTarget(com.mojang.blaze3d.pipeline.RenderTarget target);

    @Accessor("renderBuffers") @Mutable
    void stardewcraft$setRenderBuffers(RenderBuffers buffers);
}
