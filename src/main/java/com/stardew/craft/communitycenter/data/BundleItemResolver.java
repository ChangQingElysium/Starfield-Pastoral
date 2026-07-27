package com.stardew.craft.communitycenter.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves Stardew Valley item IDs (numeric or string) to mod item ResourceLocation paths.
 * Reads from /data/stardewcraft/communitycenter/bundle_item_map.json at class-load time.
 */
@SuppressWarnings("null")
public final class BundleItemResolver {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String MAP_PATH = "/data/stardewcraft/communitycenter/bundle_item_map.json";
    private static final Map<String, String> SDV_TO_MOD = loadMap();

    private BundleItemResolver() {}

    /**
     * Resolve an SDV item ID token (e.g. "24", "DeluxeBait") to a mod item path (e.g. "parsnip").
     * Returns the mod item path or null if unmapped.
     */
    @Nullable
    public static String resolve(String sdvToken) {
        if (sdvToken == null || sdvToken.isBlank()) return null;
        return SDV_TO_MOD.get(sdvToken);
    }

    /**
     * Resolve an SDV item ID token to a Minecraft {@link Item}.
     * Tries stardewcraft namespace first, then minecraft namespace.
     * Returns {@link Items#AIR} if not found.
     */
    public static Item resolveItem(String sdvToken) {
        String path = resolve(sdvToken);
        if (path == null) return Items.AIR;

        ResourceLocation modId = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
        if (BuiltInRegistries.ITEM.containsKey(modId)) {
            return BuiltInRegistries.ITEM.get(modId);
        }

        // Fallback to vanilla namespace
        ResourceLocation vanillaId = ResourceLocation.withDefaultNamespace(path);
        if (BuiltInRegistries.ITEM.containsKey(vanillaId)) {
            return BuiltInRegistries.ITEM.get(vanillaId);
        }

        return Items.AIR;
    }

    /**
     * Resolve a mod item path (e.g. "parsnip") to an ItemStack.
     * Returns {@link ItemStack#EMPTY} if not found.
     */
    public static ItemStack resolveItemStack(String modPath) {
        if (modPath == null || modPath.isBlank()) return ItemStack.EMPTY;

        if (modPath.contains(":")) {
            ResourceLocation namespacedId = ResourceLocation.tryParse(modPath);
            if (namespacedId == null || !BuiltInRegistries.ITEM.containsKey(namespacedId)) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(BuiltInRegistries.ITEM.get(namespacedId));
        }

        ResourceLocation modId = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, modPath);
        if (BuiltInRegistries.ITEM.containsKey(modId)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(modId));
        }

        ResourceLocation vanillaId = ResourceLocation.withDefaultNamespace(modPath);
        if (BuiltInRegistries.ITEM.containsKey(vanillaId)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(vanillaId));
        }

        return ItemStack.EMPTY;
    }

    /** Get the full mapping (unmodifiable). */
    public static Map<String, String> getFullMap() {
        return SDV_TO_MOD;
    }

    private static Map<String, String> loadMap() {
        try (InputStream in = BundleItemResolver.class.getResourceAsStream(MAP_PATH)) {
            if (in == null) {
                StardewCraft.LOGGER.warn("[COMMUNITY CENTER] Missing bundle item map: {}", MAP_PATH);
                return Map.of();
            }

            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> parsed = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            if (parsed == null) return Map.of();

            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : parsed.entrySet()) {
                String key = entry.getKey();
                // Skip comment entries
                if (key.startsWith("_")) continue;
                normalized.put(key, entry.getValue().toLowerCase(Locale.ROOT));
            }

            StardewCraft.LOGGER.info("[COMMUNITY CENTER] Loaded {} bundle item mappings", normalized.size());
            return Collections.unmodifiableMap(normalized);
        } catch (Exception ex) {
            StardewCraft.LOGGER.warn("[COMMUNITY CENTER] Failed to load bundle item map: {}", ex.getMessage());
            return Map.of();
        }
    }
}
