package com.stardew.craft.platform.mixin.client.extension.mixin;


import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderer.class)
public interface ItemRendererAccess {
    @Accessor("blockEntityRenderer") BlockEntityWithoutLevelRenderer stardewcraft$blockEntityRenderer();
}
