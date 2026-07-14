package com.stardew.craft.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.cooking.CookingPlacedFoodBlock;
import com.stardew.craft.block.mine.CalicoStatueBlock;
import com.stardew.craft.fluid.ModFluids;
import com.stardew.craft.tree.fruit.FruitTreeType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册管理器
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StardewCraft.MODID);

                @SuppressWarnings("null")
                private static Block.Properties stoneProps(MapColor color, SoundType sound, float hardness) {
                return Block.Properties.of()
                                .mapColor(color)
                                .sound(sound)
                                .strength(hardness, 6.0F);
        }

                @SuppressWarnings("null")
                private static DeferredBlock<SlabBlock> slab(String name, Block.Properties props) {
                return BLOCKS.register(name, () -> new SlabBlock(props));
        }

                @SuppressWarnings("null")
                private static DeferredBlock<StairBlock> stairs(String name, DeferredBlock<Block> base, Block.Properties props) {
                return BLOCKS.register(name, () -> new StairBlock(base.get().defaultBlockState(), props));
        }

                @SuppressWarnings("null")
                private static DeferredBlock<StairBlock> stairsFromAnyBlock(String name, DeferredBlock<? extends Block> base, Block.Properties props) {
                return BLOCKS.register(name, () -> new StairBlock(base.get().defaultBlockState(), props));
        }

                @SuppressWarnings("null")
                private static DeferredBlock<WallBlock> wall(String name, Block.Properties props) {
                return BLOCKS.register(name, () -> new WallBlock(props));
        }

                @SuppressWarnings("null")
                private static DeferredBlock<FenceBlock> fence(String name, Block.Properties props) {
                return BLOCKS.register(name, () -> new FenceBlock(props));
        }

                @SuppressWarnings("null")
                private static DeferredBlock<FenceGateBlock> fenceGate(String name, Block.Properties props) {
                return BLOCKS.register(name, () -> new FenceGateBlock(WoodType.OAK, props));
        }

        private static final String[] PLACEABLE_COOKING_FOOD_IDS = {
                "cheese_cauliflower",
                "ice_cream",
                "pumpkin_soup",
                "tortilla",
                "rice_pudding",
                "eggplant_parmesan",
                "maki_roll",
                "sashimi",
                "autumn_s_bounty",
                "red_plate",
                "blueberry_tart",
                "popsicle",
                "baked_fish",
                "carp_surprise",
                "crispy_bass",
                "fish_taco",
                "salmon_dinner",
                "dish_o_the_sea",
                "seafoam_pudding",
                "omelet",
                "fried_egg",
                "fried_mushroom",
                "hashbrowns",
                "pancakes",
                "strange_bun",
                "complete_breakfast",
                "farmer_s_lunch",
                "lucky_lunch",
                "salad",
                "fried_calamari",
                "fried_chicken_fries",
                "pink_cake",
                "pizza",
                "survival_burger",
                "algae_soup",
                "bean_hotpot",
                "glazed_yams",
                "cranberry_sauce",
                "triple_shot_espresso",
                "vegetable_medley",
                "stuffing",
                "super_meal",
                "miner_s_treat",
                "roots_platter",
                "parsnip_soup",
                "rhubarb_pie",
                "chocolate_cake",
                "spaghetti",
                "tom_kha_soup",
                "fried_eel",
                "pepper_poppers",
                "bread",
                "cookie",
                "spicy_eel",
                "trout_soup"
        };

        private static Map<String, DeferredBlock<CookingPlacedFoodBlock>> registerPlacedCookingFoods() {
                LinkedHashMap<String, DeferredBlock<CookingPlacedFoodBlock>> foods = new LinkedHashMap<>();
                for (String itemId : PLACEABLE_COOKING_FOOD_IDS) {
                        foods.put(itemId, BLOCKS.register("placed_food_" + itemId,
                                        () -> new CookingPlacedFoodBlock(itemId, Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)
                                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak())));
                }
                return Collections.unmodifiableMap(foods);
        }

        public static final Map<String, DeferredBlock<CookingPlacedFoodBlock>> PLACED_COOKING_FOODS = registerPlacedCookingFoods();

        public static DeferredBlock<CookingPlacedFoodBlock> getPlacedCookingFoodBlock(String itemId) {
                return PLACED_COOKING_FOODS.get(itemId);
        }

        // 自然/杂草
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_WEEDS = BLOCKS.register("wild_weeds",
                        () -> new com.stardew.craft.block.nature.WildWeedsBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()
                                        .randomTicks()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PASTURE_GRASS = BLOCKS.register("pasture_grass",
                        () -> new com.stardew.craft.block.nature.PastureGrassBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()
                                        .randomTicks()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BLUE_PASTURE_GRASS = BLOCKS.register("blue_pasture_grass",
                        () -> new com.stardew.craft.block.nature.PastureGrassBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()
                                        .randomTicks()));

        public static final DeferredBlock<Block> SMALL_BUSH = BLOCKS.register("small_bush",
                        () -> new com.stardew.craft.block.nature.SmallBushBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.2F)));

        public static final DeferredBlock<Block> BERRY_BUSH = BLOCKS.register("berry_bush",
                        () -> new com.stardew.craft.block.nature.BerryBushBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.3F)));

        // ---- 采集物方块 (Forage blocks with cross model, drop corresponding items) ----
        private static final int SPRING = 0;
        private static final int SUMMER = 1;
        private static final int FALL = 2;
        private static final int WINTER = 3;

        @SuppressWarnings("null")
        private static Block.Properties forageProps(boolean expiresOutOfSeason) {
                Block.Properties properties = Block.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                        .noCollission()
                        .noOcclusion()
                        .instabreak();
                return expiresOutOfSeason ? properties.randomTicks() : properties;
        }
        @SuppressWarnings("null")
        private static DeferredBlock<Block> forage(String name) {
                return forage(name, new int[0]);
        }
        @SuppressWarnings("null")
        private static DeferredBlock<Block> forage(String name, int... seasons) {
                return BLOCKS.register("forage_" + name,
                        () -> new com.stardew.craft.block.nature.ForageBlock(forageProps(seasons.length > 0))
                                .setDrop(() -> new net.minecraft.world.item.ItemStack(
                                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("stardewcraft", name))))
                                .setAllowedSeasons(seasons));
        }

        // Spring forage
        public static final DeferredBlock<Block> FORAGE_WILD_HORSERADISH = forage("wild_horseradish", SPRING);
        public static final DeferredBlock<Block> FORAGE_DAFFODIL          = forage("daffodil", SPRING);
        public static final DeferredBlock<Block> FORAGE_LEEK              = forage("leek", SPRING);
        public static final DeferredBlock<Block> FORAGE_DANDELION         = forage("dandelion", SPRING);
        public static final DeferredBlock<Block> FORAGE_SPRING_ONION      = forage("spring_onion", SPRING);
        // Summer forage
        public static final DeferredBlock<Block> FORAGE_SPICE_BERRY       = forage("spice_berry", SUMMER);
        public static final DeferredBlock<Block> FORAGE_SWEET_PEA         = forage("sweet_pea", SUMMER);
        public static final DeferredBlock<Block> FORAGE_GRAPE             = forage("grape", SUMMER);
        public static final DeferredBlock<Block> FORAGE_FIDDLEHEAD_FERN   = forage("fiddlehead_fern", SUMMER);
        // Fall forage
        public static final DeferredBlock<Block> FORAGE_WILD_PLUM         = forage("wild_plum", FALL);
        public static final DeferredBlock<Block> FORAGE_HAZELNUT          = forage("hazelnut", FALL);
        public static final DeferredBlock<Block> FORAGE_BLACKBERRY        = forage("blackberry", FALL);
        // Winter forage
        public static final DeferredBlock<Block> FORAGE_WINTER_ROOT       = forage("winter_root", WINTER);
        public static final DeferredBlock<Block> FORAGE_CRYSTAL_FRUIT     = forage("crystal_fruit", WINTER);
        public static final DeferredBlock<Block> FORAGE_CROCUS            = forage("crocus", WINTER);
        public static final DeferredBlock<Block> FORAGE_HOLLY             = forage("holly", WINTER);
        // Cave / universal
        public static final DeferredBlock<Block> FORAGE_CAVE_CARROT       = forage("cave_carrot");
        // Beach
        public static final DeferredBlock<Block> FORAGE_NAUTILUS_SHELL    = forage("nautilus_shell", WINTER);
        public static final DeferredBlock<Block> FORAGE_CORAL             = forage("coral");
        public static final DeferredBlock<Block> FORAGE_RAINBOW_SHELL     = forage("rainbow_shell", SUMMER);
        public static final DeferredBlock<Block> FORAGE_SEA_URCHIN        = forage("sea_urchin");
        // Desert / tropical
        public static final DeferredBlock<Block> FORAGE_COCONUT           = forage("coconut");
        public static final DeferredBlock<Block> FORAGE_CACTUS_FRUIT      = forage("cactus_fruit", SUMMER, FALL);
        // Mushrooms (cave)
        public static final DeferredBlock<Block> FORAGE_COMMON_MUSHROOM   = forage("common_mushroom", SPRING, SUMMER, FALL);
        public static final DeferredBlock<Block> FORAGE_RED_MUSHROOM      = forage("red_mushroom", SUMMER, FALL);
        public static final DeferredBlock<Block> FORAGE_PURPLE_MUSHROOM   = forage("purple_mushroom");
        public static final DeferredBlock<Block> FORAGE_MOREL             = forage("morel", SPRING);
        public static final DeferredBlock<Block> FORAGE_CHANTERELLE       = forage("chanterelle", FALL);
        public static final DeferredBlock<Block> FORAGE_MAGMA_CAP         = forage("magma_cap");
        // Fruit Cave (SDV FarmCave fruit bats spawns)
        public static final DeferredBlock<Block> FORAGE_SALMONBERRY       = forage("salmonberry");
        public static final DeferredBlock<Block> FORAGE_APPLE             = forage("apple");
        public static final DeferredBlock<Block> FORAGE_APRICOT           = forage("apricot");
        public static final DeferredBlock<Block> FORAGE_ORANGE            = forage("orange");
        public static final DeferredBlock<Block> FORAGE_PEACH             = forage("peach");
        public static final DeferredBlock<Block> FORAGE_POMEGRANATE       = forage("pomegranate");
        public static final DeferredBlock<Block> FORAGE_MANGO             = forage("mango");

        // ---- 农场洞穴：蘑菇培养盆 ----
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MUSHROOM_BOX = BLOCKS.register("mushroom_box",
                        () -> new com.stardew.craft.block.farm.MushroomBoxBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()
                                        .noOcclusion()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ANIMAL_PRODUCE_SPOT = BLOCKS.register("animal_produce_spot",
                        () -> new com.stardew.craft.block.animal.AnimalProduceSpotBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.NONE)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EGG_FESTIVAL_EGG = BLOCKS.register("egg_festival_egg",
                        () -> new com.stardew.craft.block.festival.EggFestivalEggBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()
                                        .noLootTable()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LARGE_STUMP = BLOCKS.register("large_stump",
                        () -> new com.stardew.craft.block.decor.ResourceClumpBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(5.0F, 6.0F)
                                        .noLootTable(),
                                        "stardewcraft:decor/common/large_stump",
                                        com.stardew.craft.block.decor.ResourceClumpBlock.RequiredTool.AXE,
                                        1,
                                        10.0F,
                                        () -> com.stardew.craft.item.ModItems.WOOD_HARD.get(),
                                        2,
                                        () -> com.stardew.craft.item.ModItems.MAHOGANY_SEED.get(),
                                        1,
                                        0.1D,
                                        com.stardew.craft.player.SkillType.FORAGING,
                                        25,
                                        -8.0D,
                                        -5.0D,
                                        27.0D,
                                        23.0D,
                                        23.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOLLOW_LOG = BLOCKS.register("hollow_log",
                        () -> new com.stardew.craft.block.decor.SecretWoodsEntranceLogBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(5.0F, 6.0F)
                                        .noLootTable(),
                                        "stardewcraft:decor/common/hollow_log",
                                        com.stardew.craft.block.decor.ResourceClumpBlock.RequiredTool.AXE,
                                        2,
                                        20.0F,
                                        () -> com.stardew.craft.item.ModItems.WOOD_HARD.get(),
                                        8,
                                        () -> com.stardew.craft.item.ModItems.MAHOGANY_SEED.get(),
                                        1,
                                        0.1D,
                                        com.stardew.craft.player.SkillType.FORAGING,
                                        25,
                                        -9.0D,
                                        -10.0D,
                                        20.0D,
                                        15.0D,
                                        30.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LARGE_BOULDER = BLOCKS.register("large_boulder",
                        () -> new com.stardew.craft.block.decor.ResourceClumpBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(6.0F, 6.0F)
                                        .noLootTable(),
                                        "stardewcraft:decor/common/large_boulder",
                                        com.stardew.craft.block.decor.ResourceClumpBlock.RequiredTool.PICKAXE,
                                        2,
                                        10.0F,
                                        () -> com.stardew.craft.item.ModItems.STONE.get(),
                                        15,
                                        null,
                                        0,
                                        -11.0D,
                                        -11.0D,
                                        29.0D,
                                        25.0D,
                                        32.0D));

        // 矿井
        // 说明：生存/冒险不可破坏（硬度 -1），创造模式可正常破坏。
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_BARRIER = BLOCKS.register("mine_barrier",
                        () -> new Block(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .strength(-1.0F, 3600000.0F)));

        // 矿井：主石头
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_SHALE = BLOCKS.register("earth_shale",
                        () -> new Block(stoneProps(MapColor.STONE, SoundType.STONE, 5.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FROST_GNEISS = BLOCKS.register("frost_gneiss",
                        () -> new Block(stoneProps(MapColor.STONE, SoundType.STONE, 7.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAVA_BASALT = BLOCKS.register("lava_basalt",
                        () -> new Block(stoneProps(MapColor.STONE, SoundType.STONE, 10.0F)));

        // 矿井：Dark 变体（随机修饰器用）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DARK_EARTH_SHALE = BLOCKS.register("dark_earth_shale",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 6.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DARK_FROST_GNEISS = BLOCKS.register("dark_frost_gneiss",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 8.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DARK_LAVA_BASALT = BLOCKS.register("dark_lava_basalt",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 12.0F)));

        // ========== 主石头变体：台阶、楼梯、墙 ==========
        
        // Earth Shale 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> EARTH_SHALE_SLAB = slab("earth_shale_slab",
                        stoneProps(MapColor.STONE, SoundType.STONE, 5.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> EARTH_SHALE_STAIRS = stairs("earth_shale_stairs", EARTH_SHALE,
                        stoneProps(MapColor.STONE, SoundType.STONE, 5.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> EARTH_SHALE_WALL = wall("earth_shale_wall",
                        stoneProps(MapColor.STONE, SoundType.STONE, 5.0F));

        // Frost Gneiss 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> FROST_GNEISS_SLAB = slab("frost_gneiss_slab",
                        stoneProps(MapColor.STONE, SoundType.STONE, 7.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> FROST_GNEISS_STAIRS = stairs("frost_gneiss_stairs", FROST_GNEISS,
                        stoneProps(MapColor.STONE, SoundType.STONE, 7.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> FROST_GNEISS_WALL = wall("frost_gneiss_wall",
                        stoneProps(MapColor.STONE, SoundType.STONE, 7.0F));

        // Lava Basalt 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> LAVA_BASALT_SLAB = slab("lava_basalt_slab",
                        stoneProps(MapColor.STONE, SoundType.STONE, 10.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> LAVA_BASALT_STAIRS = stairs("lava_basalt_stairs", LAVA_BASALT,
                        stoneProps(MapColor.STONE, SoundType.STONE, 10.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> LAVA_BASALT_WALL = wall("lava_basalt_wall",
                        stoneProps(MapColor.STONE, SoundType.STONE, 10.0F));

        // Dark Earth Shale 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> DARK_EARTH_SHALE_SLAB = slab("dark_earth_shale_slab",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 6.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> DARK_EARTH_SHALE_STAIRS = stairs("dark_earth_shale_stairs", DARK_EARTH_SHALE,
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 6.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> DARK_EARTH_SHALE_WALL = wall("dark_earth_shale_wall",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 6.0F));

        // Dark Frost Gneiss 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> DARK_FROST_GNEISS_SLAB = slab("dark_frost_gneiss_slab",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 8.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> DARK_FROST_GNEISS_STAIRS = stairs("dark_frost_gneiss_stairs", DARK_FROST_GNEISS,
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 8.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> DARK_FROST_GNEISS_WALL = wall("dark_frost_gneiss_wall",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 8.0F));

        // Dark Lava Basalt 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> DARK_LAVA_BASALT_SLAB = slab("dark_lava_basalt_slab",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 12.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> DARK_LAVA_BASALT_STAIRS = stairs("dark_lava_basalt_stairs", DARK_LAVA_BASALT,
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 12.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> DARK_LAVA_BASALT_WALL = wall("dark_lava_basalt_wall",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 12.0F));

        // 矿井：装饰石材族
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BANDED_MARBLE = BLOCKS.register("banded_marble",
                        () -> new Block(stoneProps(MapColor.QUARTZ, SoundType.STONE, 4.5F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIMESTONE = BLOCKS.register("limestone",
                        () -> new Block(stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 4.5F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MOSSY_SANDSTONE = BLOCKS.register("mossy_sandstone",
                        () -> new Block(stoneProps(MapColor.SAND, SoundType.STONE, 4.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CRACKED_SLATE = BLOCKS.register("cracked_slate",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 5.5F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCORIA = BLOCKS.register("scoria",
                        () -> new Block(stoneProps(MapColor.COLOR_BLACK, SoundType.BASALT, 5.5F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SALT_ROCK = BLOCKS.register("salt_rock",
                        () -> new Block(stoneProps(MapColor.SNOW, SoundType.STONE, 4.0F)));

        // ========== 装饰石材变体：台阶、楼梯、墙 ==========
        
        // Banded Marble 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> BANDED_MARBLE_SLAB = slab("banded_marble_slab",
                        stoneProps(MapColor.QUARTZ, SoundType.STONE, 4.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> BANDED_MARBLE_STAIRS = stairs("banded_marble_stairs", BANDED_MARBLE,
                        stoneProps(MapColor.QUARTZ, SoundType.STONE, 4.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> BANDED_MARBLE_WALL = wall("banded_marble_wall",
                        stoneProps(MapColor.QUARTZ, SoundType.STONE, 4.5F));

        // Limestone 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> LIMESTONE_SLAB = slab("limestone_slab",
                        stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 4.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> LIMESTONE_STAIRS = stairs("limestone_stairs", LIMESTONE,
                        stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 4.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> LIMESTONE_WALL = wall("limestone_wall",
                        stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 4.5F));

        // Mossy Sandstone 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> MOSSY_SANDSTONE_SLAB = slab("mossy_sandstone_slab",
                        stoneProps(MapColor.SAND, SoundType.STONE, 4.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> MOSSY_SANDSTONE_STAIRS = stairs("mossy_sandstone_stairs", MOSSY_SANDSTONE,
                        stoneProps(MapColor.SAND, SoundType.STONE, 4.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> MOSSY_SANDSTONE_WALL = wall("mossy_sandstone_wall",
                        stoneProps(MapColor.SAND, SoundType.STONE, 4.0F));

        // Cracked Slate 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> CRACKED_SLATE_SLAB = slab("cracked_slate_slab",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 5.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> CRACKED_SLATE_STAIRS = stairs("cracked_slate_stairs", CRACKED_SLATE,
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 5.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> CRACKED_SLATE_WALL = wall("cracked_slate_wall",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 5.5F));

        // Scoria 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> SCORIA_SLAB = slab("scoria_slab",
                        stoneProps(MapColor.COLOR_BLACK, SoundType.BASALT, 5.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> SCORIA_STAIRS = stairs("scoria_stairs", SCORIA,
                        stoneProps(MapColor.COLOR_BLACK, SoundType.BASALT, 5.5F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> SCORIA_WALL = wall("scoria_wall",
                        stoneProps(MapColor.COLOR_BLACK, SoundType.BASALT, 5.5F));

        // Salt Rock 变体
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> SALT_ROCK_SLAB = slab("salt_rock_slab",
                        stoneProps(MapColor.SNOW, SoundType.STONE, 4.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> SALT_ROCK_STAIRS = stairs("salt_rock_stairs", SALT_ROCK,
                        stoneProps(MapColor.SNOW, SoundType.STONE, 4.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> SALT_ROCK_WALL = wall("salt_rock_wall",
                        stoneProps(MapColor.SNOW, SoundType.STONE, 4.0F));

        // 矿井：电梯（模型由资源包提供，有朝向）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ELEVATOR = BLOCKS.register("elevator",
                        () -> new com.stardew.craft.block.mine.ElevatorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .requiresCorrectToolForDrops()
                                        .strength(3.0F, 6.0F)
                                        .noOcclusion()));

        // 矿井：下楼梯子（挖石头出现的传送点）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_LADDER = BLOCKS.register("mine_ladder",
                        () -> new com.stardew.craft.block.mine.MineLadderBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.LADDER)
                                        .strength(-1.0F, 3600000.0F)
                                        .lightLevel(state -> 15)
                                        .noOcclusion()));

        // 矿井：木桶（仿 SDV BreakableContainer，可挖掘/武器打碎，掉落战利品）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_BARREL = BLOCKS.register("mine_barrel",
                        () -> new com.stardew.craft.block.mine.MineBarrelBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(0.6F, 2.0F)
                                        .noOcclusion()));

        // 矿井：宝箱（per-player 独立库存，不可破坏）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_CHEST = BLOCKS.register("mine_chest",
                        () -> new com.stardew.craft.block.mine.MineChestBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(-1.0F, 3600000.0F) // 不可破坏
                                        .noOcclusion()));

        // 矿井：出口（金色梯子，中心安全区，打开GUI）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_EXIT = BLOCKS.register("mine_exit",
                        () -> new com.stardew.craft.block.mine.MineExitBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.GOLD)
                                        .sound(net.minecraft.world.level.block.SoundType.LADDER)
                                        .strength(-1.0F, 3600000.0F) // 不可破坏
                                        .lightLevel(state -> 15)
                                        .noOcclusion()));

        // 矿井：矿石方块（主题三套外观）
        @SuppressWarnings("null")
        private static Block.Properties oreProps(float hardness) {
                return Block.Properties.of()
                                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                .sound(net.minecraft.world.level.block.SoundType.STONE)
                                .requiresCorrectToolForDrops()
                                .strength(hardness, 6.0F);
        }

        // 矿井：直接采集矿物方块（洞窟表面）
        @SuppressWarnings("null")
        private static Block.Properties mineralNodeProps(float hardness) {
                return Block.Properties.of()
                                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                .sound(net.minecraft.world.level.block.SoundType.STONE)
                                .requiresCorrectToolForDrops()
                                .noOcclusion()
                                .strength(hardness, 6.0F);
        }

        public static final DeferredBlock<Block> QUARTZ = BLOCKS.register("quartz",
                        () -> new com.stardew.craft.block.mine.MineralNodeBlock(mineralNodeProps(3.0F)));   // Earth段，tier 0
        public static final DeferredBlock<Block> EARTH_CRYSTAL = BLOCKS.register("earth_crystal",
                        () -> new com.stardew.craft.block.mine.MineralNodeBlock(mineralNodeProps(3.0F)));   // Earth段，tier 0
        public static final DeferredBlock<Block> FROZEN_TEAR = BLOCKS.register("frozen_tear",
                        () -> new com.stardew.craft.block.mine.MineralNodeBlock(mineralNodeProps(4.0F)));   // Frost段，tier 1
        public static final DeferredBlock<Block> FIRE_QUARTZ = BLOCKS.register("fire_quartz",
                        () -> new com.stardew.craft.block.mine.MineralNodeBlock(mineralNodeProps(5.0F)));   // Lava段，tier 2

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_COPPER_ORE = BLOCKS.register("earth_copper_ore", () -> new Block(oreProps(4.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FROST_COPPER_ORE = BLOCKS.register("frost_copper_ore", () -> new Block(oreProps(4.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAVA_COPPER_ORE = BLOCKS.register("lava_copper_ore", () -> new Block(oreProps(4.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_IRON_ORE = BLOCKS.register("earth_iron_ore", () -> new Block(oreProps(5.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FROST_IRON_ORE = BLOCKS.register("frost_iron_ore", () -> new Block(oreProps(5.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAVA_IRON_ORE = BLOCKS.register("lava_iron_ore", () -> new Block(oreProps(5.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_GOLD_ORE = BLOCKS.register("earth_gold_ore", () -> new Block(oreProps(7.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FROST_GOLD_ORE = BLOCKS.register("frost_gold_ore", () -> new Block(oreProps(7.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAVA_GOLD_ORE = BLOCKS.register("lava_gold_ore", () -> new Block(oreProps(7.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_IRIDIUM_ORE = BLOCKS.register("earth_iridium_ore", () -> new Block(oreProps(10.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FROST_IRIDIUM_ORE = BLOCKS.register("frost_iridium_ore", () -> new Block(oreProps(10.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAVA_IRIDIUM_ORE = BLOCKS.register("lava_iridium_ore", () -> new Block(oreProps(10.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_COAL_ORE = BLOCKS.register("earth_coal_ore", () -> new Block(oreProps(3.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FROST_COAL_ORE = BLOCKS.register("frost_coal_ore", () -> new Block(oreProps(3.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAVA_COAL_ORE = BLOCKS.register("lava_coal_ore", () -> new Block(oreProps(3.0F)));

        // ========== 骷髅矿洞（Skull Cavern）方块 ==========

        // 骷髅矿：主石头
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_BEDROCK = BLOCKS.register("desert_bedrock",
                        () -> new Block(stoneProps(MapColor.SAND, SoundType.STONE, 12.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DARK_DESERT_BEDROCK = BLOCKS.register("dark_desert_bedrock",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 14.0F)));

        // 骷髅矿：装饰石
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SULFUR_ROCK = BLOCKS.register("sulfur_rock",
                        () -> new Block(stoneProps(MapColor.COLOR_YELLOW, SoundType.STONE, 6.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WEATHERED_STONE = BLOCKS.register("weathered_stone",
                        () -> new Block(stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<Block> CALICO_EGG_STONE = BLOCKS.register("calico_egg_stone",
                        () -> new Block(stoneProps(MapColor.COLOR_ORANGE, SoundType.STONE, 5.0F)));
        public static final DeferredBlock<Block> CALICO_STATUE = BLOCKS.register("calico_statue",
                        () -> new CalicoStatueBlock(stoneProps(MapColor.COLOR_PURPLE, SoundType.STONE, 5.0F)));

        // 骷髅矿主石头/装饰石的建材变体（slab/stairs/wall）
        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> DESERT_BEDROCK_SLAB = slab("desert_bedrock_slab",
                        stoneProps(MapColor.SAND, SoundType.STONE, 12.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> DESERT_BEDROCK_STAIRS = stairs("desert_bedrock_stairs", DESERT_BEDROCK,
                        stoneProps(MapColor.SAND, SoundType.STONE, 12.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> DESERT_BEDROCK_WALL = wall("desert_bedrock_wall",
                        stoneProps(MapColor.SAND, SoundType.STONE, 12.0F));

        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> DARK_DESERT_BEDROCK_SLAB = slab("dark_desert_bedrock_slab",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 14.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> DARK_DESERT_BEDROCK_STAIRS = stairs("dark_desert_bedrock_stairs", DARK_DESERT_BEDROCK,
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 14.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> DARK_DESERT_BEDROCK_WALL = wall("dark_desert_bedrock_wall",
                        stoneProps(MapColor.DEEPSLATE, SoundType.DEEPSLATE, 14.0F));

        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> SULFUR_ROCK_SLAB = slab("sulfur_rock_slab",
                        stoneProps(MapColor.COLOR_YELLOW, SoundType.STONE, 6.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> SULFUR_ROCK_STAIRS = stairs("sulfur_rock_stairs", SULFUR_ROCK,
                        stoneProps(MapColor.COLOR_YELLOW, SoundType.STONE, 6.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> SULFUR_ROCK_WALL = wall("sulfur_rock_wall",
                        stoneProps(MapColor.COLOR_YELLOW, SoundType.STONE, 6.0F));

        @SuppressWarnings("null")
        public static final DeferredBlock<SlabBlock> WEATHERED_STONE_SLAB = slab("weathered_stone_slab",
                        stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 5.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<StairBlock> WEATHERED_STONE_STAIRS = stairs("weathered_stone_stairs", WEATHERED_STONE,
                        stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 5.0F));
        @SuppressWarnings("null")
        public static final DeferredBlock<WallBlock> WEATHERED_STONE_WALL = wall("weathered_stone_wall",
                        stoneProps(MapColor.TERRACOTTA_WHITE, SoundType.STONE, 5.0F));

        // 骷髅矿：矿石（desert 主题外观）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_COPPER_ORE = BLOCKS.register("desert_copper_ore", () -> new Block(oreProps(4.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_IRON_ORE = BLOCKS.register("desert_iron_ore", () -> new Block(oreProps(5.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_GOLD_ORE = BLOCKS.register("desert_gold_ore", () -> new Block(oreProps(7.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_IRIDIUM_ORE = BLOCKS.register("desert_iridium_ore", () -> new Block(oreProps(10.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_COAL_ORE = BLOCKS.register("desert_coal_ore", () -> new Block(oreProps(3.0F)));

        // 骷髅矿：功能方块
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> QUICKSAND = BLOCKS.register("quicksand",
                        () -> new com.stardew.craft.block.mine.QuicksandBlock(Block.Properties.of()
                                        .mapColor(MapColor.SAND)
                                        .sound(SoundType.SAND)
                                        .strength(0.5F, 0.5F)
                                        .noOcclusion()
                                        .isViewBlocking((s, g, p) -> true)
                                        .isSuffocating((s, g, p) -> false)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TOXIC_SPORE_BLOCK = BLOCKS.register("toxic_spore_block",
                        () -> new com.stardew.craft.block.mine.ToxicSporeBlock(Block.Properties.of()
                                        .mapColor(MapColor.COLOR_GREEN)
                                        .sound(SoundType.MOSS)
                                        .strength(1.0F, 1.0F)
                                        .lightLevel(state -> 3)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> UNSTABLE_ROCK = BLOCKS.register("unstable_rock",
                        () -> new com.stardew.craft.block.mine.UnstableRockBlock(Block.Properties.of()
                                        .mapColor(MapColor.STONE)
                                        .sound(SoundType.STONE)
                                        .strength(3.0F, 3.0F)));

        // 矿井：矿物矿石节点（宝石矿，晶洞产物不做）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> AMETHYST_ORE = BLOCKS.register("amethyst_ore", () -> new Block(oreProps(4.0F)));   // tier 0
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> AQUAMARINE_ORE = BLOCKS.register("aquamarine_ore", () -> new Block(oreProps(5.0F))); // tier 1
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DIAMOND_ORE = BLOCKS.register("diamond_ore", () -> new Block(oreProps(7.0F)));     // tier 2
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EMERALD_ORE = BLOCKS.register("emerald_ore", () -> new Block(oreProps(6.0F)));     // tier 2
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JADE_ORE = BLOCKS.register("jade_ore", () -> new Block(oreProps(5.0F)));           // tier 1
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> RUBY_ORE = BLOCKS.register("ruby_ore", () -> new Block(oreProps(6.0F)));           // tier 2
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TOPAZ_ORE = BLOCKS.register("topaz_ore", () -> new Block(oreProps(4.0F)));         // tier 0

    // 作物方块
    public static final DeferredBlock<Block> AMARANTH_CROP = BLOCKS.register("amaranth_crop",
            () -> new com.stardew.craft.block.crop.AmaranthCropBlock());

    public static final DeferredBlock<Block> ANCIENT_FRUIT_CROP = BLOCKS.register("ancient_fruit_crop",
            () -> new com.stardew.craft.block.crop.AncientFruitCropBlock());

    public static final DeferredBlock<Block> ARTICHOKE_CROP = BLOCKS.register("artichoke_crop",
            () -> new com.stardew.craft.block.crop.ArtichokeCropBlock());

    public static final DeferredBlock<Block> BEET_CROP = BLOCKS.register("beet_crop",
            () -> new com.stardew.craft.block.crop.BeetCropBlock());

    public static final DeferredBlock<Block> BLUE_JAZZ_CROP = BLOCKS.register("blue_jazz_crop",
            () -> new com.stardew.craft.block.crop.BlueJazzCropBlock());

    public static final DeferredBlock<Block> BLUEBERRY_CROP = BLOCKS.register("blueberry_crop",
            () -> new com.stardew.craft.block.crop.BlueberryCropBlock());

    public static final DeferredBlock<Block> BOK_CHOY_CROP = BLOCKS.register("bok_choy_crop",
            () -> new com.stardew.craft.block.crop.BokChoyCropBlock());

    public static final DeferredBlock<Block> BROCCOLI_CROP = BLOCKS.register("broccoli_crop",
            () -> new com.stardew.craft.block.crop.BroccoliCropBlock());

    public static final DeferredBlock<Block> CARROT_CROP = BLOCKS.register("carrot_crop",
            () -> new com.stardew.craft.block.crop.CarrotCropBlock());

    public static final DeferredBlock<Block> CAULIFLOWER_CROP = BLOCKS.register("cauliflower_crop",
            () -> new com.stardew.craft.block.crop.CauliflowerCropBlock());

    public static final DeferredBlock<Block> COFFEE_BEAN_CROP = BLOCKS.register("coffee_bean_crop",
            () -> new com.stardew.craft.block.crop.CoffeeBeanCropBlock());

    public static final DeferredBlock<Block> CORN_CROP = BLOCKS.register("corn_crop",
            () -> new com.stardew.craft.block.crop.CornCropBlock());

    public static final DeferredBlock<Block> CRANBERRY_CROP = BLOCKS.register("cranberry_crop",
            () -> new com.stardew.craft.block.crop.CranberryCropBlock());

    public static final DeferredBlock<Block> EGGPLANT_CROP = BLOCKS.register("eggplant_crop",
            () -> new com.stardew.craft.block.crop.EggplantCropBlock());

    public static final DeferredBlock<Block> FAIRY_ROSE_CROP = BLOCKS.register("fairy_rose_crop",
            () -> new com.stardew.craft.block.crop.FairyRoseCropBlock());

    public static final DeferredBlock<Block> GARLIC_CROP = BLOCKS.register("garlic_crop",
            () -> new com.stardew.craft.block.crop.GarlicCropBlock());

    public static final DeferredBlock<Block> GRAPE_CROP = BLOCKS.register("grape_crop",
            () -> new com.stardew.craft.block.crop.GrapeCropBlock());

    public static final DeferredBlock<Block> GREEN_BEAN_CROP = BLOCKS.register("green_bean_crop",
            () -> new com.stardew.craft.block.crop.GreenBeanCropBlock());

    public static final DeferredBlock<Block> HOPS_CROP = BLOCKS.register("hops_crop",
            () -> new com.stardew.craft.block.crop.HopsCropBlock());

    public static final DeferredBlock<Block> HOT_PEPPER_CROP = BLOCKS.register("hot_pepper_crop",
            () -> new com.stardew.craft.block.crop.HotPepperCropBlock());

    public static final DeferredBlock<Block> KALE_CROP = BLOCKS.register("kale_crop",
            () -> new com.stardew.craft.block.crop.KaleCropBlock());

    public static final DeferredBlock<Block> MELON_CROP = BLOCKS.register("melon_crop",
            () -> new com.stardew.craft.block.crop.MelonCropBlock());

    public static final DeferredBlock<Block> PARSNIP_CROP = BLOCKS.register("parsnip_crop",
            () -> new com.stardew.craft.block.crop.ParsnipCropBlock());

    public static final DeferredBlock<Block> POPPY_CROP = BLOCKS.register("poppy_crop",
            () -> new com.stardew.craft.block.crop.PoppyCropBlock());

    public static final DeferredBlock<Block> POTATO_CROP = BLOCKS.register("potato_crop",
            () -> new com.stardew.craft.block.crop.PotatoCropBlock());

    public static final DeferredBlock<Block> POWDER_MELON_CROP = BLOCKS.register("powder_melon_crop",
            () -> new com.stardew.craft.block.crop.PowderMelonCropBlock());

    public static final DeferredBlock<Block> PUMPKIN_CROP = BLOCKS.register("pumpkin_crop",
            () -> new com.stardew.craft.block.crop.PumpkinCropBlock());

    public static final DeferredBlock<Block> RADISH_CROP = BLOCKS.register("radish_crop",
            () -> new com.stardew.craft.block.crop.RadishCropBlock());

    public static final DeferredBlock<Block> RED_CABBAGE_CROP = BLOCKS.register("red_cabbage_crop",
            () -> new com.stardew.craft.block.crop.RedCabbageCropBlock());

    public static final DeferredBlock<Block> RHUBARB_CROP = BLOCKS.register("rhubarb_crop",
            () -> new com.stardew.craft.block.crop.RhubarbCropBlock());

    public static final DeferredBlock<Block> RICE_CROP = BLOCKS.register("rice_crop",
            () -> new com.stardew.craft.block.crop.RiceCropBlock());

    public static final DeferredBlock<Block> STARFRUIT_CROP = BLOCKS.register("starfruit_crop",
            () -> new com.stardew.craft.block.crop.StarfruitCropBlock());

    public static final DeferredBlock<Block> STRAWBERRY_CROP = BLOCKS.register("strawberry_crop",
            () -> new com.stardew.craft.block.crop.StrawberryCropBlock());

    public static final DeferredBlock<Block> SUMMER_SPANGLE_CROP = BLOCKS.register("summer_spangle_crop",
            () -> new com.stardew.craft.block.crop.SummerSpangleCropBlock());

    public static final DeferredBlock<Block> SUMMER_SQUASH_CROP = BLOCKS.register("summer_squash_crop",
            () -> new com.stardew.craft.block.crop.SummerSquashCropBlock());

    public static final DeferredBlock<Block> SUNFLOWER_CROP = BLOCKS.register("sunflower_crop",
            () -> new com.stardew.craft.block.crop.SunflowerCropBlock());

    public static final DeferredBlock<Block> TOMATO_CROP = BLOCKS.register("tomato_crop",
            () -> new com.stardew.craft.block.crop.TomatoCropBlock());

    public static final DeferredBlock<Block> TULIP_CROP = BLOCKS.register("tulip_crop",
            () -> new com.stardew.craft.block.crop.TulipCropBlock());

    public static final DeferredBlock<Block> WHEAT_CROP = BLOCKS.register("wheat_crop",
            () -> new com.stardew.craft.block.crop.WheatCropBlock());

    public static final DeferredBlock<Block> YAM_CROP = BLOCKS.register("yam_crop",
            () -> new com.stardew.craft.block.crop.YamCropBlock());

    public static final DeferredBlock<Block> FIBER_CROP = BLOCKS.register("fiber_crop",
            () -> new com.stardew.craft.block.crop.FiberCropBlock());
    public static final DeferredBlock<Block> SWEET_GEM_BERRY_CROP = BLOCKS.register("sweet_gem_berry_crop",
            () -> new com.stardew.craft.block.crop.SweetGemBerryCropBlock());

    // Wild Seed Crops (grow 7 days then transform into forage blocks)
    public static final DeferredBlock<Block> SPRING_WILD_SEED_CROP = BLOCKS.register("spring_wild_seed_crop",
            () -> new com.stardew.craft.block.crop.WildSeedCropBlock(0, com.stardew.craft.item.ModItems.SPRING_SEEDS));
    public static final DeferredBlock<Block> SUMMER_WILD_SEED_CROP = BLOCKS.register("summer_wild_seed_crop",
            () -> new com.stardew.craft.block.crop.WildSeedCropBlock(1, com.stardew.craft.item.ModItems.SUMMER_SEEDS));
    public static final DeferredBlock<Block> FALL_WILD_SEED_CROP = BLOCKS.register("fall_wild_seed_crop",
            () -> new com.stardew.craft.block.crop.WildSeedCropBlock(2, com.stardew.craft.item.ModItems.FALL_SEEDS));
    public static final DeferredBlock<Block> WINTER_WILD_SEED_CROP = BLOCKS.register("winter_wild_seed_crop",
            () -> new com.stardew.craft.block.crop.WildSeedCropBlock(3, com.stardew.craft.item.ModItems.WINTER_SEEDS));

        @SuppressWarnings("null")
public static final DeferredBlock<Block> DEAD_CROP = BLOCKS.register("dead_crop",
            () -> new com.stardew.craft.block.crop.DeadCropBlock(Block.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .sound(net.minecraft.world.level.block.SoundType.GRASS)
                    .noCollission()
                    .instabreak()));

        @SuppressWarnings("null")
        private static Block.Properties newTreeWoodProps() {
                return Block.Properties.of()
                                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                .strength(2.0F, 3.0F);
        }

        @SuppressWarnings("null")
        private static DeferredBlock<Block> newTreeRoot(String name) {
                return BLOCKS.register(name + "_root",
                                () -> new com.stardew.craft.block.tree.NewTreePartBlock(newTreeWoodProps().noOcclusion(), true));
        }

        @SuppressWarnings("null")
        private static DeferredBlock<Block> newTreeLog(String name) {
                return BLOCKS.register(name + "_log", () -> new com.stardew.craft.block.tree.NewTreeLogBlock(newTreeWoodProps()));
        }

        @SuppressWarnings("null")
        private static DeferredBlock<Block> newTreeLeaves(String name) {
                return BLOCKS.register(name + "_leaves",
                                () -> new com.stardew.craft.block.tree.StardewLeavesBlock(Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                .strength(0.2F)
                                                .randomTicks()
                                                .noOcclusion()));
        }

        @SuppressWarnings("null")
        private static DeferredBlock<Block> newTreeBranch(String name) {
                return BLOCKS.register(name + "_branch",
                                () -> new com.stardew.craft.block.tree.NewTreePartBlock(newTreeWoodProps().noOcclusion(), true));
        }

        // 新树系统（与 wild_* 旧树系统并存）
        public static final DeferredBlock<Block> OAK_ROOT = newTreeRoot("oak");
        public static final DeferredBlock<Block> OAK_LOG = newTreeLog("oak");
        public static final DeferredBlock<Block> OAK_LEAVES = newTreeLeaves("oak");
        public static final DeferredBlock<Block> OAK_LEAVES_QUESTION = BLOCKS.register("oak_leaves_question",
                        () -> new Block(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noCollission()
                                        .noOcclusion()));
        public static final DeferredBlock<Block> OAK_BRANCH = newTreeBranch("oak");

        public static final DeferredBlock<Block> MAPLE_ROOT = newTreeRoot("maple");
        public static final DeferredBlock<Block> MAPLE_LOG = newTreeLog("maple");
        public static final DeferredBlock<Block> MAPLE_LEAVES = newTreeLeaves("maple");
        public static final DeferredBlock<Block> MAPLE_BRANCH = newTreeBranch("maple");

        public static final DeferredBlock<Block> PINE_ROOT = newTreeRoot("pine");
        public static final DeferredBlock<Block> PINE_LOG = newTreeLog("pine");
        public static final DeferredBlock<Block> PINE_LEAVES = newTreeLeaves("pine");
        public static final DeferredBlock<Block> PINE_BRANCH = newTreeBranch("pine");

        public static final DeferredBlock<Block> MAHOGANY_ROOT = newTreeRoot("mahogany");
        public static final DeferredBlock<Block> MAHOGANY_LOG = newTreeLog("mahogany");
        public static final DeferredBlock<Block> MAHOGANY_LEAVES = newTreeLeaves("mahogany");
        public static final DeferredBlock<Block> MAHOGANY_BRANCH = newTreeBranch("mahogany");

        public static final DeferredBlock<Block> MYSTIC_TREE_ROOT = newTreeRoot("mystic_tree");
        public static final DeferredBlock<Block> MYSTIC_TREE_LOG = newTreeLog("mystic_tree");
        public static final DeferredBlock<Block> MYSTIC_TREE_LEAVES = newTreeLeaves("mystic_tree");
        public static final DeferredBlock<Block> MYSTIC_TREE_BRANCH = newTreeBranch("mystic_tree");

        private static final String[] NEW_TREE_WOOD_SPECIES = {
                "oak",
                "maple",
                "pine",
                "mahogany",
                "mystic_tree"
        };

        private static final String[] NEW_TREE_PLANK_PATTERNS = {
                "",
                "checkerboard_",
                "fishscale_"
        };

        private static DeferredBlock<? extends Block> newTreeLogBlock(String species) {
                return switch (species) {
                        case "oak" -> OAK_LOG;
                        case "maple" -> MAPLE_LOG;
                        case "pine" -> PINE_LOG;
                        case "mahogany" -> MAHOGANY_LOG;
                        case "mystic_tree" -> MYSTIC_TREE_LOG;
                        default -> throw new IllegalArgumentException("Unknown new tree species: " + species);
                };
        }

        private static Map<String, DeferredBlock<? extends Block>> registerNewTreeBuildingBlocks() {
                LinkedHashMap<String, DeferredBlock<? extends Block>> blocks = new LinkedHashMap<>();

                for (String species : NEW_TREE_WOOD_SPECIES) {
                        for (String pattern : NEW_TREE_PLANK_PATTERNS) {
                                String baseName = species + "_" + pattern + "planks";
                                DeferredBlock<Block> planks = BLOCKS.register(baseName, () -> new Block(newTreeWoodProps()));
                                blocks.put(baseName, planks);
                                blocks.put(baseName + "_stairs", stairsFromAnyBlock(baseName + "_stairs", planks, newTreeWoodProps()));
                                blocks.put(baseName + "_slab", slab(baseName + "_slab", newTreeWoodProps()));
                                blocks.put(baseName + "_fence", fence(baseName + "_fence", newTreeWoodProps()));
                                blocks.put(baseName + "_fence_gate", fenceGate(baseName + "_fence_gate", newTreeWoodProps()));
                        }

                        String logBaseName = species + "_log";
                        DeferredBlock<? extends Block> log = newTreeLogBlock(species);
                        blocks.put(logBaseName + "_stairs", stairsFromAnyBlock(logBaseName + "_stairs", log, newTreeWoodProps()));
                        blocks.put(logBaseName + "_slab", slab(logBaseName + "_slab", newTreeWoodProps()));
                }

                return Collections.unmodifiableMap(blocks);
        }

        public static final Map<String, DeferredBlock<? extends Block>> NEW_TREE_BUILDING_BLOCKS = registerNewTreeBuildingBlocks();

        // 野生树（原型：橡树）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_TRUNK0 = BLOCKS.register("wild_oak_trunk0",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_TRUNK1 = BLOCKS.register("wild_oak_trunk1",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_BRANCH1 = BLOCKS.register("wild_oak_branch1",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_BRANCH2 = BLOCKS.register("wild_oak_branch2",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_LEAVES = BLOCKS.register("wild_oak_leaves",
                        () -> new com.stardew.craft.block.tree.WildOakLeavesBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noOcclusion()));

        @SuppressWarnings("null")
        private static Block.Properties fruitSaplingProps() {
                return Block.Properties.of()
                                .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                .noCollission()
                                .noOcclusion()
                                .instabreak();
        }

        @SuppressWarnings("null")
        private static Block.Properties fruitTreeProps() {
                return Block.Properties.of()
                                .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                .noOcclusion()
                                .strength(2.0F, 3.0F);
        }

        private static Map<FruitTreeType, DeferredBlock<Block>> registerFruitTreeSaplings() {
                LinkedHashMap<FruitTreeType, DeferredBlock<Block>> blocks = new LinkedHashMap<>();
                for (FruitTreeType type : FruitTreeType.values()) {
                        blocks.put(type, BLOCKS.register(type.saplingBlockId(),
                                        () -> new com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock(type, fruitSaplingProps())));
                }
                return Collections.unmodifiableMap(blocks);
        }

        private static Map<FruitTreeType, DeferredBlock<Block>> registerFruitTrees() {
                LinkedHashMap<FruitTreeType, DeferredBlock<Block>> blocks = new LinkedHashMap<>();
                for (FruitTreeType type : FruitTreeType.values()) {
                        blocks.put(type, BLOCKS.register(type.matureBlockId(),
                                        () -> new com.stardew.craft.block.tree.fruit.FruitTreeBlock(type, fruitTreeProps())));
                }
                return Collections.unmodifiableMap(blocks);
        }

        private static Map<FruitTreeType, DeferredBlock<Block>> registerFruitTreeExtensions() {
                LinkedHashMap<FruitTreeType, DeferredBlock<Block>> blocks = new LinkedHashMap<>();
                for (FruitTreeType type : FruitTreeType.values()) {
                        blocks.put(type, BLOCKS.register(type.extensionBlockId(),
                                        () -> new com.stardew.craft.block.tree.fruit.FruitTreeExtensionBlock(type, fruitTreeProps())));
                }
                return Collections.unmodifiableMap(blocks);
        }

        public static final Map<FruitTreeType, DeferredBlock<Block>> FRUIT_TREE_SAPLINGS = registerFruitTreeSaplings();
        public static final Map<FruitTreeType, DeferredBlock<Block>> FRUIT_TREES = registerFruitTrees();
        public static final Map<FruitTreeType, DeferredBlock<Block>> FRUIT_TREE_EXTENSIONS = registerFruitTreeExtensions();

        public static final DeferredBlock<Block> CHERRY_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.CHERRY);
        public static final DeferredBlock<Block> APRICOT_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.APRICOT);
        public static final DeferredBlock<Block> ORANGE_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.ORANGE);
        public static final DeferredBlock<Block> PEACH_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.PEACH);
        public static final DeferredBlock<Block> POMEGRANATE_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.POMEGRANATE);
        public static final DeferredBlock<Block> APPLE_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.APPLE);
        public static final DeferredBlock<Block> BANANA_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.BANANA);
        public static final DeferredBlock<Block> MANGO_SAPLING = FRUIT_TREE_SAPLINGS.get(FruitTreeType.MANGO);

        public static final DeferredBlock<Block> CHERRY_TREE = FRUIT_TREES.get(FruitTreeType.CHERRY);
        public static final DeferredBlock<Block> APRICOT_TREE = FRUIT_TREES.get(FruitTreeType.APRICOT);
        public static final DeferredBlock<Block> ORANGE_TREE = FRUIT_TREES.get(FruitTreeType.ORANGE);
        public static final DeferredBlock<Block> PEACH_TREE = FRUIT_TREES.get(FruitTreeType.PEACH);
        public static final DeferredBlock<Block> POMEGRANATE_TREE = FRUIT_TREES.get(FruitTreeType.POMEGRANATE);
        public static final DeferredBlock<Block> APPLE_TREE = FRUIT_TREES.get(FruitTreeType.APPLE);
        public static final DeferredBlock<Block> BANANA_TREE = FRUIT_TREES.get(FruitTreeType.BANANA);
        public static final DeferredBlock<Block> MANGO_TREE = FRUIT_TREES.get(FruitTreeType.MANGO);

        public static final DeferredBlock<Block> CHERRY_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.CHERRY);
        public static final DeferredBlock<Block> APRICOT_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.APRICOT);
        public static final DeferredBlock<Block> ORANGE_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.ORANGE);
        public static final DeferredBlock<Block> PEACH_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.PEACH);
        public static final DeferredBlock<Block> POMEGRANATE_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.POMEGRANATE);
        public static final DeferredBlock<Block> APPLE_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.APPLE);
        public static final DeferredBlock<Block> BANANA_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.BANANA);
        public static final DeferredBlock<Block> MANGO_TREE_EXTENSION = FRUIT_TREE_EXTENSIONS.get(FruitTreeType.MANGO);

        // 野生树苗（2阶段，28天成熟）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_SAPLING0 = BLOCKS.register("wild_oak_sapling0",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.OAK,
                                        0,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_OAK_SAPLING1 = BLOCKS.register("wild_oak_sapling1",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.OAK,
                                        1,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        // 野生树：枫树
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_TRUNK0 = BLOCKS.register("wild_maple_trunk0",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_TRUNK1 = BLOCKS.register("wild_maple_trunk1",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_BRANCH1 = BLOCKS.register("wild_maple_branch1",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_BRANCH2 = BLOCKS.register("wild_maple_branch2",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_LEAVES = BLOCKS.register("wild_maple_leaves",
                        () -> new com.stardew.craft.block.tree.WildOakLeavesBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noOcclusion()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_SAPLING0 = BLOCKS.register("wild_maple_sapling0",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.MAPLE,
                                        0,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAPLE_SAPLING1 = BLOCKS.register("wild_maple_sapling1",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.MAPLE,
                                        1,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        // 野生树：松树
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_TRUNK0 = BLOCKS.register("wild_pine_trunk0",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_TRUNK1 = BLOCKS.register("wild_pine_trunk1",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_BRANCH1 = BLOCKS.register("wild_pine_branch1",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_BRANCH2 = BLOCKS.register("wild_pine_branch2",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_LEAVES = BLOCKS.register("wild_pine_leaves",
                        () -> new com.stardew.craft.block.tree.WildOakLeavesBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noOcclusion()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_SAPLING0 = BLOCKS.register("wild_pine_sapling0",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.PINE,
                                        0,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_PINE_SAPLING1 = BLOCKS.register("wild_pine_sapling1",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.PINE,
                                        1,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        // 野生树：桃花心木
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_TRUNK0 = BLOCKS.register("wild_mahogany_trunk0",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_TRUNK1 = BLOCKS.register("wild_mahogany_trunk1",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_BRANCH1 = BLOCKS.register("wild_mahogany_branch1",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_BRANCH2 = BLOCKS.register("wild_mahogany_branch2",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_LEAVES = BLOCKS.register("wild_mahogany_leaves",
                        () -> new com.stardew.craft.block.tree.WildOakLeavesBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noOcclusion()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_SAPLING0 = BLOCKS.register("wild_mahogany_sapling0",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.MAHOGANY,
                                        0,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MAHOGANY_SAPLING1 = BLOCKS.register("wild_mahogany_sapling1",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.MAHOGANY,
                                        1,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        // 野生树：神秘树
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_TRUNK0 = BLOCKS.register("wild_mystic_tree_trunk0",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_TRUNK1 = BLOCKS.register("wild_mystic_tree_trunk1",
                        () -> new com.stardew.craft.block.tree.WildOakTrunkBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_BRANCH1 = BLOCKS.register("wild_mystic_tree_branch1",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_BRANCH2 = BLOCKS.register("wild_mystic_tree_branch2",
                        () -> new com.stardew.craft.block.tree.WildOakBranchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_LEAVES = BLOCKS.register("wild_mystic_tree_leaves",
                        () -> new com.stardew.craft.block.tree.WildOakLeavesBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noOcclusion()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_SAPLING0 = BLOCKS.register("wild_mystic_tree_sapling0",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.MYSTIC_TREE,
                                        0,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WILD_MYSTIC_TREE_SAPLING1 = BLOCKS.register("wild_mystic_tree_sapling1",
                        () -> new com.stardew.craft.block.tree.WildTreeSaplingBlock(
                                        com.stardew.craft.tree.WildTrees.MYSTIC_TREE,
                                        1,
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noCollission()
                                                        .noOcclusion()
                                                        .instabreak()));

        // 实用设施
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TAPPER = BLOCKS.register("tapper",
                        () -> new com.stardew.craft.block.utility.TapperBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LUCKY_PURPLE_SHORTS = BLOCKS.register("lucky_purple_shorts",
                        () -> new com.stardew.craft.block.utility.LuckyPurpleShortsBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.2F, 0.2F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> KEG = BLOCKS.register("keg",
                        () -> new com.stardew.craft.block.utility.KegBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PRESERVES_JAR = BLOCKS.register("preserves_jar",
                        () -> new com.stardew.craft.block.utility.PreservesJarBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DEHYDRATOR = BLOCKS.register("dehydrator",
                        () -> new com.stardew.craft.block.utility.DehydratorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BAIT_MAKER = BLOCKS.register("bait_maker",
                        () -> new com.stardew.craft.block.utility.BaitMakerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FISH_SMOKER = BLOCKS.register("fish_smoker",
                        () -> new com.stardew.craft.block.utility.FishSmokerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .lightLevel(state -> state.getValue(com.stardew.craft.block.utility.FishSmokerBlock.WORKING) ? 13 : 0)
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> RECYCLING_MACHINE = BLOCKS.register("recycling_machine",
                        () -> new com.stardew.craft.block.utility.RecyclingMachineBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> COOKING_POT = BLOCKS.register("cooking_pot",
                        () -> new com.stardew.craft.block.utility.CookingPotBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CRYSTALARIUM = BLOCKS.register("crystalarium",
                        () -> new com.stardew.craft.block.utility.CrystalariumBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SEED_MAKER = BLOCKS.register("seed_maker",
                        () -> new com.stardew.craft.block.utility.SeedMakerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> FURNACE = BLOCKS.register("furnace",
                                        () -> new com.stardew.craft.block.utility.FurnaceBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                .noOcclusion()
                                                                .lightLevel(state -> state.getValue(com.stardew.craft.block.utility.FurnaceBlock.WORKING) ? 15 : 0)
                                                                .strength(1.5F, 3.0F)));

                // ─── Mastery reward blocks ───
                @SuppressWarnings("null")
                public static final DeferredBlock<Block> HEAVY_FURNACE = BLOCKS.register("heavy_furnace",
                                        () -> new com.stardew.craft.block.mastery.HeavyFurnaceBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                .noOcclusion()
                                                                .lightLevel(state -> state.getValue(com.stardew.craft.block.utility.FurnaceBlock.WORKING) ? 15 : 0)
                                                                .strength(2.5F, 6.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> STATUE_OF_BLESSINGS = BLOCKS.register("statue_of_blessings",
                                        () -> new com.stardew.craft.block.mastery.StatueOfBlessingsBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                                                .sound(net.minecraft.world.level.block.SoundType.STONE)
                                                                .noOcclusion()
                                                                .strength(2.0F, 6.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> STATUE_OF_DWARF_KING = BLOCKS.register("statue_of_dwarf_king",
                                        () -> new com.stardew.craft.block.mastery.StatueOfDwarfKingBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                                                .sound(net.minecraft.world.level.block.SoundType.STONE)
                                                                .noOcclusion()
                                                                .strength(2.0F, 6.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> UNCERTAINTY_STATUE = BLOCKS.register("uncertainty_statue",
                                        () -> new com.stardew.craft.block.decor.UncertaintyStatueBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                                                .sound(net.minecraft.world.level.block.SoundType.STONE)
                                                                .noOcclusion()
                                                                .strength(-1.0F, 3600000.0F)
                                                                .noLootTable()));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> ANVIL_MASTERY = BLOCKS.register("anvil_mastery",
                                        () -> new com.stardew.craft.block.mastery.AnvilBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.ANVIL)
                                                                .noOcclusion()
                                                                .strength(3.0F, 6.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> MINI_FORGE = BLOCKS.register("mini_forge",
                                        () -> new com.stardew.craft.block.mastery.MiniForgeBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                .noOcclusion()
                                                                .lightLevel(s -> 7)
                                                                .strength(2.5F, 6.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> CHARCOAL_KILN = BLOCKS.register("charcoal_kiln",
                                                () -> new com.stardew.craft.block.utility.CharcoalKilnBlock(Block.Properties.of()
                                                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                                        .noOcclusion()
                                                                        .lightLevel(state -> state.getValue(com.stardew.craft.block.utility.CharcoalKilnBlock.WORKING) ? 15 : 0)
                                                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> LIGHTNING_ROD = BLOCKS.register("lightning_rod",
                                () -> new com.stardew.craft.block.utility.LightningRodBlock(Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> SPRINKLER = BLOCKS.register("sprinkler",
                                () -> new com.stardew.craft.block.utility.SprinklerBlock(
                                                com.stardew.craft.block.utility.SprinklerTier.BASIC,
                                                Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                .noOcclusion()
                                                                .strength(1.0F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> QUALITY_SPRINKLER = BLOCKS.register("quality_sprinkler",
                                () -> new com.stardew.craft.block.utility.SprinklerBlock(
                                                com.stardew.craft.block.utility.SprinklerTier.QUALITY,
                                                Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                .noOcclusion()
                                                                .strength(1.0F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> IRIDIUM_SPRINKLER = BLOCKS.register("iridium_sprinkler",
                                () -> new com.stardew.craft.block.utility.SprinklerBlock(
                                                com.stardew.craft.block.utility.SprinklerTier.IRIDIUM,
                                                Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                .noOcclusion()
                                                                .strength(1.0F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> SOLAR_PANEL = BLOCKS.register("solar_panel",
                                                () -> new com.stardew.craft.block.utility.SolarPanelBlock(Block.Properties.of()
                                                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                                                        .noOcclusion()
                                                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                private static DeferredBlock<Block> specialOrderUtilityBlock(String name, MapColor color, SoundType sound) {
                                return BLOCKS.register(name, () -> new Block(Block.Properties.of()
                                                .mapColor(color)
                                                .sound(sound)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));
                }

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> SPECIAL_ORDERS_BOARD = BLOCKS.register("special_orders_board",
                                                () -> new com.stardew.craft.block.decor.SpecialOrdersBoardBlock(Block.Properties.of()
                                                                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                                                .strength(-1.0F, 3600000.0F)
                                                                .noLootTable()
                                                                .noOcclusion(),
                                                                "stardewcraft:decor/special_orders_board"));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> GEODE_CRUSHER = BLOCKS.register("geode_crusher",
                                () -> new com.stardew.craft.block.utility.GeodeCrusherBlock(Block.Properties.of()
                                                .mapColor(MapColor.METAL)
                                                .sound(SoundType.METAL)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));
                public static final DeferredBlock<Block> MINI_OBELISK = BLOCKS.register("mini_obelisk",
                                () -> new com.stardew.craft.block.utility.MiniObeliskBlock(Block.Properties.of()
                                                .mapColor(MapColor.COLOR_PURPLE)
                                                .sound(SoundType.STONE)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));
                public static final DeferredBlock<Block> FARM_COMPUTER = BLOCKS.register("farm_computer",
                                () -> new com.stardew.craft.block.utility.FarmComputerBlock(Block.Properties.of()
                                                .mapColor(MapColor.METAL)
                                                .sound(SoundType.METAL)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));
                public static final DeferredBlock<Block> BONE_MILL = BLOCKS.register("bone_mill",
                                () -> new com.stardew.craft.block.utility.BoneMillBlock(Block.Properties.of()
                                                .mapColor(MapColor.TERRACOTTA_WHITE)
                                                .sound(SoundType.WOOD)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));
                @SuppressWarnings("null")
                public static final DeferredBlock<Block> COFFEE_MAKER = BLOCKS.register("coffee_maker",
                                () -> new com.stardew.craft.block.utility.CoffeeMakerBlock(Block.Properties.of()
                                                .mapColor(MapColor.METAL)
                                                .sound(SoundType.METAL)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));


                @SuppressWarnings("null")
                public static final DeferredBlock<Block> CASK = BLOCKS.register("cask",
                                                () -> new com.stardew.craft.block.utility.CaskBlock(Block.Properties.of()
                                                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                                        .noOcclusion()
                                                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> CHEESE_PRESS = BLOCKS.register("cheese_press",
                                                () -> new com.stardew.craft.block.utility.CheesePressBlock(Block.Properties.of()
                                                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                                        .noOcclusion()
                                                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> MAYONNAISE_MACHINE = BLOCKS.register("mayonnaise_machine",
                                                () -> new com.stardew.craft.block.utility.MayonnaiseMachineBlock(Block.Properties.of()
                                                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                                        .noOcclusion()
                                                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> INCUBATOR = BLOCKS.register("incubator",
                                                () -> new com.stardew.craft.block.utility.IncubatorBlock(Block.Properties.of()
                                                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                                        .noOcclusion()
                                                                        .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> OIL_MAKER = BLOCKS.register("oil_maker",
                                () -> new com.stardew.craft.block.utility.OilMakerBlock(Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> LOOM = BLOCKS.register("loom",
                                () -> new com.stardew.craft.block.utility.LoomBlock(Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                .noOcclusion()
                                                .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WORM_BIN = BLOCKS.register("worm_bin",
                        () -> new com.stardew.craft.block.utility.WormBinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FEED_TROUGH = BLOCKS.register("feed_trough",
                        () -> new com.stardew.craft.block.utility.FeedTroughBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> AUTOFEED_TROUGH = BLOCKS.register("autofeed_trough",
                        () -> new com.stardew.craft.block.utility.AutoFeedTroughBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> AUTO_GRABBER = BLOCKS.register("auto_grabber",
                        () -> new com.stardew.craft.block.utility.AutoGrabberBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> AUTO_PETTER = BLOCKS.register("auto_petter",
                        () -> new com.stardew.craft.block.utility.AutoPetterBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WOODEN_CHEST = BLOCKS.register("wooden_chest",
                        () -> new com.stardew.craft.block.utility.WoodenChestBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> STONE_CHEST = BLOCKS.register("stone_chest",
                        () -> new com.stardew.craft.block.utility.StoneChestBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FRIDGE = BLOCKS.register("fridge",
                        () -> new com.stardew.craft.block.utility.FridgeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F), "stardewcraft:decor/common/fridge"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MAILBOX = BLOCKS.register("mailbox",
                        () -> new com.stardew.craft.block.utility.MailboxBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHIPPING_BIN = BLOCKS.register("shipping_bin",
                        () -> new com.stardew.craft.block.utility.ShippingBinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HEATER = BLOCKS.register("heater",
                        () -> new com.stardew.craft.block.utility.HeaterBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HAY_HOPPER = BLOCKS.register("hay_hopper",
                        () -> new com.stardew.craft.block.utility.HayHopperBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FRIENDSHIP_DOOR = BLOCKS.register("friendship_door",
                        () -> new com.stardew.craft.block.utility.FriendshipDoorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(3.0F)
                                        .noOcclusion()
                                        .ignitedByLava()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        @SuppressWarnings("null")
        // 硬度=1.5F（木质），未绑定建筑时可正常挖掉；绑定后由 onDestroyedByPlayer 拦截
        public static final DeferredBlock<Block> COOP_MANAGER = BLOCKS.register("coop_manager",
                        () -> new com.stardew.craft.block.utility.CoopManagerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(1.5F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BARN_MANAGER = BLOCKS.register("barn_manager",
                        () -> new com.stardew.craft.block.utility.BarnManagerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(1.5F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SILO_MANAGER = BLOCKS.register("silo_manager",
                        () -> new com.stardew.craft.block.utility.SiloManagerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(1.5F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FISH_POND_MANAGER = BLOCKS.register("fish_pond_manager",
                        () -> new com.stardew.craft.block.utility.FishPondManagerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(1.5F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TRASH_BIN = BLOCKS.register("trash_bin",
                        () -> new com.stardew.craft.block.utility.TrashBinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DELUXE_WORM_BIN = BLOCKS.register("deluxe_worm_bin",
                        () -> new com.stardew.craft.block.utility.DeluxeWormBinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BEE_HOUSE = BLOCKS.register("bee_house",
                        () -> new com.stardew.craft.block.utility.BeeHouseBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CRAB_POT = BLOCKS.register("crab_pot",
                        () -> new com.stardew.craft.block.utility.CrabPotBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        // 需求：徒手也能很快拆（类似羊毛/更快）
                                        .strength(0.2F, 1.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WATER_LANTERN = BLOCKS.register("water_lantern",
                        () -> new com.stardew.craft.block.festival.WaterLanternBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .lightLevel(state -> 15)
                                        .noCollission()
                                        .noOcclusion()
                                        .strength(0.2F, 1.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FISH_NET = BLOCKS.register("fish_net",
                        () -> new com.stardew.craft.block.decor.FishNetDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 2.0F), "stardewcraft:utility/fish_net"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FISH_POND_BUCKET = BLOCKS.register("fish_pond_bucket",
                        () -> new com.stardew.craft.block.utility.FishPondBucketBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FISH_POND_WATER = BLOCKS.register("fish_pond_water",
                        () -> new com.stardew.craft.block.utility.FishPondWaterBlock(ModFluids.FISH_POND_WATER.get(), Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WATER)
                                        .noCollission()
                                        .noLootTable()
                                        .noOcclusion()
                                        .replaceable()
                                        .strength(100.0F, 3600000.0F)
                                        .sound(net.minecraft.world.level.block.SoundType.EMPTY)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MUSEUM_EXHIBIT_STAND = BLOCKS.register("museum_exhibit_stand",
                        () -> new com.stardew.craft.block.utility.MuseumExhibitStandBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BED_1 = BLOCKS.register("bed_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/bed_1"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BED_2 = BLOCKS.register("bed_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/bed_2"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SOFA = BLOCKS.register("sofa",
                        () -> new com.stardew.craft.block.utility.SofaBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CHAIR_1 = BLOCKS.register("chair_1",
                        () -> new com.stardew.craft.block.utility.ChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/chair_1", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CHAIR_2 = BLOCKS.register("chair_2",
                        () -> new com.stardew.craft.block.utility.ChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/chair_2", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CHAIR_3 = BLOCKS.register("chair_3",
                        () -> new com.stardew.craft.block.utility.ChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/chair_3", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_1 = BLOCKS.register("light_1",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_1"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_2 = BLOCKS.register("light_2",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_2"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_3 = BLOCKS.register("light_3",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_3"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_4 = BLOCKS.register("light_4",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_4"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_5 = BLOCKS.register("light_5",
                        () -> new com.stardew.craft.block.decor.ToggleableCeilingLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_5"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_6 = BLOCKS.register("light_6",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_6"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_7 = BLOCKS.register("light_7",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_7"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CUSHION = BLOCKS.register("cushion",
                        () -> new com.stardew.craft.block.utility.CushionBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/cushion"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OFFICE_STOOL = BLOCKS.register("office_stool",
                        () -> new com.stardew.craft.block.utility.OfficeStoolBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/office_stool_collision"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OFFICE_CHAIR_2 = BLOCKS.register("office_chair_2",
                        () -> new com.stardew.craft.block.utility.OfficeChair2Block(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/office_chair_2_collision"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OFFICE_STOOL_TOP_RENDER = BLOCKS.register("office_stool_top_render",
                        () -> new com.stardew.craft.block.utility.OfficeStoolTopRenderBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OFFICE_CHAIR_2_TOP_RENDER = BLOCKS.register("office_chair_2_top_render",
                        () -> new com.stardew.craft.block.utility.OfficeChair2TopRenderBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SINK_4 = BLOCKS.register("sink_4",
                        () -> new com.stardew.craft.block.utility.MapUtilityStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.3F), "stardewcraft:decor/common/sink_4"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FLOOR_LAMP = BLOCKS.register("floor_lamp",
                        () -> new com.stardew.craft.block.utility.ToggleableDecorLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/floor_lamp_5"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TABLE_LAMP = BLOCKS.register("table_lamp",
                        () -> new com.stardew.craft.block.utility.ToggleableDecorLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/table_lamp"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> STOOL = BLOCKS.register("stool",
                        () -> new com.stardew.craft.block.utility.DyeableChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/stool", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> IRON_STOOL = BLOCKS.register("iron_stool",
                        () -> new com.stardew.craft.block.utility.DyeableChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/iron_stool", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DINING_CHAIR_WOOD = BLOCKS.register("dining_chair_wood",
                        () -> new com.stardew.craft.block.utility.DyeableChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/dining_chair_wood", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ARCADE_MACHINE = BLOCKS.register("arcade_machine",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/arcade_machine"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DINING_CHAIR_IRON = BLOCKS.register("dining_chair_iron",
                        () -> new com.stardew.craft.block.utility.DyeableChairBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/dining_chair_iron", 9.0D / 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PHOTO_FRAME = BLOCKS.register("photo_frame",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/photo_frame"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OAK_TABLE = BLOCKS.register("oak_table",
                        () -> new com.stardew.craft.block.utility.OakTableBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SPRUCE_TABLE = BLOCKS.register("spruce_table",
                        () -> new com.stardew.craft.block.utility.OakTableBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "spruce_table"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BIRCH_TABLE = BLOCKS.register("birch_table",
                        () -> new com.stardew.craft.block.utility.OakTableBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "birch_table"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PASTEL_BANNER = BLOCKS.register("pastel_banner",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.8F), "stardewcraft:decor/festival/pastel_banner"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FLOWER_BASKET = BLOCKS.register("flower_basket",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:decor/festival/flower_basket"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FLOWER_CLUSTER = BLOCKS.register("flower_cluster",
                        () -> new com.stardew.craft.block.decor.FlowerDanceDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SEASONAL_DECOR = BLOCKS.register("seasonal_decor",
                        () -> new com.stardew.craft.block.decor.FlowerDanceDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LUAU_SOUP_POT = BLOCKS.register("luau_soup_pot",
                        () -> new com.stardew.craft.block.decor.LuauGeoFestivalDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable(), "stardewcraft:decor/festival/luau_soup_pot_proxy"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LUAU_TORCH = BLOCKS.register("luau_torch",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .lightLevel(state -> 15)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/festival/luau_torch"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LUAU_SPEAKER = BLOCKS.register("luau_speaker",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/festival/luau_speaker"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LUAU_TOTEM = BLOCKS.register("luau_totem",
                        () -> new com.stardew.craft.block.decor.LuauGeoFestivalDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:decor/festival/luau_totem_proxy"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_TREE = BLOCKS.register("winter_star_tree",
                        () -> new com.stardew.craft.block.decor.LuauGeoFestivalDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:decor/festival/winter_star_tree_proxy"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_LARGE_RED_CANDY_CANE = BLOCKS.register("winter_star_large_red_candy_cane",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/festival/winter_star_large_red_candy_cane"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_LARGE_GREEN_CANDY_CANE = BLOCKS.register("winter_star_large_green_candy_cane",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/festival/winter_star_large_green_candy_cane"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_MIXED_CANDY_CANES = BLOCKS.register("winter_star_mixed_candy_canes",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/festival/winter_star_mixed_candy_canes"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_RED_CANDY_CANES = BLOCKS.register("winter_star_red_candy_canes",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/festival/winter_star_red_candy_canes"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_GREEN_CANDY_CANES = BLOCKS.register("winter_star_green_candy_canes",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/festival/winter_star_green_candy_canes"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_PURPLE_GIFT_BOX = BLOCKS.register("winter_star_purple_gift_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.3F, 0.6F), "stardewcraft:decor/festival/winter_star_purple_gift_box"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_RED_GIFT_BOX = BLOCKS.register("winter_star_red_gift_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.3F, 0.6F), "stardewcraft:decor/festival/winter_star_red_gift_box"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_GREEN_GIFT_BOX = BLOCKS.register("winter_star_green_gift_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.3F, 0.6F), "stardewcraft:decor/festival/winter_star_green_gift_box"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINTER_STAR_BLUE_GIFT_BOX = BLOCKS.register("winter_star_blue_gift_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.3F, 0.6F), "stardewcraft:decor/festival/winter_star_blue_gift_box"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SQUID_FEST_PROMO_POSTER = BLOCKS.register("squid_fest_promo_poster",
                        () -> new com.stardew.craft.block.decor.GeoFestivalDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:geo/block/festival/squid_fest_promo_poster.geo.json"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SQUID_FEST_REQUIREMENT_POSTER = BLOCKS.register("squid_fest_requirement_poster",
                        () -> new com.stardew.craft.block.decor.GeoFestivalDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:geo/block/festival/squid_fest_requirement_poster.geo.json",
                                        "stardewcraft.squid_fest.sign"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FAIR_STRENGTH_TESTER = BLOCKS.register("fair_strength_tester",
                        () -> new com.stardew.craft.block.decor.FairStrengthTesterBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable(), "stardewcraft:decor/festival/fair_strength_tester_proxy"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FAIR_WHEEL = BLOCKS.register("fair_wheel",
                        () -> new com.stardew.craft.block.decor.FairWheelBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable(), "stardewcraft:decor/festival/fair_wheel"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FAIR_GRAVE_STONE = BLOCKS.register("fair_grave_stone",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/festival/fair_grave_stone", true));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SPIRIT_EVE_SPIDER_STATUE = BLOCKS.register("spirit_eve_spider_statue",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/festival/spirit_eve_spider_statue",
                                        0.0D, 0.0D, 0.0D, 16.0D, 6.5D, 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SPIRIT_EVE_JACK_O_LANTERN = BLOCKS.register("spirit_eve_jack_o_lantern",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .lightLevel(state -> 15)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/festival/spirit_eve_jack_o_lantern"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FAIR_GRILL = BLOCKS.register("fair_grill",
                        () -> new com.stardew.craft.block.decor.FairGrillBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/festival/fair_grill"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PLUSH_BUNNY = BLOCKS.register("plush_bunny",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.8F), "stardewcraft:decor/festival/plush_bunny"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LAWN_FLAMINGO = BLOCKS.register("lawn_flamingo",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.8F), "stardewcraft:decor/festival/lawn_flamingo"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOLIDAY_RIBBON_POST = BLOCKS.register("holiday_ribbon_post",
                        () -> new com.stardew.craft.block.decor.HolidayRibbonPostBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.8F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SPRUCE_COUNTER = BLOCKS.register("spruce_counter",
                        () -> new com.stardew.craft.block.utility.SpruceCounterBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OAK_ROUND_TABLE = BLOCKS.register("oak_round_table",
                        () -> new com.stardew.craft.block.utility.OakRoundTableBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TABLE_LANTERN = BLOCKS.register("table_lantern",
                        () -> new com.stardew.craft.block.utility.ToggleableDecorLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/table_lantern"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JUKEBOX = BLOCKS.register("jukebox",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/jukebox"));

        // 地图装饰：皮埃尔商店（第一批）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_BASKET = BLOCKS.register("shop_basket",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/2"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_1 = BLOCKS.register("shop_crate_fruit_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_2 = BLOCKS.register("shop_crate_fruit_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_2"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_3 = BLOCKS.register("shop_crate_fruit_3",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_3"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_4 = BLOCKS.register("shop_crate_fruit_4",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_4"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_5 = BLOCKS.register("shop_crate_fruit_5",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_5"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_6 = BLOCKS.register("shop_crate_fruit_6",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_6"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_7 = BLOCKS.register("shop_crate_fruit_7",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_7"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_8 = BLOCKS.register("shop_crate_fruit_8",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_8"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_9 = BLOCKS.register("shop_crate_fruit_9",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_9"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_CRATE_FRUIT_10 = BLOCKS.register("shop_crate_fruit_10",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/3_10"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_PACK_BOX = BLOCKS.register("shop_pack_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/4_1"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_COUNTER_1 = BLOCKS.register("shop_counter_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(1.0F, 1.0F), "stardewcraft:decor/pierre_shop/5_1", true));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_COUNTER_2 = BLOCKS.register("shop_counter_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(1.0F, 1.0F), "stardewcraft:decor/pierre_shop/5_2", true));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_COUNTER_3 = BLOCKS.register("shop_counter_3",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(1.0F, 1.0F), "stardewcraft:decor/pierre_shop/5_3", true));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SUPERMARKET_SHELF_1 = BLOCKS.register("supermarket_shelf_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 1.0F), "stardewcraft:decor/common/supermarket_shelf_1",
                                        0, 0, -16, 16, 25, 32));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SUPERMARKET_SHELF_2 = BLOCKS.register("supermarket_shelf_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 1.0F), "stardewcraft:decor/common/supermarket_shelf_2",
                                        0, 0, -16, 16, 25, 32));

        // Joja 超市相关：箱子（单格）、购物车（2 长 1 宽 1 高，硬编码碰撞）、冰柜（默认自适应）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JOJA_SUPERMARKET_CRATE = BLOCKS.register("joja_supermarket_crate",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/joja_supermarket_crate"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SUPERMARKET_CART = BLOCKS.register("supermarket_cart",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/supermarket_cart",
                                        0, 0, 0, 16, 16, 32));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SUPERMARKET_FREEZER = BLOCKS.register("supermarket_freezer",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.0F, 1.0F), "stardewcraft:decor/common/supermarket_freezer"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_SHIPPING_BIN = BLOCKS.register("shop_shipping_bin",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/pierre_shop/7_1"));

        // 地图装饰：地毯（地面）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_1 = BLOCKS.register("carpet_1",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_2 = BLOCKS.register("carpet_2",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_2"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_3 = BLOCKS.register("carpet_3",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_3"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_4 = BLOCKS.register("carpet_4",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_4"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_5 = BLOCKS.register("carpet_5",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_5"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_6 = BLOCKS.register("carpet_6",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_6"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_7 = BLOCKS.register("carpet_7",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_7"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_8 = BLOCKS.register("carpet_8",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_8"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_9 = BLOCKS.register("carpet_9",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_9"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_10 = BLOCKS.register("carpet_10",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_10"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_11 = BLOCKS.register("carpet_11",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_11"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_12 = BLOCKS.register("carpet_12",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_12"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_13 = BLOCKS.register("carpet_13",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_13"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_14 = BLOCKS.register("carpet_14",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_14"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_15 = BLOCKS.register("carpet_15",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_15"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_16 = BLOCKS.register("carpet_16",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_16"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_17 = BLOCKS.register("carpet_17",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_17"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_18 = BLOCKS.register("carpet_18",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_18"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_19 = BLOCKS.register("carpet_19",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_19"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_20 = BLOCKS.register("carpet_20",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_20"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> CARPET_21 = BLOCKS.register("carpet_21",
                        () -> new com.stardew.craft.block.decor.CarpetDecorBlock(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOL).sound(net.minecraft.world.level.block.SoundType.WOOL).noOcclusion().instabreak(), "stardewcraft:decor/carpet/carpet_21"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_HANGING_SMALL_A = BLOCKS.register("wall_hanging_small_a",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_hanging_small_a"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_NOTICE_BOARD_SMALL = BLOCKS.register("wall_notice_board_small",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_notice_board_small"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_HANGING_SMALL_B = BLOCKS.register("wall_hanging_small_b",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_hanging_small_b"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_BULLETIN_NOTES = BLOCKS.register("wall_bulletin_notes",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_bulletin_notes"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_HANGING_STRIP = BLOCKS.register("wall_hanging_strip",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_SWITCH_PANEL = BLOCKS.register("wall_switch_panel",
                        () -> new com.stardew.craft.block.decor.MapDecorWallSwitchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_HANGING_TRIPTYCH = BLOCKS.register("wall_hanging_triptych",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_hanging_triptych"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_HANGING_ORNAMENT = BLOCKS.register("wall_hanging_ornament",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_NOTICE_BOARD_MEDIUM = BLOCKS.register("wall_notice_board_medium",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_FRAME_WIDE = BLOCKS.register("wall_frame_wide",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_frame_wide"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_FRAME_DOUBLE = BLOCKS.register("wall_frame_double",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_frame_double"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_STICKY_NOTES = BLOCKS.register("wall_sticky_notes",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_POSTER_GAMEPAD = BLOCKS.register("wall_poster_gamepad",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_POSTER_DOLPHIN = BLOCKS.register("wall_poster_dolphin",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_POSTER_GAME_CHARACTER = BLOCKS.register("wall_poster_game_character",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_OUTLET = BLOCKS.register("wall_outlet",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_PHOTO_WHITE_HALL = BLOCKS.register("wall_photo_white_hall",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_photo_white_hall"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_BLACKSMITH_SIGN = BLOCKS.register("wall_blacksmith_sign",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_blacksmith_sign"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_BLACKSMITH_HAMMERS = BLOCKS.register("wall_blacksmith_hammers",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/wall_blacksmith_hammers"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SQUID_KID_PAINTING = BLOCKS.register("squid_kid_painting",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/wall_decor/common/squid_kid_painting"));

        public static final DeferredBlock<Block> RED_EAGLE = nightMarketPainting("red_eagle");
        public static final DeferredBlock<Block> PORTRAIT_OF_A_MERMAID = nightMarketPainting("portrait_of_a_mermaid");
        public static final DeferredBlock<Block> SOLAR_KINGDOM = nightMarketPainting("solar_kingdom");
        public static final DeferredBlock<Block> CLOUDS = nightMarketPainting("clouds");
        public static final DeferredBlock<Block> THOUSAND_YEARS_FROM_NOW = nightMarketPainting("1000_years_from_now");
        public static final DeferredBlock<Block> THREE_TREES = nightMarketPainting("three_trees");
        public static final DeferredBlock<Block> THE_SERPENT = nightMarketPainting("the_serpent");
        public static final DeferredBlock<Block> TROPICAL_FISH_173 = nightMarketPainting("tropical_fish_173");
        public static final DeferredBlock<Block> LAND_OF_CLAY = nightMarketPainting("land_of_clay");

        private static DeferredBlock<Block> nightMarketPainting(String name) {
                return BLOCKS.register(name,
                                () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                                .noOcclusion()
                                                .strength(0.6F, 1.0F),
                                                "stardewcraft:decor/wall_decor/night_market/" + name));
        }

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_WINDOW_1 = BLOCKS.register("shop_window_1",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                        .lightLevel(state -> 15)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/pierre_shop/window_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOP_WINDOW_2 = BLOCKS.register("shop_window_2",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                        .lightLevel(state -> 15)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:decor/pierre_shop/window_2"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FIREPLACE_LARGE = BLOCKS.register("fireplace_large",
                        () -> new com.stardew.craft.block.decor.LargeFireplaceDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.5F, 6.0F), "stardewcraft:decor/common/fireplace_1"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BONSAI_1 = BLOCKS.register("bonsai_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/bamboo_planter",
                                        0.0D, 0.0D, 0.0D, 16.0D, 32.0D, 16.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BONSAI_2 = BLOCKS.register("bonsai_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/hibiscus_planter",
                                        0.0D, 0.0D, 0.0D, 16.0D, 32.0D, 16.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BONSAI_3 = BLOCKS.register("bonsai_3",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/monstera_planter",
                                        0.0D, 0.0D, 0.0D, 16.0D, 32.0D, 16.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BONSAI_4 = BLOCKS.register("bonsai_4",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/snake_plant_planter",
                                        0.0D, 0.0D, 0.0D, 16.0D, 32.0D, 16.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BONSAI_5_WALL = BLOCKS.register("bonsai_5_wall",
                        () -> new com.stardew.craft.block.decor.DualMountHangingPlantBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BONSAI_BUSH = BLOCKS.register("bonsai_bush",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/canna_planter",
                                        0.0D, 0.0D, 0.0D, 16.0D, 32.0D, 16.0D));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> COMPUTER = BLOCKS.register("computer",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 2.0F), "stardewcraft:decor/common/computer_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SAILBOAT = BLOCKS.register("sailboat",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.2F), "stardewcraft:decor/common/sailboat_2"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DRESSER_1 = BLOCKS.register("dresser_1",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 1, 3));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DRESSER_2 = BLOCKS.register("dresser_2",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 2, 6));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DRESSER_3 = BLOCKS.register("dresser_3",
                        () -> new com.stardew.craft.block.utility.WardrobeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> REDWOOD_WARDROBE = BLOCKS.register("redwood_wardrobe",
                        () -> new com.stardew.craft.block.utility.WardrobeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALNUT_WARDROBE = BLOCKS.register("walnut_wardrobe",
                        () -> new com.stardew.craft.block.utility.WardrobeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OAK_WARDROBE = BLOCKS.register("oak_wardrobe",
                        () -> new com.stardew.craft.block.utility.WardrobeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OAK_BEDSIDE_CABINET = BLOCKS.register("oak_bedside_cabinet",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 1, 3));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> OAK_DRESSER = BLOCKS.register("oak_dresser",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 2, 6));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> REDWOOD_BEDSIDE_CABINET = BLOCKS.register("redwood_bedside_cabinet",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 1, 3));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> REDWOOD_DRESSER = BLOCKS.register("redwood_dresser",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 2, 6));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALNUT_BEDSIDE_CABINET = BLOCKS.register("walnut_bedside_cabinet",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 1, 3));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALNUT_DRESSER = BLOCKS.register("walnut_dresser",
                        () -> new com.stardew.craft.block.utility.FurnitureStorageBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), 2, 6));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WOOD_BUNDLE = BLOCKS.register("wood_bundle",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.2F), "stardewcraft:decor/common/wood_bundle_4"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BARREL = BLOCKS.register("barrel",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/common/barrel_5"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POTTED_PLANT_1 = BLOCKS.register("potted_plant_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/succulent_pot",
                                        4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POTTED_PLANT_2 = BLOCKS.register("potted_plant_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/shrub_pot",
                                        4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POTTED_PLANT_3 = BLOCKS.register("potted_plant_3",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/mini_cattail_pot",
                                        4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POTTED_PLANT_4 = BLOCKS.register("potted_plant_4",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/clover_pot",
                                        4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POTTED_PLANT_5 = BLOCKS.register("potted_plant_5",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/cactus_pot",
                                        4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POTTED_PLANT_6 = BLOCKS.register("potted_plant_6",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:decor/plants/sunflower_pot",
                                        4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHRINE = BLOCKS.register("shrine",
                        () -> new com.stardew.craft.block.decor.ShrineDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.5F, 6.0F), "stardewcraft:geo/block/decor/shrine_7.geo.json"));

        // ── 稻草人系列（0=基础 9 格半径，1-8=Rarecrow 8 格半径） ──
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_0 = BLOCKS.register("scarecrow_0",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/0", 0, 9));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_1 = BLOCKS.register("scarecrow_1",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/1", 1, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_2 = BLOCKS.register("scarecrow_2",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/2", 2, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_3 = BLOCKS.register("scarecrow_3",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/3", 3, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_4 = BLOCKS.register("scarecrow_4",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/4", 4, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_5 = BLOCKS.register("scarecrow_5",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/5", 5, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_6 = BLOCKS.register("scarecrow_6",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/6", 6, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_7 = BLOCKS.register("scarecrow_7",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/7", 7, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_8 = BLOCKS.register("scarecrow_8",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/8", 8, 8));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCARECROW_9 = BLOCKS.register("scarecrow_9",
                        () -> new com.stardew.craft.block.decor.ScarecrowBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:block/scarecrow/9", 9, 17));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JUNIMO_HUT_DECOR = BLOCKS.register("junimo_hut_decor",
                        () -> new com.stardew.craft.block.decor.JunimoHutDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 6.0F), "stardewcraft:geo/block/decor/junimo_hut_decor.geo.json"));

        // ── Giant Crops (3×3×2 GeckoLib block, spawned via GiantCropSpawner only) ──
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GIANT_CAULIFLOWER = BLOCKS.register("giant_cauliflower",
                        () -> new com.stardew.craft.block.crop.giant.GiantCauliflowerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)
                                        .noLootTable()));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GIANT_MELON = BLOCKS.register("giant_melon",
                        () -> new com.stardew.craft.block.crop.giant.GiantMelonBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)
                                        .noLootTable()));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GIANT_PUMPKIN = BLOCKS.register("giant_pumpkin",
                        () -> new com.stardew.craft.block.crop.giant.GiantPumpkinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)
                                        .noLootTable()));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GIANT_POWDERMELON = BLOCKS.register("giant_powdermelon",
                        () -> new com.stardew.craft.block.crop.giant.GiantPowdermelonBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F)
                                        .noLootTable()));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PILLAR = BLOCKS.register("pillar",
                        () -> new com.stardew.craft.block.decor.PillarGeoDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.5F, 6.0F), "stardewcraft:geo/block/decor/pillar_1.geo.json"));

                @SuppressWarnings("null")
                public static final DeferredBlock<Block> GALAXY_PILLAR = BLOCKS.register("galaxy_pillar",
                                () -> new com.stardew.craft.block.decor.PillarGeoDecorBlock(Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                                .sound(net.minecraft.world.level.block.SoundType.STONE)
                                                .noOcclusion()
                                                .strength(-1.0F, 3600000.0F)
                                                .noLootTable(), "stardewcraft:geo/block/decor/desert_galaxy_pillar.geo.json"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> RADIO = BLOCKS.register("radio",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/common/radio_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOK_STACK_1 = BLOCKS.register("book_stack_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/book_stack_2_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOK_STACK_2 = BLOCKS.register("book_stack_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/book_stack_2_2"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOK_STACK_3 = BLOCKS.register("book_stack_3",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/book_stack_2_3"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOKSHELF_WALL = BLOCKS.register("bookshelf_wall",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/wall_decor/common/bookshelf_wall_3_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOKSHELF_TALL_1 = BLOCKS.register("bookshelf_tall_1",
                        () -> new com.stardew.craft.block.decor.BookshelfGeoDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:geo/block/decor/bookshelf_3_2.geo.json"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOKSHELF_TALL_2 = BLOCKS.register("bookshelf_tall_2",
                        () -> new com.stardew.craft.block.decor.BookshelfGeoDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:geo/block/decor/bookshelf_3_3.geo.json"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALLPAPER_BLOCK = BLOCKS.register("wallpaper_block",
                        () -> new com.stardew.craft.block.utility.WallpaperBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .strength(0.8F, 1.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FLOORING_BLOCK = BLOCKS.register("flooring_block",
                        () -> new com.stardew.craft.block.utility.FlooringBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(1.0F, 1.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TV_1 = BLOCKS.register("tv_1",
                        () -> new com.stardew.craft.block.tv.TVBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/common/tv_1"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TV_2 = BLOCKS.register("tv_2",
                        () -> new com.stardew.craft.block.tv.TVBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/common/tv_2"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_PHOTO_FRAME = BLOCKS.register("wall_photo_frame",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_BONE_DECOR = BLOCKS.register("wall_bone_decor",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> KITCHEN_COUNTER = BLOCKS.register("kitchen_counter",
                        () -> new com.stardew.craft.block.utility.KitchenCounterBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TABLEWARE_PINK = BLOCKS.register("tableware_pink",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/common/tableware_pink"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TABLEWARE_BLUE = BLOCKS.register("tableware_blue",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/common/tableware_blue"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_KITCHEN_CABINET = BLOCKS.register("wall_kitchen_cabinet",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F)));

        // ──── Batch 3: 10 new furniture items ────
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JOJA_VENDING_MACHINE = BLOCKS.register("joja_vending_machine",
                        () -> new com.stardew.craft.block.decor.JojaVendingMachineBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PRIZE_TICKET_MACHINE = BLOCKS.register("prize_ticket_machine",
                        () -> new com.stardew.craft.block.decor.PrizeTicketMachineBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WHITE_TEACUP = BLOCKS.register("white_teacup",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/common/white_teacup"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> POOL_TABLE = BLOCKS.register("pool_table",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:decor/common/pool_table"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GLOBE = BLOCKS.register("globe",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 0.5F), "stardewcraft:decor/common/globe"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TELESCOPE = BLOCKS.register("telescope",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.5F, 0.5F), "stardewcraft:decor/common/telescope"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BEAR_FIGURINE = BLOCKS.register("bear_figurine",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/common/bear_figurine"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FISH_SHOP_COUNTER = BLOCKS.register("fish_shop_counter",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 1.0F), "stardewcraft:decor/common/fish_shop_counter"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOSPITAL_COUNTER = BLOCKS.register("hospital_counter",
                        () -> new com.stardew.craft.block.utility.HospitalCounterBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOSPITAL_POSTER_1 = BLOCKS.register("hospital_poster_1",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOSPITAL_POSTER_2 = BLOCKS.register("hospital_poster_2",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOSPITAL_POSTER_3 = BLOCKS.register("hospital_poster_3",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOSPITAL_POSTER_4 = BLOCKS.register("hospital_poster_4",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> HOSPITAL_POSTER_5 = BLOCKS.register("hospital_poster_5",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ELECTRIC_PIANO = BLOCKS.register("electric_piano",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/electric_piano"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WIZARD_CAULDRON = BLOCKS.register("wizard_cauldron",
                        () -> new com.stardew.craft.block.decor.WizardCauldronBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GUITAR = BLOCKS.register("guitar",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 0.8F), "stardewcraft:decor/common/guitar"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MICROWAVE = BLOCKS.register("microwave",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/microwave"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GRANDFATHER_CLOCK = BLOCKS.register("grandfather_clock",
                        () -> new com.stardew.craft.block.decor.GrandfatherClockBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/grandfather_clock_display"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DRUM_SET = BLOCKS.register("drum_set",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/drum_set",
                                        -10, 0, -12, 30, 24, 16));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINE_CABINET_1 = BLOCKS.register("wine_cabinet_1",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/wine_cabinet_1"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINE_CABINET_2 = BLOCKS.register("wine_cabinet_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/wine_cabinet_2"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WINE_CABINET_3 = BLOCKS.register("wine_cabinet_3",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/wine_cabinet_3"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ALEX_POSTER_1 = BLOCKS.register("alex_poster_1",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ALEX_POSTER_2 = BLOCKS.register("alex_poster_2",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ALEX_POSTER_3 = BLOCKS.register("alex_poster_3",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LEAH_POSTER_1 = BLOCKS.register("leah_poster_1",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LEAH_POSTER_2 = BLOCKS.register("leah_poster_2",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LEAH_POSTER_3 = BLOCKS.register("leah_poster_3",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PERIODIC_TABLE = BLOCKS.register("periodic_table",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/wall_decor/common/periodic_table"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MICROSCOPE = BLOCKS.register("microscope",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.5F, 0.8F), "stardewcraft:decor/common/microscope"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BEAKER = BLOCKS.register("beaker",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.NONE)
                                        .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                        .noOcclusion()
                                        .strength(0.3F, 0.3F), "stardewcraft:decor/common/beaker"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TRAIN_PHOTO = BLOCKS.register("train_photo",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/wall_decor/common/train_photo"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_PHOTO_1 = BLOCKS.register("wall_photo_1",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PAPER_CHECKLIST = BLOCKS.register("paper_checklist",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SINE_WAVE_POSTER = BLOCKS.register("sine_wave_poster",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SCATTERED_PAPERS = BLOCKS.register("scattered_papers",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.2F, 0.2F), "stardewcraft:decor/common/scattered_papers"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SEBASTIAN_POSTER_1 = BLOCKS.register("sebastian_poster_1",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SEBASTIAN_POSTER_2 = BLOCKS.register("sebastian_poster_2",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SEBASTIAN_POSTER_3 = BLOCKS.register("sebastian_poster_3",
                        () -> new com.stardew.craft.block.decor.MapDecorWallThinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SNOW)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F)));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOARD_GAME = BLOCKS.register("board_game",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/common/board_game"));

        // ── 新家具批次 ──────────────────────────────────────────────────────────

        // 墙饰
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_ADVENTURER_MAP = BLOCKS.register("wall_adventurer_map",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/wall_decor/common/wall_adventurer_map"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_BUOY = BLOCKS.register("wall_buoy",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/wall_decor/common/wall_buoy"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_FISH_SIGN = BLOCKS.register("wall_fish_sign",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/wall_decor/common/wall_fish_sign"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WALL_ISLAND_MAP = BLOCKS.register("wall_island_map",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/wall_decor/common/wall_island_map"));

        // 地面摆件
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BEAR_SKIN_RUG = BLOCKS.register("bear_skin_rug",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.DIRT)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.4F, 0.6F), "stardewcraft:decor/common/bear_skin_rug"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LEANING_SWORD = BLOCKS.register("leaning_sword",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.6F, 2.0F), "stardewcraft:decor/common/leaning_sword"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LEAH_SCULPTURE = BLOCKS.register("leah_sculpture",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.0F, 3.0F), "stardewcraft:decor/common/leah_sculpture"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EASEL = BLOCKS.register("easel",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/easel"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BLUE_BEAR_PLUSHIE = BLOCKS.register("blue_bear_plushie",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.3F, 0.5F), "stardewcraft:decor/common/blue_bear_plushie"));

        // ── 图腾柱 ────────────────────────────────────────────────────────────
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TOTEM_POLE_FARM = BLOCKS.register("totem_pole_farm",
                        () -> new com.stardew.craft.block.utility.totem.TotemPoleBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 6.0F),
                                        com.stardew.craft.block.utility.totem.TotemType.FARM,
                                        "stardewcraft:block/utility/totem_pole_farm"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TOTEM_POLE_MOUNTAIN = BLOCKS.register("totem_pole_mountain",
                        () -> new com.stardew.craft.block.utility.totem.TotemPoleBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 6.0F),
                                        com.stardew.craft.block.utility.totem.TotemType.MOUNTAIN,
                                        "stardewcraft:block/utility/totem_pole_mountain"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TOTEM_POLE_BEACH = BLOCKS.register("totem_pole_beach",
                        () -> new com.stardew.craft.block.utility.totem.TotemPoleBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 6.0F),
                                        com.stardew.craft.block.utility.totem.TotemType.BEACH,
                                        "stardewcraft:block/utility/totem_pole_beach"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> TOTEM_POLE_DESERT = BLOCKS.register("totem_pole_desert",
                        () -> new com.stardew.craft.block.utility.totem.TotemPoleBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 6.0F),
                                        com.stardew.craft.block.utility.totem.TotemType.DESERT,
                                        "stardewcraft:block/utility/totem_pole_desert"));

        // 黄土
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> YELLOW_DIRT = BLOCKS.register("yellow_dirt",
                        () -> new com.stardew.craft.block.nature.YellowDirtBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SAND)
                                        .sound(net.minecraft.world.level.block.SoundType.GRAVEL)
                                        .strength(0.5F)));

        // 远古斑点黄土（Artifact Spot）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ARTIFACT_SPOT_DIRT = BLOCKS.register("artifact_spot_dirt",
                        () -> new com.stardew.craft.block.nature.ArtifactSpotBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.SAND)
                                        .sound(net.minecraft.world.level.block.SoundType.GRAVEL)
                                        .strength(0.5F)));

        // 沙漠远古斑点（沙子变体）— 锄头锄后变回普通沙子
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_ARTIFACT_SPOT = BLOCKS.register("desert_artifact_spot",
                        () -> new com.stardew.craft.block.nature.ArtifactSpotBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SAND),
                                        net.minecraft.world.level.block.Blocks.SAND));

        // 海滩远古斑点（沙子变体）— 锄头锄后变回普通沙子
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BEACH_ARTIFACT_SPOT = BLOCKS.register("beach_artifact_spot",
                        () -> new com.stardew.craft.block.nature.ArtifactSpotBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SAND),
                                        net.minecraft.world.level.block.Blocks.SAND));

        // 传送触发方块（隐形，无碰撞，不可破坏）— 替代 Interaction 实体
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PORTAL_TRIGGER = BLOCKS.register("portal_trigger",
                        () -> new com.stardew.craft.block.portal.PortalTriggerBlock(Block.Properties.of()
                                        .noCollission()
                                        .noOcclusion()
                                        .noLootTable()
                                        .strength(-1.0f, 3600000.0f)
                                        .sound(net.minecraft.world.level.block.SoundType.EMPTY)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        // 室内装饰（自动 extension + 自动碰撞）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SAFE_BOX = BLOCKS.register("safe_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.2F, 4.0F), "stardewcraft:block/decor/common/safe_box"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BROKEN_SAFE_BOX = BLOCKS.register("broken_safe_box",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.0F, 3.0F), "stardewcraft:block/decor/common/broken_safe_box"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LOOM_MACHINE = BLOCKS.register("loom_machine",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.2F), "stardewcraft:block/decor/common/loom_machine"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOILER_DECOR = BLOCKS.register("boiler_decor",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.2F, 3.0F), "stardewcraft:block/decor/common/boiler_decor"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BROKEN_BOILER = BLOCKS.register("broken_boiler",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:block/decor/common/broken_boiler"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> YARN_CABINET = BLOCKS.register("yarn_cabinet",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.2F), "stardewcraft:block/decor/common/yarn_cabinet"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BROKEN_CHAIR = BLOCKS.register("broken_chair",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.4F, 0.8F), "stardewcraft:block/decor/common/broken_chair"));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> COAL_BASKET = BLOCKS.register("coal_basket",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.6F, 1.0F), "stardewcraft:block/decor/common/coal_basket"));

        // 家具目录 (SDV Furniture Catalogue)
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FURNITURE_CATALOGUE = BLOCKS.register("furniture_catalogue",
                        () -> new com.stardew.craft.block.decor.FurnitureCatalogueBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F)));

        // 公告栏 (SDV Bulletin Board)
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BULLETIN_BOARD = BLOCKS.register("bulletin_board",
                        () -> new com.stardew.craft.block.decor.BulletinBoardBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable(),
                                        "stardewcraft:block/decor/bulletin_board"));

        // 社区中心献祭卷轴 (SDV Junimo Note)
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JUNIMO_NOTE = BLOCKS.register("junimo_note",
                        () -> new com.stardew.craft.communitycenter.block.JunimoNoteBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.GOLD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()));

        // 社区中心星盘 (Star Plaque)
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> STAR_PLAQUE = BLOCKS.register("star_plaque",
                        () -> new com.stardew.craft.communitycenter.block.StarPlaqueBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()));

        // ── 加工台 (Workbenches) ──────────────────────────────────────────
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WOOD_WORKBENCH = BLOCKS.register("wood_workbench",
                        () -> new com.stardew.craft.block.utility.WoodWorkbenchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(2.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> STONE_WORKBENCH = BLOCKS.register("stone_workbench",
                        () -> new com.stardew.craft.block.utility.StoneWorkbenchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .strength(3.5F, 3.0F)));

        // ── 祝尼魔温室符文 (Junimo Greenhouse Rune) ──────────────────────────
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JUNIMO_GREENHOUSE_RUNE = BLOCKS.register("junimo_greenhouse_rune",
                        () -> new com.stardew.craft.block.utility.JunimoGreenhouseRuneBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.EMERALD)
                                        .sound(net.minecraft.world.level.block.SoundType.AMETHYST)
                                        .strength(-1.0F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .lightLevel(state -> 7)));

        // ── 装饰：农场常用 (Farm Common Decor) ──────────────────────────
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> STANDING_HOE = BLOCKS.register("standing_hoe",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/standing_hoe"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EMPTY_TERRACOTTA_POT = BLOCKS.register("empty_terracotta_pot",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_BROWN)
                                        .sound(net.minecraft.world.level.block.SoundType.DECORATED_POT)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/empty_terracotta_pot"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> RESERVOIR = BLOCKS.register("reservoir",
                        () -> new com.stardew.craft.block.decor.ReservoirBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F), "stardewcraft:decor/common/reservoir"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LONG_POTTED_PLANT = BLOCKS.register("long_potted_plant",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.AZALEA)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/long_potted_plant"));

        }
