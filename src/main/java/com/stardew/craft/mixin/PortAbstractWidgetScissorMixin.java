package com.stardew.craft.mixin;

import com.stardew.craft.port.PortGuiGraphics;
import com.stardew.craft.port.PortScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 {@code AbstractWidget#render} (final) computes
 * {@code isHovered = graphics.containsPointInScissor(mouseX, mouseY) && <bounds>}; 1.20.1 ignores the scissor, so
 * widgets clipped out of a scroll area would still report hover. The scissor term is applied before renderWidget for
 * the mod's content only: every widget while a StardewCraft screen ({@link PortScreen}) is open, and StardewCraft
 * widget classes anywhere. Vanilla 1.20.1 screens and other mods' screens keep the 1.20.1 hover rule.
 */
@Mixin(AbstractWidget.class)
public abstract class PortAbstractWidgetScissorMixin {
    @Shadow protected boolean isHovered;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void port$scissorHover(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.isHovered && port$modContent() && !PortGuiGraphics.containsPointInScissor(graphics, mouseX, mouseY)) {
            this.isHovered = false;
        }
    }

    @Unique
    private boolean port$modContent() {
        return Minecraft.getInstance().screen instanceof PortScreen
                || this.getClass().getName().startsWith("com.stardew.craft.");
    }
}
