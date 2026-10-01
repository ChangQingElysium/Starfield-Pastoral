package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import org.joml.Vector3f;

/** Bug presentation only. Patrol displacement and damage knockback belong to the entity. */
public final class NativeBugMotion {
    private NativeBugMotion() {}

    public static void flight(NativeNpcPose pose, double wingClock, double swayClock) {
        pose.reset();
        pose.apply("animation.bug.fly", wingClock);
        pose.apply("animation.bug.sway", swayClock);
    }

    /** Apply after flight; disjoint channels keep the membrane phase continuous. */
    public static void hit(NativeNpcPose pose, double elapsed) {
        pose.apply("animation.bug.hit", elapsed);
    }

    /** Capture the last living pose once, then blend into a rigid death roll. */
    public static void death(NativeNpcModel model, NativeNpcPose result,
                             NativeNpcPose captured, double elapsed) {
        result.reset();
        result.apply("animation.bug.death", elapsed);
        // Preserve the captured lateral sway; death must not snap back to the patrol axis.
        double u = com.stardew.craft.port.PortJava.clamp(elapsed / .12, 0, 1);
        double blend = u * u * (3 - 2 * u);
        result.blendFrom(captured, 1 - blend);
        var sourceRoot = captured.matrices()[0];
        result.addPosition("root", sourceRoot.m30() * blend, 0, sourceRoot.m32() * blend);
        float min = Float.POSITIVE_INFINITY;
        var matrices = result.matrices();
        var point = new Vector3f();
        for (var quad : model.quads()) for (var vertex : quad.vertices()) {
            min = Math.min(min, matrices[quad.bone()].transformPosition(
                    point.set(vertex[0], vertex[1], vertex[2])).y);
        }
        if (min < .25F) result.addPosition("root", 0, .25F - min, 0);
    }
}
