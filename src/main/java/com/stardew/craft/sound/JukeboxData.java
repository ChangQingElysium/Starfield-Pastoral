package com.stardew.craft.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 存储各唱片机方块当前播放的曲目 ID。
 * 按维度保存（每个 ServerLevel 拥有独立实例）。
 */
public class JukeboxData extends SavedData {

    private static final String DATA_ID = "stardewcraft_jukebox";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_POS = "Pos";
    private static final String TAG_TRACK = "Track";

    private final Map<BlockPos, String> tracks = new HashMap<>();

    public JukeboxData() {}

    /** 获取当前 ServerLevel 的唱片机数据。 */
    public static JukeboxData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(JukeboxData::new, JukeboxData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(JukeboxData::new, JukeboxData::load)),
                DATA_ID
        );
    }

    public String getTrack(BlockPos pos) {
        return tracks.getOrDefault(pos, "");
    }

    public void setTrack(BlockPos pos, String trackId) {
        if (trackId == null || trackId.isEmpty() || JukeboxTrackRegistry.TURN_OFF.equals(trackId)) {
            tracks.remove(pos);
        } else {
            tracks.put(pos, trackId);
        }
        setDirty();
    }

    // ── 序列化 ──

    @Override
    public CompoundTag save(@javax.annotation.Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        ListTag list = new ListTag();
        for (var entry : tracks.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong(TAG_POS, entry.getKey().asLong());
            e.putString(TAG_TRACK, entry.getValue());
            list.add(e);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    private static JukeboxData load(CompoundTag tag, HolderLookup.Provider registries) {
        JukeboxData data = new JukeboxData();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockPos pos = BlockPos.of(e.getLong(TAG_POS));
            String track = e.getString(TAG_TRACK);
            if (!track.isEmpty()) {
                data.tracks.put(pos, track);
            }
        }
        return data;
    }
}
