package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Narrow metallic highlights with tapered bodies; reward motes close toward the real grip. */
public final class NeedleBurglarGeometry {
    private NeedleBurglarGeometry() {}
    static void contact(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
                        WeaponTargetImpactClient.Style style, float age, float fade, boolean edge, int r, int g, int b) {
        if (fade <= 0) return;
        double size = style.size;
        if (style == WeaponTargetImpactClient.Style.BURGLAR_STRIKE) {
            Vec3[] arc = new Vec3[17];
            for (int i = 0; i < arc.length; i++) {
                double t = i / 16.0;
                arc[i] = center.add(right.scale((t * 1.1 - .55) * size))
                        .add(up.scale((.18 * Math.sin(t * Math.PI) - .24 * t) * size));
            }
            WeaponContactGeometry.ribbon(out, pose, arc, normal, .085 * size, fade, edge, r, g, b);
            Vec3 p = center.add(right.scale(.15 * size)).add(up.scale(-.15 * size));
            WeaponContactGeometry.blade(out, pose, p.subtract(up.scale(.10 * size)), p.add(up.scale(.10 * size)),
                    normal, .024 * size, fade * .7f, edge, edge ? r : 236, edge ? g : 192, edge ? b : 116);
            return;
        }
        Vec3 axis = right.add(up.scale(.38)).normalize();
        WeaponContactGeometry.blade(out, pose, center.subtract(axis.scale(.68 * size)), center.add(axis.scale(.68 * size)),
                normal, .055 * size, fade, edge, r, g, b);
        // Two detached small petals keep the silhouette legible without filling the center white.
        for (int sign : new int[]{-1, 1}) {
            Vec3 p = center.add(up.scale(sign * (.16 + Math.min(age, 4) * .018) * size))
                    .add(right.scale(-sign * .14 * size)).add(normal.scale(.025));
            Vec3 petal = up.add(right.scale(-.3)).normalize().scale(.12 * size);
            WeaponContactGeometry.blade(out, pose, p.subtract(petal), p.add(petal), normal, .032 * size, fade * .75f, edge, r, g, b);
        }
    }
    public static void needleBlade(VertexConsumer out, Matrix4f pose, Vec3 base, Vec3 tip, float frenzy, float hitAge) {
        if (frenzy <= 0 && (hitAge < 0 || hitAge >= 3)) return;
        Vec3 along = tip.subtract(base).normalize(), side = new Vec3(-along.y, along.x, 0).normalize();
        float flash = hitAge >= 0 && hitAge < 3 ? (1 - hitAge / 3) * .8f : 0;
        for (int face : new int[]{-1, 1}) {
            Vec3 offset = new Vec3(0, 0, face * .037);
            if (frenzy > 0) for (int sign : new int[]{-1, 1}) {
                Vec3 start = base.lerp(tip, .35).add(side.scale(sign * .013)).add(offset);
                Vec3 end = base.lerp(tip, .96).add(side.scale(sign * .013)).add(offset);
                WeaponContactGeometry.blade(out, pose, start, end, new Vec3(0, 0, 1), .008, frenzy * .65f, false, 183, 142, 246);
            }
            if (flash > 0) WeaponContactGeometry.blade(out, pose, tip.subtract(along.scale(.065)).add(offset),
                    tip.add(along.scale(.025)).add(offset), new Vec3(0, 0, 1), .015, flash, false, 227, 218, 255);
        }
    }
    public static void loot(VertexConsumer out, Matrix4f pose, Vec3 base, Vec3 tip, float age) {
        if (age < 0 || age >= 10) return;
        Vec3 along = tip.subtract(base).normalize(), side = new Vec3(-along.y, along.x, 0).normalize();
        for (int i = 0; i < 4; i++) {
            double t = com.stardew.craft.port.PortJava.clamp((age - i * .7) / 7, 0, 1), sign = i % 2 == 0 ? -1 : 1;
            if (age < i * .7 || t >= 1) continue;
            Vec3 p = base.lerp(tip, .75 * (1 - t)).add(side.scale(sign * (.07 + .09 * Math.sin(t * Math.PI)) * (1 - t)));
            for (int face : new int[]{-1, 1}) {
                Vec3 offset = new Vec3(0, 0, face * .04);
                WeaponContactGeometry.blade(out, pose, p.subtract(along.scale(.022)).add(offset), p.add(along.scale(.022)).add(offset),
                        new Vec3(0, 0, 1), .009, (float)(1 - t) * .85f, false, 243, 199, 107);
            }
        }
    }
}
