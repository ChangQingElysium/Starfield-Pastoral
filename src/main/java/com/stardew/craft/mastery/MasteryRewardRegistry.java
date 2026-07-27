package com.stardew.craft.mastery;

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
import com.stardew.craft.api.v1.mastery.StardewMasteryRewardDefinition;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Reloadable mastery reward rows, synchronized to clients for menu parity. */
public final class MasteryRewardRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<StardewMasteryRewardDefinition> STORE =
            new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    private MasteryRewardRegistry() {
    }

    public enum StatBonus {
        NONE,
        TRINKET_SLOT
    }

    public enum RewardKind {
        ITEM,
        RECIPE,
        STAT
    }

    public record RewardEntry(Supplier<ItemStack> stack, String recipeId, String nameKey,
                              String descKey, StatBonus statBonus, RewardKind kind) {
        public boolean isItem() { return kind == RewardKind.ITEM && !stack.get().isEmpty(); }
        public boolean isRecipe() { return kind == RewardKind.RECIPE && recipeId != null && !recipeId.isBlank(); }
    }

    public static DefinitionSnapshot<StardewMasteryRewardDefinition> snapshot() {
        return catalog.definitions();
    }

    public static List<RewardEntry> rewardsFor(SkillType skill) {
        return catalog.rewards().getOrDefault(skill, List.of());
    }

    public static String getCachedJson() {
        return catalog.cachedJson();
    }

    public static void applyFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, StardewMasteryRewardDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            null, null, "Invalid mastery reward ID " + entry.getKey()));
                    continue;
                }
                StardewMasteryRewardDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(message -> diagnostics.add(
                                DefinitionDiagnostic.error(id, id, message)))
                        .ifPresent(definition -> {
                            definitions.put(id, definition);
                            sources.put(id, encodeDefinition(definition));
                        });
            }
            applyCandidate(definitions, sources, diagnostics, json, "client sync");
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[Mastery rewards] Failed client sync", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "mastery_rewards");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, StardewMasteryRewardDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            objects.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> StardewMasteryRewardDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                            .resultOrPartial(message -> diagnostics.add(
                                    DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                            .ifPresent(definition -> {
                                definitions.put(entry.getKey(), definition);
                                StardewMasteryRewardDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                                        .resultOrPartial(message -> diagnostics.add(
                                                DefinitionDiagnostic.error(entry.getKey(), entry.getKey(), message)))
                                        .ifPresent(encoded -> sources.put(entry.getKey(), GSON.toJson(encoded)));
                            }));
            applyCandidate(definitions, sources, diagnostics, null, "reload");
        }
    }

    static synchronized void applyCandidate(
            Map<ResourceLocation, StardewMasteryRewardDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics,
            @Nullable String sourceJson,
            String operation
    ) {
        List<DefinitionDiagnostic> preparedDiagnostics = new ArrayList<>(diagnostics);
        String nextCachedJson = sourceJson;
        Map<SkillType, List<RewardEntry>> nextRewards = prepareRewards(definitions);
        if (nextCachedJson == null && preparedDiagnostics.stream().noneMatch(
                diagnostic -> diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR)) {
            try {
                nextCachedJson = encodeDefinitions(definitions);
            } catch (RuntimeException exception) {
                preparedDiagnostics.add(DefinitionDiagnostic.error(
                        null, null, "Failed to encode mastery reward sync catalog: "
                                + exception.getMessage()));
            }
        }

        var result = STORE.applyLocal(definitions, sources, preparedDiagnostics);
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Mastery rewards] {}", diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Mastery rewards] {}", diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Mastery rewards] Rejected {}; keeping v{} with {} reward sets",
                    operation, catalog.definitions().version(),
                    catalog.definitions().definitions().size());
            return;
        }
        if (nextCachedJson == null) {
            throw new IllegalStateException("Accepted mastery rewards have no sync catalog");
        }
        catalog = new Catalog(result.snapshot(), nextRewards, nextCachedJson);
        StardewCraft.LOGGER.info(
                "[Mastery rewards] Applied {} v{} ({} reward sets)",
                operation, catalog.definitions().version(),
                catalog.definitions().definitions().size());
    }

    private static Map<SkillType, List<RewardEntry>> prepareRewards(
            Map<ResourceLocation, StardewMasteryRewardDefinition> definitions
    ) {
        Map<SkillType, List<Map.Entry<ResourceLocation, StardewMasteryRewardDefinition>>> grouped =
                new EnumMap<>(SkillType.class);
        definitions.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<ResourceLocation, StardewMasteryRewardDefinition>>comparingInt(
                                entry -> entry.getValue().priority()).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    SkillType skill = skill(entry.getValue().skill());
                    if (skill != null) grouped.computeIfAbsent(skill, ignored -> new ArrayList<>()).add(entry);
                });
        Map<SkillType, List<RewardEntry>> prepared = new EnumMap<>(SkillType.class);
        grouped.forEach((skill, sets) -> prepared.put(skill, sets.stream()
                .flatMap(entry -> entry.getValue().entries().stream())
                .map(MasteryRewardRegistry::toRuntime)
                .toList()));
        return Map.copyOf(prepared);
    }

    private static RewardEntry toRuntime(StardewMasteryRewardDefinition.Entry entry) {
        Supplier<ItemStack> stack = () -> entry.item()
                .filter(BuiltInRegistries.ITEM::containsKey)
                .map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id), entry.count()))
                .orElse(ItemStack.EMPTY);
        RewardKind kind = switch (entry.kind()) {
            case ITEM -> RewardKind.ITEM;
            case RECIPE -> RewardKind.RECIPE;
            case STAT, PLACEHOLDER -> RewardKind.STAT;
        };
        StatBonus bonus = entry.statBonus()
                .filter(id -> id.equals(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "trinket_slot")))
                .map(ignored -> StatBonus.TRINKET_SLOT)
                .orElse(StatBonus.NONE);
        return new RewardEntry(stack, entry.recipeId(), entry.nameKey(), entry.descKey(), bonus, kind);
    }

    private static SkillType skill(ResourceLocation id) {
        try {
            return SkillType.valueOf(id.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            StardewCraft.LOGGER.error("[Mastery rewards] Unknown core skill {}", id);
            return null;
        }
    }

    private static String encodeDefinitions(Map<ResourceLocation, StardewMasteryRewardDefinition> definitions) {
        JsonObject root = new JsonObject();
        definitions.forEach((id, definition) ->
                root.add(id.toString(), JsonParser.parseString(encodeDefinition(definition))));
        return GSON.toJson(root);
    }

    private static String encodeDefinition(StardewMasteryRewardDefinition definition) {
        return StardewMasteryRewardDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .resultOrPartial(message -> {
                    throw new IllegalArgumentException(message);
                })
                .map(GSON::toJson)
                .orElseThrow();
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewMasteryRewardDefinition> definitions,
            Map<SkillType, List<RewardEntry>> rewards,
            String cachedJson
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(definitions, "definitions");
            rewards = Map.copyOf(rewards);
            cachedJson = java.util.Objects.requireNonNull(cachedJson, "cachedJson");
        }

        private static Catalog empty() {
            return new Catalog(DefinitionSnapshot.empty(), Map.of(), "{}");
        }
    }
}
