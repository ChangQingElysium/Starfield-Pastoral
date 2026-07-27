package com.stardew.craft.shop;

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
import com.stardew.craft.api.v1.loot.StardewPrizeTicketRewardDefinition;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/** Atomic prize-ticket reward table with fixed and repeating selectors. */
@SuppressWarnings("null")
public final class PrizeTicketRewardData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation BUILTIN_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "rewards");
    private static final AtomicDefinitionStore<StardewPrizeTicketRewardDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private PrizeTicketRewardData() {
    }

    public static DefinitionSnapshot<StardewPrizeTicketRewardDefinition> snapshot() {
        return catalog.definitions();
    }

    public static Optional<ItemStack> resolve(ServerPlayer player, int prizeLevel, Random random) {
        StardewConditionContext conditionContext = StardewConditionContext.forPlayer(player);
        for (Map.Entry<ResourceLocation, StardewPrizeTicketRewardDefinition> entry
                : catalog.ordered()) {
            StardewPrizeTicketRewardDefinition definition = entry.getValue();
            if (!definition.matches(prizeLevel)) continue;
            boolean available = definition.availableWhen().stream().allMatch(condition ->
                    StardewConditions.test(condition, conditionContext).result().orElse(false));
            if (!available) continue;
            List<ItemStack> stacks = StardewItemQueries.resolve(definition.reward(),
                            StardewItemQueryContext.forPlayer(player, random))
                    .resultOrPartial(message -> StardewCraft.LOGGER.error(
                            "[Prize ticket] Definition {} failed: {}", entry.getKey(), message))
                    .orElse(List.of());
            if (!stacks.isEmpty()) return Optional.of(stacks.getFirst().copy());
        }
        return Optional.empty();
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "prize_ticket");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewPrizeTicketRewardDefinition> definitions = new LinkedHashMap<>();
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
            Map<ResourceLocation, StardewPrizeTicketRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        List<Map.Entry<ResourceLocation,
                StardewPrizeTicketRewardDefinition>> prepared =
                definitions.entrySet().stream()
                        .sorted(Comparator
                                .<Map.Entry<ResourceLocation,
                                        StardewPrizeTicketRewardDefinition>>
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
                    "[Prize ticket] Rejected reload; keeping {} rewards",
                    catalog.ordered().size());
            return;
        }
        catalog = new Catalog(result.snapshot(), prepared);
        StardewCraft.LOGGER.info(
                "[Prize ticket] Applied {} rewards",
                catalog.ordered().size());
    }

    private static void decodeBuiltinTable(
            JsonElement table,
            Map<ResourceLocation, StardewPrizeTicketRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (table == null || !table.isJsonArray()) {
            diagnostics.add(DefinitionDiagnostic.error(BUILTIN_TABLE, null,
                    "Missing built-in prize-ticket reward table"));
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
            Map<ResourceLocation, StardewPrizeTicketRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(source, null, "Missing or invalid reward ID"));
            return;
        }
        StardewPrizeTicketRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(source, id, message)))
                .ifPresent(definition -> {
                    if (definitions.putIfAbsent(id, definition) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(source, id, "Duplicate reward ID"));
                        return;
                    }
                    StardewPrizeTicketRewardDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(source, id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Prize ticket] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Prize ticket] {}", diagnostic.message());
            }
        }
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewPrizeTicketRewardDefinition> definitions,
            List<Map.Entry<ResourceLocation,
                    StardewPrizeTicketRewardDefinition>> ordered
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
