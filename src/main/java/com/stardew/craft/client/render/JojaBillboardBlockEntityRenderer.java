package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.JojaBillboardBlock;
import com.stardew.craft.blockentity.JojaBillboardBlockEntity;
import com.stardew.craft.client.model.JojaBillboardModels;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.stardew.craft.port.PortVertex;
public final class JojaBillboardBlockEntityRenderer implements LargeDecorBlockEntityRenderer<JojaBillboardBlockEntity> {
    public JojaBillboardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(JojaBillboardBlockEntity entity) { return entity.getRenderBoundingBox(); }
    @Override public void render(JojaBillboardBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (entity.getLevel() == null) return;
        pose.pushPose(); pose.translate(.5, 0, .5);
        int yaw = switch (entity.getBlockState().getValue(JojaBillboardBlock.FACING)) {
            case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5, 0, -.5);
        // The approved native parts contain only the intended exposed faces.
        var consumer = buffer.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        for (var part : JojaBillboardModels.parts(TerrainSeasonTextures.currentTextureSet())) {
            pose.pushPose(); pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
            for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, 1, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
