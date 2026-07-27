package com.stardew.craft.blockentity;

import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TimedProductionPersistenceTest {
    private static final HolderLookup.Provider REGISTRIES =
            VanillaRegistries.createLookup();

    @Test
    void runningCycleRoundTripsWithoutReconsumingOrCreatingOutput() {
        MayonnaiseMachineBlockEntity original = machine();
        original.input = new ItemStack(Items.EGG);
        original.product = new ItemStack(Items.HONEY_BOTTLE, 2);
        original.readyAtAbsMinute = 9_876L;
        original.ready = false;

        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, REGISTRIES);

        MayonnaiseMachineBlockEntity restored = machine();
        restored.loadAdditional(saved, REGISTRIES);

        assertEquals(1, restored.getInput().getCount());
        assertEquals(Items.EGG, restored.getInput().getItem());
        assertEquals(2, restored.getProduct().getCount());
        assertEquals(Items.HONEY_BOTTLE,
                restored.getProduct().getItem());
        assertEquals(9_876L,
                restored.stardewReadyAtAbsoluteMinute());
        assertFalse(restored.isReady());
    }

    @Test
    void readyCycleRoundTripsAsOneCollectableOutput() {
        MayonnaiseMachineBlockEntity original = machine();
        original.input = new ItemStack(Items.EGG);
        original.product = new ItemStack(Items.HONEY_BOTTLE, 2);
        original.readyAtAbsMinute = 123L;
        original.ready = true;

        CompoundTag saved = new CompoundTag();
        original.saveAdditional(saved, REGISTRIES);

        MayonnaiseMachineBlockEntity restored = machine();
        restored.loadAdditional(saved, REGISTRIES);

        assertEquals(1, restored.getInput().getCount());
        assertEquals(2, restored.getProduct().getCount());
        assertEquals(123L,
                restored.stardewReadyAtAbsoluteMinute());
        ItemStack collected = restored.harvestOne();
        assertEquals(Items.HONEY_BOTTLE, collected.getItem());
        assertEquals(2, collected.getCount());
        assertEquals(0, restored.getInput().getCount());
        assertEquals(0, restored.getProduct().getCount());
        assertEquals(-1L,
                restored.stardewReadyAtAbsoluteMinute());
    }

    private static MayonnaiseMachineBlockEntity machine() {
        return new MayonnaiseMachineBlockEntity(
                BlockPos.ZERO,
                ModBlocks.MAYONNAISE_MACHINE.get()
                        .defaultBlockState());
    }
}
