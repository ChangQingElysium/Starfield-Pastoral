package com.stardew.craft.fishing;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** The same north-facing coordinates are used by the hit regions and the three fish models. */
public final class FishMarketCrateLayout {
    public static final int SLOTS = 3;
    public static final double ICE_HEIGHT = 6.5 / 16;
    private static final double[] CENTERS = {7.7 / 16, 1, 24.3 / 16};
    private static final float[] YAW = {82, 96, 87};

    private FishMarketCrateLayout() {}

    public static int quarterTurns(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }

    public static int slotAt(Vec3 relativeHit, Direction facing) {
        double x = relativeHit.x - .5, z = relativeHit.z - .5;
        for (int i = 0; i < quarterTurns(facing); i++) { double oldX = x; x = z; z = -oldX; }
        x += .5;
        return x < (CENTERS[0] + CENTERS[1]) / 2 ? 0 : x < (CENTERS[1] + CENTERS[2]) / 2 ? 1 : 2;
    }

    public static double centerX(int slot) { return CENTERS[slot]; }
    public static float yaw(int slot) { return YAW[slot]; }

    public record Pose(double unitScale, double centerY, float roll, float yaw) {}

    /** Fit the complete rotated envelope, including negative hulls, between the ice and the rim. */
    public static Pose fit(double width, double height, double depth, boolean upright, int slot) {
        double h = upright ? height : depth, d = upright ? depth : height;
        double angle = Math.toRadians(yaw(slot));
        double spanX = Math.abs(Math.cos(angle)) * width + Math.abs(Math.sin(angle)) * d;
        double spanZ = Math.abs(Math.sin(angle)) * width + Math.abs(Math.cos(angle)) * d;
        double scale = Math.min(.55, Math.min(7 / spanX, Math.min(10 / spanZ, 3.5 / h))) / 16;
        return new Pose(scale, ICE_HEIGHT + h * scale / 2, upright ? 0 : 90, yaw(slot));
    }
}
