package com.stardew.craft.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineMonsterNamesTest {
    @Test
    void translationKeysUseStableMonsterIds() {
        assertEquals(
                "entity.stardewcraft.mine_monster.green_slime",
                MineMonsterNames.translationKey("green_slime"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MineMonsterNames.translationKey("Green Slime"));
    }

    @Test
    void everyLanguageContainsEveryMineMonsterName() throws Exception {
        var englishResource = Objects.requireNonNull(
                MineMonsterNamesTest.class.getClassLoader()
                        .getResource("assets/stardewcraft/lang/en_us.json"));
        Path languageDirectory = Path.of(englishResource.toURI()).getParent();

        try (var languages = Files.list(languageDirectory)) {
            var files = languages
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
            assertEquals(12, files.size());
            for (Path language : files) {
                JsonObject translations = readJson(language);
                for (String monsterId : MineMonsterNames.ALL_IDS) {
                    String key = MineMonsterNames.translationKey(monsterId);
                    assertTrue(
                            translations.has(key),
                            language.getFileName() + " missing " + key);
                    assertFalse(
                            translations.get(key).getAsString().isBlank(),
                            language.getFileName() + " has blank " + key);
                }
            }
        }
    }

    private static JsonObject readJson(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
