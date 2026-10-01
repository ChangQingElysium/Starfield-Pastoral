package com.stardew.craft.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.model.ParkFountainModels;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** One 256px texture upload per rendered time value, shared by all visible fountains. */
final class ParkFountainWaterTexture {
    static final ResourceLocation ID = new ResourceLocation(StardewCraft.MODID, "fountain/water_live");
    private static DynamicTexture texture;
    private static int[] base;
    private static int generation = -1;
    private static int season = -1;
    private static double lastTime = Double.NaN;
    private static List<Region> regions = List.of();
    private ParkFountainWaterTexture() {}

    private record Region(boolean jet, boolean reverse, boolean mirror, int u, int v, int w, int h,
                          double x, double z, double top, double along) {}

    static void update(double seconds) {
        if (generation != ParkFountainModels.generation()
                || season != com.stardew.craft.client.model.terrain.TerrainSeasonTextures.currentTextureSet()) reload();
        if (seconds == lastTime) return;
        lastTime = seconds;
        var pixels = texture.getPixels();
        if (pixels == null) return;
        for (Region r : regions) for (int y = 0; y < r.h; y++) for (int x = 0; x < r.w; x++) {
            int color = base[(r.v + y) * 256 + r.u + x];
            if (!r.jet) {
                if ((color >>> 24) == 0) continue;
                int wave = (int) Math.round(2 * Math.sin((r.x + x) * .17 + (r.z + y) * .23 - seconds * Math.PI * 2 / 3.6));
                color = rgba((color & 255) + wave, (color >>> 8 & 255) + wave,
                        (color >>> 16 & 255) + wave, color >>> 24);
            } else {
                color = 0;
                double along = r.along + (r.reverse ? r.w - x - .5 : x + .5);
                if (r.mirror) along = 80 - along;
                double t = (along - 46) / 18;
                double curve = 44 + 4 * t - 36 * t * t;
                double distance = Math.abs(r.top - y - .5 - curve) / Math.sqrt(1 + Math.pow((4 - 72 * t) / 18, 2));
                if (t >= 0 && t <= 1 && distance <= 1.05) {
                    double pulse = (Math.sin(t * 31 - seconds * Math.PI * 2 / .6) + 1) / 2;
                    double mix = distance < .45 ? .55 + .45 * pulse : distance < .85 ? .22 + .4 * pulse : .1 + .2 * pulse;
                    color = rgba(46 + (int) Math.round(148 * mix) + (season == 1 ? -2 : season == 2 ? 1 : 0),
                            145 + (int) Math.round(96 * mix) + (season == 1 ? 5 : season == 2 ? -7 : 0),
                            183 + (int) Math.round(57 * mix) + (season == 1 ? 1 : season == 2 ? 2 : 0),
                            (int) Math.round(255 * (.38 + .55 * mix)));
                }
            }
            pixels.setPixelRGBA(r.u + x, r.v + y, color);
        }
        texture.upload();
    }

    private static int rgba(int r, int g, int b, int a) {
        return com.stardew.craft.port.PortJava.clamp(r, 0, 255) | com.stardew.craft.port.PortJava.clamp(g, 0, 255) << 8 | com.stardew.craft.port.PortJava.clamp(b, 0, 255) << 16 | a << 24;
    }

    private static void reload() {
        var minecraft = Minecraft.getInstance();
        season = com.stardew.craft.client.model.terrain.TerrainSeasonTextures.currentTextureSet();
        if (texture != null) minecraft.getTextureManager().release(ID);
        String directory = new String[]{"spring", "summer", "fall", "winter"}[season];
        var resource = new ResourceLocation(StardewCraft.MODID,
                "textures/block/park_fountain/" + directory + "/water.png");
        try (var stream = minecraft.getResourceManager().getResourceOrThrow(resource).open()) {
            NativeImage pixels = NativeImage.read(stream);
            if (pixels.getWidth() != 256 || pixels.getHeight() != 256) {
                pixels.close();
                throw new IllegalStateException("Fountain water atlas must be 256x256");
            }
            base = new int[256 * 256];
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) base[y * 256 + x] = pixels.getPixelRGBA(x, y);
            texture = new DynamicTexture(pixels);
            minecraft.getTextureManager().register(ID, texture);
        } catch (IOException exception) { throw new IllegalStateException("Cannot load fountain water texture", exception); }
        List<Region> loaded = new ArrayList<>();
        for (var entry : ParkFountainModels.description().getAsJsonArray("regions")) {
            var r = entry.getAsJsonObject(); var from = r.getAsJsonArray("from"); var to = r.getAsJsonArray("to");
            String face = r.get("face").getAsString(), tag = r.get("tag").getAsString();
            double x = from.get(0).getAsDouble(), z = from.get(2).getAsDouble();
            loaded.add(new Region(r.get("material").getAsString().equals("jet"), face.equals("north") || face.equals("east"),
                    tag.equals("west") || tag.equals("north"), r.get("u").getAsInt(), r.get("v").getAsInt(),
                    r.get("w").getAsInt(), r.get("h").getAsInt(), x, z, to.get(1).getAsDouble(),
                    x == to.get(0).getAsDouble() ? z : x));
        }
        regions = List.copyOf(loaded);
        generation = ParkFountainModels.generation();
        lastTime = Double.NaN;
    }
}
