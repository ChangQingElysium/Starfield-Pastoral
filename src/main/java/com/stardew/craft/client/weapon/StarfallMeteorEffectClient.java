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

public final class StarfallMeteorEffectClient {



    private static final List<Meteor> METEORS = new ArrayList<>();

    private StarfallMeteorEffectClient() {}

    public static void add(float x, float y, float z, float height, int durationTicks, int color) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            return;
        }
        if (durationTicks <= 0 || height <= 0.1f) {
            return;
        }
        METEORS.add(new Meteor(new Vec3(x, y, z), height, durationTicks, color));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (METEORS.isEmpty()) {
            return;
        }
        Iterator<Meteor> it = METEORS.iterator();
        while (it.hasNext()) {
            Meteor meteor = it.next();
            meteor.age++;
            if (meteor.age > meteor.durationTicks) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (METEORS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }


        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType trailType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer trailConsumer = buffer.getBuffer(trailType);
        float partial = event.getPartialTick();
        RenderType headType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer headConsumer = buffer.getBuffer(headType);

        for (Meteor meteor : METEORS) {
            float age = meteor.age + partial;
            float t = Math.max(0.0f, Math.min(1.0f, age / Math.max(1.0f, meteor.durationTicks)));

            float alpha = 0.95f;
            if (t < 0.2f) {
                alpha *= (t / 0.2f);
            }

            float headY = (float) (meteor.pos.y + meteor.height * (1.0f - t));
            float trailLen = meteor.height * (0.65f + 0.35f * (1.0f - t));

            int r = (meteor.color >> 16) & 0xFF;
            int g = (meteor.color >> 8) & 0xFF;
            int b = meteor.color & 0xFF;
            int rOuter = Math.min(255, (int) (r * 1.15f));
            int gOuter = Math.min(255, (int) (g * 1.15f));
            int bOuter = Math.min(255, (int) (b * 1.15f));

            float flow = (age * 0.08f) % 1.0f;
            renderTrail(trailConsumer, poseStack, camPos, meteor.pos.x, headY - (trailLen * 0.5f), meteor.pos.z,
                trailLen, 0.55f, alpha, r, g, b, flow);

            renderHead(headConsumer, poseStack, camPos, meteor.pos.x, headY, meteor.pos.z, 0.75f,
                alpha, rOuter, gOuter, bOuter, age * 6.0f);
        }

        buffer.endBatch(trailType);
        buffer.endBatch(headType);
    }

    @SuppressWarnings("null")
    private static void renderHead(VertexConsumer consumer, PoseStack poseStack, Vec3 camPos,
                                   double x, double y, double z, float size, float alpha,
                                   int r, int g, int b, float spin) {
        poseStack.pushPose();
        poseStack.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        int light = 0xF000F0;
        int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
        WeaponEffectMesh.renderSphere(consumer, pose, light, r, g, b, a,
            size, 8, 12, spin * 0.002f, spin * 0.003f);

        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(x - camPos.x, y - camPos.y - 0.18, z - camPos.z);
        poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(spin * 0.6f));

        PoseStack.Pose halo = poseStack.last();
        Matrix4f haloPose = halo.pose();
        int haloAlpha = Math.max(0, (int) (a * 0.55f));
        WeaponEffectMesh.renderRing3D(consumer, haloPose, light, r, g, b, haloAlpha,
            size * 0.45f, size * 0.85f, size * 0.08f, 28, spin * 0.0015f);

        poseStack.popPose();
    }

    @SuppressWarnings("null")
    private static void renderTrail(VertexConsumer consumer, PoseStack poseStack, Vec3 camPos,
                                    double x, double y, double z, float length, float width, float alpha, int r, int g, int b, float flow) {
        poseStack.pushPose();
        poseStack.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(flow * 360.0f));
        poseStack.scale(1.0f, 1.0f, 1.0f);

        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        int light = 0xF000F0;
        int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
        WeaponEffectMesh.renderTaperedTube(consumer, pose, light, r, g, b, a,
            width * 0.35f, width * 0.08f, length * 0.5f, 24, flow);

        poseStack.popPose();
    }

    private static final class Meteor {
        private final Vec3 pos;
        private final float height;
        private final int durationTicks;
        private final int color;
        private int age = 0;

        private Meteor(Vec3 pos, float height, int durationTicks, int color) {
            this.pos = pos;
            this.height = height;
            this.durationTicks = durationTicks;
            this.color = color;
        }
    }
}
