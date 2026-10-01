package com.stardew.craft.block.crop;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.function.Supplier;

/**
 * 蓝爵作物
 */
public class BlueJazzCropBlock extends StardewCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 2, 2, 2};
    private static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 5);
    private static final int COLOR_COUNT = 6;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    @SuppressWarnings("null")
    public BlueJazzCropBlock() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(PLACED_BY_PLAYER, false));
    }

        private static final int[] OUTLINE_HEIGHTS = new int[]{3, 7, 10, 15};
        private static final int[] OUTLINE_WIDTHS = new int[]{1, 1, 1, 6};
    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.BLUE_JAZZ_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.BLUE_JAZZ;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return seasonForGrowth() == 0;
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
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.BLUE_JAZZ.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @SuppressWarnings("null")
    @Override
    protected ItemStack applyHarvestItemCustomization(ItemStack stack, BlockState state) {
        if (state.hasProperty(COLOR)) {
            @SuppressWarnings("null")
            int color = state.getValue(COLOR);
            @SuppressWarnings("null")
            var customData = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    com.stardew.craft.port.net.minecraft.world.item.component.CustomData.EMPTY);
            var tag = customData.copyTag();
            tag.putInt("FlowerColor", color);
            PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));
            setFlowerVariantModelData(stack, color);
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
        return "item.stardewcraft.blue_jazz";
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
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) return false;
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return true;
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || (above.is(this) && above.getValue(HALF) == DoubleBlockHalf.UPPER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return;
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel) {
            syncMultiBlockPartnerFromRoot(level, pos, state);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void syncMultiBlockPartnerFromRoot(Level level, BlockPos pos, BlockState state) {
        BlockPos root = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockState above = level.getBlockState(root.above());
        if (!above.isAir() && !(above.is(this) && above.getValue(HALF) == DoubleBlockHalf.UPPER)) return;
        super.syncMultiBlockPartnerFromRoot(level, pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
            BlockPos partnerPos = lower ? pos.above() : pos.below();
            BlockState partner = level.getBlockState(partnerPos);
            if (partner.is(this) && partner.getValue(HALF) != state.getValue(HALF)) {
                level.setBlock(partnerPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state, boolean watered,
                               CropGrowthManager.CropGrowthState growthState) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return;
        super.growCropOneDay(level, pos, state, watered, growthState);
        BlockState lower = level.getBlockState(pos);
        if (lower.is(this)) syncMultiBlockPartnerFromRoot(level, pos, lower);
    }

    @Override
    public void syncVisualStage(ServerLevel level, BlockPos pos, CropGrowthManager.CropGrowthState growth) {
        super.syncVisualStage(level, pos, growth);
        BlockState lower = level.getBlockState(pos);
        if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockState above = level.getBlockState(pos.above());
            if (above.isAir() || above.is(this)) syncMultiBlockPartnerFromRoot(level, pos, lower);
        }
    }
}
