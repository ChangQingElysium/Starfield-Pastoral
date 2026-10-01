package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class BoneClaymoreGeometry {
    private BoneClaymoreGeometry() {}
    static void contact(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up, Vec3 normal,
                        WeaponTargetImpactClient.Style style, float age, float fade, boolean edge, int r, int g, int b) {
        if (fade <= 0) return;
        double size = style.size;
        if (style == WeaponTargetImpactClient.Style.BONE_FRACTURE) {
            Vec3[] crack = {center.add(right.scale(-.38*size)).add(up.scale(.43*size)),
                    center.add(right.scale(-.12*size)).add(up.scale(.08*size)), center.add(right.scale(.10*size)).add(up.scale(.13*size)),
                    center.add(right.scale(.06*size)).add(up.scale(-.13*size)), center.add(right.scale(.39*size)).add(up.scale(-.40*size))};
            WeaponContactGeometry.ribbon(out, pose, crack, normal, .085*size, fade, edge, r,g,b);
            for (int sign : new int[]{-1,1}) {
                Vec3 p = center.add(right.scale(sign*(.16+age*.035)*size)).add(up.scale((sign*.2-age*age*.006)*size));
                Vec3 axis = up.add(right.scale(sign*.55)).normalize().scale(.12*size);
                WeaponContactGeometry.blade(out,pose,p.subtract(axis),p.add(axis),normal,.035*size,fade*.8f,edge,r,g,b);
            }
        } else {
            // One bowed steel cut, broad at contact, narrowing continuously toward both ends.
            Vec3[] arc = new Vec3[13];
            for(int i=0;i<arc.length;i++) {
                double t=i/12.0, x=(t-.5)*1.25, y=x*.48 + Math.sin(t*Math.PI)*.14;
                arc[i]=center.add(right.scale(x*size)).add(up.scale(y*size)).add(normal.scale(Math.sin(t*Math.PI)*.025));
            }
            WeaponContactGeometry.ribbon(out,pose,arc,normal,.11*size,fade,edge,r,g,b);
            for(int i=0;i<2;i++) {
                Vec3 p=center.add(right.scale((.16+age*.04+i*.14)*size)).add(up.scale((-.17-i*.12-age*age*.004)*size));
                Vec3 axis=right.add(up.scale(.48)).normalize().scale((.07+i*.02)*size);
                WeaponContactGeometry.blade(out,pose,p.subtract(axis),p.add(axis),normal,.018*size,fade*.7f,edge,r,g,b);
            }
        }
    }
    /** Quiet, stationary break in a short scored line; it is a status cue, not a repeated hit. */
    static void fractureTrace(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up, Vec3 normal, float fade) {
        if (fade <= 0) return;
        for (int sign : new int[]{-1,1}) {
            Vec3[] path={center.add(up.scale(sign*.18)).add(right.scale(-sign*.065)),
                    center.add(up.scale(sign*.10)), center.add(up.scale(sign*.025)).add(right.scale(sign*.025))};
            WeaponContactGeometry.ribbon(out,pose,path,normal,.018,fade,false,218,207,174);
        }
    }
    static float traceOpacity(float remaining) { return com.stardew.craft.port.PortJava.clamp(remaining / 10, 0, 1) * .34f; }
}
