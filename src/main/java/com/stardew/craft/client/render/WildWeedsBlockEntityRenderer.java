package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.blockentity.WildWeedsBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class WildWeedsBlockEntityRenderer implements BlockEntityRenderer<WildWeedsBlockEntity> {
	private static final ResourceLocation SPRING_DANDELION = weedBlockbenchModel("wild_weeds_spring_2");
	private static final ResourceLocation SUMMER_FRUIT = weedBlockbenchModel("wild_weeds_summer_2");

	public WildWeedsBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(@Nonnull WildWeedsBlockEntity blockEntity, float partialTick,
					   @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer,
					   int packedLight, int packedOverlay) {
		ResourceLocation model = negativeModel(blockEntity.getBlockState());
		if (model == null) {
			return;
		}
		poseStack.pushPose();
		BlockbenchElementRenderer.renderNegativeOnly(model, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static ResourceLocation negativeModel(BlockState state) {
		if (!(state.getBlock() instanceof WildWeedsBlock)) {
			return null;
		}
		int season = state.getValue(WildWeedsBlock.SEASON);
		int variant = state.getValue(WildWeedsBlock.VARIANT);
		if (season == 0 && variant == 2) {
			return SPRING_DANDELION;
		}
		if (season == 1 && variant == 2) {
			return SUMMER_FRUIT;
		}
		return null;
	}

	private static ResourceLocation weedBlockbenchModel(String name) {
		return new ResourceLocation(StardewCraft.MODID, "models/block/weeds_blockbench/" + name + ".json");
	}
}
