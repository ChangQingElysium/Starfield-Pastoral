package com.stardew.craft.client.fishing;

import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.fishing.network.FishingResultPayload;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;


import java.util.Random;
import java.util.UUID;

public final class FishingMinigameScreen extends Screen implements com.stardew.craft.client.gui.StardewRealtimeScreen,
        com.stardew.craft.client.gui.common.StardewGuiContentSize {
	@Override public int minimumCanvasWidth() { return 480; }
	@Override public int minimumCanvasHeight() { return 348; }
	// Stardew values (see StardewValley.Menus.BobberBar)
	private static final int BOBBER_TRACK_HEIGHT = 548;
	private static final int BOBBER_BAR_TRACK_HEIGHT = 568;
	private static final int TIME_PER_FISH_SIZE_REDUCTION_MS = 800;

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
		if (minecraft == null || minecraft.screen != null && minecraft.screen != this) {
			return false;
		}
		// Stardew: LeftMouse / useToolButton / gamepad X/A
		return minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown() || minecraft.options.keyJump.isDown();
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
		if (minecraft != null && minecraft.player != null) {
			for (int i = 0; i < 9; i++) {
				if (minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
					FishingInteractionState.selectSlot(i);
					return true;
				}
			}
			if (minecraft.options.keySwapOffhand.matches(keyCode, scanCode)
					|| minecraft.options.keyDrop.matches(keyCode, scanCode)) {
				boolean swap = minecraft.options.keySwapOffhand.matches(keyCode, scanCode);
				FishingInteractionState.cancel(true);
				if (swap) minecraft.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
						net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
						net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
				else minecraft.player.drop(hasControlDown());
				return true;
			}
		}
		// Space / jump as a backup (some users prefer keyboard)
		if (minecraft != null && minecraft.options.keyJump.matches(keyCode, scanCode)) {
			mouseHeld = true;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@SuppressWarnings("null")
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (minecraft != null && minecraft.player != null && scrollY != 0) {
			FishingInteractionState.selectSlot(Math.floorMod(minecraft.player.getInventory().selected - (int) Math.signum(scrollY), 9));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

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
		FishingMinigameHud.finish(this, success);
	}

	@Override
	public void removed() {
		if (!sentResult) {
			sentResult = true;
			mouseHeld = false;
			FishingInteractionState.cancel(true);
		}
		super.removed();
	}

	public void cancelWithoutResult() {
		if (sentResult) return;
		mouseHeld = false;
		sentResult = true;
		if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
	}

	@Override
	public void onClose() {
		FishingInteractionState.cancel(true);
		cancelWithoutResult();
	}

    public UUID session() { return sessionId; }
    public record Snapshot(float fishPosition,float fishVelocity,float barPosition,int barHeight,boolean inBar,
        boolean held,float progress,boolean perfect,boolean legendary,net.minecraft.world.item.ItemStack sonar,
        boolean hasTreasure,boolean golden,float treasurePosition,float treasureScale,float treasureProgress,
        boolean treasureCaught,int challengeFish,float scale,boolean entering,float fishShakeX,float fishShakeY,
        float barShakeX,float barShakeY,float treasureShakeX,float treasureShakeY) {}
    public Snapshot snapshot() { return new Snapshot(bobberPosition,bobberSpeed,bobberBarPos,bobberBarHeight,bobberInBar,
        buttonPressed,distanceFromCatching,perfect,legendaryFish,sonarFishStack,hasTreasure,goldenTreasure,
        treasurePosition,treasureScale,treasureCatchLevel,treasureCaught,challengeBaitFishes,scale,fadeIn,
        fishShakeX,fishShakeY,barShakeX,barShakeY,treasureShakeX,treasureShakeY); }
    /** Start the unchanged simulator clock with the first visible HUD frame. */
    public void startPresentation(long now) { lastUpdateMs=now;accumulatedMs=0; }
    public void suspendPresentation(long now) { startPresentation(now);mouseHeld=false;buttonPressed=false; }
    public void advance() {
        if(sentResult)return;
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

    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        FishingMinigameHud.draw(graphics,snapshot(),width,height);
    }
}
