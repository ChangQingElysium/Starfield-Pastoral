package com.stardew.craft.platform.mixin.client.widget.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.widget.TextShadowAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(EditBox.class)
public abstract class EditBoxTextShadowMixin implements TextShadowAccess {
    @Unique private boolean stardewcraft$textShadow = true;
    @Override public void stardewcraft$setTextShadow(boolean shadow) { stardewcraft$textShadow = shadow; }
    @Override public boolean stardewcraft$getTextShadow() { return stardewcraft$textShadow; }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int stardewcraft$formattedShadow(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        return graphics.drawString(font, text, x, y, color, stardewcraft$textShadow);
    }
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int stardewcraft$hintShadow(GuiGraphics graphics, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        return graphics.drawString(font, text, x, y, color, stardewcraft$textShadow);
    }
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int stardewcraft$stringShadow(GuiGraphics graphics, Font font, String text, int x, int y, int color, Operation<Integer> original) {
        return graphics.drawString(font, text, x, y, color, stardewcraft$textShadow);
    }
}
