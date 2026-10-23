package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class QuadBakingVertexConsumerTest {
    @Test void nativeBlockAttributesAndExplicitAoSurviveBaking() throws Exception {
        var baker = new QuadBakingVertexConsumer();
        var sprite = spriteIdentity();
        baker.setSprite(sprite);
        baker.setDirection(Direction.EAST);
        baker.setTintIndex(7);
        baker.setShade(false);
        baker.setHasAmbientOcclusion(false);
        for (int i = 0; i < 4; i++) {
            baker.addVertex(i, i + .25F, i + .5F).setUv(i / 4F, .75F)
                    .setColor(0x12, 0x34, 0x56, 0x78).setUv2(0x80, 0xc0).setNormal(-1, .5F, 2);
        }
        var quad = baker.bakeQuad();
        assertSame(sprite, quad.getSprite());
        assertSame(Direction.EAST, quad.getDirection());
        assertEquals(7, quad.getTintIndex());
        assertFalse(quad.isShade());
        assertFalse(ModelQuad.ambientOcclusion(quad));
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            assertEquals(i, Float.intBitsToFloat(quad.getVertices()[offset]));
            assertEquals(i + .25F, Float.intBitsToFloat(quad.getVertices()[offset + 1]));
            assertEquals(i + .5F, Float.intBitsToFloat(quad.getVertices()[offset + 2]));
            assertEquals(0x78563412, quad.getVertices()[offset + 3]);
            assertEquals(i / 4F, Float.intBitsToFloat(quad.getVertices()[offset + 4]));
            assertEquals(.75F, Float.intBitsToFloat(quad.getVertices()[offset + 5]));
            assertEquals(0x00c00080, quad.getVertices()[offset + 6]);
            assertEquals(0x007f3f81, quad.getVertices()[offset + 7]);
        }
    }

    @Test void incompleteOverflowAndAttributeBeforeVertexAreRejected() {
        var baker = new QuadBakingVertexConsumer();
        assertThrows(IllegalStateException.class, () -> baker.setUv(0, 0));
        assertThrows(IllegalStateException.class, baker::bakeQuad);
        for (int i = 0; i < 4; i++) baker.addVertex(0, 0, 0);
        assertThrows(IllegalStateException.class, () -> baker.addVertex(0, 0, 0));
        assertThrows(NullPointerException.class, baker::bakeQuad);
    }

    @Test void bakedVertexArraysAreIndependentAndDefaultsAreWhiteShadedAo() throws Exception {
        var baker = new QuadBakingVertexConsumer();
        baker.setSprite(spriteIdentity());
        for (int i = 0; i < 4; i++) baker.addVertex(i, 0, 0).setColor(-1).setNormal(0, 1, 0);
        var first = baker.bakeQuad();
        var second = baker.bakeQuad();
        first.getVertices()[0] = -1;
        assertEquals(0, second.getVertices()[0]);
        assertEquals(-1, second.getVertices()[3]);
        assertTrue(second.isShade());
        assertTrue(ModelQuad.ambientOcclusion(second));
    }

    private static TextureAtlasSprite spriteIdentity() throws Exception {
        // Only identity is consumed; no atlas/GL/image state is inspected by the native quad.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (TextureAtlasSprite) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TextureAtlasSprite.class);
    }
}
