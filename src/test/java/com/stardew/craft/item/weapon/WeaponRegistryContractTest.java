package com.stardew.craft.item.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponRegistryContractTest {
    @Test
    void everyRegisteredWeaponHasAValidDefinitionAndCanonicalSkillIds() {
        assertFalse(WeaponRegistry.getAll().isEmpty());
        Set<String> ids = new HashSet<>();
        Set<String> skillIds = new HashSet<>();

        for (WeaponData weapon : WeaponRegistry.getAll()) {
            assertTrue(ids.add(weapon.getId()), () -> "duplicate weapon id " + weapon.getId());
            assertEquals(weapon, WeaponRegistry.get(weapon.getId()));
            assertNotNull(weapon.getWeaponType());
            assertTrue(weapon.getDamageMin() >= 0, weapon::getId);
            assertTrue(weapon.getDamageMax() >= weapon.getDamageMin(), weapon::getId);

            verifySkill(weapon.getSkill1(), skillIds);
            verifySkill(weapon.getSkill2(), skillIds);
        }

        assertEquals(53, ids.size());
        assertEquals(64, skillIds.size());
        assertEquals(ids, new HashSet<>(WeaponRegistry.getAllIds()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> WeaponRegistry.getAllIds().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> WeaponRegistry.getAll().clear()
        );
    }

    @Test
    void buildersRejectDefinitionsThatWouldCorruptRuntimeContracts() {
        assertThrows(
                IllegalStateException.class,
                () -> WeaponData.builder("broken").damage(5, 2).build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponSkillData.builder("Invalid Skill Id").build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponData.builder("Invalid Weapon Id").build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponData.builder("other:weapon").build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponSkillData.builder("negative_cooldown").cooldown(-1).build()
        );
        assertThrows(
                IllegalStateException.class,
                () -> WeaponData.builder("negative_precision").precision(-1).build()
        );
    }

    @Test
    void everyRegistryDefinitionProjectsDirectCriticalMultiplierAndPrecision() {
        for (WeaponData weapon : WeaponRegistry.getAll()) {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            WeaponItemSupport.ensureStats(stack, weapon);
            WeaponStats stats = WeaponStats.fromItemStack(stack);
            float expectedPoints = (float) Math.max(
                    0.0,
                    (weapon.getCritMultiplier() - 3.0) * 50.0
            );

            assertEquals(expectedPoints, stats.getBonusCritPower(), 0.00001f,
                    weapon::getId);
            assertEquals(weapon.getPrecision(), stats.getPrecision(),
                    weapon::getId);
            assertEquals(
                    weapon.getCritMultiplier(),
                    3.0f + stats.getBonusCritPower() / 50.0f,
                    0.00001f,
                    weapon::getId
            );
        }
    }

    @Test
    void originalCriticalMultipliersAreCompleteAndExact() {
        Map<String, Double> nonDefault = Map.ofEntries(
                Map.entry("obsidian_edge", 3.2),
                Map.entry("yeti_tooth", 3.2),
                Map.entry("steel_falchion", 3.4),
                Map.entry("lava_katana", 3.5),
                Map.entry("dragontooth_cutlass", 4.0),
                Map.entry("wind_spire", 3.2),
                Map.entry("burglars_shank", 3.5),
                Map.entry("crystal_dagger", 4.0),
                Map.entry("iridium_needle", 7.0),
                Map.entry("dragontooth_shiv", 5.0),
                Map.entry("leahs_whittler", 3.2),
                Map.entry("haleys_iron", 3.2)
        );
        for (WeaponData weapon : WeaponRegistry.getAll()) {
            assertEquals(
                    nonDefault.getOrDefault(weapon.getId(), 3.0),
                    weapon.getCritMultiplier(),
                    weapon::getId
            );
        }

        ItemStack lavaKatana = new ItemStack(Items.IRON_SWORD);
        WeaponItemSupport.ensureStats(
                lavaKatana,
                WeaponRegistry.get("lava_katana")
        );
        assertEquals(
                25.0f,
                WeaponStats.fromItemStack(lavaKatana).getBonusCritPower()
        );

        ItemStack iridiumNeedle = new ItemStack(Items.IRON_SWORD);
        WeaponItemSupport.ensureStats(
                iridiumNeedle,
                WeaponRegistry.get("iridium_needle")
        );
        assertEquals(
                200.0f,
                WeaponStats.fromItemStack(iridiumNeedle).getBonusCritPower()
        );
    }

    @Test
    void originalNonZeroPrecisionIsCompleteAndExact() {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("silver_saber", 1),
                Map.entry("forest_sword", 5),
                Map.entry("meowmere", 5),
                Map.entry("bone_sword", 5),
                Map.entry("neptunes_glaive", 6),
                Map.entry("templars_blade", 10),
                Map.entry("insect_head", 9),
                Map.entry("steel_falchion", 5),
                Map.entry("elf_blade", 5),
                Map.entry("burglars_shank", 5),
                Map.entry("crystal_dagger", 10),
                Map.entry("wicked_kris", 8),
                Map.entry("broken_trident", 8),
                Map.entry("elliotts_pencil", 8),
                Map.entry("abbys_planchette", 8)
        );

        Map<String, Integer> actual = new HashMap<>();
        for (WeaponData weapon : WeaponRegistry.getAll()) {
            if (weapon.getPrecision() > 0) {
                actual.put(weapon.getId(), weapon.getPrecision());
            }
        }
        assertEquals(expected, actual);
    }

    @Test
    void spouseWeaponSpeedUsesStardewsDisplayedStatUnits() {
        for (String club : Set.of(
                "alexs_bat",
                "sams_old_guitar",
                "marus_wrench",
                "harveys_mallet",
                "pennys_fryer",
                "sebs_lost_mace"
        )) {
            assertEquals(-2, WeaponRegistry.get(club).getSpeed(), club);
        }
        assertEquals(-1, WeaponRegistry.get("leahs_whittler").getSpeed());
        assertEquals(-1, WeaponRegistry.get("haleys_iron").getSpeed());
    }

    @Test
    void builtInCombatStatsExactlyMatchLocalStardewWeaponData()
            throws Exception {
        Path project = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        JsonObject source = readJson(project.resolve(Path.of(
                "源文件", "Content", "Data", "Weapons.json"
        )));
        Map<String, JsonObject> byId = new HashMap<>();
        for (var entry : source.entrySet()) {
            JsonObject data = entry.getValue().getAsJsonObject();
            byId.put(canonicalSourceId(data.get("Name").getAsString()), data);
        }

        for (WeaponData weapon : WeaponRegistry.getAll()) {
            JsonObject original = byId.get(weapon.getId());
            assertNotNull(original, weapon::getId);
            int rawType = original.get("Type").getAsInt();
            int rawSpeed = original.get("Speed").getAsInt();
            int baseline = rawType == 2 ? -8 : 0;

            assertEquals(sourceType(rawType), weapon.getWeaponType(), weapon::getId);
            assertEquals(original.get("MinDamage").getAsInt(), weapon.getDamageMin(), weapon::getId);
            assertEquals(original.get("MaxDamage").getAsInt(), weapon.getDamageMax(), weapon::getId);
            assertEquals(original.get("Defense").getAsInt(), weapon.getDefense(), weapon::getId);
            assertEquals(original.get("Precision").getAsInt(), weapon.getPrecision(), weapon::getId);
            assertEquals(original.get("CritChance").getAsDouble(), weapon.getCritChance(), weapon::getId);
            assertEquals(original.get("CritMultiplier").getAsDouble(), weapon.getCritMultiplier(), weapon::getId);
            assertEquals((rawSpeed - baseline) / 2, weapon.getSpeed(), weapon::getId);
            assertEquals(rawSpeed, weapon.getRawSpeed(), weapon::getId);
            assertEquals(original.get("Knockback").getAsDouble(), weapon.getKnockback(), 0.00001, weapon::getId);
        }
    }

    @Test
    void builtInItemsExactlyMatchRegistryIdsConstructorIdsAndTypes()
            throws Exception {
        Map<String, RegisteredItem> items = discoverBuiltInWeaponItems();
        assertEquals(53, items.size());
        assertEquals(new HashSet<>(WeaponRegistry.getAllIds()), items.keySet());

        for (WeaponData weapon : WeaponRegistry.getAll()) {
            RegisteredItem item = items.get(weapon.getId());
            assertNotNull(item, weapon::getId);
            assertEquals(weapon.getId(), item.constructorId(), weapon::getId);
            assertEquals(expectedItemClass(weapon.getWeaponType()),
                    item.className(), weapon::getId);
        }
    }

    @Test
    void everyBuiltInWeaponAndSkillTooltipKeyExistsInAllLanguages()
            throws Exception {
        var englishResource = Objects.requireNonNull(
                WeaponRegistryContractTest.class.getClassLoader()
                        .getResource("assets/stardewcraft/lang/en_us.json")
        );
        Path languageDirectory = Path.of(englishResource.toURI()).getParent();
        try (var languages = Files.list(languageDirectory)) {
            var files = languages
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
            assertEquals(12, files.size());
            for (Path language : files) {
                JsonObject translations = readJson(language);
                for (WeaponData weapon : WeaponRegistry.getAll()) {
                    assertTranslation(
                            translations,
                            "item.stardewcraft." + weapon.getId(),
                            language
                    );
                    verifySkillTranslations(
                            translations,
                            weapon.getSkill1(),
                            language
                    );
                    verifySkillTranslations(
                            translations,
                            weapon.getSkill2(),
                            language
                    );
                }
            }
        }
    }

    private static void verifySkill(
            WeaponSkillData skill,
            Set<String> skillIds
    ) {
        if (skill == null) {
            return;
        }
        assertTrue(skillIds.add(skill.getId()),
                () -> "duplicate built-in skill id " + skill.getId());
        assertNotNull(skill.getResourceId());
        assertTrue(skill.matches(skill.getResourceId()), skill::getId);
        assertTrue(skill.getDamagePercent() >= 0, skill::getId);
        assertTrue(skill.getCooldown() >= 0, skill::getId);
        assertFalse(skill.getNameKey().isBlank(), skill::getId);
        assertFalse(skill.getDescriptionKeys().isEmpty(), skill::getId);
        assertTrue(skill.getDescriptionKeys().stream().noneMatch(String::isBlank),
                skill::getId);
        assertFalse(skill.getEffectKeys().isEmpty(), skill::getId);
        assertTrue(skill.getEffectKeys().stream().noneMatch(String::isBlank),
                skill::getId);
        assertNotNull(skill.getIconChar(), skill::getId);
        assertFalse(skill.getIconChar().isBlank(), skill::getId);
    }

    private static Map<String, RegisteredItem> discoverBuiltInWeaponItems()
            throws Exception {
        Path project = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        String source = Files.readString(project.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "item", "ModItems.java"
        )));
        Pattern registration = Pattern.compile(
                "ITEMS\\.register\\(\\\"([^\\\"]+)\\\"\\s*,\\s*"
                        + "\\(\\)\\s*->\\s*new\\s+"
                        + "com\\.stardew\\.craft\\.item\\.weapon\\."
                        + "(StardewWeaponItem|StardewDaggerItem|StardewClubItem)"
                        + "\\(\\\"([^\\\"]+)\\\""
        );
        Map<String, RegisteredItem> result = new HashMap<>();
        var matcher = registration.matcher(source);
        while (matcher.find()) {
            String registryId = matcher.group(1);
            RegisteredItem previous = result.put(
                    registryId,
                    new RegisteredItem(matcher.group(2), matcher.group(3))
            );
            assertTrue(previous == null,
                    () -> "duplicate item registration " + registryId);
        }
        return result;
    }

    private static String expectedItemClass(WeaponType type) {
        return switch (type) {
            case DAGGER -> "StardewDaggerItem";
            case CLUB -> "StardewClubItem";
            default -> "StardewWeaponItem";
        };
    }

    private static WeaponType sourceType(int type) {
        return switch (type) {
            case 1 -> WeaponType.DAGGER;
            case 2 -> WeaponType.CLUB;
            case 0, 3 -> WeaponType.SWORD;
            default -> throw new IllegalArgumentException("unsupported source weapon type " + type);
        };
    }

    private static String canonicalSourceId(String name) {
        String id = name.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "pirates_sword".equals(id) ? "pirate_sword" : id;
    }

    private static void verifySkillTranslations(
            JsonObject translations,
            WeaponSkillData skill,
            Path language
    ) {
        if (skill == null) {
            return;
        }
        assertTranslation(translations, skill.getNameKey(), language);
        skill.getDescriptionKeys().forEach(
                key -> assertTranslation(translations, key, language)
        );
        skill.getEffectKeys().forEach(
                key -> assertTranslation(translations, key, language)
        );
    }

    private static void assertTranslation(
            JsonObject translations,
            String key,
            Path language
    ) {
        assertTrue(translations.has(key),
                () -> language.getFileName() + " missing " + key);
        assertFalse(translations.get(key).getAsString().isBlank(),
                () -> language.getFileName() + " has blank " + key);
    }

    private static JsonObject readJson(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private record RegisteredItem(String className, String constructorId) {}
}
