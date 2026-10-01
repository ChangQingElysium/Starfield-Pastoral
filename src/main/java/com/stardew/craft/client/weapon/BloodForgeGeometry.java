package com.stardew.craft.client.weapon;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Thin light surfaces support the weapon and contact; no large glyph or replacement picture. */
public final class BloodForgeGeometry {
    private BloodForgeGeometry() {}
    public static Vec3 recoveryPoint(Vec3 from,Vec3 to,double t) {
        return from.lerp(to,t).add(0,Math.sin(Math.PI*t)*0.22,0);
    }
    public static void recovery(VertexConsumer out,Matrix4f pose,Vec3 from,Vec3 to,float age) {
        double head=com.stardew.craft.port.PortJava.clamp(age/8,0,1), tail=Math.max(0,head-.3);
        for(int i=0;i<12;i++) {
            double a=tail+(head-tail)*i/12,b=tail+(head-tail)*(i+1)/12;
            Vec3 p=recoveryPoint(from,to,a),q=recoveryPoint(from,to,b);
            float fade=(float)Math.sin(Math.PI*head)*(i+1)/12f;
            for(Vec3 axis:new Vec3[]{new Vec3(0,1,0),new Vec3(0,0,1)}) {
                strip(out,pose,p,q,axis.scale(.025*fade),188,31,61,Math.round(85*fade));
                strip(out,pose,p,q,axis.scale(.006*fade),255,206,192,Math.round(180*fade));
            }
        }
    }
    public static void heat(VertexConsumer out,Matrix4f pose,Vec3 p,Vec3 right,Vec3 up,float progress) {
        float strength=com.stardew.craft.port.PortJava.clamp(progress,0,1);
        Vec3 axis=right.add(up.scale(.3)).normalize();
        Vec3 a=p.subtract(axis.scale(.23)),b=p.add(axis.scale(.23));
        WeaponContactGeometry.blade(out,pose,a,b,right.cross(up),.038,0.6f+strength*.4f,false,
                180+(int)(65*strength),35+(int)(125*strength),20);
    }
    public static void blade(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,boolean moon) {
        Vec3 axis=tip.subtract(base).normalize(),side=new Vec3(-axis.y,axis.x,0).normalize();
        for(int face:new int[]{-1,1}) {
            Vec3 offset=new Vec3(0,0,face*.034);
            Vec3 a=base.lerp(tip,moon?.45:.7).add(offset),b=tip.add(offset);
            strip(out,pose,a,b,side.scale(moon?.016:.01),162,18,44,95);
            strip(out,pose,a,b,side.scale(.004),240,82,101,160);
        }
    }
    public static void fireFront(VertexConsumer out,Matrix4f pose,Vec3[] points,float fade) {
        for(int i=0;i<points.length;i++) {
            Vec3 a=points[i],b=points[(i+1)%points.length];
            if(a==null||b==null||Math.abs(a.y-b.y)>.2) continue;
            Vec3 side=b.subtract(a).cross(new Vec3(0,1,0)).normalize();
            strip(out,pose,a,b,side.scale(.065),245,83,14,Math.round(65*fade));
            strip(out,pose,a.add(0,.003,0),b.add(0,.003,0),side.scale(.012),255,211,106,Math.round(165*fade));
            if(i%4==0) strip(out,pose,a,a.add(0,.09*fade,0).add(side.scale(.035)),side.scale(.012*fade),255,166,50,Math.round(125*fade));
        }
    }
    public static void billetTrail(VertexConsumer out,Matrix4f pose,List<com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity.TrailPoint> samples,Vec3 head,float time) {
        Vec3 previous=null; float prior=0;
        for(var sample:samples) {
            // Tick-end positions newer than the rendered projectile are omitted.
            if(sample.tick()>time) continue;
            float fade=com.stardew.craft.port.PortJava.clamp(1-(time-sample.tick())/6,0,1);
            if(previous!=null) trailSegment(out,pose,previous.subtract(head),sample.position().subtract(head),prior,fade);
            previous=sample.position();prior=fade;
        }
        if(previous!=null) trailSegment(out,pose,previous.subtract(head),Vec3.ZERO,prior,1);
    }
    private static void trailSegment(VertexConsumer out,Matrix4f pose,Vec3 a,Vec3 b,float fa,float fb) {
        if(a.distanceToSqr(b)>16) return;
        for(Vec3 axis:new Vec3[]{new Vec3(0,1,0),new Vec3(1,0,0),new Vec3(0,0,1)}) for(int layer=0;layer<2;layer++) {
            double w=layer==0?.065:.012;
            int g=layer==0?111:234,c=layer==0?25:160,alpha=layer==0?60:150;
            vertex(out,pose,a.add(axis.scale(w*fa)),255,g,c,Math.round(alpha*fa*fa));
            vertex(out,pose,a.subtract(axis.scale(w*fa)),255,g,c,Math.round(alpha*fa*fa));
            vertex(out,pose,b.subtract(axis.scale(w*fb)),255,g,c,Math.round(alpha*fb*fb));
            vertex(out,pose,b.add(axis.scale(w*fb)),255,g,c,Math.round(alpha*fb*fb));
        }
    }
}
