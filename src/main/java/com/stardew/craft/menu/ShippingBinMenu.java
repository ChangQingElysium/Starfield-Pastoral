package com.stardew.craft.menu;

import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("null")
public class ShippingBinMenu extends AbstractContainerMenu {
    private static final int BIN_SLOTS = 1;

    private final Container container;

    public ShippingBinMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(BIN_SLOTS));
    }

    public ShippingBinMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenuTypes.SHIPPING_BIN.get(), containerId);
        this.container = container;

        checkContainerSize(container, BIN_SLOTS);
        container.startOpen(playerInventory.player);

        // Keep the temporary shipping buffer slot in the visual center.
        this.addSlot(new Slot(container, 0, 80, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ShippingBinBlockEntity.canShip(stack);
            }

            @Override
            public ItemStack safeInsert(ItemStack stack, int increment) {
                if (container instanceof ShippingBinBlockEntity bin && mayPlace(stack)) {
                    int inserted = Math.min(increment, stack.getCount());
                    if (inserted > 0 && bin.depositFromPlayer(playerInventory.player, stack.copyWithCount(inserted))) {
                        stack.shrink(inserted);
                    }
                    return stack;
                }
                return super.safeInsert(stack, increment);
            }

            // PORT(1.20.1): 1.21 Slot#setByPlayer(stack) forwards to setByPlayer(stack, getItem()).
            @Override
            public void setByPlayer(ItemStack stack) {
                setByPlayer(stack, getItem());
            }

            public void setByPlayer(ItemStack stack, ItemStack oldStack) {
                if (container instanceof ShippingBinBlockEntity bin && !stack.isEmpty()) {
                    int oldCount = ItemStack.isSameItemSameTags(stack, oldStack) ? oldStack.getCount() : 0;
                    int inserted = stack.getCount() - oldCount;
                    if (inserted > 0) {
                        // Drag distribution passes the combined stack. Only this player's
                        // added quantity belongs to the new batch; settle the old one first.
                        bin.depositFromPlayer(playerInventory.player, stack.copyWithCount(inserted));
                        return;
                    }
                }
                super.setByPlayer(stack);
            }
        });

        int playerInvY = 49;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
            }
        }

        int hotbarY = 107;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, hotbarY));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 0 && clickType == ClickType.PICKUP) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                if (!ShippingBinBlockEntity.canShip(carried)) return;
                ItemStack toShip = carried.copyWithCount(button == 1 ? 1 : carried.getCount());
                if (deposit(player, toShip)) {
                    carried.shrink(toShip.getCount());
                    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
                return;
            }
        }
        if (slotId == 0 && clickType == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)) {
            ItemStack hotbar = player.getInventory().getItem(button);
            if (!hotbar.isEmpty()) {
                if (ShippingBinBlockEntity.canShip(hotbar) && deposit(player, hotbar)) {
                    player.getInventory().setItem(button, ItemStack.EMPTY);
                }
                return;
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    private boolean deposit(Player player, ItemStack stack) {
        if (container instanceof ShippingBinBlockEntity bin) return bin.depositFromPlayer(player, stack);
        container.setItem(0, stack.copy());
        container.setChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        result = stackInSlot.copy();

        if (index < BIN_SLOTS) {
            if (!this.moveItemStackTo(stackInSlot, BIN_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!ShippingBinBlockEntity.canShip(stackInSlot)) {
                return ItemStack.EMPTY;
            }
            if (this.container instanceof ShippingBinBlockEntity bin) {
                if (!bin.depositFromPlayer(player, stackInSlot)) return ItemStack.EMPTY;
                stackInSlot.setCount(0);
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(stackInSlot, 0, BIN_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
