package com.stardew.craft.secretnote;

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
import com.stardew.craft.api.v1.secretnote.StardewSecretNoteDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic data-pack registry for ordinary secret notes. */
@SuppressWarnings("null")
public final class SecretNoteRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewSecretNoteDefinition> STORE = new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private SecretNoteRegistry() {}

    public static DefinitionSnapshot<StardewSecretNoteDefinition> snapshot() {
        return catalog.definitions();
    }

    public static StardewSecretNoteDefinition get(ResourceLocation id) {
        return catalog.notes().get(id);
    }

    public static ResourceLocation byVanillaNumber(int number) {
        return catalog.vanillaNumbers().get(number);
    }

    public static ResourceLocation byDisplayNumber(int number) {
        return catalog.displayNumbers().get(number);
    }

    public static List<Map.Entry<ResourceLocation, StardewSecretNoteDefinition>> orderedNotes() {
        return catalog.orderedNotes();
    }

    public static String getCachedJson() {
        return catalog.cachedJson();
    }

    /** Replays the committed server catalog on a dedicated client. */
    public static void applyFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                decode(id, id, entry.getValue(), definitions, sources, diagnostics);
            }
            applyDefinitions(definitions, sources, diagnostics, json, "client sync");
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[Secret notes] Failed client sync", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "secret_notes");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> decodeResource(entry.getKey(), entry.getValue(), definitions, sources, diagnostics));

            applyDefinitions(definitions, sources, diagnostics, null, "reload");
        }
    }

    private static synchronized void applyDefinitions(
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics,
            String sourceJson,
            String operation
    ) {
        Map<Integer, ResourceLocation> numberIndex = buildVanillaNumberIndex(definitions, diagnostics);
        Map<Integer, ResourceLocation> displayIndex = buildDisplayNumberIndex(definitions, diagnostics);
        List<Map.Entry<ResourceLocation, StardewSecretNoteDefinition>> ordered =
                definitions.entrySet().stream()
                        .sorted(Comparator
                                .comparingInt((Map.Entry<ResourceLocation, StardewSecretNoteDefinition> entry) ->
                                        entry.getValue().sortOrder())
                                .thenComparing(entry -> entry.getKey().toString()))
                        .map(entry -> Map.entry(
                                entry.getKey(), entry.getValue()))
                        .toList();
        String nextCachedJson = sourceJson;
        if (nextCachedJson == null && diagnostics.stream()
                .noneMatch(diagnostic -> diagnostic.severity()
                        == DefinitionDiagnostic.Severity.ERROR)) {
            try {
                nextCachedJson = encodeDefinitions(definitions);
            } catch (RuntimeException exception) {
                diagnostics.add(DefinitionDiagnostic.error(
                        null,
                        null,
                        "Failed to encode secret-note sync catalog: "
                                + exception.getMessage()));
            }
        }

        var result = STORE.applyLocal(
                definitions, sources, diagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error("[Secret notes] Rejected {}; keeping v{} with {} notes",
                    operation,
                    catalog.definitions().version(),
                    catalog.notes().size());
            return;
        }
        if (nextCachedJson == null) {
            throw new IllegalStateException(
                    "Accepted secret-note definitions have no sync catalog");
        }

        catalog = new Catalog(
                result.snapshot(),
                numberIndex,
                displayIndex,
                ordered,
                nextCachedJson);
        StardewCraft.LOGGER.info("[Secret notes] Applied {} v{} ({} notes)",
                operation,
                catalog.definitions().version(),
                catalog.notes().size());
    }

    private static void decodeResource(
            ResourceLocation source,
            JsonElement json,
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    diagnostics.add(DefinitionDiagnostic.error(source, null, "Array entry must be an object"));
                    continue;
                }
                JsonObject object = element.getAsJsonObject().deepCopy();
                String path = object.has("id") ? object.remove("id").getAsString() : "";
                ResourceLocation id = ResourceLocation.tryBuild(source.getNamespace(), path);
                decode(source, id, object, definitions, sources, diagnostics);
            }
            return;
        }

        String path = source.getPath();
        ResourceLocation id = ResourceLocation.tryBuild(source.getNamespace(), path);
        decode(source, id, json, definitions, sources, diagnostics);
    }

    private static void decode(
            ResourceLocation source,
            ResourceLocation id,
            JsonElement json,
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Missing or invalid secret-note ID"));
            return;
        }
        StardewSecretNoteDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> {
                    if (definitions.putIfAbsent(id, definition) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(source, id, "Duplicate secret-note ID"));
                        return;
                    }
                    StardewSecretNoteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static Map<Integer, ResourceLocation> buildVanillaNumberIndex(
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<Integer, ResourceLocation> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, StardewSecretNoteDefinition> entry : definitions.entrySet()) {
            int number = entry.getValue().vanillaNumber();
            if (number < 0) continue;
            ResourceLocation existing = result.putIfAbsent(number, entry.getKey());
            if (existing != null) {
                diagnostics.add(DefinitionDiagnostic.error(null, entry.getKey(),
                        "vanilla_number " + number + " is already used by " + existing));
            }
        }
        return result;
    }

    private static Map<Integer, ResourceLocation> buildDisplayNumberIndex(
            Map<ResourceLocation, StardewSecretNoteDefinition> definitions,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<Integer, ResourceLocation> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, StardewSecretNoteDefinition> entry : definitions.entrySet()) {
            int number = entry.getValue().displayNumber();
            if (number < 1) {
                diagnostics.add(DefinitionDiagnostic.error(null, entry.getKey(),
                        "display_number must be positive"));
                continue;
            }
            ResourceLocation existing = result.putIfAbsent(number, entry.getKey());
            if (existing != null) {
                diagnostics.add(DefinitionDiagnostic.error(null, entry.getKey(),
                        "display_number " + number + " is already used by " + existing));
            }
        }
        for (int expected = 1; expected <= definitions.size(); expected++) {
            if (!result.containsKey(expected)) {
                diagnostics.add(DefinitionDiagnostic.error(null, null,
                        "display_number sequence is missing " + expected));
            }
        }
        return result;
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            String source = diagnostic.source() == null ? "<secret-note reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Secret notes] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Secret notes] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
    }

    private static String encodeDefinitions(Map<ResourceLocation, StardewSecretNoteDefinition> definitions) {
        JsonObject root = new JsonObject();
        definitions.forEach((id, definition) -> StardewSecretNoteDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, definition)
                .resultOrPartial(message -> {
                    throw new IllegalArgumentException(
                            id + ": " + message);
                })
                .ifPresent(json -> root.add(id.toString(), json)));
        return GSON.toJson(root);
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewSecretNoteDefinition> definitions,
            Map<Integer, ResourceLocation> vanillaNumbers,
            Map<Integer, ResourceLocation> displayNumbers,
            List<Map.Entry<ResourceLocation, StardewSecretNoteDefinition>> orderedNotes,
            String cachedJson
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            vanillaNumbers = Map.copyOf(vanillaNumbers);
            displayNumbers = Map.copyOf(displayNumbers);
            orderedNotes = List.copyOf(orderedNotes);
            cachedJson = java.util.Objects.requireNonNull(
                    cachedJson, "cachedJson");
        }

        Map<ResourceLocation, StardewSecretNoteDefinition> notes() {
            return definitions.definitions();
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    "{}");
        }
    }
}
