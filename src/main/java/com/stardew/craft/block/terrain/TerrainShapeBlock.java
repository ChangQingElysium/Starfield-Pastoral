package com.stardew.craft.block.terrain;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A shaped terrain block retains its own item while sharing its parent surface and variant. */
public interface TerrainShapeBlock {
    enum Kind { GRASS, DARK_GRASS, DIRT, SAND, CLIFF;
        public Block block() {
            return switch (this) {
                case GRASS -> ModBlocks.GRASS_BLOCK.get();
                case DARK_GRASS -> ModBlocks.DARK_GRASS_BLOCK.get();
                case DIRT -> ModBlocks.DIRT.get();
                case SAND -> ModBlocks.SAND.get();
                case CLIFF -> ModBlocks.CLIFF.get();
            };
        }
    }
    Kind terrainKind();
    static BlockState material(BlockState state) {
        if (!(state.getBlock() instanceof TerrainShapeBlock shape)) return state;
        BlockState base = shape.terrainKind().block().defaultBlockState();
        var property = TerrainVariants.property(base);
        return property != null && state.hasProperty(property) ? base.setValue(property, state.getValue(property)) : base;
    }
}
