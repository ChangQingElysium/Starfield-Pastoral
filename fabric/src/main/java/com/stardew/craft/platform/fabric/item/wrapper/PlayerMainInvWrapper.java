package com.stardew.craft.platform.fabric.item.wrapper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The 36 main player slots, excluding armor/offhand, with the original pickup notification. */
public class PlayerMainInvWrapper extends RangedWrapper {
    private final Inventory inventory;

    public PlayerMainInvWrapper(Inventory inventory) {
        super(new InvWrapper(inventory), 0, inventory.items.size());
        this.inventory = inventory;
    }

    public Inventory getInventoryPlayer() { return inventory; }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        ItemStack remainder = super.insertItem(slot, stack, simulate);
        if (remainder.getCount() != stack.getCount()) {
            ItemStack inSlot = getStackInSlot(slot);
            if (!inSlot.isEmpty()) {
                // NeoForge 21.1.217 also notifies for a simulated successful merge into an
                // occupied slot. Preserve that notification, without changing stored counts.
                if (inventory.player.level().isClientSide) {
                    inSlot.setPopTime(5);
                } else if (inventory.player instanceof ServerPlayer) {
                    inventory.player.containerMenu.broadcastChanges();
                }
            }
        }
        return remainder;
    }
}
