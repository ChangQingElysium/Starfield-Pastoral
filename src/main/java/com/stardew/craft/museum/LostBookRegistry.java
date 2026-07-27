package com.stardew.craft.museum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.museum.StardewLostBookDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic registry for the museum's pre-1.6 lost-book collection. */
@SuppressWarnings("null")
public final class LostBookRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation BUILTIN_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "lost_books");
    private static final AtomicDefinitionStore<StardewLostBookDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private LostBookRegistry() {
    }

    public static DefinitionSnapshot<StardewLostBookDefinition> snapshot() {
        return catalog.definitions();
    }

    public static StardewLostBookDefinition get(ResourceLocation id) {
        return catalog.books().get(id);
    }

    public static ResourceLocation at(ResourceLocation dimension, BlockPos pos) {
        return catalog.interactions().get(
                new InteractionKey(dimension, pos.asLong()));
    }

    public static int discoveryMaximum() {
        return catalog.discoveryMaximum();
    }

    public static List<Map.Entry<ResourceLocation, StardewLostBookDefinition>> orderedBooks() {
        return catalog.orderedBooks();
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "museum");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewLostBookDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

            JsonElement builtin = objects.get(BUILTIN_TABLE);
            if (builtin == null || !builtin.isJsonArray()) {
                diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                        "Missing built-in lost-book table"));
            } else {
                for (JsonElement raw : builtin.getAsJsonArray()) {
                    if (!raw.isJsonObject()) {
                        diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                                "Built-in entry must be an object"));
                        continue;
                    }
                    JsonObject object = raw.getAsJsonObject().deepCopy();
                    String path = object.has("id") ? object.remove("id").getAsString() : "";
                    ResourceLocation id = ResourceLocation.tryBuild(StardewCraft.MODID, path);
                    decode(BUILTIN_TABLE, id, object, definitions, sources, diagnostics);
                }
            }

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("lost_books/"))
                    .forEach(entry -> {
                        String path = entry.getKey().getPath().substring("lost_books/".length());
                        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
                        decode(entry.getKey(), id, entry.getValue(), definitions, sources, diagnostics);
                    });

            applyCandidate(definitions, sources, diagnostics);
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewLostBookDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<InteractionKey, ResourceLocation> nextInteractions =
                buildInteractions(definitions, diagnostics);
        List<Map.Entry<ResourceLocation, StardewLostBookDefinition>> nextOrdered =
                definitions.entrySet().stream()
                        .sorted(Comparator.comparingInt(
                                        (Map.Entry<ResourceLocation, StardewLostBookDefinition> entry) ->
                                                entry.getValue().unlockAt())
                                .thenComparing(entry ->
                                        entry.getKey().toString()))
                        .map(entry -> Map.entry(
                                entry.getKey(), entry.getValue()))
                        .toList();
        int nextDiscoveryMaximum = Math.max(
                21,
                definitions.values().stream()
                        .mapToInt(StardewLostBookDefinition::unlockAt)
                        .max()
                        .orElse(0));

        var result = STORE.applyLocal(
                definitions, sources, diagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Lost books] Rejected snapshot; keeping v{} with {} books",
                    catalog.definitions().version(),
                    catalog.books().size());
            return;
        }

        catalog = new Catalog(
                result.snapshot(),
                nextInteractions,
                nextOrdered,
                nextDiscoveryMaximum);
        StardewCraft.LOGGER.info(
                "[Lost books] Applied snapshot v{} ({} books, {} interaction points)",
                catalog.definitions().version(),
                catalog.books().size(),
                catalog.interactions().size());
    }

    private static void decode(
            ResourceLocation source,
            ResourceLocation id,
            JsonElement json,
            Map<ResourceLocation, StardewLostBookDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Missing or invalid lost-book ID"));
            return;
        }
        StardewLostBookDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> {
                    if (definitions.putIfAbsent(id, definition) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(source, id, "Duplicate lost-book ID"));
                        return;
                    }
                    StardewLostBookDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(source, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static Map<InteractionKey, ResourceLocation> buildInteractions(
            Map<ResourceLocation, StardewLostBookDefinition> definitions,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<InteractionKey, ResourceLocation> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, StardewLostBookDefinition> entry : definitions.entrySet()) {
            for (StardewLostBookDefinition.Interaction interaction : entry.getValue().interactions()) {
                InteractionKey key = new InteractionKey(interaction.dimension(),
                        BlockPos.asLong(interaction.x(), interaction.y(), interaction.z()));
                ResourceLocation existing = result.putIfAbsent(key, entry.getKey());
                if (existing != null) {
                    diagnostics.add(DefinitionDiagnostic.error(null, entry.getKey(),
                            "Interaction point is already bound to " + existing));
                }
            }
        }
        return result;
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            String source = diagnostic.source() == null ? "<lost-book reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Lost books] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Lost books] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewLostBookDefinition> definitions,
            Map<InteractionKey, ResourceLocation> interactions,
            List<Map.Entry<ResourceLocation, StardewLostBookDefinition>> orderedBooks,
            int discoveryMaximum
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            interactions = Map.copyOf(interactions);
            orderedBooks = List.copyOf(orderedBooks);
            if (discoveryMaximum < 21) {
                throw new IllegalArgumentException(
                        "Lost-book discovery maximum must preserve the legacy minimum");
            }
        }

        Map<ResourceLocation, StardewLostBookDefinition> books() {
            return definitions.definitions();
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(),
                    Map.of(),
                    List.of(),
                    21);
        }
    }

    record InteractionKey(ResourceLocation dimension, long position) {
    }
}
