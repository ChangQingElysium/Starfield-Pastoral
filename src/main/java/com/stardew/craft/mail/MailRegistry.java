package com.stardew.craft.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.mail.StardewMailDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic namespaced mail registry with an adapter for the original array files. */
@SuppressWarnings("null")
public final class MailRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewMailDefinition> STORE = new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private MailRegistry() {
    }

    @Nullable
    public static MailEntry get(String mailId) {
        ResourceLocation id = normalizeId(mailId);
        if (id == null) return null;
        Catalog current = catalog;
        StardewMailDefinition definition =
                current.definitions().definitions().get(id);
        return definition == null
                ? null
                : new MailEntry(
                        id, displayId(current, id), definition);
    }

    @Nullable
    public static StardewMailDefinition getDefinition(ResourceLocation id) {
        return catalog.definitions().definitions().get(id);
    }

    public static Collection<MailEntry> getAll() {
        Catalog current = catalog;
        return current.definitions().definitions().entrySet().stream()
                .map(entry -> new MailEntry(
                        entry.getKey(),
                        displayId(current, entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    public static boolean contains(String mailId) {
        ResourceLocation id = normalizeId(mailId);
        return id != null
                && catalog.definitions().definitions().containsKey(id);
    }

    public static DefinitionSnapshot<StardewMailDefinition> snapshot() {
        return catalog.definitions();
    }

    @Nullable
    public static ResourceLocation normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        return rawId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(rawId)
                : ResourceLocation.tryBuild(StardewCraft.MODID, legacyPath(rawId));
    }

    static String displayId(ResourceLocation id) {
        return displayId(catalog, id);
    }

    private static String displayId(Catalog catalog, ResourceLocation id) {
        return catalog.displayIds().getOrDefault(id,
                StardewCraft.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString());
    }

    private static Candidate parseCandidate(Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, StardewMailDefinition> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        Map<ResourceLocation, String> displayIds = new LinkedHashMap<>();
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> resource : resources.entrySet()) {
            JsonElement root = resource.getValue();
            if (root == null || (!root.isJsonObject() && !root.isJsonArray())) {
                diagnostics.add(DefinitionDiagnostic.error(
                        resource.getKey(), null, "Mail resource must be an object or array"));
                continue;
            }
            JsonArray entries = root.isJsonArray() ? root.getAsJsonArray() : singleton(root);
            int index = 0;
            for (JsonElement element : entries) {
                parseEntry(resource.getKey(), index++, element, definitions, sources, displayIds, diagnostics);
            }
        }
        return new Candidate(definitions, sources, displayIds, diagnostics);
    }

    private static void parseEntry(
            ResourceLocation source,
            int index,
            JsonElement element,
            Map<ResourceLocation, StardewMailDefinition> definitions,
            Map<ResourceLocation, String> sources,
            Map<ResourceLocation, String> displayIds,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (!element.isJsonObject()) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Mail entry #" + index + " must be an object"));
            return;
        }
        JsonObject raw = element.getAsJsonObject();
        ResourceLocation id = resolveId(source, raw);
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Mail entry #" + index + " has an invalid ID"));
            return;
        }
        JsonObject normalized = normalizeLegacyFields(raw);
        StardewMailDefinition.CODEC.parse(JsonOps.INSTANCE, normalized)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> {
                    String displayId = resolvedDisplayId(source, raw);
                    String previousDisplayId = displayIds.get(id);
                    if (definitions.put(id, definition) != null) {
                        String detail = previousDisplayId != null && !previousDisplayId.equals(displayId)
                                ? " after canonicalizing legacy IDs '" + previousDisplayId + "' and '" + displayId + "'"
                                : "";
                        diagnostics.add(DefinitionDiagnostic.error(
                                source, id, "Duplicate mail definition" + detail));
                    }
                    sources.put(id, GSON.toJson(normalized));
                    displayIds.put(id, displayId);
                });
    }

    @Nullable
    private static ResourceLocation resolveId(ResourceLocation source, JsonObject raw) {
        if (!raw.has("id")) {
            return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), source.getPath());
        }
        String value = raw.get("id").getAsString();
        if (value.indexOf(':') >= 0) return ResourceLocation.tryParse(value);
        return StardewCraft.MODID.equals(source.getNamespace())
                ? ResourceLocation.tryBuild(source.getNamespace(), legacyPath(value))
                : ResourceLocation.tryBuild(source.getNamespace(), value);
    }

    private static String resolvedDisplayId(ResourceLocation source, JsonObject raw) {
        if (!raw.has("id")) return source.toString();
        String value = raw.get("id").getAsString();
        return value.indexOf(':') >= 0 || StardewCraft.MODID.equals(source.getNamespace())
                ? value : source.getNamespace() + ":" + value;
    }

    private static String legacyPath(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static JsonObject normalizeLegacyFields(JsonObject raw) {
        JsonObject result = raw.deepCopy();
        result.remove("id");
        alias(result, "customBgTexture", "custom_background_texture");
        alias(result, "textColor", "text_color");
        alias(result, "attachedItems", "attached_items");
        alias(result, "learnedRecipe", "learned_recipe");
        alias(result, "recipeIsCooking", "recipe_is_cooking");
        aliasNamespacedId(result, "questId", "quest");
        aliasNamespacedId(result, "specialOrderId", "special_order");
        return result;
    }

    private static void alias(JsonObject object, String legacy, String modern) {
        if (!object.has(modern) && object.has(legacy)) object.add(modern, object.get(legacy));
        object.remove(legacy);
    }

    private static void aliasNamespacedId(JsonObject object, String legacy, String modern) {
        alias(object, legacy, modern);
        if (!object.has(modern) || object.get(modern).isJsonNull()) return;
        String value = object.get(modern).getAsString();
        if (value.indexOf(':') < 0) {
            object.addProperty(modern, StardewCraft.MODID + ":" + value);
        }
    }

    private static JsonArray singleton(JsonElement element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    static synchronized void applyCandidate(Candidate candidate) {
        var result = STORE.applyLocal(
                candidate.definitions(),
                candidate.sources(),
                candidate.diagnostics());
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            String source = diagnostic.source() == null ? "<mail reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Mail] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Mail] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error("[Mail] Rejected datapack snapshot; keeping v{} with {} definitions",
                    catalog.definitions().version(),
                    catalog.definitions().definitions().size());
            return;
        }
        if (result.changed()) {
            catalog = new Catalog(
                    result.snapshot(), candidate.displayIds());
        }
        StardewCraft.LOGGER.info("[Mail] Applied snapshot v{} ({} definitions)",
                catalog.definitions().version(),
                catalog.definitions().definitions().size());
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "mail");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Candidate candidate = parseCandidate(objects);
            applyCandidate(candidate);
        }
    }

    record Candidate(
            Map<ResourceLocation, StardewMailDefinition> definitions,
            Map<ResourceLocation, String> sources,
            Map<ResourceLocation, String> displayIds,
            List<DefinitionDiagnostic> diagnostics
    ) {
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewMailDefinition> definitions,
            Map<ResourceLocation, String> displayIds
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            displayIds = Map.copyOf(displayIds);
        }

        private static Catalog empty() {
            return new Catalog(DefinitionSnapshot.empty(), Map.of());
        }
    }
}
