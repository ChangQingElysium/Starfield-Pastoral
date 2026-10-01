package com.stardew.craft.client.model.terrain;

import com.stardew.craft.block.terrain.TerrainFaceConnections;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiFunction;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Clips a shape against the material's actual painted rectangles instead of stretching every rectangle over it. */
public final class ShapedMaterialQuads {
    private static final double EPS = 1e-6;
    private ShapedMaterialQuads() {}

    public record Patch(BakedQuad quad, double u, double v) {}
    private record Vertex(Vec3 p, double u, double v) {
        Vertex mix(Vertex b, double t) { return new Vertex(p.lerp(b.p,t),u+(b.u-u)*t,v+(b.v-v)*t); }
    }

    public static List<BakedQuad> map(BakedQuad target, List<BakedQuad> geometry, boolean grass,
            List<Double> extraU, List<Double> extraV, BiFunction<Patch, Direction, List<BakedQuad>> sources) {
        Direction face = target.getDirection();
        var frame = TerrainFaceConnections.frame(face);
        List<Vertex> polygon = vertices(target, frame);
        double minU=polygon.stream().mapToDouble(Vertex::u).min().orElse(0),maxU=polygon.stream().mapToDouble(Vertex::u).max().orElse(0);
        double minV=polygon.stream().mapToDouble(Vertex::v).min().orElse(0),maxV=polygon.stream().mapToDouble(Vertex::v).max().orElse(0);
        TreeSet<Double> us=new TreeSet<>(List.of(minU,maxU)),vs=new TreeSet<>(List.of(minV,maxV));
        for(double u:extraU) if(u>minU+EPS&&u<maxU-EPS)us.add(u);
        for(double v:extraV) if(v>minV+EPS&&v<maxV-EPS)vs.add(v);
        boolean anchor=grass&&face.getAxis().isHorizontal();
        if(anchor)for(BakedQuad q:geometry)if(coplanar(target,q))for(Vertex p:vertices(q,frame))if(p.u>minU+EPS&&p.u<maxU-EPS)us.add(p.u);
        List<Double> ux=new ArrayList<>(us),vy=new ArrayList<>(vs);
        List<BakedQuad> result=new ArrayList<>();
        for(int x=0;x<ux.size()-1;x++)for(int y=0;y<vy.size()-1;y++){
            double x0=ux.get(x),x1=ux.get(x+1),y0=vy.get(y),y1=vy.get(y+1);
            List<Vertex> part=rect(polygon,x0,y0,x1,y1);
            if(part.size()<3||area(part)<EPS)continue;
            double u=(x0+x1)/2,v=(y0+y1)/2;
            List<BakedQuad> paints=sources.apply(new Patch(target,u,v),face);
            double inset=Math.min((x1-x0)/4, EPS*32);
            double top0=anchor?runTop(target,geometry,x0+inset,v):0;
            double top1=anchor?runTop(target,geometry,x1-inset,v):0;
            double slope=(top1-top0)/(x1-x0-2*inset);
            top0-=slope*inset; top1+=slope*inset;
            // One-pixel-per-unit translation: never compress a full 16-pixel side into an 8-pixel slab.
            List<Vertex> mapped=new ArrayList<>();
            for(Vertex p:part){double top=top0+(top1-top0)*(p.u-x0)/(x1-x0);mapped.add(new Vertex(p.p,p.u,p.v-top));}
            for(BakedQuad source:paints){
                List<Vertex> sourcePolygon=vertices(source,TerrainFaceConnections.frame(source.getDirection()));
                double a=sourcePolygon.stream().mapToDouble(Vertex::u).min().orElse(0),b=sourcePolygon.stream().mapToDouble(Vertex::u).max().orElse(1);
                double c=sourcePolygon.stream().mapToDouble(Vertex::v).min().orElse(0),d=sourcePolygon.stream().mapToDouble(Vertex::v).max().orElse(1);
                // Thick roofs and projecting templates can cross a cell edge.
                // Repeat whole native tiles there; cropping to 0..1 would erase their end faces.
                int firstU=(int)Math.floor(mapped.stream().mapToDouble(Vertex::u).min().orElse(0)+EPS);
                int lastU=(int)Math.ceil(mapped.stream().mapToDouble(Vertex::u).max().orElse(1)-EPS);
                int firstV=(int)Math.floor(mapped.stream().mapToDouble(Vertex::v).min().orElse(0)+EPS);
                int lastV=(int)Math.ceil(mapped.stream().mapToDouble(Vertex::v).max().orElse(1)-EPS);
                for(int tileU=firstU;tileU<lastU;tileU++)for(int tileV=firstV;tileV<lastV;tileV++) {
                    List<Vertex> clipped=rect(mapped,a+tileU,c+tileV,b+tileU,d+tileV);
                    if(clipped.size()<3||area(clipped)<EPS)continue;
                    List<Vertex> local=new ArrayList<>();
                    for(Vertex vertex:clipped)local.add(new Vertex(vertex.p,vertex.u-tileU,vertex.v-tileV));
                    emit(result,target,source,local);
                }
            }
        }
        return result;
    }

