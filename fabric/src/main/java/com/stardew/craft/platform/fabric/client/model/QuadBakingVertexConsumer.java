package com.stardew.craft.platform.fabric.client.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Builds the native four-vertex BLOCK format used by the template mesh fallback. */
public final class QuadBakingVertexConsumer implements VertexConsumer {
    private final int[] vertices = new int[32];
    private int count;
    private int tintIndex = -1;
    private Direction direction = Direction.DOWN;
    private TextureAtlasSprite sprite;
    private boolean shade = true;
    private boolean ambientOcclusion = true;

    public void setDirection(Direction direction) { this.direction = Objects.requireNonNull(direction); }
    public void setSprite(TextureAtlasSprite sprite) { this.sprite = Objects.requireNonNull(sprite); }
    public void setTintIndex(int tintIndex) { this.tintIndex = tintIndex; }
    public void setShade(boolean shade) { this.shade = shade; }
    public void setHasAmbientOcclusion(boolean ambientOcclusion) { this.ambientOcclusion = ambientOcclusion; }

    @Override public QuadBakingVertexConsumer addVertex(float x, float y, float z) {
        if (count == 4) throw new IllegalStateException("A baked quad has exactly four vertices");
        int offset = count++ * 8;
        vertices[offset] = Float.floatToRawIntBits(x);
        vertices[offset + 1] = Float.floatToRawIntBits(y);
        vertices[offset + 2] = Float.floatToRawIntBits(z);
        vertices[offset + 3] = -1;
        return this;
    }

    private int offset() {
        if (count == 0) throw new IllegalStateException("Add a vertex before its attributes");
        return (count - 1) * 8;
    }

    @Override public QuadBakingVertexConsumer setColor(int red, int green, int blue, int alpha) {
        vertices[offset() + 3] = (red & 255) | (green & 255) << 8 | (blue & 255) << 16 | (alpha & 255) << 24;
        return this;
    }

    @Override public QuadBakingVertexConsumer setUv(float u, float v) {
        int offset = offset();
        vertices[offset + 4] = Float.floatToRawIntBits(u);
        vertices[offset + 5] = Float.floatToRawIntBits(v);
        return this;
    }

    @Override public QuadBakingVertexConsumer setUv1(int u, int v) {
        // BLOCK vertices have no overlay attribute, as in native block buffers.
        offset();
        return this;
    }

    @Override public QuadBakingVertexConsumer setUv2(int block, int sky) {
        vertices[offset() + 6] = (block & 65535) | (sky & 65535) << 16;
        return this;
    }

    @Override public QuadBakingVertexConsumer setNormal(float x, float y, float z) {
        vertices[offset() + 7] = packedNormal(x) | packedNormal(y) << 8 | packedNormal(z) << 16;
        return this;
    }

    private static int packedNormal(float value) { return (int) (Mth.clamp(value, -1, 1) * 127) & 255; }

    public BakedQuad bakeQuad() {
        if (count != 4) throw new IllegalStateException("A baked quad requires four vertices, received " + count);
        return ModelQuad.create(vertices.clone(), tintIndex, direction, Objects.requireNonNull(sprite, "quad sprite"),
                shade, ambientOcclusion);
    }
}
