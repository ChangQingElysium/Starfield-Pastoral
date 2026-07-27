package com.stardew.craft.api.v1.agriculture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime bridge for addon-owned crop blocks and persistence.
 *
 * <p>{@link #inspect} must be side-safe and read-only. Daily mutation is invoked only on the
 * logical server. The addon owns its age, phase, regrowth and death persistence; StardewCraft owns
 * scheduling and supplies world-derived inputs.
 */
public interface StardewCropRuntimeAdapter {
    /**
     * Recognizes the crop part at {@code position}, or returns {@code null}.
     *
     * <p>The returned type ID must equal the ID used when this adapter was registered.
     */
    @Nullable
    StardewCropState inspect(LevelReader level, BlockPos position);

    /**
     * Processes one in-game day for a revalidated crop.
     *
     * <p>Return {@link DailyResult#REMOVED} when the crop was replaced or removed so its scheduled
     * position can be discarded immediately.
     */
    default DailyResult growOneDay(
            ServerLevel level,
            StardewCropState crop,
            StardewCropDailyContext context
    ) {
        return DailyResult.UNCHANGED;
    }

    /**
     * Performs an authoritative harvest and sends produced stacks to the supplied staging sink.
     *
     * <p>The runtime copies and withholds staged stacks until this method returns
     * {@link StardewCropHarvestResult.Status#HARVESTED}. The addon owns its crop mutation and
     * regrowth persistence, but must not put items directly into a player or automation inventory.
     * Returning any other status discards staged outputs.
     */
    default StardewCropHarvestResult harvest(
            ServerLevel level,
            StardewCropState crop,
            StardewCropHarvestContext context,
            Consumer<ItemStack> output
    ) {
        return StardewCropHarvestResult.pass();
    }

    /**
     * Removes an intact crop for a world-system cause without producing harvest outputs.
     *
     * <p>Return true only after every addon-owned block/state belonging to this logical crop has
     * been removed or replaced. The runtime then removes its daily scheduler entry.
     */
    default boolean remove(
            ServerLevel level,
            StardewCropState crop,
            StardewCropRemovalCause cause
    ) {
        return false;
    }

    enum DailyResult {
        UNCHANGED,
        CHANGED,
        REMOVED
    }
}
