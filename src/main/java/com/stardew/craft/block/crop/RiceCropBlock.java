package com.stardew.craft.block.crop;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class RiceCropBlock extends StardewCropBlock implements SimpleWaterloggedBlock {
    private static final int[] PHASE_DAYS = new int[]{1, 2, 2, 3};
    private static final int[] OUTLINE_HEIGHTS = new int[]{20, 23, 28, 31};
    private static final int[] OUTLINE_WIDTHS = new int[]{8, 9, 10, 11};
    private static final float PADDY_SPEED_BOOST = 0.25f;

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    @SuppressWarnings("null")
    public RiceCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP), false);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(AGE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.RICE_SHOOT;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.UNMILLED_RICE;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return StardewTimeManager.get().getCurrentSeason() == 0;
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
        ItemStack stack = new ItemStack(ModItems.UNMILLED_RICE.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected HarvestMethod getHarvestMethod() {
        return HarvestMethod.SCYTHE;
    }

    @Override
    protected float getAdditionalSpeedBoost(ServerLevel level, BlockPos pos, CropGrowthManager.CropGrowthState growthState) {
        return isPaddyWatered(level, pos, level.getBlockState(pos)) ? PADDY_SPEED_BOOST : 0f;
    }

    @Override
    protected int getHarvestMaxStack() {
        return 1;
    }

    @Override
    protected float getHarvestMaxIncreasePerFarmingLevel() {
        return 0.1f;
    }

    @Override
    protected double getExtraHarvestChance() {
        return 0.1;
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
    protected int getRegrowDays() {
        return 0;
    }

    @Override
    protected void addExtraProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, WATERLOGGED);
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
        String modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age + ",half=" + half + ",waterlogged=" + state.getValue(WATERLOGGED));
        if (modelId == null || modelId.isBlank()) {
            modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age + ",half=" + half);
        }
        if (modelId != null && !modelId.isBlank()) {
            return ModelVoxelShapeCache.shape(modelId);
        }

        int h = OUTLINE_HEIGHTS[Math.min(age, OUTLINE_HEIGHTS.length - 1)];
        int w = OUTLINE_WIDTHS[Math.min(age, OUTLINE_WIDTHS.length - 1)];
        w = Math.max(0, Math.min(16, w));
        int halfHeight = state.getValue(HALF) == DoubleBlockHalf.UPPER ? Math.max(0, h - 16) : Math.min(16, h);
        if (halfHeight <= 0) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        double halfGap = (16.0 - w) / 2.0;
        return Block.box(halfGap, 0.0, halfGap, 16.0 - halfGap, halfHeight, 16.0 - halfGap);
    }

    @SuppressWarnings({"null", "deprecation"})
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.getBlock() == this && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }

        if (!isPaddyWatered(level, pos, state)) {
            return false;
        }

        BlockState below = level.getBlockState(pos.below());
        boolean farmland = below.getBlock() instanceof FarmBlock;
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
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (!state.canSurvive(level, pos)) {
            return getRemovalReplacement(state);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @SuppressWarnings("null")
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
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
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.isAir() || !(aboveState.getBlock() == this && aboveState.getValue(HALF) == DoubleBlockHalf.UPPER)) {
                boolean upperWaterlogged = level.getFluidState(above).is(FluidTags.WATER);
                level.setBlock(above, state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(WATERLOGGED, upperWaterlogged), 3);
            }
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
                level.setBlock(above, getRemovalReplacement(aboveState), 3);
            }
        } else {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.getBlock() == this && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, getRemovalReplacement(belowState), 3);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected BlockState getPostHarvestState(ServerLevel level, BlockPos pos, BlockState harvestedState) {
        return getRemovalReplacement(harvestedState);
    }

    private BlockState getRemovalReplacement(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED)
                ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @SuppressWarnings("null")
    @Override
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state, boolean watered, CropGrowthManager.CropGrowthState growthState) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return;
        }

        super.growCropOneDay(level, pos, state, watered || isPaddyWatered(level, pos, state), growthState);

        BlockState lower = level.getBlockState(pos);
        if (lower.getBlock() != this) {
            return;
        }
        BlockPos above = pos.above();
        BlockState upper = level.getBlockState(above);
        BlockState expectedUpper = lower.setValue(HALF, DoubleBlockHalf.UPPER)
                .setValue(WATERLOGGED, level.getFluidState(above).is(FluidTags.WATER));
        if (upper.getBlock() != this || upper.getValue(HALF) != DoubleBlockHalf.UPPER) {
            level.setBlock(above, expectedUpper, 3);
        } else if (!upper.equals(expectedUpper)) {
            level.setBlock(above, expectedUpper, 3);
        }
    }

    private boolean isPaddyWatered(LevelReader level, BlockPos pos, BlockState state) {
        return state.hasProperty(WATERLOGGED)
                && state.getValue(WATERLOGGED)
                && level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.rice";
    }
}
