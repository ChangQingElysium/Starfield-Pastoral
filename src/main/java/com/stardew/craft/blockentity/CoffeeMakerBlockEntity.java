package com.stardew.craft.blockentity;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class CoffeeMakerBlockEntity extends BlockEntity implements UtilityAutomationAccess, AdvanceableUtility, UtilityMachineInfo {
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY = "ready";
    private static final String TAG_LAST_DAY = "lastDayIndex";

    private ItemStack product = ItemStack.EMPTY;
    private boolean ready = false;
    private long lastDayIndex = -1;
    private final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);

    public CoffeeMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COFFEE_MAKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoffeeMakerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickServer();
    }

    private void tickServer() {
        long currentDayIndex = getCurrentDayIndex();
        if (lastDayIndex < 0) {
            lastDayIndex = currentDayIndex;
            setChanged();
            return;
        }

        if (currentDayIndex == lastDayIndex) {
            return;
        }

        lastDayIndex = currentDayIndex;
        if (product.isEmpty()) {
            product = new ItemStack(ModItems.COFFEE.get());
            ready = true;
            setChanged();
            syncToClient();
        } else if (!ready) {
            ready = true;
            setChanged();
            syncToClient();
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return false;
    }

    public ItemStack getProduct() {
        return product;
    }

    public ItemStack harvestOne() {
        if (!ready || product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        ready = false;
        setChanged();
        syncToClient();
        return out;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    @Override
    public void advanceDays(int days) {
        if (days <= 0) {
            return;
        }
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        if (product.isEmpty()) {
            product = new ItemStack(ModItems.COFFEE.get());
        }
        ready = true;
        lastDayIndex = getCurrentDayIndex();
        setChanged();
        syncToClient();
    }

    @Override
    public String getUtilityTooltipKey() {
        return "coffee_maker";
    }

    @Override
    public boolean isReadyForDisplay() {
        return isReady();
    }

    @Override
    public boolean isWorkingForDisplay() {
        return false;
    }

    @Override
    public boolean shouldShowInputInDisplay() {
        return false;
    }

    @Override
    public ItemStack getDisplayOutput() {
        return product;
    }

    @Override
    public String getIdleTooltipKey() {
        return "stardewcraft.tooltip.coffee_maker.waiting";
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

    private void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static long getCurrentDayIndex() {
        StardewTimeManager tm = StardewTimeManager.get();
        int year = tm.getCurrentYear();
        int season = tm.getCurrentSeason();
        int day = tm.getCurrentDay();
        return (long) (year - 1) * 112L + (long) season * 28L + (long) day;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putBoolean(TAG_READY, ready);
        tag.putLong(TAG_LAST_DAY, lastDayIndex);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ready = tag.getBoolean(TAG_READY);
        lastDayIndex = tag.contains(TAG_LAST_DAY) ? tag.getLong(TAG_LAST_DAY) : -1;
    }
}
