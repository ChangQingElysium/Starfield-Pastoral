package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A repaintable frame with built-in glass; adjacent cells share their outer moulding. */
public final class GridWindowTemplateBlock extends MaterialTemplateBlock {
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 15);

    public GridWindowTemplateBlock(Properties properties) {
        super(TemplateShape.GRID_WINDOW, properties);
        registerDefaultState(defaultBlockState().setValue(CONNECTIONS, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTIONS);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : connect(state, context.getLevel(), context.getClickedPos());
    }

    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos other) {
        return connect(state, level, pos);
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        int mask = state.getValue(CONNECTIONS);
        return super.mirror(state, mirror).setValue(CONNECTIONS, (mask & 5) | ((mask & 2) << 2) | ((mask & 8) >> 2));
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction front = state.getValue(FACING);
        Direction[] sides = {Direction.UP, front.getClockWise(), Direction.DOWN, front.getCounterClockWise()};
        int mask = 0;
        for (int i = 0; i < sides.length; i++) {
            BlockState neighbor = level.getBlockState(pos.relative(sides[i]));
            if (neighbor.is(this) && neighbor.getValue(FACING) == front) mask |= 1 << i;
        }
        return state.setValue(CONNECTIONS, mask);
    }

    static VoxelShape outline(BlockState state) {
        VoxelShape result = Shapes.empty();
        int turns = TemplateShapeCache.turnsFrom(Direction.NORTH, state.getValue(FACING));
        var boxes = new java.util.ArrayList<>(GridWindowProfile.frame(state.getValue(CONNECTIONS)));
        boxes.add(new TemplateBox(0, 0, 5, 16, 16, 6));
        for (TemplateBox b : boxes) {
            double x = b.minX(), z = b.minZ(), maxX = b.maxX(), maxZ = b.maxZ();
            for (int i = 0; i < turns; i++) {
                double oldX = x, oldMaxX = maxX;
                x = 16 - maxZ; maxX = 16 - z; z = oldX; maxZ = oldMaxX;
            }
            result = Shapes.or(result, Shapes.box(x / 16, b.minY() / 16, z / 16, maxX / 16, b.maxY() / 16, maxZ / 16));
        }
        return result.optimize();
    }
}
