package com.stardew.craft.mixin;

import com.stardew.craft.client.hud.StardewHotbarHud;
import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swap native HUD sprites without replacing ItemStack rendering or GUI coordinates. */
@Mixin(Gui.class)
public abstract class StardewHotbarSpriteMixin {
    @ModifyArg(method = {"renderItemHotbar", "renderExperienceBar"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), index = 0)
    private ResourceLocation stardewcraft$seasonalSprite(ResourceLocation sprite) {
        return StardewHotbarHud.replaceSprite(sprite);
    }

    @ModifyArg(method = {"renderItemHotbar", "renderExperienceBar"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"), index = 0)
    private ResourceLocation stardewcraft$seasonalProgress(ResourceLocation sprite) {
        return StardewHotbarHud.replaceSprite(sprite);
    }

    @Inject(method = "renderItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void stardewcraft$exteriorLeaves(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        StardewHotbarHud.renderOrnament(graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$wideLevelGap(GuiGraphics graphics, int x, CallbackInfo callback) {
        if (StardewHotbarHud.renderWideExperienceBar(graphics, x)) callback.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$compactLevel(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        if (StardewHotbarHud.renderExperienceLevel(graphics)) callback.cancel();
    }
}
