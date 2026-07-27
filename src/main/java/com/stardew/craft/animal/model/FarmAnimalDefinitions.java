package com.stardew.craft.animal.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Transactional, data-pack-backed farm-animal definition registry.
 *
 * <p>Definitions are loaded from {@code data/<namespace>/stardewcraft/farm_animals/*.json}.
 * A mod JAR's built-in data pack and a user data pack therefore use exactly the same schema and
 * reload path. A higher-priority resource pack can replace a file with the same resource ID.
 * Cross-file replacement of an existing {@code animal_type_id} must opt in with
 * {@code "replace": true}; accidental duplicate IDs never silently win by load order.
 */
public final class FarmAnimalDefinitions {
    public static final String DATA_DIRECTORY = "stardewcraft/farm_animals";
    private static final String BUNDLED_DATA =
            "/data/stardewcraft/stardewcraft/farm_animals/vanilla_1_6_15.json";
    private static volatile Snapshot snapshot = loadBundledSnapshot();

    private FarmAnimalDefinitions() {
    }

    @Nullable
    public static FarmAnimalDefinition find(String animalTypeId) {
        if (animalTypeId == null) {
            return null;
        }
        return effectiveSnapshot().byAnimalType().get(
                normalizeId(animalTypeId));
    }

    public static FarmAnimalDefinition require(String animalTypeId) {
        FarmAnimalDefinition definition = find(animalTypeId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown farm animal type: " + animalTypeId);
        }
        return definition;
    }

    public static Collection<FarmAnimalDefinition> all() {
        return effectiveSnapshot().orderedDefinitions();
    }

    public static Set<String> ids() {
        return effectiveSnapshot().byAnimalType().keySet();
    }

    public static List<String> animalShopOrder() {
        return effectiveSnapshot().animalShopOrder();
    }

    public static long generation() {
        return effectiveSnapshot().generation();
    }

    public static String displayNameKeyFor(String animalTypeId) {
        FarmAnimalDefinition definition = find(animalTypeId);
        if (definition != null) {
            return definition.displayNameKey();
        }
        if (animalTypeId == null
                || animalTypeId.isBlank()) {
            return "entity.stardewcraft.unknown";
        }
        return defaultDisplayNameKey(
                normalizeId(animalTypeId));
    }

    static Snapshot decodeForTests(Map<ResourceLocation, JsonElement> resources) {
        return decodeSnapshot(
                resources,
                effectiveSnapshot().generation() + 1);
    }

    static void installForTests(Snapshot replacement) {
        snapshot = replacement;
    }

    private static Snapshot loadBundledSnapshot() {
        // The bundled parity data uses the public condition codec (e.g. seen_event for blue
        // chickens). Bootstrap is idempotent and normally already runs at the start of the mod
        // constructor; this also makes classpath/unit-test loading deterministic.
        com.stardew.craft.api.v1.internal.BuiltinApiTypes.bootstrap();
        try (var stream = FarmAnimalDefinitions.class.getResourceAsStream(BUNDLED_DATA)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled animal data " + BUNDLED_DATA);
            }
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            ResourceLocation id =
                    ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "vanilla_1_6_15");
            return decodeSnapshot(Map.of(id, root), 0L);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static Snapshot decodeSnapshot(
            Map<ResourceLocation, JsonElement> resources,
            long generation
    ) {
        List<Candidate> candidates = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> decodeResource(entry.getKey(), entry.getValue(), candidates));

        Map<String, List<Candidate>> grouped = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            grouped.computeIfAbsent(candidate.definition().id(), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        Map<String, FarmAnimalDefinition> byType = new LinkedHashMap<>();
        Map<String, ResourceLocation> sources = new LinkedHashMap<>();
        for (Map.Entry<String, List<Candidate>> entry : grouped.entrySet()) {
            Candidate selected = selectCandidate(entry.getKey(), entry.getValue());
            if (selected == null) {
                continue;
            }
            byType.put(entry.getKey(), selected.definition());
            sources.put(entry.getKey(), selected.source());
        }

        List<FarmAnimalDefinition> ordered = byType.values().stream()
                .sorted(Comparator.comparingInt(FarmAnimalDefinition::shopOrder)
                        .thenComparing(FarmAnimalDefinition::id))
                .toList();
        List<String> shopOrder = ordered.stream()
                .filter(FarmAnimalDefinition::soldByAnimalShop)
                .map(FarmAnimalDefinition::id)
                .toList();
        return new Snapshot(
                Map.copyOf(byType),
                List.copyOf(ordered),
                shopOrder,
                Map.copyOf(sources),
                generation
        );
    }

    private static void decodeResource(
            ResourceLocation resourceId,
            JsonElement root,
            List<Candidate> output
    ) {
        try {
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("root must be a JSON object");
            }
            JsonObject object = root.getAsJsonObject();
            if (object.has("animals")) {
                JsonArray animals = requireArray(object, "animals");
                for (int index = 0; index < animals.size(); index++) {
                    if (!animals.get(index).isJsonObject()) {
                        throw new IllegalArgumentException(
                                "animals[" + index + "] must be an object");
                    }
                    ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath(
                            resourceId.getNamespace(),
                            resourceId.getPath() + "/" + index
                    );
                    output.add(new Candidate(entryId, parseDefinition(
                            entryId, animals.get(index).getAsJsonObject())));
                }
            } else {
                output.add(new Candidate(resourceId, parseDefinition(resourceId, object)));
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid farm-animal resource "
                            + resourceId + ": "
                            + exception.getMessage(),
                    exception);
        }
    }

    private static Candidate selectCandidate(String animalTypeId, List<Candidate> candidates) {
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        List<Candidate> replacements = candidates.stream()
                .filter(candidate -> candidate.definition().replacesExisting())
                .toList();
        if (replacements.size() == 1) {
            Candidate selected = replacements.getFirst();
            StardewCraft.LOGGER.info(
                    "[ANIMAL_DATA] {} explicitly replaces {} other definition(s) for {}",
                    selected.source(), candidates.size() - 1, animalTypeId);
            return selected;
        }
        throw new IllegalArgumentException(
                "Duplicate animal_type_id " + animalTypeId + " from "
                        + candidates.stream().map(Candidate::source).toList()
                        + "; define exactly one explicit \"replace\": true entry"
        );
    }

    private static FarmAnimalDefinition parseDefinition(
            ResourceLocation dataId,
            JsonObject root
    ) {
        String id = requiredString(root, "animal_type_id");
        String sourceKey = requiredString(root, "source_key");
        String sourceHouse = requiredString(root, "source_house");
        String family = string(root, "family", familyFromHouse(sourceHouse));
        int purchasePrice = integer(root, "purchase_price", -1);
        int sellPrice = requiredInt(root, "sell_price");

        List<FarmAnimalDefinition.AlternatePurchaseType> purchaseTypes = new ArrayList<>();
        for (JsonObject value : objectList(root, "alternate_purchase_types")) {
            purchaseTypes.add(new FarmAnimalDefinition.AlternatePurchaseType(
                    requiredString(value, "id"),
                    condition(value, "condition"),
                    nullableString(value, "source_condition"),
                    number(value, "chance", 1.0),
                    stringList(value, "animal_type_ids")
            ));
        }

        List<FarmAnimalDefinition.ProduceEntry> produce = parseProduce(root, "produce");
        List<FarmAnimalDefinition.ProduceEntry> deluxe =
                parseProduce(root, "deluxe_produce");

        return new FarmAnimalDefinition(
                dataId,
                bool(root, "replace", false),
                id,
                sourceKey,
                sourceHouse,
                family,
                string(root, "gender", "Female"),
                purchasePrice,
                sellPrice,
                nullableString(root, "required_building"),
                condition(root, "unlock_condition"),
                purchaseTypes,
                resourceList(root, "egg_items"),
                integer(root, "incubation_time", -1),
                integer(root, "incubator_parent_sheet_offset", 1),
                string(root, "birth_text", ""),
                requiredInt(root, "days_to_mature"),
                bool(root, "can_get_pregnant", false),
                requiredInt(root, "days_to_produce"),
                parseHarvestType(requiredString(root, "harvest_type")),
                nullableResource(root, "harvest_tool"),
                produce,
                deluxe,
                bool(root, "produce_on_mature", false),
                integer(root, "friendship_for_faster_produce", -1),
                integer(root, "deluxe_produce_minimum_friendship", 200),
                number(root, "deluxe_produce_care_divisor", 1200.0),
                number(root, "deluxe_produce_luck_multiplier", 0.0),
                bool(root, "can_eat_golden_crackers", true),
                integer(root, "profession_for_happiness_boost", -1),
                integer(root, "profession_for_quality_boost", -1),
                integer(root, "profession_for_faster_produce", -1),
                bool(root, "can_swim", false),
                bool(root, "babies_follow_adults", false),
                requiredInt(root, "grass_eat_amount"),
                requiredInt(root, "happiness_drain"),
                requiredResource(root, "entity_type"),
                nullableResource(root, "sound_event"),
                parseProduceStats(root),
                integer(root, "required_building_tier", purchasePrice >= 0 ? 1 : 0),
                integer(root, "shop_order", Integer.MAX_VALUE),
                string(root, "default_name", sourceKey),
                string(root, "display_name_key", defaultDisplayNameKey(id)),
                nullableResource(root, "shop_texture"),
                integer(root, "shop_texture_width", 32),
                integer(root, "shop_texture_height", 16),
                string(root, "shop_description_key", ""),
                string(root, "shop_lock_reason_key", ""),
                nullableString(root, "project_override_reason")
        );
    }

    private static List<FarmAnimalDefinition.ProduceStat>
    parseProduceStats(JsonObject root) {
        List<FarmAnimalDefinition.ProduceStat> result =
                new ArrayList<>();
        for (JsonObject value :
                objectList(root, "produce_stats")) {
            result.add(new FarmAnimalDefinition.ProduceStat(
                    requiredString(value, "id"),
                    requiredString(value, "stat_name"),
                    nullableString(
                            value,
                            "source_required_item_id"),
                    optionalStringList(
                            value,
                            "source_required_tags"),
                    resourceList(value, "required_items"),
                    resourceList(value, "required_tags")
            ));
        }
        if (!result.isEmpty()) {
            return result;
        }

        // v1 compatibility: the old schema could only express one
        // unconditional tool-harvest stat.
        String legacy = nullableString(
                root, "produce_stat_key");
        if (legacy != null && !legacy.isBlank()) {
            return List.of(
                    new FarmAnimalDefinition.ProduceStat(
                            "Legacy",
                            legacy,
                            null,
                            List.of(),
                            List.of(),
                            List.of()
                    ));
        }
        return List.of();
    }

    private static List<FarmAnimalDefinition.ProduceEntry> parseProduce(
            JsonObject root,
            String field
    ) {
        List<FarmAnimalDefinition.ProduceEntry> result = new ArrayList<>();
        for (JsonObject value : objectList(root, field)) {
            result.add(new FarmAnimalDefinition.ProduceEntry(
                    string(value, "id", "Default"),
                    condition(value, "condition"),
                    nullableString(value, "source_condition"),
                    integer(value, "minimum_friendship", 0),
                    nullableString(value, "source_item_id"),
                    requiredResource(value, "item")
            ));
        }
        return result;
    }

    @Nullable
    private static StardewCondition condition(JsonObject root, String field) {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            return null;
        }
        return StardewConditions.CODEC.parse(JsonOps.INSTANCE, root.get(field))
                .getOrThrow(message -> new IllegalArgumentException(field + ": " + message));
    }

    private static FarmAnimalDefinition.HarvestType parseHarvestType(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "drop_overnight", "dropovernight" ->
                    FarmAnimalDefinition.HarvestType.DROP_OVERNIGHT;
            case "harvest_with_tool", "harvestwithtool" ->
                    FarmAnimalDefinition.HarvestType.HARVEST_WITH_TOOL;
            case "dig_up", "digup" -> FarmAnimalDefinition.HarvestType.DIG_UP;
            default -> throw new IllegalArgumentException("unknown harvest_type: " + raw);
        };
    }

    private static List<JsonObject> objectList(JsonObject root, String field) {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            return List.of();
        }
        JsonArray array = requireArray(root, field);
        List<JsonObject> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            if (!array.get(index).isJsonObject()) {
                throw new IllegalArgumentException(field + "[" + index + "] must be an object");
            }
            result.add(array.get(index).getAsJsonObject());
        }
        return result;
    }

    private static List<ResourceLocation> resourceList(JsonObject root, String field) {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            return List.of();
        }
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement element : requireArray(root, field)) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) {
                throw new IllegalArgumentException("invalid resource ID in " + field + ": " + element);
            }
            result.add(id);
        }
        return result;
    }

    private static List<String> stringList(JsonObject root, String field) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : requireArray(root, field)) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static List<String> optionalStringList(
            JsonObject root,
            String field
    ) {
        if (!root.has(field)
                || root.get(field).isJsonNull()) {
            return List.of();
        }
        return stringList(root, field);
    }

    private static JsonArray requireArray(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return root.getAsJsonArray(field);
    }

    private static String requiredString(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing string field " + field);
        }
        return root.get(field).getAsString();
    }

    private static String string(JsonObject root, String field, String fallback) {
        return root.has(field) && !root.get(field).isJsonNull()
                ? root.get(field).getAsString()
                : fallback;
    }

    @Nullable
    private static String nullableString(JsonObject root, String field) {
        return root.has(field) && !root.get(field).isJsonNull()
                ? root.get(field).getAsString()
                : null;
    }

    private static int requiredInt(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing integer field " + field);
        }
        return root.get(field).getAsInt();
    }

    private static int integer(JsonObject root, String field, int fallback) {
        return root.has(field) && !root.get(field).isJsonNull()
                ? root.get(field).getAsInt()
                : fallback;
    }

    private static double number(JsonObject root, String field, double fallback) {
        return root.has(field) && !root.get(field).isJsonNull()
                ? root.get(field).getAsDouble()
                : fallback;
    }

    private static boolean bool(JsonObject root, String field, boolean fallback) {
        return root.has(field) && !root.get(field).isJsonNull()
                ? root.get(field).getAsBoolean()
                : fallback;
    }

    private static ResourceLocation requiredResource(JsonObject root, String field) {
        ResourceLocation id = ResourceLocation.tryParse(requiredString(root, field));
        if (id == null) {
            throw new IllegalArgumentException("invalid resource ID in " + field);
        }
        return id;
    }

    @Nullable
    private static ResourceLocation nullableResource(JsonObject root, String field) {
        String value = nullableString(root, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid resource ID in " + field);
        }
        return id;
    }

    private static String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String familyFromHouse(String sourceHouse) {
        String normalized = sourceHouse.toLowerCase(Locale.ROOT);
        if (normalized.contains("coop")) {
            return "coop";
        }
        if (normalized.contains("barn")) {
            return "barn";
        }
        throw new IllegalArgumentException(
                "family is required when source_house is not a Coop or Barn variant");
    }

    private static String defaultDisplayNameKey(String animalTypeId) {
        int separator = animalTypeId.indexOf(':');
        if (separator > 0 && separator < animalTypeId.length() - 1) {
            return "entity." + animalTypeId.substring(0, separator) + "."
                    + animalTypeId.substring(separator + 1);
        }
        return "entity.stardewcraft." + animalTypeId;
    }

    static void validateRuntimeReferences(Snapshot candidate) {
        for (FarmAnimalDefinition definition : candidate.orderedDefinitions()) {
            if (!AnimalNameRules.isValidExplicitName(
                    definition.defaultName())) {
                throw new IllegalArgumentException(
                        definition.dataId()
                                + " has an invalid default_name");
            }
            if (definition.soldByAnimalShop()
                    && definition.requiredBuildingTier() < 1) {
                throw new IllegalArgumentException(
                        definition.dataId()
                                + " is sold by the animal shop but has no required building tier");
            }
            if (definition.deluxeProduceCareDivisor() <= 0.0D
                    && definition.deluxeProduceCareDivisor()
                    != -1.0D) {
                throw new IllegalArgumentException(
                        definition.dataId()
                                + " has invalid deluxe_produce_care_divisor");
            }
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(definition.entityTypeId())) {
                throw new IllegalArgumentException(
                        definition.dataId() + " references unknown entity_type "
                                + definition.entityTypeId());
            }
            if (definition.harvestTool() != null
                    && (!BuiltInRegistries.ITEM.containsKey(definition.harvestTool())
                    || BuiltInRegistries.ITEM.get(definition.harvestTool())
                    == Items.AIR)) {
                throw new IllegalArgumentException(
                        definition.dataId() + " references unknown harvest_tool "
                                + definition.harvestTool());
            }
            if (definition.soundEventId() != null
                    && !BuiltInRegistries.SOUND_EVENT.containsKey(definition.soundEventId())) {
                throw new IllegalArgumentException(
                        definition.dataId() + " references unknown sound_event "
                                + definition.soundEventId());
            }
            for (FarmAnimalDefinition.ProduceEntry produceEntry : definition.produce()) {
                validateProduceItem(definition, produceEntry);
            }
            for (FarmAnimalDefinition.ProduceEntry produceEntry : definition.deluxeProduce()) {
                validateProduceItem(definition, produceEntry);
            }
            for (ResourceLocation eggItem : definition.eggItemIds()) {
                if (!BuiltInRegistries.ITEM.containsKey(eggItem)
                        || BuiltInRegistries.ITEM.get(eggItem)
                        == Items.AIR) {
                    throw new IllegalArgumentException(
                            definition.dataId() + " references unknown egg item " + eggItem);
                }
            }
            java.util.HashSet<String> statIds =
                    new java.util.HashSet<>();
            for (FarmAnimalDefinition.ProduceStat stat
                    : definition.produceStats()) {
                if (!statIds.add(stat.id())) {
                    throw new IllegalArgumentException(
                            definition.dataId()
                                    + " has duplicate produce stat id "
                                    + stat.id());
                }
                for (ResourceLocation requiredItem
                        : stat.requiredItems()) {
                    if (!BuiltInRegistries.ITEM
                            .containsKey(requiredItem)
                            || BuiltInRegistries.ITEM
                            .get(requiredItem) == Items.AIR) {
                        throw new IllegalArgumentException(
                                definition.dataId()
                                        + " produce stat "
                                        + stat.id()
                                        + " references unknown required item "
                                        + requiredItem);
                    }
                }
            }
            for (FarmAnimalDefinition.AlternatePurchaseType alternate
                    : definition.alternatePurchaseTypes()) {
                for (String targetId : alternate.animalTypeIds()) {
                    FarmAnimalDefinition target =
                            candidate.byAnimalType().get(normalizeId(targetId));
                    if (target == null) {
                        throw new IllegalArgumentException(
                                definition.dataId() + " alternate purchase type "
                                        + alternate.id() + " references unknown animal "
                                        + targetId);
                    }
                    if (!definition.family().equals(target.family())) {
                        throw new IllegalArgumentException(
                                definition.dataId() + " alternate purchase target " + targetId
                                        + " changes building family from " + definition.family()
                                        + " to " + target.family());
                    }
                    if (target.requiredBuildingTier()
                            > definition.requiredBuildingTier()) {
                        throw new IllegalArgumentException(
                                definition.dataId()
                                        + " alternate purchase target "
                                        + targetId
                                        + " requires a higher building tier than its shop entry");
                    }
                }
            }
        }
    }

    private static void validateProduceItem(
            FarmAnimalDefinition definition,
            FarmAnimalDefinition.ProduceEntry produceEntry
    ) {
        if (!BuiltInRegistries.ITEM.containsKey(produceEntry.itemId())
                || BuiltInRegistries.ITEM.get(produceEntry.itemId())
                == Items.AIR) {
            throw new IllegalArgumentException(
                    definition.dataId() + " references unknown produce item "
                            + produceEntry.itemId());
        }
    }

    static Snapshot currentSnapshot() {
        return effectiveSnapshot();
    }

    private static Snapshot effectiveSnapshot() {
        return AnimalDefinitionSnapshot.animals(snapshot);
    }

    record Snapshot(
            Map<String, FarmAnimalDefinition> byAnimalType,
            List<FarmAnimalDefinition> orderedDefinitions,
            List<String> animalShopOrder,
            Map<String, ResourceLocation> sources,
            long generation
    ) {
    }

    private record Candidate(
            ResourceLocation source,
            FarmAnimalDefinition definition
    ) {
    }
}
