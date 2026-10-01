package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.client.model.nativebb.*;
import com.stardew.craft.model.AnimatedModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.Map;
import java.util.WeakHashMap;

/** Native renderer for the existing exported block geometry resources. */
public class StardewGeoBlockRenderer<T extends BlockEntity & AnimatedModel> implements BlockEntityRenderer<T> {
    private final BlockbenchModel<T> model;
    private final Map<T,BlockbenchPlayback> playback=new WeakHashMap<>();
    public StardewGeoBlockRenderer(BlockbenchModel<T> model) {this.model=model;}
    public ResourceLocation getTextureLocation(T entity) {return model.getTextureResource(entity);}
    public RenderType getRenderType(T entity,ResourceLocation texture,MultiBufferSource buffers,float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
    protected boolean visible(T entity,BlockbenchFrame frame,int bone) {return true;}
    protected RenderType boneType(T entity,BlockbenchFrame frame,int bone,RenderType type) {return type;}
    protected int boneLight(T entity,BlockbenchFrame frame,int bone,int light) {return light;}
    protected void renderExtras(T entity,BlockbenchFrame frame,PoseStack stack,MultiBufferSource buffers,float partialTick,int light,int overlay) {}
    @Override public void render(T entity,float partialTick,PoseStack stack,MultiBufferSource buffers,int light,int overlay) {
        var asset=BlockbenchAssets.get(model.getModelResource(entity),model.getAnimationResource(entity));
        var player=playback.compute(entity,(key,old)->old!=null&&old.model()==asset?old:new BlockbenchPlayback(asset));
        double now=(entity.getLevel()==null?0:(double)entity.getLevel().getGameTime()+partialTick)/20.0;
        var pose=player.sample(entity.modelAnimation(false,partialTick),now,entity.modelTransitionTicks());
        model.pose(entity,pose,partialTick);
        var frame=new BlockbenchFrame(asset,pose);
        stack.pushPose();
        stack.translate(.5,0,.5);rotateBlock(facing(entity),stack);stack.scale(1F/16,1F/16,1F/16);
        RenderType type=getRenderType(entity,getTextureLocation(entity),buffers,partialTick);
        frame.render(stack,buffers,new BlockbenchFrame.Material() {
            @Override public boolean visible(int bone) {return StardewGeoBlockRenderer.this.visible(entity,frame,bone);}
            @Override public RenderType type(int bone) {return boneType(entity,frame,bone,type);}
            @Override public int light(int bone) {return boneLight(entity,frame,bone,light);}
        },overlay,0xFFFFFFFF);
        renderExtras(entity,frame,stack,buffers,partialTick,light,overlay);
        stack.popPose();
    }
    private Direction facing(T entity) {
        var state=entity.getBlockState();
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if(state.hasProperty(BlockStateProperties.FACING))return state.getValue(BlockStateProperties.FACING);
        return Direction.NORTH;
    }
    protected void rotateBlock(Direction facing,PoseStack stack) {
        switch(facing) {
            case SOUTH -> stack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> stack.mulPose(Axis.YP.rotationDegrees(90));
            case EAST -> stack.mulPose(Axis.YP.rotationDegrees(270));
            case UP -> stack.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> stack.mulPose(Axis.XP.rotationDegrees(-90));
            default -> {}
        }
    }
    @Override public boolean shouldRenderOffScreen(T entity) {return true;}
    @Override public int getViewDistance() {return 128;}
}
