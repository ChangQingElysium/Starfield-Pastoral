package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class FridgeBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container, MenuProvider {
    private static final int SLOT_COUNT = 27;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private boolean doorOpen;
    private float openness;
    private float previousOpenness;
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override protected void onOpen(Level level, BlockPos pos, BlockState state) {}
        @Override protected void onClose(Level level, BlockPos pos, BlockState state) {}

        @Override protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int count) {
            level.blockEvent(pos, state.getBlock(), 1, count);
        }

        @Override protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == FridgeBlockEntity.this;
        }
    };

    public FridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FRIDGE.get(), pos, state);
    }

    public void dropAllContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.toArray(new ItemStack[0]));
        Containers.dropContents(level, pos, container);
        clearContent();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
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
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public void startOpen(Player player) {
        if (!isRemoved() && !player.isSpectator() && level != null && !level.isClientSide) {
            openers.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!isRemoved() && !player.isSpectator() && level != null && !level.isClientSide) {
            openers.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    public void recheckOpeners() {
        if (!isRemoved() && level != null && !level.isClientSide) {
            openers.recheckOpeners(level, worldPosition, getBlockState());
        }
    }

    @Override
    public boolean triggerEvent(int id, int value) {
        if (id == 1) {
            doorOpen = value > 0;
            return true;
        }
        return super.triggerEvent(id, value);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FridgeBlockEntity fridge) {
        fridge.previousOpenness = fridge.openness;
        fridge.openness = Mth.clamp(fridge.openness + (fridge.doorOpen ? .1F : -.1F), 0, 1);
    }

    /** Half-second hinge motion, interpolated every frame; reversing never resets the angle. */
    public float getDoorOpenness(float partialTick) {
        float t = Mth.lerp(partialTick, previousOpenness, openness);
        return t * t * (3 - 2 * t);
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("DoorOpen", openers.getOpenerCount() > 0);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        // A newly tracking client starts at the current pose; never send the stored inventory here.
        doorOpen = tag.getBoolean("DoorOpen");
        previousOpenness = openness = doorOpen ? 1 : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Stack", PortItemStacks.save(stack, registries));
            list.add(entry);
        }
        tag.put("items", list);
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);

        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }

        if (tag.contains("items", 9)) {
            net.minecraft.nbt.ListTag list = tag.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot < 0 || slot >= items.size()) {
                    continue;
                }
                ItemStack parsed = PortItemStacks.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                items.set(slot, parsed);
            }
        }
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("container.stardew_craft.fridge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return ChestMenu.threeRows(containerId, playerInventory, this);
    }
}
