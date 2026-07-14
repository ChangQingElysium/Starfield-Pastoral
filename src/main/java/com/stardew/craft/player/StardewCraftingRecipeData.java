package com.stardew.craft.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.production.StardewCraftingIngredient;
import com.stardew.craft.api.v1.production.StardewCraftingRecipeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reloadable Stardew crafting recipes with a compatibility adapter for the original combined table. */
@SuppressWarnings("null")
public final class StardewCraftingRecipeData {
    public record IngredientEntry(String item, String tag, String displayItem, String displayName, int count) {
    }

    public record OutputEntry(String item, int count) {
    }

    public record RecipeEntry(
            String id,
            OutputEntry output,
            List<IngredientEntry> ingredients,
            String unlockCondition,
            List<StardewCondition> unlockWhen
    ) {
        public RecipeEntry {
            ingredients = List.copyOf(ingredients == null ? List.of() : ingredients);
            unlockWhen = List.copyOf(unlockWhen == null ? List.of() : unlockWhen);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation LEGACY_TABLE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "vanilla_crafting_recipes");
    private static final AtomicDefinitionStore<RecipeEntry> STORE = new AtomicDefinitionStore<>();
    private static volatile Map<ResourceLocation, RecipeEntry> recipes = Map.of();
    private static volatile String cachedJson = "";

    private StardewCraftingRecipeData() {
    }

    public static DefinitionSnapshot<RecipeEntry> snapshot() {
        return STORE.snapshot();
    }

    public static List<String> getRecipeIds() {
        return recipes.keySet().stream().map(StardewCraftingRecipeData::storageId).toList();
    }

    public static Optional<RecipeEntry> getRecipe(String id) {
        ResourceLocation key = normalizeId(id);
        return key == null ? Optional.empty() : Optional.ofNullable(recipes.get(key));
    }

    public static List<RecipeEntry> getRecipes() {
        return List.copyOf(recipes.values());
    }

    public static String getUnlockCondition(String id) {
        return getRecipe(id).map(RecipeEntry::unlockCondition).orElse("");
    }

    public static List<StardewCondition> getUnlockConditions(String id) {
        return getRecipe(id).map(RecipeEntry::unlockWhen).orElse(List.of());
    }

    public static ItemStack getOutputStack(String id) {
        RecipeEntry recipe = getRecipe(id).orElse(null);
        if (recipe == null || recipe.output() == null) return ItemStack.EMPTY;
        ResourceLocation itemId = ResourceLocation.tryParse(recipe.output().item());
        if (itemId == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == null || item == Items.AIR
                ? ItemStack.EMPTY
                : new ItemStack(item, Math.max(1, recipe.output().count()));
    }

    public static List<IngredientEntry> getIngredientEntries(String id) {
        return getIngredientEntries(id, false);
    }

    public static List<IngredientEntry> getIngredientEntries(String id, boolean hasTrapper) {
        RecipeEntry recipe = getRecipe(id).orElse(null);
        if (recipe == null) return List.of();
        ResourceLocation recipeId = normalizeId(id);
        if (hasTrapper && recipeId != null && StardewCraft.MODID.equals(recipeId.getNamespace())
                && "crab_pot".equals(recipeId.getPath())) {
            return List.of(
                    new IngredientEntry("stardewcraft:wood_normal", null, null, null, 25),
                    new IngredientEntry("stardewcraft:iron_bar", null, null, null, 2));
        }
        return recipe.ingredients().stream()
                .filter(entry -> entry != null && hasIngredientTarget(entry) && entry.count() > 0)
                .toList();
    }

    public static List<Ingredient> toExpandedIngredients(String id) {
        return toExpandedIngredients(id, false);
    }

    public static List<Ingredient> toExpandedIngredients(String id, boolean hasTrapper) {
        List<Ingredient> expanded = new ArrayList<>();
        for (IngredientEntry entry : getIngredientEntries(id, hasTrapper)) {
            Ingredient ingredient = toIngredient(entry);
            if (ingredient.isEmpty()) continue;
            for (int i = 0; i < Math.max(1, entry.count()); i++) expanded.add(ingredient);
        }
        return expanded;
    }

    public static Ingredient toIngredient(IngredientEntry entry) {
        if (entry == null) return Ingredient.EMPTY;
        if (entry.tag() != null && !entry.tag().isBlank()) {
            ResourceLocation tagId = ResourceLocation.tryParse(entry.tag());
            return tagId == null ? Ingredient.EMPTY : Ingredient.of(TagKey.create(Registries.ITEM, tagId));
        }
        ResourceLocation itemId = ResourceLocation.tryParse(entry.item());
        if (itemId == null) return Ingredient.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == null || item == Items.AIR ? Ingredient.EMPTY : Ingredient.of(new ItemStack(item));
    }

    public static ItemStack getDisplayStack(IngredientEntry entry) {
        if (entry == null) return ItemStack.EMPTY;
        String raw = entry.displayItem() == null || entry.displayItem().isBlank()
                ? entry.item() : entry.displayItem();
        ResourceLocation itemId = raw == null ? null : ResourceLocation.tryParse(raw);
        if (itemId == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == null || item == Items.AIR
                ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, entry.count()));
    }

    public static Component getDisplayName(IngredientEntry entry) {
        if (entry == null) return Component.empty();
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            return Component.translatable(entry.displayName().trim());
        }
        ItemStack display = getDisplayStack(entry);
        return display.isEmpty() ? Component.empty() : display.getHoverName().copy();
    }

