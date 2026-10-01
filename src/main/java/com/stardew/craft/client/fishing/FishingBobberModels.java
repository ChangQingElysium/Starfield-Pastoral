package com.stardew.craft.client.fishing;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stardew.craft.port.PortVertex;
/** Editor-rendered cuboid faces, including the original inward-facing outline geometry. */
public final class FishingBobberModels {
    public record Vertex(float[] point,float[] uv) {}
    public record Face(String part,List<Vertex> vertices) {}
    public record Model(int version,int style,float[] mainLine,float[] leader,float[] stowedOffset,
                        float[] waterline,List<Face> faces) {}
    public static volatile Map<Integer,Model> models=Map.of();

    private FishingBobberModels() {}

    public static ResourceLocation texture(int style) {
        return FishingRigAssets.resource("textures/entity/fishing_native/bobbers/"+style+".png");
    }

    public static Map<Integer,Model> load(ResourceManager resources) throws IOException {
        var next=new HashMap<Integer,Model>();var gson=new Gson();
        for(int style=0;style<40;style++) {
            try(var reader=resources.openAsReader(FishingRigAssets.resource("fishing_native/bobbers/"+style+".json"))) {
                var model=gson.fromJson(reader,Model.class);validate(model,style);
                resources.getResourceOrThrow(texture(style));next.put(style,model);
            }
        }
        return Map.copyOf(next);
    }

    public static void validate(Model model,int style) {
        if(model==null||model.version()!=1||model.style()!=style||model.faces()==null||model.faces().isEmpty())
            throw new IllegalArgumentException("Bobber model "+style);
        vector(model.mainLine(),3);vector(model.leader(),3);vector(model.stowedOffset(),3);vector(model.waterline(),3);
        for(var face:model.faces()) {
            if(face.vertices()==null||face.vertices().size()!=4)throw new IllegalArgumentException("Bobber quad");
            for(var vertex:face.vertices()) {
                vector(vertex.point(),3);vector(vertex.uv(),2);
                for(float uv:vertex.uv())if(uv<0||uv>1)throw new IllegalArgumentException("Bobber UV");
            }
            if(normal(face).lengthSquared()<1e-10f)throw new IllegalArgumentException("Empty bobber face");
        }
    }

    private static void vector(float[] values,int length) {
        if(values==null||values.length!=length)throw new IllegalArgumentException("Bobber vector");
        for(float value:values)if(!Float.isFinite(value))throw new IllegalArgumentException("Bobber finite value");
    }

    public static Model get(int style) {return models.getOrDefault(style,models.get(0));}

    /** Preserve the animated orientation and native scale; attach the selected shell's own top. */
    public static Matrix4f transform(Model model,Matrix4f attachment,Vec3 end,float stowed,Double waterY) {
        var matrix=new Matrix4f(attachment);
        var offset=new Vector3f(model.mainLine()).sub(new Vector3f(model.stowedOffset()).mul(stowed));
        matrix.transformDirection(offset);
        matrix.setTranslation((float)end.x-offset.x,(float)end.y-offset.y,(float)end.z-offset.z);
        if(waterY!=null) {
            var water=matrix.transformPosition(new Vector3f(model.waterline()));
            matrix.m31(matrix.m31()+(float)(waterY-water.y));
        }
        return matrix;
    }

    public static Vec3 anchor(Matrix4f matrix,float[] point) {
        var p=matrix.transformPosition(new Vector3f(point));return new Vec3(p.x,p.y,p.z);
    }

    private static Vector3f normal(Face face) {
        var a=new Vector3f(face.vertices().get(0).point());
        return new Vector3f(face.vertices().get(1).point()).sub(a)
                .cross(new Vector3f(face.vertices().get(2).point()).sub(a));
    }

    public static void render(Model model,Matrix4f matrix,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        try {
            PortVertex.mulPose(pose, matrix);boolean reflected=matrix.determinant()<0;
            var out=buffers.getBuffer(RenderType.entityCutout(texture(model.style())));
            for(var face:model.faces()) {
                var normal=normal(face).normalize();
                for(int k=0;k<4;k++) {
                    var v=face.vertices().get(reflected?3-k:k);var p=v.point();
                    PortVertex.of(out).addVertex(pose.last().pose(),p[0],p[1],p[2]).setColor(255,255,255,255)
                            .setUv(v.uv()[0],v.uv()[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose.last(),normal.x,normal.y,normal.z).endVertex();
                }
            }
        } finally {pose.popPose();}
    }
}
