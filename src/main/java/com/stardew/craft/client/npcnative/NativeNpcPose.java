package com.stardew.craft.client.npcnative;

import org.joml.Matrix4f;

/** Reusable per-render scratch pose. Independent clips own disjoint bone channels. */
public final class NativeNpcPose {
    private final NativeNpcModel model;
    private final float[][] positions;
    private final float[][] rotations;
    private final float[][] scales;
    private final Matrix4f[] matrices;
    private final java.util.Map<String,Integer> boneIndices = new java.util.HashMap<>();
    private final float[] sampled = new float[3];
    private final NativeNpcCloth cloth;
    private final NativeNpcSkin skin;
    private final boolean[] legBones;
    private final java.util.Map<Integer,Matrix4f> worldOverrides = new java.util.HashMap<>();
    private final java.util.Map<Integer,Float> opacity = new java.util.HashMap<>();

    public NativeNpcPose(NativeNpcModel model) {
        this.model = model;
        int size = model.bones().size();
        positions = new float[size][3];
        rotations = new float[size][3];
        scales = new float[size][3];
        matrices = new Matrix4f[size];
        legBones = new boolean[size];
        for (int i = 0; i < size; i++) {
            matrices[i] = new Matrix4f();
            boneIndices.put(model.bones().get(i).name(),i);
            var bone = model.bones().get(i);
            legBones[i] = bone.name().equals("leg_right") || bone.name().equals("leg_left")
                    || bone.parent() >= 0 && legBones[bone.parent()];
        }
        reset();
        cloth=model.profile()==null || model.profile().cloth()==null?null:new NativeNpcCloth(model);
        skin=new NativeNpcSkin(model);
    }

    public void reset() {
        worldOverrides.clear(); opacity.clear();
        for (int i = 0; i < matrices.length; i++) {
            for (int axis = 0; axis < 3; axis++) {
                positions[i][axis] = rotations[i][axis] = 0;
                scales[i][axis] = 1;
            }
        }
    }

    /** Snapshot/transition complete local channels before rebuilding bone matrices. */
    public void copyFrom(NativeNpcPose other) {
        if (model != other.model) throw new IllegalArgumentException("Different pose models");
        for (int i = 0; i < matrices.length; i++) for (int axis = 0; axis < 3; axis++) {
            positions[i][axis] = other.positions[i][axis];
            rotations[i][axis] = other.rotations[i][axis];
            scales[i][axis] = other.scales[i][axis];
        }
    }
    public void blendFrom(NativeNpcPose other, double weight) {
        if (model != other.model) throw new IllegalArgumentException("Different pose models");
        float w = (float) Math.clamp(weight, 0, 1);
        for (int i = 0; i < matrices.length; i++) for (int axis = 0; axis < 3; axis++) {
            positions[i][axis] += (other.positions[i][axis] - positions[i][axis]) * w;
            rotations[i][axis] += (other.rotations[i][axis] - rotations[i][axis]) * w;
            scales[i][axis] += (other.scales[i][axis] - scales[i][axis]) * w;
        }
    }

    public void apply(String name, double time) {
        blend(name,time,1);
    }

    /** Blend only channels owned by the incoming clip; eyes and independent breathing survive. */
    public void blend(String name, double time, double weight) {
        if (weight<=0) return;
        weight=Math.min(1,weight);
        NativeNpcModel.Clip clip = model.clips().get(name);
        if (clip == null) throw new IllegalArgumentException("Missing clip: " + name);
        // Positive modulo also handles a negative preview offset without clamping a loop to its end.
        double t = clip.loop() ? time - Math.floor(time / clip.length()) * clip.length()
                : Math.max(0, Math.min(time, clip.length()));
        for (NativeNpcModel.Track track : clip.tracks()) {
            float[] target = switch (track.channel()) {
                case "position" -> positions[track.bone()];
                case "rotation" -> rotations[track.bone()];
                case "scale" -> scales[track.bone()];
                default -> throw new IllegalArgumentException("Unsupported channel: " + track.channel());
            };
            sample(track, t, sampled);
            for(int axis=0;axis<3;axis++)target[axis]+=(sampled[axis]-target[axis])*weight;
        }
    }

    /** Sample an explicitly managed clock without applying the authored loop flag again. */
    public void applyAt(String name, double time) {
        var clip = model.clips().get(name);
        if (clip == null) throw new IllegalArgumentException("Missing clip: " + name);
        for (var track : clip.tracks()) {
            float[] target = switch (track.channel()) {
                case "position" -> positions[track.bone()];
                case "rotation" -> rotations[track.bone()];
                case "scale" -> scales[track.bone()];
                default -> throw new IllegalArgumentException("Unsupported channel: " + track.channel());
            };
            sample(track, Math.max(0, Math.min(time, clip.length())), target);
        }
    }

