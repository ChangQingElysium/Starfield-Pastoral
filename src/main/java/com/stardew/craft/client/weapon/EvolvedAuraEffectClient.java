package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.Config;
import com.stardew.craft.combat.VfxColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class EvolvedAuraEffectClient {



    private EvolvedAuraEffectClient() {}

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        int stacks = SingularityClientState.getStacks(player);
        if (stacks < 12) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType runeType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        RenderType coreType = WeaponEffectRenderTypes.MOLTEN_GLOW;

        float age = player.tickCount + event.getPartialTick();
        float pulse = 0.85f + 0.15f * (float) Math.sin(age * 0.2f);
        float radius = 1.1f * pulse;

        int color = VfxColors.INFINITY_GOLD;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int alpha = 180;

        Vec3 pos = player.position();
        double x = pos.x - camPos.x;
        double y = pos.y - camPos.y + 0.02;
        double z = pos.z - camPos.z;

        VertexConsumer runeConsumer = buffer.getBuffer(runeType);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.5f));
        poseStack.scale(radius * 2.0f, radius * 2.0f, radius * 2.0f);

        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        float size = 0.5f;
        WeaponEffectShapes.ring(runeConsumer, pose, size, r, g, b, alpha);
        poseStack.popPose();

        VertexConsumer coreConsumer = buffer.getBuffer(coreType);
        poseStack.pushPose();
        poseStack.translate(x, y + 0.8, z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.9f, 1.6f, 0.9f);

        PoseStack.Pose column = poseStack.last();
        Matrix4f columnPose = column.pose();
        int colAlpha = 140;
        WeaponEffectShapes.ring(coreConsumer, columnPose, size, r, g, b, colAlpha);
        poseStack.popPose();

        buffer.endBatch(runeType);
        buffer.endBatch(coreType);
    }



}
