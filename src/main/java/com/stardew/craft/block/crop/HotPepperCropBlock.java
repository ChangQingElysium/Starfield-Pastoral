package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 辣椒作物
 */
public class HotPepperCropBlock extends TomatoCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 1, 1, 1, 1}; // SDV: 5 days

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.HOT_PEPPER_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.HOT_PEPPER;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return seasonForGrowth() == 1;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            BlockState above = level.getBlockState(pos.above());
            if (!above.isAir() && above.getBlock() != this) return;
        }
        super.onPlace(state, level, pos, oldState, moving);
    }

    @Override
    protected void syncMultiBlockPartnerFromRoot(Level level, BlockPos pos, BlockState state) {
        BlockPos root = resolveMultiBlockRootPos(level, pos, state);
        BlockState above = level.getBlockState(root.above());
        // Old saves used a single block: never replace a block above an existing crop.
        if (!above.isAir() && above.getBlock() != this) return;
        super.syncMultiBlockPartnerFromRoot(level, pos, state);
    }

    @Override
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state,
            boolean watered, CropGrowthManager.CropGrowthState growth) {
        BlockPos root = resolveMultiBlockRootPos(level, pos, state);
        BlockState above = level.getBlockState(root.above());
        if (!above.isAir() && above.getBlock() != this) return;
        super.growCropOneDay(level, pos, state, watered, growth);
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        @SuppressWarnings("null")
        ItemStack stack = new ItemStack(ModItems.HOT_PEPPER.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
    }

    @Override
    protected boolean canRegrow() {
        return true;
    }

    @Override
    protected int getRegrowAge() {
        return 2;
    }

    @Override
    protected int getRegrowDays() {
        return 3;
    }

    @Override
    protected double getExtraHarvestChance() {
        return 0.03;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.hot_pepper";
    }
}
