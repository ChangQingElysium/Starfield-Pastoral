package com.stardew.craft.block.crop;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class SweetGemBerryCropBlock extends StardewCropBlock {
    private static final int[] PHASE_DAYS = new int[]{2, 4, 12, 6};
    private static final int[] OUTLINE_HEIGHTS = new int[]{5, 13, 18, 20};
    private static final int[] OUTLINE_WIDTHS = new int[]{3, 7, 10, 12};
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty MATURE = BooleanProperty.create("mature");

    @SuppressWarnings("null")
    public SweetGemBerryCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP), false);
        registerDefaultState(defaultBlockState()
                .setValue(AGE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(MATURE, Boolean.FALSE));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.RARE_SEED;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.SWEET_GEM_BERRY;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return StardewTimeManager.get().getCurrentSeason() == 2;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected int[] getOutlineHeightsPxByAge() {
        return OUTLINE_HEIGHTS;
    }

    @Override
    protected int[] getOutlineWidthsPxByAge() {
        return OUTLINE_WIDTHS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        ItemStack stack = new ItemStack(ModItems.SWEET_GEM_BERRY.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected boolean canRegrow() {
        return false;
    }

    @Override
    protected int getRegrowAge() {
        return 0;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.sweet_gem_berry";
    }

    @Override
    protected BlockState applyMatureVariant(ServerLevel level, BlockPos pos, BlockState state) {
        return super.applyMatureVariant(level, pos, state).setValue(MATURE, Boolean.TRUE);
    }

    @Override
    protected void addExtraProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(HALF, MATURE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getHalfShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @SuppressWarnings("null")
    private VoxelShape getHalfShape(BlockState state) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        int age = state.getValue(AGE);
        String half = state.getValue(HALF) == DoubleBlockHalf.UPPER ? "upper" : "lower";
        String mature = state.getValue(MATURE) ? "true" : "false";
        String modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age + ",half=" + half + ",mature=" + mature);
        if (modelId != null && !modelId.isBlank()) {
            return ModelVoxelShapeCache.shape(modelId);
        }

        int height = OUTLINE_HEIGHTS[Math.min(age, OUTLINE_HEIGHTS.length - 1)];
        int width = OUTLINE_WIDTHS[Math.min(age, OUTLINE_WIDTHS.length - 1)];
        int halfHeight = state.getValue(HALF) == DoubleBlockHalf.UPPER ? Math.max(0, height - 16) : Math.min(16, height);
        if (halfHeight <= 0) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        double gap = (16.0D - Math.max(0, Math.min(16, width))) / 2.0D;
        return net.minecraft.world.level.block.Block.box(gap, 0.0D, gap, 16.0D - gap, halfHeight, 16.0D - gap);
    }

    @SuppressWarnings({"null", "deprecation"})
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.getBlock() == this && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }

        BlockState below = level.getBlockState(pos.below());
        boolean farmland = below.getBlock() instanceof net.minecraft.world.level.block.FarmBlock;
        if (!farmland) {
            String blockId = below.getBlock().builtInRegistryHolder().key().location().toString().toLowerCase();
            farmland = blockId.contains("farmland");
        }
        if (!farmland) {
            return false;
        }

        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || (above.getBlock() == this && above.getValue(HALF) == DoubleBlockHalf.UPPER);
    }

    @SuppressWarnings("null")
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @SuppressWarnings("null")
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return;
        }
        if (state.is(oldState.getBlock())) {
            super.onPlace(state, level, pos, oldState, isMoving);
            return;
        }
        if (level instanceof ServerLevel) {
            syncUpper((ServerLevel) level, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @SuppressWarnings("null")
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.getBlock() == this && aboveState.getValue(HALF) == DoubleBlockHalf.UPPER) {
                level.setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        } else {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.getBlock() == this && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state, boolean watered, com.stardew.craft.manager.CropGrowthManager.CropGrowthState growthState) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return;
        }
        super.growCropOneDay(level, pos, state, watered, growthState);
        syncUpper(level, pos);
    }

    @SuppressWarnings("null")
    private void syncUpper(ServerLevel level, BlockPos pos) {
        BlockState lower = level.getBlockState(pos);
        if (lower.getBlock() != this || lower.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        BlockPos above = pos.above();
        BlockState upperState = lower.setValue(HALF, DoubleBlockHalf.UPPER);
        BlockState upper = level.getBlockState(above);
        if (upper.getBlock() != this || upper.getValue(HALF) != DoubleBlockHalf.UPPER || upper.getValue(AGE) != lower.getValue(AGE) || upper.getValue(MATURE) != lower.getValue(MATURE)) {
            level.setBlock(above, upperState, 3);
        }
    }
}
