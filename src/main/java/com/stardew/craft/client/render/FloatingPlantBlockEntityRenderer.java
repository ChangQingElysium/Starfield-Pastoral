package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.decor.NaturalPlantBlock;
import com.stardew.craft.block.decor.FloatingPlantMotion;
import com.stardew.craft.blockentity.FloatingPlantBlockEntity;
import com.stardew.craft.client.model.NaturalDecorModels;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

import com.stardew.craft.port.PortVertex;
public final class FloatingPlantBlockEntityRenderer implements BlockEntityRenderer<FloatingPlantBlockEntity> {
    public FloatingPlantBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FloatingPlantBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffer,
            int light, int overlay) {
        var level = entity.getLevel(); if (level == null) return;
        var state = entity.getBlockState(); var kind = ((NaturalPlantBlock) state.getBlock()).kind();
        var minecraft = Minecraft.getInstance();
        var parts = NaturalDecorModels.floatingParts(kind, TerrainSeasonTextures.currentTextureSet());
        if (parts == null) return;
        double phase = FloatingPlantMotion.phase(kind, entity.getBlockPos(), level.getGameTime(), partialTick);
        double waterline = level.getFluidState(entity.getBlockPos().below()).getHeight(level, entity.getBlockPos().below()) - 1;
        pose.pushPose();
        pose.translate(.5, waterline + .002, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(NaturalPlantBlock.FACING).toYRot()));
        pose.pushPose();
        pose.translate(0, FloatingPlantMotion.bob(phase), 0);
        pose.mulPose(Axis.XP.rotationDegrees(FloatingPlantMotion.pitch(phase)));
        pose.mulPose(Axis.ZP.rotationDegrees(FloatingPlantMotion.roll(phase)));
        pose.translate(-.5, 0, -.5);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffer.getBuffer(RenderTypeHelper.getEntityRenderType(RenderType.cutout(), false)), state, parts.body(),
                1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
        // Water stays horizontal while the plant rocks. Two rings fade to zero before resetting.
        var water = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS, false));
        for (int ring = 0; ring < 2; ring++) {
            double progress = FloatingPlantMotion.rippleProgress(phase, ring);
            float scale = FloatingPlantMotion.rippleScale(progress);
            float alpha = FloatingPlantMotion.rippleAlpha(progress);
            pose.pushPose();
            pose.scale(scale, 1, scale);
            pose.translate(-.5, ring * .0002, -.5);
            for (var quad : parts.ripples())
                if (quad.getDirection() == Direction.UP)
                    PortVertex.putBulkData(water, pose.last(), quad, 1, 1, 1, alpha, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
