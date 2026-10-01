package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLifecycle;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfiguration;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutRegistration;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.farm.StardewFarmSnapshot;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLifecycleRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * 核心 SavedData：管理所有玩家的农场实例。
 * 存储在 Overworld 的 DataStorage 中，全局唯一。
 */
@SuppressWarnings("null")
public class FarmInstanceRegistry extends SavedData {

    private static final String DATA_NAME = "stardew_farm_instances";

    /** 注册键 → 农场实例。普通农场的键是主人 UUID，调试附加农场使用隔离的代理 UUID。 */
    private final Map<UUID, FarmInstance> instances = new HashMap<>();
    /** 槽位序号 → 玩家UUID（用于反查） */
    private final Map<Integer, UUID> slotToOwner = new HashMap<>();
    /** 成员UUID → 农场主人UUID（反查索引，不含 owner 自己） */
    private final Map<UUID, UUID> memberToOwner = new HashMap<>();
    /** 调试附加农场注册键 → 实际控制玩家。 */
    private final Map<UUID, UUID> debugFarmControllers = new LinkedHashMap<>();
    /** 玩家 → 当前选中的农场注册键；未设置时仍按原有主农场规则解析。 */
    private final Map<UUID, UUID> selectedFarmByPlayer = new HashMap<>();
    /** 可跨重启恢复的调试农场删除任务，按实例 ID 标记。 */
    private final Set<UUID> pendingDebugFarmDeletions = new HashSet<>();
    /** 下一个可分配的槽位序号 */
    private int nextSlotIndex = 0;

    public FarmInstanceRegistry() {}

    // ── 访问方法 ──

