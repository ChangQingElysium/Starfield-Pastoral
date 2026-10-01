package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortContainerScreen;
import com.stardew.craft.port.PortScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): 1.21.1 {@code AbstractContainerScreen#render} order for StardewCraft container screens.
 * 1.20.1 calls {@code renderBg} where 1.21 calls {@code renderBackground} (gradient + renderBg, overridable),
 * calls the private {@code renderSlot}, and draws the static slot highlight inline where 1.21 calls the overridable
 * {@code renderSlotHighlight(GuiGraphics, Slot, int, int, float)}. Vanilla screens are untouched.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class PortContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void port$renderBackground(AbstractContainerScreen<?> self, GuiGraphics graphics, float partialTick,
                                       int mouseX, int mouseY, Operation<Void> original) {
        if (self instanceof PortScreen screen) {
            screen.renderBackground(graphics, mouseX, mouseY, partialTick);
        } else {
            original.call(self, graphics, partialTick, mouseX, mouseY);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V"))
    private void port$renderSlot(AbstractContainerScreen<?> self, GuiGraphics graphics, Slot slot,
                                 Operation<Void> original) {
        if (self instanceof PortContainerScreen screen) {
            screen.port$renderSlot(graphics, slot);
        } else {
            original.call(self, graphics, slot);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"))
    private void port$renderSlotHighlight(GuiGraphics graphics, int x, int y, int blitOffset, int color,
                                          Operation<Void> original,
                                          @Local(argsOnly = true, ordinal = 0) int mouseX,
                                          @Local(argsOnly = true, ordinal = 1) int mouseY,
                                          @Local(argsOnly = true) float partialTick) {
        if ((Object) this instanceof PortContainerScreen screen) {
            screen.port$renderSlotHighlight(graphics, this.hoveredSlot, mouseX, mouseY, partialTick);
        } else {
            original.call(graphics, x, y, blitOffset, color);
        }
    }
}
