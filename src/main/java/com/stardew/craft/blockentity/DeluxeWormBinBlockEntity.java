package com.stardew.craft.blockentity;

import com.stardew.craft.production.MachineProductionData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class DeluxeWormBinBlockEntity extends TimedProductionBlockEntity implements BubbleItemCountProvider {

    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {
    }

    public DeluxeWormBinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DELUXE_WORM_BIN.get(), pos, state);
    }

    private boolean modelFootprintChecked;

    public static void serverTick(Level level, BlockPos pos, BlockState state, DeluxeWormBinBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (!be.modelFootprintChecked) {
            be.modelFootprintChecked = com.stardew.craft.block.utility.MachineModelFootprint.repair(level, pos, state);
        }
        be.tickServer(level);
    }

    private void tickServer(Level level) {
        if (product.isEmpty()) {
            if (readyAtAbsMinute >= 0 || ready) {
                clearState();
            }
            startCycle(level);
            return;
        }

        boolean newReady = refreshReady();
        if (newReady != ready) {
            ready = newReady;
            setChanged();
            syncToClient();
        }
    }

    private void startCycle(Level level) {
        product = createOutput(level.random);
        readyAtAbsMinute = MachineProductionData.cycle("deluxe_worm_bin", "default").deadline(getCurrentAbsMinute(), "deluxe_worm_bin");
        ready = false;
        setChanged();
        syncToClient();
    }

    private void clearState() {
        product = ItemStack.EMPTY;
        readyAtAbsMinute = -1;
        ready = false;
        setChanged();
        syncToClient();
    }

    @SuppressWarnings("null")
    private static ItemStack createOutput(RandomSource random) {
        return MachineProductionData.cycle("deluxe_worm_bin", "default").createOutput(random);
    }


    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !product.isEmpty() && !ready;
    }

    public boolean canApplyFairyDust() {
        return isWorking();
    }

    public boolean applyFairyDust() {
        if (!canApplyFairyDust()) {
            return false;
        }
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return false;
        }
        readyAtAbsMinute = getCurrentAbsMinute();
        ready = true;
        setChanged();
        syncToClient();
        return true;
    }

    public ItemStack getProduct() {
        return product;
    }

    @Override
    public int getBubbleItemCount() {
        return product.getCount();
    }

    public RemainingTime getRemainingTime() {
        long remaining = getRemainingAbsMinutes();
        int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
        int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
        int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
        int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
        return new RemainingTime(days, hours, minutes);
    }

    public ItemStack harvestOne() {
        if (!isReady()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        clearState();
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            startCycle(currentLevel);
        }
        return out;
    }

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ready ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (!ready || product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = AutomationStackHelper.extractUpTo(product, amount);
        if (simulate) {
            return out;
        }
        if (out.getCount() >= product.getCount()) {
            return harvestOne();
        }
        product.shrink(out.getCount());
        setChanged();
        syncToClient();
        return out;
    }


    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag getUpdateTag(@SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_PRODUCT)) {
            product = ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY);
        } else {
            product = ItemStack.EMPTY;
        }
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
    }
}
