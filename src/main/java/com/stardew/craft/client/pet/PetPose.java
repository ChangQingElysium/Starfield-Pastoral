package com.stardew.craft.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.client.fishing.FishingRigPose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/** Reuses native ZYX channel sampling; pet vertices have no fishing-arm shape corrections. */
@OnlyIn(Dist.CLIENT)
public final class PetPose {
    public final PetNativeAssets.Asset asset;
    public final FishingRigPose pose;
    private final FishingRigPose previous;
    private final Vector3f[][] vertices;
    private final Vector3f scratch = new Vector3f(), normal = new Vector3f(), edge = new Vector3f();
    private String lastClip;
    private long lastSequence = Long.MIN_VALUE;
    private double changedAt;
    private boolean verticesReady;

    public PetPose(PetNativeAssets.Asset asset) {
        this.asset = asset;
        pose = new FishingRigPose(asset.rig());
        previous = new FishingRigPose(asset.rig());
        vertices = new Vector3f[asset.rig().faces().size()][4];
        for (var face : vertices) for (int i = 0; i < 4; i++) face[i] = new Vector3f();
    }

    public void sample(String clipName, double clipTime, long sequence, double now) {
        var clip = asset.clips().get(clipName);
        if (clip == null) throw new IllegalArgumentException("Unknown pet clip " + clipName);
        boolean first = lastClip == null;
        if (!clipName.equals(lastClip) || lastSequence != sequence) {
            previous.copy(pose);
            changedAt = first ? now - .12 : now;
            lastClip = clipName;
            lastSequence = sequence;
        }
        pose.reset();
        pose.apply(clip, clipTime);
        double progress = Math.max(0, Math.min(1, (now - changedAt) / .12));
        if (progress < 1) pose.mix(previous, (float) (progress * progress * (3 - 2 * progress)));
        else pose.matrices();
        verticesReady = false;
        // Authored clips are grounded, but interpolating joint angles does not preserve
        // their support plane. Correct the final transition surface for every species,
        // including addon pets and their hats, without changing the authored full poses.
        if (progress < 1) groundTransition();
    }

    private void groundTransition() {
        vertices();
        float bottom = 0;
        for (var points : vertices) {
            normal.set(points[1]).sub(points[0]).cross(edge.set(points[2]).sub(points[0]));
            if (normal.lengthSquared() < 1e-10f) continue;
            for (var point : points) bottom = Math.min(bottom, point.y);
        }
        if (bottom >= -.001f) return;
        for (int i = 0; i < pose.world.length; i++) {
            pose.world[i].translateLocal(0, -bottom, 0);
            pose.skin[i].translateLocal(0, -bottom, 0);
        }
        for (var points : vertices) for (var point : points) point.y -= bottom;
    }

    /** Also used by the native-preview comparison test. Buffers are allocated once per visible pet. */
    public Vector3f[][] vertices() {
        if (verticesReady) return vertices;
        for (int f = 0; f < vertices.length; f++) {
            var face = asset.rig().faces().get(f);
            for (int c = 0; c < 4; c++) {
                var source = face.vertices().get(c);
                var result = vertices[f][c].zero();
                for (int w = 0; w < source.bones().length; w++) {
                    pose.skin[source.bones()[w]].transformPosition(scratch.set(source.point()));
                    result.fma(source.weights()[w], scratch);
                }
            }
        }
        verticesReady = true;
        return vertices;
    }

    public void render(PoseStack stack, VertexConsumer consumer, int light) {
        vertices();
        for (int f = 0; f < vertices.length; f++) {
            var points = vertices[f];
            normal.set(points[1]).sub(points[0]).cross(edge.set(points[2]).sub(points[0]));
            if (normal.lengthSquared() < 1e-10f) continue;
            normal.normalize();
            var face = asset.rig().faces().get(f);
            for (int c = 0; c < 4; c++) {
                var v = points[c];
                var uv = face.vertices().get(c).uv();
                consumer.addVertex(stack.last().pose(), v.x, v.y, v.z).setColor(255, 255, 255, 255)
                        .setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(stack.last(), normal.x, normal.y, normal.z);
            }
        }
    }
}
