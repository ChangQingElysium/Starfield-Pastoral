package com.stardew.craft.forge.registry;

import com.stardew.craft.forge.ForgeBootstrap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
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

    private static BlockBehaviour.Properties stoneProps(MapColor color, SoundType sound, float hardness) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(sound)
                .strength(hardness, 6.0F);
    }

    private ForgeBlocks() {
    }
}
