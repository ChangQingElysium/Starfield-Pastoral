package com.stardew.craft.client.fishing;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;

import com.stardew.craft.port.PortVertex;
/** Authored tier geometry, in the existing animated rod bones' local coordinates. */
public final class FishingRodModels {
    public static final List<String> IDS=List.of("bamboo_pole","training_rod","fiberglass_rod","iridium_rod","advanced_iridium_rod");
    public record Face(String part,List<FishingBobberModels.Vertex> vertices) {}
    public record Part(String bone,List<Face> faces) {}
    public record Model(int version,String id,List<Part> parts) {}
    public static volatile Map<String,Model> models=Map.of();
    private FishingRodModels() {}
    public static String select(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem rod?rod.getTier().name().toLowerCase(Locale.ROOT):"";
    }
    public static ResourceLocation texture(String id){return FishingRigAssets.resource("textures/entity/fishing_native/rods/"+id+".png");}
    public static Map<String,Model> load(ResourceManager resources,FishingRigAssets.Rig rig)throws IOException {
        var next=new HashMap<String,Model>();var gson=new Gson();
        for(String id:IDS)try(var reader=resources.openAsReader(FishingRigAssets.resource("fishing_native/rods/"+id+".json"))) {
            var model=gson.fromJson(reader,Model.class);validate(model,id,rig);resources.getResourceOrThrow(texture(id));next.put(id,model);
        }
        return Map.copyOf(next);
    }
    public static void validate(Model model,String id,FishingRigAssets.Rig rig) {
        if(model==null||model.version()!=1||!id.equals(model.id())||model.parts()==null||model.parts().isEmpty())throw new IllegalArgumentException("Rod model "+id);
        var bones=new HashSet<String>();
        for(var part:model.parts()) {
            if(part.bone()==null||!part.bone().startsWith("rod_")||rig.bones().stream().noneMatch(b->b.name().equals(part.bone()))||!bones.add(part.bone())||part.faces()==null||part.faces().isEmpty())throw new IllegalArgumentException("Rod bone "+id);
            for(var face:part.faces()) {
                if(face.vertices()==null||face.vertices().size()!=4)throw new IllegalArgumentException("Rod quad "+id);
                for(var v:face.vertices()) {
                    if(v.point()==null||v.point().length!=3||v.uv()==null||v.uv().length!=2)throw new IllegalArgumentException("Rod vertex "+id);
                    for(float p:v.point())if(!Float.isFinite(p))throw new IllegalArgumentException("Rod position "+id);
                    for(float uv:v.uv())if(!Float.isFinite(uv)||uv<0||uv>1)throw new IllegalArgumentException("Rod UV "+id);
                }
                if(normal(face).lengthSquared()<1e-12)throw new IllegalArgumentException("Empty rod face "+id);
            }
        }
    }
    private static Vector3f normal(Face face) {
        var a=new Vector3f(face.vertices().get(0).point());
        return new Vector3f(face.vertices().get(1).point()).sub(a).cross(new Vector3f(face.vertices().get(2).point()).sub(a));
    }
    public static void render(String id,FishingRigPose rig,Matrix4f actor,PoseStack pose,MultiBufferSource buffers,int light) {
        var model=models.get(id);if(model==null)return;
        var out=buffers.getBuffer(RenderType.entityCutout(texture(id)));
        for(var part:model.parts()) {
            var matrix=new Matrix4f(actor).mul(rig.world[rig.index(part.bone())]);boolean reflected=matrix.determinant()<0;
            pose.pushPose();
            try {
                PortVertex.mulPose(pose, matrix);
                for(var face:part.faces()) {
                    var n=normal(face).normalize();
                    for(int k=0;k<4;k++) {
                        var v=face.vertices().get(reflected?3-k:k);var p=v.point();
                        PortVertex.of(out).addVertex(pose.last().pose(),p[0],p[1],p[2]).setColor(255,255,255,255)
                                .setUv(v.uv()[0],v.uv()[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                                .setNormal(pose.last(),n.x,n.y,n.z).endVertex();
                    }
                }
            }finally{pose.popPose();}
        }
    }
}
