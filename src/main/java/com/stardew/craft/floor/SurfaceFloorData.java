package com.stardew.craft.floor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

/** Dimension-owned surface data; the support block and the space above remain untouched. */
public final class SurfaceFloorData extends SavedData {
    public record Cover(SurfaceFloorType type, int variant) {
        public Cover { variant = Math.floorMod(variant, 16); }
    }
    private static final String ID = "stardew_surface_floors";
    private static final Factory<SurfaceFloorData> FACTORY = new Factory<>(SurfaceFloorData::new, SurfaceFloorData::load);
    private final Map<Long, Map<Long, Cover>> chunks = new HashMap<>();

    public static SurfaceFloorData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static void supportChanged(ServerLevel level, BlockPos pos) {
        SurfaceFloorData data = level.getDataStorage().get(FACTORY, ID);
        if (data == null || data.at(pos) == null
                || SurfaceFloorItem.supports(level, pos, level.getBlockState(pos))) return;
        if (level.captureBlockSnapshots) {
            // Placement/tool transactions can roll back; only remove after their final state is known.
            BlockPos stablePos = pos.immutable();
            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(),
                    () -> supportChanged(level, stablePos)));
        } else {
            data.remove(level, pos, true);
        }
    }

    public Cover at(BlockPos pos) {
        var chunk = chunks.get(ChunkPos.asLong(pos));
        return chunk == null ? null : chunk.get(pos.asLong());
    }

    public boolean place(ServerLevel level, BlockPos pos, SurfaceFloorType type, ServerPlayer player) {
        Cover previous = at(pos);
        if (previous != null && previous.type() == type) return false;
        int variant = type == SurfaceFloorType.STEPPING_STONE_PATH ? level.random.nextInt(16) : 0;
        var cover = new Cover(type, variant);
        chunks.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>()).put(pos.asLong(), cover);
        setDirty();
        if (previous != null && !player.isCreative()) Block.popResource(level, pos.above(), new ItemStack(previous.type().item()));
        sync(level, pos, cover);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, .7f, 1.15f);
        return true;
    }

    public boolean remove(ServerLevel level, BlockPos pos, boolean drop) {
        var chunk = chunks.get(ChunkPos.asLong(pos));
        Cover old = chunk == null ? null : chunk.remove(pos.asLong());
        if (old == null) return false;
        if (chunk.isEmpty()) chunks.remove(ChunkPos.asLong(pos));
        setDirty();
        sync(level, pos, null);
        if (drop) Block.popResource(level, pos.above(), new ItemStack(old.type().item()));
        return true;
    }

    /** Exact transactional replay used when moving a whole building, without drops or random variation. */
    public void restore(ServerLevel level, BlockPos pos, Cover cover) {
        chunks.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>()).put(pos.asLong(), cover);
        setDirty(); sync(level, pos, cover);
    }

    /** Removes virtual floor covers during an authoritative region deletion without spawning drops. */
    public void removeRegion(BlockPos min, BlockPos max) {
        boolean changed = false;
        for (var chunkEntry : new ArrayList<>(chunks.entrySet())) {
            var covers = chunkEntry.getValue();
            changed |= covers.keySet().removeIf(key -> {
                BlockPos pos = BlockPos.of(key);
                return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                        && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                        && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
            });
            if (covers.isEmpty()) chunks.remove(chunkEntry.getKey());
        }
        if (changed) setDirty();
    }

    private void sync(ServerLevel level, BlockPos pos, Cover cover) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
                new SurfaceFloorPacket(level.dimension().location(), new ChunkPos(pos).toLong(), false,
                        List.of(SurfaceFloorPacket.Entry.of(pos, cover))));
    }

    public void sendChunk(ServerLevel level, ChunkPos pos, ServerPlayer player) {
        var entries = new ArrayList<SurfaceFloorPacket.Entry>();
        var chunk = chunks.get(pos.toLong());
        if (chunk != null) chunk.forEach((key, cover) -> entries.add(SurfaceFloorPacket.Entry.of(BlockPos.of(key), cover)));
        // Bounded batches also handle tall, densely covered chunks without oversize custom packets.
        for (int offset = 0; offset < Math.max(1, entries.size()); offset += SurfaceFloorPacket.MAX_ENTRIES) {
            PacketDistributor.sendToPlayer(player, new SurfaceFloorPacket(level.dimension().location(), pos.toLong(), offset == 0,
                    entries.subList(offset, Math.min(entries.size(), offset + SurfaceFloorPacket.MAX_ENTRIES))));
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        chunks.values().forEach(chunk -> chunk.forEach((pos, cover) -> {
            var entry = new CompoundTag();
            entry.putLong("Pos", pos);
            entry.putString("Type", cover.type().id);
            entry.putInt("Variant", cover.variant());
            list.add(entry);
        }));
        tag.put("Floors", list);
        return tag;
    }

    public static SurfaceFloorData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SurfaceFloorData();
        for (Tag raw : tag.getList("Floors", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            SurfaceFloorType type = SurfaceFloorType.byId(entry.getString("Type"));
            if (type == null) continue;
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            data.chunks.computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>())
                    .put(pos.asLong(), new Cover(type, entry.getInt("Variant")));
        }
        return data;
    }
}
