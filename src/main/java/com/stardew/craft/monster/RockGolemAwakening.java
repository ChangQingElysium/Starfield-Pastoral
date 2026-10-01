package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
/** RockGolem's frame 16..23 unfolds once; damage focuses pursuit without making the heap invulnerable. */
public final class RockGolemAwakening {
    private boolean seen,walking,focused;private int frame=16;private float timer;
    public boolean seen(){return seen;}public boolean walking(){return walking;}public boolean focused(){return focused;}
    public boolean awake(){return seen&&frame<16;}
    public double progress(){return awake()?1:com.stardew.craft.port.PortJava.clamp((frame-16+timer/75.)/8,0,1);}
    public boolean step(boolean within){
        if(!seen){if(within){seen=true;return true;}frame=16;}
        else if(frame>=16){timer+=1000F/60;if(timer>75){timer=0;if(++frame>=24){frame=0;walking=true;}}}
        return false;
    }
    public void struck(){walking=true;focused=true;}
    public void walkFrame(){timer+=1000F/60;if(timer>175){timer=0;frame=(frame+1)%4;}}
    public CompoundTag save(){var t=new CompoundTag();t.putBoolean("Seen",seen);t.putBoolean("Walking",walking);t.putBoolean("Focused",focused);t.putInt("Frame",frame);t.putFloat("Timer",timer);return t;}
    public void load(CompoundTag t){seen=t.getBoolean("Seen");walking=t.getBoolean("Walking");focused=t.getBoolean("Focused");frame=t.contains("Frame")?t.getInt("Frame"):16;timer=t.getFloat("Timer");}
}
