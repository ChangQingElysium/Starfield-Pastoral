package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.utility.GardenPotBlock;
import com.stardew.craft.blockentity.GardenPotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.stardew.craft.block.crop.StardewCropBlock;

/** Re-renders the real crop on the pot's 13x13 soil plane. */
public final class GardenPotBlockEntityRenderer
        extends UtilityMachineBlockEntityRenderer<GardenPotBlockEntity> {
    private static final float SOIL_Y = 11.0F / 16.0F;
    private static final float CROP_SCALE = 13.0F / 16.0F;

    public GardenPotBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected boolean shouldApplyWorkingAnimation(GardenPotBlockEntity be) {
        return false;
    }

    @Override
    protected void renderBlockModel(
            GardenPotBlockEntity be,
            BlockState state,
            Level level,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedOverlay
    ) {
        super.renderBlockModel(be, state, level, poseStack, buffer, packedOverlay);

        BlockState crop = level.getBlockState(be.getBlockPos().above());
        if (!GardenPotBlock.isSupportedPlant(crop)) {
            return;
        }
        poseStack.pushPose();
        // Scale the complete baked model uniformly about the centre of the pot. Its unscaled
        // bottom remains y=0, so the transformed bottom lands exactly on the 11px soil plane.
        poseStack.translate(0.5F, SOIL_Y, 0.5F);
        poseStack.scale(CROP_SCALE, CROP_SCALE, CROP_SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        BlockPos cropPos = be.getBlockPos().above();
        renderCropPart(level, cropPos, crop, poseStack, buffer);

        if (crop.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && crop.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockState upper = level.getBlockState(be.getBlockPos().above(2));
            if (upper.getBlock() == crop.getBlock()) {
                poseStack.translate(0.0F, 1.0F, 0.0F);
                renderCropPart(level, cropPos.above(), upper, poseStack, buffer);
            }
        }
        poseStack.popPose();
    }

    private static void renderCropPart(
            Level level,
            BlockPos pos,
            BlockState state,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {
        // The world carrier state is invisible. Render its ordinary non-potted model once through
        // Minecraft's canonical block-entity-safe path; the outer pose owns all scaling.
        BlockState visualState = state.hasProperty(StardewCropBlock.POTTED)
                ? state.setValue(StardewCropBlock.POTTED, false)
                : state;
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                visualState,
                poseStack,
                buffer,
                LevelRenderer.getLightColor(level, pos),
                OverlayTexture.NO_OVERLAY);
    }
}
