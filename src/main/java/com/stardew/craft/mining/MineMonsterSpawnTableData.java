package com.stardew.craft.mining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.mining.StardewMineMonsterContext;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfile;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfiles;
import com.stardew.craft.api.v1.mining.StardewMineMonsterSpawnEntry;
import com.stardew.craft.api.v1.mining.StardewMineMonsterSpawnTableDefinition;
import com.stardew.craft.api.v1.mining.StardewMineThemeDefinition;
import com.stardew.craft.event.MineMonsterSpawnHandler;
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

/** Atomic datapack-backed mine-monster spawn tables. */
public final class MineMonsterSpawnTableData {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<
            StardewMineMonsterSpawnTableDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private MineMonsterSpawnTableData() {
    }

    public static DefinitionSnapshot<StardewMineMonsterSpawnTableDefinition>
    snapshot() {
        return catalog.definitions();
    }

    /**
     * Selects from the first matching table. Returns {@code null} when no table
     * matches so callers can preserve Java-provider and built-in fallbacks.
     */
    @Nullable
    public static StardewMineMonsterProfile select(
            StardewMineMonsterContext context
    ) {
        Map.Entry<ResourceLocation, StardewMineThemeDefinition> activeTheme =
                MineThemeData.entryForFloor(context.floor());
        ResourceLocation themeId =
                activeTheme == null ? null : activeTheme.getKey();
        String mechanicId =
                activeTheme == null
                        ? null
                        : activeTheme.getValue().mechanicId();
        for (var table : catalog.ordered()) {
            ResourceLocation profileId = selectProfileId(
                    table.getValue(), context, themeId, mechanicId);
            if (profileId == null) {
                continue;
            }
            StardewMineMonsterProfile profile =
                    StardewMineMonsterProfiles.find(profileId);
            if (profile != null) {
                return profile;
            }
            StardewCraft.LOGGER.error(
                    "[Mine monster tables] Table {} selected missing "
                            + "profile {}; falling back",
                    table.getKey(), profileId);
            return null;
        }
        return null;
    }

    @Nullable
    static ResourceLocation selectProfileId(
            StardewMineMonsterSpawnTableDefinition table,
            StardewMineMonsterContext context,
            @Nullable ResourceLocation themeId,
            @Nullable String mechanicId
    ) {
        if (!matches(table, context, themeId, mechanicId)) {
            return null;
        }
        int totalWeight = table.entries().stream()
                .mapToInt(StardewMineMonsterSpawnEntry::weight)
                .sum();
        int roll = context.random().nextInt(totalWeight);
        int accumulated = 0;
        for (StardewMineMonsterSpawnEntry entry : table.entries()) {
            accumulated += entry.weight();
            if (roll < accumulated) {
                return entry.profile();
            }
        }
        throw new IllegalStateException(
                "Validated mine monster table has no selectable entry");
    }

    private static boolean matches(
            StardewMineMonsterSpawnTableDefinition table,
            StardewMineMonsterContext context,
            @Nullable ResourceLocation themeId,
            @Nullable String mechanicId
    ) {
        if (context.floor() < table.minFloor()
                || context.floor() > table.maxFloor()
                || context.distanceFromSpawn() < table.minDistance()
                || context.distanceFromSpawn() > table.maxDistance()) {
            return false;
        }
        if (table.dark().isPresent()
                && table.dark().get() != context.dark()) {
            return false;
        }
        if (!table.themes().isEmpty()
                && (themeId == null || !table.themes().contains(themeId))) {
            return false;
        }
        return table.mechanics().isEmpty()
                || mechanicId != null
                && table.mechanics().contains(mechanicId);
    }

