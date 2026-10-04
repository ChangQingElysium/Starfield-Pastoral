package com.stardew.craft.client.model.nativebb;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.model.ModelAnimation;

/** Per-instance clock and transition; model data is shared, mutable poses never are. */
public final class BlockbenchPlayback {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final NativeNpcModel model;
    private final NativeNpcPose pose, previous;
    private ModelAnimation selection;
    private double start, transitionStart, lastTime=Double.NaN;
    private boolean transitioning;
    private final java.util.Set<String> missingNpcClips = new java.util.HashSet<>();
    public BlockbenchPlayback(NativeNpcModel model) {
        this.model=model;pose=new NativeNpcPose(model);previous=new NativeNpcPose(model);
    }
    public NativeNpcModel model() {return model;}
    /** NPC content gaps retain this model's real idle; machine animations remain strict in sample(). */
    public NativeNpcPose sampleNpc(String npcId, ModelAnimation requested, double now, int transitionTicks) {
        if (requested != null) {
            String clip = com.stardew.craft.client.npcnative.NativeActorAnimation.resolve(model, npcId, requested.clip());
            if (clip == null) {
                String idle = com.stardew.craft.client.npcnative.NativeActorAnimation.resolve(model, npcId, "idle");
                if (missingNpcClips.add(requested.clip())) {
                    LOGGER.warn(
                            "Missing NPC Blockbench animation {} for {}; retaining the same model's {}",
                            requested.clip(), npcId, idle == null ? "static pose" : "idle animation");
                }
                requested = idle == null ? null : ModelAnimation.loop(idle);
            } else if (!clip.equals(requested.clip())) {
                requested = new ModelAnimation(clip, requested.loop(), requested.hold(), requested.time(), requested.initiallyComplete());
            }
        }
        return sample(requested, now, transitionTicks);
    }
    public NativeNpcPose sample(ModelAnimation requested,double now,int transitionTicks) {
        boolean first=!Double.isFinite(lastTime);
        boolean changed=first || !sameSelection(requested,selection);
        if(changed) {
            previous.copyFrom(pose);transitioning=Double.isFinite(lastTime)&&transitionTicks>0;
            start=transitionStart=now;selection=requested;
            if(first && requested!=null && requested.initiallyComplete()) {
                var clip=model.clips().get(requested.clip());
                if(clip!=null)start-=clip.length();
            }
        } else if(now<lastTime) {
            // Render-time corrections are not state changes. Keep elapsed clip and
            // blend time; restarting CLOSE would briefly show a fully open lid.
            double correction=now-lastTime;
            start+=correction;transitionStart+=correction;
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
