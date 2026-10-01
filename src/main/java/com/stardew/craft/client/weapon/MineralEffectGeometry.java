package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Fracture and bone use different depth, line rhythm and pulse direction. No custom textures. */
public final class MineralEffectGeometry {
    private static final Vec3 UP = new Vec3(0,1,0);
    private MineralEffectGeometry() {}
    static float pulseStrength(float age) {
        return age < 0 || age >= 9 ? 0 : (float)Math.pow(1-age/9, 2);
    }
    static void fieldSegment(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 center,
                             boolean bone, int index, float age, float pulseAge, float fade, boolean edge) {
        float pulse = pulseStrength(pulseAge);
        Vec3 side = b.subtract(a).cross(UP).normalize();
        int r = edge ? 30 : bone ? 229 : 160, g = edge ? 22 : bone ? 216 : 87, c = edge ? 38 : bone ? 181 : 239;
        double width = (bone ? 0.013 : 0.018+0.018*pulse)*(edge ? 2.3 : 1);
        strip(out, pose, a, b, side.scale(width), r,g,c, Math.round((bone ? 95 : 120)*fade));
        if (!edge) strip(out, pose, a.add(0,0.002,0), b.add(0,0.002,0), side.scale(0.004),
                bone ? 253 : 223, bone ? 246 : 194, bone ? 224 : 255, Math.round((125+100*pulse)*fade));
        if (bone && index < 36 && index%3 == 0) {
            // Sparse slanted ribs retain an open, readable center and never enclose the target in a cage.
            Vec3 inward = new Vec3(center.x-a.x, 0, center.z-a.z).normalize();
            double rise = Math.min(1, age/5)*0.32;
            Vec3 tip = a.add(inward.scale(0.18+0.13*pulse)).add(0,rise,0);
            shard(out, pose, a, tip, side.scale(edge ? 0.036 : 0.022), inward.scale(0.016), r,g,c, Math.round(145*fade));
            if (pulse > 0) {
                // A short inward stroke only on an actual authoritative damage pulse.
                Vec3 from = a.add(inward.scale(0.12 + (1-pulse)*0.5)).add(0,0.05,0);
                strip(out, pose, from, from.add(inward.scale(0.36*pulse)), side.scale(0.013), r,g,c, Math.round(210*pulse*fade));
            }
        } else if (!bone && index < 36 && index%3 == 0 && pulse > 0) {
            // Small rising shards, not a solid wall along the six-block seam.
            Vec3 base = a.add(side.scale((index%2 == 0 ? 1 : -1)*(1-pulse)*0.16));
            double h = (0.23+(index%5)*0.055)*Math.min(1,pulseAge/1.5f);
            shard(out, pose, base, base.add(0,h,0), side.scale(edge ? 0.06 : 0.04), b.subtract(a).normalize().scale(0.022),
                    r,g,c, Math.round(180*pulse*fade));
        }
    }
    /** Three etched seams fill towards the tip as the original seven-second passive charge completes. */
    public static void chargedBlade(VertexConsumer out, Matrix4f pose, Vec3 base, Vec3 tip, float charge) {
        Vec3 axis = tip.subtract(base).normalize(), side = new Vec3(-axis.y,axis.x,0).normalize();
        for (int i = 0; i < 3; i++) {
            float fill = com.stardew.craft.port.PortJava.clamp(charge*3-i,0,1);
            if (fill <= 0) continue;
            for (int face : new int[]{-1,1}) {
                Vec3 start = base.lerp(tip,0.38+i*0.19).add(0,0,face*0.034);
                Vec3 elbow = start.add(axis.scale(0.025)).add(side.scale(0.015));
                Vec3 end = start.add(axis.scale(0.068*fill));
                strip(out,pose,start,elbow,side.scale(0.009),120,59,193,Math.round(95*fill));
                strip(out,pose,elbow,end,side.scale(0.004),215,177,253,Math.round((charge>=1?225:135)*fill));
            }
        }
    }

    public static void boneMark(VertexConsumer out, Matrix4f pose, float fade) {
        for (int i = -1; i <= 1; i++) {
            Vec3 base = new Vec3(i*0.11+0.02,-0.19+Math.abs(i)*0.04,0);
            Vec3 tip = new Vec3(i*0.11-0.02,0.22-Math.abs(i)*0.045,0);
            shard(out, pose, base, tip, new Vec3(0.013,0,0), new Vec3(0,0,0.009), 233,222,195, Math.round(160*fade));
        }
    }
    private static void shard(VertexConsumer out, Matrix4f pose, Vec3 base, Vec3 tip, Vec3 side, Vec3 depth,
                              int r,int g,int b,int alpha) {
        Vec3 shoulder = base.lerp(tip,0.3);
        Vec3[] rim = {shoulder.add(side), shoulder.add(depth), shoulder.subtract(side), shoulder.subtract(depth)};
        for (int i = 0; i < 4; i++) for (Vec3 cap : new Vec3[]{base, tip}) {
            vertex(out,pose,rim[i],r,g,b,alpha); vertex(out,pose,rim[(i+1)%4],r,g,b,alpha);
            vertex(out,pose,cap,r,g,b,alpha/2); vertex(out,pose,cap,r,g,b,alpha/2);
        }
    }
}
