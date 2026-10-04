package com.stardew.craft.client.model.nativebb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.model.AnimatedModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import java.util.Map;
import java.util.WeakHashMap;

/** Ordinary Minecraft entity rendering with the same native pose evaluator as NPCs and monsters. */
public class BlockbenchEntityRenderer<T extends Entity & AnimatedModel> extends EntityRenderer<T> {
    private final BlockbenchModel<T> model;
    private final Map<T,BlockbenchPlayback> playback=new WeakHashMap<>();
    public BlockbenchEntityRenderer(EntityRendererProvider.Context context,BlockbenchModel<T> model) {super(context);this.model=model;}
    @Override public ResourceLocation getTextureLocation(T entity) {return model.getTextureResource(entity);}
    public RenderType getRenderType(T entity,ResourceLocation texture,MultiBufferSource buffers,float partialTick) {return RenderType.entityCutoutNoCull(texture);}
    public int getRenderColor(T entity,float partialTick,int light) {return 0xFFFFFFFF;}
    /** Only NPC renderers opt into missing-action idle fallback; other model contracts stay strict. */
    protected String npcAnimationId(T entity) {return null;}
    protected void renderExtras(T entity,BlockbenchFrame frame,PoseStack stack,MultiBufferSource buffers,float partialTick,int light,int overlay) {}
    @Override public void render(T entity,float yaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light) {
        if(!entity.isInvisible()) {
            var asset=BlockbenchAssets.get(model.getModelResource(entity),model.getAnimationResource(entity));
            var player=playback.compute(entity,(key,old)->old!=null&&old.model()==asset?old:new BlockbenchPlayback(asset));
            var velocity=entity.getDeltaMovement();
            boolean moving=(Math.abs(velocity.x)+Math.abs(velocity.z))/2>=.015
                    && entity instanceof LivingEntity living && living.walkAnimation.speed(partialTick)!=0;
            var request=entity.modelAnimation(moving,partialTick);
            String npcId=npcAnimationId(entity);
            double now=(entity.tickCount+partialTick)/20.0;
            var pose=npcId==null ? player.sample(request,now,entity.modelTransitionTicks())
                    : player.sampleNpc(npcId,request,now,entity.modelTransitionTicks());
            model.pose(entity,pose,partialTick);var frame=new BlockbenchFrame(asset,pose);
            stack.pushPose();
            float bodyYaw=entity instanceof LivingEntity living
                    ? Mth.rotLerp(partialTick,living.yBodyRotO,living.yBodyRot)
                    : Mth.rotLerp(partialTick,entity.yRotO,entity.getYRot());
            stack.mulPose(Axis.YP.rotationDegrees(180-bodyYaw));
            int overlay=OverlayTexture.NO_OVERLAY;
            float scale=1F/16;
            if(entity instanceof LivingEntity living) {
                if(living.deathTime>0)stack.mulPose(Axis.ZP.rotationDegrees(Math.min(1,Mth.sqrt((living.deathTime+partialTick-1)/20F*1.6F))*90));
                scale*=living.getScale();
                overlay=OverlayTexture.pack(0,OverlayTexture.v(living.hurtTime>0||living.deathTime>0));
            }
            stack.scale(scale,scale,scale);
            RenderType type=getRenderType(entity,getTextureLocation(entity),buffers,partialTick);
            frame.render(stack,buffers,new BlockbenchFrame.Material() {
                @Override public RenderType type(int bone) {return type;}
                @Override public int light(int bone) {return light;}
            },overlay,getRenderColor(entity,partialTick,light));
            renderExtras(entity,frame,stack,buffers,partialTick,light,overlay);
            stack.popPose();
        }
        super.render(entity,yaw,partialTick,stack,buffers,light);
    }
}
