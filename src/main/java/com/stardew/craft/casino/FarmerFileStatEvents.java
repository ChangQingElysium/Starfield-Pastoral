package com.stardew.craft.casino;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Fills SDV Farmer File counters that vanilla Minecraft statistics don't provide. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FarmerFileStatEvents {
    private FarmerFileStatEvents() {
    }

    @SubscribeEvent
    public static void onCropPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getPlacedBlock().getBlock() instanceof StardewCropBlock)
                || !event.getPlacedBlock().hasProperty(StardewCropBlock.AGE)
                || event.getPlacedBlock().getValue(StardewCropBlock.AGE) != StardewCropBlock.SEED_PHASE) {
            return;
        }
        if (event.getPlacedBlock().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && event.getPlacedBlock().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return;
        }
        PlayerDataManager.getPlayerData(player).incrementStat("seedsSown", 1);
    }
}
