package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.Window;
import com.stardew.craft.client.gui.common.StardewGuiViewport;
import com.stardew.craft.client.gui.common.GuiScissorMath;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphics.class)
public abstract class StardewGuiScissorMixin {
    @WrapOperation(method = "renderTooltipInternal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    private org.joml.Vector2ic stardewcraft$visibleTooltip(
            net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
            int width, int height, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight,
            Operation<org.joml.Vector2ic> original) {
        var zoom = com.stardew.craft.client.gui.common.StardewReadingZoom.current(net.minecraft.client.Minecraft.getInstance().screen);
        if (zoom == null || StardewGuiViewport.active() == null) {
            return original.call(positioner, width, height, mouseX, mouseY, tooltipWidth, tooltipHeight);
        }
        var v = zoom.viewport();
        int left = (int) Math.ceil(-v.x() / v.scale()), top = (int) Math.ceil(-v.y() / v.scale());
        int visibleW = Math.max(1, (int) (zoom.viewWidth() / (v.scale() * v.windowScale())));
        int visibleH = Math.max(1, (int) (zoom.viewHeight() / (v.scale() * v.windowScale())));
        var pos = original.call(positioner, visibleW, visibleH, mouseX - left, mouseY - top, tooltipWidth, tooltipHeight);
        return new org.joml.Vector2i(left + pos.x(), top + pos.y());
    }

    @WrapMethod(method = "enableScissor")
    private void stardewcraft$pushClip(int left, int top, int right, int bottom, Operation<Void> original) {
        var layout = StardewGuiViewport.active();
        if (layout == null) {
            original.call(left, top, right, bottom);
            return;
        }
        var pose = ((GuiGraphics) (Object) this).pose().last().pose();
        var clip = GuiScissorMath.framebuffer(pose, layout.windowScale(), left, top, right, bottom);
        original.call(clip.left(), clip.top(), clip.right(), clip.bottom());
    }

    // PORT(1.20.1): containsPointInScissor does not exist on 1.20.1; its Stardew point transform lives in
    // PortGuiGraphicsScissorMixin together with the 1.21.1 method it wrapped.

    @WrapOperation(method = "applyScissor", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScale()D"))
    private double stardewcraft$physicalScissor(Window window, Operation<Double> original) {
        var layout = StardewGuiViewport.active();
        // Every active stack entry is already in physical pixels, including a parent
        // restored by disableScissor. Do not apply the current pose a second time.
        return layout == null ? original.call(window) : 1.0;
    }
}
