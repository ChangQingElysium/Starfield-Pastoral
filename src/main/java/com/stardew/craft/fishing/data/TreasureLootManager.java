package com.stardew.craft.fishing.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data-driven fishing treasure generator.
 *
 * Config path: data/stardewcraft/fishing/fishing_treasure.json
 */
@SuppressWarnings("null")
public class TreasureLootManager extends SimplePreparableReloadListener<TreasureLootManager.TreasureData> {
	private static final Logger LOGGER = LoggerFactory.getLogger(TreasureLootManager.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private TreasureData data = new TreasureData();

	public static class TreasureLootEntry {
		public String item;
		public int minCount = 1;
		public int maxCount = 1;
		public int weight = 100;
		public int minFishingLevel = 0;
	}

	public static class TreasureData {
		public List<TreasureLootEntry> commonLoot = new ArrayList<>();
		public List<TreasureLootEntry> rareLoot = new ArrayList<>();
		public List<TreasureLootEntry> goldenLoot = new ArrayList<>();
		public List<TreasureLootEntry> fallbackLoot = new ArrayList<>();

		public double rollChanceStart = 1.0;
		public double rollChanceDecayNormal = 0.4;
		public double rollChanceDecayGolden = 0.6;
		public double rareChance = 0.15;
		public double goldenPoolChance = 0.5;
	}

	@Override
	protected TreasureData prepare(@Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
		TreasureData loaded = loadFromResourceManager(resourceManager);
		return loaded != null ? loaded : createDefaultData();
	}

	@Override
	protected void apply(@Nonnull TreasureData prepared, @Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
		this.data = prepared;
		LOGGER.info("Fishing treasure table applied: common={}, rare={}, golden={}, fallback={}",
				data.commonLoot.size(), data.rareLoot.size(), data.goldenLoot.size(), data.fallbackLoot.size());
	}

	public void initializeWithDefaults() {
		this.data = createDefaultData();
	}

	public void loadFromBundledData() {
		try (InputStream in = TreasureLootManager.class.getResourceAsStream("/data/stardewcraft/fishing/fishing_treasure.json")) {
			if (in == null) {
				LOGGER.warn("Missing bundled fishing treasure data, using defaults");
				this.data = createDefaultData();
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				TreasureData loaded = GSON.fromJson(json, TreasureData.class);
				this.data = sanitize(loaded);
				LOGGER.info("Loaded bundled fishing treasure data");
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load bundled fishing treasure data, using defaults", e);
			this.data = createDefaultData();
		}
	}

	private TreasureData loadFromResourceManager(ResourceManager resourceManager) {
		try {
			Map<ResourceLocation, Resource> resources = resourceManager.listResources(
					"fishing",
					loc -> loc.getPath().endsWith("fishing_treasure.json")
			);
			for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
				ResourceLocation id = entry.getKey();
				if (!"stardewcraft".equals(id.getNamespace())) {
					continue;
				}
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8))) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					LOGGER.info("Loaded fishing treasure table from {}", id);
					return sanitize(GSON.fromJson(json, TreasureData.class));
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load treasure table from resource manager", e);
		}
		return null;
	}

	private TreasureData sanitize(TreasureData in) {
		if (in == null) {
			return createDefaultData();
		}
		if (in.commonLoot == null) in.commonLoot = new ArrayList<>();
		if (in.rareLoot == null) in.rareLoot = new ArrayList<>();
		if (in.goldenLoot == null) in.goldenLoot = new ArrayList<>();
		if (in.fallbackLoot == null) in.fallbackLoot = new ArrayList<>();

		if (in.rollChanceStart <= 0) in.rollChanceStart = 1.0;
		if (in.rollChanceDecayNormal <= 0 || in.rollChanceDecayNormal >= 1.0) in.rollChanceDecayNormal = 0.4;
		if (in.rollChanceDecayGolden <= 0 || in.rollChanceDecayGolden >= 1.0) in.rollChanceDecayGolden = 0.6;
		in.rareChance = Mth.clamp((float) in.rareChance, 0f, 1f);
		in.goldenPoolChance = Mth.clamp((float) in.goldenPoolChance, 0f, 1f);
		return in;
	}

	public List<ItemStack> generateTreasure(int fishingLevel, boolean golden, RandomSource random, int clearWaterDistance, double dailyLuck) {
		return generateTreasure(fishingLevel, golden, random, clearWaterDistance, dailyLuck, null);
	}

	/**
	 * SDV 原版宝箱战利品生成 — 完全按照 FishingRod.openTreasureMenuEndFunction 实现。
 * 跳过 mod 中不存在的物品（skill_book, raccoon_seeds, mineral_water,
 * artifact_trove, book_of_roe, golden_bobber, qi_bean, golden_walnut, treasure_appraisal_guide）。
	 */
	public List<ItemStack> generateTreasure(int fishingLevel, boolean golden, RandomSource random,
			int clearWaterDistance, double dailyLuck, @Nullable ServerPlayer player) {
		List<ItemStack> treasures = new ArrayList<>();
		// SDV clearWaterDistance 上限5
		int dist = Math.min(5, Math.max(0, clearWaterDistance));
		boolean foundLostBook = false;

		float chance = 1f;
		while (random.nextDouble() <= (double) chance) {
			chance *= (golden ? 0.6f : 0.4f);

			// ---- Pre-roll conditional items (SDV: before the main switch) ----

			// SDV: Spring + not beach + 10% → Rice Shoot (2-5 or 7-10)
			if (player != null) {
				String season = getCurrentSeason(player);
				if ("spring".equals(season) && random.nextDouble() < 0.1) {
					int count = random.nextInt(2, 6) + (random.nextDouble() < 0.25 ? 5 : 0);
					addItem(treasures, "stardewcraft:rice_shoot", count);
				}
			}

			// SDV: Mystery Box (8% + avgDailyLuck/5)
			double mysteryChance = BookPowerEffects.applyMysteryBoxChance(
					player == null ? null : PlayerDataManager.getPlayerData(player), 0.08 + dailyLuck / 5.0);
			if (random.nextDouble() < mysteryChance) {
				boolean hasForagingMastery = hasMastery(player, SkillType.FORAGING);
				addItem(treasures, hasForagingMastery ? "stardewcraft:golden_mystery_box" : "stardewcraft:mystery_box", 1);
			}

			// SDV: Golden Animal Cracker (Farming mastery + 5%)
			if (hasMastery(player, SkillType.FARMING) && random.nextDouble() < 0.05) {
				addItem(treasures, "stardewcraft:golden_animal_cracker", 1);
			}

			// ---- Main loot selection ----
			if (golden && random.nextDouble() < 0.5) {
				// ---- SDV Golden Treasure Pool (case 0-12, 跳过不存在的) ----
				rollGoldenPool(treasures, random);
				continue; // SDV: golden pool 之后 continue 跳过普通 case
			}

			// ---- SDV Normal Case 0-3 ----
			switch (random.nextInt(4)) {
				case 0 -> rollOres(treasures, dist, fishingLevel, random, player);
				case 1 -> rollBaitAndTackle(treasures, dist, fishingLevel, random);
				case 2 -> foundLostBook |= rollArtifactsAndGeodes(treasures, fishingLevel, random, player);
				case 3 -> rollGemsAndEquipment(treasures, dist, fishingLevel, dailyLuck, random, player);
			}
		}

		// SDV: 空宝箱 → 给鱼饵
		if (treasures.isEmpty() && !foundLostBook) {
			addItem(treasures, "stardewcraft:bait", random.nextInt(1, 4) * 5);
		}
		if (com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.shouldForceGoldenBobberTreasure(player)) {
			treasures.clear();
			addItem(treasures, "stardewcraft:golden_bobber", 1);
		}

		return treasures;
	}

	/**
	 * SDV golden treasure: random.Next(13), 跳过不存在的物品重新掷骰
	 */
	private void rollGoldenPool(List<ItemStack> treasures, RandomSource random) {
		// SDV 原版13项，我们有10项，映射到0-9
		// 0=gold_bar, 1=fairy_dust, 2=dressed_spinner, 3=challenge_bait,
		// 4=magnet, 5=stardrop_tea, 6=pearl, 7=deluxe_worm_bin, 8=fish_smoker, 9=sonar_bobber
		switch (random.nextInt(10)) {
			case 0 -> addItem(treasures, "stardewcraft:gold_bar", random.nextInt(1, 6));
			case 1 -> addItem(treasures, "stardewcraft:fairy_dust", random.nextInt(3, 6));
			case 2 -> addItem(treasures, "stardewcraft:dressed_spinner", 1);
			case 3 -> addItem(treasures, "stardewcraft:challenge_bait", random.nextInt(3, 6));
			case 4 -> addItem(treasures, "stardewcraft:magnet", random.nextInt(3, 6));
			case 5 -> addItem(treasures, "stardewcraft:stardrop_tea", 1);
			case 6 -> addItem(treasures, "stardewcraft:pearl", 1);
			case 7 -> addItem(treasures, "stardewcraft:deluxe_worm_bin", 1);
			case 8 -> addItem(treasures, "stardewcraft:fish_smoker", 1);
			case 9 -> addItem(treasures, "stardewcraft:sonar_bobber", 1);
		}
	}

	/**
	 * SDV case 0: 矿石，按离岸距离分级
	 */
	private void rollOres(List<ItemStack> treasures, int dist, int fishingLevel,
			RandomSource random, @Nullable ServerPlayer player) {
		// SDV: dist>=5 → 3% 铱矿(1-2)
		if (dist >= 5 && random.nextDouble() < 0.03) {
			addItem(treasures, "stardewcraft:iridium_ore", random.nextInt(1, 3));
			return;
		}
		// SDV: 构建可选矿石列表
		List<String> possibles = new ArrayList<>();
		if (dist >= 4) {
			possibles.add("stardewcraft:gold_ore");
		}
		if (dist >= 3 && (possibles.isEmpty() || random.nextDouble() < 0.6)) {
			possibles.add("stardewcraft:iron_ore");
		}
		if (possibles.isEmpty() || random.nextDouble() < 0.6) {
			possibles.add("stardewcraft:copper_ore");
		}
		// SDV: 388=Wood, 我们没有wood物品 → 跳过
		if (possibles.isEmpty() || random.nextDouble() < 0.6) {
			possibles.add("stardewcraft:stone");
		}
		possibles.add("stardewcraft:coal");

		String chosen = possibles.get(random.nextInt(possibles.size()));
		int count = random.nextInt(2, 7);
		int luckLevel = player != null ? Math.max(0, com.stardew.craft.player.PlayerStardewDataAPI.getLuckBuffLevel(player)) : 0;
		if (random.nextDouble() < 0.05 + luckLevel * 0.015) {
			count *= 2;
		}
		if (random.nextDouble() < 0.05 + luckLevel * 0.03) {
			count *= 2;
		}
		addItem(treasures, chosen, count);
	}

	/**
	 * SDV case 1: 鱼饵和渔具
	 */
	private void rollBaitAndTackle(List<ItemStack> treasures, int dist, int fishingLevel, RandomSource random) {
		if (dist >= 4 && random.nextDouble() < 0.1 && fishingLevel >= 6) {
			// SDV: Dressed Spinner
			addItem(treasures, "stardewcraft:dressed_spinner", 1);
		} else if (random.nextDouble() < 0.25 && fishingLevel >= 4) {
			// SDV: Wild Bait (原版检查craftingRecipes, 我们用fishingLevel>=4近似)
			int count = 5 + (random.nextDouble() < 0.25 ? 5 : 0);
			addItem(treasures, "stardewcraft:wild_bait", count);
		} else if (random.nextDouble() < 0.11 && fishingLevel >= 6) {
			// SDV: Sonar Bobber
			addItem(treasures, "stardewcraft:sonar_bobber", 1);
		} else if (fishingLevel >= 6) {
			// SDV: Deluxe Bait
			addItem(treasures, "stardewcraft:deluxe_bait", 5);
		} else {
			// SDV: 普通鱼饵
			addItem(treasures, "stardewcraft:bait", 10);
		}
	}

	/**
	 * SDV case 2: 文物和晶洞
	 * Lost Book enters the treasure container as object 102, then converts to
	 * shared museum progress when the player receives it.
	 */
	private boolean rollArtifactsAndGeodes(List<ItemStack> treasures, int fishingLevel, RandomSource random,
			@Nullable ServerPlayer player) {
		com.stardew.craft.player.PlayerStardewData playerData = player == null
				? null : PlayerDataManager.getPlayerData(player);
		if (player != null
				&& random.nextDouble() < 0.1
				&& (playerData.hasMailFlag(com.stardew.craft.museum.LostBookService.FIRST_BOOK_FLAG)
						|| playerData.hasMailFlagForTomorrow(
								com.stardew.craft.museum.LostBookService.FIRST_BOOK_FLAG))
				&& com.stardew.craft.museum.LostBookService.canFindAnother(player)) {
			treasures.add(new ItemStack(com.stardew.craft.item.ModItems.LOST_BOOK.get()));
			return true;
		}
		if (random.nextDouble() < 0.5 && fishingLevel > 1) {
			// SDV: 随机考古文物 (103-119) — 从我们已有的考古文物中选择
			String[] artifacts = {
				"stardewcraft:ancient_doll", "stardewcraft:elvish_jewelry",
				"stardewcraft:chewing_stick", "stardewcraft:ornamental_fan",
				"stardewcraft:ancient_sword", "stardewcraft:rusty_spoon",
				"stardewcraft:rusty_spur", "stardewcraft:rusty_cog",
				"stardewcraft:chicken_statue", "stardewcraft:ancient_seed",
				"stardewcraft:prehistoric_tool", "stardewcraft:dried_starfish",
				"stardewcraft:anchor", "stardewcraft:glass_shards",
				"stardewcraft:bone_flute", "stardewcraft:prehistoric_handaxe"
			};
			addItem(treasures, artifacts[random.nextInt(artifacts.length)], 1);
		} else {
			// SDV: 晶洞
			addItem(treasures, "stardewcraft:geode", random.nextInt(1, 3));
		}
		return false;
	}

	/**
	 * SDV case 3: 按子判定分为 宝石晶洞(0) / 宝石(1) / 装备(2)
	 */
	private void rollGemsAndEquipment(List<ItemStack> treasures, int dist, int fishingLevel,
			double dailyLuck, RandomSource random, @Nullable ServerPlayer player) {
		switch (random.nextInt(3)) {
			case 0 -> {
				// SDV case 3, sub 0: 晶洞（按距离分级）
				if (dist >= 4) {
					// SDV: 537 + random(-2,0) = Magma Geode / Frozen Geode / Geode
					String[] geodes = {"stardewcraft:magma_geode", "stardewcraft:frozen_geode", "stardewcraft:geode"};
					addItem(treasures, random.nextDouble() < 0.4 ? geodes[random.nextInt(1, 3)] : geodes[0],
							random.nextInt(1, 4));
				} else if (dist >= 3) {
					// SDV: 536 + random(0,-1) = Frozen Geode / Geode
					addItem(treasures, random.nextDouble() < 0.4 ? "stardewcraft:geode" : "stardewcraft:frozen_geode",
							random.nextInt(1, 4));
				} else {
					addItem(treasures, "stardewcraft:geode", random.nextInt(1, 4));
				}
				// SDV: 5% double
				if (!treasures.isEmpty() && random.nextDouble() < 0.05) {
					treasures.get(treasures.size() - 1).grow(treasures.get(treasures.size() - 1).getCount());
				}
			}
			case 1 -> {
				// SDV case 3, sub 1: 宝石（按距离分级）
				if (fishingLevel < 2) {
					addItem(treasures, "stardewcraft:coal", random.nextInt(1, 4));
				} else if (dist >= 4) {
					// SDV: 30% Fire Quartz, else Ruby/Emerald
					String gem = random.nextDouble() < 0.3 ? "stardewcraft:fire_quartz" :
							(random.nextBoolean() ? "stardewcraft:ruby" : "stardewcraft:emerald");
					addItem(treasures, gem, random.nextInt(1, 3));
				} else if (dist >= 3) {
					String gem = random.nextDouble() < 0.3 ? "stardewcraft:frozen_tear" :
							(random.nextBoolean() ? "stardewcraft:jade" : "stardewcraft:aquamarine");
					addItem(treasures, gem, random.nextInt(1, 3));
				} else {
					String gem = random.nextDouble() < 0.3 ? "stardewcraft:earth_crystal" :
							(random.nextBoolean() ? "stardewcraft:amethyst" : "stardewcraft:topaz");
					addItem(treasures, gem, random.nextInt(1, 3));
				}
				// SDV: 2.8% × dist/5 → Diamond
				if (random.nextDouble() < 0.028 * dist / 5.0) {
					addItem(treasures, "stardewcraft:diamond", 1);
				}
				// SDV: 5% double
				if (!treasures.isEmpty() && random.nextDouble() < 0.05) {
					treasures.get(treasures.size() - 1).grow(treasures.get(treasures.size() - 1).getCount());
				}
			}
			case 2 -> {
				// SDV case 3, sub 2: 装备（武器/戒指/靴子/特殊物品）
				if (fishingLevel < 2) {
					addItem(treasures, "stardewcraft:mixed_seeds", random.nextInt(1, 4));
				} else {
					rollSpecialEquipment(treasures, fishingLevel, random, dist, dailyLuck, player);
				}
			}
		}
	}

	/**
	 * SDV case 3 sub-case 2: 装备掷骰。
	 * 每项独立概率检查，与SDV FishingRod.cs L2734-2820 完全对应。
	 */
	@SuppressWarnings("null")
	private void rollSpecialEquipment(List<ItemStack> treasures, int fishingLevel,
			RandomSource random, int dist, double dailyLuck, @Nullable ServerPlayer player) {
		float luckModifier = (1f + (float) dailyLuck) * ((float) dist / 5f);
		PlayerStardewData playerData = player != null ? PlayerDataManager.getPlayerData(player) : null;

		// SDV: Neptune's Glaive — 5% × luckModifier, once per save
		if (random.nextFloat() < 0.05f * luckModifier) {
			if (playerData == null || !playerData.hasSpecialItem("stardewcraft:neptunes_glaive")) {
				addItem(treasures, "stardewcraft:neptunes_glaive", 1);
				if (playerData != null) playerData.addSpecialItem("stardewcraft:neptunes_glaive");
			}
		}

		// SDV: Broken Trident — 5% × luckModifier, once per save
		if (random.nextFloat() < 0.05f * luckModifier) {
			if (playerData == null || !playerData.hasSpecialItem("stardewcraft:broken_trident")) {
				addItem(treasures, "stardewcraft:broken_trident", 1);
				if (playerData != null) playerData.addSpecialItem("stardewcraft:broken_trident");
			}
		}

		// SDV: Rings — 7% × luckModifier
		if (random.nextFloat() < 0.07f * luckModifier) {
			int luckLevel = player != null ? Math.max(0, com.stardew.craft.player.PlayerStardewDataAPI.getLuckBuffLevel(player)) : 0;
			switch (random.nextInt(3)) {
				case 0 -> {
					// SDV: 516/517 (Small Glow Ring / Glow Ring) 按幸运分
					String ring = random.nextDouble() < (double) luckLevel / 11.0 ?
							"stardewcraft:glow_ring" : "stardewcraft:small_glow_ring";
					addItem(treasures, ring, 1);
				}
				case 1 -> {
					String ring = random.nextDouble() < (double) luckLevel / 11.0 ?
							"stardewcraft:magnet_ring" : "stardewcraft:small_magnet_ring";
					addItem(treasures, ring, 1);
				}
				case 2 -> {
					String[] gemRings = {
						"stardewcraft:amethyst_ring", "stardewcraft:topaz_ring",
						"stardewcraft:aquamarine_ring", "stardewcraft:jade_ring",
						"stardewcraft:emerald_ring", "stardewcraft:ruby_ring"
					};
					addItem(treasures, gemRings[random.nextInt(gemRings.length)], 1);
				}
			}
		}

		// SDV: Prismatic Shard — 0.1% × luckModifier, fishing > 5
		if (fishingLevel > 5 && random.nextFloat() < 0.001f * luckModifier) {
			addItem(treasures, "stardewcraft:prismatic_shard", 1);
		}

		// SDV: Strange Doll (green) — 1% × luckModifier  (SDV object 127)
		if (random.nextFloat() < 0.01f * luckModifier) {
			addItem(treasures, "stardewcraft:strange_doll_green", 1);
		}

		// SDV: Strange Doll (yellow) — 1% × luckModifier  (SDV object 126)
		if (random.nextFloat() < 0.01f * luckModifier) {
			addItem(treasures, "stardewcraft:strange_doll_yellow", 1);
		}

		// SDV: Iridium Band — 1% × luckModifier (SDV ring 527)
		if (random.nextFloat() < 0.01f * luckModifier) {
			addItem(treasures, "stardewcraft:iridium_band", 1);
		}

		// SDV: Boots — 1% × luckModifier (SDV boots 504-513)
		if (random.nextFloat() < 0.01f * luckModifier) {
			String[] boots = {
				"stardewcraft:sneakers", "stardewcraft:rubber_boots",
				"stardewcraft:leather_boots", "stardewcraft:work_boots",
				"stardewcraft:combat_boots", "stardewcraft:tundra_boots",
				"stardewcraft:thermal_boots", "stardewcraft:dark_boots",
				"stardewcraft:firewalker_boots", "stardewcraft:space_boots"
			};
			addItem(treasures, boots[random.nextInt(boots.length)], 1);
		}

		// SDV: 如果 sub-case 2 什么都没给 → Diamond
		if (treasures.isEmpty()) {
			addItem(treasures, "stardewcraft:diamond", 1);
		}
	}

	/**
	 * 从玩家所在维度获取当前季节名称。
	 * 注：必须使用 {@link com.stardew.craft.time.StardewTimeManager} 而不是
	 * {@code level.getDayTime()}，因为星露谷使用独立于主世界的共享时钟，
	 * 直接除以 24000 会得到主世界的天数（错误的季节）。
	 */
	private String getCurrentSeason(ServerPlayer player) {
		com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
		int seasonIndex = tm != null ? tm.getCurrentSeason() : 0;
		return switch (seasonIndex) {
			case 0 -> "spring";
			case 1 -> "summer";
			case 2 -> "fall";
			case 3 -> "winter";
			default -> "spring";
		};
	}

	private boolean hasMastery(@Nullable ServerPlayer player, SkillType skill) {
		if (player == null) return false;
		PlayerStardewData data = PlayerDataManager.getPlayerData(player);
		return data != null && data.hasMastery(skill);
	}

	private void addItem(List<ItemStack> treasures, String itemId, int count) {
		ResourceLocation rl = ResourceLocation.tryParse(itemId);
		if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
			treasures.add(new ItemStack(BuiltInRegistries.ITEM.get(rl), count));
		}
	}

	public List<ItemStack> generateTreasure(int fishingLevel, boolean golden, RandomSource random) {
		return generateTreasure(fishingLevel, golden, random, 50, 0.0);
	}

	private TreasureData createDefaultData() {
		TreasureData d = new TreasureData();

		d.commonLoot.add(createEntry("stardewcraft:copper_ore", 2, 7, 120));
		d.commonLoot.add(createEntry("stardewcraft:iron_ore", 2, 6, 100));
		d.commonLoot.add(createEntry("stardewcraft:coal", 3, 8, 140));
		d.commonLoot.add(createEntry("stardewcraft:stone", 5, 15, 80));
		d.commonLoot.add(createEntry("stardewcraft:quartz", 1, 4, 60));

		d.rareLoot.add(createEntry("stardewcraft:gold_ore", 1, 3, 90));
		d.rareLoot.add(createEntry("stardewcraft:diamond", 1, 2, 50));
		d.rareLoot.add(createEntry("stardewcraft:omni_geode", 1, 1, 45));
		d.rareLoot.add(createEntry("stardewcraft:sonar_bobber", 1, 1, 25, 6));
		d.rareLoot.add(createEntry("stardewcraft:deluxe_bait", 5, 5, 35, 6));
		d.rareLoot.add(createEntry("stardewcraft:fish_stew", 1, 1, 16, 2));
		d.rareLoot.add(createEntry("stardewcraft:shrimp_cocktail", 1, 1, 14, 2));

		d.goldenLoot.add(createEntry("stardewcraft:diamond", 2, 5, 100));
		d.goldenLoot.add(createEntry("stardewcraft:prismatic_shard", 1, 2, 35));
		d.goldenLoot.add(createEntry("stardewcraft:fairy_dust", 3, 6, 40));
		d.goldenLoot.add(createEntry("stardewcraft:challenge_bait", 3, 6, 45));
		d.goldenLoot.add(createEntry("stardewcraft:magnet", 3, 6, 30));
		d.goldenLoot.add(createEntry("stardewcraft:fish_stew", 1, 2, 18, 2));
		d.goldenLoot.add(createEntry("stardewcraft:shrimp_cocktail", 1, 2, 16, 2));

		d.fallbackLoot.add(createEntry("stardewcraft:bait", 5, 15, 100));
		return d;
	}

	private TreasureLootEntry createEntry(String item, int minCount, int maxCount, int weight) {
		return createEntry(item, minCount, maxCount, weight, 0);
	}

	private TreasureLootEntry createEntry(String item, int minCount, int maxCount, int weight, int minLevel) {
		TreasureLootEntry e = new TreasureLootEntry();
		e.item = item;
		e.minCount = minCount;
		e.maxCount = maxCount;
		e.weight = weight;
		e.minFishingLevel = minLevel;
		return e;
	}
}
