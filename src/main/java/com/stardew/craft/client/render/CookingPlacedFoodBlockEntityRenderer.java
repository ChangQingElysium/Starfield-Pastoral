package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.cooking.CookingPlacedFoodBlock;
import com.stardew.craft.blockentity.CookingPlacedFoodBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class CookingPlacedFoodBlockEntityRenderer implements BlockEntityRenderer<CookingPlacedFoodBlockEntity> {
    private static final ResourceLocation BAKED_FISH = new ResourceLocation(
            StardewCraft.MODID, "native_models/block/cooking_blockbench/baked_fish.json");

    public CookingPlacedFoodBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@Nonnull CookingPlacedFoodBlockEntity blockEntity, float partialTick,
                       @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof CookingPlacedFoodBlock foodBlock)) {
            return;
        }
        if (!"baked_fish".equals(foodBlock.getItemId())) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(state.getValue(CookingPlacedFoodBlock.FACING))));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        BlockbenchElementRenderer.renderAll(BAKED_FISH, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static float rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
