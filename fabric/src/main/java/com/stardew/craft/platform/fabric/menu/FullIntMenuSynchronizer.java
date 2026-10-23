package com.stardew.craft.platform.fabric.menu;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;

/** Keeps the vanilla item/carried synchronization while replacing only the lossy short data path. */
final class FullIntMenuSynchronizer implements ContainerSynchronizer {
    private final AbstractContainerMenu owner;
    private final ResourceLocation menuType;
    private final ContainerSynchronizer vanilla;
    private final Consumer<MenuDataPayload> sender;

    FullIntMenuSynchronizer(AbstractContainerMenu owner, ResourceLocation menuType,
            ContainerSynchronizer vanilla, Consumer<MenuDataPayload> sender) {
        this.owner = Objects.requireNonNull(owner, "Menu owner");
        this.menuType = Objects.requireNonNull(menuType, "Menu type");
        this.vanilla = Objects.requireNonNull(vanilla, "Vanilla synchronizer");
        this.sender = Objects.requireNonNull(sender, "Menu data sender");
    }

    boolean isFor(AbstractContainerMenu menu) { return owner == menu; }

    @Override
    public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
            ItemStack carried, int[] data) {
        requireOwner(menu);
        // Sending the ordinary data array first would briefly expose truncated animal ids,
        // prices and silo totals to client listeners. Keep only its native item snapshot.
        vanilla.sendInitialData(menu, items, carried, new int[0]);
        for (int slot = 0; slot < data.length; slot++) {
            sendDataChange(menu, slot, data[slot]);
        }
    }

    @Override
    public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
        requireOwner(menu);
        vanilla.sendSlotChange(menu, slot, stack);
    }

    @Override
    public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
        requireOwner(menu);
        vanilla.sendCarriedChange(menu, carried);
    }

    @Override
    public void sendDataChange(AbstractContainerMenu menu, int dataSlot, int value) {
        requireOwner(menu);
        sender.accept(new MenuDataPayload(menu.containerId, menuType, dataSlot, value));
    }

    private void requireOwner(AbstractContainerMenu menu) {
        if (!isFor(menu)) {
            throw new IllegalArgumentException("A menu synchronizer cannot be reused for another menu instance");
        }
    }
}
