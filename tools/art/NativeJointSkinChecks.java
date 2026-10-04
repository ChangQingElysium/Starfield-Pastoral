import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.*;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Validate the production surface: welded cuts, finite bends and rigid endpoints. */
public final class NativeJointSkinChecks {
    static void near(Vector3f a,Vector3f b,String label) {
        if (!a.isFinite() || a.distance(b)>2e-4) throw new AssertionError(label+": "+a+" / "+b);
    }
    static void straight(NativeNpcModel m,NativeNpcPose p) {
        p.reset();
        for (var b:m.bones()) p.addRotation(b.name(),-b.rotation()[0],-b.rotation()[1],-b.rotation()[2]);
    }
    static int inspect(NativeNpcModel m,String id) {
        if(m.quads().stream().anyMatch(q->q.sourcePart().startsWith("elbow_fill_")||q.sourcePart().startsWith("knee_fill_")))
            throw new AssertionError("Unconverted joint filler "+id);
        var p=new NativeNpcPose(m);
        var pairs=new LinkedHashMap<String,NativeNpcModel.Skin>();
        for(var q:m.quads())if(q.skin()!=null) {
            var s=q.skin();pairs.put(s.upper()+"/"+s.lower(),s);
            for(float w:s.weights())if(!Float.isFinite(w)||w<0||w>1)throw new AssertionError("Weight "+id);
        }
        for(var binding:pairs.values())for(double angle:new double[]{0,30,60,90,120}) {
            straight(m,p);p.addRotation(m.bones().get(binding.lower()).name(),angle,0,0);
            var matrices=p.matrices();var surface=p.surfaceVertices(matrices);
            Map<String,Vector3f> joined=new HashMap<>();Set<String> owners=new HashSet<>();
            int interior=0;
            for(int qi=0;qi<m.quads().size();qi++) {
                var q=m.quads().get(qi);var s=q.skin();
                if(s==null||s.upper()!=binding.upper()||s.lower()!=binding.lower())continue;
                owners.add(q.sourcePart());
                for(int vi=0;vi<4;vi++) {
                    var v=q.vertices()[vi];var actual=new Vector3f(surface[qi][vi]);
                    if(!actual.isFinite())throw new AssertionError("Nonfinite joint "+id);
                    String key=String.format(Locale.ROOT,"%.5f/%.5f/%.5f",v[0],v[1],v[2]);
                    var previous=joined.putIfAbsent(key,actual);
                    if(previous!=null)near(actual,previous,"Joint seam "+id);
                    if(angle==0)near(actual,new Vector3f(v[0],v[1],v[2]),"Straight surface "+id);
                    float w=s.weights()[vi];
                    if(w>0&&w<1)interior++;
                    else near(actual,matrices[w==0?s.upper():s.lower()].transformPosition(new Vector3f(v[0],v[1],v[2])),"Rigid endpoint "+id);
                }
            }
            // Torso panels blend into the body's parent, including a seated
            // character's person group. Hip rows instead blend root into a thigh.
            // Both are one authored surface; elbow/knee cuts still need two parts.
            String lowerName=m.bones().get(binding.lower()).name();
            boolean torso=(lowerName.equals("body")||lowerName.equals("chest_breath"))
                    && m.bones().stream().anyMatch(b->b.name().equals("body")&&b.parent()==binding.upper());
            boolean hip=m.bones().get(binding.upper()).name().equals("root")
                    && (lowerName.equals("leg_left")||lowerName.equals("leg_right"));
            if(interior==0||(!(torso||hip)&&owners.size()<2))throw new AssertionError("Missing continuous bridge "+id);
        }
        return pairs.size();
    }
    static void rejectIncompleteLimb(NativeNpcModel m) {
        var first=m.quads().stream().filter(q->q.skin()!=null
                && m.bones().get(q.skin().upper()).name().startsWith("arm_")
                && m.bones().get(q.skin().lower()).name().startsWith("forearm_")).findFirst().orElseThrow();
        var binding=first.skin();
        var incomplete=m.quads().stream().filter(q->q.skin()==null
                || q.skin().upper()!=binding.upper()||q.skin().lower()!=binding.lower()
                || q.sourcePart().equals(first.sourcePart())).toList();
        var broken=new NativeNpcModel(m.version(),m.texture(),m.bones(),incomplete,m.clips(),m.profile());
        try {
            inspect(broken,"single-part-limb-regression");
        } catch(AssertionError expected) {
            if(expected.getMessage().equals("Missing continuous bridge single-part-limb-regression"))return;
            throw expected;
        }
        throw new AssertionError("Incomplete one-part limb bridge was accepted");
    }
    static void export(NativeNpcModel m,String id,Path out,Gson gson)throws Exception {
        var p=new NativeNpcPose(m);var frames=new ArrayList<Map<String,Object>>();
        String clip=m.clips().keySet().stream().filter(n->n.endsWith(".walk")||n.endsWith("_play")).findFirst().orElse(m.clips().keySet().iterator().next());
        int count=Math.max(48,(int)Math.ceil(m.clips().get(clip).length()*24));
        for(int i=0;i<count;i++) {
            double phase=i/(double)count;p.reset();p.apply(clip,phase*m.clips().get(clip).length());
            var matrices=p.matrices();var surface=p.surfaceVertices(matrices);var deformed=new LinkedHashMap<Integer,float[][]>();
            if(surface!=null)for(int q=0;q<surface.length;q++)if(surface[q]!=null)
                deformed.put(q,Arrays.stream(surface[q]).map(float[]::clone).toArray(float[][]::new));
            frames.add(Map.of("matrices",Arrays.stream(matrices).map(a->a.get(new float[16])).toList(),"surface",deformed));
        }
        Files.createDirectories(out);Files.writeString(out.resolve(id+"-poses.json"),gson.toJson(Map.of("clip",clip,"duration",m.clips().get(clip).length(),"frames",frames)));
    }
    public static void main(String[] args)throws Exception {
        var gson=new Gson();Path directory=Path.of(args[0]),out=Path.of(args[1]);int models=0,pairs=0;
        // Accept one compiled model for an isolated character check as well.
        try(var files=Files.isRegularFile(directory)?java.util.stream.Stream.of(directory):Files.list(directory)) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                String id=file.getFileName().toString().replace(".json","");
                var model=gson.fromJson(Files.readString(file),NativeNpcModel.class);
                int count=inspect(model,id);if(count>0)models++;pairs+=count;
                if(id.equals("sam")) {
                    rejectIncompleteLimb(model);
                    System.out.println("PASS: an incomplete one-part limb bridge is still rejected.");
                }
                if(args.length>2 && args[2].equals("--preview") && Set.of("sam","sam_guitar","willy","jas","george","emily").contains(id))export(model,id,out,gson);
            }
        }
        if(models==0||pairs==0)throw new AssertionError("No NPC joint surfaces checked");
        System.out.println("Joint skin: "+models+" models, "+pairs+" joints; five bend angles, welded seams, finite vertices and rigid endpoints passed.");
    }
}
