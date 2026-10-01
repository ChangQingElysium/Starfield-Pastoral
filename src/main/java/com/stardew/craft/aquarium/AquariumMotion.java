package com.stardew.craft.aquarium;

/** Model units, +X is the fish's nose. Bounds include the rotated body, never just its centre. */
public final class AquariumMotion {
    public record Pose(double x, double y, double z, float yaw, float roll) {}
    private AquariumMotion() {}

    public static Pose sample(String movement, int slot, long seed, double seconds, double width, double height, double depth) {
        double phase = Math.floorMod(seed + slot * 719L, 6283) / 1000.0;
        double radius = Math.hypot(width, depth) * .5;
        double x, y, z, yaw = 0, roll = 0;
        double floor = 7.35 + height / 2, ceiling = 30.5 - height / 2;
        if (slot >= 3 || movement.equals("frog")) {
            int ground = Math.floorMod(slot - 3, 3);
            double center = -18 + ground * 18;
            double t = seconds * .12 + phase;
            double wander = switch (movement) { case "static" -> 0; case "front_crawl" -> 1.1; case "crawl" -> 2.1; default -> 3.4; };
            x = center + Math.sin(t) * wander;
            z = -6.5 + Math.cos(t * .7) * (movement.equals("static") ? 0 : .7);
            y = floor;
            yaw = Math.cos(t) >= 0 ? 0 : 180;
            // Ease the turn while almost stationary, instead of snapping through 180 degrees.
            if (wander > 0) yaw = 90 - 90 * Math.tanh(Math.cos(t) * 4);
            if (movement.equals("frog")) {
                double hop = (seconds + phase) % 8;
                if (hop < .8) y += 2.3 * Math.sin(hop / .8 * Math.PI);
            }
        } else if (movement.equals("float")) {
            x = -14 + slot * 14 + Math.sin(seconds * .22 + phase) * 2;
            z = -1 + Math.cos(seconds * .18 + phase) * 2;
            y = floor + (ceiling - floor) * .72 + Math.sin(seconds * .7 + phase) * .5;
            yaw = Math.sin(seconds * .14 + phase) * 28;
            roll = Math.sin(seconds * .8 + phase) * 3;
        } else {
            double speed = movement.equals("cephalopod") ? .16 : movement.equals("eel") ? .19 : .21;
            double angle = seconds * speed + phase + .18 * Math.sin(seconds * .43 + phase);
            double rx = Math.max(2, 28.5 - radius), rz = Math.max(1, 12.5 - radius);
            x = Math.cos(angle) * rx; z = Math.sin(angle) * rz;
            yaw = Math.toDegrees(Math.atan2(Math.cos(angle) * rz, -Math.sin(angle) * rx));
            y = floor + (ceiling - floor) * (.4 + slot * .18) + Math.sin(seconds * .5 + phase) * .5;
            roll = Math.sin(seconds * 1.2 + phase) * (movement.equals("eel") ? 3 : 1.5);
        }
        double a = Math.toRadians(yaw), r = Math.toRadians(roll);
        double rotatedWidth = Math.abs(Math.cos(r)) * width + Math.abs(Math.sin(r)) * height;
        double halfX = (Math.abs(Math.cos(a)) * rotatedWidth + Math.abs(Math.sin(a)) * depth) / 2;
        double halfZ = (Math.abs(Math.sin(a)) * rotatedWidth + Math.abs(Math.cos(a)) * depth) / 2;
        double halfY = (Math.abs(Math.sin(r)) * width + Math.abs(Math.cos(r)) * height) / 2;
        return new Pose(com.stardew.craft.port.PortJava.clamp(x, -28.8 + halfX, 28.8 - halfX),
                com.stardew.craft.port.PortJava.clamp(y, 7.2 + halfY, 30.8 - halfY), com.stardew.craft.port.PortJava.clamp(z, -12.8 + halfZ, 12.8 - halfZ),
                (float) yaw, (float) roll);
    }
}
