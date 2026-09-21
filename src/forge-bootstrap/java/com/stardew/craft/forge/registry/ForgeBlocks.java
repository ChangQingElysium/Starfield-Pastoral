package com.stardew.craft.forge.registry;

import com.stardew.craft.block.tree.NewTreeLogBlock;
import com.stardew.craft.block.tree.NewTreePartBlock;
import com.stardew.craft.block.tree.StardewLeavesBlock;
import com.stardew.craft.block.terrain.AsphaltRoadBlock;
import com.stardew.craft.block.terrain.PlaygroundSandBlock;
import com.stardew.craft.block.terrain.RoadMarkingBlock;
import com.stardew.craft.forge.ForgeBootstrap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.20.1 registrations for the completed plain-block slices.
 *
 * <p>The source line uses {@code Block.Properties.ofFullCopy} (the 1.21 API).
 * Forge 1.20.1 exposes the equivalent operation as
 * {@link BlockBehaviour.Properties#copy(BlockBehaviour)}; no gameplay property
 * is intentionally changed here.</p>
 */
public final class ForgeBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ForgeBootstrap.MOD_ID);

    public static final RegistryObject<Block> PALE_CYAN_PLASTER = BLOCKS.register(
            "pale_cyan_plaster",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.TERRACOTTA)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE))
    );

    public static final RegistryObject<RotatedPillarBlock> TEAL_PAINTED_TIMBER = BLOCKS.register(
            "teal_painted_timber",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_CYAN))
    );

    public static final RegistryObject<Block> CREAM_SIDING = BLOCKS.register(
            "cream_siding",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.SAND))
    );

    public static final RegistryObject<Block> TERRACOTTA_ROOF_TILES = BLOCKS.register(
            "terracotta_roof_tiles",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRICKS)
                    .mapColor(MapColor.TERRACOTTA_ORANGE))
    );

    public static final RegistryObject<Block> DARK_BROWN_ROOF_TILES = BLOCKS.register(
            "dark_brown_roof_tiles",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRICKS)
                    .mapColor(MapColor.TERRACOTTA_BROWN))
    );

    public static final RegistryObject<Block> IVORY_SIDING = BLOCKS.register(
            "ivory_siding",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.SAND))
    );

    public static final RegistryObject<Block> GRAY_GREEN_MASONRY = BLOCKS.register(
            "gray_green_masonry",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS)
                    .mapColor(MapColor.TERRACOTTA_LIGHT_GREEN))
    );

    public static final RegistryObject<Block> GRAY_VIOLET_ROOF_TILES = BLOCKS.register(
            "gray_violet_roof_tiles",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRICKS)
                    .mapColor(MapColor.TERRACOTTA_PURPLE))
    );

    public static final RegistryObject<Block> BRICK_RED_ROOF_TILES = BLOCKS.register(
            "brick_red_roof_tiles",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRICKS)
                    .mapColor(MapColor.TERRACOTTA_RED))
    );

    public static final RegistryObject<RotatedPillarBlock> BLUE_GRAY_TIMBER = BLOCKS.register(
            "blue_gray_timber",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_CYAN))
    );

    public static final RegistryObject<Block> PALE_BLUE_SIDING = BLOCKS.register(
            "pale_blue_siding",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE))
    );

    public static final RegistryObject<Block> BLUE_PAINTED_PLANKS = BLOCKS.register(
            "blue_painted_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_BLUE))
    );

    public static final RegistryObject<PlaygroundSandBlock> PLAYGROUND_SAND = BLOCKS.register(
            "playground_sand",
            () -> new PlaygroundSandBlock(BlockBehaviour.Properties.copy(Blocks.SAND))
    );

    public static final RegistryObject<AsphaltRoadBlock> ASPHALT_ROAD = BLOCKS.register(
            "asphalt_road",
            () -> new AsphaltRoadBlock(BlockBehaviour.Properties.copy(Blocks.STONE))
    );

    public static final RegistryObject<RoadMarkingBlock> ROAD_DASH = BLOCKS.register(
            "road_dash",
            () -> new RoadMarkingBlock(roadMarkingProperties())
    );

    public static final RegistryObject<RoadMarkingBlock> ROAD_DOUBLE_LINE = BLOCKS.register(
            "road_double_line",
            () -> new RoadMarkingBlock(roadMarkingProperties())
    );

    public static final RegistryObject<Block> MINE_EARTH_LOOSE_SOIL = BLOCKS.register(
            "mine_earth_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT))
    );

    public static final RegistryObject<Block> MINE_EARTH_WALL = BLOCKS.register(
            "mine_earth_wall",
            () -> new Block(stoneProps(MapColor.TERRACOTTA_BROWN, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_EARTH_DARK_LOOSE_SOIL = BLOCKS.register(
            "mine_earth_dark_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.DEEPSLATE))
    );

    public static final RegistryObject<Block> MINE_EARTH_DARK_WALL = BLOCKS.register(
            "mine_earth_dark_wall",
            () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_FROST_DARK_LOOSE_SOIL = BLOCKS.register(
            "mine_frost_dark_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.DEEPSLATE))
    );

    public static final RegistryObject<Block> MINE_FROST_DARK_WALL = BLOCKS.register(
            "mine_frost_dark_wall",
            () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_LAVA_DARK_LOOSE_SOIL = BLOCKS.register(
            "mine_lava_dark_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.DEEPSLATE))
    );

    public static final RegistryObject<Block> MINE_LAVA_DARK_WALL = BLOCKS.register(
            "mine_lava_dark_wall",
            () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_DESERT_DARK_LOOSE_SOIL = BLOCKS.register(
            "mine_desert_dark_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.DEEPSLATE))
    );

    public static final RegistryObject<Block> MINE_DESERT_DARK_WALL = BLOCKS.register(
            "mine_desert_dark_wall",
            () -> new Block(stoneProps(MapColor.DEEPSLATE, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_FROST_LOOSE_SOIL = BLOCKS.register(
            "mine_frost_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.ICE))
    );

    public static final RegistryObject<Block> MINE_FROST_WALL = BLOCKS.register(
            "mine_frost_wall",
            () -> new Block(stoneProps(MapColor.ICE, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_LAVA_LOOSE_SOIL = BLOCKS.register(
            "mine_lava_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.COLOR_PURPLE))
    );

    public static final RegistryObject<Block> MINE_LAVA_WALL = BLOCKS.register(
            "mine_lava_wall",
            () -> new Block(stoneProps(MapColor.COLOR_PURPLE, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_DESERT_LOOSE_SOIL = BLOCKS.register(
            "mine_desert_loose_soil",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.SAND))
    );

    public static final RegistryObject<Block> MINE_DESERT_WALL = BLOCKS.register(
            "mine_desert_wall",
            () -> new Block(stoneProps(MapColor.SAND, SoundType.STONE, 5.0F))
    );

    public static final RegistryObject<Block> MINE_BARRIER = BLOCKS.register(
            "mine_barrier",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.STONE)
                    .strength(-1.0F, 3600000.0F))
    );

    public static final RegistryObject<Block> PALE_BLUE_WINDOW_GLASS = BLOCKS.register(
            "pale_blue_window_glass",
            () -> new GlassBlock(BlockBehaviour.Properties.copy(Blocks.GLASS))
    );

    public static final RegistryObject<Block> OAK_ROOT = newTreeRoot("oak");
    public static final RegistryObject<Block> OAK_LOG = newTreeLog("oak");
    public static final RegistryObject<Block> OAK_LEAVES = newTreeLeaves("oak");
    public static final RegistryObject<Block> OAK_LEAVES_QUESTION = newPersistentLeaves("oak_leaves_question");
    public static final RegistryObject<Block> OAK_BRANCH = newTreeBranch("oak");

    public static final RegistryObject<Block> MAPLE_ROOT = newTreeRoot("maple");
    public static final RegistryObject<Block> MAPLE_LOG = newTreeLog("maple");
    public static final RegistryObject<Block> MAPLE_LEAVES = newTreeLeaves("maple");
    public static final RegistryObject<Block> MAPLE_BRANCH = newTreeBranch("maple");

    public static final RegistryObject<Block> PINE_ROOT = newTreeRoot("pine");
    public static final RegistryObject<Block> PINE_LOG = newTreeLog("pine");
    public static final RegistryObject<Block> PINE_LEAVES = newTreeLeaves("pine");
    public static final RegistryObject<Block> PINE_BRANCH = newTreeBranch("pine");

    public static final RegistryObject<Block> MAHOGANY_ROOT = newTreeRoot("mahogany");
    public static final RegistryObject<Block> MAHOGANY_LOG = newTreeLog("mahogany");
    public static final RegistryObject<Block> MAHOGANY_LEAVES = newTreeLeaves("mahogany");
    public static final RegistryObject<Block> MAHOGANY_BRANCH = newTreeBranch("mahogany");

    public static final RegistryObject<Block> MYSTIC_TREE_ROOT = newTreeRoot("mystic_tree");
    public static final RegistryObject<Block> MYSTIC_TREE_LOG = newTreeLog("mystic_tree");
    public static final RegistryObject<Block> MYSTIC_TREE_LEAVES = newTreeLeaves("mystic_tree");
    public static final RegistryObject<Block> MYSTIC_TREE_BRANCH = newTreeBranch("mystic_tree");

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

    public static final Map<String, RegistryObject<? extends Block>> NEW_TREE_BUILDING_BLOCKS =
            registerNewTreeBuildingBlocks();

    private static BlockBehaviour.Properties newTreeWoodProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F, 3.0F);
    }

    private static RegistryObject<Block> newTreeRoot(String species) {
        return BLOCKS.register(species + "_root",
                () -> new NewTreePartBlock(newTreeWoodProps().noOcclusion(), true));
    }

    private static RegistryObject<Block> newTreeLog(String species) {
        return BLOCKS.register(species + "_log", () -> new NewTreeLogBlock(newTreeWoodProps()));
    }

    private static RegistryObject<Block> newTreeBranch(String species) {
        return BLOCKS.register(species + "_branch",
                () -> new NewTreePartBlock(newTreeWoodProps().noOcclusion(), true));
    }

    private static RegistryObject<Block> newTreeLeaves(String species) {
        return BLOCKS.register(species + "_leaves", () -> new StardewLeavesBlock(
                BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES).dynamicShape()));
    }

    private static RegistryObject<Block> newPersistentLeaves(String name) {
        return BLOCKS.register(name, () -> new StardewLeavesBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.PLANT)
                        .sound(SoundType.GRASS)
                        .strength(0.2F)
                        .noCollission()
                        .noOcclusion()
                        .dynamicShape(), true));
    }

    private static RegistryObject<? extends Block> newTreeLogBlock(String species) {
        return switch (species) {
            case "oak" -> OAK_LOG;
            case "maple" -> MAPLE_LOG;
            case "pine" -> PINE_LOG;
            case "mahogany" -> MAHOGANY_LOG;
            case "mystic_tree" -> MYSTIC_TREE_LOG;
            default -> throw new IllegalArgumentException("Unknown new tree species: " + species);
        };
    }

    private static Map<String, RegistryObject<? extends Block>> registerNewTreeBuildingBlocks() {
        LinkedHashMap<String, RegistryObject<? extends Block>> blocks = new LinkedHashMap<>();

        for (String species : NEW_TREE_WOOD_SPECIES) {
            for (String pattern : NEW_TREE_PLANK_PATTERNS) {
                String baseName = species + "_" + pattern + "planks";
                RegistryObject<Block> planks = BLOCKS.register(baseName, () -> new Block(newTreeWoodProps()));
                blocks.put(baseName, planks);
                blocks.put(baseName + "_stairs",
                        stairsFromAnyBlock(baseName + "_stairs", planks, newTreeWoodProps()));
                blocks.put(baseName + "_slab", slab(baseName + "_slab", newTreeWoodProps()));
                blocks.put(baseName + "_fence", fence(baseName + "_fence", newTreeWoodProps()));
                blocks.put(baseName + "_fence_gate",
                        fenceGate(baseName + "_fence_gate", newTreeWoodProps()));
            }

            String logBaseName = species + "_log";
            RegistryObject<? extends Block> log = newTreeLogBlock(species);
            blocks.put(logBaseName + "_stairs",
                    stairsFromAnyBlock(logBaseName + "_stairs", log, newTreeWoodProps()));
            blocks.put(logBaseName + "_slab", slab(logBaseName + "_slab", newTreeWoodProps()));
        }

        return Collections.unmodifiableMap(blocks);
    }

    private static RegistryObject<StairBlock> stairsFromAnyBlock(
            String name, RegistryObject<? extends Block> base, BlockBehaviour.Properties props) {
        return BLOCKS.register(name, () -> new StairBlock(base.get().defaultBlockState(), props));
    }

    private static RegistryObject<SlabBlock> slab(String name, BlockBehaviour.Properties props) {
        return BLOCKS.register(name, () -> new SlabBlock(props));
    }

    private static RegistryObject<FenceBlock> fence(String name, BlockBehaviour.Properties props) {
        return BLOCKS.register(name, () -> new FenceBlock(props));
    }

    private static RegistryObject<FenceGateBlock> fenceGate(String name, BlockBehaviour.Properties props) {
        return BLOCKS.register(name, () -> new FenceGateBlock(props, WoodType.OAK));
    }

    private static BlockBehaviour.Properties stoneProps(MapColor color, SoundType sound, float hardness) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(sound)
                .strength(hardness, 6.0F);
    }

    private static BlockBehaviour.Properties roadMarkingProperties() {
        return BlockBehaviour.Properties.of()
                .noCollission()
                .noOcclusion()
                .instabreak()
                .sound(SoundType.STONE)
                .pushReaction(PushReaction.DESTROY);
    }

    private ForgeBlocks() {
    }
}
