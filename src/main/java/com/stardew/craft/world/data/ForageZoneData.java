package com.stardew.craft.world.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.world.StardewForageZoneDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic registry for forage zones. */
public final class ForageZoneData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewForageZoneDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile List<Map.Entry<ResourceLocation, StardewForageZoneDefinition>> ordered = List.of();

    private ForageZoneData() {
    }

    public static DefinitionSnapshot<StardewForageZoneDefinition> snapshot() {
        return STORE.snapshot();
    }

    public static List<Map.Entry<ResourceLocation, StardewForageZoneDefinition>> available(ServerLevel level) {
        StardewConditionContext context = new StardewConditionContext(level, null);
        return ordered.stream().filter(entry -> entry.getValue().availableWhen().stream().allMatch(condition ->
                StardewConditions.test(condition, context).result().orElse(false))).toList();
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "forage_zones");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewForageZoneDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> StardewForageZoneDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                            .ifPresent(definition -> {
                                definitions.put(entry.getKey(), definition);
                                StardewForageZoneDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                                        .resultOrPartial(message -> diagnostics.add(
                                                DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                                        .ifPresent(encoded -> sources.put(entry.getKey(), GSON.toJson(encoded)));
                            }));
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                    StardewCraft.LOGGER.error("[Forage data] {}", diagnostic.message());
                } else {
                    StardewCraft.LOGGER.warn("[Forage data] {}", diagnostic.message());
                }
            }
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Forage data] Rejected reload; keeping {} zones", ordered.size());
                return;
            }
            ordered = result.snapshot().definitions().entrySet().stream()
                    .sorted(Comparator
                            .<Map.Entry<ResourceLocation, StardewForageZoneDefinition>>comparingInt(
                                    entry -> entry.getValue().priority()).reversed()
                            .thenComparing(entry -> entry.getKey().toString()))
                    .toList();
            StardewCraft.LOGGER.info("[Forage data] Applied {} zones", ordered.size());
        }
    }
}
