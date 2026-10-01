package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 农场访问权限管理器。
 * <p>
 * 权限等级：
 * <ul>
 *   <li>0 — 不可访问</li>
 *   <li>1 — 仅可访问（区域受保护，不可破坏/放置方块）</li>
 *   <li>2 — 可访问 + 可破坏/放置方块</li>
 * </ul>
 * <p>
 * 每个农场主人有一个默认权限等级（初始为 1）和针对特定玩家的覆盖权限。
 */
@SuppressWarnings("null")
public class FarmPermissionManager extends SavedData {

    private static final String DATA_NAME = "stardew_farm_permissions";

    /** 权限等级常量 */
    public static final int PERM_NONE = 0;
    public static final int PERM_VISIT = 1;
    public static final int PERM_FULL = 2;

    /** 农场主人 UUID → 默认权限等级（对所有非自己的玩家） */
    private final Map<UUID, Integer> defaultPermissions = new HashMap<>();

    /** 农场主人 UUID → (访客 UUID → 权限等级) */
    private final Map<UUID, Map<UUID, Integer>> playerPermissions = new HashMap<>();

    public FarmPermissionManager() {}

    public static FarmPermissionManager get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new FarmPermissionManager();
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    // ── 查询 ──

    /**
     * 获取玩家对某农场的权限等级。
     * 共享农场成员（owner + members）永远返回 PERM_FULL。
     */
    public int getPermission(UUID farmOwner, UUID visitor) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(farmOwner);
        if (farm != null && farm.isFarmer(visitor)) {
            return PERM_FULL;
        }
        if (farmOwner.equals(visitor)) return PERM_FULL;
        Map<UUID, Integer> overrides = playerPermissions.get(farmOwner);
        if (overrides != null && overrides.containsKey(visitor)) {
            return overrides.get(visitor);
        }
        return getDefaultPermission(farmOwner);
    }

    /**
     * 获取农场主人的默认权限等级。
     */
    public int getDefaultPermission(UUID farmOwner) {
        return defaultPermissions.getOrDefault(farmOwner, PERM_VISIT);
    }

    /**
     * 是否可以访问（权限 >= 1）。
     */
    public boolean canVisit(UUID farmOwner, UUID visitor) {
        return getPermission(farmOwner, visitor) >= PERM_VISIT;
    }

    /**
     * 是否可以修改方块（权限 >= 2）。
     */
    public boolean canModify(UUID farmOwner, UUID visitor) {
        return getPermission(farmOwner, visitor) >= PERM_FULL;
    }

    /**
     * 获取某个农场主人对特定玩家的权限覆盖（如果有的话）。
     * 返回 -1 表示没有覆盖（使用默认值）。
     */
    public int getOverridePermission(UUID farmOwner, UUID visitor) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(farmOwner);
        if (farm != null && farm.isFarmer(visitor)) {
            return PERM_FULL;
        }
        Map<UUID, Integer> overrides = playerPermissions.get(farmOwner);
        if (overrides != null && overrides.containsKey(visitor)) {
            return overrides.get(visitor);
        }
        return -1;
    }

    /**
     * 获取某个农场主人的所有权限覆盖。
     */
    public Map<UUID, Integer> getOverrides(UUID farmOwner) {
        Map<UUID, Integer> overrides = playerPermissions.get(farmOwner);
        return overrides != null ? Collections.unmodifiableMap(overrides) : Collections.emptyMap();
    }

    // ── 设置 ──

    /**
     * 设置某个农场主人的默认权限等级。
     */
    public void setDefaultPermission(UUID farmOwner, int level) {
        level = clampLevel(level);
        defaultPermissions.put(farmOwner, level);
        setDirty();
        StardewCraft.LOGGER.info("[FARM_PERM] {} set default permission to {}",
                farmOwner, level);
    }

    /**
     * 设置某个农场主人对特定玩家的权限。
     */
    public void setPermission(UUID farmOwner, UUID visitor, int level) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(farmOwner);
        if (farm != null && farm.isFarmer(visitor)) {
            removeOverride(farmOwner, visitor);
            return;
        }
        level = clampLevel(level);
        playerPermissions.computeIfAbsent(farmOwner, k -> new HashMap<>()).put(visitor, level);
        setDirty();
        StardewCraft.LOGGER.info("[FARM_PERM] {} set permission for {} to {}",
                farmOwner, visitor, level);
    }

    /**
     * 移除某个农场主人对特定玩家的权限覆盖（回退到默认值）。
     */
    public void removeOverride(UUID farmOwner, UUID visitor) {
        Map<UUID, Integer> overrides = playerPermissions.get(farmOwner);
        if (overrides != null) {
            overrides.remove(visitor);
            if (overrides.isEmpty()) {
                playerPermissions.remove(farmOwner);
            }
            setDirty();
        }
    }

    /**
     * 清除某个农场主人的所有权限数据（删除农场时调用）。
     */
    public void clearAllForOwner(UUID farmOwner) {
        defaultPermissions.remove(farmOwner);
        playerPermissions.remove(farmOwner);
        setDirty();
        StardewCraft.LOGGER.info("[FARM_PERM] Cleared all permissions for owner {}", farmOwner);
    }

    /**
     * 将权限从旧主人迁移到新主人（易主时调用）。
     */
    public void transferPermissions(UUID fromOwner, UUID toOwner) {
        Integer defaultPerm = defaultPermissions.remove(fromOwner);
        if (defaultPerm != null) {
            defaultPermissions.put(toOwner, defaultPerm);
        }
        Map<UUID, Integer> overrides = playerPermissions.remove(fromOwner);
        if (overrides != null) {
            // 移除新主人自己的覆盖（自己农场无需权限）
            overrides.remove(toOwner);
            playerPermissions.put(toOwner, overrides);
        }
        setDirty();
    }

    private static int clampLevel(int level) {
        return Math.max(PERM_NONE, Math.min(PERM_FULL, level));
    }

    // ── NBT ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        // 默认权限
        CompoundTag defaultsTag = new CompoundTag();
        for (var entry : defaultPermissions.entrySet()) {
            defaultsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("Defaults", defaultsTag);

        // 玩家权限覆盖
        ListTag overridesList = new ListTag();
        for (var entry : playerPermissions.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", entry.getKey());
            CompoundTag permsTag = new CompoundTag();
            for (var perm : entry.getValue().entrySet()) {
                permsTag.putInt(perm.getKey().toString(), perm.getValue());
            }
            ownerTag.put("Perms", permsTag);
            overridesList.add(ownerTag);
        }
        tag.put("Overrides", overridesList);
        return tag;
    }

    private static FarmPermissionManager load(CompoundTag tag, HolderLookup.Provider provider) {
        FarmPermissionManager mgr = new FarmPermissionManager();

        if (tag.contains("Defaults")) {
            CompoundTag defaultsTag = tag.getCompound("Defaults");
            for (String key : defaultsTag.getAllKeys()) {
                try {
                    mgr.defaultPermissions.put(UUID.fromString(key), defaultsTag.getInt(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (tag.contains("Overrides")) {
            ListTag overridesList = tag.getList("Overrides", Tag.TAG_COMPOUND);
            for (int i = 0; i < overridesList.size(); i++) {
                CompoundTag ownerTag = overridesList.getCompound(i);
                UUID owner = ownerTag.getUUID("Owner");
                CompoundTag permsTag = ownerTag.getCompound("Perms");
                Map<UUID, Integer> perms = new HashMap<>();
                for (String key : permsTag.getAllKeys()) {
                    try {
                        perms.put(UUID.fromString(key), permsTag.getInt(key));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (!perms.isEmpty()) {
                    mgr.playerPermissions.put(owner, perms);
                }
            }
        }

        StardewCraft.LOGGER.info("[FARM_PERM] Loaded permissions: {} defaults, {} overrides",
                mgr.defaultPermissions.size(), mgr.playerPermissions.size());
        return mgr;
    }

    public static SavedData.Factory<FarmPermissionManager> factory() {
        return new SavedData.Factory<>(FarmPermissionManager::new, FarmPermissionManager::load);
    }
}
