package com.stardew.craft.mining;

import net.minecraft.nbt.CompoundTag;

/**
 * 玩家矿井数据 - 记录当前层数和最深到达层数
 */
public class MiningPlayerData {
    
    private int currentFloor = 0;  // 当前所在层数（0=大厅）
    private int maxFloorReached = 0;  // 最深到达的层数
    private boolean receivedMineTotem = false; // 是否已领取矿洞图腾
    
    public MiningPlayerData() {
        this.currentFloor = 0;
        this.maxFloorReached = 0;
        this.receivedMineTotem = false;
    }
    
    /**
     * 获取当前层数
     */
    public int getCurrentFloor() {
        return currentFloor;
    }
    
    /**
     * 设置当前层数
     */
    public void setCurrentFloor(int floor) {
        this.currentFloor = floor;
        if (floor > maxFloorReached) {
            this.maxFloorReached = floor;
        }
    }
    
    /**
     * 获取最深到达层数
     */
    public int getMaxFloorReached() {
        return maxFloorReached;
    }

    public boolean hasReceivedMineTotem() {
        return receivedMineTotem;
    }

    public void setReceivedMineTotem(boolean received) {
        this.receivedMineTotem = received;
    }
    
    /**
     * 保存到 NBT
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("currentFloor", currentFloor);
        tag.putInt("maxFloorReached", maxFloorReached);
        tag.putBoolean("receivedMineTotem", receivedMineTotem);
        return tag;
    }
    
    /**
     * 从 NBT 加载
     */
    public void load(CompoundTag tag) {
        this.currentFloor = tag.getInt("currentFloor");
        this.maxFloorReached = tag.getInt("maxFloorReached");
        this.receivedMineTotem = tag.getBoolean("receivedMineTotem");
    }
    
    /**
     * 从 NBT 创建
     */
    public static MiningPlayerData fromNBT(CompoundTag tag) {
        MiningPlayerData data = new MiningPlayerData();
        data.load(tag);
        return data;
    }
}
