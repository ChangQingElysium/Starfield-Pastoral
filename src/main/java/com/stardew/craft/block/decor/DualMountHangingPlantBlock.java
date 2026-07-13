package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

@SuppressWarnings("null")
public class DualMountHangingPlantBlock extends MapDecorStaticBlock {
    public enum Mount implements StringRepresentable {
        WALL("wall"),
        CEILING("ceiling");

        private final String name;

        Mount(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
    private static final VoxelShape CEILING_SHAPE = Block.box(4.0D, 3.0D, 4.0D, 12.0D, 14.0D, 12.0D);
    private static final VoxelShape WALL_NORTH_SHAPE = Block.box(4.0D, 3.0D, 12.0D, 12.0D, 14.0D, 16.0D);

    public DualMountHangingPlantBlock(Properties properties) {
        super(properties, "stardewcraft:decor/plants/hanging_dancing_flower_ceiling");
        registerDefaultState(defaultBlockState().setValue(MOUNT, Mount.CEILING));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MOUNT);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Mount mount;
        Direction facing;
        if (clickedFace.getAxis().isHorizontal()) {
            mount = Mount.WALL;
            facing = clickedFace;
        } else if (clickedFace == Direction.DOWN) {
            mount = Mount.CEILING;
            facing = context.getHorizontalDirection().getOpposite();
        } else {
            return null;
        }

        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(PART, Part.MAIN)
                .setValue(FACING, facing)
                .setValue(MOUNT, mount);
        return state.canSurvive(context.getLevel(), pos) ? state : null;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state,
                               @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos,
                               @Nonnull CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state,
                                        @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        return Set.of(CellOffset.ZERO);
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) {
            return false;
        }
        BlockPos mainPos = state.getValue(PART) == Part.MAIN ? pos : findMainPos(level, pos, state);
        if (mainPos == null) {
            return false;
        }
        BlockState mainState = level.getBlockState(mainPos);
        Mount mount = mainState.is(this) ? mainState.getValue(MOUNT) : state.getValue(MOUNT);
        Direction facing = mainState.is(this) ? mainState.getValue(FACING) : state.getValue(FACING);
        BlockPos supportPos = mount == Mount.CEILING ? mainPos.above() : mainPos.relative(facing.getOpposite());
        Direction supportFace = mount == Mount.CEILING ? Direction.DOWN : facing;
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, supportFace);
    }

    @Override
    protected BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    private VoxelShape shapeForState(BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return Shapes.empty();
        }
        return state.getValue(MOUNT) == Mount.CEILING
                ? CEILING_SHAPE
                : rotateShapeForFacing(WALL_NORTH_SHAPE, state.getValue(FACING));
    }
}
