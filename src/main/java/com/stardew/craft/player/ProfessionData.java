package com.stardew.craft.player;

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
import com.stardew.craft.api.v1.profession.StardewProfessionDefinition;
import com.stardew.craft.api.v1.profession.StardewProfessionEffectHandlers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reloadable metadata for the fixed legacy profession slots. */
public final class ProfessionData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation BUILTIN_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "vanilla");
    private static final AtomicDefinitionStore<StardewProfessionDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile String cachedJson = "{}";

    private ProfessionData() {
    }

    public static DefinitionSnapshot<StardewProfessionDefinition> snapshot() {
        return STORE.snapshot();
    }

    public static ResourceLocation id(ProfessionType profession) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, profession.getName());
    }

    public static Optional<StardewProfessionDefinition> definition(ProfessionType profession) {
        return Optional.ofNullable(STORE.snapshot().definitions().get(id(profession)));
    }

    public static String getCachedJson() {
        return cachedJson;
    }

    public static double applyEffect(ProfessionType profession, ResourceLocation operation,
                                     @Nullable ServerPlayer player, ItemStack stack, double value) {
        return definition(profession).flatMap(StardewProfessionDefinition::effectHandler)
                .map(handler -> StardewProfessionEffectHandlers.apply(
                        handler, id(profession), operation, player, stack, value))
                .orElse(value);
    }

    public static void applyFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, StardewProfessionDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                decode(id, id, entry.getValue(), definitions, sources, diagnostics);
            }
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            logDiagnostics(result.diagnostics());
            if (result.accepted()) cachedJson = json;
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[Professions] Failed client sync", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "professions");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewProfessionDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            JsonElement builtin = objects.get(BUILTIN_TABLE);
            if (builtin == null || !builtin.isJsonObject() || !builtin.getAsJsonObject().has("professions")) {
                diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                        "Missing built-in profession table"));
            } else {
                for (JsonElement raw : builtin.getAsJsonObject().getAsJsonArray("professions")) {
                    if (!raw.isJsonObject()) continue;
                    JsonObject object = raw.getAsJsonObject().deepCopy();
                    String path = object.has("id") ? object.remove("id").getAsString() : "";
                    decode(BUILTIN_TABLE, ResourceLocation.tryBuild(StardewCraft.MODID, path), object,
                            definitions, sources, diagnostics);
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
                StardewCraft.LOGGER.error("[Professions] Rejected reload; keeping {} definitions",
                        STORE.snapshot().definitions().size());
                return;
            }
            cachedJson = encodeDefinitions(result.snapshot().definitions());
            StardewCraft.LOGGER.info("[Professions] Applied {} definitions", definitions.size());
        }
    }

    private static void decode(ResourceLocation source, ResourceLocation id, JsonElement json,
                               Map<ResourceLocation, StardewProfessionDefinition> definitions,
                               Map<ResourceLocation, String> sources,
                               List<DefinitionDiagnostic> diagnostics) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Missing or invalid profession ID"));
            return;
        }
        StardewProfessionDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> StardewProfessionDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                        .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                        .ifPresent(encoded -> {
                            definitions.put(id, definition);
                            sources.put(id, GSON.toJson(encoded));
                        }));
    }

    private static String encodeDefinitions(Map<ResourceLocation, StardewProfessionDefinition> definitions) {
        JsonObject root = new JsonObject();
        definitions.forEach((id, definition) -> StardewProfessionDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, definition).result().ifPresent(json -> root.add(id.toString(), json)));
        return GSON.toJson(root);
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Professions] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Professions] {}", diagnostic.message());
            }
        }
    }
}
