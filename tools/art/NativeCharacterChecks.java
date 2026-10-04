import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.*;
import com.stardew.craft.npc.attention.*;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Additional production-rig checks; existing Sam regression checks remain independent. */
public final class NativeCharacterChecks {
    static String ID;
    static String probeArm = "arm_left";
    static com.google.gson.JsonObject gait;
    static double setting(String key,double fallback) { return gait!=null && gait.has(key)?gait.get(key).getAsDouble():fallback; }
    static boolean levelWalk() { return gait!=null && gait.has("levelWalk") && gait.get("levelWalk").getAsBoolean(); }
    static boolean characterGait() { return gait!=null && gait.has("revision") && gait.get("revision").getAsString().startsWith("character-articulated-"); }
    static void near(double a,double b,double e,String label) {
        if(!Double.isFinite(a) || Math.abs(a-b)>e)throw new AssertionError(label+": "+a+" != "+b);
    }
    static int bone(NativeNpcModel m,String n) {
        for(int i=0;i<m.bones().size();i++)if(m.bones().get(i).name().equals(n))return i;
        throw new AssertionError(n);
    }
    static List<float[]> matrices(NativeNpcPose p) {
        return Arrays.stream(p.matrices()).map(m->m.get(new float[16])).toList();
    }
    static Vector3f point(NativeNpcPose p,int b,float x,float y,float z) {
        return p.matrices()[b].transformPosition(new Vector3f(x,y,z));
    }
    static double sole(NativeNpcModel m,NativeNpcPose p,int b) {
        double y=Double.POSITIVE_INFINITY;
        for(var q:m.quads())if(descendsFrom(m,q.bone(),b)&&p.isContactBone(q.bone()))for(var v:q.vertices())y=Math.min(y,point(p,q.bone(),v[0],v[1],v[2]).y);
        return y+m.profile().groundOffset();
    }
    static void fixedEyes(NativeNpcModel m,NativeNpcPose p) {
        var head=p.matrices()[bone(m,"head")];
        near(head.getScale(new Vector3f()).y,1,1e-5,"face unscaled");
        for(String n:List.of("eye_left","eye_right","pupil_left","pupil_right","iris_left","iris_right","eyelashes_left","eyelashes_right")) {
            // Some approved faces (Abigail) are painted directly on the head, without separate eye bones.
            if(m.bones().stream().noneMatch(b->b.name().equals(n)))continue;
            float[] a=p.matrices()[bone(m,n)].get(new float[16]), b=head.get(new float[16]);
            for(int i=0;i<16;i++)near(a[i],b[i],1e-5,"fixed eye/eyebrow "+n);
        }
    }
    static void marlonEyelid(NativeNpcModel m) {
        var p=new NativeNpcPose(m);
        for(double time:new double[]{-1,0,.07,.23}) {
            p.reset();p.apply("animation.marlon.idle",0);
            if(time>=0)p.apply("animation.marlon.blink",time);
            var inverse=new org.joml.Matrix4f(p.boneMatrix("head")).invert();
            var mask=m.quads().stream().filter(q->q.sourcePart().equals("mask_left")).findFirst().orElseThrow();
            var transform=new org.joml.Matrix4f(inverse).mul(p.matrices()[mask.bone()]);
            var bounds=new Vector3f[]{new Vector3f(Float.POSITIVE_INFINITY),new Vector3f(Float.NEGATIVE_INFINITY)};
            for(var v:mask.vertices()) {
                var point=transform.transformPosition(new Vector3f(v[0],v[1],v[2]));bounds[0].min(point);bounds[1].max(point);
            }
            boolean closed=time==.07;
            for(var q:m.quads())if(Set.of("eye_right_white","eye_right_iris").contains(q.sourcePart()))for(var v:q.vertices()) {
                var eye=new org.joml.Matrix4f(inverse).mul(p.matrices()[q.bone()]).transformPosition(new Vector3f(v[0],v[1],v[2]));
                if(closed) {
                    if(bounds[1].z>=eye.z || eye.x<bounds[0].x-.001 || eye.x>bounds[1].x+.001 || eye.y<bounds[0].y-.001 || eye.y>bounds[1].y+.001)
                        throw new AssertionError("Marlon closed eyelid leaves eye exposed");
                } else if(bounds[0].z<=eye.z)throw new AssertionError("Marlon open eyelid hides eye white");
            }
        }
    }
    static void guntherEyelids(NativeNpcModel m) {
        var p=new NativeNpcPose(m);
        for(double time:new double[]{-1,0,.065,.09,.23}) {
            p.reset();p.apply("animation.gunther.idle",0);
            if(time>=0)p.apply("animation.gunther.blink",time);
            var inverse=new org.joml.Matrix4f(p.boneMatrix("head")).invert();
            for(String side:List.of("left","right")) {
                var mask=m.quads().stream().filter(q->q.sourcePart().equals("mask_"+side)).findFirst().orElseThrow();
                var transform=new org.joml.Matrix4f(inverse).mul(p.matrices()[mask.bone()]);
                var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
                for(var v:mask.vertices()) {var point=transform.transformPosition(new Vector3f(v[0],v[1],v[2]));lo.min(point);hi.max(point);}
                boolean closed=time==.065 || time==.09;
                for(var q:m.quads())if(q.bone()==bone(m,"eye_"+side))for(var v:q.vertices()) {
                    var eye=new org.joml.Matrix4f(inverse).mul(p.matrices()[q.bone()]).transformPosition(new Vector3f(v[0],v[1],v[2]));
                    if(closed) {
                        if(hi.z>=eye.z || eye.x<lo.x-.001 || eye.x>hi.x+.001 || eye.y<lo.y-.001 || eye.y>hi.y+.001)
                            throw new AssertionError("Gunther closed eyelid leaves eye exposed");
                    } else if(lo.z<=eye.z)throw new AssertionError("Gunther open eyelid covers eye");
                }
            }
        }
        for(var q:m.quads())if(q.sourcePart().startsWith("glasses_") && !q.translucent())
            throw new AssertionError("Gunther glasses lost their translucent pass");
    }
    static Vector3f handPoint(NativeNpcModel m) {
        int arm=bone(m,probeArm);
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
        float minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(var q:m.quads())if(descendsFrom(m,q.bone(),arm))for(var v:q.vertices()) {
            minX=Math.min(minX,v[0]);maxX=Math.max(maxX,v[0]);minY=Math.min(minY,v[1]);
            minZ=Math.min(minZ,v[2]);maxZ=Math.max(maxZ,v[2]);
        }
        return new Vector3f((minX+maxX)/2,minY,(minZ+maxZ)/2);
    }
    static boolean descendsFrom(NativeNpcModel m,int child,int ancestor) {
        while(child>=0) { if(child==ancestor)return true;child=m.bones().get(child).parent(); }
        return false;
    }
    static double armReach(NativeNpcModel m) {
        return handPoint(m).distance(new Vector3f(m.bones().get(bone(m,probeArm)).origin()));
    }
    static double handTravel(NativeNpcModel m,String id) {
        var p=new NativeNpcPose(m);int hand=bone(m,probeArm);String clip="animation."+id+".idle";
        var h=handPoint(m);p.apply(clip,0);var first=point(p,hand,h.x,h.y,h.z);double travel=0;
        for(int i=0;i<=Math.ceil(m.clips().get(clip).length()*60);i++) {
            p.reset();p.apply(clip,i/60.0);travel=Math.max(travel,point(p,hand,h.x,h.y,h.z).distance(first));
        }
        return travel;
    }
    static double bodyTravel(NativeNpcModel m,String id) {
        var p=new NativeNpcPose(m);int body=bone(m,"body");double min=99,max=-99;
        for(int i=0;i<=480;i++) {
            double t=i/240.0;p.reset();p.apply("animation."+id+".idle",t);p.blend("animation."+id+".walk",t,1);p.groundFeet(m.profile().groundOffset());
            double y=point(p,body,0,m.bones().get(body).origin()[1],0).y;min=Math.min(min,y);max=Math.max(max,y);
        }
        return max-min;
    }
    static double span(NativeNpcModel m,String id,String clip,String name,String channel,int axis) {
        var track=m.clips().get("animation."+id+"."+clip).tracks().stream()
                .filter(t->t.bone()==bone(m,name)&&t.channel().equals(channel)).findFirst().orElseThrow();
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(var k:track.keys())for(float v:new float[]{k.before()[axis],k.after()[axis]}) {min=Math.min(min,v);max=Math.max(max,v);}
        return max-min;
    }
    static void matchesSam(double actual,double reference,String label) {
        // Current same-scale human batch: preserve the approved visible amplitude, with sampling tolerance.
        if(actual<reference*.98 || actual>reference*1.10)throw new AssertionError(label+" differs from approved Sam: "+actual+" vs "+reference);
    }
    static void compareSamChannels(NativeNpcModel m,NativeNpcModel sam) {
        for(String entry:List.of("idle/body/position/1","idle/body/rotation/0","idle/body/rotation/2",
                "idle/arm_right/position/1","idle/arm_left/position/1",
                "idle/arm_right/rotation/2","idle/arm_left/rotation/2",
                "idle/chest_breath/scale/1","idle/chest_breath/scale/2")) {
            String[] a=entry.split("/");int axis=Integer.parseInt(a[3]);
            matchesSam(span(m,ID,a[0],a[1],a[2],axis),span(sam,"sam",a[0],a[1],a[2],axis),entry);
        }
        for(String entry:List.of("root/position/0","root/position/1","root/rotation/1",
                "body/rotation/0","body/rotation/1","body/rotation/2",
                "head/rotation/0","head/rotation/1","head/rotation/2",
                "arm_right/rotation/0","arm_left/rotation/0")) {
            String[] a=entry.split("/");int axis=Integer.parseInt(a[2]);
            matchesSam(span(m,ID,"walk",a[0],a[1],axis),span(sam,"sam","walk",a[0],a[1],axis),"walk "+entry);
        }
        double proportionalStride=21*m.bones().get(bone(m,"leg_right")).origin()[1]/12/16;
        if(m.profile().walkStride()<proportionalStride*.9 || m.profile().walkStride()>proportionalStride*1.001)
            throw new AssertionError("Stride must fit actual leg length: "+m.profile().walkStride());
        for(String side:List.of("right","left")) {
            if(span(m,ID,"walk","shin_"+side,"rotation",0)<35)
                throw new AssertionError("Missing knee recovery "+side);
            near(span(m,ID,"walk","leg_"+side,"position",0),0,1e-6,"anchored hip x");
            near(span(m,ID,"walk","leg_"+side,"position",1),0,1e-6,"anchored hip y");
        }
    }
    static void articulatedContacts(NativeNpcModel m) {
        var p=new NativeNpcPose(m);String clip="animation."+ID+".walk";
        double stride=m.profile().walkStride()*16,stance=setting("stance",.55);
        for(String side:List.of("right","left")) {
            int foot=bone(m,"foot_"+side),leg=bone(m,"leg_"+side);
            float sole=Float.POSITIVE_INFINITY,heel=-99,toe=99;
            for(var q:m.quads())if(q.bone()==foot)for(var v:q.vertices())sole=Math.min(sole,v[1]);
            for(var q:m.quads())if(q.bone()==foot)for(var v:q.vertices())if(Math.abs(v[1]-sole)<.0001){heel=Math.max(heel,v[2]);toe=Math.min(toe,v[2]);}
            double stableX=Double.NaN;
            for(int i=0;i<720;i++) {
                double phase=i/720.,t=phase+(side.equals("left")?.5:0);p.reset();p.apply(clip,t);
                var hip=new Vector3f(m.bones().get(leg).origin());
                near(p.boneMatrix("leg_"+side).transformPosition(new Vector3f(hip)).distance(p.boneMatrix("root").transformPosition(new Vector3f(hip))),0,.0001,"attached hip");
                var relative=new org.joml.Matrix4f(p.boneMatrix("leg_"+side)).invert().mul(p.boneMatrix("shin_"+side));
                double bend=Math.toDegrees(Math.atan2(relative.m12(),relative.m22()));
                if(bend>0.001 || bend< (levelWalk()?-50:-85))throw new AssertionError("Unsafe knee bend "+bend);
                near(p.boneMatrix("shin_"+side).getScale(new Vector3f()).y,1,.00001,"rigid shin");
                if(phase>stance)continue;
                float z=phase<.12?heel:toe;
                var contact=point(p,foot,hip.x,sole,z);
                if(Double.isNaN(stableX))stableX=contact.x;
                near(contact.x,stableX,.002,"stance lateral anchor");
                near(contact.y,-m.profile().groundOffset(),.003,"stance heel/toe contact");
                near(contact.z,z+stride*(phase-stance/2),.002,"stance distance without skating");
            }
        }
    }
    public static void main(String[] args)throws Exception {
        ID=Path.of(args[0]).getFileName().toString().replace(".json", "");
        var gson=new Gson();var m=gson.fromJson(Files.readString(Path.of(args[0])),NativeNpcModel.class);
        gait=com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject().getAsJsonObject("profile").getAsJsonObject("gait");
        // A shoulder mantle can constrain one arm. Measure readability on the free arm,
        // while checking both authored walk amplitudes independently below.
        if(setting("armScaleLeft",1)<setting("armScaleRight",1)) probeArm="arm_right";
        var p=new NativeNpcPose(m);var rig=m.profile().attentionRig();
        if(ID.equals("marlon"))marlonEyelid(m);
        if(ID.equals("gunther"))guntherEyelids(m);
        int r=bone(m,"leg_right"),l=bone(m,"leg_left"),body=bone(m,"body"),hand=bone(m,probeArm);
        near(m.bones().get(r).origin()[1],rig.hipHeight(),1e-6,"measured hip");
        p.reset();near(sole(m,p,r),0,1e-6,"inflated sole baseline");
        var h=handPoint(m);p.apply("animation."+ID+".idle",0);var start=point(p,hand,h.x,h.y,h.z);double travel=0;
        for(int i=0;i<=Math.ceil(m.clips().get("animation."+ID+".idle").length()*60);i++) {
            double t=i/60.0;p.reset();p.apply("animation."+ID+".idle",t);
            near(sole(m,p,r),0,1e-5,"idle right grounded");near(sole(m,p,l),0,1e-5,"idle left grounded");
            fixedEyes(m,p);travel=Math.max(travel,point(p,hand,h.x,h.y,h.z).distance(start));
        }

        double min=99,max=-99,prev=Double.NaN,jump=0;
        for(int i=0;i<=480;i++) {
            double t=i/240.0;p.reset();p.apply("animation."+ID+".idle",t);p.blend("animation."+ID+".walk",t,1);p.groundFeet(m.profile().groundOffset());
            double y=point(p,body,0,m.bones().get(body).origin()[1],0).y;min=Math.min(min,y);max=Math.max(max,y);
            if(Double.isFinite(prev))jump=Math.max(jump,Math.abs(y-prev));prev=y;
            near(Math.min(sole(m,p,r),sole(m,p,l)),0,.001,"walking support contact at "+t);fixedEyes(m,p);
        }
        if(jump>.05)throw new AssertionError("Walk internal height jump "+jump);
        var sam=gson.fromJson(Files.readString(Path.of(args[0]).resolveSibling("sam.json")),NativeNpcModel.class);
        if(characterGait()) {
            near(span(m,ID,"walk","root","position",1),2*setting("bob",0),.002,"character vertical travel");
            near(span(m,ID,"walk","root","position",0),2*setting("sway",0),.002,"character lateral transfer");
            for(String side:List.of("left","right")) {
                String scale=side.equals("left")?"armScaleLeft":"armScaleRight";
                near(span(m,ID,"walk","arm_"+side,"rotation",0),2*setting("armSwing",0)*setting(scale,1),.01,"character arm swing "+side);
            }
            double heightScale=rig.hipHeight()<=5?rig.hipHeight()/10.0:1;
            double lower=levelWalk()?(rig.hipHeight()<=5?.10:.30):1.2*heightScale;
            double upper=levelWalk()?(rig.hipHeight()<=5?.40:.90):1.9*heightScale;
            if(max-min<lower || max-min>upper || travel<armReach(m)*.07)
                throw new AssertionError("Unreadable/excessive whole-body motion: "+(max-min)+" hand="+travel);
            for(String side:List.of("right","left")) {
                near(span(m,ID,"walk","leg_"+side,"position",0),0,.00001,"anchored hip x");
                near(span(m,ID,"walk","leg_"+side,"position",1),0,.00001,"anchored hip y");
                if(span(m,ID,"walk","shin_"+side,"rotation",0)<(levelWalk()?15:25))throw new AssertionError("No knee recovery");
            }
        } else {
            double samHand=handTravel(sam,"sam"),reachRatio=Math.max(1,armReach(m)/armReach(sam));
            if(travel<samHand*.98 || travel>samHand*1.10*reachRatio)
                throw new AssertionError("Anatomical idle hand travel: "+travel+" vs Sam "+samHand+", reach ratio "+reachRatio);
            matchesSam(max-min,bodyTravel(sam,"sam"),"Walk body excursion");
            compareSamChannels(m,sam);
        }
        articulatedContacts(m);
        for(double yaw:new double[]{-180,-130,-90,-50,-20,0,20,50,90,130,180}) {
            Vector3f[] planted=new Vector3f[2];
            for(int i=0;i<=Math.ceil(NpcAttentionMotion.duration(yaw)*120);i++) {
                double t=i/120.0;var s=NpcAttentionMotion.sample(t,yaw,12,32,rig);
                p.reset();p.apply("animation."+ID+".idle",t);NativeSamAttentionPose.apply(p,s,1,rig);
                fixedEyes(m,p);
                for(int side=0;side<2;side++) {
                    var f=side==0?s.right():s.left();
                    var toe=point(p,side==0?r:l,m.bones().get(side==0?r:l).origin()[0],(float)rig.soleY(),(float)-rig.toeDepth());
                    near(toe.x,f.toeX(),1e-4,"turn toe x");near(toe.z,f.toeZ(),1e-4,"turn toe z");near(toe.y,f.lift()+rig.soleY(),1e-4,"turn toe height");
                    if(f.lift()<1e-9) {if(planted[side]!=null)near(toe.distance(planted[side]),0,1e-4,"turn planted foot");planted[side]=new Vector3f(toe);}else planted[side]=null;
                }
                if(Math.abs(s.headYaw())>48.001)throw new AssertionError("Head limit");
            }
            near(NpcDialogueMotion.motionTime(120,yaw,-1),NpcDialogueMotion.readyTime(yaw),1e-9,"long chat hold");
            double ready=NpcDialogueMotion.motionTime(4,yaw,4), after=NpcDialogueMotion.motionTime(4.00001,yaw,4);
            var a=NpcAttentionMotion.sample(ready,yaw,0,32,rig);var b=NpcAttentionMotion.sample(after,yaw,0,32,rig);
            near(a.bodyYaw(),b.bodyYaw(),.001,"chat release continuity");
            var end=NpcAttentionMotion.sample(NpcDialogueMotion.motionTime(10,yaw,4),yaw,0,32,rig);
            near(end.bodyYaw(),0,1e-8,"chat returns home");
        }
        for(double yaw:new double[]{-130,50,180})for(double t:new double[]{.4,.7,1.0,2.6})for(int i=0;i<=20;i++) {
            p.reset();p.apply("animation."+ID+".idle",t);
            NativeSamAttentionPose.apply(p,NpcAttentionMotion.sample(t,yaw,0,32,rig),i/20.0,rig);
            p.groundFeet(m.profile().groundOffset());
            if(Math.min(sole(m,p,r),sole(m,p,l))<-.0001)throw new AssertionError("Attention fade underground");
            fixedEyes(m,p);
        }
        for(String name:List.of("idle","walk")) {
            var clip=m.clips().get("animation."+ID+"."+name);
            p.reset();p.apply("animation."+ID+"."+name,0);var a=matrices(p);
            p.reset();p.apply("animation."+ID+"."+name,clip.length());var b=matrices(p);
            for(int i=0;i<a.size();i++)for(int j=0;j<16;j++)near(a.get(i)[j],b.get(i)[j],1e-6,"loop all bones");
        }
        for(int phase=0;phase<12;phase++)for(int i=0;i<=40;i++) {
            p.reset();p.apply("animation."+ID+".idle",i/60.0);p.blend("animation."+ID+".walk",phase/12.0,i/40.0);p.groundFeet(m.profile().groundOffset());
            if(Math.min(sole(m,p,r),sole(m,p,l))<-.0001)throw new AssertionError("Blend underground");
        }
        System.out.println("PASS "+ID+": fixed face/eyes, idle hand travel="+travel+", walk body excursion="+(max-min)+", max 1/240-cycle jump="+jump+", 11 turns with planted toes, dialogue hold/return, 492 blends");
        if(m.profile().cloth()!=null)NativeClothChecks.verify(m,ID);
        if(ID.equals("leah"))NativeHairChecks.verifyLeah(m);
        if(ID.equals("caroline"))NativeHairChecks.verify(m,ID);
        if(ID.equals("marnie"))NativeHairChecks.verifyMarnie(m);
        if(args.length>1)export(m,Path.of(args[1]),gson);
    }
    static void export(NativeNpcModel m,Path out,Gson gson)throws Exception {
        var p=new NativeNpcPose(m);var rig=m.profile().attentionRig();
        Map<String,Object> result=new LinkedHashMap<>();result.put("fps",25);
        List<Map<String,Object>> parity=new ArrayList<>();
        for(String name:List.of("idle","walk","blink"))for(int i=0;i<41;i++) {
            var clip=m.clips().get("animation."+ID+"."+name);
            if(clip==null && name.equals("blink") && !m.profile().visibleBlink())continue;
            double t=clip.length()*(i+.37)/41;
            // BB snaps within roughly 1/1200 s of saved keys; compare interpolation away from that editor-only zone.
            if(clip.tracks().stream().anyMatch(track->track.keys().stream().anyMatch(k->Math.abs(k.time()-t)<.001)))continue;
            p.reset();p.apply("animation."+ID+"."+name,t);
            parity.add(Map.of("clip",name,"time",t,"matrices",matrices(p)));
        }
        result.put("parity",parity);
        for(String mode:List.of("idle","walk","attention","dialogue")) {
            int count=mode.equals("idle")?(int)Math.round(m.clips().get("animation."+ID+".idle").length()*25):mode.equals("walk")?150:mode.equals("attention")?150:200;
            List<Map<String,Object>> frames=new ArrayList<>();var clock=new NativeWalkClock(m.profile().walkStride());
            for(int i=0;i<count;i++) {
                double t=i/25.0,blink=-1,distance=0;p.reset();p.apply("animation."+ID+".idle",t);
                if(mode.equals("idle"))for(double start:new double[]{2.1,6.4})if(t>=start&&t<start+.23)blink=t-start;
                if(mode.equals("walk")) {
                    distance=Math.max(0,Math.min(t-.6,3.6))*(characterGait()?setting("previewSpeed",1.15):m.profile().walkStride());var w=clock.sample(t,0,-distance,true);
                    p.blend("animation."+ID+".walk",w.phase(),w.weight());if(w.weight()>0)p.groundFeet(m.profile().groundOffset());
                }
                if(mode.equals("attention")||mode.equals("dialogue")) {
                    double age=Math.max(0,t-.5),yaw=130;
                    double mt=mode.equals("dialogue")?NpcDialogueMotion.motionTime(age,yaw,4):age;
                    var s=NpcAttentionMotion.sample(mt,yaw,8,48,rig);NativeSamAttentionPose.apply(p,s,1,rig);blink=s.blink();
                    p.groundFeet(m.profile().groundOffset());
                }
                if(blink>=0 && m.profile().visibleBlink())p.apply("animation."+ID+".blink",blink);
                Map<String,Object> frame=new LinkedHashMap<>();
                frame.put("time",t);frame.put("distance",distance);frame.put("matrices",matrices(p));
                var cloth=p.surfaceVertices(p.matrices());
                if(cloth!=null) {
                    Map<Integer,float[][]> surface=new LinkedHashMap<>();
                    for(int q=0;q<cloth.length;q++)if(cloth[q]!=null)
                        surface.put(q,Arrays.stream(cloth[q]).map(float[]::clone).toArray(float[][]::new));
                    frame.put("cloth",surface);
                }
                frames.add(frame);
            }
            result.put(mode,frames);
        }
        Files.createDirectories(out.getParent());Files.writeString(out,gson.toJson(result));
    }
}
