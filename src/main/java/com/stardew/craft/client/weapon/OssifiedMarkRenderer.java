package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

@SuppressWarnings("unused")
public final class OssifiedMarkRenderer {


    private static final RenderType MARK_RENDER_TYPE = WeaponEffectRenderTypes.MOLTEN_GLOW;

    private OssifiedMarkRenderer() {}

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
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
        VertexConsumer consumer = buffer.getBuffer(MARK_RENDER_TYPE);
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        for (LivingEntity entity : entities) {
            if (!OssifiedMarkClientState.isMarked(entity.getId(), nowTick)) {
                continue;
            }

            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var bounds = entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()));
            Vec3 center = bounds.getCenter();
            Vec3 pos = bounds.clip(camPos, center).orElse(center).add(camPos.subtract(center).normalize().scale(0.04));
            double x = pos.x - camPos.x;
            double y = pos.y - camPos.y;
            double z = pos.z - camPos.z;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(dispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            float scale = Math.clamp(entity.getBbHeight()*0.65f, 0.55f, 1.4f);
            poseStack.scale(scale, scale, scale);

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            MineralEffectGeometry.boneMark(consumer, pose, OssifiedMarkClientState.fade(entity.getId(), nowTick + partial));

            poseStack.popPose();
        }

        buffer.endBatch(MARK_RENDER_TYPE);
    }


}
