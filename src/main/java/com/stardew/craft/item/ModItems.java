package com.stardew.craft.item;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.book.BookDefinition;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.item.crop.fall.AmaranthItem;
import com.stardew.craft.item.crop.fall.AmaranthSeedItem;
import com.stardew.craft.item.crop.other.AncientFruitItem;
import com.stardew.craft.item.crop.other.AncientFruitSeedItem;
import com.stardew.craft.item.crop.fall.ArtichokeItem;
import com.stardew.craft.item.crop.fall.ArtichokeSeedItem;
import com.stardew.craft.item.crop.fall.BeetItem;
import com.stardew.craft.item.crop.fall.BeetSeedItem;
import com.stardew.craft.item.crop.spring.BlueJazzItem;
import com.stardew.craft.item.crop.spring.BlueJazzSeedItem;
import com.stardew.craft.item.crop.summer.BlueberryItem;
import com.stardew.craft.item.crop.summer.BlueberrySeedItem;
import com.stardew.craft.item.crop.fall.BokChoyItem;
import com.stardew.craft.item.crop.fall.BokChoySeedItem;
import com.stardew.craft.item.crop.fall.BroccoliItem;
import com.stardew.craft.item.crop.fall.BroccoliSeedItem;
import com.stardew.craft.item.crop.spring.CarrotItem;
import com.stardew.craft.item.crop.spring.CarrotSeedItem;
import com.stardew.craft.item.crop.spring.CauliflowerItem;
import com.stardew.craft.item.crop.spring.CauliflowerSeedItem;
import com.stardew.craft.item.crop.other.CoffeeBeanItem;
import com.stardew.craft.item.crop.other.CornItem;
import com.stardew.craft.item.crop.other.CornSeedItem;
import com.stardew.craft.item.crop.fall.CranberryItem;
import com.stardew.craft.item.crop.fall.CranberrySeedItem;
import com.stardew.craft.item.crop.fall.EggplantItem;
import com.stardew.craft.item.crop.fall.EggplantSeedItem;
import com.stardew.craft.item.crop.fall.FairyRoseItem;
import com.stardew.craft.item.crop.fall.FairyRoseSeedItem;
import com.stardew.craft.item.crop.spring.GarlicItem;
import com.stardew.craft.item.crop.spring.GarlicSeedItem;
import com.stardew.craft.item.crop.fall.GrapeItem;
import com.stardew.craft.item.crop.fall.GrapeSeedItem;
import com.stardew.craft.item.crop.spring.GreenBeanItem;
import com.stardew.craft.item.crop.spring.GreenBeanSeedItem;
import com.stardew.craft.item.crop.summer.HopsItem;
import com.stardew.craft.item.crop.summer.HopsSeedItem;
import com.stardew.craft.item.crop.summer.HotPepperItem;
import com.stardew.craft.item.crop.summer.HotPepperSeedItem;
import com.stardew.craft.item.crop.spring.KaleItem;
import com.stardew.craft.item.crop.spring.KaleSeedItem;
import com.stardew.craft.item.crop.summer.MelonItem;
import com.stardew.craft.item.crop.summer.MelonSeedItem;
import com.stardew.craft.item.crop.spring.ParsnipItem;
import com.stardew.craft.item.crop.spring.ParsnipSeedItem;
import com.stardew.craft.item.crop.spring.RiceShootItem;
import com.stardew.craft.item.crop.summer.PoppyItem;
import com.stardew.craft.item.crop.summer.PoppySeedItem;
import com.stardew.craft.item.crop.spring.PotatoItem;
import com.stardew.craft.item.crop.spring.PotatoSeedItem;
import com.stardew.craft.item.crop.winter.PowderMelonItem;
import com.stardew.craft.item.crop.winter.PowderMelonSeedItem;
import com.stardew.craft.item.crop.fall.PumpkinItem;
import com.stardew.craft.item.crop.fall.PumpkinSeedItem;
import com.stardew.craft.item.crop.fall.RareSeedItem;
import com.stardew.craft.item.crop.summer.RadishItem;
import com.stardew.craft.item.crop.summer.RadishSeedItem;
import com.stardew.craft.item.crop.summer.RedCabbageItem;
import com.stardew.craft.item.crop.summer.RedCabbageSeedItem;
import com.stardew.craft.item.crop.spring.RhubarbItem;
import com.stardew.craft.item.crop.spring.RhubarbSeedItem;
import com.stardew.craft.item.crop.summer.StarfruitItem;
import com.stardew.craft.item.crop.summer.StarfruitSeedItem;
import com.stardew.craft.item.crop.spring.StrawberryItem;
import com.stardew.craft.item.crop.spring.StrawberrySeedItem;
import com.stardew.craft.item.crop.fall.SweetGemBerryItem;
import com.stardew.craft.item.crop.summer.SummerSpangleItem;
import com.stardew.craft.item.crop.summer.SummerSpangleSeedItem;
import com.stardew.craft.item.crop.summer.SummerSquashItem;
import com.stardew.craft.item.crop.summer.SummerSquashSeedItem;
import com.stardew.craft.item.crop.other.SunflowerItem;
import com.stardew.craft.item.crop.other.SunflowerSeedItem;
import com.stardew.craft.item.crop.summer.TomatoItem;
import com.stardew.craft.item.crop.summer.TomatoSeedItem;
import com.stardew.craft.item.crop.spring.TulipItem;
import com.stardew.craft.item.crop.spring.TulipSeedItem;
import com.stardew.craft.item.crop.other.WheatItem;
import com.stardew.craft.item.crop.other.WheatSeedItem;
import com.stardew.craft.item.crop.fall.YamItem;
import com.stardew.craft.item.crop.fall.YamSeedItem;
import com.stardew.craft.item.artisan.ArtisanDrinkItem;
import com.stardew.craft.item.artisan.DriedMushroomsItem;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.item.artisan.SmokedFishItem;
import com.stardew.craft.item.cooking.CookingDishItem;
import com.stardew.craft.item.cooking.CookingDishRegistrar;
import com.stardew.craft.item.cooking.DesertFestivalCookDishRegistrar;
import com.stardew.craft.item.misc.GalaxySoulItem;
import com.stardew.craft.item.misc.GoldenPumpkinItem;
import com.stardew.craft.item.misc.IridiumMilkItem;
import com.stardew.craft.item.misc.LuckyPurpleShortsItem;
import com.stardew.craft.item.misc.LifeElixirItem;
import com.stardew.craft.item.misc.MagicRockCandyItem;
import com.stardew.craft.item.misc.AuctionPaddleItem;
import com.stardew.craft.item.misc.MoneyContractItem;
import com.stardew.craft.item.RouteGuidanceConsumableItem;
import com.stardew.craft.item.misc.StardropItem;
import com.stardew.craft.item.misc.StardropTeaItem;
import com.stardew.craft.item.tool.AutoFeedTroughUpgraderItem;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.item.tool.PaintbrushItem;
import com.stardew.craft.item.tool.PointPlanWandItem;
import com.stardew.craft.item.tool.RouteEditorWandItem;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.tree.fruit.FruitTreeType;
// 楸肩被 - 娌虫祦
import com.stardew.craft.item.fish.river.*;
// 楸肩被 - 婀栨硦
import com.stardew.craft.item.fish.lake.*;
// 楸肩被 - 娴锋磱
import com.stardew.craft.item.fish.ocean.*;
// 楸肩被 - 鐗规畩浣嶇疆
import com.stardew.craft.item.fish.special.*;
// 楸肩被 - 浼犺
import com.stardew.craft.item.fish.legendary.*;
// 楸肩被 - 锜圭
import com.stardew.craft.item.fish.crabpot.*;
// 楸肩被 - 鏉傞」
import com.stardew.craft.item.fish.misc.*;
// 鍨冨溇
import com.stardew.craft.item.fishing.trash.TrashItem;
import com.stardew.craft.item.fishing.trash.JojaColaItem;
import com.stardew.craft.item.tree.TreeSeedItem;
import com.stardew.craft.item.block.WaterLanternItem;
import com.stardew.craft.item.block.WildWeedsBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 鐗╁搧娉ㄥ唽绠＄悊鍣?
 */
