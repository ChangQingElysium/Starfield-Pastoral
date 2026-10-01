package com.stardew.craft.port;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PORT(1.20.1): the 1.20.2+ GUI sprite atlas ({@code GuiGraphics#blitSprite}) does not exist on 1.20.1.
 * Each sprite {@code ns:path} is the texture {@code ns:textures/gui/sprites/path.png}; its {@code gui.scaling}
 * metadata ({@code stretch} default, {@code tile}, {@code nine_slice}) is read from the {@code .png.mcmeta}
 * exactly like 1.21's {@code GuiSpriteScaling}. Every method reproduces 1.21.1 {@code GuiGraphics} line for line
 * (same slicing, tiling order, zero-size skips and the vanilla nine-slice right-column width quirk). A standalone
 * texture addressed with normalized UVs samples the same texels as the atlas sprite, which also uses normalized
 * sprite UVs ({@code TextureAtlasSprite#getU(float)}), so the drawn pixels are identical.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortGuiSprites {
    private static final Map<ResourceLocation, Scaling> SCALING = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    private PortGuiSprites() {}

    /** 1.21 {@code GuiSpriteScaling}. */
    public sealed interface Scaling permits Stretch, Tile, NineSlice {}

    public record Stretch() implements Scaling {}

    public record Tile(int width, int height) implements Scaling {}

    public record NineSlice(int width, int height, int left, int top, int right, int bottom) implements Scaling {}

    private static final Scaling DEFAULT = new Stretch();

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> SCALING.clear());
    }

    /** The texture that backs a GUI sprite id. */
    public static ResourceLocation texture(ResourceLocation sprite) {
        return TEXTURES.computeIfAbsent(sprite, id -> new ResourceLocation(id.getNamespace(),
                "textures/gui/sprites/" + id.getPath() + ".png"));
    }

    public static Scaling scaling(ResourceLocation sprite) {
        return SCALING.computeIfAbsent(sprite, PortGuiSprites::readScaling);
    }

    private static Scaling readScaling(ResourceLocation sprite) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture(sprite));
        if (resource.isEmpty()) return DEFAULT;
        try {
            // The sibling .png.mcmeta "gui" section, as 1.21's GuiMetadataSection reads it.
            JsonObject gui = rawGuiSection(resource.get().metadata());
            if (gui == null || !gui.has("scaling")) return DEFAULT;
            return parse(gui.getAsJsonObject("scaling"));
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Unable to parse GUI sprite metadata for {}", sprite, e);
            return DEFAULT;
        }
    }

    private static JsonObject rawGuiSection(net.minecraft.server.packs.resources.ResourceMetadata metadata) {
        return metadata.getSection(GUI_SECTION).orElse(null);
    }

    private static final net.minecraft.server.packs.metadata.MetadataSectionSerializer<JsonObject> GUI_SECTION =
            new net.minecraft.server.packs.metadata.MetadataSectionSerializer<>() {
                @Override
                public String getMetadataSectionName() {
                    return "gui";
                }

                @Override
                public JsonObject fromJson(JsonObject json) {
                    return json;
                }
            };

    /** Mirrors 1.21 {@code GuiSpriteScaling.CODEC}. */
    static Scaling parse(JsonObject json) {
        String type = json.get("type").getAsString();
        switch (type) {
            case "stretch":
                return DEFAULT;
            case "tile":
                return new Tile(positive(json, "width"), positive(json, "height"));
            case "nine_slice": {
                int width = positive(json, "width");
                int height = positive(json, "height");
                JsonElement border = json.get("border");
                if (border.isJsonPrimitive()) {
                    int size = nonNegative(border.getAsInt(), "border");
                    return new NineSlice(width, height, size, size, size, size);
                }
                JsonObject sides = border.getAsJsonObject();
                return new NineSlice(width, height,
                        nonNegative(sides.get("left").getAsInt(), "left"),
                        nonNegative(sides.get("top").getAsInt(), "top"),
                        nonNegative(sides.get("right").getAsInt(), "right"),
                        nonNegative(sides.get("bottom").getAsInt(), "bottom"));
            }
            default:
                throw new IllegalArgumentException("Unknown GUI sprite scaling type " + type);
        }
    }

    private static int positive(JsonObject json, String key) {
        int value = json.get(key).getAsInt();
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive: " + value);
        return value;
    }

    private static int nonNegative(int value, String key) {
        if (value < 0) throw new IllegalArgumentException(key + " must be non-negative: " + value);
        return value;
    }

    // ---- 1.21.1 GuiGraphics#blitSprite overloads ----

    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        blitSprite(graphics, sprite, x, y, 0, width, height);
    }

    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int blitOffset,
                                  int width, int height) {
        ResourceLocation texture = texture(sprite);
        Scaling scaling = scaling(sprite);
        if (scaling instanceof Stretch) {
            blitWhole(graphics, texture, x, y, blitOffset, width, height);
        } else if (scaling instanceof Tile tile) {
            blitTiled(graphics, texture, x, y, blitOffset, width, height, 0, 0,
                    tile.width(), tile.height(), tile.width(), tile.height());
        } else if (scaling instanceof NineSlice nineSlice) {
            blitNineSliced(graphics, texture, nineSlice, x, y, blitOffset, width, height);
        }
    }

    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int textureWidth, int textureHeight,
                                  int uPosition, int vPosition, int x, int y, int uWidth, int vHeight) {
        blitSprite(graphics, sprite, textureWidth, textureHeight, uPosition, vPosition, x, y, 0, uWidth, vHeight);
    }

    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int textureWidth, int textureHeight,
                                  int uPosition, int vPosition, int x, int y, int blitOffset, int uWidth, int vHeight) {
        ResourceLocation texture = texture(sprite);
        if (scaling(sprite) instanceof Stretch) {
            blitRegion(graphics, texture, textureWidth, textureHeight, uPosition, vPosition, x, y, blitOffset, uWidth, vHeight);
        } else {
            blitWhole(graphics, texture, x, y, blitOffset, uWidth, vHeight);
        }
    }

    /** 1.21 private {@code blitSprite(TextureAtlasSprite, texW, texH, u, v, x, y, z, w, h)}. */
    private static void blitRegion(GuiGraphics graphics, ResourceLocation texture, int textureWidth, int textureHeight,
                                   int uPosition, int vPosition, int x, int y, int blitOffset, int uWidth, int vHeight) {
        if (uWidth != 0 && vHeight != 0) {
            // u / textureWidth .. (u + width) / textureWidth, the sprite-relative UVs 1.21 maps through getU/getV.
            graphics.blit(texture, x, y, blitOffset, (float) uPosition, (float) vPosition, uWidth, vHeight,
                    textureWidth, textureHeight);
        }
    }

    /** 1.21 private {@code blitSprite(TextureAtlasSprite, x, y, z, w, h)}: the whole sprite. */
    private static void blitWhole(GuiGraphics graphics, ResourceLocation texture, int x, int y, int blitOffset,
                                  int width, int height) {
        if (width != 0 && height != 0) {
            graphics.blit(texture, x, y, blitOffset, 0.0F, 0.0F, width, height, width, height);
        }
    }

    private static void blitNineSliced(GuiGraphics graphics, ResourceLocation sprite, NineSlice nineSlice,
                                       int x, int y, int blitOffset, int width, int height) {
        int i = Math.min(nineSlice.left(), width / 2);
        int j = Math.min(nineSlice.right(), width / 2);
        int k = Math.min(nineSlice.top(), height / 2);
        int l = Math.min(nineSlice.bottom(), height / 2);
        int w = nineSlice.width();
        int h = nineSlice.height();
        if (width == w && height == h) {
            blitRegion(graphics, sprite, w, h, 0, 0, x, y, blitOffset, width, height);
        } else if (height == h) {
            blitRegion(graphics, sprite, w, h, 0, 0, x, y, blitOffset, i, height);
            blitTiled(graphics, sprite, x + i, y, blitOffset, width - j - i, height, i, 0, w - j - i, h, w, h);
            blitRegion(graphics, sprite, w, h, w - j, 0, x + width - j, y, blitOffset, j, height);
        } else if (width == w) {
            blitRegion(graphics, sprite, w, h, 0, 0, x, y, blitOffset, width, k);
            blitTiled(graphics, sprite, x, y + k, blitOffset, width, height - l - k, 0, k, w, h - l - k, w, h);
            blitRegion(graphics, sprite, w, h, 0, h - l, x, y + height - l, blitOffset, width, l);
        } else {
            blitRegion(graphics, sprite, w, h, 0, 0, x, y, blitOffset, i, k);
            blitTiled(graphics, sprite, x + i, y, blitOffset, width - j - i, k, i, 0, w - j - i, k, w, h);
            blitRegion(graphics, sprite, w, h, w - j, 0, x + width - j, y, blitOffset, j, k);
            blitRegion(graphics, sprite, w, h, 0, h - l, x, y + height - l, blitOffset, i, l);
            blitTiled(graphics, sprite, x + i, y + height - l, blitOffset, width - j - i, l, i, h - l, w - j - i, l, w, h);
            blitRegion(graphics, sprite, w, h, w - j, h - l, x + width - j, y + height - l, blitOffset, j, l);
            blitTiled(graphics, sprite, x, y + k, blitOffset, i, height - l - k, 0, k, i, h - l - k, w, h);
            blitTiled(graphics, sprite, x + i, y + k, blitOffset, width - j - i, height - l - k, i, k, w - j - i, h - l - k, w, h);
            // 1.21.1 passes the left border width (i) for the right column; kept for pixel parity.
            blitTiled(graphics, sprite, x + width - j, y + k, blitOffset, i, height - l - k, w - j, k, j, h - l - k, w, h);
        }
    }

    private static void blitTiled(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int blitOffset,
                                  int width, int height, int uPosition, int vPosition, int spriteWidth, int spriteHeight,
                                  int nineSliceWidth, int nineSliceHeight) {
        if (width > 0 && height > 0) {
            if (spriteWidth > 0 && spriteHeight > 0) {
                for (int i = 0; i < width; i += spriteWidth) {
                    int j = Math.min(spriteWidth, width - i);
                    for (int k = 0; k < height; k += spriteHeight) {
                        int l = Math.min(spriteHeight, height - k);
                        blitRegion(graphics, sprite, nineSliceWidth, nineSliceHeight, uPosition, vPosition,
                                x + i, y + k, blitOffset, j, l);
                    }
                }
            } else {
                throw new IllegalArgumentException("Tiled sprite texture size must be positive, got "
                        + spriteWidth + "x" + spriteHeight);
            }
        }
    }
}
