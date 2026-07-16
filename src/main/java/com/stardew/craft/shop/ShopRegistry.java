package com.stardew.craft.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.shop.StardewShopDefinition;
import com.stardew.craft.api.v1.shop.StardewShopEntry;
import com.stardew.craft.api.v1.shop.StardewShopInventoryContext;
import com.stardew.craft.api.v1.shop.StardewShopInventoryProviders;
import com.stardew.craft.core.ModTags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side static registry of shop definitions.
 *
 * SalableItemTags mapping (SDV Data/Shops.json → MC IStardewItem.getItemTypeKey()):
 *
 *   SeedShop  : crop,fruit,seed,fertilizer,cooking,cooking_ingredient,
 *               animal_product,artisan_goods,artisan_animal_quality,forage
 *   FishShop  : fish,legendary_fish,crabpot,fishing equipment
 *   AnimalShop: animal_product,artisan_animal_quality
 *   Blacksmith: mineral plus item tags stardewcraft:ores / stardewcraft:bars / coal
 *
 * Season constants: 0=spring, 1=summer, 2=fall, 3=winter  (matches StardewTimeManager)
 */
public final class ShopRegistry {

    private record TravelingCartPortraitEntry(String npcId, String itemId) {}

    // Season indices – mirrors StardewTimeManager.currentSeason values
    private static final int SPRING = 0;
    private static final int SUMMER = 1;
    private static final int FALL   = 2;
    private static final int WINTER = 3;

    public record ShopDefinition(
        String              shopId,
        String              ownerNpcId,
        String              ownerDialogue,
        List<ShopItemEntry> items,
        /** itemTypeKeys whose items this shop will buy from the player. */
        Set<String>         acceptedSellTypes,
        List<ResourceLocation> inventoryProviders
    ) {
        public ShopDefinition(
                String shopId,
                String ownerNpcId,
                String ownerDialogue,
                List<ShopItemEntry> items,
                Set<String> acceptedSellTypes
        ) {
            this(shopId, ownerNpcId, ownerDialogue, items, acceptedSellTypes, List.of());
        }

        public ShopDefinition {
            items = List.copyOf(items);
            acceptedSellTypes = Set.copyOf(acceptedSellTypes);
            inventoryProviders = List.copyOf(inventoryProviders);
        }

        /**
         * Returns items that should currently be visible in the shop given
         * the current season and year.  Mirrors SDV ShopBuilder.CheckItemCondition.
         */
        public List<ShopItemEntry> getAvailableItems(int season, int year) {
            return items.stream()
                    .filter(e -> e.isAvailableIn(season, year))
                    .collect(Collectors.toList());
        }
    }

    private static final String TRAVELING_CART_RARECROW_ID = "stardewcraft:scarecrow_4";
    private static final String TRAVELING_CART_VANILLA_OBJECTS_RESOURCE =
            "data/stardewcraft/npc/vanilla/data/Objects.json";
    private static final Map<String, String> TRAVELING_CART_OBJECT_PATH_OVERRIDES = Map.ofEntries(
            Map.entry("bomb", "bomb_item"),
            Map.entry("grape_starter", "grape_seeds"),
            Map.entry("hops_starter", "hops_seeds"),
            Map.entry("l_milk", "large_milk"),
            Map.entry("l_goat_milk", "large_goat_milk")
    );
    private static final List<TravelingCartPortraitEntry> TRAVELING_CART_PORTRAITS = List.of(
        new TravelingCartPortraitEntry("abigail", "stardewcraft:abigail_portrait"),
        new TravelingCartPortraitEntry("emily", "stardewcraft:emily_portrait"),
        new TravelingCartPortraitEntry("haley", "stardewcraft:haley_portrait"),
        new TravelingCartPortraitEntry("leah", "stardewcraft:leah_portrait"),
        new TravelingCartPortraitEntry("penny", "stardewcraft:penny_portrait"),
        new TravelingCartPortraitEntry("maru", "stardewcraft:maru_portrait"),
        new TravelingCartPortraitEntry("alex", "stardewcraft:alex_portrait"),
        new TravelingCartPortraitEntry("sebastian", "stardewcraft:sebastian_portrait"),
        new TravelingCartPortraitEntry("harvey", "stardewcraft:harvey_portrait"),
        new TravelingCartPortraitEntry("sam", "stardewcraft:sam_portrait"),
        new TravelingCartPortraitEntry("elliott", "stardewcraft:elliott_portrait"),
        new TravelingCartPortraitEntry("shane", "stardewcraft:shane_portrait"),
        new TravelingCartPortraitEntry("krobus", "stardewcraft:krobus_portrait")
    );
    private static final List<String> TRAVELING_CART_SKILL_BOOKS = List.of(
        "stardewcraft:skill_book_0",
        "stardewcraft:skill_book_1",
        "stardewcraft:skill_book_2",
        "stardewcraft:skill_book_3",
        "stardewcraft:skill_book_4"
    );
    private static final List<String> BOOKSELLER_SKILL_BOOKS = List.of(
        "stardewcraft:skill_book_0",
        "stardewcraft:skill_book_1",
        "stardewcraft:skill_book_2",
        "stardewcraft:skill_book_3",
        "stardewcraft:skill_book_4"
    );
    private static final List<String> BOOKSELLER_RANDOM_POWER_BOOKS = List.of(
        "stardewcraft:book_trash",
        "stardewcraft:book_crabbing",
        "stardewcraft:book_bombs",
        "stardewcraft:book_roe",
        "stardewcraft:book_wild_seeds",
        "stardewcraft:book_woodcutting",
        "stardewcraft:book_defense",
        "stardewcraft:book_friendship",
        "stardewcraft:book_void",
        "stardewcraft:book_marlon",
        "stardewcraft:book_artifact"
    );
    private static final List<String> BOOKSELLER_TRADE_JELLIES = List.of(
        "stardewcraft:cave_jelly",
        "stardewcraft:river_jelly",
        "stardewcraft:sea_jelly"
    );
    private static final List<String> BOOKSELLER_TRADE_BIG_CHESTS = List.of(
        "stardewcraft:wooden_chest",
        "stardewcraft:stone_chest"
    );
    private static final List<String> BOOKSELLER_TRADE_ARTIFACT_ITEMS = List.of(
        "stardewcraft:treasure_chest",
        "stardewcraft:artifact_trove"
    );
    private static volatile List<String> travelingCartRandomObjectCandidates;
    public static ShopDefinition get(String shopId) {
        ResourceLocation modernId = ResourceLocation.tryParse(shopId);
        if (modernId != null) {
            StardewShopDefinition definition = ShopDataLoader.getDefinition(modernId);
            if (definition != null) return fromData(modernId, definition);
        }
        return ShopDataLoader.snapshot().definitions().entrySet().stream()
                .filter(entry -> entry.getValue().legacyId().equals(shopId))
                .findFirst()
                .map(entry -> fromData(entry.getKey(), entry.getValue()))
                .orElse(null);
    }

