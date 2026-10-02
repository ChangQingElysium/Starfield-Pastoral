package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.integration.jade.SeasonalJadeThemePlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import snownee.jade.api.theme.Theme;
import snownee.jade.overlay.TooltipRenderer;

/** PORT(1.20.1): choose the seasonal frame and its padding before Jade measures the collected tooltip. */
@Pseudo
@Mixin(targets = "snownee.jade.overlay.WailaTickHandler", remap = false)
public abstract class PortJadeSeasonalThemeCollectMixin {
    @WrapOperation(method = "tickClient()V", at = @At(value = "INVOKE",
            target = "Lsnownee/jade/overlay/TooltipRenderer;setPaddingFromTheme(Lsnownee/jade/api/theme/Theme;)V"))
    private void stardewcraft$measureCollectedSeasonalFrame(TooltipRenderer renderer, Theme theme,
                                                           Operation<Void> original) {
        original.call(renderer, SeasonalJadeThemePlugin.collectTheme(renderer, theme));
    }
}
