package com.stardew.craft.blockentity;

import net.minecraft.world.item.ItemStack;

public interface UtilityMachineInfo {
    String getUtilityTooltipKey();

    boolean isReadyForDisplay();

    boolean isWorkingForDisplay();

    default boolean shouldShowInputInDisplay() {
        return true;
    }

    default ItemStack getDisplayInput() {
        return ItemStack.EMPTY;
    }

    default ItemStack getDisplayOutput() {
        return ItemStack.EMPTY;
    }

    default boolean hasRemainingTimeForDisplay() {
        return false;
    }

    default RemainingTime getRemainingTimeForDisplay() {
        return new RemainingTime(0, 0, 0);
    }

    default String getIdleTooltipKey() {
        return "";
    }

    record RemainingTime(int days, int hours, int minutes) {
    }
}
