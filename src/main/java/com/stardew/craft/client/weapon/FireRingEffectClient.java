package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

public final class FireRingEffectClient {


    private static final int EXTRA_FADE_TICKS = 6;

    private static final List<Ring> RINGS = new ArrayList<>();

    private FireRingEffectClient() {}

    public static void add(float x, float y, float z, float maxRadius, int durationTicks) {
        if (durationTicks <= 0 || maxRadius <= 0.0f) {
            return;
        }
        RINGS.add(new Ring(new Vec3(x, y, z), maxRadius, durationTicks));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (RINGS.isEmpty()) {
            return;
        }
        Iterator<Ring> it = RINGS.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            ring.age++;
            if (ring.age > ring.durationTicks + EXTRA_FADE_TICKS) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (RINGS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType ringType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer consumer = buffer.getBuffer(ringType);

        for (Ring ring : RINGS) {
            float progress = ring.durationTicks <= 0 ? 1.0f : (ring.age / (float) ring.durationTicks);
            float clamped = Math.max(0.0f, Math.min(1.0f, progress));
            float radius = Math.max(0.25f, ring.maxRadius * clamped);

            int alpha = 255;
            if (ring.age > ring.durationTicks) {
                float fadeT = (ring.age - ring.durationTicks) / (float) EXTRA_FADE_TICKS;
                fadeT = Math.max(0.0f, Math.min(1.0f, fadeT));
                radius = ring.maxRadius * (1.0f + 0.2f * fadeT);
                alpha = (int) (255 * (1.0f - fadeT));
            }

            double x = ring.pos.x - camPos.x;
            double y = ring.pos.y - camPos.y + 0.02;
            double z = ring.pos.z - camPos.z;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.scale(radius * 2.0f, radius * 2.0f, radius * 2.0f);

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            float size = 0.5f;
            WeaponEffectShapes.ring(consumer, pose, size, 255, 131, 46, alpha);

            poseStack.popPose();
        }

        buffer.endBatch(ringType);
    }



    private static final class Ring {
        private final Vec3 pos;
        private final float maxRadius;
        private final int durationTicks;
        private int age = 0;

        private Ring(Vec3 pos, float maxRadius, int durationTicks) {
            this.pos = pos;
            this.maxRadius = maxRadius;
            this.durationTicks = durationTicks;
        }
    }
}
