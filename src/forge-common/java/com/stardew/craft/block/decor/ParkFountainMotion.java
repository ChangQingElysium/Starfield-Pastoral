package com.stardew.craft.block.decor;

/** The approved continuous prototype motion, in model pixels and seconds. */
public final class ParkFountainMotion {
    private ParkFountainMotion() {}
    public record Pose(double x, double y, double z, float sx, float sy, float sz, float alpha) {}

    public static Pose sample(String kind, int side, int index, double seconds) {
        int dx = side == 0 ? 1 : side == 2 ? -1 : 0;
        int dz = side == 1 ? 1 : side == 3 ? -1 : 0;
        double offset = side * .137;
        if (kind.equals("drop")) {
            double t = fract(seconds / .9 + index * .25 + offset), r = 6 + 18 * t;
            return new Pose(40 + dx * r, 44 + 4 * t - 36 * t * t, 40 + dz * r, 1, (float) (.8 + .3 * t), 1, .75f);
        }
        if (kind.equals("ripple")) {
            double t = fract(seconds / 1.2 + index * .5 + offset);
            float scale = (float) (.23 + 1.05 * t);
            return new Pose(40 + dx * 24, 12.08 + index * .015, 40 + dz * 24, scale, 1, scale,
                    (float) (Math.sin(Math.PI * t) * .78 * (1 - t * .4)));
        }
        double t = fract(seconds / .6 + index / 3.0 + offset), angle = (index - 1) * 1.05;
        double r = 24 + Math.cos(angle) * 3 * t, tangent = Math.sin(angle) * 3 * t;
        return new Pose(40 + dx * r - dz * tangent, 12.5 + 5 * Math.sin(Math.PI * t),
                40 + dz * r + dx * tangent, .85f, 1.2f, .85f, (float) (Math.sin(Math.PI * t) * .72));
    }

    private static double fract(double value) {
        double phase = value - Math.floor(value);
        // Keep mathematically identical cycle boundaries stable under floating-point rounding.
        return phase < 1e-10 || phase > 1 - 1e-10 ? 0 : phase;
    }
}
