package com.stardew.craft.npc.animation;

/** Shipped native Sam activities; the same aliases are accepted by schedules and scripted actors. */
public enum SamActivity {
    GUITAR("guitar"), GAMEBOY("gameboy"), SKATEBOARD("skateboard"), SWEEP("sweep"), SIT("sit"), SLEEP("sleep"), POOL("pool");

    private final String name;
    SamActivity(String name) { this.name=name; }
    public String asset() { return "sam_"+name; }
    public String playClip() { return "animation.sam."+name+"_play"; }
    public String holdClip() { return "animation.sam."+name+"_hold"; }
    public boolean supported() { return this == SIT || this == SLEEP; }
    public int enterTicks() { return this == SLEEP ? 104 : this == SIT ? 32 : 16; }
    public int exitTicks() { return this == SLEEP ? 108 : this == SIT ? 32 : 16; }
    public String enterClip() { return "animation.sam."+name+"_enter"+(this == SLEEP ? "_full" : ""); }
    public String exitClip() { return "animation.sam."+name+"_exit"+(this == SLEEP ? "_full" : ""); }
    public static SamActivity fromAnimation(String name) {
        if ("sam_skateboarding".equals(name)) return SKATEBOARD;
        if ("sam_work".equals(name)) return SWEEP;
        for(var action:values())if(action.asset().equals(name) || action.playClip().equals(name)
                || action.holdClip().equals(name))return action;
        return null;
    }
}
