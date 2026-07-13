package com.stardew.craft.fishing.server;

import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.item.IStardewItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FishingSession {
	public enum State {
		WAITING_BITE,
		BITE_READY,
		HOOKED_ANIM,
		MINIGAME,
		DONE
	}

	// 星露谷物语的基础宝箱概率
	private static final double BASE_TREASURE_CHANCE = 0.15;

	private final UUID id;
	private BlockPos bobberPos;
	private int waterDepth;
	private State state;
	private int ticksUntilBite;
	private int ticksUntilTimeout;
	private ItemStack plannedCatch;
	private int difficulty;
	private int motionTypeId;
	private boolean skipMinigame;
	private int hookEntityId;
	private boolean hookInWater;
	private boolean inSplash;
	private int settleTicks;

	// 宝箱相关
	private boolean hasTreasure;
	private boolean goldenTreasure;
	private List<ItemStack> treasureLoot;
	
	// 鱼品质相关
	private double fishSize;  // 0.0-1.0，决定初始品质
	private int minFishSize;
	private int maxFishSize;
	private int caughtFishSize;
	private int initialQuality;  // 基础品质（0-2）
	private float castPower;  // 0.0-1.0，抛竿力度（对应SDV clearWaterDistance/5）

	public FishingSession(UUID id, BlockPos bobberPos, int waterDepth, int ticksUntilBite) {
		this.id = id;
		this.bobberPos = bobberPos;
		this.waterDepth = waterDepth;
		this.ticksUntilBite = ticksUntilBite;
		this.ticksUntilTimeout = 0;
		this.state = State.WAITING_BITE;
		this.plannedCatch = ItemStack.EMPTY;
		this.skipMinigame = false;
		this.hookEntityId = -1;
		this.hookInWater = false;
		this.settleTicks = 40;
		this.hasTreasure = false;
		this.goldenTreasure = false;
		this.treasureLoot = List.of();
		this.fishSize = 0.0;
		this.minFishSize = 0;
		this.maxFishSize = 0;
		this.caughtFishSize = 0;
		this.initialQuality = 0;
		this.castPower = 0.0f;
	}

	public float castPower() {
		return castPower;
	}

	public void setCastPower(float castPower) {
		this.castPower = castPower;
	}

	public UUID id() {
		return id;
	}

	public State state() {
		return state;
	}

	public int waterDepth() {
		return waterDepth;
	}

	public BlockPos bobberPos() {
		return bobberPos;
	}

	public int difficulty() {
		return difficulty;
	}

	public int motionTypeId() {
		return motionTypeId;
	}

	public int ticksUntilTimeout() {
		return ticksUntilTimeout;
	}

	public boolean skipMinigame() {
		return skipMinigame;
	}

	public int hookEntityId() {
		return hookEntityId;
	}

	public void setHookEntityId(int entityId) {
		this.hookEntityId = entityId;
	}

	public boolean hasTreasure() {
		return hasTreasure;
	}

	public boolean isGoldenTreasure() {
		return goldenTreasure;
	}

	public List<ItemStack> treasureLoot() {
		return treasureLoot;
	}

	public void setTreasureLoot(List<ItemStack> loot) {
		this.treasureLoot = loot;
	}

	@SuppressWarnings("null")
	public boolean tick(ServerPlayer player, ServerLevel level, RandomSource random) {
		if (state == State.DONE) {
			return false;
		}

		// Track hook landing. Only start the bite timer once the hook is actually in water.
		if (hookEntityId >= 0) {
			var e = level.getEntity(hookEntityId);
			if (e != null) {
				BlockPos hookPos = e.blockPosition();
				this.bobberPos = hookPos;
				boolean iceFishingContest = com.stardew.craft.festival.FestivalOfIceService.isFishingContestActive(player);
				boolean inWater = level.getFluidState(hookPos).is(net.minecraft.tags.FluidTags.WATER)
					|| level.getFluidState(hookPos).is(net.minecraft.tags.FluidTags.LAVA)
					|| iceFishingContest;
				if (inWater && !hookInWater) {
					hookInWater = true;
					if (iceFishingContest) {
						this.waterDepth = 5;
						this.inSplash = false;
					} else {
						// SDV uses tile coords with practical max ~5 (legendary maxDepth). Cap to 5 to match.
						this.waterDepth = com.stardew.craft.fishing.server.FishingSessionManager.estimateWaterDepth(level, hookPos, 5);
						// SDV: bobber within fishSplashPoint rect → timeUntilFishingBite /= 4 + later +0.4 chance + +1 depth.
						try {
							com.stardew.craft.fishing.splash.FishSplashState fs =
									com.stardew.craft.fishing.splash.FishSplashState.getStardewState(level);
							if (fs != null) {
								net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> bh = level.getBiome(hookPos);
								java.util.List<String> keys = com.stardew.craft.fishing.data.FishingDataManager
										.resolveVanillaAlignedLocationKeysStatic(level, bh, hookPos);
								if (fs.findIntersecting(keys, hookPos) != null) {
									this.inSplash = true;
									this.ticksUntilBite = Math.max(1, this.ticksUntilBite / 4);
								}
							}
						} catch (Exception ignored) {}
					}
				}

				// If the hook fails to land in water soon, treat it as a cancelled/invalid cast (vanilla would reel back).
				if (!hookInWater) {
					settleTicks--;
					if (settleTicks <= 0) {
						state = State.DONE;
						return false;
					}
				}
			}
		}
		if (state == State.WAITING_BITE) {
			if (!hookInWater) {
				return true;
			}
			ticksUntilBite--;
			if (ticksUntilBite > 0) {
				return true;
			}

			// 使用最新的bobberPos进行鱼类选择（因为初始创建session时用的是player位置占位符）
			BlockPos actualBobberPos = (hookEntityId >= 0) ? this.bobberPos : bobberPos;
			Optional<FishingDataManager.FishSelection> selected = FishingDataManager.get().selectFish(player, level, actualBobberPos, waterDepth, this.inSplash, random);
			if (selected.isEmpty()) {
				plannedCatch = ItemStack.EMPTY;
				difficulty = 15;
				motionTypeId = 0;
				skipMinigame = false;
				this.fishSize = 0.0;
				this.minFishSize = 0;
				this.maxFishSize = 0;
				this.caughtFishSize = 0;
				this.initialQuality = 0;
			} else {
				// 找到鱼了！计算鱼的大小和初始品质
				// SDV原版公式：fishSize = (clearWaterDistance/5) * random(min,max)/5 * favBait * ±10%
				// MC中用castPower替代clearWaterDistance/5，下限0.2（SDV短抛至少戉1格离岸距离=0.2）
					ItemStack rod = getRodFromPlayer(player);
				int fishingLevel = StardewEnchantments.effectiveFishingLevel(player, rod);

				// 1. 抛竿力度因子（SDV: clearWaterDistance/5，范围0.2-1.0）
				this.fishSize = Math.max(0.2, (double) this.castPower);

				// 2. 钓鱼等级随机项：random(1+level/2, max(6, 1+level/2)) / 5
				int minimumSizeContribution = 1 + fishingLevel / 2;
				int upperBound = Math.max(6, minimumSizeContribution);
				if (upperBound <= minimumSizeContribution) {
					this.fishSize *= (double) minimumSizeContribution / 5.0;
				} else {
					this.fishSize *= (double) (minimumSizeContribution + random.nextInt(upperBound - minimumSizeContribution)) / 5.0;
				}

				// 3. 偏爱饵料加成（Targeted Bait匹配目标鱼时×1.2）
					rod = getRodFromPlayer(player);
				if (rod != null && rod.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem fishingRodItem) {
					ItemStack baitStack = fishingRodItem.getAttachmentsForTooltip(rod).bait();
					if (!baitStack.isEmpty() && baitStack.getItem() instanceof com.stardew.craft.item.SpecificBaitItem) {
						String targetFishId = com.stardew.craft.item.SpecificBaitItem.getTargetFishId(baitStack);
						if (targetFishId != null) {
							@SuppressWarnings("null")
							String caughtFishId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(selected.get().stack().getItem()).toString();
							if (targetFishId.equals(caughtFishId)) {
								this.fishSize *= 1.2;
							}
						}
					}
				}

				// 4. ±10%随机波动
				this.fishSize *= 1.0 + (double) (random.nextInt(21) - 10) / 100.0;

				// 5. Clamp到[0,1]
				this.fishSize = Math.max(0.0, Math.min(1.0, this.fishSize));
				this.minFishSize = Math.max(0, selected.get().minFishSize());
				this.maxFishSize = Math.max(this.minFishSize, selected.get().maxFishSize());
				this.caughtFishSize = (int) ((float) this.minFishSize
						+ (float) (this.maxFishSize - this.minFishSize) * (float) this.fishSize);
				this.caughtFishSize++;
				
				// 根据fishSize决定初始品质（星露谷原版逻辑）
				if (this.fishSize < 0.33) {
					this.initialQuality = 0;  // 普通
				} else if (this.fishSize < 0.66) {
					this.initialQuality = 1;  // 银星
				} else {
					this.initialQuality = 2;  // 金星
				}

				if (rod != null && rod.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem fishingRodItem
						&& fishingRodItem.getTier() == com.stardew.craft.item.tool.FishingRodItem.RodTier.TRAINING_ROD) {
					this.initialQuality = 0;
					this.caughtFishSize = this.minFishSize;
				}
				
				plannedCatch = selected.get().stack();
				difficulty = selected.get().difficulty();
				motionTypeId = selected.get().motionTypeId();
				skipMinigame = selected.get().skipMinigame();
			}

			// 决定是否生成宝箱(参考星露谷物语的计算)
			// 获取鱼竿用于检查鱼饵和渔具
				ItemStack rod = getRodFromPlayer(player);
			decideTreasure(player, rod, random);

			// 咬钩了：等待玩家再次右键"收杆"才进入小游戏。
			state = State.BITE_READY;
			ticksUntilTimeout = 20 * 10;
			return true;
		}

		if (state == State.BITE_READY) {
			ticksUntilTimeout--;
			if (ticksUntilTimeout <= 0) {
				state = State.DONE;
				return false;
			}
			return true;
		}

		if (state == State.HOOKED_ANIM) {
			// Countdown until the server opens the minigame.
			// The manager will transition to MINIGAME when this reaches 0.
			ticksUntilTimeout--;
			return true;
		}

		if (state == State.MINIGAME) {
			if (ticksUntilTimeout < 0) {
				return true;
			}
			ticksUntilTimeout--;
			return ticksUntilTimeout > 0;
		}

		return false;
	}

	public void finish() {
		state = State.DONE;
	}

	public void startMinigame(int minigameTimeoutTicks) {
		state = State.MINIGAME;
		ticksUntilTimeout = minigameTimeoutTicks;
	}

	public void startHookedAnim(int animTicks) {
		state = State.HOOKED_ANIM;
		ticksUntilTimeout = animTicks;
	}

	public ItemStack plannedCatch() {
		return plannedCatch;
	}

	public boolean isPlannedCatchLegendaryFish() {
		if (plannedCatch == null || plannedCatch.isEmpty()) {
			return false;
		}
		if (!(plannedCatch.getItem() instanceof IStardewItem stardewItem)) {
			return false;
		}
		return "stardewcraft.type.legendary_fish".equals(stardewItem.getItemTypeKey());
	}
	
	public double fishSize() {
		return fishSize;
	}

	public int minFishSize() {
		return minFishSize;
	}

	public int maxFishSize() {
		return maxFishSize;
	}

	public int caughtFishSize() {
		return caughtFishSize;
	}

	public void applyFinalCaughtFishSize(int finalFishSize) {
		if (minFishSize <= 0 && maxFishSize <= 0) {
			return;
		}
		int upper = Math.max(maxFishSize + 1, caughtFishSize);
		caughtFishSize = net.minecraft.util.Mth.clamp(finalFishSize, minFishSize, upper);
	}
	
	public int initialQuality() {
		return initialQuality;
	}

	/**
	 * 决定是否生成宝箱(参考星露谷物语FishingRod.cs的startMinigameEndFunction)
	 * 
	 * 原版逻辑：
	 * baseChanceForTreasure = 0.15
	 * chance = base + LuckLevel*0.005 + dailyLuck/2 + (profession_pirate ? base : 0) + (bait_magnet ? base : 0) + extraTackle
	 */
	private void decideTreasure(ServerPlayer player, ItemStack rod, RandomSource random) {
		if (com.stardew.craft.festival.fair.FairFishingGameService.isFishingGameActive(player)
				|| com.stardew.craft.festival.FestivalOfIceService.isFishingContestActive(player)) {
			hasTreasure = false;
			goldenTreasure = false;
			treasureLoot = List.of();
			return;
		}
		// SV behavior: non-fish catchables that skip the minigame can't have treasure.
		if (skipMinigame) {
			hasTreasure = false;
			goldenTreasure = false;
			treasureLoot = List.of();
			return;
		}
		int fishingLevel = StardewEnchantments.effectiveFishingLevel(player, rod);
		
		// 基础概率 15%
		double treasureChance = BASE_TREASURE_CHANCE;

		// 幸运Buff（SV: LuckLevel*0.005）
		int luckBuff = com.stardew.craft.player.PlayerStardewDataAPI.getLuckBuffLevel(player);
		treasureChance += luckBuff * 0.005;

		// 钓鱼等级加成：每级 +0.5%
		treasureChance += fishingLevel * 0.005;

		// 幸运加成：dailyLuck/2
		double dailyLuck = com.stardew.craft.player.PlayerStardewDataAPI.getDailyLuck(player);
		treasureChance += dailyLuck / 2.0;

		// Pirate：额外+15%宝箱率（与原版一致为再加一次 baseChance）。
		if (com.stardew.craft.player.PlayerStardewDataAPI.hasProfession(player, com.stardew.craft.player.ProfessionType.PIRATE)) {
			treasureChance += BASE_TREASURE_CHANCE;
		}

		// Magnet鱼饵：+15% (物品ID: stardewcraft:magnet)
		if (com.stardew.craft.item.tool.FishingRodItem.hasBait(rod, "stardewcraft:magnet")) {
			treasureChance += BASE_TREASURE_CHANCE;
		}
		
		// Treasure Hunter渔具：每个+5%（原版按 tackleIds 计数叠加）
		int treasureHunterCount = com.stardew.craft.item.tool.FishingRodItem.countTackle(rod, "stardewcraft:treasure_hunter");
		if (treasureHunterCount > 0) {
			treasureChance += treasureHunterCount * (BASE_TREASURE_CHANCE / 3.0); // 0.15/3 = 0.05
		}

		// 投骰子
		hasTreasure = random.nextDouble() < treasureChance;

		// 如果有宝箱，判断是否为金色
		// SV(1.6): 需要 Fishing Mastery，且概率为 0.25 + AverageDailyLuck。
		// 本模组目前按 per-player daily luck 实现，因此用玩家 dailyLuck 近似 AverageDailyLuck。
		if (hasTreasure) {
			double goldenChance = 0.25 + dailyLuck;
			goldenChance = net.minecraft.util.Mth.clamp(goldenChance, 0.0, 1.0);
			if (PlayerDataManager.getPlayerData(player).hasMastery(SkillType.FISHING) && random.nextDouble() < goldenChance) {
				goldenTreasure = true;
			}
		}
	}

	private static ItemStack getRodFromPlayer(ServerPlayer player) {
		if (com.stardew.craft.festival.fair.FairFishingGameService.isFishingGameActive(player)) {
			ItemStack main = player.getMainHandItem();
			if (com.stardew.craft.festival.fair.FairFishingGameService.isUsableFishingGameRod(player, main)) {
				return main;
			}
			ItemStack off = player.getOffhandItem();
			if (com.stardew.craft.festival.fair.FairFishingGameService.isUsableFishingGameRod(player, off)) {
				return off;
			}
			return ItemStack.EMPTY;
		}
		return com.stardew.craft.item.tool.FishingRodItem.findRod(player);
	}
}
