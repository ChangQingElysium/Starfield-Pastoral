package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the actual Minecraft buffer scheduler and actual bubble types; only GPU upload is intercepted. */
class BubbleBatchingTest {
    private static final RenderType BACKGROUND = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/bubble.png"));
    private static final int MACHINES = 500;

    @Test void hundredsOfReadyMachinesSubmitThreeBatchesInsteadOfPerMachineSwitches() {
        try (CountingBuffers old = new CountingBuffers(false); CountingBuffers current = new CountingBuffers(true)) {
            emitBubbles(old);
            emitBubbles(current);
            assertEquals(MACHINES * 3 - 1, old.submissions.size(), "Shared buffers flush on each type switch");
            assertEquals(0, current.submissions.size(), "Fixed bubble buffers must remain open throughout the frame");
            old.endBatch();
            current.endBatch();
            assertEquals(MACHINES * 3, old.submissions.size());
            assertEquals(3, current.submissions.size());
            assertEquals(old.vertices, current.vertices, "Batching must retain every icon/background vertex");
            assertEquals(MACHINES * 12, current.vertices);
            assertEquals(new ArrayList<>(current.fixed.keySet()), current.submissions,
                    "Bubble backgrounds must be submitted before native colored icon layers");
            System.out.printf("READY-BUBBLE %d machines: native buffer submissions %d -> %d; %d vertices retained%n",
                    MACHINES, old.submissions.size(), current.submissions.size(), current.vertices);
        }
    }

    @Test void unrelatedNativeItemAndFontBuffersStillFlushAndEndNormally() {
        RenderType otherItem = RenderType.entityCutout(ResourceLocation.fromNamespaceAndPath("machine_rendering_test", "other_item.png"));
        RenderType font = RenderType.text(ResourceLocation.fromNamespaceAndPath("machine_rendering_test", "font.png"));
        try (CountingBuffers buffers = new CountingBuffers(true)) {
            quad(buffers.getBuffer(otherItem));
            emitBubbles(buffers);
            assertEquals(0, buffers.submissions.size(), "Bubble types must not flush an unrelated shared item buffer");
            quad(buffers.getBuffer(font));
            assertEquals(List.of(otherItem), buffers.submissions);
            buffers.endBatch();
            assertEquals(otherItem, buffers.submissions.getFirst());
            assertEquals(font, buffers.submissions.get(1));
            assertEquals(5, buffers.submissions.size());
            assertTrue(buffers.noStartedBuilders(), "The frame must finish both fixed and shared buffers");
            int count = buffers.submissions.size();
            buffers.endBatch();
            assertEquals(count, buffers.submissions.size(), "A second endBatch must not resubmit closed builders");
        }
    }

    @Test void newRenderBufferOwnersReceiveFreshAllocationsWithTheSameRenderTypes() {
        try (CountingBuffers first = new CountingBuffers(true); CountingBuffers reloaded = new CountingBuffers(true)) {
            assertEquals(3, first.fixed.size());
            assertEquals(first.fixed.keySet(), reloaded.fixed.keySet());
            for (var type : first.fixed.keySet()) assertNotSame(first.fixed.get(type), reloaded.fixed.get(type));
            assertSame(BACKGROUND, first.fixed.firstEntry().getKey());
            MultiBufferSource ordered = BubbleItemRenderer.orderedLayers(reloaded);
            assertSame(reloaded.getBuffer(reloaded.types.get(1)), ordered.getBuffer(Sheets.translucentCullBlockSheet()));
            assertSame(reloaded.getBuffer(reloaded.types.get(2)), ordered.getBuffer(Sheets.translucentItemSheet()));
            // Real buffer owners close all their allocations; production holds types, never buffer/map owners.
            reloaded.endBatch();
            first.endBatch();
            assertTrue(reloaded.noStartedBuilders());
        }
    }

    private static void emitBubbles(CountingBuffers buffers) {
        MultiBufferSource icons = BubbleItemRenderer.orderedLayers(buffers);
        for (int i = 0; i < MACHINES; i++) {
            quad(buffers.getBuffer(BACKGROUND));
            quad(icons.getBuffer(Sheets.translucentCullBlockSheet()));
            quad(icons.getBuffer(Sheets.translucentItemSheet()));
        }
    }

    private static void quad(VertexConsumer vertices) {
        for (float[] p : new float[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}}) {
            vertices.addVertex(p[0], p[1], 0).setColor(255, 255, 255, 255)
                    .setUv(p[0], p[1]).setOverlay(0).setLight(15728880).setNormal(0, 0, 1);
        }
    }

    private static final class CountingBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
        private final SequencedMap<RenderType, ByteBufferBuilder> fixed;
        private final List<RenderType> types;
        private final List<RenderType> submissions = new ArrayList<>();
        private int vertices;

        CountingBuffers(boolean registered) { this(registered ? registeredBuffers() : new LinkedHashMap<>()); }
        private CountingBuffers(SequencedMap<RenderType, ByteBufferBuilder> fixed) {
            super(new ByteBufferBuilder(1536), fixed);
            this.fixed = fixed;
            this.types = new ArrayList<>(fixed.keySet());
        }
        private static SequencedMap<RenderType, ByteBufferBuilder> registeredBuffers() {
            SequencedMap<RenderType, ByteBufferBuilder> fixed = new LinkedHashMap<>();
            BubbleItemRenderer.registerRenderBuffers(new RegisterRenderBuffersEvent(fixed));
            return fixed;
        }
        @Override public void endBatch(RenderType type) {
            BufferBuilder builder = startedBuilders.remove(type);
            if (builder != null) {
                try (MeshData mesh = builder.build()) {
                    if (mesh != null) {
                        submissions.add(type);
                        vertices += mesh.drawState().vertexCount();
                    }
                }
            }
            if (type.equals(lastSharedType)) lastSharedType = null;
        }
        boolean noStartedBuilders() { return startedBuilders.isEmpty() && lastSharedType == null; }
        @Override public void close() {
            endBatch();
            sharedBuffer.close();
            fixed.values().forEach(ByteBufferBuilder::close);
        }
    }
}
