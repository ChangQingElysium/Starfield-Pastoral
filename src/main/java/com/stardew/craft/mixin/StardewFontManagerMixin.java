package com.stardew.craft.mixin;

import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes styled Stardew font IDs resolvable by Minecraft's normal tooltip measurer. */
@Mixin(FontManager.class)
public abstract class StardewFontManagerMixin {
    @Inject(method = "getFontSetRaw", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$resolveAuthoredFont(
            ResourceLocation id, CallbackInfoReturnable<FontSet> callback) {
        FontSet fontSet = StardewFonts.resolveFontSet(id);
        if (fontSet != null) {
            callback.setReturnValue(fontSet);
        }
    }
}
