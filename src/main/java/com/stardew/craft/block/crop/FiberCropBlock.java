package com.stardew.craft.block.crop;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class FiberCropBlock extends StardewCropBlock {
    private static final int[] PHASE_DAYS = new int[]{1, 2, 2, 2};
    private static final int[] OUTLINE_HEIGHTS = new int[]{28, 29, 29, 29};
    private static final int[] OUTLINE_WIDTHS = new int[]{13, 13, 14, 13};
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    @SuppressWarnings("null")
    public FiberCropBlock() {
        super(Properties.of()
            .mapColor(MapColor.PLANT)
            .pushReaction(PushReaction.DESTROY)
            .sound(SoundType.CROP), false);
        registerDefaultState(defaultBlockState().setValue(AGE, 0).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.FIBER_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.FIBER;
    }

    @Override
    protected boolean isInSeason(Level level) {
        return true;
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
        return new ItemStack(ModItems.FIBER.get());
    }

    @Override
    protected int getHarvestMinStack() {
        return 4;
    }

    @Override
    protected int getHarvestMaxStack() {
        return 7;
    }

    @Override
    protected double getExtraHarvestChance() {
        return 0.01D;
    }

    @Override
    protected int getHarvestQuality(net.minecraft.util.RandomSource random, int fertilizerLevel, int farmingLevel) {
        return QualityHelper.NORMAL;
    }

    @Override
    protected HarvestMethod getHarvestMethod() {
        return HarvestMethod.SCYTHE;
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
    protected void addExtraProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (com.stardew.craft.block.utility.GardenPotBlock.isPottedPlant(level, pos, state)) return net.minecraft.world.phys.shapes.Shapes.empty();
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
        String modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age + ",half=" + half);
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
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.isAir() || !(aboveState.getBlock() == this && aboveState.getValue(HALF) == DoubleBlockHalf.UPPER)) {
                level.setBlock(above, state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
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
        super.growCropOneDay(level, pos, state, true, growthState);
        syncUpper(level, pos);
    }

    @SuppressWarnings("null")
    private void syncUpper(ServerLevel level, BlockPos pos) {
        BlockState lower = level.getBlockState(pos);
        if (lower.getBlock() != this || lower.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        BlockPos above = pos.above();
        BlockState upper = level.getBlockState(above);
        if (upper.getBlock() != this || upper.getValue(HALF) != DoubleBlockHalf.UPPER) {
            level.setBlock(above, lower.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        } else if (upper.getValue(AGE) != lower.getValue(AGE)) {
            level.setBlock(above, upper.setValue(AGE, lower.getValue(AGE)), 3);
        }
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.fiber";
    }
}
