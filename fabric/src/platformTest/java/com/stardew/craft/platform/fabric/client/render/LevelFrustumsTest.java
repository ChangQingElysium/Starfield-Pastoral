package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class LevelFrustumsTest {
    @Test
    void capturedDebugFrustumTakesPriorityAndMissingBothRemainsNull() {
        var captured = new Frustum(new Matrix4f(), new Matrix4f()); var culling = new Frustum(new Matrix4f(), new Matrix4f());
        assertSame(captured, LevelFrustums.select(captured, culling)); assertSame(culling, LevelFrustums.select(null, culling)); assertNull(LevelFrustums.select(null, null));
    }
}
