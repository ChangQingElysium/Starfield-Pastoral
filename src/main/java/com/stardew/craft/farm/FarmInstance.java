package com.stardew.craft.farm;

import com.stardew.craft.api.v1.farm.StardewFarmPersistentData;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfiguration;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachment;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachmentKeys;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutRegistration;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ResourceLocation farmLayoutId;
    /**
     * Geometry captured when the farm was created. Existing farms therefore
     * remain navigable if an addon disappears or changes its registration.
     */
    private StardewFarmLayout farmLayoutSnapshot;
    /** Last successfully applied version of the selected layout. */
    private int farmLayoutVersion;
    /** Server-validated creation options; unknown keys survive addon removal. */
    private StardewFarmLayoutConfiguration farmLayoutConfiguration;
    /** Creation-time named points; preserved when the layout addon is absent. */
    private Map<ResourceLocation, StardewFarmLayoutAttachment>
            farmLayoutAttachments;
    private boolean initialized;
    private long createdTimestamp;
    private int lastOnlineDay;
    private int lastOnlineSeason;
    /** Last absolute day when any player visited this farm. */
    private int lastActiveDay;
    /** 跨季宽限剩余天数。>0 时该农场的过季作物不会枯死。 */
    private int graceDaysLeft;
    /** 洞穴类型选择（对齐 SDV Farmer.caveChoice） */
    private FarmCaveChoice caveChoice = FarmCaveChoice.NONE;
    private boolean goldClockPresent;
    private boolean goldClockEnabled = true;
    /** 农场成员 UUID 列表（不含 owner）。容量由 gamerule stardewMaxFarmersPerFarm 控制。 */
    private final List<UUID> members = new ArrayList<>();
    /** 已成功执行的附属初始化步骤版本；未知 ID 必须跨读写保留。 */
    private final Map<ResourceLocation, Integer> initializationStepVersions = new HashMap<>();
    /** 按附属命名空间隔离的版本化农场状态。 */
    private StardewFarmPersistentData persistentData = StardewFarmPersistentData.empty();

    public FarmInstance(UUID ownerUUID, String ownerName, String farmName,
                        int slotIndex, BlockPos origin, FarmType farmType) {
        this(ownerUUID, ownerName, farmName, slotIndex, origin,
                StardewFarmLayoutRegistry.builtinId(farmType));
    }

    public FarmInstance(UUID ownerUUID, String ownerName, String farmName,
                        int slotIndex, BlockPos origin,
                        ResourceLocation farmLayoutId) {
        this(ownerUUID, ownerName, farmName, slotIndex, origin, farmLayoutId,
                StardewFarmLayouts.find(farmLayoutId).orElse(null));
    }

    FarmInstance(UUID ownerUUID, String ownerName, String farmName,
                 int slotIndex, BlockPos origin,
                 ResourceLocation farmLayoutId,
                 StardewFarmLayout farmLayoutSnapshot) {
        this(ownerUUID, ownerName, farmName, slotIndex, origin, farmLayoutId,
                farmLayoutSnapshot,
                StardewFarmLayouts.findRegistration(farmLayoutId)
                        .map(registration -> registration.version())
                        .orElse(1),
                StardewFarmLayouts.findRegistration(farmLayoutId)
                        .map(registration -> registration.defaultConfiguration())
                        .orElseGet(StardewFarmLayoutConfiguration::empty),
                StardewFarmLayouts.findRegistration(farmLayoutId)
                        .map(StardewFarmLayoutRegistration::attachments)
                        .orElse(List.of()));
    }

    FarmInstance(UUID ownerUUID, String ownerName, String farmName,
                 int slotIndex, BlockPos origin,
                 ResourceLocation farmLayoutId,
                 StardewFarmLayout farmLayoutSnapshot,
                 int farmLayoutVersion,
                 StardewFarmLayoutConfiguration farmLayoutConfiguration) {
        this(ownerUUID, ownerName, farmName, slotIndex, origin,
                farmLayoutId, farmLayoutSnapshot, farmLayoutVersion,
                farmLayoutConfiguration,
                StardewFarmLayouts.findRegistration(farmLayoutId)
                        .map(StardewFarmLayoutRegistration::attachments)
                        .orElse(List.of()));
    }

    FarmInstance(UUID ownerUUID, String ownerName, String farmName,
                 int slotIndex, BlockPos origin,
                 ResourceLocation farmLayoutId,
                 StardewFarmLayout farmLayoutSnapshot,
                 int farmLayoutVersion,
                 StardewFarmLayoutConfiguration farmLayoutConfiguration,
                 List<StardewFarmLayoutAttachment> farmLayoutAttachments) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.farmName = farmName;
        this.slotIndex = slotIndex;
        this.origin = origin;
        this.farmLayoutId = farmLayoutId;
        this.farmLayoutSnapshot = farmLayoutSnapshot;
        this.farmLayoutVersion = Math.max(1, farmLayoutVersion);
        this.farmLayoutConfiguration = farmLayoutConfiguration == null
                ? StardewFarmLayoutConfiguration.empty()
                : farmLayoutConfiguration;
        this.farmLayoutAttachments = attachmentMap(
                farmLayoutAttachments);
        this.initialized = false;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastOnlineDay = 1;
        this.lastOnlineSeason = 0;
        this.lastActiveDay = 1;
        this.graceDaysLeft = 0;
    }

    // ── Getters ──

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public String getFarmName() { return farmName; }
    public int getSlotIndex() { return slotIndex; }
    public BlockPos getOrigin() { return origin; }
    /** Legacy enum view. Addon layouts intentionally fall back to STANDARD here. */
    public FarmType getFarmType() {
        return farmLayoutId.getNamespace().equals("stardewcraft")
                ? FarmType.fromId(farmLayoutId.getPath())
                : FarmType.STANDARD;
    }
    public ResourceLocation getFarmLayoutId() { return farmLayoutId; }
    public int getFarmLayoutVersion() { return farmLayoutVersion; }
    public StardewFarmLayoutConfiguration getFarmLayoutConfiguration() {
        return farmLayoutConfiguration;
    }
    public List<StardewFarmLayoutAttachment> getFarmLayoutAttachments() {
        return List.copyOf(farmLayoutAttachments.values());
    }
    public java.util.Optional<StardewFarmLayoutAttachment> findFarmLayoutAttachment(
            ResourceLocation id
    ) {
        return java.util.Optional.ofNullable(
                farmLayoutAttachments.get(id));
    }
    public StardewFarmLayout getFarmLayout() {
        if (farmLayoutSnapshot != null) {
            return farmLayoutSnapshot;
        }
        return StardewFarmLayouts.find(farmLayoutId)
                .orElseGet(() -> StardewFarmLayouts.find(
                        StardewFarmLayoutRegistry.builtinId(FarmType.STANDARD))
                        .orElseThrow());
    }
    public boolean isInitialized() { return initialized; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public int getLastOnlineDay() { return lastOnlineDay; }
    public int getLastOnlineSeason() { return lastOnlineSeason; }
    public int getLastActiveDay() { return lastActiveDay; }
    public int getGraceDaysLeft() { return graceDaysLeft; }
    public FarmCaveChoice getCaveChoice() { return caveChoice; }
    public boolean hasActiveGoldClock() { return goldClockPresent && goldClockEnabled; }
    public boolean hasGoldClock() { return goldClockPresent; }
    public boolean isGoldClockEnabled() { return goldClockEnabled; }
    public StardewFarmPersistentData persistentData() { return persistentData; }
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
    public void markActiveOnDay(int absoluteDay) {
        lastActiveDay = Math.max(lastActiveDay, absoluteDay);
    }

    public boolean wasActiveOnDay(int absoluteDay) {
        return lastActiveDay >= absoluteDay;
    }
    public void setGraceDaysLeft(int days) { this.graceDaysLeft = days; }
    public void setCreatedTimestamp(long ts) { this.createdTimestamp = ts; }
    public void setCaveChoice(FarmCaveChoice choice) { this.caveChoice = (choice == null ? FarmCaveChoice.NONE : choice); }
    public void setGoldClockState(boolean present, boolean enabled) {
        this.goldClockPresent = present;
        this.goldClockEnabled = enabled;
    }

    public void markFarmLayoutVersion(int version) {
        if (version < farmLayoutVersion) {
            throw new IllegalArgumentException("Farm layout version cannot move backwards");
        }
        farmLayoutVersion = version;
    }

    /**
     * Internal atomic completion used by the public migration facade.
     * A null layout preserves the existing creation-time geometry.
     */
    public void completeFarmLayoutMigration(
            int version,
            StardewFarmLayoutRegistration adoptedRegistration
    ) {
        StardewFarmLayout adoptedLayout = adoptedRegistration == null
                ? null : adoptedRegistration.layout();
        if (adoptedLayout != null
                && !adoptedLayout.id().equals(farmLayoutId)) {
            throw new IllegalArgumentException(
                    "Cannot adopt geometry from another farm layout");
        }
        markFarmLayoutVersion(version);
        if (adoptedLayout != null) {
            farmLayoutSnapshot = adoptedLayout;
            farmLayoutAttachments = attachmentMap(
                    adoptedRegistration.attachments());
        }
    }

    public int getInitializationStepVersion(ResourceLocation id) {
        return initializationStepVersions.getOrDefault(id, -1);
    }

    public void markInitializationStepComplete(ResourceLocation id, int version) {
        if (version < 0) {
            throw new IllegalArgumentException("Initialization step version must be non-negative");
        }
        initializationStepVersions.merge(id, version, Math::max);
    }

    /** Copies state that belongs to the farm itself when ownership changes. */
    void copyTransferStateFrom(FarmInstance source) {
        if (source.initialized) {
            markInitialized();
        }
        createdTimestamp = source.createdTimestamp;
        lastOnlineDay = source.lastOnlineDay;
        lastOnlineSeason = source.lastOnlineSeason;
        lastActiveDay = source.lastActiveDay;
        graceDaysLeft = source.graceDaysLeft;
        caveChoice = source.caveChoice;
        goldClockPresent = source.goldClockPresent;
        goldClockEnabled = source.goldClockEnabled;
        farmLayoutVersion = source.farmLayoutVersion;
        farmLayoutConfiguration = new StardewFarmLayoutConfiguration(
                source.farmLayoutConfiguration.values());
        farmLayoutAttachments = attachmentMap(
                source.getFarmLayoutAttachments());
        initializationStepVersions.clear();
        initializationStepVersions.putAll(source.initializationStepVersions);
        persistentData = StardewFarmPersistentData.fromTag(source.persistentData.toTag());
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
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.SPAWN);
        if (attachment != null) {
            return attachment.resolve(origin);
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.spawnOffset()) : origin;
    }

    /** 出生点朝向 */
    public float getSpawnYaw() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.SPAWN);
        if (attachment != null) {
            return attachment.yaw();
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? layout.spawnYaw() : 90.0f;
    }

    /** 农场图腾柱位置 */
    public BlockPos getFarmTotemPos() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.FARM_TOTEM);
        if (attachment != null) {
            return attachment.resolve(origin);
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.totemOffset()) : origin;
    }

    /** 温室位置 */
    public BlockPos getGreenhousePos() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.GREENHOUSE);
        if (attachment != null) {
            return attachment.resolve(origin);
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.greenhouseOffset()) : origin;
    }

    /** 从公共区域南入口进入时的传送目标 */
    public BlockPos getSouthEntryPos() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.ENTRY_SOUTH);
        if (attachment != null) {
            return attachment.resolve(origin);
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.entrySouth().teleportOffset()) : getSpawnPoint();
    }

    public float getSouthEntryYaw() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.ENTRY_SOUTH);
        if (attachment != null) {
            return attachment.yaw();
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? layout.entrySouth().yaw() : 90.0f;
    }

    /** 从公共区域东入口进入时的传送目标 */
    public BlockPos getEastEntryPos() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.ENTRY_EAST);
        if (attachment != null) {
            return attachment.resolve(origin);
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.entryEast().teleportOffset()) : getSpawnPoint();
    }

    public float getEastEntryYaw() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.ENTRY_EAST);
        if (attachment != null) {
            return attachment.yaw();
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? layout.entryEast().yaw() : -90.0f;
    }

    /** 从公共区域西入口进入时的传送目标 */
    public BlockPos getWestEntryPos() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.ENTRY_WEST);
        if (attachment != null) {
            return attachment.resolve(origin);
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.entryWest().teleportOffset()) : getSpawnPoint();
    }

    public float getWestEntryYaw() {
        StardewFarmLayoutAttachment attachment =
                farmLayoutAttachments.get(
                        StardewFarmLayoutAttachmentKeys.ENTRY_WEST);
        if (attachment != null) {
            return attachment.yaw();
        }
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? layout.entryWest().yaw() : 180.0f;
    }

    /** 农场区域边界最小坐标 */
    public BlockPos getFarmBoundsMin() {
        StardewFarmLayout layout = getFarmLayout();
        return layout != null ? origin.offset(layout.boundsMin()) : origin;
    }

    /** 农场区域边界最大坐标 */
    public BlockPos getFarmBoundsMax() {
        StardewFarmLayout layout = getFarmLayout();
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
        tag.putString("FarmType", getFarmType().getId());
        tag.putString("FarmLayoutId", farmLayoutId.toString());
        tag.putInt("FarmLayoutVersion", farmLayoutVersion);
        if (!farmLayoutConfiguration.values().isEmpty()) {
            CompoundTag configuration = new CompoundTag();
            farmLayoutConfiguration.values().forEach(
                    (id, value) -> configuration.putString(id.toString(), value));
            tag.put("FarmLayoutConfiguration", configuration);
        }
        if (!farmLayoutAttachments.isEmpty()) {
            CompoundTag attachments = new CompoundTag();
            farmLayoutAttachments.forEach((id, attachment) -> {
                CompoundTag stored = new CompoundTag();
                putPos(stored, "Offset", attachment.offset());
                stored.putFloat("Yaw", attachment.yaw());
                if (!attachment.tags().isEmpty()) {
                    ListTag tags = new ListTag();
                    for (ResourceLocation attachmentTag
                            : attachment.tags()) {
                        CompoundTag tagEntry = new CompoundTag();
                        tagEntry.putString(
                                "Id", attachmentTag.toString());
                        tags.add(tagEntry);
                    }
                    stored.put("Tags", tags);
                }
                attachments.put(id.toString(), stored);
            });
            tag.put("FarmLayoutAttachments", attachments);
        }
        if (farmLayoutSnapshot != null) {
            tag.put("FarmLayoutSnapshot", saveLayout(farmLayoutSnapshot));
        }
        tag.putBoolean("Initialized", initialized);
        tag.putLong("CreatedTimestamp", createdTimestamp);
        tag.putInt("LastOnlineDay", lastOnlineDay);
        tag.putInt("LastOnlineSeason", lastOnlineSeason);
        tag.putInt("LastActiveDay", lastActiveDay);
        tag.putInt("GraceDaysLeft", graceDaysLeft);
        tag.putString("CaveChoice", caveChoice.getName());
        tag.putBoolean("GoldClockPresent", goldClockPresent);
        tag.putBoolean("GoldClockEnabled", goldClockEnabled);
        if (!initializationStepVersions.isEmpty()) {
            CompoundTag completedSteps = new CompoundTag();
            initializationStepVersions.forEach(
                    (id, version) -> completedSteps.putInt(id.toString(), version));
            tag.put("AddonInitializationSteps", completedSteps);
        }
        if (!persistentData.isEmpty()) {
            tag.put("AddonData", persistentData.toTag());
        }
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
        ResourceLocation farmLayoutId = tag.contains(
                "FarmLayoutId", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("FarmLayoutId"))
                : null;
        if (farmLayoutId == null) {
            FarmType farmType = FarmType.fromId(tag.getString("FarmType"));
            farmLayoutId = StardewFarmLayoutRegistry.builtinId(farmType);
        }

        StardewFarmLayout layoutSnapshot = null;
        if (tag.contains("FarmLayoutSnapshot", Tag.TAG_COMPOUND)) {
            layoutSnapshot = loadLayout(
                    farmLayoutId, tag.getCompound("FarmLayoutSnapshot"));
        }
        if (layoutSnapshot == null) {
            layoutSnapshot = StardewFarmLayouts.find(farmLayoutId).orElse(null);
        }

        int layoutVersion = tag.contains("FarmLayoutVersion", Tag.TAG_INT)
                ? Math.max(1, tag.getInt("FarmLayoutVersion")) : 1;
        Map<ResourceLocation, String> configurationValues = new HashMap<>();
        if (tag.contains("FarmLayoutConfiguration", Tag.TAG_COMPOUND)) {
            CompoundTag configuration = tag.getCompound("FarmLayoutConfiguration");
            for (String rawId : configuration.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(rawId);
                if (id != null && configuration.contains(rawId, Tag.TAG_STRING)) {
                    configurationValues.put(id, configuration.getString(rawId));
                }
            }
        }
        List<StardewFarmLayoutAttachment> attachmentValues =
                loadAttachments(tag, farmLayoutId, layoutSnapshot);

        FarmInstance instance = new FarmInstance(
                uuid, ownerName, farmName, slotIndex, origin,
                farmLayoutId, layoutSnapshot, layoutVersion,
                new StardewFarmLayoutConfiguration(configurationValues),
                attachmentValues);
        instance.initialized = tag.getBoolean("Initialized");
        instance.createdTimestamp = tag.getLong("CreatedTimestamp");
        instance.lastOnlineDay = tag.getInt("LastOnlineDay");
        instance.lastOnlineSeason = tag.getInt("LastOnlineSeason");
        instance.lastActiveDay = tag.contains("LastActiveDay", Tag.TAG_INT)
                ? tag.getInt("LastActiveDay")
                : instance.lastOnlineDay;
        instance.graceDaysLeft = tag.getInt("GraceDaysLeft");
        if (tag.contains("CaveChoice", Tag.TAG_STRING)) {
            FarmCaveChoice parsed = FarmCaveChoice.fromName(tag.getString("CaveChoice"));
            instance.caveChoice = parsed != null ? parsed : FarmCaveChoice.NONE;
        } else {
            instance.caveChoice = FarmCaveChoice.NONE;
        }
        instance.goldClockPresent = tag.getBoolean("GoldClockPresent");
        instance.goldClockEnabled = !tag.contains("GoldClockEnabled") || tag.getBoolean("GoldClockEnabled");
        if (tag.contains("AddonInitializationSteps", Tag.TAG_COMPOUND)) {
            CompoundTag completedSteps = tag.getCompound("AddonInitializationSteps");
            for (String rawId : completedSteps.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(rawId);
                if (id != null && completedSteps.contains(rawId, Tag.TAG_INT)) {
                    instance.initializationStepVersions.put(id, completedSteps.getInt(rawId));
                }
            }
        }
        if (tag.contains("AddonData", Tag.TAG_COMPOUND)) {
            instance.persistentData = StardewFarmPersistentData.fromTag(
                    tag.getCompound("AddonData"));
        }
        // 加载成员列表
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < memberList.size(); i++) {
                instance.members.add(memberList.getCompound(i).getUUID("UUID"));
            }
        }
        return instance;
    }

    private static CompoundTag saveLayout(StardewFarmLayout layout) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Selectable", layout.selectable());
        tag.putString("Icon", layout.iconTexture().toString());
        tag.putString("Schematic", layout.schematic().toString());
        tag.putInt("OriginY", layout.originY());
        tag.putInt("Width", layout.width());
        tag.putInt("Height", layout.height());
        tag.putInt("Length", layout.length());
        putPos(tag, "Spawn", layout.spawnOffset());
        tag.putFloat("SpawnYaw", layout.spawnYaw());
        putPos(tag, "Greenhouse", layout.greenhouseOffset());
        putPos(tag, "Totem", layout.totemOffset());
        tag.put("EntrySouth", saveEntry(layout.entrySouth()));
        tag.put("EntryEast", saveEntry(layout.entryEast()));
        tag.put("EntryWest", saveEntry(layout.entryWest()));
        if (layout.biomeId() != null) {
            tag.putString("BiomeId", layout.biomeId());
        }
        putNullablePos(tag, "ForageMin", layout.forageZoneMin());
        putNullablePos(tag, "ForageMax", layout.forageZoneMax());
        putRegion(tag, "CaveBlackWall", layout.caveBlackWall());
        putRegion(tag, "CavePortalWall", layout.cavePortalWall());
        putRegion(tag, "CaveClearBox", layout.caveClearBox());
        putNullablePos(tag, "CaveExit", layout.caveExitSpawn());
        tag.putFloat("CaveExitYaw", layout.caveExitYaw());
        return tag;
    }

    private static Map<ResourceLocation, StardewFarmLayoutAttachment> attachmentMap(
            List<StardewFarmLayoutAttachment> attachments
    ) {
        LinkedHashMap<ResourceLocation, StardewFarmLayoutAttachment> values =
                new LinkedHashMap<>();
        for (StardewFarmLayoutAttachment attachment : attachments) {
            if (values.putIfAbsent(attachment.id(), attachment) != null) {
                throw new IllegalArgumentException(
                        "Duplicate farm layout attachment: "
                                + attachment.id());
            }
        }
        return values;
    }

    private static List<StardewFarmLayoutAttachment> loadAttachments(
            CompoundTag farmTag,
            ResourceLocation farmLayoutId,
            StardewFarmLayout layoutSnapshot
    ) {
        if (!farmTag.contains(
                "FarmLayoutAttachments", Tag.TAG_COMPOUND)) {
            return StardewFarmLayouts.findRegistration(farmLayoutId)
                    .map(StardewFarmLayoutRegistration::attachments)
                    .orElseGet(() -> layoutSnapshot == null
                            ? List.of()
                            : StardewFarmLayoutRegistry
                                    .projectLegacyAttachments(
                                            layoutSnapshot));
        }
        CompoundTag storedAttachments =
                farmTag.getCompound("FarmLayoutAttachments");
        ArrayList<StardewFarmLayoutAttachment> attachments =
                new ArrayList<>();
        for (String rawId : storedAttachments.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null || !storedAttachments.contains(
                    rawId, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag stored = storedAttachments.getCompound(rawId);
            java.util.HashSet<ResourceLocation> tags =
                    new java.util.HashSet<>();
            if (stored.contains("Tags", Tag.TAG_LIST)) {
                ListTag storedTags = stored.getList(
                        "Tags", Tag.TAG_COMPOUND);
                for (int index = 0; index < storedTags.size(); index++) {
                    ResourceLocation attachmentTag =
                            ResourceLocation.tryParse(
                                    storedTags.getCompound(index)
                                            .getString("Id"));
                    if (attachmentTag != null) {
                        tags.add(attachmentTag);
                    }
                }
            }
            attachments.add(new StardewFarmLayoutAttachment(
                    id,
                    getPos(stored, "Offset"),
                    stored.getFloat("Yaw"),
                    tags));
        }
        return List.copyOf(attachments);
    }

    private static StardewFarmLayout loadLayout(
            ResourceLocation id,
            CompoundTag tag
    ) {
        try {
            ResourceLocation icon = ResourceLocation.tryParse(
                    tag.getString("Icon"));
            ResourceLocation schematic = ResourceLocation.tryParse(
                    tag.getString("Schematic"));
            if (icon == null || schematic == null) {
                return null;
            }
            return new StardewFarmLayout(
                    id,
                    tag.getBoolean("Selectable"),
                    Component.literal(id.toString()),
                    Component.empty(),
                    icon,
                    schematic,
                    tag.getInt("OriginY"),
                    tag.getInt("Width"),
                    tag.getInt("Height"),
                    tag.getInt("Length"),
                    getPos(tag, "Spawn"),
                    tag.getFloat("SpawnYaw"),
                    getPos(tag, "Greenhouse"),
                    getPos(tag, "Totem"),
                    loadEntry(tag.getCompound("EntrySouth")),
                    loadEntry(tag.getCompound("EntryEast")),
                    loadEntry(tag.getCompound("EntryWest")),
                    tag.contains("BiomeId", Tag.TAG_STRING)
                            ? tag.getString("BiomeId") : null,
                    getNullablePos(tag, "ForageMin"),
                    getNullablePos(tag, "ForageMax"),
                    getRegion(tag, "CaveBlackWall"),
                    getRegion(tag, "CavePortalWall"),
                    getRegion(tag, "CaveClearBox"),
                    getNullablePos(tag, "CaveExit"),
                    tag.getFloat("CaveExitYaw"));
        } catch (RuntimeException malformedSnapshot) {
            return null;
        }
    }

    private static CompoundTag saveEntry(StardewFarmLayout.Entry entry) {
        CompoundTag tag = new CompoundTag();
        putPos(tag, "Teleport", entry.teleportOffset());
        tag.putFloat("Yaw", entry.yaw());
        putPos(tag, "ExitMin", entry.exitMin());
        putPos(tag, "ExitMax", entry.exitMax());
        return tag;
    }

    private static StardewFarmLayout.Entry loadEntry(CompoundTag tag) {
        return new StardewFarmLayout.Entry(
                getPos(tag, "Teleport"),
                tag.getFloat("Yaw"),
                getPos(tag, "ExitMin"),
                getPos(tag, "ExitMax"));
    }

    private static void putRegion(
            CompoundTag parent,
            String key,
            StardewFarmLayout.Region region
    ) {
        if (region == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        putPos(tag, "Min", region.min());
        putPos(tag, "Max", region.max());
        parent.put(key, tag);
    }

    private static StardewFarmLayout.Region getRegion(
            CompoundTag parent,
            String key
    ) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = parent.getCompound(key);
        return new StardewFarmLayout.Region(
                getPos(tag, "Min"), getPos(tag, "Max"));
    }

    private static void putNullablePos(
            CompoundTag parent,
            String key,
            BlockPos pos
    ) {
        if (pos != null) {
            putPos(parent, key, pos);
        }
    }

    private static BlockPos getNullablePos(CompoundTag parent, String key) {
        return parent.contains(key, Tag.TAG_COMPOUND)
                ? getPos(parent, key) : null;
    }

    private static void putPos(
            CompoundTag parent,
            String key,
            BlockPos pos
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        parent.put(key, tag);
    }

    private static BlockPos getPos(CompoundTag parent, String key) {
        CompoundTag tag = parent.getCompound(key);
        return new BlockPos(
                tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }
}