    /** The top of the continuous side at this column, including an upper stair sharing this plane. */
    static double runTop(BakedQuad target,List<BakedQuad> geometry,double u,double v){
        var frame=TerrainFaceConnections.frame(target.getDirection());
        List<double[]> spans=new ArrayList<>();
        for(BakedQuad q:geometry)if(coplanar(target,q)) {
            double[] span=verticalSpan(q,frame,u);
            if(span!=null)spans.add(span);
        }
        double[] seed=verticalSpan(target,frame,u);
        double lo=seed==null?v:seed[0],hi=seed==null?v:seed[1];boolean changed;
        do{changed=false;for(double[] s:spans)if(s[0]<=hi+EPS&&s[1]>=lo-EPS){double l=Math.min(lo,s[0]),h=Math.max(hi,s[1]);if(l<lo-EPS||h>hi+EPS){lo=l;hi=h;changed=true;}}}while(changed);
        return lo;
    }

    private static double[] verticalSpan(BakedQuad quad,TerrainFaceConnections.Frame frame,double u) {
        List<Vertex> polygon=vertices(quad,frame);List<Double> hits=new ArrayList<>();
        for(int i=0;i<polygon.size();i++) {
            Vertex a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            if(u<Math.min(a.u,b.u)-EPS||u>Math.max(a.u,b.u)+EPS)continue;
            if(Math.abs(b.u-a.u)<EPS){hits.add(a.v);hits.add(b.v);}
            else hits.add(a.v+(b.v-a.v)*(u-a.u)/(b.u-a.u));
        }
        return hits.isEmpty()?null:new double[]{hits.stream().mapToDouble(x->x).min().orElse(0),hits.stream().mapToDouble(x->x).max().orElse(0)};
    }

    public static Vec3 point(BakedQuad q,double u,double v){
        var frame=TerrainFaceConnections.frame(q.getDirection());List<Vertex> p=vertices(q,frame);
        for(int i=1;i<p.size()-1;i++){
            Vertex a=p.get(0),b=p.get(i),c=p.get(i+1);double det=(b.u-a.u)*(c.v-a.v)-(c.u-a.u)*(b.v-a.v);
            if(Math.abs(det)<EPS)continue;
            double s=((u-a.u)*(c.v-a.v)-(c.u-a.u)*(v-a.v))/det;
            double t=((b.u-a.u)*(v-a.v)-(u-a.u)*(b.v-a.v))/det;
            return a.p.add(b.p.subtract(a.p).scale(s)).add(c.p.subtract(a.p).scale(t));
        }
        return com.stardew.craft.port.PortJava.getFirst(p).p;
    }

    public static boolean contains(BakedQuad q,Vec3 point){
        Vec3 normal=normal(q);List<Vertex> p=vertices(q,TerrainFaceConnections.frame(q.getDirection()));
        if(Math.abs(point.subtract(com.stardew.craft.port.PortJava.getFirst(p).p).dot(normal))>EPS*8)return false;
        var frame=TerrainFaceConnections.frame(q.getDirection());double u=frame.x(point),v=frame.y(point),sign=0;
        for(int i=0;i<p.size();i++){Vertex a=p.get(i),b=p.get((i+1)%p.size());double cross=(b.u-a.u)*(v-a.v)-(b.v-a.v)*(u-a.u);if(Math.abs(cross)<EPS)continue;if(sign!=0&&sign*cross<0)return false;sign=cross;}
        return sign!=0;
    }

