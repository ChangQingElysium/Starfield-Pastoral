package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.crop.giant.GiantCropBlock;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;

/** Restore new upper carriers when an existing giant crop is loaded from an older save. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class GiantCropFootprintEvents {
    private GiantCropFootprintEvents() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> {
            if (!level.hasChunk(chunk.getPos().x, chunk.getPos().z)) return;
            var roots = new HashSet<BlockPos>();
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                var section = sections[index];
                if (!section.maybeHas(state -> state.getBlock() instanceof GiantCropBlock)) continue;
                int baseY = chunk.getMinBuildHeight() + index * 16;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (!(state.getBlock() instanceof GiantCropBlock giant)) continue;
                    var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                    var main = giant.findMainPos(level, pos, state);
                    if (main != null) roots.add(main);
                }
            }
            // Loading either side of a chunk boundary can complete an old footprint.
            for (BlockPos main : roots) {
                if (level.getBlockState(main).getBlock() instanceof GiantCropBlock giant) level.scheduleTick(main, giant, 1);
            }
        }));
    }
}
