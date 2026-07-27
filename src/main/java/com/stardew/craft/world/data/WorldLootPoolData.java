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
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import com.stardew.craft.api.v1.world.StardewWorldLootPoolDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic registry for structured world-content item and placeable-block pools. */
public final class WorldLootPoolData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewWorldLootPoolDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private WorldLootPoolData() {
    }

    public static DefinitionSnapshot<StardewWorldLootPoolDefinition> snapshot() {
        return catalog.definitions();
    }

    public static List<ItemStack> resolve(
            ResourceLocation source,
            String group,
            ServerLevel level,
            @Nullable ServerPlayer player,
            RandomSource random
    ) {
        List<Map.Entry<ResourceLocation, StardewWorldLootPoolDefinition>> matching =
                catalog.ordered().stream()
                .filter(entry -> entry.getValue().source().equals(source))
                .filter(entry -> entry.getValue().group().equals(group))
                .filter(entry -> available(entry.getValue().availableWhen(), level, player))
                .toList();
        if (matching.isEmpty()) return List.of();

        StardewWorldLootPoolDefinition.Mode mode = matching.getFirst().getValue().mode();
        List<ResolvedEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, StardewWorldLootPoolDefinition> pool : matching) {
            if (pool.getValue().mode() != mode) {
                StardewCraft.LOGGER.error("[World loot] Ignoring {} because group {}/{} mixes modes",
                        pool.getKey(), source, group);
                continue;
            }
            for (StardewWorldLootPoolDefinition.Entry entry : pool.getValue().entries()) {
                if (available(entry.availableWhen(), level, player)) {
                    entries.add(new ResolvedEntry(pool.getKey(), entry));
                }
            }
        }
        return mode == StardewWorldLootPoolDefinition.Mode.SEQUENTIAL
                ? resolveSequential(entries, level, player, random)
                : resolveWeighted(entries, level, player, random);
    }

    private static List<ItemStack> resolveSequential(
            List<ResolvedEntry> entries,
            ServerLevel level,
            @Nullable ServerPlayer player,
            RandomSource random
    ) {
        List<ItemStack> result = new ArrayList<>();
        for (ResolvedEntry resolved : entries) {
            var entry = resolved.entry();
            if (random.nextDouble() >= entry.chance()) continue;
            List<ItemStack> stacks = runQuery(resolved, level, player, random);
            if (stacks.isEmpty()) continue;
            result.addAll(stacks);
            if (!entry.continueOnSuccess()) break;
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> resolveWeighted(
            List<ResolvedEntry> entries,
            ServerLevel level,
            @Nullable ServerPlayer player,
            RandomSource random
    ) {
        List<ResolvedEntry> eligible = entries.stream()
                .filter(entry -> random.nextDouble() < entry.entry().chance())
                .toList();
        long totalWeight = eligible.stream().mapToLong(entry -> entry.entry().weight()).sum();
        if (totalWeight <= 0) return List.of();
        long roll = Math.floorMod(random.nextLong(), totalWeight);
        for (ResolvedEntry entry : eligible) {
            roll -= entry.entry().weight();
            if (roll < 0) return runQuery(entry, level, player, random);
        }
        return List.of();
    }

    private static List<ItemStack> runQuery(
            ResolvedEntry resolved,
            ServerLevel level,
            @Nullable ServerPlayer player,
            RandomSource random
    ) {
        return StardewItemQueries.resolve(resolved.entry().query(),
                        new StardewItemQueryContext(level, player, random::nextLong))
                .resultOrPartial(message -> StardewCraft.LOGGER.error(
                        "[World loot] Definition {} failed: {}", resolved.id(), message))
                .orElse(List.of());
    }

    private static boolean available(
            List<com.stardew.craft.api.v1.condition.StardewCondition> conditions,
            ServerLevel level,
            @Nullable ServerPlayer player
    ) {
        StardewConditionContext context = new StardewConditionContext(level, player);
        return conditions.stream().allMatch(condition ->
                StardewConditions.test(condition, context).result().orElse(false));
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "world_loot");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewWorldLootPoolDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> decode(entry.getKey(), entry.getValue(), definitions, sources, diagnostics));
            applyCandidate(definitions, sources, diagnostics);
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewWorldLootPoolDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        List<Map.Entry<ResourceLocation, StardewWorldLootPoolDefinition>>
                prepared = definitions.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<ResourceLocation,
                                StardewWorldLootPoolDefinition>>
                                comparingInt(entry ->
                                        entry.getValue().priority())
                        .reversed()
                        .thenComparing(entry ->
                                entry.getKey().toString()))
                .toList();
        var result = STORE.applyLocal(
                definitions, sources, diagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[World loot] Rejected reload; keeping {} pools",
                    catalog.ordered().size());
            return;
        }
        catalog = new Catalog(result.snapshot(), prepared);
        StardewCraft.LOGGER.info(
                "[World loot] Applied {} pools",
                catalog.ordered().size());
    }

    private static void decode(
            ResourceLocation id,
            JsonElement json,
            Map<ResourceLocation, StardewWorldLootPoolDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        StardewWorldLootPoolDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(id, id, message)))
                .ifPresent(definition -> {
                    definitions.put(id, definition);
                    StardewWorldLootPoolDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(id, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[World loot] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[World loot] {}", diagnostic.message());
            }
        }
    }

    private record ResolvedEntry(ResourceLocation id, StardewWorldLootPoolDefinition.Entry entry) {
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewWorldLootPoolDefinition> definitions,
            List<Map.Entry<ResourceLocation,
                    StardewWorldLootPoolDefinition>> ordered
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
