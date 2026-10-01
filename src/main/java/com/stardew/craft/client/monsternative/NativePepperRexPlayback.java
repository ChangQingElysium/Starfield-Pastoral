package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.monster.PepperRexBehavior;
import org.joml.Vector3f;
/** Heavy grounded gait, visible jaw anticipation and source-timed head sweep. */
public final class NativePepperRexPlayback {
 private final NativeNpcModel model;private final NativeNpcPose pose,capture;private double previous=Double.NaN,walkTime,weight,transition;private int oldPhase;private boolean dying;
 public NativePepperRexPlayback(NativeNpcModel m){model=m;pose=new NativeNpcPose(m);capture=new NativeNpcPose(m);}public NativeNpcPose pose(){return pose;}
 public void sample(double clock,boolean moving,int phase,double action,double hit,double death){
  if(clock==previous)return;double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.1);
  if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();pose.apply("animation.pepper_rex.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.08));}
  else{
   if(phase!=oldPhase){capture.copyFrom(pose);transition=clock;}
   weight+=com.stardew.craft.port.PortJava.clamp((moving?1:0)-weight,-dt/.12,dt/.12);walkTime+=dt*weight;pose.reset();
   if(phase==PepperRexBehavior.PREPARE)pose.apply("animation.pepper_rex.prepare",action);
   else if(phase==PepperRexBehavior.FIRE)pose.apply("animation.pepper_rex.fire",action);
   else{pose.apply("animation.pepper_rex.idle",clock);pose.blend("animation.pepper_rex.walk",walkTime,NativeGrubMotion.smooth(weight));}
   if(clock-transition<.12)pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.12));
   if(hit>=0&&hit<.25)NativeMonsterMotion.addRotationClip(model,pose,"animation.pepper_rex.hit",hit);
  }
  float low=Float.POSITIVE_INFINITY;var point=new Vector3f();var mats=pose.matrices();for(var q:model.quads())for(var v:q.vertices())low=Math.min(low,mats[q.bone()].transformPosition(point.set(v[0],v[1],v[2])).y);if(Float.isFinite(low))pose.addPosition("root",0,.12-low,0);
  previous=clock;oldPhase=phase;dying=death>0;
 }
}
