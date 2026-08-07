package com.stardew.craft.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Semantic Font whose measuring and drawing use Stardew's own cursor rules.
 *
 * <p>XNA SpriteFont applies the first glyph's left bearing differently from
 * later glyphs, while Latin SpriteText uses both the current and next
 * character's width offsets. Neither rule can be represented by Minecraft's
 * independent per-glyph advances, so both operations share this layout pass.</p>
 */
final class StardewFont extends Font {
    private record StyledGlyph(int index, Style style, int codepoint) {
    }

    private final StardewFonts.Role defaultRole;
    private final Function<ResourceLocation, FontSet> fontSets;
    private final boolean filterFishy;

    StardewFont(StardewFonts.Role defaultRole,
                Function<ResourceLocation, FontSet> fontSets,
                boolean filterFishyGlyphs) {
        super(fontSets, filterFishyGlyphs);
        this.defaultRole = defaultRole;
        this.fontSets = fontSets;
        this.filterFishy = filterFishyGlyphs;
    }

    @Override
    public int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return width(sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink));
    }

    @Override
    public int width(FormattedText text) {
        return width(Language.getInstance().getVisualOrder(text));
    }

    @Override
    public int width(FormattedCharSequence text) {
        return Mth.ceil(layout(collect(text), null, 0.0F, 0.0F,
                0xFFFFFFFF, false, null, DisplayMode.NORMAL, 0).width());
    }

    @Override
    public int drawInBatch(String text, float x, float y, int color, boolean shadow,
                           Matrix4f matrix, MultiBufferSource buffers, DisplayMode mode,
                           int backgroundColor, int packedLight, boolean bidi) {
        String rendered = bidi ? bidirectionalShaping(text) : text;
        FormattedCharSequence sequence = sink ->
                StringDecomposer.iterateFormatted(rendered, Style.EMPTY, sink);
        return drawExact(sequence, x, y, color, shadow, matrix, buffers, mode,
                backgroundColor, packedLight);
    }

    @Override
    public int drawInBatch(Component text, float x, float y, int color, boolean shadow,
                           Matrix4f matrix, MultiBufferSource buffers, DisplayMode mode,
                           int backgroundColor, int packedLight) {
        return drawExact(text.getVisualOrderText(), x, y, color, shadow, matrix,
                buffers, mode, backgroundColor, packedLight);
    }

    @Override
    public int drawInBatch(FormattedCharSequence text, float x, float y, int color,
                           boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
                           DisplayMode mode, int backgroundColor, int packedLight) {
        return drawExact(text, x, y, color, shadow, matrix, buffers, mode,
                backgroundColor, packedLight);
    }

    @Override
    public int wordWrapHeight(FormattedText text, int maxWidth) {
        return split(text, maxWidth).size() * lineHeight();
    }

    @Override
    public int wordWrapHeight(String text, int maxWidth) {
        return split(FormattedText.of(text), maxWidth).size() * lineHeight();
    }

    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        List<StyledGlyph> glyphs = collect(Language.getInstance().getVisualOrder(text));
        if (glyphs.isEmpty()) {
            return List.of(FormattedCharSequence.EMPTY);
        }
        int safeWidth = Math.max(1, maxWidth);
        List<FormattedCharSequence> lines = new ArrayList<>();
        int start = 0;
        while (start < glyphs.size()) {
            int lastSpace = -1;
            int end = start;
            int nextStart = start;
            for (int i = start; i < glyphs.size(); i++) {
                StyledGlyph glyph = glyphs.get(i);
                if (isExplicitBreak(glyph)) {
                    end = i;
                    nextStart = i + 1;
                    break;
                }
                if (Character.isWhitespace(glyph.codepoint())) {
                    lastSpace = i;
                }
                float candidateWidth = layout(
                        glyphs.subList(start, i + 1), null, 0.0F, 0.0F,
                        0xFFFFFFFF, false, null, DisplayMode.NORMAL, 0).width();
                if (candidateWidth > safeWidth) {
                    if (lastSpace >= start) {
                        end = lastSpace;
                        nextStart = lastSpace + 1;
                        while (nextStart < glyphs.size()
                                && Character.isWhitespace(glyphs.get(nextStart).codepoint())
                                && !isExplicitBreak(glyphs.get(nextStart))) {
                            nextStart++;
                        }
                    } else {
                        end = Math.max(start + 1, i);
                        nextStart = end;
                    }
                    break;
                }
                end = i + 1;
                nextStart = end;
            }
            lines.add(sequence(glyphs.subList(start, end)));
            start = Math.max(nextStart, start + 1);
        }
        return lines;
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth) {
        return plainSubstrByWidth(text, maxWidth, false);
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth, boolean fromEnd) {
        if (fromEnd) {
            // Pair-dependent SpriteText spacing makes reverse truncation a
            // separate operation. Keep MC's established tail behavior here;
            // authored UI call sites use forward truncation.
            return super.plainSubstrByWidth(text, maxWidth, true);
        }
        List<StyledGlyph> glyphs = collect(sink ->
                StringDecomposer.iterateFormatted(text, Style.EMPTY, sink));
        int included = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            float candidate = layout(glyphs.subList(0, i + 1), null,
                    0.0F, 0.0F, 0xFFFFFFFF, false, null,
                    DisplayMode.NORMAL, 0).width();
            if (candidate > maxWidth) {
                break;
            }
            included = i + 1;
        }
        if (included == 0) {
            return "";
        }
        int end = included < glyphs.size() ? glyphs.get(included).index() : text.length();
        return text.substring(0, end);
    }

    private int drawExact(FormattedCharSequence text, float x, float y, int color,
                          boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
                          DisplayMode mode, int backgroundColor, int packedLight) {
        List<StyledGlyph> glyphs = collect(text);
        int adjustedColor = opaqueDefault(color);
        if (shadow) {
            layout(glyphs, buffers, x, y, adjustedColor, true, matrix, mode, packedLight);
        }
        LayoutResult result = layout(glyphs, buffers, x, y, adjustedColor, false,
                matrix, mode, packedLight);
        return (int) result.endX() + (shadow ? 1 : 0);
    }

    private record LayoutResult(float width, float endX) {
    }

    private LayoutResult layout(List<StyledGlyph> glyphs, MultiBufferSource buffers,
                                float startX, float startY, int color, boolean shadow,
                                Matrix4f matrix, DisplayMode mode, int packedLight) {
        float cursorX = startX;
        float cursorY = startY;
        float maxX = startX;
        StardewFontData previousData = null;
        boolean firstOnLine = true;

        for (int i = 0; i < glyphs.size(); i++) {
            StyledGlyph styled = glyphs.get(i);
            StardewFonts.Role role = roleFor(styled.style());
            StardewFontData data = role == null ? null : StardewFontManager.data(role);
            boolean spriteTextBreak = styled.codepoint() == '^'
                    && data != null
                    && data.type() != StardewFontData.Type.SPRITE_FONT;
            if (styled.codepoint() == '\n' || spriteTextBreak) {
                maxX = Math.max(maxX, cursorX);
                cursorX = startX;
                cursorY += lineHeightFor(styled.style());
                previousData = null;
                firstOnLine = true;
                continue;
            }

            StardewFontData.Glyph metric = data == null ? null : data.glyph(styled.codepoint());
            ResourceLocation requested = requestedFont(styled.style(), role);
            FontSet fontSet = fontSets.apply(requested);
            GlyphInfo glyphInfo = fontSet.getGlyphInfo(styled.codepoint(), filterFishy);

            if (metric != null && data.type() == StardewFontData.Type.SPRITE_FONT) {
                if (firstOnLine || previousData != data) {
                    cursorX += Math.max(metric.left(), 0.0F) * data.scale();
                } else {
                    cursorX += (data.spacing() + metric.left()) * data.scale();
                }
            }

            if (buffers != null) {
                renderGlyph(fontSet, glyphInfo, styled, cursorX, cursorY, color, shadow,
                        matrix, buffers, mode, packedLight, metric != null);
            }

            if (metric == null || data == null) {
                cursorX += glyphInfo.getAdvance(styled.style().isBold());
            } else {
                cursorX += switch (data.type()) {
                    case SPRITE_FONT -> (metric.sourceWidth() + metric.right()) * data.scale();
                    case SPRITE_TEXT_BM -> metric.advance() * data.scale();
                    case SPRITE_TEXT_LATIN -> latinAdvance(glyphs, i, data, metric);
                };
            }
            maxX = Math.max(maxX, cursorX);
            previousData = data;
            firstOnLine = false;
        }
        return new LayoutResult(maxX - startX, cursorX);
    }

    private float latinAdvance(List<StyledGlyph> glyphs, int index,
                               StardewFontData data, StardewFontData.Glyph metric) {
        float nextOffset = 0.0F;
        if (index + 1 < glyphs.size()) {
            StyledGlyph next = glyphs.get(index + 1);
            StardewFonts.Role nextRole = roleFor(next.style());
            StardewFontData nextData = nextRole == null ? null : StardewFontManager.data(nextRole);
            StardewFontData.Glyph nextMetric = nextData == data ? data.glyph(next.codepoint()) : null;
            if (nextMetric != null && next.codepoint() != '\n' && next.codepoint() != '^') {
                nextOffset = nextMetric.widthOffset();
            }
        }
        return (8.0F + metric.widthOffset() + nextOffset) * data.scale();
    }

    private boolean isExplicitBreak(StyledGlyph glyph) {
        if (glyph.codepoint() == '\n') {
            return true;
        }
        if (glyph.codepoint() != '^') {
            return false;
        }
        StardewFonts.Role role = roleFor(glyph.style());
        StardewFontData data = role == null ? null : StardewFontManager.data(role);
        return data != null && data.type() != StardewFontData.Type.SPRITE_FONT;
    }

    private static FormattedCharSequence sequence(List<StyledGlyph> glyphs) {
        List<StyledGlyph> copy = List.copyOf(glyphs);
        return sink -> {
            for (StyledGlyph glyph : copy) {
                if (!sink.accept(glyph.index(), glyph.style(), glyph.codepoint())) {
                    return false;
                }
            }
            return true;
        };
    }

    private void renderGlyph(FontSet fontSet, GlyphInfo glyphInfo, StyledGlyph styled,
                             float x, float y, int color, boolean shadow, Matrix4f matrix,
                             MultiBufferSource buffers, DisplayMode mode, int packedLight,
                             boolean authored) {
        BakedGlyph baked = styled.style().isObfuscated() && styled.codepoint() != 32
                ? fontSet.getRandomGlyph(glyphInfo)
                : fontSet.getGlyph(styled.codepoint());
        if (baked instanceof EmptyGlyph) {
            return;
        }

        float dim = shadow ? 0.25F : 1.0F;
        TextColor styleColor = styled.style().getColor();
        int rgb = styleColor == null ? color : styleColor.getValue() | 0xFF000000;
        float red = (rgb >> 16 & 0xFF) / 255.0F * dim;
        float green = (rgb >> 8 & 0xFF) / 255.0F * dim;
        float blue = (rgb & 0xFF) / 255.0F * dim;
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float offset = shadow ? glyphInfo.getShadowOffset() : 0.0F;
        VertexConsumer consumer = buffers.getBuffer(baked.renderType(mode));
        boolean bold = !authored && styled.style().isBold();
        baked.render(styled.style().isItalic(), x + offset, y + offset, matrix,
                consumer, red, green, blue, alpha, packedLight);
        if (bold) {
            baked.render(styled.style().isItalic(), x + offset + glyphInfo.getBoldOffset(),
                    y + offset, matrix, consumer, red, green, blue, alpha, packedLight);
        }
    }

    private int lineHeight() {
        StardewFontData data = StardewFontManager.data(defaultRole);
        return data == null ? 9 : Math.max(1, Mth.ceil(data.lineHeight()));
    }

    private float lineHeightFor(Style style) {
        StardewFonts.Role role = roleFor(style);
        StardewFontData data = role == null ? null : StardewFontManager.data(role);
        return data == null ? 9.0F : data.lineHeight();
    }

    private StardewFonts.Role roleFor(Style style) {
        ResourceLocation font = style.getFont();
        if (Style.DEFAULT_FONT.equals(font)) {
            return defaultRole;
        }
        return StardewFontManager.role(font);
    }

    private ResourceLocation requestedFont(Style style, StardewFonts.Role role) {
        return role == null ? style.getFont() : role.id();
    }

    private static List<StyledGlyph> collect(FormattedCharSequence text) {
        List<StyledGlyph> result = new ArrayList<>();
        text.accept((index, style, codepoint) -> {
            result.add(new StyledGlyph(index, style, codepoint));
            return true;
        });
        return result;
    }

    private static int opaqueDefault(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
