package com.stardew.craft.platform.fabric.client.render;

import com.stardew.craft.platform.fabric.client.event.RenderLevelStageEvent;
import com.stardew.craft.platform.fabric.client.event.RenderLevelStageEvent.Stage;
import com.stardew.craft.platform.fabric.event.IEventBus;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

/** Per-invocation native frame data; Fabric callbacks retain their original draw-stage boundaries. */
@Environment(EnvType.CLIENT)
public final class FabricLevelRenderEvents {
    private static final ThreadLocal<FrameScope> FRAME = new ThreadLocal<>();
    private static IEventBus installedBus;

    private FabricLevelRenderEvents() {}

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Level render events already have an installed bus");
        WorldRenderEvents.AFTER_ENTITIES.register(context -> postNativeStage(Stage.AFTER_ENTITIES, context.worldRenderer()));
        WorldRenderEvents.END.register(context -> postNativeStage(Stage.AFTER_LEVEL, context.worldRenderer()));
        installedBus = bus;
    }

    /** The renderLevel wrapper closes this in finally, including a failed nested portal draw. */
    public static FrameScope beginFrame(LevelRenderer renderer, DeltaTracker partialTick, Camera camera,
            Matrix4f modelView, Matrix4f projection, int ticks, Frustum frustum) {
        var frame = new FrameScope(FRAME.get(), renderer, partialTick, camera, modelView, projection, ticks, frustum);
        FRAME.set(frame);
        return frame;
    }

    /** Captured at the native NEW, after opaque layers and before entity rendering. */
    public static PoseStack captureMatrixStack(LevelRenderer renderer, PoseStack pose) {
        var frame = matchingFrame(renderer);
        if (frame.pose != null && frame.pose != pose) {
            throw new IllegalStateException("Level render frame has multiple native matrix stacks");
        }
        frame.pose = Objects.requireNonNull(pose, "native matrix stack");
        return pose;
    }

    public static void postNativeStage(Stage stage, LevelRenderer renderer) {
        if (installedBus == null) throw new IllegalStateException("Level render events have no installed bus");
        installedBus.post(matchingFrame(renderer).event(stage));
    }

    private static FrameScope matchingFrame(LevelRenderer renderer) {
        var frame = FRAME.get();
        if (frame == null || frame.renderer != renderer) {
            throw new IllegalStateException("Native level draw stage has no matching installed Fabric frame");
        }
        return frame;
    }

    /**
     * FAPI reuses one mutable WorldRenderContextImpl per renderer. A stack of
     * those objects would still lose the outer camera, matrices and buffers.
     * Store the actual native arguments and call-local PoseStack instead.
     */
    public static final class FrameScope implements AutoCloseable {
        private final FrameScope previous;
        private final LevelRenderer renderer;
        private final DeltaTracker partialTick;
        private final Camera camera;
        private final Matrix4f modelView;
        private final Matrix4f projection;
        private final int ticks;
        private final Frustum frustum;
        private PoseStack pose;
        private boolean closed;

        private FrameScope(FrameScope previous, LevelRenderer renderer, DeltaTracker partialTick, Camera camera,
                Matrix4f modelView, Matrix4f projection, int ticks, Frustum frustum) {
            this.previous = previous;
            this.renderer = renderer;
            this.partialTick = partialTick;
            this.camera = camera;
            this.modelView = modelView;
            this.projection = projection;
            this.ticks = ticks;
            this.frustum = frustum;
        }

        private RenderLevelStageEvent event(Stage stage) {
            // Neo's automatic section stages and AFTER_SKY construct an identity
            // stack; other stages expose the native renderLevel local unchanged.
            var stack = stage.isSectionLayer() || stage == Stage.AFTER_SKY ? null : pose;
            if (stack == null && !stage.isSectionLayer() && stage != Stage.AFTER_SKY) {
                throw new IllegalStateException("World render matrix stack is unavailable at " + stage);
            }
            return new RenderLevelStageEvent(stage, renderer, stack, modelView,
                    projection, ticks, partialTick, camera, frustum);
        }

        @Override
        public void close() {
            if (closed) return;
            if (FRAME.get() != this) throw new IllegalStateException("Level render frames must close in call order");
            if (previous == null) FRAME.remove();
            else FRAME.set(previous);
            closed = true;
        }
    }
}
