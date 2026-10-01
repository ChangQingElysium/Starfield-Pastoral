package com.stardew.craft.templates.client;

import com.stardew.craft.templates.*;
import java.util.*;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import static com.stardew.craft.templates.client.TemplateMesh.*;

/** Material-independent roof body and connected gable trim, with separate optional artwork UVs. */
final class RoofTemplateDetails {

    static List<MeshQuad> add(List<MeshQuad> shell, TemplateShape shape, BlockState state,
                              int phaseX, int phaseZ, int edges, int phaseY) {
        List<MeshQuad> result = new ArrayList<>();
        for (MeshQuad q : shell) {
            // A connection removes the internal section, not the thickness
            // of either neighboring roof course.
            mapTexture(result, q, phaseX, phaseZ, phaseY);
        }
        if (!shape.roofForm().isRidge()) {
            // Raised verge only on sloping, exposed gable edges. A matching
            // neighbor removes it; courses on the same gable share its section.
            for (MeshQuad q : shell) if (q.part() == RoofPart.FIELD) {
                var field=polygon(q);
                for (int side=0;side<4;side++) {
                    if ((edges & (1<<side))==0) continue;
                    final int edge=side;
                    ToDoubleFunction<Vector3f> distance=v->edgeDistance(v,edge);
                    var planeNormal=normal(q);
                    double edgeSlope=(side%2==0?planeNormal.x:planeNormal.z);
                    if (Math.abs(edgeSlope)<1E-6) continue;
                    var band=clip(field,distance,0,RoofTemplateForm.VERGE_WIDTH);
                    if (band.size()<3 || area(band)<1E-9) continue;
                    var top=band.stream().map(v->new Vector3f(v).add(0,RoofTemplateForm.VERGE_HEIGHT,0)).toList();
                    for(int i=1;i<top.size()-1;i++) mapTexture(result,new MeshQuad(Direction.UP,Direction.UP,
                            List.of(com.stardew.craft.port.PortJava.getFirst(top),top.get(i),top.get(i+1),top.get(i+1)),null).withPart(RoofPart.CAP),phaseX,phaseZ,phaseY);
                    for(int i=0;i<band.size();i++) {
                        int j=(i+1)%band.size();var a=band.get(i);var b=band.get(j);var A=top.get(i);var B=top.get(j);
                        boolean wall=Math.abs(distance.applyAsDouble(a)-RoofTemplateForm.VERGE_WIDTH)<1E-6
                                && Math.abs(distance.applyAsDouble(b)-RoofTemplateForm.VERGE_WIDTH)<1E-6;
                        for(int boundary=0;boundary<4;boundary++) if((edges&(1<<boundary))!=0
                                && edgeDistance(a,boundary)<1E-6 && edgeDistance(b,boundary)<1E-6) wall=true;
                        if(!wall) continue;
                        var normal=new Vector3f(b).sub(a).cross(new Vector3f(A).sub(a));
                        if(normal.lengthSquared()<1E-10)continue;
                        Direction d=Direction.getNearest(normal.x,0,normal.z);
                        mapTexture(result,new MeshQuad(d,d,List.of(A,a,b,B),null).withPart(RoofPart.TRIM),phaseX,phaseZ,phaseY);
                    }
                }
            }
        }
        return result;
    }

    private static double edgeDistance(Vector3f v,int edge) {
        return switch(edge) {case 0 -> v.z;case 1 -> 1-v.x;case 2 -> 1-v.z;default -> v.x;};
    }

    static List<MeshQuad> studyTexture(List<MeshQuad> quads,int phaseX,int phaseZ) {
        return quads.stream().map(q->q.studyPoints()==null?q:new MeshQuad(q.direction(),q.textureDirection(),q.vertices(),q.studyPoints(),q.part(),q.studyPoints())).toList();
    }

