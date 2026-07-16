package com.stardew.craft.client.hud;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.LocalizedGuiAssets;
import com.stardew.craft.client.sound.LoopingVariablePitchSoundInstance;
import com.stardew.craft.fishing.FishingCastPower;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.InputStream;
import java.util.Optional;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishingCastHud {
	private static final ResourceLocation TIMING_CAST_TEX = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/fishing/timing_cast.png");

	// Stardew: background source is 47x12 at scale 4 => 188x48.
	private static final int FALLBACK_BG_W = 47 * 4;
	private static final int FALLBACK_BG_H = 12 * 4;
	// Stardew: fill is 164px wide, 25px tall; placed 12px below background top.
	private static final int FILL_W = 164;
	private static final int FILL_H = 25;
	private static final int FILL_OFFSET_X = 12;
	private static final int FILL_OFFSET_Y = 12;

	// Stardew MAX sprite: 53x19 at scale 2 => 106x38.
	private static final int FALLBACK_MAX_W = 53 * 2;
	private static final int FALLBACK_MAX_H = 19 * 2;
	private static final int MAX_POP_DELAY_MS = 200;
	private static final int MAX_POP_TOTAL_MS = 800;

	private FishingCastHud() {
	}

	private static boolean wasUsing;
	private static int postReleaseCountdownMs;
	private static int maxPopMs;
	private static long lastFrameMs;
	private static LoopingVariablePitchSoundInstance sinWaveLoop;

	private static int timingTexW;
	private static int timingTexH;
	private static int maxTexW;
	private static int maxTexH;

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		// Never render the casting HUD behind the minigame.
		if (mc.screen instanceof com.stardew.craft.client.fishing.FishingMinigameScreen
				|| mc.screen instanceof StardewHudLayoutEditorScreen) {
			stopSinWave(mc);
			return;
		}
		Player player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		if (mc.options.hideGui || player.isSpectator()) {
			return;
		}

		long now = System.currentTimeMillis();
		int dt = 0;
		if (lastFrameMs != 0L) {
			dt = (int) Mth.clamp(now - lastFrameMs, 0L, 100L);
		}
		lastFrameMs = now;

		boolean usingRod = player.isUsingItem() && player.getUseItem().getItem() instanceof com.stardew.craft.item.tool.FishingRodItem;
		float progress = 0f;
		if (usingRod) {
			ItemStack using = player.getUseItem();
			int usedTicks = using.getUseDuration(player) - player.getUseItemRemainingTicks();
			progress = FishingCastPower.getCastPower01FromUsedTicks(usedTicks);
			if (progress > 0.99f) {
			}
		}

		// SV FishingRod: chargeSound loops while charging; pitch rises with power.
		if (!usingRod) {
			stopSinWave(mc);
		} else {
			float pitch = 0.8f + (Mth.clamp(progress, 0f, 1f) * 0.6f);
			ensureSinWavePlaying(mc, pitch);
		}

		// release edge: per current spec, hide immediately on release/cancel.
		if (wasUsing && !usingRod) {
			postReleaseCountdownMs = 0;
			maxPopMs = 0;
			stopSinWave(mc);
		}
		wasUsing = usingRod;

		if (postReleaseCountdownMs > 0) {
			postReleaseCountdownMs = Math.max(0, postReleaseCountdownMs - dt);
		}
		if (maxPopMs > 0) {
			maxPopMs = Math.max(0, maxPopMs - dt);
		}

		if (!usingRod) {
			return;
		}

		renderTimingCast(event.getGuiGraphics(), mc.font, progress, usingRod);
	}

	@SuppressWarnings("null")
	private static void renderTimingCast(GuiGraphics g, Font font, float progress, boolean usingRod) {
		Minecraft mc = Minecraft.getInstance();
		int srcW = getTextureWidthOrFallback(TIMING_CAST_TEX, FALLBACK_BG_W);
		int srcH = getTextureHeightOrFallback(TIMING_CAST_TEX, FALLBACK_BG_H);
		float baseScale = computeTimingCastScale(srcW, srcH);
		float bgScale = baseScale;
		int bgW = Math.round(srcW * bgScale);
		int bgH = Math.round(srcH * bgScale);

		StardewHudLayout.Placement placement = StardewHudLayout.current(
			Config.HudElement.FISHING_CAST, g.guiWidth(), g.guiHeight());
		int x = 0;
		int yBase = 0;

		int yOffset = 0;
		float alpha = 1f;
		if (!usingRod && postReleaseCountdownMs > 0) {
			// Stardew draw():
			// yOffset = (0 - abs(t/2 - t)) / 50
			// alpha = (t in (0,100)) ? t/100 : 1
			float t = postReleaseCountdownMs;
			yOffset = (int) ((0f - Math.abs(t / 2f - t)) / 50f);
			alpha = (t > 0f && t < 100f) ? (t / 100f) : 1f;
		}

		int y = yBase + yOffset;
		g.pose().pushPose();
		g.pose().translate(placement.x(), placement.y(), 0.0F);
		g.pose().scale(placement.scale(), placement.scale(), 1.0F);

		// background
		g.setColor(1f, 1f, 1f, alpha);
		g.pose().pushPose();
		g.pose().translate(0.0, 0.0, 0.0);
		g.pose().scale(bgScale, bgScale, 1.0f);
		g.blit(TIMING_CAST_TEX, Math.round(x / bgScale), Math.round(y / bgScale), 0, 0, srcW, srcH, srcW, srcH);
		g.pose().popPose();
		g.setColor(1f, 1f, 1f, 1f);

		// fill bar is expressed in post-scale pixels in Stardew; only apply our global shrink.
		float p = Mth.clamp(progress, 0f, 1f);
		int fillColor = (Mth.hsvToRgb((p * 0.33f), 1f, 1f) | 0xFF000000);
		int a = (int) (alpha * 255f);
		fillColor = (fillColor & 0x00FFFFFF) | (a << 24);

		// Scale offsets and sizes proportionally to the rendered background to avoid rounding drift at small HUD_SCALE.
		float oxRatio = (float) FILL_OFFSET_X / (float) FALLBACK_BG_W;
		float oyRatio = (float) FILL_OFFSET_Y / (float) FALLBACK_BG_H;
		float wRatio = (float) FILL_W / (float) FALLBACK_BG_W;
		float hRatio = (float) FILL_H / (float) FALLBACK_BG_H;

		int ox = x + Math.round(bgW * oxRatio);
		int oy = y + Math.round(bgH * oyRatio);
		int fw = Math.round(bgW * wRatio * p);
		int fh = Math.round(bgH * hRatio);
		if (fw > 0 && fh > 0) {
			g.fill(ox, oy, ox + fw, oy + fh, fillColor);
		}

		renderMaxPopup(g, x, y, bgW, 1.0F);
		g.pose().popPose();
	}

	private static float computeTimingCastScale(int srcW, int srcH) {
		// Stardew sprite is 47x12, drawn at scale 4.
		// If the provided PNG is already scaled (e.g., 188x48), keep scale 1.
		if (srcW > 0 && srcH > 0 && srcW <= 64 && srcH <= 32) {
			return 4.0f;
		}
		return 1.0f;
	}

	@SuppressWarnings("null")
	private static void renderMaxPopup(GuiGraphics g, int barX, int barY, int bgW, float hudScale) {
		if (maxPopMs <= 0) {
			return;
		}
		int elapsed = MAX_POP_TOTAL_MS - maxPopMs;
		if (elapsed < MAX_POP_DELAY_MS) {
			return;
		}

		// Approximate Stardew temp sprite motion: start at -4, accel +0.2 per tick.
		float t = (elapsed - MAX_POP_DELAY_MS) / 50f;
		float yMotion = (-4f * t) + (0.5f * 0.2f * t * t);

		ResourceLocation maxTexture = LocalizedGuiAssets.texture("fishing/max.png");
		int maxSrcW = getTextureWidthOrFallback(maxTexture, FALLBACK_MAX_W);
		int maxSrcH = getTextureHeightOrFallback(maxTexture, FALLBACK_MAX_H);
		float maxScale = computeMaxScale(maxSrcW, maxSrcH) * hudScale;
		int maxW = Math.round(maxSrcW * maxScale);
		int maxH = Math.round(maxSrcH * maxScale);
		int x = barX + (bgW - maxW) / 2;
		int y = barY - maxH - 8 + (int) yMotion;

		if (hasResource(maxTexture)) {
			g.pose().pushPose();
			g.pose().scale(maxScale, maxScale, 1.0f);
			g.blit(maxTexture, Math.round(x / maxScale), Math.round(y / maxScale), 0, 0, maxSrcW, maxSrcH, maxSrcW, maxSrcH);
			g.pose().popPose();
		}
	}

	@SuppressWarnings("null")
	private static void ensureSinWavePlaying(Minecraft mc, float pitch) {
		if (mc.player == null) {
			return;
		}
		if (sinWaveLoop == null) {
			sinWaveLoop = new LoopingVariablePitchSoundInstance(ModSounds.SIN_WAVE.get(), 0.6f, pitch);
			mc.getSoundManager().play(sinWaveLoop);
		} else {
			sinWaveLoop.setPitch(pitch);
		}
	}

	@SuppressWarnings("null")
	private static void stopSinWave(Minecraft mc) {
		if (sinWaveLoop == null) {
			return;
		}
		sinWaveLoop.stopNow();
		mc.getSoundManager().stop(sinWaveLoop);
		sinWaveLoop = null;
	}

	private static float computeMaxScale(int srcW, int srcH) {
		// Stardew MAX sprite is 53x19, drawn at scale 2.
		if (srcW > 0 && srcH > 0 && srcW <= 80 && srcH <= 40) {
			return 2.0f;
		}
		return 1.0f;
	}

	@SuppressWarnings("null")
	private static boolean hasResource(ResourceLocation loc) {
		ResourceManager rm = Minecraft.getInstance().getResourceManager();
		return rm.getResource(loc).isPresent();
	}

	private static int getTextureWidthOrFallback(ResourceLocation loc, int fallback) {
		ensureDimensionsLoaded();
		if (loc.equals(TIMING_CAST_TEX) && timingTexW > 0) {
			return timingTexW;
		}
		if (isMaxTexture(loc) && maxTexW > 0) {
			return maxTexW;
		}
		return fallback;
	}

	private static int getTextureHeightOrFallback(ResourceLocation loc, int fallback) {
		ensureDimensionsLoaded();
		if (loc.equals(TIMING_CAST_TEX) && timingTexH > 0) {
			return timingTexH;
		}
		if (isMaxTexture(loc) && maxTexH > 0) {
			return maxTexH;
		}
		return fallback;
	}

	private static void ensureDimensionsLoaded() {
		if (timingTexW == 0 && timingTexH == 0) {
			int[] dim = tryReadPngDimensions(TIMING_CAST_TEX);
			timingTexW = dim[0];
			timingTexH = dim[1];
		}
		if (maxTexW == 0 && maxTexH == 0) {
			int[] dim = tryReadPngDimensions(LocalizedGuiAssets.texture("fishing/max.png"));
			maxTexW = dim[0];
			maxTexH = dim[1];
		}
	}

	private static int[] tryReadPngDimensions(ResourceLocation loc) {
		try {
			ResourceManager rm = Minecraft.getInstance().getResourceManager();
			@SuppressWarnings("null")
			Optional<Resource> res = rm.getResource(loc);
			if (res.isEmpty()) {
				return new int[] { 0, 0 };
			}
			try (InputStream in = res.get().open()) {
				@SuppressWarnings("null")
				NativeImage img = NativeImage.read(in);
				int w = img.getWidth();
				int h = img.getHeight();
				img.close();
				return new int[] { w, h };
			}
		} catch (Exception e) {
			return new int[] { 0, 0 };
		}
	}

	private static boolean isMaxTexture(ResourceLocation loc) {
		return loc.getPath().endsWith("/fishing/max.png");
	}
}
