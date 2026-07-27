package com.stardew.craft.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic reload store for {@code data/<namespace>/regions/*.json}. */
public final class WorldRegionRegistry {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static volatile Catalog catalog = Catalog.empty();

    private WorldRegionRegistry() {
    }

    public static Optional<StardewRegion> get(ResourceLocation id) {
        return Optional.ofNullable(
                id == null ? null : catalog.byId().get(id));
    }

    public static Optional<StardewRegion> find(
            ResourceLocation dimension,
            BlockPos position
    ) {
        return findAll(dimension, position).stream().findFirst();
    }

    public static List<StardewRegion> findAll(
            ResourceLocation dimension,
            BlockPos position
    ) {
        if (dimension == null || position == null) {
            return List.of();
        }
        return catalog.ordered().stream()
                .filter(region -> region.contains(dimension, position))
                .toList();
    }

    public static List<StardewRegion> all() {
        return catalog.ordered();
    }

    public static List<StardewRegion> withTag(ResourceLocation tag) {
        if (tag == null) {
            return List.of();
        }
        return catalog.ordered().stream()
                .filter(region -> region.hasTag(tag))
                .toList();
    }

    public static List<StardewRegion> forLocation(
            ResourceLocation locationId
    ) {
        return locationId == null
                ? List.of()
                : catalog.byLocation()
                        .getOrDefault(locationId, List.of());
    }

    public static boolean hasLocationGeometry(
            ResourceLocation locationId
    ) {
        return locationId != null
                && catalog.byLocation().containsKey(locationId);
    }

    public static boolean containsLocation(
            ResourceLocation locationId,
            ResourceLocation dimension,
            BlockPos position
    ) {
        return forLocation(locationId).stream()
                .anyMatch(region -> region.contains(dimension, position));
    }

    static synchronized void publish(
            Map<ResourceLocation, StardewRegion> regions
    ) {
        List<StardewRegion> prepared = regions.values().stream()
                .sorted(Comparator.comparingInt(StardewRegion::priority)
                        .reversed()
                        .thenComparing(region -> region.id().toString()))
                .toList();
        LinkedHashMap<ResourceLocation, StardewRegion> preparedById =
                new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, List<StardewRegion>>
                preparedByLocation = new LinkedHashMap<>();
        for (StardewRegion region : prepared) {
            preparedById.put(region.id(), region);
            if (region.locationId() != null) {
                preparedByLocation.computeIfAbsent(
                        region.locationId(),
                        ignored -> new ArrayList<>()).add(region);
            }
        }
        LinkedHashMap<ResourceLocation, List<StardewRegion>>
                immutableByLocation = new LinkedHashMap<>();
        preparedByLocation.forEach((id, values) ->
                immutableByLocation.put(id, List.copyOf(values)));
        catalog = new Catalog(
                catalog.revision() + 1,
                preparedById,
                prepared,
                immutableByLocation);
    }

    static Catalog catalog() {
        return catalog;
    }

    static StardewRegion decode(
            ResourceLocation id,
            JsonElement raw
    ) {
        if (raw == null || !raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "region definition must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        ResourceLocation dimension = object.has("dimension")
                ? readId(object.get("dimension"), "dimension",
                        id.getNamespace())
                : ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "stardew_valley");
        ResourceLocation locationId = object.has("location")
                ? readId(object.get("location"), "location",
                        id.getNamespace())
                : null;
        List<StardewRegion.Box> includes = readBoxes(
                object.get("include"), "include", true);
        List<StardewRegion.Box> excludes = readBoxes(
                object.get("exclude"), "exclude", false);
        Set<ResourceLocation> tags = readIds(
                object.get("tags"), "tags", id.getNamespace());
        int priority = object.has("priority")
                ? readInteger(object.get("priority"), "priority")
                : 0;
        return new StardewRegion(
                id, dimension, locationId, includes,
                excludes, tags, priority);
    }

    private static List<StardewRegion.Box> readBoxes(
            JsonElement raw,
            String field,
            boolean required
    ) {
        if (raw == null || raw.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException(
                        "missing " + field);
            }
            return List.of();
        }
        JsonArray values;
        if (raw.isJsonObject()) {
            values = new JsonArray();
            values.add(raw);
        } else if (raw.isJsonArray()) {
            values = raw.getAsJsonArray();
        } else {
            throw new IllegalArgumentException(
                    field + " must be a box or box array");
        }
        ArrayList<StardewRegion.Box> boxes = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonElement element = values.get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        field + "[" + index + "] must be an object");
            }
            JsonObject box = element.getAsJsonObject();
            boxes.add(new StardewRegion.Box(
                    readPos(box.get("min"),
                            field + "[" + index + "].min"),
                    readPos(box.get("max"),
                            field + "[" + index + "].max")));
        }
        if (required && boxes.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " must contain at least one box");
        }
        return List.copyOf(boxes);
    }

    private static BlockPos readPos(JsonElement raw, String field) {
        if (raw == null || !raw.isJsonArray()
                || raw.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(
                    field + " must contain exactly three integers");
        }
        JsonArray values = raw.getAsJsonArray();
        return new BlockPos(
                readInteger(values.get(0), field + "[0]"),
                readInteger(values.get(1), field + "[1]"),
                readInteger(values.get(2), field + "[2]"));
    }

    private static int readInteger(JsonElement raw, String field) {
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    field + " must be an integer");
        }
        double value = raw.getAsDouble();
        if (!Double.isFinite(value)
                || Math.rint(value) != value
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    field + " must be a 32-bit integer");
        }
        return (int) value;
    }

    private static Set<ResourceLocation> readIds(
            JsonElement raw,
            String field,
            String defaultNamespace
    ) {
        if (raw == null || raw.isJsonNull()) {
            return Set.of();
        }
        if (!raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    field + " must be an array");
        }
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement element : raw.getAsJsonArray()) {
            result.add(readId(element, field, defaultNamespace));
        }
        return Set.copyOf(result);
    }

    private static ResourceLocation readId(
            JsonElement raw,
            String field,
            String defaultNamespace
    ) {
        if (raw == null || !raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    field + " must contain resource ID strings");
        }
        String value = raw.getAsString().trim();
        ResourceLocation id = ResourceLocation.tryParse(
                value.indexOf(':') >= 0
                        ? value
                        : defaultNamespace + ":" + value);
        if (id == null) {
            throw new IllegalArgumentException(
                    "invalid " + field + " ID: " + value);
        }
        return id;
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "regions");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            LinkedHashMap<ResourceLocation, StardewRegion> candidate =
                    new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        try {
                            candidate.put(entry.getKey(), decode(
                                    entry.getKey(), entry.getValue()));
                        } catch (RuntimeException exception) {
                            errors.add(entry.getKey() + ": "
                                    + exception.getMessage());
                        }
                    });
            if (!errors.isEmpty()) {
                errors.forEach(error -> StardewCraft.LOGGER.error(
                        "[WorldRegions] {}", error));
                StardewCraft.LOGGER.error(
                        "[WorldRegions] Rejected reload; keeping {} regions",
                        catalog.ordered().size());
                return;
            }
            publish(candidate);
            StardewCraft.LOGGER.info(
                    "[WorldRegions] Applied snapshot v{} ({} regions)",
                    catalog.revision(),
                    catalog.ordered().size());
        }
    }

    record Catalog(
            long revision,
            Map<ResourceLocation, StardewRegion> byId,
            List<StardewRegion> ordered,
            Map<ResourceLocation, List<StardewRegion>> byLocation
    ) {
        Catalog {
            if (revision < 0) {
                throw new IllegalArgumentException(
                        "revision must be non-negative");
            }
            byId = Map.copyOf(byId);
            ordered = List.copyOf(ordered);
            LinkedHashMap<ResourceLocation, List<StardewRegion>>
                    frozenByLocation = new LinkedHashMap<>();
            byLocation.forEach((id, values) ->
                    frozenByLocation.put(id, List.copyOf(values)));
            byLocation = Map.copyOf(frozenByLocation);
        }

        private static Catalog empty() {
            return new Catalog(0, Map.of(), List.of(), Map.of());
        }
    }
}
