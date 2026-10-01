package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared visual-only transforms. Bounds include inverted shells and the final death rotation. */
public final class NativeMonsterPresentation {
    public static final float GROUND_CLEARANCE = 1F / 32;
    private NativeMonsterPresentation() {}
    /** Display only: keep the source color untouched for breeding and color-dependent loot. */
    public static int slimeTint(int source, boolean outline) {
        int r = source >> 16 & 255, g = source >> 8 & 255, b = source & 255;
        if (outline) {
            // A bright, chromatic rim: adding more white alone desaturates it into the body.
            float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            float range = max - min;
            float hue = range == 0 ? 0 : max == r ? (g - b) / range : max == g ? (b - r) / range + 2 : (r - g) / range + 4;
            hue = (hue / 6 + 1 - 24F / 360) % 1;
            float saturation = max == 0 ? 0 : Math.min(.88F, range / max * 1.05F);
            float value = max / 255 + (1 - max / 255) * .85F;
            return net.minecraft.util.Mth.hsvToRgb(hue, saturation, value);
        }
        float pale = .18F;
        return Math.round(r + (255 - r) * pale) << 16 | Math.round(g + (255 - g) * pale) << 8
                | Math.round(b + (255 - b) * pale);
    }
    /** Blue-violet gel needs a perceptually brighter rim: HSV value alone leaves blue dark. */
    public static int bigSlimeOutlineTint(int source){
        int rim=slimeTint(source,true),body=slimeTint(source,false);
        double rimLuma=luma(rim),target=Math.min(.85,luma(body)+.23);
        double pale=com.stardew.craft.port.PortJava.clamp((target-rimLuma)/(1-rimLuma),0,1);
        return (int)Math.round((rim>>16&255)+(255-(rim>>16&255))*pale)<<16
                |(int)Math.round((rim>>8&255)+(255-(rim>>8&255))*pale)<<8
                |(int)Math.round((rim&255)+(255-(rim&255))*pale);
    }
    private static double luma(int c){return ((c>>16&255)*.2126+(c>>8&255)*.7152+(c&255)*.0722)/255;}
    public static double slimeLift(boolean dashing, boolean dying, double time, float growth) {
        // Ordinary movement squashes on the ground; source yOffset is for trajectory-driven jumps.
        return dashing && !dying ? growth * .48 * Math.sin(Math.PI * com.stardew.craft.port.PortJava.clamp(time / .65, 0, 1)) : 0;
    }
    public static Matrix4f deathTransform(float deathTicks, float pivotY) {
        // LivingEntityRenderer's familiar 90-degree death roll, around the body's center.
        float progress = deathTicks <= 0 ? 0 : Math.min(1, (float) Math.sqrt(Math.max(0, (deathTicks - 1) / 20 * 1.6F)));
        return new Matrix4f().translate(0, pivotY, 0).rotateZ(progress * (float) Math.PI / 2).translate(0, -pivotY, 0);
    }
    public static float groundLift(NativeNpcModel model, Matrix4f[] bones, Matrix4f body, float scale) {
        return groundLift(model, bones, body, scale, 2);
    }
    public static boolean slimePartVisible(String part, int antenna) {
        if (part.startsWith("antenna_")) return antenna != 0;
        if (part.startsWith("male_")) return antenna == 1;
        if (part.startsWith("special_")) return antenna == 2;
        return true;
    }
    public static float groundLift(NativeNpcModel model, Matrix4f[] bones, Matrix4f body, float scale, int antenna) {
        float lowest = Float.POSITIVE_INFINITY;
        var vertex = new Vector3f();
        for (var quad : model.quads()) {
            if (!slimePartVisible(quad.sourcePart(), antenna)) continue;
            for (var v : quad.vertices()) {
                bones[quad.bone()].transformPosition(vertex.set(v[0], v[1], v[2]));
                body.transformPosition(vertex);
                lowest = Math.min(lowest, vertex.y * scale);
            }
        }
        return GROUND_CLEARANCE + Math.max(0, -lowest);
    }
}
