package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

public final class YetiFreezeRenderer {

    private static final ResourceLocation ICE_TEXTURE = new ResourceLocation(
        "minecraft",
        "textures/block/ice.png"
    );

    @SuppressWarnings("null")
    private static final RenderType ICE_RENDER_TYPE = RenderType.create(
        "stardew_yeti_freeze",
        DefaultVertexFormat.POSITION_TEX_COLOR,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionTexColorShader))
            .setTextureState(new RenderType.TextureStateShard(ICE_TEXTURE, false, false))
            .setTransparencyState(new RenderType.TransparencyStateShard("translucent_transparency", () -> {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            }, () -> {
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }))
            .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false))
            .setCullState(new RenderType.CullStateShard(false))
            .setDepthTestState(new RenderType.DepthTestStateShard("always", 519))
            .createCompositeState(false)
    );

    private YetiFreezeRenderer() {}

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        long nowTick = mc.level.getGameTime();
        AABB box = new AABB(
            camPos.x - 48, camPos.y - 48, camPos.z - 48,
            camPos.x + 48, camPos.y + 48, camPos.z + 48
        );

        List<LivingEntity> entities = mc.level.getEntitiesOfClass(LivingEntity.class, box);
        if (entities.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(ICE_RENDER_TYPE);
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        for (LivingEntity entity : entities) {
            if (!YetiFreezeClientState.isFrozen(entity.getId(), nowTick)) {
                continue;
            }

            Vec3 pos = entity.position().add(0, entity.getBbHeight() * 0.55, 0);
            double x = pos.x - camPos.x;
            double y = pos.y - camPos.y;
            double z = pos.z - camPos.z;

            float halfW = Math.max(0.6f, entity.getBbWidth() * 0.9f);
            float halfH = Math.max(0.9f, entity.getBbHeight() * 0.6f);

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(dispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            vertex(consumer, pose, 0xF000F0, -halfW, -halfH, 0, 1, 180);
            vertex(consumer, pose, 0xF000F0, halfW, -halfH, 1, 1, 180);
            vertex(consumer, pose, 0xF000F0, halfW, halfH, 1, 0, 180);
            vertex(consumer, pose, 0xF000F0, -halfW, halfH, 0, 0, 180);

            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            last = poseStack.last();
            pose = last.pose();
            vertex(consumer, pose, 0xF000F0, -halfW, -halfH, 0, 1, 160);
            vertex(consumer, pose, 0xF000F0, halfW, -halfH, 1, 1, 160);
            vertex(consumer, pose, 0xF000F0, halfW, halfH, 1, 0, 160);
            vertex(consumer, pose, 0xF000F0, -halfW, halfH, 0, 0, 160);

            poseStack.popPose();
        }

        buffer.endBatch(ICE_RENDER_TYPE);
    }

    @SuppressWarnings("null")
    private static void vertex(VertexConsumer consumer, Matrix4f pose, int light,
                               float x, float y, float u, float v, int alpha) {
        consumer.addVertex(pose, x, y, 0.0f)
            .setColor(200, 220, 255, alpha)
            .setUv(u, v)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
    }
}
