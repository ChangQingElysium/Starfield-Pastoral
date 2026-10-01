package com.stardew.craft.tree;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.tree.StardewLeavesBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Update opacity through normal block updates, on season boundaries and when old chunks load. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SeasonalLeafLighting {
    private static final Map<ServerLevel, Set<Long>> LOADED = new WeakHashMap<>();
    private static final Map<ServerLevel, Boolean> WINTER = new WeakHashMap<>();

    private SeasonalLeafLighting() {}

    @SubscribeEvent
    public static void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Chunk load can run before it is safe to issue setBlock/update-light calls.
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> {
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
            LOADED.computeIfAbsent(level, ignored -> new HashSet<>()).add(chunk.getPos().toLong());
            refreshChunk(level, chunk);
        }));
    }

    @SubscribeEvent
    public static void unload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<Long> chunks = LOADED.get(level);
        if (chunks != null) chunks.remove(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED.remove(level);
            WINTER.remove(level);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) refreshSeason(level);
    }

    public static void refreshSeason(ServerLevel level) {
        boolean winter = level.dimension().equals(ModDimensions.STARDEW_VALLEY)
                && StardewTimeManager.get().getCurrentSeason() == 3;
        Boolean previous = WINTER.put(level, winter);
        if (previous != null && previous == winter) return;
        for (long packed : LOADED.getOrDefault(level, Set.of())) {
            var pos = new ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) refreshChunk(level, chunk);
        }
    }

    public static void refreshChunk(ServerLevel level, LevelChunk chunk) {
        var cursor = new BlockPos.MutableBlockPos();
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (!section.maybeHas(state -> state.hasProperty(StardewLeavesBlock.DORMANT)
                    && StardewLeavesBlock.seasonalState(state, level) != state)) continue;
            int baseY = chunk.getMinBuildHeight() + index * 16;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                var updated = StardewLeavesBlock.seasonalState(state, level);
                if (updated == state) continue;
                cursor.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                // setBlock schedules light propagation and sends the state to clients, including adjacent meshes.
                level.setBlock(cursor, updated, Block.UPDATE_CLIENTS);
            }
        }
    }
}