    private static void mapTexture(List<MeshQuad> out,MeshQuad q,int phaseX,int phaseZ,int phaseY) {
        List<Vector3f> p=polygon(q);
        if(p.size()<3 || area(p)<1E-10) return;
        Plane plane=new Plane(q,phaseX,phaseZ,phaseY);
        if(q.part()==RoofPart.EDGE) {
            boolean alongX=q.direction().getAxis()==Direction.Axis.Z;
            ToDoubleFunction<Vector3f> t=v->alongX?v.x:v.z;
            double lo=min(p,t),hi=max(p,t);
            if(hi-lo<1E-8)return;
            double lowTop=p.stream().filter(v->Math.abs(t.applyAsDouble(v)-lo)<1E-6).mapToDouble(v->v.y).max().orElse(0);
            double highTop=p.stream().filter(v->Math.abs(t.applyAsDouble(v)-hi)<1E-6).mapToDouble(v->v.y).max().orElse(0);
            double gradient=(highTop-lowTop)/(hi-lo);
            ToDoubleFunction<Vector3f> roof=v->lowTop+(t.applyAsDouble(v)-lo)*gradient;
            ToDoubleFunction<Vector3f> u=Math.abs(gradient)<1E-6
                    ? v->(t.applyAsDouble(v)+(alongX?phaseX:phaseZ))*16
                    : v->-(roof.applyAsDouble(v)+phaseY)*Math.round(16*Math.sqrt(1+gradient*gradient))/Math.abs(gradient);
            ToDoubleFunction<Vector3f> depth=v->Math.max(0,(roof.applyAsDouble(v)-v.y)*16);
            int period=Math.abs(gradient)<1E-6?64:68;
            for(int tile=(int)Math.floor(min(p,u)/period);tile<=(int)Math.floor(max(p,u)/period);tile++) {
                final int k=tile;var band=clip(p,u,tile*period,(tile+1)*period);
                // Camouflage side faces keep world-horizontal brick courses.
                // Only the optional authored fascia uses sloping wood grain.
                emitMapped(out,q,band,v->(t.applyAsDouble(v)+(alongX?phaseX:phaseZ))*16,v->-(v.y+phaseY)*16,
                        v->(144+u.applyAsDouble(v)-k*period)/256,v->(96+depth.applyAsDouble(v))/128);
            }
            return;
        }
        if((q.part()==RoofPart.FIELD || q.part()==RoofPart.UNDERSIDE || q.part()==RoofPart.CAP) && !plane.flat) {
            // Source study: 64x68 slate, 64x68 backing. A 9-course repeat
            // reverses the half-tile offset, so two repeats restore the phase.
            int c0=(int)Math.floor(min(p,plane.down)/68),c1=(int)Math.floor(max(p,plane.down)/68);
            for(int cycle=c0;cycle<=c1;cycle++) {
                var band=clip(p,plane.down,cycle*68,(cycle+1)*68);
                int shift=Math.floorMod(cycle,2)*4;
                int x0=(int)Math.floor((min(band,plane.across)+shift)/64),x1=(int)Math.floor((max(band,plane.across)+shift)/64);
                for(int tile=x0;tile<=x1;tile++) {
                    var part=clip(band,plane.across,tile*64-shift,(tile+1)*64-shift);
                    final int cy=cycle,tx=tile;
                    emitMapped(out,q,part,plane.across,plane.nativeDown,
                            v->((q.part()==RoofPart.UNDERSIDE?64:0)+Math.max(0,Math.min(64,plane.across.applyAsDouble(v)+shift-tx*64)))/256,
                            v->Math.max(0,Math.min(68,plane.down.applyAsDouble(v)-cy*68))/128);
                }
            }
        } else {
            Vector3f normal=normal(q);
            float rx=(float)(max(p,v->v.x)-min(p,v->v.x)),rz=(float)(max(p,v->v.z)-min(p,v->v.z));
            Vector3f u=rx>=rz?new Vector3f(1,0,0):new Vector3f(0,0,1);
            u.sub(new Vector3f(normal).mul(u.dot(normal))).normalize();
            if(!Float.isFinite(u.x)) u.set(0,1,0);
            Vector3f v=new Vector3f(normal).cross(u).normalize();
            ToDoubleFunction<Vector3f> pu=a->a.dot(u)*16,pv=a->a.dot(v)*16;
            double lo=min(p,pv);
            double baseU=min(p,pu),baseV=q.part()==RoofPart.FIELD || q.part()==RoofPart.CAP ? 16 : 0;
            double startU=144;
            emitMapped(out,q,p,pu,pv,a->(startU+pu.applyAsDouble(a)-baseU)/256,
                    a->(baseV+pv.applyAsDouble(a)-lo)/128);
        }
    }

