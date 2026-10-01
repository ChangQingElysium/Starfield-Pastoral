package com.stardew.craft.mixin;

import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/**
 * Makes styled Stardew font IDs resolvable by Minecraft's normal tooltip measurer.
 *
 * <p>PORT(1.20.1): 1.21 hooks {@code FontManager#getFontSetRaw}, the lookup behind the font-set function of the
 * Fonts made by {@code createFont}/{@code createFontFilterFishy}. 1.20.1 has no such method: those Fonts receive a
 * lambda {@code id -> fontSets.getOrDefault(getActualId(id), missing)}. The same function is wrapped here so the
 * Stardew resolution runs first for exactly the same Fonts.
 */
@Mixin(FontManager.class)
public abstract class StardewFontManagerMixin {
    @ModifyArg(method = {"createFont", "createFontFilterFishy"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;<init>(Ljava/util/function/Function;Z)V"), index = 0)
    private Function<ResourceLocation, FontSet> stardewcraft$resolveAuthoredFont(Function<ResourceLocation, FontSet> original) {
        return id -> {
            FontSet fontSet = StardewFonts.resolveFontSet(id);
            return fontSet != null ? fontSet : original.apply(id);
        };
    }
}
