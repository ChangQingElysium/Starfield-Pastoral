package com.stardew.craft.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerSpecialItemResourceTest {
    private static final List<String> ALL_POWER_ITEM_MODELS = List.of(
            "forest_magic",
            "dwarvish_translation_guide",
            "rusty_key",
            "club_card",
            "special_charm",
            "skull_key",
            "magnifying_glass",
            "dark_talisman",
            "magic_ink",
            "bear_knowledge",
            "spring_onion_mastery",
            "key_to_the_town"
    );

    private static final List<String> NEW_POWER_ITEMS = List.of(
            "forest_magic",
            "club_card",
            "dark_talisman",
            "magic_ink",
            "spring_onion_mastery",
            "key_to_the_town"
    );

    @Test
    void everyDisplayedPowerItemModelUsesAnItemTexture() throws Exception {
        ClassLoader loader = PowerSpecialItemResourceTest.class.getClassLoader();
        for (String id : ALL_POWER_ITEM_MODELS) {
            String modelPath = "assets/stardewcraft/models/item/" + id + ".json";
            try (var stream = loader.getResourceAsStream(modelPath)) {
                assertNotNull(stream, "missing model " + modelPath);
                JsonObject model = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
                assertTrue(texture.startsWith("stardewcraft:item/"),
                        id + " still references a non-item texture: " + texture);
                String texturePath = "assets/stardewcraft/textures/"
                        + texture.substring("stardewcraft:".length()) + ".png";
                assertNotNull(loader.getResource(texturePath), "missing texture " + texturePath);
            }
        }
    }

    @Test
    void everyProjectLanguageContainsCompleteNewPowerItemTooltips() throws Exception {
        var englishResource = Objects.requireNonNull(PowerSpecialItemResourceTest.class.getClassLoader()
                .getResource("assets/stardewcraft/lang/en_us.json"));
        Path langDirectory = Path.of(englishResource.toURI()).getParent();
        try (var languages = Files.list(langDirectory)) {
            for (Path language : languages.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject translations = JsonParser.parseString(Files.readString(language)).getAsJsonObject();
                for (String id : NEW_POWER_ITEMS) {
                    for (String suffix : List.of("", ".desc", ".tooltip.flavor", ".tooltip.granted")) {
                        String key = "item.stardewcraft." + id + suffix;
                        assertTrue(translations.has(key), language.getFileName() + " missing " + key);
                        assertFalse(translations.get(key).getAsString().isBlank(),
                                language.getFileName() + " has blank " + key);
                    }
                }
            }
        }
    }
}
