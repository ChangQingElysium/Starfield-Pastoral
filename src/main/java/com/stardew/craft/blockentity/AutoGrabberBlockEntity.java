package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.AutoGrabberBlock;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.UUID;

@SuppressWarnings("null")
public class AutoGrabberBlockEntity extends BlockEntity implements UtilityAutomationAccess, Container, MenuProvider {
    private static final String TAG_ITEMS = "items";
    public static final int STORAGE_ROWS = 4;
    public static final int SLOT_COUNT = STORAGE_ROWS * 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int openCount = 0;

    public AutoGrabberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_GRABBER.get(), pos, state);
    }

    public static void recordCollectedForOwner(
            String ownerPlayerUuid,
            int collected
    ) {
        if (ownerPlayerUuid == null || ownerPlayerUuid.isBlank() || collected <= 0) {
            return;
        }
        try {
            PlayerStardewDataAPI.recordAnimalProductsCollected(UUID.fromString(ownerPlayerUuid), collected);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private ItemStack extractUpTo(int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            int taken = Math.min(amount, stack.getCount());
            ItemStack out = stack.copy();
            out.setCount(taken);
            if (!simulate) {
                if (taken >= stack.getCount()) {
                    items.set(i, ItemStack.EMPTY);
                } else {
                    stack.shrink(taken);
                }
                setChanged();
                syncToClient();
            }
            return out;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack insertIntoStorage(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        for (int i = 0; i < items.size(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, remaining)) {
                continue;
            }
            int max = slot.getMaxStackSize();
            int canAdd = Math.min(max - slot.getCount(), remaining.getCount());
            if (canAdd <= 0) {
                continue;
            }
            if (!simulate) {
                slot.grow(canAdd);
            }
            remaining.shrink(canAdd);
            if (remaining.isEmpty()) {
                if (!simulate) {
                    setChanged();
                }
                return ItemStack.EMPTY;
            }
        }

        for (int i = 0; i < items.size(); i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty()) {
                continue;
            }
            int toPut = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            if (!simulate) {
                ItemStack newStack = remaining.copy();
                newStack.setCount(toPut);
                items.set(i, newStack);
            }
            remaining.shrink(toPut);
            if (remaining.isEmpty()) {
                if (!simulate) {
                    setChanged();
                }
                return ItemStack.EMPTY;
            }
        }

        if (!simulate) {
            setChanged();
            syncToClient();
        }
        return remaining;
    }

    private boolean hasAnyItem() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return item;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        return insertIntoStorage(stack, simulate);
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        return extractUpTo(amount, simulate);
    }

    @Override
    public int getAutomationSlotLimit(int slot) {
        return 64;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return !hasAnyItem();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= items.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int removed = Math.min(amount, stack.getCount());
        ItemStack out = stack.copy();
        out.setCount(removed);
        if (removed >= stack.getCount()) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            stack.shrink(removed);
        }
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        if (stack.isEmpty()) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            ItemStack copy = stack.copy();
            copy.setCount(Math.min(copy.getCount(), copy.getMaxStackSize()));
            items.set(slot, copy);
        }
        setChanged();
        syncToClient();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
        syncToClient();
    }

    public void dropAllContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.toArray(new ItemStack[0]));
        Containers.dropContents(level, pos, container);
        clearContent();
        setChanged();
        syncToClient();
    }

    @Override
    public void startOpen(Player player) {
        if (player.isSpectator()) {
            return;
        }
        openCount++;
        if (openCount == 1 && level != null) {
            level.playSound(null, worldPosition, ModSounds.OPEN_CHEST.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (player.isSpectator()) {
            return;
        }
        openCount = Math.max(0, openCount - 1);
        if (openCount == 0 && level != null) {
            level.playSound(null, worldPosition, ModSounds.DOOR_CREAK_REVERSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.stardew_craft.auto_grabber");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChestMenu(
                MenuType.GENERIC_9x4,
                containerId,
                playerInventory,
                this,
                STORAGE_ROWS
        );
    }

    private void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        if (state.getBlock() instanceof AutoGrabberBlock autoGrabber && state.hasProperty(AutoGrabberBlock.FULL)) {
            boolean fullNow = hasAnyItem();
            if (state.getValue(AutoGrabberBlock.FULL) != fullNow) {
                BlockState mainUpdated = state.setValue(AutoGrabberBlock.FULL, fullNow);
                currentLevel.setBlock(worldPosition, mainUpdated, 3);

                BlockPos extensionPos = AutoGrabberBlock.getExtensionPos(worldPosition, mainUpdated);
                BlockState extensionState = currentLevel.getBlockState(extensionPos);
                if (extensionState.is(autoGrabber) && extensionState.hasProperty(AutoGrabberBlock.FULL)) {
                    currentLevel.setBlock(extensionPos, extensionState.setValue(AutoGrabberBlock.FULL, fullNow), 3);
                }
            }
        }

        currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        if (currentLevel instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Stack", stack.save(registries));
            list.add(entry);
        }
        tag.put(TAG_ITEMS, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        if (tag.contains(TAG_ITEMS, 9)) {
            ListTag list = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot < 0 || slot >= items.size()) {
                    continue;
                }
                ItemStack parsed = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                items.set(slot, parsed);
            }
        }
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
}
