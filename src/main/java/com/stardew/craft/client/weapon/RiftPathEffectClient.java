package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RiftPathEffectClient {


    private static final List<Rift> RIFTS = new ArrayList<>();

    private RiftPathEffectClient() {}

    public static void add(float x, float y, float z, float yaw, float length, int durationTicks, int color) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        if (durationTicks <= 0 || length <= 0.0f) {
            return;
        }
        RIFTS.add(new Rift(new Vec3(x, y, z), yaw, length, durationTicks, color));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (RIFTS.isEmpty()) {
            return;
        }
        Iterator<Rift> it = RIFTS.iterator();
        while (it.hasNext()) {
            Rift rift = it.next();
            rift.age++;
            if (rift.age > rift.durationTicks) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (RIFTS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        RenderType riftType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer consumer = buffer.getBuffer(riftType);

        for (Rift rift : RIFTS) {
            float age = rift.age + partial;
            float t = Math.max(0.0f, Math.min(1.0f, age / Math.max(1.0f, rift.durationTicks)));

            float alpha = 0.95f;
            if (t < 0.15f) {
                alpha *= (t / 0.15f);
            } else if (t > 0.75f) {
                alpha *= ((1.0f - t) / 0.25f);
            }

            double x = rift.pos.x - camPos.x;
            double y = rift.pos.y - camPos.y + 0.03;
            double z = rift.pos.z - camPos.z;

            int r = (rift.color >> 16) & 0xFF;
            int g = (rift.color >> 8) & 0xFF;
            int b = rift.color & 0xFF;
            int rOuter = Math.min(255, (int) (r * 1.1f));
            int gOuter = Math.min(255, (int) (g * 1.1f));
            int bOuter = Math.min(255, (int) (b * 1.1f));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(rift.yaw));

            float halfLength = rift.length * 0.5f;
            float halfWidth = 0.26f;

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();
            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            WeaponEffectShapes.crack(consumer, pose, halfLength, halfWidth, rOuter, gOuter, bOuter, a);

            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(x, y + 0.14, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(rift.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(16.0f));

            PoseStack.Pose tilt = poseStack.last();
            Matrix4f tiltPose = tilt.pose();
            int upperAlpha = Math.max(0, (int) (a * 0.55f));
            WeaponEffectShapes.crack(consumer, tiltPose, halfLength, halfWidth, r, g, b, upperAlpha);

            poseStack.popPose();

        }

        buffer.endBatch(riftType);
    }



    private static final class Rift {
        private final Vec3 pos;
        private final float yaw;
        private final float length;
        private final int durationTicks;
        private final int color;
        private int age = 0;

        private Rift(Vec3 pos, float yaw, float length, int durationTicks, int color) {
            this.pos = pos;
            this.yaw = yaw;
            this.length = length;
            this.durationTicks = durationTicks;
            this.color = color;
        }
    }
}
