package com.stardew.craft.client.fishing;

import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.fishing.network.FishingResultPayload;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;


import java.util.Random;
import java.util.UUID;

public final class FishingMinigameScreen extends Screen implements com.stardew.craft.client.gui.StardewRealtimeScreen {
	private static final float UI_SCALE = 0.7f;
	// Stardew values (see StardewValley.Menus.BobberBar)
	private static final int BOBBER_TRACK_HEIGHT = 548;
	private static final int BOBBER_BAR_TRACK_HEIGHT = 568;
	private static final int TIME_PER_FISH_SIZE_REDUCTION_MS = 800;

	// Stardew UI layout constants (from BobberBar)
	private static final int UI_TRACK_CENTER_X = 70;
	private static final int UI_TRACK_CENTER_Y = 296;
	private static final int UI_BAR_X = 64;
	private static final int UI_BAR_Y = 12;
	private static final int UI_FISH_X = 64 + 18;
	private static final int UI_FISH_Y = 12 + 24;
	private static final int UI_PROGRESS_X = 124;
	private static final int UI_PROGRESS_Y = 4;
	private static final int UI_PROGRESS_HEIGHT = 580;
	private static final int UI_PROGRESS_WIDTH = 16;
	private static final int UI_REEL_X = 18;
	private static final int UI_REEL_Y = 514;

	// Textured UI pieces (user-provided). If missing, we fall back to simple rectangles.
	private static final ResourceLocation TEX_BUBBLE = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/bubble.png");
	private static final ResourceLocation TEX_TRACK = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/track.png");
	private static final ResourceLocation TEX_BAR_TOP = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/bar_top.png");
	private static final ResourceLocation TEX_BAR_MID = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/bar_mid.png");
	private static final ResourceLocation TEX_BAR_BOTTOM = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/bar_bottom.png");
	private static final ResourceLocation TEX_FISH = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/fish.png");
	private static final ResourceLocation TEX_FISH_BOSS = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/fish_boss.png");
	private static final ResourceLocation TEX_REEL = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/reel.png");
	private static final ResourceLocation TEX_TREASURE = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/treasure.png");
	private static final ResourceLocation TEX_TREASURE_GOLD = ResourceLocation.fromNamespaceAndPath("stardewcraft", "textures/gui/fishing/treasure_gold.png");

	private final UUID sessionId;
	private final float difficulty;
	private final int motionType;
	private final boolean legendaryFish;
	private int remainingTicks;
	
	// 渔具效果
	private final int barSizeBonus;    // Cork Bobber（+24像素/个）等像素加成
	private final float escapeLossPerTick;    // Trap Bobber（不在条内时掉进度）
	private final int barbedHookCount;        // Barbed Hook（吸附/重力）
	private final int leadBobberCount;        // Lead Bobber（底部反弹衰减）
	private final int treasureHunterCount;    // Treasure Hunter（捕获宝箱时保护鱼的进度）
	private final int minFishSize;
	private final int maxFishSize;
	private final boolean hasSonarBobber;
	private final String sonarFishItemId;
	private net.minecraft.world.item.ItemStack sonarFishStack;

	private final Random random;

	private boolean sentResult;
	private long lastReelSoundMs;
	private boolean hasChallengeBait;
	private int challengeBaitFishes;

	// Timing for SV-like 60fps simulation
	private long lastUpdateMs;
	private long accumulatedMs;

	// SV state
	private float scale;
	private boolean fadeIn;
	private boolean fadeOut;
	private float everythingShakeTimer;
	private float everythingShakeX;
	private float everythingShakeY;
	private float barShakeX;
	private float barShakeY;
	private float fishShakeX;
	private float fishShakeY;
	private float reelRotation;

	private float bobberPosition;
	private float bobberSpeed;
	private float bobberAcceleration;
	private float bobberTargetPosition;
	private float floaterSinkerAcceleration;
	private int bobberBarHeight;
	private float bobberBarPos;
	private float bobberBarSpeed;
	private boolean bobberInBar;
	private boolean buttonPressed;
	private boolean mouseHeld;
	private boolean perfect;
	private final float initialCatchProgress;
	private final boolean loseProgressOutsideBar;
	private float distanceFromCatching;

	// Fish size shrink: BobberBar reduces fishSize by 1 every 800ms while outside the bar.
	private int currentFishSize;
	private int fishSizeReductionTimerMs;

	// Treasure (宝箱)
	private final boolean hasTreasure;
	private final boolean goldenTreasure;
	private float treasurePosition;
	private float treasureCatchLevel;
	private float treasureAppearTimer;
	private float treasureScale;
	private float treasureShakeX;
	private float treasureShakeY;
	private boolean treasureCaught;

	// Cached texture availability checks (per screen instance)
	private Boolean hasTexturedUi;