    public static final class ReloadListener
            extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "mine_monster_spawns");
        }

        @Override
        protected void apply(
                Map<ResourceLocation, JsonElement> objects,
                ResourceManager manager,
                ProfilerFiller profiler
        ) {
            MineMonsterSpawnHandler.ensureProfilesRegistered();
            Map<ResourceLocation, StardewMineMonsterSpawnTableDefinition>
                    definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry ->
                            decode(entry, definitions, sources, diagnostics));
            validateReferences(definitions, diagnostics);
            applyCandidate(definitions, sources, diagnostics);
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewMineMonsterSpawnTableDefinition>
                    definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        List<Map.Entry<ResourceLocation,
                StardewMineMonsterSpawnTableDefinition>> prepared =
                definitions.entrySet().stream()
                        .sorted(Comparator
                                .<Map.Entry<ResourceLocation,
                                        StardewMineMonsterSpawnTableDefinition>>
                                        comparingInt(value ->
                                                value.getValue().priority())
                                .reversed()
                                .thenComparing(value ->
                                        value.getKey().toString()))
                        .toList();
        var result = STORE.applyLocal(
                definitions, sources, diagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Mine monster tables] Rejected reload; keeping {} tables",
                    catalog.ordered().size());
            return;
        }
        catalog = new Catalog(result.snapshot(), prepared);
        StardewCraft.LOGGER.info(
                "[Mine monster tables] Applied {} tables",
                catalog.ordered().size());
    }

    private static void decode(
            Map.Entry<ResourceLocation, JsonElement> entry,
            Map<ResourceLocation, StardewMineMonsterSpawnTableDefinition>
                    definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        ResourceLocation id = entry.getKey();
        StardewMineMonsterSpawnTableDefinition.CODEC
                .parse(JsonOps.INSTANCE, entry.getValue())
                .resultOrPartial(message -> diagnostics.add(
                        DefinitionDiagnostic.error(id, id, message)))
                .ifPresent(definition -> {
                    definitions.put(id, definition);
                    StardewMineMonsterSpawnTableDefinition.CODEC
                            .encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(
                                            id, id, message)))
                            .ifPresent(encoded ->
                                    sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static void validateReferences(
            Map<ResourceLocation, StardewMineMonsterSpawnTableDefinition>
                    definitions,
            List<DefinitionDiagnostic> diagnostics
    ) {
        Map<ResourceLocation, StardewMineThemeDefinition> themes =
                MineThemeData.snapshot().definitions();
        for (var table : definitions.entrySet()) {
            ResourceLocation tableId = table.getKey();
            for (ResourceLocation themeId : table.getValue().themes()) {
                if (!themes.containsKey(themeId)) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            tableId,
                            tableId,
                            "Unknown mine theme " + themeId));
                }
            }
            for (String mechanicId : table.getValue().mechanics()) {
                if (themes.values().stream().noneMatch(theme ->
                        theme.mechanicId().equals(mechanicId))) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            tableId,
                            tableId,
                            "Unknown mine mechanic " + mechanicId));
                }
            }
            if (!table.getValue().themes().isEmpty()
                    && !table.getValue().mechanics().isEmpty()
                    && table.getValue().themes().stream()
                    .map(themes::get)
                    .filter(java.util.Objects::nonNull)
                    .noneMatch(theme -> table.getValue().mechanics()
                            .contains(theme.mechanicId()))) {
                diagnostics.add(DefinitionDiagnostic.error(
                        tableId,
                        tableId,
                        "Mine theme and mechanic filters never overlap"));
            }
            for (StardewMineMonsterSpawnEntry spawn :
                    table.getValue().entries()) {
                if (StardewMineMonsterProfiles.find(spawn.profile())
                        == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            tableId,
                            tableId,
                            "Unknown mine monster profile "
                                    + spawn.profile()));
                }
            }
        }
    }

    private static void logDiagnostics(
            List<DefinitionDiagnostic> diagnostics
    ) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity()
                    == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error(
                        "[Mine monster tables] {}",
                        diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn(
                        "[Mine monster tables] {}",
                        diagnostic.message());
            }
        }
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewMineMonsterSpawnTableDefinition>
                    definitions,
            List<Map.Entry<ResourceLocation,
                    StardewMineMonsterSpawnTableDefinition>> ordered
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            ordered = List.copyOf(ordered);
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(), List.of());
        }
    }
}
