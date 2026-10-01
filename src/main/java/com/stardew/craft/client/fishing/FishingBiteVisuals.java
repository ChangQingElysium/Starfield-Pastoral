package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishingBiteVisuals {
	private FishingBiteVisuals() {
	}

	private static final Map<Integer, Long> dipUntilMsByHookId = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> dipStartMsByHookId = new ConcurrentHashMap<>();
	private static volatile long bitePromptUntilMs;
	private static volatile long bitePromptStartMs;

	// NOTE: This texture should be provided by the project/resourcepack.
	// If missing, we intentionally render nothing (no text fallback) to avoid deviating from the spec.
	private static final ResourceLocation EXCLAMATION_TEX = new ResourceLocation(
			StardewCraft.MODID,
			"textures/gui/fishing/bite_exclamation.png"
	);
	private static final float EXCLAMATION_TARGET_PX = 28f;
	private static final float EXCLAMATION_JUMP_PX = 22f;
	private static final Map<ResourceLocation, int[]> TEX_SIZE_CACHE = new ConcurrentHashMap<>();

    /** The complete scope surrounds the renderer, including cancellation or exceptions inside it. */
    public static void renderHook(com.mojang.blaze3d.vertex.PoseStack pose,boolean nativeTackle,float dip,Runnable vanilla) {
        if(nativeTackle)return;
        if(dip==0){vanilla.run();return;}
        pose.pushPose();
        try {pose.translate(0,dip,0);vanilla.run();}
        finally {pose.popPose();}
    }

	public static void clear() {
		dipUntilMsByHookId.clear();
		dipStartMsByHookId.clear();
		bitePromptUntilMs = 0;
		bitePromptStartMs = 0;
	}

	public static void clearTexSizeCache() {
		TEX_SIZE_CACHE.clear();
	}

	public static void startBitePrompt(int hookEntityId, int durationTicks) {
		long now = System.currentTimeMillis();
		long until = now + (durationTicks * 50L);
		bitePromptStartMs = now;
		bitePromptUntilMs = Math.max(bitePromptUntilMs, until);
		if (hookEntityId >= 0) {
			dipStartMsByHookId.put(hookEntityId, now);
			dipUntilMsByHookId.put(hookEntityId, until);
		}
	}

	public static void startHookedAnim(int durationTicks) {
		// The "Hooked!" animation is the existing caught popup texture animation.
		// It should play exactly when the player reacts to the bite.
		FishingCatchVisuals.startHookedPopup();
	}

	public static float getBobberDipOffsetY(int hookEntityId) {
		Long until = dipUntilMsByHookId.get(hookEntityId);
		if (until == null) {
			return 0f;
		}
		Long start = dipStartMsByHookId.get(hookEntityId);
		if (start == null) {
			start = until - 900L;
		}
		long now = System.currentTimeMillis();
		if (now >= until) {
			dipUntilMsByHookId.remove(hookEntityId);
			dipStartMsByHookId.remove(hookEntityId);
			return 0f;
		}
		long durationMs = Math.max(1L, until - start);
		float t = (float) (now - start) / (float) durationMs;
		t = Mth.clamp(t, 0f, 1f);
		// Simple "dip" curve: down then back up.
		float down = Mth.sin(t * Mth.PI);
		return -0.12f * down;
	}

	private static int[] getTextureSize(ResourceLocation tex) {
		int[] cached = TEX_SIZE_CACHE.get(tex);
		if (cached != null) {
			return cached;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return new int[]{16, 16};
		}
		try {
			@SuppressWarnings("null")
			var resOpt = mc.getResourceManager().getResource(tex);
			if (resOpt.isEmpty()) {
				return new int[]{16, 16};
			}
			try (InputStream in = resOpt.get().open()) {
				try (@SuppressWarnings("null")
				NativeImage img = NativeImage.read(in)) {
					int w = Math.max(1, img.getWidth());
					int h = Math.max(1, img.getHeight());
					int[] out = new int[]{w, h};
					TEX_SIZE_CACHE.put(tex, out);
					return out;
				}
			}
		} catch (Exception ignored) {
			return new int[]{16, 16};
		}
	}

	private static float easeOutBack(float t) {
		// t in [0,1]
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + (c3 * u * u * u) + (c1 * u * u);
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		if (mc.options.hideGui || mc.player.isSpectator()) {
			return;
		}
		// Don't render on top of the minigame.
		if (FishingMinigameHud.active()) {
			return;
		}

		long now = System.currentTimeMillis();
		GuiGraphics g = event.getGuiGraphics();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();

		if (now < bitePromptUntilMs) {
			@SuppressWarnings("null")
			boolean hasTex = mc.getResourceManager().getResource(EXCLAMATION_TEX).isPresent();
			if (hasTex) {
				long start = bitePromptStartMs;
				long until = bitePromptUntilMs;
				long durationMs = Math.max(1L, until - start);
				float t = (float) (now - start) / (float) durationMs;
				t = Mth.clamp(t, 0f, 1f);
				float pop = easeOutBack(t);
				float alpha = 1f;
				if (t > 0.75f) {
					float f = (t - 0.75f) / 0.25f;
					f = Mth.clamp(f, 0f, 1f);
					alpha = 1f - (f * f);
				}

				int[] size = getTextureSize(EXCLAMATION_TEX);
				int texW = size[0];
				int texH = size[1];
				float baseScale = EXCLAMATION_TARGET_PX / (float) texH;
				float scale = baseScale * (1f + 0.18f * pop);
				int x = w / 2;
				int y = h / 2 + 6;
				float yOff = -EXCLAMATION_JUMP_PX * pop;

				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				g.setColor(1f, 1f, 1f, alpha);
				g.pose().pushPose();
				g.pose().translate(x, y + yOff, 0);
				g.pose().scale(scale, scale, 1f);
				g.blit(EXCLAMATION_TEX, -texW / 2, -texH / 2, 0, 0, texW, texH, texW, texH);
				g.pose().popPose();
				g.setColor(1f, 1f, 1f, 1f);
			}
		}
	}
}
