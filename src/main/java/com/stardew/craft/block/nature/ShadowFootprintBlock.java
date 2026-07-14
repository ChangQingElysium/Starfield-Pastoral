package com.stardew.craft.block.nature;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.secretnote.ShadowFootprintData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Debug-only surface decal used to author the Shadow Guy's footprint trail. */
public final class ShadowFootprintBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ShadowFootprintBlock> CODEC = simpleCodec(ShadowFootprintBlock::new);
    public static final EnumProperty<Foot> FOOT = EnumProperty.create("foot", Foot.class);
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 0.0016D, 15.0D);

    public ShadowFootprintBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FOOT, Foot.RIGHT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FOOT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ShadowFootprintData data = ShadowFootprintData.get(serverLevel);
            Foot foot = data.takeNextFoot();
            BlockState authoredState = state.setValue(FOOT, foot);
            if (authoredState != state) {
                level.setBlock(pos, authoredState, Block.UPDATE_CLIENTS);
            }
            data.add(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ShadowFootprintData.get(serverLevel).remove(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    public enum Foot implements StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        private final String name;

        Foot(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
