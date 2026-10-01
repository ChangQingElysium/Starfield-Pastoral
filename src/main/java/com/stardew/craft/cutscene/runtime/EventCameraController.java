package com.stardew.craft.cutscene.runtime;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side camera controller for cutscene events.
 * When active, overrides the normal player camera via Mixin.
 */
@OnlyIn(Dist.CLIENT)
public final class EventCameraController {

    private static boolean active = false;

    // Previous tick state (for partial-tick interpolation)
    private static double prevX, prevY, prevZ;
    private static float prevYaw, prevPitch;

    // Current tick state
    private static double posX, posY, posZ;
    private static float yaw, pitch;

    // Lerp: start → target over N ticks
    private static double startX, startY, startZ;
    private static float startYaw, startPitch;
    private static double targetX, targetY, targetZ;
    private static float targetYaw, targetPitch;
    private static int lerpTicks = 0;
    private static int lerpTotal = 0;

    // Follow mode
    private static Entity followTarget = null;
    private static Vec3 followOffset = new Vec3(0, 3, -5);

    private EventCameraController() {}

    // ─── API for EventCommands ───

    public static void setPosition(double x, double y, double z, float newYaw, float newPitch) {
        prevX = posX = startX = targetX = x;
        prevY = posY = startY = targetY = y;
        prevZ = posZ = startZ = targetZ = z;
        prevYaw = yaw = startYaw = targetYaw = newYaw;
        prevPitch = pitch = startPitch = targetPitch = newPitch;
        lerpTicks = 0;
        lerpTotal = 0;
        followTarget = null;
        active = true;
    }

    public static void moveTo(double x, double y, double z, float newYaw, float newPitch, int ticks) {
        // Capture current position as lerp start
        startX = posX;
        startY = posY;
        startZ = posZ;
        startYaw = yaw;
        startPitch = pitch;
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = newYaw;
        targetPitch = newPitch;
        lerpTicks = 0;
        lerpTotal = ticks;
        followTarget = null;
    }

    public static void follow(Entity entity) {
        followTarget = entity;
        lerpTicks = 0;
        lerpTotal = 0;
    }

    public static void setFollowOffset(Vec3 offset) {
        followOffset = offset;
    }

    public static void release() {
        active = false;
        followTarget = null;
        lerpTicks = 0;
        lerpTotal = 0;
    }

    public static boolean isActive() {
        return active;
    }

    // ─── Tick (called from ModClientEvents) ───

    public static void tick() {
        if (!active) return;

        // Save previous tick state for partial-tick interpolation
        prevX = posX;
        prevY = posY;
        prevZ = posZ;
        prevYaw = yaw;
        prevPitch = pitch;

        if (followTarget != null) {
            Vec3 targetPos = followTarget.position().add(followOffset);
            posX = targetPos.x;
            posY = targetPos.y;
            posZ = targetPos.z;
            double dx = followTarget.getX() - posX;
            double dy = (followTarget.getY() + followTarget.getEyeHeight() * 0.5) - posY;
            double dz = followTarget.getZ() - posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            pitch = (float) -(Mth.atan2(dy, dist) * Mth.RAD_TO_DEG);
        } else if (lerpTotal > 0 && lerpTicks < lerpTotal) {
            lerpTicks++;
            float t = smoothstep((float) lerpTicks / (float) lerpTotal);
            posX = Mth.lerp(t, startX, targetX);
            posY = Mth.lerp(t, startY, targetY);
            posZ = Mth.lerp(t, startZ, targetZ);
            yaw = Mth.rotLerp(t, startYaw, targetYaw);
            pitch = Mth.lerp(t, startPitch, targetPitch);
        }
    }

    // ─── Partial-tick interpolated getters for Mixin ───

    public static double getInterpolatedX(float partialTick) { return Mth.lerp(partialTick, prevX, posX); }
    public static double getInterpolatedY(float partialTick) { return Mth.lerp(partialTick, prevY, posY); }
    public static double getInterpolatedZ(float partialTick) { return Mth.lerp(partialTick, prevZ, posZ); }
    public static float getInterpolatedYaw(float partialTick) { return Mth.rotLerp(partialTick, prevYaw, yaw); }
    public static float getInterpolatedPitch(float partialTick) { return Mth.lerp(partialTick, prevPitch, pitch); }

    public static boolean isLerping() {
        return lerpTotal > 0 && lerpTicks < lerpTotal;
    }

    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
