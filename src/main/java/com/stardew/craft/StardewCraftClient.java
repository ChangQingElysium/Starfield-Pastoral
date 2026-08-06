package com.stardew.craft;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.client.render.FallenOakTreeRenderer;
import com.stardew.craft.client.ModItemProperties;
import com.stardew.craft.client.ModRenderLayers;
import com.stardew.craft.client.render.FertilizerOverlayRenderer;
import com.stardew.craft.client.render.TVScreenOverlayRenderer;
import com.stardew.craft.client.renderer.SprinklerOverlayRenderer;
import com.stardew.craft.client.DebugKeybindsTick;
import com.stardew.craft.client.renderer.entity.SofaSeatEntityRenderer;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.block.utility.CushionBlock;
import com.stardew.craft.block.utility.OfficeChair2Block;
import com.stardew.craft.block.utility.DyeableChairBlock;
import com.stardew.craft.block.utility.OfficeChair2TopRenderBlock;
import com.stardew.craft.block.utility.OfficeStoolBlock;
import com.stardew.craft.block.utility.OfficeStoolTopRenderBlock;
import com.stardew.craft.block.utility.OakTableBlock;
import com.stardew.craft.block.utility.SofaBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = StardewCraft.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class StardewCraftClient {
    private static final int STARDEW_GRASS_SPRING = 0xB7E36A;
    private static final int STARDEW_GRASS_SUMMER = 0x6CCF43;
    private static final int STARDEW_GRASS_FALL = 0xD8A53A;
    private static final int STARDEW_GRASS_WINTER = 0xC7DBEC;
    private static final int STARDEW_LEAF_WINTER = 0xEDF7FF;
    private static final int STARDEW_YELLOW_DIRT_WINTER = 0xF8FCFF;

    public StardewCraftClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (minecraft, parent) -> new com.stardew.craft.client.gui.StardewSettingsScreen(parent));
        // Weapon shader registration — client only (moved from StardewCraft main class)
        modEventBus.addListener(com.stardew.craft.client.weapon.WeaponShaderRegistry::onRegisterShadersSafe);
    NeoForge.EVENT_BUS.register(FertilizerOverlayRenderer.class);
    NeoForge.EVENT_BUS.register(TVScreenOverlayRenderer.class);
    NeoForge.EVENT_BUS.register(SprinklerOverlayRenderer.class);
    if (!FMLLoader.isProduction()) {
        NeoForge.EVENT_BUS.register(DebugKeybindsTick.class);
    }
    }

    @SubscribeEvent
    @SuppressWarnings({ "deprecation", "null" })
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        StardewCraft.LOGGER.info("HELLO FROM CLIENT SETUP");
        StardewCraft.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        event.enqueueWork(() -> {
            ModRenderLayers.registerCutout(List.of(
                ModBlocks.FRIENDSHIP_DOOR.get(),
                ModBlocks.WILD_WEEDS.get(),
                ModBlocks.PASTURE_GRASS.get(),
                ModBlocks.BLUE_PASTURE_GRASS.get(),
                ModBlocks.CHERRY_SAPLING.get(),
                ModBlocks.APRICOT_SAPLING.get(),
                ModBlocks.ORANGE_SAPLING.get(),
                ModBlocks.PEACH_SAPLING.get(),
                ModBlocks.POMEGRANATE_SAPLING.get(),
                ModBlocks.APPLE_SAPLING.get(),
                ModBlocks.BANANA_SAPLING.get(),
                ModBlocks.MANGO_SAPLING.get(),
                ModBlocks.EGG_FESTIVAL_EGG.get(),
                ModBlocks.WATER_LANTERN.get(),
                // Forage blocks (cross model, cutout render)
                ModBlocks.FORAGE_WILD_HORSERADISH.get(),
                ModBlocks.FORAGE_DAFFODIL.get(),
                ModBlocks.FORAGE_LEEK.get(),
                ModBlocks.FORAGE_DANDELION.get(),
                ModBlocks.FORAGE_SPRING_ONION.get(),
                ModBlocks.FORAGE_SPICE_BERRY.get(),
                ModBlocks.FORAGE_SWEET_PEA.get(),
                ModBlocks.FORAGE_GRAPE.get(),
                ModBlocks.FORAGE_FIDDLEHEAD_FERN.get(),
                ModBlocks.FORAGE_WILD_PLUM.get(),
                ModBlocks.FORAGE_HAZELNUT.get(),
                ModBlocks.FORAGE_BLACKBERRY.get(),
                ModBlocks.FORAGE_WINTER_ROOT.get(),
                ModBlocks.FORAGE_CRYSTAL_FRUIT.get(),
                ModBlocks.FORAGE_CROCUS.get(),
                ModBlocks.FORAGE_HOLLY.get(),
                ModBlocks.FORAGE_CAVE_CARROT.get(),
                ModBlocks.FORAGE_NAUTILUS_SHELL.get(),
                ModBlocks.FORAGE_CORAL.get(),
                ModBlocks.FORAGE_RAINBOW_SHELL.get(),
                ModBlocks.FORAGE_SEA_URCHIN.get(),
                ModBlocks.FORAGE_COCONUT.get(),
                ModBlocks.FORAGE_CACTUS_FRUIT.get(),
                ModBlocks.FORAGE_COMMON_MUSHROOM.get(),
                ModBlocks.FORAGE_RED_MUSHROOM.get(),
                ModBlocks.FORAGE_PURPLE_MUSHROOM.get(),
                ModBlocks.FORAGE_MOREL.get(),
                ModBlocks.FORAGE_CHANTERELLE.get(),
                ModBlocks.FORAGE_MAGMA_CAP.get(),
                // Farm Cave fruit forage (Fruit Bats)
                ModBlocks.FORAGE_SALMONBERRY.get(),
                ModBlocks.FORAGE_APPLE.get(),
                ModBlocks.FORAGE_APRICOT.get(),
                ModBlocks.FORAGE_ORANGE.get(),
                ModBlocks.FORAGE_PEACH.get(),
                ModBlocks.FORAGE_POMEGRANATE.get(),
                ModBlocks.FORAGE_MANGO.get(),
                ModBlocks.WILD_OAK_TRUNK0.get(),
                ModBlocks.WILD_OAK_TRUNK1.get(),
                ModBlocks.WILD_OAK_BRANCH1.get(),
                ModBlocks.WILD_OAK_BRANCH2.get(),
                ModBlocks.WILD_MAPLE_TRUNK0.get(),
                ModBlocks.WILD_MAPLE_TRUNK1.get(),
                ModBlocks.WILD_MAPLE_BRANCH1.get(),
                ModBlocks.WILD_MAPLE_BRANCH2.get(),
                ModBlocks.WILD_PINE_TRUNK0.get(),
                ModBlocks.WILD_PINE_TRUNK1.get(),
                ModBlocks.WILD_PINE_BRANCH1.get(),
                ModBlocks.WILD_PINE_BRANCH2.get(),
                ModBlocks.WILD_MAHOGANY_TRUNK0.get(),
                ModBlocks.WILD_MAHOGANY_TRUNK1.get(),
                ModBlocks.WILD_MAHOGANY_BRANCH1.get(),
                ModBlocks.WILD_MAHOGANY_BRANCH2.get(),
                ModBlocks.WILD_MYSTIC_TREE_TRUNK0.get(),
                ModBlocks.WILD_MYSTIC_TREE_TRUNK1.get(),
                ModBlocks.WILD_MYSTIC_TREE_BRANCH1.get(),
                ModBlocks.WILD_MYSTIC_TREE_BRANCH2.get(),
                ModBlocks.OAK_ROOT.get(),
                ModBlocks.OAK_BRANCH.get(),
                ModBlocks.MAPLE_ROOT.get(),
                ModBlocks.MAPLE_BRANCH.get(),
                ModBlocks.PINE_ROOT.get(),
                ModBlocks.PINE_BRANCH.get(),
                ModBlocks.MAHOGANY_ROOT.get(),
                ModBlocks.MAHOGANY_BRANCH.get(),
                ModBlocks.MYSTIC_TREE_ROOT.get(),
                ModBlocks.MYSTIC_TREE_BRANCH.get(),
                ModBlocks.QUARTZ.get(),
                ModBlocks.EARTH_CRYSTAL.get(),
                ModBlocks.FROZEN_TEAR.get(),
                ModBlocks.FIRE_QUARTZ.get(),
                ModBlocks.WILD_OAK_SAPLING0.get(),
                ModBlocks.WILD_OAK_SAPLING1.get(),
                ModBlocks.WILD_MAPLE_SAPLING0.get(),
                ModBlocks.WILD_MAPLE_SAPLING1.get(),
                ModBlocks.WILD_PINE_SAPLING0.get(),
                ModBlocks.WILD_PINE_SAPLING1.get(),
                ModBlocks.WILD_MAHOGANY_SAPLING0.get(),
                ModBlocks.WILD_MAHOGANY_SAPLING1.get(),
                ModBlocks.WILD_MYSTIC_TREE_SAPLING0.get(),
                ModBlocks.WILD_MYSTIC_TREE_SAPLING1.get(),
                ModBlocks.AMARANTH_CROP.get(),
                ModBlocks.ANCIENT_FRUIT_CROP.get(),
                ModBlocks.ARTICHOKE_CROP.get(),
                ModBlocks.BEET_CROP.get(),
                ModBlocks.BLUE_JAZZ_CROP.get(),
                ModBlocks.BLUEBERRY_CROP.get(),
                ModBlocks.BOK_CHOY_CROP.get(),
                ModBlocks.BROCCOLI_CROP.get(),
                ModBlocks.CARROT_CROP.get(),
                ModBlocks.CAULIFLOWER_CROP.get(),
                ModBlocks.COFFEE_BEAN_CROP.get(),
                ModBlocks.CORN_CROP.get(),
                ModBlocks.CRANBERRY_CROP.get(),
                ModBlocks.EGGPLANT_CROP.get(),
                ModBlocks.FAIRY_ROSE_CROP.get(),
                ModBlocks.GARLIC_CROP.get(),
                ModBlocks.GRAPE_CROP.get(),
                ModBlocks.GREEN_BEAN_CROP.get(),
                ModBlocks.HOPS_CROP.get(),
                ModBlocks.HOT_PEPPER_CROP.get(),
                ModBlocks.KALE_CROP.get(),
                ModBlocks.MELON_CROP.get(),
                ModBlocks.PARSNIP_CROP.get(),
                ModBlocks.POPPY_CROP.get(),
                ModBlocks.POTATO_CROP.get(),
                ModBlocks.POWDER_MELON_CROP.get(),
                ModBlocks.PUMPKIN_CROP.get(),
                ModBlocks.RADISH_CROP.get(),
                ModBlocks.RED_CABBAGE_CROP.get(),
                ModBlocks.RHUBARB_CROP.get(),
                ModBlocks.RICE_CROP.get(),
                ModBlocks.STARFRUIT_CROP.get(),
                ModBlocks.STRAWBERRY_CROP.get(),
                ModBlocks.SUMMER_SPANGLE_CROP.get(),
                ModBlocks.SUMMER_SQUASH_CROP.get(),
                ModBlocks.SUNFLOWER_CROP.get(),
                ModBlocks.TOMATO_CROP.get(),
                ModBlocks.TULIP_CROP.get(),
                ModBlocks.WHEAT_CROP.get(),
                ModBlocks.YAM_CROP.get(),
                ModBlocks.SPRING_WILD_SEED_CROP.get(),
                ModBlocks.SUMMER_WILD_SEED_CROP.get(),
                ModBlocks.FALL_WILD_SEED_CROP.get(),
                ModBlocks.WINTER_WILD_SEED_CROP.get(),
                ModBlocks.FIBER_CROP.get(),
                ModBlocks.DEAD_CROP.get(),
                ModBlocks.WALL_HANGING_SMALL_A.get(),
                ModBlocks.WALL_NOTICE_BOARD_SMALL.get(),
                ModBlocks.WALL_HANGING_SMALL_B.get(),
                ModBlocks.WALL_BULLETIN_NOTES.get(),
                ModBlocks.WALL_HANGING_STRIP.get(),
                ModBlocks.WALL_SWITCH_PANEL.get(),
                ModBlocks.WALL_HANGING_TRIPTYCH.get(),
                ModBlocks.WALL_HANGING_ORNAMENT.get(),
                ModBlocks.WALL_NOTICE_BOARD_MEDIUM.get(),
                ModBlocks.WALL_FRAME_WIDE.get(),
                ModBlocks.WALL_FRAME_DOUBLE.get(),
                ModBlocks.WALL_STICKY_NOTES.get(),
                ModBlocks.WALL_POSTER_GAMEPAD.get(),
                ModBlocks.WALL_POSTER_DOLPHIN.get(),
                ModBlocks.WALL_POSTER_GAME_CHARACTER.get(),
                ModBlocks.WALL_OUTLET.get(),
                ModBlocks.WALL_PHOTO_WHITE_HALL.get(),
                ModBlocks.SQUID_KID_PAINTING.get(),
                ModBlocks.RED_EAGLE.get(),
                ModBlocks.PORTRAIT_OF_A_MERMAID.get(),
                ModBlocks.SOLAR_KINGDOM.get(),
                ModBlocks.CLOUDS.get(),
                ModBlocks.THOUSAND_YEARS_FROM_NOW.get(),
                ModBlocks.THREE_TREES.get(),
                ModBlocks.THE_SERPENT.get(),
                ModBlocks.TROPICAL_FISH_173.get(),
                ModBlocks.LAND_OF_CLAY.get(),
                ModBlocks.SHOP_WINDOW_1.get(),
                ModBlocks.SHOP_WINDOW_2.get(),
                ModBlocks.BONSAI_1.get(),
                ModBlocks.BONSAI_2.get(),
                ModBlocks.BONSAI_3.get(),
                ModBlocks.BONSAI_4.get(),
                ModBlocks.BONSAI_5_WALL.get(),
                ModBlocks.BONSAI_BUSH.get(),
                ModBlocks.COMPUTER.get(),
                ModBlocks.SAILBOAT.get(),
                ModBlocks.DRESSER_1.get(),
                ModBlocks.DRESSER_2.get(),
                ModBlocks.DRESSER_3.get(),
                ModBlocks.REDWOOD_WARDROBE.get(),
                ModBlocks.WALNUT_WARDROBE.get(),
                ModBlocks.OAK_WARDROBE.get(),
                ModBlocks.OAK_BEDSIDE_CABINET.get(),
                ModBlocks.OAK_DRESSER.get(),
                ModBlocks.REDWOOD_BEDSIDE_CABINET.get(),
                ModBlocks.REDWOOD_DRESSER.get(),
                ModBlocks.WALNUT_BEDSIDE_CABINET.get(),
                ModBlocks.WALNUT_DRESSER.get(),
                ModBlocks.POTTED_PLANT_1.get(),
                ModBlocks.POTTED_PLANT_2.get(),
                ModBlocks.POTTED_PLANT_3.get(),
                ModBlocks.POTTED_PLANT_4.get(),
                ModBlocks.POTTED_PLANT_5.get(),
                ModBlocks.POTTED_PLANT_6.get(),
                ModBlocks.RADIO.get(),
                ModBlocks.BOOK_STACK_1.get(),
                ModBlocks.BOOK_STACK_2.get(),
                ModBlocks.BOOK_STACK_3.get(),
                ModBlocks.BOOKSHELF_WALL.get(),
                ModBlocks.SOFA.get(),
                ModBlocks.STOOL.get(),
                ModBlocks.IRON_STOOL.get(),
                ModBlocks.DINING_CHAIR_WOOD.get(),
                ModBlocks.DINING_CHAIR_IRON.get(),
                ModBlocks.CHAIR_1.get(),
                ModBlocks.CHAIR_2.get(),
                ModBlocks.CHAIR_3.get(),
                ModBlocks.CUSHION.get(),
                ModBlocks.OFFICE_STOOL.get(),
                ModBlocks.OFFICE_CHAIR_2.get(),
                ModBlocks.LIGHT_1.get(),
                ModBlocks.LIGHT_2.get(),
                ModBlocks.LIGHT_3.get(),
                ModBlocks.LIGHT_4.get(),
                ModBlocks.LIGHT_5.get(),
                ModBlocks.LIGHT_6.get(),
                ModBlocks.LIGHT_7.get(),
                ModBlocks.OAK_TABLE.get(),
                ModBlocks.SPRUCE_TABLE.get(),
                ModBlocks.BIRCH_TABLE.get(),
                ModBlocks.OAK_ROUND_TABLE.get(),
                ModBlocks.ARCADE_MACHINE.get(),
                ModBlocks.WALL_PHOTO_FRAME.get(),
                ModBlocks.WALL_BONE_DECOR.get(),
                ModBlocks.KITCHEN_COUNTER.get(),
                ModBlocks.TABLEWARE_PINK.get(),
                ModBlocks.TABLEWARE_BLUE.get(),
                ModBlocks.WALL_KITCHEN_CABINET.get(),
                ModBlocks.JOJA_VENDING_MACHINE.get(),
                ModBlocks.WHITE_TEACUP.get(),
                ModBlocks.POOL_TABLE.get(),
                ModBlocks.GLOBE.get(),
                ModBlocks.TELESCOPE.get(),
                ModBlocks.BEAR_FIGURINE.get(),
                ModBlocks.FISH_SHOP_COUNTER.get(),
                ModBlocks.SUPERMARKET_SHELF_1.get(),
                ModBlocks.SUPERMARKET_SHELF_2.get(),
                ModBlocks.JOJA_SUPERMARKET_CRATE.get(),
                ModBlocks.SUPERMARKET_CART.get(),
                ModBlocks.SUPERMARKET_FREEZER.get(),
                ModBlocks.HOSPITAL_COUNTER.get(),
                ModBlocks.HOSPITAL_POSTER_1.get(),
                ModBlocks.HOSPITAL_POSTER_2.get(),
                ModBlocks.HOSPITAL_POSTER_3.get(),
                ModBlocks.HOSPITAL_POSTER_4.get(),
                ModBlocks.HOSPITAL_POSTER_5.get(),
                ModBlocks.ELECTRIC_PIANO.get(),
                ModBlocks.WIZARD_CAULDRON.get(),
                ModBlocks.GUITAR.get(),
                ModBlocks.MICROWAVE.get(),
                ModBlocks.DRUM_SET.get(),
                ModBlocks.WINE_CABINET_1.get(),
                ModBlocks.WINE_CABINET_2.get(),
                ModBlocks.WINE_CABINET_3.get(),
                ModBlocks.ALEX_POSTER_1.get(),
                ModBlocks.ALEX_POSTER_2.get(),
                ModBlocks.ALEX_POSTER_3.get(),
                ModBlocks.LEAH_POSTER_1.get(),
                ModBlocks.LEAH_POSTER_2.get(),
                ModBlocks.LEAH_POSTER_3.get(),
                ModBlocks.PERIODIC_TABLE.get(),
                ModBlocks.MICROSCOPE.get(),
                ModBlocks.TRAIN_PHOTO.get(),
                ModBlocks.WALL_PHOTO_1.get(),
                ModBlocks.PAPER_CHECKLIST.get(),
                ModBlocks.SINE_WAVE_POSTER.get(),
                ModBlocks.SCATTERED_PAPERS.get(),
                ModBlocks.SEBASTIAN_POSTER_1.get(),
                ModBlocks.SEBASTIAN_POSTER_2.get(),
                ModBlocks.SEBASTIAN_POSTER_3.get(),
                ModBlocks.BOARD_GAME.get(),
                ModBlocks.WALL_ADVENTURER_MAP.get(),
                ModBlocks.WALL_BUOY.get(),
                ModBlocks.WALL_FISH_SIGN.get(),
                ModBlocks.WALL_ISLAND_MAP.get(),
                ModBlocks.BEAR_SKIN_RUG.get(),
                ModBlocks.LEANING_SWORD.get(),
                ModBlocks.LEAH_SCULPTURE.get(),
                ModBlocks.EASEL.get(),
                ModBlocks.PASTEL_BANNER.get(),
                ModBlocks.FLOWER_BASKET.get(),
                ModBlocks.FLOWER_CLUSTER.get(),
                ModBlocks.SEASONAL_DECOR.get(),
                ModBlocks.SQUID_FEST_PROMO_POSTER.get(),
                ModBlocks.SQUID_FEST_REQUIREMENT_POSTER.get(),
                ModBlocks.PLUSH_BUNNY.get(),
                ModBlocks.LAWN_FLAMINGO.get(),
                ModBlocks.HOLIDAY_RIBBON_POST.get(),
                ModBlocks.BLUE_BEAR_PLUSHIE.get(),
                ModBlocks.SAFE_BOX.get(),
                ModBlocks.BROKEN_SAFE_BOX.get(),
                ModBlocks.LOOM_MACHINE.get(),
                ModBlocks.BOILER_DECOR.get(),
                ModBlocks.BROKEN_BOILER.get(),
                ModBlocks.YARN_CABINET.get(),
                ModBlocks.BROKEN_CHAIR.get(),
                ModBlocks.COAL_BASKET.get(),
                ModBlocks.FURNITURE_CATALOGUE.get(),
                ModBlocks.STANDING_HOE.get(),
                ModBlocks.EMPTY_TERRACOTTA_POT.get(),
                ModBlocks.RESERVOIR.get(),
                ModBlocks.LONG_POTTED_PLANT.get(),
                ModBlocks.FAIR_WHEEL.get(),
                ModBlocks.FAIR_GRAVE_STONE.get(),
                ModBlocks.FAIR_GRILL.get(),
                ModBlocks.STATUE_OF_BLESSINGS.get(),
                ModBlocks.STATUE_OF_DWARF_KING.get()
            ));

            ModRenderLayers.registerCutoutMipped(List.of(
                ModBlocks.WILD_OAK_LEAVES.get(),
                ModBlocks.WILD_MAPLE_LEAVES.get(),
                ModBlocks.WILD_PINE_LEAVES.get(),
                ModBlocks.WILD_MAHOGANY_LEAVES.get(),
                ModBlocks.WILD_MYSTIC_TREE_LEAVES.get(),
                ModBlocks.OAK_LEAVES.get(),
                ModBlocks.OAK_LEAVES_QUESTION.get(),
                ModBlocks.MAPLE_LEAVES.get(),
                ModBlocks.PINE_LEAVES.get(),
                ModBlocks.MAHOGANY_LEAVES.get(),
                ModBlocks.MYSTIC_TREE_LEAVES.get()
            ));

            registerNegativeVolumeModels();
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                ModBlocks.FISH_POND_WATER.get(),
                net.minecraft.client.renderer.RenderType.translucent()
            );
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.stardew.craft.fluid.ModFluids.FISH_POND_WATER.get(),
                net.minecraft.client.renderer.RenderType.translucent()
            );
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.stardew.craft.fluid.ModFluids.FLOWING_FISH_POND_WATER.get(),
                net.minecraft.client.renderer.RenderType.translucent()
            );

            ModItemProperties.register();
        });
    }

    private static void registerNegativeVolumeModels() {
        List<net.minecraft.world.level.block.Block> blocks = new java.util.ArrayList<>(List.of(
            ModBlocks.OIL_MAKER.get(),
            ModBlocks.MUSEUM_EXHIBIT_STAND.get(),
            ModBlocks.TABLE_LANTERN.get(),
            ModBlocks.JUKEBOX.get(),
            ModBlocks.BEAKER.get(),
            ModBlocks.FISH_SHOP_COUNTER.get(),
            ModBlocks.DRESSER_1.get(),
            ModBlocks.DRESSER_2.get(),
            ModBlocks.DRESSER_3.get(),
            ModBlocks.REDWOOD_WARDROBE.get(),
            ModBlocks.WALNUT_WARDROBE.get(),
            ModBlocks.OAK_WARDROBE.get(),
            ModBlocks.OAK_BEDSIDE_CABINET.get(),
            ModBlocks.OAK_DRESSER.get(),
            ModBlocks.REDWOOD_BEDSIDE_CABINET.get(),
            ModBlocks.REDWOOD_DRESSER.get(),
            ModBlocks.WALNUT_BEDSIDE_CABINET.get(),
            ModBlocks.WALNUT_DRESSER.get(),
            ModBlocks.LIGHTNING_ROD.get(),
            ModBlocks.WINTER_STAR_LARGE_RED_CANDY_CANE.get(),
            ModBlocks.WINTER_STAR_LARGE_GREEN_CANDY_CANE.get(),
            ModBlocks.WINTER_STAR_MIXED_CANDY_CANES.get(),
            ModBlocks.WINTER_STAR_RED_CANDY_CANES.get(),
            ModBlocks.WINTER_STAR_GREEN_CANDY_CANES.get(),
            ModBlocks.WINTER_STAR_PURPLE_GIFT_BOX.get(),
            ModBlocks.WINTER_STAR_RED_GIFT_BOX.get(),
            ModBlocks.WINTER_STAR_GREEN_GIFT_BOX.get(),
            ModBlocks.WINTER_STAR_BLUE_GIFT_BOX.get()
        ));
        ModBlocks.PLACED_COOKING_FOODS.values().forEach(block -> blocks.add(block.get()));
        blocks.forEach(block -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
            block,
            net.minecraft.client.renderer.RenderType.translucent()
        ));
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FALLEN_OAK_TREE.get(), FallenOakTreeRenderer::new);
        event.registerEntityRenderer(ModEntities.FALLEN_PREFAB_TREE.get(),
                com.stardew.craft.client.render.FallenPrefabTreeRenderer::new);
        event.registerEntityRenderer(ModEntities.SOFA_SEAT.get(), SofaSeatEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.MINECART_STATION.get(),
                com.stardew.craft.client.render.MinecartStationRenderer::new);
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            return resolveSeasonalGrassColor(level, pos);
        }, Blocks.GRASS_BLOCK);

        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            return resolveSeasonalGrassColor(level, pos);
        }, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TALL_GRASS, Blocks.LARGE_FERN,
                ModBlocks.PASTURE_GRASS.get(), ModBlocks.BLUE_PASTURE_GRASS.get());

        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            return resolveSeasonalLeafColor(state, level, pos);
        },
                Blocks.OAK_LEAVES,
                Blocks.SPRUCE_LEAVES,
                Blocks.BIRCH_LEAVES,
                Blocks.JUNGLE_LEAVES,
                Blocks.ACACIA_LEAVES,
                Blocks.DARK_OAK_LEAVES,
                Blocks.MANGROVE_LEAVES,
                Blocks.AZALEA_LEAVES,
                Blocks.FLOWERING_AZALEA_LEAVES,
                ModBlocks.WILD_OAK_LEAVES.get(),
                ModBlocks.WILD_MAPLE_LEAVES.get(),
                ModBlocks.WILD_PINE_LEAVES.get(),
                ModBlocks.WILD_MAHOGANY_LEAVES.get(),
                ModBlocks.OAK_LEAVES.get(),
                ModBlocks.OAK_LEAVES_QUESTION.get(),
                ModBlocks.MAPLE_LEAVES.get(),
                ModBlocks.PINE_LEAVES.get(),
                ModBlocks.MAHOGANY_LEAVES.get(),
                ModBlocks.SMALL_BUSH.get(),
                ModBlocks.BERRY_BUSH.get());

        event.register((state, level, pos, tintIndex) -> 0xFFFFFFFF,
                ModBlocks.WILD_MYSTIC_TREE_LEAVES.get(),
                ModBlocks.MYSTIC_TREE_LEAVES.get());

        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            return resolveYellowDirtColor();
        }, ModBlocks.YELLOW_DIRT.get(), ModBlocks.ARTIFACT_SPOT_DIRT.get());

        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0 || state == null) {
                return 0xFFFFFFFF;
            }
            Integer indexValue = null;
            WoodenChestColorPalette.TintMaterial tintMaterial = WoodenChestColorPalette.TintMaterial.DIRECT;
            if (state.hasProperty(SofaBlock.COLOR)) {
                indexValue = state.getValue(SofaBlock.COLOR);
                tintMaterial = WoodenChestColorPalette.TintMaterial.SOFA;
            } else if (state.hasProperty(CushionBlock.COLOR)) {
                indexValue = state.getValue(CushionBlock.COLOR);
                tintMaterial = WoodenChestColorPalette.TintMaterial.CUSHION;
            } else if (state.hasProperty(OfficeStoolBlock.COLOR)) {
                indexValue = state.getValue(OfficeStoolBlock.COLOR);
                tintMaterial = WoodenChestColorPalette.TintMaterial.OFFICE_SEATING;
            } else if (state.hasProperty(OfficeStoolTopRenderBlock.COLOR)) {
                indexValue = state.getValue(OfficeStoolTopRenderBlock.COLOR);
                tintMaterial = WoodenChestColorPalette.TintMaterial.OFFICE_SEATING;
            } else if (state.hasProperty(OfficeChair2Block.COLOR)) {
                indexValue = state.getValue(OfficeChair2Block.COLOR);
                tintMaterial = WoodenChestColorPalette.TintMaterial.OFFICE_SEATING;
            } else if (state.hasProperty(OfficeChair2TopRenderBlock.COLOR)) {
                indexValue = state.getValue(OfficeChair2TopRenderBlock.COLOR);
                tintMaterial = WoodenChestColorPalette.TintMaterial.OFFICE_SEATING;
            } else if (state.hasProperty(DyeableChairBlock.COLOR)) {
                indexValue = state.getValue(DyeableChairBlock.COLOR);
                tintMaterial = dyeableChairTintMaterial(state);
            } else if (state.hasProperty(OakTableBlock.CLOTH_STYLE)
                && OakTableBlock.isDyeableClothStyle(state.getValue(OakTableBlock.CLOTH_STYLE))
                && level != null
                && pos != null
                && level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe) {
                indexValue = tableBe.getClothColor();
                tintMaterial = WoodenChestColorPalette.TintMaterial.TABLECLOTH;
            }
            if (indexValue == null) {
                return 0xFFFFFFFF;
            }
            int index = WoodenChestColorPalette.clampIndex(indexValue);
            if (index < 0) {
                index = 0;
            }
            return 0xFF000000 | (WoodenChestColorPalette.tintRgbAt(index, tintMaterial) & 0xFFFFFF);
        }, ModBlocks.SOFA.get(), ModBlocks.CUSHION.get(), ModBlocks.OFFICE_STOOL.get(), ModBlocks.OFFICE_STOOL_TOP_RENDER.get(), ModBlocks.OFFICE_CHAIR_2.get(), ModBlocks.OFFICE_CHAIR_2_TOP_RENDER.get(), ModBlocks.STOOL.get(), ModBlocks.IRON_STOOL.get(), ModBlocks.DINING_CHAIR_WOOD.get(), ModBlocks.DINING_CHAIR_IRON.get(), ModBlocks.OAK_TABLE.get(), ModBlocks.SPRUCE_TABLE.get(), ModBlocks.BIRCH_TABLE.get());

    }

    private static WoodenChestColorPalette.TintMaterial dyeableChairTintMaterial(BlockState state) {
        if (state.is(ModBlocks.STOOL.get()) || state.is(ModBlocks.IRON_STOOL.get())) {
            return WoodenChestColorPalette.TintMaterial.STOOL;
        }
        if (state.is(ModBlocks.DINING_CHAIR_WOOD.get()) || state.is(ModBlocks.DINING_CHAIR_IRON.get())) {
            return WoodenChestColorPalette.TintMaterial.DINING_CHAIR;
        }
        return WoodenChestColorPalette.TintMaterial.DIRECT;
    }

    private static int resolveSeasonalGrassColor(BlockAndTintGetter level, BlockPos pos) {
        if (!isRenderingStardewValley() || !StardewTimeHud.isTimeSynced()) {
            return getVanillaGrassColor(level, pos);
        }

        return switch (StardewTimeHud.getClientTimeCache().getCurrentSeason()) {
            case 0 -> STARDEW_GRASS_SPRING;
            case 1 -> STARDEW_GRASS_SUMMER;
            case 2 -> STARDEW_GRASS_FALL;
            case 3 -> getWinterGrassColor();
            default -> getVanillaGrassColor(level, pos);
        };
    }

    public static int resolveSeasonalLeafColor(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (!isRenderingStardewValley() || !StardewTimeHud.isTimeSynced()) {
            return getVanillaLeafColor(state, level, pos);
        }

        return switch (StardewTimeHud.getClientTimeCache().getCurrentSeason()) {
            case 0 -> STARDEW_GRASS_SPRING;
            case 1 -> STARDEW_GRASS_SUMMER;
            case 2 -> STARDEW_GRASS_FALL;
            case 3 -> getWinterLeafColor();
            default -> getVanillaLeafColor(state, level, pos);
        };
    }

    private static boolean isRenderingStardewValley() {
        var clientLevel = Minecraft.getInstance().level;
        return clientLevel != null && clientLevel.dimension().equals(ModDimensions.STARDEW_VALLEY);
    }

    private static int getVanillaGrassColor(BlockAndTintGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return GrassColor.getDefaultColor();
        }
        return BiomeColors.getAverageGrassColor(level, pos);
    }

    private static int getVanillaLeafColor(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (state != null) {
            if (state.is(Blocks.SPRUCE_LEAVES)
                    || state.is(ModBlocks.WILD_PINE_LEAVES.get())
                    || state.is(ModBlocks.PINE_LEAVES.get())) {
                return FoliageColor.getEvergreenColor();
            }
            if (state.is(Blocks.BIRCH_LEAVES)) {
                return FoliageColor.getBirchColor();
            }
        }
        if (level == null || pos == null) {
            return FoliageColor.getDefaultColor();
        }
        return BiomeColors.getAverageFoliageColor(level, pos);
    }

    private static int getWinterGrassColor() {
        return STARDEW_GRASS_WINTER;
    }

    private static int getWinterLeafColor() {
        return STARDEW_LEAF_WINTER;
    }

    private static int resolveYellowDirtColor() {
        if (!isRenderingStardewValley() || !StardewTimeHud.isTimeSynced()) {
            return 0xFFFFFFFF;
        }
        return StardewTimeHud.getClientTimeCache().getCurrentSeason() == 3
            ? STARDEW_YELLOW_DIRT_WINTER
            : 0xFFFFFFFF;
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            WoodenChestColorPalette.TintMaterial tintMaterial = furnitureItemTintMaterial(stack.getItem());
            int rgb = WoodenChestColorPalette.tintRgbAt(WoodenChestColorPalette.defaultColorIndex(), tintMaterial);
            return 0xFF000000 | (rgb & 0xFFFFFF);
        }, ModItems.SOFA.get(), ModItems.CUSHION.get(), ModItems.OFFICE_STOOL.get(), ModItems.OFFICE_CHAIR_2.get(), ModItems.STOOL.get(), ModItems.IRON_STOOL.get(), ModItems.DINING_CHAIR_WOOD.get(), ModItems.DINING_CHAIR_IRON.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            int defaultColor = WoodenChestColorPalette.size() - 1;
            return 0xFF000000 | (WoodenChestColorPalette.rgbAt(defaultColor) & 0xFFFFFF);
        }, ModItems.FLORAL_TABLECLOTH.get(), ModItems.BLANK_TABLECLOTH.get());

        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof PreservesItem preservesItem)) {
                return 0xFFFFFFFF;
            }
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            int color = preservesItem.getColor(stack);
            int rgb = color >= 0 ? color : 0xFFFFFF;
            return 0xFF000000 | (rgb & 0xFFFFFF);
        },
            ModItems.JELLY.get(),
            ModItems.PICKLES.get(),
            ModItems.ROE.get(),
            ModItems.AGED_ROE.get(),
            ModItems.DRIED_FRUIT.get(),
            ModItems.DRIED_MUSHROOMS.get()
        );
        java.util.ArrayList<Item> flavoredDrinkItems = new java.util.ArrayList<>();
        flavoredDrinkItems.add(ModItems.WINE.get());
        flavoredDrinkItems.add(ModItems.JUICE.get());
        ModItems.LEGACY_FLAVORED_DRINKS.values().forEach(holder -> flavoredDrinkItems.add(holder.get()));
        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof com.stardew.craft.item.artisan.FlavoredArtisanDrinkItem drink)
                    || tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            int color = drink.getColor(stack);
            int rgb = color >= 0 ? color : 0xFFFFFF;
            return 0xFF000000 | (rgb & 0xFFFFFF);
        }, flavoredDrinkItems.toArray(Item[]::new));
            event.register((stack, tintIndex) -> {
                if (!(stack.getItem() instanceof SpecificBaitItem specificBaitItem)) {
                    return 0xFFFFFFFF;
                }
                if (tintIndex != 1) {
                    return 0xFFFFFFFF;
                }
                int color = specificBaitItem.getColor(stack);
                int rgb = color >= 0 ? color : 0xFFFFFF;
                return 0xFF000000 | (rgb & 0xFFFFFF);
            }, ModItems.TARGETED_BAIT.get());

        // SDV parity: Junimo bundle held item — tinted by JunimoBundleLayer.currentRenderBundleColor
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return 0xFFFFFFFF;
            int rgb = com.stardew.craft.client.renderer.entity.JunimoBundleLayer.currentRenderBundleColor;
            return 0xFF000000 | (rgb & 0xFFFFFF);
        }, ModItems.JUNIMO_BUNDLE.get());
    }

    private static WoodenChestColorPalette.TintMaterial furnitureItemTintMaterial(Item item) {
        if (item == ModItems.SOFA.get()) {
            return WoodenChestColorPalette.TintMaterial.SOFA;
        }
        if (item == ModItems.CUSHION.get()) {
            return WoodenChestColorPalette.TintMaterial.CUSHION;
        }
        if (item == ModItems.OFFICE_STOOL.get() || item == ModItems.OFFICE_CHAIR_2.get()) {
            return WoodenChestColorPalette.TintMaterial.OFFICE_SEATING;
        }
        if (item == ModItems.STOOL.get() || item == ModItems.IRON_STOOL.get()) {
            return WoodenChestColorPalette.TintMaterial.STOOL;
        }
        if (item == ModItems.DINING_CHAIR_WOOD.get() || item == ModItems.DINING_CHAIR_IRON.get()) {
            return WoodenChestColorPalette.TintMaterial.DINING_CHAIR;
        }
        return WoodenChestColorPalette.TintMaterial.DIRECT;
    }

    @SubscribeEvent
    static void onTextureStitch(TextureAtlasStitchedEvent event) {
        if (!event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) {
            return;
        }

        StardewCraft.LOGGER.info("Block atlas stitched - fertilizer textures should be available");
    }

}
