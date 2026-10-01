package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.entity.FallenPrefabTreeEntity;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.model.PineCanopyModels;
import com.stardew.craft.tree.PineCanopyConnections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public class FallenPrefabTreeRenderer extends EntityRenderer<FallenPrefabTreeEntity> {
	public FallenPrefabTreeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@SuppressWarnings({ "null", "deprecation" })
	@Override
	public void render(@SuppressWarnings("null") FallenPrefabTreeEntity entity, float entityYaw, float partialTicks,
			@SuppressWarnings("null") PoseStack poseStack, @SuppressWarnings("null") MultiBufferSource buffer, int packedLight) {
		Level level = entity.level();
		BlockPos root = entity.blockPosition();

		float t = entity.getProgress(partialTicks);
		float eased = t * t;
		float angle = 90.0F * eased;
		Direction dir = entity.getFallDirection();

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		switch (dir) {
			case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-angle));
			case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
			case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(angle));
			case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-angle));
			default -> {
			}
		}
		poseStack.translate(-0.5F, 0.0F, -0.5F);

		var blockRenderer = Minecraft.getInstance().getBlockRenderer();
		// The standing blocks are already gone; retain neighbours from the falling prefab.
		Map<BlockPos, BlockState> members = new HashMap<>();
		for (var piece : entity.getPieces()) members.put(root.offset(piece.dx, piece.dy, piece.dz), piece.state);
		for (FallenPrefabTreeEntity.Piece piece : entity.getPieces()) {
			BlockState state = piece.state;
			if (state.isAir()) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(piece.dx, piece.dy, piece.dz);
			int light = packedLight;
			if (level != null) {
				BlockPos p = root.offset(piece.dx, piece.dy, piece.dz);
				light = LevelRenderer.getLightColor(level, p);
			}
			ModelData data = state.is(ModBlocks.PINE_LEAVES.get()) ? ModelData.builder().with(PineCanopyModels.CANOPY,
					PineCanopyConnections.inspect(p -> members.getOrDefault(p, Blocks.AIR.defaultBlockState()),
							root.offset(piece.dx, piece.dy, piece.dz))).build() : ModelData.EMPTY;
			blockRenderer.renderSingleBlock(state, poseStack, buffer, light, OverlayTexture.NO_OVERLAY, data, null);
			poseStack.popPose();
		}

		poseStack.popPose();
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}

	@SuppressWarnings("deprecation")
	@Override
	public ResourceLocation getTextureLocation(@SuppressWarnings("null") FallenPrefabTreeEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
