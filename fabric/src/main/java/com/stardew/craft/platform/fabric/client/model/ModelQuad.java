package com.stardew.craft.platform.fabric.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** Native quad payload plus the explicit per-face AO bit consumed by the FRAPI bridge. */
public final class ModelQuad extends BakedQuad {
    private final boolean ambientOcclusion;

    public ModelQuad(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite,
            boolean shade, boolean ambientOcclusion) {
        super(vertices, tintIndex, direction, sprite, shade);
        this.ambientOcclusion = ambientOcclusion;
    }

    public static boolean ambientOcclusion(BakedQuad quad) {
        return !(quad instanceof ModelQuad custom) || custom.ambientOcclusion;
    }

    public static BakedQuad create(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite,
            boolean shade, boolean ambientOcclusion) {
        return new ModelQuad(vertices, tintIndex, direction, sprite, shade, ambientOcclusion);
    }
}
