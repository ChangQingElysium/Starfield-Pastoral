package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source-of-truth bridge from vanilla SDV gift taste tokens to registered mod items.
 * If a StardewCraft item can be mapped to a vanilla object, vanilla NPCGiftTastes
 * decides its taste; otherwise the caller should keep the project fallback.
 */
@SuppressWarnings("null")
public final class VanillaGiftTasteResolver {
    private static final String VANILLA_TASTES_RESOURCE = "data/stardewcraft/npc/vanilla/data/NPCGiftTastes.json";
    private static final String VANILLA_OBJECTS_RESOURCE = "data/stardewcraft/npc/vanilla/data/Objects.json";
    private static volatile VanillaData cachedData;

    private VanillaGiftTasteResolver() {
    }

    public static Result resolve(ItemStack held, String npcId) {
        VanillaData data = data();
        if (data == VanillaData.EMPTY || held == null || held.isEmpty()) {
            return null;
        }

        ItemProfile item = profile(held, data);
        if (item.objectInfo() == null && item.vanillaToken() == null) {
            return null;
        }

        TasteDecision decision = resolveTaste(data, item, normalizeNpcId(npcId));
        return new Result(decision.taste(), decision.source());
    }

    public static String objectToken(ItemStack held) {
        VanillaData data = data();
        if (data != VanillaData.EMPTY && held != null && !held.isEmpty()) {
            ItemProfile item = profile(held, data);
            if (item.objectInfo() != null && item.objectInfo().key() != null && !item.objectInfo().key().isBlank()) {
                return item.objectInfo().key();
            }
            if (item.vanillaToken() != null && !item.vanillaToken().isBlank()) {
                return item.vanillaToken();
            }
        }
        return fallbackObjectToken(held);
    }

    private static TasteDecision resolveTaste(VanillaData data, ItemProfile item, String npcId) {
        Taste taste = Taste.NEUTRAL;
        boolean wasIndividualUniversal = false;
        boolean skipDefaultValueRules = false;

        TasteTable universal = data.universal();
        if (containsCategory(universal.loved(), item)) {
            taste = Taste.LOVED;
        } else if (containsCategory(universal.hated(), item)) {
            taste = Taste.HATED;
        } else if (containsCategory(universal.liked(), item)) {
            taste = Taste.LIKED;
        } else if (containsCategory(universal.disliked(), item)) {
            taste = Taste.DISLIKED;
        }

        Taste contextTaste = findByContext(universal, item, false);
        if (contextTaste != null) {
            taste = contextTaste;
        }

        Taste itemTaste = findByItem(universal, item);
        if (itemTaste != null) {
            taste = itemTaste;
            wasIndividualUniversal = true;
            skipDefaultValueRules = itemTaste == Taste.NEUTRAL;
        }

        ObjectInfo objectInfo = item.objectInfo();
        if (objectInfo != null && "Arch".equalsIgnoreCase(objectInfo.type())) {
            taste = ("penny".equals(npcId) || "dwarf".equals(npcId)) ? Taste.LIKED : Taste.DISLIKED;
        }

        if (objectInfo != null && taste == Taste.NEUTRAL && !skipDefaultValueRules) {
            if (objectInfo.edibility() != -300 && objectInfo.edibility() < 0) {
                taste = Taste.HATED;
            } else if (objectInfo.price() < 20) {
                taste = Taste.DISLIKED;
            }
        }

        TasteTable npcTable = data.npcTables().get(npcId);
        if (npcTable != null) {
            Taste npcItemTaste = findByItem(npcTable, item);
            if (npcItemTaste != null) {
                return new TasteDecision(npcItemTaste, "vanilla-npc-item");
            }
            Taste npcContextTaste = findByContext(npcTable, item, true);
            if (npcContextTaste != null) {
                return new TasteDecision(npcContextTaste, "vanilla-npc-context");
            }
            if (!wasIndividualUniversal) {
                Taste npcCategoryTaste = findByCategory(npcTable, item);
                if (npcCategoryTaste != null) {
                    return new TasteDecision(npcCategoryTaste, "vanilla-npc-category");
                }
            }
        }

        return new TasteDecision(taste, "vanilla-universal");
    }

