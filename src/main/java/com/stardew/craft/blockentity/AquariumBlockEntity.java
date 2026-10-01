package com.stardew.craft.blockentity;

import com.stardew.craft.aquarium.AquariumRules;
import com.stardew.craft.menu.AquariumMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Slots retain the complete original items; clients receive full replacement snapshots. */
public final class AquariumBlockEntity extends BlockEntity implements Container, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(AquariumRules.SIZE, ItemStack.EMPTY);
    private long layoutSeed;
    public boolean discardEmptyOnRemoval;

    public AquariumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LARGE_FISH_TANK.get(), pos, state);
        layoutSeed = net.minecraft.util.RandomSource.create().nextLong();
    }
    public long layoutSeed() { return layoutSeed; }
    public int insert(ItemStack stack) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isEmpty() && canPlaceItem(i, stack)) {
            setItem(i, stack.copyWithCount(1)); return i;
        }
        return -1;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return AquariumRules.canPlace(this, slot, stack); }
    @Override public int getContainerSize() { return items.size(); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        // Interaction may start at any of this four-block-wide furniture's occupied cells.
        return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5) <= 100;
    }
    @Override public Component getDisplayName() { return Component.translatable("block.stardewcraft.large_fish_tank"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new AquariumMenu(id, inventory, this); }
    @Override public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putLong("LayoutSeed", layoutSeed);
    }
    @Override public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        items.clear(); ContainerHelper.loadAllItems(tag, items);
        if (tag.contains("LayoutSeed")) layoutSeed = tag.getLong("LayoutSeed");
    }
    @Override public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        var tag = new CompoundTag(); saveAdditional(tag); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        load(com.stardew.craft.port.PortBlockEntityPackets.tag(packet));
    }
}
