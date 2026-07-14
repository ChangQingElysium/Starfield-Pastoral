package com.stardew.craft.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Atomic, namespaced unlock bundles with aliases for the original multi-colon source IDs. */
@SuppressWarnings("null")
public final class UnlockSourceData {
    public record UnlockBundle(List<String> recipes, List<String> wallpapers, List<String> floorings) {
        public static final UnlockBundle EMPTY = new UnlockBundle(List.of(), List.of(), List.of());
        public static final Codec<UnlockBundle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("recipes", List.of()).forGetter(UnlockBundle::recipes),
                Codec.STRING.listOf().optionalFieldOf("wallpapers", List.of()).forGetter(UnlockBundle::wallpapers),
                Codec.STRING.listOf().optionalFieldOf("floorings", List.of()).forGetter(UnlockBundle::floorings)
        ).apply(instance, UnlockBundle::new));

        public UnlockBundle {
            recipes = distinct(recipes);
            wallpapers = distinct(wallpapers);
            floorings = distinct(floorings);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation LEGACY_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "unlock_sources");
    private static final AtomicDefinitionStore<UnlockBundle> STORE = new AtomicDefinitionStore<>();
    private static volatile Map<ResourceLocation, UnlockBundle> sources = Map.of();
    private static volatile String cachedJson = "";

    private UnlockSourceData() {
    }

    public static DefinitionSnapshot<UnlockBundle> snapshot() {
        return STORE.snapshot();
    }

    public static UnlockBundle getUnlocks(String sourceId) {
        ResourceLocation id = normalizeSourceId(sourceId);
        return id == null ? UnlockBundle.EMPTY : sources.getOrDefault(id, UnlockBundle.EMPTY);
    }

    public static boolean hasSource(String sourceId) {
        ResourceLocation id = normalizeSourceId(sourceId);
        return id != null && sources.containsKey(id);
    }

    public static List<String> getSourceIds() {
        return sources.keySet().stream().map(ResourceLocation::toString).toList();
    }

    public static String skillLevelSourceId(SkillType skill, int level) {
        if (skill == null || level <= 0) return "";
        return StardewCraft.MODID + ":skill/" + skill.getName().toLowerCase(Locale.ROOT) + "/" + level;
    }

    public static UnlockBundle getSkillLevelUnlocks(SkillType skill, int level) {
        return getUnlocks(skillLevelSourceId(skill, level));
    }

    public static String getCachedJson() {
        String current = cachedJson;
        if (!current.isEmpty()) return current;
        JsonObject root = new JsonObject();
        sources.forEach((id, bundle) -> UnlockBundle.CODEC.encodeStart(JsonOps.INSTANCE, bundle)
                .result().ifPresent(json -> root.add(id.toString(), json)));
        current = GSON.toJson(root);
        cachedJson = current;
        return current;
    }

    public static void applyFromJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;
            Map<ResourceLocation, UnlockBundle> decoded = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    errors.add("Invalid unlock source ID " + entry.getKey());
                    continue;
                }
                UnlockBundle.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(errors::add)
                        .ifPresent(bundle -> decoded.put(id, bundle));
            }
            if (!errors.isEmpty()) {
                StardewCraft.LOGGER.error("[DATA-SYNC] Rejected unlock sources: {}", String.join("; ", errors));
                return;
            }
            sources = Collections.unmodifiableMap(new LinkedHashMap<>(decoded));
            cachedJson = json;
            StardewCraft.LOGGER.info("[DATA-SYNC] Applied {} unlock sources", sources.size());
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[DATA-SYNC] Failed to apply unlock sources", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "player");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, UnlockBundle> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> canonicalSources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

            JsonElement legacy = objects.get(LEGACY_TABLE);
            if (legacy == null || !legacy.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(LEGACY_TABLE, null, "Missing legacy unlock source table"));
            } else {
                for (Map.Entry<String, JsonElement> entry : legacy.getAsJsonObject().entrySet()) {
                    ResourceLocation id = normalizeSourceId(entry.getKey());
                    decode(LEGACY_TABLE, id, entry.getValue(), definitions, canonicalSources, diagnostics);
                }
            }

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("unlock_sources/"))
                    .forEach(entry -> {
                        String path = entry.getKey().getPath().substring("unlock_sources/".length());
                        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
                        decode(entry.getKey(), id, entry.getValue(), definitions, canonicalSources, diagnostics);
                    });

            var result = STORE.applyLocal(definitions, canonicalSources, diagnostics);
            for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
                String source = diagnostic.source() == null ? "<unlock reload>" : diagnostic.source().toString();
                if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                    StardewCraft.LOGGER.error("[Unlock] Definition error [{}]: {}", source, diagnostic.message());
                } else {
                    StardewCraft.LOGGER.warn("[Unlock] Definition warning [{}]: {}", source, diagnostic.message());
                }
            }
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Unlock] Rejected snapshot; keeping v{} with {} sources",
                        result.snapshot().version(), result.snapshot().definitions().size());
                return;
            }
            sources = result.snapshot().definitions();
            cachedJson = "";
            StardewCraft.LOGGER.info("[Unlock] Applied snapshot v{} ({} sources)",
                    result.snapshot().version(), sources.size());
        }
    }

    private static void decode(
            ResourceLocation source,
            @Nullable ResourceLocation id,
            JsonElement json,
            Map<ResourceLocation, UnlockBundle> definitions,
            Map<ResourceLocation, String> canonicalSources,
            List<DefinitionDiagnostic> diagnostics) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Invalid unlock source ID"));
            return;
        }
        UnlockBundle.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(bundle -> {
                    if (definitions.putIfAbsent(id, bundle) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(source, id, "Duplicate unlock source ID"));
                    } else {
                        canonicalSources.put(id, UnlockBundle.CODEC.encodeStart(JsonOps.INSTANCE, bundle)
                                .result().map(GSON::toJson).orElse("{}"));
                    }
                });
    }

    @Nullable
    public static ResourceLocation normalizeSourceId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.startsWith("skill:") || value.startsWith("shop:")) {
            return ResourceLocation.tryBuild(
                    StardewCraft.MODID, value.replace(':', '/').toLowerCase(Locale.ROOT));
        }
        ResourceLocation direct = ResourceLocation.tryParse(value);
        if (direct != null && value.indexOf(':') == value.lastIndexOf(':')) return direct;
        String[] parts = value.split(":");
        if (parts.length >= 2) {
            String path = String.join("/", parts).toLowerCase(Locale.ROOT);
            return ResourceLocation.tryBuild(StardewCraft.MODID, path);
        }
        return ResourceLocation.tryBuild(StardewCraft.MODID, value.toLowerCase(Locale.ROOT));
    }

    private static List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !result.contains(value)) result.add(value);
        }
        return List.copyOf(result);
    }
}
