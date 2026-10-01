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

public final class AccretionDiskEffectClient {


    private static final List<Disk> DISKS = new ArrayList<>();

    private AccretionDiskEffectClient() {}

    public static void add(float x, float y, float z, float radius, int durationTicks, int color) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        if (durationTicks <= 0 || radius <= 0.0f) {
            return;
        }
        DISKS.add(new Disk(new Vec3(x, y, z), radius, durationTicks, color));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (DISKS.isEmpty()) {
            return;
        }
        Iterator<Disk> it = DISKS.iterator();
        while (it.hasNext()) {
            Disk disk = it.next();
            disk.age++;
            if (disk.age > disk.durationTicks) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (DISKS.isEmpty()) {
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
        RenderType diskType = WeaponEffectRenderTypes.MOLTEN_GLOW;
        VertexConsumer consumer = buffer.getBuffer(diskType);

        for (Disk disk : DISKS) {
            float age = disk.age + partial;
            float t = Math.max(0.0f, Math.min(1.0f, age / Math.max(1.0f, disk.durationTicks)));

            float alpha = 0.9f;
            if (t < 0.12f) {
                alpha *= (t / 0.12f);
            } else if (t > 0.85f) {
                alpha *= ((1.0f - t) / 0.15f);
            }

            double x = disk.pos.x - camPos.x;
            double y = disk.pos.y - camPos.y + 0.02;
            double z = disk.pos.z - camPos.z;

            int r = (disk.color >> 16) & 0xFF;
            int g = (disk.color >> 8) & 0xFF;
            int b = disk.color & 0xFF;
            int rInner = Math.max(0, (int) (r * 0.65f));
            int gInner = Math.max(0, (int) (g * 0.65f));
            int bInner = Math.max(0, (int) (b * 0.65f));
            int rOuter = Math.min(255, (int) (r * 1.15f));
            int gOuter = Math.min(255, (int) (g * 1.15f));
            int bOuter = Math.min(255, (int) (b * 1.15f));
            float outerScroll = (age * 0.025f) % 1.0f;
            float innerScroll = (age * 0.045f) % 1.0f;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            float rot = (age * 6.0f) % 360.0f;
            poseStack.mulPose(Axis.YP.rotationDegrees(rot));
            poseStack.scale(disk.radius, disk.radius, disk.radius);

            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            WeaponEffectMesh.renderRing3D(consumer, pose, 0xF000F0, rOuter, gOuter, bOuter, a,
                0.58f, 1.05f, 0.06f, 48, outerScroll);

            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate(x, y + 0.06, z);
            float innerRot = (age * 10.0f) % 360.0f;
            poseStack.mulPose(Axis.YP.rotationDegrees(innerRot));
            poseStack.scale(disk.radius * 0.85f, disk.radius * 0.85f, disk.radius * 0.85f);

            PoseStack.Pose inner = poseStack.last();
            Matrix4f innerPose = inner.pose();
            int innerAlpha = Math.max(0, (int) (a * 0.75f));
            WeaponEffectMesh.renderRing3D(consumer, innerPose, 0xF000F0, rInner, gInner, bInner, innerAlpha,
                0.45f, 0.82f, 0.05f, 48, innerScroll);

            poseStack.popPose();
        }

        buffer.endBatch(diskType);
    }

    private static final class Disk {
        private final Vec3 pos;
        private final float radius;
        private final int durationTicks;
        private final int color;
        private int age = 0;

        private Disk(Vec3 pos, float radius, int durationTicks, int color) {
            this.pos = pos;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.color = color;
        }
    }
}
