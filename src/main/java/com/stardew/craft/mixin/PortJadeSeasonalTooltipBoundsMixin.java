package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stardew.craft.integration.jade.SeasonalJadeThemePlugin;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import snownee.jade.api.ui.ITooltipRenderer;

/** PORT(1.20.1): Jade 15's sprite frame has zero border width, unlike Jade 11's extra rounded border. */
@Pseudo
@Mixin(targets = "snownee.jade.overlay.TooltipRenderer", remap = false)
public abstract class PortJadeSeasonalTooltipBoundsMixin {
    @ModifyExpressionValue(method = "recalculateRealRect()V", at = @At(value = "INVOKE",
            target = "Lsnownee/jade/impl/config/WailaConfig$ConfigOverlay;getSquare()Z"))
    private boolean stardewcraft$keepSpriteBoundsSquare(boolean original) {
        return SeasonalJadeThemePlugin.hasCollectedFrame((ITooltipRenderer) (Object) this) || original;
    }

    @ModifyExpressionValue(method = "recalculateRealRect()V", at = @At(value = "FIELD",
            target = "Lsnownee/jade/api/theme/Theme;backgroundTexture:Lnet/minecraft/resources/ResourceLocation;",
            opcode = Opcodes.GETFIELD))
    private ResourceLocation stardewcraft$skipLegacyTextureBorder(ResourceLocation original) {
        return SeasonalJadeThemePlugin.hasCollectedFrame((ITooltipRenderer) (Object) this) ? null : original;
    }
}
