package com.stardew.craft.block;

import com.stardew.craft.forge.registry.ForgeBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Compatibility names for the Forge-port block slice. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = ForgeBlocks.BLOCKS;

    public static final RegistryObject<Block> PALE_CYAN_PLASTER = ForgeBlocks.PALE_CYAN_PLASTER;
    public static final RegistryObject<RotatedPillarBlock> TEAL_PAINTED_TIMBER = ForgeBlocks.TEAL_PAINTED_TIMBER;
    public static final RegistryObject<Block> CREAM_SIDING = ForgeBlocks.CREAM_SIDING;
    public static final RegistryObject<Block> TERRACOTTA_ROOF_TILES = ForgeBlocks.TERRACOTTA_ROOF_TILES;
    public static final RegistryObject<Block> DARK_BROWN_ROOF_TILES = ForgeBlocks.DARK_BROWN_ROOF_TILES;
    public static final RegistryObject<Block> IVORY_SIDING = ForgeBlocks.IVORY_SIDING;
    public static final RegistryObject<Block> GRAY_GREEN_MASONRY = ForgeBlocks.GRAY_GREEN_MASONRY;
    public static final RegistryObject<Block> GRAY_VIOLET_ROOF_TILES = ForgeBlocks.GRAY_VIOLET_ROOF_TILES;
    public static final RegistryObject<Block> BRICK_RED_ROOF_TILES = ForgeBlocks.BRICK_RED_ROOF_TILES;
    public static final RegistryObject<RotatedPillarBlock> BLUE_GRAY_TIMBER = ForgeBlocks.BLUE_GRAY_TIMBER;
    public static final RegistryObject<Block> PALE_BLUE_SIDING = ForgeBlocks.PALE_BLUE_SIDING;
    public static final RegistryObject<Block> BLUE_PAINTED_PLANKS = ForgeBlocks.BLUE_PAINTED_PLANKS;

    private ModBlocks() {
    }
}
