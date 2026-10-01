package com.stardew.craft.client.model.nativebb;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.model.ModelAnimation;

/** Per-instance clock and transition; model data is shared, mutable poses never are. */
public final class BlockbenchPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose, previous;
    private ModelAnimation selection;
    private double start, transitionStart, lastTime=Double.NaN;
    private boolean transitioning;
    public BlockbenchPlayback(NativeNpcModel model) {
        this.model=model;pose=new NativeNpcPose(model);previous=new NativeNpcPose(model);
    }
    public NativeNpcModel model() {return model;}
    public NativeNpcPose sample(ModelAnimation requested,double now,int transitionTicks) {
        boolean first=!Double.isFinite(lastTime);
        boolean changed=first || now<lastTime || !sameSelection(requested,selection);
        if(changed) {
            previous.copyFrom(pose);transitioning=Double.isFinite(lastTime)&&transitionTicks>0;
            start=transitionStart=now;selection=requested;
            if(first && requested!=null && requested.initiallyComplete()) {
                var clip=model.clips().get(requested.clip());
                if(clip!=null)start-=clip.length();
            }
        }
        pose.reset();
        if(requested!=null) {
            var clip=model.clips().get(requested.clip());
            if(clip==null)throw new IllegalArgumentException("Missing Blockbench animation: "+requested.clip());
            double time=Double.isFinite(requested.time())?requested.time():Math.max(0,now-start);
            if(requested.loop())time=time-Math.floor(time/clip.length())*clip.length();
            if(requested.loop()||requested.hold()||time<=clip.length())pose.applyAt(requested.clip(),Math.min(time,clip.length()));
        }
        if(transitioning) {
            double weight=(now-transitionStart)*20/transitionTicks;
            if(weight>=1)transitioning=false;else pose.blendFrom(previous,1-weight);
        }
        lastTime=now;return pose;
    }
    private static boolean sameSelection(ModelAnimation a,ModelAnimation b) {
        return a==null?b==null:b!=null && a.clip().equals(b.clip()) && a.loop()==b.loop() && a.hold()==b.hold();
    }
}
