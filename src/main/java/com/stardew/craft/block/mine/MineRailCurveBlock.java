package com.stardew.craft.block.mine;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A complete two-by-two bend, placed and removed as one building component. */
public final class MineRailCurveBlock extends MapDecorStaticBlock {
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);

    public MineRailCurveBlock(Properties properties) {
        super(properties, "block/mine_rail/curve_00", 0, 0, 0, 32, 4, 32);
        registerDefaultState(defaultBlockState().setValue(SECTION, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SECTION);
    }

    @Override protected BlockState extensionState(BlockState state, BlockPos offset) {
        int x = offset.getX(), z = offset.getZ();
        BlockPos local = switch (state.getValue(FACING)) {
            case EAST -> new BlockPos(z, 0, -x);
            case SOUTH -> new BlockPos(-x, 0, -z);
            case WEST -> new BlockPos(-z, 0, x);
            default -> offset;
        };
        return super.extensionState(state, offset).setValue(SECTION, local.getX() * 2 + local.getZ());
    }

    // StructureTemplate sorts blocks by position, so negative-facing sections can arrive
    // before their anchor. Validate after the complete placement, not halfway through it.
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!super.canSurvive(state, level, pos)) runWithDropsSuppressed(() -> level.removeBlock(pos, false));
    }

    /** Which outside edge of this cell is an actual open end, in world coordinates. */
    public static boolean opens(BlockState state, Direction edge) {
        if (!(state.getBlock() instanceof MineRailCurveBlock)) return false;
        int section = state.getValue(SECTION);
        Direction local = section == 0 ? Direction.NORTH : section == 3 ? Direction.EAST : null;
        if (local == null) return false;
        for (int i = 0; i < state.getValue(FACING).get2DDataValue() + 2; i++) local = local.getClockWise();
        return local == edge;
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