    /**
     * 获取全局唯一实例。
     */
    public static FarmInstanceRegistry get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new FarmInstanceRegistry();
        return get(server);
    }

    public static FarmInstanceRegistry get(MinecraftServer server) {
        return Objects.requireNonNull(server, "server")
                .overworld().getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    /**
     * 获取玩家的农场实例，没有则返回 null。
     */
    @Nullable
    public FarmInstance getFarm(UUID playerUUID) {
        return instances.get(playerUUID);
    }

    /** 按稳定实例 ID 精确查找，不会受玩家当前选择影响。 */
    @Nullable
    public FarmInstance getFarmByInstanceId(UUID instanceId) {
        if (instanceId == null) return null;
        for (FarmInstance farm : instances.values()) {
            if (instanceId.equals(farm.getInstanceId())) return farm;
        }
        return null;
    }

    /** 返回实例在注册表中的内部键，供坐标、权限和删除流程精确定位。 */
    @Nullable
    public UUID getRegistryKey(FarmInstance farm) {
        if (farm == null) return null;
        for (var entry : instances.entrySet()) {
            if (entry.getValue() == farm
                    || entry.getValue().getInstanceId().equals(farm.getInstanceId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 玩家是否已有农场（作为 owner 或 member）。
     */
    public boolean hasFarm(UUID playerUUID) {
        return getFarmForPlayer(playerUUID) != null;
    }

    /**
     * 获取玩家所属农场（作为 owner 或 member）。
     */
    @Nullable
    public FarmInstance getFarmForPlayer(UUID playerUUID) {
        UUID selectedKey = selectedFarmByPlayer.get(playerUUID);
        FarmInstance selected = selectedKey == null ? null : instances.get(selectedKey);
        if (selected != null && selected.isFarmer(playerUUID)) return selected;
        FarmInstance own = instances.get(playerUUID);
        if (own != null) return own;
        UUID ownerUUID = memberToOwner.get(playerUUID);
        if (ownerUUID != null) return instances.get(ownerUUID);
        return debugFarmControllers.entrySet().stream()
                .filter(entry -> entry.getValue().equals(playerUUID))
                .map(entry -> instances.get(entry.getKey()))
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(FarmInstance::getSlotIndex))
                .orElse(null);
    }

    /**
     * 获取玩家所属农场的 owner UUID（如果该玩家是 owner 则返回自己，是 member 则返回 owner）。
     */
    @Nullable
    public UUID getOwnerForPlayer(UUID playerUUID) {
        FarmInstance farm = getFarmForPlayer(playerUUID);
        return getRegistryKey(farm);
    }

    /** 玩家可通过调试流程选择其主农场或名下任一附加农场。 */
    public boolean selectFarm(UUID playerUUID, UUID instanceId) {
        FarmInstance farm = getFarmByInstanceId(instanceId);
        UUID key = getRegistryKey(farm);
        if (farm == null || key == null || !farm.isFarmer(playerUUID)) return false;
        selectedFarmByPlayer.put(playerUUID, key);
        setDirty();
        return true;
    }

    public List<FarmInstance> getFarmsForPlayer(UUID playerUUID) {
        return instances.values().stream()
                .filter(farm -> farm.isFarmer(playerUUID))
                .sorted(Comparator.comparingInt(FarmInstance::getSlotIndex))
                .toList();
    }

    public List<FarmInstance> getDebugFarms(UUID controller) {
        return debugFarmControllers.entrySet().stream()
                .filter(entry -> entry.getValue().equals(controller))
                .map(entry -> instances.get(entry.getKey()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(FarmInstance::getSlotIndex))
                .toList();
    }

    public boolean isDebugFarm(UUID instanceId, UUID controller) {
        FarmInstance farm = getFarmByInstanceId(instanceId);
        UUID key = getRegistryKey(farm);
        return key != null && controller.equals(debugFarmControllers.get(key));
    }

    @Nullable
    public UUID getDebugFarmController(UUID instanceId) {
        FarmInstance farm = getFarmByInstanceId(instanceId);
        UUID key = getRegistryKey(farm);
        return key == null ? null : debugFarmControllers.get(key);
    }

    public boolean beginDebugFarmDeletion(UUID controller, UUID instanceId) {
        if (!isDebugFarm(instanceId, controller)
                || !pendingDebugFarmDeletions.add(instanceId)) return false;
        setDirty();
        return true;
    }

    public Set<UUID> getPendingDebugFarmDeletions() {
        return Set.copyOf(pendingDebugFarmDeletions);
    }

    /**
     * 判断两个玩家是否属于同一农场（owner 或 member 均可）。
     */
    public boolean areFarmmates(UUID playerA, UUID playerB) {
        if (playerA.equals(playerB)) return true;
        UUID ownerA = getOwnerForPlayer(playerA);
        UUID ownerB = getOwnerForPlayer(playerB);
        return ownerA != null && ownerA.equals(ownerB);
    }

    /**
     * 判断玩家是否可以操作某个建筑（建筑属于自己，或和建筑主人同属一个农场）。
     */
    public boolean canOperateBuilding(UUID playerUUID, String buildingOwnerUuid) {
        if (buildingOwnerUuid == null || buildingOwnerUuid.isBlank()) return false;
        try {
            UUID buildingOwner = UUID.fromString(buildingOwnerUuid);
            UUID farmOwner = getOwnerForPlayer(buildingOwner);
            if (farmOwner == null) {
                farmOwner = buildingOwner;
            }
            return FarmPermissionManager.get().canModify(farmOwner, playerUUID);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 添加成员到某个农场。
     * @return true 成功，false 失败（农场不存在/已满/该玩家已有农场）
     */
    public boolean addMember(UUID ownerUUID, UUID memberUUID) {
        FarmInstance farm = instances.get(ownerUUID);
        if (farm == null) return false;
        if (hasFarm(memberUUID)) return false; // 已有农场（拥有或加入）
        if (!farm.addMember(memberUUID, maxFarmersPerFarm())) return false;
        memberToOwner.put(memberUUID, ownerUUID);
        setDirty();
        StardewCraft.LOGGER.info("[FARM_REGISTRY] Added member {} to {}'s farm", memberUUID, ownerUUID);
        return true;
    }

    /**
     * 从农场移除成员。
     */
    public boolean removeMember(UUID ownerUUID, UUID memberUUID) {
        FarmInstance farm = instances.get(ownerUUID);
        if (farm == null) return false;
        if (!farm.removeMember(memberUUID)) return false;
        memberToOwner.remove(memberUUID);
        setDirty();
        StardewCraft.LOGGER.info("[FARM_REGISTRY] Removed member {} from {}'s farm", memberUUID, ownerUUID);
        return true;
    }

    /**
     * 重建 memberToOwner 索引（从所有 FarmInstance 的 members 列表）。
     */
    private void rebuildMemberIndex() {
        memberToOwner.clear();
        for (var entry : instances.entrySet()) {
            if (debugFarmControllers.containsKey(entry.getKey())) continue;
            FarmInstance farm = entry.getValue();
            for (UUID member : farm.getMembers()) {
                memberToOwner.put(member, farm.getOwnerUUID());
            }
        }
    }

    /**
     * 获取玩家的农场出生点，没有农场则返回 null。
     * 支持 owner 和 member。
     */
    @Nullable
    public BlockPos getFarmSpawnPoint(UUID playerUUID) {
        FarmInstance farm = getFarmForPlayer(playerUUID);
        return farm != null ? farm.getSpawnPoint() : null;
    }

    /**
     * 根据坐标反查归属玩家。O(1) 计算。
     */
    @Nullable
    public UUID getOwnerAt(BlockPos pos) {
        int slotIndex = FarmInstanceAllocator.getSlotIndexAt(pos);
        if (slotIndex < 0) return null;
        return slotToOwner.get(slotIndex);
    }

    /**
     * 根据槽位序号查找归属玩家。
     */
    @Nullable
    public UUID getOwnerBySlot(int slotIndex) {
        return slotToOwner.get(slotIndex);
    }

    /**
     * 获取所有农场实例（只读）。
     */
    public Collection<FarmInstance> getAllFarms() {
        return Collections.unmodifiableCollection(instances.values());
    }

    /**
     * 获取农场总数。
     */
    public int getFarmCount() {
        return instances.size();
    }

    /**
     * 获取下一个槽位序号。
     */
    public int getNextSlotIndex() {
        return nextSlotIndex;
    }

    // ── 管理方法（OP 专用） ──

    /** 已回收的可复用槽位 */
    private final Queue<Integer> recycledSlots = new ArrayDeque<>();

    /**
     * 删除玩家的农场实例。
     * 注意：不会清理方块，需调用者另行处理。
     * @return 被删除的农场实例，如果不存在返回 null
     */
    @Nullable
    public FarmInstance deleteFarm(UUID playerUUID) {
        FarmInstance farm = instances.get(playerUUID);
        if (farm == null) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        StardewFarmLifecycle.FarmContext context = new StardewFarmLifecycle.FarmContext(
                server, StardewFarmSnapshots.from(farm));
        StardewFarmLifecycleRegistry.beforeDelete(context);
        if(server!=null) {
            var caveLevel=server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if(caveLevel!=null)com.stardew.craft.interior.FarmCaveRuntime.retire(
                    caveLevel,farm.getInstanceId());
            com.stardew.craft.world.event.WorldEventSavedData.get(server)
                    .remove(farm.getInstanceId());
        }

        if (server != null) {
            com.stardew.craft.animal.runtime.LivestockService.recover(server);
            var buildings = com.stardew.craft.building.runtime.BuildingWorldData.get(server);
            var homes = buildings.all().stream().filter(b -> b.farmId().equals(farm.getInstanceId())).map(com.stardew.craft.building.runtime.BuildingRecord::id).collect(java.util.stream.Collectors.toSet());
            com.stardew.craft.animal.runtime.LivestockWorldData.get(server).removeFarm(farm.getInstanceId(), homes);
            com.stardew.craft.pet.PetWorldData.get(server).removeFarm(farm.getInstanceId());
            buildings.removeFarm(farm.getInstanceId());
        }

        instances.remove(playerUUID);
        pendingDebugFarmDeletions.remove(farm.getInstanceId());
        debugFarmControllers.remove(playerUUID);
        selectedFarmByPlayer.entrySet().removeIf(entry -> entry.getValue().equals(playerUUID));
        slotToOwner.remove(farm.getSlotIndex());
        recycledSlots.add(farm.getSlotIndex());
        // 清除所有成员的 memberToOwner 映射
        for (UUID member : farm.getMembers()) {
            memberToOwner.remove(member);
        }
        setDirty();
        StardewCraft.LOGGER.info("[FARM_REGISTRY] Deleted farm for {} (slot={})", playerUUID, farm.getSlotIndex());
        StardewFarmLifecycleRegistry.afterDelete(context);
        return farm;
    }

    /** 创建一个由玩家控制、但拥有独立注册身份的调试农场。 */
    public FarmInstance createDebugFarm(
            UUID controller,
            String playerName,
            String farmName,
            FarmType farmType
    ) {
        UUID registryKey;
        do {
            registryKey = UUID.randomUUID();
        } while (instances.containsKey(registryKey));
        FarmInstance farm = createFarm(registryKey, playerName, farmName, farmType);
        if (!farm.addMember(controller, Integer.MAX_VALUE)) {
            deleteFarm(registryKey);
            throw new IllegalStateException("Could not attach debug farm controller");
        }
        debugFarmControllers.put(registryKey, controller);
        selectedFarmByPlayer.put(controller, registryKey);
        setDirty();
        return farm;
    }

    /** 删除前校验实际控制者，防止通过猜测实例 ID 删除别人的农场。 */
    @Nullable
    public FarmInstance deleteDebugFarm(UUID controller, UUID instanceId) {
        FarmInstance farm = getFarmByInstanceId(instanceId);
        UUID key = getRegistryKey(farm);
        if (key == null || !controller.equals(debugFarmControllers.get(key))) return null;
        return deleteFarm(key);
    }

    /**
     * 将农场从一个玩家转移给另一个玩家。
     * @return true 成功，false 失败（目标已有农场或源无农场）
     */
    public boolean transferFarm(UUID fromUUID, UUID toUUID, String newOwnerName) {
        FarmInstance farm = instances.get(fromUUID);
        if (farm == null) return false;
        if (instances.containsKey(toUUID)) return false;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        StardewFarmSnapshot sourceSnapshot = StardewFarmSnapshots.from(farm);
        StardewFarmLifecycleRegistry.beforeTransfer(new StardewFarmLifecycle.TransferRequest(
                server, sourceSnapshot, toUUID, newOwnerName));

        if(server!=null) {
            var caveLevel=server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if(caveLevel!=null)com.stardew.craft.interior.FarmCaveData.get(caveLevel).allocate(caveLevel,farm);
        }
        instances.remove(fromUUID);
        // 创建新实例保持相同槽位和坐标
        FarmInstance transferred = new FarmInstance(
            toUUID, newOwnerName, farm.getFarmName(),
            farm.getSlotIndex(), farm.getOrigin(), farm.getFarmLayoutId(),
            farm.getFarmLayout()
        );
        transferred.copyTransferStateFrom(farm);

        // 迁移成员列表（排除新 owner 如果之前是成员）
        int maxFarmers = maxFarmersPerFarm();
        for (UUID member : farm.getMembers()) {
            if (!member.equals(toUUID)) {
                transferred.addMember(member, maxFarmers);
            }
        }
        // 旧 owner 成为成员（如果还有空位且不是被删除的情况）
        if (!fromUUID.equals(toUUID) && transferred.getFarmerCount() < maxFarmers) {
            transferred.addMember(fromUUID, maxFarmers);
        }

        instances.put(toUUID, transferred);
        slotToOwner.put(farm.getSlotIndex(), toUUID);

        // 重建 memberToOwner 索引
        rebuildMemberIndex();

        setDirty();
        StardewCraft.LOGGER.info("[FARM_REGISTRY] Transferred farm slot={} from {} to {}",
                farm.getSlotIndex(), fromUUID, toUUID);
        StardewFarmLifecycleRegistry.afterTransfer(new StardewFarmLifecycle.TransferResult(
                server, sourceSnapshot, StardewFarmSnapshots.from(transferred)));
        return true;
    }

    /**
     * 修改农场名称。
     */
    public boolean renameFarm(UUID playerUUID, String newName) {
        FarmInstance farm = instances.get(playerUUID);
        if (farm == null) return false;
        farm.setFarmName(newName);
        setDirty();
        return true;
    }

    /** Keeps the persisted owner label aligned with the Stardew character name. */
    public void updateOwnerName(UUID playerUUID, String ownerName) {
        FarmInstance farm = instances.get(playerUUID);
        String normalized = ownerName == null ? "" : ownerName.trim();
        if (farm != null && !normalized.isBlank() && !normalized.equals(farm.getOwnerName())) {
            farm.setOwnerName(normalized);
            setDirty();
        }
    }

    // ── 分配方法 ──

    /**
     * 为玩家创建新农场实例。如果已有农场则返回现有的。
     */
    public FarmInstance createFarm(UUID playerUUID, String playerName, String farmName, FarmType farmType) {
        return createFarm(
                playerUUID,
                playerName,
                farmName,
                com.stardew.craft.api.v1.internal.farm
                        .StardewFarmLayoutRegistry.builtinId(farmType));
    }

    public FarmInstance createFarm(
            UUID playerUUID,
            String playerName,
            String farmName,
            ResourceLocation farmLayoutId
    ) {
        return createFarm(
                playerUUID, playerName, farmName, farmLayoutId, Map.of());
    }

    public FarmInstance createFarm(
            UUID playerUUID,
            String playerName,
            String farmName,
            ResourceLocation farmLayoutId,
            Map<ResourceLocation, String> requestedConfiguration
    ) {
        StardewTimeManager timeManager = StardewTimeManager.get();
        return createFarmAtDate(
                playerUUID, playerName, farmName, farmLayoutId,
                requestedConfiguration,
                timeManager.getAbsoluteDay(), timeManager.getCurrentSeason());
    }

    FarmInstance createFarmAtDate(UUID playerUUID, String playerName, String farmName, FarmType farmType,
                                  int absoluteDay, int season) {
        return createFarmAtDate(
                playerUUID,
                playerName,
                farmName,
                com.stardew.craft.api.v1.internal.farm
                        .StardewFarmLayoutRegistry.builtinId(farmType),
                absoluteDay,
                season);
    }

    FarmInstance createFarmAtDate(
            UUID playerUUID,
            String playerName,
            String farmName,
            ResourceLocation farmLayoutId,
            int absoluteDay,
            int season
    ) {
        return createFarmAtDate(
                playerUUID, playerName, farmName, farmLayoutId, Map.of(),
                absoluteDay, season);
    }

    FarmInstance createFarmAtDate(
            UUID playerUUID,
            String playerName,
            String farmName,
            ResourceLocation farmLayoutId,
            Map<ResourceLocation, String> requestedConfiguration,
            int absoluteDay,
            int season
    ) {
        if (instances.containsKey(playerUUID)) {
            StardewCraft.LOGGER.warn("[FARM_REGISTRY] Player {} already has a farm, returning existing", playerUUID);
            return instances.get(playerUUID);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        StardewFarmLayoutRegistration registration =
                StardewFarmLayouts.findRegistration(farmLayoutId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown farm layout: " + farmLayoutId));
        StardewFarmLayout layout = registration.layout();
        StardewFarmLayoutConfiguration configuration =
                StardewFarmLayoutConfiguration.validate(
                        registration.configurationFields(),
                        Objects.requireNonNull(
                                requestedConfiguration,
                                "requestedConfiguration"));
        StardewFarmLifecycleRegistry.beforeCreate(new StardewFarmLifecycle.CreateRequest(
                server, playerUUID, playerName, farmName, farmLayoutId));

        int slotIndex = recycledSlots.isEmpty() ? nextSlotIndex++ : recycledSlots.poll();
        BlockPos origin = FarmInstanceAllocator.getFarmOrigin(slotIndex, layout);
        FarmInstance instance = new FarmInstance(
                playerUUID, playerName, farmName, slotIndex, origin,
                farmLayoutId, layout, registration.version(), configuration,
                registration.attachments());
        instance.setLastOnlineDay(absoluteDay);
        instance.setLastOnlineSeason(season);
        instance.markActiveOnDay(absoluteDay);

        instances.put(playerUUID, instance);
        slotToOwner.put(slotIndex, playerUUID);
        setDirty();

        StardewCraft.LOGGER.info("[FARM_REGISTRY] Created farm for {} (slot={}, origin={}, type={})",
                playerName, slotIndex, origin, farmLayoutId);
        StardewFarmLifecycleRegistry.afterCreate(new StardewFarmLifecycle.FarmContext(
                server, StardewFarmSnapshots.from(instance)));
        return instance;
    }

    /**
     * 标记农场为已初始化。
     */
    public void markFarmInitialized(UUID playerUUID) {
        FarmInstance farm = instances.get(playerUUID);
        if (farm != null) {
            farm.markInitialized();
            setDirty();
        }
    }

    // ── NBT 序列化 ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("NextSlotIndex", nextSlotIndex);

        // 保存回收槽位
        int[] recycledArr = recycledSlots.stream().mapToInt(Integer::intValue).toArray();
        tag.putIntArray("RecycledSlots", recycledArr);

        ListTag debugFarms = new ListTag();
        debugFarmControllers.forEach((key, controller) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Key", key);
            entry.putUUID("Controller", controller);
            debugFarms.add(entry);
        });
        tag.put("DebugFarms", debugFarms);

        ListTag selections = new ListTag();
        selectedFarmByPlayer.forEach((player, key) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putUUID("Key", key);
            selections.add(entry);
        });
        tag.put("SelectedFarms", selections);
        ListTag pendingDeletions = new ListTag();
        pendingDebugFarmDeletions.forEach(instanceId -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("InstanceId", instanceId);
            pendingDeletions.add(entry);
        });
        tag.put("PendingDebugDeletions", pendingDeletions);

        ListTag list = new ListTag();
        for (FarmInstance instance : instances.values()) {
            list.add(instance.save());
        }
        tag.put("Instances", list);
        return tag;
    }

    private static FarmInstanceRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        registry.nextSlotIndex = tag.getInt("NextSlotIndex");

        // 加载回收槽位
        if (tag.contains("RecycledSlots")) {
            int[] recycledArr = tag.getIntArray("RecycledSlots");
            for (int slot : recycledArr) {
                registry.recycledSlots.add(slot);
            }
        }

        ListTag list = tag.getList("Instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            FarmInstance instance = FarmInstance.load(list.getCompound(i));
            if (!list.getCompound(i).hasUUID("InstanceId")) {
                registry.setDirty();
            }
            registry.instances.put(instance.getOwnerUUID(), instance);
            registry.slotToOwner.put(instance.getSlotIndex(), instance.getOwnerUUID());
        }

        for (Tag raw : tag.getList("DebugFarms", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Key") && entry.hasUUID("Controller")
                    && registry.instances.containsKey(entry.getUUID("Key"))) {
                registry.debugFarmControllers.put(
                        entry.getUUID("Key"), entry.getUUID("Controller"));
            }
        }
        for (Tag raw : tag.getList("SelectedFarms", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Player") && entry.hasUUID("Key")
                    && registry.instances.containsKey(entry.getUUID("Key"))) {
                registry.selectedFarmByPlayer.put(
                        entry.getUUID("Player"), entry.getUUID("Key"));
            }
        }
        for (Tag raw : tag.getList("PendingDebugDeletions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("InstanceId")
                    && registry.getFarmByInstanceId(entry.getUUID("InstanceId")) != null) {
                registry.pendingDebugFarmDeletions.add(entry.getUUID("InstanceId"));
            }
        }

        StardewCraft.LOGGER.info("[FARM_REGISTRY] Loaded {} farm instances, nextSlot={}",
                registry.instances.size(), registry.nextSlotIndex);
        registry.rebuildMemberIndex();
        return registry;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<FarmInstanceRegistry> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(FarmInstanceRegistry::new, FarmInstanceRegistry::load);
    }

    private static int maxFarmersPerFarm() {
        return ModGameRules.getMaxFarmersPerFarm(ServerLifecycleHooks.getCurrentServer());
    }
}
