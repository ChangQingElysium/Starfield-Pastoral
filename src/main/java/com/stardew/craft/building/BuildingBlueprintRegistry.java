package com.stardew.craft.building;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;
import com.stardew.craft.api.v1.building.StardewBuildingBlueprintDefinition;
import com.stardew.craft.api.v1.building.StardewBuildingMaterial;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic Java/datapack catalog behind the public building API. */
public final class BuildingBlueprintRegistry {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FIELDS = Set.of(
            "builder", "order", "display_name", "description",
            "money", "materials", "result_item", "result_count",
            "upgrade", "preview_canvas_size", "magical_construction",
            "available_when", "tags", "properties");
    private static final Map<ResourceLocation,
            StardewBuildingBlueprintDefinition> JAVA = new LinkedHashMap<>();
    private static volatile Catalog catalog = Catalog.empty();
    private static volatile ReloadReport lastReport =
            new ReloadReport(0, 0, false, List.of());

    private BuildingBlueprintRegistry() {
    }

    public static synchronized void register(
            ResourceLocation id,
            StardewBuildingBlueprintDefinition definition
    ) {
        if (id == null || definition == null) {
            throw new NullPointerException(
                    "building blueprint ID and definition are required");
        }
        if (JAVA.containsKey(id) || catalog.data().containsKey(id)) {
            throw new IllegalStateException(
                    "Building blueprint already registered: " + id);
        }
        validateReferences(id, definition);
        JAVA.put(id, definition);
        rebuild(catalog.data());
    }

    public static Optional<StardewBuildingBlueprint> find(
            ResourceLocation id
    ) {
        return Optional.ofNullable(
                id == null ? null : catalog.byId().get(id));
    }

    public static List<StardewBuildingBlueprint> all() {
        return catalog.ordered();
    }

    public static List<StardewBuildingBlueprint> forBuilder(
            ResourceLocation builder
    ) {
        if (builder == null) {
            return List.of();
        }
        return catalog.ordered().stream()
                .filter(blueprint -> blueprint.definition().builder()
                        .equals(builder))
                .toList();
    }

    public static List<StardewBuildingBlueprint> availableFor(
            ServerPlayer player,
            ResourceLocation builder
    ) {
        if (player == null) {
            return List.of();
        }
        StardewConditionContext context =
                StardewConditionContext.forPlayer(player);
        return forBuilder(builder).stream()
                .filter(blueprint -> blueprint.definition()
                        .availableWhen().stream().allMatch(condition ->
                                StardewConditions.test(condition, context)
                                        .resultOrPartial(message ->
                                                StardewCraft.LOGGER.error(
                                                        "Building blueprint {} condition failed: {}",
                                                        blueprint.id(),
                                                        message))
                                        .orElse(false)))
                .toList();
    }

    public static long revision() {
        return catalog.revision();
    }

    public static ReloadReport lastReport() {
        return lastReport;
    }

    static synchronized void publish(
            Map<ResourceLocation, StardewBuildingBlueprintDefinition>
                    candidate
    ) {
        for (ResourceLocation id : candidate.keySet()) {
            if (JAVA.containsKey(id)) {
                throw new IllegalStateException(
                        "Data building blueprint conflicts with Java blueprint: "
                                + id);
            }
        }
        rebuild(candidate);
    }

    private static void rebuild(
            Map<ResourceLocation, StardewBuildingBlueprintDefinition>
                    data
    ) {
        LinkedHashMap<ResourceLocation, StardewBuildingBlueprint> merged =
                new LinkedHashMap<>();
        JAVA.forEach((id, definition) -> merged.put(
                id, new StardewBuildingBlueprint(id, definition)));
        data.forEach((id, definition) -> merged.put(
                id, new StardewBuildingBlueprint(id, definition)));
        List<StardewBuildingBlueprint> prepared = merged.values().stream()
                .sorted(Comparator
                        .comparing((StardewBuildingBlueprint blueprint) ->
                                blueprint.definition().builder().toString())
                        .thenComparingInt(blueprint ->
                                blueprint.definition().order())
                        .thenComparing(blueprint ->
                                blueprint.id().toString()))
                .toList();
        LinkedHashMap<ResourceLocation, StardewBuildingBlueprint> indexed =
                new LinkedHashMap<>();
        prepared.forEach(blueprint ->
                indexed.put(blueprint.id(), blueprint));
        catalog = new Catalog(
                catalog.revision() + 1,
                data,
                indexed,
                prepared);
    }

