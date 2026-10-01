package com.stardew.craft.fishing.data;

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
import com.stardew.craft.api.v1.fishing.StardewFishingTreasureEntry;
import com.stardew.craft.api.v1.fishing.StardewFishingTreasurePoolDefinition;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Namespaced additions and replacements for the data-defined fishing treasure base. */
@SuppressWarnings("null")
public final class FishingTreasurePoolData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewFishingTreasurePoolDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private FishingTreasurePoolData() {
    }

    public static DefinitionSnapshot<StardewFishingTreasurePoolDefinition> snapshot() {
        return catalog.definitions();
    }

    /** Replacement suppresses the base even when its roll is empty; this permits intentional removal. */
    public static boolean replacesBase(ServerPlayer player, boolean golden) {
        if (player == null) return false;
        var context = StardewConditionContext.forPlayer(player);
        return catalog.definitions().definitions().values().stream().anyMatch(pool -> pool.replaceBase()
                && pool.accepts(golden) && pool.availableWhen().stream().allMatch(c ->
                    StardewConditions.test(c, context).result().orElse(false)));
    }

    public static void appendLoot(
            List<ItemStack> output,
            ServerPlayer player,
            int fishingLevel,
            boolean golden,
            int waterDistance,
            RandomSource random
    ) {
        Map<ResourceLocation, StardewFishingTreasurePoolDefinition> pools =
                catalog.definitions().definitions();
        if (output == null || player == null || pools.isEmpty()) return;
        int distance = Math.max(0, Math.min(5, waterDistance));
        StardewConditionContext conditionContext = StardewConditionContext.forPlayer(player);
        var effects = new ArrayList<com.stardew.craft.api.v1.action.StardewAction>();

        for (Map.Entry<ResourceLocation, StardewFishingTreasurePoolDefinition> registered : pools.entrySet()) {
            StardewFishingTreasurePoolDefinition pool = registered.getValue();
            if (!pool.accepts(golden) || random.nextFloat() >= pool.chance()) continue;
            boolean available = pool.availableWhen().stream().allMatch(condition ->
                    StardewConditions.test(condition, conditionContext).result().orElse(false));
            if (!available) continue;

            List<StardewFishingTreasureEntry> eligible = pool.entries().stream()
                    .filter(entry -> entry.isEligible(fishingLevel, distance))
                    .toList();
            for (int roll = 0; roll < pool.rolls() && !eligible.isEmpty(); roll++) {
                StardewFishingTreasureEntry selected = select(eligible, random);
                var candidateEffects = new ArrayList<com.stardew.craft.api.v1.action.StardewAction>();
                var queryContext = new StardewItemQueryContext(player.serverLevel(), player,
                        new RandomSourceAdapter(random), Map.of("fishing_level", (double) fishingLevel,
                                "water_distance", (double) distance, "golden", golden ? 1d : 0d), candidateEffects::add);
                StardewItemQueries.resolve(selected.query(), queryContext)
                        .resultOrPartial(message -> StardewCraft.LOGGER.warn(
                                "[Fishing treasure] Pool {} failed: {}", registered.getKey(), message))
                        .ifPresent(stacks -> {
                            if (!stacks.isEmpty()) { output.addAll(stacks); effects.addAll(candidateEffects); }
                        });
            }
        }
        com.stardew.craft.loot.LootEffects.commit(player, effects);
    }

    private static StardewFishingTreasureEntry select(
            List<StardewFishingTreasureEntry> entries,
            RandomSource random
    ) {
        long total = entries.stream().mapToLong(StardewFishingTreasureEntry::weight).sum();
        long value = Math.floorMod(random.nextLong(), total);
        for (StardewFishingTreasureEntry entry : entries) {
            value -= entry.weight();
            if (value < 0) return entry;
        }
        return com.stardew.craft.port.PortJava.getLast(entries);
    }

    private record RandomSourceAdapter(RandomSource source) implements java.util.random.RandomGenerator {
        @Override
        public long nextLong() {
            return source.nextLong();
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "fishing");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, StardewFishingTreasurePoolDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("treasure_pools/"))
                    .forEach(entry -> decode(entry, definitions, sources, diagnostics));

            applyCandidate(definitions, sources, diagnostics);
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewFishingTreasurePoolDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        var result = STORE.applyLocal(
                definitions, sources, diagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Fishing treasure] Rejected snapshot; keeping v{} with {} pools",
                    catalog.definitions().version(),
                    catalog.definitions().definitions().size());
            return;
        }
        catalog = new Catalog(result.snapshot());
        StardewCraft.LOGGER.info(
                "[Fishing treasure] Applied snapshot v{} ({} pools)",
                catalog.definitions().version(),
                catalog.definitions().definitions().size());
    }

    private static void decode(
            Map.Entry<ResourceLocation, JsonElement> entry,
            Map<ResourceLocation, StardewFishingTreasurePoolDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        String path = entry.getKey().getPath().substring("treasure_pools/".length());
        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), null, "Invalid treasure pool path"));
            return;
        }
        try {
            StardewFishingTreasurePoolDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), id, message)))
                .ifPresent(definition -> {
                    definitions.put(id, definition);
                    StardewFishingTreasurePoolDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(entry.getKey(), id, message)))
                            .ifPresent(json -> sources.put(id, GSON.toJson(json)));
                });
        } catch (RuntimeException ex) {
            diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), id, ex.getMessage()));
        }
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            String source = diagnostic.source() == null ? "<fishing treasure reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Fishing treasure] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Fishing treasure] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewFishingTreasurePoolDefinition>
                    definitions
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
        }

        private static Catalog empty() {
            return new Catalog(DefinitionSnapshot.empty());
        }
    }
}
