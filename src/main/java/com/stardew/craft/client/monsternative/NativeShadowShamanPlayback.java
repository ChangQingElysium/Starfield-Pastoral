package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
/** Held spell pose blends from the current gait; release never delays the server's next movement. */
public final class NativeShadowShamanPlayback {
    private final NativeNpcModel model;private final NativeNpcPose pose,capture;private double previous=Double.NaN,walkTime,weight,transition;private boolean lastCast,dying;
    public NativeShadowShamanPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,boolean moving,boolean casting,double castTime,double release,double hit,double death){
        if(clock==previous)return;double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.1);
        if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();pose.apply("animation.shadow_shaman.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.1));}
        else{
            if(casting!=lastCast){capture.copyFrom(pose);transition=clock;}
            weight+=com.stardew.craft.port.PortJava.clamp((moving?1:0)-weight,-dt/.1,dt/.1);walkTime+=dt*weight;pose.reset();pose.apply("animation.shadow_shaman.idle",clock);pose.blend("animation.shadow_shaman.walk",walkTime,NativeGrubMotion.smooth(weight));
            if(casting)pose.apply("animation.shadow_shaman.cast",castTime);
            else if(release>=0&&release<.2)pose.blend("animation.shadow_shaman.recover",release,1-NativeGrubMotion.smooth(release/.2));
            if(!Double.isNaN(previous)&&clock-transition<.12)pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.12));
            if(hit>=0&&hit<.28)NativeMonsterMotion.addRotationClip(model,pose,"animation.shadow_shaman.hit",hit);
        }
        float low=Float.POSITIVE_INFINITY;var point=new Vector3f();var matrices=pose.matrices();
        for(var q:model.quads())for(var v:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(point.set(v[0],v[1],v[2])).y);
        if(Float.isFinite(low))pose.addPosition("root",0,.18-low,0);
        previous=clock;lastCast=casting;dying=death>0;
    }
}
