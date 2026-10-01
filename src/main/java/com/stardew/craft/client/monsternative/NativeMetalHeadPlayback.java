package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
/** Rigid helmet, grounded brass feet and blended start/stop; no soft-body scale. */
public final class NativeMetalHeadPlayback {
    private final NativeNpcModel model;private final NativeNpcPose pose,capture;private double previous=Double.NaN,walkTime,weight;private boolean dying;
    public NativeMetalHeadPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,boolean moving,double hit,double death){
        if(clock==previous)return;double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.1);
        if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();pose.apply("animation.metal_head.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.09));}
        else{
            weight+=com.stardew.craft.port.PortJava.clamp((moving?1:0)-weight,-dt/.1,dt/.1);walkTime+=dt*weight;pose.reset();pose.apply("animation.metal_head.idle",clock);pose.blend("animation.metal_head.walk",walkTime,NativeGrubMotion.smooth(weight));
            if(hit>=0&&hit<.24)NativeMonsterMotion.addRotationClip(model,pose,"animation.metal_head.hit",hit);
        }
        float low=Float.POSITIVE_INFINITY;var point=new Vector3f();var matrices=pose.matrices();
        for(var q:model.quads())for(var v:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(point.set(v[0],v[1],v[2])).y);
        if(Float.isFinite(low))pose.addPosition("root",0,.18-low,0);
        previous=clock;dying=death>0;
    }
}
