package com.stardew.craft.fishing.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.tags.TagKey;

import java.util.*;

public final class FishingDataManager {
	private static final Gson GSON = new Gson();
	private static final String SECRET_NOTE_ITEM_ID = "stardewcraft:secret_note";
	private static final Set<String> INHERITED_POOL_KEYS = Set.of("Default");
	private static final String LEGACY_COMPAT_POOL_KEY = "stardewcraft:stardew_valley";

	/**
	 * Items that SDV pulls from the water without opening BobberBar. Most aren't
	 * backed by Data/Fish, but FishingRod also explicitly treats seaweed and both
	 * algae types as junk even though they do have Data/Fish entries.
	 * The JSON {@code skipMinigame} flag is honored when true; this set is an
	 * additional safety-net so a missing flag never accidentally forces a minigame.
	 */
	private static final Set<String> NON_FISH_CATCHABLE_IDS = Set.of(
			"stardewcraft:seaweed",
			"stardewcraft:green_algae",
			"stardewcraft:white_algae",
			"stardewcraft:sea_jelly",
			"stardewcraft:river_jelly",
			"stardewcraft:cave_jelly",
			"stardewcraft:trash",
			"stardewcraft:driftwood",
			"stardewcraft:soggy_newspaper",
			"stardewcraft:broken_cd",
			"stardewcraft:broken_glasses",
			"stardewcraft:ancient_doll",
			"stardewcraft:joja_cola"
	);

	/** True when the resolved item is a non-fish catchable that should bypass the bobber-bar minigame. */
	public static boolean isNonFishCatchable(String itemId) {
		return itemId != null && NON_FISH_CATCHABLE_IDS.contains(itemId);
	}

	private static volatile RuleEligibilityHook RULE_ELIGIBILITY_HOOK = RuleEligibilityHook.ALLOW_ALL;
	private static volatile FishingDataManager INSTANCE = new FishingDataManager(
			Collections.singletonMap("Default", FishingLocationData.defaultFallback())
	);

	/** 获取缓存的 JSON（服务端调用）。若 GC 回收则重新序列化 */
	public static String getCachedJson() {
		return INSTANCE.cachedJson();
	}

	private String rebuildCacheJson() {
		if (byLocationKey.isEmpty()) return "";
		com.google.gson.JsonObject cacheRoot = new com.google.gson.JsonObject();
		for (Map.Entry<String, FishingLocationData> me : byLocationKey.entrySet()) {
			com.google.gson.JsonObject locObj = new com.google.gson.JsonObject();
			locObj.addProperty("location", me.getValue().locationKey());
			com.google.gson.JsonArray fishArr = new com.google.gson.JsonArray();
			for (SpawnFishRule rule : me.getValue().fish()) {
				fishArr.add(ruleToJson(rule));
			}
			locObj.add("fish", fishArr);
			cacheRoot.add(me.getKey(), locObj);
		}
		return GSON.toJson(cacheRoot);
	}

