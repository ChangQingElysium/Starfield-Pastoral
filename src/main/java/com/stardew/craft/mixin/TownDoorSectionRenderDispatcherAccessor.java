package com.stardew.craft.mixin;

import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render-buffer field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(ChunkRenderDispatcher.class)
public interface TownDoorSectionRenderDispatcherAccessor {
    @Accessor("fixedBuffers")
    ChunkBufferBuilderPack stardewcraft$getFixedBuffers();

    @Accessor("fixedBuffers") @Mutable
    void stardewcraft$setFixedBuffers(ChunkBufferBuilderPack buffers);
}
