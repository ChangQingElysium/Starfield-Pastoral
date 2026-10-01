package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 全局矿井数据管理器 - 保存所有玩家的矿井进度
 */
public class MiningDataManager extends SavedData {
    
    private static final String DATA_NAME = "stardew_mining_data";

    /**
     * Replay/partial servers such as Flashback do not necessarily construct the mining
     * dimension which normally owns this SavedData. Keep their projected player state
     * isolated in memory so generic player ticks can safely query mine progress without
     * writing replay data into an unrelated dimension.
     */
    private static final Map<MinecraftServer, MiningDataManager> TRANSIENT_SERVERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    
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
        MinecraftServer server = player.getServer();
        ServerLevel miningLevel = server.getLevel(com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING);
        if (miningLevel == null) {
            return TRANSIENT_SERVERS.computeIfAbsent(server, ignored -> {
                StardewCraft.LOGGER.debug(
                        "Using transient mining data because the mining dimension is unavailable");
                return new MiningDataManager();
            });
        }
        return miningLevel.getDataStorage()
            .computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(
                    MiningDataManager::new,
                    MiningDataManager::load
                )), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(
                    MiningDataManager::new,
                    MiningDataManager::load
                )),
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
    public @NotNull CompoundTag save(@SuppressWarnings("null") @NotNull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag playersTag = new CompoundTag();
        
        for (Map.Entry<UUID, MiningPlayerData> entry : playerDataMap.entrySet()) {
            playersTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        
        tag.put("players", playersTag);
        return tag;
    }
}