	public FishingMinigameScreen(UUID sessionId, int difficulty, int motionTypeId, boolean legendaryFish, int durationTicks,
	                             boolean hasTreasure, boolean goldenTreasure,
	                             boolean hasSonarBobber, String sonarFishItemId,
	                             int barSizeBonus, float escapeLossPerTick, int barbedHookCount, int leadBobberCount,
	                             int treasureHunterCount,
	                             int minFishSize, int maxFishSize, int currentFishSize,
	                             float initialCatchProgress, boolean loseProgressOutsideBar) {
		// Title should not render in the UI; keep screen title empty.
		super(Component.empty());
		this.sessionId = sessionId;
		int d = Math.max(1, difficulty);
		// Our data JSON initially used a small scale (e.g. 1-10). Stardew's BobberBar logic expects ~0-100.
		if (d <= 10) {
			d *= 10;
		}
		this.difficulty = d;
		this.motionType = motionTypeId;
		this.legendaryFish = legendaryFish;
		this.remainingTicks = (durationTicks <= 0) ? -1 : Math.max(20, durationTicks);
		this.random = new Random(sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits());
		this.hasTreasure = hasTreasure;
		this.goldenTreasure = goldenTreasure;
		this.hasSonarBobber = hasSonarBobber;
		this.sonarFishItemId = (sonarFishItemId == null) ? "" : sonarFishItemId;
		this.barSizeBonus = barSizeBonus;
		this.escapeLossPerTick = escapeLossPerTick;
		this.barbedHookCount = Math.max(0, barbedHookCount);
		this.leadBobberCount = Math.max(0, leadBobberCount);
		this.treasureHunterCount = Math.max(0, treasureHunterCount);
		this.minFishSize = Math.max(0, minFishSize);
		this.maxFishSize = Math.max(this.minFishSize, maxFishSize);
		this.currentFishSize = Mth.clamp(currentFishSize, this.minFishSize, Math.max(this.maxFishSize + 1, currentFishSize));
		this.initialCatchProgress = Mth.clamp(initialCatchProgress, 0.0F, 1.0F);
		this.loseProgressOutsideBar = loseProgressOutsideBar;
		this.sonarFishStack = net.minecraft.world.item.ItemStack.EMPTY;
	}

