package com.stardew.craft.fishing;

/** Fixed mouth hook shared by every fish; only whole-fish orientation and scale change. */
public final class PlacedFishLayout {
    public static final double HOOK_X = 8, HOOK_Y = 12, HOOK_Z = 8.5;
    private PlacedFishLayout() {}
    public record Pose(double scale, double centerX, double centerY, double centerZ, float roll, float pitch) {}

    public static Pose floor(double width, double height, double depth, boolean upright) {
        double h = upright ? height : depth, d = upright ? depth : height;
        double scale = Math.min(.85, Math.min(14 / width, Math.min(14 / d, 8 / h)));
        return new Pose(scale, 8, h * scale / 2, 8, upright ? 0 : 90, 0);
    }

    /** The existing catch-animation anchor is the hook tip, never a fin-dependent support point. */
    public static Pose hanging(double[] min, double[] max, double[] mouth, boolean verticalGrip) {
        double left = verticalGrip ? min[0] - mouth[0] : mouth[1] - max[1];
        double right = verticalGrip ? max[0] - mouth[0] : mouth[1] - min[1];
        double bottom = verticalGrip ? min[1] - mouth[1] : min[0] - mouth[0];
        double top = verticalGrip ? max[1] - mouth[1] : max[0] - mouth[0];
        double scale = .85;
        scale = Math.min(scale, 6.5 / Math.max(Math.abs(left), Math.abs(right)));
        scale = Math.min(scale, 5.5 / Math.max(Math.abs(min[2] - mouth[2]), Math.abs(max[2] - mouth[2])));
        if (bottom < 0) scale = Math.min(scale, (HOOK_Y - 1) / -bottom);
        if (top > 0) scale = Math.min(scale, (15.8 - HOOK_Y) / top);
        double dx = (min[0] + max[0]) / 2 - mouth[0], dy = (min[1] + max[1]) / 2 - mouth[1];
        double dz = (min[2] + max[2]) / 2 - mouth[2];
        return new Pose(scale, HOOK_X + (verticalGrip ? dx : -dy) * scale,
                HOOK_Y + (verticalGrip ? dy : dx) * scale, HOOK_Z + dz * scale, 0, verticalGrip ? 0 : -90);
    }
}
