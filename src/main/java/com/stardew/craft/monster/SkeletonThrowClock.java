package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
/** AnimatedSprite's carried float timer and strict >150ms advance, never a guessed cooldown. */
public final class SkeletonThrowClock {
    private float timer;private int frame;private boolean throwing;
    public boolean throwing(){return throwing;}
    public double progress(){return throwing?com.stardew.craft.port.PortJava.clamp((frame-20+timer/150.)/4.,0,1):0;}
    public void begin(){throwing=true;frame=20;}
    public void interrupt(){throwing=false;frame-=Math.floorMod(frame,4);}
    public void walk(){timer+=1000F/60;if(timer>175){frame=(frame+1)%4;timer=0;}}
    public boolean step(){if(!throwing)return false;timer+=1000F/60;if(timer>150){timer=0;if(++frame==24){throwing=false;frame=0;return true;}}return false;}
    public CompoundTag save(){var t=new CompoundTag();t.putFloat("Timer",timer);t.putInt("Frame",frame);t.putBoolean("Throwing",throwing);return t;}
    public void load(CompoundTag t){timer=t.getFloat("Timer");frame=t.getInt("Frame");throwing=t.getBoolean("Throwing");}
}
