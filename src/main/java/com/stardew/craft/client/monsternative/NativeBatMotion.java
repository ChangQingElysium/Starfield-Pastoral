package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.entity.monster.BatFlight;
import com.stardew.craft.entity.monster.MineBatEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Absolute server action time: late tracking and reloads never replay the wake animation. */
public final class NativeBatMotion {
    private NativeBatMotion() {}
    public static void sample(NativeNpcPose pose, int action, double time) {
        pose.reset();
        if (action == MineBatEntity.ROOST) pose.apply("animation.bat.roost", 0);
        else if (action == MineBatEntity.AWAKE && time < BatFlight.WAKE_SECONDS) pose.apply("animation.bat.wake", time);
        else pose.apply("animation.bat.fly", time - (action == MineBatEntity.AWAKE ? BatFlight.WAKE_SECONDS : 0));
    }
    public static float lift(NativeNpcModel model, Matrix4f[] matrices, int action, double time) {
        double progress = action == MineBatEntity.ROOST ? 0
                : action == MineBatEntity.AWAKE ? com.stardew.craft.port.PortJava.clamp(time / BatFlight.WAKE_SECONDS, 0, 1) : 1;
        double smooth = progress * progress * (3 - 2 * progress);
        float desired = (float) ((MineBatEntity.HEIGHT - .75 - .02) * (1 - smooth));
        float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
        var vertex = new Vector3f();
        for (var quad : model.quads()) {
            if (quad.sourcePart().contains("membrane")) continue;
            for (var v : quad.vertices()) {
                matrices[quad.bone()].transformPosition(vertex.set(v[0], v[1], v[2]));
                low = Math.min(low, vertex.y / 16); high = Math.max(high, vertex.y / 16);
            }
        }
        // Keep the approved head hull and solid body inside the stable physics box while rolling.
        return Math.max(.02F - low, Math.min(desired, MineBatEntity.HEIGHT - .02F - high));
    }
}
