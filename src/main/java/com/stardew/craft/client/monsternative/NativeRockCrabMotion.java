package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Vector3f;

/** Authored animation/presentation contract; entity AI and combat are integrated separately. */
public final class NativeRockCrabMotion {
    private NativeRockCrabMotion() {}

    public static boolean visible(String part, String clip, double time) {
        boolean shell = part.startsWith("shell_");
        if (shell) return !clip.startsWith("flee") && !clip.equals("bare_idle") && !clip.equals("death_bare")
                && (!clip.equals("shell_break") || time < .32);
        return !clip.equals("disguise") && (!clip.equals("emerge") || time >= .16)
                && (!clip.equals("retract") || time < .30);
    }

    public static void sample(NativeNpcModel model, NativeNpcPose pose, String clip, double time) {
        pose.reset();
        pose.apply("animation.rock_crab." + clip, time);
        if (!clip.startsWith("death_")) return;
        // Ground the real rotated vertices; don't push the negative hull through the floor.
        var matrices = pose.matrices();
        var v = new Vector3f();
        float low = Float.POSITIVE_INFINITY;
        for (var quad : model.quads()) if (visible(quad.sourcePart(), clip, time)) {
            for (var vertex : quad.vertices()) {
                matrices[quad.bone()].transformPosition(v.set(vertex[0], vertex[1], vertex[2]));
                low = Math.min(low, v.y);
            }
        }
        if (low < .2F) pose.addPosition("root", 0, .2F - low, 0);
    }

    /** Capture the current walking pose at interruption, then ease to rest without snapping phase. */
    public static void stopFrom(NativeNpcPose pose, NativeNpcPose interrupted, double progress) {
        double p = com.stardew.craft.port.PortJava.clamp(progress, 0, 1);
        pose.reset();
        pose.blendFrom(interrupted, 1 - p * p * (3 - 2 * p));
    }
}
