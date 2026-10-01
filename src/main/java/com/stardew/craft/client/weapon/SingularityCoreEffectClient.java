package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

public final class SingularityCoreEffectClient {


    private static final List<Core> CORES = new ArrayList<>();

    private SingularityCoreEffectClient() {}

    public static void add(float x, float y, float z, float radius, int durationTicks, int color) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            return;
        }
        if (durationTicks <= 0 || radius <= 0.0f) {
            return;
        }
        CORES.add(new Core(new Vec3(x, y, z), radius, durationTicks, color));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (CORES.isEmpty()) {
            return;
        }
        Iterator<Core> it = CORES.iterator();
        while (it.hasNext()) {
            Core core = it.next();
            core.age++;
            if (core.age > core.durationTicks) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (CORES.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }


        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick();
        RenderType coreType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer consumer = buffer.getBuffer(coreType);

        for (Core core : CORES) {
            float age = core.age + partial;
            float t = Math.max(0.0f, Math.min(1.0f, age / Math.max(1.0f, core.durationTicks)));

            float pulse = 0.9f + 0.1f * (float) Math.sin(age * 0.35f);
            float alpha = 0.95f;
            if (t < 0.12f) {
                alpha *= (t / 0.12f);
            } else if (t > 0.85f) {
                alpha *= ((1.0f - t) / 0.15f);
            }

            double x = core.pos.x - camPos.x;
            double y = core.pos.y - camPos.y + 0.06;
            double z = core.pos.z - camPos.z;

            int r = (core.color >> 16) & 0xFF;
            int g = (core.color >> 8) & 0xFF;
            int b = core.color & 0xFF;
            int rInner = Math.max(0, (int) (r * 0.55f));
            int gInner = Math.max(0, (int) (g * 0.55f));
            int bInner = Math.max(0, (int) (b * 0.55f));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.scale(core.radius * 2.0f * pulse, core.radius * 2.0f * pulse, core.radius * 2.0f * pulse);

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            WeaponEffectMesh.renderSphere(consumer, pose, 0xF000F0, rInner, gInner, bInner, a,
                0.5f, 8, 12, age * 0.01f, age * 0.015f);

            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(x, y + 0.1, z);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(18.0f));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * 6.0f));
            poseStack.scale(core.radius * 1.25f * pulse, core.radius * 1.25f * pulse, core.radius * 1.25f * pulse);

            PoseStack.Pose billboard = poseStack.last();
            Matrix4f billboardPose = billboard.pose();
            int outerAlpha = Math.max(0, (int) (a * 0.8f));
            WeaponEffectMesh.renderRing3D(consumer, billboardPose, 0xF000F0, r, g, b, outerAlpha,
                0.38f, 0.58f, 0.06f, 36, age * 0.02f);

            poseStack.popPose();
        }

        buffer.endBatch(coreType);
    }

    private static final class Core {
        private final Vec3 pos;
        private final float radius;
        private final int durationTicks;
        private final int color;
        private int age = 0;

        private Core(Vec3 pos, float radius, int durationTicks, int color) {
            this.pos = pos;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.color = color;
        }
    }
}
