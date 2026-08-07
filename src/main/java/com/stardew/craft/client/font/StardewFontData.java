package com.stardew.craft.client.font;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Metrics exported directly from XNA SpriteFont or Stardew's SpriteText data. */
final class StardewFontData {
    enum Type {
        SPRITE_FONT,
        SPRITE_TEXT_LATIN,
        SPRITE_TEXT_BM
    }

    record Glyph(
            int codepoint,
            int page,
            int x,
            int y,
            int widthPixels,
            int heightPixels,
            float left,
            float top,
            float cropX,
            float sourceWidth,
            float right,
            float advance,
            float widthOffset
    ) {
        boolean isSpace() {
            return Character.isWhitespace(codepoint) || widthPixels <= 0 || heightPixels <= 0;
        }
    }

    private static final Gson GSON = new Gson();

    private final Type type;
    private final float scale;
    private final float originY;
    private final float lineHeight;
    private final float spacing;
    private final int defaultCodepoint;
    private final List<ResourceLocation> pages;
    private final Int2ObjectMap<Glyph> glyphs;

    private StardewFontData(Type type, float scale, float originY, float lineHeight, float spacing,
                            int defaultCodepoint, List<ResourceLocation> pages,
                            Int2ObjectMap<Glyph> glyphs) {
        this.type = type;
        this.scale = scale;
        this.originY = originY;
        this.lineHeight = lineHeight;
        this.spacing = spacing;
        this.defaultCodepoint = defaultCodepoint;
        this.pages = List.copyOf(pages);
        this.glyphs = glyphs;
    }

    /**
     * Reuse an authored glyph atlas with a different UI cell. Tooltip text is
     * hosted by Minecraft's fixed 10px row, so its raster scale and row height
     * must be independent from Stardew's much looser 28/42px source leading.
     */
    StardewFontData withLayout(float newScale, float logicalLineHeight) {
        return new StardewFontData(type, newScale, originY,
                logicalLineHeight / newScale, spacing, defaultCodepoint, pages, glyphs);
    }

    static StardewFontData load(ResourceManager resources, ResourceLocation location) throws IOException {
        JsonObject root;
        try (Reader reader = new InputStreamReader(
                resources.open(location), StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }
        Type type = switch (root.get("type").getAsString()) {
            case "sprite_font" -> Type.SPRITE_FONT;
            case "sprite_text_latin" -> Type.SPRITE_TEXT_LATIN;
            case "sprite_text_bm" -> Type.SPRITE_TEXT_BM;
            default -> throw new IOException("Unknown Stardew font type in " + location);
        };
        List<ResourceLocation> pages = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("pages")) {
            pages.add(ResourceLocation.parse(element.getAsString()));
        }
        Int2ObjectMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
        JsonArray sourceGlyphs = root.getAsJsonArray("glyphs");
        for (JsonElement element : sourceGlyphs) {
            JsonObject glyph = element.getAsJsonObject();
            int codepoint = integer(glyph, "cp", 0);
            glyphs.put(codepoint, new Glyph(
                    codepoint,
                    integer(glyph, "page", 0),
                    integer(glyph, "x", 0),
                    integer(glyph, "y", 0),
                    integer(glyph, "w", 0),
                    integer(glyph, "h", 0),
                    decimal(glyph, "left", 0.0F),
                    decimal(glyph, "top", 0.0F),
                    decimal(glyph, "crop_x", 0.0F),
                    decimal(glyph, "width", 0.0F),
                    decimal(glyph, "right", 0.0F),
                    decimal(glyph, "advance", 0.0F),
                    decimal(glyph, "offset", 0.0F)
            ));
        }
        return new StardewFontData(
                type,
                root.get("scale").getAsFloat(),
                decimal(root, "origin_y", 0.0F),
                root.get("line_height").getAsFloat(),
                decimal(root, "spacing", 0.0F),
                integer(root, "default", -1),
                pages,
                glyphs
        );
    }

    private static int integer(JsonObject object, String field, int fallback) {
        return object.has(field) ? object.get(field).getAsInt() : fallback;
    }

    private static float decimal(JsonObject object, String field, float fallback) {
        return object.has(field) ? object.get(field).getAsFloat() : fallback;
    }

    Type type() {
        return type;
    }

    float scale() {
        return scale;
    }

    float lineHeight() {
        return lineHeight * scale;
    }

    float sourceLineHeight() {
        return lineHeight;
    }

    float spacing() {
        return spacing;
    }

    int defaultCodepoint() {
        return defaultCodepoint;
    }

    List<ResourceLocation> pages() {
        return pages;
    }

    Int2ObjectMap<Glyph> glyphs() {
        return glyphs;
    }

    Glyph glyph(int codepoint) {
        return glyphs.get(codepoint);
    }

    float nominalAdvance(Glyph glyph) {
        return switch (type) {
            case SPRITE_FONT -> (Math.max(glyph.left(), 0.0F)
                    + glyph.sourceWidth() + glyph.right() + spacing) * scale;
            case SPRITE_TEXT_LATIN -> (8.0F + glyph.widthOffset() * 2.0F) * scale;
            case SPRITE_TEXT_BM -> glyph.advance() * scale;
        };
    }

    float visualLeft(Glyph glyph) {
        return switch (type) {
            case SPRITE_FONT -> glyph.cropX() * scale;
            case SPRITE_TEXT_LATIN, SPRITE_TEXT_BM -> glyph.left() * scale;
        };
    }

    float visualTop(Glyph glyph) {
        return glyph.top() * scale + originY;
    }
}
