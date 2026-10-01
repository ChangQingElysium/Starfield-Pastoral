package com.stardew.craft.client.model.terrain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/** Paints opaque overlay pixels into the receiving top, without a raised shadow-occluding layer. */
final class ConnectedTopQuads {
    private record Key(List<BakedQuad> base, List<BakedQuad> overlays) {}
    private final Map<Key, List<BakedQuad>> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, List<BakedQuad>> entry) { return size() > 2048; }
    };

    synchronized List<BakedQuad> compose(List<BakedQuad> base, List<BakedQuad> overlays) {
        if (base.isEmpty() || overlays.isEmpty()) return base;
        return cache.computeIfAbsent(new Key(List.copyOf(base), List.copyOf(overlays)), this::build);
    }

    private List<BakedQuad> build(Key key) {
        var overlays = key.overlays().stream().filter(q -> q.getDirection() == Direction.UP).map(Top::new).toList();
        var result = new ArrayList<BakedQuad>();
        for (BakedQuad quad : key.base()) {
            if (quad.getDirection() != Direction.UP) { result.add(quad); continue; }
            Top base = new Top(quad);
            Top[] owners = new Top[256];
            boolean[] used = new boolean[256];
            boolean painted = false;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                float px = (x + .5f) / 16, pz = (z + .5f) / 16;
                if (!base.contains(px, pz)) { used[i] = true; continue; }
                owners[i] = base;
                // Later layers win, preserving dark grass > grass and the caller's material order.
                for (Top overlay : overlays) if (overlay.contains(px, pz) && overlay.opaque(px, pz)) {
                    owners[i] = overlay;
                    painted = true;
                }
            }
            if (!painted) { result.add(quad); continue; }
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                if (used[i]) continue;
                Top owner = owners[i]; int width = 1, height = 1;
                while (x + width < 16 && !used[i + width] && owners[i + width] == owner) width++;
                outer: while (z + height < 16) {
                    for (int dx = 0; dx < width; dx++)
                        if (used[(z + height) * 16 + x + dx] || owners[(z + height) * 16 + x + dx] != owner) break outer;
                    height++;
                }
                for (int dz = 0; dz < height; dz++) for (int dx = 0; dx < width; dx++) used[(z + dz) * 16 + x + dx] = true;
                result.add(owner.crop(Math.max(base.x0, x / 16f), Math.max(base.z0, z / 16f),
                        Math.min(base.x1, (x + width) / 16f), Math.min(base.z1, (z + height) / 16f), base.y));
            }
        }
        return List.copyOf(result);
    }

    private static final class Top {
        final BakedQuad quad;
        final int[] vertices;
        final int stride;
        float x0 = Float.POSITIVE_INFINITY, z0 = Float.POSITIVE_INFINITY;
        float x1 = Float.NEGATIVE_INFINITY, z1 = Float.NEGATIVE_INFINITY;
        final float y;
        int origin, east, south;
        Top(BakedQuad quad) {
            this.quad = quad; vertices = quad.getVertices(); stride = vertices.length / 4; y = value(1);
            for (int v = 0; v < vertices.length; v += stride) {
                x0 = Math.min(x0, value(v)); x1 = Math.max(x1, value(v));
                z0 = Math.min(z0, value(v + 2)); z1 = Math.max(z1, value(v + 2));
            }
            for (int v = 0; v < vertices.length; v += stride) {
                boolean x = value(v) > (x0 + x1) / 2, z = value(v + 2) > (z0 + z1) / 2;
                if (!x && !z) origin = v;
                if (x && !z) east = v;
                if (!x && z) south = v;
            }
        }
        float value(int index) { return Float.intBitsToFloat(vertices[index]); }
        boolean contains(float x, float z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
        float uv(int component, float x, float z) {
            float start = value(origin + component);
            return start + (value(east + component) - start) * (x - x0) / (x1 - x0)
                    + (value(south + component) - start) * (z - z0) / (z1 - z0);
        }
        boolean opaque(float x, float z) {
            var sprite = quad.getSprite();
            int px = com.stardew.craft.port.PortJava.clamp((int) ((uv(4, x, z) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * sprite.contents().width()), 0, sprite.contents().width() - 1);
            int pz = com.stardew.craft.port.PortJava.clamp((int) ((uv(5, x, z) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * sprite.contents().height()), 0, sprite.contents().height() - 1);
            return (sprite.getPixelRGBA(0, px, pz) >>> 24) != 0;
        }
        BakedQuad crop(float left, float north, float right, float southEdge, float height) {
            int[] cropped = vertices.clone();
            for (int v = 0; v < vertices.length; v += stride) {
                float x = value(v) > (x0 + x1) / 2 ? right : left;
                float z = value(v + 2) > (z0 + z1) / 2 ? southEdge : north;
                cropped[v] = Float.floatToRawIntBits(x);
                cropped[v + 1] = Float.floatToRawIntBits(height);
                cropped[v + 2] = Float.floatToRawIntBits(z);
                cropped[v + 4] = Float.floatToRawIntBits(uv(4, x, z));
                cropped[v + 5] = Float.floatToRawIntBits(uv(5, x, z));
            }
            return new BakedQuad(cropped, quad.getTintIndex(), Direction.UP, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
        }
    }
}
