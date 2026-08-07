package com.stardew.craft.client.render;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public final class BubbleItemCountRenderer {
	private BubbleItemCountRenderer() {
	}

	@SuppressWarnings("null")
	public static void renderCount(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack, float iconLeft, float iconTop, float px) {
		renderCount(poseStack, buffer, packedLight, stack.getCount(), iconLeft, iconTop, px);
	}

	@SuppressWarnings("null")
	public static void renderCount(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int count, float iconLeft, float iconTop, float px) {
		if (count <= 1) {
			return;
		}

		renderCountInternal(poseStack, buffer, packedLight, count, iconLeft, iconTop, px);
	}

	@SuppressWarnings("null")
	public static void renderCountAlways(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int count, float iconLeft, float iconTop, float px) {
		if (count <= 0) {
			return;
		}

		renderCountInternal(poseStack, buffer, packedLight, count, iconLeft, iconTop, px);
	}

	@SuppressWarnings("null")
	private static void renderCountInternal(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int count, float iconLeft, float iconTop, float px) {
		Font font = StardewFonts.tiny();
		@Nonnull String label = String.valueOf(count);
		int textWidth = font.width(label);

		poseStack.pushPose();
		poseStack.translate(iconLeft, iconTop - (14 * px), 0.002f);
		poseStack.scale(px, px, 0.001f);

		float textX = Math.max(0, 14 - textWidth);
		float textY = 14 - 7;

		font.drawInBatch(label, textX + 1, textY + 1, 0x000000, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
		font.drawInBatch(label, textX, textY, 0xFFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);

		poseStack.popPose();
	}
}
