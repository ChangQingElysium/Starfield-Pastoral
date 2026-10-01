package com.stardew.craft.client.monsternative;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.entity.projectile.SkeletonBoneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class NativeSkeletonBoneRenderer extends EntityRenderer<SkeletonBoneEntity> {
    public NativeSkeletonBoneRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.06F;}
    @Override protected boolean shouldShowName(SkeletonBoneEntity entity){return false;}
    @Override public ResourceLocation getTextureLocation(SkeletonBoneEntity entity){return new ResourceLocation("stardewcraft:textures/entity/monster_native/skeleton_bone.png");}
    @Override public void render(SkeletonBoneEntity e,float yaw,float p,PoseStack stack,MultiBufferSource buffers,int light){
        stack.pushPose();stack.translate(0,e.getBbHeight()/2,0);stack.mulPose(Axis.YP.rotationDegrees(180-e.getYRot()));stack.mulPose(Axis.ZP.rotationDegrees((e.tickCount+p)*33.75F));stack.scale(1F/16,1F/16,1F/16);NativeSkeletonRenderer.drawBone(stack,buffers,light);stack.popPose();super.render(e,yaw,p,stack,buffers,light);
    }
}