    public static List<String> allShopIds() {
        List<String> ids = new ArrayList<>();
        ShopDataLoader.snapshot().definitions().entrySet().stream()
                .map(entry -> entry.getValue().legacyId().isBlank()
                        ? entry.getKey().toString() : entry.getValue().legacyId())
                .filter(id -> !ids.contains(id))
                .forEach(ids::add);
        return ids;
    }

    private static ShopDefinition fromData(ResourceLocation id, StardewShopDefinition definition) {
        List<ShopItemEntry> entries = definition.entries().stream()
                .map(ShopRegistry::fromDataEntry)
                .toList();
        return new ShopDefinition(
                definition.legacyId().isBlank() ? id.toString() : definition.legacyId(),
                definition.ownerNpc(),
                definition.ownerDialogue(),
                entries,
                Set.copyOf(definition.acceptedSellTypes()),
                definition.inventoryProviders());
    }

    private static ShopItemEntry fromDataEntry(StardewShopEntry entry) {
        return new ShopItemEntry(
                entry.item(), entry.displayName(), entry.description(), entry.price(), entry.stock(),
                entry.tradeItem().orElse(null), entry.tradeItemCount(), Set.copyOf(entry.seasons()),
                entry.minYear(), entry.minMineLevel(), entry.mailFlag().orElse(null), entry.dayOfWeek(),
                entry.dayOfMonthParity(), entry.purchaseStack(), entry.availableWhen());
    }

