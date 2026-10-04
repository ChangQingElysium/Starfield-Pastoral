package com.stardew.craft.block.terrain;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class TerrainSlabBlock extends SlabBlock implements TerrainShapeBlock {
    private final Kind kind;
    protected TerrainSlabBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }
    @Override public Kind terrainKind() { return kind; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state != null && state.getValue(TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) return state;
        return state == null ? null : TerrainVariants.placement(state, context);
    }
    public static final class Grass extends TerrainSlabBlock {
        public Grass(Properties properties) { super(Kind.GRASS, properties); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder); builder.add(TerrainVariants.GRASS);
        }
    }
    public static final class DarkGrass extends TerrainSlabBlock {
        public DarkGrass(Properties properties) { super(Kind.DARK_GRASS, properties); }
    }
    public static final class Dirt extends TerrainSlabBlock {
        public Dirt(Properties properties) { super(Kind.DIRT, properties); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder); builder.add(TerrainVariants.DIRT);
        }
    }
    public static final class Sand extends TerrainSlabBlock {
        public Sand(Properties properties) { super(Kind.SAND, properties); }
    }
    public static final class Cliff extends TerrainSlabBlock {
        public Cliff(Properties properties) { super(Kind.CLIFF, properties); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder); builder.add(TerrainVariants.CLIFF);
        }
    }
}
