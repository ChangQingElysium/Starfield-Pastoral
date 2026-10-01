package com.stardew.craft.client.slingshot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.entity.projectile.SlingshotProjectile;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

import com.stardew.craft.port.PortVertex;
/** Camera-facing existing 2D item sprite; flight and collision remain fully three-dimensional. */
public final class SlingshotProjectileRenderer extends EntityRenderer<SlingshotProjectile> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public SlingshotProjectileRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();}
    @Override public ResourceLocation getTextureLocation(SlingshotProjectile entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(SlingshotProjectile e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        stack.pushPose();stack.mulPose(entityRenderDispatcher.cameraOrientation());stack.mulPose(Axis.YP.rotationDegrees(180));
        stack.mulPose(Axis.ZP.rotationDegrees((e.tickCount+partial)*e.spinDegreesPerTick()));
        String id=com.stardew.craft.item.weapon.SlingshotAmmo.sourceId(e.getItem());
        if(java.util.Set.of("388","390","378","380","384","382","386").contains(id)) {
            var texture=new ResourceLocation("stardewcraft","textures/entity/slingshot/"+id+".png");
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
            float[][] corners={{-.5f,-.5f,0,1},{.5f,-.5f,1,1},{.5f,.5f,1,0},{-.5f,.5f,0,0}};
            for(var v:corners) PortVertex.of(out).addVertex(stack.last().pose(),v[0],v[1],0).setColor(-1).setUv(v[2],v[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),0,0,1).endVertex();
        } else
        items.renderStatic(e.getItem(),ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,stack,buffers,e.level(),e.getId());stack.popPose();
    }
}
