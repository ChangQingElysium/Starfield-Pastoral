package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.BooksellerDecorBlock;
import com.stardew.craft.blockentity.BooksellerDecorBlockEntity;
import com.stardew.craft.client.model.BooksellerDecorModels;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

import com.stardew.craft.port.PortVertex;
public final class BooksellerDecorBlockEntityRenderer implements LargeDecorBlockEntityRenderer<BooksellerDecorBlockEntity> {
    public BooksellerDecorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(BooksellerDecorBlockEntity entity) { return entity.getRenderBoundingBox(); }
    @Override public void render(BooksellerDecorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        if (entity.getLevel() == null) return;
        var state = entity.getBlockState();
        pose.pushPose(); pose.translate(.5, 0, .5);
        int yaw = switch (state.getValue(BooksellerDecorBlock.FACING)) {
            case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5, 0, -.5);
        // Back-face culling is necessary for both the inverted hulls and double-sided cloth panels.
        var consumer = buffer.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        for (var part : BooksellerDecorModels.parts(((BooksellerDecorBlock) state.getBlock()).assetId())) {
            pose.pushPose(); pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
            for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, 1,
                    part.emissive() ? LightTexture.FULL_BRIGHT : light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
