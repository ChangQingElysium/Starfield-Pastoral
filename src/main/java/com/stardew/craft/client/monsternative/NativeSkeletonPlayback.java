package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
/** Smooth rigid-bone clips, with source-timed throw and a visual-only release recovery. */
public final class NativeSkeletonPlayback {
    private final NativeNpcModel model;private final NativeNpcPose pose,capture;private double previous=Double.NaN,walkTime,weight,transition;private int lastAction=-1;private boolean dying;
    public NativeSkeletonPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,boolean moving,int action,double progress,double release,double hit,double death){
        if(clock==previous)return;double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.1);
        if(death>0){if(!dying){capture.copyFrom(pose);}pose.reset();pose.apply("animation.skeleton.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.1));}
        else{
            if(action!=lastAction){capture.copyFrom(pose);transition=clock;}
            weight+=com.stardew.craft.port.PortJava.clamp((moving?1:0)-weight,-dt/.12,dt/.12);walkTime+=dt*weight;
            pose.reset();pose.apply("animation.skeleton.idle",clock);pose.blend("animation.skeleton.walk",walkTime,NativeGrubMotion.smooth(weight));
            if(action==1){pose.blend("animation.skeleton.throw",progress*.6,1);pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.08));}
            else if(release>=0&&release<.22)pose.blend("animation.skeleton.recover",release,1);
            else if(lastAction==1||clock-transition<.08)pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.08));
            if(hit>=0&&hit<.28)NativeMonsterMotion.addRotationClip(model,pose,"animation.skeleton.hit",hit);
        }
        // Rigid soles touch the same plane throughout gait; no hull may intersect it on death.
        float low=Float.POSITIVE_INFINITY;var point=new Vector3f();var matrices=pose.matrices();
        for(var q:model.quads())if(death>0||q.sourcePart().startsWith("foot_"))for(var v:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(point.set(v[0],v[1],v[2])).y);
        if(Float.isFinite(low))pose.addPosition("root",0,.03-low,0);
        previous=clock;lastAction=action;dying=death>0;
    }
}
