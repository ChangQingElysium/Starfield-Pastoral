package com.stardew.craft.block.terrain;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

// PORT(1.20.1): 1.21.1 stairs do not forward ticks/use to the base block (grass base would randomly tick and decay).
public class TerrainStairBlock extends com.stardew.craft.port.PortStairBlock implements TerrainShapeBlock {
    private final Kind kind;
    protected TerrainStairBlock(Kind kind, Properties properties) {
        super(kind.block().defaultBlockState(), properties);
        this.kind = kind;
    }
    @Override public Kind terrainKind() { return kind; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : TerrainVariants.placement(state, context);
    }
    public static final class Grass extends TerrainStairBlock {
        public Grass(Properties properties) { super(Kind.GRASS, properties); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder); builder.add(TerrainVariants.GRASS);
        }
    }
    public static final class DarkGrass extends TerrainStairBlock {
        public DarkGrass(Properties properties) { super(Kind.DARK_GRASS, properties); }
    }
    public static final class Dirt extends TerrainStairBlock {
        public Dirt(Properties properties) { super(Kind.DIRT, properties); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder); builder.add(TerrainVariants.DIRT);
        }
    }
    public static final class Cliff extends TerrainStairBlock {
        public Cliff(Properties properties) { super(Kind.CLIFF, properties); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder); builder.add(TerrainVariants.CLIFF);
        }
    }
}
