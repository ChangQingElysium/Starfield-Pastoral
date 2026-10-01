package com.stardew.craft.mixin;

import com.stardew.craft.port.net.minecraft.client.renderer.SectionBufferBuilderPack;
import com.stardew.craft.port.net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render-buffer field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(SectionRenderDispatcher.class)
public interface TownDoorSectionRenderDispatcherAccessor {
    @Accessor("fixedBuffers")
    SectionBufferBuilderPack stardewcraft$getFixedBuffers();

    @Accessor("fixedBuffers") @Mutable
    void stardewcraft$setFixedBuffers(SectionBufferBuilderPack buffers);
}
