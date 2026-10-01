package com.stardew.craft.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface TownDoorViewAreaAccessor {
    @Invoker("getRenderChunkAt")
    ChunkRenderDispatcher.RenderChunk stardewcraft$getRenderSectionAt(BlockPos pos);

    /** PORT(1.20.1): no getViewDistance(); setViewDistance stores the grid width {@code viewDistance * 2 + 1}. */
    @Accessor("chunkGridSizeX")
    int stardewcraft$getChunkGridSizeX();
}
