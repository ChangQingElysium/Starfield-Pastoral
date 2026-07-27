package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.service.AnimalEntityProjectionPolicy;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import com.stardew.craft.animal.service.ManagedAnimalRuntimeIndex;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

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
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof BaseCoopAnimalEntity animal)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        long managedId = animal.getManagedAnimalId();
        if (managedId <= 0L) return;

        AnimalWorldData data = AnimalWorldData.get(level);
        FarmAnimalRecord record = data.getAnimal(managedId).orElse(null);
        AnimalBuildingRecord building = record == null
            ? null
            : data.getBuildingIncludingInactive(
                    record.buildingId()).orElse(null);
        AnimalEntityProjectionPolicy.JoinDecision decision =
                AnimalEntityProjectionPolicy.decideJoin(
                        record != null,
                        building != null,
                        building != null
                                && building.isGameplayEnabled(),
                        building != null
                                && level.dimension().location()
                                        .toString().equals(
                                                building.dimensionId()));
        if (decision != AnimalEntityProjectionPolicy
                .JoinDecision.ACCEPT_ACTIVE
                && decision != AnimalEntityProjectionPolicy
                .JoinDecision.ACCEPT_PAUSED) {
            StardewCraft.LOGGER.info(
                    "[ANIMAL_GUARD] Discarding projection {} for managed animal {}",
                    decision,
                    managedId);
            event.setCanceled(true);
            animal.discard();
            return;
        }

        AnimalEntitySyncService.applyAuthoritativeState(animal, record);
        BaseCoopAnimalEntity existing = ManagedAnimalRuntimeIndex.register(level, animal);
        if (existing != null) {
            StardewCraft.LOGGER.info("[ANIMAL_GUARD] Duplicate detected for managedId {}, discarding new entity", managedId);
            event.setCanceled(true);
            animal.discard();
        } else if (record.updateProjectionAnchor(
                level.dimension().location().toString(),
                animal.blockPosition())) {
            data.markChanged();
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof BaseCoopAnimalEntity animal) {
            if (ManagedAnimalRuntimeIndex.isCanonical(level, animal)) {
                AnimalWorldData data = AnimalWorldData.get(level);
                data.getAnimal(animal.getManagedAnimalId())
                        .ifPresent(record -> {
                            if (record.updateProjectionAnchor(
                                    level.dimension().location()
                                            .toString(),
                                    animal.blockPosition())) {
                                data.markChanged();
                            }
                        });
            }
            ManagedAnimalRuntimeIndex.remove(level, animal);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ManagedAnimalRuntimeIndex.clear(level);
        }
    }
}
