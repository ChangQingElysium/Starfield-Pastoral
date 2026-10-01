package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.vertex;

/** Six simultaneous colors across the ribbon, continuous shared joins and a tapered oldest end. */
public final class RainbowTrailGeometry {
    public record Sample(Vec3 position, float age) {}
    private static final int[][] COLORS = {{255, 99, 134}, {255, 178, 94}, {246, 235, 115},
            {111, 226, 153}, {91, 199, 245}, {181, 131, 247}};
    private RainbowTrailGeometry() {}
    public static int[] color(int band) { return COLORS[Math.floorMod(band, COLORS.length)].clone(); }
    public static float fade(float age, float lifetime) {
        return age < 0 || age >= lifetime ? 0 : (float) Math.pow(1 - age / lifetime, 1.5);
    }
    public static void twinkles(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up,
                                float size, float age, float fade, boolean edge) {
        for (int i = 0; i < 3; i++) {
            double angle = i * 2.1 + 0.3, radius = size * (0.12 + age * 0.035);
            Vec3 p = center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius + age * 0.018));
            int[] color = COLORS[i * 2];
            double length = size * (i == 0 ? 0.22 : 0.14);
            for (int axis = 0; axis < 2; axis++) {
                Vec3 along = axis == 0 ? up : right;
                WeaponContactGeometry.blade(out, pose, p.subtract(along.scale(length)), p.add(along.scale(length)),
                        right.cross(up), length * 0.26, fade, edge,
                        edge ? 38 : color[0], edge ? 29 : color[1], edge ? 55 : color[2]);
            }
        }
    }
    public static void render(VertexConsumer out, Matrix4f pose, List<Sample> points, Vec3 camera, float lifetime) {
        if (points.size() < 2) return;
        points = smooth(points);
        Vec3[] sides = new Vec3[points.size()];
        float[] widths = new float[points.size()], fades = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i).position;
            Vec3 prev = points.get(Math.max(0, i - 1)).position, next = points.get(Math.min(points.size() - 1, i + 1)).position;
            Vec3 tangent = next.subtract(prev);
            Vec3 side = tangent.cross(camera.subtract(p));
            if (side.lengthSqr() < 1e-8) side = tangent.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-8) side = new Vec3(1, 0, 0);
            side = side.normalize();
            if (i > 0 && side.dot(sides[i - 1]) < 0) side = side.scale(-1);
            sides[i] = side;
            fades[i] = fade(points.get(i).age, lifetime);
            float taper = Math.min(1, i / 2f);
            widths[i] = 0.16f * fades[i] * taper;
            fades[i] *= taper;
        }
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1).position, b = points.get(i).position;
            if (a.distanceToSqr(b) > 16 || a.distanceToSqr(b) < 1e-8) continue;
            for (int lane = 0; lane < COLORS.length; lane++) {
                double lo = -1 + lane / 3.0, hi = -1 + (lane + 1) / 3.0;
                int[] c = COLORS[lane];
                vertex(out, pose, a.add(sides[i - 1].scale(widths[i - 1] * lo)), c[0], c[1], c[2], Math.round(185 * fades[i - 1] * (lane == 0 ? 0.45f : 1)));
                vertex(out, pose, a.add(sides[i - 1].scale(widths[i - 1] * hi)), c[0], c[1], c[2], Math.round(185 * fades[i - 1] * (lane == 5 ? 0.45f : 1)));
                vertex(out, pose, b.add(sides[i].scale(widths[i] * hi)), c[0], c[1], c[2], Math.round(185 * fades[i] * (lane == 5 ? 0.45f : 1)));
                vertex(out, pose, b.add(sides[i].scale(widths[i] * lo)), c[0], c[1], c[2], Math.round(185 * fades[i] * (lane == 0 ? 0.45f : 1)));
            }
        }
    }
    private static List<Sample> smooth(List<Sample> points) {
        var result = new java.util.ArrayList<Sample>();
        result.add(com.stardew.craft.port.PortJava.getFirst(points));
        for (int i = 0; i < points.size() - 1; i++) {
            Sample a = points.get(i), b = points.get(i + 1);
            Vec3 before = points.get(Math.max(0, i - 1)).position, after = points.get(Math.min(points.size() - 1, i + 2)).position;
            Vec3 travel = b.position.subtract(a.position);
            if (before.distanceToSqr(a.position) > 16) before = a.position;
            if (after.distanceToSqr(b.position) > 16) after = b.position;
            if (travel.lengthSqr() > 16) { result.add(b); continue; }
            // Preserve sharp bounces and discontinuities exactly; smooth only ordinary flight.
            boolean curve = travel.dot(a.position.subtract(before)) >= 0
                    && travel.dot(after.subtract(b.position)) >= 0;
            for (int step = 1; step <= 3; step++) {
                double t = step / 3.0, t2 = t * t, t3 = t2 * t;
                Vec3 p = a.position.lerp(b.position, t);
                if (curve) {
                    Vec3 m0 = b.position.subtract(before).scale(0.5), m1 = after.subtract(a.position).scale(0.5);
                    p = a.position.scale(2 * t3 - 3 * t2 + 1).add(m0.scale(t3 - 2 * t2 + t))
                            .add(b.position.scale(-2 * t3 + 3 * t2)).add(m1.scale(t3 - t2));
                    p = new Vec3(com.stardew.craft.port.PortJava.clamp(p.x, Math.min(a.position.x, b.position.x), Math.max(a.position.x, b.position.x)),
                            com.stardew.craft.port.PortJava.clamp(p.y, Math.min(a.position.y, b.position.y), Math.max(a.position.y, b.position.y)),
                            com.stardew.craft.port.PortJava.clamp(p.z, Math.min(a.position.z, b.position.z), Math.max(a.position.z, b.position.z)));
                }
                result.add(new Sample(p, (float) (a.age + (b.age - a.age) * t)));
            }
        }
        return result;
    }
}
