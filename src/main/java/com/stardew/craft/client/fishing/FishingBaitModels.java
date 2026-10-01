package com.stardew.craft.client.fishing;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.item.tool.FishingRodItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stardew.craft.port.PortVertex;
/** One removable, editor-authored portion of the rod's real bait stack. Local zero is the hook tip. */
public final class FishingBaitModels {
    public static final List<String> IDS=List.of("bait","magnet","wild_bait","magic_bait","deluxe_bait","challenge_bait","targeted_bait");
    public record Face(String part,boolean tinted,List<FishingBobberModels.Vertex> vertices) {}
    public record Model(int version,String id,List<Face> faces) {}
    public record Selection(String model,int tint) {
        public static final Selection EMPTY=new Selection("",0xffffff);
    }
    public static volatile Map<String,Model> models=Map.of();
    private FishingBaitModels() {}

    /** ItemStack components already synchronize through vanilla equipment packets, including target fish color. */
    public static Selection select(ItemStack rodStack) {
        if(!(rodStack.getItem() instanceof FishingRodItem rod))return Selection.EMPTY;
        var bait=rod.getAttachmentsForTooltip(rodStack).bait();if(bait.isEmpty())return Selection.EMPTY;
        var id=BuiltInRegistries.ITEM.getKey(bait.getItem());
        if(!id.getNamespace().equals("stardewcraft")||!IDS.contains(id.getPath()))return Selection.EMPTY;
        int tint=0xffffff;
        if(bait.getItem() instanceof SpecificBaitItem specific){tint=specific.getColor(bait);if(tint<0)tint=0xeabe50;}
        return new Selection(id.getPath(),tint&0xffffff);
    }

    /** Decode nested attachment NBT only after an actual equipment/component change. */
    public static final class SelectionCache {
        private ItemStack previous=ItemStack.EMPTY;
        private Selection selected=Selection.EMPTY;
        public Selection update(ItemStack rod) {
            if(!ItemStack.matches(previous,rod)){selected=select(rod);previous=rod.copy();}
            return selected;
        }
    }

    public static ResourceLocation texture(String id){return FishingRigAssets.resource("textures/entity/fishing_native/baits/"+id+".png");}
    public static Map<String,Model> load(ResourceManager resources)throws IOException {
        var next=new HashMap<String,Model>();var gson=new Gson();
        for(String id:IDS)try(var reader=resources.openAsReader(FishingRigAssets.resource("fishing_native/baits/"+id+".json"))) {
            var model=gson.fromJson(reader,Model.class);validate(model,id);resources.getResourceOrThrow(texture(id));next.put(id,model);
        }
        return Map.copyOf(next);
    }
    public static void validate(Model model,String id) {
        if(model==null||model.version()!=1||!id.equals(model.id())||model.faces()==null||model.faces().isEmpty())throw new IllegalArgumentException("Bait model "+id);
        for(var face:model.faces()) {
            if(face.vertices()==null||face.vertices().size()!=4)throw new IllegalArgumentException("Bait quad "+id);
            for(var v:face.vertices()) {
                if(v.point()==null||v.point().length!=3||v.uv()==null||v.uv().length!=2)throw new IllegalArgumentException("Bait vertex "+id);
                for(float p:v.point())if(!Float.isFinite(p))throw new IllegalArgumentException("Bait position "+id);
                for(float uv:v.uv())if(!Float.isFinite(uv)||uv<0||uv>1)throw new IllegalArgumentException("Bait UV "+id);
            }
            if(normal(face).lengthSquared()<1e-12)throw new IllegalArgumentException("Empty bait face "+id);
            if(face.tinted()&&!id.equals("targeted_bait"))throw new IllegalArgumentException("Unexpected bait tint "+id);
        }
    }
    public static Matrix4f attachment(FishingRigPose pose,Matrix4f actor){return new Matrix4f(actor).mul(pose.world[pose.index("rod_anchor_hook_tip")]);}
    public static int faceColor(Face face,Selection selected){return face.tinted()?selected.tint():0xffffff;}
    private static Vector3f normal(Face face) {
        var a=new Vector3f(face.vertices().get(0).point());
        return new Vector3f(face.vertices().get(1).point()).sub(a).cross(new Vector3f(face.vertices().get(2).point()).sub(a));
    }
    public static void render(Selection selected,Matrix4f matrix,PoseStack pose,MultiBufferSource buffers,int light) {
        var model=models.get(selected.model());if(model==null)return;
        pose.pushPose();
        try {
            PortVertex.mulPose(pose, matrix);boolean reflected=matrix.determinant()<0;
            var out=buffers.getBuffer(RenderType.entityCutout(texture(model.id())));
            for(var face:model.faces()) {
                var n=normal(face).normalize();int color=faceColor(face,selected);
                for(int k=0;k<4;k++) {
                    var v=face.vertices().get(reflected?3-k:k);var p=v.point();
                    PortVertex.of(out).addVertex(pose.last().pose(),p[0],p[1],p[2]).setColor(0xff000000|color)
                            .setUv(v.uv()[0],v.uv()[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose.last(),n.x,n.y,n.z).endVertex();
                }
            }
        }finally{pose.popPose();}
    }
}