    /**
     * Build the filtered item list that should be visible to a specific player.
     * This applies season/year filtering, per-player stock tracking, and recipe-already-known filtering.
     * Must be used both when OPENING the shop and when HANDLING purchases, so that indices match.
     */
    public static List<ShopItemEntry> getFilteredItemsForPlayer(
            String shopId, ShopDefinition shop,
            net.minecraft.server.level.ServerPlayer player) {
        com.stardew.craft.time.StardewTimeManager time = com.stardew.craft.time.StardewTimeManager.get();
        final int season = time.getCurrentSeason();
        final int year = time.getCurrentYear();
        final int dayOfMonth = time.getCurrentDay();
        List<ShopItemEntry> sourceItems = new ArrayList<>(shop.items());
        ResourceLocation modernShopId = ResourceLocation.tryParse(shopId);
        if (modernShopId != null) {
            StardewShopInventoryContext context = new StardewShopInventoryContext(player, modernShopId);
            for (ResourceLocation providerId : shop.inventoryProviders()) {
                var provider = StardewShopInventoryProviders.get(providerId);
                if (provider == null) {
                    StardewCraft.LOGGER.error("[Shop] Missing inventory provider {} for {}", providerId, shopId);
                    continue;
                }
                try {
                    List<StardewShopEntry> provided = provider.provide(context);
                    if (provided != null) provided.stream().map(ShopRegistry::fromDataEntry).forEach(sourceItems::add);
                } catch (RuntimeException exception) {
                    StardewCraft.LOGGER.error("[Shop] Inventory provider {} failed for {}", providerId, shopId, exception);
                }
            }
        }
        List<ShopItemEntry> rawItems = sourceItems.stream()
                .filter(e -> e.isAvailableOnDate(season, year, dayOfMonth))
                .collect(Collectors.toList());

        java.util.UUID playerId = player.getUUID();
        com.stardew.craft.player.PlayerStardewData data =
            com.stardew.craft.player.PlayerDataManager.getPlayerData(player);

        // Gather player conditions for mine-level / mail-flag filtering
        int playerMineLevel = com.stardew.craft.mining.MiningDataManager.getPlayerData(player).getMaxFloorReached();
        java.util.Set<String> playerMailFlags = data.getMailFlags();

        List<ShopItemEntry> result = new ArrayList<>();
        for (ShopItemEntry e : rawItems) {
            boolean conditionsMatch = true;
            for (var condition : e.availableWhen()) {
                boolean allowed = StardewConditions.test(condition, StardewConditionContext.forPlayer(player))
                        .resultOrPartial(message -> StardewCraft.LOGGER.error(
                                "[Shop] Entry condition failed for {} / {}: {}", shopId, e.itemId(), message))
                        .orElse(false);
                if (!allowed) {
                    conditionsMatch = false;
                    break;
                }
            }
            if (!conditionsMatch) continue;
            if ("DesertFestival_EggShop".equals(shopId)
                    && "random:desert_festival_food".equals(e.itemId())) {
                appendDesertFestivalEggShopRandomFood(result, playerId, time);
                continue;
            }
            // SDV parity: never show recipes the player already knows
            if (e.itemId().startsWith("recipe:")) {
                String recipeId = SaloonService.extractRecipeId(e.itemId());
                if (data.isRecipeUnlocked(recipeId)) continue;
            }
            if ("ShadowShop".equals(shopId)
                    && "stardewcraft:stardrop".equals(e.itemId())
                    && data.hasMailFlag(com.stardew.craft.sewer.SewerStoryFlags.SEWER_STARDROP_PURCHASED)) {
                continue;
            }
            if ("ShadowShop".equals(shopId)
                    && "stardewcraft:warp_wand".equals(e.itemId())
                    && data.hasMailFlag(com.stardew.craft.sewer.SewerStoryFlags.RETURN_SCEPTER_PURCHASED)) {
                continue;
            }
            if (com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_SHOP_ID.equals(shopId)
                    && "stardewcraft:stardrop".equals(e.itemId())
                    && data.hasMailFlag(com.stardew.craft.festival.FairFestivalService.FAIR_STARDROP_FLAG)) {
                continue;
            }
            if (shopId.startsWith("Festival_NightMarket_MagicBoat_")
                    && !meetsNightMarketMuseumCondition(player, e.itemId())) {
                continue;
            }
            // SDV parity: mine-level and mail-flag conditions
            if (!e.meetsPlayerConditions(playerMineLevel, playerMailFlags)) continue;
            if ("JojaMart".equals(shopId)
                && "stardewcraft:auto_petter".equals(e.itemId())
                && (!com.stardew.craft.communitycenter.state.CCStoryFlags.isJojaMember(player)
                    || !com.stardew.craft.communitycenter.state.CCStoryFlags.hasFlag(
                        player, com.stardew.craft.communitycenter.state.CCStoryFlags.CC_IS_COMPLETE))) {
                continue;
            }

            int remaining = ShopStockTracker.getRemaining(playerId, shopId, e.itemId(), e.stock());
            if (remaining == 0) continue;

            result.add(remaining == e.stock() ? e : new ShopItemEntry(
                e.itemId(), e.displayName(), e.description(),
                e.price(), remaining, e.tradeItemId(), e.tradeItemCount(),
                e.seasons(), e.minYear(), e.minMineLevel(), e.mailFlag(),
                e.dayOfWeek(), e.dayOfMonthParity(), e.purchaseStack()
            ));
        }

        if ("Traveler".equals(shopId)) {
            net.minecraft.server.level.ServerLevel stardewLevel =
                player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if (stardewLevel != null) {
                appendTravelingCartStock(result, player, data, time, TravelingCartManager.get(stardewLevel));
            }
        }
        if ("Bookseller".equals(shopId)) {
            appendBooksellerStock(result, player, data, time);
        }
        if ("BooksellerTrade".equals(shopId)) {
            appendBooksellerTradeStock(result, player, time);
        }
        if ("Festival_FeastOfTheWinterStar_Pierre".equals(shopId)) {
            appendWinterStarRandomStock(result, player, time);
        }

        if (("Festival_FestivalOfIce_TravelingMerchant".equals(shopId)
                || "Festival_FeastOfTheWinterStar_Pierre".equals(shopId))
                && !data.isDecorationUnlocked(com.stardew.craft.deco.DecorationType.WALLPAPER, "MoreWalls:19")) {
            String wpItemId = "wallpaper:MoreWalls:19";
            int wpRemaining = ShopStockTracker.getRemaining(playerId, shopId, wpItemId, 1);
            if (wpRemaining > 0) {
                ShopItemEntry wallpaperEntry = new ShopItemEntry(wpItemId, "", "", 500, wpRemaining,
                    null, 0, Set.of(), 1, 0, null, -1, 0, 1);
                int insertAt = "Festival_FeastOfTheWinterStar_Pierre".equals(shopId)
                    ? result.size()
                    : Math.min(2, result.size());
                result.add(insertAt, wallpaperEntry);
            }
        }

        // SDV parity: Joja 每日 RANDOM 壁纸/地板 — (WP) 0..111 / (FL) 0..39 @ 250g
        // 追加在列表末尾（与 SDV Shops.json Joja 节最后两条 RANDOM_ITEMS 顺序一致）。
        //
        // 设计：daily seed 决定当天的 styleId（固定，不因已解锁而轮换），stock=1 per-player-per-day。
        // ShopStockTracker 记录购买；已购 → stock=0 → UI 灰显 + ShopPurchasePayload 拒绝。
        // 已拥有的款式（/wallpaper unlock all 调出来的）直接不上架（SDV 也不会在已拥有时显示）。
        if ("JojaMart".equals(shopId)) {
            int dayKey = time.getAbsoluteDay();
            java.util.Random rng = new java.util.Random(dayKey * 2654435761L ^ 0xC0FFEEL);
            int wpId = rng.nextInt(112);
            int flId = rng.nextInt(40);
            // Wallpaper
            if (!data.isDecorationUnlocked(com.stardew.craft.deco.DecorationType.WALLPAPER, String.valueOf(wpId))) {
                String wpItemId = "wallpaper:" + wpId;
                int wpRemaining = ShopStockTracker.getRemaining(playerId, shopId, wpItemId, 1);
                result.add(new ShopItemEntry(wpItemId, "", "", 250, wpRemaining,
                    null, 0, Set.of(), 1, 0, null, -1, 0, 1));
            }
            // Flooring
            if (!data.isDecorationUnlocked(com.stardew.craft.deco.DecorationType.FLOORING, String.valueOf(flId))) {
                String flItemId = "flooring:" + flId;
                int flRemaining = ShopStockTracker.getRemaining(playerId, shopId, flItemId, 1);
                result.add(new ShopItemEntry(flItemId, "", "", 250, flRemaining,
                    null, 0, Set.of(), 1, 0, null, -1, 0, 1));
            }
        }

        // SDV parity: Joja 非会员 1.25x 溢价（PriceModifier "NonMemberMarkup"）
        if ("JojaMart".equals(shopId)
            && !com.stardew.craft.communitycenter.state.CCStoryFlags.isJojaMember(player)) {
            List<ShopItemEntry> marked = new ArrayList<>(result.size());
            for (ShopItemEntry e : result) {
                int markedPrice = (int) Math.round(e.price() * 1.25);
                marked.add(new ShopItemEntry(
                    e.itemId(), e.displayName(), e.description(),
                    markedPrice, e.stock(), e.tradeItemId(), e.tradeItemCount(),
                    e.seasons(), e.minYear(), e.minMineLevel(), e.mailFlag(),
                    e.dayOfWeek(), e.dayOfMonthParity(), e.purchaseStack()
                ));
            }
            return marked;
        }
        return result;
    }

