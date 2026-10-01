package com.stardew.craft.weather;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 天气数据持久化
 */
public class WeatherSavedData extends SavedData {
    private static final String DATA_NAME = "stardew_weather_data";
    
    // 每个维度的天气状态
    private final Map<ResourceKey<Level>, WeatherManager.WeatherState> weatherByDimension = new HashMap<>();
    
    public WeatherSavedData() {}
    
    /**
     * 获取服务器级别的天气数据实例
     */
    public static WeatherSavedData get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                WeatherSavedData::new,
                                WeatherSavedData::load
                        ),
                        DATA_NAME
                );
    }
    
    /**
     * 从NBT加载数据
     */
    public static WeatherSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WeatherSavedData data = new WeatherSavedData();
        
        CompoundTag weatherData = tag.getCompound("weather");
        for (String dimKey : weatherData.getAllKeys()) {
            try {
                @SuppressWarnings("null")
                ResourceLocation dimLoc = new ResourceLocation(dimKey);
                @SuppressWarnings("null")
                ResourceKey<Level> levelKey = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    dimLoc
                );
                
                @SuppressWarnings("null")
                CompoundTag stateTag = weatherData.getCompound(dimKey);
                WeatherManager.WeatherState state = new WeatherManager.WeatherState();
                state.loadFromNBT(stateTag);
                data.weatherByDimension.put(levelKey, state);
                
                StardewCraft.LOGGER.info("Loaded weather for dimension {}: {}", 
                    dimKey, state.getWeatherType());
            } catch (Exception e) {
                StardewCraft.LOGGER.error("Failed to load weather for dimension {}", dimKey, e);
            }
        }
        
        return data;
    }
    
    /**
     * 保存数据到NBT
     */
    @SuppressWarnings("null")
    @Override
    public @NotNull CompoundTag save(@SuppressWarnings("null") @NotNull CompoundTag tag, @SuppressWarnings("null") HolderLookup.@NotNull Provider provider) {
        CompoundTag weatherData = new CompoundTag();
        
        for (Map.Entry<ResourceKey<Level>, WeatherManager.WeatherState> entry : weatherByDimension.entrySet()) {
            String dimKey = entry.getKey().location().toString();
            CompoundTag stateTag = new CompoundTag();
            entry.getValue().saveToNBT(stateTag);
            weatherData.put(dimKey, stateTag);
            
        }
        
        tag.put("weather", weatherData);
        return tag;
    }
    
    /**
     * 获取维度的天气状态
     */
    public WeatherManager.WeatherState getWeatherState(ResourceKey<Level> dimension) {
        return weatherByDimension.computeIfAbsent(dimension, k -> new WeatherManager.WeatherState());
    }
    
    /**
     * 设置维度的天气状态
     */
    public void setWeatherState(ResourceKey<Level> dimension, WeatherManager.WeatherState state) {
        weatherByDimension.put(dimension, state);
        setDirty();
    }
}
