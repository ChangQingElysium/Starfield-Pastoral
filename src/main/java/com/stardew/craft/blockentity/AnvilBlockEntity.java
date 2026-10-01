package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class AnvilBlockEntity extends TimedProductionBlockEntity {
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";
    private static final String TAG_NEXT_TAP_TICK = "nextTapTick";
    private static final String TAG_TAPS_REMAINING = "tapsRemaining";
    private static final int MINUTES_UNTIL_READY = 10;

    private long nextTapTick = -1;
    private int tapsRemaining = 0;

    public AnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.anvil(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnvilBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        boolean newReady = be.refreshReady();
        if (newReady != be.ready) {
            be.ready = newReady;
            be.setChanged();
            be.syncToClient();
        }
        be.tickQueuedTaps(level, pos);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !product.isEmpty() && !ready && readyAtAbsMinute >= 0;
    }

    public boolean hasPayload() {
        return !product.isEmpty();
    }

    public ItemStack getProduct() {
        return product;
    }

    public void startReforge(ItemStack output) {
        product = output.copyWithCount(1);
        readyAtAbsMinute = getCurrentAbsMinute() + MINUTES_UNTIL_READY;
        ready = false;
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            playTap(currentLevel, worldPosition);
            nextTapTick = currentLevel.getGameTime() + 5;
            tapsRemaining = 2;
        }
        setChanged();
        syncToClient();
    }

    public ItemStack harvestOne() {
        if (!isReady()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        readyAtAbsMinute = -1;
        ready = false;
        nextTapTick = -1;
        tapsRemaining = 0;
        setChanged();
        syncToClient();
        return out;
    }

    public ItemStack dropStoredPayload() {
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        readyAtAbsMinute = -1;
        ready = false;
        nextTapTick = -1;
        tapsRemaining = 0;
        setChanged();
        return out;
    }

    private void tickQueuedTaps(Level level, BlockPos pos) {
        if (tapsRemaining <= 0 || nextTapTick < 0 || level.getGameTime() < nextTapTick) {
            return;
        }
        playTap(level, pos);
        tapsRemaining--;
        nextTapTick = tapsRemaining > 0 ? level.getGameTime() + 5 : -1;
        setChanged();
    }

    private static void playTap(Level level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.METAL_TAP.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
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
        ItemStack out = product.copyWithCount(Math.min(amount, product.getCount()));
        if (!simulate) {
            harvestOne();
        }
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, PortItemStacks.save(product, registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
        tag.putLong(TAG_NEXT_TAP_TICK, nextTapTick);
        tag.putInt(TAG_TAPS_REMAINING, tapsRemaining);
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        product = tag.contains(TAG_PRODUCT) ? PortItemStacks.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
        nextTapTick = tag.contains(TAG_NEXT_TAP_TICK) ? tag.getLong(TAG_NEXT_TAP_TICK) : -1;
        tapsRemaining = tag.contains(TAG_TAPS_REMAINING) ? tag.getInt(TAG_TAPS_REMAINING) : 0;
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
