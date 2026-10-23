package com.stardew.craft.platform.fabric.client.framebuffer;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.mixin.client.framebuffer.mixin.RenderTargetStencilMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

class StencilBuffersTest {
    @Test
    void enablingIsPerTargetIdempotentAndRebuildsStorageWithTheFlagAlreadySet() {
        List<String> resized = new ArrayList<>();
        var first = new RenderTargetStencilMixin() {
            @Override public void resize(int width, int height, boolean clear) {
                assertTrue(stardewcraft$isStencilEnabled()); resized.add(width + "x" + height);
            }
        };
        var second = new RenderTargetStencilMixin() { @Override public void resize(int w, int h, boolean clear) { fail(); } };
        first.viewWidth = 853; first.viewHeight = 479;
        assertFalse(first.stardewcraft$isStencilEnabled()); first.stardewcraft$enableStencil(); first.stardewcraft$enableStencil();
        assertEquals(List.of("853x479"), resized); assertFalse(second.stardewcraft$isStencilEnabled());
    }
    @Test
    void actualDepthAllocatorAndAttachmentWrappersUseOnePackedImageForDepthAndStencil() throws Exception {
        var target = new RenderTargetStencilMixin() { @Override public void resize(int w, int h, boolean clear) {} };
        var storage = Arrays.stream(RenderTargetStencilMixin.class.getDeclaredMethods()).filter(method -> method.getName().equals("stardewcraft$depthStorage")).findFirst().orElseThrow();
        var attach = Arrays.stream(RenderTargetStencilMixin.class.getDeclaredMethods()).filter(method -> method.getName().equals("stardewcraft$depthAndStencilAttachments")).findFirst().orElseThrow();
        storage.setAccessible(true); attach.setAccessible(true);
        List<List<Object>> writes = new ArrayList<>(); Operation<Void> original = args -> { writes.add(Arrays.asList(args)); return null; };
        storage.invoke(target, GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, 853, 479, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null, original);
        assertEquals(GL11.GL_DEPTH_COMPONENT, writes.getFirst().get(2)); assertEquals(1, writes.size()); writes.clear();
        attach.invoke(target, GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 19, 0, original);
        assertEquals(1, writes.size()); writes.clear(); target.stardewcraft$enableStencil();
        storage.invoke(target, GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, 853, 479, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null, original);
        assertEquals(GL30.GL_DEPTH32F_STENCIL8, writes.getFirst().get(2)); assertEquals(GL30.GL_DEPTH_STENCIL, writes.getFirst().get(6));
        assertEquals(GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, writes.getFirst().get(7)); assertEquals(853, writes.getFirst().get(3)); assertEquals(479, writes.getFirst().get(4));
        writes.clear(); attach.invoke(target, GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 19, 0, original);
        assertEquals(2, writes.size()); assertEquals(GL30.GL_DEPTH_ATTACHMENT, writes.get(0).get(1)); assertEquals(GL30.GL_STENCIL_ATTACHMENT, writes.get(1).get(1));
        assertEquals(writes.get(0).get(3), writes.get(1).get(3)); assertEquals(19, writes.get(1).get(3));
    }
}
