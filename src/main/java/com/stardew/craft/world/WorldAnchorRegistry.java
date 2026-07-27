package com.stardew.craft.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewWorldAnchor;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic reload store for {@code data/<namespace>/anchors/*.json}. */
public final class WorldAnchorRegistry {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static volatile Catalog catalog = Catalog.empty();
    private static Map<ResourceLocation, StardewWorldAnchor> dataAnchors =
            Map.of();
    private static Map<ResourceLocation, StardewWorldAnchor>
            legacyNpcAnchors = Map.of();
    private static volatile ReloadReport lastReport =
            new ReloadReport(0, 0, false, List.of());

    private WorldAnchorRegistry() {
    }

    public static Optional<StardewWorldAnchor> get(ResourceLocation id) {
        return Optional.ofNullable(
                id == null ? null : catalog.byId().get(id));
    }

    public static Optional<StardewWorldAnchor> resolve(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawId.trim().toLowerCase(java.util.Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(
                normalized.indexOf(':') >= 0
                        ? normalized
                        : StardewCraft.MODID + ":" + normalized);
        return get(id);
    }

    public static List<StardewWorldAnchor> all() {
        return catalog.ordered();
    }

    public static List<StardewWorldAnchor> withRole(ResourceLocation role) {
        if (role == null) {
            return List.of();
        }
        return catalog.ordered().stream()
                .filter(anchor -> anchor.hasRole(role))
                .toList();
    }

    public static ReloadReport lastReport() {
        return lastReport;
    }

    static synchronized void publish(
            Map<ResourceLocation, StardewWorldAnchor> anchors
    ) {
        dataAnchors = Map.copyOf(anchors);
        rebuild();
    }

    public static synchronized void replaceLegacyNpcAnchors(
            Map<ResourceLocation, StardewWorldAnchor> anchors
    ) {
        legacyNpcAnchors = Map.copyOf(
                anchors == null ? Map.of() : anchors);
        rebuild();
    }

    private static void rebuild() {
        LinkedHashMap<ResourceLocation, StardewWorldAnchor> prepared =
                new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, StardewWorldAnchor> combined =
                new LinkedHashMap<>(legacyNpcAnchors);
        combined.putAll(dataAnchors);
        combined.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> prepared.put(
                        entry.getKey(), entry.getValue()));
        catalog = new Catalog(
                catalog.revision() + 1,
                prepared,
                List.copyOf(prepared.values()));
    }

    static Catalog catalog() {
        return catalog;
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "anchors");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            LinkedHashMap<ResourceLocation, StardewWorldAnchor> candidate =
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
            for (StardewWorldAnchor anchor : candidate.values()) {
                if (anchor.locationId() != null
                        && StardewLocations.get(
                                anchor.locationId()).isEmpty()) {
                    errors.add(anchor.id()
                            + ": unknown location "
                            + anchor.locationId());
                }
            }
            if (!errors.isEmpty()) {
                errors.forEach(error -> StardewCraft.LOGGER.error(
                        "[WorldAnchors] {}", error));
                StardewCraft.LOGGER.error(
                        "[WorldAnchors] Rejected reload; keeping {} anchors",
                        catalog.ordered().size());
                lastReport = new ReloadReport(
                        lastReport.revision(),
                        dataAnchors.size(),
                        true,
                        errors);
                return;
            }
            publish(candidate);
            lastReport = new ReloadReport(
                    lastReport.revision() + 1,
                    candidate.size(),
                    false,
                    List.of());
            StardewCraft.LOGGER.info(
                    "[WorldAnchors] Applied snapshot v{} ({} anchors)",
                    catalog.revision(),
                    catalog.ordered().size());
        }
    }

    static StardewWorldAnchor decode(
            ResourceLocation id,
            JsonElement raw
    ) {
        if (!raw.isJsonObject()) {
            throw new IllegalArgumentException(
                    "anchor definition must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        rejectUnknownFields(object, Set.of(
                "dimension", "position", "yaw", "indoor",
                "use_ground_height", "location", "roles"));
        ResourceLocation dimension = readId(
                object, "dimension",
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "stardew_valley"));
        Vec3 position = readPosition(object.get("position"));
        float yaw = object.has("yaw")
                ? readFloat(object.get("yaw"), "yaw") : 0.0F;
        boolean indoor = object.has("indoor")
                && readBoolean(object.get("indoor"), "indoor");
        boolean useGroundHeight = object.has("use_ground_height")
                ? readBoolean(object.get("use_ground_height"),
                        "use_ground_height")
                : !indoor;
        ResourceLocation locationId = object.has("location")
                ? readId(object, "location", null) : null;
        Set<ResourceLocation> roles = readRoles(object.get("roles"));
        return new StardewWorldAnchor(
                id, dimension, position, yaw, indoor,
                useGroundHeight, locationId, roles);
    }

    private static Vec3 readPosition(JsonElement raw) {
        if (raw == null || !raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    "position must be a three-number array");
        }
        JsonArray values = raw.getAsJsonArray();
        if (values.size() != 3) {
            throw new IllegalArgumentException(
                    "position must contain exactly three numbers");
        }
        return new Vec3(
                readDouble(values.get(0), "position[0]"),
                readDouble(values.get(1), "position[1]"),
                readDouble(values.get(2), "position[2]"));
    }

    private static Set<ResourceLocation> readRoles(JsonElement raw) {
        if (raw == null) {
            return Set.of();
        }
        if (!raw.isJsonArray()) {
            throw new IllegalArgumentException(
                    "roles must be an array");
        }
        LinkedHashSet<ResourceLocation> roles = new LinkedHashSet<>();
        for (JsonElement element : raw.getAsJsonArray()) {
            if (!element.isJsonPrimitive()
                    || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "roles must contain string IDs");
            }
            ResourceLocation role = ResourceLocation.tryParse(
                    element.getAsString());
            if (role == null) {
                throw new IllegalArgumentException(
                        "invalid role ID: " + element);
            }
            roles.add(role);
        }
        if (roles.size() > 64) {
            throw new IllegalArgumentException(
                    "anchor has more than 64 roles");
        }
        return java.util.Collections.unmodifiableSet(roles);
    }

    private static ResourceLocation readId(
            JsonObject object,
            String key,
            ResourceLocation fallback
    ) {
        if (!object.has(key)) {
            if (fallback != null) {
                return fallback;
            }
            throw new IllegalArgumentException("missing " + key);
        }
        JsonElement raw = object.get(key);
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    key + " must be a string ID");
        }
        ResourceLocation id = ResourceLocation.tryParse(
                raw.getAsString());
        if (id == null) {
            throw new IllegalArgumentException(
                    "invalid " + key + " ID");
        }
        return id;
    }

    private static void rejectUnknownFields(
            JsonObject object,
            Set<String> allowed
    ) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(
                        "unknown anchor field: " + field);
            }
        }
    }

    private static boolean readBoolean(
            JsonElement raw,
            String field
    ) {
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    field + " must be a boolean");
        }
        return raw.getAsBoolean();
    }

    private static float readFloat(
            JsonElement raw,
            String field
    ) {
        double value = readDouble(raw, field);
        if (value < -Float.MAX_VALUE || value > Float.MAX_VALUE) {
            throw new IllegalArgumentException(
                    field + " must fit a finite float");
        }
        return (float) value;
    }

    private static double readDouble(
            JsonElement raw,
            String field
    ) {
        if (!raw.isJsonPrimitive()
                || !raw.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    field + " must be a number");
        }
        double value = raw.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    field + " must be finite");
        }
        return value;
    }

    public record ReloadReport(
            long revision,
            int activeDataCount,
            boolean rejected,
            List<String> errors
    ) {
        public ReloadReport {
            if (revision < 0 || activeDataCount < 0) {
                throw new IllegalArgumentException(
                        "Invalid world anchor reload report");
            }
            errors = List.copyOf(errors);
        }
    }

    record Catalog(
            long revision,
            Map<ResourceLocation, StardewWorldAnchor> byId,
            List<StardewWorldAnchor> ordered
    ) {
        Catalog {
            if (revision < 0) {
                throw new IllegalArgumentException(
                        "revision must be non-negative");
            }
            byId = Map.copyOf(byId);
            ordered = List.copyOf(ordered);
        }

        private static Catalog empty() {
            return new Catalog(0, Map.of(), List.of());
        }
    }
}
