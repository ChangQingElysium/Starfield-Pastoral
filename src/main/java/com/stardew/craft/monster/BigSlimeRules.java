package com.stardew.craft.monster;
import net.minecraft.util.RandomSource;

/** BigSlime.cs constructor and independent death split roll, ordinary difficulty. */
public final class BigSlimeRules {
    private BigSlimeRules(){}
    /** Whole gel/hull envelope, including squash and hit tilt; independent of animation phase. */
    public static net.minecraft.world.phys.AABB collisionBox(double x,double y,double z,float yaw,float scale){
        double a=Math.toRadians(yaw),c=Math.abs(Math.cos(a)),s=Math.abs(Math.sin(a));
        double dx=(.97*c+.80*s)*scale,dz=(.97*s+.80*c)*scale;
        return new net.minecraft.world.phys.AABB(x-dx,y,z-dz,x+dx,y+1.4*scale,z+dz);
    }
    public static int area(int floor){return floor>120?121:floor>=80?80:floor>=40?40:0;}
    public static int healthMultiplier(int area){return area==121?4:area==80?3:area==40?2:1;}
    public static int damageMultiplier(int area){return area==121?3:area==80?2:1;}
    public static int experienceMultiplier(int area){return area>=80?3:area==40?2:1;}
    public static int color(int area,RandomSource random){
        int base=switch(area){case 40->0x40e0d0;case 80->0xff0000;case 121->0x8a2be2;default->0x00ff00;};
        int r=com.stardew.craft.port.PortJava.clamp((base>>16&255)+random.nextInt(-20,21),0,255),g=com.stardew.craft.port.PortJava.clamp((base>>8&255)+random.nextInt(-20,21),0,255),b=com.stardew.craft.port.PortJava.clamp((base&255)+random.nextInt(-20,21),0,255);float brightness=random.nextInt(7,11)/10F;
        return (int)(255*brightness)<<24|(int)(r*brightness)<<16|(int)(g*brightness)<<8|(int)(b*brightness);
    }
    public static boolean holdsCake(int area,RandomSource random){return random.nextDouble()<.01&&area>=40;}
    public static int splitCount(RandomSource random){return random.nextDouble()<.75?random.nextInt(2,5):0;}
}
