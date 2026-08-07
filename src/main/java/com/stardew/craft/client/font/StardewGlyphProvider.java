package com.stardew.craft.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Supplies source-resolution glyph rectangles with Stardew's exact bearings. */
final class StardewGlyphProvider implements GlyphProvider {
    private final StardewFontData data;
    private final List<NativeImage> pages;

    StardewGlyphProvider(StardewFontData data, ResourceManager resources) throws IOException {
        this.data = data;
        this.pages = new ArrayList<>(data.pages().size());
        try {
            for (var page : data.pages()) {
                try (InputStream stream = resources.open(page)) {
                    pages.add(NativeImage.read(NativeImage.Format.RGBA, stream));
                }
            }
        } catch (Throwable throwable) {
            close();
            throw throwable;
        }
    }

    @Override
    public GlyphInfo getGlyph(int codepoint) {
        StardewFontData.Glyph glyph = data.glyph(codepoint);
        if (glyph == null) {
            return null;
        }
        float advance = data.nominalAdvance(glyph);
        if (glyph.isSpace()) {
            return (GlyphInfo.SpaceGlyphInfo) () -> advance;
        }
        return new SourceGlyph(glyph, advance);
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return data.glyphs().keySet();
    }

    @Override
    public void close() {
        pages.forEach(NativeImage::close);
        pages.clear();
    }

    private final class SourceGlyph implements GlyphInfo {
        private final StardewFontData.Glyph glyph;
        private final float advance;

        private SourceGlyph(StardewFontData.Glyph glyph, float advance) {
            this.glyph = glyph;
            this.advance = advance;
        }

        @Override
        public float getAdvance() {
            return advance;
        }

        @Override
        public float getShadowOffset() {
            // Stardew's common SpriteFont shadow offset is 2 source pixels.
            return 0.5F;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitch) {
            return stitch.apply(new SheetGlyphInfo() {
                @Override
                public int getPixelWidth() {
                    return glyph.widthPixels();
                }

                @Override
                public int getPixelHeight() {
                    return glyph.heightPixels();
                }

                @Override
                public void upload(int x, int y) {
                    pages.get(glyph.page()).upload(
                            0, x, y, glyph.x(), glyph.y(),
                            glyph.widthPixels(), glyph.heightPixels(), false, false);
                }

                @Override
                public boolean isColored() {
                    return true;
                }

                @Override
                public float getOversample() {
                    return 1.0F / data.scale();
                }

                @Override
                public float getBearingLeft() {
                    return data.visualLeft(glyph);
                }

                @Override
                public float getBearingTop() {
                    return 7.0F - data.visualTop(glyph);
                }
            });
        }
    }
}
