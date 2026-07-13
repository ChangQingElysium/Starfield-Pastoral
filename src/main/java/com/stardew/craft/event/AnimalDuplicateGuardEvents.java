package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 防止动物实体重复加入世界。
 * 
 * 问题场景：
 * 1. syncAll() 在日结算时被调用，但农场区块可能未加载
 * 2. collectLoaded() 找不到卸载区块中的实体
 * 3. syncAll() 以为该动物不存在，spawn 了新实体
 * 4. 区块重新加载时，旧实体从存档恢复，导致重复
 * 
 * 解决方案：在实体加入世界时，检测是否已有相同 managedAnimalId 的实体存在，
 * 如果是，则取消加入（discard 旧的那个）。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class AnimalDuplicateGuardEvents {

    /** 正在处理中的 ID，防止递归 */
    private static final Set<Long> processingIds = new HashSet<>();

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof BaseCoopAnimalEntity animal)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        long managedId = animal.getManagedAnimalId();
        if (managedId <= 0L) return;

        // 防止递归处理
        if (processingIds.contains(managedId)) return;

        AnimalWorldData data = AnimalWorldData.get(level);
        FarmAnimalRecord record = data.getAnimal(managedId).orElse(null);
        AnimalBuildingRecord building = record == null
            ? null
            : data.getBuilding(record.buildingId()).orElse(null);
        if (record == null || building == null
            || !level.dimension().location().toString().equals(building.dimensionId())) {
            StardewCraft.LOGGER.info("[ANIMAL_GUARD] Discarding managed animal {} without an active building", managedId);
            event.setCanceled(true);
            animal.discard();
            return;
        }

        // 检查是否已有相同 ID 的实体在世界中
        processingIds.add(managedId);
        try {
            for (BaseCoopAnimalEntity existing : level.getEntitiesOfClass(
                    BaseCoopAnimalEntity.class, 
                    animal.getBoundingBox().inflate(256))) {
                if (existing == animal) continue;
                if (existing.getManagedAnimalId() == managedId) {
                    // 发现重复，取消当前实体加入
                    StardewCraft.LOGGER.info("[ANIMAL_GUARD] Duplicate detected for managedId {}, discarding new entity", managedId);
                    event.setCanceled(true);
                    animal.discard();
                    return;
                }
            }
        } finally {
            processingIds.remove(managedId);
        }
    }
}
