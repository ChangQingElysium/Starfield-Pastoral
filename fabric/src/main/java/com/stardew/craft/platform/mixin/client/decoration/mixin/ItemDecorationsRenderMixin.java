package com.stardew.craft.platform.mixin.client.decoration.mixin;


import com.stardew.craft.platform.fabric.client.decoration.FabricItemDecorations;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GuiGraphics.class)
public abstract class ItemDecorationsRenderMixin {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void stardewcraft$decorations(Font font, ItemStack stack, int x, int y, String text, CallbackInfo callback) {
        FabricItemDecorations.render((GuiGraphics) (Object) this, font, stack, x, y);
    }
}
