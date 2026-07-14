package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家数据管理器
 * 负责管理所有玩家的星露谷数据，使用 SavedData 持久化到世界存储
 */
public class PlayerDataManager extends SavedData {
    
    private static final String DATA_NAME = "stardew_player_data";
    
    // 存储所有玩家的数据
    private final Map<UUID, PlayerStardewData> playerDataMap = new HashMap<>();
    
    public PlayerDataManager() {
    }
    
    /**
     * 获取玩家数据，如果不存在则创建新的
     */
    public PlayerStardewData getOrCreateData(UUID playerUUID) {
        return playerDataMap.computeIfAbsent(playerUUID, uuid -> {
            StardewCraft.LOGGER.info("Creating new player data for: {}", uuid);
            PlayerStardewData data = new PlayerStardewData(uuid);
            setDirty();
            return data;
        });
    }
    
    /**
     * 获取玩家数据，如果不存在返回 null
     */
    public PlayerStardewData getData(UUID playerUUID) {
        return playerDataMap.get(playerUUID);
    }

    public Map<UUID, PlayerStardewData> getAllPlayerData() {
        return Collections.unmodifiableMap(playerDataMap);
    }
    
    /**
     * 保存玩家数据
     */
    public void savePlayerData(UUID playerUUID, PlayerStardewData data) {
        playerDataMap.put(playerUUID, data);
        setDirty();
    }
    
    /**
     * 移除玩家数据（可选，通常不需要）
     */
    public void removePlayerData(UUID playerUUID) {
        playerDataMap.remove(playerUUID);
        setDirty();
    }
    
    /**
     * 检查并保存所有标记为脏的数据
     */
    public void tickAndSaveDirty() {
        boolean anyDirty = false;
        for (PlayerStardewData data : playerDataMap.values()) {
            if (data.isDirty()) {
                data.markClean();
                anyDirty = true;
            }
        }
        if (anyDirty) {
            setDirty();
        }
    }
    
    // ============ SavedData 实现 ============
    
    @SuppressWarnings("null")
    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider provider) {
        // 保存玩家数量
        tag.putInt("PlayerCount", playerDataMap.size());
        
        // 保存每个玩家的数据
        int index = 0;
        for (Map.Entry<UUID, PlayerStardewData> entry : playerDataMap.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());
            playerTag.put("Data", entry.getValue().toNBT(provider));
            tag.put("Player_" + index, playerTag);
            index++;
        }
        
        StardewCraft.LOGGER.info("[STARDEW PLAYER] Saved {} player data entries", playerDataMap.size());
        return tag;
    }
    
    /**
     * 从NBT加载数据
     */
    public static PlayerDataManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        PlayerDataManager manager = new PlayerDataManager();
        
        int playerCount = tag.getInt("PlayerCount");
        
        for (int i = 0; i < playerCount; i++) {
            String key = "Player_" + i;
            if (tag.contains(key)) {
                CompoundTag playerTag = tag.getCompound(key);
                UUID playerUUID = playerTag.getUUID("UUID");
                CompoundTag dataTag = playerTag.getCompound("Data");
                
                PlayerStardewData data = PlayerStardewData.fromNBT(dataTag, playerUUID, provider);
                manager.playerDataMap.put(playerUUID, data);
            }
        }
        
        StardewCraft.LOGGER.info("[STARDEW PLAYER] Loaded {} player data entries", manager.playerDataMap.size());
        return manager;
    }
    
    /**
     * 获取全局实例
     */
    @SuppressWarnings("null")
    public static PlayerDataManager get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Cannot get PlayerDataManager on client side or before server started!");
        }
        
        @SuppressWarnings("null")
        var overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld not found!");
        }
        
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                PlayerDataManager::new,
                PlayerDataManager::load,
                null
            ),
            DATA_NAME
        );
    }
    
    /**
     * 静态工具方法：获取玩家数据
     */
    public static PlayerStardewData getPlayerData(ServerPlayer player) {
        return get().getOrCreateData(player.getUUID());
    }
    
    /**
     * 静态工具方法：获取玩家数据（通过UUID）
     */
    public static PlayerStardewData getPlayerData(UUID playerUUID) {
        return get().getOrCreateData(playerUUID);
    }
}
