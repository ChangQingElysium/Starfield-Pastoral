package com.stardew.craft.client.monsternative;

import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.entity.monster.RockCrabEntity;

/** Per-observer presentation. Action clips never delay authoritative movement or immunity. */
public final class NativeRockCrabPlayback {
    private final NativeNpcModel model;
    private final NativeNpcPose pose, outgoing;
    private int phase=-1;
    private boolean moving;
    private double entered, movementChanged, transition, lastClock=Double.NaN;
    private String clip="disguise";
    private double time;
    public NativeRockCrabPlayback(NativeNpcModel model) { this.model=model;pose=new NativeNpcPose(model);outgoing=new NativeNpcPose(model); }
    public NativeNpcPose pose() { return pose; }
    public boolean visible(String part) { return NativeRockCrabMotion.visible(part,clip,time); }
    public void sample(int next, boolean moves, double actionTime, double clock, double hitTime, double deathTime) {
        if(clock==lastClock)return;
        boolean reset=!Double.isFinite(lastClock)||clock<lastClock||clock-lastClock>.5;
        if(reset) { phase=next;moving=moves;entered=clock-actionTime;movementChanged=clock-1;transition=clock-1; }
        if(next!=phase) { outgoing.copyFrom(pose);phase=next;entered=clock;transition=clock; }
        if(moves!=moving) { outgoing.copyFrom(pose);moving=moves;movementChanged=clock;transition=clock; }
        double age=clock-entered, moveAge=clock-movementChanged;
        if(deathTime>0) {clip=next==RockCrabEntity.BARE?"death_bare":"death_shell";time=deathTime;}
        else if(next==RockCrabEntity.DISGUISE) {clip=age<.45?"retract":"disguise";time=age;}
        else if(next==RockCrabEntity.ACTIVE&&age<.5) {clip="emerge";time=age;}
        else if(next==RockCrabEntity.BARE&&age<.4) {clip="shell_break";time=age;}
        else {
            boolean bare=next==RockCrabEntity.BARE;
            double start=bare?.175:.21;
            if(moving&&age<(bare?.4:.5)+start) {clip=bare?"flee_start":"walk_start";time=age-(bare?.4:.5);}
            else if(!moving&&moveAge<start) {clip=bare?"flee_stop":"walk_stop";time=moveAge;}
            else {clip=moving?(bare?"flee":"walk"):(bare?"bare_idle":"idle");time=moving?Math.max(0,age-(bare?.575:.71)):clock;}
        }
        // Initial camouflage must already be a stone, including newly created entities at time zero.
        if(reset&&next==RockCrabEntity.DISGUISE){clip="disguise";time=0;entered=clock-1;}
        NativeRockCrabMotion.sample(model,pose,clip,time);
        if(deathTime<=0) {
            double p=com.stardew.craft.port.PortJava.clamp((clock-transition)/.15,0,1);
            if(p<1)pose.blendFrom(outgoing,1-p*p*(3-2*p));
            if(hitTime>=0&&hitTime<.5&&next!=RockCrabEntity.BARE) {
                double shake=Math.sin(hitTime*Math.PI*16)*Math.pow(1-hitTime/.5,2);
                pose.addRotation("shell",0,0,shake*3);pose.addPosition("shell",shake*.22,0,0);
            }
        }
        lastClock=clock;
    }
}
