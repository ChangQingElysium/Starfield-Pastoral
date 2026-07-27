package com.stardew.craft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.communitycenter.data.BundleItemResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Source-backed view of the bundled SDV {@code Data/Objects} table. */
@SuppressWarnings("null")
public final class VanillaObjectCatalog {
    private static final String OBJECTS_RESOURCE = "data/stardewcraft/npc/vanilla/data/Objects.json";
    private static final CatalogData DATA = load();

    private VanillaObjectCatalog() {
    }

    @Nullable
    public static Entry resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return null;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        Entry result = DATA.byItemPath().get(path);
        if (result == null && path.endsWith("_item")) {
            result = DATA.byItemPath().get(path.substring(0, path.length() - "_item".length()));
        }
        return result;
    }

    /** Returns one exact vanilla object row by its source key (for example {@code 446}). */
    @Nullable
    public static Entry entryByKey(String objectKey) {
        return objectKey == null ? null : DATA.byKey().get(objectKey);
    }

    /** Returns the vanilla source rows for one collection, in the exact CollectionsPage order. */
    public static List<Entry> entriesForCollection(int collectionTab) {
        return DATA.byKey().values().stream()
                .filter(entry -> entry.collectionTab() == collectionTab)
                .sorted(sourceOrder())
                .toList();
    }

    /** Resolves one vanilla object row back to this project's corresponding item. */
    public static ItemStack stackFor(Entry entry) {
        if (entry == null) return ItemStack.EMPTY;
        Set<String> candidates = new LinkedHashSet<>();
        for (Map.Entry<String, String> alias : aliases().entrySet()) {
            if (entry.key().equals(alias.getValue())) candidates.add(alias.getKey());
        }
        String mapped = BundleItemResolver.resolve(entry.key());
        if (mapped != null) candidates.add(mapped);
        candidates.add(normalize(entry.name()));
        candidates.add(normalize(entry.key()));

        for (String path : candidates) {
            if (path == null || path.isBlank()) continue;
            ResourceLocation modId = ResourceLocation.tryBuild("stardewcraft", path);
            if (modId != null && BuiltInRegistries.ITEM.containsKey(modId)) {
                var item = BuiltInRegistries.ITEM.get(modId);
                if (item != Items.AIR) return new ItemStack(item);
            }
            ResourceLocation minecraftId = ResourceLocation.tryBuild("minecraft", path);
            if (minecraftId != null && BuiltInRegistries.ITEM.containsKey(minecraftId)) {
                var item = BuiltInRegistries.ITEM.get(minecraftId);
                if (item != Items.AIR) return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether a project item ID represents this vanilla object row. */
    public static boolean matchesItemId(Entry entry, String itemId) {
        if (entry == null || itemId == null || itemId.isBlank()) return false;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        String path = id == null ? itemId : id.getPath();
        Entry direct = DATA.byItemPath().get(path.toLowerCase(Locale.ROOT));
        if (direct != null && direct.key().equals(entry.key())) return true;
        return switch (entry.key()) {
            case "348" -> "wine".equals(path);
            case "350" -> "juice".equals(path);
            case "SmokedFish" -> path.startsWith("smoked_");
            default -> false;
        };
    }

    public static Comparator<Entry> sourceOrder() {
        return Comparator.comparingInt((Entry entry) ->
                        DATA.sourceOrderByKey().getOrDefault(entry.key(), Integer.MAX_VALUE))
                .thenComparing(Entry::textureName)
                .thenComparingInt(Entry::spriteIndex)
                .thenComparing(Entry::key);
    }

    public record Entry(
            String key,
            String name,
            String type,
            int category,
            String textureName,
            int spriteIndex,
            boolean excludeFromFishingCollection,
            boolean excludeFromShippingCollection
    ) {
        /** Exact routing used by vanilla CollectionsPage. */
        public int collectionTab() {
            if ("Arch".equals(type)) return 2;
            if ("Fish".equals(type)) return excludeFromFishingCollection ? -1 : 1;
            if ("Minerals".equals(type) || category == -2) return 3;
            if ("Cooking".equals(type) || category == -7) {
                return Set.of("217", "772", "773", "279", "873").contains(key) ? -1 : 4;
            }
            return isPotentialBasicShipped() ? 0 : -1;
        }

        /** Exact {@code Object.isPotentialBasicShipped} predicate. */
        public boolean isPotentialBasicShipped() {
            if ("433".equals(key)) return true;
            if (Set.of("Arch", "Fish", "Minerals", "Cooking").contains(type)) return false;
            if (Set.of(-999, -103, -102, -96, -74, -29, -24, -22, -21,
                    -20, -19, -14, -12, -8, -7, -2, 0).contains(category)) {
                return false;
            }
            return !excludeFromShippingCollection;
        }
    }

    private static CatalogData load() {
        try (var stream = VanillaObjectCatalog.class.getClassLoader().getResourceAsStream(OBJECTS_RESOURCE)) {
            if (stream == null) return CatalogData.EMPTY;
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Entry> byKey = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> raw : root.entrySet()) {
                if (!raw.getValue().isJsonObject()) continue;
                JsonObject object = raw.getValue().getAsJsonObject();
                String texture = readNullableString(object, "Texture");
                byKey.put(raw.getKey(), new Entry(
                        raw.getKey(),
                        readString(object, "Name", raw.getKey()),
                        readString(object, "Type", ""),
                        readInt(object, "Category", 0),
                        texture == null ? "Maps\\springobjects" : texture,
                        readInt(object, "SpriteIndex", 0),
                        readBoolean(object, "ExcludeFromFishingCollection"),
                        readBoolean(object, "ExcludeFromShippingCollection")
                ));
            }
            return new CatalogData(
                    Collections.unmodifiableMap(byKey),
                    buildItemPathIndex(byKey),
                    buildSourceOrder(byKey)
            );
        } catch (Exception ignored) {
            return CatalogData.EMPTY;
        }
    }

    /** Mirrors the four processed-goods insertions in vanilla CollectionsPage. */
    private static Map<String, Integer> buildSourceOrder(Map<String, Entry> byKey) {
        java.util.List<Entry> ordered = new java.util.ArrayList<>(byKey.values());
        ordered.sort(Comparator.comparing(Entry::textureName)
                .thenComparingInt(Entry::spriteIndex)
                .thenComparing(Entry::key));

        java.util.List<Entry> processedGoods = new java.util.ArrayList<>();
        Set<String> names = Set.of("Wine", "Pickles", "Jelly", "Juice");
        for (int index = ordered.size() - 1; index >= 0 && processedGoods.size() < 4; index--) {
            Entry entry = ordered.get(index);
            if (names.contains(entry.name())) {
                processedGoods.add(entry);
                ordered.remove(index);
            }
        }
        if (processedGoods.size() == 4) {
            processedGoods.sort(Comparator.comparing(Entry::name));
            insertAt(ordered, 278, processedGoods.get(2));
            insertAt(ordered, 279, processedGoods.get(0));
            insertAt(ordered, 283, processedGoods.get(3));
            insertAt(ordered, 284, processedGoods.get(1));
        } else {
            ordered.addAll(processedGoods);
            ordered.sort(Comparator.comparing(Entry::textureName)
                    .thenComparingInt(Entry::spriteIndex)
                    .thenComparing(Entry::key));
        }

        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            ranks.put(ordered.get(index).key(), index);
        }
        return Collections.unmodifiableMap(ranks);
    }

    private static void insertAt(java.util.List<Entry> entries, int index, Entry entry) {
        entries.add(Math.min(index, entries.size()), entry);
    }

    private static Map<String, Entry> buildItemPathIndex(Map<String, Entry> byKey) {
        Map<String, Entry> candidates = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Entry entry : byKey.values()) {
            addCandidate(candidates, ambiguous, normalize(entry.key()), entry);
            addCandidate(candidates, ambiguous, normalize(entry.name()), entry);
        }
        for (String path : ambiguous) candidates.remove(path);

        for (Map.Entry<String, String> mapping : BundleItemResolver.getFullMap().entrySet()) {
            Entry entry = byKey.get(mapping.getKey());
            if (entry != null) candidates.put(mapping.getValue(), entry);
        }
        for (Map.Entry<String, String> alias : aliases().entrySet()) {
            Entry entry = byKey.get(alias.getValue());
            if (entry != null) candidates.put(alias.getKey(), entry);
        }
        return Collections.unmodifiableMap(candidates);
    }

    private static void addCandidate(Map<String, Entry> candidates, Set<String> ambiguous,
                                     String path, Entry entry) {
        if (path.isBlank() || ambiguous.contains(path)) return;
        Entry previous = candidates.putIfAbsent(path, entry);
        if (previous != null && !previous.key().equals(entry.key())) {
            candidates.remove(path);
            ambiguous.add(path);
        }
    }

    private static Map<String, String> aliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("autumn_s_bounty", "235");
        aliases.put("wine", "348");
        aliases.put("juice", "350");
        aliases.put("strange_doll_green", "126");
        aliases.put("strange_doll_yellow", "127");
        aliases.put("dried_fruit", "DriedFruit");
        aliases.put("dried_mushrooms", "DriedMushrooms");
        aliases.put("powder_melon", "Powdermelon");
        aliases.put("smoked_chub", "SmokedFish");
        aliases.put("tea_set", "341");
        aliases.put("pina_colada", "873");
        aliases.put("large_goat_milk", "438");
        aliases.put("rabbits_foot", "446");
        aliases.put("tea_leaves", "815");
        aliases.put("wood_normal", "388");
        aliases.put("wood_hard", "709");
        aliases.put("hardwood", "709");
        aliases.put("stone", "390");
        aliases.put("fiber", "771");
        aliases.put("magic_rock_candy", "279");
        aliases.put("golden_pumpkin", "373");
        aliases.put("egg_white", "176");
        aliases.put("egg_brown", "180");
        aliases.put("large_egg_brown", "174");
        aliases.put("large_egg_white", "182");
        aliases.put("milk", "184");
        aliases.put("goat_milk", "436");
        return aliases;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder(value.length());
        boolean underscore = false;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                result.append(c);
                underscore = false;
            } else if (!underscore && result.length() > 0) {
                result.append('_');
                underscore = true;
            }
        }
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '_') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    private static String readString(JsonObject object, String key, String fallback) {
        String value = readNullableString(object, key);
        return value == null ? fallback : value;
    }

    @Nullable
    private static String readNullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : null;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key) {
        try {
            return object.has(key) && object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record CatalogData(Map<String, Entry> byKey, Map<String, Entry> byItemPath,
                               Map<String, Integer> sourceOrderByKey) {
        private static final CatalogData EMPTY = new CatalogData(Map.of(), Map.of(), Map.of());
    }
}
