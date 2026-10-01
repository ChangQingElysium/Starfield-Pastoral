package com.stardew.craft.client.fishing;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;

/** Two independent physical slots; the existing hook/line and shorts bobber retain their identities. */
public final class FishingTackleModels {
    public static final List<String> IDS=List.of("spinner","dressed_spinner","barbed_hook","lead_bobber","treasure_hunter","trap_bobber","cork_bobber","curiosity_lure","quality_bobber","sonar_bobber");
    public record Face(String part,boolean lamp,List<FishingBobberModels.Vertex> vertices) {}
    public record Model(int version,String id,String motion,float[] min,float[] max,float[] outlet,float radius,List<Face> faces) {}
    public record BobberBounds(float minY,float minZ) {}
    public record Assets(Map<String,Model> models,Map<Integer,BobberBounds> bobbers) {}
    public record Selection(String a,String b) {public static final Selection EMPTY=new Selection("","");}
    public record Mounted(int slot,Model model,float x,float y,float z) {}
    public static volatile Assets assets=new Assets(Map.of(),Map.of());
    // Yaw cannot change the vertical envelopes. This gap includes both negative hulls.
    public static final float PART_GAP=.65f;
    private FishingTackleModels() {}

    public static Selection select(ItemStack stack) {
        if(!(stack.getItem() instanceof FishingRodItem rod))return Selection.EMPTY;
        var attachments=rod.getAttachmentsForTooltip(stack);
        return new Selection(id(attachments.tackle1()),id(attachments.tackle2()));
    }
    private static String id(ItemStack stack) {
        if(stack.isEmpty())return "";var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        // Lucky shorts occupy a gameplay slot but already appear as bobber style 39.
        return id.getNamespace().equals("stardewcraft")&&IDS.contains(id.getPath())?id.getPath():"";
    }
    public static final class SelectionCache {
        private ItemStack previous=ItemStack.EMPTY;
        private Selection selected=Selection.EMPTY;
        private double lastTime=Double.NaN;
        private float phase,speed;
        public Selection update(ItemStack stack) {
            if(!ItemStack.matches(previous,stack)){selected=select(stack);previous=stack.copy();}
            return selected;
        }
        public float rotation(double time,boolean water) {
            float dt=Double.isNaN(lastTime)?0:(float)Math.max(0,Math.min(.05,time-lastTime));lastTime=time;
            speed+=(water?1.8f-speed:-speed)*(1-(float)Math.exp(-dt*5));
            phase=(phase+speed*dt)%(2*(float)Math.PI);return phase;
        }
    }
    public static ResourceLocation texture(String id){return FishingRigAssets.resource("textures/entity/fishing_native/tackles/"+id+".png");}
    public static Assets load(ResourceManager resources,Map<Integer,FishingBobberModels.Model> bobbers)throws IOException {
        var models=new HashMap<String,Model>();var gson=new Gson();
        for(String id:IDS)try(var reader=resources.openAsReader(FishingRigAssets.resource("fishing_native/tackles/"+id+".json"))) {
            var model=gson.fromJson(reader,Model.class);validate(model,id);resources.getResourceOrThrow(texture(id));models.put(id,model);
        }
        return compile(models,bobbers);
    }
    static Assets compile(Map<String,Model> models,Map<Integer,FishingBobberModels.Model> bobbers) {
        var bounds=new HashMap<Integer,BobberBounds>();
        for(var bobber:bobbers.values()) {
            float y=Float.POSITIVE_INFINITY,z=y;
            for(var f:bobber.faces())for(var v:f.vertices()){y=Math.min(y,v.point()[1]);z=Math.min(z,v.point()[2]);}
            bounds.put(bobber.style(),new BobberBounds(y,z));
        }
        return new Assets(Map.copyOf(models),Map.copyOf(bounds));
    }
    public static void validate(Model m,String id) {
        if(m==null||m.version()!=1||!id.equals(m.id())||m.faces()==null||m.faces().isEmpty()||!List.of("spin","swivel","swing").contains(m.motion()))throw new IllegalArgumentException("Tackle model "+id);
        if(m.min()==null||m.max()==null||m.min().length!=3||m.max().length!=3||!Float.isFinite(m.radius())||m.radius()<=0)throw new IllegalArgumentException("Tackle envelope "+id);
        for(int k=0;k<3;k++)if(!Float.isFinite(m.min()[k])||!Float.isFinite(m.max()[k])||m.min()[k]>m.max()[k])throw new IllegalArgumentException("Tackle bounds "+id);
        if(Math.abs(m.max()[1])>.0001)throw new IllegalArgumentException("Tackle mounting surface "+id);
        if(m.outlet()==null||m.outlet().length!=3)throw new IllegalArgumentException("Tackle outlet "+id);
        for(int k=0;k<3;k++)if(!Float.isFinite(m.outlet()[k])||m.outlet()[k]<m.min()[k]-.0001||m.outlet()[k]>m.max()[k]+.0001)throw new IllegalArgumentException("Tackle outlet bounds "+id);
        for(var f:m.faces()) {
            if(f.vertices()==null||f.vertices().size()!=4)throw new IllegalArgumentException("Tackle quad "+id);
            if(f.lamp()&&!id.equals("sonar_bobber"))throw new IllegalArgumentException("Unexpected tackle lamp "+id);
            for(var v:f.vertices()) {
                if(v.point()==null||v.point().length!=3||v.uv()==null||v.uv().length!=2)throw new IllegalArgumentException("Tackle vertex "+id);
                for(int k=0;k<3;k++)if(!Float.isFinite(v.point()[k])||v.point()[k]<m.min()[k]-.0001||v.point()[k]>m.max()[k]+.0001)throw new IllegalArgumentException("Tackle bounds do not contain geometry "+id);
                if(Math.hypot(v.point()[0],v.point()[2])>m.radius()+.00001)throw new IllegalArgumentException("Tackle rotation envelope "+id);
                for(float uv:v.uv())if(!Float.isFinite(uv)||uv<0||uv>1)throw new IllegalArgumentException("Tackle UV "+id);
            }
            if(normal(f).lengthSquared()<1e-12)throw new IllegalArgumentException("Empty tackle quad "+id);
        }
    }
    /** A then B on one vertical line. A single remaining accessory uses the upper position. */
    public static List<Mounted> layout(Selection selection,FishingBobberModels.Model bobber) {
        var result=new ArrayList<Mounted>(2);var data=assets;var bounds=data.bobbers().get(bobber.style());
        if(bounds==null)return List.of();float y=bounds.minY()-PART_GAP;
        for(int slot=0;slot<2;slot++) {
            var model=data.models().get(slot==0?selection.a():selection.b());if(model==null)continue;
            result.add(new Mounted(slot,model,bobber.leader()[0],y,Math.min(bounds.minZ(),bobber.leader()[2])-.9f));
            y+=model.min()[1]-PART_GAP;
        }
        return List.copyOf(result);
    }
    public static Vector3f hookPosition(List<Mounted> mounted) {
        var last=com.stardew.craft.port.PortJava.getLast(mounted);return new Vector3f(last.x(),last.y()+last.model().min()[1]-.85f,last.z());
    }
    public static Vector3f outlet(Mounted mount,Matrix4f bobber,float rotation,double time) {
        return transform(mount,bobber,rotation,time).transformPosition(new Vector3f(mount.model().outlet()));
    }
    public static float angle(Mounted mount,float rotation,double time) {
        return mount.model().motion().equals("spin")?rotation+(mount.slot()==0?0:.8f):
                (float)Math.sin(time*1.3+mount.slot()*1.7)*(mount.model().motion().equals("swivel")?.16f:.07f);
    }
    public static Matrix4f transform(Mounted mount,Matrix4f bobber,float rotation,double time) {
        return new Matrix4f(bobber).translate(mount.x(),mount.y(),mount.z()).rotateY(angle(mount,rotation,time));
    }
    private static Vector3f normal(Face face) {
        var a=new Vector3f(face.vertices().get(0).point());
        return new Vector3f(face.vertices().get(1).point()).sub(a).cross(new Vector3f(face.vertices().get(2).point()).sub(a));
    }
    public static void render(Selection selected,FishingBobberModels.Model bobber,Matrix4f matrix,PoseStack pose,MultiBufferSource buffers,int light,float rotation,double time,boolean signal) {
        var mounted=layout(selected,bobber);if(mounted.isEmpty())return;
        pose.pushPose();
        try {
            pose.mulPose(matrix);boolean reflected=matrix.determinant()<0;
            var previous=new Vector3f(bobber.leader());
            for(var mount:mounted) {
                link(pose,buffers,previous,new Vector3f(mount.x(),mount.y()-.08f,mount.z()),light,reflected);
                previous=outlet(mount,new Matrix4f(),rotation,time);
            }
        }finally{pose.popPose();}
        for(var mount:mounted) {
            var transform=transform(mount,matrix,rotation,time);boolean reflected=transform.determinant()<0;
            pose.pushPose();
            try {
                pose.mulPose(transform);var out=buffers.getBuffer(RenderType.entityCutout(texture(mount.model().id())));
                for(var face:mount.model().faces()) {
                    var n=normal(face).normalize();
                    for(int k=0;k<4;k++) {
                        var v=face.vertices().get(reflected?3-k:k);var p=v.point();
                        out.addVertex(pose.last().pose(),p[0],p[1],p[2]).setColor(255,255,255,255).setUv(v.uv()[0],v.uv()[1])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(face.lamp()&&signal?LightTexture.FULL_BRIGHT:light).setNormal(pose.last(),n.x,n.y,n.z);
                    }
                }
            }finally{pose.popPose();}
        }
    }
    /** Short square-section swivel links between serial parts; face colors match the authored metal. */
    private static void link(PoseStack pose,MultiBufferSource buffers,Vector3f a,Vector3f b,int light,boolean reflected) {
        var axis=new Vector3f(b).sub(a);if(axis.lengthSquared()<1e-8)return;axis.normalize();
        var side=new Vector3f(axis).cross(new Vector3f(0,0,1));if(side.lengthSquared()<.0001)side.set(axis).cross(new Vector3f(1,0,0));side.normalize().mul(.075f);
        var up=new Vector3f(axis).cross(side).normalize().mul(.075f);var p=new Vector3f[8];
        for(int end=0;end<2;end++)for(int i=0;i<4;i++)p[end*4+i]=new Vector3f(end==0?a:b).fma(i==0||i==3?-1:1,side).fma(i<2?-1:1,up);
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}};
        var out=buffers.getBuffer(RenderType.entityCutout(FishingRigAssets.resource("textures/entity/fishing_native/2.png")));
        for(var face:faces) {
            var n=new Vector3f(p[face[1]]).sub(p[face[0]]).cross(new Vector3f(p[face[2]]).sub(p[face[0]])).normalize();
            int color=n.y>.4?0xffc5d5d4:n.z<-.4?0xff91a6b1:0xff687f95;
            for(int k=0;k<4;k++){var v=p[face[reflected?3-k:k]];out.addVertex(pose.last().pose(),v.x,v.y,v.z).setColor(color).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),n.x,n.y,n.z);}
        }
    }
}
