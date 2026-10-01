package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.vertex;

/** Contact light has a filled bright middle, colored shoulders and transparent outer edges. */
public final class WeaponContactGeometry {
    private static final double[] PROFILE = {-1.55, -1, -0.20, 0, 0.20, 1, 1.55};
    private WeaponContactGeometry() {}

    /** The earlier slender crossed facets, retained as an authored alternative to four separate diamonds. */
    public static void slenderCross(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 right,Vec3 up,Vec3 normal,
                                    float size,float fade,boolean edge,int r,int g,int b) {
        double[][] rays={{.87,.5,1.05,.105},{-.55,.83,.7,.075}};
        for(double[] ray:rays) {
            Vec3 axis=right.scale(ray[0]).add(up.scale(ray[1])).normalize(),side=axis.cross(normal).normalize();
            for(int layer=0;layer<(edge?1:2);layer++) {
                boolean core=layer==1;double width=size*ray[3]*fade*(edge?1.2:core?.23:1);
                int red=core?255:r,green=core?251:g,blue=core?234:b,alpha=Math.round((edge?95:core?250:145)*fade);
                vertex(out,pose,center.subtract(axis.scale(size*ray[2])),red,green,blue,0);
                vertex(out,pose,center.add(side.scale(width)),red,green,blue,alpha);
                vertex(out,pose,center.add(axis.scale(size*ray[2])),red,green,blue,0);
                vertex(out,pose,center.subtract(side.scale(width)),red,green,blue,alpha);
            }
        }
    }

    /** Four separate diamonds leave a small gap at the contact, with no overlapping white center. */
    public static void fourDiamonds(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up,
                                    Vec3 normal, float size, float fade, boolean edge, int r, int g, int b) {
        Vec3[] axes = {right.scale(0.87).add(up.scale(0.5)).normalize(),
                right.scale(-0.55).add(up.scale(0.83)).normalize()};
        for (int pair = 0; pair < axes.length; pair++) for (int sign : new int[]{-1, 1}) {
            Vec3 direction = axes[pair].scale(sign);
            double reach = size * (pair == 0 ? 0.70 : 0.58);
            blade(out, pose, center.add(direction.scale(size * 0.055)), center.add(direction.scale(reach)),
                    normal, size * (pair == 0 ? 0.065 : 0.055), fade * 0.72f, edge, r, g, b);
        }
    }

    public static void blade(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, Vec3 normal,
                             double width, float fade, boolean edge, int red, int green, int blue) {
        Vec3 side = to.subtract(from).cross(normal).normalize();
        Vec3 middle = from.lerp(to, 0.5);
        // Split at the center instead of triangulating a transparent tip-to-tip diagonal.
        section(out, pose, from, middle, side, side, 0, width, fade, edge, red, green, blue);
        section(out, pose, middle, to, side, side, width, 0, fade, edge, red, green, blue);
    }

    public static void ribbon(VertexConsumer out, Matrix4f pose, Vec3[] path, Vec3 normal,
                              double width, float fade, boolean edge, int red, int green, int blue) {
        if (path.length < 2) return;
        Vec3[] sides = new Vec3[path.length];
        double[] widths = new double[path.length];
        for (int i = 0; i < path.length; i++) {
            Vec3 tangent = path[Math.min(i + 1, path.length - 1)].subtract(path[Math.max(i - 1, 0)]);
            sides[i] = tangent.cross(normal).normalize();
            // Taper across the whole arc, so neighboring segments share exactly the same join.
            widths[i] = width * Math.sin(Math.PI * i / (path.length - 1));
        }
        widths[0] = widths[path.length - 1] = 0;
        for (int i = 1; i < path.length; i++)
            section(out, pose, path[i - 1], path[i], sides[i - 1], sides[i], widths[i - 1], widths[i],
                    fade, edge, red, green, blue);
    }

    private static void section(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 sideA, Vec3 sideB,
                                double widthA, double widthB, float fade, boolean edge, int r, int g, int blue) {
        float visibility = com.stardew.craft.port.PortJava.clamp(fade, 0, 1);
        // Hold the broad silhouette as it fades, rather than collapsing it into a needle.
        double contraction = 0.65 + 0.35 * Math.sqrt(visibility);
        int steps = edge ? 2 : PROFILE.length - 1;
        for (int i = 0; i < steps; i++) {
            sample(out, pose, a, sideA, widthA * contraction, i, visibility, edge, r, g, blue);
            sample(out, pose, a, sideA, widthA * contraction, i + 1, visibility, edge, r, g, blue);
            sample(out, pose, b, sideB, widthB * contraction, i + 1, visibility, edge, r, g, blue);
            sample(out, pose, b, sideB, widthB * contraction, i, visibility, edge, r, g, blue);
        }
    }

    private static void sample(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 side, double width,
                               int i, float fade, boolean edge, int r, int g, int b) {
        double offset = edge ? (i - 1) * 1.2 : PROFILE[i];
        boolean core = !edge && i >= 2 && i <= 4;
        float white = core ? (i == 3 ? 1 : 0.70f) * (float) Math.sqrt(fade) : 0;
        int alpha = edge ? (i == 1 ? 100 : 0) : i == 0 || i == 6 ? 0 : core ? (i == 3 ? 245 : 185) : 155;
        alpha = width == 0 ? 0 : Math.round(alpha * (float) Math.pow(fade, core ? 1.15 : 0.7));
        vertex(out, pose, center.add(side.scale(width * offset)),
                Math.round(r + (255 - r) * white), Math.round(g + (250 - g) * white),
                Math.round(b + (229 - b) * white), alpha);
    }
}
