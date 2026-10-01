package com.stardew.craft.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 矿井楼层数据管理器
 * 
 * 记录每层的生成状态和刷新时间：
 * - 每层只在第一次进入时生成
 * - 每天刷新（隔天重置）
 * - 存储stonesLeft等楼层数据
 */
public class MineFloorDataManager extends SavedData {
    
    private static final String DATA_NAME = "stardew_mine_floors";
    
    // 每层的最后生成日期（floor -> lastGeneratedDay）
    private final Map<Integer, Integer> floorGenerationDays = new HashMap<>();
    
    // 每层的楼层数据（floor -> MineFloorData）
    private final Map<Integer, MineFloorData> floorDataMap = new HashMap<>();
    
    public MineFloorDataManager() {
        super();
    }
    
    /**
     * 获取管理器实例
     */
    public static MineFloorDataManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(
                MineFloorDataManager::new,
                MineFloorDataManager::load
            )), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(
                MineFloorDataManager::new,
                MineFloorDataManager::load
            )),
            DATA_NAME
        );
    }
    
    /**
     * 检查楼层是否需要生成/刷新
     */
    public boolean needsGeneration(int floor) {
        if (!floorGenerationDays.containsKey(floor)) {
            return true; // 从未生成过
        }
        
        // 检查是否是新的一天
        int currentDay = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        int lastDay = floorGenerationDays.get(floor);
        return currentDay != lastDay;
    }
    
    /**
     * 标记楼层已生成
     */
    public void markGenerated(int floor) {
        int currentDay = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        floorGenerationDays.put(floor, currentDay);
        setDirty();
    }
    
    
    /**
     * 获取楼层数据（如果不存在返回null）
     */
    public MineFloorData getFloorData(int floorNumber) {
        return floorDataMap.get(floorNumber);
    }
    
    /**
     * 设置楼层数据
     */
    public void setFloorData(int floorNumber, MineFloorData data) {
        floorDataMap.put(floorNumber, data);
        setDirty();
    }
    
    /**
     * 清除楼层数据（隔天刷新时调用）
     */
    public java.util.Set<Integer> floorNumbers() { return java.util.Set.copyOf(floorDataMap.keySet()); }

    public void clearFloorData(int floorNumber) {
        floorDataMap.remove(floorNumber);
        setDirty();
    }
    
    /**
     * 从 NBT 加载
     */
    public static MineFloorDataManager load(CompoundTag tag, HolderLookup.Provider provider) {
        MineFloorDataManager manager = new MineFloorDataManager();
        
        // 加载生成日期
        CompoundTag floorsTag = tag.getCompound("floors");
        for (String key : floorsTag.getAllKeys()) {
            int floor = Integer.parseInt(key);
            @SuppressWarnings("null")
            int day = floorsTag.getInt(key);
            manager.floorGenerationDays.put(floor, day);
        }
        
        // 加载楼层数据
        if (tag.contains("floorData", Tag.TAG_LIST)) {
            ListTag floorDataList = tag.getList("floorData", Tag.TAG_COMPOUND);
            for (int i = 0; i < floorDataList.size(); i++) {
                CompoundTag floorDataTag = floorDataList.getCompound(i);
                int floorNumber = floorDataTag.getInt("floorNumber");
                MineFloorData data = MineFloorData.fromNBT(floorDataTag.getCompound("data"));
                manager.floorDataMap.put(floorNumber, data);
            }
        }
        
        return manager;
    }
    
    /**
     * 保存到 NBT
     */
    @SuppressWarnings("null")
    @Override
    public @NotNull CompoundTag save(@SuppressWarnings("null") @NotNull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        // 保存生成日期
        CompoundTag floorsTag = new CompoundTag();
        for (Map.Entry<Integer, Integer> entry : floorGenerationDays.entrySet()) {
            floorsTag.putInt(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("floors", floorsTag);
        
        // 保存楼层数据
        ListTag floorDataList = new ListTag();
        for (Map.Entry<Integer, MineFloorData> entry : floorDataMap.entrySet()) {
            CompoundTag floorDataTag = new CompoundTag();
            floorDataTag.putInt("floorNumber", entry.getKey());
            floorDataTag.put("data", entry.getValue().toNBT());
            floorDataList.add(floorDataTag);
        }
        tag.put("floorData", floorDataList);
        
        return tag;
    }
}
