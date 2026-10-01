package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 雨滴隐藏器 - 在下雪天隐藏原版雨滴粒子
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class WeatherRainHider {

    private static int lastCheckTick = 0;

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ClientLevel level = mc.level;

        // 每10 ticks检查一次（降低性能消耗）
        lastCheckTick++;
        if (lastCheckTick < 10) {
            return;
        }
        lastCheckTick = 0;

        // 检查是否是下雪天（使用客户端缓存）
        boolean isSnowing = "Snow".equals(ClientWeatherCache.getCurrentWeather(level.dimension()));

        if (isSnowing && level.isRaining()) {
            // 雪天时，强制设置rainLevel为0来隐藏雨滴粒子
            // 但保持 isRaining() 为 true，这样耕地润湿等逻辑仍然有效
            level.rainLevel = 0.0f;
            level.oRainLevel = 0.0f;
        }
    }
}
