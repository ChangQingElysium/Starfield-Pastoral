package com.stardew.craft.api.v1.machine;

import net.minecraft.world.item.ItemStack;

/** Read-only timed-production capability implemented by the core machine base class. */
public interface StardewTimedProduction {
    ItemStack stardewInput();

    ItemStack stardewOutput();

    long stardewReadyAtAbsoluteMinute();

    long stardewRemainingMinutes();

    boolean stardewIsReady();

    /** Cycle shape; legacy addon implementations default to one-shot batch. */
    default StardewMachineCycleKind stardewCycleKind() {
        return StardewMachineCycleKind.BATCH;
    }

    /** Whether the active cycle was committed without a direct player action. */
    default boolean stardewAutomationStarted() {
        return false;
    }
}
