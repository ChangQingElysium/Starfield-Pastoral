package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;

/** Snapshot blends preserve an arbitrary crawl/hit pose when stopping, forming or dying. */
public final class NativeGrubPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose,outgoing,deathCapture;
    private double lastClock=Double.NaN,transition=-100;
    private int phase=-1;
    private boolean moving,dying;
    public NativeGrubPlayback(NativeNpcModel model) {
        this.model=model;pose=new NativeNpcPose(model);outgoing=new NativeNpcPose(model);deathCapture=new NativeNpcPose(model);
    }
    public NativeNpcPose pose() { return pose; }
    /** 0=mobile, 1=retreat, 2=forming, 3=pupa. Form progress is authoritative, independent of the pose blend. */
    public void sample(double clock,int next,boolean moves,double age,double formProgress,double hitAge,double deathTime) {
        if(clock==lastClock)return;
        boolean reset=!Double.isFinite(lastClock)||clock<lastClock||clock-lastClock>.5;
        if(deathTime>0) {
            if(!dying||reset) {
                if(reset)live(clock-deathTime,next,moves,Math.max(0,age-deathTime),formProgress,hitAge-deathTime);
                deathCapture.copyFrom(pose);
            }
            NativeGrubMotion.sample(model,pose,"death",deathTime);
            pose.blendFrom(deathCapture,1-NativeGrubMotion.smooth(deathTime/.16));
        } else {
            if(reset){phase=next;moving=moves;transition=clock-1;}
            if(phase!=next||moving!=moves||dying){outgoing.copyFrom(pose);transition=clock;phase=next;moving=moves;}
            live(clock,next,moves,age,formProgress,hitAge);
            double weight=1-NativeGrubMotion.smooth((clock-transition)/.18);
            if(weight>0)pose.blendFrom(outgoing,weight);
        }
        NativeGrubMotion.ground(model,pose);dying=deathTime>0;lastClock=clock;
    }
    private void live(double clock,int phase,boolean moves,double age,double progress,double hitAge) {
        String clip=phase==3?"pupa":phase==2?"form":moves?"crawl":"idle";
        double time=phase==3?age:phase==2?com.stardew.craft.port.PortJava.clamp(progress,0,1)*.4:clock;
        NativeGrubMotion.sample(model,pose,clip,time);
        if(hitAge>=0&&hitAge<.26&&phase<2)NativeMonsterMotion.addRotationClip(model,pose,"animation.grub.hit",hitAge);
    }
}
