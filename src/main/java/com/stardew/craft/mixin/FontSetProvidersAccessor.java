package com.stardew.craft.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(FontSet.class)
public interface FontSetProvidersAccessor {
    // PORT(1.20.1): 1.20.5+ activeProviders (option-filtered) is 1.20.1 providers (no font options).
    @Accessor("providers")
    List<GlyphProvider> stardewcraft$getActiveProviders();
}
