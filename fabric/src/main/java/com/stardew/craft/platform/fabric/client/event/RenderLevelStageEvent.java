package com.stardew.craft.platform.fabric.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Frame parameters and the native draw boundary at which the event is published. */
@Environment(EnvType.CLIENT)
public final class RenderLevelStageEvent extends Event {
    private final Stage stage;
    private final LevelRenderer levelRenderer;
    private final PoseStack poseStack;
    private final Matrix4f modelViewMatrix;
    private final Matrix4f projectionMatrix;
    private final int renderTick;
    private final DeltaTracker partialTick;
    private final Camera camera;
    private final Frustum frustum;

    public RenderLevelStageEvent(Stage stage, LevelRenderer renderer, @Nullable PoseStack pose,
            Matrix4f modelView, Matrix4f projection, int renderTick, DeltaTracker partialTick, Camera camera, Frustum frustum) {
        this.stage = stage;
        levelRenderer = renderer;
        poseStack = pose == null ? new PoseStack() : pose;
        modelViewMatrix = modelView;
        projectionMatrix = projection;
        this.renderTick = renderTick;
        this.partialTick = partialTick;
        this.camera = camera;
        this.frustum = frustum;
    }

    public Stage getStage() { return stage; }
    public LevelRenderer getLevelRenderer() { return levelRenderer; }
    public PoseStack getPoseStack() { return poseStack; }
    public Matrix4f getModelViewMatrix() { return modelViewMatrix; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public int getRenderTick() { return renderTick; }
    public DeltaTracker getPartialTick() { return partialTick; }
    public Camera getCamera() { return camera; }
    public Frustum getFrustum() { return frustum; }

    public enum Stage {
        AFTER_SKY(false),
        AFTER_SOLID_BLOCKS(true),
        AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS(true),
        AFTER_CUTOUT_BLOCKS(true),
        AFTER_ENTITIES(false),
        AFTER_BLOCK_ENTITIES(false),
        AFTER_TRANSLUCENT_BLOCKS(true),
        AFTER_TRIPWIRE_BLOCKS(true),
        AFTER_PARTICLES(false),
        AFTER_WEATHER(false),
        AFTER_LEVEL(false);

        private final boolean sectionLayer;
        Stage(boolean sectionLayer) { this.sectionLayer = sectionLayer; }
        public boolean isSectionLayer() { return sectionLayer; }

        public static @Nullable Stage fromRenderType(RenderType type) {
            if (type == RenderType.solid()) return AFTER_SOLID_BLOCKS;
            if (type == RenderType.cutoutMipped()) return AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS;
            if (type == RenderType.cutout()) return AFTER_CUTOUT_BLOCKS;
            if (type == RenderType.translucent()) return AFTER_TRANSLUCENT_BLOCKS;
            if (type == RenderType.tripwire()) return AFTER_TRIPWIRE_BLOCKS;
            return null;
        }

        @Override public String toString() {
            String path = this == AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS ? "after_cutout_mipped_blocks" : name().toLowerCase(java.util.Locale.ROOT);
            return "minecraft:" + path;
        }
    }
}
