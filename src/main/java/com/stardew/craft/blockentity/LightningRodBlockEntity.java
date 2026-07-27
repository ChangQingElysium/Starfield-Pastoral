package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.blockentity.registry.LightningRodRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

public class LightningRodBlockEntity extends TimedProductionBlockEntity {
    private static final int EFFECTIVE_MINUTES_PER_DAY = 1260;

    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";

    public record RemainingTime(int days, int hours, int minutes) {}

    public LightningRodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHTNING_ROD.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightningRodBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        // Self-heal: ensure this rod is registered (handles rods placed before
        // the registry existed, or after a registry data wipe).
        LightningRodRegistry registry = LightningRodRegistry.get(sl);
        registry.add(pos);

        boolean newReady = be.refreshReady();
        if (newReady != be.ready) {
            be.ready = newReady;
            be.setChanged();
            be.syncToClient();
        }
        // Drain any pending strike scheduled while this chunk was unloaded.
        if (!be.isBusy()) {
            if (registry.consumePending(pos)) {
                be.startChargingFromStrike();
            }
        }
        be.updateWorkingState(level, pos, state);
    }

    /** True while a battery is charging (occupied), regardless of ready state. */
    public boolean isBusy() {
        return !product.isEmpty() || readyAtAbsMinute >= 0;
    }

    /** Called by {@link com.stardew.craft.weather.LightningStrikeScheduler} when this rod is hit. */
    @SuppressWarnings("null")
    public void startChargingFromStrike() {
        if (isBusy()) return;
        ItemStack proposed = new ItemStack(
                (net.minecraft.world.level.ItemLike)
                        ModItems.BATTERY_PACK.get());
        var plan = prepareMachineCycle(
                StardewMachineCycleKind.ENVIRONMENTAL,
                ItemStack.EMPTY,
                proposed,
                EFFECTIVE_MINUTES_PER_DAY,
                null,
                true);
        plan.ifPresent(value -> restartMachineCycle(
                value,
                StardewMachineCycleKind.ENVIRONMENTAL,
                true));
    }


    public boolean isReady() {
        return refreshReady();
    }

    @Override
    protected StardewMachineCycleKind defaultCycleKind() {
        return StardewMachineCycleKind.ENVIRONMENTAL;
    }

    public boolean isWorking() {
        return !product.isEmpty() && !ready && readyAtAbsMinute > 0;
    }

    public ItemStack getProduct() {
        return product;
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
        return collectProduction();
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

    @SuppressWarnings("null")
    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        BooleanProperty workingProp = com.stardew.craft.block.utility.LightningRodBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
            BlockPos extensionPos = com.stardew.craft.block.utility.LightningRodBlock.getExtensionPos(pos, state);
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(state.getBlock()) && extensionState.hasProperty(workingProp)) {
                level.setBlock(extensionPos, extensionState.setValue(workingProp, workingNow), 3);
            }
        }
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
