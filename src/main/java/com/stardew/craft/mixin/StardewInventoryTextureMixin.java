package com.stardew.craft.mixin;

import com.stardew.craft.client.gui.common.StardewInventoryGui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Recognised vanilla E-screen resources only; mod-specific resource locations pass through. */
@Mixin(GuiGraphics.class)
public abstract class StardewInventoryTextureMixin {
    @ModifyVariable(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",
            at = @At("HEAD"), argsOnly = true)
    private ResourceLocation stardewcraft$inventoryBackground(ResourceLocation texture) {
        return StardewInventoryGui.replaceResource(texture);
    }

    @ModifyVariable(method = {
            "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIII)V",
            "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIIII)V"
    }, at = @At("HEAD"), argsOnly = true)
    private ResourceLocation stardewcraft$inventorySprite(ResourceLocation sprite) {
        return StardewInventoryGui.replaceResource(sprite);
    }
}
