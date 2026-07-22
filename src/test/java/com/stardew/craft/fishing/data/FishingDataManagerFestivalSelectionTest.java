package com.stardew.craft.fishing.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingDataManagerFestivalSelectionTest {
	@Test
	void fallFairAndWinterIceContestUseDifferentLocationPools() {
		List<String> regular = List.of("Forest");

		assertEquals(List.of("fishingGame"),
				FishingDataManager.resolveFishingLookupKeys(true, false, regular));
		assertEquals(List.of("Temp"),
				FishingDataManager.resolveFishingLookupKeys(false, true, regular));
		assertEquals(regular,
				FishingDataManager.resolveFishingLookupKeys(false, false, regular));
	}

	@Test
	void festivalContestCannotBecomeStuckInTutorialCatchMode() {
		assertFalse(FishingDataManager.shouldApplyTutorialCatchGate(true, true, 0));
		assertTrue(FishingDataManager.shouldApplyTutorialCatchGate(false, true, 0));
		assertFalse(FishingDataManager.shouldApplyTutorialCatchGate(false, true, 1));
		assertFalse(FishingDataManager.shouldApplyTutorialCatchGate(false, false, 0));
	}

	@Test
	void bundledIceFishingPoolContainsTheVanillaContestFish() throws Exception {
		Path path = Path.of(System.getProperty("stardewcraft.projectDir"))
				.resolve("src/main/resources/data/stardewcraft/fishing/locations/temp.json");
		JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
		List<String> fish = root.getAsJsonArray("fish").asList().stream()
				.map(element -> element.getAsJsonObject().get("item").getAsString())
				.toList();

		assertEquals("Temp", root.get("location").getAsString());
		assertEquals(List.of(
				"stardewcraft:perch",
				"stardewcraft:lingcod",
				"stardewcraft:midnight_carp",
				"stardewcraft:pike",
				"stardewcraft:red_mullet"), fish);
	}
}
