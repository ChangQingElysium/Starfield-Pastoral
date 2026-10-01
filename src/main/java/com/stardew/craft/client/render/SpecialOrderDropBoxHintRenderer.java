package com.stardew.craft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.specialorder.SpecialOrderDropBoxAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class SpecialOrderDropBoxHintRenderer {
    private static final double HINT_RANGE = 9.0D;
    private static final double HINT_RANGE_SQ = HINT_RANGE * HINT_RANGE;
    private static final double FADE_START = 6.0D;

    private static final float R = 0.95F;
    private static final float G = 0.72F;
    private static final float B = 0.32F;

    private SpecialOrderDropBoxHintRenderer() {
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !ModDimensions.STARDEW_VALLEY.equals(mc.level.dimension())) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        boolean rendered = false;
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (SpecialOrderDropBoxAnchor anchor : SpecialOrderDropBoxAnchor.all()) {
            if (!ClientSpecialOrderDropBoxHints.isActive(anchor.dropBoxId())) {
                continue;
            }

            double distanceSq = anchor.bounds().getCenter().distanceToSqr(player.position());
            if (distanceSq > HINT_RANGE_SQ) {
                continue;
            }

            float alpha = alphaForDistance(Math.sqrt(distanceSq));
            AABB box = anchor.bounds().inflate(0.045D);
            LevelRenderer.renderLineBox(poseStack, lines, box, R, G, B, alpha);
            rendered = true;
        }

        poseStack.popPose();

        if (rendered) {
            buffer.endBatch(RenderType.lines());
        }
    }

    private static float alphaForDistance(double distance) {
        if (distance <= FADE_START) {
            return 0.9F;
        }
        double ratio = 1.0D - (distance - FADE_START) / (HINT_RANGE - FADE_START);
        return (float) Math.max(0.0D, Math.min(0.9D, ratio * 0.9D));
    }
}