    public static String getCachedJson() {
        String current = cachedJson;
        if (!current.isEmpty()) return current;
        JsonObject root = new JsonObject();
        recipes.forEach((id, recipe) -> StardewCraftingRecipeDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, toDefinition(recipe))
                .result().ifPresent(json -> root.add(id.toString(), json)));
        current = GSON.toJson(root);
        cachedJson = current;
        return current;
    }

    public static void applyFromJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;
            Map<ResourceLocation, RecipeEntry> decoded = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    errors.add("Invalid crafting recipe ID " + entry.getKey());
                    continue;
                }
                StardewCraftingRecipeDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(errors::add)
                        .ifPresent(definition -> decoded.put(id, fromDefinition(id, definition)));
            }
            if (!errors.isEmpty()) {
                StardewCraft.LOGGER.error("[DATA-SYNC] Rejected crafting recipes: {}", String.join("; ", errors));
                return;
            }
            recipes = Collections.unmodifiableMap(new LinkedHashMap<>(decoded));
            cachedJson = json;
            StardewCraft.LOGGER.info("[DATA-SYNC] Applied {} crafting recipes", recipes.size());
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("[DATA-SYNC] Failed to apply crafting recipes", exception);
        }
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "player");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, RecipeEntry> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            parseLegacy(objects.get(LEGACY_TABLE), definitions, sources, diagnostics);

            objects.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                    .filter(entry -> entry.getKey().getPath().startsWith("crafting_recipes/"))
                    .forEach(entry -> parseModern(entry, definitions, sources, diagnostics));

            var result = STORE.applyLocal(definitions, sources, diagnostics);
            logDiagnostics(result.diagnostics());
            if (!result.accepted()) {
                StardewCraft.LOGGER.error("[Crafting] Rejected snapshot; keeping v{} with {} recipes",
                        result.snapshot().version(), result.snapshot().definitions().size());
                return;
            }
            recipes = result.snapshot().definitions();
            cachedJson = "";
            StardewCraft.LOGGER.info("[Crafting] Applied snapshot v{} ({} recipes)",
                    result.snapshot().version(), recipes.size());
        }
    }

    private static void parseLegacy(
            @Nullable JsonElement element,
            Map<ResourceLocation, RecipeEntry> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics) {
        if (element == null || !element.isJsonObject()) {
            diagnostics.add(DefinitionDiagnostic.error(LEGACY_TABLE, null, "Missing legacy crafting recipe table"));
            return;
        }
        JsonArray array = element.getAsJsonObject().getAsJsonArray("recipes");
        if (array == null) {
            diagnostics.add(DefinitionDiagnostic.error(LEGACY_TABLE, null, "Legacy crafting table needs recipes array"));
            return;
        }
        int index = 0;
        for (JsonElement raw : array) {
            if (!raw.isJsonObject()) {
                diagnostics.add(DefinitionDiagnostic.error(LEGACY_TABLE, null, "Recipe #" + index + " must be an object"));
                index++;
                continue;
            }
            JsonObject object = raw.getAsJsonObject();
            String rawId = string(object, "id");
            ResourceLocation id = normalizeId(rawId);
            try {
                RecipeEntry recipe = parseLegacyRecipe(id, object);
                put(LEGACY_TABLE, id, recipe, definitions, sources, diagnostics);
            } catch (RuntimeException exception) {
                diagnostics.add(DefinitionDiagnostic.error(
                        LEGACY_TABLE, id, "Invalid legacy recipe " + rawId + ": " + exception.getMessage()));
            }
            index++;
        }
    }

    private static RecipeEntry parseLegacyRecipe(ResourceLocation id, JsonObject root) {
        if (id == null) throw new IllegalArgumentException("invalid ID");
        JsonObject output = root.getAsJsonObject("output");
        if (output == null) throw new IllegalArgumentException("missing output");
        OutputEntry outputEntry = new OutputEntry(string(output, "item"), integer(output, "count", 1));
        List<IngredientEntry> ingredients = new ArrayList<>();
        JsonArray rawIngredients = root.getAsJsonArray("ingredients");
        if (rawIngredients == null || rawIngredients.isEmpty()) throw new IllegalArgumentException("missing ingredients");
        for (JsonElement raw : rawIngredients) {
            JsonObject ingredient = raw.getAsJsonObject();
            ingredients.add(new IngredientEntry(
                    nullableString(ingredient, "item"), nullableString(ingredient, "tag"),
                    nullableString(ingredient, "displayItem"), nullableString(ingredient, "displayName"),
                    integer(ingredient, "count", 1)));
        }
        return new RecipeEntry(storageId(id), outputEntry, ingredients,
                nullableString(root, "unlockCondition"), List.of());
    }

    private static void parseModern(
            Map.Entry<ResourceLocation, JsonElement> entry,
            Map<ResourceLocation, RecipeEntry> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics) {
        String path = entry.getKey().getPath().substring("crafting_recipes/".length());
        ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(), path);
        if (id == null) {
            diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), null, "Invalid crafting recipe path"));
            return;
        }
        StardewCraftingRecipeDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                .resultOrPartial(message -> diagnostics.add(DefinitionDiagnostic.error(entry.getKey(), id, message)))
                .ifPresent(definition -> put(
                        entry.getKey(), id, fromDefinition(id, definition), definitions, sources, diagnostics));
    }

    private static void put(
            ResourceLocation source,
            ResourceLocation id,
            RecipeEntry recipe,
            Map<ResourceLocation, RecipeEntry> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics) {
        if (definitions.containsKey(id)) {
            diagnostics.add(DefinitionDiagnostic.error(source, id, "Duplicate crafting recipe ID"));
            return;
        }
        try {
            Optional<JsonElement> canonical = StardewCraftingRecipeDefinition.CODEC
                    .encodeStart(JsonOps.INSTANCE, toDefinition(recipe))
                    .resultOrPartial(message -> diagnostics.add(
                            DefinitionDiagnostic.error(source, id, message)));
            if (canonical.isEmpty()) {
                return;
            }
            definitions.put(id, recipe);
            sources.put(id, GSON.toJson(canonical.get()));
        } catch (RuntimeException exception) {
            diagnostics.add(DefinitionDiagnostic.error(
                    source, id, "Invalid crafting recipe: " + exception.getMessage()));
        }
    }

    private static RecipeEntry fromDefinition(ResourceLocation id, StardewCraftingRecipeDefinition definition) {
        List<IngredientEntry> ingredients = definition.ingredients().stream()
                .map(ingredient -> new IngredientEntry(
                        ingredient.item().map(ResourceLocation::toString).orElse(null),
                        ingredient.tag().map(ResourceLocation::toString).orElse(null),
                        ingredient.displayItem().map(ResourceLocation::toString).orElse(null),
                        ingredient.displayName().orElse(null), ingredient.count()))
                .toList();
        return new RecipeEntry(storageId(id),
                new OutputEntry(definition.output().toString(), definition.outputCount()),
                ingredients, definition.legacyUnlockCondition().orElse(""), definition.unlockWhen());
    }

    private static StardewCraftingRecipeDefinition toDefinition(RecipeEntry recipe) {
        ResourceLocation output = ResourceLocation.parse(recipe.output().item());
        List<StardewCraftingIngredient> ingredients = recipe.ingredients().stream()
                .map(entry -> new StardewCraftingIngredient(
                        Optional.ofNullable(entry.item()).map(ResourceLocation::parse),
                        Optional.ofNullable(entry.tag()).map(ResourceLocation::parse),
                        Math.max(1, entry.count()),
                        Optional.ofNullable(entry.displayItem()).map(ResourceLocation::parse),
                        Optional.ofNullable(entry.displayName())))
                .toList();
        return new StardewCraftingRecipeDefinition(
                output, Math.max(1, recipe.output().count()), ingredients, recipe.unlockWhen(),
                Optional.ofNullable(recipe.unlockCondition()).filter(value -> !value.isBlank()));
    }

    private static void logDiagnostics(List<DefinitionDiagnostic> diagnostics) {
        for (DefinitionDiagnostic diagnostic : diagnostics) {
            String source = diagnostic.source() == null ? "<crafting reload>" : diagnostic.source().toString();
            if (diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error("[Crafting] Definition error [{}]: {}", source, diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn("[Crafting] Definition warning [{}]: {}", source, diagnostic.message());
            }
        }
    }

    private static boolean hasIngredientTarget(IngredientEntry entry) {
        return (entry.item() != null && !entry.item().isBlank())
                || (entry.tag() != null && !entry.tag().isBlank());
    }

    @Nullable
    private static ResourceLocation normalizeId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.indexOf(':') >= 0
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.tryBuild(StardewCraft.MODID, raw.trim().toLowerCase(Locale.ROOT));
    }

    public static String storageId(ResourceLocation id) {
        return StardewCraft.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String string(JsonObject root, String key) {
        String value = nullableString(root, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }

    @Nullable
    private static String nullableString(JsonObject root, String key) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : null;
    }

    private static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }
}
