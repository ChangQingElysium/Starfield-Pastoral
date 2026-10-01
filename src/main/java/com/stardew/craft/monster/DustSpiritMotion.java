package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** DustSpirit.cs and Character.update: source pixels at 60 steps/second, including integer jump offsets. */
public final class DustSpiritMotion {
    private int offset,voice=1,slipperiness=2;
    private float jumpVelocity;
    private double x,y;
    private boolean seen,running,charging;
    public int offset(){return offset;}
    public double x(){return x;}
    /** Source positive Y velocity moves north, opposite Minecraft Z. */
    public double y(){return y;}
    public boolean seen(){return seen;}
    public boolean running(){return running;}
    public boolean charging(){return charging;}
    public void voice(int v){voice=v;}
    public int voice(){return voice;}
    public void see(){seen=true;}
    public void flee(){running=true;}
    public void charge(){charging=true;slipperiness=10;}
    public void repath(){charging=false;}
    public void jumpPhysics(){if(offset!=0){jumpVelocity-=.5F;offset-=(int)jumpVelocity;if(offset>=0){offset=0;jumpVelocity=0;}}}
    public void launch(RandomSource r){offset=-1;jumpVelocity=r.nextInt(20)/10F+5;}
    public void landingDrift(RandomSource r){if(!charging)x=(r.nextInt(41)-20)/5.;}
    public void accelerate(double dx,double dz,int playerFacing,RandomSource r){
        // getAwayFromPlayerTrajectory uses two independently randomized components.
        double ax=-dx,ay=dz;
        if(ax==0&&ay==0){ax=playerFacing==3?-1:playerFacing==1?1:0;ay=playerFacing==0?1:playerFacing==2?-1:0;}
        double length=Math.hypot(ax,ay);ax/=length;ay/=length;
        ax*=50+r.nextInt(40)-20;ay*=50+r.nextInt(40)-20;
        x=com.stardew.craft.port.PortJava.clamp(x-ax/150+(r.nextDouble()<.01?(r.nextInt(100)-50)/10.:0),-5,5);
        y=com.stardew.craft.port.PortJava.clamp(y-ay/150+(r.nextDouble()<.01?(r.nextInt(100)-50)/10.:0),-5,5);
    }
    public void decay(boolean blocked){double divisor=blocked&&slipperiness>=8?slipperiness*4:slipperiness;
        x-=x/divisor;y-=y/divisor;if(Math.abs(x)<=.05)x=0;if(Math.abs(y)<=.05)y=0;
    }
    public void knockback(double vx,double vy){if(Math.abs(vx)>Math.abs(x))x=vx;if(Math.abs(vy)>Math.abs(y))y=vy;}
    public static float pitch(int sourcePitch){return (float)Math.pow(2,-1+sourcePitch/1200.);}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("Offset",offset);t.putFloat("JumpVelocity",jumpVelocity);t.putDouble("X",x);t.putDouble("Y",y);t.putBoolean("Seen",seen);t.putBoolean("Running",running);t.putBoolean("Charging",charging);t.putInt("Voice",voice);t.putInt("Slipperiness",slipperiness);return t;}
    public void load(CompoundTag t){offset=t.getInt("Offset");jumpVelocity=t.getFloat("JumpVelocity");x=t.getDouble("X");y=t.getDouble("Y");seen=t.getBoolean("Seen");running=t.getBoolean("Running");charging=t.getBoolean("Charging");voice=t.getInt("Voice");slipperiness=t.contains("Slipperiness")?t.getInt("Slipperiness"):2;}
}
