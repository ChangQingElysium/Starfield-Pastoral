package com.stardew.craft.client.model.nativebb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Geometry submission and bone attachment share the exact sampled hierarchy. */
public record BlockbenchFrame(NativeNpcModel model, NativeNpcPose pose) {
    public interface Material {
        default boolean visible(int bone) { return true; }
        RenderType type(int bone);
        int light(int bone);
    }
    public boolean under(int index,String name) {
        for(int i=index;i>=0;i=model.bones().get(i).parent())if(model.bones().get(i).name().equals(name))return true;
        return false;
    }
    public void render(PoseStack stack,MultiBufferSource buffers,Material material,int overlay,int color) {
        var matrices=pose.matrices();var vertex=new Vector3f();var normal=new Vector3f();var normalMatrix=new Matrix3f();
        for(var quad:model.quads()) {
            if(!material.visible(quad.bone()))continue;
            Matrix4f matrix=matrices[quad.bone()];
            // Zero-scale animation is an intentional hidden part (steam/attachments).
            if(Math.abs(matrix.determinant())<1E-10)continue;
            matrix.normal(normalMatrix).transform(normal.set(quad.normal())).normalize();
            var consumer=buffers.getBuffer(material.type(quad.bone()));
            for(var v:quad.vertices()) {
                matrix.transformPosition(vertex.set(v[0],v[1],v[2]));
                consumer.addVertex(stack.last().pose(),vertex.x,vertex.y,vertex.z).setColor(color)
                        .setUv(v[3],v[4]).setOverlay(overlay).setLight(material.light(quad.bone()))
                        .setNormal(stack.last(),normal.x,normal.y,normal.z);
            }
        }
    }
    /** Called inside the renderer's 1/16 model scale; leaves the item in ordinary block units. */
    public boolean attach(PoseStack stack,String bone) {
        if(!pose.hasBone(bone))return false;
        var origin=model.bones().stream().filter(b->b.name().equals(bone)).findFirst().orElseThrow().origin();
        stack.mulPose(pose.boneMatrix(bone));stack.translate(origin[0],origin[1],origin[2]);
        stack.scale(16,16,16);return true;
    }
}
