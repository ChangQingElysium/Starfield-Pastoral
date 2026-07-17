package com.stardew.craft.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchWarpCaveResourceTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));

    @Test
    void everyBundledLanguageNamesBothMapOnlyBlocks() throws Exception {
        Path langDir = PROJECT.resolve("src/main/resources/assets/stardewcraft/lang");
        try (var files = Files.list(langDir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject lang = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                assertTrue(lang.has("block.stardewcraft.dark_talisman_seal"), file.toString());
                assertTrue(lang.has("block.stardewcraft.magic_warp_circle"), file.toString());
            }
        }
    }

    @Test
    void southFacingSealAppliesTheAuthoredModelHalfTurn() throws Exception {
        Path blockstate = PROJECT.resolve(
                "src/main/resources/assets/stardewcraft/blockstates/dark_talisman_seal.json");
        JsonObject variants = JsonParser.parseString(Files.readString(blockstate))
                .getAsJsonObject().getAsJsonObject("variants");
        assertEquals(180, variants.getAsJsonObject("facing=south").get("y").getAsInt());
    }
}
