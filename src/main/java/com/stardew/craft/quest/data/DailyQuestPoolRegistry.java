package com.stardew.craft.quest.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic registry for daily quest generator profiles. */
public final class DailyQuestPoolRegistry {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "default");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<DailyQuestPoolDefinition> STORE = new AtomicDefinitionStore<>();

    private DailyQuestPoolRegistry() {
    }

    public static DailyQuestPoolDefinition getDefault() {
        DailyQuestPoolDefinition value = STORE.snapshot().definitions().get(DEFAULT_ID);
        if (value != null) {
            return value;
        }
        ensureBuiltinLoaded();
        value = STORE.snapshot().definitions().get(DEFAULT_ID);
        if (value == null) {
            throw new IllegalStateException("Default daily quest pool is unavailable");
        }
        return value;
    }

    private static synchronized void ensureBuiltinLoaded() {
        if (!STORE.snapshot().definitions().isEmpty()) {
            return;
        }
        try (var stream = DailyQuestPoolRegistry.class.getResourceAsStream(
                "/data/stardewcraft/daily_quest_pools/default.json")) {
            if (stream == null) {
                throw new IllegalStateException("Bundled daily quest pool was not found");
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                DailyQuestPoolDefinition definition = DailyQuestPoolDefinition.CODEC
                        .parse(JsonOps.INSTANCE, root).getOrThrow(IllegalArgumentException::new);
                STORE.applyLocal(Map.of(DEFAULT_ID, definition), Map.of(DEFAULT_ID, GSON.toJson(root)), List.of());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load bundled daily quest pool", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "daily_quest_pools");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, DailyQuestPoolDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            for (var entry : objects.entrySet()) {
                DailyQuestPoolDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(
                                entry.getKey(), entry.getKey(), message)))
                        .ifPresent(definition -> {
                            definitions.put(entry.getKey(), definition);
                            sources.put(entry.getKey(), GSON.toJson(entry.getValue()));
                        });
            }
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            if (!result.accepted()) {
                result.diagnostics().forEach(diagnostic -> StardewCraft.LOGGER.error(
                        "[Quest] Daily pool error [{}]: {}", diagnostic.source(), diagnostic.message()));
                StardewCraft.LOGGER.error("[Quest] Rejected daily quest pool reload; keeping v{}",
                        result.snapshot().version());
            } else {
                StardewCraft.LOGGER.info("[Quest] Applied {} daily quest pool definitions (v{})",
                        result.snapshot().definitions().size(), result.snapshot().version());
            }
        }
    }
}
