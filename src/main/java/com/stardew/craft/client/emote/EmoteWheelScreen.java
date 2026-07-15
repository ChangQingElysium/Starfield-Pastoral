package com.stardew.craft.client.emote;

import java.util.List;

import com.stardew.craft.emote.EmoteCatalog;
import com.stardew.craft.emote.EmoteType;
import com.stardew.craft.network.payload.EmoteUsePayload;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

@SuppressWarnings("null")
public final class EmoteWheelScreen extends Screen {

	private static final ResourceLocation EMOTES_TEXTURE = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/emotes.png");
	private static final int EMOTES_TEX_W = 64;
	private static final int EMOTES_TEX_H = 256;
	private static final int ICON_SIZE = 24;
	private static final int RADIUS = 100;
	private static final int COLLAPSED_RADIUS = 30;
	private static final int OPEN_ANIM_MS = 200;
	private static final double COLLAPSE_ANGLE = -Math.PI / 2D;

	private int selectedIndex = -1;
	private long openedAtMs;
	private final float[] hoverProgress = new float[32]; // For smooth scaling animations

	public EmoteWheelScreen() {
		super(Component.empty());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	protected void init() {
		super.init();
		openedAtMs = Util.getMillis();
	}

	@Override
	public void tick() {
		super.tick();
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			onClose();
			return;
		}
		if (!EmoteWheelClient.isWheelKeyHeld(minecraft)) {
			confirmAndClose();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int screenW = guiGraphics.guiWidth();
		int screenH = guiGraphics.guiHeight();
		int centerX = screenW / 2;
		int centerY = screenH / 2;
		
		float openProgress = getOpenProgress();
		float eased = easeInOutSmoother(openProgress);
		int currentRadius = Mth.floor(Mth.lerp(eased, COLLAPSED_RADIUS, RADIUS));

		int fadeAlpha = Mth.floor(Mth.lerp(eased, 20.0F, 140.0F));
		guiGraphics.fill(0, 0, screenW, screenH, (fadeAlpha << 24));

		List<EmoteType> wheelItems = EmoteCatalog.WHEEL_ITEMS;
		int count = wheelItems.size();
		if (count == 0) return;

		selectedIndex = computeSelectedIndex(mouseX, mouseY, centerX, centerY, count);

		if (selectedIndex >= 0 && selectedIndex < count && minecraft != null) {
			EmoteType selected = wheelItems.get(selectedIndex);
			Component label = Component.translatable("stardewcraft.emote." + selected.id());
			int labelW = minecraft.font.width(label);
			guiGraphics.drawString(minecraft.font, label, centerX - labelW / 2, centerY - 8, 0xFFF4EED0, false);
		}

		for (int i = 0; i < count; i++) {
			boolean isHovered = (i == selectedIndex);
			hoverProgress[i] = Mth.clamp(hoverProgress[i] + (isHovered ? 0.15F : -0.1F), 0.0F, 1.0F);

			float hoverScale = Mth.lerp(hoverProgress[i], 1.0F, 1.5F);
			double angle = getAnimatedSegmentCenterAngle(i, count, eased);

			int x = centerX + (int) Math.round(Math.cos(angle) * currentRadius);
			int y = centerY + (int) Math.round(Math.sin(angle) * currentRadius);

			PoseStack poseStack = guiGraphics.pose();
			poseStack.pushPose();
			poseStack.translate(x, y, 0);
			poseStack.scale(hoverScale, hoverScale, 1.0F);

			RenderSystem.enableBlend();
			
			// 纯色精确轮廓描边
			if (hoverProgress[i] > 0) {
				float alpha = hoverProgress[i];
				
				// 设定纯金色（星露谷风格的 UI 高亮色）
				RenderSystem.setShaderColor(1.0F, 0.9F, 0.2F, alpha);
				
				// 上下左右各偏移 1 像素，利用自身透明通道贴图生成完美纯色外框
				drawEmoteIcon(guiGraphics, -ICON_SIZE / 2 - 1, -ICON_SIZE / 2,     ICON_SIZE, wheelItems.get(i));
				drawEmoteIcon(guiGraphics, -ICON_SIZE / 2 + 1, -ICON_SIZE / 2,     ICON_SIZE, wheelItems.get(i));
				drawEmoteIcon(guiGraphics, -ICON_SIZE / 2,     -ICON_SIZE / 2 - 1, ICON_SIZE, wheelItems.get(i));
				drawEmoteIcon(guiGraphics, -ICON_SIZE / 2,     -ICON_SIZE / 2 + 1, ICON_SIZE, wheelItems.get(i));
				
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // 恢复正常的颜色绘制本体
			}

			drawEmoteIcon(guiGraphics, -ICON_SIZE / 2, -ICON_SIZE / 2, ICON_SIZE, wheelItems.get(i));
			poseStack.popPose();
		}
	}

	public void confirmAndClose() {
		if (selectedIndex >= 0 && selectedIndex < EmoteCatalog.WHEEL_ITEMS.size()) {
			EmoteType emote = EmoteCatalog.WHEEL_ITEMS.get(selectedIndex);
			PacketDistributor.sendToServer(new EmoteUsePayload(emote.id()));
		}
		onClose();
	}

	@Override
	public void onClose() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen == this) {
			mc.setScreen(null);
		}
	}

	private static int computeSelectedIndex(int mouseX, int mouseY, int centerX, int centerY, int count) {
		double dx = mouseX - centerX;
		double dy = mouseY - centerY;
		double length = Math.sqrt(dx * dx + dy * dy);
		if (length < 24) {
			return -1;
		}

		double step = (Math.PI * 2D) / count;
		double normalized = Math.atan2(dy, dx) - (-Math.PI / 2D) + (step * 0.5D);
		while (normalized < 0D) {
			normalized += Math.PI * 2D;
		}
		while (normalized >= Math.PI * 2D) {
			normalized -= Math.PI * 2D;
		}

		int sector = Mth.floor(normalized / step);
		return Mth.clamp(sector, 0, count - 1);
	}

	private static double getAnimatedSegmentCenterAngle(int index, int count, float easedProgress) {
		double step = (Math.PI * 2D) / count;
		double target = (-Math.PI / 2D) + (step * index);
		return Mth.lerp(easedProgress, COLLAPSE_ANGLE, target);
	}

	private float getOpenProgress() {
		if (openedAtMs <= 0L) {
			return 1.0F;
		}
		long elapsed = Util.getMillis() - openedAtMs;
		return Mth.clamp(elapsed / (float) OPEN_ANIM_MS, 0.0F, 1.0F);
	}

	private static float easeInOutSmoother(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
	}

	private static void drawEmoteIcon(GuiGraphics guiGraphics, int x, int y, int size, EmoteType emote) {
		int frame = Math.max(0, Math.min(63, emote.iconIndex()));
		int pixelX = (frame * 16) % EMOTES_TEX_W;
		int pixelY = ((frame * 16) / EMOTES_TEX_W) * 16;
		guiGraphics.blit(EMOTES_TEXTURE, x, y, size, size, pixelX, pixelY, 16, 16, EMOTES_TEX_W, EMOTES_TEX_H);
	}
}
