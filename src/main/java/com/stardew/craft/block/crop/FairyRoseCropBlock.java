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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * 玫瑰仙子作物
 */
public class FairyRoseCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 4, 4, 3};
    private static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 5);
    private static final int COLOR_COUNT = 6;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    @SuppressWarnings("null")
    public FairyRoseCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP), false);
        this.registerDefaultState(this.defaultBlockState().setValue(AGE, 0).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.FAIRY_ROSE_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.FAIRY_ROSE;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        return timeManager.getCurrentSeason() == 2;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.FAIRY_ROSE.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    /**
     * 方块颜色 ↔ 物品颜色映射表。
     * block color 0→item 5, 1→4, 2→3, 3→1, 4→0, 5→2
     */
    private static final int[] BLOCK_TO_ITEM_COLOR = {5, 4, 3, 1, 0, 2};
    private static final int[] ITEM_TO_BLOCK_COLOR = {4, 3, 5, 2, 1, 0};

    public static int itemColorToBlockColor(int itemColor) {
        if (itemColor < 0 || itemColor >= ITEM_TO_BLOCK_COLOR.length) {
            return itemColor;
        }
        return ITEM_TO_BLOCK_COLOR[itemColor];
    }

    @SuppressWarnings("null")
    @Override
    protected ItemStack applyHarvestItemCustomization(ItemStack stack, BlockState state) {
        if (state.hasProperty(COLOR)) {
            @SuppressWarnings("null")
            int blockColor = state.getValue(COLOR);
            int itemColor = BLOCK_TO_ITEM_COLOR[blockColor];
            @SuppressWarnings("null")
            var customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY);
            var tag = customData.copyTag();
            tag.putInt("FlowerColor", itemColor);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            setFlowerVariantModelData(stack, itemColor);
        }
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
    protected int getRegrowDays() {
        return 0;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.fairy_rose";
    }

    @Override
    protected void addExtraProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(COLOR);
        builder.add(HALF);
        builder.add(PLACED_BY_PLAYER);
    }

    @Override
    protected IntegerProperty getColorVariantProperty() {
        return COLOR;
    }

    @Override
    protected int getColorVariantCount() {
        return COLOR_COUNT;
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
        String modelId = ModelVoxelShapeCache.variantModel(blockId, "age=" + age + ",half=" + half);
        if (modelId != null && !modelId.isBlank()) {
            return ModelVoxelShapeCache.shape(modelId);
        }
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? net.minecraft.world.phys.shapes.Shapes.empty()
                : net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 16, 16);
    }

    @SuppressWarnings({ "null", "deprecation" })
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
        // 玩家手动放置的成品花：允许種在自然地表上
        if (!farmland
            && StardewCropBlock.isDecorativeFlowerState(state)
            && StardewCropBlock.isNaturalSoil(below)) {
            farmland = true;
        }
        if (!farmland) return false;
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
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return;
        if (state.is(oldState.getBlock())) { super.onPlace(state, level, pos, oldState, isMoving); return; }
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
        if (state.is(newState.getBlock())) { super.onRemove(state, level, pos, newState, isMoving); return; }
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

    @SuppressWarnings("null")
    @Override
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state, boolean watered, com.stardew.craft.manager.CropGrowthManager.CropGrowthState growthState) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return;
        super.growCropOneDay(level, pos, state, watered, growthState);
        BlockState lower = level.getBlockState(pos);
        if (lower.getBlock() != this) return;
        BlockPos above = pos.above();
        BlockState upper = level.getBlockState(above);
        BlockState expectedUpper = lower.setValue(HALF, DoubleBlockHalf.UPPER);
        if (upper.getBlock() != this || upper.getValue(HALF) != DoubleBlockHalf.UPPER) {
            level.setBlock(above, expectedUpper, 3);
        } else {
            if (!upper.equals(expectedUpper)) {
                level.setBlock(above, expectedUpper, 3);
            }
        }
    }
}
