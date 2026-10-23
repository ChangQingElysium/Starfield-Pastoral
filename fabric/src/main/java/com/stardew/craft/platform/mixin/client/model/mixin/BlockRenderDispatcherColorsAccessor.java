package com.stardew.craft.platform.mixin.client.model.mixin;


import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockRenderDispatcher.class)
public interface BlockRenderDispatcherColorsAccessor {
    @Accessor("blockColors") BlockColors stardewcraft$blockColors();
}
