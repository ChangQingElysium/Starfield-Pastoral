package com.stardew.craft.blockentity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilityDropHelperTest {
    @Test
    void breakingRunningMachineRefundsOnlyItsTrackedInput() {
        UtilityAutomationAccess access = access(
                new ItemStack(Items.EGG),
                ItemStack.EMPTY);

        ItemStack drop = UtilityDropHelper.primaryDrop(access);

        assertEquals(Items.EGG, drop.getItem());
        assertEquals(1, drop.getCount());
    }

    @Test
    void breakingReadyMachineDropsOnlyItsFinishedOutput() {
        UtilityAutomationAccess access = access(
                new ItemStack(Items.EGG),
                new ItemStack(Items.HONEY_BOTTLE, 2));

        ItemStack drop = UtilityDropHelper.primaryDrop(access);

        assertEquals(Items.HONEY_BOTTLE, drop.getItem());
        assertEquals(2, drop.getCount());
    }

    private static UtilityAutomationAccess access(
            ItemStack input,
            ItemStack output
    ) {
        return new UtilityAutomationAccess() {
            @Override
            public ItemStack getAutomationInput() {
                return input;
            }

            @Override
            public ItemStack getAutomationOutput() {
                return output;
            }

            @Override
            public ItemStack insertAutomation(
                    ItemStack stack,
                    boolean simulate
            ) {
                return stack;
            }

            @Override
            public ItemStack extractAutomation(
                    int amount,
                    boolean simulate
            ) {
                return ItemStack.EMPTY;
            }
        };
    }
}
