package com.stardew.craft.client.fishing;

import com.stardew.craft.client.font.StardewFonts;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.LocalizedGuiAssets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Client-only: shows a Stardew-like "caught" popup (texture), then triggers Minecraft's item activation animation.
 * After animation, opens treasure chest UI if treasure was caught.
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishingCatchVisuals {
	private static final ResourceLocation TREASURE_ANIM_TEX = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/fishing/treasure_animate.png");
	private static final ResourceLocation TREASURE_GOLD_ANIM_TEX = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/fishing/treasure_gold_animate.png");

	// SDV FishingRod: Cursors[612,1913,74,30], drawn at scale 4.
	private static final int POPUP_W = 74 * 4;
	private static final int POPUP_H = 30 * 4;
	private static final float POPUP_SCALE = 0.75f;

	private static final long POPUP_DURATION_MS = 900L;
	private static final long HOOKED_POPUP_DURATION_MS = 450L;
	private static final long POPUP_FADE_OUT_MS = 200L;
	private static final long HOOKED_POPUP_FADE_OUT_MS = 120L;
	private static final long FAIL_FLASH_DURATION_MS = 220L;
	
	// 宝箱动画参数（原版：4帧，每帧200ms，总共800ms�?
	private static final int TREASURE_FRAME_COUNT = 4;
	private static final long TREASURE_FRAME_MS = 200L;
	private static final long TREASURE_ANIM_MS = TREASURE_FRAME_COUNT * TREASURE_FRAME_MS;
	private static final int TREASURE_FRAME_SIZE = 32; // 每帧32x32
	private static final float TREASURE_SCALE = 4.0f; // 放大4倍到128x128

	private enum Mode {
		NONE,
		HOOKED_POPUP_ONLY,
		FAIL_FLASH
	}

	private static Mode mode = Mode.NONE;
	private static ItemStack pendingStack = ItemStack.EMPTY;
	private static boolean pendingFail;
	private static long startMs;
	private static boolean showingTreasureAnim;
	private static boolean treasureUIOpened;
	private static long treasureAnimStartMs;
	
	private static long pendingTreasureChestId = -1L;
	private static boolean pendingTreasureGolden = false;

	private FishingCatchVisuals() {
	}

	@SuppressWarnings("null")
	public static void start(ItemStack stack) {
		// Per current spec: after the fish is caught, go straight to Minecraft's item-activation (totem) presentation.
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		pendingStack = stack.copy();
		pendingFail = false;
		mode = Mode.NONE;
		startMs = 0L;
		treasureUIOpened = false;
		showingTreasureAnim = false;

		mc.gameRenderer.displayItemActivation(pendingStack);
		spawnTotemLikeParticles(mc);
		if (mc.player != null) {
			mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
			mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
		}

		// If treasure exists, start the treasure animation immediately (it will open the UI afterward).
		if (pendingTreasureChestId >= 0L) {
			showingTreasureAnim = true;
			treasureAnimStartMs = Util.getMillis();
		}

		pendingStack = ItemStack.EMPTY;
	}

	/**
	 * Called when the player reacts to a bite (right-click after bite).
	 * Plays the Stardew-like popup texture animation only, then the server opens the minigame.
	 */
	public static void startHookedPopup() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		pendingStack = ItemStack.EMPTY;
		pendingFail = false;
		mode = Mode.HOOKED_POPUP_ONLY;
		startMs = Util.getMillis();
		showingTreasureAnim = false;
		treasureUIOpened = false;
	}
	
	public static void setPendingTreasure(long chestId, boolean golden) {
		pendingTreasureChestId = chestId;
		pendingTreasureGolden = golden;
		StardewCraft.LOGGER.info("Set pending treasure: chestId={}, golden={}", chestId, golden);
	}

	public static void startFail() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		pendingStack = ItemStack.EMPTY;
		pendingFail = true;
		mode = Mode.FAIL_FLASH;
		startMs = Util.getMillis();
		showingTreasureAnim = false;
		pendingTreasureChestId = -1L;
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || (mc.player != null && mc.player.isSpectator())) {
			return;
		}
		// \u5904\u7406\u9c7c\u6355\u83b7\u52a8\u753b
		if (mode != Mode.NONE && startMs != 0L) {
			renderFishCatchAnimation(event.getGuiGraphics());
		}
		
		// \u5904\u7406\u5b9d\u7bb1\u5f00\u542f\u52a8\u753b\uff08\u5728\u4e0d\u6b7b\u56fe\u817e\u52a8\u753b\u4e4b\u540e\uff09
		if (showingTreasureAnim) {
			renderTreasureAnimation(event.getGuiGraphics());
		}
	}
	
	@SuppressWarnings("null")
	private static void renderFishCatchAnimation(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}

		long now = Util.getMillis();
		long elapsed = now - startMs;
		long duration = switch (mode) {
			case FAIL_FLASH -> FAIL_FLASH_DURATION_MS;
			case HOOKED_POPUP_ONLY -> HOOKED_POPUP_DURATION_MS;
			default -> POPUP_DURATION_MS;
		};
		
		if (elapsed >= duration) {
			mode = Mode.NONE;
			pendingStack = ItemStack.EMPTY;
			pendingFail = false;
			startMs = 0L;
			return;
		}

		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();

		// Failure: full-screen subtle red flash (like a filter), no centered popup.
		if (mode == Mode.FAIL_FLASH) {
			float p = (float) elapsed / (float) duration;
			p = Mth.clamp(p, 0.0f, 1.0f);
			// Single pulse: 0 -> peak -> 0
			float pulse = (float) Math.sin((double) p * Math.PI);
			float alpha = 0.18f * pulse;
			int a = (int) (alpha * 255.0f);
			int color = (a << 24) | 0xFF0000;
			g.fill(0, 0, screenW, screenH, color);
			return;
		}

		float alpha = 1.0f;
		long fadeOut = (mode == Mode.HOOKED_POPUP_ONLY) ? HOOKED_POPUP_FADE_OUT_MS : POPUP_FADE_OUT_MS;
		long fadeStart = duration - fadeOut;
		if (elapsed > fadeStart) {
			alpha = 1.0f - ((float) (elapsed - fadeStart) / (float) fadeOut);
			alpha = Mth.clamp(alpha, 0.0f, 1.0f);
		}

		float bob = (mode == Mode.HOOKED_POPUP_ONLY ? 8.0f : 4.0f) * (float) Math.sin((double) now / (mode == Mode.HOOKED_POPUP_ONLY ? 170.0 : 250.0));

		int w = Math.round(POPUP_W * POPUP_SCALE);
		int h = Math.round(POPUP_H * POPUP_SCALE);
		int x = (screenW - w) / 2;
		int y = (screenH / 2) - h - 20 + (int) bob;
		y = Mth.clamp(y, 10, Math.max(10, screenH - h - 10));

		ResourceLocation tex = LocalizedGuiAssets.texture("fishing/caught_popup.png");
		@SuppressWarnings("null")
		boolean hasTex = mc.getResourceManager().getResource(tex).isPresent();
		if (!hasTex) {
			// For HOOKED_POPUP_ONLY, require the texture (avoid drawing placeholder rectangles or items).
			if (mode == Mode.HOOKED_POPUP_ONLY) {
				return;
			}
		}

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		g.setColor(1f, 1f, 1f, alpha);
		if (hasTex) {
			g.pose().pushPose();
			g.pose().translate(x, y, 0);
			g.pose().scale(POPUP_SCALE, POPUP_SCALE, 1f);
			g.blit(tex, 0, 0, 0, 0, POPUP_W, POPUP_H, POPUP_W, POPUP_H);
			g.pose().popPose();
		} else {
			int outer = ((int) (alpha * 220) << 24) | (pendingFail ? 0x3A0000 : 0x000000);
			int inner = ((int) (alpha * 240) << 24) | (pendingFail ? 0x6A1010 : 0x202020);
			g.fill(x, y, x + w, y + h, outer);
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, inner);
		}
		g.setColor(1f, 1f, 1f, 1f);

		if (!hasTex) {
			int iconX = x + w / 2 - 8;
			int iconY = y + h / 2 - 8;
			if (!pendingStack.isEmpty()) {
				g.renderItem(pendingStack, iconX, iconY);
				g.renderItemDecorations(StardewFonts.small(), pendingStack, iconX, iconY);
			}
		}
	}
	
	@SuppressWarnings("null")
	private static void renderTreasureAnimation(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		
		long now = Util.getMillis();
		long elapsed = now - treasureAnimStartMs;
		
		// 动画结束，打开宝箱UI
		if (elapsed >= TREASURE_ANIM_MS) {
			if (!treasureUIOpened) {
				treasureUIOpened = true;
				showingTreasureAnim = false;
				StardewCraft.LOGGER.info("Requesting server to open treasure chest, chestId={}", pendingTreasureChestId);
				mc.execute(() -> openTreasureChest(mc));
			}
			return;
		}
		
		// 计算当前帧（0-3）
		int currentFrame = (int) (elapsed / TREASURE_FRAME_MS);
		if (currentFrame >= TREASURE_FRAME_COUNT) currentFrame = TREASURE_FRAME_COUNT - 1;
		
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		
		// 渲染尺寸：32x32放大4倍 = 128x128
		int renderSize = (int) (TREASURE_FRAME_SIZE * TREASURE_SCALE);
		int x = (screenW - renderSize) / 2;
		int y = screenH / 2 - renderSize - 20;
		
		// alpha淡入效果（原版：从0开始，alphaFade = -0.002）
		float alpha = Math.min(1.0f, elapsed * 0.002f);
		
		// 轻微上浮效果（原版：motion = (0, -0.128)）
		float upwardMotion = elapsed * 0.128f / 1000f;
		
		ResourceLocation treasureTex = pendingTreasureGolden ? TREASURE_GOLD_ANIM_TEX : TREASURE_ANIM_TEX;
		
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		
		g.pose().pushPose();
		g.pose().translate(x, y - upwardMotion, 0);
		g.pose().scale(TREASURE_SCALE, TREASURE_SCALE, 1.0f);
		
		g.setColor(1f, 1f, 1f, alpha);
		// 绘制当前帧：从横向排列的帧中选择一帧
		// blit参数：目标x, y, 源u, v, 宽, 高, 纹理总宽, 纹理总高
		g.blit(treasureTex, 0, 0, currentFrame * TREASURE_FRAME_SIZE, 0, 
		       TREASURE_FRAME_SIZE, TREASURE_FRAME_SIZE,
		       TREASURE_FRAME_SIZE * TREASURE_FRAME_COUNT, TREASURE_FRAME_SIZE);
		g.setColor(1f, 1f, 1f, 1f);
		
		g.pose().popPose();
		RenderSystem.disableBlend();
	}
	
	
	@SuppressWarnings("null")
	private static void openTreasureChest(Minecraft mc) {
		if (mc.player == null) {
			StardewCraft.LOGGER.warn("Cannot open treasure: player is null");
			pendingTreasureChestId = -1L;
			return;
		}

		if (pendingTreasureChestId < 0L) {
			StardewCraft.LOGGER.warn("Cannot open treasure: missing chestId");
			return;
		}

		net.neoforged.neoforge.network.PacketDistributor.sendToServer(
				new com.stardew.craft.network.payload.OpenTreasureChestRequestPayload(pendingTreasureChestId)
		);

		pendingTreasureChestId = -1L;
		pendingTreasureGolden = false;
	}

	@SuppressWarnings("null")
	private static void spawnTotemLikeParticles(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		@SuppressWarnings("null")
		RandomSource r = mc.level.getRandom();
		for (int i = 0; i < 28; i++) {
			double dx = (r.nextDouble() - 0.5) * 0.6;
			double dy = r.nextDouble() * 0.8 + 0.1;
			double dz = (r.nextDouble() - 0.5) * 0.6;
			mc.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
					mc.player.getX(),
					mc.player.getY() + 1.0,
					mc.player.getZ(),
					dx, dy, dz
			);
		}
	}
}
