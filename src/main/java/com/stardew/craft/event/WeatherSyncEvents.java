package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.WeatherSyncPacket;
import com.stardew.craft.weather.ClientWeatherCache;
import com.stardew.craft.weather.WeatherManager;
import com.stardew.craft.weather.WeatherSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/**
 * 天气数据同步事件处理器
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class WeatherSyncEvents {
    
    /**
     * 玩家登录时同步天气数据
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            syncWeatherToPlayer(player, level);
            StardewCraft.LOGGER.info("Player {} logged in, syncing weather", player.getName().getString());
        }
    }
    
    /**
     * 玩家切换维度时同步新维度的天气数据
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            syncWeatherToPlayer(player, level);
            StardewCraft.LOGGER.info("Player {} changed dimension, syncing weather", player.getName().getString());
        }
    }
    
    @SuppressWarnings("null")
    private static void syncWeatherToPlayer(ServerPlayer player, ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        WeatherSavedData data = WeatherSavedData.get(level);
        WeatherManager.WeatherState state = data.getWeatherState(level.dimension());
        
        String dimStr = level.dimension().location().toString();
        WeatherSyncPacket packet = new WeatherSyncPacket(
            dimStr,
            state.getWeatherType(),
            state.getWeatherForTomorrow(),
            state.isRaining(),
            state.isThundering()
        );
        
        PacketDistributor.sendToPlayer(player, packet);
    }
    
    /**
     * 客户端事件处理器
     */
    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        
        /**
         * 客户端断开连接时清空天气缓存
         */
        @SubscribeEvent
        public static void onClientDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
            ClientWeatherCache.clear();
            StardewCraft.LOGGER.info("Client disconnected, clearing weather cache");
        }
    }
}