    private static Taste findByItem(TasteTable table, ItemProfile item) {
        if (containsItem(table.loved(), item)) return Taste.LOVED;
        if (containsItem(table.hated(), item)) return Taste.HATED;
        if (containsItem(table.liked(), item)) return Taste.LIKED;
        if (containsItem(table.disliked(), item)) return Taste.DISLIKED;
        if (containsItem(table.neutral(), item)) return Taste.NEUTRAL;
        return null;
    }

    private static Taste findByContext(TasteTable table, ItemProfile item, boolean includeNeutral) {
        if (containsContext(table.loved(), item)) return Taste.LOVED;
        if (containsContext(table.hated(), item)) return Taste.HATED;
        if (containsContext(table.liked(), item)) return Taste.LIKED;
        if (containsContext(table.disliked(), item)) return Taste.DISLIKED;
        if (includeNeutral && containsContext(table.neutral(), item)) return Taste.NEUTRAL;
        return null;
    }

    private static Taste findByCategory(TasteTable table, ItemProfile item) {
        if (containsCategory(table.loved(), item)) return Taste.LOVED;
        if (containsCategory(table.hated(), item)) return Taste.HATED;
        if (containsCategory(table.liked(), item)) return Taste.LIKED;
        if (containsCategory(table.disliked(), item)) return Taste.DISLIKED;
        if (containsCategory(table.neutral(), item)) return Taste.NEUTRAL;
        return null;
    }

