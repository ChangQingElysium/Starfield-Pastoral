package com.stardew.craft.client.weapon;

import com.stardew.craft.port.PortCamera;
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

public final class SingularityRuneEffectClient {


    private static final List<Rune> RUNES = new ArrayList<>();

    private SingularityRuneEffectClient() {}

    public static void add(float x, float y, float z, float radius, int durationTicks, int color) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            return;
        }
        if (durationTicks <= 0 || radius <= 0.0f) {
            return;
        }
        RUNES.add(new Rune(new Vec3(x, y, z), radius, durationTicks, color));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (RUNES.isEmpty()) {
            return;
        }
        Iterator<Rune> it = RUNES.iterator();
        while (it.hasNext()) {
            Rune rune = it.next();
            rune.age++;
            if (rune.age > rune.durationTicks) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (RUNES.isEmpty()) {
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
        RenderType runeType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer consumer = buffer.getBuffer(runeType);

        for (Rune rune : RUNES) {
            float age = rune.age + partial;
            float t = Math.max(0.0f, Math.min(1.0f, age / Math.max(1.0f, rune.durationTicks)));

            float alpha = 0.9f;
            if (t < 0.12f) {
                alpha *= (t / 0.12f);
            } else if (t > 0.85f) {
                alpha *= ((1.0f - t) / 0.15f);
            }

            double x = rune.pos.x - camPos.x;
            double y = rune.pos.y - camPos.y + 0.03;
            double z = rune.pos.z - camPos.z;

            int r = (rune.color >> 16) & 0xFF;
            int g = (rune.color >> 8) & 0xFF;
            int b = rune.color & 0xFF;
            int rOuter = Math.min(255, (int) (r * 1.1f));
            int gOuter = Math.min(255, (int) (g * 1.1f));
            int bOuter = Math.min(255, (int) (b * 1.1f));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            float rot = (age * 4.0f) % 360.0f;
            poseStack.mulPose(Axis.YP.rotationDegrees(rot));
            poseStack.scale(rune.radius * 2.0f, rune.radius * 2.0f, rune.radius * 2.0f);

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            float size = 0.5f;
            WeaponEffectShapes.ring(consumer, pose, size, rOuter, gOuter, bOuter, a);

            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(x, y + 0.18, z);
            poseStack.mulPose(PortCamera.cameraOrientation(Minecraft.getInstance().getEntityRenderDispatcher()));
            poseStack.scale(rune.radius * 1.2f, rune.radius * 1.2f, rune.radius * 1.2f);

            PoseStack.Pose billboard = poseStack.last();
            Matrix4f billboardPose = billboard.pose();
            int upperAlpha = Math.max(0, (int) (a * 0.6f));
            WeaponEffectShapes.ring(consumer, billboardPose, size, r, g, b, upperAlpha);

            poseStack.popPose();
        }

        buffer.endBatch(runeType);
    }



    private static final class Rune {
        private final Vec3 pos;
        private final float radius;
        private final int durationTicks;
        private final int color;
        private int age = 0;

        private Rune(Vec3 pos, float radius, int durationTicks, int color) {
            this.pos = pos;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.color = color;
        }
    }
}
