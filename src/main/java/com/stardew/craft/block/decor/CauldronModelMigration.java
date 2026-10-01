package com.stardew.craft.block.decor;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;

/** Old static wizard cauldrons have no saved block-entity tag, on either side. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CauldronModelMigration {
    private CauldronModelMigration() {}

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            var section = chunk.getSection(i);
            if (!section.maybeHas(state -> state.is(ModBlocks.WIZARD_CAULDRON.get()))) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                if (!state.is(ModBlocks.WIZARD_CAULDRON.get())
                        || state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) continue;
                var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                // Only populate this chunk's entity map here. Neighbor occupancy is repaired by its server ticker.
                chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
            }
        }
    }
}
