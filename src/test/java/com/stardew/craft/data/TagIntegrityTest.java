package com.stardew.craft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.core.ModTags;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagIntegrityTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));
    private static final Path DATA = PROJECT.resolve("src/main/resources/data");

    @Test
    void bundledTagsUseValidJsonAndCurrentRegistryFolderNames() throws Exception {
        try (var paths = Files.walk(DATA)) {
            for (Path path : paths.filter(TagIntegrityTest::isTagJson).toList()) {
                String normalized = path.toString().replace('\\', '/');
                assertFalse(normalized.contains("/tags/items/") || normalized.contains("/tags/blocks/"),
                        () -> path + " uses a pre-1.21 plural tag registry folder");

                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                assertTrue(root.has("values") && root.get("values").isJsonArray(),
                        () -> path + " must contain a values array");
            }
        }
    }

    @Test
    void everyFixedModTagKeyHasABundledDefinition() throws Exception {
        assertTagKeysHaveFiles(ModTags.Blocks.class, "block");
        assertTagKeysHaveFiles(ModTags.Items.class, "item");
    }

    @Test
    void nestedStardewcraftTagReferencesResolveWithinTheirRegistry() throws Exception {
        Path root = DATA.resolve("stardewcraft/tags");
        for (String registry : List.of("block", "item")) {
            Path registryRoot = root.resolve(registry);
            try (var paths = Files.walk(registryRoot)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(TagIntegrityTest::isJson).toList()) {
                    for (String value : directValues(path)) {
                        if (!value.startsWith("#stardewcraft:")) {
                            continue;
                        }
                        Path target = registryRoot.resolve(value.substring("#stardewcraft:".length()) + ".json");
                        assertTrue(Files.isRegularFile(target),
                                () -> path + " references missing tag " + value);
                    }
                }
            }
        }
    }

    @Test
    void blockAndItemShapeTagsStayInSync() throws Exception {
        assertBlockItemsCovered("slabs", "slabs", "wooden_slabs");
        assertBlockItemsCovered("stairs", "stairs", "wooden_stairs");
        assertBlockItemsCovered("walls", "walls");
        assertBlockItemsCovered("doors", "doors");
    }

    @Test
    void gameplayCriticalBlocksKeepTheirSemanticAndMiningTags() throws Exception {
        Set<String> machines = resolvedValues("stardewcraft", "block", "machines", new HashSet<>());
        for (String id : List.of(
                "stardewcraft:bait_maker",
                "stardewcraft:heavy_furnace",
                "stardewcraft:anvil_mastery",
                "stardewcraft:mini_forge",
                "stardewcraft:geode_crusher",
                "stardewcraft:farm_computer",
                "stardewcraft:bone_mill",
                "stardewcraft:coffee_maker",
                "stardewcraft:sprinkler",
                "stardewcraft:quality_sprinkler",
                "stardewcraft:iridium_sprinkler")) {
            assertTrue(machines.contains(id), () -> id + " is missing from #stardewcraft:machines");
        }

        Set<String> pickaxe = directValues(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        for (String id : List.of(
                "stardewcraft:heavy_furnace",
                "stardewcraft:anvil_mastery",
                "stardewcraft:mini_forge",
                "stardewcraft:geode_crusher",
                "stardewcraft:farm_computer",
                "stardewcraft:coffee_maker")) {
            assertTrue(pickaxe.contains(id), () -> id + " is missing from #minecraft:mineable/pickaxe");
        }

        Set<String> axe = directValues(DATA.resolve("minecraft/tags/block/mineable/axe.json"));
        assertTrue(axe.contains("stardewcraft:bone_mill"));
        assertTrue(axe.contains("stardewcraft:friendship_door"));
    }

    @Test
    void curiosIntegrationDoesNotReturn() throws Exception {
        assertFalse(Files.exists(DATA.resolve("curios")),
                "Curios data-pack resources must not be bundled");
        assertFalse(Files.readString(PROJECT.resolve("build.gradle")).contains("theillusivec4.curios"));
        assertFalse(Files.readString(PROJECT.resolve("src/main/templates/META-INF/neoforge.mods.toml"))
                .contains("modId=\"curios\""));

        try (var paths = Files.walk(PROJECT.resolve("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("top.theillusivec4.curios") || source.contains("CuriosCompat"),
                        () -> path + " still contains active Curios integration");
            }
        }
    }

    private static void assertTagKeysHaveFiles(Class<?> holder, String registry) throws Exception {
        for (Field field : holder.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !TagKey.class.isAssignableFrom(field.getType())) {
                continue;
            }
            TagKey<?> tag = (TagKey<?>) field.get(null);
            Path path = DATA.resolve(tag.location().getNamespace())
                    .resolve("tags")
                    .resolve(registry)
                    .resolve(tag.location().getPath() + ".json");
            assertTrue(Files.isRegularFile(path), () -> field.getName() + " points to missing tag " + tag.location());
        }
    }

    private static void assertBlockItemsCovered(String blockTag, String... itemTags) throws Exception {
        Set<String> blocks = directValues(DATA.resolve("minecraft/tags/block/" + blockTag + ".json"));
        Set<String> items = new HashSet<>();
        for (String itemTag : itemTags) {
            items.addAll(directValues(DATA.resolve("minecraft/tags/item/" + itemTag + ".json")));
        }
        for (String block : blocks) {
            if (block.startsWith("stardewcraft:")) {
                assertTrue(items.contains(block), () -> block + " has a block tag but no matching item tag");
            }
        }
    }

    private static Set<String> resolvedValues(
            String namespace,
            String registry,
            String path,
            Set<String> visiting) throws Exception {
        String key = namespace + ":" + path;
        assertTrue(visiting.add(key), () -> "Cyclic tag reference: " + key);
        Path file = DATA.resolve(namespace).resolve("tags").resolve(registry).resolve(path + ".json");
        Set<String> result = new HashSet<>();
        for (String value : directValues(file)) {
            if (value.startsWith("#")) {
                String reference = value.substring(1);
                int separator = reference.indexOf(':');
                result.addAll(resolvedValues(reference.substring(0, separator), registry,
                        reference.substring(separator + 1), visiting));
            } else {
                result.add(value);
            }
        }
        visiting.remove(key);
        return result;
    }

    private static Set<String> directValues(Path path) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        Set<String> values = new HashSet<>();
        for (JsonElement value : root.getAsJsonArray("values")) {
            if (value.isJsonPrimitive()) {
                values.add(value.getAsString());
            } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
                values.add(value.getAsJsonObject().get("id").getAsString());
            }
        }
        return values;
    }

    private static boolean isTagJson(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return Files.isRegularFile(path) && normalized.contains("/tags/") && isJson(path);
    }

    private static boolean isJson(Path path) {
        return path.getFileName().toString().endsWith(".json");
    }
}