    private static void emitMapped(List<MeshQuad> out,MeshQuad q,List<Vector3f> p,
                                   ToDoubleFunction<Vector3f> u,ToDoubleFunction<Vector3f> v,
                                   ToDoubleFunction<Vector3f> su,ToDoubleFunction<Vector3f> sv) {
        if(p.size()<3) return;
        for(int iu=(int)Math.floor(min(p,u)/16);iu<=(int)Math.floor(max(p,u)/16);iu++) {
            List<Vector3f> a=clip(p,u,iu*16,(iu+1)*16);
            if(a.size()<3) continue;
            for(int iv=(int)Math.floor(min(a,v)/16);iv<=(int)Math.floor(max(a,v)/16);iv++) {
                List<Vector3f> b=clip(a,v,iv*16,(iv+1)*16);
                if(b.size()<3 || area(b)<1E-10) continue;
                int x=iu,y=iv;
                for(int j=1;j<b.size()-1;j++) {
                    var points=List.of(com.stardew.craft.port.PortJava.getFirst(b),b.get(j),b.get(j+1),b.get(j+1));
                    out.add(new MeshQuad(q.direction(),q.textureDirection(),points,
                            points.stream().map(t->new TexturePoint(unit(u.applyAsDouble(t)/16-x),unit(v.applyAsDouble(t)/16-y))).toList(),q.part(),
                            points.stream().map(t->new TexturePoint(unit(su.applyAsDouble(t)),unit(sv.applyAsDouble(t)))).toList()));
                }
            }
        }
    }

    private record Plane(ToDoubleFunction<Vector3f> across,ToDoubleFunction<Vector3f> down,
                         ToDoubleFunction<Vector3f> nativeDown,boolean flat) {
        Plane(MeshQuad q,int px,int pz,int py) { this(coordinates(q,px,pz,py)); }
        private Plane(Plane p) { this(p.across,p.down,p.nativeDown,p.flat); }
        private static Plane coordinates(MeshQuad q,int px,int pz,int py) {
            Vector3f n=normal(q);
            float gx=Math.abs(n.y)<1E-6?0:-n.x/n.y,gz=Math.abs(n.y)<1E-6?0:-n.z/n.y;
            boolean x=Math.abs(gx)>Math.abs(gz);
            float gradient=x?gx:gz;
            boolean flat=Math.abs(gradient)<1E-5;
            int advance=Math.round(16*(float)Math.sqrt(1+gradient*gradient));
            ToDoubleFunction<Vector3f> across=v->((x?v.z:v.x)+(x?pz:px))*16;
            // Equal world height means equal tile course, including the two
            // perpendicular planes of an inner/outer corner at any position.
            ToDoubleFunction<Vector3f> down=flat ? v->v.z*16
                    : v->-(v.y+py)*advance/Math.abs(gradient);
            return new Plane(across,v->down.applyAsDouble(v)+4,down,flat);
        }
    }

    private static Vector3f normal(MeshQuad q) {
        // Newell also handles triangular side faces with a repeated first edge.
        Vector3f n=new Vector3f();
        for(int i=0;i<4;i++) {Vector3f a=q.vertices().get(i),b=q.vertices().get((i+1)%4);
            n.add((a.y-b.y)*(a.z+b.z),(a.z-b.z)*(a.x+b.x),(a.x-b.x)*(a.y+b.y));}
        return n.lengthSquared()<1E-12?new Vector3f(q.direction().step()):n.normalize();
    }
    private static List<Vector3f> polygon(MeshQuad q) {return q.vertices().stream().distinct().toList();}
    private static double min(List<Vector3f> p,ToDoubleFunction<Vector3f> f) {return p.stream().mapToDouble(f).min().orElse(0);}
    private static double max(List<Vector3f> p,ToDoubleFunction<Vector3f> f) {return p.stream().mapToDouble(f).max().orElse(0);}
    private static double area(List<Vector3f> p) {
        if(p.size()<3) return 0;
        double a=0;for(int i=1;i<p.size()-1;i++)a+=new Vector3f(p.get(i)).sub(com.stardew.craft.port.PortJava.getFirst(p)).cross(new Vector3f(p.get(i+1)).sub(com.stardew.craft.port.PortJava.getFirst(p))).length();return a;
    }
    private static List<Vector3f> clip(List<Vector3f> p,ToDoubleFunction<Vector3f> f,double lo,double hi) {
        return clipSide(clipSide(p,v->lo-f.applyAsDouble(v)),v->f.applyAsDouble(v)-hi);
    }
    private static List<Vector3f> clipSide(List<Vector3f> p,ToDoubleFunction<Vector3f> f) {
        if(p.isEmpty())return p;
        List<Vector3f> out=new ArrayList<>();Vector3f a=com.stardew.craft.port.PortJava.getLast(p);double fa=f.applyAsDouble(a);
        for(Vector3f b:p) {double fb=f.applyAsDouble(b);boolean ai=fa<=1E-6,bi=fb<=1E-6;
            if(ai!=bi)out.add(new Vector3f(a).lerp(b,(float)Math.max(0,Math.min(1,fa/(fa-fb)))));
            if(bi)out.add(b);a=b;fa=fb;}
        return out;
    }
    private static float unit(double v) { return (float)Math.max(0,Math.min(1,v)); }
    private RoofTemplateDetails() {}
}
