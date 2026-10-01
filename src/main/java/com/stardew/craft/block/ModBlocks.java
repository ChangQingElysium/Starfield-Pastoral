package com.stardew.craft.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.cooking.CookingPlacedFoodBlock;
import com.stardew.craft.block.mine.CalicoStatueBlock;
import com.stardew.craft.block.utility.LegacyWallpaperBlock;
import com.stardew.craft.block.utility.WallpaperBlock;
import com.stardew.craft.deco.WallpaperStyles;
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
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredBlock;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册管理器
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StardewCraft.MODID);
    public static final DeferredBlock<com.stardew.craft.pet.PetBowlBlock> PET_BOWL_WOOD = BLOCKS.register("pet_bowl_wood", () -> new com.stardew.craft.pet.PetBowlBlock(Block.Properties.of().strength(1).sound(SoundType.WOOD).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "wood"));
    public static final DeferredBlock<com.stardew.craft.pet.PetBowlBlock> PET_BOWL_STONE = BLOCKS.register("pet_bowl_stone", () -> new com.stardew.craft.pet.PetBowlBlock(Block.Properties.of().strength(1).sound(SoundType.STONE).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "stone"));
    public static final DeferredBlock<com.stardew.craft.pet.PetBowlBlock> PET_BOWL_HAY = BLOCKS.register("pet_bowl_hay", () -> new com.stardew.craft.pet.PetBowlBlock(Block.Properties.of().strength(1).sound(SoundType.GRASS).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "hay"));
    public static final Map<String, DeferredBlock<com.stardew.craft.block.decor.NaturalPlantBlock>> NATURAL_DECOR =
            com.stardew.craft.block.decor.NaturalDecorRegistry.blocks(BLOCKS);


        public static final DeferredBlock<com.stardew.craft.block.terrain.PlaygroundSandBlock> PLAYGROUND_SAND =
                        BLOCKS.register("playground_sand", () -> new com.stardew.craft.block.terrain.PlaygroundSandBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SAND)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PlaygroundBlock> BIRD_SPRING_RIDER =
                        BLOCKS.register("bird_spring_rider", () -> new com.stardew.craft.block.decor.PlaygroundBlock(
                                        Block.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "bird_spring_rider"));

        public static final DeferredBlock<com.stardew.craft.block.terrain.AsphaltRoadBlock> ASPHALT_ROAD =
                        BLOCKS.register("asphalt_road", () -> new com.stardew.craft.block.terrain.AsphaltRoadBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.RoadMarkingBlock> ROAD_DASH =
                        BLOCKS.register("road_dash", () -> new com.stardew.craft.block.terrain.RoadMarkingBlock(
                                        Block.Properties.of().noCollission().noOcclusion().instabreak().sound(SoundType.STONE)
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.RoadMarkingBlock> ROAD_DOUBLE_LINE =
                        BLOCKS.register("road_double_line", () -> new com.stardew.craft.block.terrain.RoadMarkingBlock(
                                        Block.Properties.of().noCollission().noOcclusion().instabreak().sound(SoundType.STONE)
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainGrassBlock> GRASS_BLOCK =
                        BLOCKS.register("grass_block", () -> new com.stardew.craft.block.terrain.VariedTerrainGrassBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainDirtBlock> DIRT =
                        BLOCKS.register("dirt", () -> new com.stardew.craft.block.terrain.TerrainDirtBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.HardSoilBlock> HARD_SOIL =
                        BLOCKS.register("hard_soil", () -> new com.stardew.craft.block.terrain.HardSoilBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).strength(1.0F)));
        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainFarmlandBlock.Infertile> INFERTILE_FARMLAND =
                        BLOCKS.register("infertile_farmland", () -> new com.stardew.craft.block.terrain.TerrainFarmlandBlock.Infertile(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.FARMLAND)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainSandBlock> SAND =
                        BLOCKS.register("sand", () -> new com.stardew.craft.block.terrain.TerrainSandBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SAND)));
        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainFarmlandBlock.Sandy> SANDY_FARMLAND =
                        BLOCKS.register("sandy_farmland", () -> new com.stardew.craft.block.terrain.TerrainFarmlandBlock.Sandy(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.FARMLAND).mapColor(MapColor.SAND).sound(SoundType.SAND)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainFarmlandBlock> FARMLAND =
                        BLOCKS.register("farmland", () -> new com.stardew.craft.block.terrain.TerrainFarmlandBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.FARMLAND)));

        public static final DeferredBlock<com.stardew.craft.block.decor.BlacksmithToolDecorBlock> LEANING_SHOVEL =
                        BLOCKS.register("leaning_shovel", () -> new com.stardew.craft.block.decor.BlacksmithToolDecorBlock(
                                        Block.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), true));
        public static final DeferredBlock<com.stardew.craft.block.decor.BlacksmithToolDecorBlock> OLD_PLANK =
                        BLOCKS.register("old_plank", () -> new com.stardew.craft.block.decor.BlacksmithToolDecorBlock(
                                        Block.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), false));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorStaticBlock> ROAD_SIGN =
                        BLOCKS.register("road_sign", () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/road_sign"));

        public static final DeferredBlock<com.stardew.craft.block.decor.RuralFenceBlock> RURAL_FENCE =
                        BLOCKS.register("rural_fence", () -> new com.stardew.craft.block.decor.RuralFenceBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.RuralFenceBlock> WOOD_FENCE =
                        BLOCKS.register("wood_fence", () -> new com.stardew.craft.block.decor.RuralFenceBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/wood_fence"));

        public static final DeferredBlock<com.stardew.craft.block.decor.RuralFenceBlock> HARDWOOD_FENCE =
                        BLOCKS.register("hardwood_fence", () -> new com.stardew.craft.block.decor.RuralFenceBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/hardwood_fence"));

        public static final DeferredBlock<com.stardew.craft.block.decor.GardenPlanterBlock> GARDEN_PLANTER =
                        BLOCKS.register("garden_planter", () -> new com.stardew.craft.block.decor.GardenPlanterBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorStaticBlock> BLUE_FLOWERPOT =
                        BLOCKS.register("blue_flowerpot", () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(
                                        Block.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.8F, 1.0F)
                                                .sound(SoundType.STONE).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/house/blue_flowerpot"));

        public static final DeferredBlock<com.stardew.craft.block.decor.DogHouseBlock> DOG_HOUSE =
                        BLOCKS.register("dog_house", () -> new com.stardew.craft.block.decor.DogHouseBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.OldTireBlock> OLD_TIRE =
                        BLOCKS.register("old_tire", () -> new com.stardew.craft.block.decor.OldTireBlock(
                                        Block.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.WOOL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.ManholeBlock> MANHOLE =
                        BLOCKS.register("manhole", () -> new com.stardew.craft.block.decor.ManholeBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()
                                                .strength(3.0F, 6.0F).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorStaticBlock> TALL_GRAVESTONE =
                        BLOCKS.register("tall_gravestone", () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/gravestones/spring/tall_gravestone"));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorStaticBlock> SHORT_GRAVESTONE =
                        BLOCKS.register("short_gravestone", () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/gravestones/spring/short_gravestone"));

        public static final DeferredBlock<com.stardew.craft.block.utility.OutdoorTableBlock> OUTDOOR_TABLE =
                        BLOCKS.register("outdoor_table", () -> new com.stardew.craft.block.utility.OutdoorTableBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.ParkBenchBlock> PARK_BENCH =
                        BLOCKS.register("park_bench", () -> new com.stardew.craft.block.decor.ParkBenchBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PlaygroundBlock> PLAYGROUND_SLIDE =
                        BLOCKS.register("playground_slide", () -> new com.stardew.craft.block.decor.PlaygroundBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), true));

        public static final DeferredBlock<com.stardew.craft.block.decor.PlaygroundBlock> CLIMBING_FRAME =
                        BLOCKS.register("climbing_frame", () -> new com.stardew.craft.block.decor.PlaygroundBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), false));

        public static final DeferredBlock<com.stardew.craft.block.decor.BooksellerDecorBlock> BOOKSELLER_STALL =
                        BLOCKS.register("bookseller_stall", () -> new com.stardew.craft.block.decor.BooksellerDecorBlock(
                                        Block.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), false));
        public static final DeferredBlock<com.stardew.craft.block.decor.BooksellerDecorBlock> BOOKSELLER_BALLOON =
                        BLOCKS.register("bookseller_balloon", () -> new com.stardew.craft.block.decor.BooksellerDecorBlock(
                                        Block.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.0F).sound(SoundType.WOOL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), true));

        public static final DeferredBlock<com.stardew.craft.block.decor.BlacksmithVentilatorBlock> BLACKSMITH_VENTILATOR =
                        BLOCKS.register("blacksmith_ventilator", () -> new com.stardew.craft.block.decor.BlacksmithVentilatorBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.JojaBillboardBlock> JOJA_BILLBOARD =
                        BLOCKS.register("joja_billboard", () -> new com.stardew.craft.block.decor.JojaBillboardBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.IceCreamStandBlock> ICE_CREAM_STAND =
                        BLOCKS.register("ice_cream_stand", () -> new com.stardew.craft.block.decor.IceCreamStandBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .strength(2.0F).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PlazaDisplayBlock> PLAZA_DISPLAY =
                        BLOCKS.register("plaza_display", () -> new com.stardew.craft.block.decor.PlazaDisplayBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).noOcclusion()
                                                .strength(2.0F).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.ParkedVehicleBlock> BUS =
                        BLOCKS.register("bus", () -> new com.stardew.craft.block.decor.ParkedVehicleBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), true));

        public static final DeferredBlock<com.stardew.craft.block.decor.ParkedVehicleBlock> MAYOR_PICKUP =
                        BLOCKS.register("mayor_pickup", () -> new com.stardew.craft.block.decor.ParkedVehicleBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), false));

        public static final DeferredBlock<com.stardew.craft.block.decor.ParkedVehicleBlock> JOJA_TRUCK =
                        BLOCKS.register("joja_truck", () -> new com.stardew.craft.block.decor.ParkedVehicleBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "joja_truck"));

        public static final DeferredBlock<com.stardew.craft.block.decor.DoubleSwingBlock> DOUBLE_SWING =
                        BLOCKS.register("double_swing", () -> new com.stardew.craft.block.decor.DoubleSwingBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.ParkFountainBlock> PARK_FOUNTAIN =
                        BLOCKS.register("park_fountain", () -> new com.stardew.craft.block.decor.ParkFountainBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICKS).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.LargeFishTankBlock> LARGE_FISH_TANK =
                        BLOCKS.register("large_fish_tank", () -> new com.stardew.craft.block.decor.LargeFishTankBlock(
                                        Block.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.FishMarketCrateBlock> FISH_MARKET_CRATE =
                        BLOCKS.register("fish_market_crate", () -> new com.stardew.craft.block.decor.FishMarketCrateBlock(
                                        Block.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PlacedFishBlock> PLACED_FISH =
                        BLOCKS.register("placed_fish", () -> new com.stardew.craft.block.decor.PlacedFishBlock(
                                        Block.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(.1F).noCollission().noOcclusion()
                                                .noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.decor.BobberStyleMachineBlock> BOBBER_STYLE_MACHINE =
                        BLOCKS.register("bobber_style_machine", () -> new com.stardew.craft.block.decor.BobberStyleMachineBlock(
                                        Block.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.TicketMachineBlock> TICKET_MACHINE =
                        BLOCKS.register("ticket_machine", () -> new com.stardew.craft.block.decor.TicketMachineBlock(
                                        Block.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                                        "stardewcraft:block/decor/ticket_machine"));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainCliffBlock> CLIFF =
                        BLOCKS.register("cliff", () -> new com.stardew.craft.block.terrain.TerrainCliffBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TownPavingBlock> TOWN_PAVING =
                        BLOCKS.register("town_paving", () -> new com.stardew.craft.block.terrain.TownPavingBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICKS)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.PalePavingBlock> PALE_PAVING =
                        BLOCKS.register("pale_paving", () -> new com.stardew.craft.block.terrain.PalePavingBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)));

        public static final DeferredBlock<StairBlock> TOWN_PAVING_STAIRS = stairsFromAnyBlock("town_paving_stairs",
                        TOWN_PAVING, Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TownPavingBlock> PLAZA_RED_BRICKS =
                        BLOCKS.register("plaza_red_bricks", () -> new com.stardew.craft.block.terrain.TownPavingBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.OchreBricksBlock> OCHRE_BRICKS =
                        BLOCKS.register("ochre_bricks", () -> new com.stardew.craft.block.terrain.OchreBricksBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS)
                                                .mapColor(MapColor.TERRACOTTA_ORANGE)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainGrassBlock> DARK_GRASS_BLOCK =
                        BLOCKS.register("dark_grass_block", () -> new com.stardew.craft.block.terrain.TerrainGrassBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)));

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

        private static Map<String, DeferredBlock<WallpaperBlock>> registerWallpaperStyles() {
                Map<String, DeferredBlock<WallpaperBlock>> blocks = new LinkedHashMap<>();
                for (String styleId : WallpaperStyles.allStyleIds()) {
                        blocks.put(styleId, BLOCKS.register(WallpaperStyles.registryPath(styleId),
                                        () -> new WallpaperBlock(Block.Properties.of()
                                                        .mapColor(MapColor.WOOL)
                                                        .sound(SoundType.WOOL)
                                                        .strength(0.8F, 1.0F), styleId)));
                }
                return Collections.unmodifiableMap(blocks);
        }

        public static DeferredBlock<WallpaperBlock> getWallpaperStyleBlock(String styleId) {
                return WALLPAPER_STYLES.getOrDefault(styleId, WALLPAPER_STYLES.get("0"));
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
                foods.put("wine", BLOCKS.register("placed_food_wine",
                                () -> new com.stardew.craft.block.cooking.PlacedArtisanDrinkBlock("wine",
                                                Block.box(6.2, 0, 6.2, 9.8, 13.4, 9.8), Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                                .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                                .noCollission().noOcclusion().instabreak())));
                foods.put("juice", BLOCKS.register("placed_food_juice",
                                () -> new com.stardew.craft.block.cooking.PlacedArtisanDrinkBlock("juice",
                                                Block.box(4.8, 0, 4.8, 11.2, 9.6, 11.2), Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                                .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                                .noCollission().noOcclusion().instabreak())));
                foods.put("mead", BLOCKS.register("placed_food_mead",
                                () -> new com.stardew.craft.block.cooking.PlacedArtisanDrinkBlock("mead",
                                                net.minecraft.world.phys.shapes.Shapes.or(
                                                        Block.box(5.8, 0, 5.6, 10.2, 9.4, 10),
                                                        Block.box(6.8, 9.4, 6.7, 9.2, 13.4, 9.1),
                                                        Block.box(9.4, 3, 7.2, 11.8, 8.4, 8.6)), Block.Properties.of()
                                                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_YELLOW)
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                                .sound(net.minecraft.world.level.block.SoundType.GLASS)
                                                .noCollission().noOcclusion().instabreak())));
                return Collections.unmodifiableMap(foods);
        }

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainSlabBlock> GRASS_SLAB = BLOCKS.register("grass_slab",
                        () -> new com.stardew.craft.block.terrain.TerrainSlabBlock.Grass(Block.Properties.of().mapColor(MapColor.GRASS).sound(SoundType.GRASS).strength(0.6F)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainStairBlock> GRASS_STAIRS = BLOCKS.register("grass_stairs",
                        () -> new com.stardew.craft.block.terrain.TerrainStairBlock.Grass(Block.Properties.of().mapColor(MapColor.GRASS).sound(SoundType.GRASS).strength(0.6F)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainSlabBlock> DARK_GRASS_SLAB = BLOCKS.register("dark_grass_slab",
                        () -> new com.stardew.craft.block.terrain.TerrainSlabBlock.DarkGrass(Block.Properties.of().mapColor(MapColor.GRASS).sound(SoundType.GRASS).strength(0.6F)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainStairBlock> DARK_GRASS_STAIRS = BLOCKS.register("dark_grass_stairs",
                        () -> new com.stardew.craft.block.terrain.TerrainStairBlock.DarkGrass(Block.Properties.of().mapColor(MapColor.GRASS).sound(SoundType.GRASS).strength(0.6F)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainSlabBlock> DIRT_SLAB = BLOCKS.register("dirt_slab",
                        () -> new com.stardew.craft.block.terrain.TerrainSlabBlock.Dirt(Block.Properties.of().mapColor(MapColor.DIRT).sound(SoundType.GRAVEL).strength(0.5F)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainStairBlock> DIRT_STAIRS = BLOCKS.register("dirt_stairs",
                        () -> new com.stardew.craft.block.terrain.TerrainStairBlock.Dirt(Block.Properties.of().mapColor(MapColor.DIRT).sound(SoundType.GRAVEL).strength(0.5F)));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainSlabBlock> CLIFF_SLAB = BLOCKS.register("cliff_slab",
                        () -> new com.stardew.craft.block.terrain.TerrainSlabBlock.Cliff(Block.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

        public static final DeferredBlock<com.stardew.craft.block.terrain.TerrainStairBlock> CLIFF_STAIRS = BLOCKS.register("cliff_stairs",
                        () -> new com.stardew.craft.block.terrain.TerrainStairBlock.Cliff(Block.Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

        public static final Map<String, DeferredBlock<CookingPlacedFoodBlock>> PLACED_COOKING_FOODS = registerPlacedCookingFoods();

        public static DeferredBlock<CookingPlacedFoodBlock> getPlacedCookingFoodBlock(String itemId) {
                return PLACED_COOKING_FOODS.get(itemId);
        }

        public static final DeferredBlock<com.stardew.craft.block.decor.SupplyCrateBlock> SUPPLY_CRATE = BLOCKS.register("supply_crate",
                () -> new com.stardew.craft.block.decor.SupplyCrateBlock(Block.Properties.of().mapColor(MapColor.WOOD)
                        .strength(0.5F).sound(SoundType.WOOD).noOcclusion().noLootTable()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        // 自然/杂草
        @SuppressWarnings("null")
        public static final DeferredBlock<com.stardew.craft.block.decor.FarmTwigBlock> FARM_TWIG = BLOCKS.register("farm_twig",
                () -> new com.stardew.craft.block.decor.FarmTwigBlock(Block.Properties.of().mapColor(MapColor.WOOD)
                        .strength(0.5F).sound(SoundType.WOOD).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<Block> WILD_WEEDS = BLOCKS.register("wild_weeds",
                        () -> new com.stardew.craft.block.nature.WildWeedsBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> PASTURE_GRASS = BLOCKS.register("pasture_grass",
                        () -> new com.stardew.craft.block.nature.PastureGrassBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BLUE_PASTURE_GRASS = BLOCKS.register("blue_pasture_grass",
                        () -> new com.stardew.craft.block.nature.PastureGrassBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()));

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

        public static final DeferredBlock<Block> SHADOW_FOOTPRINT = BLOCKS.register("shadow_footprint",
                        () -> new com.stardew.craft.block.nature.ShadowFootprintBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.NONE)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noCollission()
                                        .noOcclusion()
                                        .instabreak()));

        public static final DeferredBlock<Block> MAGIC_WARP_CIRCLE = BLOCKS.register("magic_warp_circle",
                        () -> new com.stardew.craft.block.decor.MagicWarpCircleBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.NONE)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noCollission()
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()));

        public static final DeferredBlock<Block> DARK_TALISMAN_SEAL = BLOCKS.register("dark_talisman_seal",
                        () -> new com.stardew.craft.block.decor.DarkTalismanSealBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noCollission()
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()));

        /** Story-installed catalog; deliberately has no BlockItem or creative-tab entry. */
        public static final DeferredBlock<Block> WIZARD_BUILDING_CATALOG = BLOCKS.register("wizard_building_catalog",
                        () -> new com.stardew.craft.block.decor.WizardBuildingCatalogBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(-1.0F, 3600000.0F)
                                        .noLootTable()));

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
                                                new net.minecraft.resources.ResourceLocation("stardewcraft", name))))
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
        public static final DeferredBlock<Block> FORAGE_SNOW_YAM          = forage("snow_yam", WINTER);
        public static final DeferredBlock<Block> FORAGE_CROCUS            = forage("crocus", WINTER);
        public static final DeferredBlock<Block> FORAGE_HOLLY             = forage("holly", WINTER);
        // Cave / universal
        public static final DeferredBlock<Block> FORAGE_CAVE_CARROT       = forage("cave_carrot");
        // Beach
        public static final DeferredBlock<Block> FORAGE_NAUTILUS_SHELL    = forage("nautilus_shell", WINTER);
        public static final DeferredBlock<Block> FORAGE_CORAL             = forage("coral");
        public static final DeferredBlock<Block> FORAGE_RAINBOW_SHELL     = forage("rainbow_shell", SUMMER);
        public static final DeferredBlock<Block> FORAGE_SEA_URCHIN        = forage("sea_urchin");
        public static final DeferredBlock<Block> FORAGE_MUSSEL            = forage("mussel");
        public static final DeferredBlock<Block> FORAGE_COCKLE            = forage("cockle");
        public static final DeferredBlock<Block> FORAGE_OYSTER            = forage("oyster");
        public static final DeferredBlock<Block> FORAGE_SEAWEED           = forage("seaweed");
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

        // 矿井建筑外壳，与可采掘石头节点分开。
        public static final DeferredBlock<com.stardew.craft.block.mine.MineBlockedEntryBlock> MINE_BLOCKED_ENTRY = BLOCKS.register("mine_blocked_entry",
                        () -> new com.stardew.craft.block.mine.MineBlockedEntryBlock(Block.Properties.of()
                                        .mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.mine.SkullLobbyAssemblyBlock> SKULL_SHRINE_WALL = BLOCKS.register("skull_shrine_wall",
                        () -> new com.stardew.craft.block.mine.SkullLobbyAssemblyBlock(Block.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                                        .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().dynamicShape().sound(SoundType.STONE)
                                        .lightLevel(state -> com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.emission(
                                                com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_SHRINE_WALL, state)),
                                        com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_SHRINE_WALL));

        public static final DeferredBlock<com.stardew.craft.block.mine.SkullLobbyAssemblyBlock> SKULL_SHRINE_ALTAR = BLOCKS.register("skull_shrine_altar",
                        () -> new com.stardew.craft.block.mine.SkullLobbyAssemblyBlock(Block.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                                        .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().dynamicShape().sound(SoundType.STONE)
                                        .lightLevel(state -> com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.emission(
                                                com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_SHRINE_ALTAR, state)),
                                        com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_SHRINE_ALTAR));

        public static final DeferredBlock<com.stardew.craft.block.mine.SkullLobbyAssemblyBlock> SKULL_CAVERN_DOOR = BLOCKS.register("skull_cavern_door",
                        () -> new com.stardew.craft.block.mine.SkullLobbyAssemblyBlock(Block.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                                        .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().dynamicShape().sound(SoundType.STONE)
                                        .lightLevel(state -> com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.emission(
                                                com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_CAVERN_DOOR, state)),
                                        com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_CAVERN_DOOR));

        public static final DeferredBlock<com.stardew.craft.block.mine.SkullLobbyAssemblyBlock> SKULL_WALL_BRAZIER = BLOCKS.register("skull_wall_brazier",
                        () -> new com.stardew.craft.block.mine.SkullLobbyAssemblyBlock(Block.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                                        .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().dynamicShape().sound(SoundType.STONE)
                                        .lightLevel(state -> com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.emission(
                                                com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_WALL_BRAZIER, state)),
                                        com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_WALL_BRAZIER));

        public static final DeferredBlock<com.stardew.craft.block.mine.SkullLobbyAssemblyBlock> SKULL_STALAGMITE = BLOCKS.register("skull_stalagmite",
                        () -> new com.stardew.craft.block.mine.SkullLobbyAssemblyBlock(Block.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                                        .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().dynamicShape().sound(SoundType.STONE)
                                        .lightLevel(state -> com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.emission(
                                                com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_STALAGMITE, state)),
                                        com.stardew.craft.block.mine.SkullLobbyAssemblyBlock.Kind.SKULL_STALAGMITE));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSerpentPillarBlock> MINE_SERPENT_PILLAR = BLOCKS.register("mine_serpent_pillar",
                        () -> new com.stardew.craft.block.mine.MineSerpentPillarBlock(Block.Properties.of()
                                        .mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).sound(SoundType.STONE)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSpiralColumnBlock> MINE_SPIRAL_COLUMN = BLOCKS.register("mine_spiral_column",
                        () -> new com.stardew.craft.block.mine.MineSpiralColumnBlock(Block.Properties.of()
                                        .mapColor(MapColor.SAND).strength(-1.0F, 3600000.0F).sound(SoundType.STONE)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineDesertWallReliefBlock> MINE_DESERT_WALL_RELIEF = BLOCKS.register("mine_desert_wall_relief",
                        () -> new com.stardew.craft.block.mine.MineDesertWallReliefBlock(Block.Properties.of()
                                        .mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).sound(SoundType.METAL)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineIronWindowBlock> MINE_IRON_WINDOW = BLOCKS.register("mine_iron_window",
                        () -> new com.stardew.craft.block.mine.MineIronWindowBlock(Block.Properties.of()
                                        .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F).sound(SoundType.METAL)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineTimberSupportBlock> MINE_TIMBER_SUPPORT = BLOCKS.register("mine_timber_support",
                        () -> new com.stardew.craft.block.mine.MineTimberSupportBlock(Block.Properties.of()
                                        .mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PaintedDoorBlock> GREEN_PANEL_DOOR = BLOCKS.register("green_panel_door",
                        () -> new com.stardew.craft.block.decor.PaintedDoorBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PaintedDoorBlock> RED_GLASS_DOOR = BLOCKS.register("red_glass_door",
                        () -> new com.stardew.craft.block.decor.PaintedDoorBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR)));

        public static final DeferredBlock<com.stardew.craft.block.decor.ShopGlassDoorBlock> SHOP_GLASS_DOOR = BLOCKS.register("shop_glass_door",
                        () -> new com.stardew.craft.block.decor.ShopGlassDoorBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR)));

        public static final DeferredBlock<com.stardew.craft.block.decor.CeilingPendantBlock> OWL_PENDANT = BLOCKS.register("owl_pendant",
                        () -> new com.stardew.craft.block.decor.CeilingPendantBlock(Block.Properties.of().strength(0.5F).noOcclusion().sound(SoundType.WOOD),
                                        "stardewcraft:block/decor/house/owl_pendant", 3, -4, 6, 13, 16, 10));
        public static final DeferredBlock<com.stardew.craft.block.decor.CeilingPendantBlock> HANGING_BASKET = BLOCKS.register("hanging_basket",
                        () -> new com.stardew.craft.block.decor.CeilingPendantBlock(Block.Properties.of().strength(0.5F).noOcclusion().sound(SoundType.WOOD),
                                        "stardewcraft:block/decor/house/hanging_basket", 2, -3, 4, 14, 16, 12));
        public static final DeferredBlock<com.stardew.craft.port.net.minecraft.world.level.block.TransparentBlock> PALE_BLUE_WINDOW_GLASS = BLOCKS.register("pale_blue_window_glass",
                        () -> new com.stardew.craft.port.net.minecraft.world.level.block.TransparentBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS)));

        public static final DeferredBlock<Block> PALE_CYAN_PLASTER = BLOCKS.register("pale_cyan_plaster",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.TERRACOTTA).mapColor(MapColor.COLOR_LIGHT_BLUE)));

        public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> TEAL_PAINTED_TIMBER = BLOCKS.register("teal_painted_timber",
                        () -> new net.minecraft.world.level.block.RotatedPillarBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).mapColor(MapColor.COLOR_CYAN)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PaintedDoorBlock> BLUE_GLASS_DOOR = BLOCKS.register("blue_glass_door",
                        () -> new com.stardew.craft.block.decor.PaintedDoorBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR)));

        public static final DeferredBlock<Block> CREAM_SIDING = BLOCKS.register("cream_siding",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).mapColor(MapColor.SAND)));

        public static final DeferredBlock<Block> TERRACOTTA_ROOF_TILES = BLOCKS.register("terracotta_roof_tiles",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS).mapColor(MapColor.TERRACOTTA_ORANGE)));

        public static final DeferredBlock<Block> DARK_BROWN_ROOF_TILES = BLOCKS.register("dark_brown_roof_tiles",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS).mapColor(MapColor.TERRACOTTA_BROWN)));

        public static final DeferredBlock<Block> IVORY_SIDING = BLOCKS.register("ivory_siding",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).mapColor(MapColor.SAND)));

        public static final DeferredBlock<com.stardew.craft.block.decor.PaintedDoorBlock> BROWN_GLASS_DOOR = BLOCKS.register("brown_glass_door",
                        () -> new com.stardew.craft.block.decor.PaintedDoorBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_DOOR)));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorWallStaticBlock> PIERRE_SIGN = BLOCKS.register("pierre_sign",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "stardewcraft:block/decor/house/pierre_sign", true));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorWallStaticBlock> CLINIC_SIGN = BLOCKS.register("clinic_sign",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "stardewcraft:block/decor/house/clinic_sign", true));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorWallStaticBlock> SHIP_WHEEL_ORNAMENT = BLOCKS.register("ship_wheel_ornament",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "stardewcraft:block/decor/house/ship_wheel_ornament", true));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorWallStaticBlock> SUN_WALL_ORNAMENT = BLOCKS.register("sun_wall_ornament",
                        () -> new com.stardew.craft.block.decor.MapDecorWallStaticBlock(Block.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), "stardewcraft:block/decor/house/sun_wall_ornament", true));

        public static final DeferredBlock<Block> GRAY_GREEN_MASONRY = BLOCKS.register("gray_green_masonry",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE_BRICKS)
                                        .mapColor(MapColor.TERRACOTTA_LIGHT_GREEN)));

        public static final DeferredBlock<Block> GRAY_VIOLET_ROOF_TILES = BLOCKS.register("gray_violet_roof_tiles",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS)
                                        .mapColor(MapColor.TERRACOTTA_PURPLE)));

        public static final DeferredBlock<Block> BRICK_RED_ROOF_TILES = BLOCKS.register("brick_red_roof_tiles",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS)
                                        .mapColor(MapColor.TERRACOTTA_RED)));

        public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> BLUE_GRAY_TIMBER = BLOCKS.register("blue_gray_timber",
                        () -> new net.minecraft.world.level.block.RotatedPillarBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                                                        .mapColor(MapColor.COLOR_CYAN)));

        public static final DeferredBlock<Block> PALE_BLUE_SIDING = BLOCKS.register("pale_blue_siding",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                                        .mapColor(MapColor.COLOR_LIGHT_BLUE)));

        public static final DeferredBlock<Block> BLUE_PAINTED_PLANKS = BLOCKS.register("blue_painted_planks",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                                        .mapColor(MapColor.COLOR_BLUE)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MinePlanksBlock> MINE_PLANKS = BLOCKS.register("mine_planks",
                        () -> new com.stardew.craft.block.mine.MinePlanksBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineMasonryBlock> MINE_MASONRY = BLOCKS.register("mine_masonry",
                        () -> new com.stardew.craft.block.mine.MineMasonryBlock(Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStepStoneBlock> MINE_STEP_STONE = BLOCKS.register("mine_step_stone",
                        () -> new com.stardew.craft.block.mine.MineStepStoneBlock(Block.Properties.of()
                                        .mapColor(MapColor.STONE).strength(1.0F).sound(SoundType.STONE)
                                        .noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorStaticBlock> GOLDEN_SMALL_CHEST = BLOCKS.register("golden_small_chest",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(MapColor.GOLD).strength(1.5F).sound(SoundType.METAL).noOcclusion(),
                                        "stardewcraft:block/decor/golden_small_chest", true));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineRailBlock> MINE_RAIL = BLOCKS.register("mine_rail",
                        () -> new com.stardew.craft.block.mine.MineRailBlock(Block.Properties.of()
                                        .mapColor(MapColor.METAL).strength(0.7F).sound(SoundType.METAL).noOcclusion().noCollission()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineRailCurveBlock> MINE_RAIL_CURVE = BLOCKS.register("mine_rail_curve",
                        () -> new com.stardew.craft.block.mine.MineRailCurveBlock(Block.Properties.of()
                                        .mapColor(MapColor.METAL).strength(0.7F).sound(SoundType.METAL).noOcclusion().noCollission()));
        public static final DeferredBlock<com.stardew.craft.block.decor.MapDecorStaticBlock> MINECART_POWER_UNIT = BLOCKS.register("minecart_power_unit",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion(),
                                        "block/minecart_power_unit", 0, 0, 2, 32, 13, 14));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_32 = BLOCKS.register("mine_stone_32",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(32, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_38 = BLOCKS.register("mine_stone_38",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(38, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_40 = BLOCKS.register("mine_stone_40",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(40, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_42 = BLOCKS.register("mine_stone_42",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(42, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_668 = BLOCKS.register("mine_stone_668",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(668, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_670 = BLOCKS.register("mine_stone_670",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(670, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_751 = BLOCKS.register("mine_stone_751",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(751, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_8 = BLOCKS.register("mine_stone_8",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(8, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_10 = BLOCKS.register("mine_stone_10",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(10, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_44 = BLOCKS.register("mine_stone_44",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(44, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_34 = BLOCKS.register("mine_stone_34",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(34, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_36 = BLOCKS.register("mine_stone_36",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(36, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_48 = BLOCKS.register("mine_stone_48",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(48, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_50 = BLOCKS.register("mine_stone_50",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(50, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_52 = BLOCKS.register("mine_stone_52",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(52, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_54 = BLOCKS.register("mine_stone_54",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(54, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_290 = BLOCKS.register("mine_stone_290",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(290, Block.Properties.of()
                                        .mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_6 = BLOCKS.register("mine_stone_6",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(6, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_GREEN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_14 = BLOCKS.register("mine_stone_14",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(14, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_2 = BLOCKS.register("mine_stone_2",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(2, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_56 = BLOCKS.register("mine_stone_56",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(56, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_RED).strength(1.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_58 = BLOCKS.register("mine_stone_58",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(58, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_RED).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_760 = BLOCKS.register("mine_stone_760",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(760, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_GRAY).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_762 = BLOCKS.register("mine_stone_762",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(762, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_GRAY).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_764 = BLOCKS.register("mine_stone_764",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(764, Block.Properties.of()
                                        .mapColor(MapColor.GOLD).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_4 = BLOCKS.register("mine_stone_4",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(4, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_12 = BLOCKS.register("mine_stone_12",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(12, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_GREEN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_46 = BLOCKS.register("mine_stone_46",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock(46, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_BLUE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_765 = BLOCKS.register("mine_stone_765",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("765", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_CALICO_EGG_STONE_0 = BLOCKS.register("mine_stone_calico_egg_stone_0",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("CalicoEggStone_0", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_CALICO_EGG_STONE_1 = BLOCKS.register("mine_stone_calico_egg_stone_1",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("CalicoEggStone_1", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_CALICO_EGG_STONE_2 = BLOCKS.register("mine_stone_calico_egg_stone_2",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("CalicoEggStone_2", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_75 = BLOCKS.register("mine_stone_75",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("75", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_76 = BLOCKS.register("mine_stone_76",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("76", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_77 = BLOCKS.register("mine_stone_77",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("77", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_95 = BLOCKS.register("mine_stone_95",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("95", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_GREEN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_343 = BLOCKS.register("mine_stone_343",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("343", Block.Properties.of()
                                        .mapColor(MapColor.STONE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_450 = BLOCKS.register("mine_stone_450",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("450", Block.Properties.of()
                                        .mapColor(MapColor.STONE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_25 = BLOCKS.register("mine_stone_25",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("25", Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_816 = BLOCKS.register("mine_stone_816",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("816", Block.Properties.of()
                                        .mapColor(MapColor.STONE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_817 = BLOCKS.register("mine_stone_817",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("817", Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_818 = BLOCKS.register("mine_stone_818",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("818", Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_819 = BLOCKS.register("mine_stone_819",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("819", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_843 = BLOCKS.register("mine_stone_843",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("843", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_844 = BLOCKS.register("mine_stone_844",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("844", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_845 = BLOCKS.register("mine_stone_845",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("845", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_846 = BLOCKS.register("mine_stone_846",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("846", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_847 = BLOCKS.register("mine_stone_847",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("847", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_849 = BLOCKS.register("mine_stone_849",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("849", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_850 = BLOCKS.register("mine_stone_850",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("850", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_VOLCANO_GOLD_NODE = BLOCKS.register("mine_stone_volcano_gold_node",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("VolcanoGoldNode", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_VOLCANO_COAL_NODE0 = BLOCKS.register("mine_stone_volcano_coal_node0",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("VolcanoCoalNode0", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_VOLCANO_COAL_NODE1 = BLOCKS.register("mine_stone_volcano_coal_node1",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("VolcanoCoalNode1", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_BASIC_COAL_NODE0 = BLOCKS.register("mine_stone_basic_coal_node0",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("BasicCoalNode0", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineStoneBlock> MINE_STONE_BASIC_COAL_NODE1 = BLOCKS.register("mine_stone_basic_coal_node1",
                        () -> new com.stardew.craft.block.mine.MineStoneBlock("BasicCoalNode1", Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_752 = BLOCKS.register("mine_rock_clump_752",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(752, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_754 = BLOCKS.register("mine_rock_clump_754",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(754, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_756 = BLOCKS.register("mine_rock_clump_756",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(756, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_758 = BLOCKS.register("mine_rock_clump_758",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(758, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_CYAN).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_672 = BLOCKS.register("mine_rock_clump_672",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(672, Block.Properties.of()
                                        .mapColor(MapColor.STONE).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_622 = BLOCKS.register("mine_rock_clump_622",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(622, Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));
        public static final DeferredBlock<com.stardew.craft.block.mine.MineRockClumpBlock> MINE_ROCK_CLUMP_148 = BLOCKS.register("mine_rock_clump_148",
                        () -> new com.stardew.craft.block.mine.MineRockClumpBlock(148, Block.Properties.of()
                                        .mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F, 3600000.0F).sound(SoundType.STONE).noOcclusion()));

        public static final DeferredBlock<Block> MINE_EARTH_LOOSE_SOIL = BLOCKS.register("mine_earth_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT)));

        public static final DeferredBlock<Block> MINE_EARTH_WALL = BLOCKS.register("mine_earth_wall",
                        () -> new Block(stoneProps(MapColor.TERRACOTTA_BROWN, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_EARTH_SOIL = BLOCKS.register("mine_earth_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(
                                        Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_EARTH_DARK_SOIL = BLOCKS.register("mine_earth_dark_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_EARTH_DARK_LOOSE_SOIL = BLOCKS.register("mine_earth_dark_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_EARTH_DARK_WALL = BLOCKS.register("mine_earth_dark_wall",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_FROST_DARK_SOIL = BLOCKS.register("mine_frost_dark_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_FROST_DARK_LOOSE_SOIL = BLOCKS.register("mine_frost_dark_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_FROST_DARK_WALL = BLOCKS.register("mine_frost_dark_wall",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_LAVA_DARK_SOIL = BLOCKS.register("mine_lava_dark_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_LAVA_DARK_LOOSE_SOIL = BLOCKS.register("mine_lava_dark_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_LAVA_DARK_WALL = BLOCKS.register("mine_lava_dark_wall",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_DESERT_DARK_SOIL = BLOCKS.register("mine_desert_dark_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_DESERT_DARK_LOOSE_SOIL = BLOCKS.register("mine_desert_dark_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.DEEPSLATE)));
        public static final DeferredBlock<Block> MINE_DESERT_DARK_WALL = BLOCKS.register("mine_desert_dark_wall",
                        () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_FROST_SOIL = BLOCKS.register("mine_frost_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.ICE)));
        public static final DeferredBlock<Block> MINE_FROST_LOOSE_SOIL = BLOCKS.register("mine_frost_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.ICE)));
        public static final DeferredBlock<Block> MINE_FROST_WALL = BLOCKS.register("mine_frost_wall",
                        () -> new Block(stoneProps(MapColor.ICE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_LAVA_SOIL = BLOCKS.register("mine_lava_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.COLOR_PURPLE)));
        public static final DeferredBlock<Block> MINE_LAVA_LOOSE_SOIL = BLOCKS.register("mine_lava_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.COLOR_PURPLE)));
        public static final DeferredBlock<Block> MINE_LAVA_WALL = BLOCKS.register("mine_lava_wall",
                        () -> new Block(stoneProps(MapColor.COLOR_PURPLE, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineSoilBlock> MINE_DESERT_SOIL = BLOCKS.register("mine_desert_soil",
                        () -> new com.stardew.craft.block.mine.MineSoilBlock(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.SAND)));
        public static final DeferredBlock<Block> MINE_DESERT_LOOSE_SOIL = BLOCKS.register("mine_desert_loose_soil",
                        () -> new Block(Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DIRT).mapColor(MapColor.SAND)));
        public static final DeferredBlock<Block> MINE_DESERT_WALL = BLOCKS.register("mine_desert_wall",
                        () -> new Block(stoneProps(MapColor.SAND, SoundType.STONE, 5.0F)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineLampBlock> MINE_LAMP = BLOCKS.register("mine_lamp",
                        () -> new com.stardew.craft.block.mine.MineLampBlock(Block.Properties.of()
                                        .mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.LANTERN).noCollission().noOcclusion()
                                        .lightLevel(state -> state.getValue(com.stardew.craft.block.mine.MineLampBlock.LIT) ? 15 : 0)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineCanopyBlock> MINE_CANOPY = BLOCKS.register("mine_canopy",
                        () -> new com.stardew.craft.block.mine.MineCanopyBlock(Block.Properties.of()
                                        .mapColor(MapColor.PLANT).strength(0.3F).sound(SoundType.GRASS).noCollission().noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineWallDecorationBlock> MINE_VINES = BLOCKS.register("mine_vines",
                        () -> new com.stardew.craft.block.mine.MineWallDecorationBlock(Block.Properties.of()
                                        .mapColor(MapColor.PLANT).strength(0.3F).sound(SoundType.GRASS).noCollission().noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineWallDecorationBlock> FROST_WALL_ICE = BLOCKS.register("frost_wall_ice",
                        () -> new com.stardew.craft.block.mine.MineWallDecorationBlock(Block.Properties.of()
                                        .mapColor(MapColor.ICE).strength(0.3F).sound(SoundType.GLASS).noCollission().noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineWallDecorationBlock> LAVA_MINE_VINES = BLOCKS.register("lava_mine_vines",
                        () -> new com.stardew.craft.block.mine.MineWallDecorationBlock(Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).strength(0.3F).sound(SoundType.GRASS).noCollission().noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineWallDecorationBlock> DESERT_WALL_CRUST = BLOCKS.register("desert_wall_crust",
                        () -> new com.stardew.craft.block.mine.MineWallDecorationBlock(Block.Properties.of()
                                        .mapColor(MapColor.SAND).strength(0.3F).sound(SoundType.SAND).noCollission().noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineWallFlakesBlock> MINE_DESERT_WALL_FLAKES = BLOCKS.register("mine_desert_wall_flakes",
                        () -> new com.stardew.craft.block.mine.MineWallFlakesBlock(Block.Properties.of()
                                        .mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.STONE).noCollission().noOcclusion()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
        // 矿井：电梯（模型由资源包提供，有朝向）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ELEVATOR = BLOCKS.register("elevator",
                        () -> new com.stardew.craft.block.mine.ElevatorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .requiresCorrectToolForDrops()
                                        .strength(-1.0F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                        .noOcclusion()));

        // 矿井：下楼梯子（挖石头出现的传送点）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_LADDER = BLOCKS.register("mine_ladder",
                        () -> new com.stardew.craft.block.mine.MineLadderBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.LADDER)
                                        .strength(-1.0F, 3600000.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion()));

        public static final DeferredBlock<com.stardew.craft.block.mine.MineCoalBackpackBlock> MINE_COAL_BACKPACK = BLOCKS.register("mine_coal_backpack",
                        () -> new com.stardew.craft.block.mine.MineCoalBackpackBlock(Block.Properties.of()
                                        .mapColor(MapColor.COLOR_BROWN).sound(SoundType.WOOL)
                                        .strength(-1.0F, 3600000.0F).noLootTable()
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion()));

        // 矿井：木桶（仿 SDV BreakableContainer，可挖掘/武器打碎，掉落战利品）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_EARTH_WEEDS = BLOCKS.register("mine_earth_weeds",
                        () -> new com.stardew.craft.block.mine.MineGroundWeedsBlock(Block.Properties.of()
                                        .mapColor(MapColor.PLANT).sound(SoundType.GRASS).strength(0.1F).noOcclusion().noLootTable()));

        public static final DeferredBlock<Block> MINE_LAVA_WEEDS = BLOCKS.register("mine_lava_weeds",
                        () -> new com.stardew.craft.block.mine.MineGroundWeedsBlock(Block.Properties.of()
                                        .mapColor(MapColor.COLOR_PURPLE).sound(SoundType.GRASS).strength(0.1F).noOcclusion().noLootTable()));

        public static final DeferredBlock<Block> MINE_ICE_DEBRIS = BLOCKS.register("mine_ice_debris",
                        () -> new com.stardew.craft.block.mine.MineIceDebrisBlock(Block.Properties.of()
                                        .mapColor(MapColor.ICE).sound(SoundType.GLASS).strength(0.2F).noOcclusion().noLootTable()));

        public static final DeferredBlock<Block> MINE_BARREL = BLOCKS.register("mine_barrel",
                        () -> new com.stardew.craft.block.mine.MineBarrelBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(0.6F, 2.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion()));

        public static final DeferredBlock<Block> MINE_CRATE = BLOCKS.register("mine_crate",
                        () -> new com.stardew.craft.block.mine.MineBarrelBlock(Block.Properties.of()
                                        .mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(0.6F, 2.0F)
                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion(), true));

        // 矿井：宝箱（per-player 独立库存，不可破坏）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_CHEST = BLOCKS.register("mine_chest",
                        () -> new com.stardew.craft.block.mine.MineChestBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(-1.0F, 3600000.0F) // 不可破坏
                                        .noOcclusion()));

        // 矿井：四格高出口木梯，保留离矿确认交互
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINE_EXIT = BLOCKS.register("mine_exit",
                        () -> new com.stardew.craft.block.mine.MineExitBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.LADDER)
                                        .strength(-1.0F, 3600000.0F) // 不可破坏
                                        .noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion()));
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
        // 沙漠节日三花雕像
        public static final DeferredBlock<Block> CALICO_STATUE = BLOCKS.register("calico_statue",
                        () -> new CalicoStatueBlock(stoneProps(MapColor.COLOR_PURPLE, SoundType.STONE, 5.0F)));

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

    public static final DeferredBlock<Block> TARO_ROOT_CROP = BLOCKS.register("taro_root_crop",
            () -> new com.stardew.craft.block.crop.TaroRootCropBlock());

    public static final DeferredBlock<Block> PINEAPPLE_CROP = BLOCKS.register("pineapple_crop",
            () -> new com.stardew.craft.block.crop.PineappleCropBlock());

    public static final DeferredBlock<Block> CACTUS_FRUIT_CROP = BLOCKS.register("cactus_fruit_crop",
            () -> new com.stardew.craft.block.crop.CactusFruitCropBlock());

    public static final DeferredBlock<Block> QI_FRUIT_CROP = BLOCKS.register("qi_fruit_crop",
            () -> new com.stardew.craft.block.crop.QiFruitCropBlock());

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
                                                .dynamicShape()
                                                .noOcclusion()));
        }

        @SuppressWarnings("null")
        private static DeferredBlock<Block> newTreeBranch(String name) {
                return BLOCKS.register(name + "_branch",
                                () -> new com.stardew.craft.block.tree.NewTreePartBlock(newTreeWoodProps().noOcclusion(), true));
        }

        // 预制树组件与木制建筑方块
        private static DeferredBlock<Block> decorativeLeaves(String name) {
                return BLOCKS.register(name, () -> new com.stardew.craft.block.tree.StardewLeavesBlock(
                                Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_LEAVES).dynamicShape()));
        }

        public static final DeferredBlock<Block> BLOSSOM_LEAVES = decorativeLeaves("blossom_leaves");
        public static final DeferredBlock<Block> FINE_LEAVES = decorativeLeaves("fine_leaves");
        public static final DeferredBlock<Block> POINTED_LEAVES = decorativeLeaves("pointed_leaves");

        private static DeferredBlock<Block> forestCanopyLeaves(String name) {
                return BLOCKS.register(name, () -> new com.stardew.craft.block.tree.StardewLeavesBlock(
                                Block.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_LEAVES).dynamicShape(), true));
        }

        public static final DeferredBlock<Block> FOREST_LEAVES = forestCanopyLeaves("forest_leaves");
        public static final DeferredBlock<Block> BROADLEAF_LEAVES = forestCanopyLeaves("broadleaf_leaves");

        public static final DeferredBlock<Block> OAK_ROOT = newTreeRoot("oak");
        public static final DeferredBlock<Block> OAK_LOG = newTreeLog("oak");
        public static final DeferredBlock<Block> OAK_LEAVES = newTreeLeaves("oak");
        public static final DeferredBlock<Block> OAK_LEAVES_QUESTION = BLOCKS.register("oak_leaves_question",
                        () -> new com.stardew.craft.block.tree.StardewLeavesBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                        .strength(0.2F)
                                        .noCollission()
                                        .noOcclusion().dynamicShape(), true));
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

        public static final DeferredBlock<Block> TEA_BUSH = BLOCKS.register("tea_bush",
                        () -> new com.stardew.craft.block.nature.TeaBushBlock(
                                        Block.Properties.of()
                                                        .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                                                        .sound(net.minecraft.world.level.block.SoundType.GRASS)
                                                        .noOcclusion()
                                                        .strength(2.0F, 3.0F)
                                                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

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
        public static final DeferredBlock<Block> WOOD_SIGN = BLOCKS.register("wood_sign",
                        () -> new com.stardew.craft.block.utility.WoodSignBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion().strength(1.0F).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), false));

        public static final DeferredBlock<Block> WOOD_WALL_SIGN = BLOCKS.register("wood_wall_sign",
                        () -> new com.stardew.craft.block.utility.WoodSignBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion().strength(1.0F).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK), true));

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

        public static final DeferredBlock<Block> DECONSTRUCTOR = BLOCKS.register("deconstructor",
                () -> new com.stardew.craft.block.utility.ReclamationMachineBlock(Block.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                        .sound(net.minecraft.world.level.block.SoundType.METAL).noOcclusion()
                        .strength(1.5F, 3.0F), "deconstructor"));

        public static final DeferredBlock<Block> WOOD_CHIPPER = BLOCKS.register("wood_chipper",
                () -> new com.stardew.craft.block.utility.ReclamationMachineBlock(Block.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                        .sound(net.minecraft.world.level.block.SoundType.METAL).noOcclusion()
                        .strength(1.5F, 3.0F), "wood_chipper"));

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

                public static final DeferredBlock<Block> GARDEN_POT = BLOCKS.register("garden_pot",
                                () -> new com.stardew.craft.block.utility.GardenPotBlock(
                                                Block.Properties.of()
                                                                .mapColor(MapColor.TERRACOTTA_ORANGE)
                                                                .sound(SoundType.DECORATED_POT)
                                                                .randomTicks()
                                                                .noOcclusion()
                                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                                                .requiresCorrectToolForDrops()
                                                                .strength(1.5F, 3.0F)));

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
                                                                "stardewcraft:block/decor/special_orders/special_orders_board/spring"));

                public static final DeferredBlock<Block> PRIZE_TICKET_BOX = BLOCKS.register("prize_ticket_box",
                                () -> new com.stardew.craft.block.decor.PrizeTicketBoxBlock(Block.Properties.of()
                                                .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                                                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                                                .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()));

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
                public static final DeferredBlock<Block> STATUE_OF_ENDLESS_FORTUNE =
                                BLOCKS.register("statue_of_endless_fortune",
                                                () -> new com.stardew.craft.block.utility.DailyStatueBlock(
                                                                com.stardew.craft.blockentity.DailyStatueBlockEntity.Kind.ENDLESS_FORTUNE,
                                                                "stardewcraft:block/utility/statue_of_endless_fortune",
                                                                Block.Properties.of()
                                                                                .mapColor(MapColor.GOLD)
                                                                                .sound(SoundType.METAL)
                                                                                .noOcclusion()
                                                                                .requiresCorrectToolForDrops()
                                                                                .strength(3.0F, 6.0F)));
                public static final DeferredBlock<Block> STATUE_OF_PERFECTION =
                                BLOCKS.register("statue_of_perfection",
                                                () -> new com.stardew.craft.block.utility.DailyStatueBlock(
                                                                com.stardew.craft.blockentity.DailyStatueBlockEntity.Kind.PERFECTION,
                                                                "stardewcraft:block/utility/statue_of_perfection",
                                                                Block.Properties.of()
                                                                                .mapColor(MapColor.COLOR_PURPLE)
                                                                                .sound(SoundType.METAL)
                                                                                .noOcclusion()
                                                                                .requiresCorrectToolForDrops()
                                                                                .strength(5.0F, 10.0F)));


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

        public static final DeferredBlock<Block> BIG_CHEST = BLOCKS.register("big_chest",
                () -> new com.stardew.craft.block.utility.StorageChestBlock(Block.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                        .sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(1.5F, 3.0F),
                        com.stardew.craft.block.utility.ChestVariant.BIG_WOOD));

        public static final DeferredBlock<Block> BIG_STONE_CHEST = BLOCKS.register("big_stone_chest",
                () -> new com.stardew.craft.block.utility.StorageChestBlock(Block.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                        .sound(net.minecraft.world.level.block.SoundType.STONE).noOcclusion().strength(1.5F, 3.0F),
                        com.stardew.craft.block.utility.ChestVariant.BIG_STONE));

        public static final DeferredBlock<Block> JUNIMO_CHEST = BLOCKS.register("junimo_chest",
                () -> new com.stardew.craft.block.utility.StorageChestBlock(Block.Properties.of()
                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                        .sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion().strength(1.5F, 3.0F),
                        com.stardew.craft.block.utility.ChestVariant.JUNIMO));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> FRIDGE = BLOCKS.register("fridge",
                        () -> new com.stardew.craft.block.utility.FridgeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F), "stardewcraft:block/utility/fridge"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MAILBOX = BLOCKS.register("mailbox",
                        () -> new com.stardew.craft.block.utility.MailboxBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> MINI_SHIPPING_BIN = BLOCKS.register("mini_shipping_bin",
                        () -> new com.stardew.craft.block.utility.MiniShippingBinBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion().strength(1.5F, 3.0F)));

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
        public static final DeferredBlock<Block> UPGRADE_NOTICE = BLOCKS.register("upgrade_notice",
                        () -> new com.stardew.craft.building.runtime.UpgradeNoticeBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
        public static final DeferredBlock<Block> CONSTRUCTION_FENCE = BLOCKS.register("construction_fence",
                () -> new com.stardew.craft.building.runtime.ConstructionFenceBlock(
                        net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().sound(SoundType.WOOD)));

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
                        () -> new com.stardew.craft.block.utility.RuntimeSiloManagerBlock(Block.Properties.of()
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
        public static final DeferredBlock<Block> GREENHOUSE_MANAGER = BLOCKS.register("greenhouse_manager",
                        () -> new com.stardew.craft.block.utility.GreenhouseManagerBlock(Block.Properties.of()
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

        public static final DeferredBlock<Block> POND_STONE = BLOCKS.register("pond_stone",
                        () -> new com.stardew.craft.block.utility.PondStoneBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion().strength(1.5F, 6.0F)));

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

        public static final DeferredBlock<Block> UPRIGHT_PIANO = BLOCKS.register("upright_piano",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.0F, 3.0F), "stardewcraft:decor/common/upright_piano"));

        public static final DeferredBlock<Block> JUNIMO_PLUSH = BLOCKS.register("junimo_plush",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .noOcclusion()
                                        .strength(0.5F, 1.0F), "stardewcraft:decor/common/junimo_plush"));

        public static final DeferredBlock<Block> STONE_JUNIMO = BLOCKS.register("stone_junimo",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .requiresCorrectToolForDrops()
                                        .noOcclusion()
                                        .strength(1.5F, 6.0F), "stardewcraft:decor/common/stone_junimo"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BED_1 = BLOCKS.register("bed_1",
                        () -> new com.stardew.craft.block.decor.BedDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/bed_1", false));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BED_2 = BLOCKS.register("bed_2",
                        () -> new com.stardew.craft.block.decor.BedDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/bed_2", true));

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
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
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
        public static final DeferredBlock<Block> LIGHT_8 = BLOCKS.register("light_8",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_8"));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> LIGHT_9 = BLOCKS.register("light_9",
                        () -> new com.stardew.craft.block.decor.ToggleableWallLightBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.2F), "stardewcraft:decor/common/light_9"));

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
        public static final DeferredBlock<Block> STREET_LAMP = BLOCKS.register("street_lamp",
                        () -> new com.stardew.craft.block.decor.StreetLampBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.LANTERN)
                                        .noOcclusion()
                                        .strength(0.5F)));

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
                                        0, 0, -16, 16, 25, 32, true));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SUPERMARKET_SHELF_2 = BLOCKS.register("supermarket_shelf_2",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 1.0F), "stardewcraft:decor/common/supermarket_shelf_2",
                                        0, 0, -16, 16, 25, 32, true));

        // Joja 超市相关：快递盒、购物篮、购物车和冰柜各自独立。
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JOJA_SUPERMARKET_CRATE = BLOCKS.register("joja_supermarket_crate",
                        () -> new com.stardew.craft.block.decor.JojaParcelBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .strength(0.8F, 1.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SUPERMARKET_CART = BLOCKS.register("supermarket_cart",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/supermarket_cart",
                                        0, 0, 0, 16, 16, 32));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> SHOPPING_BASKET = BLOCKS.register("shopping_basket",
                        () -> new com.stardew.craft.block.decor.MapDecorStaticBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .strength(0.8F, 1.0F), "stardewcraft:decor/common/shopping_basket",
                                        2, 0, 0, 14, 9, 16));

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
        public static final DeferredBlock<Block> SEBASTIAN_COMPUTER = BLOCKS.register("sebastian_computer",
                        () -> new com.stardew.craft.block.decor.SebastianComputerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion().strength(0.8F, 2.0F)));
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
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/common/barrel_5", true));
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
                                        .strength(1.5F, 6.0F), "stardewcraft:block/decor/grandpa_shrine/spring/grandpa_shrine"));

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

        // ── Wizard buildings: footprint and collision are derived from each GeckoLib model. ──
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> JUNIMO_HUT = BLOCKS.register("junimo_hut",
                        () -> new com.stardew.craft.block.utility.WizardBuildingBlock(Block.Properties.of()
                                        .mapColor(MapColor.PLANT).sound(SoundType.WOOD).noOcclusion()
                                        .strength(2.0F, 6.0F),
                                        com.stardew.craft.block.utility.WizardBuildingKind.JUNIMO_HUT));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> EARTH_OBELISK = BLOCKS.register("earth_obelisk",
                        () -> wizardObelisk(com.stardew.craft.block.utility.WizardBuildingKind.EARTH_OBELISK));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WATER_OBELISK = BLOCKS.register("water_obelisk",
                        () -> wizardObelisk(com.stardew.craft.block.utility.WizardBuildingKind.WATER_OBELISK));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> DESERT_OBELISK = BLOCKS.register("desert_obelisk",
                        () -> wizardObelisk(com.stardew.craft.block.utility.WizardBuildingKind.DESERT_OBELISK));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> ISLAND_OBELISK = BLOCKS.register("island_obelisk",
                        () -> wizardObelisk(com.stardew.craft.block.utility.WizardBuildingKind.ISLAND_OBELISK));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> GOLD_CLOCK = BLOCKS.register("gold_clock",
                        () -> new com.stardew.craft.block.utility.WizardBuildingBlock(Block.Properties.of()
                                        .mapColor(MapColor.GOLD).sound(SoundType.METAL).noOcclusion()
                                        .strength(6.0F, 12.0F),
                                        com.stardew.craft.block.utility.WizardBuildingKind.GOLD_CLOCK,
                                        -12.0D, 0.0D, -12.0D, 28.0D, 48.0D, 12.0D));

        private static Block wizardObelisk(com.stardew.craft.block.utility.WizardBuildingKind kind) {
                return new com.stardew.craft.block.utility.WizardBuildingBlock(Block.Properties.of()
                                .mapColor(MapColor.COLOR_PURPLE).sound(SoundType.STONE).noOcclusion()
                                .strength(6.0F, 12.0F), kind);
        }

        // ── Giant Crops (3×3×2 static model cells, spawned via GiantCropSpawner only) ──
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
        public static final DeferredBlock<Block> GIANT_QI_FRUIT = BLOCKS.register("giant_qi_fruit",
                        () -> new com.stardew.craft.block.crop.giant.GiantQiFruitBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
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
                        () -> new com.stardew.craft.block.decor.BookshelfWallDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(0.8F, 1.6F), "stardewcraft:decor/wall_decor/common/bookshelf_wall_3_1",
                                        -0.5, 2.5, 8.5, 16.5, 13.5, 16.5));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOKSHELF_TALL_1 = BLOCKS.register("bookshelf_tall_1",
                        () -> new com.stardew.craft.block.decor.BookshelfStaticDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:decor/common/bookshelf_3_2_display",
                                        -16, 0, 4, 16, 32, 16));
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> BOOKSHELF_TALL_2 = BLOCKS.register("bookshelf_tall_2",
                        () -> new com.stardew.craft.block.decor.BookshelfGeoDecorBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.0F, 2.0F), "stardewcraft:decor/common/bookshelf_3_3_display",
                                        -15, 0, 5, 17, 48, 16));

        @SuppressWarnings("null")
        public static final DeferredBlock<LegacyWallpaperBlock> WALLPAPER_BLOCK = BLOCKS.register("wallpaper_block",
                        () -> new LegacyWallpaperBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOL)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOL)
                                        .strength(0.8F, 1.0F)));

        public static final Map<String, DeferredBlock<WallpaperBlock>> WALLPAPER_STYLES = registerWallpaperStyles();

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
        public static final DeferredBlock<Block> CLUB_COMPUTER = BLOCKS.register("club_computer",
                        () -> new com.stardew.craft.block.casino.ClubComputerBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .requiresCorrectToolForDrops()
                                        .noOcclusion()
                                        .strength(3.0F, 6.0F)));
        public static final DeferredBlock<Block> CALICO_JACK_TABLE = BLOCKS.register("calico_jack_table",
                        () -> new com.stardew.craft.block.casino.CalicoJackTableBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(1.5F, 3.0F)));
        public static final DeferredBlock<Block> SLOT_MACHINE = BLOCKS.register("slot_machine",
                        () -> new com.stardew.craft.block.casino.CasinoInteractiveBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .requiresCorrectToolForDrops()
                                        .noOcclusion()
                                        .strength(3.0F, 6.0F),
                                        "stardewcraft:casino/slot_machine",
                                        0.0D, 0.0D, 0.0D, 16.0D, 26.5D, 16.0D,
                                        com.stardew.craft.block.casino.CasinoInteractiveBlock.Kind.SLOT_MACHINE));
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

        public static final DeferredBlock<Block> ARTIFACT_SPOT = BLOCKS.register("artifact_spot",
                        () -> new com.stardew.craft.block.nature.SurfaceArtifactSpotBlock(Block.Properties.of()
                                        .sound(net.minecraft.world.level.block.SoundType.GRAVEL), false));
        public static final DeferredBlock<Block> SEED_SPOT = BLOCKS.register("seed_spot",
                        () -> new com.stardew.craft.block.nature.SurfaceArtifactSpotBlock(Block.Properties.of()
                                        .sound(net.minecraft.world.level.block.SoundType.GRASS), true));

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

        // 齐先生任务：隧道墙上的保险箱（地图固定方块，不注册对应物品）
        @SuppressWarnings("null")
        public static final DeferredBlock<Block> QI_TUNNEL_SAFE = BLOCKS.register("qi_tunnel_safe",
                        () -> new com.stardew.craft.block.utility.MrQiTunnelSafeBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                                        .sound(net.minecraft.world.level.block.SoundType.METAL)
                                        .noOcclusion()
                                        .noLootTable()
                                        .strength(-1.0F, 3600000.0F)
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
        public static final DeferredBlock<Block> TEMPLATE_WORKBENCH = BLOCKS.register("template_workbench",
                        () -> new com.stardew.craft.block.utility.TemplateWorkbenchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> WOOD_WORKBENCH = BLOCKS.register("wood_workbench",
                        () -> new com.stardew.craft.block.utility.WoodWorkbenchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                                        .sound(net.minecraft.world.level.block.SoundType.WOOD)
                                        .noOcclusion()
                                        .strength(2.5F, 3.0F)));

        @SuppressWarnings("null")
        public static final DeferredBlock<Block> STONE_WORKBENCH = BLOCKS.register("stone_workbench",
                        () -> new com.stardew.craft.block.utility.StoneWorkbenchBlock(Block.Properties.of()
                                        .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                                        .sound(net.minecraft.world.level.block.SoundType.STONE)
                                        .noOcclusion()
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
