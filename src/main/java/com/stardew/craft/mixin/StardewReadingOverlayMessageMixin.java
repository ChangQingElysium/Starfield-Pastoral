package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.stardew.craft.client.gui.common.StardewReadingHudText;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): reading-size scaling of 1.21 {@code Gui#renderOverlayMessage}
 * ({@code drawStringWithBackdrop(font, msg, -k / 2, -4, k, color)}). Forge 1.20.1 draws the action bar in
 * {@code ForgeGui#renderRecordOverlay} as {@code drawBackdrop(-4, k)} + {@code drawString(-k / 2, -4)}; both get the
 * 1.21 transform around {@code (-k / 2 + k / 2, -4)}.
 */
@Mixin(ForgeGui.class)
public abstract class StardewReadingOverlayMessageMixin {
    @Unique
    private Component stardewcraft$message() {
        return ((PortGuiOverlayAccessor) (Object) this).port$overlayMessageString();
    }

    @WrapOperation(method = "renderRecordOverlay", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraftforge/client/gui/overlay/ForgeGui;drawBackdrop(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;III)V"))
    private void stardewcraft$readingBackdrop(ForgeGui gui, GuiGraphics g, Font font, int y, int width, int color,
                                              Operation<Void> original) {
        if (!StardewReadingHudText.push(g, stardewcraft$message(), -width / 2, y, width)) {
            original.call(gui, g, font, y, width, color);
            return;
        }
        try { original.call(gui, g, font, y, width, color); }
        finally { g.pose().popPose(); }
    }

    @WrapOperation(method = "renderRecordOverlay", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int stardewcraft$readingText(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color,
                                         Operation<Integer> original) {
        Component message = stardewcraft$message();
        int width = font.width(message);
        if (!StardewReadingHudText.push(g, message, -width / 2, y, width)) {
            return original.call(g, font, text, x, y, color);
        }
        try { return original.call(g, font, text, x, y, color); }
        finally { g.pose().popPose(); }
    }
}
