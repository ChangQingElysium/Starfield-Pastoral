package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 单个玩家农场实例的数据记录。
 * 不可变（创建后字段不会改变，除 initialized 和 farmName）。
 */
public class FarmInstance {

    private final UUID ownerUUID;
    private String ownerName;
    private String farmName;
    private final int slotIndex;
    private final BlockPos origin;
    private final FarmType farmType;
    private boolean initialized;
    private long createdTimestamp;
    private int lastOnlineDay;
    private int lastOnlineSeason;
    /** 跨季宽限剩余天数。>0 时该农场的过季作物不会枯死。 */
    private int graceDaysLeft;
    /** 洞穴类型选择（对齐 SDV Farmer.caveChoice） */
    private FarmCaveChoice caveChoice = FarmCaveChoice.NONE;
    private boolean goldClockPresent;
    private boolean goldClockEnabled = true;
    /** 农场成员 UUID 列表（不含 owner）。容量由 gamerule stardewMaxFarmersPerFarm 控制。 */
    private final List<UUID> members = new ArrayList<>();

    public FarmInstance(UUID ownerUUID, String ownerName, String farmName,
                        int slotIndex, BlockPos origin, FarmType farmType) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.farmName = farmName;
        this.slotIndex = slotIndex;
        this.origin = origin;
        this.farmType = farmType;
        this.initialized = false;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastOnlineDay = 1;
        this.lastOnlineSeason = 0;
        this.graceDaysLeft = 0;
    }

    // ── Getters ──

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public String getFarmName() { return farmName; }
    public int getSlotIndex() { return slotIndex; }
    public BlockPos getOrigin() { return origin; }
    public FarmType getFarmType() { return farmType; }
    public boolean isInitialized() { return initialized; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public int getLastOnlineDay() { return lastOnlineDay; }
    public int getLastOnlineSeason() { return lastOnlineSeason; }
    public int getGraceDaysLeft() { return graceDaysLeft; }
    public FarmCaveChoice getCaveChoice() { return caveChoice; }
    public boolean hasActiveGoldClock() { return goldClockPresent && goldClockEnabled; }
    public boolean hasGoldClock() { return goldClockPresent; }
    /** 获取成员列表（只读，不含 owner） */
    public List<UUID> getMembers() { return Collections.unmodifiableList(members); }

    /** 获取所有共同农场主（owner + members） */
    public List<UUID> getAllFarmers() {
        List<UUID> all = new ArrayList<>(members.size() + 1);
        all.add(ownerUUID);
        all.addAll(members);
        return all;
    }

    /** 是否为该农场的成员（含 owner） */
    public boolean isFarmer(UUID uuid) {
        return ownerUUID.equals(uuid) || members.contains(uuid);
    }

    /** 农场当前人数（owner + members） */
    public int getFarmerCount() { return 1 + members.size(); }

    // ── Setters (mutable fields) ──

    public void setFarmName(String farmName) { this.farmName = farmName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void markInitialized() { this.initialized = true; }
    public void setLastOnlineDay(int day) { this.lastOnlineDay = day; }
    public void setLastOnlineSeason(int season) { this.lastOnlineSeason = season; }
    public void setGraceDaysLeft(int days) { this.graceDaysLeft = days; }
    public void setCreatedTimestamp(long ts) { this.createdTimestamp = ts; }
    public void setCaveChoice(FarmCaveChoice choice) { this.caveChoice = (choice == null ? FarmCaveChoice.NONE : choice); }
    public void setGoldClockState(boolean present, boolean enabled) {
        this.goldClockPresent = present;
        this.goldClockEnabled = enabled;
    }

    /** 添加成员。返回 false 如果已满或已存在。 */
    public boolean addMember(UUID uuid, int maxFarmers) {
        if (isFarmer(uuid)) return false;
        if (getFarmerCount() >= Math.max(1, maxFarmers)) return false;
        members.add(uuid);
        return true;
    }

    /** 移除成员。不能移除 owner。 */
    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    // ── 坐标计算（从 FarmType 布局读取偏移） ──

    /** 农场出生点 */
    public BlockPos getSpawnPoint() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.spawnOffset()) : origin;
    }

    /** 出生点朝向 */
    public float getSpawnYaw() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? layout.spawnYaw() : 90.0f;
    }

    /** 农场图腾柱位置 */
    public BlockPos getFarmTotemPos() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.totemOffset()) : origin;
    }

    /** 温室位置 */
    public BlockPos getGreenhousePos() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.greenhouseOffset()) : origin;
    }

    /** 从公共区域南入口进入时的传送目标 */
    public BlockPos getSouthEntryPos() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.entrySouth().teleportOffset()) : getSpawnPoint();
    }

    public float getSouthEntryYaw() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? layout.entrySouth().yaw() : 90.0f;
    }

    /** 从公共区域东入口进入时的传送目标 */
    public BlockPos getEastEntryPos() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.entryEast().teleportOffset()) : getSpawnPoint();
    }

    public float getEastEntryYaw() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? layout.entryEast().yaw() : -90.0f;
    }

    /** 从公共区域西入口进入时的传送目标 */
    public BlockPos getWestEntryPos() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.entryWest().teleportOffset()) : getSpawnPoint();
    }

    public float getWestEntryYaw() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? layout.entryWest().yaw() : 180.0f;
    }

    /** 农场区域边界最小坐标 */
    public BlockPos getFarmBoundsMin() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.boundsMin()) : origin;
    }

    /** 农场区域边界最大坐标 */
    public BlockPos getFarmBoundsMax() {
        FarmType.FarmLayout layout = farmType.getLayout();
        return layout != null ? origin.offset(layout.boundsMax()) : origin.offset(336, 22, 381);
    }

    /** 判断某位置是否在此农场边界内 */
    public boolean contains(BlockPos pos) {
        BlockPos min = getFarmBoundsMin();
        BlockPos max = getFarmBoundsMax();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    // ── NBT 序列化 ──

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("OwnerUUID", ownerUUID);
        tag.putString("OwnerName", ownerName);
        tag.putString("FarmName", farmName);
        tag.putInt("SlotIndex", slotIndex);
        tag.putInt("OriginX", origin.getX());
        tag.putInt("OriginY", origin.getY());
        tag.putInt("OriginZ", origin.getZ());
        tag.putString("FarmType", farmType.getId());
        tag.putBoolean("Initialized", initialized);
        tag.putLong("CreatedTimestamp", createdTimestamp);
        tag.putInt("LastOnlineDay", lastOnlineDay);
        tag.putInt("LastOnlineSeason", lastOnlineSeason);
        tag.putInt("GraceDaysLeft", graceDaysLeft);
        tag.putString("CaveChoice", caveChoice.getName());
        tag.putBoolean("GoldClockPresent", goldClockPresent);
        tag.putBoolean("GoldClockEnabled", goldClockEnabled);
        // 成员列表
        if (!members.isEmpty()) {
            ListTag memberList = new ListTag();
            for (UUID m : members) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("UUID", m);
                memberList.add(mt);
            }
            tag.put("Members", memberList);
        }
        return tag;
    }

    public static FarmInstance load(CompoundTag tag) {
        UUID uuid = tag.getUUID("OwnerUUID");
        String ownerName = tag.getString("OwnerName");
        String farmName = tag.getString("FarmName");
        int slotIndex = tag.getInt("SlotIndex");
        BlockPos origin = new BlockPos(tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
        FarmType farmType = FarmType.fromId(tag.getString("FarmType"));

        FarmInstance instance = new FarmInstance(uuid, ownerName, farmName, slotIndex, origin, farmType);
        instance.initialized = tag.getBoolean("Initialized");
        instance.createdTimestamp = tag.getLong("CreatedTimestamp");
        instance.lastOnlineDay = tag.getInt("LastOnlineDay");
        instance.lastOnlineSeason = tag.getInt("LastOnlineSeason");
        instance.graceDaysLeft = tag.getInt("GraceDaysLeft");
        if (tag.contains("CaveChoice", Tag.TAG_STRING)) {
            FarmCaveChoice parsed = FarmCaveChoice.fromName(tag.getString("CaveChoice"));
            instance.caveChoice = parsed != null ? parsed : FarmCaveChoice.NONE;
        } else {
            instance.caveChoice = FarmCaveChoice.NONE;
        }
        instance.goldClockPresent = tag.getBoolean("GoldClockPresent");
        instance.goldClockEnabled = !tag.contains("GoldClockEnabled") || tag.getBoolean("GoldClockEnabled");
        // 加载成员列表
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < memberList.size(); i++) {
                instance.members.add(memberList.getCompound(i).getUUID("UUID"));
            }
        }
        return instance;
    }
}
