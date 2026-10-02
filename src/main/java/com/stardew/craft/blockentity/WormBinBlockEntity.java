package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.production.MachineProductionData;
import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.api.v1.machine.StardewProductionPhase;
import com.stardew.craft.block.utility.WormBinBlock;
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
        WormBinBlock.ensureExtension(level, pos, state);
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
                MachineProductionData.cycle("worm_bin", "default").rawMinutes(getCurrentAbsMinute()),
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
        return MachineProductionData.cycle("worm_bin", "default").createOutput(random);
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
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, PortItemStacks.save(product, registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
    }

    @SuppressWarnings("null")
    @Override
    public void load(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        if (tag.contains(TAG_PRODUCT)) {
            product = PortItemStacks.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY);
        } else {
            product = ItemStack.EMPTY;
        }
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
    }
}
