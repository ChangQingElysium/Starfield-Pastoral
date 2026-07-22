package com.stardew.craft.fishing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingVanillaParityTest {
	@Test
	void mineRareFishChanceUsesVanillaFormula() {
		assertEquals(0.046f,
				FishingDataManager.mineRareFishChance(0.02f, 0.01f, 3, 4, false, false),
				0.00001f);
		assertEquals(0.196f,
				FishingDataManager.mineRareFishChance(0.02f, 0.01f, 3, 4, true, true),
				0.00001f);
	}

	@Test
	void defaultPoolContainsOnlyImplementedVanillaInheritedRule() {
		JsonArray rules = location("default").getAsJsonArray("fish");
		assertEquals(1, rules.size());
		assertEquals("secret_note_or_item", rules.get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test
	void beachUsesVanillaDeepMagicFishRandomPool() {
		JsonArray rules = location("beach").getAsJsonArray("fish");
		Set<String> ids = ids(rules);
		assertFalse(ids.contains("lingcod"));
		JsonObject deepPool = find(rules, "beach_deep_magic_fish");
		assertEquals(-10, deepPool.get("precedence").getAsInt());
		assertEquals(0.1f, deepPool.get("chance").getAsFloat(), 0.00001f);
		assertTrue(deepPool.get("requireMagicBait").getAsBoolean());
		assertTrue(deepPool.get("ignoreFishDataRequirements").getAsBoolean());
		assertEquals(Set.of("stardewcraft:midnight_squid", "stardewcraft:spook_fish", "stardewcraft:blobfish"),
				strings(deepPool.getAsJsonArray("randomItems")));
	}

	@Test
	void forestDoesNotLeakWoodsOrMountainFish() {
		Set<String> ids = ids(location("forest").getAsJsonArray("fish"));
		assertFalse(ids.contains("carp"));
		assertFalse(ids.contains("woodskip"));
	}

	@Test
	void submarineSpecialRulesUseVanillaAscendingPrecedence() {
		JsonArray rules = location("submarine").getAsJsonArray("fish");
		assertEquals(-70, find(rules, "blobfish").get("precedence").getAsInt());
		assertEquals(-60, find(rules, "spook_fish").get("precedence").getAsInt());
		assertEquals(-50, find(rules, "midnight_squid").get("precedence").getAsInt());
	}

	private static JsonObject location(String name) {
		String path = "/data/stardewcraft/fishing/locations/" + name + ".json";
		try (var stream = FishingVanillaParityTest.class.getResourceAsStream(path)) {
			if (stream == null) throw new AssertionError("Missing resource " + path);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception exception) {
			throw new AssertionError("Failed reading " + path, exception);
		}
	}

	private static JsonObject find(JsonArray rules, String id) {
		for (var element : rules) {
			JsonObject rule = element.getAsJsonObject();
			if (rule.has("id") && id.equals(rule.get("id").getAsString())) return rule;
		}
		throw new AssertionError("Missing fishing rule " + id);
	}

	private static Set<String> ids(JsonArray rules) {
		Set<String> ids = new HashSet<>();
		for (var element : rules) {
			JsonObject rule = element.getAsJsonObject();
			if (rule.has("id")) ids.add(rule.get("id").getAsString());
		}
		return ids;
	}

	private static Set<String> strings(JsonArray values) {
		Set<String> result = new HashSet<>();
		values.forEach(value -> result.add(value.getAsString()));
		return result;
	}
}
