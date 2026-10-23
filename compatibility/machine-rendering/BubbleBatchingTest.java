package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the actual Minecraft buffer scheduler and actual bubble types; only GPU upload is intercepted. */
class BubbleBatchingTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        // Plain-JVM bootstrap: use Forge's real event instances without ModLauncher's synthetic constructors.
        new net.minecraftforge.network.NetworkEvent(() -> null).getListenerList();
        new net.minecraftforge.network.NetworkEvent.GatherLoginPayloadsEvent(new ArrayList<>(), false).getListenerList();
        net.minecraft.server.Bootstrap.bootStrap();
        // Sheets' static initializer only asks the loader whether to warn about early materials.
        // There is no ModLauncher here; isolate that diagnostic, retaining real Sheets and buffers.
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var singleton = net.minecraftforge.fml.ModLoader.class.getDeclaredField("INSTANCE"); singleton.setAccessible(true);
            Object previous = singleton.get(null);
            singleton.set(null, unsafe.allocateInstance(net.minecraftforge.fml.ModLoader.class));
            try { Class.forName(Sheets.class.getName()); }
            finally { singleton.set(null, previous); }
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    private static final RenderType BACKGROUND = RenderType.entityTranslucent(
            new ResourceLocation("stardewcraft", "textures/gui/bubble.png"));
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
        RenderType otherItem = RenderType.entityCutout(new ResourceLocation("machine_rendering_test", "other_item.png"));
        RenderType font = RenderType.text(new ResourceLocation("machine_rendering_test", "font.png"));
        try (CountingBuffers buffers = new CountingBuffers(true)) {
            quad(buffers.getBuffer(otherItem));
            emitBubbles(buffers);
            // 1.20.1 ends the preceding shared batch when entering a fixed type; 1.21 keeps it open.
            // Preserve the native version's ordering and still batch all 500 bubbles together.
            assertEquals(List.of(otherItem), buffers.submissions);
            quad(buffers.getBuffer(font));
            assertEquals(List.of(otherItem), buffers.submissions);
            buffers.endBatch();
            assertEquals(otherItem, buffers.submissions.get(0));
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
            assertSame(BACKGROUND, first.fixed.keySet().iterator().next());
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
            vertices.vertex(p[0], p[1], 0).color(255, 255, 255, 255)
                    .uv(p[0], p[1]).overlayCoords(0).uv2(15728880).normal(0, 0, 1).endVertex();
        }
    }

    private static final class CountingBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
        private final Map<RenderType, BufferBuilder> fixed;
        private final List<RenderType> types;
        private final List<RenderType> submissions = new ArrayList<>();
        private int vertices;

        CountingBuffers(boolean registered) { this(registered ? registeredBuffers() : new LinkedHashMap<>()); }
        private CountingBuffers(Map<RenderType, BufferBuilder> fixed) {
            super(new BufferBuilder(1536), fixed);
            this.fixed = fixed;
            this.types = new ArrayList<>(fixed.keySet());
        }
        private static Map<RenderType, BufferBuilder> registeredBuffers() {
            Map<RenderType, BufferBuilder> fixed = new LinkedHashMap<>();
            BubbleItemRenderer.registerRenderBuffers(new RegisterRenderBuffersEvent(fixed));
            return fixed;
        }
        @Override public void endBatch(RenderType type) {
            BufferBuilder target = fixedBuffers.getOrDefault(type, builder);
            boolean last = lastState.equals(type.asOptional());
            if (!last && target == builder || !startedBuffers.remove(target)) return;
            BufferBuilder.RenderedBuffer mesh = target.end();
            try {
                submissions.add(type);
                vertices += mesh.drawState().vertexCount();
            } finally { mesh.release(); }
            if (last) lastState = Optional.empty();
        }
        boolean noStartedBuilders() { return startedBuffers.isEmpty() && lastState.isEmpty(); }
        @Override public void close() { endBatch(); }
    }
}