@SuppressWarnings("null")
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StardewCraft.MODID);
        public static final java.util.Map<String, DeferredItem<Item>> COOKING_DISHES = CookingDishRegistrar.registerAll(ITEMS);
        public static final java.util.Map<String, DeferredItem<Item>> DESERT_FESTIVAL_COOK_DISHES = DesertFestivalCookDishRegistrar.registerAll(ITEMS);
                public static final java.util.Map<String, DeferredItem<Item>> VANILLA_CATEGORY_ITEMS = VanillaCategoryItemRegistrar.registerAll(ITEMS);
        public static final DeferredItem<Item> DRAGON_TOOTH = VANILLA_CATEGORY_ITEMS.get("dragon_tooth");
        public static final DeferredItem<Item> RICE = VANILLA_CATEGORY_ITEMS.get("rice");
        public static final DeferredItem<Item> UNMILLED_RICE = VANILLA_CATEGORY_ITEMS.get("unmilled_rice");
        public static final DeferredItem<Item> RICE_SHOOT = ITEMS.register("rice_shoot",
                        () -> new RiceShootItem(new Item.Properties().stacksTo(999)));

        private static Item.Properties blockItemProps() {
                return new Item.Properties().stacksTo(999);
        }

        private static DeferredItem<Item> blockItem(String name, DeferredBlock<?> block) {
                return ITEMS.register(name, () -> new BlockItem(block.get(), blockItemProps()));
        }

        private static java.util.Map<String, DeferredItem<Item>> blockItems(java.util.Map<String, ? extends DeferredBlock<?>> blocks) {
                java.util.LinkedHashMap<String, DeferredItem<Item>> items = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<String, ? extends DeferredBlock<?>> entry : blocks.entrySet()) {
                        items.put(entry.getKey(), blockItem(entry.getKey(), entry.getValue()));
                }
                return java.util.Collections.unmodifiableMap(items);
        }

        private static DeferredItem<Item> stoneConvertibleBlockItem(String name, DeferredBlock<?> block) {
                return ITEMS.register(name, () -> new StoneConvertibleBlockItem(block.get(), blockItemProps()));
        }

        private static java.util.Map<String, DeferredItem<Item>> registerBooks() {
                java.util.LinkedHashMap<String, DeferredItem<Item>> books = new java.util.LinkedHashMap<>();
                for (BookDefinition definition : BookDefinition.all()) {
                        books.put(definition.registryName(), ITEMS.register(definition.registryName(),
                                        () -> new StardewBookItem(definition, new Item.Properties().stacksTo(999))));
                }
                return java.util.Collections.unmodifiableMap(books);
        }

        public static final java.util.Map<String, DeferredItem<Item>> BOOKS = registerBooks();
        public static final DeferredItem<Item> SKILL_BOOK_0 = BOOKS.get("skill_book_0");
        public static final DeferredItem<Item> SKILL_BOOK_1 = BOOKS.get("skill_book_1");
        public static final DeferredItem<Item> SKILL_BOOK_2 = BOOKS.get("skill_book_2");
        public static final DeferredItem<Item> SKILL_BOOK_3 = BOOKS.get("skill_book_3");
        public static final DeferredItem<Item> SKILL_BOOK_4 = BOOKS.get("skill_book_4");
        public static final DeferredItem<Item> PURPLE_BOOK = BOOKS.get("purple_book");
        public static final DeferredItem<Item> BOOK_ANIMAL_CATALOGUE = BOOKS.get("book_animal_catalogue");
        public static final DeferredItem<Item> BOOK_ARTIFACT = BOOKS.get("book_artifact");
        public static final DeferredItem<Item> BOOK_BOMBS = BOOKS.get("book_bombs");
        public static final DeferredItem<Item> BOOK_CRABBING = BOOKS.get("book_crabbing");
        public static final DeferredItem<Item> BOOK_DEFENSE = BOOKS.get("book_defense");
        public static final DeferredItem<Item> BOOK_DIAMONDS = BOOKS.get("book_diamonds");
        public static final DeferredItem<Item> BOOK_FRIENDSHIP = BOOKS.get("book_friendship");
        public static final DeferredItem<Item> BOOK_GRASS = BOOKS.get("book_grass");
        public static final DeferredItem<Item> BOOK_HORSE = BOOKS.get("book_horse");
        public static final DeferredItem<Item> BOOK_MARLON = BOOKS.get("book_marlon");
        public static final DeferredItem<Item> BOOK_MYSTERY = BOOKS.get("book_mystery");
        public static final DeferredItem<Item> BOOK_QUEEN_OF_SAUCE = BOOKS.get("book_queen_of_sauce");
        public static final DeferredItem<Item> BOOK_ROE = BOOKS.get("book_roe");
        public static final DeferredItem<Item> BOOK_SPEED = BOOKS.get("book_speed");
        public static final DeferredItem<Item> BOOK_SPEED2 = BOOKS.get("book_speed2");
        public static final DeferredItem<Item> BOOK_TRASH = BOOKS.get("book_trash");
        public static final DeferredItem<Item> BOOK_VOID = BOOKS.get("book_void");
        public static final DeferredItem<Item> BOOK_WILD_SEEDS = BOOKS.get("book_wild_seeds");
        public static final DeferredItem<Item> BOOK_WOODCUTTING = BOOKS.get("book_woodcutting");

        // 閲庤崏鏂瑰潡鐗╁搧锛氫富鍏ュ彛浣跨敤缁熶竴 wild_weeds銆?
        // 鏃?seasonal id 淇濈暀涓哄吋瀹瑰埆鍚嶏紙閬垮厤宸叉湁瀛樻。/鐗╁搧涓㈠け锛夈€?
        public static final DeferredItem<Item> WILD_WEEDS = blockItem("wild_weeds", ModBlocks.WILD_WEEDS);

        // 鏄ュ閲庤崏 (legacy alias)
        public static final DeferredItem<Item> WILD_WEEDS_SPRING_0 = ITEMS.register("wild_weeds_spring_0",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_WEEDS_SPRING_1 = ITEMS.register("wild_weeds_spring_1",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 0, 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_WEEDS_SPRING_2 = ITEMS.register("wild_weeds_spring_2",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 0, 2, new Item.Properties().stacksTo(999)));
        
        // 澶忓閲庤崏 (legacy alias)
        public static final DeferredItem<Item> WILD_WEEDS_SUMMER_0 = ITEMS.register("wild_weeds_summer_0",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 1, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_WEEDS_SUMMER_1 = ITEMS.register("wild_weeds_summer_1",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 1, 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_WEEDS_SUMMER_2 = ITEMS.register("wild_weeds_summer_2",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 1, 2, new Item.Properties().stacksTo(999)));
        
        // 绉嬪閲庤崏 (legacy alias)
        public static final DeferredItem<Item> WILD_WEEDS_FALL_0 = ITEMS.register("wild_weeds_fall_0",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 2, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_WEEDS_FALL_1 = ITEMS.register("wild_weeds_fall_1",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 2, 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_WEEDS_FALL_2 = ITEMS.register("wild_weeds_fall_2",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 2, 2, new Item.Properties().stacksTo(999)));
        
        // 鍐閲庤崏 (legacy alias)
        public static final DeferredItem<Item> WILD_WEEDS_WINTER_0 = ITEMS.register("wild_weeds_winter_0",
                        () -> new WildWeedsBlockItem(ModBlocks.WILD_WEEDS.get(), 3, 0, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PASTURE_GRASS = blockItem("pasture_grass", ModBlocks.PASTURE_GRASS);
        public static final DeferredItem<Item> BLUE_PASTURE_GRASS = blockItem("blue_pasture_grass", ModBlocks.BLUE_PASTURE_GRASS);
        public static final DeferredItem<Item> SMALL_BUSH = blockItem("small_bush", ModBlocks.SMALL_BUSH);
        public static final DeferredItem<Item> BERRY_BUSH = blockItem("berry_bush", ModBlocks.BERRY_BUSH);

        // 农场洞穴：蘑菇培养盆（无 CreativeTab 入口，按设计不可获取）
        public static final DeferredItem<Item> MUSHROOM_BOX = blockItem("mushroom_box", ModBlocks.MUSHROOM_BOX);

        public static final DeferredItem<Item> LARGE_STUMP = blockItem("large_stump", ModBlocks.LARGE_STUMP);
        public static final DeferredItem<Item> HOLLOW_LOG = blockItem("hollow_log", ModBlocks.HOLLOW_LOG);
        public static final DeferredItem<Item> LARGE_BOULDER = blockItem("large_boulder", ModBlocks.LARGE_BOULDER);

        // 鏂瑰潡鐗╁搧锛堥噹鐢熸爲锛氭鏍戝師鍨嬶級
        // 鐭夸簳
        public static final DeferredItem<Item> MINE_BARRIER = blockItem("mine_barrier", ModBlocks.MINE_BARRIER);
        public static final DeferredItem<Item> MINE_BARREL = blockItem("mine_barrel", ModBlocks.MINE_BARREL);

        public static final DeferredItem<Item> EARTH_SHALE = stoneConvertibleBlockItem("earth_shale", ModBlocks.EARTH_SHALE);
        public static final DeferredItem<Item> FROST_GNEISS = stoneConvertibleBlockItem("frost_gneiss", ModBlocks.FROST_GNEISS);
        public static final DeferredItem<Item> LAVA_BASALT = stoneConvertibleBlockItem("lava_basalt", ModBlocks.LAVA_BASALT);

        public static final DeferredItem<Item> DARK_EARTH_SHALE = stoneConvertibleBlockItem("dark_earth_shale", ModBlocks.DARK_EARTH_SHALE);
        public static final DeferredItem<Item> DARK_FROST_GNEISS = stoneConvertibleBlockItem("dark_frost_gneiss", ModBlocks.DARK_FROST_GNEISS);
        public static final DeferredItem<Item> DARK_LAVA_BASALT = stoneConvertibleBlockItem("dark_lava_basalt", ModBlocks.DARK_LAVA_BASALT);

        // ========== 涓荤煶澶村彉浣撶墿鍝侊細鍙伴樁銆佹ゼ姊€佸 ==========
        
        // Earth Shale 鍙樹綋
        public static final DeferredItem<Item> EARTH_SHALE_SLAB = blockItem("earth_shale_slab", ModBlocks.EARTH_SHALE_SLAB);
        public static final DeferredItem<Item> EARTH_SHALE_STAIRS = blockItem("earth_shale_stairs", ModBlocks.EARTH_SHALE_STAIRS);
        public static final DeferredItem<Item> EARTH_SHALE_WALL = blockItem("earth_shale_wall", ModBlocks.EARTH_SHALE_WALL);

        // Frost Gneiss 鍙樹綋
        public static final DeferredItem<Item> FROST_GNEISS_SLAB = blockItem("frost_gneiss_slab", ModBlocks.FROST_GNEISS_SLAB);
        public static final DeferredItem<Item> FROST_GNEISS_STAIRS = blockItem("frost_gneiss_stairs", ModBlocks.FROST_GNEISS_STAIRS);
        public static final DeferredItem<Item> FROST_GNEISS_WALL = blockItem("frost_gneiss_wall", ModBlocks.FROST_GNEISS_WALL);

        // Lava Basalt 鍙樹綋
        public static final DeferredItem<Item> LAVA_BASALT_SLAB = blockItem("lava_basalt_slab", ModBlocks.LAVA_BASALT_SLAB);
        public static final DeferredItem<Item> LAVA_BASALT_STAIRS = blockItem("lava_basalt_stairs", ModBlocks.LAVA_BASALT_STAIRS);
        public static final DeferredItem<Item> LAVA_BASALT_WALL = blockItem("lava_basalt_wall", ModBlocks.LAVA_BASALT_WALL);

        // Dark Earth Shale 鍙樹綋
        public static final DeferredItem<Item> DARK_EARTH_SHALE_SLAB = blockItem("dark_earth_shale_slab", ModBlocks.DARK_EARTH_SHALE_SLAB);
        public static final DeferredItem<Item> DARK_EARTH_SHALE_STAIRS = blockItem("dark_earth_shale_stairs", ModBlocks.DARK_EARTH_SHALE_STAIRS);
        public static final DeferredItem<Item> DARK_EARTH_SHALE_WALL = blockItem("dark_earth_shale_wall", ModBlocks.DARK_EARTH_SHALE_WALL);

        // Dark Frost Gneiss 鍙樹綋
        public static final DeferredItem<Item> DARK_FROST_GNEISS_SLAB = blockItem("dark_frost_gneiss_slab", ModBlocks.DARK_FROST_GNEISS_SLAB);
        public static final DeferredItem<Item> DARK_FROST_GNEISS_STAIRS = blockItem("dark_frost_gneiss_stairs", ModBlocks.DARK_FROST_GNEISS_STAIRS);
        public static final DeferredItem<Item> DARK_FROST_GNEISS_WALL = blockItem("dark_frost_gneiss_wall", ModBlocks.DARK_FROST_GNEISS_WALL);

        // Dark Lava Basalt 鍙樹綋
        public static final DeferredItem<Item> DARK_LAVA_BASALT_SLAB = blockItem("dark_lava_basalt_slab", ModBlocks.DARK_LAVA_BASALT_SLAB);
        public static final DeferredItem<Item> DARK_LAVA_BASALT_STAIRS = blockItem("dark_lava_basalt_stairs", ModBlocks.DARK_LAVA_BASALT_STAIRS);
        public static final DeferredItem<Item> DARK_LAVA_BASALT_WALL = blockItem("dark_lava_basalt_wall", ModBlocks.DARK_LAVA_BASALT_WALL);

        public static final DeferredItem<Item> BANDED_MARBLE = stoneConvertibleBlockItem("banded_marble", ModBlocks.BANDED_MARBLE);
        public static final DeferredItem<Item> LIMESTONE = stoneConvertibleBlockItem("limestone", ModBlocks.LIMESTONE);
        public static final DeferredItem<Item> MOSSY_SANDSTONE = stoneConvertibleBlockItem("mossy_sandstone", ModBlocks.MOSSY_SANDSTONE);
        public static final DeferredItem<Item> CRACKED_SLATE = stoneConvertibleBlockItem("cracked_slate", ModBlocks.CRACKED_SLATE);
        public static final DeferredItem<Item> SCORIA = stoneConvertibleBlockItem("scoria", ModBlocks.SCORIA);
        public static final DeferredItem<Item> SALT_ROCK = stoneConvertibleBlockItem("salt_rock", ModBlocks.SALT_ROCK);

        // ========== 瑁呴グ鐭虫潗鍙樹綋鐗╁搧锛氬彴闃躲€佹ゼ姊€佸 ==========
        
        // Banded Marble 鍙樹綋
        public static final DeferredItem<Item> BANDED_MARBLE_SLAB = blockItem("banded_marble_slab", ModBlocks.BANDED_MARBLE_SLAB);
        public static final DeferredItem<Item> BANDED_MARBLE_STAIRS = blockItem("banded_marble_stairs", ModBlocks.BANDED_MARBLE_STAIRS);
        public static final DeferredItem<Item> BANDED_MARBLE_WALL = blockItem("banded_marble_wall", ModBlocks.BANDED_MARBLE_WALL);

        // Limestone 鍙樹綋
        public static final DeferredItem<Item> LIMESTONE_SLAB = blockItem("limestone_slab", ModBlocks.LIMESTONE_SLAB);
        public static final DeferredItem<Item> LIMESTONE_STAIRS = blockItem("limestone_stairs", ModBlocks.LIMESTONE_STAIRS);
        public static final DeferredItem<Item> LIMESTONE_WALL = blockItem("limestone_wall", ModBlocks.LIMESTONE_WALL);

        // Mossy Sandstone 鍙樹綋
        public static final DeferredItem<Item> MOSSY_SANDSTONE_SLAB = blockItem("mossy_sandstone_slab", ModBlocks.MOSSY_SANDSTONE_SLAB);
        public static final DeferredItem<Item> MOSSY_SANDSTONE_STAIRS = blockItem("mossy_sandstone_stairs", ModBlocks.MOSSY_SANDSTONE_STAIRS);
        public static final DeferredItem<Item> MOSSY_SANDSTONE_WALL = blockItem("mossy_sandstone_wall", ModBlocks.MOSSY_SANDSTONE_WALL);

        // Cracked Slate 鍙樹綋
        public static final DeferredItem<Item> CRACKED_SLATE_SLAB = blockItem("cracked_slate_slab", ModBlocks.CRACKED_SLATE_SLAB);
        public static final DeferredItem<Item> CRACKED_SLATE_STAIRS = blockItem("cracked_slate_stairs", ModBlocks.CRACKED_SLATE_STAIRS);
        public static final DeferredItem<Item> CRACKED_SLATE_WALL = blockItem("cracked_slate_wall", ModBlocks.CRACKED_SLATE_WALL);

        // Scoria 鍙樹綋
        public static final DeferredItem<Item> SCORIA_SLAB = blockItem("scoria_slab", ModBlocks.SCORIA_SLAB);
        public static final DeferredItem<Item> SCORIA_STAIRS = blockItem("scoria_stairs", ModBlocks.SCORIA_STAIRS);
        public static final DeferredItem<Item> SCORIA_WALL = blockItem("scoria_wall", ModBlocks.SCORIA_WALL);

        // Salt Rock 鍙樹綋
        public static final DeferredItem<Item> SALT_ROCK_SLAB = blockItem("salt_rock_slab", ModBlocks.SALT_ROCK_SLAB);
        public static final DeferredItem<Item> SALT_ROCK_STAIRS = blockItem("salt_rock_stairs", ModBlocks.SALT_ROCK_STAIRS);
        public static final DeferredItem<Item> SALT_ROCK_WALL = blockItem("salt_rock_wall", ModBlocks.SALT_ROCK_WALL);

        public static final DeferredItem<Item> ELEVATOR = blockItem("elevator", ModBlocks.ELEVATOR);

        // 鐭夸簳锛氫笅妤兼瀛愮墿鍝?
        public static final DeferredItem<Item> MINE_LADDER = blockItem("mine_ladder", ModBlocks.MINE_LADDER);

        // 鐭夸簳锛氬嚭鍙ｆ瀛愮墿鍝?
        public static final DeferredItem<Item> MINE_EXIT = blockItem("mine_exit", ModBlocks.MINE_EXIT);

        // 鐭跨煶鏂瑰潡鐗╁搧
        public static final DeferredItem<Item> EARTH_COPPER_ORE = ITEMS.register("earth_copper_ore",
                        () -> new BlockItem(ModBlocks.EARTH_COPPER_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROST_COPPER_ORE = ITEMS.register("frost_copper_ore",
                        () -> new BlockItem(ModBlocks.FROST_COPPER_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LAVA_COPPER_ORE = ITEMS.register("lava_copper_ore",
                        () -> new BlockItem(ModBlocks.LAVA_COPPER_ORE.get(), new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> EARTH_IRON_ORE = ITEMS.register("earth_iron_ore",
                        () -> new BlockItem(ModBlocks.EARTH_IRON_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROST_IRON_ORE = ITEMS.register("frost_iron_ore",
                        () -> new BlockItem(ModBlocks.FROST_IRON_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LAVA_IRON_ORE = ITEMS.register("lava_iron_ore",
                        () -> new BlockItem(ModBlocks.LAVA_IRON_ORE.get(), new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> EARTH_GOLD_ORE = ITEMS.register("earth_gold_ore",
                        () -> new BlockItem(ModBlocks.EARTH_GOLD_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROST_GOLD_ORE = ITEMS.register("frost_gold_ore",
                        () -> new BlockItem(ModBlocks.FROST_GOLD_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LAVA_GOLD_ORE = ITEMS.register("lava_gold_ore",
                        () -> new BlockItem(ModBlocks.LAVA_GOLD_ORE.get(), new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> EARTH_IRIDIUM_ORE = ITEMS.register("earth_iridium_ore",
                        () -> new BlockItem(ModBlocks.EARTH_IRIDIUM_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROST_IRIDIUM_ORE = ITEMS.register("frost_iridium_ore",
                        () -> new BlockItem(ModBlocks.FROST_IRIDIUM_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LAVA_IRIDIUM_ORE = ITEMS.register("lava_iridium_ore",
                        () -> new BlockItem(ModBlocks.LAVA_IRIDIUM_ORE.get(), new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> EARTH_COAL_ORE = ITEMS.register("earth_coal_ore",
                        () -> new BlockItem(ModBlocks.EARTH_COAL_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROST_COAL_ORE = ITEMS.register("frost_coal_ore",
                        () -> new BlockItem(ModBlocks.FROST_COAL_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LAVA_COAL_ORE = ITEMS.register("lava_coal_ore",
                        () -> new BlockItem(ModBlocks.LAVA_COAL_ORE.get(), new Item.Properties().stacksTo(999)));

        // 骷髅矿洞方块物品
        public static final DeferredItem<Item> DESERT_BEDROCK = stoneConvertibleBlockItem("desert_bedrock", ModBlocks.DESERT_BEDROCK);
        public static final DeferredItem<Item> DARK_DESERT_BEDROCK = stoneConvertibleBlockItem("dark_desert_bedrock", ModBlocks.DARK_DESERT_BEDROCK);
        public static final DeferredItem<Item> SULFUR_ROCK = stoneConvertibleBlockItem("sulfur_rock", ModBlocks.SULFUR_ROCK);
        public static final DeferredItem<Item> WEATHERED_STONE = stoneConvertibleBlockItem("weathered_stone", ModBlocks.WEATHERED_STONE);
        public static final DeferredItem<Item> CALICO_EGG_STONE = blockItem("calico_egg_stone", ModBlocks.CALICO_EGG_STONE);
        public static final DeferredItem<Item> CALICO_STATUE = blockItem("calico_statue", ModBlocks.CALICO_STATUE);

        // 骷髅矿建材变体
        public static final DeferredItem<Item> DESERT_BEDROCK_SLAB = blockItem("desert_bedrock_slab", ModBlocks.DESERT_BEDROCK_SLAB);
        public static final DeferredItem<Item> DESERT_BEDROCK_STAIRS = blockItem("desert_bedrock_stairs", ModBlocks.DESERT_BEDROCK_STAIRS);
        public static final DeferredItem<Item> DESERT_BEDROCK_WALL = blockItem("desert_bedrock_wall", ModBlocks.DESERT_BEDROCK_WALL);
        public static final DeferredItem<Item> DARK_DESERT_BEDROCK_SLAB = blockItem("dark_desert_bedrock_slab", ModBlocks.DARK_DESERT_BEDROCK_SLAB);
        public static final DeferredItem<Item> DARK_DESERT_BEDROCK_STAIRS = blockItem("dark_desert_bedrock_stairs", ModBlocks.DARK_DESERT_BEDROCK_STAIRS);
        public static final DeferredItem<Item> DARK_DESERT_BEDROCK_WALL = blockItem("dark_desert_bedrock_wall", ModBlocks.DARK_DESERT_BEDROCK_WALL);
        public static final DeferredItem<Item> SULFUR_ROCK_SLAB = blockItem("sulfur_rock_slab", ModBlocks.SULFUR_ROCK_SLAB);
        public static final DeferredItem<Item> SULFUR_ROCK_STAIRS = blockItem("sulfur_rock_stairs", ModBlocks.SULFUR_ROCK_STAIRS);
        public static final DeferredItem<Item> SULFUR_ROCK_WALL = blockItem("sulfur_rock_wall", ModBlocks.SULFUR_ROCK_WALL);
        public static final DeferredItem<Item> WEATHERED_STONE_SLAB = blockItem("weathered_stone_slab", ModBlocks.WEATHERED_STONE_SLAB);
        public static final DeferredItem<Item> WEATHERED_STONE_STAIRS = blockItem("weathered_stone_stairs", ModBlocks.WEATHERED_STONE_STAIRS);
        public static final DeferredItem<Item> WEATHERED_STONE_WALL = blockItem("weathered_stone_wall", ModBlocks.WEATHERED_STONE_WALL);

        public static final DeferredItem<Item> DESERT_COPPER_ORE = ITEMS.register("desert_copper_ore",
                        () -> new BlockItem(ModBlocks.DESERT_COPPER_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DESERT_IRON_ORE = ITEMS.register("desert_iron_ore",
                        () -> new BlockItem(ModBlocks.DESERT_IRON_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DESERT_GOLD_ORE = ITEMS.register("desert_gold_ore",
                        () -> new BlockItem(ModBlocks.DESERT_GOLD_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DESERT_IRIDIUM_ORE = ITEMS.register("desert_iridium_ore",
                        () -> new BlockItem(ModBlocks.DESERT_IRIDIUM_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DESERT_COAL_ORE = ITEMS.register("desert_coal_ore",
                        () -> new BlockItem(ModBlocks.DESERT_COAL_ORE.get(), new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> QUICKSAND = blockItem("quicksand", ModBlocks.QUICKSAND);
        public static final DeferredItem<Item> TOXIC_SPORE_BLOCK = blockItem("toxic_spore_block", ModBlocks.TOXIC_SPORE_BLOCK);
        public static final DeferredItem<Item> UNSTABLE_ROCK = stoneConvertibleBlockItem("unstable_rock", ModBlocks.UNSTABLE_ROCK);

        // 鐭跨墿鐭跨煶鑺傜偣锛堝疂鐭崇熆锛屾櫠娲炰骇鐗╀笉鍋氾級
        public static final DeferredItem<Item> AMETHYST_ORE = ITEMS.register("amethyst_ore",
                        () -> new BlockItem(ModBlocks.AMETHYST_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> AQUAMARINE_ORE = ITEMS.register("aquamarine_ore",
                        () -> new BlockItem(ModBlocks.AQUAMARINE_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DIAMOND_ORE = ITEMS.register("diamond_ore",
                        () -> new BlockItem(ModBlocks.DIAMOND_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> EMERALD_ORE = ITEMS.register("emerald_ore",
                        () -> new BlockItem(ModBlocks.EMERALD_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JADE_ORE = ITEMS.register("jade_ore",
                        () -> new BlockItem(ModBlocks.JADE_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RUBY_ORE = ITEMS.register("ruby_ore",
                        () -> new BlockItem(ModBlocks.RUBY_ORE.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TOPAZ_ORE = ITEMS.register("topaz_ore",
                        () -> new BlockItem(ModBlocks.TOPAZ_ORE.get(), new Item.Properties().stacksTo(999)));

        // 鏂瑰潡鐗╁搧锛堥噹鐢熸爲锛氭鏍戝師鍨嬶級
        public static final DeferredItem<Item> WILD_OAK_TRUNK0 = ITEMS.register("wild_oak_trunk0",
                        () -> new BlockItem(ModBlocks.WILD_OAK_TRUNK0.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_OAK_TRUNK1 = ITEMS.register("wild_oak_trunk1",
                        () -> new BlockItem(ModBlocks.WILD_OAK_TRUNK1.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_OAK_BRANCH1 = ITEMS.register("wild_oak_branch1",
                        () -> new BlockItem(ModBlocks.WILD_OAK_BRANCH1.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_OAK_BRANCH2 = ITEMS.register("wild_oak_branch2",
                        () -> new BlockItem(ModBlocks.WILD_OAK_BRANCH2.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILD_OAK_LEAVES = ITEMS.register("wild_oak_leaves",
                        () -> new BlockItem(ModBlocks.WILD_OAK_LEAVES.get(), new Item.Properties().stacksTo(999)));

                // 鏂瑰潡鐗╁搧锛堥噹鐢熸爲锛氭灚鏍戯級
                public static final DeferredItem<Item> WILD_MAPLE_TRUNK0 = ITEMS.register("wild_maple_trunk0",
                                                () -> new BlockItem(ModBlocks.WILD_MAPLE_TRUNK0.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAPLE_TRUNK1 = ITEMS.register("wild_maple_trunk1",
                                                () -> new BlockItem(ModBlocks.WILD_MAPLE_TRUNK1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAPLE_BRANCH1 = ITEMS.register("wild_maple_branch1",
                                                () -> new BlockItem(ModBlocks.WILD_MAPLE_BRANCH1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAPLE_BRANCH2 = ITEMS.register("wild_maple_branch2",
                                                () -> new BlockItem(ModBlocks.WILD_MAPLE_BRANCH2.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAPLE_LEAVES = ITEMS.register("wild_maple_leaves",
                                                () -> new BlockItem(ModBlocks.WILD_MAPLE_LEAVES.get(), new Item.Properties().stacksTo(999)));

                // 鏂瑰潡鐗╁搧锛堥噹鐢熸爲锛氭澗鏍戯級
                public static final DeferredItem<Item> WILD_PINE_TRUNK0 = ITEMS.register("wild_pine_trunk0",
                                                () -> new BlockItem(ModBlocks.WILD_PINE_TRUNK0.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_PINE_TRUNK1 = ITEMS.register("wild_pine_trunk1",
                                                () -> new BlockItem(ModBlocks.WILD_PINE_TRUNK1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_PINE_BRANCH1 = ITEMS.register("wild_pine_branch1",
                                                () -> new BlockItem(ModBlocks.WILD_PINE_BRANCH1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_PINE_BRANCH2 = ITEMS.register("wild_pine_branch2",
                                                () -> new BlockItem(ModBlocks.WILD_PINE_BRANCH2.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_PINE_LEAVES = ITEMS.register("wild_pine_leaves",
                                                () -> new BlockItem(ModBlocks.WILD_PINE_LEAVES.get(), new Item.Properties().stacksTo(999)));

                // 鏂瑰潡鐗╁搧锛堥噹鐢熸爲锛氭鑺卞績鏈級
                public static final DeferredItem<Item> WILD_MAHOGANY_TRUNK0 = ITEMS.register("wild_mahogany_trunk0",
                                                () -> new BlockItem(ModBlocks.WILD_MAHOGANY_TRUNK0.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAHOGANY_TRUNK1 = ITEMS.register("wild_mahogany_trunk1",
                                                () -> new BlockItem(ModBlocks.WILD_MAHOGANY_TRUNK1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAHOGANY_BRANCH1 = ITEMS.register("wild_mahogany_branch1",
                                                () -> new BlockItem(ModBlocks.WILD_MAHOGANY_BRANCH1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAHOGANY_BRANCH2 = ITEMS.register("wild_mahogany_branch2",
                                                () -> new BlockItem(ModBlocks.WILD_MAHOGANY_BRANCH2.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MAHOGANY_LEAVES = ITEMS.register("wild_mahogany_leaves",
                                                () -> new BlockItem(ModBlocks.WILD_MAHOGANY_LEAVES.get(), new Item.Properties().stacksTo(999)));

                // 鏂瑰潡鐗╁搧锛堥噹鐢熸爲锛氱绉樻爲锛?
                public static final DeferredItem<Item> WILD_MYSTIC_TREE_TRUNK0 = ITEMS.register("wild_mystic_tree_trunk0",
                                                () -> new BlockItem(ModBlocks.WILD_MYSTIC_TREE_TRUNK0.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MYSTIC_TREE_TRUNK1 = ITEMS.register("wild_mystic_tree_trunk1",
                                                () -> new BlockItem(ModBlocks.WILD_MYSTIC_TREE_TRUNK1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MYSTIC_TREE_BRANCH1 = ITEMS.register("wild_mystic_tree_branch1",
                                                () -> new BlockItem(ModBlocks.WILD_MYSTIC_TREE_BRANCH1.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MYSTIC_TREE_BRANCH2 = ITEMS.register("wild_mystic_tree_branch2",
                                                () -> new BlockItem(ModBlocks.WILD_MYSTIC_TREE_BRANCH2.get(), new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> WILD_MYSTIC_TREE_LEAVES = ITEMS.register("wild_mystic_tree_leaves",
                                                () -> new BlockItem(ModBlocks.WILD_MYSTIC_TREE_LEAVES.get(), new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> OAK_ROOT = blockItem("oak_root", ModBlocks.OAK_ROOT);
        public static final DeferredItem<Item> OAK_LOG = blockItem("oak_log", ModBlocks.OAK_LOG);
        public static final DeferredItem<Item> OAK_LEAVES = blockItem("oak_leaves", ModBlocks.OAK_LEAVES);
        public static final DeferredItem<Item> OAK_LEAVES_QUESTION = blockItem("oak_leaves_question", ModBlocks.OAK_LEAVES_QUESTION);
        public static final DeferredItem<Item> OAK_BRANCH = blockItem("oak_branch", ModBlocks.OAK_BRANCH);

        public static final DeferredItem<Item> MAPLE_ROOT = blockItem("maple_root", ModBlocks.MAPLE_ROOT);
        public static final DeferredItem<Item> MAPLE_LOG = blockItem("maple_log", ModBlocks.MAPLE_LOG);
        public static final DeferredItem<Item> MAPLE_LEAVES = blockItem("maple_leaves", ModBlocks.MAPLE_LEAVES);
        public static final DeferredItem<Item> MAPLE_BRANCH = blockItem("maple_branch", ModBlocks.MAPLE_BRANCH);

        public static final DeferredItem<Item> PINE_ROOT = blockItem("pine_root", ModBlocks.PINE_ROOT);
        public static final DeferredItem<Item> PINE_LOG = blockItem("pine_log", ModBlocks.PINE_LOG);
        public static final DeferredItem<Item> PINE_LEAVES = blockItem("pine_leaves", ModBlocks.PINE_LEAVES);
        public static final DeferredItem<Item> PINE_BRANCH = blockItem("pine_branch", ModBlocks.PINE_BRANCH);

        public static final DeferredItem<Item> MAHOGANY_ROOT = blockItem("mahogany_root", ModBlocks.MAHOGANY_ROOT);
        public static final DeferredItem<Item> MAHOGANY_LOG = blockItem("mahogany_log", ModBlocks.MAHOGANY_LOG);
        public static final DeferredItem<Item> MAHOGANY_LEAVES = blockItem("mahogany_leaves", ModBlocks.MAHOGANY_LEAVES);
        public static final DeferredItem<Item> MAHOGANY_BRANCH = blockItem("mahogany_branch", ModBlocks.MAHOGANY_BRANCH);

        public static final DeferredItem<Item> MYSTIC_TREE_ROOT = blockItem("mystic_tree_root", ModBlocks.MYSTIC_TREE_ROOT);
        public static final DeferredItem<Item> MYSTIC_TREE_LOG = blockItem("mystic_tree_log", ModBlocks.MYSTIC_TREE_LOG);
        public static final DeferredItem<Item> MYSTIC_TREE_LEAVES = blockItem("mystic_tree_leaves", ModBlocks.MYSTIC_TREE_LEAVES);
        public static final DeferredItem<Item> MYSTIC_TREE_BRANCH = blockItem("mystic_tree_branch", ModBlocks.MYSTIC_TREE_BRANCH);

        public static final java.util.Map<String, DeferredItem<Item>> NEW_TREE_BUILDING_ITEMS =
                blockItems(ModBlocks.NEW_TREE_BUILDING_BLOCKS);

        // 鏂瑰潡鐗╁搧锛堝疄鐢ㄨ鏂斤級
        public static final DeferredItem<Item> TAPPER = ITEMS.register("tapper",
                        () -> new StardewBlockItem(ModBlocks.TAPPER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> KEG = ITEMS.register("keg",
                        () -> new StardewBlockItem(ModBlocks.KEG.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PRESERVES_JAR = ITEMS.register("preserves_jar",
                        () -> new StardewBlockItem(ModBlocks.PRESERVES_JAR.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> DEHYDRATOR = ITEMS.register("dehydrator",
                        () -> new StardewBlockItem(ModBlocks.DEHYDRATOR.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BAIT_MAKER = ITEMS.register("bait_maker",
                        () -> new StardewBlockItem(ModBlocks.BAIT_MAKER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FISH_SMOKER = ITEMS.register("fish_smoker",
                        () -> new StardewBlockItem(ModBlocks.FISH_SMOKER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PRIZE_TICKET_MACHINE = ITEMS.register("prize_ticket_machine",
                        () -> new StardewBlockItem(ModBlocks.PRIZE_TICKET_MACHINE.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RECYCLING_MACHINE = ITEMS.register("recycling_machine",
                        () -> new StardewBlockItem(ModBlocks.RECYCLING_MACHINE.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> COOKING_POT = ITEMS.register("cooking_pot",
                        () -> new StardewBlockItem(ModBlocks.COOKING_POT.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CRYSTALARIUM = ITEMS.register("crystalarium",
                        () -> new StardewBlockItem(ModBlocks.CRYSTALARIUM.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SEED_MAKER = ITEMS.register("seed_maker",
                        () -> new StardewBlockItem(ModBlocks.SEED_MAKER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FURNACE = ITEMS.register("furnace",
                                        () -> new StardewBlockItem(ModBlocks.FURNACE.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
                public static final DeferredItem<Item> CHARCOAL_KILN = ITEMS.register("charcoal_kiln",
                                                                        () -> new StardewBlockItem(ModBlocks.CHARCOAL_KILN.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        // ─── Mastery reward items ───
        public static final DeferredItem<Item> HEAVY_FURNACE = ITEMS.register("heavy_furnace",
                () -> new StardewBlockItem(ModBlocks.HEAVY_FURNACE.get(), "stardewcraft.type.utility", -1, "item.stardewcraft.heavy_furnace.desc", new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STATUE_OF_BLESSINGS = ITEMS.register("statue_of_blessings",
                () -> new StardewBlockItem(ModBlocks.STATUE_OF_BLESSINGS.get(), "stardewcraft.type.magic", -1, "item.stardewcraft.statue_of_blessings.desc", new Item.Properties().stacksTo(1)));
        public static final DeferredItem<Item> STATUE_OF_DWARF_KING = ITEMS.register("statue_of_dwarf_king",
                () -> new StardewBlockItem(ModBlocks.STATUE_OF_DWARF_KING.get(), "stardewcraft.type.magic", -1, "item.stardewcraft.statue_of_dwarf_king.desc", new Item.Properties().stacksTo(1)));
        public static final DeferredItem<Item> ANVIL_MASTERY = ITEMS.register("anvil_mastery",
                () -> new StardewBlockItem(ModBlocks.ANVIL_MASTERY.get(), "stardewcraft.type.utility", -1, "item.stardewcraft.anvil_mastery.desc", new Item.Properties().stacksTo(1)));
        public static final DeferredItem<Item> MINI_FORGE = ITEMS.register("mini_forge",
                () -> new StardewBlockItem(ModBlocks.MINI_FORGE.get(), "stardewcraft.type.utility", -1, "item.stardewcraft.mini_forge.desc", new Item.Properties().stacksTo(1)));
        public static final DeferredItem<Item> TREASURE_TOTEM = ITEMS.register("treasure_totem",
                () -> new com.stardew.craft.item.mastery.TreasureTotemItem("stardewcraft.type.magic", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHTNING_ROD = ITEMS.register("lightning_rod",
                        () -> new StardewBlockItem(ModBlocks.LIGHTNING_ROD.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

                public static final DeferredItem<Item> SOLAR_PANEL = ITEMS.register("solar_panel",
                                                () -> new StardewBlockItem(ModBlocks.SOLAR_PANEL.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SPECIAL_ORDERS_BOARD = ITEMS.register("special_orders_board",
                        () -> new StardewBlockItem(ModBlocks.SPECIAL_ORDERS_BOARD.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(1)));
        public static final DeferredItem<Item> GEODE_CRUSHER = ITEMS.register("geode_crusher",
                        () -> new StardewBlockItem(ModBlocks.GEODE_CRUSHER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MINI_OBELISK = ITEMS.register("mini_obelisk",
                        () -> new StardewBlockItem(ModBlocks.MINI_OBELISK.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FARM_COMPUTER = ITEMS.register("farm_computer",
                        () -> new StardewBlockItem(ModBlocks.FARM_COMPUTER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONE_MILL = ITEMS.register("bone_mill",
                        () -> new StardewBlockItem(ModBlocks.BONE_MILL.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> COFFEE_MAKER = ITEMS.register("coffee_maker",
                        () -> new StardewBlockItem(ModBlocks.COFFEE_MAKER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> ECTOPLASM = ITEMS.register("ectoplasm",
                        () -> new SimpleStardewItem("stardewcraft.type.quest", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PRISMATIC_JELLY = ITEMS.register("prismatic_jelly",
                        () -> new SimpleStardewItem("stardewcraft.type.quest", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MONSTER_MUSK = ITEMS.register("monster_musk",
                        () -> new MonsterMuskItem(new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FIBER_SEEDS = ITEMS.register("fiber_seeds",
                        () -> new com.stardew.craft.item.crop.special.FiberSeedsItem(new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SPRINKLER = ITEMS.register("sprinkler",
                        () -> new StardewBlockItem(ModBlocks.SPRINKLER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> QUALITY_SPRINKLER = ITEMS.register("quality_sprinkler",
                        () -> new StardewBlockItem(ModBlocks.QUALITY_SPRINKLER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> IRIDIUM_SPRINKLER = ITEMS.register("iridium_sprinkler",
                        () -> new StardewBlockItem(ModBlocks.IRIDIUM_SPRINKLER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

                public static final DeferredItem<Item> CASK = ITEMS.register("cask",
                                                () -> new StardewBlockItem(ModBlocks.CASK.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CHEESE_PRESS = ITEMS.register("cheese_press",
                                        () -> new StardewBlockItem(ModBlocks.CHEESE_PRESS.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

                public static final DeferredItem<Item> MAYONNAISE_MACHINE = ITEMS.register("mayonnaise_machine",
                                                                                () -> new StardewBlockItem(ModBlocks.MAYONNAISE_MACHINE.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> INCUBATOR = ITEMS.register("incubator",
                        () -> new StardewBlockItem(ModBlocks.INCUBATOR.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> OIL_MAKER = ITEMS.register("oil_maker",
                        () -> new StardewBlockItem(ModBlocks.OIL_MAKER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LOOM = ITEMS.register("loom",
                                                () -> new StardewBlockItem(ModBlocks.LOOM.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WORM_BIN = ITEMS.register("worm_bin",
                        () -> new StardewBlockItem(ModBlocks.WORM_BIN.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FEED_TROUGH = ITEMS.register("feed_trough",
                        () -> new StardewBlockItem(ModBlocks.FEED_TROUGH.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> AUTOFEED_TROUGH = ITEMS.register("autofeed_trough",
                        () -> new StardewBlockItem(ModBlocks.AUTOFEED_TROUGH.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> AUTO_GRABBER = ITEMS.register("auto_grabber",
                        () -> new StardewBlockItem(ModBlocks.AUTO_GRABBER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> AUTO_PETTER = ITEMS.register("auto_petter",
                        () -> new StardewBlockItem(ModBlocks.AUTO_PETTER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WOODEN_CHEST = ITEMS.register("wooden_chest",
                        () -> new StardewBlockItem(ModBlocks.WOODEN_CHEST.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> STONE_CHEST = ITEMS.register("stone_chest",
                        () -> new StardewBlockItem(ModBlocks.STONE_CHEST.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FRIDGE = ITEMS.register("fridge",
                        () -> new StardewBlockItem(ModBlocks.FRIDGE.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> MAILBOX = ITEMS.register("mailbox",
                        () -> new StardewBlockItem(ModBlocks.MAILBOX.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SHIPPING_BIN = ITEMS.register("shipping_bin",
                        () -> new StardewBlockItem(ModBlocks.SHIPPING_BIN.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> TRASH_BIN = ITEMS.register("trash_bin",
                        () -> new StardewBlockItem(ModBlocks.TRASH_BIN.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> HEATER = ITEMS.register("heater",
                        () -> new StardewBlockItem(ModBlocks.HEATER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> HAY_HOPPER = ITEMS.register("hay_hopper",
                        () -> new StardewBlockItem(ModBlocks.HAY_HOPPER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FRIENDSHIP_DOOR = ITEMS.register("friendship_door",
                        () -> new FriendshipDoorItem(ModBlocks.FRIENDSHIP_DOOR.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> COOP_MANAGER = ITEMS.register("coop_manager",
                        () -> new StardewBlockItem(ModBlocks.COOP_MANAGER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FISH_POND_MANAGER = ITEMS.register("fish_pond_manager",
                        () -> new StardewBlockItem(ModBlocks.FISH_POND_MANAGER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BARN_MANAGER = ITEMS.register("barn_manager",
                        () -> new StardewBlockItem(ModBlocks.BARN_MANAGER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SILO_MANAGER = ITEMS.register("silo_manager",
                        () -> new StardewBlockItem(ModBlocks.SILO_MANAGER.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> DELUXE_WORM_BIN = ITEMS.register("deluxe_worm_bin",
                        () -> new StardewBlockItem(ModBlocks.DELUXE_WORM_BIN.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BEE_HOUSE = ITEMS.register("bee_house",
                        () -> new StardewBlockItem(ModBlocks.BEE_HOUSE.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CRAB_POT = ITEMS.register("crab_pot",
                        () -> new CrabPotItem(ModBlocks.CRAB_POT.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WATER_LANTERN = ITEMS.register("water_lantern",
                        () -> new WaterLanternItem(ModBlocks.WATER_LANTERN.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FISH_NET = ITEMS.register("fish_net",
                        () -> new StardewBlockItem(ModBlocks.FISH_NET.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FISH_POND_BUCKET = ITEMS.register("fish_pond_bucket",
                        () -> new StardewBlockItem(ModBlocks.FISH_POND_BUCKET.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> MUSEUM_EXHIBIT_STAND = ITEMS.register("museum_exhibit_stand",
                        () -> new StardewBlockItem(ModBlocks.MUSEUM_EXHIBIT_STAND.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BED_1 = ITEMS.register("bed_1",
                        () -> new StardewBlockItem(ModBlocks.BED_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BED_2 = ITEMS.register("bed_2",
                        () -> new StardewBlockItem(ModBlocks.BED_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SOFA = ITEMS.register("sofa",
                        () -> new StardewBlockItem(ModBlocks.SOFA.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CHAIR_1 = ITEMS.register("chair_1",
                        () -> new StardewBlockItem(ModBlocks.CHAIR_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CHAIR_2 = ITEMS.register("chair_2",
                        () -> new StardewBlockItem(ModBlocks.CHAIR_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CHAIR_3 = ITEMS.register("chair_3",
                        () -> new StardewBlockItem(ModBlocks.CHAIR_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_1 = ITEMS.register("light_1",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_2 = ITEMS.register("light_2",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_3 = ITEMS.register("light_3",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_4 = ITEMS.register("light_4",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_5 = ITEMS.register("light_5",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_5.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_6 = ITEMS.register("light_6",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_6.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LIGHT_7 = ITEMS.register("light_7",
                        () -> new StardewBlockItem(ModBlocks.LIGHT_7.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CUSHION = ITEMS.register("cushion",
                        () -> new StardewBlockItem(ModBlocks.CUSHION.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> OFFICE_STOOL = ITEMS.register("office_stool",
                        () -> new StardewBlockItem(ModBlocks.OFFICE_STOOL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> OFFICE_CHAIR_2 = ITEMS.register("office_chair_2",
                        () -> new StardewBlockItem(ModBlocks.OFFICE_CHAIR_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SINK_4 = ITEMS.register("sink_4",
                        () -> new StardewBlockItem(ModBlocks.SINK_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FLOOR_LAMP = ITEMS.register("floor_lamp",
                        () -> new StardewBlockItem(ModBlocks.FLOOR_LAMP.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> TABLE_LAMP = ITEMS.register("table_lamp",
                        () -> new StardewBlockItem(ModBlocks.TABLE_LAMP.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> STOOL = ITEMS.register("stool",
                        () -> new StardewBlockItem(ModBlocks.STOOL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> IRON_STOOL = ITEMS.register("iron_stool",
                        () -> new StardewBlockItem(ModBlocks.IRON_STOOL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> DINING_CHAIR_WOOD = ITEMS.register("dining_chair_wood",
                        () -> new StardewBlockItem(ModBlocks.DINING_CHAIR_WOOD.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> ARCADE_MACHINE = ITEMS.register("arcade_machine",
                        () -> new StardewBlockItem(ModBlocks.ARCADE_MACHINE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> DINING_CHAIR_IRON = ITEMS.register("dining_chair_iron",
                        () -> new StardewBlockItem(ModBlocks.DINING_CHAIR_IRON.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PHOTO_FRAME = ITEMS.register("photo_frame",
                        () -> new StardewBlockItem(ModBlocks.PHOTO_FRAME.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> TV_1 = ITEMS.register("tv_1",
                        () -> new StardewBlockItem(ModBlocks.TV_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> TV_2 = ITEMS.register("tv_2",
                        () -> new StardewBlockItem(ModBlocks.TV_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> OAK_TABLE = ITEMS.register("oak_table",
                        () -> new StardewBlockItem(ModBlocks.OAK_TABLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SPRUCE_TABLE = ITEMS.register("spruce_table",
                        () -> new StardewBlockItem(ModBlocks.SPRUCE_TABLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BIRCH_TABLE = ITEMS.register("birch_table",
                        () -> new StardewBlockItem(ModBlocks.BIRCH_TABLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PASTEL_BANNER = ITEMS.register("pastel_banner",
                        () -> new StardewBlockItem(ModBlocks.PASTEL_BANNER.get(), "stardewcraft.type.festival_decoration", 500, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FLOWER_BASKET = ITEMS.register("flower_basket",
                        () -> new StardewBlockItem(ModBlocks.FLOWER_BASKET.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FLOWER_CLUSTER = ITEMS.register("flower_cluster",
                        () -> new StardewBlockItem(ModBlocks.FLOWER_CLUSTER.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SEASONAL_DECOR = ITEMS.register("seasonal_decor",
                        () -> new StardewBlockItem(ModBlocks.SEASONAL_DECOR.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LUAU_SOUP_POT = ITEMS.register("luau_soup_pot",
                        () -> new StardewBlockItem(ModBlocks.LUAU_SOUP_POT.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LUAU_TORCH = ITEMS.register("luau_torch",
                        () -> new StardewBlockItem(ModBlocks.LUAU_TORCH.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LUAU_SPEAKER = ITEMS.register("luau_speaker",
                        () -> new StardewBlockItem(ModBlocks.LUAU_SPEAKER.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LUAU_TOTEM = ITEMS.register("luau_totem",
                        () -> new StardewBlockItem(ModBlocks.LUAU_TOTEM.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_TREE = ITEMS.register("winter_star_tree",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_TREE.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_LARGE_RED_CANDY_CANE = ITEMS.register("winter_star_large_red_candy_cane",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_LARGE_RED_CANDY_CANE.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_LARGE_GREEN_CANDY_CANE = ITEMS.register("winter_star_large_green_candy_cane",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_LARGE_GREEN_CANDY_CANE.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_MIXED_CANDY_CANES = ITEMS.register("winter_star_mixed_candy_canes",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_MIXED_CANDY_CANES.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_RED_CANDY_CANES = ITEMS.register("winter_star_red_candy_canes",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_RED_CANDY_CANES.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_GREEN_CANDY_CANES = ITEMS.register("winter_star_green_candy_canes",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_GREEN_CANDY_CANES.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_PURPLE_GIFT_BOX = ITEMS.register("winter_star_purple_gift_box",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_PURPLE_GIFT_BOX.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_RED_GIFT_BOX = ITEMS.register("winter_star_red_gift_box",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_RED_GIFT_BOX.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_GREEN_GIFT_BOX = ITEMS.register("winter_star_green_gift_box",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_GREEN_GIFT_BOX.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WINTER_STAR_BLUE_GIFT_BOX = ITEMS.register("winter_star_blue_gift_box",
                        () -> new StardewBlockItem(ModBlocks.WINTER_STAR_BLUE_GIFT_BOX.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SQUID_FEST_PROMO_POSTER = ITEMS.register("squid_fest_promo_poster",
                        () -> new StardewBlockItem(ModBlocks.SQUID_FEST_PROMO_POSTER.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SQUID_FEST_REQUIREMENT_POSTER = ITEMS.register("squid_fest_requirement_poster",
                        () -> new StardewBlockItem(ModBlocks.SQUID_FEST_REQUIREMENT_POSTER.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FAIR_WHEEL = ITEMS.register("fair_wheel",
                        () -> new StardewBlockItem(ModBlocks.FAIR_WHEEL.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FAIR_GRAVE_STONE = ITEMS.register("fair_grave_stone",
                        () -> new StardewBlockItem(ModBlocks.FAIR_GRAVE_STONE.get(), "stardewcraft.type.festival_decoration", -1,
                                        new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SPIRIT_EVE_SPIDER_STATUE = ITEMS.register("spirit_eve_spider_statue",
                        () -> new StardewBlockItem(ModBlocks.SPIRIT_EVE_SPIDER_STATUE.get(), "stardewcraft.type.festival_decoration", -1,
                                        new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SPIRIT_EVE_JACK_O_LANTERN = ITEMS.register("spirit_eve_jack_o_lantern",
                        () -> new StardewBlockItem(ModBlocks.SPIRIT_EVE_JACK_O_LANTERN.get(), "stardewcraft.type.festival_decoration", -1,
                                        new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FAIR_GRILL = ITEMS.register("fair_grill",
                        () -> new StardewBlockItem(ModBlocks.FAIR_GRILL.get(), "stardewcraft.type.festival_decoration", -1,
                                        new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PLUSH_BUNNY = ITEMS.register("plush_bunny",
                        () -> new StardewBlockItem(ModBlocks.PLUSH_BUNNY.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> LAWN_FLAMINGO = ITEMS.register("lawn_flamingo",
                        () -> new StardewBlockItem(ModBlocks.LAWN_FLAMINGO.get(), "stardewcraft.type.festival_decoration", 50, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> HOLIDAY_RIBBON_POST = ITEMS.register("holiday_ribbon_post",
                        () -> new StardewBlockItem(ModBlocks.HOLIDAY_RIBBON_POST.get(), "stardewcraft.type.festival_decoration", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SPRUCE_COUNTER = ITEMS.register("spruce_counter",
                        () -> new StardewBlockItem(ModBlocks.SPRUCE_COUNTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> OAK_ROUND_TABLE = ITEMS.register("oak_round_table",
                        () -> new StardewBlockItem(ModBlocks.OAK_ROUND_TABLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> TABLE_LANTERN = ITEMS.register("table_lantern",
                        () -> new StardewBlockItem(ModBlocks.TABLE_LANTERN.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> JUKEBOX = ITEMS.register("jukebox",
                        () -> new StardewBlockItem(ModBlocks.JUKEBOX.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PINK_TABLECLOTH = ITEMS.register("pink_tablecloth",
                        () -> new com.stardew.craft.item.furniture.PinkTableclothItem(new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> SKY_BLUE_TABLECLOTH = ITEMS.register("sky_blue_tablecloth",
                        () -> new com.stardew.craft.item.furniture.SkyBlueTableclothItem(new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FLORAL_TABLECLOTH = ITEMS.register("floral_tablecloth",
                        () -> new com.stardew.craft.item.furniture.FloralTableclothItem(new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BLANK_TABLECLOTH = ITEMS.register("blank_tablecloth",
                        () -> new com.stardew.craft.item.furniture.BlankTableclothItem(new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> WALLPAPER_BLOCK = ITEMS.register("wallpaper_block",
                        () -> new StardewBlockItem(ModBlocks.WALLPAPER_BLOCK.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> FLOORING_BLOCK = ITEMS.register("flooring_block",
                        () -> new StardewBlockItem(ModBlocks.FLOORING_BLOCK.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

        // 鍦板浘瑁呴グ锛氱毊鍩冨皵鍟嗗簵锛堢涓€鎵癸級
        public static final DeferredItem<Item> SHOP_BASKET = ITEMS.register("shop_basket",
                        () -> new StardewBlockItem(ModBlocks.SHOP_BASKET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_1 = ITEMS.register("shop_crate_fruit_1",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_2 = ITEMS.register("shop_crate_fruit_2",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_3 = ITEMS.register("shop_crate_fruit_3",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_4 = ITEMS.register("shop_crate_fruit_4",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_5 = ITEMS.register("shop_crate_fruit_5",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_5.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_6 = ITEMS.register("shop_crate_fruit_6",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_6.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_7 = ITEMS.register("shop_crate_fruit_7",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_7.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_8 = ITEMS.register("shop_crate_fruit_8",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_8.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_9 = ITEMS.register("shop_crate_fruit_9",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_9.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_CRATE_FRUIT_10 = ITEMS.register("shop_crate_fruit_10",
                        () -> new StardewBlockItem(ModBlocks.SHOP_CRATE_FRUIT_10.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_PACK_BOX = ITEMS.register("shop_pack_box",
                        () -> new StardewBlockItem(ModBlocks.SHOP_PACK_BOX.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_COUNTER_1 = ITEMS.register("shop_counter_1",
                        () -> new StardewBlockItem(ModBlocks.SHOP_COUNTER_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_COUNTER_2 = ITEMS.register("shop_counter_2",
                        () -> new StardewBlockItem(ModBlocks.SHOP_COUNTER_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_COUNTER_3 = ITEMS.register("shop_counter_3",
                        () -> new StardewBlockItem(ModBlocks.SHOP_COUNTER_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SUPERMARKET_SHELF_1 = ITEMS.register("supermarket_shelf_1",
                        () -> new StardewBlockItem(ModBlocks.SUPERMARKET_SHELF_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SUPERMARKET_SHELF_2 = ITEMS.register("supermarket_shelf_2",
                        () -> new StardewBlockItem(ModBlocks.SUPERMARKET_SHELF_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JOJA_SUPERMARKET_CRATE = ITEMS.register("joja_supermarket_crate",
                        () -> new StardewBlockItem(ModBlocks.JOJA_SUPERMARKET_CRATE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SUPERMARKET_CART = ITEMS.register("supermarket_cart",
                        () -> new StardewBlockItem(ModBlocks.SUPERMARKET_CART.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SUPERMARKET_FREEZER = ITEMS.register("supermarket_freezer",
                        () -> new StardewBlockItem(ModBlocks.SUPERMARKET_FREEZER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_SHIPPING_BIN = ITEMS.register("shop_shipping_bin",
                        () -> new StardewBlockItem(ModBlocks.SHOP_SHIPPING_BIN.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_1 = ITEMS.register("carpet_1",
                        () -> new StardewBlockItem(ModBlocks.CARPET_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_2 = ITEMS.register("carpet_2",
                        () -> new StardewBlockItem(ModBlocks.CARPET_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_3 = ITEMS.register("carpet_3",
                        () -> new StardewBlockItem(ModBlocks.CARPET_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_4 = ITEMS.register("carpet_4",
                        () -> new StardewBlockItem(ModBlocks.CARPET_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_5 = ITEMS.register("carpet_5",
                        () -> new StardewBlockItem(ModBlocks.CARPET_5.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_6 = ITEMS.register("carpet_6",
                        () -> new StardewBlockItem(ModBlocks.CARPET_6.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_7 = ITEMS.register("carpet_7",
                        () -> new StardewBlockItem(ModBlocks.CARPET_7.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_8 = ITEMS.register("carpet_8",
                        () -> new StardewBlockItem(ModBlocks.CARPET_8.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_9 = ITEMS.register("carpet_9",
                        () -> new StardewBlockItem(ModBlocks.CARPET_9.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_10 = ITEMS.register("carpet_10",
                        () -> new StardewBlockItem(ModBlocks.CARPET_10.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_11 = ITEMS.register("carpet_11",
                        () -> new StardewBlockItem(ModBlocks.CARPET_11.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_12 = ITEMS.register("carpet_12",
                        () -> new StardewBlockItem(ModBlocks.CARPET_12.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_13 = ITEMS.register("carpet_13",
                        () -> new StardewBlockItem(ModBlocks.CARPET_13.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_14 = ITEMS.register("carpet_14",
                        () -> new StardewBlockItem(ModBlocks.CARPET_14.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_15 = ITEMS.register("carpet_15",
                        () -> new StardewBlockItem(ModBlocks.CARPET_15.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_16 = ITEMS.register("carpet_16",
                        () -> new StardewBlockItem(ModBlocks.CARPET_16.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_17 = ITEMS.register("carpet_17",
                        () -> new StardewBlockItem(ModBlocks.CARPET_17.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_18 = ITEMS.register("carpet_18",
                        () -> new StardewBlockItem(ModBlocks.CARPET_18.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_19 = ITEMS.register("carpet_19",
                        () -> new StardewBlockItem(ModBlocks.CARPET_19.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_20 = ITEMS.register("carpet_20",
                        () -> new StardewBlockItem(ModBlocks.CARPET_20.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARPET_21 = ITEMS.register("carpet_21",
                        () -> new StardewBlockItem(ModBlocks.CARPET_21.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_HANGING_SMALL_A = ITEMS.register("wall_hanging_small_a",
                        () -> new StardewBlockItem(ModBlocks.WALL_HANGING_SMALL_A.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_NOTICE_BOARD_SMALL = ITEMS.register("wall_notice_board_small",
                        () -> new StardewBlockItem(ModBlocks.WALL_NOTICE_BOARD_SMALL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_HANGING_SMALL_B = ITEMS.register("wall_hanging_small_b",
                        () -> new StardewBlockItem(ModBlocks.WALL_HANGING_SMALL_B.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_BULLETIN_NOTES = ITEMS.register("wall_bulletin_notes",
                        () -> new StardewBlockItem(ModBlocks.WALL_BULLETIN_NOTES.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_HANGING_STRIP = ITEMS.register("wall_hanging_strip",
                        () -> new StardewBlockItem(ModBlocks.WALL_HANGING_STRIP.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_SWITCH_PANEL = ITEMS.register("wall_switch_panel",
                        () -> new StardewBlockItem(ModBlocks.WALL_SWITCH_PANEL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_HANGING_TRIPTYCH = ITEMS.register("wall_hanging_triptych",
                        () -> new StardewBlockItem(ModBlocks.WALL_HANGING_TRIPTYCH.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_HANGING_ORNAMENT = ITEMS.register("wall_hanging_ornament",
                        () -> new StardewBlockItem(ModBlocks.WALL_HANGING_ORNAMENT.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_NOTICE_BOARD_MEDIUM = ITEMS.register("wall_notice_board_medium",
                        () -> new StardewBlockItem(ModBlocks.WALL_NOTICE_BOARD_MEDIUM.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_FRAME_WIDE = ITEMS.register("wall_frame_wide",
                        () -> new StardewBlockItem(ModBlocks.WALL_FRAME_WIDE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_FRAME_DOUBLE = ITEMS.register("wall_frame_double",
                        () -> new StardewBlockItem(ModBlocks.WALL_FRAME_DOUBLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_STICKY_NOTES = ITEMS.register("wall_sticky_notes",
                        () -> new StardewBlockItem(ModBlocks.WALL_STICKY_NOTES.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_POSTER_GAMEPAD = ITEMS.register("wall_poster_gamepad",
                        () -> new StardewBlockItem(ModBlocks.WALL_POSTER_GAMEPAD.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_POSTER_DOLPHIN = ITEMS.register("wall_poster_dolphin",
                        () -> new StardewBlockItem(ModBlocks.WALL_POSTER_DOLPHIN.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_POSTER_GAME_CHARACTER = ITEMS.register("wall_poster_game_character",
                        () -> new StardewBlockItem(ModBlocks.WALL_POSTER_GAME_CHARACTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_OUTLET = ITEMS.register("wall_outlet",
                        () -> new StardewBlockItem(ModBlocks.WALL_OUTLET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_PHOTO_WHITE_HALL = ITEMS.register("wall_photo_white_hall",
                        () -> new StardewBlockItem(ModBlocks.WALL_PHOTO_WHITE_HALL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_BLACKSMITH_SIGN = ITEMS.register("wall_blacksmith_sign",
                        () -> new StardewBlockItem(ModBlocks.WALL_BLACKSMITH_SIGN.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALL_BLACKSMITH_HAMMERS = ITEMS.register("wall_blacksmith_hammers",
                        () -> new StardewBlockItem(ModBlocks.WALL_BLACKSMITH_HAMMERS.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SQUID_KID_PAINTING = ITEMS.register("squid_kid_painting",
                        () -> new StardewBlockItem(ModBlocks.SQUID_KID_PAINTING.get(), "stardewcraft.type.furniture_painting", 500, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RED_EAGLE = nightMarketPainting("red_eagle", ModBlocks.RED_EAGLE);
        public static final DeferredItem<Item> PORTRAIT_OF_A_MERMAID = nightMarketPainting("portrait_of_a_mermaid", ModBlocks.PORTRAIT_OF_A_MERMAID);
        public static final DeferredItem<Item> SOLAR_KINGDOM = nightMarketPainting("solar_kingdom", ModBlocks.SOLAR_KINGDOM);
        public static final DeferredItem<Item> CLOUDS = nightMarketPainting("clouds", ModBlocks.CLOUDS);
        public static final DeferredItem<Item> THOUSAND_YEARS_FROM_NOW = nightMarketPainting("1000_years_from_now", ModBlocks.THOUSAND_YEARS_FROM_NOW);
        public static final DeferredItem<Item> THREE_TREES = nightMarketPainting("three_trees", ModBlocks.THREE_TREES);
        public static final DeferredItem<Item> THE_SERPENT = nightMarketPainting("the_serpent", ModBlocks.THE_SERPENT);
        public static final DeferredItem<Item> TROPICAL_FISH_173 = nightMarketPainting("tropical_fish_173", ModBlocks.TROPICAL_FISH_173);
        public static final DeferredItem<Item> LAND_OF_CLAY = nightMarketPainting("land_of_clay", ModBlocks.LAND_OF_CLAY);

        private static DeferredItem<Item> nightMarketPainting(String name, DeferredBlock<?> block) {
                return ITEMS.register(name,
                                () -> new StardewBlockItem(block.get(), "stardewcraft.type.furniture_painting", 1000,
                                                new Item.Properties().stacksTo(999)));
        }
        public static final DeferredItem<Item> SHOP_WINDOW_1 = ITEMS.register("shop_window_1",
                        () -> new StardewBlockItem(ModBlocks.SHOP_WINDOW_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHOP_WINDOW_2 = ITEMS.register("shop_window_2",
                        () -> new StardewBlockItem(ModBlocks.SHOP_WINDOW_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FIREPLACE_LARGE = ITEMS.register("fireplace_large",
                        () -> new StardewBlockItem(ModBlocks.FIREPLACE_LARGE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONSAI_1 = ITEMS.register("bonsai_1",
                        () -> new StardewBlockItem(ModBlocks.BONSAI_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONSAI_2 = ITEMS.register("bonsai_2",
                        () -> new StardewBlockItem(ModBlocks.BONSAI_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONSAI_3 = ITEMS.register("bonsai_3",
                        () -> new StardewBlockItem(ModBlocks.BONSAI_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONSAI_4 = ITEMS.register("bonsai_4",
                        () -> new StardewBlockItem(ModBlocks.BONSAI_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONSAI_5_WALL = ITEMS.register("bonsai_5_wall",
                        () -> new StardewBlockItem(ModBlocks.BONSAI_5_WALL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONSAI_BUSH = ITEMS.register("bonsai_bush",
                        () -> new StardewBlockItem(ModBlocks.BONSAI_BUSH.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> COMPUTER = ITEMS.register("computer",
                        () -> new StardewBlockItem(ModBlocks.COMPUTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SAILBOAT = ITEMS.register("sailboat",
                        () -> new StardewBlockItem(ModBlocks.SAILBOAT.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DRESSER_1 = ITEMS.register("dresser_1",
                        () -> new StardewBlockItem(ModBlocks.DRESSER_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DRESSER_2 = ITEMS.register("dresser_2",
                        () -> new StardewBlockItem(ModBlocks.DRESSER_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DRESSER_3 = ITEMS.register("dresser_3",
                        () -> new StardewBlockItem(ModBlocks.DRESSER_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> REDWOOD_WARDROBE = ITEMS.register("redwood_wardrobe",
                        () -> new StardewBlockItem(ModBlocks.REDWOOD_WARDROBE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALNUT_WARDROBE = ITEMS.register("walnut_wardrobe",
                        () -> new StardewBlockItem(ModBlocks.WALNUT_WARDROBE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OAK_WARDROBE = ITEMS.register("oak_wardrobe",
                        () -> new StardewBlockItem(ModBlocks.OAK_WARDROBE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OAK_BEDSIDE_CABINET = ITEMS.register("oak_bedside_cabinet",
                        () -> new StardewBlockItem(ModBlocks.OAK_BEDSIDE_CABINET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OAK_DRESSER = ITEMS.register("oak_dresser",
                        () -> new StardewBlockItem(ModBlocks.OAK_DRESSER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> REDWOOD_BEDSIDE_CABINET = ITEMS.register("redwood_bedside_cabinet",
                        () -> new StardewBlockItem(ModBlocks.REDWOOD_BEDSIDE_CABINET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> REDWOOD_DRESSER = ITEMS.register("redwood_dresser",
                        () -> new StardewBlockItem(ModBlocks.REDWOOD_DRESSER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALNUT_BEDSIDE_CABINET = ITEMS.register("walnut_bedside_cabinet",
                        () -> new StardewBlockItem(ModBlocks.WALNUT_BEDSIDE_CABINET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WALNUT_DRESSER = ITEMS.register("walnut_dresser",
                        () -> new StardewBlockItem(ModBlocks.WALNUT_DRESSER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WOOD_BUNDLE = ITEMS.register("wood_bundle",
                        () -> new StardewBlockItem(ModBlocks.WOOD_BUNDLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BARREL = ITEMS.register("barrel",
                        () -> new StardewBlockItem(ModBlocks.BARREL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTTED_PLANT_1 = ITEMS.register("potted_plant_1",
                        () -> new StardewBlockItem(ModBlocks.POTTED_PLANT_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTTED_PLANT_2 = ITEMS.register("potted_plant_2",
                        () -> new StardewBlockItem(ModBlocks.POTTED_PLANT_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTTED_PLANT_3 = ITEMS.register("potted_plant_3",
                        () -> new StardewBlockItem(ModBlocks.POTTED_PLANT_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTTED_PLANT_4 = ITEMS.register("potted_plant_4",
                        () -> new StardewBlockItem(ModBlocks.POTTED_PLANT_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTTED_PLANT_5 = ITEMS.register("potted_plant_5",
                        () -> new StardewBlockItem(ModBlocks.POTTED_PLANT_5.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTTED_PLANT_6 = ITEMS.register("potted_plant_6",
                        () -> new StardewBlockItem(ModBlocks.POTTED_PLANT_6.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SHRINE = ITEMS.register("shrine",
                        () -> new StardewBlockItem(ModBlocks.SHRINE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        // ── 稻草人 / Rarecrow（item type=stardewcraft.type.scarecrow） ──
        public static final DeferredItem<Item> SCARECROW_0 = ITEMS.register("scarecrow_0",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_0.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_1 = ITEMS.register("scarecrow_1",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_1.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_2 = ITEMS.register("scarecrow_2",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_2.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_3 = ITEMS.register("scarecrow_3",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_3.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_4 = ITEMS.register("scarecrow_4",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_4.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_5 = ITEMS.register("scarecrow_5",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_5.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_6 = ITEMS.register("scarecrow_6",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_6.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_7 = ITEMS.register("scarecrow_7",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_7.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_8 = ITEMS.register("scarecrow_8",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_8.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SCARECROW_9 = ITEMS.register("scarecrow_9",
                        () -> new StardewBlockItem(ModBlocks.SCARECROW_9.get(), "stardewcraft.type.scarecrow", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JUNIMO_HUT_DECOR = ITEMS.register("junimo_hut_decor",
                        () -> new StardewBlockItem(ModBlocks.JUNIMO_HUT_DECOR.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PILLAR = ITEMS.register("pillar",
                        () -> new StardewBlockItem(ModBlocks.PILLAR.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RADIO = ITEMS.register("radio",
                        () -> new StardewBlockItem(ModBlocks.RADIO.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOOK_STACK_1 = ITEMS.register("book_stack_1",
                        () -> new StardewBlockItem(ModBlocks.BOOK_STACK_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOOK_STACK_2 = ITEMS.register("book_stack_2",
                        () -> new StardewBlockItem(ModBlocks.BOOK_STACK_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOOK_STACK_3 = ITEMS.register("book_stack_3",
                        () -> new StardewBlockItem(ModBlocks.BOOK_STACK_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOOKSHELF_WALL = ITEMS.register("bookshelf_wall",
                        () -> new StardewBlockItem(ModBlocks.BOOKSHELF_WALL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOOKSHELF_TALL_1 = ITEMS.register("bookshelf_tall_1",
                        () -> new StardewBlockItem(ModBlocks.BOOKSHELF_TALL_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOOKSHELF_TALL_2 = ITEMS.register("bookshelf_tall_2",
                        () -> new StardewBlockItem(ModBlocks.BOOKSHELF_TALL_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

        
    
    // 宸ュ叿 - 闀板垁
    public static final DeferredItem<Item> SCYTHE = ITEMS.register("scythe",
            () -> new ScytheItem(ScytheItem.Tier.NORMAL, new Item.Properties()));

    public static final DeferredItem<Item> GOLDEN_SCYTHE = ITEMS.register("golden_scythe",
            () -> new ScytheItem(ScytheItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<Item> IRIDIUM_SCYTHE = ITEMS.register("iridium_scythe",
            () -> new ScytheItem(ScytheItem.Tier.IRIDIUM, new Item.Properties()));

    // 宸ュ叿 - 鐣滅墽宸ュ叿锛堝崰妯★紝鏃犺€愪箙锛?
    public static final DeferredItem<Item> MILK_PAIL = ITEMS.register("milk_pail",
            () -> new SimpleStardewItem("stardewcraft.tool.scythe", -1, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> SHEARS = ITEMS.register("shears",
            () -> new SimpleStardewItem("stardewcraft.tool.scythe", -1, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> AUTOFEED_TROUGH_UPGRADER = ITEMS.register("autofeed_trough_upgrader",
            () -> new AutoFeedTroughUpgraderItem(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<Item> PAINTBRUSH = ITEMS.register("paintbrush",
            () -> new PaintbrushItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> ROUTE_EDITOR_WAND = ITEMS.register("route_editor_wand",
            () -> new RouteEditorWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> POINT_PLAN_WAND = ITEMS.register("point_plan_wand",
            () -> new PointPlanWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> MINE_TOTEM = ITEMS.register("mine_totem",
            () -> new com.stardew.craft.item.tool.MineTotemItem(new Item.Properties().stacksTo(1)));

    // Decoration icons are standalone items for shop/catalog usage.
    // They are intentionally hidden from creative tab population by using a non-listed type key.
    public static final DeferredItem<Item> WALLPAPER_ICON = ITEMS.register("wallpaper_icon",
            () -> new SimpleStardewItem("stardewcraft.type.hidden", -1, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> FLOORING_ICON = ITEMS.register("flooring_icon",
            () -> new SimpleStardewItem("stardewcraft.type.hidden", -1, new Item.Properties().stacksTo(1)));

    // 宸ュ叿 - 闀愬瓙锛堟槦闇茶胺鍛藉悕锛氬熀纭€/閾?閽?閲?閾憋紱鍐呴儴 tier0-4 閫昏緫涓嶅彉锛?
    public static final DeferredItem<Item> PICKAXE = ITEMS.register("pickaxe",
            () -> new StardewPickaxeItem(0, Tiers.WOOD, 0.0F, new Item.Properties()));

    public static final DeferredItem<Item> COPPER_PICKAXE = ITEMS.register("copper_pickaxe",
            () -> new StardewPickaxeItem(1, Tiers.STONE, 0.0F, new Item.Properties()));

    public static final DeferredItem<Item> STEEL_PICKAXE = ITEMS.register("steel_pickaxe",
            () -> new StardewPickaxeItem(2, Tiers.IRON, 0.0F, new Item.Properties()));

    public static final DeferredItem<Item> GOLD_PICKAXE = ITEMS.register("gold_pickaxe",
            () -> new StardewPickaxeItem(3, Tiers.DIAMOND, 0.0F, new Item.Properties()));

    public static final DeferredItem<Item> IRIDIUM_PICKAXE = ITEMS.register("iridium_pickaxe",
            () -> new StardewPickaxeItem(4, Tiers.NETHERITE, 1.0F, new Item.Properties()));

    // 宸ュ叿 - 閿勫ご
    public static final DeferredItem<Item> HOE = ITEMS.register("hoe",
            () -> new HoeItem(HoeItem.Tier.STARTER, new Item.Properties()));

    public static final DeferredItem<Item> COPPER_HOE = ITEMS.register("copper_hoe",
            () -> new HoeItem(HoeItem.Tier.COPPER, new Item.Properties()));

    public static final DeferredItem<Item> STEEL_HOE = ITEMS.register("steel_hoe",
            () -> new HoeItem(HoeItem.Tier.STEEL, new Item.Properties()));

    public static final DeferredItem<Item> GOLD_HOE = ITEMS.register("gold_hoe",
            () -> new HoeItem(HoeItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<Item> IRIDIUM_HOE = ITEMS.register("iridium_hoe",
            () -> new HoeItem(HoeItem.Tier.IRIDIUM, new Item.Properties()));

    // 宸ュ叿 - 鍠峰６
    public static final DeferredItem<Item> WATERING_CAN = ITEMS.register("watering_can",
            () -> new WateringCanItem(WateringCanItem.Tier.STARTER, new Item.Properties()));
            
    public static final DeferredItem<Item> COPPER_WATERING_CAN = ITEMS.register("copper_watering_can",
            () -> new WateringCanItem(WateringCanItem.Tier.COPPER, new Item.Properties()));
            
    public static final DeferredItem<Item> STEEL_WATERING_CAN = ITEMS.register("steel_watering_can",
            () -> new WateringCanItem(WateringCanItem.Tier.STEEL, new Item.Properties()));
            
    public static final DeferredItem<Item> GOLD_WATERING_CAN = ITEMS.register("gold_watering_can",
            () -> new WateringCanItem(WateringCanItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<Item> IRIDIUM_WATERING_CAN = ITEMS.register("iridium_watering_can",
            () -> new WateringCanItem(WateringCanItem.Tier.IRIDIUM, new Item.Properties()));

    // 宸ュ叿 - 鏂уご
    public static final DeferredItem<Item> AXE = ITEMS.register("axe",
            () -> new StardewAxeItem(StardewAxeItem.Tier.STARTER, new Item.Properties()));

    public static final DeferredItem<Item> COPPER_AXE = ITEMS.register("copper_axe",
            () -> new StardewAxeItem(StardewAxeItem.Tier.COPPER, new Item.Properties()));

    public static final DeferredItem<Item> STEEL_AXE = ITEMS.register("steel_axe",
            () -> new StardewAxeItem(StardewAxeItem.Tier.STEEL, new Item.Properties()));

    public static final DeferredItem<Item> GOLD_AXE = ITEMS.register("gold_axe",
            () -> new StardewAxeItem(StardewAxeItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<Item> IRIDIUM_AXE = ITEMS.register("iridium_axe",
            () -> new StardewAxeItem(StardewAxeItem.Tier.IRIDIUM, new Item.Properties()));

    // 宸ュ叿 - 閽撻奔绔?
    // Keep legacy id `fishing_rod` as Bamboo Pole.
    public static final DeferredItem<Item> FISHING_ROD = ITEMS.register("fishing_rod",
            () -> new FishingRodItem(FishingRodItem.RodTier.BAMBOO_POLE, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> TRAINING_ROD = ITEMS.register("training_rod",
            () -> new FishingRodItem(FishingRodItem.RodTier.TRAINING_ROD, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> FIBERGLASS_ROD = ITEMS.register("fiberglass_rod",
            () -> new FishingRodItem(FishingRodItem.RodTier.FIBERGLASS_ROD, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> IRIDIUM_ROD = ITEMS.register("iridium_rod",
            () -> new FishingRodItem(FishingRodItem.RodTier.IRIDIUM_ROD, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> ADVANCED_IRIDIUM_ROD = ITEMS.register("advanced_iridium_rod",
            () -> new FishingRodItem(FishingRodItem.RodTier.ADVANCED_IRIDIUM_ROD, new Item.Properties().stacksTo(1)));

    // 法师塔指南针
    public static final DeferredItem<Item> WIZARD_TOWER_COMPASS = ITEMS.register("wizard_tower_compass",
            () -> new com.stardew.craft.item.tool.WizardTowerCompassItem(new Item.Properties().stacksTo(1)));

    // 淘金盘 — CC Fish Tank 奖励 + 铁匠 4 档升级
    public static final DeferredItem<Item> COPPER_PAN = ITEMS.register("copper_pan",
            () -> new com.stardew.craft.item.tool.PanItem(com.stardew.craft.item.tool.PanItem.Tier.COPPER, new Item.Properties()));
    public static final DeferredItem<Item> STEEL_PAN = ITEMS.register("steel_pan",
            () -> new com.stardew.craft.item.tool.PanItem(com.stardew.craft.item.tool.PanItem.Tier.STEEL, new Item.Properties()));
    public static final DeferredItem<Item> GOLD_PAN = ITEMS.register("gold_pan",
            () -> new com.stardew.craft.item.tool.PanItem(com.stardew.craft.item.tool.PanItem.Tier.GOLD, new Item.Properties()));
    public static final DeferredItem<Item> IRIDIUM_PAN = ITEMS.register("iridium_pan",
            () -> new com.stardew.craft.item.tool.PanItem(com.stardew.craft.item.tool.PanItem.Tier.IRIDIUM, new Item.Properties()));

    // 閽撻奔 - 楸奸サ (stackable)
    public static final DeferredItem<Item> BAIT = ITEMS.register("bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> MAGNET = ITEMS.register("magnet",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 15, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> WILD_BAIT = ITEMS.register("wild_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 15, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> MAGIC_BAIT = ITEMS.register("magic_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> DELUXE_BAIT = ITEMS.register("deluxe_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> CHALLENGE_BAIT = ITEMS.register("challenge_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> TARGETED_BAIT = ITEMS.register("targeted_bait",
            () -> new SpecificBaitItem(new Item.Properties().stacksTo(999)));

    // 閽撻奔 - 娓斿叿 (non-stackable)
    public static final DeferredItem<Item> SPINNER = ITEMS.register("spinner",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> DRESSED_SPINNER = ITEMS.register("dressed_spinner",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 500, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> TRAP_BOBBER = ITEMS.register("trap_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 200, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> CORK_BOBBER = ITEMS.register("cork_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> LEAD_BOBBER = ITEMS.register("lead_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 150, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> TREASURE_HUNTER = ITEMS.register("treasure_hunter",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> BARBED_HOOK = ITEMS.register("barbed_hook",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 500, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> CURIOSITY_LURE = ITEMS.register("curiosity_lure",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 500, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> QUALITY_BOBBER = ITEMS.register("quality_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 300, new Item.Properties().stacksTo(1).durability(20)));

    public static final DeferredItem<Item> SONAR_BOBBER = ITEMS.register("sonar_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20)));

    // 璧勬簮/鏉傞」
    public static final DeferredItem<Item> CLAY = ITEMS.register("clay",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 20, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> FIBER = ITEMS.register("fiber",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 1, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> HAY = ITEMS.register("hay",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 0, new Item.Properties().stacksTo(999)));

	// 鏈ㄦ潗锛堝榻?Stardew Valley锛氳祫婧愶級
	public static final DeferredItem<Item> WOOD_NORMAL = ITEMS.register("wood_normal",
			() -> new SimpleStardewItem("stardewcraft.type.resource", 2, new Item.Properties().stacksTo(999)));

	// 纭湪锛堝榻?Stardew Valley锛氳祫婧愶級
	public static final DeferredItem<Item> WOOD_HARD = ITEMS.register("wood_hard",
			() -> new SimpleStardewItem("stardewcraft.type.resource", 15, new Item.Properties().stacksTo(999)));

    // Vanilla object 348/341. Winter Star's generic return-gift pool requires
    // these exact base objects (not a fruit-specific wine or furniture block).
    public static final DeferredItem<Item> WINE = ITEMS.register("wine",
            () -> new ArtisanDrinkItem(400, 50, 22, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> TEA_SET = ITEMS.register("tea_set",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 200, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> OAK_RESIN = ITEMS.register("oak_resin",
            () -> new SimpleStardewItem("stardewcraft.type.artisan_goods", 150, new Item.Properties().stacksTo(999)));

        // 鏍戞恫閲囬泦鍣ㄤ骇鐗╋紙瀵归綈鍘熺増 Stardew Valley锛?
        public static final DeferredItem<Item> MAPLE_SYRUP = ITEMS.register("maple_syrup",
                        () -> new EdibleSimpleStardewItem("stardewcraft.type.artisan_goods", 200, 50, 22, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PINE_TAR = ITEMS.register("pine_tar",
                        () -> new SimpleStardewItem("stardewcraft.type.artisan_goods", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SAP = ITEMS.register("sap",
                        () -> new EdibleSimpleStardewItem("stardewcraft.type.resource", 2, -2, 0, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MYSTIC_SYRUP = ITEMS.register("mystic_syrup",
                        () -> new EdibleSimpleStardewItem("stardewcraft.type.artisan_goods", 1000, 500, 225, true, new Item.Properties().stacksTo(999)));

        // Special item: permanently doubles produce for one farm animal (except pigs).
        public static final DeferredItem<Item> GOLDEN_ANIMAL_CRACKER = ITEMS.register("golden_animal_cracker",
                        () -> new SimpleStardewItem("stardewcraft.type.misc", 1000, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> PRIZE_TICKET = ITEMS.register("prize_ticket",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 0, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> CALICO_EGG = ITEMS.register("calico_egg",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 0, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> GOLDEN_TAG = ITEMS.register("golden_tag",
                        () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> MYSTERY_BOX = ITEMS.register("mystery_box",
                        () -> new SimpleStardewItem("stardewcraft.type.misc", 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLDEN_MYSTERY_BOX = ITEMS.register("golden_mystery_box",
                        () -> new SimpleStardewItem("stardewcraft.type.misc", 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STARDROP_TEA = ITEMS.register("stardrop_tea",
                        () -> new StardropTeaItem(new Item.Properties().stacksTo(999).fireResistant()));
        public static final DeferredItem<Item> MONEY_CONTRACT = ITEMS.register("money_contract",
                        () -> new MoneyContractItem(new Item.Properties().stacksTo(1).fireResistant()));
        public static final DeferredItem<Item> AUCTION_PADDLE = ITEMS.register("auction_paddle",
                        () -> new AuctionPaddleItem(new Item.Properties().stacksTo(1).fireResistant()));
        public static final DeferredItem<Item> STARDROP = ITEMS.register("stardrop",
                        () -> new StardropItem(new Item.Properties().stacksTo(999).fireResistant()));
        public static final DeferredItem<Item> IRIDIUM_MILK = ITEMS.register("iridium_milk",
                        () -> new IridiumMilkItem(new Item.Properties().stacksTo(999).fireResistant()));
        public static final DeferredItem<Item> GALAXY_SOUL = ITEMS.register("galaxy_soul",
                        () -> new GalaxySoulItem(new Item.Properties().stacksTo(999).fireResistant()));
        public static final DeferredItem<Item> GOLDEN_PUMPKIN = ITEMS.register("golden_pumpkin",
                        () -> new GoldenPumpkinItem(new Item.Properties().stacksTo(999).fireResistant()));
        public static final DeferredItem<Item> FIELD_SNACK = ITEMS.register("field_snack",
                        () -> new StardewQualityItem("stardewcraft.type.cooking", 20, 18, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MAGIC_ROCK_CANDY = ITEMS.register("magic_rock_candy",
                        () -> new MagicRockCandyItem(new Item.Properties().stacksTo(999).fireResistant()));
        public static final DeferredItem<Item> WISP_TRAIL_ELIXIR = ITEMS.register("wisp_trail_elixir",
                        () -> new RouteGuidanceConsumableItem(
                                        com.stardew.craft.route.RouteGuidanceRegistry.SPIRIT_EVE_MAZE_EXIT,
                                        120 * 20,
                                        100,
                                        com.stardew.craft.festival.SpiritEveFestivalService.FESTIVAL_ID,
                                        "stardewcraft.route_guidance.spirit_eve_only",
                                        new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GREEN_TEA = ITEMS.register("green_tea",
                        () -> new CookingDishItem(100, 5, java.util.List.of(
                                        new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 30, 360 * 20),
                                        new CookingDishItem.DishBuff(CookingDishItem.BuffType.SPEED, 1, 360 * 20)),
                                        new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> OIL_OF_GARLIC = ITEMS.register("oil_of_garlic",
                        () -> new CookingDishItem(1000, 80, java.util.List.of(
                                        new CookingDishItem.DishBuff(CookingDishItem.BuffType.AVOID_MONSTERS, 1, 600 * 20)),
                                        new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> LIFE_ELIXIR = ITEMS.register("life_elixir",
                        () -> new LifeElixirItem("stardewcraft.type.cooking", 250, 80, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PINA_COLADA = ITEMS.register("pina_colada",
                        () -> new CookingDishItem(300, 30, java.util.List.of(), new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> BUG_STEAK = ITEMS.register("bug_steak",
                        () -> new StardewQualityItem("stardewcraft.type.cooking", 50, 18, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TREASURE_CHEST = ITEMS.register("treasure_chest",
                        () -> new SimpleStardewItem("stardewcraft.type.misc", 5000, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PEARL = ITEMS.register("pearl",
                        () -> new SimpleStardewItem("stardewcraft.type.misc", 2500, new Item.Properties().stacksTo(999)));

        // Clinic medicine items (Harvey's hospital shop)
        public static final DeferredItem<Item> ENERGY_TONIC = ITEMS.register("energy_tonic",
                        () -> new StardewQualityItem("stardewcraft.type.misc", 500, 200, false, new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> MUSCLE_REMEDY = ITEMS.register("muscle_remedy",
                        () -> new com.stardew.craft.item.misc.MuscleRemedyItem("stardewcraft.type.misc", 500, 20, false, new Item.Properties().stacksTo(999)));

        // 鍔ㄧ墿浜х墿
        public static final DeferredItem<Item> EGG_WHITE = ITEMS.register("egg_white",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 50, 10, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> EGG_BROWN = ITEMS.register("egg_brown",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 50, 10, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LARGE_EGG_WHITE = ITEMS.register("large_egg_white",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 95, 15, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LARGE_EGG_BROWN = ITEMS.register("large_egg_brown",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 95, 15, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DUCK_EGG = ITEMS.register("duck_egg",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 95, 15, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> VOID_EGG = ITEMS.register("void_egg",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 65, -15, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLDEN_EGG = ITEMS.register("golden_egg",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 500, 10, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OSTRICH_EGG = ITEMS.register("ostrich_egg",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 600, 15, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MILK = ITEMS.register("milk",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 125, 15, true, new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> LARGE_MILK = ITEMS.register("large_milk",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 190, 20, true, new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> GOAT_MILK = ITEMS.register("goat_milk",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 225, 25, true, new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> LARGE_GOAT_MILK = ITEMS.register("large_goat_milk",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 345, 35, true, new Item.Properties().stacksTo(999), true));
        public static final DeferredItem<Item> WOOL = ITEMS.register("wool",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 340, -300, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DUCK_FEATHER = ITEMS.register("duck_feather",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 250, -300, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RABBITS_FOOT = ITEMS.register("rabbits_foot",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 565, -300, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TRUFFLE = ITEMS.register("truffle",
                        () -> new StardewQualityItem("stardewcraft.type.animal_product", 625, 5, true, new Item.Properties().stacksTo(999)));

        // 鍔ㄧ墿宸ュ尃鐗╁搧锛堟湁鍝佽川锛?
        public static final DeferredItem<Item> CHEESE = ITEMS.register("cheese",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_animal_quality", 230, 50, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOAT_CHEESE = ITEMS.register("goat_cheese",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_animal_quality", 400, 50, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MAYONNAISE = ITEMS.register("mayonnaise",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_animal_quality", 190, 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DINOSAUR_MAYONNAISE = ITEMS.register("dinosaur_mayonnaise",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_animal_quality", 800, 50, true, new Item.Properties().stacksTo(999)));

        // 鍔ㄧ墿宸ュ尃鐗╁搧锛堟棤鍝佽川锛?
        public static final DeferredItem<Item> DUCK_MAYONNAISE = ITEMS.register("duck_mayonnaise",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_goods", 375, 30, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> VOID_MAYONNAISE = ITEMS.register("void_mayonnaise",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_goods", 275, -30, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CLOTH = ITEMS.register("cloth",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_goods", 470, -300, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OIL = ITEMS.register("oil",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_goods", 100, 5, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TRUFFLE_OIL = ITEMS.register("truffle_oil",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_goods", 1065, 15, false, new Item.Properties().stacksTo(999)));

        // 宸ュ尃鐗╁搧 - 鑴辨按鏈轰骇鐗?
        public static final DeferredItem<Item> RAISINS = ITEMS.register("raisins",
                        () -> new StardewQualityItem("stardewcraft.type.artisan_goods", 600, 50, false, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DRIED_FRUIT = ITEMS.register("dried_fruit",
                        () -> new PreservesItem(PreserveType.DRIED_FRUIT, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DRIED_MUSHROOMS = ITEMS.register("dried_mushrooms",
                        () -> new DriedMushroomsItem(new Item.Properties().stacksTo(999)));

        // 宸ュ尃鐗╁搧 - 灏忔《浜х墿锛堥ギ鍝侊級
        public static final DeferredItem<Item> HONEY = ITEMS.register("honey",
                        () -> new HoneyItem("stardewcraft.type.artisan_goods", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JELLY = ITEMS.register("jelly",
                        () -> new PreservesItem(PreserveType.JELLY, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PICKLES = ITEMS.register("pickles",
                        () -> new PreservesItem(PreserveType.PICKLES, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ROE = ITEMS.register("roe",
                        () -> new PreservesItem(PreserveType.ROE, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> AGED_ROE = ITEMS.register("aged_roe",
                        () -> new PreservesItem(PreserveType.AGED_ROE, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CAVIAR = ITEMS.register("caviar",
                        () -> new PreservesItem(PreserveType.CAVIAR, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BEER = ITEMS.register("beer",
                        () -> new ArtisanDrinkItem(200, 50, 22, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PALE_ALE = ITEMS.register("pale_ale",
                        () -> new ArtisanDrinkItem(300, 50, 22, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MEAD = ITEMS.register("mead",
                        () -> new ArtisanDrinkItem(300, 75, 33, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> COFFEE = ITEMS.register("coffee",
                        () -> new ArtisanDrinkItem(150, 3, 1, 1, 83 * 20, new Item.Properties().stacksTo(999)));

        // 宸ュ尃鐗╁搧 - 鏋滈厭
        public static final DeferredItem<Item> ANCIENT_FRUIT_WINE = ITEMS.register("ancient_fruit_wine",
                        () -> new ArtisanDrinkItem(1650, 105, 43, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BLUEBERRY_WINE = ITEMS.register("blueberry_wine",
                        () -> new ArtisanDrinkItem(150, 43, 17, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CRANBERRY_WINE = ITEMS.register("cranberry_wine",
                        () -> new ArtisanDrinkItem(225, 66, 26, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CRYSTAL_FRUIT_WINE = ITEMS.register("crystal_fruit_wine",
                        () -> new ArtisanDrinkItem(450, 108, 43, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GRAPE_WINE = ITEMS.register("grape_wine",
                        () -> new ArtisanDrinkItem(240, 66, 26, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> HOT_PEPPER_WINE = ITEMS.register("hot_pepper_wine",
                        () -> new ArtisanDrinkItem(120, 43, 17, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MELON_WINE = ITEMS.register("melon_wine",
                        () -> new ArtisanDrinkItem(750, 183, 78, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POWDER_MELON_WINE = ITEMS.register("powder_melon_wine",
                        () -> new ArtisanDrinkItem(180, 183, 78, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RHUBARB_WINE = ITEMS.register("rhubarb_wine",
                        () -> new ArtisanDrinkItem(660, 87, 38, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STARFRUIT_WINE = ITEMS.register("starfruit_wine",
                        () -> new ArtisanDrinkItem(2250, 148, 59, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STRAWBERRY_WINE = ITEMS.register("strawberry_wine",
                        () -> new ArtisanDrinkItem(360, 87, 38, -1, 30 * 20, true, new Item.Properties().stacksTo(999)));

        // 宸ュ尃鐗╁搧 - 鏋滄眮
        public static final DeferredItem<Item> AMARANTH_JUICE = ITEMS.register("amaranth_juice",
                        () -> new ArtisanDrinkItem(337, 100, 44, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ARTICHOKE_JUICE = ITEMS.register("artichoke_juice",
                        () -> new ArtisanDrinkItem(360, 150, 60, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BEET_JUICE = ITEMS.register("beet_juice",
                        () -> new ArtisanDrinkItem(225, 60, 26, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BOK_CHOY_JUICE = ITEMS.register("bok_choy_juice",
                        () -> new ArtisanDrinkItem(180, 90, 36, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BROCCOLI_JUICE = ITEMS.register("broccoli_juice",
                        () -> new ArtisanDrinkItem(157, 174, 70, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CARROT_JUICE = ITEMS.register("carrot_juice",
                        () -> new ArtisanDrinkItem(78, 70, 30, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CAULIFLOWER_JUICE = ITEMS.register("cauliflower_juice",
                        () -> new ArtisanDrinkItem(393, 150, 60, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CORN_JUICE = ITEMS.register("corn_juice",
                        () -> new ArtisanDrinkItem(112, 50, 20, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> EGGPLANT_JUICE = ITEMS.register("eggplant_juice",
                        () -> new ArtisanDrinkItem(135, 100, 40, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GARLIC_JUICE = ITEMS.register("garlic_juice",
                        () -> new ArtisanDrinkItem(135, 64, 26, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GREEN_BEAN_JUICE = ITEMS.register("green_bean_juice",
                        () -> new ArtisanDrinkItem(90, 50, 20, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> KALE_JUICE = ITEMS.register("kale_juice",
                        () -> new ArtisanDrinkItem(247, 100, 44, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PARSNIP_JUICE = ITEMS.register("parsnip_juice",
                        () -> new ArtisanDrinkItem(78, 50, 20, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> POTATO_JUICE = ITEMS.register("potato_juice",
                        () -> new ArtisanDrinkItem(180, 50, 20, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PUMPKIN_JUICE = ITEMS.register("pumpkin_juice",
                        () -> new ArtisanDrinkItem(720, 210, 90, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RADISH_JUICE = ITEMS.register("radish_juice",
                        () -> new ArtisanDrinkItem(202, 90, 36, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RED_CABBAGE_JUICE = ITEMS.register("red_cabbage_juice",
                        () -> new ArtisanDrinkItem(585, 150, 60, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SUMMER_SQUASH_JUICE = ITEMS.register("summer_squash_juice",
                        () -> new ArtisanDrinkItem(101, 130, 54, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TOMATO_JUICE = ITEMS.register("tomato_juice",
                        () -> new ArtisanDrinkItem(135, 40, 16, 0, 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> YAM_JUICE = ITEMS.register("yam_juice",
                        () -> new ArtisanDrinkItem(360, 90, 36, 0, 0, new Item.Properties().stacksTo(999)));

        // 鐭夸簳璧勬簮锛堜綘宸叉妸璐村浘鏀惧湪 textures/item/resource 涓嬶級
        public static final DeferredItem<Item> COPPER_ORE = ITEMS.register("copper_ore",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 5, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> IRON_ORE = ITEMS.register("iron_ore",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 10, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLD_ORE = ITEMS.register("gold_ore",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 25, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> IRIDIUM_ORE = ITEMS.register("iridium_ore",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> COPPER_BAR = ITEMS.register("copper_bar",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 60, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> IRON_BAR = ITEMS.register("iron_bar",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 120, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLD_BAR = ITEMS.register("gold_bar",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 250, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> IRIDIUM_BAR = ITEMS.register("iridium_bar",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 1000, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> COAL = ITEMS.register("coal",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 15, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STONE = ITEMS.register("stone",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 2, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> REFINED_QUARTZ = ITEMS.register("refined_quartz",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BATTERY_PACK = ITEMS.register("battery_pack",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 500, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CINDER_SHARD = ITEMS.register("cinder_shard",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> RADIOACTIVE_ORE = ITEMS.register("radioactive_ore",
                                        () -> new SimpleStardewItem("stardewcraft.type.resource", 300, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RADIOACTIVE_BAR = ITEMS.register("radioactive_bar",
                                        () -> new SimpleStardewItem("stardewcraft.type.resource", 3000, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> BOUQUET = ITEMS.register("bouquet",
                                        () -> new SimpleStardewItem("stardewcraft.type.misc", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WILTED_BOUQUET = ITEMS.register("wilted_bouquet",
                                        () -> new SimpleStardewItem("stardewcraft.type.misc", 100, new Item.Properties().stacksTo(999)));

        // 鏅舵礊
        public static final DeferredItem<Item> GEODE = ITEMS.register("geode",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROZEN_GEODE = ITEMS.register("frozen_geode",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MAGMA_GEODE = ITEMS.register("magma_geode",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OMNI_GEODE = ITEMS.register("omni_geode",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", -1, new Item.Properties().stacksTo(999)));
        // 古物宝藏 (Artifact Trove, SDV O-275) — 与晶洞类似，开启后掉落随机古物
        public static final DeferredItem<Item> ARTIFACT_TROVE = ITEMS.register("artifact_trove",
                        () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999)));

        // 鐭跨墿锛圡inerals锛?
        public static final DeferredItem<Item> QUARTZ = ITEMS.register("quartz",
                        () -> new StardewBlockItem(ModBlocks.QUARTZ.get(), "stardewcraft.type.mineral", 25,
                                        new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> EARTH_CRYSTAL = ITEMS.register("earth_crystal",
                        () -> new StardewBlockItem(ModBlocks.EARTH_CRYSTAL.get(), "stardewcraft.type.mineral", 50,
                                        new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FROZEN_TEAR = ITEMS.register("frozen_tear",
                        () -> new StardewBlockItem(ModBlocks.FROZEN_TEAR.get(), "stardewcraft.type.mineral", 75,
                                        new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FIRE_QUARTZ = ITEMS.register("fire_quartz",
                        () -> new StardewBlockItem(ModBlocks.FIRE_QUARTZ.get(), "stardewcraft.type.mineral", 100,
                                        new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> EMERALD = ITEMS.register("emerald",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> AQUAMARINE = ITEMS.register("aquamarine",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 180, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RUBY = ITEMS.register("ruby",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> AMETHYST = ITEMS.register("amethyst",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TOPAZ = ITEMS.register("topaz",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 80, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JADE = ITEMS.register("jade",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DIAMOND = ITEMS.register("diamond",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 750, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PRISMATIC_SHARD = ITEMS.register("prismatic_shard",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 2000, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TIGERSEYE = ITEMS.register("tigerseye",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 275, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OPAL = ITEMS.register("opal",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FIRE_OPAL = ITEMS.register("fire_opal",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 350, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ALAMITE = ITEMS.register("alamite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BIXITE = ITEMS.register("bixite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 300, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BARYTE = ITEMS.register("baryte",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> AERINITE = ITEMS.register("aerinite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 125, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CALCITE = ITEMS.register("calcite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 75, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DOLOMITE = ITEMS.register("dolomite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 300, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ESPERITE = ITEMS.register("esperite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FLUORAPATITE = ITEMS.register("fluorapatite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GEMINITE = ITEMS.register("geminite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> HELVITE = ITEMS.register("helvite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 450, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JAMBORITE = ITEMS.register("jamborite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JAGOITE = ITEMS.register("jagoite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 115, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> KYANITE = ITEMS.register("kyanite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LUNARITE = ITEMS.register("lunarite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MALACHITE = ITEMS.register("malachite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> NEPTUNITE = ITEMS.register("neptunite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 400, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LEMON_STONE = ITEMS.register("lemon_stone",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> NEKOITE = ITEMS.register("nekoite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 80, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ORPIMENT = ITEMS.register("orpiment",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 80, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PETRIFIED_SLIME = ITEMS.register("petrified_slime",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 120, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> THUNDER_EGG = ITEMS.register("thunder_egg",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PYRITE = ITEMS.register("pyrite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 120, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OCEAN_STONE = ITEMS.register("ocean_stone",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 220, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GHOST_CRYSTAL = ITEMS.register("ghost_crystal",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> JASPER = ITEMS.register("jasper",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CELESTINE = ITEMS.register("celestine",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 125, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MARBLE = ITEMS.register("marble",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 110, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SANDSTONE = ITEMS.register("sandstone",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 60, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GRANITE = ITEMS.register("granite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 75, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BASALT = ITEMS.register("basalt",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 175, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LIMESTONE_MINERAL = ITEMS.register("limestone_mineral",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 15, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SOAPSTONE = ITEMS.register("soapstone",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 120, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> HEMATITE = ITEMS.register("hematite",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MUDSTONE = ITEMS.register("mudstone",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 25, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> OBSIDIAN = ITEMS.register("obsidian",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SLATE = ITEMS.register("slate",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 85, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FAIRY_STONE = ITEMS.register("fairy_stone",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STAR_SHARDS = ITEMS.register("star_shards",
                        () -> new SimpleStardewItem("stardewcraft.type.mineral", 500, new Item.Properties().stacksTo(999)));

        // 鍙ょ墿锛圓rtifacts锛?
        public static final DeferredItem<Item> DWARF_SCROLL_I = ITEMS.register("dwarf_scroll_i",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DWARF_SCROLL_II = ITEMS.register("dwarf_scroll_ii",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DWARF_SCROLL_III = ITEMS.register("dwarf_scroll_iii",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DWARF_SCROLL_IV = ITEMS.register("dwarf_scroll_iv",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DWARVISH_TRANSLATION_GUIDE = ITEMS.register("dwarvish_translation_guide",
                        () -> new DwarvishTranslationGuideItem(new Item.Properties().stacksTo(1)));
        public static final DeferredItem<Item> SKULL_KEY = ITEMS.register("skull_key",
                        () -> new SkullKeyItem(new Item.Properties().stacksTo(1).fireResistant()));
        public static final DeferredItem<Item> RUSTY_KEY = ITEMS.register("rusty_key",
                        () -> new RustyKeyItem(new Item.Properties().stacksTo(1).fireResistant()));
        public static final DeferredItem<Item> CHIPPED_AMPHORA = ITEMS.register("chipped_amphora",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 40, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ARROWHEAD = ITEMS.register("arrowhead",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 40, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ANCIENT_DOLL = ITEMS.register("ancient_doll",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 60, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ELVISH_JEWELRY = ITEMS.register("elvish_jewelry",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CHEWING_STICK = ITEMS.register("chewing_stick",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ORNAMENTAL_FAN = ITEMS.register("ornamental_fan",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 300, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DINOSAUR_EGG = ITEMS.register("dinosaur_egg",
                        () -> new StardewQualityItem("stardewcraft.type.artifact_quality", 350, -300, true, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RARE_DISC = ITEMS.register("rare_disc",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 300, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ANCIENT_SWORD = ITEMS.register("ancient_sword",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RUSTY_SPOON = ITEMS.register("rusty_spoon",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 25, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RUSTY_SPUR = ITEMS.register("rusty_spur",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 25, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> RUSTY_COG = ITEMS.register("rusty_cog",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 25, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> CHICKEN_STATUE = ITEMS.register("chicken_statue",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ANCIENT_SEED = ITEMS.register("ancient_seed",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 5, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_TOOL = ITEMS.register("prehistoric_tool",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DRIED_STARFISH = ITEMS.register("dried_starfish",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 40, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ANCHOR = ITEMS.register("anchor",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GLASS_SHARDS = ITEMS.register("glass_shards",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 20, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BONE_FLUTE = ITEMS.register("bone_flute",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_HANDAXE = ITEMS.register("prehistoric_handaxe",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DWARVISH_HELM = ITEMS.register("dwarvish_helm",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> DWARF_GADGET = ITEMS.register("dwarf_gadget",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 200, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> ANCIENT_DRUM = ITEMS.register("ancient_drum",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLDEN_MASK = ITEMS.register("golden_mask",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 500, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLDEN_RELIC = ITEMS.register("golden_relic",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 250, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> GOLDEN_BOBBER = ITEMS.register("golden_bobber",
                        () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> LUCKY_PURPLE_SHORTS = ITEMS.register("lucky_purple_shorts",
                        () -> new LuckyPurpleShortsItem(ModBlocks.LUCKY_PURPLE_SHORTS.get(), new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STRANGE_DOLL_GREEN = ITEMS.register("strange_doll_green",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 1000, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> STRANGE_DOLL_YELLOW = ITEMS.register("strange_doll_yellow",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 1000, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_SCAPULA = ITEMS.register("prehistoric_scapula",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_TIBIA = ITEMS.register("prehistoric_tibia",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_SKULL = ITEMS.register("prehistoric_skull",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SKELETAL_HAND = ITEMS.register("skeletal_hand",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_RIB = ITEMS.register("prehistoric_rib",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PREHISTORIC_VERTEBRA = ITEMS.register("prehistoric_vertebra",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SKELETAL_TAIL = ITEMS.register("skeletal_tail",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> NAUTILUS_FOSSIL = ITEMS.register("nautilus_fossil",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 80, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> AMPHIBIAN_FOSSIL = ITEMS.register("amphibian_fossil",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 150, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PALM_FOSSIL = ITEMS.register("palm_fossil",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> TRILOBITE = ITEMS.register("trilobite",
                        () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> MIXED_SEEDS = ITEMS.register("mixed_seeds",
                        () -> new MixedSeedsItem(new Item.Properties().stacksTo(999)));

        // SDV Wild Seeds (seasonal forage seeds) — IDs 495-498
        // SDV: Spring Seeds sell=35, Summer=55, Fall=45, Winter=30
        public static final DeferredItem<Item> SPRING_SEEDS = ITEMS.register("spring_seeds",
                        () -> new WildSeedsItem(0, 35, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> SUMMER_SEEDS = ITEMS.register("summer_seeds",
                        () -> new WildSeedsItem(1, 55, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> FALL_SEEDS = ITEMS.register("fall_seeds",
                        () -> new WildSeedsItem(2, 45, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> WINTER_SEEDS = ITEMS.register("winter_seeds",
                        () -> new WildSeedsItem(3, 30, new Item.Properties().stacksTo(999)));

        public static final DeferredItem<Item> GRASS_STARTER = ITEMS.register("grass_starter",
                                                () -> new GrassStarterItem(ModBlocks.PASTURE_GRASS, 50, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> BLUE_GRASS_STARTER = ITEMS.register("blue_grass_starter",
                                                () -> new GrassStarterItem(ModBlocks.BLUE_PASTURE_GRASS, 50, new Item.Properties().stacksTo(999)));

        // 鏍戠瀛愶紙鏉ヨ嚜鏄熼湶璋凤細姗″瓙/鏋爲绉嶅瓙/鏉炬灉/妗冭姳蹇冩湪绉嶅瓙/绁炵鏍戠锛?
        public static final DeferredItem<Item> ACORN = ITEMS.register("acorn",
                        () -> new TreeSeedItem(ModBlocks.WILD_OAK_SAPLING0, 20, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MAPLE_SEED = ITEMS.register("maple_seed",
                        () -> new TreeSeedItem(ModBlocks.WILD_MAPLE_SAPLING0, 5, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> PINE_CONE = ITEMS.register("pine_cone",
                        () -> new TreeSeedItem(ModBlocks.WILD_PINE_SAPLING0, 5, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MAHOGANY_SEED = ITEMS.register("mahogany_seed",
                        () -> new TreeSeedItem(ModBlocks.WILD_MAHOGANY_SAPLING0, 100, new Item.Properties().stacksTo(999)));
        public static final DeferredItem<Item> MYSTIC_TREE_SEED = ITEMS.register("mystic_tree_seed",
                        () -> new TreeSeedItem(ModBlocks.WILD_MYSTIC_TREE_SAPLING0, 100, new Item.Properties().stacksTo(999)));
        private static java.util.Map<FruitTreeType, DeferredItem<Item>> registerFruitTreeSaplings() {
                java.util.LinkedHashMap<FruitTreeType, DeferredItem<Item>> items = new java.util.LinkedHashMap<>();
                for (FruitTreeType type : FruitTreeType.values()) {
                        items.put(type, ITEMS.register(type.saplingItemId(),
                                        () -> new com.stardew.craft.item.tree.fruit.FruitTreeSaplingItem(
                                                        type,
                                                        ModBlocks.FRUIT_TREE_SAPLINGS.get(type),
                                                        new Item.Properties().stacksTo(999))));
                }
                return java.util.Collections.unmodifiableMap(items);
        }

        public static final java.util.Map<FruitTreeType, DeferredItem<Item>> FRUIT_TREE_SAPLINGS = registerFruitTreeSaplings();
        public static final DeferredItem<Item> CHERRY_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.CHERRY);
        public static final DeferredItem<Item> APRICOT_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.APRICOT);
        public static final DeferredItem<Item> ORANGE_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.ORANGE);
        public static final DeferredItem<Item> PEACH_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.PEACH);
        public static final DeferredItem<Item> POMEGRANATE_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.POMEGRANATE);
        public static final DeferredItem<Item> APPLE_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.APPLE);
        public static final DeferredItem<Item> BANANA_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.BANANA);
        public static final DeferredItem<Item> MANGO_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.MANGO);

    // 鑲ユ枡锛堝榻?Stardew Valley锛氬搧璐ㄧ被鑲ユ枡锛?
    public static final DeferredItem<Item> BASIC_FERTILIZER = ITEMS.register("basic_fertilizer",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.BASIC_FERTILIZER, 
                    2, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> QUALITY_FERTILIZER = ITEMS.register("quality_fertilizer",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.QUALITY_FERTILIZER, 
                    10, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> DELUXE_FERTILIZER = ITEMS.register("deluxe_fertilizer",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.DELUXE_FERTILIZER, 
                    70, new Item.Properties().stacksTo(999)));

    // 鑲ユ枡锛堝榻?Stardew Valley锛氫繚婀垮湡澹わ級
    public static final DeferredItem<Item> BASIC_RETAINING_SOIL = ITEMS.register("basic_retaining_soil",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.BASIC_RETAINING_SOIL, 
                    4, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> QUALITY_RETAINING_SOIL = ITEMS.register("quality_retaining_soil",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.QUALITY_RETAINING_SOIL, 
                    5, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> DELUXE_RETAINING_SOIL = ITEMS.register("deluxe_retaining_soil",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.DELUXE_RETAINING_SOIL, 
                    30, new Item.Properties().stacksTo(999)));

    // 鑲ユ枡锛堝榻?Stardew Valley锛氱敓闀挎縺绱狅級
    public static final DeferredItem<Item> SPEED_GRO = ITEMS.register("speed_gro",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.SPEED_GRO, 
                    20, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> DELUXE_SPEED_GRO = ITEMS.register("deluxe_speed_gro",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.DELUXE_SPEED_GRO, 
                    40, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HYPER_SPEED_GRO = ITEMS.register("hyper_speed_gro",
            () -> new FertilizerItem(com.stardew.craft.block.FertilizerType.HYPER_SPEED_GRO, 
                    70, new Item.Properties().stacksTo(999)));

    // 鑲ユ枡锛堝榻?Stardew Valley锛氭爲鑲ワ級娉ㄦ剰锛氭爲鑲ヤ笉鐢ㄤ簬鑰曞湴锛屾殏鏃朵繚鐣橲impleStardewItem
    public static final DeferredItem<Item> TREE_FERTILIZER = ITEMS.register("tree_fertilizer",
            () -> new TreeFertilizerItem(10, new Item.Properties().stacksTo(999)));

    // 鎵撻€犵墿鍝?
    public static final DeferredItem<Item> FAIRY_DUST = ITEMS.register("fairy_dust",
            () -> new FairyDustItem(300, new Item.Properties().stacksTo(999)));

    // 绉嶅瓙
    public static final DeferredItem<Item> AMARANTH_SEEDS = ITEMS.register("amaranth_seeds",
            () -> new AmaranthSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ANCIENT_FRUIT_SEEDS = ITEMS.register("ancient_fruit_seeds",
            () -> new AncientFruitSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ARTICHOKE_SEEDS = ITEMS.register("artichoke_seeds",
            () -> new ArtichokeSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BEET_SEEDS = ITEMS.register("beet_seeds",
            () -> new BeetSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BLUE_JAZZ_SEEDS = ITEMS.register("blue_jazz_seeds",
            () -> new BlueJazzSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BLUEBERRY_SEEDS = ITEMS.register("blueberry_seeds",
            () -> new BlueberrySeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BOK_CHOY_SEEDS = ITEMS.register("bok_choy_seeds",
            () -> new BokChoySeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BROCCOLI_SEEDS = ITEMS.register("broccoli_seeds",
            () -> new BroccoliSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CARROT_SEEDS = ITEMS.register("carrot_seeds",
            () -> new CarrotSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CAULIFLOWER_SEEDS = ITEMS.register("cauliflower_seeds",
            () -> new CauliflowerSeedItem(new Item.Properties().stacksTo(999)));
    
    // 娉ㄦ剰锛氬挅鍟¤眴鑷繁灏辨槸绉嶅瓙锛屼笉闇€瑕佸崟鐙殑绉嶅瓙鐗╁搧
    
    public static final DeferredItem<Item> CORN_SEEDS = ITEMS.register("corn_seeds",
            () -> new CornSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CRANBERRY_SEEDS = ITEMS.register("cranberry_seeds",
            () -> new CranberrySeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> EGGPLANT_SEEDS = ITEMS.register("eggplant_seeds",
            () -> new EggplantSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> FAIRY_ROSE_SEEDS = ITEMS.register("fairy_rose_seeds",
            () -> new FairyRoseSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GARLIC_SEEDS = ITEMS.register("garlic_seeds",
            () -> new GarlicSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GRAPE_SEEDS = ITEMS.register("grape_seeds",
            () -> new GrapeSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GREEN_BEAN_SEEDS = ITEMS.register("green_bean_seeds",
            () -> new GreenBeanSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> HOPS_SEEDS = ITEMS.register("hops_seeds",
            () -> new HopsSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> HOT_PEPPER_SEEDS = ITEMS.register("hot_pepper_seeds",
            () -> new HotPepperSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> KALE_SEEDS = ITEMS.register("kale_seeds",
            () -> new KaleSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> MELON_SEEDS = ITEMS.register("melon_seeds",
            () -> new MelonSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PARSNIP_SEEDS = ITEMS.register("parsnip_seeds",
            () -> new ParsnipSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> POPPY_SEEDS = ITEMS.register("poppy_seeds",
            () -> new PoppySeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> POTATO_SEEDS = ITEMS.register("potato_seeds",
            () -> new PotatoSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> POWDER_MELON_SEEDS = ITEMS.register("powder_melon_seeds",
            () -> new PowderMelonSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PUMPKIN_SEEDS = ITEMS.register("pumpkin_seeds",
            () -> new PumpkinSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RADISH_SEEDS = ITEMS.register("radish_seeds",
            () -> new RadishSeedItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> RARE_SEED = ITEMS.register("rare_seed",
            () -> new RareSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RED_CABBAGE_SEEDS = ITEMS.register("red_cabbage_seeds",
            () -> new RedCabbageSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RHUBARB_SEEDS = ITEMS.register("rhubarb_seeds",
            () -> new RhubarbSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> STARFRUIT_SEEDS = ITEMS.register("starfruit_seeds",
            () -> new StarfruitSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> STRAWBERRY_SEEDS = ITEMS.register("strawberry_seeds",
            () -> new StrawberrySeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUMMER_SPANGLE_SEEDS = ITEMS.register("summer_spangle_seeds",
            () -> new SummerSpangleSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUMMER_SQUASH_SEEDS = ITEMS.register("summer_squash_seeds",
            () -> new SummerSquashSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUNFLOWER_SEEDS = ITEMS.register("sunflower_seeds",
            () -> new SunflowerSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TOMATO_SEEDS = ITEMS.register("tomato_seeds",
            () -> new TomatoSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TULIP_SEEDS = ITEMS.register("tulip_seeds",
            () -> new TulipSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> WHEAT_SEEDS = ITEMS.register("wheat_seeds",
            () -> new WheatSeedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> YAM_SEEDS = ITEMS.register("yam_seeds",
            () -> new YamSeedItem(new Item.Properties().stacksTo(999)));
    
    // 浣滅墿 (浣跨敤鍝佽川绯荤粺)
    public static final DeferredItem<Item> AMARANTH = ITEMS.register("amaranth",
            () -> new AmaranthItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ANCIENT_FRUIT = ITEMS.register("ancient_fruit",
            () -> new AncientFruitItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ARTICHOKE = ITEMS.register("artichoke",
            () -> new ArtichokeItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BEET = ITEMS.register("beet",
            () -> new BeetItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BLUE_JAZZ = ITEMS.register("blue_jazz",
            () -> new BlueJazzItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BLUEBERRY = ITEMS.register("blueberry",
            () -> new BlueberryItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BOK_CHOY = ITEMS.register("bok_choy",
            () -> new BokChoyItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BROCCOLI = ITEMS.register("broccoli",
            () -> new BroccoliItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CARROT = ITEMS.register("carrot",
            () -> new CarrotItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CAULIFLOWER = ITEMS.register("cauliflower",
            () -> new CauliflowerItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> COFFEE_BEAN = ITEMS.register("coffee_bean",
            () -> new CoffeeBeanItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CORN = ITEMS.register("corn",
            () -> new CornItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CRANBERRY = ITEMS.register("cranberry",
            () -> new CranberryItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> EGGPLANT = ITEMS.register("eggplant",
            () -> new EggplantItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> FAIRY_ROSE = ITEMS.register("fairy_rose",
            () -> new FairyRoseItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GARLIC = ITEMS.register("garlic",
            () -> new GarlicItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GRAPE = ITEMS.register("grape",
            () -> new GrapeItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GREEN_BEAN = ITEMS.register("green_bean",
            () -> new GreenBeanItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> HOPS = ITEMS.register("hops",
            () -> new HopsItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> HOT_PEPPER = ITEMS.register("hot_pepper",
            () -> new HotPepperItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> KALE = ITEMS.register("kale",
            () -> new KaleItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> MELON = ITEMS.register("melon",
            () -> new MelonItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PARSNIP = ITEMS.register("parsnip",
            () -> new ParsnipItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> POPPY = ITEMS.register("poppy",
            () -> new PoppyItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> POTATO = ITEMS.register("potato",
            () -> new PotatoItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> POWDER_MELON = ITEMS.register("powder_melon",
            () -> new PowderMelonItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PUMPKIN = ITEMS.register("pumpkin",
            () -> new PumpkinItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RADISH = ITEMS.register("radish",
            () -> new RadishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RED_CABBAGE = ITEMS.register("red_cabbage",
            () -> new RedCabbageItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RHUBARB = ITEMS.register("rhubarb",
            () -> new RhubarbItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> STARFRUIT = ITEMS.register("starfruit",
            () -> new StarfruitItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> STRAWBERRY = ITEMS.register("strawberry",
            () -> new StrawberryItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> SWEET_GEM_BERRY = ITEMS.register("sweet_gem_berry",
            () -> new SweetGemBerryItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUMMER_SPANGLE = ITEMS.register("summer_spangle",
            () -> new SummerSpangleItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUMMER_SQUASH = ITEMS.register("summer_squash",
            () -> new SummerSquashItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUNFLOWER = ITEMS.register("sunflower",
            () -> new SunflowerItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TOMATO = ITEMS.register("tomato",
            () -> new TomatoItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TULIP = ITEMS.register("tulip",
            () -> new TulipItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> WHEAT = ITEMS.register("wheat",
            () -> new WheatItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> YAM = ITEMS.register("yam",
            () -> new YamItem(new Item.Properties().stacksTo(999)));
    
    // ==================== 楸肩被 ====================
    
    // 娌虫祦楸肩被
    public static final DeferredItem<Item> CHUB = ITEMS.register("chub",
            () -> new ChubItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BREAM = ITEMS.register("bream",
            () -> new BreamItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SMALLMOUTH_BASS = ITEMS.register("smallmouth_bass",
            () -> new SmallmouthBassItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CATFISH = ITEMS.register("catfish",
            () -> new CatfishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SHAD = ITEMS.register("shad",
            () -> new ShadItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TIGER_TROUT = ITEMS.register("tiger_trout",
            () -> new TigerTroutItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SALMON = ITEMS.register("salmon",
            () -> new SalmonItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> WALLEYE = ITEMS.register("walleye",
            () -> new WalleyeItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PIKE = ITEMS.register("pike",
            () -> new PikeItem(new Item.Properties().stacksTo(999)));
    
    // 婀栨硦楸肩被
    public static final DeferredItem<Item> LARGEMOUTH_BASS = ITEMS.register("largemouth_bass",
            () -> new LargemouthBassItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CARP = ITEMS.register("carp",
            () -> new CarpItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BULLHEAD = ITEMS.register("bullhead",
            () -> new BullheadItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> STURGEON = ITEMS.register("sturgeon",
            () -> new SturgeonItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RAINBOW_TROUT = ITEMS.register("rainbow_trout",
            () -> new RainbowTroutItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PERCH = ITEMS.register("perch",
            () -> new PerchItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUNFISH = ITEMS.register("sunfish",
            () -> new SunfishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> LINGCOD = ITEMS.register("lingcod",
            () -> new LingcodItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> MIDNIGHT_CARP = ITEMS.register("midnight_carp",
            () -> new MidnightCarpItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> WOODSKIP = ITEMS.register("woodskip",
            () -> new WoodskipItem(new Item.Properties().stacksTo(999)));
    
    // 娴锋磱楸肩被
    public static final DeferredItem<Item> SARDINE = ITEMS.register("sardine",
            () -> new SardineItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ANCHOVY = ITEMS.register("anchovy",
            () -> new AnchovyItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> HERRING = ITEMS.register("herring",
            () -> new HerringItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RED_SNAPPER = ITEMS.register("red_snapper",
            () -> new RedSnapperItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> RED_MULLET = ITEMS.register("red_mullet",
            () -> new RedMulletItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SEA_CUCUMBER = ITEMS.register("sea_cucumber",
            () -> new SeaCucumberItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SUPER_CUCUMBER = ITEMS.register("super_cucumber",
            () -> new SuperCucumberItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> FLOUNDER = ITEMS.register("flounder",
            () -> new FlounderItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> HALIBUT = ITEMS.register("halibut",
            () -> new HalibutItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> EEL = ITEMS.register("eel",
            () -> new EelItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> OCTOPUS = ITEMS.register("octopus",
            () -> new OctopusItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SQUID = ITEMS.register("squid",
            () -> new SquidItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TUNA = ITEMS.register("tuna",
            () -> new TunaItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> TILAPIA = ITEMS.register("tilapia",
            () -> new TilapiaItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PUFFERFISH = ITEMS.register("pufferfish",
            () -> new PufferfishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ALBACORE = ITEMS.register("albacore",
            () -> new AlbacoreItem(new Item.Properties().stacksTo(999)));
    
    // 鐗规畩浣嶇疆楸肩被
    public static final DeferredItem<Item> SANDFISH = ITEMS.register("sandfish",
            () -> new SandfishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SCORPION_CARP = ITEMS.register("scorpion_carp",
            () -> new ScorpionCarpItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> DORADO = ITEMS.register("dorado",
            () -> new DoradoItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GHOSTFISH = ITEMS.register("ghostfish",
            () -> new GhostfishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> STONEFISH = ITEMS.register("stonefish",
            () -> new StonefishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ICE_PIP = ITEMS.register("ice_pip",
            () -> new IcePipItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> LAVA_EEL = ITEMS.register("lava_eel",
            () -> new LavaEelItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> VOID_SALMON = ITEMS.register("void_salmon",
            () -> new VoidSalmonItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SLIMEJACK = ITEMS.register("slimejack",
            () -> new SlimejackItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> MIDNIGHT_SQUID = ITEMS.register("midnight_squid",
            () -> new MidnightSquidItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SPOOK_FISH = ITEMS.register("spook_fish",
            () -> new SpookFishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BLOBFISH = ITEMS.register("blobfish",
            () -> new BlobfishItem(new Item.Properties().stacksTo(999)));

    // 姜岛/河流补充鱼 (Ginger Island & Forest River)
    public static final DeferredItem<Item> GOBY = ITEMS.register("goby",
            () -> new com.stardew.craft.item.fish.river.GobyItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> LIONFISH = ITEMS.register("lionfish",
            () -> new com.stardew.craft.item.fish.special.LionfishItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> BLUE_DISCUS = ITEMS.register("blue_discus",
            () -> new com.stardew.craft.item.fish.special.BlueDiscusItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> STINGRAY = ITEMS.register("stingray",
            () -> new com.stardew.craft.item.fish.special.StingrayItem(new Item.Properties().stacksTo(999)));
    
    // 浼犺楸肩被
    public static final DeferredItem<Item> LEGEND = ITEMS.register("legend",
            () -> new LegendItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> GLACIERFISH = ITEMS.register("glacierfish",
            () -> new GlacierfishItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> CRIMSONFISH = ITEMS.register("crimsonfish",
            () -> new CrimsonfishItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> ANGLER = ITEMS.register("angler",
            () -> new AnglerItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> MUTANT_CARP = ITEMS.register("mutant_carp",
            () -> new MutantCarpItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> LEGEND_II = ITEMS.register("legend_ii",
            () -> new LegendIIItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> GLACIERFISH_JR = ITEMS.register("glacierfish_jr",
            () -> new GlacierfishJrItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> SON_OF_CRIMSONFISH = ITEMS.register("son_of_crimsonfish",
            () -> new SonOfCrimsonfishItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> MS_ANGLER = ITEMS.register("ms_angler",
            () -> new MsAnglerItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredItem<Item> RADIOACTIVE_CARP = ITEMS.register("radioactive_carp",
            () -> new RadioactiveCarpItem(new Item.Properties().stacksTo(1)));
    
    // 锜圭鐗╁搧
    public static final DeferredItem<Item> LOBSTER = ITEMS.register("lobster",
            () -> new LobsterItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CRAB = ITEMS.register("crab",
            () -> new CrabItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SHRIMP = ITEMS.register("shrimp",
            () -> new ShrimpItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CLAM = ITEMS.register("clam",
            () -> new ClamItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> MUSSEL = ITEMS.register("mussel",
            () -> new MusselItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> OYSTER = ITEMS.register("oyster",
            () -> new OysterItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> COCKLE = ITEMS.register("cockle",
            () -> new CockleItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> CRAYFISH = ITEMS.register("crayfish",
            () -> new CrayfishItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SNAIL = ITEMS.register("snail",
            () -> new SnailItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PERIWINKLE = ITEMS.register("periwinkle",
            () -> new PeriwinkleItem(new Item.Properties().stacksTo(999)));

    // 鐔忛奔
    public static final DeferredItem<Item> SMOKED_CHUB = ITEMS.register("smoked_chub",
            () -> new SmokedFishItem(ModItems.CHUB::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_BREAM = ITEMS.register("smoked_bream",
            () -> new SmokedFishItem(ModItems.BREAM::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SMALLMOUTH_BASS = ITEMS.register("smoked_smallmouth_bass",
            () -> new SmokedFishItem(ModItems.SMALLMOUTH_BASS::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_CATFISH = ITEMS.register("smoked_catfish",
            () -> new SmokedFishItem(ModItems.CATFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SHAD = ITEMS.register("smoked_shad",
            () -> new SmokedFishItem(ModItems.SHAD::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_TIGER_TROUT = ITEMS.register("smoked_tiger_trout",
            () -> new SmokedFishItem(ModItems.TIGER_TROUT::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SALMON = ITEMS.register("smoked_salmon",
            () -> new SmokedFishItem(ModItems.SALMON::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_WALLEYE = ITEMS.register("smoked_walleye",
            () -> new SmokedFishItem(ModItems.WALLEYE::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_PIKE = ITEMS.register("smoked_pike",
            () -> new SmokedFishItem(ModItems.PIKE::get, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> SMOKED_LARGEMOUTH_BASS = ITEMS.register("smoked_largemouth_bass",
            () -> new SmokedFishItem(ModItems.LARGEMOUTH_BASS::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_CARP = ITEMS.register("smoked_carp",
            () -> new SmokedFishItem(ModItems.CARP::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_BULLHEAD = ITEMS.register("smoked_bullhead",
            () -> new SmokedFishItem(ModItems.BULLHEAD::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_STURGEON = ITEMS.register("smoked_sturgeon",
            () -> new SmokedFishItem(ModItems.STURGEON::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_RAINBOW_TROUT = ITEMS.register("smoked_rainbow_trout",
            () -> new SmokedFishItem(ModItems.RAINBOW_TROUT::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_PERCH = ITEMS.register("smoked_perch",
            () -> new SmokedFishItem(ModItems.PERCH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SUNFISH = ITEMS.register("smoked_sunfish",
            () -> new SmokedFishItem(ModItems.SUNFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_LINGCOD = ITEMS.register("smoked_lingcod",
            () -> new SmokedFishItem(ModItems.LINGCOD::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_MIDNIGHT_CARP = ITEMS.register("smoked_midnight_carp",
            () -> new SmokedFishItem(ModItems.MIDNIGHT_CARP::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_WOODSKIP = ITEMS.register("smoked_woodskip",
            () -> new SmokedFishItem(ModItems.WOODSKIP::get, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> SMOKED_SARDINE = ITEMS.register("smoked_sardine",
            () -> new SmokedFishItem(ModItems.SARDINE::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_ANCHOVY = ITEMS.register("smoked_anchovy",
            () -> new SmokedFishItem(ModItems.ANCHOVY::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_HERRING = ITEMS.register("smoked_herring",
            () -> new SmokedFishItem(ModItems.HERRING::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_RED_SNAPPER = ITEMS.register("smoked_red_snapper",
            () -> new SmokedFishItem(ModItems.RED_SNAPPER::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_RED_MULLET = ITEMS.register("smoked_red_mullet",
            () -> new SmokedFishItem(ModItems.RED_MULLET::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SEA_CUCUMBER = ITEMS.register("smoked_sea_cucumber",
            () -> new SmokedFishItem(ModItems.SEA_CUCUMBER::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SUPER_CUCUMBER = ITEMS.register("smoked_super_cucumber",
            () -> new SmokedFishItem(ModItems.SUPER_CUCUMBER::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_FLOUNDER = ITEMS.register("smoked_flounder",
            () -> new SmokedFishItem(ModItems.FLOUNDER::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_HALIBUT = ITEMS.register("smoked_halibut",
            () -> new SmokedFishItem(ModItems.HALIBUT::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_EEL = ITEMS.register("smoked_eel",
            () -> new SmokedFishItem(ModItems.EEL::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_OCTOPUS = ITEMS.register("smoked_octopus",
            () -> new SmokedFishItem(ModItems.OCTOPUS::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SQUID = ITEMS.register("smoked_squid",
            () -> new SmokedFishItem(ModItems.SQUID::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_TUNA = ITEMS.register("smoked_tuna",
            () -> new SmokedFishItem(ModItems.TUNA::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_TILAPIA = ITEMS.register("smoked_tilapia",
            () -> new SmokedFishItem(ModItems.TILAPIA::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_PUFFERFISH = ITEMS.register("smoked_pufferfish",
            () -> new SmokedFishItem(ModItems.PUFFERFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_ALBACORE = ITEMS.register("smoked_albacore",
            () -> new SmokedFishItem(ModItems.ALBACORE::get, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> SMOKED_SANDFISH = ITEMS.register("smoked_sandfish",
            () -> new SmokedFishItem(ModItems.SANDFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SCORPION_CARP = ITEMS.register("smoked_scorpion_carp",
            () -> new SmokedFishItem(ModItems.SCORPION_CARP::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_DORADO = ITEMS.register("smoked_dorado",
            () -> new SmokedFishItem(ModItems.DORADO::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_GHOSTFISH = ITEMS.register("smoked_ghostfish",
            () -> new SmokedFishItem(ModItems.GHOSTFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_STONEFISH = ITEMS.register("smoked_stonefish",
            () -> new SmokedFishItem(ModItems.STONEFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_ICE_PIP = ITEMS.register("smoked_ice_pip",
            () -> new SmokedFishItem(ModItems.ICE_PIP::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_LAVA_EEL = ITEMS.register("smoked_lava_eel",
            () -> new SmokedFishItem(ModItems.LAVA_EEL::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_VOID_SALMON = ITEMS.register("smoked_void_salmon",
            () -> new SmokedFishItem(ModItems.VOID_SALMON::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SLIMEJACK = ITEMS.register("smoked_slimejack",
            () -> new SmokedFishItem(ModItems.SLIMEJACK::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_MIDNIGHT_SQUID = ITEMS.register("smoked_midnight_squid",
            () -> new SmokedFishItem(ModItems.MIDNIGHT_SQUID::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SPOOK_FISH = ITEMS.register("smoked_spook_fish",
            () -> new SmokedFishItem(ModItems.SPOOK_FISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_BLOBFISH = ITEMS.register("smoked_blobfish",
            () -> new SmokedFishItem(ModItems.BLOBFISH::get, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> SMOKED_LEGEND = ITEMS.register("smoked_legend",
            () -> new SmokedFishItem(ModItems.LEGEND::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_GLACIERFISH = ITEMS.register("smoked_glacierfish",
            () -> new SmokedFishItem(ModItems.GLACIERFISH::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_CRIMSONFISH = ITEMS.register("smoked_crimsonfish",
            () -> new SmokedFishItem(ModItems.CRIMSONFISH::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_ANGLER = ITEMS.register("smoked_angler",
            () -> new SmokedFishItem(ModItems.ANGLER::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_MUTANT_CARP = ITEMS.register("smoked_mutant_carp",
            () -> new SmokedFishItem(ModItems.MUTANT_CARP::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_LEGEND_II = ITEMS.register("smoked_legend_ii",
            () -> new SmokedFishItem(ModItems.LEGEND_II::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_GLACIERFISH_JR = ITEMS.register("smoked_glacierfish_jr",
            () -> new SmokedFishItem(ModItems.GLACIERFISH_JR::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_SON_OF_CRIMSONFISH = ITEMS.register("smoked_son_of_crimsonfish",
            () -> new SmokedFishItem(ModItems.SON_OF_CRIMSONFISH::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_MS_ANGLER = ITEMS.register("smoked_ms_angler",
            () -> new SmokedFishItem(ModItems.MS_ANGLER::get, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SMOKED_RADIOACTIVE_CARP = ITEMS.register("smoked_radioactive_carp",
            () -> new SmokedFishItem(ModItems.RADIOACTIVE_CARP::get, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> SMOKED_LOBSTER = ITEMS.register("smoked_lobster",
            () -> new SmokedFishItem(ModItems.LOBSTER::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_CRAB = ITEMS.register("smoked_crab",
            () -> new SmokedFishItem(ModItems.CRAB::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SHRIMP = ITEMS.register("smoked_shrimp",
            () -> new SmokedFishItem(ModItems.SHRIMP::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_CLAM = ITEMS.register("smoked_clam",
            () -> new SmokedFishItem(ModItems.CLAM::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_MUSSEL = ITEMS.register("smoked_mussel",
            () -> new SmokedFishItem(ModItems.MUSSEL::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_OYSTER = ITEMS.register("smoked_oyster",
            () -> new SmokedFishItem(ModItems.OYSTER::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_COCKLE = ITEMS.register("smoked_cockle",
            () -> new SmokedFishItem(ModItems.COCKLE::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_CRAYFISH = ITEMS.register("smoked_crayfish",
            () -> new SmokedFishItem(ModItems.CRAYFISH::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_SNAIL = ITEMS.register("smoked_snail",
            () -> new SmokedFishItem(ModItems.SNAIL::get, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SMOKED_PERIWINKLE = ITEMS.register("smoked_periwinkle",
            () -> new SmokedFishItem(ModItems.PERIWINKLE::get, new Item.Properties().stacksTo(999)));
    
    // 鏉傞」
    public static final DeferredItem<Item> SEAWEED = ITEMS.register("seaweed",
            () -> new SeaweedItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> GREEN_ALGAE = ITEMS.register("green_algae",
            () -> new GreenAlgaeItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> WHITE_ALGAE = ITEMS.register("white_algae",
            () -> new WhiteAlgaeItem(new Item.Properties().stacksTo(999)));

    // 鏉傞」 - 铇戣弴
    public static final DeferredItem<Item> COMMON_MUSHROOM = ITEMS.register("common_mushroom",
            () -> new StardewQualityItem("stardewcraft.type.misc", 40, 15, false, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> RED_MUSHROOM = ITEMS.register("red_mushroom",
            () -> new StardewQualityItem("stardewcraft.type.misc", 75, -20, false, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> PURPLE_MUSHROOM = ITEMS.register("purple_mushroom",
            () -> new StardewQualityItem("stardewcraft.type.misc", 250, 50, false, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> MOREL = ITEMS.register("morel",
            () -> new StardewQualityItem("stardewcraft.type.misc", 150, 8, false, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> CHANTERELLE = ITEMS.register("chanterelle",
            () -> new StardewQualityItem("stardewcraft.type.misc", 160, 30, false, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> MAGMA_CAP = ITEMS.register("magma_cap",
            () -> new StardewQualityItem("stardewcraft.type.misc", 400, 70, false, new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> SEA_JELLY = ITEMS.register("sea_jelly",
            () -> new SeaJellyItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> RIVER_JELLY = ITEMS.register("river_jelly",
            () -> new RiverJellyItem(new Item.Properties().stacksTo(999)));

    public static final DeferredItem<Item> CAVE_JELLY = ITEMS.register("cave_jelly",
            () -> new CaveJellyItem(new Item.Properties().stacksTo(999)));
    
    // 鍨冨溇鐗╁搧锛堥挀楸煎瀮鍦撅級
    public static final DeferredItem<Item> TRASH = ITEMS.register("trash",
            () -> new TrashItem(0, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> DRIFTWOOD = ITEMS.register("driftwood",
            () -> new TrashItem(0, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> SOGGY_NEWSPAPER = ITEMS.register("soggy_newspaper",
            () -> new TrashItem(0, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BROKEN_CD = ITEMS.register("broken_cd",
            () -> new TrashItem(0, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> BROKEN_GLASSES = ITEMS.register("broken_glasses",
            () -> new TrashItem(0, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> JOJA_COLA = ITEMS.register("joja_cola",
            () -> new JojaColaItem(new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ROTTEN_PLANT = ITEMS.register("rotten_plant",
            () -> new TrashItem(0, new Item.Properties().stacksTo(999)));
    
    // ========== 姝﹀櫒 ==========
    // 鍓戠被
    public static final DeferredItem<Item> RUSTY_SWORD = ITEMS.register("rusty_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("rusty_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STEEL_SMALLSWORD = ITEMS.register("steel_smallsword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("steel_smallsword", new Item.Properties().stacksTo(1)));

    // 鍖曢
    public static final DeferredItem<Item> CARVING_KNIFE = ITEMS.register("carving_knife",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("carving_knife", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> IRON_DIRK = ITEMS.register("iron_dirk",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("iron_dirk", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> WIND_SPIRE = ITEMS.register("wind_spire",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("wind_spire", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> ELF_BLADE = ITEMS.register("elf_blade",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("elf_blade", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> BURGLARS_SHANK = ITEMS.register("burglars_shank",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("burglars_shank", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> CRYSTAL_DAGGER = ITEMS.register("crystal_dagger",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("crystal_dagger", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> SHADOW_DAGGER = ITEMS.register("shadow_dagger",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("shadow_dagger", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> WICKED_KRIS = ITEMS.register("wicked_kris",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("wicked_kris", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GALAXY_DAGGER = ITEMS.register("galaxy_dagger",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("galaxy_dagger", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DWARF_DAGGER = ITEMS.register("dwarf_dagger",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("dwarf_dagger", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> IRIDIUM_NEEDLE = ITEMS.register("iridium_needle",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("iridium_needle", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> INFINITY_DAGGER = ITEMS.register("infinity_dagger",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("infinity_dagger", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DRAGONTOOTH_SHIV = ITEMS.register("dragontooth_shiv",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("dragontooth_shiv", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> BROKEN_TRIDENT = ITEMS.register("broken_trident",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("broken_trident", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ELLIOTTS_PENCIL = ITEMS.register("elliotts_pencil",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("elliotts_pencil", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ABBYS_PLANCHETTE = ITEMS.register("abbys_planchette",
            () -> new com.stardew.craft.item.weapon.StardewDaggerItem("abbys_planchette", new Item.Properties().stacksTo(1)));

    // 妫嶆
    public static final DeferredItem<Item> FEMUR = ITEMS.register("femur",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("femur", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ALEXS_BAT = ITEMS.register("alexs_bat",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("alexs_bat", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SAMS_OLD_GUITAR = ITEMS.register("sams_old_guitar",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("sams_old_guitar", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MARUS_WRENCH = ITEMS.register("marus_wrench",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("marus_wrench", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HARVEYS_MALLET = ITEMS.register("harveys_mallet",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("harveys_mallet", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PENNYS_FRYER = ITEMS.register("pennys_fryer",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("pennys_fryer", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SEBS_LOST_MACE = ITEMS.register("sebs_lost_mace",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("sebs_lost_mace", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GALAXY_HAMMER = ITEMS.register("galaxy_hammer",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("galaxy_hammer", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> INFINITY_GAVEL = ITEMS.register("infinity_gavel",
            () -> new com.stardew.craft.item.weapon.StardewClubItem("infinity_gavel", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> WOODEN_BLADE = ITEMS.register("wooden_blade",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("wooden_blade", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> PIRATE_SWORD = ITEMS.register("pirate_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("pirate_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> SILVER_SABER = ITEMS.register("silver_saber",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("silver_saber", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> CUTLASS = ITEMS.register("cutlass",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("cutlass", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> FOREST_SWORD = ITEMS.register("forest_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("forest_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> IRON_EDGE = ITEMS.register("iron_edge",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("iron_edge", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LEAHS_WHITTLER = ITEMS.register("leahs_whittler",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("leahs_whittler", new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HALEYS_IRON = ITEMS.register("haleys_iron",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("haleys_iron", new Item.Properties().stacksTo(1)));

    // Lv.4
    public static final DeferredItem<Item> MEOWMERE = ITEMS.register("meowmere",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("meowmere", new Item.Properties().stacksTo(1)));

    // Lv.5
    public static final DeferredItem<Item> BONE_SWORD = ITEMS.register("bone_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("bone_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> CLAYMORE = ITEMS.register("claymore",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("claymore", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> NEPTUNES_GLAIVE = ITEMS.register("neptunes_glaive",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("neptunes_glaive", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> TEMPLARS_BLADE = ITEMS.register("templars_blade",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("templars_blade", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> INSECT_HEAD = ITEMS.register("insect_head",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("insect_head", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> OBSIDIAN_EDGE = ITEMS.register("obsidian_edge",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("obsidian_edge", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> OSSIFIED_BLADE = ITEMS.register("ossified_blade",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("ossified_blade", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> HOLY_BLADE = ITEMS.register("holy_blade",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("holy_blade", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> TEMPERED_BROADSWORD = ITEMS.register("tempered_broadsword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("tempered_broadsword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> YETI_TOOTH = ITEMS.register("yeti_tooth",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("yeti_tooth", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STEEL_FALCHION = ITEMS.register("steel_falchion",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("steel_falchion", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DARK_SWORD = ITEMS.register("dark_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("dark_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> LAVA_KATANA = ITEMS.register("lava_katana",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("lava_katana", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DRAGONTOOTH_CUTLASS = ITEMS.register("dragontooth_cutlass",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("dragontooth_cutlass", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DWARF_SWORD = ITEMS.register("dwarf_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("dwarf_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GALAXY_SWORD = ITEMS.register("galaxy_sword",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("galaxy_sword", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> INFINITY_BLADE = ITEMS.register("infinity_blade",
            () -> new com.stardew.craft.item.weapon.StardewWeaponItem("infinity_blade", new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> WALL_PHOTO_FRAME = ITEMS.register("wall_photo_frame",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_PHOTO_FRAME.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WALL_BONE_DECOR = ITEMS.register("wall_bone_decor",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_BONE_DECOR.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> KITCHEN_COUNTER = ITEMS.register("kitchen_counter",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.KITCHEN_COUNTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> TABLEWARE_PINK = ITEMS.register("tableware_pink",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TABLEWARE_PINK.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> TABLEWARE_BLUE = ITEMS.register("tableware_blue",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TABLEWARE_BLUE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WALL_KITCHEN_CABINET = ITEMS.register("wall_kitchen_cabinet",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_KITCHEN_CABINET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

    // ──── Batch 3: 10 new furniture items ────
    public static final DeferredItem<Item> JOJA_VENDING_MACHINE = ITEMS.register("joja_vending_machine",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.JOJA_VENDING_MACHINE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WHITE_TEACUP = ITEMS.register("white_teacup",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WHITE_TEACUP.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> POOL_TABLE = ITEMS.register("pool_table",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.POOL_TABLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> GLOBE = ITEMS.register("globe",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.GLOBE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> TELESCOPE = ITEMS.register("telescope",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TELESCOPE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BEAR_FIGURINE = ITEMS.register("bear_figurine",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BEAR_FIGURINE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> FISH_SHOP_COUNTER = ITEMS.register("fish_shop_counter",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.FISH_SHOP_COUNTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HOSPITAL_COUNTER = ITEMS.register("hospital_counter",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.HOSPITAL_COUNTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HOSPITAL_POSTER_1 = ITEMS.register("hospital_poster_1",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.HOSPITAL_POSTER_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HOSPITAL_POSTER_2 = ITEMS.register("hospital_poster_2",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.HOSPITAL_POSTER_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HOSPITAL_POSTER_3 = ITEMS.register("hospital_poster_3",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.HOSPITAL_POSTER_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HOSPITAL_POSTER_4 = ITEMS.register("hospital_poster_4",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.HOSPITAL_POSTER_4.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> HOSPITAL_POSTER_5 = ITEMS.register("hospital_poster_5",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.HOSPITAL_POSTER_5.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> ELECTRIC_PIANO = ITEMS.register("electric_piano",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.ELECTRIC_PIANO.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WIZARD_CAULDRON = ITEMS.register("wizard_cauldron",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WIZARD_CAULDRON.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> GUITAR = ITEMS.register("guitar",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.GUITAR.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> MICROWAVE = ITEMS.register("microwave",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.MICROWAVE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> GRANDFATHER_CLOCK = ITEMS.register("grandfather_clock",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.GRANDFATHER_CLOCK.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> DRUM_SET = ITEMS.register("drum_set",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.DRUM_SET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WINE_CABINET_1 = ITEMS.register("wine_cabinet_1",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WINE_CABINET_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WINE_CABINET_2 = ITEMS.register("wine_cabinet_2",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WINE_CABINET_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WINE_CABINET_3 = ITEMS.register("wine_cabinet_3",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WINE_CABINET_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> ALEX_POSTER_1 = ITEMS.register("alex_poster_1",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.ALEX_POSTER_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> ALEX_POSTER_2 = ITEMS.register("alex_poster_2",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.ALEX_POSTER_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> ALEX_POSTER_3 = ITEMS.register("alex_poster_3",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.ALEX_POSTER_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LEAH_POSTER_1 = ITEMS.register("leah_poster_1",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LEAH_POSTER_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LEAH_POSTER_2 = ITEMS.register("leah_poster_2",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LEAH_POSTER_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LEAH_POSTER_3 = ITEMS.register("leah_poster_3",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LEAH_POSTER_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    
    public static final DeferredItem<Item> PERIODIC_TABLE = ITEMS.register("periodic_table",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.PERIODIC_TABLE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> MICROSCOPE = ITEMS.register("microscope",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.MICROSCOPE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BEAKER = ITEMS.register("beaker",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BEAKER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> TRAIN_PHOTO = ITEMS.register("train_photo",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TRAIN_PHOTO.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WALL_PHOTO_1 = ITEMS.register("wall_photo_1",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_PHOTO_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> PAPER_CHECKLIST = ITEMS.register("paper_checklist",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.PAPER_CHECKLIST.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SINE_WAVE_POSTER = ITEMS.register("sine_wave_poster",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.SINE_WAVE_POSTER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SCATTERED_PAPERS = ITEMS.register("scattered_papers",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.SCATTERED_PAPERS.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SEBASTIAN_POSTER_1 = ITEMS.register("sebastian_poster_1",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.SEBASTIAN_POSTER_1.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SEBASTIAN_POSTER_2 = ITEMS.register("sebastian_poster_2",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.SEBASTIAN_POSTER_2.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SEBASTIAN_POSTER_3 = ITEMS.register("sebastian_poster_3",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.SEBASTIAN_POSTER_3.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BOARD_GAME = ITEMS.register("board_game",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BOARD_GAME.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

    // ── 新家具批次 ────────────────────────────────────────────────────────────
    public static final DeferredItem<Item> WALL_ADVENTURER_MAP = ITEMS.register("wall_adventurer_map",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_ADVENTURER_MAP.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WALL_BUOY = ITEMS.register("wall_buoy",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_BUOY.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WALL_FISH_SIGN = ITEMS.register("wall_fish_sign",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_FISH_SIGN.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WALL_ISLAND_MAP = ITEMS.register("wall_island_map",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WALL_ISLAND_MAP.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BEAR_SKIN_RUG = ITEMS.register("bear_skin_rug",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BEAR_SKIN_RUG.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LEANING_SWORD = ITEMS.register("leaning_sword",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LEANING_SWORD.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LEAH_SCULPTURE = ITEMS.register("leah_sculpture",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LEAH_SCULPTURE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> EASEL = ITEMS.register("easel",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.EASEL.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BLUE_BEAR_PLUSHIE = ITEMS.register("blue_bear_plushie",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BLUE_BEAR_PLUSHIE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

    // ── 图腾柱方块物品 ────────────────────────────────────────────────────
    public static final DeferredItem<Item> TOTEM_POLE_FARM = ITEMS.register("totem_pole_farm",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TOTEM_POLE_FARM.get(), "stardewcraft.type.magic", -1, new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> TOTEM_POLE_MOUNTAIN = ITEMS.register("totem_pole_mountain",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TOTEM_POLE_MOUNTAIN.get(), "stardewcraft.type.magic", -1, new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> TOTEM_POLE_BEACH = ITEMS.register("totem_pole_beach",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TOTEM_POLE_BEACH.get(), "stardewcraft.type.magic", -1, new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> TOTEM_POLE_DESERT = ITEMS.register("totem_pole_desert",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.TOTEM_POLE_DESERT.get(), "stardewcraft.type.magic", -1, new Item.Properties().stacksTo(64)));

    // ── 传送图腾 ────────────────────────────────────────────────────────────
    public static final DeferredItem<Item> WARP_TOTEM_FARM = ITEMS.register("warp_totem_farm",
            () -> new com.stardew.craft.item.totem.TeleportTotemItem(com.stardew.craft.block.utility.totem.TotemType.FARM, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WARP_TOTEM_MOUNTAIN = ITEMS.register("warp_totem_mountain",
            () -> new com.stardew.craft.item.totem.TeleportTotemItem(com.stardew.craft.block.utility.totem.TotemType.MOUNTAIN, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WARP_TOTEM_BEACH = ITEMS.register("warp_totem_beach",
            () -> new com.stardew.craft.item.totem.TeleportTotemItem(com.stardew.craft.block.utility.totem.TotemType.BEACH, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> WARP_TOTEM_DESERT = ITEMS.register("warp_totem_desert",
            () -> new com.stardew.craft.item.totem.TeleportTotemItem(com.stardew.craft.block.utility.totem.TotemType.DESERT, new Item.Properties().stacksTo(999)));

    // ── 雨水图腾 ────────────────────────────────────────────────────────────
    public static final DeferredItem<Item> RAIN_TOTEM = ITEMS.register("rain_totem",
            () -> new com.stardew.craft.item.totem.RainTotemItem(new Item.Properties().stacksTo(999)));

    // 黄土
    public static final DeferredItem<Item> YELLOW_DIRT = blockItem("yellow_dirt", com.stardew.craft.block.ModBlocks.YELLOW_DIRT);
    // 远古斑点黄土
    public static final DeferredItem<Item> ARTIFACT_SPOT_DIRT = blockItem("artifact_spot_dirt", com.stardew.craft.block.ModBlocks.ARTIFACT_SPOT_DIRT);
        // 沙漠远古斑点（沙子变体）
    public static final DeferredItem<Item> DESERT_ARTIFACT_SPOT = blockItem("desert_artifact_spot", com.stardew.craft.block.ModBlocks.DESERT_ARTIFACT_SPOT);
                // 海滩远古斑点（沙子变体）
        public static final DeferredItem<Item> BEACH_ARTIFACT_SPOT = blockItem("beach_artifact_spot", com.stardew.craft.block.ModBlocks.BEACH_ARTIFACT_SPOT);

    // ── 怪物掉落物品 (Monster Loot) ────────────────────────────────────────
    public static final DeferredItem<Item> SLIME_ITEM = ITEMS.register("slime_item",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 5, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BAT_WING = ITEMS.register("bat_wing",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 15, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> SOLAR_ESSENCE = ITEMS.register("solar_essence",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 40, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> VOID_ESSENCE = ITEMS.register("void_essence",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 50, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BONE_FRAGMENT = ITEMS.register("bone_fragment",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 12, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BUG_MEAT = ITEMS.register("bug_meat",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 8, new Item.Properties().stacksTo(999)));

    // ── 海滩拾取物 (Beach Forage) — 已由 VanillaCategoryItemRegistrar 注册 ──

    // ── 资源材料 (Resource Materials) ─────────────────────────────────────
    public static final DeferredItem<Item> MOSS = ITEMS.register("moss",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 5, new Item.Properties().stacksTo(999)));

    // ── 炸弹系列 (Bombs) ──────────────────────────────────────────────────
    public static final DeferredItem<Item> CHERRY_BOMB = ITEMS.register("cherry_bomb",
            () -> new com.stardew.craft.item.bomb.StardewBombItem(com.stardew.craft.item.bomb.BombType.CHERRY_BOMB, 50, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BOMB_ITEM = ITEMS.register("bomb_item",
            () -> new com.stardew.craft.item.bomb.StardewBombItem(com.stardew.craft.item.bomb.BombType.BOMB, 50, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> MEGA_BOMB = ITEMS.register("mega_bomb",
            () -> new com.stardew.craft.item.bomb.StardewBombItem(com.stardew.craft.item.bomb.BombType.MEGA_BOMB, 50, new Item.Properties().stacksTo(999)));

    // ============ 饰品 (Trinkets, SDV 1.6 Mastery) ============
    public static final DeferredItem<Item> MAGIC_HAIR_DYE = ITEMS.register("magic_hair_dye",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.MAGIC_HAIR_DYE, new Item.Properties()));
    public static final DeferredItem<Item> FROG_EGG = ITEMS.register("frog_egg",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.FROG_EGG, new Item.Properties()));
    public static final DeferredItem<Item> MAGIC_QUIVER = ITEMS.register("magic_quiver",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.MAGIC_QUIVER, new Item.Properties()));
    public static final DeferredItem<Item> FAIRY_BOX = ITEMS.register("fairy_box",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.FAIRY_BOX, new Item.Properties()));
    public static final DeferredItem<Item> PARROT_EGG = ITEMS.register("parrot_egg",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.PARROT_EGG, new Item.Properties()));
    public static final DeferredItem<Item> ICE_ROD = ITEMS.register("ice_rod",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.ICE_ROD, new Item.Properties()));
    public static final DeferredItem<Item> IRIDIUM_SPUR = ITEMS.register("iridium_spur",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.IRIDIUM_SPUR, new Item.Properties()));
    public static final DeferredItem<Item> BASILISK_PAW = ITEMS.register("basilisk_paw",
            () -> new com.stardew.craft.item.trinket.StardewTrinketItem(com.stardew.craft.item.trinket.TrinketType.BASILISK_PAW, new Item.Properties()));

    public static final DeferredItem<Item> SMALL_GLOW_RING = ITEMS.register("small_glow_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.SMALL_GLOW_RING, 100, new Item.Properties()));
    public static final DeferredItem<Item> GLOW_RING = ITEMS.register("glow_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.GLOW_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> SMALL_MAGNET_RING = ITEMS.register("small_magnet_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.SMALL_MAGNET_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> MAGNET_RING = ITEMS.register("magnet_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.MAGNET_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> SLIME_CHARMER_RING = ITEMS.register("slime_charmer_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.SLIME_CHARMER_RING, 700, new Item.Properties()));
    public static final DeferredItem<Item> WARRIOR_RING = ITEMS.register("warrior_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.WARRIOR_RING, 1500, new Item.Properties()));
    public static final DeferredItem<Item> VAMPIRE_RING = ITEMS.register("vampire_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.VAMPIRE_RING, 1500, new Item.Properties()));
    public static final DeferredItem<Item> SAVAGE_RING = ITEMS.register("savage_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.SAVAGE_RING, 1500, new Item.Properties()));
    public static final DeferredItem<Item> RING_OF_YOBA = ITEMS.register("ring_of_yoba",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.RING_OF_YOBA, 1500, new Item.Properties()));
    public static final DeferredItem<Item> STURDY_RING = ITEMS.register("sturdy_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.STURDY_RING, 1500, new Item.Properties()));
    public static final DeferredItem<Item> BURGLARS_RING = ITEMS.register("burglars_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.BURGLARS_RING, 1500, new Item.Properties()));
    public static final DeferredItem<Item> IRIDIUM_BAND = ITEMS.register("iridium_band",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.IRIDIUM_BAND, 2000, new Item.Properties()));
    public static final DeferredItem<Item> AMETHYST_RING = ITEMS.register("amethyst_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.AMETHYST_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> TOPAZ_RING = ITEMS.register("topaz_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.TOPAZ_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> AQUAMARINE_RING = ITEMS.register("aquamarine_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.AQUAMARINE_RING, 400, new Item.Properties()));
    public static final DeferredItem<Item> JADE_RING = ITEMS.register("jade_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.JADE_RING, 400, new Item.Properties()));
    public static final DeferredItem<Item> EMERALD_RING = ITEMS.register("emerald_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.EMERALD_RING, 600, new Item.Properties()));
    public static final DeferredItem<Item> RUBY_RING = ITEMS.register("ruby_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.RUBY_RING, 600, new Item.Properties()));
    public static final DeferredItem<Item> CRABSHELL_RING = ITEMS.register("crabshell_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.CRABSHELL_RING, 2000, new Item.Properties()));
    public static final DeferredItem<Item> NAPALM_RING = ITEMS.register("napalm_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.NAPALM_RING, 2000, new Item.Properties()));
    public static final DeferredItem<Item> THORNS_RING = ITEMS.register("thorns_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.THORNS_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> LUCKY_RING = ITEMS.register("lucky_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.LUCKY_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> HOT_JAVA_RING = ITEMS.register("hot_java_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.HOT_JAVA_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> PROTECTION_RING = ITEMS.register("protection_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.PROTECTION_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> SOUL_SAPPER_RING = ITEMS.register("soul_sapper_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.SOUL_SAPPER_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> PHOENIX_RING = ITEMS.register("phoenix_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.PHOENIX_RING, 200, new Item.Properties()));
    public static final DeferredItem<Item> IMMUNITY_BAND = ITEMS.register("immunity_band",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.IMMUNITY_BAND, 500, new Item.Properties()));
    public static final DeferredItem<Item> GLOWSTONE_RING = ITEMS.register("glowstone_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.GLOWSTONE_RING, 200, new Item.Properties()));
    // 结婚戒指 — 纯装饰，暂无结婚系统
    public static final DeferredItem<Item> WEDDING_RING = ITEMS.register("wedding_ring",
            () -> new com.stardew.craft.item.equipment.StardewRingItem(com.stardew.craft.item.equipment.RingType.WEDDING_RING, 50, new Item.Properties()));

    public static final DeferredItem<Item> COMBINED_RING = ITEMS.register("combined_ring",
            () -> new com.stardew.craft.item.equipment.CombinedRingItem(new Item.Properties()));

    // ============ 装饰物品：帽子 (Cosmetic Hats) ============
    public static final DeferredItem<Item> BLUE_BONNET = registerHat("blue_bonnet", "6");
    public static final DeferredItem<Item> STRAW_HAT = registerHat("straw_hat", "4");
    public static final DeferredItem<Item> SAILORS_CAP = registerHat("sailors_cap", "17");
    public static final DeferredItem<Item> EARMUFFS = registerHat("earmuffs", "11");
    public static final DeferredItem<Item> TOP_HAT = registerHat("top_hat", "2");
    public static final DeferredItem<Item> BLUE_RIBBON = registerHat("blue_ribbon", "BlueRibbon");
    public static final DeferredItem<Item> BUTTERFLY_BOW = registerHat("butterfly_bow", "14");
    public static final DeferredItem<Item> DAISY = registerHat("daisy", "29");
    public static final DeferredItem<Item> DELICATE_BOW = registerHat("delicate_bow", "12");
    public static final DeferredItem<Item> EYE_PATCH = registerHat("eye_patch", "24");
    public static final DeferredItem<Item> SQUID_HAT = registerHat("squid_hat", "SquidHat");
    public static final DeferredItem<Item> SUNGLASSES = registerHat("sunglasses", "88");
    public static final DeferredItem<Item> TIARA = registerHat("tiara", "26");
    public static final DeferredItem<Item> GARBAGE_HAT = registerHat("garbage_hat", "66");
    public static final DeferredItem<Item> TROPICLIP = registerHat("tropiclip", "13");
    public static final DeferredItem<Item> WATERMELON_BAND = registerHat("watermelon_band", "30");
    public static final DeferredItem<Item> LUCKY_BOW = registerHat("lucky_bow", "21");
    public static final DeferredItem<Item> POLKA_BOW = registerHat("polka_bow", "22");
    public static final DeferredItem<Item> PINK_BOW = registerHat("pink_bow", "77");
    public static final DeferredItem<Item> BLUE_BOW = registerHat("blue_bow", "BlueBow");
    public static final DeferredItem<Item> ABIGAILS_BOW = registerHat("abigails_bow", "AbigailsBow");

    // ============ 靴子 (Boots) ============
    public static final DeferredItem<Item> SNEAKERS = ITEMS.register("sneakers",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.SNEAKERS, new Item.Properties()));
    public static final DeferredItem<Item> RUBBER_BOOTS = ITEMS.register("rubber_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.RUBBER_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> LEATHER_BOOTS = ITEMS.register("leather_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.LEATHER_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> WORK_BOOTS = ITEMS.register("work_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.WORK_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> COMBAT_BOOTS = ITEMS.register("combat_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.COMBAT_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> TUNDRA_BOOTS = ITEMS.register("tundra_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.TUNDRA_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> THERMAL_BOOTS = ITEMS.register("thermal_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.THERMAL_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> DARK_BOOTS = ITEMS.register("dark_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.DARK_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> FIREWALKER_BOOTS = ITEMS.register("firewalker_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.FIREWALKER_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> GENIE_SHOES = ITEMS.register("genie_shoes",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.GENIE_SHOES, new Item.Properties()));
    public static final DeferredItem<Item> SPACE_BOOTS = ITEMS.register("space_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.SPACE_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> COWBOY_BOOTS = ITEMS.register("cowboy_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.COWBOY_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> LEPRECHAUN_SHOES = ITEMS.register("leprechaun_shoes",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.LEPRECHAUN_SHOES, new Item.Properties()));
    public static final DeferredItem<Item> CINDERCLOWN_SHOES = ITEMS.register("cinderclown_shoes",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.CINDERCLOWN_SHOES, new Item.Properties()));
    public static final DeferredItem<Item> MERMAID_BOOTS = ITEMS.register("mermaid_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.MERMAID_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> DRAGONSCALE_BOOTS = ITEMS.register("dragonscale_boots",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.DRAGONSCALE_BOOTS, new Item.Properties()));
    public static final DeferredItem<Item> CRYSTAL_SHOES = ITEMS.register("crystal_shoes",
            () -> new com.stardew.craft.item.equipment.StardewBootsItem(com.stardew.craft.item.equipment.BootsType.CRYSTAL_SHOES, new Item.Properties()));

    // tmp_models 室内装饰家具
    public static final DeferredItem<Item> SAFE_BOX = ITEMS.register("safe_box",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.SAFE_BOX.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BROKEN_SAFE_BOX = ITEMS.register("broken_safe_box",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BROKEN_SAFE_BOX.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LOOM_MACHINE = ITEMS.register("loom_machine",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LOOM_MACHINE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BOILER_DECOR = ITEMS.register("boiler_decor",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BOILER_DECOR.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BROKEN_BOILER = ITEMS.register("broken_boiler",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BROKEN_BOILER.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> YARN_CABINET = ITEMS.register("yarn_cabinet",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.YARN_CABINET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> BROKEN_CHAIR = ITEMS.register("broken_chair",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.BROKEN_CHAIR.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> COAL_BASKET = ITEMS.register("coal_basket",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.COAL_BASKET.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

    // 家具目录 (SDV Furniture Catalogue, ID 1226)
    public static final DeferredItem<Item> FURNITURE_CATALOGUE = ITEMS.register("furniture_catalogue",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.FURNITURE_CATALOGUE.get(), "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

    // 公告栏 (SDV Bulletin Board)
    public static final DeferredItem<Item> BULLETIN_BOARD = ITEMS.register("bulletin_board",
            () -> new BlockItem(com.stardew.craft.block.ModBlocks.BULLETIN_BOARD.get(), new Item.Properties().stacksTo(1)));

    // 社区中心献祭卷轴 (SDV Junimo Note)
    public static final DeferredItem<Item> JUNIMO_NOTE = ITEMS.register("junimo_note",
            () -> new BlockItem(com.stardew.craft.block.ModBlocks.JUNIMO_NOTE.get(), new Item.Properties().stacksTo(1)));

    // ---- Junimo 专用持有物 (不进创造物品栏) ----
    /** SDV: Junimo holdingBundle 时头上的彩色 bundle 包裹 (Characters/Junimo.png 0,96 16×13) */
    public static final DeferredItem<Item> JUNIMO_BUNDLE = ITEMS.register("junimo_bundle",
            () -> new Item(new Item.Properties().stacksTo(1)));
    /** SDV: Junimo holdingStar 时头上的金色星星 (Characters/Junimo.png 0,109 16×19) */
    public static final DeferredItem<Item> JUNIMO_STAR = ITEMS.register("junimo_star",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // ── 加工台 (Workbenches) ──────────────────────────────────────────────
    public static final DeferredItem<Item> WOOD_WORKBENCH = ITEMS.register("wood_workbench",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.WOOD_WORKBENCH.get(),
                    "stardewcraft.type.utility", 500, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> STONE_WORKBENCH = ITEMS.register("stone_workbench",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.STONE_WORKBENCH.get(),
                    "stardewcraft.type.utility", 500, new Item.Properties().stacksTo(999)));

    // ── 祝尼魔温室符文 (Junimo Greenhouse Rune) ──────────────────────────
    public static final DeferredItem<Item> JUNIMO_GREENHOUSE_RUNE = ITEMS.register("junimo_greenhouse_rune",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.JUNIMO_GREENHOUSE_RUNE.get(),
                    "stardewcraft.type.magic", -1, new Item.Properties().stacksTo(1)));

    // ── 传送魔杖 (Warp Wand) ──────────────────────────
    public static final DeferredItem<Item> WARP_WAND = ITEMS.register("warp_wand",
            () -> new com.stardew.craft.item.tool.WarpWandItem(new Item.Properties().stacksTo(1)));

    // ── 装饰：农场常用 (Farm Common Decor) ──────────────────────────
    public static final DeferredItem<Item> STANDING_HOE = ITEMS.register("standing_hoe",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.STANDING_HOE.get(),
                    "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> EMPTY_TERRACOTTA_POT = ITEMS.register("empty_terracotta_pot",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.EMPTY_TERRACOTTA_POT.get(),
                    "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> RESERVOIR = ITEMS.register("reservoir",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.RESERVOIR.get(),
                    "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));
    public static final DeferredItem<Item> LONG_POTTED_PLANT = ITEMS.register("long_potted_plant",
            () -> new StardewBlockItem(com.stardew.craft.block.ModBlocks.LONG_POTTED_PLANT.get(),
                    "stardewcraft.type.furniture", -1, new Item.Properties().stacksTo(999)));

    private static DeferredItem<Item> registerHat(String itemId, String vanillaId) {
        return ITEMS.register(itemId,
                () -> new com.stardew.craft.item.cosmetic.StardewHatItem(vanillaId, -1,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                StardewCraft.MODID, "models/entity/hat/" + itemId + ".json"),
                        new Item.Properties().stacksTo(1)));
    }
}
