package com.stardew.craft.client.npcnative;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.stardew.craft.port.PortVertex;
/** Shared native geometry, UV, normal and material passes for NPCs and construction actors. */
public final class NativeNpcPoseRenderer {
    private final Vector3f vertex = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private final Matrix3f normalMatrix = new Matrix3f();

    public void render(net.minecraft.world.entity.Entity entity,PoseStack stack,MultiBufferSource buffers,int light,
                            NativeNpcModel model,NativeNpcPose currentPose,float bodyYaw) {
        if (entity.isInvisible()) return;
        stack.pushPose();
        // Generic front is -Z; MC yaw zero faces +Z. Convert model units once.
        stack.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        stack.scale(1/16F, 1/16F, 1/16F);
        stack.translate(0, model.profile().groundOffset(), 0);
        renderGeometry(stack, buffers, light, model, currentPose);
        stack.popPose();
    }

    /** Geometry-only pass shared with placed Generic furniture; caller owns units and orientation. */
    public void renderGeometry(PoseStack stack, MultiBufferSource buffers, int light,
                               NativeNpcModel model, NativeNpcPose currentPose) {
        var matrices = currentPose.matrices();
        var surface = currentPose.surfaceVertices(matrices);
        var texture = new ResourceLocation(model.texture());
        for (int pass = 0; pass < 3; pass++) {
            boolean translucent = pass == 2;
            var consumer = buffers.getBuffer(translucent ? RenderType.entityTranslucent(texture)
                    : pass == 1 ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture));
            for (int qi=0;qi<model.quads().size();qi++) {
                var quad=model.quads().get(qi);
                float alpha = currentPose.opacity(quad.bone());
                boolean transparent = quad.translucent() || alpha < .999F;
                if (alpha <= .001F || transparent != translucent || (!translucent && quad.cull() != (pass == 1))) continue;
                var deformed=surface==null?null:surface[qi];
                normal.set(quad.normal());
                if(deformed!=null) {
                    normal.set(deformed[1]).sub(deformed[0][0],deformed[0][1],deformed[0][2]);
                    vertex.set(deformed[2]).sub(deformed[0][0],deformed[0][1],deformed[0][2]);
                    normal.cross(vertex).normalize();
                } else if (quad.bone() >= 0) {
                    matrices[quad.bone()].normal(normalMatrix).transform(normal).normalize();
                }
                for (int vi=0;vi<4;vi++) {
                    var v=quad.vertices()[vi];
                    vertex.set(v[0], v[1], v[2]);
                    if(deformed!=null)vertex.set(deformed[vi]);
                    else if (quad.bone() >= 0) matrices[quad.bone()].transformPosition(vertex);
                    PortVertex.of(consumer).addVertex(stack.last().pose(), vertex.x, vertex.y, vertex.z)
                            .setColor(255,255,255,Math.round(255*alpha)).setUv(v[3],v[4])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(stack.last(), normal.x, normal.y, normal.z).endVertex();
                }
            }
        }
    }
}
