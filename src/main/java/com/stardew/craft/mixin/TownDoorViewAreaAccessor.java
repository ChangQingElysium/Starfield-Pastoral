package com.stardew.craft.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface TownDoorViewAreaAccessor {
    @Invoker("getRenderSectionAt")
    ChunkRenderDispatcher.RenderChunk stardewcraft$getRenderSectionAt(BlockPos pos);
}
