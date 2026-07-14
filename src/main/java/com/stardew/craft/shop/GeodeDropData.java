package com.stardew.craft.shop;

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
import com.stardew.craft.api.v1.loot.StardewGeodeDropDefinition;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import net.minecraft.core.registries.BuiltInRegistries;
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

/** Namespaced custom geode identities shared by Clint and the geode crusher. */
@SuppressWarnings("null")
public final class GeodeDropData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewGeodeDropDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Map<ResourceLocation, StardewGeodeDropDefinition> definitions = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> inputToDefinition = Map.of();

    private GeodeDropData() {
    }

    public static DefinitionSnapshot<StardewGeodeDropDefinition> snapshot() {
        return STORE.snapshot();
    }

    public static ResourceLocation definitionFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return inputToDefinition.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static Optional<ItemStack> roll(ResourceLocation id, ServerPlayer player, Random random) {
        StardewGeodeDropDefinition definition = definitions.get(id);
        if (definition == null || player == null) return Optional.empty();
        if (!isAvailable(id, player)) return Optional.of(ItemStack.EMPTY);

        int totalWeight = definition.entries().stream().mapToInt(StardewGeodeDropDefinition.Entry::weight).sum();
        int selectedWeight = random.nextInt(totalWeight);
        StardewGeodeDropDefinition.Entry selected = definition.entries().getLast();
        for (StardewGeodeDropDefinition.Entry entry : definition.entries()) {
            selectedWeight -= entry.weight();
            if (selectedWeight < 0) {
                selected = entry;
                break;
            }
        }
        List<ItemStack> stacks = StardewItemQueries.resolve(selected.query(),
                        StardewItemQueryContext.forPlayer(player, random))
                .resultOrPartial(message -> StardewCraft.LOGGER.error(
                        "[Geode drop] Definition {} failed: {}", id, message))
                .orElse(List.of());
        return Optional.of(stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst().copy());
    }

    public static boolean isAvailable(ResourceLocation id, ServerPlayer player) {
        StardewGeodeDropDefinition definition = definitions.get(id);
        if (definition == null || player == null) return false;
        StardewConditionContext conditionContext = StardewConditionContext.forPlayer(player);
        return definition.availableWhen().stream().allMatch(condition ->
                StardewConditions.test(condition, conditionContext).result().orElse(false));
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "geode");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewGeodeDropDefinition> next = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("drops/"))
                    .forEach(entry -> decode(entry, next, sources, diagnostics));

            Map<ResourceLocation, ResourceLocation> inputIndex = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, StardewGeodeDropDefinition> entry : next.entrySet()) {
                for (ResourceLocation input : entry.getValue().inputs()) {
                    ResourceLocation previous = inputIndex.putIfAbsent(input, entry.getKey());
                    if (previous != null) {
                        diagnostics.add(DefinitionDiagnostic.error(null, entry.getKey(),
                                "Input " + input + " is already assigned to " + previous));
                    }
                    if (!BuiltInRegistries.ITEM.containsKey(input)) {
                        diagnostics.add(DefinitionDiagnostic.error(null, entry.getKey(),
                                "Unknown geode input item " + input));
                    }
                }
            }

            var result = STORE.applyLocal(next, sources, diagnostics);
            for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                    StardewCraft.LOGGER.error("[Geode drop] {}", diagnostic.message());
                } else {
                    StardewCraft.LOGGER.warn("[Geode drop] {}", diagnostic.message());
                }
            }
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Geode drop] Rejected reload; keeping {} custom geodes",
                        definitions.size());
                return;
            }
            definitions = result.snapshot().definitions();
            inputToDefinition = Map.copyOf(inputIndex);
            StardewCraft.LOGGER.info("[Geode drop] Applied {} custom geodes", definitions.size());
        }
    }

    private static void decode(
            Map.Entry<ResourceLocation, JsonElement> entry,
            Map<ResourceLocation, StardewGeodeDropDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics
    ) {
        String path = entry.getKey().getPath().substring("drops/".length());
        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), null, "Invalid geode definition path"));
            return;
        }
        StardewGeodeDropDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), id, message)))
                .ifPresent(definition -> {
                    definitions.put(id, definition);
                    StardewGeodeDropDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(entry.getKey(), id, message)))
                            .ifPresent(encoded -> sources.put(id, GSON.toJson(encoded)));
                });
    }
}
