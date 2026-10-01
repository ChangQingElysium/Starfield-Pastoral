package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.StardewBlockFluidProtection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FluidProtectionEvents {
    private FluidProtectionEvents() {
    }

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        BlockState original = event.getOriginalState();
        if (original.isAir() || original.is(event.getNewState().getBlock())) {
            return;
        }
        if (StardewBlockFluidProtection.blocksFlowReplacement(original)) {
            event.setCanceled(true);
        }
    }
}
