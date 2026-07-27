package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.api.v1.machine.StardewProductionPhase;
import com.stardew.craft.item.ModItems;
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

public class WormBinBlockEntity extends TimedProductionBlockEntity implements BubbleItemCountProvider {
    private static final int EFFECTIVE_MINUTES_PER_DAY = 1260;
    private static final int DAYS_UNTIL_READY = 1;
    private static final int MIN_OUTPUT = 4;
    private static final int MAX_OUTPUT = 5;

    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {
    }

    public WormBinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORM_BIN.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WormBinBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickServer(level);
    }

    private void tickServer(Level level) {
        if (product.isEmpty()) {
            if (readyAtAbsMinute >= 0
                    && getCurrentAbsMinute() < readyAtAbsMinute) {
                return;
            }
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
        ItemStack proposed = createOutput(level.random);
        var plan = prepareMachineCycle(
                StardewMachineCycleKind.PASSIVE,
                ItemStack.EMPTY,
                proposed,
                DAYS_UNTIL_READY * EFFECTIVE_MINUTES_PER_DAY,
                null,
                true);
        if (plan.isPresent()) {
            restartMachineCycle(
                    plan.get(),
                    StardewMachineCycleKind.PASSIVE,
                    true);
            return;
        }
        // Autonomous rejection pauses retries instead of evaluating providers
        // and consuming randomness every server tick.
        readyAtAbsMinute = getCurrentAbsMinute()
                + EFFECTIVE_MINUTES_PER_DAY;
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
        int count = random.nextInt(MAX_OUTPUT - MIN_OUTPUT + 1) + MIN_OUTPUT;
        return new ItemStack((net.minecraft.world.level.ItemLike) ModItems.BAIT.get(), count);
    }


    public boolean isReady() {
        return refreshReady();
    }

    @Override
    protected StardewMachineCycleKind defaultCycleKind() {
        return StardewMachineCycleKind.PASSIVE;
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
        emitProductionEvent(StardewProductionPhase.COLLECTED);
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
