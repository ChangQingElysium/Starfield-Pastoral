package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class CrystalVenomGeometry {
    private CrystalVenomGeometry() {}
    public static void venomContact(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 right,Vec3 up,Vec3 normal,
                                    float size,float age,float fade,boolean edge,boolean burst,int r,int g,int b) {
        for(int sign:new int[]{-1,1}) {
            Vec3[] path=new Vec3[17];
            for(int i=0;i<path.length;i++) {
                double t=i/16.0,x=sign*(-.4+.8*t),y=sign*Math.sin(Math.PI*t)*.22;
                if(burst) {x+=sign*age*.025;y+=sign*.1;}
                path[i]=center.add(right.scale(x*size)).add(up.scale(y*size));
            }
            WeaponContactGeometry.ribbon(out,pose,path,normal,size*(burst?.09:.065),fade,edge,r,g,b);
        }
    }
    public static void poisonMark(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 right,Vec3 up,Vec3 normal,
                                  int stacks,float fuse,float visibility) {
        // Two short curved punctures; the fuse surrounds them only while a detonation is scheduled.
        for(int sign:new int[]{-1,1}) {
            Vec3[] fang=new Vec3[9];
            for(int i=0;i<fang.length;i++) {double t=i/8.0;fang[i]=center.add(right.scale(sign*(.045+Math.sin(Math.PI*t)*.035))).add(up.scale(.09-.18*t));}
            WeaponContactGeometry.ribbon(out,pose,fang,normal,.009+com.stardew.craft.port.PortJava.clamp(stacks,1,5)*.0015,visibility,false,103,178,57);
        }
        if(fuse<0) return;
        double radius=.13+.20*(1-com.stardew.craft.port.PortJava.clamp(fuse,0,1));
        for(int sign:new int[]{-1,1}) {
            Vec3[] arc=new Vec3[17];
            for(int i=0;i<arc.length;i++) {double angle=-1.1+i*2.2/16;arc[i]=center.add(right.scale(sign*Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));}
            WeaponContactGeometry.ribbon(out,pose,arc,normal,.02,visibility,false,159+(int)(65*fuse),194,63);
        }
    }
    public static void crystalLayers(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,int stacks,float burstAge) {
        Vec3 along=tip.subtract(base).normalize(),side=new Vec3(-along.y,along.x,0).normalize(),normal=new Vec3(0,0,1);
        boolean burst=burstAge>=0&&burstAge<6;
        for(int i=0;i<(burst?4:com.stardew.craft.port.PortJava.clamp(stacks,0,4));i++) for(int face:new int[]{-1,1}) {
            Vec3 center=base.lerp(tip,.4+i*.14).add(0,0,.036*face);
            if(burst) center=center.add(side.scale((i%2==0?1:-1)*burstAge*.018));
            WeaponContactGeometry.blade(out,pose,center.subtract(along.scale(.03)),center.add(along.scale(.03)),normal,
                    .010,burst?1-burstAge/6:stacks==4?.95f:.68f,false,116,204,255);
        }
    }
    public static void ripple(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 forward,float age) {
        if(age<0||age>=6) return;
        Vec3 side=forward.cross(new Vec3(0,1,0)).normalize();
        Vec3[] arc=new Vec3[25];double radius=.6+age*.15;
        for(int i=0;i<arc.length;i++) {double angle=-1.25+i*2.5/24;arc[i]=center.add(forward.scale(Math.cos(angle)*radius)).add(side.scale(Math.sin(angle)*radius));}
        WeaponContactGeometry.ribbon(out,pose,arc,new Vec3(0,1,0),.07,(1-age/6)*.75f,false,93,165,53);
    }
}
