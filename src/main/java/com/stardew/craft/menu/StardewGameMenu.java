package com.stardew.craft.menu;

import com.stardew.craft.network.payload.EquipmentActionPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative container behind the Stardew-style game menu.
 *
 * <p>The screen is free to draw these slots with Stardew assets, but all inventory
 * interaction stays on Minecraft's normal {@link Slot}/{@link AbstractContainerMenu}
 * path so other inventory mods can recognize it.</p>
 */
@SuppressWarnings("null")
public final class StardewGameMenu extends AbstractContainerMenu {
    public static final int MAIN_INVENTORY_SLOT_COUNT = 27;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final int PLAYER_SLOT_COUNT = MAIN_INVENTORY_SLOT_COUNT + HOTBAR_SLOT_COUNT;

    public StardewGameMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.STARDEW_GAME_MENU.get(), containerId);

        // The screen assigns the final visual positions after it knows its GUI scale.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 0, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 0, 0));
        }
    }

    /** Converts a vanilla player-inventory index (0..35) to this menu's slot index. */
    public static int menuSlotForInventoryIndex(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= PLAYER_SLOT_COUNT) {
            return -1;
        }
        return inventoryIndex < HOTBAR_SLOT_COUNT
                ? MAIN_INVENTORY_SLOT_COUNT + inventoryIndex
                : inventoryIndex - HOTBAR_SLOT_COUNT;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (player instanceof ServerPlayer serverPlayer
                && EquipmentActionPayload.tryQuickEquip(serverPlayer, source)) {
            if (source.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            slot.onTake(player, source);
            broadcastChanges();
            return original;
        }

        boolean moved;
        if (index < MAIN_INVENTORY_SLOT_COUNT) {
            moved = moveItemStackTo(source, MAIN_INVENTORY_SLOT_COUNT, PLAYER_SLOT_COUNT, false);
        } else {
            moved = moveItemStackTo(source, 0, MAIN_INVENTORY_SLOT_COUNT, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
