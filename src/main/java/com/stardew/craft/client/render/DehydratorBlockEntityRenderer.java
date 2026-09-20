package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.DehydratorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class DehydratorBlockEntityRenderer implements BlockEntityRenderer<DehydratorBlockEntity> {
	private static final ResourceLocation BUBBLE_TEX = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/bubble.png");
	private static final float PX = 1.0f / 32.0f;

	public DehydratorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@SuppressWarnings({ "null", "deprecation" })
	@Override
	public void render(@Nonnull DehydratorBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
		boolean ready = be.isReady();
		ItemStack product = be.getProduct();

		BlockState state = be.getBlockState();
		Level level = be.getLevel();
		if (level != null && com.stardew.craft.block.utility.UtilityMachineRenderState.rendersDynamicBody(state)) {
			poseStack.pushPose();
			if (be.isWorking() && !ready) {
				applyKegWorkingPose(poseStack, level, be.getBlockPos(), partialTick);
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

		if (!ready || product.isEmpty() || level == null) {
			return;
		}

		float bubbleY = BubbleYHelper.get(state, level, be.getBlockPos());

		poseStack.pushPose();
		poseStack.translate(0.5f, bubbleY, 0.5f);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

		float w = 20 * PX;
		float h = 24 * PX;
		float x0 = -w / 2.0f;
		float x1 = w / 2.0f;
		float y0 = 0.0f;
		float y1 = h;

		VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(BUBBLE_TEX));
		vc.addVertex(poseStack.last().pose(), x0, y1, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
		vc.addVertex(poseStack.last().pose(), x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
		vc.addVertex(poseStack.last().pose(), x1, y0, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
		vc.addVertex(poseStack.last().pose(), x0, y0, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);

		float innerW = 14 * PX;
		float innerH = 14 * PX;
		float iconCenterX = x0 + (3 * PX) + innerW / 2.0f;
		float iconCenterY = y1 - (3 * PX) - innerH / 2.0f;

		poseStack.pushPose();
		poseStack.translate(iconCenterX, iconCenterY, 0.001f);
		float scale = innerW;
		poseStack.scale(scale, scale, 0.001f);

		Minecraft.getInstance().getItemRenderer().renderStatic(
			product,
			ItemDisplayContext.GUI,
			packedLight,
			OverlayTexture.NO_OVERLAY,
			poseStack,
			buffer,
			be.getLevel(),
			0
		);
		poseStack.popPose();

		BubbleItemCountRenderer.renderCount(poseStack, buffer, packedLight, product, x0 + (3 * PX), y1 - (3 * PX), PX);

		poseStack.popPose();
	}

	private static void applyKegWorkingPose(PoseStack poseStack, Level level, net.minecraft.core.BlockPos pos, float partialTick) {
		float time = getCycleTime(level, pos, partialTick);

		Keyframe k0 = new Keyframe(0f, 1.05f, 1.45f, 1.05f, -0.10f);
		Keyframe k1 = new Keyframe(8f, 1.35f, 1.05f, 1.35f, 0.05f);
		Keyframe k2 = new Keyframe(15f, 1.20f, 1.20f, 1.20f, 0.00f);
		Keyframe k3 = new Keyframe(23f, 1.25f, 1.15f, 1.25f, 0.02f);
		Keyframe k4 = new Keyframe(30f, 1.05f, 1.45f, 1.05f, -0.10f);

		Keyframe a;
		Keyframe b;
		if (time < k1.t) {
			a = k0;
			b = k1;
		} else if (time < k2.t) {
			a = k1;
			b = k2;
		} else if (time < k3.t) {
			a = k2;
			b = k3;
		} else {
			a = k3;
			b = k4;
		}

		float t = (time - a.t) / (b.t - a.t);
		float sx = lerp(t, a.sx, b.sx);
		float sy = lerp(t, a.sy, b.sy);
		float sz = lerp(t, a.sz, b.sz);
		float y = lerp(t, a.y, b.y);

		float baseScale = 1.2f;
		sx /= baseScale;
		sy /= baseScale;
		sz /= baseScale;

		float scaleAmp = 0.55f;
		sx = 1.0f + (sx - 1.0f) * scaleAmp;
		sy = 1.0f + (sy - 1.0f) * scaleAmp;
		sz = 1.0f + (sz - 1.0f) * scaleAmp;
		sx = clamp(sx, 0.95f, 1.05f);
		sy = clamp(sy, 0.95f, 1.05f);
		sz = clamp(sz, 0.95f, 1.05f);
		y = clamp(y * 0.30f, 0.0f, 0.04f);

		poseStack.translate(0.0f, y, 0.0f);
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(sx, sy, sz);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}

	private static float getCycleTime(Level level, net.minecraft.core.BlockPos pos, float partialTick) {
		long seed = pos.asLong();
		float phase = ((seed * 0x9E3779B97F4A7C15L) >>> 40) / 4096.0f;
		float t = level.getGameTime() + partialTick + phase * 30.0f;
		float mod = t % 30.0f;
		return mod < 0 ? mod + 30.0f : mod;
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	private record Keyframe(float t, float sx, float sy, float sz, float y) {}
}
