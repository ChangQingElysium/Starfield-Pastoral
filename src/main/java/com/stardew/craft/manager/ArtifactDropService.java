package com.stardew.craft.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewWorldLootPools;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.world.data.WorldLootPoolData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SDV-parity artifact drop service for hoe digging (artifact spots).
 * Replicates GameLocation.digUpArtifactSpot() + ItemQueryResolver RANDOM_ARTIFACT_FOR_DIG_SPOT.
 *
 * <p>This project intentionally does not execute the vanilla Default artifact-spot table.
 * Only three runtime groups are used: Beach, Desert, and one mixed outdoor table for every
 * other outdoor valley location.
 */
@SuppressWarnings("null")
public final class ArtifactDropService {

    private ArtifactDropService() {}

    // ======================== Zone Definition ========================

    private record ZoneRect(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static ZoneRect rect(int x1, int z1, int x2, int z2) {
        return new ZoneRect(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    private static final Map<String, List<ZoneRect>> ZONE_RECTS = new LinkedHashMap<>();
        private static final String DEFAULT_LOCATION = "Default";
        private static final String BEACH_LOCATION = "Beach";
        private static final String DESERT_LOCATION = "Desert";
        private static final String UNDERGROUND_MINE_LOCATION = "UndergroundMine";
        private static final String MIXED_OUTDOOR_LOCATION = "OtherOutdoorsMixed";
        private static final List<String> MIXED_OUTDOOR_SOURCE_LOCATIONS = List.of(
            "Town",
            "Forest",
            "Mountain",
            "BusStop",
            "Backwoods",
            "Railroad",
            "Farm"
        );
            private static final DropEntry SYNTHETIC_RANDOM_ARTIFACT_DROP = new DropEntry(
                "RANDOM_ARTIFACT_FOR_DIG_SPOT",
                1.0,
                -100,
                false,
                null,
                null,
                -1,
                -1,
                    (location, season, totalDaysPlayed, random, player) -> true
            );

    static {
        ZONE_RECTS.put(MIXED_OUTDOOR_LOCATION, List.of(
            rect(-151, -237, 200, 79)
        ));
        ZONE_RECTS.put("Beach", List.of(
            rect(-4, 77, 239, 186)
        ));
    }

    // ======================== Unified Drop Entry ========================

    private record DropEntry(
            String id,
            double chance,
            int precedence,
            boolean continueOnDrop,
            DeferredItem<? extends Item> item,
            List<DeferredItem<? extends Item>> randomItems,
            int minStack,
            int maxStack,
            DropCondition condition
    ) {}

    @FunctionalInterface
    private interface DropCondition {
        boolean test(String location, int season, int totalDaysPlayed, RandomSource random, ServerPlayer player);
    }

    private static final DropCondition ALWAYS = (loc, season, days, rng, player) -> true;

    // ======================== ArtifactSpot Data Sources ========================

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Map<String, List<DropEntry>> locationDrops = Map.of();
    private static final Map<String, DeferredItem<? extends Item>> DROP_ITEM_IDS = Map.ofEntries(
            Map.entry("(O)110", ModItems.RUSTY_SPOON),
            Map.entry("(O)273", ModItems.RICE_SHOOT),
            Map.entry("(O)330", ModItems.CLAY),
            Map.entry("(O)378", ModItems.COPPER_ORE),
            Map.entry("(O)382", ModItems.COAL),
            Map.entry("(O)384", ModItems.GOLD_ORE),
            Map.entry("(O)390", ModItems.STONE),
            Map.entry("(O)412", ModItems.VANILLA_CATEGORY_ITEMS.get("winter_root")),
            Map.entry("(O)416", ModItems.VANILLA_CATEGORY_ITEMS.get("snow_yam")),
            Map.entry("(O)581", ModItems.PREHISTORIC_SKULL),
            Map.entry("(O)582", ModItems.SKELETAL_HAND),
            Map.entry("(O)583", ModItems.PREHISTORIC_RIB),
            Map.entry("(O)584", ModItems.PREHISTORIC_VERTEBRA),
            Map.entry("(O)580", ModItems.PREHISTORIC_TIBIA),
            Map.entry("(O)579", ModItems.PREHISTORIC_SCAPULA),
            Map.entry("(O)588", ModItems.PALM_FOSSIL),
            Map.entry("(O)589", ModItems.TRILOBITE),
            Map.entry("(O)688", ModItems.WARP_TOTEM_FARM),
            Map.entry("(O)689", ModItems.WARP_TOTEM_MOUNTAIN),
            Map.entry("(O)690", ModItems.WARP_TOTEM_BEACH),
            Map.entry("(O)770", ModItems.MIXED_SEEDS),
            Map.entry("(O)881", ModItems.BONE_FRAGMENT)
    );

    // ======================== ArtifactSpotChances (from Objects.json) ========================

    private record ArtifactChance(DeferredItem<? extends Item> item, double chance) {}

    private static final String VANILLA_OBJECTS_RESOURCE =
            "data/stardewcraft/npc/vanilla/data/Objects.json";
    private static final Map<String, DeferredItem<? extends Item>> OBJECT_ID_TO_ARTIFACT_ITEM = Map.ofEntries(
            Map.entry("100", ModItems.CHIPPED_AMPHORA),
            Map.entry("101", ModItems.ARROWHEAD),
            Map.entry("103", ModItems.ANCIENT_DOLL),
            Map.entry("104", ModItems.ELVISH_JEWELRY),
            Map.entry("105", ModItems.CHEWING_STICK),
            Map.entry("106", ModItems.ORNAMENTAL_FAN),
            Map.entry("107", ModItems.DINOSAUR_EGG),
            Map.entry("108", ModItems.RARE_DISC),
            Map.entry("109", ModItems.ANCIENT_SWORD),
            Map.entry("110", ModItems.RUSTY_SPOON),
            Map.entry("111", ModItems.RUSTY_SPUR),
            Map.entry("112", ModItems.RUSTY_COG),
            Map.entry("113", ModItems.CHICKEN_STATUE),
            Map.entry("114", ModItems.ANCIENT_SEED),
            Map.entry("115", ModItems.PREHISTORIC_TOOL),
            Map.entry("116", ModItems.DRIED_STARFISH),
            Map.entry("117", ModItems.ANCHOR),
            Map.entry("118", ModItems.GLASS_SHARDS),
            Map.entry("119", ModItems.BONE_FLUTE),
            Map.entry("120", ModItems.PREHISTORIC_HANDAXE),
            Map.entry("121", ModItems.DWARVISH_HELM),
            Map.entry("122", ModItems.DWARF_GADGET),
            Map.entry("123", ModItems.ANCIENT_DRUM),
            Map.entry("124", ModItems.GOLDEN_MASK),
            Map.entry("125", ModItems.GOLDEN_RELIC),
            Map.entry("126", ModItems.STRANGE_DOLL_GREEN),
            Map.entry("127", ModItems.STRANGE_DOLL_YELLOW),
            Map.entry("579", ModItems.PREHISTORIC_SCAPULA),
            Map.entry("580", ModItems.PREHISTORIC_TIBIA),
            Map.entry("581", ModItems.PREHISTORIC_SKULL),
            Map.entry("582", ModItems.SKELETAL_HAND),
            Map.entry("583", ModItems.PREHISTORIC_RIB),
            Map.entry("584", ModItems.PREHISTORIC_VERTEBRA),
            Map.entry("585", ModItems.SKELETAL_TAIL),
            Map.entry("586", ModItems.NAUTILUS_FOSSIL),
            Map.entry("587", ModItems.AMPHIBIAN_FOSSIL),
            Map.entry("588", ModItems.PALM_FOSSIL),
            Map.entry("589", ModItems.TRILOBITE),
            Map.entry("590", ModItems.BONE_FLUTE),
            Map.entry("591", ModItems.ANCIENT_DOLL),
            Map.entry("592", ModItems.CHEWING_STICK)
    );
    private static final Map<String, List<ArtifactChance>> ARTIFACT_SPOT_CHANCES = new LinkedHashMap<>();

    static {
        loadArtifactSpotChances();
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "artifact_spots");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<String, List<DropEntry>> prepared = new LinkedHashMap<>();
            try {
                for (Map.Entry<ResourceLocation, JsonElement> resource : objects.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                        .toList()) {
                    if (!resource.getValue().isJsonObject()) {
                        throw new IllegalArgumentException(resource.getKey() + " must contain a location object");
                    }
                    for (Map.Entry<String, JsonElement> group : resource.getValue().getAsJsonObject().entrySet()) {
                        if (!group.getValue().isJsonArray()) {
                            throw new IllegalArgumentException(resource.getKey() + " group " + group.getKey()
                                    + " must be an array");
                        }
                        List<DropEntry> drops = prepared.computeIfAbsent(group.getKey(), key -> new ArrayList<>());
                        for (JsonElement raw : group.getValue().getAsJsonArray()) {
                            if (!raw.isJsonObject()) {
                                throw new IllegalArgumentException(resource.getKey() + " group " + group.getKey()
                                        + " contains a non-object entry");
                            }
                            drops.add(parseDropEntry(raw.getAsJsonObject()));
                        }
                    }
                }
                buildMixedOutdoorDropTable(prepared);
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error("[Artifact spots] Rejected reload; keeping {} groups: {}",
                        locationDrops.size(), exception.getMessage());
                return;
            }
            Map<String, List<DropEntry>> immutable = new LinkedHashMap<>();
            prepared.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
            locationDrops = Map.copyOf(immutable);
            StardewCraft.LOGGER.info("[Artifact spots] Applied {} groups", locationDrops.size());
        }
    }

    private static void buildMixedOutdoorDropTable(Map<String, List<DropEntry>> dropsByLocation) {
        LinkedHashMap<String, Double> mergedChances = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> mergedCounts = new LinkedHashMap<>();
        LinkedHashMap<String, DropEntry> prototypes = new LinkedHashMap<>();
        for (String sourceLocation : MIXED_OUTDOOR_SOURCE_LOCATIONS) {
            List<DropEntry> drops = dropsByLocation.get(sourceLocation);
            if (drops == null) {
                continue;
            }
            for (DropEntry drop : drops) {
                String key = dropMergeKey(drop);
                prototypes.putIfAbsent(key, drop);
                mergedChances.merge(key, drop.chance, Double::sum);
                mergedCounts.merge(key, 1, Integer::sum);
            }
        }

        List<DropEntry> mixedDrops = new ArrayList<>();
        for (Map.Entry<String, DropEntry> entry : prototypes.entrySet()) {
            DropEntry prototype = entry.getValue();
            double averageChance = mergedChances.getOrDefault(entry.getKey(), 0.0)
                    / Math.max(1, mergedCounts.getOrDefault(entry.getKey(), 1));
            mixedDrops.add(new DropEntry(
                    prototype.id,
                    Math.min(1.0, averageChance),
                    prototype.precedence,
                    prototype.continueOnDrop,
                    prototype.item,
                    prototype.randomItems,
                    prototype.minStack,
                    prototype.maxStack,
                    prototype.condition
            ));
        }
        dropsByLocation.put(MIXED_OUTDOOR_LOCATION, List.copyOf(mixedDrops));
    }

    private static String dropMergeKey(DropEntry drop) {
        String itemId = drop.item == null ? "" : String.valueOf(drop.item.getId());
        String randomItemIds = "";
        if (drop.randomItems != null && !drop.randomItems.isEmpty()) {
            StringJoiner joiner = new StringJoiner("|");
            for (DeferredItem<? extends Item> randomItem : drop.randomItems) {
                joiner.add(String.valueOf(randomItem.getId()));
            }
            randomItemIds = joiner.toString();
        }
        return drop.id + "#" + itemId + "#" + randomItemIds + "#" + drop.precedence + "#"
                + drop.continueOnDrop + "#" + drop.minStack + "#" + drop.maxStack;
    }

    private static DropEntry parseDropEntry(JsonObject json) {
        return new DropEntry(
                json.get("Id").getAsString(),
                json.get("Chance").getAsDouble(),
                json.get("Precedence").getAsInt(),
                json.get("ContinueOnDrop").getAsBoolean(),
                resolveDropItemId(getNullableString(json, "ItemId")),
                resolveRandomItemIds(json.get("RandomItemId")),
                json.get("MinStack").getAsInt(),
                json.get("MaxStack").getAsInt(),
                parseCondition(getNullableString(json, "Condition"))
        );
    }

    private static String getNullableString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static DeferredItem<? extends Item> resolveDropItemId(String itemId) {
        if (itemId == null || itemId.contains("LOST_BOOK_OR_ITEM") || itemId.contains("SECRET_NOTE_OR_ITEM")
                || itemId.contains("RANDOM_ARTIFACT_FOR_DIG_SPOT")) {
            return null;
        }
        DeferredItem<? extends Item> item = DROP_ITEM_IDS.get(itemId);
        if (item == null && itemId.startsWith("(O)")) {
            item = OBJECT_ID_TO_ARTIFACT_ITEM.get(itemId.substring(3, itemId.length() - 1));
        }
        return item;
    }

    private static List<DeferredItem<? extends Item>> resolveRandomItemIds(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        List<DeferredItem<? extends Item>> items = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement itemElement : element.getAsJsonArray()) {
                DeferredItem<? extends Item> item = resolveDropItemId(itemElement.getAsString());
                if (item != null) {
                    items.add(item);
                }
            }
        } else if (element.isJsonPrimitive()) {
            DeferredItem<? extends Item> item = resolveDropItemId(element.getAsString());
            if (item != null) {
                items.add(item);
            }
        }
        return items.isEmpty() ? null : List.copyOf(items);
    }

    private static DropCondition parseCondition(String rawCondition) {
        if (rawCondition == null || rawCondition.isBlank()) {
            return ALWAYS;
        }
        List<DropCondition> conditions = new ArrayList<>();
        for (String token : rawCondition.split(",\\s*")) {
            conditions.add(parseConditionToken(token.trim()));
        }
        return (location, season, totalDaysPlayed, random, player) -> {
            for (DropCondition condition : conditions) {
                if (!condition.test(location, season, totalDaysPlayed, random, player)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static DropCondition parseConditionToken(String token) {
        boolean inverted = token.startsWith("!");
        String normalized = inverted ? token.substring(1).trim() : token;
        String[] parts = normalized.split("\\s+");
        DropCondition baseCondition = switch (parts[0]) {
            case "PLAYER_LOCATION_NAME" -> buildLocationNameCondition(parts);
            case "LOCATION_SEASON" -> buildSeasonCondition(parts);
            case "RANDOM" -> (location, season, totalDaysPlayed, random, player) -> random.nextDouble() < Double.parseDouble(parts[1]);
            case "PLAYER_SPECIAL_ORDER_RULE_ACTIVE" ->
                    (location, season, totalDaysPlayed, random, player) -> player != null
                            && PlayerStardewDataAPI.isSpecialOrderRuleActive(player, parts[2]);
            case "PLAYER_HAS_MAIL" ->
                    (location, season, totalDaysPlayed, random, player) -> player != null
                            && getPlayerData(player) != null
                            && getPlayerData(player).hasMailFlag(parts[2]);
            case "PLAYER_SPECIAL_ORDER_ACTIVE" ->
                    (location, season, totalDaysPlayed, random, player) -> false;
            case "DAYS_PLAYED" ->
                    (location, season, totalDaysPlayed, random, player) -> totalDaysPlayed >= Integer.parseInt(parts[1]);
            default -> {
                StardewCraft.LOGGER.warn("Unsupported artifact spot condition token: {}", token);
                yield (location, season, totalDaysPlayed, random, player) -> false;
            }
        };
        if (!inverted) {
            return baseCondition;
        }
        return (location, season, totalDaysPlayed, random, player) ->
                !baseCondition.test(location, season, totalDaysPlayed, random, player);
    }

    private static DropCondition buildLocationNameCondition(String[] parts) {
        String expectedLocation = parts[2];
        return (location, season, totalDaysPlayed, random, player) -> expectedLocation.equals(location);
    }

    private static DropCondition buildSeasonCondition(String[] parts) {
        int expectedSeason = switch (parts[2]) {
            case "Spring" -> 0;
            case "Summer" -> 1;
            case "Fall" -> 2;
            case "Winter" -> 3;
            default -> -1;
        };
        return (location, season, totalDaysPlayed, random, player) -> season == expectedSeason;
    }

    private static PlayerStardewData getPlayerData(ServerPlayer player) {
        return PlayerDataManager.getPlayerData(player);
    }

    private static void loadArtifactSpotChances() {
        try (InputStream stream = ArtifactDropService.class.getClassLoader().getResourceAsStream(VANILLA_OBJECTS_RESOURCE)) {
            if (stream == null) {
                StardewCraft.LOGGER.warn("Artifact spot chance source {} was not found", VANILLA_OBJECTS_RESOURCE);
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                DeferredItem<? extends Item> item = OBJECT_ID_TO_ARTIFACT_ITEM.get(entry.getKey());
                if (item == null) {
                    continue;
                }
                JsonElement artifactSpotChances = entry.getValue().getAsJsonObject().get("ArtifactSpotChances");
                if (artifactSpotChances == null || artifactSpotChances.isJsonNull() || !artifactSpotChances.isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> chanceEntry : artifactSpotChances.getAsJsonObject().entrySet()) {
                    String locationKey = normalizeArtifactChanceLocation(chanceEntry.getKey());
                    ARTIFACT_SPOT_CHANCES
                            .computeIfAbsent(locationKey, key -> new ArrayList<>())
                            .add(new ArtifactChance(item, chanceEntry.getValue().getAsDouble()));
                }
            }
            buildMixedOutdoorArtifactChanceTable();
        } catch (Exception exception) {
            StardewCraft.LOGGER.warn("Failed to load artifact spot chances from {}: {}",
                    VANILLA_OBJECTS_RESOURCE, exception.getMessage());
            ARTIFACT_SPOT_CHANCES.clear();
        }
    }

    private static void buildMixedOutdoorArtifactChanceTable() {
        LinkedHashMap<DeferredItem<? extends Item>, Double> mergedChances = new LinkedHashMap<>();
        LinkedHashMap<DeferredItem<? extends Item>, Integer> mergedCounts = new LinkedHashMap<>();
        for (String sourceLocation : MIXED_OUTDOOR_SOURCE_LOCATIONS) {
            List<ArtifactChance> chances = ARTIFACT_SPOT_CHANCES.get(sourceLocation);
            if (chances == null) {
                continue;
            }
            for (ArtifactChance chance : chances) {
                mergedChances.merge(chance.item, chance.chance, Double::sum);
                mergedCounts.merge(chance.item, 1, Integer::sum);
            }
        }

        List<ArtifactChance> mixedChances = new ArrayList<>();
        for (Map.Entry<DeferredItem<? extends Item>, Double> entry : mergedChances.entrySet()) {
            double averageChance = entry.getValue() / Math.max(1, mergedCounts.getOrDefault(entry.getKey(), 1));
            mixedChances.add(new ArtifactChance(entry.getKey(), averageChance));
        }
        ARTIFACT_SPOT_CHANCES.put(MIXED_OUTDOOR_LOCATION, List.copyOf(mixedChances));
    }

    private static String normalizeArtifactChanceLocation(String locationKey) {
        return "Mine".equals(locationKey) ? "UndergroundMine" : locationKey;
    }

    // ======================== Zone Resolution ========================

    public static String resolveLocation(ServerLevel level, BlockPos pos) {
        if (level.dimension().equals(ModMiningDimensions.STARDEW_MINING)) {
            return UNDERGROUND_MINE_LOCATION;
        }
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            return DEFAULT_LOCATION;
        }
        if (DesertConstants.isInDesertRegion(pos)) {
            return DESERT_LOCATION;
        }
        int x = pos.getX();
        int z = pos.getZ();
        for (Map.Entry<String, List<ZoneRect>> entry : ZONE_RECTS.entrySet()) {
            for (ZoneRect r : entry.getValue()) {
                if (r.contains(x, z)) {
                    return entry.getKey();
                }
            }
        }
        return DEFAULT_LOCATION;
    }

    private static String resolveDropGroup(ServerLevel level, String actualLocation) {
        if (level.dimension().equals(ModMiningDimensions.STARDEW_MINING)) {
            return UNDERGROUND_MINE_LOCATION;
        }
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            return actualLocation;
        }
        if (BEACH_LOCATION.equals(actualLocation) || DESERT_LOCATION.equals(actualLocation)) {
            return actualLocation;
        }
        return MIXED_OUTDOOR_LOCATION;
    }

    private static List<DropEntry> dropsForGroup(String dropGroup) {
        List<DropEntry> drops = new ArrayList<>();
        List<DropEntry> defaultDrops = locationDrops.get(DEFAULT_LOCATION);
        if (defaultDrops == null || defaultDrops.isEmpty()) {
            drops.add(SYNTHETIC_RANDOM_ARTIFACT_DROP);
        } else {
            drops.addAll(defaultDrops);
        }
        List<DropEntry> locDrops = DEFAULT_LOCATION.equals(dropGroup) ? null : locationDrops.get(dropGroup);
        if (locDrops != null) {
            for (DropEntry drop : locDrops) {
                if (!"RANDOM_ARTIFACT_FOR_DIG_SPOT".equals(drop.id)) {
                    drops.add(drop);
                }
            }
        }
        return drops;
    }

    // ======================== Main Drop Logic ========================

    /**
     * Rolls artifact/item drops for a hoe dig at the given position.
     * Mirrors SDV's digUpArtifactSpot(): merges Default + Location drops,
     * sorts by Precedence, evaluates with ContinueOnDrop support.
     *
    * @return list of drops (usually 0 or 1, but ContinueOnDrop entries can add more).
     */
    @SuppressWarnings("null")
    public static List<ItemStack> rollDrops(ServerLevel level, BlockPos pos) {
        return rollDrops(level, pos, null);
    }

    @SuppressWarnings("null")
    public static List<ItemStack> rollDrops(ServerLevel level, BlockPos pos, ServerPlayer player) {
        RandomSource random = level.getRandom();
        String actualLocation = resolveLocation(level, pos);
        String dropGroup = resolveDropGroup(level, actualLocation);
        StardewTimeManager tm = StardewTimeManager.get();
        int season = tm.getCurrentSeason();
        int totalDaysPlayed = (tm.getCurrentYear() - 1) * 112 + season * 28 + tm.getCurrentDay();

        // Build runtime drop list from the selected location group only.
        List<DropEntry> allDrops = dropsForGroup(dropGroup);
        allDrops.sort(Comparator.comparingInt(DropEntry::precedence));

        List<ItemStack> results = new ArrayList<>();

        if (DESERT_LOCATION.equals(actualLocation) && DesertFestivalService.isFestivalOpen()) {
            results.add(new ItemStack(ModItems.CALICO_EGG.get(), 3 + random.nextInt(4)));
            return results;
        }

        List<ItemStack> extensionDrops = WorldLootPoolData.resolve(
                StardewWorldLootPools.ARTIFACT_SPOT,
                dropGroup.toLowerCase(Locale.ROOT),
                level,
                player,
                random);
        if (!extensionDrops.isEmpty()) {
            results.addAll(extensionDrops);
            return results;
        }

        for (DropEntry drop : allDrops) {
            if (random.nextDouble() >= drop.chance) {
                continue;
            }
            if (!drop.condition.test(actualLocation, season, totalDaysPlayed, random, player)) {
                continue;
            }

            if ("RANDOM_ARTIFACT_FOR_DIG_SPOT".equals(drop.id)) {
                ItemStack artifact = rollRandomArtifact(dropGroup, random);
                if (artifact != null) {
                    results.add(artifact);
                    if (!drop.continueOnDrop) break;
                }
                continue;
            }

            if (drop.id.startsWith("LOST_BOOK_OR_ITEM")
                    && player != null
                    && com.stardew.craft.museum.LostBookService.find(player)) {
                if (!drop.continueOnDrop) break;
                continue;
            }

            ItemStack stack = resolveDropItem(drop, random, player);
            if (!stack.isEmpty()) {
                results.add(stack);
                if (!drop.continueOnDrop) {
                    break;
                }
            }
        }

        return results;
    }

    /**
     * Convenience method that returns only the first drop.
     * Most callers just need a single ItemStack.
     */
    public static ItemStack rollDrop(ServerLevel level, BlockPos pos) {
        List<ItemStack> drops = rollDrops(level, pos, null);
        return drops.isEmpty() ? ItemStack.EMPTY : drops.get(0);
    }

    public static ItemStack rollDrop(ServerLevel level, BlockPos pos, ServerPlayer player) {
        List<ItemStack> drops = rollDrops(level, pos, player);
        return drops.isEmpty() ? ItemStack.EMPTY : drops.get(0);
    }

    /**
     * Returns all drops (for callers that support ContinueOnDrop multi-drops).
     */
    public static List<ItemStack> rollAllDrops(ServerLevel level, BlockPos pos) {
        return rollDrops(level, pos, null);
    }

    public static List<ItemStack> rollAllDrops(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return rollDrops(level, pos, player);
    }

    private static ItemStack resolveDropItem(DropEntry drop, RandomSource random, ServerPlayer player) {
        if (drop.id.startsWith("LOST_BOOK_OR_ITEM")) {
            String fallback = drop.id.substring("LOST_BOOK_OR_ITEM".length()).trim();
            return fallback.isEmpty() ? ItemStack.EMPTY : resolveSpecialQueryDrop(fallback, random);
        }
        if (drop.id.startsWith("SECRET_NOTE_OR_ITEM")) {
            return resolveSpecialQueryDrop("(O)390", random);
        }

        Item item;
        if (drop.randomItems != null && !drop.randomItems.isEmpty()) {
            item = drop.randomItems.get(random.nextInt(drop.randomItems.size())).get();
        } else if (drop.item != null) {
            item = drop.item.get();
        } else {
            return ItemStack.EMPTY;
        }
        int minStack = normalizeStackValue(drop.minStack);
        int maxStack = Math.max(minStack, normalizeStackValue(drop.maxStack));
        int count = minStack;
        if (maxStack > minStack) {
            count = minStack + random.nextInt(maxStack - minStack + 1);
        }
        return new ItemStack(item, count);
    }

    private static ItemStack resolveSpecialQueryDrop(String itemId, RandomSource random) {
        DeferredItem<? extends Item> item = resolveDropItemId(itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item.get());
    }

    private static int normalizeStackValue(int value) {
        return value <= 0 ? 1 : value;
    }

    /**
     * Rolls RANDOM_ARTIFACT_FOR_DIG_SPOT: iterates all artifacts with ArtifactSpotChances
     * for the current location, returns the first match.
     */
    private static ItemStack rollRandomArtifact(String location, RandomSource random) {
        List<ArtifactChance> chances = ARTIFACT_SPOT_CHANCES.get(location);
        if (chances == null) return null;
        for (ArtifactChance ac : chances) {
            if (random.nextDouble() < ac.chance) {
                return new ItemStack(ac.item.get());
            }
        }
        return null;
    }
}
