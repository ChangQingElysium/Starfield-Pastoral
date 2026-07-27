package com.stardew.craft.item.artisan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PreservesIngredientDataManager {
    private PreservesIngredientDataManager() {
    }

    public static final class IngredientData {
        public int price;
        public int edibility;
        public String color;

        public int getColorRgb() {
            if (color == null || color.isBlank()) {
                return -1;
            }
            String value = color.trim();
            if (value.startsWith("#")) {
                value = value.substring(1);
            }
            try {
                return Integer.parseInt(value, 16) & 0xFFFFFF;
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Catalog catalog = Catalog.empty();

    /** 获取缓存的 JSON（服务端调用）。若 GC 回收则重新序列化 */
    public static String getCachedJson() {
        return catalog.cachedJson();
    }

    /** 从 JSON 字符串重放解析（客户端调用） */
    public static void applyFromJson(String json) {
        try {
            com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
            if (root == null) return;
            Map<String, IngredientData> loaded = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                IngredientData data = GSON.fromJson(entry.getValue(), IngredientData.class);
                if (data != null) loaded.put(entry.getKey(), data);
            }
            publish(loaded);
            com.stardew.craft.StardewCraft.LOGGER.info("[DATA-SYNC] Applied preserves data from network: {} entries", loaded.size());
        } catch (Exception e) {
            com.stardew.craft.StardewCraft.LOGGER.error("[DATA-SYNC] Failed to apply preserves JSON", e);
        }
    }

    @SuppressWarnings("null")
    public static Optional<IngredientData> getData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return Optional.ofNullable(catalog.data().get(id.getPath()));
    }

    public static Optional<IngredientData> getData(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalog.data().get(id.getPath()));
    }

    public static boolean hasData(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        return catalog.data().containsKey(id.getPath());
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "preserves");
        }

        @Override
        protected void apply(@SuppressWarnings("null") Map<ResourceLocation, JsonElement> objects,
                             @SuppressWarnings("null") ResourceManager resourceManager,
                             @SuppressWarnings("null") ProfilerFiller profiler) {
            Map<String, IngredientData> loaded = new HashMap<>();
            var entries = new java.util.ArrayList<>(objects.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries) {
                ResourceLocation resourceId = entry.getKey();
                if (StardewCraft.MODID.equals(resourceId.getNamespace()) && "vanilla_objects".equals(resourceId.getPath())) {
                    continue;
                }
                JsonElement el = entry.getValue();
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject root = el.getAsJsonObject();
                for (Map.Entry<String, JsonElement> itemEntry : root.entrySet()) {
                    if (!itemEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    IngredientData data = GSON.fromJson(itemEntry.getValue(), IngredientData.class);
                    if (data == null) {
                        continue;
                    }
                    loaded.put(itemEntry.getKey(), data);
                }
            }
            applyVanillaOverrides(loaded, resourceManager);
            publish(loaded);
            StardewCraft.LOGGER.info("Loaded preserves ingredient data: {} entries", loaded.size());
        }
    }

    private static synchronized void publish(Map<String, IngredientData> loaded) {
        catalog = new Catalog(loaded);
    }

    @SuppressWarnings("null")
    private static void applyVanillaOverrides(Map<String, IngredientData> loaded, ResourceManager resourceManager) {
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "preserves/vanilla_objects.json");
        resourceManager.getResource(resourceId).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) {
                    return;
                }
                int overrides = 0;
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    IngredientData data = GSON.fromJson(entry.getValue(), IngredientData.class);
                    if (data == null) {
                        continue;
                    }
                    IngredientData target = loaded.get(entry.getKey());
                    if (target == null) {
                        continue;
                    }
                    target.price = data.price;
                    target.edibility = data.edibility;
                    if (data.color != null && !data.color.isBlank()) {
                        target.color = data.color;
                    }
                    overrides++;
                }

                if (overrides > 0) {
                    StardewCraft.LOGGER.info("Applied {} vanilla preserves overrides from {}", overrides, resourceId);
                }
            } catch (IOException ex) {
                StardewCraft.LOGGER.warn("Failed to read bundled vanilla overrides: {}", ex.getMessage());
            }
        });
    }

    static Catalog catalog() {
        return catalog;
    }

    static final class Catalog {
        private final Map<String, IngredientData> data;
        private volatile SoftReference<String> cachedJson =
                new SoftReference<>(null);

        private Catalog(Map<String, IngredientData> data) {
            this.data = Collections.unmodifiableMap(
                    new LinkedHashMap<>(data));
        }

        Map<String, IngredientData> data() {
            return data;
        }

        String cachedJson() {
            String json = cachedJson.get();
            if (json != null) return json;
            JsonObject cacheRoot = new JsonObject();
            data.forEach((id, value) ->
                    cacheRoot.add(id, GSON.toJsonTree(value)));
            json = data.isEmpty() ? "" : GSON.toJson(cacheRoot);
            cachedJson = new SoftReference<>(json);
            return json;
        }

        private static Catalog empty() {
            return new Catalog(Map.of());
        }
    }
}
