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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static volatile List<InteriorRegion> regions = List.of();
    private static volatile Map<String, String> aliases = Map.of();
    private static volatile String cachedJson = "{}";

    private InteriorRegionRegistry() {
    }

    public static DefinitionSnapshot<StardewLocationDefinition> snapshot() {
        return STORE.snapshot();
    }

    public static Optional<InteriorRegion> fixedInteriorAt(BlockPos pos) {
        if (pos == null) return Optional.empty();
        return regions.stream().filter(region -> region.contains(pos)).findFirst();
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
        return aliases.getOrDefault(normalized, normalized);
    }

    public static boolean hasFixedInteriorAlias(String idOrAlias) {
        return idOrAlias != null && !idOrAlias.isBlank() && aliases.containsKey(normalize(idOrAlias));
    }

    public static List<InteriorRegion> regions() {
        return regions;
    }

    public static String getCachedJson() {
        return cachedJson;
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
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            logDiagnostics(result.diagnostics());
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Locations] Rejected client sync; keeping {} locations", regions.size());
                return;
            }
            publish(result.snapshot().definitions());
            cachedJson = json;
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
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            logDiagnostics(result.diagnostics());
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Locations] Rejected reload; keeping {} locations", regions.size());
                return;
            }
            publish(result.snapshot().definitions());
            cachedJson = encodeDefinitions(result.snapshot().definitions());
            StardewCraft.LOGGER.info("[Locations] Applied {} locations", regions.size());
        }
    }

    private static void publish(Map<ResourceLocation, StardewLocationDefinition> definitions) {
        List<InteriorRegion> prepared = definitions.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<ResourceLocation, StardewLocationDefinition>>comparingInt(
                                entry -> entry.getValue().priority()).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(entry -> toRegion(entry.getKey(), entry.getValue()))
                .toList();
        Map<String, String> preparedAliases = new LinkedHashMap<>();
        for (InteriorRegion region : prepared) {
            preparedAliases.put(normalize(region.id()), region.id());
            region.aliases().forEach(alias -> preparedAliases.put(normalize(alias), region.id()));
        }
        regions = List.copyOf(prepared);
        aliases = Map.copyOf(preparedAliases);
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

    public record InteriorRegion(String id, String ledgerId, int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ, List<String> aliases) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
