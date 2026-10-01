package com.stardew.craft.network;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.weather.ClientWeatherCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端独立处理 {@link WeatherSyncPacket}，避免在专用服务器
 * 注册阶段触发 {@code net.minecraft.client.*} 类加载。
 */
@OnlyIn(Dist.CLIENT)
public final class WeatherSyncPacketClient {

    private WeatherSyncPacketClient() {}

    public static void apply(WeatherSyncPacket packet) {
        try {
            ResourceLocation dimLoc = new ResourceLocation(packet.dimension());
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ClientWeatherCache.setWeather(dimKey, packet.weatherType(), packet.weatherForTomorrow());

            if (!ModDimensions.STARDEW_VALLEY.equals(dimKey)) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.multiplayer.ClientLevel level = mc.level;
            if (level != null && level.dimension().equals(dimKey)) {
                float targetRain = packet.raining() ? 1.0f : 0.0f;
                float targetThunder = packet.thundering() ? 1.0f : 0.0f;
                level.rainLevel = targetRain;
                level.oRainLevel = targetRain;
                level.thunderLevel = targetThunder;
                level.oThunderLevel = targetThunder;
                level.getLevelData().setRaining(packet.raining());
            }
        } catch (Exception e) {
            com.stardew.craft.StardewCraft.LOGGER.error("Failed to parse weather sync packet", e);
        }
    }
}
