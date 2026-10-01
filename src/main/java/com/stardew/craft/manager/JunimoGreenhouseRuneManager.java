package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.JunimoGreenhouseRuneBlock;
import com.stardew.craft.farming.SeasonLocationRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理所有已放置的祝尼魔温室符文。
 * <p>
 * 每个符文记录放置时的季节。换季时，前一个季节放置的所有符文将自动移除。
 * 符文周围 7×7（水平±3, 垂直±2）范围内的作物免受季节限制。
 */
@SuppressWarnings("null")
public class JunimoGreenhouseRuneManager extends SavedData {

    private static final String DATA_NAME = "stardew_junimo_greenhouse_rune";
    private static final int HORIZONTAL_RANGE = 3;
    private static final int VERTICAL_RANGE = 2;

    /** pos → 放置时的季节 (0=春 1=夏 2=秋 3=冬) */
    private final Map<BlockPos, Integer> runes = new HashMap<>();

    // ─── 符文增删 ───────────────────────────────────────────────

    public void addRune(BlockPos pos, int season) {
        runes.put(pos.immutable(), season);
        setDirty();
    }

    public void removeRune(BlockPos pos) {
        if (runes.remove(pos) != null) {
            setDirty();
        }
    }

    // ─── 范围查询 ───────────────────────────────────────────────

    /**
     * 服务端：检查 pos 是否在任意符文的保护范围内。
     */
    private boolean isInRangeInternal(BlockPos pos) {
        for (BlockPos runePos : runes.keySet()) {
            if (Math.abs(pos.getX() - runePos.getX()) <= HORIZONTAL_RANGE
                    && Math.abs(pos.getY() - runePos.getY()) <= VERTICAL_RANGE
                    && Math.abs(pos.getZ() - runePos.getZ()) <= HORIZONTAL_RANGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通用入口——服务端走 SavedData，客户端扫描方块。
     */
    public static boolean isInRuneRange(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return get(serverLevel).isInRangeInternal(pos);
        }
        // 客户端回退：在范围内扫描是否存在符文方块
        return scanForRuneNearby(level, pos);
    }

    private static boolean scanForRuneNearby(Level level, BlockPos pos) {
        for (int dx = -HORIZONTAL_RANGE; dx <= HORIZONTAL_RANGE; dx++) {
            for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE; dy++) {
                for (int dz = -HORIZONTAL_RANGE; dz <= HORIZONTAL_RANGE; dz++) {
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (level.isLoaded(check)
                            && level.getBlockState(check).getBlock() instanceof JunimoGreenhouseRuneBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<BlockPos> getAllRunePositions() {
        return Collections.unmodifiableSet(runes.keySet());
    }

    // ─── 换季清除 ───────────────────────────────────────────────

    /**
     * 换季时调用。移除所有上一季放置的符文并清除世界中的方块。
     */
    public void removeExpiredRunes(ServerLevel level, int currentSeason) {
        List<BlockPos> toRemove = new ArrayList<>();
        for (var entry : runes.entrySet()) {
            if (entry.getValue() != currentSeason) {
                toRemove.add(entry.getKey());
            }
        }
        for (BlockPos pos : toRemove) {
            if (level.isLoaded(pos)) {
                level.removeBlock(pos, false);
            }
            runes.remove(pos);
        }
        if (!toRemove.isEmpty()) {
            setDirty();
            StardewCraft.LOGGER.info("[RUNE] Removed {} expired greenhouse runes on season change", toRemove.size());
        }
    }

    // ─── 季节豁免规则注册 ───────────────────────────────────────

    private static boolean seasonRuleRegistered = false;

    public static void registerSeasonRule() {
        if (seasonRuleRegistered) return;
        SeasonLocationRules.registerIgnoreSeasonsRule(JunimoGreenhouseRuneManager::isInRuneRange);
        seasonRuleRegistered = true;
        StardewCraft.LOGGER.info("[RUNE] Greenhouse rune season immunity rule registered");
    }

    // ─── 序列化 ─────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        ListTag list = new ListTag();
        for (var entry : runes.entrySet()) {
            CompoundTag runeTag = new CompoundTag();
            runeTag.put("Pos", com.stardew.craft.port.PortNbtUtils.writeBlockPos(entry.getKey()));
            runeTag.putInt("Season", entry.getValue());
            list.add(runeTag);
        }
        tag.put("Runes", list);
        return tag;
    }

    public static JunimoGreenhouseRuneManager load(CompoundTag tag, HolderLookup.Provider provider) {
        JunimoGreenhouseRuneManager manager = new JunimoGreenhouseRuneManager();
        if (tag.contains("Runes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Runes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag runeTag = list.getCompound(i);
                BlockPos pos = com.stardew.craft.port.PortNbtUtils.readBlockPos(runeTag, "Pos").orElse(BlockPos.ZERO);
                int season = runeTag.getInt("Season");
                manager.runes.put(pos, season);
            }
        }
        return manager;
    }

    // ─── 单例获取 ───────────────────────────────────────────────

    public static JunimoGreenhouseRuneManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(
                        JunimoGreenhouseRuneManager::new,
                        JunimoGreenhouseRuneManager::load,
                        null
                )), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(
                        JunimoGreenhouseRuneManager::new,
                        JunimoGreenhouseRuneManager::load,
                        null
                )),
                DATA_NAME
        );
    }
}
