package com.stardew.craft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Platform-neutral Stardew metadata contract for the Forge migration line. */
public interface IStardewItem {
    String getItemTypeKey();

    default int getSellPrice(ItemStack stack) {
        return -1;
    }

    default int getBaseSellPrice(ItemStack stack) {
        return getSellPrice(stack);
    }

    default int getEdibility(ItemStack stack) {
        return isFood() ? Math.round(getEnergy(stack) / 2.5F) : -300;
    }

    default boolean isFood() {
        return false;
    }

    default int getEnergy(ItemStack stack) {
        return 0;
    }

    default int getHealth(ItemStack stack) {
        return 0;
    }

    default List<Component> getAfterEatTooltipLines(ItemStack stack) {
        return List.of();
    }
}