    /** Keep the actual inflated soles out of the ground during idle/walk cross-fades. */
    public void groundFeet(float groundOffset) {
        var transforms=matrices();
        float lowest=0;
        for(var quad:model.quads()) if(isContactBone(quad.bone())) {
            var matrix=transforms[quad.bone()];
            for(var v:quad.vertices()) {
                float y=matrix.m01()*v[0]+matrix.m11()*v[1]+matrix.m21()*v[2]+matrix.m31()+groundOffset;
                lowest=Math.min(lowest,y);
            }
        }
        if(lowest<0)addPosition("root",0,-lowest,0);
    }

    /** Includes articulated shins, feet and their visible surfaces. */
    public boolean isLegBone(int index) { return index >= 0 && legBones[index]; }

    public boolean isContactBone(int index) {
        if(!isLegBone(index))return false;
        for(int i=index;i>=0;i=model.bones().get(i).parent())
            if(model.bones().get(i).name().contains("_detail_"))return false;
        return true;
    }

    public void addRotation(String bone, double x, double y, double z) {
        add(rotations,bone,x,y,z);
    }

    public void addPosition(String bone, double x, double y, double z) {
        add(positions,bone,x,y,z);
    }

    public void setScale(String bone,double x,double y,double z) {
        var v=scales[boneIndices.get(bone)];v[0]=(float)x;v[1]=(float)y;v[2]=(float)z;
    }

    public boolean hasBone(String name) { return boneIndices.containsKey(name); }
    /** Current hierarchy transform for contact-constrained secondary joints. */
    public Matrix4f boneMatrix(String name) { return matrices()[boneIndices.get(name)]; }
    public void blendRotation(String name,double x,double y,double z,double weight) {
        var v=rotations[boneIndices.get(name)];
        v[0]+=(float)((x-v[0])*weight);v[1]+=(float)((y-v[1])*weight);v[2]+=(float)((z-v[2])*weight);
    }
    public float[][][] clothVertices() { return cloth==null?null:cloth.update(matrices()); }
    /** Final production surface, shared by rendering and animation preview/checks. */
    public float[][][] surfaceVertices(Matrix4f[] transforms) {
        return skin.update(transforms, cloth==null?null:cloth.update(transforms));
    }
    public NativeNpcModel.LookLimits lookLimits() { return model.profile().lookLimits(); }

    private void add(float[][] channel, String bone, double x, double y, double z) {
        Integer index=boneIndices.get(bone);
        if (index==null) throw new IllegalArgumentException("Missing overlay bone: "+bone);
        channel[index][0]+=(float)x;
        channel[index][1]+=(float)y;
        channel[index][2]+=(float)z;
    }

    public static void sample(NativeNpcModel.Track track, double time, float[] target) {
        var keys = track.keys();
        var first = keys.getFirst();
        if (time < first.time()) {
            System.arraycopy(first.before(), 0, target, 0, 3);
            return;
        }
        var previous = first;
        for (int i = 1; i < keys.size(); i++) {
            var next = keys.get(i);
            if (time < next.time()) {
                double alpha = previous.step() ? 0 : (time - previous.time()) / (next.time() - previous.time());
                for (int axis = 0; axis < 3; axis++) {
                    target[axis] = (float) (previous.after()[axis]
                            + (next.before()[axis] - previous.after()[axis]) * alpha);
                }
                return;
            }
            previous = next;
        }
        System.arraycopy(previous.after(), 0, target, 0, 3);
    }

    public void overrideWorldTransform(String bone, Matrix4f matrix) {
        worldOverrides.put(boneIndices.get(bone),new Matrix4f(matrix));
    }
    public void setOpacity(String bone, float alpha) { opacity.put(boneIndices.get(bone),alpha); }
    public float opacity(int bone) {
        float alpha = 1;
        for (int i=bone;i>=0;i=model.bones().get(i).parent()) alpha *= opacity.getOrDefault(i,1F);
        return alpha;
    }

    public Matrix4f[] matrices() {
        for (int i = 0; i < matrices.length; i++) {
            var bone = model.bones().get(i);
            var m = matrices[i];
            if (worldOverrides.containsKey(i)) { m.set(worldOverrides.get(i)); continue; }
            if (bone.parent() < 0) m.identity();
            else m.set(matrices[bone.parent()]);
            float[] o = bone.origin(), r = bone.rotation(), p = positions[i], a = rotations[i], s = scales[i];
            float rad = (float) (Math.PI / 180);
            // BB Generic uses ZYX Euler order and adds animation angles to the rest rotation.
            m.translate(o[0] + p[0], o[1] + p[1], o[2] + p[2])
                    .rotateZYX((r[2] + a[2]) * rad, (r[1] + a[1]) * rad, (r[0] + a[0]) * rad)
                    .scale(s[0], s[1], s[2]).translate(-o[0], -o[1], -o[2]);
        }
        return matrices;
    }
}
