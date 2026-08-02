package com.stardew.craft.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    void everyRegisteredCustomMonsterHasEveryLanguageName() throws Exception {
        Path projectRoot = Path.of(System.getProperty(
                "stardewcraft.projectDir", "."));
        String entities = Files.readString(projectRoot.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "entity", "ModEntities.java"
        )));
        Set<String> monsterIds = registeredMonsterIds(entities);
        assertFalse(monsterIds.isEmpty(), "No custom monsters discovered");

        var englishResource = Objects.requireNonNull(
                MineMonsterNamesTest.class.getClassLoader()
                        .getResource("assets/stardewcraft/lang/en_us.json"));
        Path languageDirectory = Path.of(englishResource.toURI()).getParent();
        try (var languages = Files.list(languageDirectory)) {
            for (Path language : languages
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                JsonObject translations = readJson(language);
                for (String monsterId : monsterIds) {
                    String key = "entity.stardewcraft." + monsterId;
                    assertTrue(translations.has(key),
                            language.getFileName() + " missing " + key);
                    assertFalse(translations.get(key).getAsString().isBlank(),
                            language.getFileName() + " has blank " + key);
                }
            }
        }
    }

    @Test
    void everyBuiltInSpawnNameUsesTheCanonicalLocalizedIdentity()
            throws Exception {
        Path projectRoot = Path.of(System.getProperty(
                "stardewcraft.projectDir", "."));
        String spawnHandler = Files.readString(projectRoot.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "event", "MineMonsterSpawnHandler.java"
        )));
        var matcher = Pattern.compile(
                "setSDVName\\(mob,\\s*\"([^\"]+)\"\\)"
        ).matcher(spawnHandler);
        Set<String> namedIds = new TreeSet<>();
        while (matcher.find()) {
            namedIds.add(matcher.group(1));
        }
        assertEquals(MineMonsterNames.ALL_IDS, namedIds);
    }

    @Test
    void summonSuccessNeverFallsBackToRawMonsterId() throws Exception {
        Path projectRoot = Path.of(System.getProperty(
                "stardewcraft.projectDir", "."));
        String command = Files.readString(projectRoot.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "command", "MonsterSummonCommand.java"
        )));
        assertFalse(command.contains("Component.literal(monsterId)"));
        assertTrue(command.contains("resultMob.getDisplayName()"));
        assertTrue(command.contains("MineMonsterNames.displayName(monsterId)"));
    }

    @Test
    void legacyEnglishLiteralMigratesOnlyForItsExactMonsterIdentity() {
        Component migrated = MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("Frost Bat"),
                Set.of("sd_mob_bat", "sd_tier_2")
        ).orElseThrow();

        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class,
                migrated.getContents()
        );
        assertEquals(
                "entity.stardewcraft.mine_monster.frost_bat",
                contents.getKey()
        );
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("Frost Bat"),
                Set.of("sd_mob_slime", "sd_tier_2")
        ).isEmpty());
        assertEquals(
                "entity.stardewcraft.mine_monster.dust_sprite",
                migratedKey("Dust Spirit", Set.of("sd_mob_dust_sprite"))
        );
        assertEquals(
                "entity.stardewcraft.mine_monster.fly",
                migratedKey("Fly", Set.of("sd_mob_fly"))
        );
    }

    @Test
    void legacyMigrationPreservesPlayerAndAlreadyLocalizedNames() {
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("My Frost Bat"),
                Set.of("sd_mob_bat", "sd_tier_2")
        ).isEmpty());
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("Frost Bat").withStyle(ChatFormatting.GOLD),
                Set.of("sd_mob_bat", "sd_tier_2")
        ).isEmpty());
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.translatable(
                        "entity.stardewcraft.mine_monster.frost_bat"),
                Set.of("sd_mob_bat", "sd_tier_2")
        ).isEmpty());
    }

    @Test
    void legacyBaseNamesDoNotOverwriteVariantCustomNames() {
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("Bat"),
                Set.of("sd_mob_bat", "sd_tier_2")
        ).isEmpty());
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("Grub"),
                Set.of("sd_mob_grub", "sd_mob_mutant_grub")
        ).isEmpty());
        assertTrue(MineMonsterNames.migrateLegacyDisplayName(
                Component.literal("Rock Crab"),
                Set.of("sd_mob_crab", "sd_truffle_crab")
        ).isEmpty());
    }

    @Test
    void legacyMigrationCoversEveryCanonicalMonsterAndRunsBeforeDimensionExit()
            throws Exception {
        Path projectRoot = Path.of(System.getProperty(
                "stardewcraft.projectDir", "."));
        String names = Files.readString(projectRoot.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "mining", "MineMonsterNames.java"
        )));
        var matcher = Pattern.compile(
                "legacy\\(\"[^\"]+\",\\s*\"([^\"]+)\""
        ).matcher(names);
        Set<String> migratedIds = new TreeSet<>();
        while (matcher.find()) {
            migratedIds.add(matcher.group(1));
        }
        assertEquals(MineMonsterNames.ALL_IDS, migratedIds);

        String spawnHandler = Files.readString(projectRoot.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "event", "MineMonsterSpawnHandler.java"
        )));
        assertTrue(
                spawnHandler.indexOf("migrateLegacyDisplayName(")
                        < spawnHandler.indexOf(
                        "!serverLevel.dimension().equals("
                                + "ModMiningDimensions.STARDEW_MINING")
        );
    }

    private static String migratedKey(String legacyName, Set<String> tags) {
        Component migrated = MineMonsterNames.migrateLegacyDisplayName(
                Component.literal(legacyName), tags
        ).orElseThrow();
        return assertInstanceOf(
                TranslatableContents.class,
                migrated.getContents()
        ).getKey();
    }

    private static Set<String> registeredMonsterIds(String source) {
        Pattern firstString = Pattern.compile("^\\s*\"([^\"]+)\"");
        Set<String> result = new TreeSet<>();
        String[] registrations = source.split(
                "ENTITY_TYPES\\.register\\("
        );
        for (int index = 1; index < registrations.length; index++) {
            String registration = registrations[index];
            if (!registration.contains("MobCategory.MONSTER")) {
                continue;
            }
            var matcher = firstString.matcher(registration);
            assertTrue(matcher.find(), "Monster registration has no literal ID");
            result.add(matcher.group(1));
        }
        return Set.copyOf(result);
    }

    private static JsonObject readJson(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
