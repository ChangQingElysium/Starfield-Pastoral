package com.stardew.craft.time;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;

class StardewScheduledTickClockTest {
	@Test
	void scheduledTicksKeepMinecraftsSingleGameTimeDomain() {
		try (var stream = StardewScheduledTickClockTest.class.getResourceAsStream("/stardewcraft.mixins.json")) {
			if (stream == null) throw new AssertionError("Missing stardewcraft.mixins.json");
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			assertFalse(root.getAsJsonArray("mixins").asList().stream()
					.anyMatch(element -> "LevelStardewGameTimeMixin".equals(element.getAsString())),
					"Level.getGameTime must not diverge from LevelAccessor's scheduled-tick clock");
		} catch (Exception exception) {
			throw new AssertionError("Failed reading mixin configuration", exception);
		}
	}
}
