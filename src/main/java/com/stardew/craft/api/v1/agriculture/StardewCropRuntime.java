package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/** Safe crop inspection, scheduling and daily operations shared by core and addons. */
public final class StardewCropRuntime {
    private StardewCropRuntime() {
    }

    /** Returns an immutable crop view for any core or registered addon crop part. */
    @Nullable
    public static StardewCropState inspect(LevelReader level, BlockPos position) {
        return StardewCropRuntimeRegistry.inspect(level, position);
    }

    /** Fast, side-safe prefilter for UI and tool integrations before invoking an adapter. */
    public static boolean isRegisteredBlock(BlockState state) {
        return StardewCropRuntimeRegistry.isRegisteredBlock(state);
    }

    /**
     * Adds an addon crop root to StardewCraft's persistent daily scheduler.
     *
     * <p>Call this after placement. Repeated calls are safe. Core {@code StardewCropBlock}
     * instances already register themselves.
     */
    public static void track(ServerLevel level, BlockPos root) {
        track(level, root, null);
    }

    /** Adds an addon crop root and its optional planter identity to the daily scheduler. */
    public static void track(ServerLevel level, BlockPos root, @Nullable UUID planterId) {
        CropGrowthManager.get(level).addCrop(level, root, planterId);
    }

    /** Removes an addon crop root from StardewCraft's persistent daily scheduler. */
    public static void untrack(ServerLevel level, BlockPos root) {
        CropGrowthManager.get(level).removeCrop(level, root);
    }

    /**
     * Processes one day immediately using the same validated runtime path as the daily scheduler.
     * This is useful for addon catch-up systems and administrative tools.
     */
    public static StardewCropRuntimeAdapter.DailyResult growOneDay(
            ServerLevel level,
            BlockPos position,
            boolean watered,
            boolean offlineCatchUp
    ) {
        return StardewCropRuntimeRegistry.growOneDay(
                level, position, watered, offlineCatchUp);
    }

    /**
     * Harvests for a player after server-side crop identity and farm permission checks.
     *
     * <p>Addon outputs are dropped at the crop root, matching core crop behavior. The held stack is
     * exposed to the adapter as a defensive copy; tool durability and consumption remain owned by
     * the caller/tool implementation.
     */
    public static StardewCropHarvestResult harvestForPlayer(
            ServerPlayer player,
            BlockPos position,
            InteractionHand hand,
            boolean toolHarvest,
            boolean forceToolHarvest
    ) {
        ItemStack tool = player.getItemInHand(hand);
        int farmingLevel = com.stardew.craft.player.PlayerStardewDataAPI
                .getSkillLevel(player, com.stardew.craft.player.SkillType.FARMING);
        StardewCropState inspected = inspect(player.serverLevel(), position);
        BlockPos dropPosition = inspected == null ? position : inspected.root();
        return StardewCropRuntimeRegistry.harvest(
                player.serverLevel(),
                position,
                new StardewCropHarvestContext(
                        toolHarvest
                                ? StardewCropHarvestContext.Source.TOOL
                                : StardewCropHarvestContext.Source.PLAYER,
                        player,
                        tool,
                        farmingLevel,
                        forceToolHarvest),
                stack -> net.minecraft.world.level.block.Block.popResource(
                        player.serverLevel(), dropPosition, stack)
        );
    }

    /**
     * Harvests into an automation-owned output target. Farming experience is never awarded.
     */
    public static StardewCropHarvestResult harvestForAutomation(
            ServerLevel level,
            BlockPos position,
            int farmingLevel,
            Consumer<ItemStack> output
    ) {
        return StardewCropRuntimeRegistry.harvest(
                level,
                position,
                new StardewCropHarvestContext(
                        StardewCropHarvestContext.Source.AUTOMATION,
                        null,
                        ItemStack.EMPTY,
                        farmingLevel,
                        false),
                output
        );
    }

    /**
     * Removes a crop for a non-harvest world event after identity revalidation.
     *
     * <p>Addon adapters own arbitrary multi-block cleanup; scheduler state is removed only when
     * that cleanup reports success.
     */
    public static boolean remove(
            ServerLevel level,
            BlockPos position,
            StardewCropRemovalCause cause
    ) {
        return StardewCropRuntimeRegistry.remove(level, position, cause);
    }
}
