package com.stardew.craft.block.tree.fruit;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.manager.FruitTreeGrowthManager;
import com.stardew.craft.tree.fruit.FruitTreeRules;
import com.stardew.craft.tree.fruit.FruitTreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;

public class FruitTreeSaplingBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private final FruitTreeType type;

    public FruitTreeSaplingBlock(FruitTreeType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    public FruitTreeType getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, HALF);
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String half = state.getValue(HALF) == DoubleBlockHalf.UPPER ? "upper" : "lower";
        String modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + state.getValue(AGE) + ",half=" + half);
        if (modelId != null && !modelId.isBlank()) {
            return ModelVoxelShapeCache.shape(modelId);
        }
        return Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public PushReaction getPistonPushReaction(@Nonnull BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        BlockState above = level.getBlockState(pos.above());
        return FruitTreeRules.isValidGround(level.getBlockState(pos.below()))
                && (above.isAir() || (above.is(this) && above.getValue(HALF) == DoubleBlockHalf.UPPER));
    }

    @Override
    public BlockState updateShape(@Nonnull BlockState state, @Nonnull Direction direction, @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level, @Nonnull BlockPos pos, @Nonnull BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            super.onPlace(state, level, pos, oldState, movedByPiston);
            return;
        }

        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            BlockPos above = pos.above();
            BlockState upperState = state.setValue(HALF, DoubleBlockHalf.UPPER);
            if (!level.getBlockState(above).is(this)) {
                level.setBlock(above, upperState, Block.UPDATE_ALL);
            }
            if (level instanceof ServerLevel serverLevel) {
                FruitTreeGrowthManager.get(serverLevel).addSapling(serverLevel, pos, type);
            }
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockPos above = pos.above();
                if (level.getBlockState(above).is(this)) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                if (level instanceof ServerLevel serverLevel) {
                    FruitTreeGrowthManager.get(serverLevel).removeSapling(serverLevel, pos);
                }
            } else {
                BlockPos below = pos.below();
                if (level.getBlockState(below).is(this)) {
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
}
