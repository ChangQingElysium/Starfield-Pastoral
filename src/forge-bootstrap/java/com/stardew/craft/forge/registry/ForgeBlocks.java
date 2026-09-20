package com.stardew.craft.forge.registry;

import com.stardew.craft.forge.ForgeBootstrap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.20.1 registrations for the first complete building-material slice.
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

    private ForgeBlocks() {
    }
}