	/** 从 JSON 字符串重放解析（客户端调用） */
	public static void applyFromJson(String json) {
		try {
			com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
			if (root == null) return;
			Map<String, FishingLocationData> loaded = new HashMap<>();
			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
				FishingLocationData data = FishingLocationData.fromJson(entry.getValue().getAsJsonObject());
				loaded.put(entry.getKey(), data);
			}
			loaded.putIfAbsent("Default", FishingLocationData.defaultFallback());
			INSTANCE = new FishingDataManager(
					Collections.unmodifiableMap(loaded));
			StardewCraft.LOGGER.info("[DATA-SYNC] Applied fishing data from network: {}", loaded.keySet());
		} catch (Exception e) {
			StardewCraft.LOGGER.error("[DATA-SYNC] Failed to apply fishing JSON", e);
		}
	}

	public static FishingDataManager get() {
		return INSTANCE;
	}

	static {
		// Default eligibility hook is ALLOW_ALL; condition evaluation now happens explicitly
		// in selectFish so it can pass the magic-bait flag for MagicBaitIgnoreQueryKeys.
	}

	/**
	 * Leave a single extension point for vanilla-gated conditions that require runtime state
	 * outside raw fish JSON rules (e.g. special order unlocks).
	 */
	public static void setRuleEligibilityHook(RuleEligibilityHook hook) {
		RULE_ELIGIBILITY_HOOK = hook == null ? RuleEligibilityHook.ALLOW_ALL : hook;
	}

	private final Map<String, FishingLocationData> byLocationKey;
	private volatile java.lang.ref.SoftReference<String> cachedJson;

	private FishingDataManager(Map<String, FishingLocationData> byLocationKey) {
		this.byLocationKey = byLocationKey;
		this.cachedJson = new java.lang.ref.SoftReference<>(null);
	}

	private String cachedJson() {
		String json = cachedJson.get();
		if (json != null) return json;
		json = rebuildCacheJson();
		cachedJson = new java.lang.ref.SoftReference<>(json);
		return json;
	}

	/**
	 * 根据钓鱼位置选择鱼
	 *
	 * @param player     钓鱼玩家
	 * @param level      服务端世界
	 * @param bobberPos  浮漂位置
	 * @param waterDepth 水深/离岸距离
	 * @param random     随机源
	 * @return 选中的鱼
	 */
	@SuppressWarnings("null")
	public Optional<FishSelection> selectFish(ServerPlayer player, ServerLevel level, BlockPos bobberPos, int waterDepth, RandomSource random) {
		return selectFish(player, level, bobberPos, waterDepth, false, random);
	}

	/**
	 * SDV-parity selection with optional fish-splash boost. SDV's {@code FishingRod}
	 * passes {@code clearWaterDistance + 1} into {@code getFish(...)} when the bobber
	 * intersects the splash rect. The {@code baitPotency + 0.4} that SDV also passes is
	 * a dead parameter ({@code GetFishFromLocationData} ignores it), so splash only
	 * affects depth-related rolls, not chance directly.
	 */
	@SuppressWarnings("null")
	public Optional<FishSelection> selectFish(ServerPlayer player, ServerLevel level, BlockPos bobberPos, int waterDepth, boolean inSplash, RandomSource random) {
        return selectFish(player, level, bobberPos, waterDepth, inSplash, random, null);
    }

    /** Pet LOCATION_FISH query: inherited pool, no rod, tutorial catch, special bobber rewards or junk fallback. */
    public ItemStack selectPetGift(ServerPlayer player, ServerLevel level, String location, RandomSource random) {
        if (!location.equals("Mountain") && !location.equals("Forest")) throw new IllegalArgumentException("Pet fishing location");
        return selectFish(player, level, player.blockPosition(), 1, false, random, location).map(FishSelection::stack).orElse(ItemStack.EMPTY);
    }

    private Optional<FishSelection> selectFish(ServerPlayer player, ServerLevel level, BlockPos bobberPos, int waterDepth, boolean inSplash, RandomSource random, String queryLocation) {
		int effectiveDepth = waterDepth + (inSplash ? 1 : 0);
		if (!isStardewFishingDimension(level)) {
			return queryLocation != null ? Optional.empty() : Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		Optional<ItemStack> secretNote25Catch = queryLocation != null ? Optional.empty() : com.stardew.craft.secretnote.SecretNote25Service
				.tryCreateNecklaceCatch(player, level, bobberPos);
		if (secretNote25Catch.isPresent()) {
			return Optional.of(new FishSelection(secretNote25Catch.get(), 0, 0, 0, 0, true));
		}

		int luckBuffLevel = Math.max(0, PlayerStardewDataAPI.getLuckBuffLevel(player));
		PlayerStardewData playerData = PlayerStardewDataAPI.getData(player);
		boolean hasCuriosityLure = queryLocation == null && hasCuriosityLure(player);
		ItemStack rodStack = queryLocation == null ? getRodFromPlayer(player) : ItemStack.EMPTY;
		int fishingLevel = StardewEnchantments.effectiveFishingLevel(player, rodStack);
		boolean usingMagicBait = !rodStack.isEmpty()
				&& (rodStack.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem)
				&& com.stardew.craft.item.tool.FishingRodItem.hasBait(rodStack, "stardewcraft:magic_bait");
		boolean isTrainingRod = !rodStack.isEmpty()
				&& rodStack.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem fri
				&& fri.getTier() == com.stardew.craft.item.tool.FishingRodItem.RodTier.TRAINING_ROD;
		boolean usingGoodBait = isUsingGoodBait(rodStack);
		String baitTargetFishId = getTargetedBaitFishId(rodStack);
		Holder<Biome> worldBiomeHolder = queryLocation == null
				? level.getBiome(bobberPos)
				: biomeForLocationPool(level, queryLocation);
		Optional<FishSelection> mineCatch = queryLocation != null ? Optional.empty() : trySelectVanillaMineCatch(
				worldBiomeHolder, isTrainingRod, fishingLevel, effectiveDepth,
				luckBuffLevel, hasCuriosityLure, baitTargetFishId, random);
		if (mineCatch.isPresent()) {
			return mineCatch;
		}
		boolean fairFishingGame = queryLocation == null && com.stardew.craft.festival.fair.FairFishingGameService.isFishingGameActive(player);
		boolean iceFishingContest = queryLocation == null && com.stardew.craft.festival.FestivalOfIceService.isFishingContestActive(player);
		boolean festivalFishingGame = fairFishingGame || iceFishingContest;
		if (queryLocation == null && !festivalFishingGame
				&& isFourCornersFishingRegion(level, bobberPos)
				&& playerData.hasMailFlag("cursed_doll")
				&& !playerData.hasMailFlag("eric's_prank_1")
				&& random.nextDouble() < 0.50D) {
			return createSelectionForItem("stardewcraft:ancient_doll", true);
		}
		Optional<ForestFarmFishingRoll> forestFarmRoll = queryLocation == null && !festivalFishingGame
				? resolveForestFarmFishingRoll(level, bobberPos, random)
				: Optional.empty();
		if (forestFarmRoll.orElse(null) == ForestFarmFishingRoll.WOODSKIP) {
			return createSelectionForItem("stardewcraft:woodskip", false);
		}
		if (forestFarmRoll.orElse(null) == ForestFarmFishingRoll.JUNK) {
			return Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		Optional<BeachFarmFishingRoll> beachFarmRoll = queryLocation == null && !festivalFishingGame
				&& forestFarmRoll.isEmpty() ? resolveBeachFarmFishingRoll(level, bobberPos, random) : Optional.empty();
		if (beachFarmRoll.orElse(null) == BeachFarmFishingRoll.SEAWEED) {
			return createSelectionForItem("stardewcraft:seaweed", true);
		}
		if (beachFarmRoll.orElse(null) == BeachFarmFishingRoll.BEACH_FORAGE) {
			String[] keys = {"723", "393", "719", "718"};
			ItemStack stack = com.stardew.craft.data.VanillaObjectCatalog.stackFor(
					com.stardew.craft.data.VanillaObjectCatalog.entryByKey(keys[random.nextInt(keys.length)]));
			return Optional.of(new FishSelection(stack, 0, 0, 0, 0, true));
		}
		if (beachFarmRoll.orElse(null) == BeachFarmFishingRoll.JUNK) {
			return Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		Optional<SpecialFarmFishingRoll> specialFarmRoll = queryLocation == null
				&& !festivalFishingGame && forestFarmRoll.isEmpty() && beachFarmRoll.isEmpty()
				? resolveSpecialFarmFishingRoll(level, bobberPos, random)
				: Optional.empty();
		if (specialFarmRoll.orElse(null) == SpecialFarmFishingRoll.JUNK) {
			return Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		Optional<String> riverlandFarmPool = queryLocation == null && !festivalFishingGame
				&& forestFarmRoll.isEmpty() && beachFarmRoll.isEmpty() && specialFarmRoll.isEmpty()
				? resolveRiverlandFarmFishingPool(level, bobberPos, random)
				: Optional.empty();
		Optional<String> farmLocationPool = forestFarmRoll.isPresent()
				? Optional.of("Forest")
				: beachFarmRoll.isPresent()
						? Optional.of("Beach")
						: specialFarmRoll.map(roll -> roll == SpecialFarmFishingRoll.FOREST ? "Forest" : "Mountain")
								.or(() -> riverlandFarmPool);
		List<String> regularLocationKeys = queryLocation != null || festivalFishingGame
				? List.of()
				: farmLocationPool
						.map(List::of)
						.orElseGet(() -> resolveVanillaAlignedLocationKeys(
								level, worldBiomeHolder, bobberPos));
		// LOCATION_FISH on a farm changes the logical fishing location, not the
		// world's physical biome. The representative biome supplies that logical
		// context to hooks and conditions without rewriting world chunks. Explicit
		// biome and FishAreaId filters are skipped below for this farm-wide pool.
		Holder<Biome> biomeHolder = farmLocationPool
				.map(pool -> biomeForLocationPool(level, pool))
				.orElse(worldBiomeHolder);
		List<String> lookupKeys = queryLocation != null ? List.of(queryLocation) : resolveFishingLookupKeys(
				fairFishingGame,
				iceFishingContest,
				regularLocationKeys);
		boolean nightMarketFishing = com.stardew.craft.festival.nightmarket.NightMarketSubmarineService
				.isInsideSubmarineBounds(bobberPos)
				|| hasBiomeTag(worldBiomeHolder, "stardewcraft:is_night_market");
		boolean poolOnly = queryLocation == null && (festivalFishingGame || nightMarketFishing || useDesertFestivalPoolOnly(worldBiomeHolder));

		// 获取当前环境条件
		boolean isRaining = com.stardew.craft.weather.WeatherManager.isRaining(level);
		// 直接使用 StardewTimeManager 的时间（HHMM, 600~2600），避免 MC dayTime 与
		// 星露谷虚拟时间在自定义维度下不一致，且 MC→SDV 转换无法表示 24:00 之后的时刻。
		int stardewTime = currentStardewTime();
		String currentSeason = getCurrentSeason(level);

		String fishAreaId = festivalFishingGame ? null : resolveVanillaFishAreaId(biomeHolder);
		boolean farmWideLocationPool = farmLocationPool.isPresent();
		List<CandidateRule> candidates = collectCandidatesByKeys(lookupKeys, poolOnly);
		if (iceFishingContest) {
			candidates = new ArrayList<>(candidates.stream()
					.filter(candidate -> isFishRule(candidate.rule()))
					.toList());
		}

		if (candidates.isEmpty()) {
			// 如果没有任何候选鱼（数据未加载或配置错误），直接返回垃圾
			return queryLocation != null ? Optional.empty() : Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}

		// SDV: who.fishCaught.Length == 0 → only tutorial fish allowed; Sunfish fallback otherwise.
		// MC adaptation: if no candidate in this location is flagged tutorial (e.g. Desert),
		// suppress the gate entirely — otherwise the player would always get Sunfish from
		// outside Pelican Town, which contradicts the location's biome rules.
		boolean hasTutorialFishHere = false;
		for (CandidateRule cr : candidates) {
			if (cr.rule().isTutorialFish()) {
				hasTutorialFishHere = true;
				break;
			}
		}
		boolean isTutorialCatch = queryLocation == null && shouldApplyTutorialCatchGate(
				festivalFishingGame,
				hasTutorialFishHere,
				playerData.getDistinctFishCaughtCount());

		// SDV GameLocation.GetFishFromLocationData: OrderBy(Precedence), then shuffle ties.
		// Smaller values therefore run first (legendary/special rules use negative precedence).
		candidates.sort(Comparator.comparingInt(candidate -> candidate.rule().precedence()));
		List<CandidateRule> ordered = stableShuffleByPrecedence(candidates, random);

		// Stardew Valley style selection: iterate by precedence and roll chance (not weighted).
		SpawnFishRule chosen = null;
		ItemStack resolvedSpecialCatch = ItemStack.EMPTY;
		int targetedBaitTries = 0;
		SpawnFishRule firstNonTargetRule = null;
		for (int pass = 0; pass < 2 && chosen == null; pass++) {
			for (CandidateRule candidate : ordered) {
				SpawnFishRule rule = candidate.rule();
				if ((queryLocation != null || farmLocationPool.isPresent() || candidate.inherited())
						&& !rule.canBeInherited()) {
					continue;
				}
				if (!RULE_ELIGIBILITY_HOOK.allow(player, level, bobberPos, biomeHolder, rule)) {
					continue;
				}
				// SDV GameStateQuery.CheckConditions(spawn.Condition, ..., MagicBaitIgnoreQueryKeys)
				if (!matchesVanillaCondition(player, level, bobberPos, biomeHolder, rule, usingMagicBait)) {
					continue;
				}
				if (rule.catchLimit() >= 0 && playerData.getFishCatchCount(rule.itemId()) >= rule.catchLimit()) {
					continue;
				}
				if (rule.requireMagicBait() && !usingMagicBait) {
					continue;
				}
				if (!farmWideLocationPool && rule.fishAreaId() != null && !rule.fishAreaId().isBlank()) {
					if (fishAreaId == null || !rule.fishAreaId().equalsIgnoreCase(fishAreaId)) {
						continue;
					}
				}
				if (!rule.matchesBasic(fishingLevel, effectiveDepth)) {
					continue;
				}
				if (!farmWideLocationPool && !rule.matchesBiome(biomeHolder)) {
					continue;
				}
				if (!usingMagicBait && !rule.ignoreFishDataRequirements()) {
					if (!rule.matchesSeason(currentSeason) || !rule.matchesWeather(isRaining) || !rule.matchesStardewTime(stardewTime)) {
						continue;
					}
				}
				float chance = Math.max(0f, rule.chance());
				// === SDV SpawnFishData.GetChance: apply curiosity/luck/target bait modifiers to first roll ===
				if (hasCuriosityLure) {
					if (rule.curiosityLureBuff() > -1f) {
						chance += rule.curiosityLureBuff();
					} else {
						float max = 0.25f, min = 0.08f;
						chance = (max - min) / max * chance + (max - min) / 2f;
					}
				}
				if (rule.applyDailyLuck()) {
					chance += (float) playerData.getDailyLuck();
				}
				chance += rule.chanceBoostPerLuckLevel() * luckBuffLevel;
				if (baitTargetFishId != null && !baitTargetFishId.isBlank()
						&& baitTargetFishId.equals(rule.itemId())) {
					chance *= 1.66f;
				}
				// SDV SpawnFishData.GetChance: apply user-defined ChanceModifiers list.
				if (rule.chanceModifiers() != null && !rule.chanceModifiers().isEmpty()) {
					final boolean mb = usingMagicBait;
					chance = QuantityModifier.apply(chance, rule.chanceModifiers(), rule.chanceModifierMode(), random,
							cond -> evalGsqCondition(player, level, bobberPos, biomeHolder, cond, mb));
				}
				chance = Math.min(chance, 1.0f);
				if (chance <= 0f) {
					continue;
				}
				// SDV first roll: optional seeded RNG keyed on PreciseFishCaught (e.g. extended family fish).
				boolean rollPass;
				if (rule.useFishCaughtSeededRandom()) {
					long worldSeed = level.getSeed();
					long key = ((long) playerData.getPreciseFishCaught()) * 859L;
					java.util.Random seeded = new java.util.Random(worldSeed ^ key);
					rollPass = seeded.nextFloat() < chance;
				} else {
					rollPass = random.nextFloat() < chance;
				}
				if (!rollPass) {
					continue;
				}
				// SDV Locations rule SECRET_NOTE_OR_ITEM: the 8% rule roll happens first,
				// then tryToCreateUnseenSecretNote performs its own 80% -> 12% roll.
				if (SECRET_NOTE_ITEM_ID.equals(rule.itemId())) {
					ItemStack secretNote = com.stardew.craft.secretnote.SecretNoteService
							.tryCreateUnseenNote(player, random);
					if (secretNote.isEmpty()) {
						continue;
					}
					chosen = rule;
					resolvedSpecialCatch = secretNote;
					break;
				}
				// === SDV CheckGenericFishRequirements: depth-aware spawnRate second roll ===
				// Replicates GameLocation.cs CheckGenericFishRequirements (1.6 source).
				// SDV training-rod gate: reject difficulty>=50 unless rule overrides.
				if (isTrainingRod && rule.difficulty() >= 50) {
					continue;
				}
				// SDV tutorial gate: first-catch ever → only tutorial fish allowed.
				if (isTutorialCatch && !rule.isTutorialFish()) {
					continue;
				}
				if (rule.ignoreFishDataRequirements()) {
					chosen = rule;
					break;
				}
				float dropOff = rule.depthMultiplier() * rule.spawnRate();
				float c = rule.spawnRate();
				c -= Math.max(0, rule.maxDepth() - effectiveDepth) * dropOff;
				c += fishingLevel / 50f;
				if (isTrainingRod) {
					c *= 1.1f;
				}
				c = Math.min(c, 0.9f);
				if (c < 0.25f && hasCuriosityLure) {
					if (rule.curiosityLureBuff() > -1f) {
						c += rule.curiosityLureBuff();
					} else {
						c = (0.25f - 0.08f) / 0.25f * c + (0.25f - 0.08f) / 2f;
					}
				}
				if (baitTargetFishId != null && !baitTargetFishId.isBlank()
						&& baitTargetFishId.equals(rule.itemId())) {
					c *= 1.66f;
				}
				if (rule.applyDailyLuck()) {
					c += (float) playerData.getDailyLuck();
				}
				// SDV CheckGenericFishRequirements: apply user-defined ChanceModifiers to second roll too.
				if (rule.chanceModifiers() != null && !rule.chanceModifiers().isEmpty()) {
					final boolean mb2 = usingMagicBait;
					c = QuantityModifier.apply(c, rule.chanceModifiers(), rule.chanceModifierMode(), random,
							cond -> evalGsqCondition(player, level, bobberPos, biomeHolder, cond, mb2));
				}
				if (random.nextFloat() >= c) {
					continue;
				}
				// === end SDV second roll ===
				if (baitTargetFishId != null && !baitTargetFishId.isBlank()
						&& !baitTargetFishId.equals(rule.itemId())
						&& targetedBaitTries < 2) {
					if (firstNonTargetRule == null) {
						firstNonTargetRule = rule;
					}
					targetedBaitTries++;
					continue;
				}
				chosen = rule;
				break;
			}

			// In Stardew, "good bait" effectively gives an extra pass; otherwise only one attempt.
			if (!usingGoodBait) {
				break;
			}
		}

		if (chosen == null && firstNonTargetRule != null) {
			chosen = firstNonTargetRule;
		}

		if (chosen == null) {
			// SDV: tutorial first-catch always falls back to Sunfish ((O)145).
			if (isTutorialCatch) {
				Item sunfish = BuiltInRegistries.ITEM.get(ResourceLocation.parse("stardewcraft:sunfish"));
				if (sunfish != null && sunfish != Items.AIR) {
					return Optional.of(new FishSelection(new ItemStack(sunfish), 30, 0, 5, 15, false));
				}
			}
			return queryLocation != null ? Optional.empty() : Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		String resolvedItemId = chosen.itemId();
		SpawnFishRule resultRule = chosen;
		if (chosen.randomItemIds() != null && !chosen.randomItemIds().isEmpty()) {
			resolvedItemId = chosen.randomItemIds().get(random.nextInt(chosen.randomItemIds().size()));
			resultRule = getRuleByItemId(resolvedItemId).orElse(chosen);
		}
		Item item;
		try {
			item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(resolvedItemId));
		} catch (Exception ex) {
			item = Items.COD;
		}
		if (item == Items.AIR) {
			item = Items.COD;
		}

		@SuppressWarnings("null")
		ItemStack stack = resolvedSpecialCatch.isEmpty()
				? new ItemStack(item)
				: resolvedSpecialCatch.copy();
		boolean skip = chosen.skipMinigame() || isNonFishCatchable(resolvedItemId);
		return Optional.of(new FishSelection(stack, resultRule.difficulty(), resultRule.motionTypeId(),
				resultRule.minFishSize(), resultRule.maxFishSize(), skip));
	}

	private Optional<FishSelection> trySelectVanillaMineCatch(
			Holder<Biome> biome,
			boolean trainingRod,
			int fishingLevel,
			int waterDepth,
			int luckLevel,
			boolean curiosityLure,
			String targetedFishId,
			RandomSource random) {
        if(hasBiomeId(biome,"stardewcraft:skull_cavern"))
            return trainingRod?Optional.of(new FishSelection(getRandomJunk(random),0,0,0,0,true)):Optional.empty();
		String fishId = null;
		float chance = 0f;
		boolean lavaArea = hasBiomeTag(biome, "stardewcraft:is_mines_100");
		if (hasBiomeTag(biome, "stardewcraft:is_mines_20")) {
			fishId = "stardewcraft:stonefish";
			chance = mineRareFishChance(0.02f, 0.01f, fishingLevel, waterDepth,
					curiosityLure, fishId.equals(targetedFishId));
		} else if (hasBiomeTag(biome, "stardewcraft:is_mines_60")) {
			fishId = "stardewcraft:ice_pip";
			chance = mineRareFishChance(0.015f, 0.009f, fishingLevel, waterDepth,
					curiosityLure, fishId.equals(targetedFishId));
		} else if (lavaArea) {
			fishId = "stardewcraft:lava_eel";
			chance = mineRareFishChance(0.01f, 0.008f, fishingLevel, waterDepth,
					curiosityLure, fishId.equals(targetedFishId));
		} else {
			return Optional.empty();
		}

		// MineShaft.getFish returns uniform trash immediately for Training Rods.
		if (trainingRod) {
			return Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		if (random.nextFloat() < chance) {
			return createSelectionForItem(fishId, false);
		}
		if (lavaArea) {
			if (random.nextFloat() < 0.05f + Math.max(0, luckLevel) * 0.05f) {
				return createSelectionForItem("stardewcraft:cave_jelly", true);
			}
			return Optional.of(new FishSelection(getRandomJunk(random), 0, 0, 0, 0, true));
		}
		return Optional.empty();
	}

	static float mineRareFishChance(float baseChance, float multiplier,
			int fishingLevel, int waterDepth, boolean curiosityLure, boolean targetedBait) {
		float chanceMultiplier = 1f + 0.4f * fishingLevel + 0.1f * waterDepth;
		if (curiosityLure) chanceMultiplier += 5f;
		if (targetedBait) chanceMultiplier += 10f;
		return baseChance + multiplier * chanceMultiplier;
	}

	private Optional<FishSelection> createSelectionForItem(String itemId, boolean forceSkipMinigame) {
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id == null) return Optional.empty();
		Item item = BuiltInRegistries.ITEM.get(id);
		if (item == null || item == Items.AIR) return Optional.empty();
		SpawnFishRule metadata = getRuleByItemId(itemId).orElse(null);
		if (metadata == null) {
			return Optional.of(new FishSelection(new ItemStack(item), 0, 0, 0, 0,
					forceSkipMinigame || isNonFishCatchable(itemId)));
		}
		return Optional.of(new FishSelection(new ItemStack(item), metadata.difficulty(), metadata.motionTypeId(),
				metadata.minFishSize(), metadata.maxFishSize(),
				forceSkipMinigame || metadata.skipMinigame() || isNonFishCatchable(itemId)));
	}

	private static boolean isFishRule(SpawnFishRule rule) {
		if (rule == null || rule.itemId() == null || rule.itemId().isBlank()) {
			return false;
		}
		ResourceLocation id = ResourceLocation.tryParse(rule.itemId());
		if (id == null) {
			return false;
		}
		Item item = BuiltInRegistries.ITEM.get(id);
		return item != null && item != Items.AIR && item.builtInRegistryHolder().is(ModTags.Items.FISHES);
	}

	static List<String> resolveFishingLookupKeys(
			boolean fairFishingGame,
			boolean iceFishingContest,
			List<String> regularLocationKeys) {
		if (fairFishingGame) {
			return List.of("fishingGame");
		}
		if (iceFishingContest) {
			// SDV changes winter8 to a temporary location whose dedicated pool contains
			// the ice-contest fish; it does not use fall16's FishingGame location.
			return List.of("Temp");
		}
		return regularLocationKeys;
	}

	static boolean shouldApplyTutorialCatchGate(
			boolean festivalFishingGame,
			boolean hasTutorialFishHere,
			int distinctFishCaught) {
		// Festival catches intentionally don't update the permanent collection. Applying
		// the first-catch gate here would therefore lock a new player to Sunfish forever.
		return !festivalFishingGame && hasTutorialFishHere && distinctFishCaught == 0;
	}

	private List<CandidateRule> collectCandidatesByKeys(List<String> lookupKeys) {
		return collectCandidatesByKeys(lookupKeys, false);
	}

	private List<CandidateRule> collectCandidatesByKeys(List<String> lookupKeys, boolean skipDefaultPool) {
		LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
		if (!skipDefaultPool) {
			uniqueKeys.add("Default");
		}
		uniqueKeys.addAll(lookupKeys);

		boolean hasMappedLocationData = lookupKeys.stream().anyMatch(byLocationKey::containsKey);
		if (!hasMappedLocationData && byLocationKey.containsKey(LEGACY_COMPAT_POOL_KEY)) {
			// Compatibility fallback during Step 2 migration: only use the legacy mega-pool
			// when no mapped vanilla location file is present for this biome route.
			uniqueKeys.add(LEGACY_COMPAT_POOL_KEY);
		}

		List<CandidateRule> out = new ArrayList<>();
		Set<String> idsFromEarlierPools = new HashSet<>();
		for (String key : uniqueKeys) {
			FishingLocationData data = byLocationKey.get(key);
			if (data != null) {
				boolean inherited = (INHERITED_POOL_KEYS.contains(key) && !lookupKeys.contains(key))
						|| LEGACY_COMPAT_POOL_KEY.equals(key);
				Set<String> idsInThisPool = new HashSet<>();
				for (SpawnFishRule rule : data.fish()) {
					// LOCATION_FISH and the project's shared Ginger Island biome may combine
					// vanilla pools. Identical rules from a later pool must not gain extra rolls;
					// duplicate variants inside one pool (e.g. SquidFest) remain distinct.
					if (!idsFromEarlierPools.contains(rule.id())) {
						out.add(new CandidateRule(rule, inherited));
					}
					idsInThisPool.add(rule.id());
				}
				idsFromEarlierPools.addAll(idsInThisPool);
			}
		}
		return out;
	}

	private record CandidateRule(SpawnFishRule rule, boolean inherited) {
	}

	private boolean useDesertFestivalPoolOnly(Holder<Biome> biomeHolder) {
		return DesertFestivalService.isFestivalDay() && hasBiomeTag(biomeHolder, "stardewcraft:is_desert");
	}

	/**
	 * Match vanilla location buckets first (from Data/Locations), then fall back to legacy keys.
	 */
	private List<String> resolveVanillaAlignedLocationKeys(
			ServerLevel level,
			Holder<Biome> biomeHolder,
			BlockPos position) {
		return resolveVanillaAlignedLocationKeysStatic(level, biomeHolder, position);
	}

	/** Public access for systems outside selection (e.g. splash-point ticker). */
	public static List<String> resolveVanillaAlignedLocationKeysStatic(ServerLevel level, Holder<Biome> biomeHolder) {
		return resolveVanillaAlignedLocationKeysStatic(level, biomeHolder, null);
	}

	/** Position-aware access for fixed sublocations which share an overworld biome. */
	public static List<String> resolveVanillaAlignedLocationKeysStatic(
			ServerLevel level,
			Holder<Biome> biomeHolder,
			BlockPos position) {
		return com.stardew.craft.api.v1.internal.fishing.StardewFishingLocationKeyRegistry
				.resolve(
						level,
						biomeHolder,
						position,
						resolveCoreLocationKeys(level, biomeHolder, position));
	}

	/**
	 * Riverland Farm's Data/Locations rule delegates each cast to Forest 30% of
	 * the time and Town otherwise. Keep the roll at location-pool resolution so
	 * the inherited pool still applies its own season, time and weather rules.
	 */
	public static Optional<String> resolveRiverlandFarmFishingPool(
			ServerLevel level,
			BlockPos position,
			RandomSource random) {
		UUID owner = FarmInstanceRegistry.get(level.getServer()).getOwnerAt(position);
		if (owner == null) return Optional.empty();
		var farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
		if (farm == null || !farm.contains(position)
				|| !farm.getFarmLayoutId().equals(
						com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
								.builtinId(FarmType.RIVERLAND))) {
			return Optional.empty();
		}
		return Optional.of(random.nextDouble() < 0.3D ? "Forest" : "Town");
	}

	public enum ForestFarmFishingRoll {
		WOODSKIP,
		FOREST,
		JUNK
	}

	public enum SpecialFarmFishingRoll {
		FOREST,
		MOUNTAIN,
		JUNK
	}

	public enum BeachFarmFishingRoll {
		SEAWEED,
		BEACH_FORAGE,
		BEACH,
		JUNK
	}

	/** Forest Farm: 5% Woodskip, 45% Forest river fish, otherwise trash. */
	public static Optional<ForestFarmFishingRoll> resolveForestFarmFishingRoll(
			ServerLevel level,
			BlockPos position,
			RandomSource random) {
		UUID owner = FarmInstanceRegistry.get(level.getServer()).getOwnerAt(position);
		if (owner == null) return Optional.empty();
		var farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
		if (farm == null || !farm.contains(position)
				|| !farm.getFarmLayoutId().equals(
						com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
								.builtinId(FarmType.FOREST))) {
			return Optional.empty();
		}
		double roll = random.nextDouble();
		if (roll < 0.05D) return Optional.of(ForestFarmFishingRoll.WOODSKIP);
		if (roll < 0.50D) return Optional.of(ForestFarmFishingRoll.FOREST);
		return Optional.of(ForestFarmFishingRoll.JUNK);
	}

	/** Beach Farm: every cast inside the farm follows the source's sequential
	 * 15% seaweed, 6% beach forage, 66% Beach-pool rules. */
	public static Optional<BeachFarmFishingRoll> resolveBeachFarmFishingRoll(
			ServerLevel level, BlockPos bobberPosition, RandomSource random) {
		UUID owner = FarmInstanceRegistry.get(level.getServer()).getOwnerAt(bobberPosition);
		if (owner == null) return Optional.empty();
		var farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
		if (farm == null || !farm.contains(bobberPosition)
				|| !farm.getFarmLayoutId().equals(
						StardewFarmLayoutRegistry.builtinId(FarmType.BEACH))) return Optional.empty();
		if (random.nextDouble() < 0.15D) return Optional.of(BeachFarmFishingRoll.SEAWEED);
		if (random.nextDouble() < 0.06D) return Optional.of(BeachFarmFishingRoll.BEACH_FORAGE);
		if (random.nextDouble() < 0.66D) return Optional.of(BeachFarmFishingRoll.BEACH);
		return Optional.of(BeachFarmFishingRoll.JUNK);
	}

	/** Hilltop 50% Forest, Wilderness 35% Mountain, Four Corners 50% Forest,
	 * and Meadowlands 40% Forest. Every water block inside a matching farm uses
	 * the farm-wide rule; custom farm maps do not maintain brittle pond masks. */
	public static Optional<SpecialFarmFishingRoll> resolveSpecialFarmFishingRoll(
			ServerLevel level,
			BlockPos position,
			RandomSource random) {
		return resolveSpecialFarmFishingRoll(level, position, position, random);
	}

	public static Optional<SpecialFarmFishingRoll> resolveSpecialFarmFishingRoll(
			ServerLevel level,
			BlockPos fisherPosition,
			BlockPos bobberPosition,
			RandomSource random) {
		UUID owner = FarmInstanceRegistry.get(level.getServer()).getOwnerAt(bobberPosition);
		if (owner == null) return Optional.empty();
		var farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
		if (farm == null || !farm.contains(bobberPosition)) return Optional.empty();
		ResourceLocation layoutId = farm.getFarmLayoutId();
		if (layoutId.equals(com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
				.builtinId(FarmType.HILLTOP))) {
			return Optional.of(random.nextDouble() < 0.50D
					? SpecialFarmFishingRoll.FOREST : SpecialFarmFishingRoll.JUNK);
		}
		if (layoutId.equals(com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
				.builtinId(FarmType.WILDERNESS))) {
			return Optional.of(random.nextDouble() < 0.35D
					? SpecialFarmFishingRoll.MOUNTAIN : SpecialFarmFishingRoll.JUNK);
		}
		if (layoutId.equals(com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
				.builtinId(FarmType.FOUR_CORNERS))) {
			return Optional.of(random.nextDouble() < 0.50D
					? SpecialFarmFishingRoll.FOREST : SpecialFarmFishingRoll.JUNK);
		}
		if (layoutId.equals(com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
				.builtinId(FarmType.MEADOWLANDS))) {
			return Optional.of(random.nextDouble() < 0.40D
					? SpecialFarmFishingRoll.FOREST : SpecialFarmFishingRoll.JUNK);
		}
		return Optional.empty();
	}

	public static boolean isFourCornersFishingRegion(ServerLevel level, BlockPos fisherPosition) {
		UUID owner = FarmInstanceRegistry.get(level.getServer()).getOwnerAt(fisherPosition);
		if (owner == null) return false;
		var farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
		return farm != null && isFourCornersFishingRegion(farm, fisherPosition);
	}

	private static boolean isFourCornersFishingRegion(
			com.stardew.craft.farm.FarmInstance farm,
			BlockPos fisherPosition) {
		return farm.contains(fisherPosition)
				&& farm.getFarmLayoutId().equals(
				com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
						.builtinId(FarmType.FOUR_CORNERS));
	}

	private static List<String> resolveCoreLocationKeys(
			ServerLevel level,
			Holder<Biome> biomeHolder,
			BlockPos position) {
		if (com.stardew.craft.festival.nightmarket.NightMarketSubmarineService
				.isInsideSubmarineBounds(position)) {
			return List.of("Submarine");
		}

		if (hasBiomeTag(biomeHolder, "stardewcraft:is_night_market")) {
			// BeachNightMarket's vanilla LOCATION_FISH rule delegates to Beach.
			return List.of("BeachNightMarket", "Beach");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_pirate_cove")) {
			return List.of("IslandSouthEastCave");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_ginger_island_ocean")) {
			// South and SouthEast have identical fish rules. IslandWest adds Octopus
			// to ocean water, so include it once for the project's shared island-ocean biome.
			return List.of("IslandSouth", "IslandWest");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_ginger_island_river")) {
			// North and West freshwater have the same ordinary fish pool.
			return List.of("IslandNorth");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_ginger_island_pond")) {
			return List.of("IslandWest");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_volcano")) {
			return List.of("Caldera");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_witch_swamp")) {
			return List.of("WitchSwamp");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_mutant_bug_lair")) {
			return List.of("BugLand");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_sewers")) {
			return List.of("Sewer");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_desert")) {
			return List.of("Desert");
		}
		if (hasBiomeId(biomeHolder,"stardewcraft:skull_cavern") || hasBiomeTag(biomeHolder, "stardewcraft:is_mines_20")
				|| hasBiomeTag(biomeHolder, "stardewcraft:is_mines_60")
				|| hasBiomeTag(biomeHolder, "stardewcraft:is_mines_100")) {
			return List.of("UndergroundMine");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_beach") || hasBiomeTag(biomeHolder, "stardewcraft:is_ocean")) {
			return List.of("Beach");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_town_river") || hasBiomeTag(biomeHolder, "stardewcraft:is_jojamart_bridge")) {
			return List.of("Town");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_mountain_lake")) {
			return List.of("Mountain");
		}
		// secret_woods_pond is also (incorrectly) included in is_forest_pond;
		// resolve the more specific vanilla Woods location first so pools never stack.
		if (hasBiomeId(biomeHolder, "stardewcraft:secret_woods_pond")) {
			return List.of("Woods");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_forest_pond")
				|| hasBiomeTag(biomeHolder, "stardewcraft:is_forest_river")
				|| hasBiomeTag(biomeHolder, "stardewcraft:is_forest_waterfall")) {
			return List.of("Forest");
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_secret_woods")) {
			return List.of("Woods");
		}
		return List.of("Default");
	}

	private static boolean hasBiomeId(Holder<Biome> biomeHolder, String biomeId) {
		ResourceLocation expected = ResourceLocation.parse(biomeId);
		return biomeHolder.unwrapKey().map(key -> key.location().equals(expected)).orElse(false);
	}

	/**
	 * Representative environment for an inherited location pool. Farm fishing
	 * is farm-wide, so this holder is used only while evaluating the delegated
	 * pool's hooks and location conditions; it never changes world biome data.
	 */
	private static Holder<Biome> biomeForLocationPool(ServerLevel level, String locationKey) {
		String biomePath = switch (locationKey) {
			case "Beach" -> "beach_ocean";
			case "Mountain" -> "mountain_lake";
			case "Town" -> "pelican_town_river";
			case "Forest" -> "forest_river";
			default -> "stardew_default";
		};
		return level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
				net.minecraft.resources.ResourceKey.create(
						Registries.BIOME,
						ResourceLocation.fromNamespaceAndPath("stardewcraft", biomePath)));
	}

	private String resolveVanillaFishAreaId(Holder<Biome> biomeHolder) {
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_beach_pier")) {
			return "Ocean";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_beach") || hasBiomeTag(biomeHolder, "stardewcraft:is_ocean")) {
			return "Ocean";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_town_river") || hasBiomeTag(biomeHolder, "stardewcraft:is_river")) {
			return "River";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_mountain_lake")) {
			return "Lake";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_forest_pond")) {
			return "Lake";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_ginger_island_pond")) {
			return "Freshwater";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_ginger_island_river")) {
			return "Freshwater";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_mutant_bug_lair")) {
			return "Marsh";
		}
		if (hasBiomeTag(biomeHolder, "stardewcraft:is_desert")) {
			return "TopPond";
		}
		return null;
	}

	@SuppressWarnings("null")
	private static boolean hasBiomeTag(Holder<Biome> biomeHolder, String tagId) {
		ResourceLocation id = ResourceLocation.parse(tagId);
		TagKey<Biome> tag = TagKey.create(Registries.BIOME, id);
		return biomeHolder.is(tag);
	}

	/** Public mirror for splash-point ticker. */
	public static boolean hasBiomeTagPublic(Holder<Biome> biomeHolder, String tagId) {
		return hasBiomeTag(biomeHolder, tagId);
	}

	public static boolean isStardewFishingDimensionPublic(ServerLevel level) {
		return isStardewFishingDimension(level);
	}

	private static boolean isStardewFishingDimension(ServerLevel level) {
		return level.dimension() == ModDimensions.STARDEW_VALLEY
				|| level.dimension() == ModMiningDimensions.STARDEW_MINING;
	}

	private static ItemStack getRodFromPlayer(ServerPlayer player) {
		if (player == null) return ItemStack.EMPTY;
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

	private static boolean isUsingGoodBait(ItemStack rodStack) {
		if (rodStack == null || rodStack.isEmpty()) {
			return false;
		}
		if (!(rodStack.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem rodItem)) {
			return false;
		}
		ItemStack bait = rodItem.getAttachmentsForTooltip(rodStack).bait();
		if (bait.isEmpty()) {
			return false;
		}
		@SuppressWarnings("null")
		ResourceLocation baitId = BuiltInRegistries.ITEM.getKey(bait.getItem());
		// Stardew: "good bait" means anything other than the basic bait item.
		return !baitId.toString().equals("stardewcraft:bait");
	}

	private static boolean hasCuriosityLure(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		var main = player.getMainHandItem();
		if (!main.isEmpty() && main.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem) {
			return com.stardew.craft.item.tool.FishingRodItem.hasTackle(main, "stardewcraft:curiosity_lure");
		}
		var off = player.getOffhandItem();
		if (!off.isEmpty() && off.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem) {
			return com.stardew.craft.item.tool.FishingRodItem.hasTackle(off, "stardewcraft:curiosity_lure");
		}
		return false;
	}

	private static String getTargetedBaitFishId(ItemStack rodStack) {
		if (rodStack == null || rodStack.isEmpty()) {
			return null;
		}
		if (!(rodStack.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem rodItem)) {
			return null;
		}
		ItemStack bait = rodItem.getAttachmentsForTooltip(rodStack).bait();
		if (bait.isEmpty() || !(bait.getItem() instanceof SpecificBaitItem)) {
			return null;
		}
		String fishId = SpecificBaitItem.getTargetFishId(bait);
		if (fishId == null || fishId.isBlank()) {
			return null;
		}
		return fishId;
	}

	/**
	 * SDV {@code GameStateQuery.MagicBaitIgnoreQueryKeys}: when magic bait is equipped,
	 * time/season/weather/day clauses are skipped (treated as true).
	 */
	private static final java.util.Set<String> MAGIC_BAIT_IGNORE_KEYS = java.util.Set.of(
			"DAY_OF_MONTH", "DAY_OF_WEEK", "DAYS_PLAYED",
			"LOCATION_SEASON", "SEASON", "SEASON_DAY",
			"WEATHER", "TIME");

	private static boolean matchesVanillaCondition(ServerPlayer player, ServerLevel level, BlockPos bobberPos,
									  Holder<Biome> biomeHolder, SpawnFishRule rule, boolean usingMagicBait) {
		boolean proposed = evalGsqCondition(
				player,
				level,
				bobberPos,
				biomeHolder,
				rule.condition(),
				usingMagicBait);
		return com.stardew.craft.api.v1.internal.fishing
				.StardewFishingRuleConditionRegistry.evaluate(
						player,
						level,
						bobberPos,
						biomeHolder,
						rule,
						usingMagicBait,
						proposed);
	}

	/** SDV {@code GameStateQuery.CheckConditions}: evaluates a free-form condition string. */
	private static boolean evalGsqCondition(ServerPlayer player, ServerLevel level, BlockPos bobberPos,
											Holder<Biome> biomeHolder, String condition, boolean usingMagicBait) {
		if (condition == null || condition.isBlank()) {
			return true;
		}
		String[] clauses = condition.split(",");
		for (String raw : clauses) {
			String clause = raw.trim();
			if (clause.isEmpty()) continue;
			boolean negate = clause.startsWith("!");
			if (negate) clause = clause.substring(1).trim();
			String head = clause.split("\\s+", 2)[0];
			if (usingMagicBait && MAGIC_BAIT_IGNORE_KEYS.contains(head)) {
				continue;
			}
			Boolean known = evalGsqClause(player, level, clause);
			// SDV GameStateQuery: an unknown query is a parse error and fails the whole
			// condition regardless of negation.
			if (known == null) return false;
			boolean result = known;
			if (negate) result = !result;
			if (!result) return false;
		}
		return true;
	}

	/** Returns {@code null} for queries this evaluator does not know (SDV treats those as errors). */
	private static Boolean evalGsqClause(ServerPlayer player, ServerLevel level, String clause) {
		String[] parts = clause.split("\\s+");
		if (parts.length == 0) return true;
		String op = parts[0];
		switch (op) {
			case "TRUE":
				return true;
			case "FALSE":
				return false;
			case "RANDOM": {
				// RANDOM <chance> [@addDailyLuck]
				if (parts.length < 2) return false;
				double chance;
				try {
					chance = Double.parseDouble(parts[1]);
				} catch (NumberFormatException ex) {
					return false;
				}
				for (int i = 2; i < parts.length; i++) {
					if (parts[i].equalsIgnoreCase("@addDailyLuck")) {
						chance += PlayerStardewDataAPI.getDailyLuck(player);
						break;
					}
				}
				return level.random.nextDouble() < chance;
			}
			case "IS_FESTIVAL_DAY": {
				// IS_FESTIVAL_DAY [locationContext] [dayOffset]
				int offset = 0;
				if (parts.length >= 3) {
					try {
						offset = Integer.parseInt(parts[2]);
					} catch (NumberFormatException ex) {
						return false;
					}
				}
				if (offset == 0) return FestivalService.isFestivalDay();
				com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
				if (tm == null) return false;
				int totalDays = Math.floorMod(tm.getAbsoluteDay() - 1 + offset, 112);
				return FestivalService.isFestivalDay(totalDays % 28 + 1, totalDays / 28);
			}
			case "DAY_OF_WEEK": {
				// DAY_OF_WEEK <day1> [<day2> ...]   (.NET DayOfWeek: Sunday=0; SDV weekday = dayOfMonth % 7)
				if (parts.length < 2) return false;
				com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
				if (tm == null) return false;
				int today = tm.getCurrentDay() % 7;
				for (int i = 1; i < parts.length; i++) {
					int wanted = parseDayOfWeek(parts[i]);
					if (wanted < 0) return false;
					if (wanted == today) return true;
				}
				return false;
			}
			case "DAYS_PLAYED": {
				// DAYS_PLAYED <min> [max]; DaysPlayed counts the join day as 1.
				if (parts.length < 2) return false;
				com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
				if (tm == null) return false;
				int min;
				int max = Integer.MAX_VALUE;
				try {
					min = Integer.parseInt(parts[1]);
					if (parts.length >= 3) max = Integer.parseInt(parts[2]);
				} catch (NumberFormatException ex) {
					return false;
				}
				int firstJoinDay = PlayerStardewDataAPI.getData(player).getFirstJoinDay();
				int daysPlayed = firstJoinDay < 0
						? tm.getAbsoluteDay()
						: tm.getAbsoluteDay() - firstJoinDay + 1;
				return daysPlayed >= min && daysPlayed <= max;
			}
			case "PLAYER_SPECIAL_ORDER_RULE_ACTIVE": {
				// PLAYER_SPECIAL_ORDER_RULE_ACTIVE Current <ruleId>
				if (parts.length < 3) return false;
				return PlayerStardewDataAPI.isSpecialOrderRuleActive(player, parts[2]);
			}
			case "PLAYER_HAS_MAIL": {
				// PLAYER_HAS_MAIL Host <mailId>
				if (parts.length < 3) return false;
				return PlayerStardewDataAPI.getData(player).hasMailFlag(parts[2]);
			}
			case "PLAYER_HAS_ITEM": {
				// PLAYER_HAS_ITEM Current (O)<id>  -- check inventory by qualified id
				if (parts.length < 3) return false;
				String qid = parts[2];
				String want = vanillaQualifiedIdToModItemId(qid);
				if (want == null) return false;
				ResourceLocation wantLoc = ResourceLocation.tryParse(want);
				if (wantLoc == null) return false;
				for (ItemStack st : player.getInventory().items) {
					if (!st.isEmpty()) {
						ResourceLocation k = BuiltInRegistries.ITEM.getKey(st.getItem());
						if (wantLoc.equals(k)) return true;
					}
				}
				return false;
			}
			case "LOCATION_SEASON": {
				// LOCATION_SEASON Here <season1> [<season2> ...]
				if (parts.length < 3) return false;
				String current = staticGetCurrentSeason(level);
				for (int i = 2; i < parts.length; i++) {
					if (parts[i].equalsIgnoreCase(current)) return true;
				}
				return false;
			}
			case "SEASON": {
				// SEASON <season1> [<season2> ...]
				if (parts.length < 2) return false;
				String current = staticGetCurrentSeason(level);
				for (int i = 1; i < parts.length; i++) {
					if (parts[i].equalsIgnoreCase(current)) return true;
				}
				return false;
			}
			case "SEASON_DAY": {
				// SEASON_DAY <season> <day> [<season> <day> ...]
				if (parts.length < 3) return false;
				com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
				if (tm == null) return false;
				String current = staticGetCurrentSeason(level);
				int day = tm.getCurrentDay();
				for (int i = 1; i + 1 < parts.length; i += 2) {
					if (parts[i].equalsIgnoreCase(current)) {
						try {
							if (Integer.parseInt(parts[i + 1]) == day) return true;
						} catch (NumberFormatException ignored) {}
					}
				}
				return false;
			}
			case "YEAR": {
				// YEAR <minimum year>, matching SDV's year-gated location rules.
				if (parts.length < 2) return false;
				com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
				if (tm == null) return false;
				try {
					return tm.getCurrentYear() >= Integer.parseInt(parts[1]);
				} catch (NumberFormatException ex) {
					return false;
				}
			}
			case "DAY_OF_MONTH": {
				// DAY_OF_MONTH <day1> [<day2> ...]   ("even"/"odd" 也支持)
				if (parts.length < 2) return false;
				com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
				if (tm == null) return false;
				int day = tm.getCurrentDay();
				for (int i = 1; i < parts.length; i++) {
					String t = parts[i];
					if (t.equalsIgnoreCase("even")) {
						if (day % 2 == 0) return true;
					} else if (t.equalsIgnoreCase("odd")) {
						if (day % 2 == 1) return true;
					} else {
						try {
							if (Integer.parseInt(t) == day) return true;
						} catch (NumberFormatException ignored) {}
					}
				}
				return false;
			}
			case "TIME": {
				// TIME <minTime> <maxTime>   (HHMM, inclusive on min, exclusive on max — SDV 语义)
				if (parts.length < 3) return false;
				try {
					int min = Integer.parseInt(parts[1]);
					int max = Integer.parseInt(parts[2]);
					int now = currentStardewTime();
					return now >= min && now < max;
				} catch (NumberFormatException ex) {
					return false;
				}
			}
			case "WEATHER": {
				// WEATHER <location> <weather1> [<weather2> ...]
				// 我们没有按地点细分天气，统一使用全局 WeatherManager。
				if (parts.length < 3) return false;
				boolean raining = com.stardew.craft.weather.WeatherManager.isRaining(level);
				for (int i = 2; i < parts.length; i++) {
					String w = parts[i].toLowerCase(java.util.Locale.ROOT);
					switch (w) {
						case "rain", "rainy", "stormy", "snow", "green_rain":
							if (raining) return true;
							break;
						case "sun", "sunny", "wind", "festival":
							if (!raining) return true;
							break;
						default:
							break;
					}
				}
				return false;
			}
			case "IS_PASSIVE_FESTIVAL_OPEN": {
				// IS_PASSIVE_FESTIVAL_OPEN <festivalId> [TIME hhmm hhmm]
				if (parts.length < 2 || !FestivalService.isPassiveFestivalOpen(parts[1])) return false;
				if (parts.length >= 5 && parts[2].equalsIgnoreCase("TIME")) {
					try {
						int min = Integer.parseInt(parts[3]);
						int max = Integer.parseInt(parts[4]);
						int now = currentStardewTime();
						return now >= min && now < max;
					} catch (NumberFormatException ex) {
						return false;
					}
				}
				return true;
			}
			default:
				// Unknown query: SDV reports an error and the condition fails.
				return null;
		}
	}

	/** SDV {@code WorldDate.TryGetDayOfWeekFor}; returns -1 when the value is not a weekday. */
	private static int parseDayOfWeek(String raw) {
		try {
			return Math.floorMod(Integer.parseInt(raw), 7);
		} catch (NumberFormatException ignored) {
		}
		return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
			case "sun", "sunday" -> 0;
			case "mon", "monday" -> 1;
			case "tue", "tuesday" -> 2;
			case "wed", "wednesday" -> 3;
			case "thu", "thursday" -> 4;
			case "fri", "friday" -> 5;
			case "sat", "saturday" -> 6;
			default -> -1;
		};
	}

	private static String vanillaQualifiedIdToModItemId(String qid) {
		// Maps SDV qualified item id like "(O)308" to mod item id, if known.
		// Currently we only need a few; return null for unmapped → condition will fail.
		if (qid == null || qid.isBlank()) return null;
		switch (qid) {
			case "(O)308": return "stardewcraft:void_mayonnaise";
			default: return null;
		}
	}

	private static String staticGetCurrentSeason(ServerLevel level) {
		com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
		int idx = tm != null ? tm.getCurrentSeason() : 0;
		return switch (idx) {
			case 0 -> "spring";
			case 1 -> "summer";
			case 2 -> "fall";
			case 3 -> "winter";
			default -> "spring";
		};
	}

	/**
	 * 取当前星露谷时间（HHMM 格式，600~2600）。在凌晨 0~2 点会返回 2400~2600，
	 * 与 fishing JSON 中 timeRanges 的约定一致。
	 */
	public static int currentStardewTime() {
		com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
		if (tm == null) {
			return 600;
		}
		int hour = tm.getHour();
		int minute = tm.getMinute();
		return hour * 100 + minute;
	}

	/**
	 * 旧版兼容方法
	 */
	public Optional<FishSelection> selectFish(ServerPlayer player, ServerLevel level, int waterDepth, RandomSource random) {
		// 使用玩家位置作为浮漂位置（兼容旧代码）
		return selectFish(player, level, player.blockPosition(), waterDepth, random);
	}

	/**
	 * 获取当前季节
	 */
	private String getCurrentSeason(ServerLevel level) {
		return staticGetCurrentSeason(level);
	}

	/**
	 * 获取随机垃圾物品
	 * SDV 原版（GameLocation.getFish 末尾回退 + Data/Locations.json 的 trash RandomItemId 池）：
	 * 在 6 个垃圾物品中均匀随机 —— Joja Cola(167) / Trash(168) / Driftwood(169) /
	 * Broken Glasses(170) / Broken CD(171) / Soggy Newspaper(172)。
	 * 海草/藻类/Joja可乐等应作为独立鱼规则配置在位置数据中，不属于回退垃圾池。
	 */
	@SuppressWarnings("null")
	public ItemStack getRandomJunk(RandomSource random) {
		// SDV 原版回退：6 件垃圾均匀随机
		String[] ids = new String[] {
			"stardewcraft:joja_cola",
			"stardewcraft:trash",
			"stardewcraft:driftwood",
			"stardewcraft:broken_glasses",
			"stardewcraft:broken_cd",
			"stardewcraft:soggy_newspaper"
		};
		String pick = ids[random.nextInt(ids.length)];
		try {
			Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(pick));
			if (item != null && item != Items.AIR) {
				return new ItemStack(item);
			}
			Item trash = BuiltInRegistries.ITEM.get(ResourceLocation.parse("stardewcraft:trash"));
			return new ItemStack(trash);
		} catch (Exception e) {
			StardewCraft.LOGGER.error("Failed to get trash item", e);
			return new ItemStack(Items.STICK);
		}
	}

	private static List<CandidateRule> stableShuffleByPrecedence(List<CandidateRule> sorted, RandomSource random) {
		List<CandidateRule> result = new ArrayList<>(sorted.size());
		int i = 0;
		while (i < sorted.size()) {
			int precedence = sorted.get(i).rule().precedence();
			int j = i + 1;
			while (j < sorted.size() && sorted.get(j).rule().precedence() == precedence) {
				j++;
			}
			List<CandidateRule> group = new ArrayList<>(sorted.subList(i, j));
			Collections.shuffle(group, new Random(random.nextLong()));
			result.addAll(group);
			i = j;
		}
		return result;
	}

	public record FishSelection(ItemStack stack, int difficulty, int motionTypeId,
								int minFishSize, int maxFishSize, boolean skipMinigame) {
	}

	@FunctionalInterface
	public interface RuleEligibilityHook {
		RuleEligibilityHook ALLOW_ALL = (player, level, bobberPos, biomeHolder, rule) -> true;

		boolean allow(ServerPlayer player, ServerLevel level, BlockPos bobberPos, Holder<Biome> biomeHolder, SpawnFishRule rule);
	}

	/**
	 * 获取所有已加载的鱼类规则 (用于JEI等展示)
	 */
	public Map<String, FishingLocationData> getLocationDataSnapshot() {
		return byLocationKey;
	}

	/**
	 * 获取所有已加载的鱼类规则 (用于JEI等展示)
	 */
	public List<SpawnFishRule> getAllFishRules() {
		List<SpawnFishRule> allRules = new ArrayList<>();
		for (FishingLocationData data : byLocationKey.values()) {
			allRules.addAll(data.fish());
		}
		return allRules;
	}

	/**
	 * 根据物品ID获取对应的鱼类规则
	 */
	public Optional<SpawnFishRule> getRuleByItemId(String itemId) {
		for (FishingLocationData data : byLocationKey.values()) {
			for (SpawnFishRule rule : data.fish()) {
				if (rule.itemId().equals(itemId)) {
					return Optional.of(rule);
				}
			}
		}
		return Optional.empty();
	}

	private static com.google.gson.JsonObject ruleToJson(SpawnFishRule r) {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("id", r.id());
		o.addProperty("precedence", r.precedence());
		o.addProperty("item", r.itemId());
		if (r.randomItemIds() != null && !r.randomItemIds().isEmpty()) {
			com.google.gson.JsonArray randomItems = new com.google.gson.JsonArray();
			r.randomItemIds().forEach(randomItems::add);
			o.add("randomItems", randomItems);
		}
		o.addProperty("chance", r.chance());
		o.addProperty("difficulty", r.difficulty());
		o.addProperty("motionType", r.motionTypeId());
		o.addProperty("minFishSize", r.minFishSize());
		o.addProperty("maxFishSize", r.maxFishSize());
		o.addProperty("minFishingLevel", r.minFishingLevel());
		o.addProperty("minDistanceFromShore", r.minDistanceFromShore());
		o.addProperty("maxDistanceFromShore", r.maxDistanceFromShore());
		o.addProperty("skipMinigame", r.skipMinigame());
		if (r.fishAreaId() != null) o.addProperty("fishAreaId", r.fishAreaId());
		o.addProperty("canBeInherited", r.canBeInherited());
		o.addProperty("requireMagicBait", r.requireMagicBait());
		o.addProperty("catchLimit", r.catchLimit());
		if (r.condition() != null) o.addProperty("condition", r.condition());
		o.addProperty("weather", r.weather());
		if (r.isTutorialFish()) o.addProperty("isTutorialFish", true);
		if (r.ignoreFishDataRequirements()) o.addProperty("ignoreFishDataRequirements", true);
		if (r.useFishCaughtSeededRandom()) o.addProperty("useFishCaughtSeededRandom", true);
		if (r.chanceBoostPerLuckLevel() != 0f) {
			o.addProperty("chanceBoostPerLuckLevel", r.chanceBoostPerLuckLevel());
		}
		if (r.chanceModifiers() != null && !r.chanceModifiers().isEmpty()) {
			com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
			for (QuantityModifier.Entry m : r.chanceModifiers()) {
				com.google.gson.JsonObject mo = new com.google.gson.JsonObject();
				mo.addProperty("modification", m.modification().name());
				mo.addProperty("amount", m.amount());
				if (m.randomAmount() != null && !m.randomAmount().isEmpty()) {
					com.google.gson.JsonArray ra = new com.google.gson.JsonArray();
					for (Float f : m.randomAmount()) ra.add(f);
					mo.add("randomAmount", ra);
				}
				if (m.condition() != null) mo.addProperty("condition", m.condition());
				arr.add(mo);
			}
			o.add("chanceModifiers", arr);
			o.addProperty("chanceModifierMode", r.chanceModifierMode().name());
		}
		if (r.biomes() != null && !r.biomes().isEmpty()) {
			com.google.gson.JsonArray a = new com.google.gson.JsonArray();
			r.biomes().forEach(a::add);
			o.add("biomes", a);
		}
		if (r.biomeTags() != null && !r.biomeTags().isEmpty()) {
			com.google.gson.JsonArray a = new com.google.gson.JsonArray();
			r.biomeTags().forEach(a::add);
			o.add("biomeTags", a);
		}
		if (r.seasons() != null && !r.seasons().isEmpty()) {
			com.google.gson.JsonArray a = new com.google.gson.JsonArray();
			r.seasons().forEach(a::add);
			o.add("seasons", a);
		}
		if (r.timeRanges() != null && !r.timeRanges().isEmpty()) {
			com.google.gson.JsonArray ranges = new com.google.gson.JsonArray();
			for (int[] range : r.timeRanges()) {
				com.google.gson.JsonArray pair = new com.google.gson.JsonArray();
				pair.add(range[0]);
				if (range.length > 1) pair.add(range[1]);
				ranges.add(pair);
			}
			o.add("timeRanges", ranges);
		}
		return o;
	}

	public static final class ReloadListener extends SimpleJsonResourceReloadListener {
		public ReloadListener() {
			super(GSON, "fishing/locations");
		}

		@Override
		protected void apply(@SuppressWarnings("null") Map<ResourceLocation, JsonElement> objects, @SuppressWarnings("null") ResourceManager resourceManager, @SuppressWarnings("null") ProfilerFiller profiler) {
			Map<String, FishingLocationData> loaded = new HashMap<>();
			for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
				try {
					JsonObject root = entry.getValue().getAsJsonObject();
					FishingLocationData data = FishingLocationData.fromJson(root);
					loaded.put(data.locationKey(), data);
				} catch (Exception ex) {
					StardewCraft.LOGGER.error("Failed to load fishing location data {}", entry.getKey(), ex);
				}
			}

			// 确保至少有 Default
			loaded.putIfAbsent("Default", FishingLocationData.defaultFallback());
			INSTANCE = new FishingDataManager(Collections.unmodifiableMap(loaded));

			StardewCraft.LOGGER.info("Loaded fishing locations: {}", INSTANCE.byLocationKey.keySet());
		}
	}
}
