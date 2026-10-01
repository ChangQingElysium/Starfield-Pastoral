package com.stardew.craft.mixin;

import com.stardew.craft.port.PortGuiGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 {@code AbstractWidget#render} computes
 * {@code isHovered = graphics.containsPointInScissor(mouseX, mouseY) && <bounds>}; 1.20.1 ignores the scissor, so
 * widgets clipped out of a scroll area would still report hover. The scissor term is applied before renderWidget.
 */
@Mixin(AbstractWidget.class)
public abstract class PortAbstractWidgetScissorMixin {
    @Shadow protected boolean isHovered;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void port$scissorHover(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.isHovered && !PortGuiGraphics.containsPointInScissor(graphics, mouseX, mouseY)) {
            this.isHovered = false;
        }
    }
}
