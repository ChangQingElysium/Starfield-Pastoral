package com.stardew.craft.cooking.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.production.StardewCookingIngredient;
import com.stardew.craft.api.v1.production.StardewCookingRecipeDefinition;
import com.stardew.craft.player.RecipeIdNormalizer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reloadable cooking definitions with adapters for the original token-based tables. */
@SuppressWarnings("null")
public final class VanillaCookingRecipeData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation LEGACY_RECIPES = id("vanilla_cooking_recipes");
    private static final ResourceLocation LEGACY_TOKENS = id("vanilla_cooking_ingredient_map");
    private static final ResourceLocation FISH = id("fish");
    private static final ResourceLocation LEGENDARY_FISH = id("legendary_fish");
    private static final ResourceLocation EGGS = ResourceLocation.fromNamespaceAndPath("c", "eggs");
    private static final ResourceLocation MILK = ResourceLocation.fromNamespaceAndPath("c", "milk");
    private static final AtomicDefinitionStore<StardewCookingRecipeDefinition> STORE = new AtomicDefinitionStore<>();
    private static final Map<String, ResourceLocation> FALLBACK_ITEMS = Map.of(
            "sugar", ResourceLocation.fromNamespaceAndPath("minecraft", "sugar"),
            "dandelion", ResourceLocation.fromNamespaceAndPath("minecraft", "dandelion"),
            "moss", ResourceLocation.fromNamespaceAndPath("minecraft", "moss_block")
    );

    private static volatile Catalog catalog = Catalog.empty();

    private VanillaCookingRecipeData() {
    }

    public static DefinitionSnapshot<StardewCookingRecipeDefinition> snapshot() {
        return catalog.definitions();
    }

    public static List<ResourceLocation> getRecipeIds() {
        return List.copyOf(catalog.recipes().keySet());
    }

    public static Optional<StardewCookingRecipeDefinition> getDefinition(String recipeId) {
        ResourceLocation id = normalizeRecipeId(recipeId);
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(catalog.recipes().get(id));
    }

    public static Optional<StardewCookingRecipeDefinition> getDefinition(ResourceLocation recipeId) {
        return Optional.ofNullable(catalog.recipes().get(recipeId));
    }

    public static List<StardewCookingIngredient> getRequirements(String recipeId) {
        return getDefinition(recipeId).map(StardewCookingRecipeDefinition::ingredients).orElse(List.of());
    }

    public static List<StardewCookingIngredient> getRequirements(ResourceLocation recipeId) {
        return getDefinition(recipeId).map(StardewCookingRecipeDefinition::ingredients).orElse(List.of());
    }

    public static String storageId(ResourceLocation recipeId) {
        return RecipeIdNormalizer.storageId(recipeId);
    }

    public static boolean matches(ItemStack stack, StardewCookingIngredient ingredient) {
        return ingredient != null && ingredient.matches(stack);
    }

    public static ItemStack getOutputStack(ResourceLocation recipeId, int crafts) {
        StardewCookingRecipeDefinition definition =
                catalog.recipes().get(recipeId);
        if (definition == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(definition.output());
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, crafts) * definition.outputCount());
    }

    public static ItemStack getDisplayStack(StardewCookingIngredient ingredient) {
        if (ingredient == null) return ItemStack.EMPTY;
        ResourceLocation display = ingredient.displayItem().orElseGet(() -> ingredient.item().orElse(null));
        if (display == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(display);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static Component describe(StardewCookingIngredient ingredient) {
        ItemStack display = getDisplayStack(ingredient);
        if (!display.isEmpty()) return display.getHoverName();
        if (ingredient != null && ingredient.tag().isPresent()) {
            if (EGGS.equals(ingredient.tag().get())) {
                return Component.translatable("stardewcraft.cooking.ingredient.any_egg");
            }
            if (MILK.equals(ingredient.tag().get())) {
                return Component.translatable("stardewcraft.cooking.ingredient.any_milk");
            }
        }
        if (ingredient != null && ingredient.categories().contains(FISH)) {
            return Component.translatable("stardewcraft.cooking.ingredient.any_fish");
        }
        return Component.literal(ingredient == null ? "?" : ingredient.matcherKey());
    }

    public static String getCachedJson() {
        return catalog.cachedJson();
    }

    public static void applyFromJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;
            Map<ResourceLocation, StardewCookingRecipeDefinition> decoded = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources =
                    new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics =
                    new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            null,
                            null,
                            "Invalid cooking recipe ID "
                                    + entry.getKey()));
                    continue;
                }
                StardewCookingRecipeDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(message ->
                                diagnostics.add(
                                        DefinitionDiagnostic.error(
                                                id, id, message)))
                        .ifPresent(definition -> {
                            decoded.put(id, definition);
                            sources.put(
                                    id,
                                    encodeDefinition(definition));
                        });
            }
            applyCandidate(
                    decoded,
                    sources,
                    diagnostics,
                    json,
                    "client sync");
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[DATA-SYNC] Failed to apply cooking recipes", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "cooking");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, StardewCookingRecipeDefinition> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            Map<String, String> tokenMap = parseLegacyTokenMap(objects.get(LEGACY_TOKENS), diagnostics);

            JsonElement legacy = objects.get(LEGACY_RECIPES);
            if (legacy == null || !legacy.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(
                        LEGACY_RECIPES, null, "Missing legacy cooking recipe table"));
            } else {
                parseLegacyRecipes(legacy.getAsJsonObject(), tokenMap, definitions, sources, diagnostics);
            }

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("recipes/"))
                    .forEach(entry -> parseModernRecipe(entry, definitions, sources, diagnostics));

            applyCandidate(
                    definitions,
                    sources,
                    diagnostics,
                    null,
                    "reload");
        }
    }

    private static synchronized void applyCandidate(
            Map<ResourceLocation, StardewCookingRecipeDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics,
            @Nullable String sourceJson,
            String operation
    ) {
        List<DefinitionDiagnostic> preparedDiagnostics =
                new ArrayList<>(diagnostics);
        String nextCachedJson = sourceJson;
        if (nextCachedJson == null && preparedDiagnostics.stream()
                .noneMatch(diagnostic -> diagnostic.severity()
                        == DefinitionDiagnostic.Severity.ERROR)) {
            try {
                nextCachedJson = encodeDefinitions(definitions);
            } catch (RuntimeException exception) {
                preparedDiagnostics.add(DefinitionDiagnostic.error(
                        null,
                        null,
                        "Failed to encode cooking sync catalog: "
                                + exception.getMessage()));
            }
        }

        var result = STORE.applyLocal(
                definitions, sources, preparedDiagnostics);
        logDiagnostics(result.diagnostics());
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Cooking] Rejected {}; keeping v{} with {} recipes",
                    operation,
                    catalog.definitions().version(),
                    catalog.recipes().size());
            return;
        }
        if (nextCachedJson == null) {
            throw new IllegalStateException(
                    "Accepted cooking definitions have no sync catalog");
        }

        catalog = new Catalog(
                result.snapshot(),
                nextCachedJson);
        StardewCraft.LOGGER.info(
                "[Cooking] Applied {} v{} ({} recipes)",
                operation,
                catalog.definitions().version(),
                catalog.recipes().size());
    }

    private static Map<String, String> parseLegacyTokenMap(
            @Nullable JsonElement element, List<DefinitionDiagnostic> diagnostics) {
        if (element == null || !element.isJsonObject()) {
            diagnostics.add(DefinitionDiagnostic.error(LEGACY_TOKENS, null, "Missing legacy cooking token map"));
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                diagnostics.add(DefinitionDiagnostic.error(
                        LEGACY_TOKENS, null, "Cooking token " + entry.getKey() + " must map to a string"));
                continue;
            }
            result.put(entry.getKey(), entry.getValue().getAsString().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static void parseLegacyRecipes(
            JsonObject root,
            Map<String, String> tokenMap,
            Map<ResourceLocation, StardewCookingRecipeDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            ResourceLocation recipeId = ResourceLocation.tryBuild(StardewCraft.MODID, entry.getKey());
            if (recipeId == null || !entry.getValue().isJsonArray()) {
                diagnostics.add(DefinitionDiagnostic.error(
                        LEGACY_RECIPES, recipeId, "Invalid legacy cooking recipe " + entry.getKey()));
                continue;
            }
            List<StardewCookingIngredient> ingredients = new ArrayList<>();
            int index = 0;
            for (JsonElement rawIngredient : entry.getValue().getAsJsonArray()) {
                if (!rawIngredient.isJsonObject()) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            LEGACY_RECIPES, recipeId, "Ingredient #" + index + " must be an object"));
                    index++;
                    continue;
                }
                JsonObject raw = rawIngredient.getAsJsonObject();
                String token = raw.has("token") ? raw.get("token").getAsString() : "";
                int count = raw.has("count") ? raw.get("count").getAsInt() : 1;
                StardewCookingIngredient ingredient = legacyIngredient(token, count, tokenMap);
                if (ingredient == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            LEGACY_RECIPES, recipeId, "Unknown ingredient token " + token));
                } else {
                    ingredients.add(ingredient);
                }
                index++;
            }
            if (ingredients.size() != entry.getValue().getAsJsonArray().size()) continue;
            ResourceLocation output = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, entry.getKey());
            StardewCookingRecipeDefinition definition = new StardewCookingRecipeDefinition(output, 1, ingredients);
            definitions.put(recipeId, definition);
            sources.put(recipeId, encodeDefinition(definition));
        }
    }

    private static void parseModernRecipe(
            Map.Entry<ResourceLocation, JsonElement> entry,
            Map<ResourceLocation, StardewCookingRecipeDefinition> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics) {
        String path = entry.getKey().getPath().substring("recipes/".length());
        ResourceLocation recipeId = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
        if (recipeId == null) {
            diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), null, "Invalid cooking recipe path"));
            return;
        }
        StardewCookingRecipeDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                .resultOrPartial(message -> diagnostics.add(
                        DefinitionDiagnostic.error(entry.getKey(), recipeId, message)))
                .ifPresent(definition -> {
                    if (definitions.putIfAbsent(recipeId, definition) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                entry.getKey(), recipeId, "Duplicate cooking recipe ID"));
                    } else {
                        sources.put(recipeId, encodeDefinition(definition));
                    }
                });
    }

    @Nullable
    private static StardewCookingIngredient legacyIngredient(String token, int count, Map<String, String> tokenMap) {
        if (count <= 0) return null;
        if ("-4".equals(token)) {
            return ingredient(null, null, List.of(FISH, LEGENDARY_FISH), count, id("sunfish"));
        }
        if ("-5".equals(token)) {
            return ingredient(null, EGGS, List.of(), count, id("egg_white"));
        }
        if ("-6".equals(token)) {
            return ingredient(null, MILK, List.of(), count, id("milk"));
        }
        String path = tokenMap.get(token);
        if (path == null || path.isBlank()) return null;
        ResourceLocation item = id(path);
        if (!BuiltInRegistries.ITEM.containsKey(item)) {
            item = FALLBACK_ITEMS.get(path);
        }
        return item == null ? null : ingredient(item, null, List.of(), count, item);
    }

    private static StardewCookingIngredient ingredient(
            @Nullable ResourceLocation item,
            @Nullable ResourceLocation tag,
            List<ResourceLocation> categories,
            int count,
            @Nullable ResourceLocation display) {
        return new StardewCookingIngredient(
                Optional.ofNullable(item), Optional.ofNullable(tag), categories, count, Optional.ofNullable(display));
    }

    private static String encodeDefinition(StardewCookingRecipeDefinition definition) {
        return StardewCookingRecipeDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .result().map(GSON::toJson).orElse("{}");
    }

    private static String encodeDefinitions(
            Map<ResourceLocation, StardewCookingRecipeDefinition> definitions
    ) {
        JsonObject root = new JsonObject();
        definitions.forEach((id, definition) ->
                StardewCookingRecipeDefinition.CODEC
                        .encodeStart(JsonOps.INSTANCE, definition)
                        .resultOrPartial(message -> {
                            throw new IllegalArgumentException(
                                    id + ": " + message);
                        })
                        .ifPresent(json ->
                                root.add(id.toString(), json)));
        return GSON.toJson(root);
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            String source = diagnostic.source() == null ? "<cooking reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Cooking] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Cooking] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
    }

    @Nullable
    private static ResourceLocation normalizeRecipeId(String raw) {
        return RecipeIdNormalizer.definitionId(raw);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }

    static Catalog catalog() {
        return catalog;
    }

    record Catalog(
            DefinitionSnapshot<StardewCookingRecipeDefinition> definitions,
            String cachedJson
    ) {
        Catalog {
            definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            cachedJson = java.util.Objects.requireNonNull(
                    cachedJson, "cachedJson");
        }

        Map<ResourceLocation, StardewCookingRecipeDefinition> recipes() {
            return definitions.definitions();
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(),
                    "{}");
        }
    }
}
