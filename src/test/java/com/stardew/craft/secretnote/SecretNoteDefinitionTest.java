package com.stardew.craft.secretnote;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.secretnote.StardewSecretNoteDefinition;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNoteDefinitionTest {
    @Test
    void sourceParityChanceRunsFromEightyToTwelvePercent() {
        assertEquals(0.80F, SecretNoteService.generationChance(17, 17), 0.00001F);
        assertEquals(0.12F, SecretNoteService.generationChance(1, 17), 0.00001F);
        assertTrue(SecretNoteService.generationChance(9, 17)
                > SecretNoteService.generationChance(8, 17));
    }

    @Test
    void activeDataOmitsVanillaNineteenAndUsesContinuousDisplayNumbers() throws Exception {
        try (var stream = SecretNoteDefinitionTest.class.getClassLoader()
                .getResourceAsStream("data/stardewcraft/secret_notes/vanilla.json")) {
            assertTrue(stream != null);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            assertEquals(26, root.size());
            Set<Integer> vanillaNumbers = new java.util.HashSet<>();
            Set<Integer> displayNumbers = new java.util.HashSet<>();
            Set<Integer> todoNumbers = new java.util.HashSet<>();
            Map<Integer, Boolean> obtainableByVanillaNumber = new LinkedHashMap<>();
            for (var element : root) {
                var object = element.getAsJsonObject().deepCopy();
                object.remove("id");
                var definition = StardewSecretNoteDefinition.CODEC.parse(JsonOps.INSTANCE, object)
                        .result().orElseThrow();
                vanillaNumbers.add(definition.vanillaNumber());
                displayNumbers.add(definition.displayNumber());
                obtainableByVanillaNumber.put(definition.vanillaNumber(), definition.obtainable());
                if (!definition.obtainable()) {
                    todoNumbers.add(definition.vanillaNumber());
                    assertTrue(definition.implementationStatus().startsWith("TODO 0.5.1-fix"));
                }
            }
            assertFalse(vanillaNumbers.contains(19));
            assertEquals(java.util.stream.IntStream.rangeClosed(1, 26).boxed().collect(Collectors.toSet()),
                    displayNumbers);
            assertEquals(20, root.get(18).getAsJsonObject().get("vanilla_number").getAsInt());
            assertEquals(19, root.get(18).getAsJsonObject().get("display_number").getAsInt());
            assertTrue(obtainableByVanillaNumber.get(20));
            assertTrue(obtainableByVanillaNumber.get(21));
            assertTrue(obtainableByVanillaNumber.get(22));
            assertTrue(obtainableByVanillaNumber.get(23));
            assertEquals(Set.of(24, 26), todoNumbers);
            assertEquals(24, root.size() - todoNumbers.size());
        }
    }

    @Test
    void vanillaGiftRevealRowsRemainSourceComplete() throws Exception {
        try (var stream = SecretNoteDefinitionTest.class.getClassLoader()
                .getResourceAsStream("data/stardewcraft/secret_notes/vanilla.json")) {
            assertTrue(stream != null);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            Map<Integer, Integer> revealCounts = new LinkedHashMap<>();
            for (var element : root) {
                var object = element.getAsJsonObject().deepCopy();
                object.remove("id");
                var definition = StardewSecretNoteDefinition.CODEC.parse(JsonOps.INSTANCE, object)
                        .result().orElseThrow();
                if (!definition.giftReveals().isEmpty()) {
                    revealCounts.put(definition.vanillaNumber(), definition.giftReveals().size());
                }
            }
            assertEquals(Map.of(1, 5, 2, 13, 3, 5, 4, 5, 5, 11, 6, 4, 7, 7, 8, 9, 9, 2),
                    revealCounts);
        }
    }

    @Test
    void everyProjectLanguageContainsEverySecretNoteFeatureKey() throws Exception {
		var englishResource = Objects.requireNonNull(SecretNoteDefinitionTest.class.getClassLoader()
				.getResource("assets/stardewcraft/lang/en_us.json"));
		Path langDirectory = Path.of(englishResource.toURI()).getParent();
        var english = readJson(langDirectory.resolve("en_us.json"));
        Set<String> featureKeys = english.keySet().stream()
                .filter(SecretNoteDefinitionTest::isSecretNoteFeatureKey)
                .collect(Collectors.toSet());
        assertEquals(71, featureKeys.size());

        try (var languages = Files.list(langDirectory)) {
            for (Path language : languages.filter(path -> path.toString().endsWith(".json")).toList()) {
                var translations = readJson(language);
                for (String key : featureKeys) {
                    assertTrue(translations.has(key), language.getFileName() + " missing " + key);
                    assertFalse(translations.get(key).getAsString().isBlank(),
                            language.getFileName() + " has blank " + key);
                }
            }
        }
    }

    private static boolean isSecretNoteFeatureKey(String key) {
        return key.startsWith("item.stardewcraft.magnifying_glass")
                || key.startsWith("item.stardewcraft.special_charm")
                || key.startsWith("item.stardewcraft.secret_note")
                || key.startsWith("item.stardewcraft.ornate_necklace")
                || key.startsWith("stardewcraft.item.magnifying_glass")
                || key.startsWith("stardewcraft.item.special_charm")
                || key.startsWith("stardewcraft.secret_note")
                || key.startsWith("stardewcraft.collections.secret_notes")
                || key.startsWith("stardewcraft.profile.gift")
                || key.startsWith("stardewcraft.quest.29")
                || key.startsWith("stardewcraft.quest.30")
                || key.startsWith("stardewcraft.quest.31")
                || key.startsWith("stardewcraft.quest.128")
                || key.startsWith("stardewcraft.quest.129");
    }

    private static JsonObject readJson(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
