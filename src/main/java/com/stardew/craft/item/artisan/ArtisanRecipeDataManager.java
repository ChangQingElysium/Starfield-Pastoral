package com.stardew.craft.item.artisan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.AtomicDefinitionStore;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.content.DefinitionSnapshot;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ArtisanRecipeDataManager {
    private ArtisanRecipeDataManager() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicDefinitionStore<Recipe> STORE = new AtomicDefinitionStore<>();
    private static volatile Catalog catalog = Catalog.empty();

    /** 获取缓存的 JSON（服务端调用）。若 GC 回收则重新序列化 */
    public static String getCachedJson() {
        return catalog.cachedJson();
    }

    private static String rebuildCacheJson(
            Map<String, List<Recipe>> current
    ) {
        if (current.isEmpty()) return "";
        JsonObject cacheRoot = new JsonObject();
        for (Map.Entry<String, List<Recipe>> me : current.entrySet()) {
            JsonArray arr = new JsonArray();
            for (Recipe r : me.getValue()) {
                @SuppressWarnings("null")
                JsonObject ro = ReloadListener.buildRecipeJson(r);
                arr.add(ro);
            }
            cacheRoot.add(me.getKey(), arr);
        }
        return GSON.toJson(cacheRoot);
    }

    /** 从 JSON 字符串重放解析（客户端调用） */
    public static void applyFromJson(String json) {
        try {
            com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
            if (root == null) return;
            Map<String, List<Recipe>> loaded = new LinkedHashMap<>();
            Map<ResourceLocation, Recipe> definitions =
                    new LinkedHashMap<>();
            Map<ResourceLocation, String> sources =
                    new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation machineId = normalizeMachineId(entry.getKey(), StardewCraft.MODID);
                if (machineId == null) continue;
                String machineKey = machineId.toString();
                JsonArray recipes = entry.getValue().getAsJsonArray();
                List<Recipe> list = new ArrayList<>();
                int index = 0;
                for (JsonElement el : recipes) {
                    Recipe r = recipeFromJson(el.getAsJsonObject(), machineId, index++);
                    if (r != null) {
                        list.add(r);
                        definitions.put(r.id(), r);
                        sources.put(
                                r.id(),
                                GSON.toJson(
                                        ReloadListener.buildRecipeJson(r)));
                    }
                }
                loaded.put(machineKey, List.copyOf(list));
            }
            publishCandidate(
                    definitions,
                    sources,
                    List.of(),
                    loaded,
                    json,
                    "network sync");
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[DATA-SYNC] Failed to apply artisan JSON", e);
        }
    }

    @Nullable
    private static Recipe recipeFromJson(JsonObject obj, ResourceLocation machineId, int index) {
        try {
            ResourceLocation inputId = obj.has("inputId") && !obj.get("inputId").isJsonNull()
                    ? ResourceLocation.tryParse(obj.get("inputId").getAsString()) : null;
            TagKey<Item> inputTag = obj.has("inputTag") && !obj.get("inputTag").isJsonNull()
                    ? TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse(obj.get("inputTag").getAsString())) : null;
            InputMode inputMode = InputMode.valueOf(obj.get("inputMode").getAsString());
            ResourceLocation outputId = obj.has("outputId") && !obj.get("outputId").isJsonNull()
                    ? ResourceLocation.tryParse(obj.get("outputId").getAsString()) : null;
            int outputCount = obj.get("outputCount").getAsInt();
            int minutes = obj.get("minutes").getAsInt();
            int consumeCount = obj.get("consumeCount").getAsInt();
            boolean keepInputQuality = obj.get("keepInputQuality").getAsBoolean();
            int outputQuality = obj.get("outputQuality").getAsInt();
            PreserveType preserveType = obj.has("preserveType") && !obj.get("preserveType").isJsonNull()
                    ? PreserveType.valueOf(obj.get("preserveType").getAsString()) : null;
            SeedMakerRule seedMakerRule = null;
            if (obj.has("seedMakerRule") && !obj.get("seedMakerRule").isJsonNull()) {
                JsonObject sm = obj.getAsJsonObject("seedMakerRule");
                seedMakerRule = new SeedMakerRule(
                        sm.get("ancientChance").getAsDouble(), sm.get("mixedChance").getAsDouble(),
                        sm.get("mixedMin").getAsInt(), sm.get("mixedMax").getAsInt(),
                        sm.get("seedMin").getAsInt(), sm.get("seedMax").getAsInt());
            }
            OutputMode outputMode = OutputMode.valueOf(obj.get("outputMode").getAsString());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    machineId.getNamespace(), "network/" + machineId.getPath() + "/" + index);
            return new Recipe(id, machineId, inputId, inputTag, inputMode, outputId, outputCount, minutes,
                    consumeCount, keepInputQuality, outputQuality, preserveType, seedMakerRule, outputMode);
        } catch (Exception e) {
            return null;
        }
    }

    public enum InputMode {
        DEFAULT,
        CROP_TYPE,
        MINERAL_TYPE,
        FISH_TYPE
    }

    public enum OutputMode {
        FIXED,
        COPY_INPUT,
        SEEDMAKER,
        SMOKED
    }

    public record SeedMakerRule(double ancientChance,
                                double mixedChance,
                                int mixedMin,
                                int mixedMax,
                                int seedMin,
                                int seedMax) {
    }

    private static final SeedMakerRule DEFAULT_SEEDMAKER_RULE = new SeedMakerRule(0.005, 0.02, 1, 4, 1, 3);

    public record Recipe(ResourceLocation id,
                         ResourceLocation machine,
                         @Nullable ResourceLocation inputId,
                         @Nullable TagKey<Item> inputTag,
                         InputMode inputMode,
                         @Nullable ResourceLocation outputId,
                         int outputCount,
                         int minutes,
                         int consumeCount,
                         boolean keepInputQuality,
                         int outputQuality,
                         @Nullable PreserveType preserveType,
                         @Nullable SeedMakerRule seedMakerRule,
                         OutputMode outputMode) {
        @SuppressWarnings("null")
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (inputMode != null && inputMode != InputMode.DEFAULT) {
                return matchesByMode(stack, inputMode);
            }
            if (inputId != null) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                return inputId.equals(id);
            }
            if (inputTag != null) {
                return stack.is(inputTag);
            }
            return false;
        }
    }

    private static boolean matchesByMode(ItemStack stack, InputMode mode) {
        ResourceLocation category = StardewItemDataApi.resolve(stack)
                .map(data -> data.category())
                .orElse(null);
        if (category == null) return false;
        return switch (mode) {
            case CROP_TYPE -> category.equals(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "crop"));
            case MINERAL_TYPE -> category.equals(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mineral"));
            case FISH_TYPE -> category.equals(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fish"))
                    || category.equals(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "crabpot"))
                    || category.equals(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "legendary_fish"));
            default -> false;
        };
    }

    @SuppressWarnings("null")
    public static Optional<Recipe> getRecipe(String machineKey, ItemStack stack) {
        if (machineKey == null || machineKey.isBlank() || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation machineId = normalizeMachineId(machineKey, StardewCraft.MODID);
        if (machineId == null) return Optional.empty();
        List<Recipe> recipes = catalog.recipesByMachine()
                .get(machineId.toString());
        if (recipes == null || recipes.isEmpty()) {
            return Optional.empty();
        }
        for (Recipe recipe : recipes) {
            if (recipe.matches(stack)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static Optional<Recipe> getRecipeByOutput(String machineKey, ResourceLocation outputId) {
        if (machineKey == null || machineKey.isBlank() || outputId == null) {
            return Optional.empty();
        }
        ResourceLocation machineId = normalizeMachineId(machineKey, StardewCraft.MODID);
        if (machineId == null) return Optional.empty();
        List<Recipe> recipes = catalog.recipesByMachine()
                .get(machineId.toString());
        if (recipes == null || recipes.isEmpty()) {
            return Optional.empty();
        }
        for (Recipe recipe : recipes) {
            if (outputId.equals(recipe.outputId())) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static List<Recipe> getRecipes(String machineKey) {
        if (machineKey == null || machineKey.isBlank()) {
            return List.of();
        }
        ResourceLocation machineId = normalizeMachineId(machineKey, StardewCraft.MODID);
        if (machineId == null) return List.of();
        List<Recipe> recipes = catalog.recipesByMachine()
                .get(machineId.toString());
        return recipes == null ? List.of() : recipes;
    }

    public static java.util.Set<String> getAllMachineKeys() {
        return catalog.recipesByMachine().keySet();
    }

    public static DefinitionSnapshot<Recipe> snapshot() {
        return catalog.definitions();
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "artisan");
        }

        @Override
        protected void apply(@SuppressWarnings("null") Map<ResourceLocation, JsonElement> objects,
                             @SuppressWarnings("null") ResourceManager resourceManager,
                             @SuppressWarnings("null") ProfilerFiller profiler) {
            Map<ResourceLocation, Recipe> definitions = new LinkedHashMap<>();
            Map<ResourceLocation, String> sources = new LinkedHashMap<>();
            List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
            List<Map.Entry<ResourceLocation, JsonElement>> orderedResources = objects.entrySet().stream()
                    .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
                    .toList();
            for (Map.Entry<ResourceLocation, JsonElement> entry : orderedResources) {
                ResourceLocation resourceId = entry.getKey();
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            resourceId, resourceId, "Machine recipe resource must be an object"));
                    continue;
                }
                JsonObject root = element.getAsJsonObject();
                ResourceLocation machineId = normalizeMachineId(readString(root, "machine"), resourceId.getNamespace());
                if (machineId == null) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            resourceId, resourceId, "Missing or invalid namespaced machine ID"));
                    continue;
                }
                boolean grouped = root.has("recipes");
                if (grouped && !root.get("recipes").isJsonArray()) {
                    diagnostics.add(DefinitionDiagnostic.error(
                            resourceId, resourceId, "Field 'recipes' must be an array"));
                    continue;
                }
                JsonArray recipes = grouped ? root.getAsJsonArray("recipes") : singleton(root);
                int recipeIndex = 0;
                for (JsonElement recipeEl : recipes) {
                    int currentIndex = recipeIndex++;
                    if (!recipeEl.isJsonObject()) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                resourceId, definitionId(resourceId, grouped, currentIndex),
                                "Machine recipe entry must be an object"));
                        continue;
                    }
                    JsonObject recipeObj = recipeEl.getAsJsonObject();
                    if (recipeObj.has("comment")) {
                        continue;
                    }
                    ResourceLocation definitionId = definitionId(resourceId, grouped, currentIndex);
                    ResourceLocation inputId = readId(recipeObj, "input");
                    TagKey<Item> inputTag = readTag(recipeObj, "tag");
                    InputMode inputMode = readInputMode(recipeObj, "inputMode");
                    if (inputMode == InputMode.DEFAULT && inputId == null && inputTag == null) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                resourceId, definitionId, "Recipe needs input, tag, or a supported inputMode"));
                        continue;
                    }
                    OutputMode outputMode = readOutputMode(recipeObj, "outputMode");
                    ResourceLocation outputId = readId(recipeObj, "output");
                    if (outputMode == OutputMode.FIXED && outputId == null) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                resourceId, definitionId, "Fixed recipe needs a valid output item ID"));
                        continue;
                    }
                    int outputCount = readInt(recipeObj, "outputCount", 1);
                    int minutes = readInt(recipeObj, "minutes", 0);
                    int consumeCount = readInt(recipeObj, "consume", 1);
                    QualityRule qualityRule = readQualityRule(recipeObj);
                    PreserveType preserveType = readPreserveType(recipeObj, "preserveType");
                    SeedMakerRule seedMakerRule = outputMode == OutputMode.SEEDMAKER
                            ? readSeedMakerRule(recipeObj)
                            : null;
                    if (minutes <= 0) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                resourceId, definitionId, "Recipe minutes must be positive"));
                        continue;
                    }
                    outputCount = Math.max(1, outputCount);
                    consumeCount = Math.max(1, consumeCount);
                    Recipe recipe = new Recipe(definitionId, machineId, inputId, inputTag, inputMode,
                            outputId, outputCount, minutes, consumeCount, qualityRule.keepInputQuality(),
                            qualityRule.outputQuality(), preserveType, seedMakerRule, outputMode);
                    if (definitions.putIfAbsent(definitionId, recipe) != null) {
                        diagnostics.add(DefinitionDiagnostic.error(
                                resourceId, definitionId, "Duplicate machine recipe definition ID"));
                    } else {
                        JsonObject canonical = recipeObj.deepCopy();
                        canonical.addProperty("machine", machineId.toString());
                        sources.put(definitionId, GSON.toJson(canonical));
                    }
                }
            }

            publishCandidate(
                    definitions,
                    sources,
                    diagnostics,
                    indexDefinitions(definitions),
                    null,
                    "reload");
        }

        private static ResourceLocation definitionId(ResourceLocation source, boolean grouped, int index) {
            return grouped
                    ? ResourceLocation.fromNamespaceAndPath(source.getNamespace(), source.getPath() + "/" + index)
                    : source;
        }

        private static JsonArray singleton(JsonObject root) {
            JsonObject recipe = root.deepCopy();
            recipe.remove("machine");
            JsonArray array = new JsonArray();
            array.add(recipe);
            return array;
        }

        @SuppressWarnings("null")
        static JsonObject buildRecipeJson(Recipe r) {
            JsonObject ro = new JsonObject();
            ResourceLocation inputId = r.inputId();
            TagKey<Item> inputTag = r.inputTag();
            ResourceLocation outputId = r.outputId();
            PreserveType preserveType = r.preserveType();
            ro.addProperty("inputId", inputId != null ? inputId.toString() : null);
            ro.addProperty("inputTag", inputTag != null ? inputTag.location().toString() : null);
            ro.addProperty("inputMode", r.inputMode().name());
            ro.addProperty("outputId", outputId != null ? outputId.toString() : null);
            ro.addProperty("outputCount", r.outputCount());
            ro.addProperty("minutes", r.minutes());
            ro.addProperty("consumeCount", r.consumeCount());
            ro.addProperty("keepInputQuality", r.keepInputQuality());
            ro.addProperty("outputQuality", r.outputQuality());
            ro.addProperty("preserveType", preserveType != null ? preserveType.name() : null);
            SeedMakerRule seedRule = r.seedMakerRule();
            if (seedRule != null) {
                JsonObject sm = new JsonObject();
                sm.addProperty("ancientChance", seedRule.ancientChance());
                sm.addProperty("mixedChance", seedRule.mixedChance());
                sm.addProperty("mixedMin", seedRule.mixedMin());
                sm.addProperty("mixedMax", seedRule.mixedMax());
                sm.addProperty("seedMin", seedRule.seedMin());
                sm.addProperty("seedMax", seedRule.seedMax());
                ro.add("seedMakerRule", sm);
            }
            ro.addProperty("outputMode", r.outputMode().name());
            return ro;
        }

        private static int readInt(JsonObject obj, String key, int fallback) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                try {
                    return obj.get(key).getAsInt();
                } catch (Exception ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        private static double readDouble(JsonObject obj, String key, double fallback) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                try {
                    return obj.get(key).getAsDouble();
                } catch (Exception ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        private static String readString(JsonObject obj, String key) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                try {
                    return obj.get(key).getAsString();
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }

        @Nullable
        @SuppressWarnings("null")
        private static ResourceLocation readId(JsonObject obj, String key) {
            String raw = readString(obj, key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String trimmed = raw.trim();
            return ResourceLocation.tryParse(trimmed);
        }

        @Nullable
        @SuppressWarnings("null")
        private static TagKey<Item> readTag(JsonObject obj, String key) {
            ResourceLocation id = readId(obj, key);
            if (id == null) {
                return null;
            }
            return TagKey.create(Registries.ITEM, id);
        }

        private static QualityRule readQualityRule(JsonObject obj) {
            if (!obj.has("quality")) {
                return new QualityRule(false, -1);
            }
            JsonElement el = obj.get("quality");
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) {
                    return new QualityRule(false, el.getAsInt());
                }
                if (el.getAsJsonPrimitive().isString()) {
                    String raw = el.getAsString();
                    if (raw != null) {
                        String key = raw.trim().toLowerCase();
                        if ("keep".equals(key)) {
                            return new QualityRule(true, -1);
                        }
                        Integer quality = parseQualityKey(key);
                        if (quality != null) {
                            return new QualityRule(false, quality);
                        }
                    }
                }
            }
            return new QualityRule(false, -1);
        }

        private static Integer parseQualityKey(String key) {
            return switch (key) {
                case "normal" -> 0;
                case "silver" -> 1;
                case "gold" -> 2;
                case "iridium" -> 3;
                default -> null;
            };
        }

        private static InputMode readInputMode(JsonObject obj, String key) {
            String raw = readString(obj, key);
            if (raw == null || raw.isBlank()) {
                return InputMode.DEFAULT;
            }
            return switch (raw.trim().toLowerCase()) {
                case "crop_type" -> InputMode.CROP_TYPE;
                case "mineral_type" -> InputMode.MINERAL_TYPE;
                case "fish_type" -> InputMode.FISH_TYPE;
                default -> InputMode.DEFAULT;
            };
        }

        private static OutputMode readOutputMode(JsonObject obj, String key) {
            String raw = readString(obj, key);
            if (raw == null || raw.isBlank()) {
                return OutputMode.FIXED;
            }
            return switch (raw.trim().toLowerCase()) {
                case "copy_input" -> OutputMode.COPY_INPUT;
                case "seedmaker" -> OutputMode.SEEDMAKER;
                case "smoked" -> OutputMode.SMOKED;
                default -> OutputMode.FIXED;
            };
        }

        private static SeedMakerRule readSeedMakerRule(JsonObject obj) {
            if (!obj.has("seedmaker") || !obj.get("seedmaker").isJsonObject()) {
                return DEFAULT_SEEDMAKER_RULE;
            }
            JsonObject seedObj = obj.getAsJsonObject("seedmaker");
            double ancientChance = readDouble(seedObj, "ancientChance", DEFAULT_SEEDMAKER_RULE.ancientChance());
            double mixedChance = readDouble(seedObj, "mixedChance", DEFAULT_SEEDMAKER_RULE.mixedChance());
            int mixedMin = readInt(seedObj, "mixedMin", DEFAULT_SEEDMAKER_RULE.mixedMin());
            int mixedMax = readInt(seedObj, "mixedMax", DEFAULT_SEEDMAKER_RULE.mixedMax());
            int seedMin = readInt(seedObj, "seedMin", DEFAULT_SEEDMAKER_RULE.seedMin());
            int seedMax = readInt(seedObj, "seedMax", DEFAULT_SEEDMAKER_RULE.seedMax());
            mixedMin = Math.max(0, mixedMin);
            mixedMax = Math.max(mixedMin, mixedMax);
            seedMin = Math.max(0, seedMin);
            seedMax = Math.max(seedMin, seedMax);
            ancientChance = Math.max(0.0, Math.min(1.0, ancientChance));
            mixedChance = Math.max(0.0, Math.min(1.0, mixedChance));
            return new SeedMakerRule(ancientChance, mixedChance, mixedMin, mixedMax, seedMin, seedMax);
        }

        @Nullable
        private static PreserveType readPreserveType(JsonObject obj, String key) {
            String raw = readString(obj, key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return PreserveType.valueOf(raw.trim().toUpperCase());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static Map<String, List<Recipe>> indexDefinitions(
            Map<ResourceLocation, Recipe> definitions
    ) {
        Map<String, List<Recipe>> loaded = new LinkedHashMap<>();
        for (Recipe recipe : definitions.values()) {
            loaded.computeIfAbsent(
                    recipe.machine().toString(),
                    ignored -> new ArrayList<>()).add(recipe);
        }
        Map<String, List<Recipe>> frozen = new LinkedHashMap<>();
        loaded.forEach((machine, recipeList) -> {
            recipeList.sort(java.util.Comparator
                    .comparingInt(
                            ArtisanRecipeDataManager::matcherPriority)
                    .thenComparing(recipe ->
                            recipe.id().toString()));
            frozen.put(machine, List.copyOf(recipeList));
        });
        return Collections.unmodifiableMap(frozen);
    }

    private static int matcherPriority(Recipe recipe) {
        if (recipe.inputId() != null) return 0;
        if (recipe.inputTag() != null) return 1;
        return 2;
    }

    private static synchronized void publishCandidate(
            Map<ResourceLocation, Recipe> definitions,
            Map<ResourceLocation, String> sources,
            List<DefinitionDiagnostic> diagnostics,
            Map<String, List<Recipe>> recipesByMachine,
            @Nullable String sourceJson,
            String operation
    ) {
        List<DefinitionDiagnostic> preparedDiagnostics =
                new ArrayList<>(diagnostics);
        Map<ResourceLocation, Recipe> indexedDefinitions =
                new LinkedHashMap<>();
        recipesByMachine.forEach((machine, recipes) -> {
            for (Recipe recipe : recipes) {
                if (!machine.equals(recipe.machine().toString())) {
                    preparedDiagnostics.add(
                            DefinitionDiagnostic.error(
                                    recipe.id(),
                                    recipe.id(),
                                    "Recipe is indexed under the wrong machine"));
                }
                Recipe previous = indexedDefinitions.putIfAbsent(
                        recipe.id(), recipe);
                if (previous != null) {
                    preparedDiagnostics.add(
                            DefinitionDiagnostic.error(
                                    recipe.id(),
                                    recipe.id(),
                                    "Recipe appears more than once in the machine index"));
                }
            }
        });
        if (!indexedDefinitions.keySet().equals(
                definitions.keySet())) {
            preparedDiagnostics.add(DefinitionDiagnostic.error(
                    null,
                    null,
                    "Machine recipe definition and index IDs differ"));
        }

        AtomicDefinitionStore.ApplyResult<Recipe> result =
                STORE.applyLocal(
                        definitions, sources, preparedDiagnostics);
        for (DefinitionDiagnostic diagnostic : result.diagnostics()) {
            String source = diagnostic.source() == null
                    ? "<machine recipe reload>"
                    : diagnostic.source().toString();
            if (diagnostic.severity()
                    == DefinitionDiagnostic.Severity.ERROR) {
                StardewCraft.LOGGER.error(
                        "[Machine recipe] Definition error [{}]: {}",
                        source,
                        diagnostic.message());
            } else {
                StardewCraft.LOGGER.warn(
                        "[Machine recipe] Definition warning [{}]: {}",
                        source,
                        diagnostic.message());
            }
        }
        if (!result.accepted()) {
            StardewCraft.LOGGER.error(
                    "[Machine recipe] Rejected {}; keeping v{} with {} recipes",
                    operation,
                    catalog.definitions().version(),
                    catalog.definitions().definitions().size());
            return;
        }

        catalog = new Catalog(
                result.snapshot(),
                recipesByMachine,
                sourceJson);
        StardewCraft.LOGGER.info(
                "[Machine recipe] Applied {} v{} ({} recipes across {} machines)",
                operation,
                catalog.definitions().version(),
                catalog.definitions().definitions().size(),
                catalog.recipesByMachine().size());
    }

    static Catalog catalog() {
        return catalog;
    }

    static final class Catalog {
        private final DefinitionSnapshot<Recipe> definitions;
        private final Map<String, List<Recipe>> recipesByMachine;
        private volatile java.lang.ref.SoftReference<String>
                cachedJson;

        private Catalog(
                DefinitionSnapshot<Recipe> definitions,
                Map<String, List<Recipe>> recipesByMachine,
                @Nullable String cachedJson
        ) {
            this.definitions = java.util.Objects.requireNonNull(
                    definitions, "definitions");
            Map<String, List<Recipe>> frozen =
                    new LinkedHashMap<>();
            recipesByMachine.forEach((machine, recipes) ->
                    frozen.put(machine, List.copyOf(recipes)));
            this.recipesByMachine =
                    Collections.unmodifiableMap(frozen);
            this.cachedJson = new java.lang.ref.SoftReference<>(
                    cachedJson);
        }

        DefinitionSnapshot<Recipe> definitions() {
            return definitions;
        }

        Map<String, List<Recipe>> recipesByMachine() {
            return recipesByMachine;
        }

        String cachedJson() {
            String json = cachedJson.get();
            if (json != null) return json;
            json = rebuildCacheJson(recipesByMachine);
            cachedJson = new java.lang.ref.SoftReference<>(json);
            return json;
        }

        private static Catalog empty() {
            return new Catalog(
                    DefinitionSnapshot.empty(),
                    Map.of(),
                    "");
        }
    }

    private record QualityRule(boolean keepInputQuality, int outputQuality) {
    }

    @Nullable
    private static ResourceLocation normalizeMachineId(String raw, String defaultNamespace) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        return value.indexOf(':') >= 0
                ? ResourceLocation.tryParse(value)
                : ResourceLocation.tryBuild(defaultNamespace, value);
    }
}
