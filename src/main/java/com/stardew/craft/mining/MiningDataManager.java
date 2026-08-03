package com.stardew.craft.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * 全局矿井数据管理器 - 保存所有玩家的矿井进度
 */
public class MiningDataManager extends SavedData {
    
    private static final String DATA_NAME = "stardew_mining_data";
    
    private final Map<UUID, MiningPlayerData> playerDataMap = new HashMap<>();
    
    public MiningDataManager() {
        super();
    }
    
    public void clearPlayer(UUID playerId) {
        playerDataMap.remove(playerId);
        com.stardew.craft.leaderboard.LeaderboardService.invalidateCache();
        setDirty();
    }

    public static void clearPlayerData(ServerPlayer player) {
        get(player).clearPlayer(player.getUUID());
    }

    /**
     * 获取玩家的矿井数据
     */
    public static MiningPlayerData getPlayerData(ServerPlayer player) {
        MiningDataManager manager = get(player);
        return manager.playerDataMap.computeIfAbsent(
            player.getUUID(), 
            uuid -> new MiningPlayerData()
        );
    }

    public static Map<UUID, MiningPlayerData> getAllPlayerData(ServerPlayer player) {
        return Collections.unmodifiableMap(get(player).playerDataMap);
    }
    
    /**
     * 保存玩家数据并标记需要保存
     */
    public static void savePlayerData(ServerPlayer player, MiningPlayerData data) {
        MiningDataManager manager = get(player);
        manager.playerDataMap.put(player.getUUID(), data);
        com.stardew.craft.npc.runtime.NpcDialogueTopicService
                .onMineFloorReached(player, data.getCurrentFloor());
        com.stardew.craft.leaderboard.LeaderboardService.invalidateCache();
        manager.setDirty();
    }
    
    /**
     * 获取管理器实例
     */
    @SuppressWarnings("null")
    private static MiningDataManager get(ServerPlayer player) {
        return player.getServer()
            .getLevel(com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING)
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(
                    MiningDataManager::new,
                    MiningDataManager::load
                ),
                DATA_NAME
            );
    }
    
    /**
     * 从 NBT 加载
     */
    public static MiningDataManager load(CompoundTag tag, HolderLookup.Provider provider) {
        MiningDataManager manager = new MiningDataManager();
        
        CompoundTag playersTag = tag.getCompound("players");
        for (String key : playersTag.getAllKeys()) {
            UUID uuid = UUID.fromString(key);
            @SuppressWarnings("null")
            CompoundTag playerTag = playersTag.getCompound(key);
            manager.playerDataMap.put(uuid, MiningPlayerData.fromNBT(playerTag));
        }
        
        return manager;
    }
    
    /**
     * 保存到 NBT
     */
    @SuppressWarnings("null")
    @Override
    public @NotNull CompoundTag save(@SuppressWarnings("null") @NotNull CompoundTag tag, @SuppressWarnings("null") HolderLookup.@NotNull Provider provider) {
        CompoundTag playersTag = new CompoundTag();
        
        for (Map.Entry<UUID, MiningPlayerData> entry : playerDataMap.entrySet()) {
            playersTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        
        tag.put("players", playersTag);
        return tag;
    }
}
