package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.PlaygroundBlock;
import com.stardew.craft.blockentity.PlaygroundBlockEntity;
import com.stardew.craft.client.model.PlaygroundModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.stardew.craft.port.PortVertex;
public final class PlaygroundBlockEntityRenderer implements LargeDecorBlockEntityRenderer<PlaygroundBlockEntity> {
    public PlaygroundBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(PlaygroundBlockEntity entity) { return entity.getRenderBoundingBox(); }
    @Override public void render(PlaygroundBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        var state = entity.getBlockState();
        if (!(state.getBlock() instanceof PlaygroundBlock block)) return;
        var consumer = buffer.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose(); pose.translate(.5, 0, .5);
        int yaw = switch (state.getValue(PlaygroundBlock.FACING)) { case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0; };
        pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5, 0, -.5);
        if (block.modelName().equals("bird_spring_rider")) {
            double seconds = (entity.getLevel().getGameTime() + partialTick) / 20.0;
            double angle = com.stardew.craft.block.decor.BirdSpringRiderMotion.angle(seconds);
            for (var part : com.stardew.craft.client.model.BirdSpringRiderModels.parts()) {
                pose.pushPose();
                if (part.flex() > 0) {
                    pose.translate(.5, .5, 15.0 / 16);
                    pose.mulPose(Axis.XP.rotationDegrees((float) (angle * part.flex())));
                    pose.translate(-.5, -.5, -15.0 / 16);
                }
                pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
                for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, 1, light, overlay);
                pose.popPose();
            }
        }
        for (var part : PlaygroundModels.parts(block.modelName())) {
            pose.pushPose(); pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
            for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, 1, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
