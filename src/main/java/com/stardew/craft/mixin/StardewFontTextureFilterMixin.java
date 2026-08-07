package com.stardew.craft.mixin;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Keep Stardew's pixel glyphs on Minecraft's crisp nearest-neighbour font path. */
@Mixin(FontSet.class)
public abstract class StardewFontTextureFilterMixin {
    @Shadow @Final private ResourceLocation name;
    @Shadow @Final private List<FontTexture> textures;
    @Unique private int stardewcraft$filteredTextureCount;

    @Inject(method = "stitch", at = @At("RETURN"))
    private void stardewcraft$useNearestFontSampling(
            SheetGlyphInfo glyph, CallbackInfoReturnable<BakedGlyph> callback) {
        if (!"stardewcraft".equals(name.getNamespace())
                || !name.getPath().startsWith("stardew/")) {
            return;
        }
        while (stardewcraft$filteredTextureCount < textures.size()) {
            textures.get(stardewcraft$filteredTextureCount++).setFilter(false, false);
        }
    }
}