    private static boolean meetsNightMarketMuseumCondition(
            net.minecraft.server.level.ServerPlayer player,
            String itemId) {
        if (!"stardewcraft:scarecrow_7".equals(itemId)
                && !"stardewcraft:scarecrow_8".equals(itemId)) {
            return true;
        }

        Set<String> donated = com.stardew.craft.museum.MuseumDonationData
                .get(player.serverLevel())
                .getDonatedItems(player.getUUID());
        int artifactCount = 0;
        int mineralCount = 0;
        for (String donatedItemId : donated) {
            Item item = com.stardew.craft.museum.MuseumRewardRegistry.resolveItem(donatedItemId);
            if (item == null) continue;
            String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(item));
            if ("stardewcraft.type.artifact".equals(typeKey)) {
                artifactCount++;
            } else if ("stardewcraft.type.mineral".equals(typeKey)) {
                mineralCount++;
            }
        }
        return "stardewcraft:scarecrow_7".equals(itemId)
                ? artifactCount >= 20
                : artifactCount + mineralCount >= 40;
    }

    private static void appendDesertFestivalEggShopRandomFood(
            List<ShopItemEntry> result,
            java.util.UUID playerId,
            com.stardew.craft.time.StardewTimeManager time) {
        List<String> foods = List.of(
            "stardewcraft:spicy_eel",
            "stardewcraft:crab_cakes",
            "stardewcraft:eggplant_parmesan",
            "stardewcraft:pumpkin_soup",
            "stardewcraft:lucky_lunch"
        );
        int index = Math.floorMod((int)(time.getAbsoluteDay() * 1103515245L + 12345L), foods.size());
        String itemId = foods.get(index);
        int remaining = ShopStockTracker.getRemaining(playerId, "DesertFestival_EggShop", itemId, 5);
        if (remaining > 0) {
            result.add(new ShopItemEntry(itemId, "", "", 0, remaining,
                "stardewcraft:calico_egg", 10, Set.of(), 1, 0, null, -1, 0, 1));
        }
    }

    private static void appendTravelingCartStock(
            List<ShopItemEntry> result,
            net.minecraft.server.level.ServerPlayer player,
            com.stardew.craft.player.PlayerStardewData data,
            com.stardew.craft.time.StardewTimeManager time,
            TravelingCartManager manager) {
        int absoluteDay = time.getAbsoluteDay();
        int season = time.getCurrentSeason();
        int year = time.getCurrentYear();
        java.util.UUID playerId = player.getUUID();
        java.util.Set<String> avoidRepeat = new java.util.LinkedHashSet<>();
        java.util.Random shopRandom = createTravelingCartShopRandom(player, absoluteDay);

        List<String> randomObjects = new ArrayList<>(collectTravelingCartRandomObjectCandidates());
        java.util.Collections.shuffle(randomObjects, shopRandom);
        for (int i = 0; i < Math.min(10, randomObjects.size()); i++) {
            String itemId = randomObjects.get(i);
            addTravelingCartEntry(
                result,
                playerId,
                avoidRepeat,
                itemId,
                getTravelingCartObjectPrice(shopRandom, itemId),
                getTravelingCartRareMultiplierStock(shopRandom),
                true
            );
        }

        if (year == 1
                && manager.getVisitsUntilY1Guarantee() == 0
                && travelingCartItemExists("stardewcraft:red_cabbage_seeds")) {
            addTravelingCartEntry(
                result,
                playerId,
                avoidRepeat,
                "stardewcraft:red_cabbage_seeds",
                getTravelingCartObjectPrice(shopRandom, "stardewcraft:red_cabbage_seeds"),
                getTravelingCartRareMultiplierStock(shopRandom),
                true
            );
        }

        List<String> randomFurniture = collectTravelingCartRandomFurnitureCandidates();
        java.util.Collections.shuffle(randomFurniture, shopRandom);
        for (String itemId : randomFurniture) {
            if (avoidRepeat.contains(itemId)) {
                continue;
            }
            addTravelingCartEntry(
                result,
                playerId,
                avoidRepeat,
                itemId,
                getTravelingCartFurniturePrice(shopRandom),
                1,
                true
            );
            break;
        }

        if ((season == SPRING || season == SUMMER) && travelingCartItemExists("stardewcraft:rare_seed")) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:rare_seed", 1000,
                    getTravelingCartRareMultiplierStock(shopRandom), true);
        }

        if ((season == FALL || season == WINTER)
                && travelingCartItemExists(TRAVELING_CART_RARECROW_ID)
                && rollTravelingCartChance(absoluteDay, "cart_rarecrow", 0.4)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, TRAVELING_CART_RARECROW_ID, 4000, 1, false);
        }

        if ((season == FALL || season == WINTER)
                && travelingCartItemExists("stardewcraft:coffee_bean")
                && rollTravelingCartChance(absoluteDay, "cart_coffee_bean", 0.25)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:coffee_bean", 2500, 1, false);
        }

        if (travelingCartItemExists("stardewcraft:red_fez")
                && rollTravelingCartChance(absoluteDay, "cart_fez", 0.1)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:red_fez", 8000, 1, false);
        }

        boolean isCommunityCenterComplete =
                com.stardew.craft.communitycenter.state.CCStoryFlags.hasFlag(
                        player, com.stardew.craft.communitycenter.state.CCStoryFlags.CC_IS_COMPLETE);
        if (isCommunityCenterComplete
                && travelingCartItemExists("stardewcraft:joja_catalogue")
                && rollTravelingCartChance(absoluteDay, "cart_jojaCatalogue", 0.1)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:joja_catalogue", 30000, 1, false);
        }
        if (isCommunityCenterComplete
                && travelingCartItemExists("stardewcraft:junimo_catalogue")
                && rollTravelingCartChance(absoluteDay, "cart_junimoCatalogue", 0.1)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:junimo_catalogue", 70000, 1, false);
        }
        if (travelingCartItemExists("stardewcraft:retro_catalogue")
                && rollTravelingCartChance(absoluteDay, "cart_retroCatalogue", 0.1)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:retro_catalogue", 110000, 1, false);
        }

        net.minecraft.server.level.ServerLevel overworld = player.server.overworld();
        if (overworld != null) {
            com.stardew.craft.npc.runtime.NpcFriendshipDataManager friendship =
                    com.stardew.craft.npc.runtime.NpcFriendshipDataManager.get(overworld);
            for (TravelingCartPortraitEntry portrait : TRAVELING_CART_PORTRAITS) {
                if (!travelingCartItemExists(portrait.itemId())) {
                    continue;
                }
                int points = friendship.getPointsForNpc(playerId, portrait.npcId());
                if (points / 250 >= 14) {
                    addTravelingCartEntry(result, playerId, avoidRepeat, portrait.itemId(), 30000, 1, false);
                }
            }
        }

        if (year >= 25
                && travelingCartItemExists("stardewcraft:tea_set")
                && rollTravelingCartChance(absoluteDay, "teaset", 0.05)) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "stardewcraft:tea_set", 1_000_000,
                    Integer.MAX_VALUE, false);
        }

        if (rollTravelingCartChance(absoluteDay, "travelerSkillBook", 0.05)) {
            List<String> availableSkillBooks = new ArrayList<>();
            for (String itemId : TRAVELING_CART_SKILL_BOOKS) {
                if (travelingCartItemExists(itemId)) {
                    availableSkillBooks.add(itemId);
                }
            }
            if (!availableSkillBooks.isEmpty()) {
                String skillBookId = availableSkillBooks.get(shopRandom.nextInt(availableSkillBooks.size()));
                addTravelingCartEntry(result, playerId, avoidRepeat, skillBookId, 6000, Integer.MAX_VALUE, false);
            }
        }

        if (isTravelingCartMultiplayer(player)
                && travelingCartItemExists("stardewcraft:wedding_ring")
                && !data.isRecipeUnlocked(com.stardew.craft.player.RecipeIdNormalizer.storageId(
                        "stardewcraft:wedding_ring"))) {
            addTravelingCartEntry(result, playerId, avoidRepeat, "recipe:stardewcraft:wedding_ring", 500, 1, false);
        }
    }

    private static void addTravelingCartEntry(
            List<ShopItemEntry> result,
            java.util.UUID playerId,
            java.util.Set<String> avoidRepeat,
            String itemId,
            int price,
            int stock,
            boolean shouldAvoidRepeat) {
        if (shouldAvoidRepeat && !avoidRepeat.add(itemId)) {
            return;
        }
        int remaining = stock == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : ShopStockTracker.getRemaining(playerId, "Traveler", itemId, stock);
        if (remaining == 0) {
            return;
        }
        result.add(new ShopItemEntry(
                itemId,
                "",
                "",
                price,
                remaining,
                null,
                0,
                Set.of(),
                1,
                0,
                null,
                -1,
                0,
                1
        ));
    }

    private static void appendBooksellerStock(
            List<ShopItemEntry> result,
            net.minecraft.server.level.ServerPlayer player,
            com.stardew.craft.player.PlayerStardewData data,
            com.stardew.craft.time.StardewTimeManager time) {
        int absoluteDay = time.getAbsoluteDay();
        int year = time.getCurrentYear();
        java.util.UUID playerId = player.getUUID();
        java.util.Set<String> chosenSkillBooks = new java.util.LinkedHashSet<>();

        if (rollBooksellerChance(player, absoluteDay, "purple", 0.25D)) {
            addBooksellerEntry(result, playerId, "stardewcraft:purple_book", 15000, 1);
        }

        addRandomBooksellerSkillBook(result, player, playerId, absoluteDay, chosenSkillBooks,
                "skill_slot_1", 0.60D, 10000);
        addRandomBooksellerSkillBook(result, player, playerId, absoluteDay, chosenSkillBooks,
                "skill_slot_2", 0.80D, 8000);
        addRandomBooksellerSkillBook(result, player, playerId, absoluteDay, chosenSkillBooks,
                "skill_slot_3", 1.00D, 5000);

        if (year >= 3) {
            String itemId = pickBooksellerItem(player, absoluteDay, "random_power", BOOKSELLER_RANDOM_POWER_BOOKS);
            if (itemId != null) {
                addBooksellerEntry(result, playerId, itemId, 20000, 1);
            }
        }

        addBooksellerEntry(result, playerId, "stardewcraft:book_speed", 15000, 1);
        if (data.getStat("Book_Speed") > 0) {
            addBooksellerEntry(result, playerId, "stardewcraft:book_speed2", 35000, 1);
        }
        addBooksellerEntry(result, playerId, "stardewcraft:book_horse", 25000, 1);
        addBooksellerEntry(result, playerId, "stardewcraft:book_grass", 25000, 1);

        // TODO: Replace this Year 3 fallback with GoldenWalnutsFound >= 100 once Ginger Island state exists.
        if (time.getCurrentYear() >= 3) {
            addBooksellerEntry(result, playerId, "stardewcraft:book_queen_of_sauce", 50000, Integer.MAX_VALUE);
        }

        if (rollBooksellerChance(player, absoluteDay, "extra_foraging", 0.33D)) {
            addBooksellerEntry(result, playerId, "stardewcraft:skill_book_2", 8000, 1);
        }
    }

    private static void appendBooksellerTradeStock(
            List<ShopItemEntry> result,
            net.minecraft.server.level.ServerPlayer player,
            com.stardew.craft.time.StardewTimeManager time) {
        int absoluteDay = time.getAbsoluteDay();
        java.util.UUID playerId = player.getUUID();

        addBooksellerTradeEntry(result, playerId,
            pickBooksellerItem(player, absoluteDay, "roe_jelly", BOOKSELLER_TRADE_JELLIES), 3,
            "stardewcraft:book_roe", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:wood_hard", 20,
            "stardewcraft:book_woodcutting", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:stuffing", 3,
            "stardewcraft:book_defense", 1);
        addBooksellerTradeEntry(result, playerId,
            pickBooksellerItem(player, absoluteDay, "void_big_chest", BOOKSELLER_TRADE_BIG_CHESTS), 1,
            "stardewcraft:book_void", 2);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:mystery_box", 7,
            "stardewcraft:book_mystery", 1);
        addBooksellerTradeEntry(result, playerId,
            pickBooksellerItem(player, absoluteDay, "artifact_trade", BOOKSELLER_TRADE_ARTIFACT_ITEMS), 3,
            "stardewcraft:book_artifact", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:fairy_dust", 8,
            "stardewcraft:purple_book", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:hot_pepper", 2,
            "stardewcraft:skill_book_0", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:deluxe_bait", 30,
            "stardewcraft:skill_book_1", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:wood_normal", 100,
            "stardewcraft:skill_book_2", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:coal", 20,
            "stardewcraft:skill_book_3", 1);
        addBooksellerTradeEntry(result, playerId,
            "stardewcraft:bat_wing", 30,
            "stardewcraft:skill_book_4", 1);
    }

    private static void addRandomBooksellerSkillBook(
            List<ShopItemEntry> result,
            net.minecraft.server.level.ServerPlayer player,
            java.util.UUID playerId,
            int absoluteDay,
            java.util.Set<String> chosenSkillBooks,
            String salt,
            double chance,
            int price) {
        if (!rollBooksellerChance(player, absoluteDay, salt + "_chance", chance)) {
            return;
        }
        List<String> candidates = new ArrayList<>();
        for (String itemId : BOOKSELLER_SKILL_BOOKS) {
            if (!chosenSkillBooks.contains(itemId) && shopItemExists(itemId)) {
                candidates.add(itemId);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        java.util.Collections.shuffle(candidates, createBooksellerRandom(player, absoluteDay, salt));
        String itemId = candidates.get(0);
        chosenSkillBooks.add(itemId);
        addBooksellerEntry(result, playerId, itemId, price, 1);
    }

    private static void addBooksellerEntry(
            List<ShopItemEntry> result,
            java.util.UUID playerId,
            String itemId,
            int price,
            int stock) {
        if (!shopItemExists(itemId)) {
            return;
        }
        int remaining = stock == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : ShopStockTracker.getRemaining(playerId, "Bookseller", itemId, stock);
        if (remaining == 0) {
            return;
        }
        result.add(new ShopItemEntry(
                itemId,
                "",
                "",
                price,
                remaining,
                null,
                0,
                Set.of(),
                1,
                0,
                null,
                -1,
                0,
                1
        ));
    }

    private static void addBooksellerTradeEntry(
            List<ShopItemEntry> result,
            java.util.UUID playerId,
            String rewardItemId,
            int rewardCount,
            String requiredBookItemId,
            int requiredBookCount) {
        if (rewardItemId == null || requiredBookItemId == null || !shopItemExists(rewardItemId) || !shopItemExists(requiredBookItemId)) {
            return;
        }
        int stock = Integer.MAX_VALUE;
        int remaining = ShopStockTracker.getRemaining(playerId, "BooksellerTrade", rewardItemId, stock);
        if (remaining == 0) {
            return;
        }
        result.add(new ShopItemEntry(
                rewardItemId,
                "",
                "",
                0,
                remaining,
                requiredBookItemId,
                requiredBookCount,
                Set.of(),
                1,
                0,
                null,
                -1,
                0,
                rewardCount
        ));
    }

    private static String pickBooksellerItem(
            net.minecraft.server.level.ServerPlayer player,
            int absoluteDay,
            String salt,
            List<String> candidates) {
        List<String> available = new ArrayList<>();
        for (String itemId : candidates) {
            if (shopItemExists(itemId)) {
                available.add(itemId);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        java.util.Collections.shuffle(available, createBooksellerRandom(player, absoluteDay, salt));
        return available.get(0);
    }

    private static java.util.Random createBooksellerRandom(
            net.minecraft.server.level.ServerPlayer player,
            int absoluteDay,
            String salt) {
        long worldSeed = player.server.overworld() != null ? player.server.overworld().getSeed() : 0L;
        long seed = (worldSeed >>> 2) ^ (absoluteDay * 7046029254386353131L) ^ salt.hashCode();
        return new java.util.Random(seed);
    }

    private static void appendWinterStarRandomStock(
            List<ShopItemEntry> result,
            net.minecraft.server.level.ServerPlayer player,
            com.stardew.craft.time.StardewTimeManager time) {
        long saveId = player.server.overworld() != null ? player.server.overworld().getSeed() : 0L;
        com.stardew.craft.util.StardewDeterministicRandom random =
            com.stardew.craft.util.StardewDeterministicRandom.create(time.getAbsoluteDay(), saveId / 2L, 0L);
        List<List<String>> groups = List.of(
            List.of("stardewcraft:triple_shot_espresso", "stardewcraft:powder_melon", "stardewcraft:garlic", "stardewcraft:fire_quartz"),
            List.of("stardewcraft:frozen_tear", "stardewcraft:fried_mushroom", "stardewcraft:duck_egg", "stardewcraft:bread"),
            List.of("stardewcraft:cave_carrot", "stardewcraft:perch", "stardewcraft:clam", "stardewcraft:winter_root")
        );
        int[] prices = {2500, 2500, 500};
        List<ShopItemEntry> randomStock = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            String itemId = groups.get(i).get(random.nextInt(groups.get(i).size()));
            if (!shopItemExists(itemId)) {
                continue;
            }
            int remaining = ShopStockTracker.getRemaining(
                player.getUUID(), "Festival_FeastOfTheWinterStar_Pierre", itemId, 1);
            if (remaining > 0) {
                randomStock.add(new ShopItemEntry(itemId, "", "", prices[i], remaining,
                    null, 0, Set.of(), 1, 0, null, -1, 0, 1));
            }
        }
        result.addAll(0, randomStock);
    }

    private static boolean rollBooksellerChance(
            net.minecraft.server.level.ServerPlayer player,
            int absoluteDay,
            String salt,
            double chance) {
        return createBooksellerRandom(player, absoluteDay, salt).nextDouble() < chance;
    }

    private static boolean shopItemExists(String itemId) {
        try {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            return item != null && item != net.minecraft.world.item.Items.AIR;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static java.util.Random createTravelingCartRandom(int absoluteDay, String salt) {
        return new java.util.Random(absoluteDay * 2654435761L ^ salt.hashCode());
    }

    private static java.util.Random createTravelingCartShopRandom(
            net.minecraft.server.level.ServerPlayer player,
            int absoluteDay) {
        long worldSeed = player.server.overworld() != null ? player.server.overworld().getSeed() : 0L;
        long seed = (worldSeed >>> 1) ^ (absoluteDay * 341873128712L) ^ 132897987541L;
        return new java.util.Random(seed);
    }

    private static boolean rollTravelingCartChance(int absoluteDay, String salt, double chance) {
        return createTravelingCartRandom(absoluteDay, salt).nextDouble() < chance;
    }

    private static int getTravelingCartRareMultiplierStock(java.util.Random shopRandom) {
        return shopRandom.nextDouble() < 0.1 ? 5 : 1;
    }

    private static int getTravelingCartObjectPrice(java.util.Random shopRandom, String itemId) {
        int flatPrice = (shopRandom.nextInt(10) + 1) * 100;
        int multiplier = 3 + shopRandom.nextInt(3);
        int basePrice = Math.max(1, getTravelingCartBasePrice(itemId));
        return Math.max(flatPrice, basePrice * multiplier);
    }

    private static int getTravelingCartFurniturePrice(java.util.Random shopRandom) {
        return 250 * (shopRandom.nextInt(10) + 1);
    }

    private static List<String> collectTravelingCartRandomObjectCandidates() {
        List<String> cached = travelingCartRandomObjectCandidates;
        if (cached != null) {
            return cached;
        }

        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (java.io.InputStream stream = ShopRegistry.class.getClassLoader()
                .getResourceAsStream(TRAVELING_CART_VANILLA_OBJECTS_RESOURCE)) {
            if (stream == null) {
                com.stardew.craft.StardewCraft.LOGGER.warn(
                        "Traveler random object source {} was not found",
                        TRAVELING_CART_VANILLA_OBJECTS_RESOURCE);
                travelingCartRandomObjectCandidates = List.of();
                return travelingCartRandomObjectCandidates;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                String itemId = resolveTravelingCartRandomObjectItemId(entry.getKey(), entry.getValue().getAsJsonObject());
                if (itemId != null && seen.add(itemId)) {
                    out.add(itemId);
                }
            }
        } catch (Exception ex) {
            com.stardew.craft.StardewCraft.LOGGER.warn(
                    "Failed to load Traveler random object candidates from {}: {}",
                    TRAVELING_CART_VANILLA_OBJECTS_RESOURCE,
                    ex.getMessage());
            travelingCartRandomObjectCandidates = List.of();
            return travelingCartRandomObjectCandidates;
        }

        travelingCartRandomObjectCandidates = List.copyOf(out);
        return travelingCartRandomObjectCandidates;
    }

    private static List<String> collectTravelingCartRandomFurnitureCandidates() {
        List<String> out = new ArrayList<>();
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!"stardewcraft.type.furniture".equals(StardewItemDataApi.getTypeKey(stack))) {
                continue;
            }
            if (StardewItemDataApi.getSellPrice(stack) <= 0) {
                continue;
            }
            net.minecraft.resources.ResourceLocation key =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (key != null) {
                out.add(key.toString());
            }
        }
        return out;
    }

    private static String resolveTravelingCartRandomObjectItemId(String sourceObjectId, JsonObject data) {
        if (!sourceObjectId.chars().allMatch(Character::isDigit)) {
            return null;
        }

        int numericId = Integer.parseInt(sourceObjectId);
        if (numericId < 2 || numericId > 789) {
            return null;
        }
        if (getJsonInt(data, "Price", 0) <= 0) {
            return null;
        }
        if (getJsonBoolean(data, "ExcludeFromRandomSale", false)) {
            return null;
        }
        if (getJsonInt(data, "Category", -999) == -999) {
            return null;
        }

        String objectType = getJsonString(data, "Type");
        if ("Quest".equals(objectType) || "Minerals".equals(objectType) || "Arch".equals(objectType)) {
            return null;
        }

        String name = getJsonString(data, "Name");
        if (name.isBlank()) {
            return null;
        }

        String path = normalizeTravelingCartObjectName(name);
        path = TRAVELING_CART_OBJECT_PATH_OVERRIDES.getOrDefault(path, path);
        String itemId = "stardewcraft:" + path;
        if (!travelingCartItemExists(itemId) || getTravelingCartBasePrice(itemId) <= 0) {
            return null;
        }
        return itemId;
    }

    private static String normalizeTravelingCartObjectName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace(".", "")
                .replace("&", "and");
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        return normalized.replaceAll("^_+|_+$", "");
    }

    private static String getJsonString(JsonObject data, String member) {
        JsonElement element = data.get(member);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int getJsonInt(JsonObject data, String member, int fallback) {
        JsonElement element = data.get(member);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private static boolean getJsonBoolean(JsonObject data, String member, boolean fallback) {
        JsonElement element = data.get(member);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    private static int getTravelingCartBasePrice(String itemId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) {
            return 0;
        }
        java.util.Optional<net.minecraft.world.item.Item> item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) {
            return 0;
        }
        return Math.max(0, StardewItemDataApi.getSellPrice(new ItemStack(item.get())));
    }

    private static boolean travelingCartItemExists(String itemId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        return id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
    }

    private static boolean isTravelingCartMultiplayer(net.minecraft.server.level.ServerPlayer player) {
        return player.server.getPlayerCount() > 1;
    }

    /**
     * Returns the sell price for an item at a given shop, or 0 if this shop
     * won't buy it.
     *
     * Logic (mirrors SDV ShopMenu.highlightItemToSell + sellToStorePrice):
     *   1. The item must expose Stardew metadata with a sell price greater than 0.
     *   2. The item's typeKey must be in shop.acceptedSellTypes.
     *   3. Sell price = StardewItemDataApi.getSellPrice(stack) * sellPercentage (SDV default 1.0).
     *      → we use 1.0, same as SDV default.
     */
    public static int getSellPrice(ItemStack stack,
                                   ShopDefinition shop) {
        if (stack.isEmpty()) return 0;
        int basePrice = StardewItemDataApi.getSellPrice(stack);
        if (basePrice <= 0) return 0;
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        if (!shop.acceptedSellTypes().contains(typeKey) && !matchesShopSpecificSellRule(stack, shop.shopId())) return 0;
        return basePrice; // SDV: sellPercentage default 1.0f
    }

    private static boolean matchesShopSpecificSellRule(ItemStack stack, String shopId) {
        if (!"Blacksmith".equals(shopId)) {
            return false;
        }
        return stack.is(ModTags.Items.ORES)
                || stack.is(ModTags.Items.BARS)
                || "stardewcraft:coal".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
