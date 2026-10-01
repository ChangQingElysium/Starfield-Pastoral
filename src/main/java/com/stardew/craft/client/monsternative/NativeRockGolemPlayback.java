package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
/** A real crouched rock heap unfolding into rigid limbs, with continuous start/stop and hit overlays. */
public final class NativeRockGolemPlayback {
    private final boolean emergesFromGround;private final NativeNpcModel model;private final NativeNpcPose pose,capture;private double previous=Double.NaN,walkTime,weight,transition;private int last=-1;private boolean dying;
    public NativeRockGolemPlayback(NativeNpcModel m){this(m,false);}
    public NativeRockGolemPlayback(NativeNpcModel m,boolean emergesFromGround){this.emergesFromGround=emergesFromGround;model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,boolean moving,int phase,double progress,double hit,double death){
        if(clock==previous)return;double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.1);
        if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();pose.apply("animation.rock_golem.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.1));}
        else{
            if(last!=phase){if(last!=-1){capture.copyFrom(pose);transition=clock;}else transition=Double.NEGATIVE_INFINITY;}
            weight+=com.stardew.craft.port.PortJava.clamp((moving?1:0)-weight,-dt/.13,dt/.13);walkTime+=dt*weight;pose.reset();
            if(phase==0)pose.apply("animation.rock_golem.disguise",0);
            else if(phase==1)pose.apply("animation.rock_golem.emerge",progress*.6);
            else{pose.apply("animation.rock_golem.idle",clock);pose.blend("animation.rock_golem.walk",walkTime,NativeGrubMotion.smooth(weight));}
            if(last!=-1&&clock-transition<.08)pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.08));
            if(hit>=0&&hit<.32)NativeMonsterMotion.addRotationClip(model,pose,"animation.rock_golem.hit",hit);
        }
        float low=Float.POSITIVE_INFINITY;var point=new Vector3f();var matrices=pose.matrices();
        for(var q:model.quads())for(var v:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(point.set(v[0],v[1],v[2])).y);
        if(Float.isFinite(low)&&(!emergesFromGround||phase==2||death>0))pose.addPosition("root",0,.2-low,0);
        previous=clock;last=phase;dying=death>0;
    }
}