	@SuppressWarnings("null")
	@Override
	protected void init() {
		super.init();
		this.sentResult = false;
		this.mouseHeld = false;
		this.hasChallengeBait = false;
		this.challengeBaitFishes = -1;

		this.scale = 0f;
		this.fadeIn = true;
		this.fadeOut = false;
		this.everythingShakeTimer = 0f;
		this.everythingShakeX = 0f;
		this.everythingShakeY = 0f;
		this.barShakeX = 0f;
		this.barShakeY = 0f;
		this.fishShakeX = 0f;
		this.fishShakeY = 0f;
		this.reelRotation = 0f;

		int fishingLevel = ClientPlayerDataCache.getSkillLevel(SkillType.FISHING);
		if (Minecraft.getInstance().player != null
				&& StardewEnchantments.has(FishingRodItem.findRod(Minecraft.getInstance().player), StardewEnchantments.MASTER)) {
			fishingLevel++;
		}
		// 浮标大小（星露谷逻辑）：基础(96+等级*8) + 像素加成
		this.bobberBarHeight = (96 + fishingLevel * 8) + barSizeBonus;
		this.bobberBarPos = BOBBER_BAR_TRACK_HEIGHT - bobberBarHeight;
		this.bobberBarSpeed = 0f;

		this.bobberPosition = 508f;
		this.bobberTargetPosition = (100f - this.difficulty) / 100f * BOBBER_TRACK_HEIGHT;
		this.bobberSpeed = 0f;
		this.bobberAcceleration = 0f;
		this.floaterSinkerAcceleration = 0f;
		this.bobberInBar = false;
		this.buttonPressed = false;
		this.perfect = true;
		this.distanceFromCatching = initialCatchProgress;

		this.fishSizeReductionTimerMs = TIME_PER_FISH_SIZE_REDUCTION_MS;

		// 宝箱初始化
		this.treasurePosition = 0f;
		this.treasureCatchLevel = 0f;
		this.treasureAppearTimer = hasTreasure ? (float) random.nextInt(1000, 3000) : -1f;
		this.treasureScale = 0f;
		this.treasureShakeX = 0f;
		this.treasureShakeY = 0f;
		this.treasureCaught = false;

		this.lastUpdateMs = Util.getMillis();
		this.accumulatedMs = 0L;
		this.hasTexturedUi = null;
		this.lastReelSoundMs = 0L;

		this.sonarFishStack = net.minecraft.world.item.ItemStack.EMPTY;
		if (hasSonarBobber && !sonarFishItemId.isBlank()) {
			try {
				ResourceLocation id = ResourceLocation.tryParse(sonarFishItemId);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					this.sonarFishStack = new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id));
				}
			} catch (Exception ignored) {
				this.sonarFishStack = net.minecraft.world.item.ItemStack.EMPTY;
			}
		}

		// Detect bait locally (SV BobberBar checks rod.GetBait()).
		var mc = Minecraft.getInstance();
		if (mc != null && mc.player != null) {
			var rod = mc.player.getMainHandItem();
			if (rod.isEmpty() || !(rod.getItem() instanceof FishingRodItem)) {
				rod = mc.player.getOffhandItem();
			}
			if (!rod.isEmpty() && rod.getItem() instanceof FishingRodItem) {
				FishingRodItem.hasBait(rod, "stardewcraft:wild_bait");
				this.hasChallengeBait = FishingRodItem.hasBait(rod, "stardewcraft:challenge_bait");
				if (this.hasChallengeBait) {
					this.challengeBaitFishes = 3;
				}
			}
		}
	}

	@SuppressWarnings("null")
	private void playLocal(SoundEvent sound, float volume, float pitch) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		minecraft.player.playSound(sound, volume, pitch);
	}

	private boolean hasTexturedUi() {
		if (hasTexturedUi != null) {
			return hasTexturedUi;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			hasTexturedUi = false;
			return false;
		}
		try {
			// Track + fish + bar middle is enough to consider textures present.
			@SuppressWarnings("null")
			boolean ok = mc.getResourceManager().getResource(TEX_TRACK).isPresent()
					&& mc.getResourceManager().getResource(TEX_FISH).isPresent()
					&& mc.getResourceManager().getResource(TEX_BAR_MID).isPresent();
			hasTexturedUi = ok;
			return ok;
		} catch (Exception ignored) {
			hasTexturedUi = false;
			return false;
		}
	}

	private static float computeFitScale(int screenW, int screenH) {
		// Stardew's bobber UI track renders to ~600px tall (38x150 scaled by 4).
		float fitH = (screenH - 40f) / 600f;
		float fitW = (screenW - 40f) / 220f;
		return Mth.clamp(Math.min(fitH, fitW) * UI_SCALE, 0.45f, 1.25f);
	}

	private static void beginAlpha(float alpha) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
	}

	private static void endAlpha() {
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	@SuppressWarnings("null")
	private static void blitCenteredScaled(GuiGraphics g, ResourceLocation tex, float cx, float cy, int w, int h, float scale) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(cx, cy, 0f);
		pose.scale(scale, scale, 1f);
		g.blit(tex, -w / 2, -h / 2, 0, 0, w, h, w, h);
		pose.popPose();
	}

	@SuppressWarnings("null")
	private static void blitTopLeftScaled(GuiGraphics g, ResourceLocation tex, float x, float y, int w, int h, float scaleX, float scaleY) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0f);
		pose.scale(scaleX, scaleY, 1f);
		g.blit(tex, 0, 0, 0, 0, w, h, w, h);
		pose.popPose();
	}

	@SuppressWarnings("null")
	private static void blitRotated(GuiGraphics g, ResourceLocation tex, float x, float y, int w, int h, float originX, float originY, float rotationRad, float scale) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0f);
		pose.mulPose(Axis.ZP.rotation(rotationRad));
		pose.scale(scale, scale, 1f);
		g.blit(tex, (int) (-originX), (int) (-originY), 0, 0, w, h, w, h);
		pose.popPose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (minecraft == null) {
			return;
		}
		// Stardew has no explicit per-fish countdown in the minigame.
		if (remainingTicks > 0) {
			remainingTicks--;
			if (remainingTicks <= 0 && !fadeOut) {
				// 兼容旧包：如果服务端仍下发了倒计时，就按“鱼逃跑”处理。
				emergencyShutDown();
			}
		}
	}

	private static int safeNext(Random random, int minValue, int maxValue) {
		if (minValue >= maxValue) {
			return maxValue;
		}
		return random.nextInt(maxValue - minValue) + minValue;
	}

	@SuppressWarnings("null")
	private boolean isUsePressed() {
		if (minecraft == null) {
			return false;
		}
		// Stardew: LeftMouse / useToolButton / gamepad X/A
		return mouseHeld || minecraft.options.keyUse.isDown() || minecraft.options.keyJump.isDown();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// 0 = LMB, 1 = RMB
		if (button == 0 || button == 1) {
			mouseHeld = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 || button == 1) {
			mouseHeld = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@SuppressWarnings("null")
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Space / jump as a backup (some users prefer keyboard)
		if (minecraft != null && minecraft.options.keyJump.matches(keyCode, scanCode)) {
			mouseHeld = true;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@SuppressWarnings("null")
	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (minecraft != null && minecraft.options.keyJump.matches(keyCode, scanCode)) {
			mouseHeld = false;
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private void emergencyShutDown() {
		// Immediate close (no fade linger). Treat as escaped.
		if (!sentResult) {
			playLocal(ModSounds.FISH_ESCAPE.get(), 1.0f, 1.0f);
		}
		distanceFromCatching = -1f;
		finish(false);
	}

	private void svUpdateStep(int elapsedMs) {
		// Mirrors StardewValley.Menus.BobberBar.update(GameTime)
		if (sentResult) {
			return;
		}
		if (everythingShakeTimer > 0f) {
			everythingShakeTimer -= elapsedMs;
			everythingShakeX = (float) (random.nextInt(21) - 10) / 10f;
			everythingShakeY = (float) (random.nextInt(21) - 10) / 10f;
			if (everythingShakeTimer <= 0f) {
				everythingShakeX = 0f;
				everythingShakeY = 0f;
			}
		}

		if (fadeIn) {
			scale += 0.05f;
			if (scale >= 1f) {
				scale = 1f;
				fadeIn = false;
			}
			return;
		}

		if (fadeOut) {
			scale -= 0.05f;
			if (scale <= 0f) {
				scale = 0f;
				fadeOut = false;
				boolean success = distanceFromCatching >= 1.0f;
				finish(success);
			}
			return;
		}

		// Fish movement / target selection
		double motionMultiplier = (motionType != 2) ? 1.0 : 20.0;
		if (random.nextDouble() < (double) (difficulty * (float) motionMultiplier / 4000f)
				&& (motionType != 2 || bobberTargetPosition == -1f)) {
			float spaceBelow = BOBBER_TRACK_HEIGHT - bobberPosition;
			float spaceAbove = bobberPosition;
			float percent = Math.min(99f, difficulty + (float) (random.nextInt(35) + 10)) / 100f;
			int min = (int) Math.min(0f - spaceAbove, spaceBelow);
			int max = (int) spaceBelow;
			bobberTargetPosition = bobberPosition + (float) safeNext(random, min, max) * percent;
		}

		switch (motionType) {
			case 4 -> floaterSinkerAcceleration = Math.max(floaterSinkerAcceleration - 0.01f, -1.5f);
			case 3 -> floaterSinkerAcceleration = Math.min(floaterSinkerAcceleration + 0.01f, 1.5f);
			default -> {
			}
		}

		if (Math.abs(bobberPosition - bobberTargetPosition) > 3f && bobberTargetPosition != -1f) {
			bobberAcceleration = (bobberTargetPosition - bobberPosition)
					/ ((float) (random.nextInt(20) + 10) + (100f - Math.min(100f, difficulty)));
			bobberSpeed += (bobberAcceleration - bobberSpeed) / 5f;
		} else if (motionType != 2 && random.nextDouble() < (double) (difficulty / 2000f)) {
			bobberTargetPosition = bobberPosition + (float) (random.nextBoolean() ? (-(random.nextInt(50) + 51)) : (random.nextInt(51) + 50));
		} else {
			bobberTargetPosition = -1f;
		}

		if (motionType == 1 && random.nextDouble() < (double) (difficulty / 1000f)) {
			int min = -100 - (int) difficulty * 2;
			int max = -51;
			int min2 = 50;
			int max2 = 101 + (int) difficulty * 2;
			bobberTargetPosition = bobberPosition + (float) (random.nextBoolean() ? safeNext(random, min, max) : safeNext(random, min2, max2));
		}

		bobberTargetPosition = Math.max(-1f, Math.min(bobberTargetPosition, (float) BOBBER_TRACK_HEIGHT));
		bobberPosition += bobberSpeed + floaterSinkerAcceleration;
		if (bobberPosition > 532f) {
			bobberPosition = 532f;
		} else if (bobberPosition < 0f) {
			bobberPosition = 0f;
		}

		bobberInBar = bobberPosition + 12f <= bobberBarPos - 32f + (float) bobberBarHeight
				&& bobberPosition - 16f >= bobberBarPos - 32f;
		if (bobberPosition >= (float) (BOBBER_TRACK_HEIGHT - bobberBarHeight)
				&& bobberBarPos >= (float) (BOBBER_BAR_TRACK_HEIGHT - bobberBarHeight - 4)) {
			bobberInBar = true;
		}

		boolean wasPressed = buttonPressed;
		buttonPressed = isUsePressed();
		// Stardew: if (!wasPressed && buttonPressed) play fishingRodBend
		if (!wasPressed && buttonPressed) {
			playLocal(ModSounds.FISHING_ROD_BEND.get(), 1.0f, 1.0f);
		}

		float gravity = buttonPressed ? -0.25f : 0.25f;
		if (buttonPressed && gravity < 0f && (bobberBarPos == 0f || bobberBarPos == (float) (BOBBER_BAR_TRACK_HEIGHT - bobberBarHeight))) {
			bobberBarSpeed = 0f;
		}

		if (bobberInBar) {
			// Barbed Hook effect (SV): different gravity and auto-tracking when fish is in the bar.
			gravity *= (barbedHookCount > 0) ? 0.3f : 0.6f;
			if (barbedHookCount > 0) {
				for (int i = 0; i < barbedHookCount; i++) {
					if (bobberPosition + 16f < bobberBarPos + (float) (bobberBarHeight / 2)) {
						bobberBarSpeed -= (i > 0) ? 0.05f : 0.2f;
					} else {
						bobberBarSpeed += (i > 0) ? 0.05f : 0.2f;
					}
					if (i > 0) {
						gravity *= 0.9f;
					}
				}
			}
		}

		float oldPos = bobberBarPos;
		bobberBarSpeed += gravity;
		bobberBarPos += bobberBarSpeed;
		if (bobberBarPos + (float) bobberBarHeight > (float) BOBBER_BAR_TRACK_HEIGHT) {
			bobberBarPos = BOBBER_BAR_TRACK_HEIGHT - bobberBarHeight;
			float bounceMult = 1f;
			if (leadBobberCount > 0) {
				bounceMult = (float) leadBobberCount * 0.1f;
			}
			bobberBarSpeed = (0f - bobberBarSpeed) * 2f / 3f * bounceMult;
			// Stardew: if (oldPos + height < 568) play shiny4
			if (oldPos + (float) bobberBarHeight < (float) BOBBER_BAR_TRACK_HEIGHT) {
				playLocal(ModSounds.SHINY4.get(), 0.9f, 1.0f);
			}
		} else if (bobberBarPos < 0f) {
			bobberBarPos = 0f;
			bobberBarSpeed = (0f - bobberBarSpeed) * 2f / 3f;
			// Stardew: if (oldPos > 0) play shiny4
			if (oldPos > 0f) {
				playLocal(ModSounds.SHINY4.get(), 0.9f, 1.0f);
			}
		}

		// ========== 宝箱更新逻辑（参考 BobberBar.cs） ==========
		boolean treasureInBar = false;
		if (hasTreasure) {
			float oldTreasureAppearTimer = treasureAppearTimer;
			treasureAppearTimer -= elapsedMs;
			if (treasureAppearTimer <= 0f) {
				if (treasureScale < 1f && !treasureCaught) {
					if (oldTreasureAppearTimer > 0f) {
						// 决定宝箱位置
						if (bobberBarPos > 274f) {
							treasurePosition = (float) random.nextInt(8, (int) bobberBarPos - 20);
						} else {
							int min = Math.min(528, (int) bobberBarPos + bobberBarHeight);
							int max = 500;
							treasurePosition = (min > max) ? (max - 1) : (float) random.nextInt(min, max);
						}
						playLocal(ModSounds.DWOP.get(), 1.0f, 1.0f);
					}
					treasureScale = Math.min(1f, treasureScale + 0.1f);
				}
				// 检查宝箱是否在绿条内
				treasureInBar = treasurePosition + 12f <= bobberBarPos - 32f + (float) bobberBarHeight
						&& treasurePosition - 16f >= bobberBarPos - 32f;
				if (treasureInBar && !treasureCaught) {
					treasureCatchLevel += 0.0135f;
					treasureShakeX = (float) (random.nextInt(5) - 2);
					treasureShakeY = (float) (random.nextInt(5) - 2);
					if (treasureCatchLevel >= 1f) {
						playLocal(ModSounds.NEW_ARTIFACT.get(), 1.0f, 1.0f);
						treasureCaught = true;
					}
				} else if (treasureCaught) {
					// 已捕获，缩小消失
					treasureScale = Math.max(0f, treasureScale - 0.1f);
				} else {
					// 不在条内，进度减少
					treasureShakeX = 0f;
					treasureShakeY = 0f;
					treasureCatchLevel = Math.max(0f, treasureCatchLevel - 0.01f);
				}
			}
		}
		// ========== 宝箱更新逻辑结束 ==========

		if (bobberInBar) {
			distanceFromCatching += 0.002f;
			reelRotation += (float) Math.PI / 8f;
			if (!sentResult) {
				long now = Util.getMillis();
				if (now - lastReelSoundMs >= 280L) {
					playLocal(ModSounds.FAST_REEL.get(), 0.7f, 1.0f);
					lastReelSoundMs = now;
				}
			}
			fishShakeX = (float) (random.nextInt(21) - 10) / 10f;
			fishShakeY = (float) (random.nextInt(21) - 10) / 10f;
			barShakeX = 0f;
			barShakeY = 0f;
		} else if (!treasureInBar || treasureCaught || treasureHunterCount <= 0) {
			if (!(fishShakeX == 0f && fishShakeY == 0f)) {
				// SV: leaving the bar breaks perfect and (with Challenge Bait) reduces remaining fish.
				perfect = false;
				playLocal(ModSounds.TINY_WHIP.get(), 1.0f, 1.0f);
				if (challengeBaitFishes > 0) {
					challengeBaitFishes--;
					if (challengeBaitFishes <= 0) {
						distanceFromCatching = 0f;
					}
				}
			}
			fishSizeReductionTimerMs -= elapsedMs;
			if (fishSizeReductionTimerMs <= 0) {
				currentFishSize = Math.max(minFishSize, currentFishSize - 1);
				fishSizeReductionTimerMs = TIME_PER_FISH_SIZE_REDUCTION_MS;
			}
			// Trap Bobber effect (SV): reduce how fast the catch meter drops.
			if (loseProgressOutsideBar) {
				distanceFromCatching -= escapeLossPerTick;
			}
			float distanceAway = Math.abs(bobberPosition - (bobberBarPos + (float) (bobberBarHeight / 2)));
			reelRotation -= (float) Math.PI / Math.max(10f, 200f - distanceAway);
			if (!sentResult) {
				long now = Util.getMillis();
				if (now - lastReelSoundMs >= 320L) {
					playLocal(ModSounds.SLOW_REEL.get(), 0.6f, 1.0f);
					lastReelSoundMs = now;
				}
			}
			barShakeX = (float) (random.nextInt(21) - 10) / 10f;
			barShakeY = (float) (random.nextInt(21) - 10) / 10f;
			fishShakeX = 0f;
			fishShakeY = 0f;
		}

		distanceFromCatching = Math.max(0f, Math.min(1f, distanceFromCatching));

		if (distanceFromCatching <= 0f) {
			// Close immediately (no linger).
			distanceFromCatching = -1f;
			playLocal(ModSounds.FISH_ESCAPE.get(), 1.0f, 1.0f);
			finish(false);
			return;
		} else if (distanceFromCatching >= 1f) {
			if (!perfect && currentFishSize == maxFishSize) {
				currentFishSize--;
			}
			playLocal(ModSounds.JINGLE1.get(), 1.0f, 1.0f);
			// Stardew does a fish-to-player animation whose endSound is tinyWhip.
			// We don't simulate that animation yet, but we can at least play the cue.
			playLocal(ModSounds.TINY_WHIP.get(), 1.0f, 1.0f);
			finish(true);
			return;
		}

		if (bobberPosition < 0f) {
			bobberPosition = 0f;
		}
		if (bobberPosition > (float) BOBBER_TRACK_HEIGHT) {
			bobberPosition = BOBBER_TRACK_HEIGHT;
		}
	}

	private void finish(boolean success) {
		if (sentResult) {
			return;
		}
		sentResult = true;
		int numCaught = 1;
		if (hasChallengeBait && challengeBaitFishes > 0) {
			numCaught = challengeBaitFishes;
		}
		PacketDistributor.sendToServer(new FishingResultPayload(sessionId, success, distanceFromCatching,
				treasureCaught, numCaught, perfect, currentFishSize));
		Minecraft.getInstance().setScreen(null);
	}

	@Override
	public void onClose() {
		mouseHeld = false;
		// User-initiated close counts as escape; close immediately.
		if (!sentResult) {
			emergencyShutDown();
		}
	}

	@SuppressWarnings("null")
	@Override
	public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (sentResult) {
			return;
		}
		// Run SV-like simulation at ~60fps based on real time.
		long now = Util.getMillis();
		long dt = now - lastUpdateMs;
		lastUpdateMs = now;
		dt = Mth.clamp(dt, 0L, 250L);
		accumulatedMs += dt;
		while (accumulatedMs >= 16L) {
			svUpdateStep(16);
			accumulatedMs -= 16L;
		}
		// If svUpdateStep triggered finish() this frame, do not draw anything else.
		if (sentResult) {
			return;
		}

		// Background first (Minecraft blur/dim), then draw our UI on top.
		renderBackground(graphics, mouseX, mouseY, partialTick);

		int centerX = width / 2;
		int centerY = height / 2;
		float fit = computeFitScale(width, height);

		// Emulate Stardew layout by anchoring the track center to screen center.
		float uiX = centerX - UI_TRACK_CENTER_X * fit;
		float uiY = centerY - UI_TRACK_CENTER_Y * fit;

		float shakeX = everythingShakeX * fit;
		float shakeY = everythingShakeY * fit;

		boolean textured = hasTexturedUi();

		// Geometry fallback (only when textures are missing).
		int fallbackTrackW = Math.round(16 * fit);
		int fallbackTrackH = Math.round(180 * fit);
		int fallbackTrackX = centerX - fallbackTrackW / 2;
		int fallbackTrackY = centerY - fallbackTrackH / 2;

		int progW = Math.round(120 * fit);
		int progH = Math.round(10 * fit);
		int progX = centerX - progW / 2;
		int progY = fallbackTrackY + fallbackTrackH + Math.round(16 * fit);
		if (!textured) {
			graphics.fill(fallbackTrackX - 2, fallbackTrackY - 2, fallbackTrackX + fallbackTrackW + 2, fallbackTrackY + fallbackTrackH + 2, 0xAA000000);
			graphics.fill(fallbackTrackX, fallbackTrackY, fallbackTrackX + fallbackTrackW, fallbackTrackY + fallbackTrackH, 0xFF2B2B2B);

			float barYNorm = bobberBarPos / (float) BOBBER_BAR_TRACK_HEIGHT;
			float barHNorm = bobberBarHeight / (float) BOBBER_BAR_TRACK_HEIGHT;
			int barY = fallbackTrackY + Math.round(barYNorm * fallbackTrackH);
			int barH = Math.max(6, Math.round(barHNorm * fallbackTrackH));
			barY = Mth.clamp(barY, fallbackTrackY, fallbackTrackY + fallbackTrackH - barH);
			int barColor = bobberInBar ? 0xFF4CFF4C : 0x804CFF4C;
			graphics.fill(fallbackTrackX, barY, fallbackTrackX + fallbackTrackW, barY + barH, barColor);

			float fishYNorm = bobberPosition / (float) BOBBER_TRACK_HEIGHT;
			int fishY = fallbackTrackY + Math.round(fishYNorm * fallbackTrackH);
			fishY = Mth.clamp(fishY, fallbackTrackY, fallbackTrackY + fallbackTrackH - 4);
			graphics.fill(fallbackTrackX - 3, fishY, fallbackTrackX + fallbackTrackW + 3, fishY + 4, 0xFFFFD34C);

			graphics.fill(progX - 1, progY - 1, progX + progW + 1, progY + progH + 1, 0xAA000000);
			graphics.fill(progX, progY, progX + progW, progY + progH, 0xFF1F1F1F);
			graphics.fill(progX, progY, progX + Math.round(progW * Mth.clamp(distanceFromCatching, 0f, 1f)), progY + progH, 0xFF4C8CFF);
		}
		if (textured) {
			// Stardew draws most UI at x4 scale, plus fade scale.
			float uiScale = fit * scale;
			float s4 = 4f * uiScale;
			float s2 = 2f * uiScale;

			// Bubble background (slightly transparent)
			beginAlpha(0.6f * scale);
			blitCenteredScaled(
					graphics,
					TEX_BUBBLE,
					(uiX + 84f * fit) + shakeX,
					(uiY + 298f * fit) + shakeY,
					52,
					157,
					s4
			);
			endAlpha();

			// Track
			beginAlpha(scale);
			blitCenteredScaled(
					graphics,
					TEX_TRACK,
					(uiX + UI_TRACK_CENTER_X * fit) + shakeX,
					(uiY + UI_TRACK_CENTER_Y * fit) + shakeY,
					38,
					150,
					s4
			);
			endAlpha();

			// Only draw the interactive elements once fully faded in, matching Stardew.
			if (scale >= 1f) {
				float blink = (float) Math.sin((double) Util.getMillis() / 100.0);
				float barAlpha = bobberInBar ? 1f : (0.25f * (blink + 2f));
				barAlpha = Mth.clamp(barAlpha, 0f, 1f);
				beginAlpha(barAlpha);
				float barX = (uiX + UI_BAR_X * fit) + (barShakeX * fit) + shakeX;
				float barYBase = (uiY + UI_BAR_Y * fit) + (bobberBarPos * fit) + (barShakeY * fit) + shakeY;
				// Top cap (9x2)
				blitTopLeftScaled(graphics, TEX_BAR_TOP, barX, barYBase, 9, 2, s4, s4);
				// Middle stretch (9x1) - IMPORTANT: scaleY must be the target pixel height (no extra uiScale factor)
				float midH = Math.max(0f, (bobberBarHeight - 16) * fit);
				blitTopLeftScaled(graphics, TEX_BAR_MID, barX, barYBase + 8f * fit, 9, 1, s4, midH);
				// Bottom cap (9x2)
				blitTopLeftScaled(graphics, TEX_BAR_BOTTOM, barX, barYBase + (bobberBarHeight - 8f) * fit, 9, 2, s4, s4);
				endAlpha();

				// Fish icon (20x20) at x2
				// Use server-selected fish category rather than local difficulty heuristic.
				beginAlpha(1f);
				float fishX = (uiX + UI_FISH_X * fit) + (fishShakeX * fit) + shakeX;
				float fishYpx = (uiY + UI_FISH_Y * fit) + (bobberPosition * fit) + (fishShakeY * fit) + shakeY;
				ResourceLocation fishTex = legendaryFish ? TEX_FISH_BOSS : TEX_FISH;
				blitCenteredScaled(graphics, fishTex, fishX, fishYpx, 20, 20, s2);
				endAlpha();

				// Sonar Bobber: show hooked fish icon next to the UI (SV draws a small popup + fishObject)
				if (hasSonarBobber && !sonarFishStack.isEmpty()) {
					float xPositionOnScreen = uiX;
					float yPositionOnScreen = uiY;
					float sonarX = (xPositionOnScreen > (width * 0.75f))
							? (xPositionOnScreen - 80f * fit)
							: (xPositionOnScreen + 216f * fit);
					boolean flip = sonarX < xPositionOnScreen;

					// Background rectangle sized like SV's (29x24) at 4x scale.
					int bgX = Math.round(sonarX - 52f * uiScale + shakeX);
					int bgY = Math.round(yPositionOnScreen + shakeY);
					int bgW = Math.round(116f * uiScale);
					int bgH = Math.round(96f * uiScale);
					graphics.fill(bgX, bgY, bgX + bgW, bgY + bgH, 0xAA000000);
					graphics.fill(bgX + 1, bgY + 1, bgX + bgW - 1, bgY + bgH - 1, 0xFF1F1F1F);

					// Fish icon in the popup; scale to match SV menu (4x).
					float iconX = (sonarX + (flip ? -32f : -16f) * uiScale) + shakeX;
					float iconY = (yPositionOnScreen + 16f * uiScale) + shakeY;
					var pose = graphics.pose();
					pose.pushPose();
					pose.translate(iconX, iconY, 0f);
					pose.scale(4f * uiScale, 4f * uiScale, 1f);
					graphics.renderItem(sonarFishStack, 0, 0);
					pose.popPose();
				}

				// Catch progress meter (simple colored bar, Stardew is red->green)
				float progXf = (uiX + UI_PROGRESS_X * fit) + shakeX;
				float progYf = (uiY + UI_PROGRESS_Y * fit) + shakeY;
				int filledH = Math.round(UI_PROGRESS_HEIGHT * fit * Mth.clamp(distanceFromCatching, 0f, 1f));
				int top = Math.round(progYf + (UI_PROGRESS_HEIGHT * fit) - filledH);
				int left = Math.round(progXf);
				int right = left + Math.round(UI_PROGRESS_WIDTH * fit);
				int bottom = Math.round(progYf + (UI_PROGRESS_HEIGHT * fit));
				int color = lerpRedToGreen(distanceFromCatching);
				graphics.fill(left, top, right, bottom, color);

				// Reel icon (5x10) x4 and rotated
				if (Minecraft.getInstance().getResourceManager().getResource(TEX_REEL).isPresent()) {
					float reelX = (uiX + UI_REEL_X * fit) + shakeX;
					float reelY = (uiY + UI_REEL_Y * fit) + shakeY;
					beginAlpha(1f);
					blitRotated(graphics, TEX_REEL, reelX, reelY, 5, 10, 2f, 10f, reelRotation, s4);
					endAlpha();
				}

				// ========== 宝箱渲染（参考 BobberBar.cs draw方法） ==========
				if (hasTreasure && treasureScale > 0f) {
					ResourceLocation treasureTex = goldenTreasure ? TEX_TREASURE_GOLD : TEX_TREASURE;
					float treasX = (uiX + UI_FISH_X * fit) + (treasureShakeX * fit) + shakeX;
					float treasY = (uiY + UI_FISH_Y * fit) + (treasurePosition * fit) + (treasureShakeY * fit) + shakeY;

					beginAlpha(1f);
					// 宝箱图标（20x24）at x2 scale，缩放动画
					float treasScale = s2 * treasureScale;
					blitCenteredScaled(graphics, treasureTex, treasX, treasY, 20, 24, treasScale);
					endAlpha();

					// 宝箱捕获进度条（橙色）
					if (treasureCatchLevel > 0f && !treasureCaught) {
					int treasBarX = Math.round((uiX + UI_BAR_X * fit) + shakeX);
					int treasBarY = Math.round((uiY + UI_BAR_Y * fit) + (treasurePosition * fit) + shakeY);
					int treasBarW = Math.round(40 * fit);
					int treasBarH = Math.round(8 * fit);

					// 背景（深灰色）
					graphics.fill(treasBarX, treasBarY, treasBarX + treasBarW, treasBarY + treasBarH, 0x80696969);
					// 进度（橙色）
					int treasProgW = Math.round(treasBarW * treasureCatchLevel);
					graphics.fill(treasBarX, treasBarY, treasBarX + treasProgW, treasBarY + treasBarH, 0xFFFF8800);
					}
				}
				// ========== 宝箱渲染结束 ==========
			}
		}

		// No top title text (matches desired clean SV-like UI).
		// Hint removed: user requested no additional on-screen hint text.
		if (perfect && distanceFromCatching > 0f && !fadeOut) {
			Component perfectText = Component.translatable("stardewcraft.fishing.minigame.perfect");
			graphics.drawString(font, perfectText, centerX - font.width(perfectText) / 2,
				progY + Math.round(38 * fit), 0xFFE87B, false);
		}

		// Do NOT call super.render() here: Screen.render may draw background again on some versions,
		// which would re-apply blur/dim over our minigame UI.
	}

	@SuppressWarnings("unused")
	private static void drawVerticalHint(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int color, float scale) {
		// Render as vertical text on the left: one visible character per line.
		var pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0f);
		pose.scale(scale, scale, 1f);
		int line = 0;
		int lineH = font.lineHeight + 1;
		for (int offset = 0; offset < text.length(); ) {
			int cp = text.codePointAt(offset);
			offset += Character.charCount(cp);
			if (Character.isWhitespace(cp)) {
				continue;
			}
			String s = new String(Character.toChars(cp));
			g.drawString(font, s, 0, line * lineH, color);
			line++;
		}
		pose.popPose();
	}

	private static int lerpRedToGreen(float t) {
		float x = Mth.clamp(t, 0f, 1f);
		int r = (int) Mth.lerp(1f - x, 0x2A, 0x4C);
		int g = (int) Mth.lerp(x, 0x2A, 0xFF);
		int b = (int) Mth.lerp(x, 0x2A, 0x4C);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
