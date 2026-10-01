package com.stardew.craft.client.monsternative;
import com.stardew.craft.client.npcnative.*;
/** One continuous gel phase; source cadence doubles while moving without resetting the shape. */
public final class NativeBigSlimePlayback {
    private final NativeNpcModel model;private final NativeNpcPose pose,capture;
    private double previous=Double.NaN,phase,rate=.625;private boolean dying;
    public NativeBigSlimePlayback(NativeNpcModel model){this.model=model;pose=new NativeNpcPose(model);capture=new NativeNpcPose(model);}
    public NativeNpcPose pose(){return pose;}
    public void sample(double clock,boolean moving,double hit,double death){
        if(clock==previous)return;
        double dt=Double.isNaN(previous)?0:com.stardew.craft.port.PortJava.clamp(clock-previous,0,.25);
        double target=moving?1.25:.625,old=rate;rate=target+(rate-target)*Math.exp(-dt/.09);phase+=(old+rate)*.5*dt;
        if(death>0){if(!dying)capture.copyFrom(pose);pose.reset();pose.apply("animation.big_slime.death",death);pose.blendFrom(capture,1-NativeGrubMotion.smooth(death/.08));}
        else {pose.reset();pose.apply("animation.big_slime.idle",phase*1.6);if(hit>=0&&hit<.24)pose.blend("animation.big_slime.hit",hit,1);}
        dying=death>0;previous=clock;
    }
}
