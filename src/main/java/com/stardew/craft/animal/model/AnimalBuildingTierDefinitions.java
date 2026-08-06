package com.stardew.craft.animal.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Atomic data-pack registry for animal-building capabilities, source costs,
 * construction durations and spatial validation rules.
 */
public final class AnimalBuildingTierDefinitions {
    public static final String DATA_DIRECTORY =
            "stardewcraft/animal_buildings";
    private static final String BUNDLED_DATA =
            "/data/stardewcraft/stardewcraft/animal_buildings/vanilla_1_6_15.json";
    private static volatile Snapshot snapshot = loadBundledSnapshot();

    private AnimalBuildingTierDefinitions() {
    }

    public static AnimalBuildingTierDefinition require(
            String family,
            int tier
    ) {
        AnimalBuildingTierDefinition definition =
                effectiveSnapshot().byKey().get(
                        key(family, tier));
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unknown animal-building tier "
                            + family + ":" + tier);
        }
        return definition;
    }

    public static long generation() {
        return effectiveSnapshot().generation();
    }

    static Snapshot decodeForTests(
            Map<ResourceLocation, JsonElement> resources
    ) {
        return decodeSnapshot(
                resources,
                effectiveSnapshot().generation() + 1L);
    }

    private static Snapshot loadBundledSnapshot() {
        try (var stream = AnimalBuildingTierDefinitions.class
                .getResourceAsStream(BUNDLED_DATA)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing bundled animal-building data "
                                + BUNDLED_DATA);
            }
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(
                            stream, StandardCharsets.UTF_8));
            ResourceLocation id =
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "vanilla_1_6_15");
            return decodeSnapshot(Map.of(id, root), 0L);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static Snapshot decodeSnapshot(
            Map<ResourceLocation, JsonElement> resources,
            long generation
    ) {
        Map<String, List<AnimalBuildingTierDefinition>> grouped =
                new LinkedHashMap<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> decodeResource(
                        entry.getKey(),
                        entry.getValue(),
                        grouped));

        LinkedHashMap<String, AnimalBuildingTierDefinition> byKey =
                new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            List<AnimalBuildingTierDefinition> candidates =
                    entry.getValue();
            if (candidates.size() == 1) {
                byKey.put(entry.getKey(), candidates.getFirst());
                continue;
            }
            List<AnimalBuildingTierDefinition> replacements =
                    candidates.stream()
                            .filter(AnimalBuildingTierDefinition
                                    ::replacesExisting)
                            .toList();
            if (replacements.size() != 1) {
                throw new IllegalArgumentException(
                        "Duplicate animal-building tier "
                                + entry.getKey()
                                + "; define exactly one replace=true entry");
            }
            byKey.put(entry.getKey(), replacements.getFirst());
        }
        for (String required : List.of(
                "coop:1", "coop:2", "coop:3",
                "barn:1", "barn:2", "barn:3",
                "silo:1")) {
            if (!byKey.containsKey(required)) {
                throw new IllegalArgumentException(
                        "Missing built-in animal-building tier "
                                + required);
            }
        }
        return new Snapshot(Map.copyOf(byKey), generation);
    }

    private static void decodeResource(
            ResourceLocation resourceId,
            JsonElement root,
            Map<String, List<AnimalBuildingTierDefinition>> output
    ) {
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException(
                    resourceId + ": root must be an object");
        }
        JsonObject object = root.getAsJsonObject();
        JsonArray entries = object.has("buildings")
                ? object.getAsJsonArray("buildings")
                : null;
        if (entries == null) {
            entries = new JsonArray();
            entries.add(object);
        }
        for (int index = 0; index < entries.size(); index++) {
            JsonObject value = entries.get(index).getAsJsonObject();
            ResourceLocation dataId =
                    ResourceLocation.fromNamespaceAndPath(
                            resourceId.getNamespace(),
                            resourceId.getPath() + "/" + index);
            AnimalBuildingTierDefinition definition =
                    parse(dataId, value);
            output.computeIfAbsent(
                    definition.key(), ignored -> new ArrayList<>())
                    .add(definition);
        }
    }

    private static AnimalBuildingTierDefinition parse(
            ResourceLocation dataId,
            JsonObject value
    ) {
        String family = requiredString(value, "family");
        int tier = requiredInt(value, "tier");
        ArrayList<AnimalBuildingTierDefinition.Material> materials =
                new ArrayList<>();
        if (value.has("materials")) {
            for (JsonElement element :
                    value.getAsJsonArray("materials")) {
                JsonObject material = element.getAsJsonObject();
                ResourceLocation item = ResourceLocation.tryParse(
                        material.get("item").getAsString());
                if (item == null) {
                    throw new IllegalArgumentException(
                            dataId + ": invalid material item");
                }
                materials.add(
                        new AnimalBuildingTierDefinition.Material(
                                item,
                                material.get("count").getAsInt()));
            }
        }
        AnimalBuildingTierDefinition.Validation defaults = defaultValidation(family, tier);
        JsonObject validation = value.has("validation")
                ? value.getAsJsonObject("validation")
                : new JsonObject();
        return new AnimalBuildingTierDefinition(
                dataId,
                bool(value, "replace", false),
                family,
                tier,
                requiredInt(value, "capacity"),
                integer(value, "hay_capacity", 0),
                bool(value, "allows_pregnancy", false),
                bool(value, "automatic_feed", false),
                requiredInt(value, "money"),
                materials,
                new AnimalBuildingTierDefinition.Validation(
                        integer(validation, "scan_range_xz", defaults.scanRangeXZ()),
                        integer(validation, "scan_range_up", defaults.scanRangeUp()),
                        integer(validation, "scan_range_down", defaults.scanRangeDown()),
                        integer(validation, "feed_troughs", defaults.feedTroughs()),
                        integer(validation, "auto_feed_troughs", defaults.autoFeedTroughs()),
                        integer(validation, "hay_hoppers", defaults.hayHoppers()),
                        integer(validation, "incubators", defaults.incubators()),
                        integer(validation, "min_interior_blocks", defaults.minInteriorBlocks()),
                        bool(validation, "require_enclosed", defaults.requireEnclosed()),
                        bool(validation, "require_door", defaults.requireDoor()),
                        integer(validation, "min_door_count", defaults.minDoorCount())));
    }

    private static AnimalBuildingTierDefinition.Validation defaultValidation(String family, int tier) {
        int[] facilities = switch (family + ":" + tier) {
            case "coop:1" -> new int[]{4, 0, 1, 0, 216};
            case "coop:2" -> new int[]{8, 0, 1, 1, 288};
            case "coop:3" -> new int[]{0, 12, 1, 1, 360};
            case "barn:1" -> new int[]{4, 0, 1, 0, 252};
            case "barn:2" -> new int[]{8, 0, 1, 0, 336};
            case "barn:3" -> new int[]{0, 12, 1, 0, 420};
            default -> new int[]{0, 0, 0, 0, 0};
        };
        boolean validatesInterior = family.equals("coop") || family.equals("barn");
        return new AnimalBuildingTierDefinition.Validation(
                validatesInterior ? 12 : 0,
                validatesInterior ? 12 : 0,
                validatesInterior ? 12 : 0,
                facilities[0], facilities[1], facilities[2], facilities[3], facilities[4],
                validatesInterior, validatesInterior, validatesInterior ? 1 : 0);
    }

    private static String requiredString(
            JsonObject value,
            String field
    ) {
        if (!value.has(field)
                || !value.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "Missing string field " + field);
        }
        return value.get(field).getAsString()
                .trim().toLowerCase(Locale.ROOT);
    }

    private static int requiredInt(
            JsonObject value,
            String field
    ) {
        if (!value.has(field)) {
            throw new IllegalArgumentException(
                    "Missing integer field " + field);
        }
        return value.get(field).getAsInt();
    }

    private static int integer(
            JsonObject value,
            String field,
            int fallback
    ) {
        return value.has(field)
                ? value.get(field).getAsInt()
                : fallback;
    }

    private static boolean bool(
            JsonObject value,
            String field,
            boolean fallback
    ) {
        return value.has(field)
                ? value.get(field).getAsBoolean()
                : fallback;
    }

    private static String key(String family, int tier) {
        return family.trim().toLowerCase(Locale.ROOT)
                + ":" + tier;
    }

    static void validateRuntimeReferences(
            Snapshot candidate
    ) {
        for (AnimalBuildingTierDefinition definition
                : candidate.byKey().values()) {
            for (AnimalBuildingTierDefinition.Material material
                    : definition.materials()) {
                if (!BuiltInRegistries.ITEM.containsKey(material.item())
                        || BuiltInRegistries.ITEM.get(material.item())
                                == Items.AIR) {
                    throw new IllegalArgumentException(
                            definition.dataId()
                                    + " references unknown material "
                                    + material.item());
                }
            }
        }
    }

    record Snapshot(
            Map<String, AnimalBuildingTierDefinition> byKey,
            long generation
    ) {
    }

    static Snapshot currentSnapshot() {
        return effectiveSnapshot();
    }

    private static Snapshot effectiveSnapshot() {
        return AnimalDefinitionSnapshot.buildings(snapshot);
    }
}
