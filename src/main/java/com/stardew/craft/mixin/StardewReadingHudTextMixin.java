package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.client.gui.common.StardewReadingHudText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mod messages and item labels hosted by vanilla's action bar also follow reading size.
 *
 * <p>PORT(1.20.1): 1.21 scales the single {@code drawStringWithBackdrop} call around
 * {@code (x + width / 2, y)}. 1.20.1 draws the selected item name as {@code fill} + {@code drawString}; both calls get
 * that same transform here, which is pixel-identical to scaling them together. The action-bar message is drawn by
 * {@code ForgeGui#renderRecordOverlay} ({@link StardewReadingOverlayMessageMixin}).
 *
 * <p>PORT(1.20.1): NOT registered in stardewcraft.mixins.json, for parity with 1.21.1's runtime behaviour. The 1.21.1
 * mixin selects {@code method = {"renderOverlayMessage", "renderSelectedItemName"}}; a descriptor-less selector
 * matches only the first overload, which in NeoForge 21.1 is the private {@code renderSelectedItemName(GuiGraphics)}
 * delegate that contains no {@code drawStringWithBackdrop} call. The selected-item name is therefore never scaled in
 * 1.21.1 (only the action-bar message is, ported by {@link StardewReadingOverlayMessageMixin}). Register this class
 * again once the 1.21.1 selector is fixed to {@code renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V}
 * (see docs/porting/bulk-port-gaps.md).
 */
@Mixin(Gui.class)
public abstract class StardewReadingHudTextMixin {
    @WrapOperation(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void stardewcraft$readingBackdrop(GuiGraphics g, int x1, int y1, int x2, int y2, int color,
                                              Operation<Void> original, @Local Component highlightTip) {
        // fill(j - 2, k - 2, j + i + 2, k + 11): the 1.21 backdrop of drawStringWithBackdrop(j, k, width i).
        int x = x1 + 2, y = y1 + 2, width = x2 - x1 - 4;
        if (!StardewReadingHudText.push(g, highlightTip, x, y, width)) {
            original.call(g, x1, y1, x2, y2, color);
            return;
        }
        try { original.call(g, x1, y1, x2, y2, color); }
        finally { g.pose().popPose(); }
    }

    @WrapOperation(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int stardewcraft$readingText(GuiGraphics g, Font font, Component text, int x, int y, int color,
                                         Operation<Integer> original) {
        // 1.21 passes width = getFont().width(highlightTip) for both the default and the item-supplied font.
        int width = ((Gui) (Object) this).getFont().width(text);
        if (!StardewReadingHudText.push(g, text, x, y, width)) return original.call(g, font, text, x, y, color);
        try { return original.call(g, font, text, x, y, color); }
        finally { g.pose().popPose(); }
    }
}
