package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.ParkedVehicleBlock;
import com.stardew.craft.blockentity.ParkedVehicleBlockEntity;
import com.stardew.craft.client.model.ParkedVehicleModels;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.stardew.craft.port.PortVertex;
public final class ParkedVehicleBlockEntityRenderer implements LargeDecorBlockEntityRenderer<ParkedVehicleBlockEntity> {
    public ParkedVehicleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(ParkedVehicleBlockEntity entity) { return entity.getRenderBoundingBox(); }
    @Override public void render(ParkedVehicleBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (entity.getLevel() == null) return;
        var block = (ParkedVehicleBlock) entity.getBlockState().getBlock();
        var parts = ParkedVehicleModels.parts(block.assetId(), block.isBus() ? 0 : TerrainSeasonTextures.currentTextureSet());
        pose.pushPose(); pose.translate(.5, 0, .5);
        int yaw = switch (entity.getBlockState().getValue(ParkedVehicleBlock.FACING)) {
            case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5, 0, -.5);
        pose.translate((8 + block.widthCenter()) / 16.0, 0, -block.longitudinalOrigin() / 16.0);
        pose.mulPose(Axis.YP.rotationDegrees(-90));
        // Cull back faces: glass planes already have two opposite faces, and outlines use inward winding.
        for (boolean glass : new boolean[]{false, true}) {
            var consumer = buffer.getBuffer(glass ? RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)
                    : RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
            for (var part : parts) {
                if (part.glass() != glass) continue;
                pose.pushPose(); pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
                for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, 1, light, overlay);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
