package com.stardew.craft.mixin;

import com.stardew.craft.client.font.StardewFonts;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Makes styled Stardew font IDs resolvable by Minecraft's normal tooltip measurer.
 *
 * <p>PORT(1.20.1): 1.21 hooks {@code FontManager#getFontSetRaw}, the lookup behind the font-set function of the
 * Fonts made by {@code createFont}/{@code createFontFilterFishy}. 1.20.1 has no such method: those Fonts receive a
 * lambda {@code id -> fontSets.getOrDefault(getActualId(id), missing)}. The same function is wrapped here so the
 * Stardew resolution runs first for exactly the same Fonts.
 *
 * <p>1.21.1 wraps that lookup in {@code getFontSetCached}: one shared entry, returned again while the same id is
 * requested and the cached set's name equals it, cleared on reload. When the reading-font switch changes what a Stardew
 * role id resolves to, 1.21.1 keeps returning the previous Stardew set until another font id is requested. That is
 * reproduced for Stardew-resolved sets; vanilla ids keep the 1.20.1 lookup (1.20.1's force-unicode switch changes the
 * resolved id, which a name-keyed cache would hide), and a vanilla request evicts the entry as it does on 1.21.1.
 */
@Mixin(FontManager.class)
public abstract class StardewFontManagerMixin {
    @Unique
    private volatile FontSet stardewcraft$lastStardewFontSet;

    @ModifyArg(method = {"createFont", "createFontFilterFishy"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;<init>(Ljava/util/function/Function;Z)V"), index = 0)
    private Function<ResourceLocation, FontSet> stardewcraft$resolveAuthoredFont(Function<ResourceLocation, FontSet> original) {
        return id -> {
            FontSet cached = this.stardewcraft$lastStardewFontSet;
            if (cached != null && id.equals(((FontSetProvidersAccessor) cached).stardewcraft$getName())) {
                return cached;
            }
            FontSet fontSet = StardewFonts.resolveFontSet(id);
            // Only a set named like the request can be hit again; a role resolved to a vanilla set (reading font
            // off) is then served by the vanilla lookup, exactly the set 1.21.1's cache would return.
            this.stardewcraft$lastStardewFontSet = fontSet != null
                    && id.equals(((FontSetProvidersAccessor) fontSet).stardewcraft$getName()) ? fontSet : null;
            return fontSet != null ? fontSet : original.apply(id);
        };
    }

    /** 1.21.1 {@code FontManager#apply} clears {@code lastFontSetCache} before rebuilding the font sets. */
    @Inject(method = "apply", at = @At("HEAD"))
    private void stardewcraft$clearFontSetCache(CallbackInfo ci) {
        this.stardewcraft$lastStardewFontSet = null;
    }
}
