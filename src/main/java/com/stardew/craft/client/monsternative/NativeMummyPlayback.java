package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.monster.MummyLifecycle;
import org.joml.Vector3f;
/** Collapse and resurrection follow source callbacks; gait and hit layering never delay gameplay. */
public final class NativeMummyPlayback {
 private final NativeNpcModel model,remainsModel;private final NativeNpcPose pose,capture,remainsPose;
 private boolean showBody=true,showRemains;
 private double previous=Double.NaN,walkTime,weight,transition;private int lastAction=-1;private boolean dying;
 public NativeMummyPlayback(NativeNpcModel model){this(model,null);}
 public NativeMummyPlayback(NativeNpcModel model,NativeNpcModel remains){this.model=model;remainsModel=remains;pose=new NativeNpcPose(model);capture=new NativeNpcPose(model);remainsPose=remains==null?null:new NativeNpcPose(remains);}
 public NativeNpcPose remainsPose(){return remainsPose;}
 public boolean showBody(){return showBody;}
 public boolean showRemains(){return showRemains;}
 public NativeNpcPose pose(){return pose;}
 public void sample(double clock,boolean moving,int phase,double actionTime,int remaining,double hit,double death){
  if(clock==previous)return;double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.1);
  if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();if(phase==MummyLifecycle.DOWNED)pose.apply("animation.mummy.downed",0);else if(phase==MummyLifecycle.CRUMBLE)pose.apply("animation.mummy.crumble",actionTime);else pose.apply("animation.mummy.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.07));}
  else {
   if(phase!=lastAction){capture.copyFrom(pose);transition=clock;}
   weight+=com.stardew.craft.port.PortJava.clamp((moving?1:0)-weight,-dt/.12,dt/.12);walkTime+=dt*weight;pose.reset();
   switch(phase){
    case MummyLifecycle.CRUMBLE -> {pose.apply("animation.mummy.crumble",actionTime);pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.045));}
    case MummyLifecycle.DOWNED -> {pose.apply("animation.mummy.downed",0);if(remaining<2000){float shake=(float)(.24*Math.sin(clock*62));pose.addPosition("root",shake,0,-shake*.5F);}}
    case MummyLifecycle.REVIVE -> pose.apply("animation.mummy.revive",actionTime);
    default -> {pose.apply("animation.mummy.idle",clock);pose.blend("animation.mummy.walk",walkTime,NativeGrubMotion.smooth(weight));if(lastAction==MummyLifecycle.REVIVE||clock-transition<.08)pose.blendFrom(capture,1-NativeGrubMotion.smooth((clock-transition)/.08));if(hit>=0&&hit<.25)NativeMonsterMotion.addRotationClip(model,pose,"animation.mummy.hit",hit);}
   }
  }
  float low=Float.POSITIVE_INFINITY;var v=new Vector3f();var matrices=pose.matrices();for(var q:model.quads())for(var p:q.vertices())low=Math.min(low,matrices[q.bone()].transformPosition(v.set(p[0],p[1],p[2])).y);if(Float.isFinite(low))pose.addPosition("root",0,.5F-low,0);
  showBody=phase!=MummyLifecycle.DOWNED && !(phase==MummyLifecycle.CRUMBLE&&actionTime>=.3) && !(phase==MummyLifecycle.REVIVE&&actionTime<=.1);
  showRemains=phase==MummyLifecycle.DOWNED || phase==MummyLifecycle.CRUMBLE&&actionTime>.14 || phase==MummyLifecycle.REVIVE&&actionTime<.3;
  if(remainsPose!=null){
   remainsPose.reset();
   if(death>0&&phase==MummyLifecycle.DOWNED)remainsPose.apply("animation.mummy.death",death);
   else if(phase==MummyLifecycle.CRUMBLE)remainsPose.apply("animation.mummy.crumble",actionTime);
   else if(phase==MummyLifecycle.REVIVE)remainsPose.apply("animation.mummy.revive",actionTime);
   else remainsPose.apply("animation.mummy.downed",0);
   float bottom=Float.POSITIVE_INFINITY;var pm=remainsPose.matrices();
   for(var q:remainsModel.quads())for(var point:q.vertices())bottom=Math.min(bottom,pm[q.bone()].transformPosition(v.set(point[0],point[1],point[2])).y);
   if(Float.isFinite(bottom))remainsPose.addPosition("root",0,.5F-bottom,0);
   if(phase==MummyLifecycle.DOWNED&&remaining<2000&&death<=0){float shake=(float)(.24*Math.sin(clock*62));remainsPose.addPosition("root",shake,0,-shake*.5F);}
  }
  previous=clock;lastAction=phase;dying=death>0;
 }
}
