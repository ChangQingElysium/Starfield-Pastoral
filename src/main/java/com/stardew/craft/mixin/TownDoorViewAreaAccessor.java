package com.stardew.craft.mixin;

import net.minecraft.client.renderer.ViewArea;
import com.stardew.craft.port.net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface TownDoorViewAreaAccessor {
    @Invoker("getRenderSectionAt")
    SectionRenderDispatcher.RenderSection stardewcraft$getRenderSectionAt(BlockPos pos);
}