    private static boolean containsItem(Set<String> tokens, ItemProfile item) {
        for (String token : tokens) {
            if (token == null || token.isBlank() || token.startsWith("-")) {
                continue;
            }
            char first = token.charAt(0);
            if (!Character.isDigit(first) && item.contextTags().contains(token)) {
                continue;
            }
            if (item.itemTokens().contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsContext(Set<String> tokens, ItemProfile item) {
        for (String token : tokens) {
            if (token == null || token.isBlank() || token.startsWith("-") || Character.isDigit(token.charAt(0))) {
                continue;
            }
            if (item.contextTags().contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCategory(Set<String> tokens, ItemProfile item) {
        return item.categoryToken() != null && tokens.contains(item.categoryToken());
    }

    private static ItemProfile profile(ItemStack held, VanillaData data) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        String id = itemId == null ? "" : itemId.toString().toLowerCase(Locale.ROOT);
        String path = itemId == null ? "" : itemId.getPath().toLowerCase(Locale.ROOT);
        String vanillaToken = specialVanillaTokens().get(path);
        String objectKey = resolveObjectKey(path, data);
        ObjectInfo objectInfo = objectKey == null ? null : data.objectsByKey().get(objectKey);

        Set<String> itemTokens = new LinkedHashSet<>();
        itemTokens.add(path);
        if (vanillaToken != null) {
            itemTokens.add(vanillaToken);
        }
        if (objectInfo != null) {
            itemTokens.add(objectInfo.key());
            itemTokens.add(objectInfo.key().toLowerCase(Locale.ROOT));
            itemTokens.add(snakeCase(objectInfo.key()));
            itemTokens.add(normalizeToken(objectInfo.name()));
        }

        Set<String> contextTags = new LinkedHashSet<>();
        if (objectInfo != null) {
            contextTags.addAll(objectInfo.contextTags());
            String categoryTag = categoryContextTag(objectInfo.category());
            if (categoryTag != null) {
                contextTags.add(categoryTag);
            }
        }
        contextTags.addAll(typeContextTags(held));

        String categoryToken = objectInfo == null ? fallbackCategoryToken(held) : String.valueOf(objectInfo.category());
        return new ItemProfile(id, path, objectInfo, itemTokens, contextTags, categoryToken, vanillaToken);
    }

    private static String resolveObjectKey(String path, VanillaData data) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String alias = explicitAliases().get(path);
        if (alias != null && data.objectsByKey().containsKey(alias)) {
            return alias;
        }
        String found = data.objectKeyByItemPath().get(path);
        if (found != null) {
            return found;
        }
        if (path.endsWith("_item")) {
            return data.objectKeyByItemPath().get(path.substring(0, path.length() - "_item".length()));
        }
        return null;
    }

    private static Set<String> typeContextTags(ItemStack held) {
        String typeKey = StardewItemDataApi.getTypeKey(held);
        if (typeKey.isBlank()) {
            return Set.of();
        }
        return switch (typeKey.trim().toLowerCase(Locale.ROOT)) {
            case "stardewcraft.type.book" -> Set.of("book_item", "category_books");
            case "stardewcraft.type.trinket" -> Set.of("category_trinket");
            case "stardewcraft.type.artifact", "stardewcraft.type.artifact_quality" -> Set.of("ancient_item");
            case "stardewcraft.type.fish", "stardewcraft.type.fish_quality", "stardewcraft.type.legendary_fish", "stardewcraft.type.crabpot" -> Set.of("category_fish");
            case "stardewcraft.type.cooking", "stardewcraft.type.festival_food" -> Set.of("category_cooking");
            case "stardewcraft.type.gem" -> Set.of("category_gem");
            case "stardewcraft.type.mineral" -> Set.of("category_minerals");
            case "stardewcraft.type.fruit" -> Set.of("category_fruits");
            case "stardewcraft.type.forage" -> Set.of("forage_item");
            case "stardewcraft.type.artisan_goods", "stardewcraft.type.artisan_animal_quality" -> Set.of("category_artisan_goods");
            default -> Set.of();
        };
    }

    private static String fallbackCategoryToken(ItemStack held) {
        String typeKey = StardewItemDataApi.getTypeKey(held);
        if (typeKey.isBlank()) {
            return null;
        }
        String path = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().toLowerCase(Locale.ROOT);
        return switch (typeKey.trim().toLowerCase(Locale.ROOT)) {
            case "stardewcraft.type.fish", "stardewcraft.type.fish_quality", "stardewcraft.type.legendary_fish", "stardewcraft.type.crabpot" -> "-4";
            case "stardewcraft.type.cooking", "stardewcraft.type.festival_food" -> "-7";
            case "stardewcraft.type.gem" -> "-2";
            case "stardewcraft.type.mineral" -> "-12";
            case "stardewcraft.type.trinket" -> "-101";
            case "stardewcraft.type.book" -> "-102";
            case "stardewcraft.type.artifact", "stardewcraft.type.artifact_quality" -> "0";
            case "stardewcraft.type.artisan_goods", "stardewcraft.type.artisan_animal_quality" -> "-26";
            case "stardewcraft.type.fruit" -> "-79";
            case "stardewcraft.type.animal_product" -> path.contains("milk") ? "-6" : (path.contains("egg") ? "-5" : null);
            default -> null;
        };
    }

    private static String categoryContextTag(int category) {
        return switch (category) {
            case -2 -> "category_gem";
            case -4 -> "category_fish";
            case -5 -> "category_egg";
            case -6 -> "category_milk";
            case -7 -> "category_cooking";
            case -8 -> "category_crafting";
            case -12 -> "category_minerals";
            case -19 -> "category_fertilizer";
            case -20 -> "category_junk";
            case -21 -> "category_bait";
            case -22 -> "category_tackle";
            case -24 -> "category_furniture";
            case -25 -> "category_ingredients";
            case -26 -> "category_artisan_goods";
            case -27 -> "category_syrup";
            case -28 -> "category_monster_loot";
            case -74 -> "category_seeds";
            case -75 -> "category_vegetable";
            case -79 -> "category_fruits";
            case -80 -> "category_flowers";
            case -81 -> "category_greens";
            case -95 -> "category_hat";
            case -96 -> "category_ring";
            case -97 -> "category_boots";
            case -98 -> "category_weapon";
            case -99 -> "category_tool";
            case -100 -> "category_clothing";
            case -101 -> "category_trinket";
            case -102, -103 -> "book_item";
            default -> null;
        };
    }

    private static VanillaData data() {
        VanillaData data = cachedData;
        if (data != null) {
            return data;
        }
        synchronized (VanillaGiftTasteResolver.class) {
            data = cachedData;
            if (data == null) {
                data = loadData();
                cachedData = data;
            }
            return data;
        }
    }

    private static VanillaData loadData() {
        JsonObject objectsRoot = readResourceJson(VANILLA_OBJECTS_RESOURCE);
        JsonObject tastesRoot = readResourceJson(VANILLA_TASTES_RESOURCE);
        if (objectsRoot == null || tastesRoot == null) {
            return VanillaData.EMPTY;
        }

        Map<String, ObjectInfo> objectsByKey = parseObjects(objectsRoot);
        Map<String, String> objectKeyByItemPath = buildObjectKeyByItemPath(objectsByKey);
        TasteTable universal = new TasteTable(
            parseTokenSet(tastesRoot, "Universal_Love"),
            parseTokenSet(tastesRoot, "Universal_Like"),
            parseTokenSet(tastesRoot, "Universal_Neutral"),
            parseTokenSet(tastesRoot, "Universal_Dislike"),
            parseTokenSet(tastesRoot, "Universal_Hate")
        );

        Map<String, TasteTable> npcTables = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : tastesRoot.entrySet()) {
            String npc = normalizeNpcId(entry.getKey());
            if (npc.startsWith("universal_") || !entry.getValue().isJsonPrimitive()) {
                continue;
            }
            TasteTable table = parseNpcTasteString(entry.getValue().getAsString());
            if (table != null) {
                npcTables.put(npc, table);
            }
        }

        return new VanillaData(objectsByKey, objectKeyByItemPath, universal, Collections.unmodifiableMap(npcTables));
    }

    private static Map<String, ObjectInfo> parseObjects(JsonObject root) {
        Map<String, ObjectInfo> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject obj = entry.getValue().getAsJsonObject();
            String key = entry.getKey();
            String name = readString(obj, "Name", key);
            int category = readInt(obj, "Category", 0);
            int price = readInt(obj, "Price", 0);
            int edibility = readInt(obj, "Edibility", -300);
            String type = readString(obj, "Type", "");
            Set<String> contextTags = readStringArray(obj, "ContextTags");
            out.put(key, new ObjectInfo(key, name, category, price, edibility, type, contextTags));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, String> buildObjectKeyByItemPath(Map<String, ObjectInfo> objectsByKey) {
        Map<String, String> candidate = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (ObjectInfo info : objectsByKey.values()) {
            addCandidate(candidate, ambiguous, snakeCase(info.key()), info.key());
            addCandidate(candidate, ambiguous, normalizeToken(info.key()), info.key());
            addCandidate(candidate, ambiguous, snakeCase(info.name()), info.key());
            addCandidate(candidate, ambiguous, normalizeToken(info.name()), info.key());
        }
        for (String key : ambiguous) {
            candidate.remove(key);
        }
        for (Map.Entry<String, String> entry : explicitAliases().entrySet()) {
            if (objectsByKey.containsKey(entry.getValue())) {
                candidate.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(candidate);
    }

    private static void addCandidate(Map<String, String> candidate, Set<String> ambiguous, String path, String objectKey) {
        if (path == null || path.isBlank() || ambiguous.contains(path)) {
            return;
        }
        String existing = candidate.putIfAbsent(path, objectKey);
        if (existing != null && !existing.equals(objectKey)) {
            ambiguous.add(path);
            candidate.remove(path);
        }
    }

    private static TasteTable parseNpcTasteString(String raw) {
        if (raw == null) {
            return null;
        }
        String[] split = raw.split("/", -1);
        if (split.length < 10) {
            return null;
        }
        return new TasteTable(
            splitTokens(split[1]),
            splitTokens(split[3]),
            splitTokens(split[9]),
            splitTokens(split[5]),
            splitTokens(split[7])
        );
    }

    private static Set<String> parseTokenSet(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
            ? splitTokens(obj.get(key).getAsString())
            : Set.of();
    }

    private static Set<String> splitTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : raw.trim().split("\\s+")) {
            if (!token.isBlank()) {
                out.add(token.trim());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static JsonObject readResourceJson(String path) {
        try (InputStream stream = VanillaGiftTasteResolver.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeNpcId(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private static String readString(JsonObject obj, String key, String fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : fallback;
    }

    private static int readInt(JsonObject obj, String key, int fallback) {
        try {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Set<String> readStringArray(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return Set.of();
        }
        JsonArray array = obj.getAsJsonArray(key);
        List<String> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
        }
        return Set.copyOf(out);
    }

    private static String fallbackObjectToken(ItemStack held) {
        if (held == null || held.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        String path = itemId == null ? "" : itemId.getPath();
        if ("magic_rock_candy".equals(path)) {
            return "279";
        }
        if ("golden_pumpkin".equals(path)) {
            return "373";
        }
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? path : sb.toString();
    }

    private static String snakeCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
                sb.append('_');
            }
            sb.append(c);
            previous = c;
        }
        return normalizeToken(sb.toString());
    }

    private static String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == '_') {
            sb.deleteCharAt(len - 1);
            len--;
        }
        return sb.toString();
    }

    private static Map<String, String> explicitAliases() {
        return Map.ofEntries(
            Map.entry("autumn_s_bounty", "235"),
            Map.entry("book_queen_of_sauce", "Book_QueenOfSauce"),
            Map.entry("book_wild_seeds", "Book_WildSeeds"),
            Map.entry("book_animal_catalogue", "Book_AnimalCatalogue"),
            Map.entry("book_speed2", "Book_Speed2"),
            Map.entry("grape_wine", "348"),
            Map.entry("wine", "348"),
            Map.entry("tea_set", "341"),
            Map.entry("pina_colada", "873"),
            Map.entry("large_goat_milk", "438"),
            Map.entry("rabbits_foot", "446"),
            Map.entry("tea_leaves", "815"),
            Map.entry("wood_normal", "388"),
            Map.entry("wood_hard", "709"),
            Map.entry("stone", "390"),
            Map.entry("hardwood", "709"),
            Map.entry("fiber", "771"),
            Map.entry("magic_rock_candy", "279"),
            Map.entry("golden_pumpkin", "373")
        );
    }

    private static Map<String, String> specialVanillaTokens() {
        return Map.of(
            "frog_egg", "FrogEgg",
            "parrot_egg", "ParrotEgg",
            "fairy_box", "FairyBox",
            "basilisk_paw", "BasiliskPaw"
        );
    }

    public enum Taste {
        LOVED,
        LIKED,
        NEUTRAL,
        DISLIKED,
        HATED
    }

    public record Result(Taste taste, String source) {
    }

    private record TasteDecision(Taste taste, String source) {
    }

    private record TasteTable(
        Set<String> loved,
        Set<String> liked,
        Set<String> neutral,
        Set<String> disliked,
        Set<String> hated
    ) {
    }

    private record ObjectInfo(
        String key,
        String name,
        int category,
        int price,
        int edibility,
        String type,
        Set<String> contextTags
    ) {
    }

    private record ItemProfile(
        String itemId,
        String path,
        ObjectInfo objectInfo,
        Set<String> itemTokens,
        Set<String> contextTags,
        String categoryToken,
        String vanillaToken
    ) {
    }

    private record VanillaData(
        Map<String, ObjectInfo> objectsByKey,
        Map<String, String> objectKeyByItemPath,
        TasteTable universal,
        Map<String, TasteTable> npcTables
    ) {
        private static final VanillaData EMPTY = new VanillaData(Map.of(), Map.of(), new TasteTable(Set.of(), Set.of(), Set.of(), Set.of(), Set.of()), Map.of());
    }
}
