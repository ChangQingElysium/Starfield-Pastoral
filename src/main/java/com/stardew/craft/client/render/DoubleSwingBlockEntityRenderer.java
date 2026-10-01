package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.DoubleSwingBlock;
import com.stardew.craft.block.decor.DoubleSwingMotion;
import com.stardew.craft.blockentity.DoubleSwingBlockEntity;
import com.stardew.craft.client.model.DoubleSwingModels;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.stardew.craft.port.PortVertex;
public final class DoubleSwingBlockEntityRenderer implements LargeDecorBlockEntityRenderer<DoubleSwingBlockEntity> {
    public DoubleSwingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(DoubleSwingBlockEntity entity) { return entity.getRenderBoundingBox(); }
    @Override public void render(DoubleSwingBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        var level = entity.getLevel(); if (level == null) return;
        int season = TerrainSeasonTextures.currentTextureSet();
        double seconds = DoubleSwingMotion.seconds(level.getGameTime(), partialTick);
        var consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose(); pose.translate(.5, 0, .5);
        int yaw = switch (entity.getBlockState().getValue(DoubleSwingBlock.FACING)) { case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0; };
        pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5, 0, -.5);
        for (var part : DoubleSwingModels.parts(season)) {
            pose.pushPose();
            if (part.seat() >= 0) {
                double pivot = part.seat() == 0 ? 24 : 56;
                pose.translate((pivot - 32) / 16, 72.0 / 16, .5);
                pose.mulPose(Axis.XP.rotationDegrees((float) DoubleSwingMotion.angle(part.seat(), seconds, season == 3)));
                pose.translate((part.x() - pivot) / 16, (part.y() - 72) / 16, (part.z() - 24) / 16);
            } else pose.translate((part.x() - 32) / 16, part.y() / 16, (part.z() - 16) / 16);
            for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, 1, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
