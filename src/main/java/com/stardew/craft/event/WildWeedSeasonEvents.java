package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps weeds in chunks loaded after a season transition on the current model family. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WildWeedSeasonEvents {
    private static final int CHUNKS_PER_TICK = 16;
    private static final Map<ServerLevel, LinkedHashMap<Long, LevelChunk>> PENDING =
            new IdentityHashMap<>();

    private WildWeedSeasonEvents() {}

    /**
     * Rewrites the old per-season weed block IDs before vanilla decodes a chunk.
     * Older development builds used IDs such as wild_weeds_spring_0 for the
     * placed block itself. They must not survive as missing blocks or turn into
     * air: every palette entry is converted to the single wild_weeds block and
     * carries its old season/variant as block-state properties.
     */
    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        migrateLegacyChunkData(event.getData());
    }

    /** Pure NBT migration entry point, also used by the regression GameTest. */
    public static int migrateLegacyChunkData(CompoundTag data) {
        if (data == null) return 0;
        int changed = 0;
        if (data.contains("sections", Tag.TAG_LIST)) {
            ListTag sections = data.getList("sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < sections.size(); i++) {
                CompoundTag section = sections.getCompound(i);
                if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;
                CompoundTag blockStates = section.getCompound("block_states");
                if (!blockStates.contains("palette", Tag.TAG_LIST)) continue;
                ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
                for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                    CompoundTag entry = palette.getCompound(paletteIndex);
                    LegacyWeed legacy = parseLegacyWeed(entry.getString("Name"));
                    if (legacy == null) continue;

                    entry.putString("Name", StardewCraft.MODID + ":wild_weeds");
                    CompoundTag properties = entry.contains("Properties", Tag.TAG_COMPOUND)
                            ? entry.getCompound("Properties") : new CompoundTag();
                    properties.putString("season", Integer.toString(legacy.season()));
                    properties.putString("variant", Integer.toString(legacy.variant()));
                    entry.put("Properties", properties);
                    palette.set(paletteIndex, entry);
                    changed++;
                }
            }
        }

        // A legacy seasonal weed block could also have serialized its block
        // entity under the seasonal ID. Keep the block entity loadable by the
        // unified WildWeedsBlockEntity type as well.
        for (String key : new String[]{"block_entities", "TileEntities"}) {
            if (!data.contains(key, Tag.TAG_LIST)) continue;
            ListTag entities = data.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag entity = entities.getCompound(i);
                if (parseLegacyWeed(entity.getString("id")) == null) continue;
                entity.putString("id", StardewCraft.MODID + ":wild_weeds");
                entities.set(i, entity);
                changed++;
            }
        }
        return changed;
    }

    private static LegacyWeed parseLegacyWeed(String id) {
        String prefix = StardewCraft.MODID + ":wild_weeds_";
        if (id == null || !id.startsWith(prefix)) return null;
        String suffix = id.substring(prefix.length());
        String[] parts = suffix.split("_");
        if (parts.length < 2) return null;
        int season = switch (parts[0]) {
            case "spring" -> 0;
            case "summer" -> 1;
            case "fall" -> 2;
            case "winter" -> 3;
            default -> -1;
        };
        if (season < 0) return null;
        try {
            int variant = Integer.parseInt(parts[1]);
            return variant >= 0 && variant <= 2 ? new LegacyWeed(season, variant) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record LegacyWeed(int season, int variant) {}

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.STARDEW_VALLEY)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // Defer block writes until the normal level tick; ChunkEvent.Load can run
        // while the chunk manager is still resolving neighbouring chunks.
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                .put(chunk.getPos().toLong(), chunk);
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Map<Long, LevelChunk> pending = PENDING.get(level);
        if (pending == null) return;
        pending.remove(event.getChunk().getPos().toLong(), event.getChunk());
        if (pending.isEmpty()) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void afterLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            return;
        }
        LinkedHashMap<Long, LevelChunk> pending = PENDING.get(level);
        if (pending == null) return;

        Map<Long, LevelChunk> batch = new LinkedHashMap<>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < CHUNKS_PER_TICK) {
            var entry = iterator.next();
            batch.put(entry.getKey(), entry.getValue());
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(level);

        int season = StardewTimeManager.get().getCurrentSeason();
        for (LevelChunk chunk : batch.values()) {
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk) {
                WildWeedsBlock.refreshChunkWeedsForSeason(level, chunk, season);
            }
        }
    }
}
