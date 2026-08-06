package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.block.animal.AnimalProduceSpotBlock;
import com.stardew.craft.block.utility.AutoFeedTroughBlock;
import com.stardew.craft.block.utility.AutoGrabberBlock;
import com.stardew.craft.block.utility.AutoPetterBlock;
import com.stardew.craft.block.utility.FeedTroughBlock;
import com.stardew.craft.block.utility.HayHopperBlock;
import com.stardew.craft.block.utility.HeaterBlock;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.manager.AnimalGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Invalidates validated animal-building snapshots when a structural cell changes. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class AnimalBuildingStructureEvents {
    private AnimalBuildingStructureEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        invalidate(
                level,
                event.getPos(),
                event.getState(),
                "structure_block_broken");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        invalidate(
                level,
                event.getPos(),
                event.getPlacedBlock(),
                "structure_block_placed");
    }

    private static void invalidate(
            ServerLevel level,
            BlockPos pos,
            BlockState changedState,
            String issue
    ) {
        AnimalWorldData data = AnimalWorldData.get(level);
        for (var building : data.getBuildingsIncludingInactive()) {
            if (building.dimensionId().equals(
                    level.dimension().location().toString())
                    && building.isUtilityScanCell(pos)) {
                AnimalGrowthManager.get(level)
                        .invalidateBuildingUtilityCache(
                                building.buildingId());
            }
        }
        if (isInteriorAnimalBlock(changedState)) {
            return;
        }
        data.invalidateStructuresAt(
                level.dimension().location().toString(),
                pos,
                issue
        );
    }

    static boolean isInteriorAnimalBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FeedTroughBlock
                || block instanceof AutoFeedTroughBlock
                || block instanceof HayHopperBlock
                || block instanceof IncubatorBlock
                || block instanceof AutoPetterBlock
                || block instanceof AutoGrabberBlock
                || block instanceof HeaterBlock
                || block instanceof AnimalProduceSpotBlock;
    }
}