    static Catalog catalog() {
        return catalog;
    }

    static StardewBuildingBlueprintDefinition decode(
            ResourceLocation id,
            JsonElement raw
    ) {
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "building blueprint must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        for (String field : object.keySet()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "unknown field '" + field + "'");
            }
        }
        StardewBuildingBlueprintDefinition definition =
                StardewBuildingBlueprintDefinition.CODEC
                        .parse(JsonOps.INSTANCE, object)
                        .getOrThrow(message ->
                                new IllegalArgumentException(message));
        validateReferences(id, definition);
        return definition;
    }

    private static void validateReferences(
            ResourceLocation id,
            StardewBuildingBlueprintDefinition definition
    ) {
        validateItem(id, "result_item", definition.resultItem());
        for (StardewBuildingMaterial material : definition.materials()) {
            validateItem(id, "material", material.item());
        }
    }

    private static void validateItem(
            ResourceLocation blueprintId,
            String field,
            ResourceLocation itemId
    ) {
        if (!BuiltInRegistries.ITEM.containsKey(itemId)
                || BuiltInRegistries.ITEM.get(itemId) == Items.AIR) {
            throw new IllegalArgumentException(
                    blueprintId + ": unknown " + field + " item "
                            + itemId);
        }
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "building_blueprints");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            LinkedHashMap<ResourceLocation,
                    StardewBuildingBlueprintDefinition> candidate =
                    new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(
                                    ResourceLocation::toString)))
                    .forEach(entry -> {
                        try {
                            candidate.put(entry.getKey(), decode(
                                    entry.getKey(), entry.getValue()));
                        } catch (RuntimeException exception) {
                            errors.add(entry.getKey() + ": "
                                    + exception.getMessage());
                        }
                    });
            for (ResourceLocation id : candidate.keySet()) {
                if (JAVA.containsKey(id)) {
                    errors.add(id
                            + ": conflicts with a Java blueprint");
                }
            }
            if (!errors.isEmpty()) {
                errors.forEach(error -> StardewCraft.LOGGER.error(
                        "[BuildingBlueprints] {}", error));
                lastReport = new ReloadReport(
                        catalog.revision(),
                        catalog.data().size(),
                        true,
                        errors);
                StardewCraft.LOGGER.error(
                        "[BuildingBlueprints] Rejected reload; keeping {} data blueprints",
                        catalog.data().size());
                return;
            }
            publish(candidate);
            lastReport = new ReloadReport(
                    catalog.revision(),
                    candidate.size(),
                    false,
                    List.of());
            StardewCraft.LOGGER.info(
                    "[BuildingBlueprints] Applied snapshot v{} ({} data blueprints)",
                    catalog.revision(), candidate.size());
        }
    }

    record Catalog(
            long revision,
            Map<ResourceLocation, StardewBuildingBlueprintDefinition> data,
            Map<ResourceLocation, StardewBuildingBlueprint> byId,
            List<StardewBuildingBlueprint> ordered
    ) {
        Catalog {
            if (revision < 0) {
                throw new IllegalArgumentException(
                        "revision must be non-negative");
            }
            data = Map.copyOf(data);
            byId = Map.copyOf(byId);
            ordered = List.copyOf(ordered);
        }

        private static Catalog empty() {
            return new Catalog(
                    0, Map.of(), Map.of(), List.of());
        }
    }

    public record ReloadReport(
            long revision,
            int activeDataCount,
            boolean rejected,
            List<String> errors
    ) {
        public ReloadReport {
            errors = List.copyOf(errors);
        }
    }
}
