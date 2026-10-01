package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;

/**
 * 肥料数据同步事件处理器
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class FertilizerSyncEvents {
    
    /**
     * 玩家登录时同步所有肥料数据
     * 延迟20 tick (1秒) 以确保区块已加载
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            // 延迟20 tick同步，确保客户端区块已加载
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 20,
                () -> {
                    FertilizerManager manager = FertilizerManager.get(player.serverLevel());
                    manager.syncAllFertilizersToPlayer(player);
                }
            ));
        }
    }
    
    /**
     * 玩家切换维度时同步新维度的肥料数据
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            FertilizerManager manager = FertilizerManager.get(level);
            manager.syncAllFertilizersToPlayer(player);
        }
    }

    // PORT(1.20.1): Forge fires ChunkWatchEvent.Watch after the chunk packet was sent (MinecraftForge: Sent).
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        ServerLevel level = event.getLevel();
        if (player.serverLevel() != level) {
            return;
        }
        FertilizerManager.get(level).syncFertilizersInChunkToPlayer(player, level, event.getPos());
    }

    @SubscribeEvent
    public static void onChunkUnwatched(ChunkWatchEvent.UnWatch event) {
        FertilizerManager.get(event.getLevel()).clearFertilizersInChunkForPlayer(
                event.getPlayer(), event.getLevel(), event.getPos());
    }
    
    /**
     * 客户端事件处理器
     */
    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        
        /**
         * 客户端断开连接时清空肥料缓存
         */
        @SubscribeEvent
        public static void onClientDisconnect(
                net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event
        ) {
            com.stardew.craft.client.ClientFertilizerCache.clear();
        }
    }
}
