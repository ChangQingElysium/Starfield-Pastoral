package com.stardew.craft.mining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.mining.StardewMineThemeDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic mine-theme registry. */
public final class MineThemeData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewMineThemeDefinition> STORE = new AtomicDefinitionStore<>();
    private static volatile List<Map.Entry<ResourceLocation, StardewMineThemeDefinition>> ordered = List.of();

    private MineThemeData() {
    }

    public static DefinitionSnapshot<StardewMineThemeDefinition> snapshot() {
        return STORE.snapshot();
    }

    @Nullable
    public static StardewMineThemeDefinition forFloor(int floor) {
        return ordered.stream()
                .map(Map.Entry::getValue)
                .filter(theme -> floor >= theme.minFloor() && floor <= theme.maxFloor())
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public static StardewMineThemeDefinition forMechanic(String mechanicId) {
        return ordered.stream()
                .map(Map.Entry::getValue)
                .filter(theme -> theme.mechanicId().equals(mechanicId))
                .findFirst()
                .orElse(null);
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "mine_themes");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewMineThemeDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> StardewMineThemeDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                            .ifPresent(definition -> {
                                definitions.put(entry.getKey(), definition);
                                StardewMineThemeDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                                        .resultOrPartial(message -> diagnostics.add(
                                                DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                                        .ifPresent(encoded -> sources.put(entry.getKey(), GSON.toJson(encoded)));
                            }));
            var result = STORE.applyLocal(definitions, sources, diagnostics);
            for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                    StardewCraft.LOGGER.error("[Mine themes] {}", diagnostic.message());
                } else {
                    StardewCraft.LOGGER.warn("[Mine themes] {}", diagnostic.message());
                }
            }
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Mine themes] Rejected reload; keeping {} themes", ordered.size());
                return;
            }
            ordered = result.snapshot().definitions().entrySet().stream()
                    .sorted(Comparator
                            .<Map.Entry<ResourceLocation, StardewMineThemeDefinition>>comparingInt(
                                    entry -> entry.getValue().priority()).reversed()
                            .thenComparing(entry -> entry.getKey().toString()))
                    .toList();
            StardewCraft.LOGGER.info("[Mine themes] Applied {} themes", ordered.size());
        }
    }
}
