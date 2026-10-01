package com.stardew.craft.block.utility;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Repair taller approved furniture without a production ticker after chunk loading. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FarmComputerFootprintMigration {
    private static final Map<ServerLevel, LinkedHashMap<Long, LevelChunk>> PENDING = new IdentityHashMap<>();
    private FarmComputerFootprintMigration() {}

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), chunk);
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pending = PENDING.get(level);
        if (pending != null) pending.remove(event.getChunk().getPos().toLong(), event.getChunk());
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var pending = PENDING.get(level);
        if (pending == null) return;
        // Never update neighbors from ChunkEvent.Load, which can run inside a managed chunk wait.
        var batch = new java.util.ArrayList<LevelChunk>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext() && batch.size() < 2) {
            batch.add(iterator.next());
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(level);
        for (var chunk : batch) {
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) continue;
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                var section = chunk.getSection(sectionIndex);
                if (!section.maybeHas(state -> (state.getBlock() instanceof FarmComputerBlock || state.getBlock() instanceof MiniObeliskBlock || state.getBlock() instanceof FridgeBlock))) continue;
                int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) * 16;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (!(state.getBlock() instanceof MapUtilityStaticBlock computer)
                            || !(computer instanceof FarmComputerBlock || computer instanceof MiniObeliskBlock || computer instanceof FridgeBlock)
                            || state.getValue(MapUtilityStaticBlock.PART) != MapUtilityStaticBlock.Part.MAIN) continue;
                    var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                    computer.placeExtensions(level, pos, state);
                }
            }
        }
    }
}
