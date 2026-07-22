package com.stardew.craft.fishing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 钓鱼规则数据结构
 * 
 * @param id 规则唯一ID
 * @param precedence 优先级（数字越小越优先）
 * @param itemId 物品ID
 * @param chance 基础出现概率
 * @param difficulty 钓鱼难度 (0-100)
 * @param motionTypeId 鱼的移动模式 (0=mixed, 1=dart, 2=smooth, 3=sinker, 4=floater)
 * @param minFishSize 原版 Data/Fish 最小鱼长
 * @param maxFishSize 原版 Data/Fish 最大鱼长
 * @param minFishingLevel 最低钓鱼等级要求
 * @param minDistanceFromShore 最小离岸距离
 * @param maxDistanceFromShore 最大离岸距离 (-1 表示无限制)
 * @param biomes 群系ID列表 (空表示所有群系)
 * @param biomeTags 群系标签列表 (如 #minecraft:is_ocean)
 * @param seasons 季节列表 (spring, summer, fall, winter，空表示所有季节)
 * @param weather 天气要求 (any, sunny, rainy)
 * @param timeRanges 时间范围列表 (如 [[600,1200],[1800,2400]]，空表示全天)
 * @param skipMinigame 是否跳过钓鱼小游戏（星露谷：非鱼类/可钓物品不会触发小游戏）
 * @param fishAreaId 原版 FishAreaId（如 Ocean/River/Lake）
 * @param canBeInherited 是否允许被继承地点查询使用
 * @param requireMagicBait 是否要求魔法鱼饵
 * @param catchLimit 抓取上限（-1 表示不限制）
 * @param condition 原版风格条件表达式（如 PLAYER_SPECIAL_ORDER_RULE_ACTIVE Current LEGENDARY_FAMILY）
 */
public record SpawnFishRule(
		String id,
		int precedence,
		String itemId,
		float chance,
		int difficulty,
		int motionTypeId,
		int minFishSize,
		int maxFishSize,
		int minFishingLevel,
		int minDistanceFromShore,
		int maxDistanceFromShore,
		List<String> biomes,
		List<String> biomeTags,
		List<String> seasons,
		String weather,
		List<int[]> timeRanges,
		boolean skipMinigame,
		String fishAreaId,
		boolean canBeInherited,
		boolean requireMagicBait,
		int catchLimit,
		String condition,
		// SDV CheckGenericFishRequirements depth-aware fields
		float spawnRate,
		int maxDepth,
		float depthMultiplier,
		boolean applyDailyLuck,
		float curiosityLureBuff,
		boolean isBossFish,
		/** SDV Data/Fish field 13: marks easy fish allowed during the player's tutorial first-catch. */
		boolean isTutorialFish,
		/** SDV SpawnFishData.IgnoreFishDataRequirements: skip Data/Fish time/season/weather/depth roll checks. */
		boolean ignoreFishDataRequirements,
		/** SDV {@code SpawnFishData.ChanceModifiers}: list of QuantityModifier applied to first-roll chance. */
		List<QuantityModifier.Entry> chanceModifiers,
		/** SDV {@code SpawnFishData.ChanceModifierMode}: how multiple modifiers combine (Stack/Min/Max). */
		QuantityModifier.Mode chanceModifierMode,
		/** SDV {@code SpawnFishData.UseFishCaughtSeededRandom}: use seeded RNG keyed on PreciseFishCaught for first roll. */
		boolean useFishCaughtSeededRandom,
		/** SDV SpawnFishData.ChanceBoostPerLuckLevel. */
		float chanceBoostPerLuckLevel,
		/** SDV {@code RandomItemId}: choose one item uniformly after this spawn rule succeeds. */
		List<String> randomItemIds
) {
	/**
	 * 检查基础条件（钓鱼等级、水深）
	 */
	public boolean matchesBasic(int fishingLevel, int waterDepth) {
		if (fishingLevel < minFishingLevel) {
			return false;
		}
		if (waterDepth < minDistanceFromShore) {
			return false;
		}
		return maxDistanceFromShore <= -1 || waterDepth <= maxDistanceFromShore;
	}

	/**
	 * 检查群系是否匹配
	 * @param biomeHolder 当前群系
	 * @return 如果没有群系限制或匹配成功返回true
	 */
	@SuppressWarnings("null")
	public boolean matchesBiome(Holder<Biome> biomeHolder) {
		// 如果没有指定任何群系条件，则匹配所有群系
		if ((biomes == null || biomes.isEmpty()) && (biomeTags == null || biomeTags.isEmpty())) {
			return true;
		}

		// 检查群系ID
		if (biomes != null && !biomes.isEmpty()) {
			ResourceLocation biomeId = biomeHolder.unwrapKey()
					.map(key -> key.location())
					.orElse(null);
			if (biomeId != null) {
				for (String allowed : biomes) {
					if (biomeId.toString().equals(allowed)) {
						return true;
					}
				}
			}
		}

		// 检查群系标签
		if (biomeTags != null && !biomeTags.isEmpty()) {
			for (String tagStr : biomeTags) {
				String tagName = tagStr.startsWith("#") ? tagStr.substring(1) : tagStr;
				try {
					@SuppressWarnings("null")
					ResourceLocation tagLoc = ResourceLocation.parse(tagName);
					@SuppressWarnings("null")
					TagKey<Biome> tag = TagKey.create(Registries.BIOME, tagLoc);
					if (biomeHolder.is(tag)) {
						return true;
					}
				} catch (Exception ignored) {
				}
			}
		}

		return false;
	}

	/**
	 * 检查季节是否匹配
	 * @param currentSeason 当前季节 (spring, summer, fall, winter)
	 */
	public boolean matchesSeason(String currentSeason) {
		if (seasons == null || seasons.isEmpty()) {
			return true;
		}
		return seasons.contains(currentSeason.toLowerCase());
	}

	/**
	 * 检查天气是否匹配
	 * @param isRaining 是否在下雨
	 */
	public boolean matchesWeather(boolean isRaining) {
		if (weather == null || weather.isEmpty() || "any".equalsIgnoreCase(weather)) {
			return true;
		}
		if ("rainy".equalsIgnoreCase(weather)) {
			return isRaining;
		}
		if ("sunny".equalsIgnoreCase(weather)) {
			return !isRaining;
		}
		return true;
	}

	/**
	 * 检查时间是否匹配
	 * @param timeOfDay 当前时间 (0-24000, MC tick时间)
	 * @deprecated MC dayTime 与星露谷虚拟时间不一致（尤其在自定义维度），且 hour 取模 24
	 *             无法表示星露谷的 2400~2600（凌晨 0~2 点）。请改用
	 *             {@link #matchesStardewTime(int)}，由调用方传入星露谷格式时间（HHMM, 600~2600）。
	 */
	@Deprecated
	public boolean matchesTime(long timeOfDay) {
		return matchesStardewTime(convertMcTimeToStardew(timeOfDay));
	}

	/**
	 * 检查星露谷时间是否匹配。
	 * @param stardewTime 星露谷时间格式（HHMM），允许 2400~2600 表示凌晨 0~2 点。
	 */
	public boolean matchesStardewTime(int stardewTime) {
		if (timeRanges == null || timeRanges.isEmpty()) {
			return true;
		}
		for (int[] range : timeRanges) {
			if (range.length >= 2 && stardewTime >= range[0] && stardewTime < range[1]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 将MC时间转换为星露谷时间格式
	 * MC: 0=6:00, 6000=12:00, 12000=18:00, 18000=0:00
	 * SV: 600=6:00, 1200=12:00, 1800=18:00, 2400=0:00
	 */
	private static int convertMcTimeToStardew(long mcTime) {
		// MC tick to hour (24000 ticks = 24 hours, offset by 6)
		int hour = (int) ((mcTime / 1000 + 6) % 24);
		int minute = (int) ((mcTime % 1000) * 60 / 1000);
		return hour * 100 + minute;
	}

	public static SpawnFishRule fromJson(JsonObject obj) {
		String id = obj.has("id") ? obj.get("id").getAsString() : "unknown";
		int precedence = obj.has("precedence") ? obj.get("precedence").getAsInt() : 100;
		String item = obj.get("item").getAsString();
		float chance = obj.has("chance") ? obj.get("chance").getAsFloat() : 1.0f;
		int difficulty = obj.has("difficulty") ? obj.get("difficulty").getAsInt() : 20;
		int motion = obj.has("motionType") ? obj.get("motionType").getAsInt() : 0;
		int minFishSize = obj.has("minFishSize") ? obj.get("minFishSize").getAsInt() : 0;
		int maxFishSize = obj.has("maxFishSize") ? obj.get("maxFishSize").getAsInt() : 0;
		int minLevel = obj.has("minFishingLevel") ? obj.get("minFishingLevel").getAsInt() : 0;
		int minDepth = obj.has("minDistanceFromShore") ? obj.get("minDistanceFromShore").getAsInt() : 0;
		int maxDepth = obj.has("maxDistanceFromShore") ? obj.get("maxDistanceFromShore").getAsInt() : -1;
		boolean skipMinigame = obj.has("skipMinigame") && obj.get("skipMinigame").getAsBoolean();
		String fishAreaId = obj.has("fishAreaId") && !obj.get("fishAreaId").isJsonNull()
				? obj.get("fishAreaId").getAsString()
				: null;
		boolean canBeInherited = !obj.has("canBeInherited") || obj.get("canBeInherited").getAsBoolean();
		boolean requireMagicBait = obj.has("requireMagicBait") && obj.get("requireMagicBait").getAsBoolean();
		int catchLimit = obj.has("catchLimit") ? obj.get("catchLimit").getAsInt() : -1;
		String condition = obj.has("condition") && !obj.get("condition").isJsonNull()
				? obj.get("condition").getAsString()
				: null;
		if (catchLimit < 0 && obj.has("legendary") && obj.get("legendary").getAsBoolean()) {
			catchLimit = 1;
		}

		// 解析群系列表
		List<String> biomes = parseStringList(obj, "biomes");
		List<String> biomeTags = parseStringList(obj, "biomeTags");
		List<String> seasons = parseStringList(obj, "seasons");
		String weather = obj.has("weather") ? obj.get("weather").getAsString() : "any";
		List<int[]> timeRanges = parseTimeRanges(obj);

		// SDV depth-aware fields (CheckGenericFishRequirements)
		float spawnRate = obj.has("spawnRate") ? obj.get("spawnRate").getAsFloat() : 1.0f;
		int maxFishDepth = obj.has("maxDepth") ? obj.get("maxDepth").getAsInt() : 4;
		float depthMultiplier = obj.has("depthMultiplier") ? obj.get("depthMultiplier").getAsFloat() : 0.0f;
		boolean applyDailyLuck = obj.has("applyDailyLuck") && obj.get("applyDailyLuck").getAsBoolean();
		float curiosityLureBuff = obj.has("curiosityLureBuff") ? obj.get("curiosityLureBuff").getAsFloat() : -1.0f;
		boolean isBossFish = obj.has("isBossFish") && obj.get("isBossFish").getAsBoolean();
		boolean isTutorialFish = obj.has("isTutorialFish") && obj.get("isTutorialFish").getAsBoolean();
		boolean ignoreFishDataRequirements = obj.has("ignoreFishDataRequirements")
				&& obj.get("ignoreFishDataRequirements").getAsBoolean();
		List<QuantityModifier.Entry> chanceModifiers = QuantityModifier.parseList(obj, "chanceModifiers");
		QuantityModifier.Mode chanceModifierMode = QuantityModifier.parseMode(obj, "chanceModifierMode");
		boolean useFishCaughtSeededRandom = obj.has("useFishCaughtSeededRandom") && obj.get("useFishCaughtSeededRandom").getAsBoolean();
		float chanceBoostPerLuckLevel = obj.has("chanceBoostPerLuckLevel")
				? obj.get("chanceBoostPerLuckLevel").getAsFloat() : 0f;
		List<String> randomItemIds = parseStringList(obj, "randomItems");

		return new SpawnFishRule(id, precedence, item, chance, difficulty, motion, minFishSize, maxFishSize, minLevel, minDepth, maxDepth,
				biomes, biomeTags, seasons, weather, timeRanges, skipMinigame,
				fishAreaId, canBeInherited, requireMagicBait, catchLimit, condition,
				spawnRate, maxFishDepth, depthMultiplier, applyDailyLuck, curiosityLureBuff, isBossFish,
				isTutorialFish, ignoreFishDataRequirements, chanceModifiers, chanceModifierMode,
				useFishCaughtSeededRandom, chanceBoostPerLuckLevel, randomItemIds);
	}

	private static List<String> parseStringList(JsonObject obj, String key) {
		if (!obj.has(key)) {
			return Collections.emptyList();
		}
		JsonArray arr = obj.getAsJsonArray(key);
		List<String> result = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			result.add(el.getAsString());
		}
		return result;
	}

	private static List<int[]> parseTimeRanges(JsonObject obj) {
		if (!obj.has("timeRanges")) {
			return Collections.emptyList();
		}
		JsonArray arr = obj.getAsJsonArray("timeRanges");
		List<int[]> result = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			JsonArray range = el.getAsJsonArray();
			if (range.size() >= 2) {
				result.add(new int[]{range.get(0).getAsInt(), range.get(1).getAsInt()});
			}
		}
		return result;
	}
}
