package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.ParkFountainBlock;
import com.stardew.craft.block.decor.ParkFountainMotion;
import com.stardew.craft.blockentity.ParkFountainBlockEntity;
import com.stardew.craft.client.model.ParkFountainModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import com.stardew.craft.port.PortVertex;
public final class ParkFountainBlockEntityRenderer implements LargeDecorBlockEntityRenderer<ParkFountainBlockEntity> {
    public ParkFountainBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ParkFountainBlockEntity entity) {
        return entity.getRenderBoundingBox();
    }

    @Override
    public void render(ParkFountainBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        var level = entity.getLevel();
        if (level == null || ParkFountainModels.parts().isEmpty()) return;
        if (!com.stardew.craft.block.decor.ParkFountainSeasons.flows(
                com.stardew.craft.client.model.terrain.TerrainSeasonTextures.currentTextureSet())) return;
        double seconds = (level.getGameTime() % 72000 + partialTick) / 20.0;
        ParkFountainWaterTexture.update(seconds);
        var consumer = buffer.getBuffer(WaterType.INSTANCE);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        int angle = switch (entity.getBlockState().getValue(ParkFountainBlock.FACING)) {
            case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-.5, 0, -.5);
        for (var part : ParkFountainModels.parts()) {
            pose.pushPose();
            float alpha = 1;
            if (part.kind().equals("water") || part.kind().equals("jet")) {
                pose.translate((part.x() - 32) / 16, part.y() / 16, (part.z() - 32) / 16);
            } else {
                var motion = ParkFountainMotion.sample(part.kind(), part.side(), part.index(), seconds);
                pose.translate((motion.x() - 32) / 16, motion.y() / 16, (motion.z() - 32) / 16);
                pose.scale(motion.sx(), motion.sy(), motion.sz());
                alpha = motion.alpha();
            }
            for (var quad : part.quads()) PortVertex.putBulkData(consumer, pose.last(), quad, 1, 1, 1, alpha, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Keep opaque stone depth, but never let a transparent water face erase another water layer. */
    private static final class WaterType extends RenderType {
        private WaterType() { super("fountain_water", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                16384, false, true, () -> {}, () -> {}); }
        private static final RenderType INSTANCE = create("fountain_water", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(ParkFountainWaterTexture.ID, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                        .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(ITEM_ENTITY_TARGET)
                        .createCompositeState(false));
    }
}
