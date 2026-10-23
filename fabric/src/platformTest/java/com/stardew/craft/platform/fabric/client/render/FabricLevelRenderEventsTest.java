package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.RenderLevelStageEvent;
import com.stardew.craft.platform.fabric.client.event.RenderLevelStageEvent.Stage;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricLevelRenderEventsTest {
    private static final EventBus BUS = new EventBus();
    private static final List<RenderLevelStageEvent> EVENTS = new ArrayList<>();

    @BeforeAll
    static void installRealCallbacks() {
        BUS.addListener(RenderLevelStageEvent.class, EVENTS::add);
        FabricLevelRenderEvents.install(BUS);
    }

    @BeforeEach
    void clearEvents() { EVENTS.clear(); }

    @Test
    void entityParticleAndWeatherEventsRetainTheirOwnStageAndTheActualFrameParameters() {
        var frame = frame(0.3f, 73);
        try (var scope = frame.open()) {
            frame.bindPose();
            for (var stage : new Stage[]{Stage.AFTER_ENTITIES, Stage.AFTER_PARTICLES, Stage.AFTER_WEATHER}) {
                FabricLevelRenderEvents.postNativeStage(stage, null);
                assertFrame(EVENTS.getLast(), frame, stage);
            }
        }
    }

    @Test
    void automaticSectionStagesKeepNeoForgeIdentityStackContractWithoutDroppingTheViewMatrix() {
        var frame = frame(0.2f, 4);
        try (var scope = frame.open()) {
            frame.bindPose();
            FabricLevelRenderEvents.postNativeStage(Stage.AFTER_TRANSLUCENT_BLOCKS, null);
            var event = EVENTS.getLast();
            assertFrame(event, frame, Stage.AFTER_TRANSLUCENT_BLOCKS);
            assertNotSame(frame.pose(), event.getPoseStack());
            assertEquals(new Matrix4f(), event.getPoseStack().last().pose());
        }
    }

    @Test
    void missingLateStageStackFailsInsteadOfSupplyingAFakeDefault() {
        try (var scope = frame(0, 1).open()) {
            assertThrows(IllegalStateException.class,
                    () -> FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null));
            assertTrue(EVENTS.isEmpty());
        }
    }

    @Test
    void nestedPortalRestoresOuterFrameDespiteFabricReusingTheExactSameMutableContext() {
        var outer = frame(0.1f, 21);
        var inner = frame(0.8f, 42);
        var shared = new WorldRenderContextImpl();
        try (var outerScope = outer.open()) {
            outer.prepareFabricContext(shared);
            WorldRenderEvents.START.invoker().onStart(shared);
            outer.bindPose();
            WorldRenderEvents.AFTER_ENTITIES.invoker().afterEntities(shared);
            try (var innerScope = inner.open()) {
                inner.prepareFabricContext(shared);
                WorldRenderEvents.START.invoker().onStart(shared);
                inner.bindPose();
                WorldRenderEvents.AFTER_ENTITIES.invoker().afterEntities(shared);
                WorldRenderEvents.END.invoker().onEnd(shared);
                assertFrame(EVENTS.getLast(), inner, Stage.AFTER_LEVEL);
            }
            // Real FAPI still holds the nested values here: a Deque of that
            // shared context object would avoid null but return the wrong view.
            assertSame(inner.camera(), shared.camera());
            assertSame(inner.pose(), shared.matrixStack());
            FabricLevelRenderEvents.postNativeStage(Stage.AFTER_TRANSLUCENT_BLOCKS, null);
            assertFrame(EVENTS.getLast(), outer, Stage.AFTER_TRANSLUCENT_BLOCKS);
            FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null);
            assertFrame(EVENTS.getLast(), outer, Stage.AFTER_PARTICLES);
            WorldRenderEvents.END.invoker().onEnd(shared);
            assertFrame(EVENTS.getLast(), outer, Stage.AFTER_LEVEL);
        }
        assertEquals(List.of(Stage.AFTER_ENTITIES, Stage.AFTER_ENTITIES, Stage.AFTER_LEVEL,
                Stage.AFTER_TRANSLUCENT_BLOCKS, Stage.AFTER_PARTICLES, Stage.AFTER_LEVEL),
                EVENTS.stream().map(RenderLevelStageEvent::getStage).toList());
        assertThrows(IllegalStateException.class,
                () -> FabricLevelRenderEvents.postNativeStage(Stage.AFTER_TRANSLUCENT_BLOCKS, null));
    }

    @Test
    void failedNestedDrawUnwindsWithoutInventingAnAfterLevelOrLosingTheOuterPose() {
        var outer = frame(0.2f, 7);
        var inner = frame(0.9f, 8);
        var shared = new WorldRenderContextImpl();
        var failure = new IllegalArgumentException("portal draw failed before the normal RETURN callback");
        try (var outerScope = outer.open()) {
            outer.prepareFabricContext(shared);
            outer.bindPose();
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> {
                try (var innerScope = inner.open()) {
                    inner.prepareFabricContext(shared);
                    inner.bindPose();
                    FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null);
                    throw failure;
                }
            }));
            assertFrame(EVENTS.getLast(), inner, Stage.AFTER_PARTICLES);
            FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null);
            assertFrame(EVENTS.getLast(), outer, Stage.AFTER_PARTICLES);
            WorldRenderEvents.END.invoker().onEnd(shared);
            assertFrame(EVENTS.getLast(), outer, Stage.AFTER_LEVEL);
        }
        assertEquals(List.of(Stage.AFTER_PARTICLES, Stage.AFTER_PARTICLES, Stage.AFTER_LEVEL),
                EVENTS.stream().map(RenderLevelStageEvent::getStage).toList());
        assertThrows(IllegalStateException.class,
                () -> FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null));
    }

    @Test
    void anOuterFailureAlsoClearsTheFrameBeforeTheNextNormalDraw() {
        var failed = frame(0.3f, 1);
        assertThrows(IllegalArgumentException.class, () -> {
            try (var scope = failed.open()) {
                failed.bindPose();
                throw new IllegalArgumentException("draw failed");
            }
        });
        assertTrue(EVENTS.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null));
        var next = frame(0.6f, 2);
        try (var scope = next.open()) {
            next.bindPose();
            FabricLevelRenderEvents.postNativeStage(Stage.AFTER_PARTICLES, null);
            assertFrame(EVENTS.getLast(), next, Stage.AFTER_PARTICLES);
        }
    }

    private static void assertFrame(RenderLevelStageEvent event, Frame frame, Stage stage) {
        assertSame(stage, event.getStage());
        if (!stage.isSectionLayer() && stage != Stage.AFTER_SKY) assertSame(frame.pose(), event.getPoseStack());
        assertSame(frame.view(), event.getModelViewMatrix());
        assertSame(frame.projection(), event.getProjectionMatrix());
        assertSame(frame.camera(), event.getCamera());
        assertSame(frame.frustum(), event.getFrustum());
        assertSame(DeltaTracker.ONE, event.getPartialTick());
        assertEquals(frame.ticks(), event.getRenderTick());
        assertFalse(event.isCancelable());
    }

    private static Frame frame(float rotation, int ticks) {
        var pose = new PoseStack();
        pose.translate(rotation, ticks, 4);
        var view = new Matrix4f().rotateY(rotation);
        var projection = new Matrix4f().scaling(1 + rotation);
        return new Frame(pose, view, projection, new Camera(), new Frustum(view, projection), ticks);
    }

    private record Frame(PoseStack pose, Matrix4f view, Matrix4f projection, Camera camera, Frustum frustum, int ticks) {
        FabricLevelRenderEvents.FrameScope open() {
            // No LevelRenderer/window construction is needed to verify the
            // same-renderer call stack; the renderer identity token is null.
            return FabricLevelRenderEvents.beginFrame(null, DeltaTracker.ONE, camera, view, projection, ticks, frustum);
        }
        void bindPose() { FabricLevelRenderEvents.captureMatrixStack(null, pose); }
        void prepareFabricContext(WorldRenderContextImpl context) {
            context.prepare(null, DeltaTracker.ONE, false, camera, null, null,
                    projection, view, null, null, false, null);
            context.setFrustum(frustum);
            context.setMatrixStack(pose);
        }
    }
}
