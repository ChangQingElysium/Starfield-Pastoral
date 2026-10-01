package com.stardew.craft.block.crop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.network.HayHarvestHudMessagePacket;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * 小麦作物
 */
public class WheatCropBlock extends TomatoCropBlock {

    private static final int[] PHASE_DAYS = new int[]{1, 1, 1, 1};

    @Override
    protected Supplier<Item> getSeedsItem() {
        return ModItems.WHEAT_SEEDS;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return ModItems.WHEAT;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) {
            return true;
        }
        return seasonForGrowth() == 1 || seasonForGrowth() == 2;
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
        ItemStack stack = new ItemStack(ModItems.WHEAT.get());
        QualityHelper.setQuality(stack, quality);
        return stack;
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
    protected int getRegrowDays() {
        return 0;
    }

    @Override
    public String getCropDisplayNameKey() {
        return "item.stardewcraft.wheat";
    }

    /**
     * SDV 1:1: Crop.cs:728 — 小麦收获时 40% 概率额外掉 1 份干草。
     * 本模组扩展：优先尝试塞入玩家的筒仓，塞不下的部分掉落在地上。
     */
    @Override
    protected void spawnHarvestSideProducts(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                            Player player, int fertilizerLevel, int farmingLevel) {
        if (random.nextDouble() >= 0.4) {
            return;
        }
        int hayCount = 1;
        int leftover = hayCount;
        if (player instanceof ServerPlayer serverPlayer) {
            int stored = com.stardew.craft.animal.runtime.FarmFeed.store(level, pos, hayCount);
            if (stored > 0) {
                HayHarvestHudMessagePacket.sendTo(serverPlayer, stored, false);
            }
            leftover = hayCount - stored;
        }
        if (leftover > 0) {
            Block.popResource(level, pos, new ItemStack(ModItems.HAY.get(), leftover));
        }
    }

    @Override
    protected void collectJunimoHarvestSideProducts(ServerLevel level, BlockPos pos, BlockState state,
                                                     RandomSource random, int fertilizerLevel,
                                                     int farmingLevel, Consumer<ItemStack> output) {
        if (random.nextDouble() < 0.4) {
            output.accept(new ItemStack(ModItems.HAY.get()));
        }
    }
}