    private static boolean coplanar(BakedQuad a,BakedQuad b){
        if(a.getDirection()!=b.getDirection())return false;
        Vec3 n=normal(a);return Math.abs(Math.abs(n.dot(normal(b)))-1)<EPS&&Math.abs(position(a,0).subtract(position(b,0)).dot(n))<EPS;
    }
    private static Vec3 position(BakedQuad q,int i){int[] d=q.getVertices();int at=i*d.length/4;return new Vec3(Float.intBitsToFloat(d[at]),Float.intBitsToFloat(d[at+1]),Float.intBitsToFloat(d[at+2]));}
    private static Vec3 normal(BakedQuad q){for(int i=1;i<3;i++){Vec3 n=position(q,i).subtract(position(q,0)).cross(position(q,i+1).subtract(position(q,0)));if(n.lengthSqr()>EPS*EPS)return n.normalize();}return Vec3.atLowerCornerOf(q.getDirection().getNormal());}
    private static List<Vertex> vertices(BakedQuad q,TerrainFaceConnections.Frame frame){List<Vertex> r=new ArrayList<>();for(int i=0;i<4;i++){Vec3 p=position(q,i);if(r.isEmpty()||com.stardew.craft.port.PortJava.getLast(r).p.distanceToSqr(p)>EPS*EPS)r.add(new Vertex(p,frame.x(p),frame.y(p)));}return r;}
    private static double area(List<Vertex> p){double a=0;for(int i=0;i<p.size();i++){Vertex b=p.get(i),c=p.get((i+1)%p.size());a+=b.u*c.v-c.u*b.v;}return Math.abs(a)/2;}
    private static List<Vertex> rect(List<Vertex> p,double x0,double y0,double x1,double y1){p=clip(p,x0,true,true);p=clip(p,x1,true,false);p=clip(p,y0,false,true);return clip(p,y1,false,false);}
    private static List<Vertex> clip(List<Vertex> p,double edge,boolean u,boolean lower){
        List<Vertex> out=new ArrayList<>();if(p.isEmpty())return out;
        Vertex a=com.stardew.craft.port.PortJava.getLast(p);double da=((u?a.u:a.v)-edge)*(lower?1:-1);
        for(Vertex b:p){double db=((u?b.u:b.v)-edge)*(lower?1:-1);if((da>=-EPS)!=(db>=-EPS))out.add(a.mix(b,da/(da-db)));if(db>=-EPS)out.add(b);a=b;da=db;}return out;
    }
    private static void emit(List<BakedQuad> out,BakedQuad target,BakedQuad source,List<Vertex> p){
        if(p.size()<=4){out.add(bake(target,source,p));return;}
        for(int i=1;i<p.size()-1;i++)out.add(bake(target,source,List.of(com.stardew.craft.port.PortJava.getFirst(p),p.get(i),p.get(i+1))));
    }
    private static BakedQuad bake(BakedQuad target,BakedQuad source,List<Vertex> p){
        int[] data=target.getVertices().clone();int stride=data.length/4;
        for(int i=0;i<4;i++){Vertex v=p.get(Math.min(i,p.size()-1));int at=i*stride;data[at]=Float.floatToRawIntBits((float)v.p.x);data[at+1]=Float.floatToRawIntBits((float)v.p.y);data[at+2]=Float.floatToRawIntBits((float)v.p.z);float[] uv=uv(source,v.u,v.v);data[at+4]=Float.floatToRawIntBits(uv[0]);data[at+5]=Float.floatToRawIntBits(uv[1]);}
        return new BakedQuad(data,source.getTintIndex(),target.getDirection(),source.getSprite(),target.isShade()&&source.isShade(),target.hasAmbientOcclusion()&&source.hasAmbientOcclusion());
    }
    private static float[] uv(BakedQuad source,double u,double v){
        List<Vertex> p=vertices(source,TerrainFaceConnections.frame(source.getDirection()));int[] data=source.getVertices();int stride=data.length/4;
        for(int i=1;i<p.size()-1;i++){Vertex a=com.stardew.craft.port.PortJava.getFirst(p),b=p.get(i),c=p.get(i+1);double det=(b.u-a.u)*(c.v-a.v)-(c.u-a.u)*(b.v-a.v);if(Math.abs(det)<EPS)continue;double s=((u-a.u)*(c.v-a.v)-(c.u-a.u)*(v-a.v))/det,t=((b.u-a.u)*(v-a.v)-(u-a.u)*(b.v-a.v))/det;float[] out=new float[2];for(int j=0;j<2;j++){float av=Float.intBitsToFloat(data[4+j]),bv=Float.intBitsToFloat(data[i*stride+4+j]),cv=Float.intBitsToFloat(data[(i+1)*stride+4+j]);out[j]=(float)(av+(bv-av)*s+(cv-av)*t);}return out;}
        return new float[]{com.stardew.craft.port.PortSprites.getU(source.getSprite(), (float)u),com.stardew.craft.port.PortSprites.getV(source.getSprite(), (float)v)};
    }
}
