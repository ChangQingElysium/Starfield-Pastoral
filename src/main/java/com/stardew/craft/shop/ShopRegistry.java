package com.stardew.craft.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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

    private static final TagKey<Item> BLACKSMITH_ORES_TAG = itemTag("ores");
    private static final TagKey<Item> BLACKSMITH_BARS_TAG = itemTag("bars");

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
        Set<String>         acceptedSellTypes
    ) {
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

    private static final Map<String, ShopDefinition> REGISTRY = new LinkedHashMap<>();
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
    static {
        // -------------------------------------------------------------------
        // Pierre – General Store
        // Source of truth: 数据包/StardewCore/data/stardew/function/shop/pierre_inventory.json
        //   and SDV 1.6 源文件/Content/Data/Shops.json (SeedShop section).
        //
        // Season mapping (SDV SEASON condition → our season int):
        //   spring→0, summer→1, fall→2, winter→3
        // Year-2+ items: garlic_seeds, red_cabbage_seeds, artichoke_seeds
        // After day-15: basic/quality fertilizer, speed_gro
        // Year-2+ fertilizers: quality_fertilizer, quality_retaining_soil, deluxe_speed_gro
        // 不卖（SDV Qi 胡桃室专属）: deluxe_fertilizer, deluxe_retaining_soil, hyper_speed_gro
        // -------------------------------------------------------------------
        REGISTRY.put("SeedShop", new ShopDefinition(
            "SeedShop",
            "Pierre",
            "stardewcraft.shop.seedshop.dialogue",
            List.of(
                // ---- Spring ----
                entry("stardewcraft:parsnip_seeds",     20,  SPRING),
                entry("stardewcraft:green_bean_seeds",  60,  SPRING),
                entry("stardewcraft:cauliflower_seeds", 80,  SPRING),
                entry("stardewcraft:potato_seeds",      50,  SPRING),
                entry("stardewcraft:tulip_seeds",       20,  SPRING),
                entry("stardewcraft:kale_seeds",        70,  SPRING),
                entry("stardewcraft:blue_jazz_seeds",   30,  SPRING),
                entryYear("stardewcraft:garlic_seeds",  40,  SPRING, 2),
                entryYear("stardewcraft:rice_shoot",    40,  SPRING, 2),

                // ---- Summer ----
                entry("stardewcraft:melon_seeds",            80, SUMMER),
                entry("stardewcraft:tomato_seeds",           50, SUMMER),
                entry("stardewcraft:blueberry_seeds",        80, SUMMER),
                entry("stardewcraft:hot_pepper_seeds",       40, SUMMER),
                entry("stardewcraft:wheat_seeds",            10, SUMMER),
                entry("stardewcraft:radish_seeds",           40, SUMMER),
                entry("stardewcraft:poppy_seeds",           100, SUMMER),
                entry("stardewcraft:summer_spangle_seeds",   50, SUMMER),
                entry("stardewcraft:hops_seeds",             60, SUMMER),
                entry("stardewcraft:corn_seeds",            150, SUMMER),
                entry("stardewcraft:sunflower_seeds",       200, SUMMER),
                entryYear("stardewcraft:red_cabbage_seeds", 100, SUMMER, 2),

                // ---- Fall ----
                entry("stardewcraft:eggplant_seeds",    20,  FALL),
                entry("stardewcraft:corn_seeds",       150,  FALL),
                entry("stardewcraft:pumpkin_seeds",    100,  FALL),
                entry("stardewcraft:bok_choy_seeds",    50,  FALL),
                entry("stardewcraft:yam_seeds",         60,  FALL),
                entry("stardewcraft:cranberry_seeds",  240,  FALL),
                entry("stardewcraft:wheat_seeds",       10,  FALL),
                entry("stardewcraft:sunflower_seeds",  200,  FALL),
                entry("stardewcraft:fairy_rose_seeds", 200,  FALL),
                entry("stardewcraft:amaranth_seeds",    70,  FALL),
                entry("stardewcraft:grape_seeds",       60,  FALL),
                entryYear("stardewcraft:artichoke_seeds", 30, FALL, 2),

                // ---- Year-round ----
                entryAllSeasons("stardewcraft:grass_starter",         100),
                // 注：blue_grass_starter 在 SDV 中是沙漠/Marnie + Mastery 解锁专属，
                // Pierre 不出售，已移至 AnimalShop（Marnie）。

                // ---- Fertilizers (SDV SeedShop) ----
                //   Basic 3 (DAYS_PLAYED 15 in SDV, 近似为 Y1 全季售卖)
                entryAllSeasons("stardewcraft:basic_fertilizer",      100),
                entryAllSeasons("stardewcraft:basic_retaining_soil",  100),
                entryAllSeasons("stardewcraft:speed_gro",             100),
                //   Quality/Deluxe-Speed 3 (SDV YEAR 2 条件, 价格来自 Price*2 markup)
                entryAllSeasonsYear("stardewcraft:quality_fertilizer",       150, 2),
                entryAllSeasonsYear("stardewcraft:quality_retaining_soil",   150, 2),
                entryAllSeasonsYear("stardewcraft:deluxe_speed_gro",         150, 2),
                //   注：deluxe_fertilizer / deluxe_retaining_soil / hyper_speed_gro
                //   在 SDV 1.6 仅 Qi 胡桃房出售，Pierre 不卖（已从本清单移除）。

                // ---- Machinery recipes (SDV: Dehydrator sold as recipe, IsRecipe=true, 5000×2=10000g) ----
                entryRecipe("stardewcraft:dehydrator",                     10000),

                // ---- Cooking ingredients (SDV: year-round, Price=-1 → 使用物品基础 Price, 无 markup) ----
                entryAllSeasons("stardewcraft:sugar",         100),
                entryAllSeasons("stardewcraft:wheat_flour",   100),
                entryAllSeasons("stardewcraft:oil",           200),
                entryAllSeasons("stardewcraft:rice",          200),
                entryAllSeasons("stardewcraft:vinegar",       200),

                // ---- Fruit tree saplings (SDV SeedShop: (O)628-633, all seasons) ----
                entryAllSeasons("stardewcraft:cherry_sapling",       1700),
                entryAllSeasons("stardewcraft:apricot_sapling",      1000),
                entryAllSeasons("stardewcraft:orange_sapling",       2000),
                entryAllSeasons("stardewcraft:peach_sapling",        3000),
                entryAllSeasons("stardewcraft:pomegranate_sapling",  3000),
                entryAllSeasons("stardewcraft:apple_sapling",        2000),
                entryAllSeasons("stardewcraft:pineapple",        300)
            ),
            Set.of(
                "stardewcraft.type.crop",
                "stardewcraft.type.fruit",
                "stardewcraft.type.forage",
                "stardewcraft.type.seed",
                "stardewcraft.type.fertilizer",
                "stardewcraft.type.cooking",
                "stardewcraft.type.cooking_ingredient",
                "stardewcraft.type.animal_product",
                "stardewcraft.type.artisan_goods",
                "stardewcraft.type.artisan_animal_quality"
            )
        ));

        // -------------------------------------------------------------------
        // Willy – Fish Shop  (SDV SalableItemTags = bait/fish/sell_at_fish_shop/tackle)
        // NOTE: SDV also sells recycling_machine and warp_totem_beach, but
        //   neither is registered in our game yet → flagged for user.
        // -------------------------------------------------------------------
        REGISTRY.put("FishShop", new ShopDefinition(
            "FishShop",
            "Willy",
            "stardewcraft.shop.fishshop.dialogue",
            List.of(
                // Available from the start
                entryAllSeasons("stardewcraft:bait",              5),
                entryAllSeasons("stardewcraft:lead_bobber",     200),
                entryAllSeasons("stardewcraft:spinner",         500),
                entryAllSeasons("stardewcraft:trap_bobber",     500),
                entryAllSeasons("stardewcraft:cork_bobber",     750),
                entryAllSeasons("stardewcraft:treasure_hunter", 750),
                entryAllSeasons("stardewcraft:dressed_spinner",1000),
                entryAllSeasons("stardewcraft:barbed_hook",    1000),
                entryAllSeasons("stardewcraft:quality_bobber", 2000),
                // Rods – limited stock 1 (mirrors SDV AvailableStockLimit = Player)
                entryStock("stardewcraft:fiberglass_rod", 1800, 1),
                entryStock("stardewcraft:iridium_rod",    7500, 1),
                entryStock("stardewcraft:training_rod",     25, 1),
                // 海滩拾取物（SDV: beach forage, 作为合成材料的获取途径）
                entryAllSeasons("stardewcraft:coral",         80),
                entryAllSeasons("stardewcraft:sea_urchin",   160)
                // SDV also: recycling_machine (free, 1 stock), warp_totem_beach (500g)
                // → NOT in our game yet
            ),
            Set.of(
                "stardewcraft.type.fish",
                "stardewcraft.type.legendary_fish",
                "stardewcraft.type.crabpot",
                "stardewcraft.type.fishing"
            )
        ));

        // -------------------------------------------------------------------
        // Marnie – Ranch Shop
        // 注：blue_grass_starter 在 SDV 中是 Mastery 解锁专属，
        // 当前版本暂不开放任何获取途径（以后再决定挂在哪里）。
        // -------------------------------------------------------------------
        REGISTRY.put("AnimalShop", new ShopDefinition(
            "AnimalShop",
            "Marnie",
            "stardewcraft.shop.animalshop.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:hay",           50),
                entryStock("stardewcraft:milk_pail",        2000, 1),
                entryStock("stardewcraft:shears",           3000, 1),
                entryAllSeasons("stardewcraft:heater",      2000),
                entryStock("stardewcraft:auto_grabber",    25000, 1),
                entryStock("stardewcraft:auto_petter",     50000, 1),
                entryAllSeasonsYear("stardewcraft:book_animal_catalogue", 5000, 2)
            ),
            Set.of(
                "stardewcraft.type.animal_product",
                "stardewcraft.type.artisan_animal_quality"
            )
        ));

        // -------------------------------------------------------------------
        // Sandy – Oasis Shop  (SDV SalableItemTags same as SeedShop)
        // Sells desert-exclusive seeds year-round, regardless of season.
        // -------------------------------------------------------------------
        REGISTRY.put("OasisShop", new ShopDefinition(
            "OasisShop",
            "Sandy",
            "stardewcraft.shop.oasis.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:rhubarb_seeds",    100),
                entryAllSeasons("stardewcraft:starfruit_seeds",  400),
                entryAllSeasons("stardewcraft:beet_seeds",        20),
                entryAllSeasons("stardewcraft:coconut",          400),
                entryAllSeasons("stardewcraft:cactus_fruit",     150)
            ),
            Set.of(
                "stardewcraft.type.crop",
                "stardewcraft.type.fruit",
                "stardewcraft.type.forage",
                "stardewcraft.type.seed"
            )
        ));

        // -------------------------------------------------------------------
        // Clint – Blacksmith Material Shop
        // SDV parity: Data/Shops.json → "Blacksmith" section
        // Copper Ore 75→150g, Iron Ore 150→250g, Coal 150→250g, Gold Ore 400→750g
        // -------------------------------------------------------------------
        REGISTRY.put("Blacksmith", new ShopDefinition(
            "Blacksmith",
            "Clint",
            "stardewcraft.npc.clint.shop_dialogue",
            List.of(
                entryAllSeasons("stardewcraft:copper_ore",     75),
                entryAllSeasons("stardewcraft:iron_ore",      150),
                entryAllSeasons("stardewcraft:coal",          150),
                entryAllSeasons("stardewcraft:gold_ore",      400),
                // 基础工具（补充新手获取途径）
                entryStock("stardewcraft:pickaxe",            100, 1),
                entryStock("stardewcraft:axe",                100, 1),
                entryStock("stardewcraft:hoe",                100, 1),
                entryStock("stardewcraft:watering_can",       100, 1),
                entryStock("stardewcraft:scythe",             100, 1),
                // 铜锅补购 — 仅对已完成 CC Fish Tank（area 2）的玩家开放
                entryMail("stardewcraft:copper_pan",         2500, "ccFishTank")
            ),
            Set.of("stardewcraft.type.mineral")
        ));

        // -------------------------------------------------------------------
        // Gus – Saloon
        // SDV parity: Data/Shops.json → "Saloon" section
        // 8 consumable items + 9 cooking recipes (recipe: prefix = unlock-only)
        // Recipes: stock 1, once learned they disappear (server checks unlocked)
        // -------------------------------------------------------------------
        REGISTRY.put("Saloon", new ShopDefinition(
            "Saloon",
            "Gus",
            "stardewcraft.shop.saloon.dialogue",
            List.of(
                // ---- Consumable food items ----
                entryAllSeasons("stardewcraft:beer",         400),
                entryAllSeasons("stardewcraft:salad",        220),
                entryAllSeasons("stardewcraft:bread",        120),
                entryAllSeasons("stardewcraft:spaghetti",    240),
                entryAllSeasons("stardewcraft:pizza",        600),
                entryAllSeasons("stardewcraft:coffee",       300),
                // ---- Cooking recipes (SDV: sold as recipe unlocks) ----
                entryRecipe("stardewcraft:hashbrowns",        50),
                entryRecipe("stardewcraft:omelet",           100),
                entryRecipe("stardewcraft:pancakes",         100),
                entryRecipe("stardewcraft:bread",            100),
                entryRecipe("stardewcraft:tortilla",         100),
                entryRecipe("stardewcraft:pizza",            150),
                entryRecipe("stardewcraft:popsicle",         150),
                entryRecipe("stardewcraft:maki_roll",        300),
                entryRecipe("stardewcraft:cookie",           300),
                entryRecipe("stardewcraft:triple_shot_espresso", 5000)
            ),
            Set.of() // Saloon doesn't buy items from player
        ));

        // -------------------------------------------------------------------
        // Harvey – Hospital / Clinic
        // SDV parity: Data/Shops.json → "Hospital" section
        // Only 2 items: Energy Tonic (1000g) and Muscle Remedy (1000g)
        // -------------------------------------------------------------------
        REGISTRY.put("Hospital", new ShopDefinition(
            "Hospital",
            "Harvey",
            "stardewcraft.shop.hospital.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:energy_tonic",   1000),
                entryAllSeasons("stardewcraft:muscle_remedy",  1000)
            ),
            Set.of() // Hospital doesn't buy items from player
        ));

        // -------------------------------------------------------------------
        // Marlon – Adventurer's Guild
        // SDV parity: Data/Shops.json → "AdventureShop" section
        // Items use MINE_LOWEST_LEVEL_REACHED / PLAYER_HAS_MAIL conditions.
        // Items NOT sold by Marlon (obtained elsewhere) are omitted:
        //   - Neptune's Glaive, Broken Trident → fishing treasure
        //   - Insect Head → Bug Killer goal (Gil)
        //   - Dark Sword → Haunted Skull drop
        //   - Meowmere → not in SDV shop
        //   - Dwarf/Dragontooth/Infinity weapons → volcano / forge (deferred)
        //   - Ossified Blade, Holy Blade, Yeti Tooth → mine chest / special
        //   - Burglar's Shank, Wicked Kris → mine drops / chests
        //   - Gil reward rings removed (slime_charmer, savage, vampire, etc.)
        //   - Craftable rings removed (warrior, ring_of_yoba, sturdy, thorns)
        // -------------------------------------------------------------------
        REGISTRY.put("AdventureShop", new ShopDefinition(
            "AdventureShop",
            "Marlon",
            "stardewcraft.shop.adventureshop.dialogue",
            List.of(
                // ---- Swords (SDV prices + mine-level conditions) ----
                entryAllSeasons("stardewcraft:rusty_sword",          250),   // no condition
                entryAllSeasons("stardewcraft:wooden_blade",         250),   // no condition
                entryMine("stardewcraft:steel_smallsword",           750, 20),
                entryMine("stardewcraft:silver_saber",               750, 20),
                entryMine("stardewcraft:pirate_sword",               850, 25),
                entryMine("stardewcraft:cutlass",                   1500, 25),
                entryMine("stardewcraft:iron_edge",                 2000, 40),
                entryMine("stardewcraft:forest_sword",              2000, 40),
                entryMine("stardewcraft:tempered_broadsword",       4000, 55),
                entryMine("stardewcraft:bone_sword",                6000, 75),
                entryMine("stardewcraft:steel_falchion",            9000, 90),
                entryMine("stardewcraft:obsidian_edge",             9000, 90),
                entryMine("stardewcraft:lava_katana",              25000, 120),
                entryMail("stardewcraft:galaxy_sword",             50000, "galaxySword"),

                // ---- Daggers (SDV prices + mine-level conditions) ----
                entryAllSeasons("stardewcraft:carving_knife",        100),   // no condition
                entryMine("stardewcraft:wind_spire",                 500, 15),
                entryMine("stardewcraft:elf_blade",                  750, 20),
                entryMine("stardewcraft:iron_dirk",                  500, 15),
                entryMine("stardewcraft:crystal_dagger",            4500, 60),
                entryMine("stardewcraft:shadow_dagger",             2000, 45),
                entryMail("stardewcraft:galaxy_dagger",            35000, "galaxySword"),

                // ---- Clubs (skip per user request — "棍棒类武器先不做了") ----
                // entryMine("stardewcraft:femur",                   350, 10),

                // ---- Boots (SDV prices + mine-level conditions) ----
                entryAllSeasons("stardewcraft:sneakers",             500),   // no condition
                entryMine("stardewcraft:rubber_boots",               500, 10),
                entryMine("stardewcraft:leather_boots",              500, 10),
                entryMine("stardewcraft:tundra_boots",               750, 50),
                entryMine("stardewcraft:thermal_boots",             1250, 40),
                entryMine("stardewcraft:combat_boots",              2000, 40),
                entryMine("stardewcraft:dark_boots",                2500, 80),
                entryMine("stardewcraft:firewalker_boots",          2000, 80),
                entryMine("stardewcraft:space_boots",               5000, 110),

                // ---- Rings (only items sold in SDV AdventureShop) ----
                entryAllSeasons("stardewcraft:small_glow_ring",     1000),
                entryMine("stardewcraft:glow_ring",                 2000, 40),
                entryAllSeasons("stardewcraft:small_magnet_ring",   1000),
                entryMine("stardewcraft:magnet_ring",               2000, 80),
                entryMine("stardewcraft:immunity_band",             4000, 80)
                // NOTE: slime_charmer, warrior, vampire, savage, ring_of_yoba,
                // sturdy, burglar's, crabshell, napalm, thorns, lucky, hot_java,
                // protection, soul_sapper, phoenix → obtained via Gil rewards
                // or crafting, NOT sold by Marlon.
            ),
            Set.of(
                "stardewcraft.type.monster_loot",
                "stardewcraft.type.boots",
                "stardewcraft.type.ring",
                "stardewcraft.type.weapon",
                "stardewcraft.type.weapon.sword",
                "stardewcraft.type.weapon.dagger",
                "stardewcraft.type.weapon.club",
                "stardewcraft.type.weapon.slingshot"
            )
        ));

        // -------------------------------------------------------------------
        // Dwarf – Mine Entrance Shop
        // SDV parity: Data/Shops.json → "Dwarf" section
        // Sells bombs, mining consumables, and misc items.
        // Items we don't have yet are skipped.
        // -------------------------------------------------------------------
        REGISTRY.put("DwarfShop", new ShopDefinition(
            "DwarfShop",
            "Dwarf",
            "stardewcraft.shop.dwarf.dialogue",
            List.of(
                // Bombs (SDV exact prices)
                entryAllSeasons("stardewcraft:cherry_bomb",   450),   // SDV: 450g
                entryAllSeasons("stardewcraft:bomb_item",    1000),   // SDV: 1000g
                entryAllSeasons("stardewcraft:mega_bomb",    1600),   // SDV: 1600g
                entryAllSeasons("stardewcraft:mine_totem",   1000),   // Mine Totem: 1000g
                entryAllSeasons("stardewcraft:life_elixir",  2000),   // SDV: 2000g
                entryAllSeasons("stardewcraft:oil_of_garlic", 3000),  // SDV: 3000g
                entryStock("stardewcraft:book_bombs",        4000, 1)
                // Miner's Treat → not yet implemented
                // Rarecrow #6, Cobblestone Path, Weathered Floor recipe → not yet implemented
            ),
            Set.of() // Dwarf doesn't buy items from player
        ));

        // -------------------------------------------------------------------
        // Krobus – Shadow Shop
        // SDV parity baseline: sewer shop, excluding unimplemented furniture/
        // cohabitation items for now.
        // -------------------------------------------------------------------
        REGISTRY.put("ShadowShop", new ShopDefinition(
            "ShadowShop",
            "Krobus",
            "stardewcraft.shop.shadowshop.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:void_essence",     100),
                entryAllSeasons("stardewcraft:solar_essence",     80),
                entryAllSeasons("stardewcraft:void_egg",        5000),
                entryStock("stardewcraft:stardrop",            20000, 1),
                entryStock("stardewcraft:warp_wand",         2000000, 1),
                entryDay("stardewcraft:omni_geode",              300, 1),
                entryDayStock("stardewcraft:iridium_sprinkler", 10000, 4, 1)
            ),
            Set.of()
        ));

        // -------------------------------------------------------------------
        // Robin – Carpenter Shop
        // SDV parity: Data/Shops.json → "Carpenter" section
        // Sells raw materials (Wood, Stone, Hardwood) + recipes/furniture.
        // Items we don't have yet are skipped per user rule.
        // -------------------------------------------------------------------
        REGISTRY.put("CarpenterShop", new ShopDefinition(
            "CarpenterShop",
            "Robin",
            "stardewcraft.shop.carpenter.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:wood_normal",   10),
                entryAllSeasons("stardewcraft:stone",         20),
                entryAllSeasons("stardewcraft:wood_hard",    200),
                entryAllSeasons("stardewcraft:moss",          50),
                // Wallpaper & flooring
                entryAllSeasons("stardewcraft:wallpaper_block", 2),
                entryAllSeasons("stardewcraft:flooring_block",  2),
                // Basic furniture
                entryAllSeasons("stardewcraft:bed_1",         100),
                entryMail("stardewcraft:bed_2",              6000, "robinDeluxeBed"),
                entryAllSeasons("stardewcraft:chair_1",        50),
                entryAllSeasons("stardewcraft:oak_table",     100),
                entryAllSeasons("stardewcraft:light_1",        75),
                entryAllSeasons("stardewcraft:dresser_1",     200),
                entryAllSeasons("stardewcraft:carpet_1",       25),
                entryAllSeasons("stardewcraft:photo_frame",    50),
                // Utility / farm upgrades
                entryAllSeasons("stardewcraft:cooking_pot",   5000),
                entryAllSeasons("stardewcraft:fridge",        3000),
                entryAllSeasons("stardewcraft:shipping_bin",  2500),

                entryAllSeasons("stardewcraft:incubator",     5000),
                // Animal building utilities
                entryAllSeasons("stardewcraft:hay_hopper",             2000),
                entryAllSeasons("stardewcraft:feed_trough",            1000),
                entryAllSeasons("stardewcraft:autofeed_trough_upgrader", 10000),
                entryAllSeasons("stardewcraft:fish_pond_bucket",        500),
                entryAllSeasons("stardewcraft:fish_net",                250),
                // Catalogue & tools
                entryAllSeasons("stardewcraft:furniture_catalogue", 200000),
                entryAllSeasons("stardewcraft:paintbrush",   0),
                // Workbenches
                entryAllSeasons("stardewcraft:wood_workbench",  500),
                entryAllSeasons("stardewcraft:stone_workbench", 500)
            ),
            Set.of("stardewcraft.type.resource")
        ));

        // -------------------------------------------------------------------
        // Desert Trader – 沙漠骆驼商人
        // SDV parity: Data/Shops.json → "DesertTrade" section
        // 1:1 item mapping (skipped per user rule: hats, furniture, Krobus pendant).
        // Staircase (BC)71 → stardewcraft:mine_ladder substitute.
        // Day-of-week: 0=Mon..6=Sun. Order preserved from vanilla JSON.
        // -------------------------------------------------------------------
        // SDV parity: DesertTrade owner has Portrait="" and Dialogues set from
        // Strings\StringsFromCSFiles (NPC random lines, not shown in the shop menu).
        // The shop menu itself does NOT draw a portrait/dialogue panel for the camel
        // trader — leave ownerNpcId / ownerDialogue empty so ShopScreen skips the left panel.
        REGISTRY.put("DesertTrade", new ShopDefinition(
            "DesertTrade",
            "",
            "",
            List.of(
                // 1. Artifact Trove ← 5× Omni Geode
                entryTrade("stardewcraft:artifact_trove",        "stardewcraft:omni_geode",    5),
                // 2. Warp Totem Desert (item) ← 3× Omni Geode
                entryTrade("stardewcraft:warp_totem_desert",     "stardewcraft:omni_geode",    3),
                // 3. Triple Shot Espresso ← 1× Diamond
                entryTrade("stardewcraft:triple_shot_espresso",  "stardewcraft:diamond",       1),
                // 4. Spicy Eel ← 1× Ruby
                entryTrade("stardewcraft:spicy_eel",             "stardewcraft:ruby",          1),
                // 5. Mega Bomb ← 5× Iridium Ore
                entryTrade("stardewcraft:mega_bomb",             "stardewcraft:iridium_ore",   5),
                // 6. Bomb ← 5× Quartz
                entryTrade("stardewcraft:bomb_item",             "stardewcraft:quartz",        5),
                // 7. Hay ×3 per purchase ← 1× Omni Geode (Monday)
                entryTradeDayStackSize("stardewcraft:hay",       "stardewcraft:omni_geode",    1, 0, 3),
                // 8. Fiber ← 5× Stone (Tuesday)
                entryTradeDay("stardewcraft:fiber",              "stardewcraft:stone",         5, 1),
                // 9. Cloth ← 3× Aquamarine (Wednesday)
                entryTradeDay("stardewcraft:cloth",              "stardewcraft:aquamarine",    3, 2),
                // 10. Magic Rock Candy ← 3× Prismatic Shard (Thursday, per-player stock 1)
                entryTradeDayStock("stardewcraft:magic_rock_candy", "stardewcraft:prismatic_shard", 3, 3, 1),
                // 11. Cheese ← 1× Emerald (Friday)
                entryTradeDay("stardewcraft:cheese",             "stardewcraft:emerald",       1, 4),
                // 12-15. Seed-pack rotation (Saturday) — each pack trades for the next season's
                entryTradeDay("stardewcraft:spring_seeds",       "stardewcraft:summer_seeds",  2, 5),
                entryTradeDay("stardewcraft:summer_seeds",       "stardewcraft:fall_seeds",    2, 5),
                entryTradeDay("stardewcraft:fall_seeds",         "stardewcraft:winter_seeds",  2, 5),
                entryTradeDay("stardewcraft:winter_seeds",       "stardewcraft:spring_seeds",  2, 5),
                // 16. Staircase (SDV BC:71) → mine_ladder substitute ← 1× Jade (Sunday)
                entryTradeDay("stardewcraft:mine_ladder",        "stardewcraft:jade",          1, 6),
                // 17. Warp Totem Desert RECIPE ← 10× Iridium Bar
                entryTradeRecipe("stardewcraft:warp_totem_desert","stardewcraft:iridium_bar", 10)
                // Skipped (user rule):
                //   (O)808  Void Ghost Pendant / Krobus marriage item
                //   (F)1971, (F)2508, MidnightBeachBed, MidnightBeachDoubleBed, DarkPiano — furniture
                //   (H)72, (H)73 (odd), (H)74 (even) — hats
                // SDV Owners condition "Closed Winter 15/16/17" not implemented yet (TODO).
            ),
            Set.of() // DesertTrader doesn't buy items from player
        ));

        // -------------------------------------------------------------------
        // Island Trader – 姜岛商人
        // SDV parity: Data/Shops.json → "IslandTrade" fruit-tree saplings.
        // The full trader inventory is not implemented here yet; these are the
        // two vanilla tropical fruit tree sapling trades.
        // -------------------------------------------------------------------
        REGISTRY.put("IslandTrade", new ShopDefinition(
            "IslandTrade",
            "",
            "",
            List.of(
                entryTrade("stardewcraft:banana_sapling", "stardewcraft:dragon_tooth", 5),
                entryTrade("stardewcraft:mango_sapling",  "stardewcraft:mussel",      75)
            ),
            Set.of()
        ));

        REGISTRY.put("DesertFestival_EggShop", new ShopDefinition(
            "DesertFestival_EggShop",
            "",
            "",
            List.of(
                entryTradeStock("stardewcraft:mystery_box", "stardewcraft:calico_egg", 20, 10),
                entryTradeDay("stardewcraft:strawberry_seeds", "stardewcraft:calico_egg", 5, 0),
                entryTrade("stardewcraft:mega_bomb", "stardewcraft:calico_egg", 15),
                entryTradeDay("stardewcraft:strawberry_seeds", "stardewcraft:calico_egg", 6, 2),
                entryTradeDayStockStackSize("stardewcraft:mixed_seeds", "stardewcraft:calico_egg", 5, 1, 10, 3),
                entryTradeDayStock("stardewcraft:magic_rock_candy", "stardewcraft:calico_egg", 250, 2, 1),
                entryTrade("random:desert_festival_food", "stardewcraft:calico_egg", 10),
                entryTrade("stardewcraft:skill_book_2", "stardewcraft:calico_egg", 100)
            ),
            Set.of()
        ));

        // -------------------------------------------------------------------
        // Stardew Valley Fair prize shop
        // Source of truth: SDV 1.6 源文件/Content/Data/Shops.json →
        // "Festival_StardewValleyFair_StarTokens" plus Event.cs starTokenShop.
        //
        // Vanilla shop metadata:
        //   Currency=1 (Farmer.festivalScore / star tokens)
        //   Owner portrait="" and no dialogue, so no portrait panel.
        //   Each item has AvailableStock=1, AvailableStockLimit=Player.
        //
        // User scope: Fedora (H)19 and Dried Sunflowers (F)1307 are skipped
        // until their corresponding project assets/items are in scope.
        // Light Green Rug (F)2488 is mapped to the confirmed carpet_19 item.
        // -------------------------------------------------------------------
        REGISTRY.put(com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_SHOP_ID, new ShopDefinition(
            com.stardew.craft.festival.FairFestivalService.STAR_TOKEN_SHOP_ID,
            "",
            "",
            List.of(
                entryStock("stardewcraft:scarecrow_1", 800, 1),    // (BC)110 Rarecrow #1
                entryStock("stardewcraft:stardrop", 2000, 1),      // (O)434, hidden once CF_Fair is set
                entryStock("stardewcraft:prize_ticket", 1000, 1),  // (O)PrizeTicket
                entryStock("stardewcraft:carpet_19", 500, 1)       // (F)2488 Light Green Rug substitute
            ),
            Set.of()
        ));

        REGISTRY.put("DesertFestival_Abigail", desertFestivalVendorShop("DesertFestival_Abigail", "abigail", List.of(
            eggStock("stardewcraft:abbys_planchette", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Alex", desertFestivalVendorShop("DesertFestival_Alex", "alex", List.of(
            eggStock("stardewcraft:muscle_remedy", 25, 1),
            eggStock("stardewcraft:fried_egg", 10, 5),
            eggStock("stardewcraft:milk", 10, 5),
            eggStock("stardewcraft:pancakes", 10, 5),
            eggStock("stardewcraft:hashbrowns", 10, 5),
            eggStock("stardewcraft:alexs_bat", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Caroline", desertFestivalVendorShop("DesertFestival_Caroline", "caroline", List.of(
            eggStock("stardewcraft:green_tea", 10, 5),
            eggStock("stardewcraft:dandelion", 10, 5)
        )));
        REGISTRY.put("DesertFestival_Clint", desertFestivalVendorShop("DesertFestival_Clint", "clint", List.of(
            eggStock("stardewcraft:coal", 5, 50),
            eggStock("stardewcraft:copper_ore", 2, 100),
            eggStock("stardewcraft:iron_ore", 4, 100)
        )));
        REGISTRY.put("DesertFestival_Demetrius", desertFestivalVendorShop("DesertFestival_Demetrius", "demetrius", List.of(
            eggStock("stardewcraft:nautilus_shell", 50, 1),
            eggStock("stardewcraft:spice_berry", 10, 3),
            eggStock("stardewcraft:deluxe_speed_gro", 2, 100)
        )));
        REGISTRY.put("DesertFestival_Elliott", desertFestivalVendorShop("DesertFestival_Elliott", "elliott", List.of(
            eggStock("stardewcraft:crab", 30, 5),
            eggStock("stardewcraft:squid_ink", 30, 5),
            eggStock("stardewcraft:elliotts_pencil", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Emily", desertFestivalVendorShop("DesertFestival_Emily", "emily", List.of(
            eggStock("stardewcraft:cloth", 25, 3),
            egg("stardewcraft:prismatic_shard", 500),
            eggStock("stardewcraft:sea_urchin", 5, 5)
        )));
        REGISTRY.put("DesertFestival_Evelyn", desertFestivalVendorShop("DesertFestival_Evelyn", "evelyn", List.of(
            eggStock("stardewcraft:cookie", 20, 5),
            eggStock("stardewcraft:mixed_seeds", 4, 25),
            eggStock("stardewcraft:daffodil", 15, 5)
        )));
        REGISTRY.put("DesertFestival_George", desertFestivalVendorShop("DesertFestival_George", "george", List.of(
            eggStock("stardewcraft:coffee", 10, 10),
            eggStock("stardewcraft:vinegar", 5, 20),
            eggStock("stardewcraft:morel", 30, 1),
            eggStock("stardewcraft:artifact_trove", 10, 5)
        )));
        REGISTRY.put("DesertFestival_Gus", desertFestivalVendorShop("DesertFestival_Gus", "gus", List.of(
            eggStock("stardewcraft:escargot", 30, 5),
            eggStock("stardewcraft:lobster_bisque", 30, 5),
            eggStock("stardewcraft:squid_ink_ravioli", 30, 5),
            eggStock("stardewcraft:eggplant_parmesan", 30, 5)
        )));
        REGISTRY.put("DesertFestival_Haley", desertFestivalVendorShop("DesertFestival_Haley", "haley", List.of(
            eggStock("stardewcraft:pink_cake", 20, 5),
            eggStock("stardewcraft:haleys_iron", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Harvey", desertFestivalVendorShop("DesertFestival_Harvey", "harvey", List.of(
            eggStock("stardewcraft:coffee", 10, 5),
            eggStock("stardewcraft:harveys_mallet", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Jas", desertFestivalVendorShop("DesertFestival_Jas", "jas", List.of(
            eggStock("stardewcraft:ancient_doll", 1, 1),
            eggStock("stardewcraft:rabbits_foot", 100, 1),
            eggStock("stardewcraft:magic_rock_candy", 300, 1)
        )));
        REGISTRY.put("DesertFestival_Jodi", desertFestivalVendorShop("DesertFestival_Jodi", "jodi", List.of(
            eggStock("stardewcraft:crispy_bass", 25, 5),
            eggStock("stardewcraft:mixed_seeds", 5, 25)
        )));
        REGISTRY.put("DesertFestival_Kent", desertFestivalVendorShop("DesertFestival_Kent", "kent", List.of(
            eggStock("stardewcraft:fiddlehead_fern", 10, 5),
            eggStock("stardewcraft:hazelnut", 10, 5)
        )));
        REGISTRY.put("DesertFestival_Leah", desertFestivalVendorShop("DesertFestival_Leah", "leah", List.of(
            eggStock("stardewcraft:salad", 20, 5),
            eggStock("stardewcraft:wood_hard", 10, 10),
            eggStock("stardewcraft:leahs_whittler", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Leo", desertFestivalVendorShop("DesertFestival_Leo", "leo", List.of(
            eggStock("stardewcraft:poi", 20, 5),
            eggStock("stardewcraft:tropical_curry", 30, 5)
        )));
        REGISTRY.put("DesertFestival_Marnie", desertFestivalVendorShop("DesertFestival_Marnie", "marnie", List.of(
            eggStock("stardewcraft:grass_starter", 2, 100),
            eggStock("stardewcraft:hay", 1, 150),
            eggStock("stardewcraft:truffle_oil", 50, 1)
        )));
        REGISTRY.put("DesertFestival_Maru", desertFestivalVendorShop("DesertFestival_Maru", "maru", List.of(
            eggStock("stardewcraft:iridium_bar", 50, 2),
            eggStock("stardewcraft:strawberry_seeds", 10, 20),
            eggStock("stardewcraft:marus_wrench", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Pam", desertFestivalVendorShop("DesertFestival_Pam", "pam", List.of(
            egg("stardewcraft:beer", 20),
            egg("stardewcraft:mead", 20),
            egg("stardewcraft:pale_ale", 30)
        )));
        REGISTRY.put("DesertFestival_Penny", desertFestivalVendorShop("DesertFestival_Penny", "penny", List.of(
            eggStock("stardewcraft:pennys_fryer", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Pierre", desertFestivalVendorShop("DesertFestival_Pierre", "pierre", List.of(
            eggStock("stardewcraft:squid", 10, 5),
            eggStock("stardewcraft:wheat_flour", 2, 100),
            eggStock("stardewcraft:oil", 4, 100)
        )));
        REGISTRY.put("DesertFestival_Robin", desertFestivalVendorShop("DesertFestival_Robin", "robin", List.of(
            eggStock("stardewcraft:stone", 1, 500),
            eggStock("stardewcraft:wood_normal", 1, 500)
        )));
        REGISTRY.put("DesertFestival_Sam", desertFestivalVendorShop("DesertFestival_Sam", "sam", List.of(
            eggStock("stardewcraft:maple_bar", 20, 5),
            eggStock("stardewcraft:joja_cola", 2, 6),
            eggStock("stardewcraft:sams_old_guitar", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Sebastian", desertFestivalVendorShop("DesertFestival_Sebastian", "sebastian", List.of(
            eggStock("stardewcraft:frozen_geode", 10, 5),
            eggStock("stardewcraft:energy_tonic", 25, 5),
            eggStock("stardewcraft:sebs_lost_mace", 70, 1)
        )));
        REGISTRY.put("DesertFestival_Shane", desertFestivalVendorShop("DesertFestival_Shane", "shane", List.of(
            eggStock("stardewcraft:pizza", 5, 5),
            eggStock("stardewcraft:pepper_poppers", 30, 3)
        )));
        REGISTRY.put("DesertFestival_Vincent", desertFestivalVendorShop("DesertFestival_Vincent", "vincent", List.of(
            eggStock("stardewcraft:fiber", 1, 1),
            eggStock("stardewcraft:bug_meat", 1, 3),
            eggStock("stardewcraft:sap", 1, 1),
            eggStock("stardewcraft:cave_carrot", 2, 1),
            eggStock("stardewcraft:skeletal_hand", 2, 1),
            eggStock("stardewcraft:clay", 1, 20),
            eggStock("stardewcraft:chipped_amphora", 1, 1)
        )));

        REGISTRY.put("Traveler", new ShopDefinition(
            "Traveler",
            "",
            "",
            List.of(),
            Set.of()
        ));

        REGISTRY.put("Bookseller", new ShopDefinition(
            "Bookseller",
            "",
            "",
            List.of(),
            Set.of("stardewcraft.type.book")
        ));

        REGISTRY.put("BooksellerTrade", new ShopDefinition(
            "BooksellerTrade",
            "",
            "",
            List.of(),
            Set.of()
        ));

        REGISTRY.put("Festival_EggFestival_Pierre", new ShopDefinition(
            "Festival_EggFestival_Pierre",
            "Pierre",
            "stardewcraft.shop.eggfestival.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:lawn_flamingo", 400),
                entryStock("stardewcraft:plush_bunny", 2000, 1),
                entryAllSeasons("stardewcraft:strawberry_seeds", 100),
                entryAllSeasons("stardewcraft:pastel_banner", 1000),
                entryAllSeasons("stardewcraft:standing_hoe", 1000)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_FlowerDance_Pierre", new ShopDefinition(
            "Festival_FlowerDance_Pierre",
            "Pierre",
            "stardewcraft.shop.flowerdance.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:seasonal_decor", 350),
                entryAllSeasons("stardewcraft:flower_cluster", 250),
                entryStock("stardewcraft:daffodil", 50, 10),
                entryStock("stardewcraft:dandelion", 50, 10),
                entryStock("stardewcraft:scarecrow_5", 2500, 1)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_Luau_Pierre", new ShopDefinition(
            "Festival_Luau_Pierre",
            "Pierre",
            "stardewcraft.shop.luau.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:luau_totem", 1000),
                entryStock("stardewcraft:starfruit", 3000, 1),
                entryAllSeasons("stardewcraft:luau_torch", 700)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_DanceOfTheMoonlightJellies_Pierre", new ShopDefinition(
            "Festival_DanceOfTheMoonlightJellies_Pierre",
            "Pierre",
            "stardewcraft.shop.moonlight_jellies.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:water_lantern", 800)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_SpiritsEve_Pierre", new ShopDefinition(
            "Festival_SpiritsEve_Pierre",
            "Pierre",
            "stardewcraft.shop.spirit_eve.dialogue",
            List.of(
                // SDV Data/Shops.json Festival_SpiritsEve_Pierre:
                // (BC)113 @ 5000 stock 1, (O)746 @ 750 stock 5,
                // (O)746 Recipe @ 2000, (BC)47 @ 350, (F)2870 @ 4000.
                entryStock("stardewcraft:scarecrow_2", 5000, 1),
                entryStock("stardewcraft:spirit_eve_jack_o_lantern", 750, 5),
                entryRecipe("stardewcraft:spirit_eve_jack_o_lantern", 2000),
                entryAllSeasons("stardewcraft:fair_grave_stone", 350),
                entryAllSeasons("stardewcraft:carpet_20", 4000),
                entryTrade("stardewcraft:wisp_trail_elixir", "stardewcraft:prismatic_shard", 1)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_FestivalOfIce_TravelingMerchant", new ShopDefinition(
            "Festival_FestivalOfIce_TravelingMerchant",
            "",
            "",
            List.of(
                // SDV Data/Shops.json Festival_FestivalOfIce_TravelingMerchant:
                // (BC)136, (F)1440, (WP)MoreWalls:19, (F)800, (O)238, (O)239, (O)236.
                // Ice Dreams, Ice Rug, Winter Dining Table, Ice Banner, and Winter End Table
                // are intentionally skipped until their project assets exist. Wallpaper is injected
                // in getFilteredItemsForPlayer using the same decoration unlock path as JojaMart.
                entryStock("stardewcraft:scarecrow_4", 5000, 1),
                entryAllSeasons("stardewcraft:winter_star_tree", 5000),
                entryAllSeasons("stardewcraft:light_6", 3000),
                entryStock("stardewcraft:cranberry_sauce", 200, 1),
                entryStock("stardewcraft:stuffing", 200, 1),
                entryStock("stardewcraft:pumpkin_soup", 250, 1)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_FeastOfTheWinterStar_Pierre", new ShopDefinition(
            "Festival_FeastOfTheWinterStar_Pierre",
            "",
            "stardewcraft.festival.winter25.dialogue.pierre",
            List.of(
                // SDV Data/Shops.json Festival_FeastOfTheWinterStar_Pierre:
                // (F)2798 Large Red Rug @ 1000, (F)1440 Tree of the Winter Star @ 5000,
                // and (WP)MoreWalls:19 @ 500. The three RANDOM_ITEMS groups are appended
                // per day below using vanilla CreateDaySaveRandom ordering.
                entryAllSeasons("stardewcraft:carpet_17", 1000),
                entryAllSeasons("stardewcraft:winter_star_tree", 5000)
            ),
            Set.of()
        ));

        // -------------------------------------------------------------------
        // Night Market boats
        // Source of truth: SDV 1.6 Content/Data/Shops.json.
        // Missing project assets are omitted. The decoration boat's missing
        // seasonal plants are represented by the project's existing plant set.
        // -------------------------------------------------------------------
        REGISTRY.put("Festival_NightMarket_DecorationBoat", new ShopDefinition(
            "Festival_NightMarket_DecorationBoat",
            "",
            "stardewcraft.shop.night_market.decoration_boat.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:winter_star_large_green_candy_cane", 200),
                entryAllSeasons("stardewcraft:winter_star_green_candy_canes", 200),
                entryAllSeasons("stardewcraft:winter_star_mixed_candy_canes", 200),
                entryAllSeasons("stardewcraft:winter_star_red_candy_canes", 200),
                entryAllSeasons("stardewcraft:winter_star_large_red_candy_cane", 200),
                entryAllSeasons("stardewcraft:seasonal_decor", 500),
                entryAllSeasons("stardewcraft:bonsai_1", 500),
                entryAllSeasons("stardewcraft:bonsai_2", 500),
                entryAllSeasons("stardewcraft:bonsai_3", 500),
                entryAllSeasons("stardewcraft:bonsai_4", 500),
                entryAllSeasons("stardewcraft:bonsai_5_wall", 500),
                entryAllSeasons("stardewcraft:bonsai_bush", 500),
                entryAllSeasons("stardewcraft:potted_plant_1", 500),
                entryAllSeasons("stardewcraft:potted_plant_2", 500),
                entryAllSeasons("stardewcraft:potted_plant_3", 500),
                entryAllSeasons("stardewcraft:potted_plant_4", 500),
                entryAllSeasons("stardewcraft:potted_plant_5", 500),
                entryAllSeasons("stardewcraft:potted_plant_6", 500)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_NightMarket_MagicBoat_Day1", new ShopDefinition(
            "Festival_NightMarket_MagicBoat_Day1",
            "",
            "stardewcraft.shop.night_market.magic_boat.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:fair_grave_stone", 200),
                entryAllSeasons("stardewcraft:parsnip_seeds", 10),
                entryAllSeasons("stardewcraft:green_bean_seeds", 30),
                entryAllSeasons("stardewcraft:cauliflower_seeds", 40),
                entryAllSeasons("stardewcraft:potato_seeds", 25),
                entryAllSeasons("stardewcraft:tulip_seeds", 10),
                entryAllSeasons("stardewcraft:kale_seeds", 35),
                entryAllSeasons("stardewcraft:blue_jazz_seeds", 15),
                entryAllSeasonsYear("stardewcraft:garlic_seeds", 20, 2),
                entryAllSeasons("stardewcraft:scarecrow_7", 5000),
                entryAllSeasons("stardewcraft:scarecrow_8", 5000)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_NightMarket_MagicBoat_Day2", new ShopDefinition(
            "Festival_NightMarket_MagicBoat_Day2",
            "",
            "stardewcraft.shop.night_market.magic_boat.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:melon_seeds", 40),
                entryAllSeasons("stardewcraft:tomato_seeds", 25),
                entryAllSeasons("stardewcraft:blueberry_seeds", 40),
                entryAllSeasons("stardewcraft:hot_pepper_seeds", 20),
                entryAllSeasons("stardewcraft:wheat_seeds", 5),
                entryAllSeasons("stardewcraft:radish_seeds", 20),
                entryAllSeasons("stardewcraft:poppy_seeds", 50),
                entryAllSeasons("stardewcraft:summer_spangle_seeds", 25),
                entryAllSeasons("stardewcraft:hops_seeds", 30),
                entryAllSeasons("stardewcraft:corn_seeds", 75),
                entryAllSeasons("stardewcraft:sunflower_seeds", 200),
                entryAllSeasonsYear("stardewcraft:red_cabbage_seeds", 50, 2),
                entryAllSeasons("stardewcraft:scarecrow_7", 5000),
                entryAllSeasons("stardewcraft:scarecrow_8", 5000)
            ),
            Set.of()
        ));

        REGISTRY.put("Festival_NightMarket_MagicBoat_Day3", new ShopDefinition(
            "Festival_NightMarket_MagicBoat_Day3",
            "",
            "stardewcraft.shop.night_market.magic_boat.dialogue",
            List.of(
                entryAllSeasons("stardewcraft:corn_seeds", 75),
                entryAllSeasons("stardewcraft:eggplant_seeds", 10),
                entryAllSeasons("stardewcraft:pumpkin_seeds", 50),
                entryAllSeasons("stardewcraft:bok_choy_seeds", 25),
                entryAllSeasons("stardewcraft:yam_seeds", 30),
                entryAllSeasons("stardewcraft:cranberry_seeds", 120),
                entryAllSeasons("stardewcraft:wheat_seeds", 5),
                entryAllSeasons("stardewcraft:fairy_rose_seeds", 100),
                entryAllSeasons("stardewcraft:amaranth_seeds", 35),
                entryAllSeasons("stardewcraft:grape_seeds", 30),
                entryAllSeasons("stardewcraft:sunflower_seeds", 200),
                entryAllSeasonsYear("stardewcraft:artichoke_seeds", 15, 2),
                entryAllSeasons("stardewcraft:scarecrow_7", 5000),
                entryAllSeasons("stardewcraft:scarecrow_8", 5000)
            ),
            Set.of()
        ));

        // -------------------------------------------------------------------
        // Joja Mart — SDV source: 源文件/Content/Data/Shops.json "Joja" section.
        //
        // SDV pricing rule (PriceModifiers):
        //   !PLAYER_HAS_MAIL Current JojaMember → price * 1.25
        //   JojaMember → base price (same as Pierre).
        // 本项目在 {@link JojaMartService#handleJojaInteraction} 中统一 ×1.25 应用非会员
        // 溢价；ShopRegistry 这里仅存基础价（会员价）。
        //
        // 基础价格与 Pierre 相同（SDV 的 UseObjectDataPrice / IgnoreShopPriceModifiers 组合
        // 让 Joja 基础价 == Pierre 基础价）。
        // -------------------------------------------------------------------
        REGISTRY.put("JojaMart", new ShopDefinition(
            "JojaMart",
            "",
            "",
            List.of(
                // ---- Joja 独占商品 ----
                entryAllSeasons("stardewcraft:joja_cola",       75),        // (O)167
                // Auto Petter — 原版要求 Joja 路线完成后的 502261 事件；
                // 本项目在 getFilteredItemsForPlayer 里按 JojaMember + ccIsComplete 过滤。
                entryAllSeasons("stardewcraft:auto_petter",  50000),
                // 家具（JojaCatalogue / JojaCouch）不做 — 以后如果加家具系统再补
                // 壁纸 / 地板 — SDV 每日 RANDOM，250g/份。通过 wallpaper:{id} / flooring:{id}
                // 前缀条目实现，在 getFilteredItemsForPlayer 中按当前日种子动态注入。

                // ---- Spring 种子 ----
                entry("stardewcraft:parsnip_seeds",     20,  SPRING),
                entry("stardewcraft:green_bean_seeds",  60,  SPRING),
                entry("stardewcraft:cauliflower_seeds", 80,  SPRING),
                entry("stardewcraft:potato_seeds",      50,  SPRING),
                entry("stardewcraft:tulip_seeds",       20,  SPRING),
                entry("stardewcraft:kale_seeds",        70,  SPRING),
                entry("stardewcraft:blue_jazz_seeds",   30,  SPRING),

                // ---- Summer 种子 ----
                entry("stardewcraft:melon_seeds",       80,  SUMMER),
                entry("stardewcraft:tomato_seeds",      50,  SUMMER),
                entry("stardewcraft:wheat_seeds",       10,  SUMMER),
                entry("stardewcraft:radish_seeds",      40,  SUMMER),
                entry("stardewcraft:blueberry_seeds",   80,  SUMMER),
                entry("stardewcraft:hops_seeds",        60,  SUMMER),
                entry("stardewcraft:poppy_seeds",      100,  SUMMER),
                entry("stardewcraft:summer_spangle_seeds", 50, SUMMER),
                // SDV Joja 对 sunflower_seeds 明确写 Price=100（Pierre 的 200 被覆盖）
                entry("stardewcraft:sunflower_seeds",  100,  SUMMER),

                // ---- Fall 种子 ----
                entry("stardewcraft:corn_seeds",       150,  FALL),
                entry("stardewcraft:eggplant_seeds",    20,  FALL),
                entry("stardewcraft:wheat_seeds",       10,  FALL),
                entry("stardewcraft:pumpkin_seeds",    100,  FALL),
                entry("stardewcraft:amaranth_seeds",    70,  FALL),
                entry("stardewcraft:grape_seeds",       60,  FALL),
                entry("stardewcraft:yam_seeds",         60,  FALL),
                entry("stardewcraft:bok_choy_seeds",    50,  FALL),
                entry("stardewcraft:cranberry_seeds",  240,  FALL),
                entry("stardewcraft:sunflower_seeds",  100,  FALL),
                entry("stardewcraft:fairy_rose_seeds", 200,  FALL),

                // ---- 年化杂货（SDV Joja 全季在售）----
                entryAllSeasons("stardewcraft:grass_starter",   100), // (O)297
                entryAllSeasons("stardewcraft:sugar",           100), // (O)245
                entryAllSeasons("stardewcraft:wheat_flour",     100), // (O)246
                entryAllSeasons("stardewcraft:rice",            200)  // (O)423
            ),
            Set.of(
                "stardewcraft.type.crop",
                "stardewcraft.type.fruit",
                "stardewcraft.type.forage",
                "stardewcraft.type.seed",
                "stardewcraft.type.fertilizer",
                "stardewcraft.type.cooking",
                "stardewcraft.type.cooking_ingredient",
                "stardewcraft.type.animal_product",
                "stardewcraft.type.artisan_goods",
                "stardewcraft.type.artisan_animal_quality"
            )
        ));
    }

    // -------------------------------------------------------------------
    // Entry helpers (server-side only; seasons/minYear not sent over network)
    // -------------------------------------------------------------------

    /** Seasonal item available from year 1+, infinite stock. */
    private static ShopItemEntry entry(String id, int price, int season) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(season), 1, 0, null, -1, 0, 1);
    }

    /** Seasonal item, custom min-year, infinite stock. */
    private static ShopItemEntry entryYear(String id, int price, int season, int minYear) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(season), minYear, 0, null, -1, 0, 1);
    }

    /** Year-round item (all seasons) from year 1+, infinite stock. */
    private static ShopItemEntry entryAllSeasons(String id, int price) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(), 1, 0, null, -1, 0, 1);
    }

    /** Year-round item with custom minYear, infinite stock. */
    private static ShopItemEntry entryAllSeasonsYear(String id, int price, int minYear) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(), minYear, 0, null, -1, 0, 1);
    }

    /** Year-round item with limited stock, from year 1+. */
    private static ShopItemEntry entryStock(String id, int price, int stock) {
        return new ShopItemEntry(id, "", "", price, stock,
                null, 0, Set.of(), 1, 0, null, -1, 0, 1);
    }

    private static ShopDefinition desertFestivalVendorShop(String shopId, String npcId, List<ShopItemEntry> items) {
        return new ShopDefinition(shopId, npcId, "stardewcraft.festival.desertfestival.dialogue.vendor", items, Set.of());
    }

    private static ShopItemEntry egg(String id, int eggCost) {
        return entryTrade(id, "stardewcraft:calico_egg", eggCost);
    }

    private static ShopItemEntry eggStock(String id, int eggCost, int stock) {
        return entryTradeStock(id, "stardewcraft:calico_egg", eggCost, stock);
    }

    /** Year-round item sold only on a specific day-of-week (0=Mon..6=Sun). */
    private static ShopItemEntry entryDay(String id, int price, int dayOfWeek) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(), 1, 0, null, dayOfWeek, 0, 1);
    }

    /** Year-round item, specific day-of-week, per-player limited stock. */
    private static ShopItemEntry entryDayStock(String id, int price, int dayOfWeek, int stock) {
        return new ShopItemEntry(id, "", "", price, stock,
                null, 0, Set.of(), 1, 0, null, dayOfWeek, 0, 1);
    }

    /**
     * Recipe unlock entry: stock 1 (per player, once learned it's gone).
     * Uses "recipe:" prefix so server/client know this is a recipe unlock, not a physical item.
     */
    private static ShopItemEntry entryRecipe(String dishId, int price) {
        return new ShopItemEntry("recipe:" + dishId, "", "", price, 1,
                null, 0, Set.of(), 1, 0, null, -1, 0, 1);
    }

    /** Year-round item with mine-level requirement, infinite stock. */
    private static ShopItemEntry entryMine(String id, int price, int minMineLevel) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(), 1, minMineLevel, null, -1, 0, 1);
    }

    /** Year-round item with mail-flag requirement, infinite stock. */
    private static ShopItemEntry entryMail(String id, int price, String mailFlag) {
        return new ShopItemEntry(id, "", "", price, Integer.MAX_VALUE,
                null, 0, Set.of(), 1, 0, mailFlag, -1, 0, 1);
    }

    // ---- DesertTrade helpers (trade-only; gold price always 0) ----------

    /** Year-round trade-only item (no gold price), infinite stock. */
    private static ShopItemEntry entryTrade(String id, String tradeItemId, int tradeCount) {
        return new ShopItemEntry(id, "", "", 0, Integer.MAX_VALUE,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, -1, 0, 1);
    }

    /** Year-round trade-only item, per-player limited stock. */
    private static ShopItemEntry entryTradeStock(String id, String tradeItemId, int tradeCount, int stock) {
        return new ShopItemEntry(id, "", "", 0, stock,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, -1, 0, 1);
    }

    /** Year-round trade-only item, sold only on a specific day-of-week (0=Mon..6=Sun). */
    private static ShopItemEntry entryTradeDay(String id, String tradeItemId, int tradeCount, int dayOfWeek) {
        return new ShopItemEntry(id, "", "", 0, Integer.MAX_VALUE,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, dayOfWeek, 0, 1);
    }

    /** Year-round trade-only item, specific day-of-week, per-player limited stock. */
    private static ShopItemEntry entryTradeDayStock(String id, String tradeItemId, int tradeCount, int dayOfWeek, int stock) {
        return new ShopItemEntry(id, "", "", 0, stock,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, dayOfWeek, 0, 1);
    }
    /** Year-round trade-only item, specific day-of-week, limited stock, grants multiple items per purchase. */
    private static ShopItemEntry entryTradeDayStockStackSize(String id, String tradeItemId, int tradeCount, int dayOfWeek, int stock, int stackPerPurchase) {
        return new ShopItemEntry(id, "", "", 0, stock,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, dayOfWeek, 0, stackPerPurchase);
    }
    /** Year-round trade item, specific day-of-week, grants `stack` items per single purchase (SDV MinStack). */
    private static ShopItemEntry entryTradeDayStackSize(String id, String tradeItemId, int tradeCount, int dayOfWeek, int stackPerPurchase) {
        return new ShopItemEntry(id, "", "", 0, Integer.MAX_VALUE,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, dayOfWeek, 0, stackPerPurchase);
    }
    /** Year-round trade recipe unlock (uses recipe: prefix). */
    private static ShopItemEntry entryTradeRecipe(String dishId, String tradeItemId, int tradeCount) {
        return new ShopItemEntry("recipe:" + dishId, "", "", 0, 1,
                tradeItemId, tradeCount, Set.of(), 1, 0, null, -1, 0, 1);
    }

    // -------------------------------------------------------------------

    public static ShopDefinition get(String shopId) {
        return REGISTRY.get(shopId);
    }

    public static List<String> allShopIds() {
        return new ArrayList<>(REGISTRY.keySet());
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
        List<ShopItemEntry> rawItems = shop.items().stream()
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
            if (item instanceof com.stardew.craft.item.IStardewItem stardewItem) {
                String typeKey = stardewItem.getItemTypeKey();
                if ("stardewcraft.type.artifact".equals(typeKey)) {
                    artifactCount++;
                } else if ("stardewcraft.type.mineral".equals(typeKey)) {
                    mineralCount++;
                }
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
                && !data.isRecipeUnlocked("stardewcraft:wedding_ring")) {
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
            if (!(item instanceof com.stardew.craft.item.IStardewItem stardewItem)) {
                continue;
            }
            if (!"stardewcraft.type.furniture".equals(stardewItem.getItemTypeKey())) {
                continue;
            }
            if (stardewItem.getSellPrice(new net.minecraft.world.item.ItemStack(item)) <= 0) {
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
        net.minecraft.world.item.Item resolved = item.get();
        if (!(resolved instanceof com.stardew.craft.item.IStardewItem stardewItem)) {
            return 0;
        }
        return Math.max(0, stardewItem.getSellPrice(new net.minecraft.world.item.ItemStack(resolved)));
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
     *   1. The item must implement IStardewItem and getSellPrice() > 0.
     *   2. The item's typeKey must be in shop.acceptedSellTypes.
     *   3. Sell price = IStardewItem.getSellPrice(stack) * sellPercentage (SDV default 1.0).
     *      → we use 1.0, same as SDV default.
     */
    public static int getSellPrice(ItemStack stack,
                                   ShopDefinition shop) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (!(item instanceof com.stardew.craft.item.IStardewItem si)) return 0;
        int basePrice = si.getSellPrice(stack);
        if (basePrice <= 0) return 0;
        String typeKey = si.getItemTypeKey();
        if (!shop.acceptedSellTypes().contains(typeKey) && !matchesShopSpecificSellRule(stack, shop.shopId())) return 0;
        return basePrice; // SDV: sellPercentage default 1.0f
    }

    private static boolean matchesShopSpecificSellRule(ItemStack stack, String shopId) {
        if (!"Blacksmith".equals(shopId)) {
            return false;
        }
        return stack.is(BLACKSMITH_ORES_TAG)
                || stack.is(BLACKSMITH_BARS_TAG)
                || "stardewcraft:coal".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("stardewcraft", path));
    }
}
