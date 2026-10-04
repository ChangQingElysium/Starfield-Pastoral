import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.npc.attention.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Inspect final triangles, not just the rig or the inputs used to construct the garment. */
public final class NativeClothChecks {
    static int bone(NativeNpcModel m,String name) {
        for(int i=0;i<m.bones().size();i++)if(m.bones().get(i).name().equals(name))return i;
        throw new AssertionError(name);
    }
    static float hit(Vector3f a,Vector3f b,Vector3f c,float x,float y) {
        float denominator=(b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y);
        if(Math.abs(denominator)<1e-6)return Float.NaN;
        float u=((b.y-c.y)*(x-c.x)+(c.x-b.x)*(y-c.y))/denominator;
        float v=((c.y-a.y)*(x-c.x)+(a.x-c.x)*(y-c.y))/denominator;
        if(u<-.0001||v<-.0001||u+v>1.0001)return Float.NaN;
        return u*a.z+v*b.z+(1-u-v)*c.z;
    }
    static int checked;
    static String coveragePart;
    static float hemInset,attachmentInset;
    static void inspect(NativeNpcModel m,NativeNpcPose p,String label) {
        var matrices=p.matrices();var cloth=p.clothVertices();var settings=m.profile().cloth();
        int owner=bone(m,settings.bone()),r=bone(m,"leg_right"),l=bone(m,"leg_left");
        Matrix4f inverse=new Matrix4f(matrices[owner]).invert();
        List<Vector3f[]> surface=new ArrayList<>();List<float[]> normals=new ArrayList<>();Map<String,Vector3f> joined=new HashMap<>();
        for(int qi=0;qi<cloth.length;qi++)if(cloth[qi]!=null) {
            Vector3f[] q=new Vector3f[4];
            for(int vi=0;vi<4;vi++) {
                var original=m.quads().get(qi).vertices()[vi];var v=new Vector3f(cloth[qi][vi]);
                if(!v.isFinite())throw new AssertionError("Nonfinite cloth "+label);
                String key=String.format(java.util.Locale.ROOT,"%.4f/%.4f/%.4f",original[0],original[1],original[2]);
                var prior=joined.putIfAbsent(key,new Vector3f(v));
                if(prior!=null&&prior.distance(v)>.0001)throw new AssertionError("Cloth seam "+label);
                q[vi]=inverse.transformPosition(v);
                if(original[1]>=settings.anchorY() && q[vi].distance(new Vector3f(original[0],original[1],original[2]))>.0001)
                    throw new AssertionError("Detached cloth attachment "+label);
            }
            if (coveragePart==null || coveragePart.equals(m.quads().get(qi).sourcePart())) {
                surface.add(q);normals.add(m.quads().get(qi).normal());
            }
        }
        if(m.clips().containsKey("animation.marnie.walk")) {
            // A skirt can be attached to its own bone yet leave a visible gap beneath
            // the bodice. Require real overlap with the separately breathing upper dress.
            var bodice=m.quads().stream().filter(q->q.sourcePart().equals("bodice")).toList();
            float bottom=Float.POSITIVE_INFINITY;
            for(var q:bodice)for(var v:q.vertices()) {
                var relative=new Matrix4f(inverse).mul(matrices[q.bone()]);
                bottom=Math.min(bottom,relative.transformPosition(new Vector3f(v[0],v[1],v[2])).y);
            }
            if(settings.anchorY()-bottom<.5F)throw new AssertionError("Skirt/bodice waist has no overlap "+label);
        }
        // Cast through actual final cloth triangles at points along each posed leg's edges.
        // Stay above the hem and below the waist overlap, where the garment must cover the legs.
        boolean skirt=settings.kind().equals("skirt");
        var hipSurface=p.surfaceVertices(matrices);
        for(int qi=0;qi<m.quads().size();qi++)if(p.isLegBone(m.quads().get(qi).bone())) {
            var q=m.quads().get(qi);
            Matrix4f relative=new Matrix4f(inverse).mul(matrices[q.bone()]);
            for(int edge=0;edge<4;edge++)for(int sample=0;sample<5;sample++) {
                var a=q.vertices()[edge];var b=q.vertices()[(edge+1)%4];float t=sample/4F;
                Vector3f v;
                if(NativeNpcCloth.hipBound(m,q)) {
                    var av=hipSurface[qi][edge];var bv=hipSurface[qi][(edge+1)%4];
                    v=inverse.transformPosition(new Vector3f(av[0]+(bv[0]-av[0])*t,av[1]+(bv[1]-av[1])*t,av[2]+(bv[2]-av[2])*t));
                } else v=relative.transformPosition(new Vector3f(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t));
                if(v.y<settings.hemY()+hemInset || v.y>Math.min(12.8,settings.anchorY()-attachmentInset))continue;
                float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                for(var face:surface)for(int tri=0;tri<2;tri++) {
                    float z=hit(face[0],face[tri+1],face[tri+2],v.x,v.y);
                    if(Float.isFinite(z)){min=Math.min(min,z);max=Math.max(max,z);}
                }
                checked++;
                if(skirt && (v.z<min-.08 || v.z>max+.08))
                    throw new AssertionError("Leg outside continuous skirt "+label+" "+v+" bounds="+min+","+max);
                // An open cape does not cover every lateral leg point; only actual overlapping cloth can collide.
                if(settings.kind().equals("apron") && Float.isFinite(max) && v.z<max-.08)
                    throw new AssertionError("Leg passes front apron "+label+" "+v+" front="+max);
                if(settings.kind().equals("cape") && Float.isFinite(min) && v.z>min+.08)
                    throw new AssertionError("Leg passes robe "+label+" "+v+" back="+min);
                if(settings.kind().equals("mantle"))for(int faceIndex=0;faceIndex<surface.size();faceIndex++) {
                    var face=surface.get(faceIndex);var normal=normals.get(faceIndex);
                    for(int tri=0;tri<2;tri++) {
                        float z=hit(face[0],face[tri+1],face[tri+2],v.x,v.y);
                        if(Float.isFinite(z) && ((normal[2]<-.5 && v.z<z-.08) || (normal[2]>.5 && v.z>z+.08)))
                            throw new AssertionError("Leg passes mantle panel "+label+" "+v);
                        if(Math.abs(normal[0])>.5) {
                            float x=hit(new Vector3f(face[0].z,face[0].y,face[0].x),
                                    new Vector3f(face[tri+1].z,face[tri+1].y,face[tri+1].x),
                                    new Vector3f(face[tri+2].z,face[tri+2].y,face[tri+2].x),v.z,v.y);
                            if(Float.isFinite(x) && (normal[0]>0?v.x>x+.08:v.x<x-.08))
                                throw new AssertionError("Leg passes mantle side "+label+" "+v);
                        }
                    }
                }
            }
        }
    }
    public static void verify(NativeNpcModel m,String id) {
        coveragePart=m.profile().cloth().clearancePart();
        boolean shortGarment=m.profile().cloth().anchorY()-m.profile().cloth().hemY()<=4;
        hemInset=shortGarment || coveragePart!=null || m.profile().cloth().kind().equals("apron")?.12F:1.3F;
        // Long cardigans cover the upper thigh too; do not exempt the former waist-overlap band.
        attachmentInset=shortGarment || id.equals("evelyn") || m.profile().cloth().kind().equals("apron")?.1F:2F;
        checked=0;var p=new NativeNpcPose(m);var rig=m.profile().attentionRig();
        if(Set.of("skirt","mantle").contains(m.profile().cloth().kind())) {
            // Remove leg surfaces for a true no-contact case. Translating thigh
            // bones does not remove pants sewn to the pelvis by hip skinning.
            var bare=new NativeNpcModel(m.version(),m.texture(),m.bones(),
                    m.quads().stream().filter(q->!p.isLegBone(q.bone())).toList(),m.clips(),m.profile());
            var barePose=new NativeNpcPose(bare);
            var cloth=barePose.clothVertices();var transforms=barePose.matrices();
            for(int q=0;q<cloth.length;q++)if(cloth[q]!=null)for(int v=0;v<4;v++) {
                var rest=bare.quads().get(q).vertices()[v];
                var expected=transforms[bare.quads().get(q).bone()].transformPosition(new Vector3f(rest[0],rest[1],rest[2]));
                if(expected.distance(new Vector3f(cloth[q][v]))>.0001)
                    throw new AssertionError("Skirt expands without contact");
            }
        }
        // Resolve rapid contact changes before comparing full and half time steps.
        // Coarse 1/120 samples can mistake continuous acceleration for a positional pop.
        final int samples=480;
        float[][][] first=null,previous=null;float jump=0,midpointError=0,halfStep=0;String worst="";
        for(int i=0;i<=samples;i++) {
            p.reset();p.apply("animation."+id+".idle",0);p.blend("animation."+id+".walk",i/(double)samples,1);p.groundFeet(m.profile().groundOffset());
            var surface=p.clothVertices();float[][][] copy=new float[surface.length][][];
            for(int q=0;q<surface.length;q++)if(surface[q]!=null) {
                copy[q]=new float[4][3];
                for(int v=0;v<4;v++) {
                    copy[q][v]=surface[q][v].clone();
                    if(previous!=null)jump=Math.max(jump,new Vector3f(copy[q][v]).distance(new Vector3f(previous[q][v])));
                    if(i==samples&&new Vector3f(copy[q][v]).distance(new Vector3f(first[q][v]))>.0001)
                        throw new AssertionError("Garment loop seam");
                }
            }
            if(previous!=null) {
                p.reset();p.apply("animation."+id+".idle",0);p.blend("animation."+id+".walk",(i-.5)/samples,1);p.groundFeet(m.profile().groundOffset());
                var middle=p.clothVertices();
                for(int q=0;q<copy.length;q++)if(copy[q]!=null)for(int v=0;v<4;v++) {
                    var mid=new Vector3f(middle[q][v]);
                    float error=mid.distance(new Vector3f(previous[q][v]).lerp(new Vector3f(copy[q][v]),.5F));
                    if(error>midpointError){midpointError=error;worst=" frame="+i+" quad="+q+" vertex="+v+" positions="+new Vector3f(previous[q][v])+" / "+mid+" / "+new Vector3f(copy[q][v]);}
                    halfStep=Math.max(halfStep,Math.max(mid.distance(new Vector3f(previous[q][v])),mid.distance(new Vector3f(copy[q][v]))));
                }
            }
            if(i==0)first=copy;previous=copy;
        }
        // Garment tips can travel faster than the torso. Halving dt must halve displacement;
        // a positional pop persists at the finer rate and fails this independent continuity check.
        if(midpointError>.05 || halfStep>jump*.6+.001)
            throw new AssertionError("Garment internal discontinuity: midpoint="+midpointError+", steps="+jump+" / "+halfStep+worst);
        for(int i=0;i<60;i++) {
            double t=i/60.;p.reset();p.apply("animation."+id+".idle",t);p.blend("animation."+id+".walk",t,1);p.groundFeet(m.profile().groundOffset());
            inspect(m,p,"walk "+t);
        }
        for(int i=0;i<12;i++)for(double w:new double[]{0,.25,.5,.75,1}) {
            p.reset();p.apply("animation."+id+".idle",i*.7);p.blend("animation."+id+".walk",i/12.,w);p.groundFeet(m.profile().groundOffset());
            inspect(m,p,"blend "+i+"/"+w);
        }
        for(double yaw:new double[]{-180,-90,50,130,180})for(int i=0;i<24;i++) {
            double t=i*NpcAttentionMotion.duration(yaw)/24;p.reset();p.apply("animation."+id+".idle",t);
            NativeSamAttentionPose.apply(p,NpcAttentionMotion.sample(t,yaw,8,48,rig),1,rig);p.groundFeet(m.profile().groundOffset());
            inspect(m,p,"turn "+yaw+"/"+t);
        }
        for(double yaw:new double[]{-130,50,180})for(double t:new double[]{.4,.7,1,2.6})for(double weight:new double[]{0,.25,.5,.75,1}) {
            p.reset();p.apply("animation."+id+".idle",t);
            NativeSamAttentionPose.apply(p,NpcAttentionMotion.sample(t,yaw,0,48,rig),weight,rig);
            p.groundFeet(m.profile().groundOffset());inspect(m,p,"attention fade "+yaw+"/"+t+"/"+weight);
        }
        for(double yaw:new double[]{-180,-90,50,130,180})for(int i=0;i<=40;i++) {
            double age=i*.2;double motion=NpcDialogueMotion.motionTime(age,yaw,4);
            p.reset();p.apply("animation."+id+".idle",age);
            NativeSamAttentionPose.apply(p,NpcAttentionMotion.sample(motion,yaw,8,48,rig),1,rig);
            p.groundFeet(m.profile().groundOffset());inspect(m,p,"dialogue "+yaw+"/"+age);
        }
        System.out.println("PASS "+id+" cloth: continuous surface/attachments, 1/480 and 1/960 vertex changes="+jump+" / "+halfStep+", midpoint error="+midpointError+", "+checked+" posed leg probes during walk, blends, turns, attention fades and dialogue return");
    }
}
