package com.stardew.craft.forge.registry;

import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.item.ButterflyPowderItem;
import com.stardew.craft.item.SimpleStardewItem;
import com.stardew.craft.item.StardewSimpleBlockItem;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.20.1 item registrations. Keep this class limited to wiring. */
public final class ForgeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ForgeBootstrap.MOD_ID);

    public static final RegistryObject<Item> BUTTERFLY_POWDER = ITEMS.register(
            "butterfly_powder",
            () -> new ButterflyPowderItem(new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PALE_CYAN_PLASTER = registerBuildingBlock(ForgeBlocks.PALE_CYAN_PLASTER);
    public static final RegistryObject<Item> TEAL_PAINTED_TIMBER = registerBuildingBlock(ForgeBlocks.TEAL_PAINTED_TIMBER);
    public static final RegistryObject<Item> CREAM_SIDING = registerBuildingBlock(ForgeBlocks.CREAM_SIDING);
    public static final RegistryObject<Item> TERRACOTTA_ROOF_TILES = registerBuildingBlock(ForgeBlocks.TERRACOTTA_ROOF_TILES);
    public static final RegistryObject<Item> DARK_BROWN_ROOF_TILES = registerBuildingBlock(ForgeBlocks.DARK_BROWN_ROOF_TILES);
    public static final RegistryObject<Item> IVORY_SIDING = registerBuildingBlock(ForgeBlocks.IVORY_SIDING);
    public static final RegistryObject<Item> GRAY_GREEN_MASONRY = registerBuildingBlock(ForgeBlocks.GRAY_GREEN_MASONRY);
    public static final RegistryObject<Item> GRAY_VIOLET_ROOF_TILES = registerBuildingBlock(ForgeBlocks.GRAY_VIOLET_ROOF_TILES);
    public static final RegistryObject<Item> BRICK_RED_ROOF_TILES = registerBuildingBlock(ForgeBlocks.BRICK_RED_ROOF_TILES);
    public static final RegistryObject<Item> BLUE_GRAY_TIMBER = registerBuildingBlock(ForgeBlocks.BLUE_GRAY_TIMBER);
    public static final RegistryObject<Item> PALE_BLUE_SIDING = registerBuildingBlock(ForgeBlocks.PALE_BLUE_SIDING);
    public static final RegistryObject<Item> BLUE_PAINTED_PLANKS = registerBuildingBlock(ForgeBlocks.BLUE_PAINTED_PLANKS);

    public static final RegistryObject<Item> MINE_EARTH_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_EARTH_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_EARTH_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_EARTH_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_EARTH_DARK_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_EARTH_DARK_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_EARTH_DARK_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_EARTH_DARK_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_FROST_DARK_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_FROST_DARK_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_FROST_DARK_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_FROST_DARK_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_LAVA_DARK_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_LAVA_DARK_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_LAVA_DARK_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_LAVA_DARK_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_DESERT_DARK_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_DESERT_DARK_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_DESERT_DARK_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_DESERT_DARK_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_FROST_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_FROST_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_FROST_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_FROST_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_LAVA_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_LAVA_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_LAVA_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_LAVA_WALL, "stardewcraft.type.natural_rock");
    public static final RegistryObject<Item> MINE_DESERT_LOOSE_SOIL = registerNaturalBlock(
            ForgeBlocks.MINE_DESERT_LOOSE_SOIL, "stardewcraft.type.natural_ground");
    public static final RegistryObject<Item> MINE_DESERT_WALL = registerNaturalBlock(
            ForgeBlocks.MINE_DESERT_WALL, "stardewcraft.type.natural_rock");

    public static final RegistryObject<Item> MINE_BARRIER = registerBuildingBlock(ForgeBlocks.MINE_BARRIER);
    public static final RegistryObject<Item> PALE_BLUE_WINDOW_GLASS = registerBuildingBlock(ForgeBlocks.PALE_BLUE_WINDOW_GLASS);

    public static final RegistryObject<Item> OAK_ROOT = registerBuildingBlock(ForgeBlocks.OAK_ROOT);
    public static final RegistryObject<Item> OAK_LOG = registerBuildingBlock(ForgeBlocks.OAK_LOG);
    public static final RegistryObject<Item> OAK_BRANCH = registerBuildingBlock(ForgeBlocks.OAK_BRANCH);
    public static final RegistryObject<Item> MAPLE_ROOT = registerBuildingBlock(ForgeBlocks.MAPLE_ROOT);
    public static final RegistryObject<Item> MAPLE_LOG = registerBuildingBlock(ForgeBlocks.MAPLE_LOG);
    public static final RegistryObject<Item> MAPLE_BRANCH = registerBuildingBlock(ForgeBlocks.MAPLE_BRANCH);
    public static final RegistryObject<Item> PINE_ROOT = registerBuildingBlock(ForgeBlocks.PINE_ROOT);
    public static final RegistryObject<Item> PINE_LOG = registerBuildingBlock(ForgeBlocks.PINE_LOG);
    public static final RegistryObject<Item> PINE_BRANCH = registerBuildingBlock(ForgeBlocks.PINE_BRANCH);
    public static final RegistryObject<Item> MAHOGANY_ROOT = registerBuildingBlock(ForgeBlocks.MAHOGANY_ROOT);
    public static final RegistryObject<Item> MAHOGANY_LOG = registerBuildingBlock(ForgeBlocks.MAHOGANY_LOG);
    public static final RegistryObject<Item> MAHOGANY_BRANCH = registerBuildingBlock(ForgeBlocks.MAHOGANY_BRANCH);
    public static final RegistryObject<Item> MYSTIC_TREE_ROOT = registerBuildingBlock(ForgeBlocks.MYSTIC_TREE_ROOT);
    public static final RegistryObject<Item> MYSTIC_TREE_LOG = registerBuildingBlock(ForgeBlocks.MYSTIC_TREE_LOG);
    public static final RegistryObject<Item> MYSTIC_TREE_BRANCH = registerBuildingBlock(ForgeBlocks.MYSTIC_TREE_BRANCH);

    public static final RegistryObject<Item> ECTOPLASM = registerSimple("ectoplasm", "stardewcraft.type.quest", -1);
    public static final RegistryObject<Item> PRISMATIC_JELLY = registerSimple("prismatic_jelly", "stardewcraft.type.quest", -1);
    public static final RegistryObject<Item> EXPLOSIVE_AMMO = registerSimple("explosive_ammo", "stardewcraft.type.resource", 20);
    public static final RegistryObject<Item> CLAY = registerSimple("clay", "stardewcraft.type.resource", 20);
    public static final RegistryObject<Item> FIBER = registerSimple("fiber", "stardewcraft.type.resource", 1);
    public static final RegistryObject<Item> HAY = registerSimple("hay", "stardewcraft.type.resource", 0);
    public static final RegistryObject<Item> WOOD_NORMAL = registerSimple("wood_normal", "stardewcraft.type.resource", 2);
    public static final RegistryObject<Item> WOOD_HARD = registerSimple("wood_hard", "stardewcraft.type.resource", 15);

    public static final RegistryObject<Item> MILK_PAIL = ITEMS.register(
            "milk_pail",
            () -> new SimpleStardewItem("stardewcraft.type.tool", -1, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> SHEARS = ITEMS.register(
            "shears",
            () -> new SimpleStardewItem("stardewcraft.type.tool", -1, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> WALLPAPER_ICON = ITEMS.register(
            "wallpaper_icon",
            () -> new SimpleStardewItem("stardewcraft.type.hidden", -1, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> FLOORING_ICON = ITEMS.register(
            "flooring_icon",
            () -> new SimpleStardewItem("stardewcraft.type.hidden", -1, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> BAIT = ITEMS.register(
            "bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MAGNET = ITEMS.register(
            "magnet",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 15, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> WILD_BAIT = ITEMS.register(
            "wild_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 15, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MAGIC_BAIT = ITEMS.register(
            "magic_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DELUXE_BAIT = ITEMS.register(
            "deluxe_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CHALLENGE_BAIT = ITEMS.register(
            "challenge_bait",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SPINNER = ITEMS.register(
            "spinner",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> DRESSED_SPINNER = ITEMS.register(
            "dressed_spinner",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 500, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> TRAP_BOBBER = ITEMS.register(
            "trap_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 200, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> CORK_BOBBER = ITEMS.register(
            "cork_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> LEAD_BOBBER = ITEMS.register(
            "lead_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 150, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> TREASURE_HUNTER = ITEMS.register(
            "treasure_hunter",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> BARBED_HOOK = ITEMS.register(
            "barbed_hook",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 500, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> CURIOSITY_LURE = ITEMS.register(
            "curiosity_lure",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 500, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> QUALITY_BOBBER = ITEMS.register(
            "quality_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 300, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> SONAR_BOBBER = ITEMS.register(
            "sonar_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.fishing", 250, new Item.Properties().stacksTo(1).durability(20))
    );

    public static final RegistryObject<Item> TEA_SET = ITEMS.register(
            "tea_set",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> OAK_RESIN = ITEMS.register(
            "oak_resin",
            () -> new SimpleStardewItem("stardewcraft.type.artisan_goods", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PINE_TAR = ITEMS.register(
            "pine_tar",
            () -> new SimpleStardewItem("stardewcraft.type.artisan_goods", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_ANIMAL_CRACKER = ITEMS.register(
            "golden_animal_cracker",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 1000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PRIZE_TICKET = ITEMS.register(
            "prize_ticket",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 0, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CALICO_EGG = ITEMS.register(
            "calico_egg",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 0, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_TAG = ITEMS.register(
            "golden_tag",
            () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MYSTERY_BOX = ITEMS.register(
            "mystery_box",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 0, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_MYSTERY_BOX = ITEMS.register(
            "golden_mystery_box",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 0, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> TREASURE_CHEST = ITEMS.register(
            "treasure_chest",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 5000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PEARL = ITEMS.register(
            "pearl",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 2500, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> COPPER_ORE = ITEMS.register(
            "copper_ore",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 5, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> IRON_ORE = ITEMS.register(
            "iron_ore",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 10, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLD_ORE = ITEMS.register(
            "gold_ore",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 25, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> IRIDIUM_ORE = ITEMS.register(
            "iridium_ore",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> COPPER_BAR = ITEMS.register(
            "copper_bar",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 60, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> IRON_BAR = ITEMS.register(
            "iron_bar",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 120, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLD_BAR = ITEMS.register(
            "gold_bar",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 250, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> IRIDIUM_BAR = ITEMS.register(
            "iridium_bar",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 1000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> COAL = ITEMS.register(
            "coal",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 15, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> STONE = ITEMS.register(
            "stone",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 2, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> REFINED_QUARTZ = ITEMS.register(
            "refined_quartz",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BATTERY_PACK = ITEMS.register(
            "battery_pack",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 500, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CINDER_SHARD = ITEMS.register(
            "cinder_shard",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RADIOACTIVE_ORE = ITEMS.register(
            "radioactive_ore",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 300, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RADIOACTIVE_BAR = ITEMS.register(
            "radioactive_bar",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 3000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BOUQUET = ITEMS.register(
            "bouquet",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> WILTED_BOUQUET = ITEMS.register(
            "wilted_bouquet",
            () -> new SimpleStardewItem("stardewcraft.type.misc", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GEODE = ITEMS.register(
            "geode",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> FROZEN_GEODE = ITEMS.register(
            "frozen_geode",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MAGMA_GEODE = ITEMS.register(
            "magma_geode",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> OMNI_GEODE = ITEMS.register(
            "omni_geode",
            () -> new SimpleStardewItem("stardewcraft.type.resource", -1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ARTIFACT_TROVE = ITEMS.register(
            "artifact_trove",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> EMERALD = ITEMS.register(
            "emerald",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> AQUAMARINE = ITEMS.register(
            "aquamarine",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 180, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RUBY = ITEMS.register(
            "ruby",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> AMETHYST = ITEMS.register(
            "amethyst",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> TOPAZ = ITEMS.register(
            "topaz",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 80, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> JADE = ITEMS.register(
            "jade",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DIAMOND = ITEMS.register(
            "diamond",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 750, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PRISMATIC_SHARD = ITEMS.register(
            "prismatic_shard",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 2000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> TIGERSEYE = ITEMS.register(
            "tigerseye",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 275, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> OPAL = ITEMS.register(
            "opal",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> FIRE_OPAL = ITEMS.register(
            "fire_opal",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 350, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ALAMITE = ITEMS.register(
            "alamite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BIXITE = ITEMS.register(
            "bixite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 300, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BARYTE = ITEMS.register(
            "baryte",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> AERINITE = ITEMS.register(
            "aerinite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 125, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CALCITE = ITEMS.register(
            "calcite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 75, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DOLOMITE = ITEMS.register(
            "dolomite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 300, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ESPERITE = ITEMS.register(
            "esperite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> FLUORAPATITE = ITEMS.register(
            "fluorapatite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GEMINITE = ITEMS.register(
            "geminite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> HELVITE = ITEMS.register(
            "helvite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 450, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> JAMBORITE = ITEMS.register(
            "jamborite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> JAGOITE = ITEMS.register(
            "jagoite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 115, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> KYANITE = ITEMS.register(
            "kyanite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> LUNARITE = ITEMS.register(
            "lunarite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MALACHITE = ITEMS.register(
            "malachite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> NEPTUNITE = ITEMS.register(
            "neptunite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 400, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> LEMON_STONE = ITEMS.register(
            "lemon_stone",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> NEKOITE = ITEMS.register(
            "nekoite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 80, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ORPIMENT = ITEMS.register(
            "orpiment",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 80, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PETRIFIED_SLIME = ITEMS.register(
            "petrified_slime",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 120, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> THUNDER_EGG = ITEMS.register(
            "thunder_egg",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PYRITE = ITEMS.register(
            "pyrite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 120, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> OCEAN_STONE = ITEMS.register(
            "ocean_stone",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 220, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GHOST_CRYSTAL = ITEMS.register(
            "ghost_crystal",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> JASPER = ITEMS.register(
            "jasper",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CELESTINE = ITEMS.register(
            "celestine",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 125, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MARBLE = ITEMS.register(
            "marble",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 110, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SANDSTONE = ITEMS.register(
            "sandstone",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 60, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GRANITE = ITEMS.register(
            "granite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 75, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BASALT = ITEMS.register(
            "basalt",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 175, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> LIMESTONE_MINERAL = ITEMS.register(
            "limestone_mineral",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 15, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SOAPSTONE = ITEMS.register(
            "soapstone",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 120, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> HEMATITE = ITEMS.register(
            "hematite",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MUDSTONE = ITEMS.register(
            "mudstone",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 25, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> OBSIDIAN = ITEMS.register(
            "obsidian",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SLATE = ITEMS.register(
            "slate",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 85, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> FAIRY_STONE = ITEMS.register(
            "fairy_stone",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 250, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> STAR_SHARDS = ITEMS.register(
            "star_shards",
            () -> new SimpleStardewItem("stardewcraft.type.mineral", 500, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DWARF_SCROLL_I = ITEMS.register(
            "dwarf_scroll_i",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DWARF_SCROLL_II = ITEMS.register(
            "dwarf_scroll_ii",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DWARF_SCROLL_III = ITEMS.register(
            "dwarf_scroll_iii",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DWARF_SCROLL_IV = ITEMS.register(
            "dwarf_scroll_iv",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ORNATE_NECKLACE = ITEMS.register(
            "ornate_necklace",
            () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> LOST_AXE = ITEMS.register(
            "lost_axe",
            () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> BLACKBERRY_BASKET = ITEMS.register(
            "blackberry_basket",
            () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> CHIPPED_AMPHORA = ITEMS.register(
            "chipped_amphora",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 40, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ARROWHEAD = ITEMS.register(
            "arrowhead",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 40, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ANCIENT_DOLL = ITEMS.register(
            "ancient_doll",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 60, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ELVISH_JEWELRY = ITEMS.register(
            "elvish_jewelry",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CHEWING_STICK = ITEMS.register(
            "chewing_stick",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ORNAMENTAL_FAN = ITEMS.register(
            "ornamental_fan",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 300, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RARE_DISC = ITEMS.register(
            "rare_disc",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 300, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ANCIENT_SWORD = ITEMS.register(
            "ancient_sword",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RUSTY_SPOON = ITEMS.register(
            "rusty_spoon",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 25, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RUSTY_SPUR = ITEMS.register(
            "rusty_spur",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 25, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> RUSTY_COG = ITEMS.register(
            "rusty_cog",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 25, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> CHICKEN_STATUE = ITEMS.register(
            "chicken_statue",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ANCIENT_SEED = ITEMS.register(
            "ancient_seed",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 5, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_TOOL = ITEMS.register(
            "prehistoric_tool",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DRIED_STARFISH = ITEMS.register(
            "dried_starfish",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 40, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ANCHOR = ITEMS.register(
            "anchor",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GLASS_SHARDS = ITEMS.register(
            "glass_shards",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 20, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BONE_FLUTE = ITEMS.register(
            "bone_flute",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_HANDAXE = ITEMS.register(
            "prehistoric_handaxe",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DWARVISH_HELM = ITEMS.register(
            "dwarvish_helm",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> DWARF_GADGET = ITEMS.register(
            "dwarf_gadget",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 200, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ANCIENT_DRUM = ITEMS.register(
            "ancient_drum",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_MASK = ITEMS.register(
            "golden_mask",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 500, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_RELIC = ITEMS.register(
            "golden_relic",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 250, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_BOBBER = ITEMS.register(
            "golden_bobber",
            () -> new SimpleStardewItem("stardewcraft.type.quest", 0, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> STRANGE_DOLL_GREEN = ITEMS.register(
            "strange_doll_green",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 1000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> STRANGE_DOLL_YELLOW = ITEMS.register(
            "strange_doll_yellow",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 1000, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MUMMIFIED_BAT = ITEMS.register(
            "mummified_bat",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> FOSSILIZED_LEG = ITEMS.register(
            "fossilized_leg",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> FOSSILIZED_RIBS = ITEMS.register(
            "fossilized_ribs",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SNAKE_VERTEBRAE = ITEMS.register(
            "snake_vertebrae",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> GOLDEN_WALNUT = ITEMS.register(
            "golden_walnut",
            () -> new SimpleStardewItem("stardewcraft.type.special", -1, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_SCAPULA = ITEMS.register(
            "prehistoric_scapula",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_TIBIA = ITEMS.register(
            "prehistoric_tibia",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_SKULL = ITEMS.register(
            "prehistoric_skull",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SKELETAL_HAND = ITEMS.register(
            "skeletal_hand",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_RIB = ITEMS.register(
            "prehistoric_rib",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PREHISTORIC_VERTEBRA = ITEMS.register(
            "prehistoric_vertebra",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SKELETAL_TAIL = ITEMS.register(
            "skeletal_tail",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> NAUTILUS_FOSSIL = ITEMS.register(
            "nautilus_fossil",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 80, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> AMPHIBIAN_FOSSIL = ITEMS.register(
            "amphibian_fossil",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 150, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> PALM_FOSSIL = ITEMS.register(
            "palm_fossil",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 100, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> TRILOBITE = ITEMS.register(
            "trilobite",
            () -> new SimpleStardewItem("stardewcraft.type.artifact", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> TEA_LEAVES = ITEMS.register(
            "tea_leaves",
            () -> new SimpleStardewItem("stardewcraft.type.crop", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SLIME_ITEM = ITEMS.register(
            "slime_item",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 5, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BAT_WING = ITEMS.register(
            "bat_wing",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 15, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> SOLAR_ESSENCE = ITEMS.register(
            "solar_essence",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 40, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> VOID_ESSENCE = ITEMS.register(
            "void_essence",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 50, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BONE_FRAGMENT = ITEMS.register(
            "bone_fragment",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 12, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> BUG_MEAT = ITEMS.register(
            "bug_meat",
            () -> new SimpleStardewItem("stardewcraft.type.monster_loot", 8, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> MOSS = ITEMS.register(
            "moss",
            () -> new SimpleStardewItem("stardewcraft.type.resource", 5, new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> JUNIMO_BUNDLE = ITEMS.register(
            "junimo_bundle",
            () -> new SimpleStardewItem("stardewcraft.type.hidden", -1, new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<Item> JUNIMO_STAR = ITEMS.register(
            "junimo_star",
            () -> new SimpleStardewItem("stardewcraft.type.hidden", -1, new Item.Properties().stacksTo(1))
    );

    private static RegistryObject<Item> registerSimple(String id, String typeKey, int sellPrice) {
        return ITEMS.register(id, () -> new SimpleStardewItem(typeKey, sellPrice, new Item.Properties().stacksTo(999)));
    }

    private static RegistryObject<Item> registerBuildingBlock(RegistryObject<? extends net.minecraft.world.level.block.Block> block) {
        String id = block.getId().getPath();
        return ITEMS.register(id, () -> new StardewSimpleBlockItem(block.get(),
                "stardewcraft.type.building", -1, new Item.Properties().stacksTo(999)));
    }

    private static RegistryObject<Item> registerNaturalBlock(
            RegistryObject<? extends net.minecraft.world.level.block.Block> block, String typeKey) {
        return registerBlockItem(block, typeKey);
    }

    private static RegistryObject<Item> registerBlockItem(
            RegistryObject<? extends net.minecraft.world.level.block.Block> block, String typeKey) {
        String id = block.getId().getPath();
        return ITEMS.register(id, () -> new StardewSimpleBlockItem(block.get(),
                typeKey, -1, new Item.Properties().stacksTo(999)));
    }

    private ForgeItems() {
    }
}
