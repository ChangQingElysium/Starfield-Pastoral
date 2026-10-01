package com.stardew.craft.npc.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent per-world NPC runtime state storage.
 */
public final class NpcRuntimeDataManager extends SavedData {
    private static final String DATA_NAME = "stardew_npc_runtime";

    private java.util.UUID scheduleContextPlayer;
    public java.util.UUID scheduleContextPlayer() { return scheduleContextPlayer; }
    public void setScheduleContextPlayer(java.util.UUID player) { scheduleContextPlayer=player; setDirty(); }

    private final Map<String, NpcRuntimeState> states = new LinkedHashMap<>();

    public static NpcRuntimeDataManager get(net.minecraft.server.level.ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(NpcRuntimeDataManager::new, NpcRuntimeDataManager::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(NpcRuntimeDataManager::new, NpcRuntimeDataManager::load)),
            DATA_NAME
        );
    }

    public Map<String, NpcRuntimeState> states() {
        return states;
    }

    public NpcRuntimeState getOrCreate(String npcId) {
        return states.computeIfAbsent(npcId, NpcRuntimeState::new);
    }

    public static NpcRuntimeDataManager load(CompoundTag tag, HolderLookup.Provider provider) {
        NpcRuntimeDataManager manager = new NpcRuntimeDataManager();
        if (tag.hasUUID("ScheduleContextPlayer")) manager.scheduleContextPlayer=tag.getUUID("ScheduleContextPlayer");
        int count = tag.getInt("Count");
        for (int i = 0; i < count; i++) {
            String key = "Npc_" + i;
            if (!tag.contains(key)) {
                continue;
            }
            CompoundTag npcTag = tag.getCompound(key);
            NpcRuntimeState state = NpcRuntimeState.fromNbt(npcTag);
            if (state.npcId() == null || state.npcId().isBlank()) {
                continue;
            }
            manager.states.put(state.npcId(), state);
        }

        return manager;
    }

    @Override
    @SuppressWarnings("null")
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        if (scheduleContextPlayer!=null) tag.putUUID("ScheduleContextPlayer",scheduleContextPlayer);
        tag.putInt("Count", states.size());
        int index = 0;
        for (NpcRuntimeState state : states.values()) {
            tag.put("Npc_" + index, state.toNbt());
            index++;
        }
        return tag;
    }
}
