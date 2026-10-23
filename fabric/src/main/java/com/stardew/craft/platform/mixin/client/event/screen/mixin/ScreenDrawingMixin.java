package com.stardew.craft.platform.mixin.client.event.screen.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.hook.ClientHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class ScreenDrawingMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void stardewcraft$screenFrame(Screen screen, GuiGraphics graphics, int x, int y, float partialTick, Operation<Void> original) {
        ClientHooks.drawScreen(screen, graphics, x, y, partialTick, original);
    }
}
