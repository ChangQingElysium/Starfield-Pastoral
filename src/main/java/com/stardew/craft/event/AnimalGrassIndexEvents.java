package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.service.AnimalGrassTargetService;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;

/** Releases transient grass-index state when a chunk leaves memory. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class AnimalGrassIndexEvents {
    private AnimalGrassIndexEvents() {
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        AnimalGrassTargetService.invalidateChunk(
                level,
                event.getChunk().getPos().x,
                event.getChunk().getPos().z
        );
    }
}
