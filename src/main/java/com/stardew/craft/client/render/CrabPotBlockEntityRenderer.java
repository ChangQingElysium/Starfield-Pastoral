package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.CrabPotBlockEntity;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Crab pot completion hint renderer (similar to TapperBlockEntityRenderer).
 * - Draws a bubble texture
 * - Renders the product item icon inside the bubble
 */
public class CrabPotBlockEntityRenderer implements BlockEntityRenderer<CrabPotBlockEntity> {
	private static final ResourceLocation BUBBLE_TEX = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/bubble.png");
	private static final float PX = 1.0f / 32.0f;

	public CrabPotBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@SuppressWarnings({ "null", "deprecation" })
	@Override
	public void render(@Nonnull CrabPotBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		// Behavior:
		// - Working (baited, not ready): bob the block model up/down
		// - No bait or ready: keep the block model still
		// - Ready: show bubble + product icon (like Tapper)
		boolean ready = be.isReady();
		ItemStack product = be.getProduct();

		// 1) Render the animated working body; idle and ready bodies live in the chunk mesh.
		BlockState state = be.getBlockState();
		@SuppressWarnings("null")
		boolean working = state.hasProperty(com.stardew.craft.block.utility.CrabPotBlock.WORKING)
				&& state.getValue(com.stardew.craft.block.utility.CrabPotBlock.WORKING);
		Level level = be.getLevel();
		if (level != null && com.stardew.craft.block.utility.UtilityMachineRenderState.rendersDynamicBody(state)) {
			poseStack.pushPose();
			if (working && !ready) {
				// Bobbing amplitude ~0.1
				float t = level.getGameTime() + partialTick;
				long seed = be.getBlockPos().asLong();
				float phase = ((seed * 0x9E3779B97F4A7C15L) >>> 40) / 4096.0f;
				float base = (t + phase) * 0.07f;
				float bob = (float) Math.sin(base) * 0.10f;
				poseStack.translate(0.0f, bob, 0.0f);
			}

			Minecraft mc = Minecraft.getInstance();
			BakedModel model = mc.getBlockRenderer().getBlockModel(state);
			ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
			RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
			RandomSource rand = RandomSource.create(0L);
			SpatialBlockModelRenderer.render(renderer,
				level,
				model,
				state,
				be.getBlockPos(),
				poseStack,
				buffer.getBuffer(renderType),
                true,
				rand,
				0L,
				packedOverlay
			);
			poseStack.popPose();
		}

		// Note: do not render the block model a second time for bobbing,
		// otherwise you will see a static + bobbing model overlapped.
		// This renderer only handles the ready bubble + item icon.

		// No bubble unless ready
		if (!ready) {
			return;
		}
		if (product.isEmpty()) {
			return;
		}

		// Ready: show bubble + product
		poseStack.pushPose();
		poseStack.translate(0.5f, BubbleYHelper.get(be.getBlockState(), be.getLevel(), be.getBlockPos()), 0.5f);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

		float w = 20 * PX;
		float h = 24 * PX;
		float x0 = -w / 2.0f;
		float x1 = w / 2.0f;
		float y0 = 0.0f;
		float y1 = h;

		@SuppressWarnings("null")
		VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(BUBBLE_TEX));
		vc.addVertex(poseStack.last().pose(), x0, y1, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
		vc.addVertex(poseStack.last().pose(), x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
		vc.addVertex(poseStack.last().pose(), x1, y0, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
		vc.addVertex(poseStack.last().pose(), x0, y0, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);

		// Bubble inner icon: 14x14 area, starting at (3,3), same as Tapper
		float innerW = 14 * PX;
		float innerH = 14 * PX;
		float iconCenterX = x0 + (3 * PX) + innerW / 2.0f;
		float iconCenterY = y1 - (3 * PX) - innerH / 2.0f;

		poseStack.pushPose();
		poseStack.translate(iconCenterX, iconCenterY, 0.001f);
		float scale = innerW;
		poseStack.scale(scale, scale, 0.001f);

		BubbleItemRenderer.render(product, packedLight, poseStack, buffer, be.getLevel());
		poseStack.popPose();

		BubbleItemCountRenderer.renderCount(poseStack, buffer, packedLight, product, x0 + (3 * PX), y1 - (3 * PX), PX);

		poseStack.popPose();
	}
}




