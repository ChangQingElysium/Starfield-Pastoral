package com.stardew.craft.interior;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.world.StardewLocationDefinition;
import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.api.v1.world.StardewLocationEnvironmentKeys;
import com.stardew.craft.world.WorldRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reloadable fixed-location lookup used by interiors, music, quests and NPC routing. */
public final class InteriorRegionRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation BUILTIN_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fixed_interiors");
    private static final AtomicDefinitionStore<StardewLocationDefinition> STORE = new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private InteriorRegionRegistry() {
    }

    public static DefinitionSnapshot<StardewLocationDefinition> snapshot() {
        return catalog.definitions();
    }

    public static Optional<InteriorRegion> fixedInteriorAt(BlockPos pos) {
        if (pos == null) return Optional.empty();
        return catalog.regions().stream()
                .filter(region -> region.contains(pos))
                .findFirst();
    }

    public static String fixedInteriorIdAt(BlockPos pos) {
        return fixedInteriorAt(pos).map(InteriorRegion::id).orElse("");
    }

    public static boolean isInFixedInterior(BlockPos pos) {
        return fixedInteriorAt(pos).isPresent();
    }

    public static String canonicalInteriorId(String idOrAlias) {
        if (idOrAlias == null || idOrAlias.isBlank()) return "";
        String normalized = normalize(idOrAlias);
        return catalog.interiorAliases()
                .getOrDefault(normalized, normalized);
    }

    public static boolean hasFixedInteriorAlias(String idOrAlias) {
        return idOrAlias != null
                && !idOrAlias.isBlank()
                && catalog.interiorAliases()
                        .containsKey(normalize(idOrAlias));
    }

    public static List<InteriorRegion> regions() {
        return catalog.regions();
    }

    public static Optional<StardewLocation> locationAt(
            ResourceLocation dimension,
            BlockPos pos
    ) {
        if (dimension == null || pos == null) {
            return Optional.empty();
        }
        return catalog.locations().stream()
                .filter(location -> {
                    if (WorldRegionRegistry.hasLocationGeometry(
                            location.id())) {
                        return WorldRegionRegistry.containsLocation(
                                location.id(), dimension, pos);
                    }
                    return location.contains(dimension, pos);
                })
                .findFirst();
    }

    public static Optional<StardewLocation> location(ResourceLocation id) {
        return Optional.ofNullable(id == null
                ? null : catalog.locationsById().get(id));
    }

    public static Optional<ResourceLocation> canonicalLocationId(String idOrAlias) {
        if (idOrAlias == null || idOrAlias.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation direct = ResourceLocation.tryParse(idOrAlias.trim());
        if (direct != null
                && catalog.locationsById().containsKey(direct)) {
            return Optional.of(direct);
        }
        return Optional.ofNullable(catalog.locationAliases()
                .get(normalize(idOrAlias)));
    }

    public static List<StardewLocation> locations() {
        return catalog.locations();
    }

    public static String getCachedJson() {
        return catalog.cachedJson();
    }

    public static void applyFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, StardewLocationDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                decode(id, id, entry.getValue(), definitions, sources, diagnostics);
            }
            validateCatalog(definitions, diagnostics);
            applyCandidate(
                    definitions,
                    sources,
                    diagnostics,
                    json,
                    "client sync");
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[Locations] Failed client sync", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "locations");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewLocationDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            JsonElement builtin = objects.get(BUILTIN_TABLE);
            if (builtin == null || !builtin.isJsonObject() || !builtin.getAsJsonObject().has("regions")) {
                diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                        "Missing built-in fixed-interior location table"));
            } else {
                for (JsonElement raw : builtin.getAsJsonObject().getAsJsonArray("regions")) {
                    if (!raw.isJsonObject()) continue;
                    JsonObject object = raw.getAsJsonObject().deepCopy();
                    if (!object.has("indoor")) {
                        object.addProperty("indoor", true);
                    }
                    String path = object.has("id") ? object.remove("id").getAsString() : "";
                    decode(BUILTIN_TABLE, ResourceLocation.tryBuild(StardewCraft.MODID, normalize(path)),
                            object, definitions, sources, diagnostics);
                }
            }
            objects.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(BUILTIN_TABLE))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> decode(entry.getKey(), entry.getKey(), entry.getValue(),
                            definitions, sources, diagnostics));
            validateCatalog(definitions, diagnostics);
            if (!applyCandidate(
                    definitions,
                    sources,
                    diagnostics,
                    encodeDefinitions(definitions),
                    "reload")) {
                return;
            }
            StardewCraft.LOGGER.info(
                    "[Locations] Applied snapshot v{} "
                            + "({} locations, {} indoor)",
                    catalog.definitions().version(),
                    catalog.locations().size(),
                    catalog.regions().size());
        }
    }

    private static synchronized boolean applyCandidate(
            Map<ResourceLocation, StardewLocationDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics,
            String cachedJson,
            String operation
    ) {
        List<DefinitionDiagnostic> preparedDiagnostics =
                new ArrayList<>(diagnostics);
        PreparedCatalog prepared = null;
        if (preparedDiagnostics.stream().noneMatch(diagnostic ->
                diagnostic.severity()
                        == DefinitionDiagnostic.Severity.ERROR)) {
            try {
                prepared = prepareCatalog(definitions);
            } catch (RuntimeException exception) {
                preparedDiagnostics.add(DefinitionDiagnostic.error(
                        null,
                        null,
                        "Failed to prepare location runtime catalog: "
                                + exception.getMessage()));
            }
        }
        var result = STORE.applyLocal(
                definitions, sources, preparedDiagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Locations] Rejected {}; keeping {} locations",
                    operation,
                    catalog.locations().size());
            return false;
        }
        if (prepared == null) {
            throw new IllegalStateException(
                    "Accepted location definitions have no runtime catalog");
        }
        catalog = new Catalog(
                result.snapshot(),
                prepared.regions(),
                prepared.interiorAliases(),
                prepared.locations(),
                prepared.locationsById(),
                prepared.locationAliases(),
                cachedJson);
        return true;
    }

    private static PreparedCatalog prepareCatalog(
            Map<ResourceLocation, StardewLocationDefinition> definitions
    ) {
        List<Map.Entry<ResourceLocation, StardewLocationDefinition>> ordered =
                definitions.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<ResourceLocation, StardewLocationDefinition>>comparingInt(
                                entry -> entry.getValue().priority()).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .toList();
        Map<ResourceLocation, StardewLocation> resolved =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, StardewLocationDefinition> entry
                : ordered) {
            resolveLocation(
                    entry.getKey(), definitions, resolved);
        }
        List<StardewLocation> preparedLocations = ordered.stream()
                .map(entry -> resolved.get(entry.getKey()))
                .toList();
        Map<ResourceLocation, StardewLocation> preparedById =
                new LinkedHashMap<>();
        Map<String, ResourceLocation> preparedLocationAliases =
                new LinkedHashMap<>();
        for (StardewLocation location : preparedLocations) {
            preparedById.put(location.id(), location);
            preparedLocationAliases.putIfAbsent(
                    normalize(location.id().toString()), location.id());
            if (location.id().getNamespace().equals(StardewCraft.MODID)) {
                preparedLocationAliases.putIfAbsent(
                        normalize(location.id().getPath()), location.id());
            }
            location.aliases().forEach(alias ->
                    preparedLocationAliases.putIfAbsent(
                            normalize(alias), location.id()));
        }

        List<InteriorRegion> prepared = ordered.stream()
                .filter(entry -> entry.getValue().indoor())
                .map(entry -> toRegion(entry.getKey(), entry.getValue()))
                .toList();
        Map<String, String> preparedAliases = new LinkedHashMap<>();
        for (InteriorRegion region : prepared) {
            preparedAliases.putIfAbsent(normalize(region.id()), region.id());
            region.aliases().forEach(alias ->
                    preparedAliases.putIfAbsent(normalize(alias), region.id()));
        }
        return new PreparedCatalog(
                List.copyOf(prepared),
                Map.copyOf(preparedAliases),
                List.copyOf(preparedLocations),
                Map.copyOf(preparedById),
                Map.copyOf(preparedLocationAliases));
    }

    private static String encodeDefinitions(Map<ResourceLocation, StardewLocationDefinition> definitions) {
        JsonObject root = new JsonObject();
        definitions.forEach((id, definition) -> StardewLocationDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, definition).result().ifPresent(json -> root.add(id.toString(), json)));
        return GSON.toJson(root);
    }

    private static void decode(ResourceLocation source, ResourceLocation id, JsonElement json,
                               Map<ResourceLocation, StardewLocationDefinition> definitions,
                               Map<ResourceLocation, String> sources,
                               List<DefinitionDiagnostic> diagnostics) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Missing or invalid location ID"));
            return;
        }
        StardewLocationDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> {
                    definitions.put(id, definition);
                    StardewLocationDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static InteriorRegion toRegion(ResourceLocation id, StardewLocationDefinition definition) {
        String runtimeId = id.getNamespace().equals(StardewCraft.MODID) ? id.getPath() : id.toString();
        return new InteriorRegion(runtimeId, definition.ledgerId(),
                definition.min().x(), definition.min().y(), definition.min().z(),
                definition.max().x(), definition.max().y(), definition.max().z(),
                definition.aliases());
    }

    private static StardewLocation toLocation(
            ResourceLocation id,
            StardewLocationDefinition definition,
            StardewLocation parent
    ) {
        LinkedHashSet<ResourceLocation> tags =
                new LinkedHashSet<>();
        LinkedHashMap<ResourceLocation, String> properties =
                new LinkedHashMap<>();
        if (parent != null) {
            tags.addAll(parent.tags());
            properties.putAll(parent.properties());
        }
        tags.remove(StardewLocationEnvironmentKeys.INDOOR);
        tags.remove(StardewLocationEnvironmentKeys.OUTDOOR);
        tags.add(definition.indoor()
                ? StardewLocationEnvironmentKeys.INDOOR
                : StardewLocationEnvironmentKeys.OUTDOOR);
        tags.addAll(definition.tags());
        properties.putAll(definition.properties());
        Component displayName = definition.displayName()
                .getString().isBlank()
                ? defaultDisplayName(id)
                : definition.displayName();
        return new StardewLocation(
                id,
                definition.dimension(),
                new BlockPos(
                        definition.min().x(),
                        definition.min().y(),
                        definition.min().z()),
                new BlockPos(
                        definition.max().x(),
                        definition.max().y(),
                        definition.max().z()),
                definition.ledgerId(),
                definition.aliases(),
                definition.priority(),
                definition.indoor(),
                definition.parentId(),
                displayName,
                definition.description(),
                definition.iconTexture(),
                tags,
                properties);
    }

    private static StardewLocation resolveLocation(
            ResourceLocation id,
            Map<ResourceLocation, StardewLocationDefinition> definitions,
            Map<ResourceLocation, StardewLocation> resolved
    ) {
        StardewLocation existing = resolved.get(id);
        if (existing != null) {
            return existing;
        }
        StardewLocationDefinition definition = definitions.get(id);
        StardewLocation parent = definition.parentId() == null
                ? null
                : resolveLocation(
                        definition.parentId(), definitions, resolved);
        StardewLocation location =
                toLocation(id, definition, parent);
        resolved.put(id, location);
        return location;
    }

    private static Component defaultDisplayName(ResourceLocation id) {
        if (id.getNamespace().equals(StardewCraft.MODID)) {
            return Component.translatable(
                    "stardewcraft.location." + id.getPath());
        }
        return Component.literal(id.toString());
    }

    private static void validateCatalog(
            Map<ResourceLocation, StardewLocationDefinition> definitions,
            List<DefinitionDiagnostic> diagnostics
    ) {
        for (Map.Entry<ResourceLocation, StardewLocationDefinition> entry
                : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            ResourceLocation parent = entry.getValue().parentId();
            com.stardew.craft.world.LocationMusicEnvironment
                    .validate(id, entry.getValue(),
                            parentProvidesMusicProfile(
                                    parent, definitions))
                    .ifPresent(message -> diagnostics.add(
                            DefinitionDiagnostic.error(
                                    id, id, message)));
            if (id.equals(parent)) {
                diagnostics.add(DefinitionDiagnostic.error(
                        id, id, "Location cannot be its own parent"));
            } else if (parent != null
                    && !definitions.containsKey(parent)) {
                diagnostics.add(DefinitionDiagnostic.error(
                        id, id, "Unknown parent location " + parent));
            }
        }
        for (ResourceLocation id : definitions.keySet()) {
            LinkedHashSet<ResourceLocation> path =
                    new LinkedHashSet<>();
            ResourceLocation cursor = id;
            while (cursor != null && definitions.containsKey(cursor)) {
                if (!path.add(cursor)) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            id, id,
                            "Location parent cycle: " + path
                                    + " -> " + cursor));
                    break;
                }
                cursor = definitions.get(cursor).parentId();
            }
        }

        LinkedHashMap<String, ResourceLocation> claimedAliases =
                new LinkedHashMap<>();
        definitions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = entry.getKey();
                    claimAlias(claimedAliases, normalize(id.toString()),
                            id, diagnostics);
                    if (id.getNamespace().equals(StardewCraft.MODID)) {
                        claimAlias(claimedAliases,
                                normalize(id.getPath()),
                                id, diagnostics);
                    }
                    entry.getValue().aliases().forEach(alias ->
                            claimAlias(claimedAliases, normalize(alias),
                                    id, diagnostics));
                });
    }

    private static boolean parentProvidesMusicProfile(
            ResourceLocation parent,
            Map<ResourceLocation, StardewLocationDefinition> definitions
    ) {
        LinkedHashSet<ResourceLocation> visited =
                new LinkedHashSet<>();
        ResourceLocation cursor = parent;
        while (cursor != null && visited.add(cursor)) {
            StardewLocationDefinition definition =
                    definitions.get(cursor);
            if (definition == null) {
                return false;
            }
            if (definition.properties().containsKey(
                    StardewLocationEnvironmentKeys.MUSIC_PROFILE)) {
                return true;
            }
            cursor = definition.parentId();
        }
        return false;
    }

    private static void claimAlias(
            Map<String, ResourceLocation> claimed,
            String alias,
            ResourceLocation id,
            List<DefinitionDiagnostic> diagnostics
    ) {
        ResourceLocation previous = claimed.putIfAbsent(alias, id);
        if (previous != null && !previous.equals(id)) {
            diagnostics.add(DefinitionDiagnostic.error(
                    id, id, "Location alias '" + alias
                            + "' conflicts with " + previous));
        }
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Locations] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Locations] {}", diagnostic.message());
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewLocationDefinition> definitions,
            List<InteriorRegion> regions,
            Map<String, String> interiorAliases,
            List<StardewLocation> locations,
            Map<ResourceLocation, StardewLocation> locationsById,
            Map<String, ResourceLocation> locationAliases,
            String cachedJson
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            regions = List.copyOf(regions);
            interiorAliases = Map.copyOf(interiorAliases);
            locations = List.copyOf(locations);
            locationsById = Map.copyOf(locationsById);
            locationAliases = Map.copyOf(locationAliases);
            cachedJson = java.util.Objects.requireNonNull(
                    cachedJson, "cachedJson");
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(),
                    List.of(),
                    Map.of(),
                    List.of(),
                    Map.of(),
                    Map.of(),
                    "{}");
        }
    }

    private record PreparedCatalog(
            List<InteriorRegion> regions,
            Map<String, String> interiorAliases,
            List<StardewLocation> locations,
            Map<ResourceLocation, StardewLocation> locationsById,
            Map<String, ResourceLocation> locationAliases
    ) {
    }

    public record InteriorRegion(String id, String ledgerId, int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ, List<String> aliases) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
