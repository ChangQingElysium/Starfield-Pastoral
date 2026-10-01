package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import com.stardew.craft.port.PortVertex;
/** Shared additive shapes for the three melee samples. */
public final class WeaponGlowGeometry {
    private WeaponGlowGeometry() {}

    public static void strip(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, Vec3 width,
                             int r, int g, int b, int alpha) {
        vertex(out, pose, from.subtract(width), r, g, b, alpha);
        vertex(out, pose, from.add(width), r, g, b, alpha);
        vertex(out, pose, to.add(width), r, g, b, alpha);
        vertex(out, pose, to.subtract(width), r, g, b, alpha);
    }

    public static void ring(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up,
                             double radius, double width, int red, int green, int blue, int alpha) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24, b = (i + 1) * Math.PI * 2 / 24;
            Vec3 p = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 q = right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            vertex(out, pose, center.add(p.scale(radius - width)), red, green, blue, alpha);
            vertex(out, pose, center.add(p.scale(radius + width)), red, green, blue, 0);
            vertex(out, pose, center.add(q.scale(radius + width)), red, green, blue, 0);
            vertex(out, pose, center.add(q.scale(radius - width)), red, green, blue, alpha);
        }
    }

    public static void vertex(VertexConsumer out, Matrix4f pose, Vec3 p, int r, int g, int b, int alpha) {
        PortVertex.of(out).addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(alpha, 0, 255)).endVertex();
    }

}
