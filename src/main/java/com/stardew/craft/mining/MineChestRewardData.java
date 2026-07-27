package com.stardew.craft.mining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.loot.StardewMineChestRewardDefinition;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Random;

/** Atomic normal-mine chest reward table keyed by floor. */
@SuppressWarnings("null")
public final class MineChestRewardData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation BUILTIN_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "rewards");
    private static final AtomicDefinitionStore<StardewMineChestRewardDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private MineChestRewardData() {
    }

    public static DefinitionSnapshot<StardewMineChestRewardDefinition> snapshot() {
        return catalog.definitions();
    }

    public static boolean isRewardFloor(int floor) {
        return catalog.ordered().stream()
                .anyMatch(entry -> entry.getValue().floor() == floor);
    }

    public static Optional<ItemStack> resolve(
            ServerLevel level,
            @Nullable ServerPlayer player,
            int floor,
            Random random
    ) {
        StardewConditionContext conditionContext = new StardewConditionContext(level, player);
        for (Map.Entry<ResourceLocation, StardewMineChestRewardDefinition> entry
                : catalog.ordered()) {
            StardewMineChestRewardDefinition definition = entry.getValue();
            if (definition.floor() != floor) continue;
            boolean available = definition.availableWhen().stream().allMatch(condition ->
                    StardewConditions.test(condition, conditionContext).result().orElse(false));
            if (!available) continue;
            List<ItemStack> stacks = StardewItemQueries.resolve(definition.reward(),
                            new StardewItemQueryContext(level, player, random))
                    .resultOrPartial(message -> StardewCraft.LOGGER.error(
                            "[Mine chest] Definition {} failed: {}", entry.getKey(), message))
                    .orElse(List.of());
            if (!stacks.isEmpty()) return Optional.of(stacks.getFirst().copy());
        }
        return Optional.empty();
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "mine_chest");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewMineChestRewardDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            decodeBuiltinTable(objects.get(BUILTIN_TABLE), definitions, sources, diagnostics);
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("rewards/"))
                    .forEach(entry -> {
                        String path = entry.getKey().getPath().substring("rewards/".length());
                        decode(entry.getKey(), ResourceLocation.tryBuild(entry.getKey().getNamespace(), path),
                                entry.getValue(), definitions, sources, diagnostics);
                    });
            applyCandidate(definitions, sources, diagnostics);
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewMineChestRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        List<Map.Entry<ResourceLocation,
                StardewMineChestRewardDefinition>> prepared =
                definitions.entrySet().stream()
                        .sorted(Comparator
                                .<Map.Entry<ResourceLocation,
                                        StardewMineChestRewardDefinition>>
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
                    "[Mine chest] Rejected reload; keeping {} rewards",
                    catalog.ordered().size());
            return;
        }
        catalog = new Catalog(result.snapshot(), prepared);
        StardewCraft.LOGGER.info(
                "[Mine chest] Applied {} rewards",
                catalog.ordered().size());
    }

    private static void decodeBuiltinTable(
            JsonElement table,
            Map<ResourceLocation, StardewMineChestRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (table == null || !table.isJsonArray()) {
            diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                    "Missing built-in mine-chest reward table"));
            return;
        }
        for (JsonElement raw : table.getAsJsonArray()) {
            if (!raw.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                        "Built-in entry must be an object"));
                continue;
            }
            JsonObject object = raw.getAsJsonObject().deepCopy();
            String path = object.has("id") ? object.remove("id").getAsString() : "";
            decode(BUILTIN_TABLE, ResourceLocation.tryBuild(StardewCraft.MODID, path), object,
                    definitions, sources, diagnostics);
        }
    }

    private static void decode(
            ResourceLocation source,
            ResourceLocation id,
            JsonElement json,
            Map<ResourceLocation, StardewMineChestRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Missing or invalid reward ID"));
            return;
        }
        StardewMineChestRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> {
                    if (definitions.putIfAbsent(id, definition) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(source, id, "Duplicate reward ID"));
                        return;
                    }
                    StardewMineChestRewardDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(source, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Mine chest] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Mine chest] {}", diagnostic.message());
            }
        }
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewMineChestRewardDefinition> definitions,
            List<Map.Entry<ResourceLocation,
                    StardewMineChestRewardDefinition>> ordered
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
