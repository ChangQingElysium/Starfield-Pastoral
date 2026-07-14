package com.stardew.craft.manager;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewAnimalData;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.service.AnimalProducePlacementService;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import com.stardew.craft.animal.service.AnimalDoorStateService;
import com.stardew.craft.blockentity.AutoFeedTroughBlockEntity;
import com.stardew.craft.blockentity.AutoPetterBlockEntity;
import com.stardew.craft.blockentity.FeedTroughBlockEntity;
import com.stardew.craft.blockentity.HeaterBlockEntity;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class AnimalGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_animal_growth_manager";
    /** 动物搜索范围：建筑边界外扩 64 格（覆盖牧场区域） */
    private static final int ENTITY_SEARCH_EXPAND = 64;
    private static final double PIG_TRUFFLE_FIND_CHANCE_PER_TICK = 0.0002D;
    private static final int APPROX_TICKS_PER_TEN_MINUTE_SLOT = 167;
    private static final double PIG_TRUFFLE_FIND_CHANCE_PER_SLOT = 1.0D
        - Math.pow(1.0D - PIG_TRUFFLE_FIND_CHANCE_PER_TICK, APPROX_TICKS_PER_TEN_MINUTE_SLOT);

    private enum AnimalHarvestType {
        DROP_OVERNIGHT,
        HELD,
        DIG_UP
    }

    private record AnimalProfile(int daysToProduce,
                                 int friendshipForFasterProduce,
                                 int deluxeProduceMinimumFriendship,
                                 float deluxeProduceCareDivisor,
                                 float deluxeProduceLuckMultiplier,
                                 int happinessDrain,
                                 int professionForHappinessBoost,
                                 int professionForQualityBoost,
                                 int professionForFasterProduce,
                                 AnimalHarvestType harvestType,
                                 Supplier<Item> produceSupplier,
                                 Supplier<Item> deluxeProduceSupplier) {
    }

    private static final AnimalProfile DEFAULT_CHICKEN = new AnimalProfile(
        1, -1, 200, 1200.0f, 0.0f, 7, -1, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT,
        ModItems.EGG_WHITE,
        ModItems.LARGE_EGG_WHITE
    );

    private static final Map<String, AnimalProfile> PROFILES = Map.ofEntries(
        Map.entry("white_chicken", new AnimalProfile(1, -1, 200, 1200.0f, 0.0f, 7, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.EGG_WHITE, ModItems.LARGE_EGG_WHITE)),
        Map.entry("brown_chicken", new AnimalProfile(1, -1, 200, 1200.0f, 0.0f, 7, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.EGG_BROWN, ModItems.LARGE_EGG_BROWN)),
        Map.entry("blue_chicken", new AnimalProfile(1, -1, 200, 1200.0f, 0.0f, 7, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.EGG_WHITE, ModItems.LARGE_EGG_WHITE)),
        Map.entry("void_chicken", new AnimalProfile(1, -1, 200, 1200.0f, 0.0f, 5, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.VOID_EGG, null)),
        Map.entry("golden_chicken", new AnimalProfile(1, -1, 200, 1200.0f, 0.0f, 10, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.GOLDEN_EGG, null)),
        Map.entry("duck", new AnimalProfile(2, -1, 200, 4750.0f, 1.01f, 5, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.DUCK_EGG, ModItems.DUCK_FEATHER)),
        Map.entry("rabbit", new AnimalProfile(4, -1, 0, 5000.0f, 1.02f, 5, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.WOOL, ModItems.RABBITS_FOOT)),
        Map.entry("dinosaur", new AnimalProfile(7, -1, 200, 1200.0f, 0.0f, 4, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.DINOSAUR_EGG, null)),
        Map.entry("ostrich", new AnimalProfile(7, -1, 200, 1200.0f, 0.0f, 5, ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), ProfessionType.COOPMASTER.getId(), AnimalHarvestType.DROP_OVERNIGHT, ModItems.OSTRICH_EGG, null)),
        Map.entry("cow", new AnimalProfile(1, -1, 200, 1200.0f, 0.0f, 8, ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), AnimalHarvestType.HELD, ModItems.MILK, ModItems.LARGE_MILK)),
        Map.entry("goat", new AnimalProfile(2, -1, 200, 1200.0f, 0.0f, 8, ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), AnimalHarvestType.HELD, ModItems.GOAT_MILK, ModItems.LARGE_GOAT_MILK)),
        Map.entry("sheep", new AnimalProfile(3, -1, 0, 5000.0f, 1.02f, 8, ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), AnimalHarvestType.HELD, ModItems.WOOL, null)),
        Map.entry("pig", new AnimalProfile(1, -1, 0, 1200.0f, 0.0f, 8, ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), ProfessionType.SHEPHERD.getId(), AnimalHarvestType.DIG_UP, ModItems.TRUFFLE, null))
    );

    public AnimalGrowthManager() {
    }

    public static AnimalGrowthManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AnimalGrowthManager::new, AnimalGrowthManager::load),
            DATA_NAME
        );
    }

    public static AnimalGrowthManager load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        return new AnimalGrowthManager();
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        return tag;
    }

    public void growDaily(ServerLevel level) {
        AnimalWorldData worldData = AnimalWorldData.get(level);

        StardewTimeManager time = StardewTimeManager.get();
        int currentAbsDay = (time.getCurrentYear() - 1) * (28 * 4) + time.getCurrentSeason() * 28 + time.getCurrentDay();

        for (FarmAnimalRecord record : worldData.getAnimals()) {
            int lastDay = record.lastProcessedAbsDay();
            
            // 新动物或旧存档：初始化为昨天，只处理今天
            if (lastDay <= 0) {
                lastDay = currentAbsDay - 1;
            }
            
            int daysMissed = currentAbsDay - lastDay;
            if (daysMissed <= 0) {
                // 已经处理过今天，跳过（防止重复调用）
                continue;
            }
            
            // 追赶离线天数（离线模式：简化逻辑，假设室内已喂食）
            for (int d = 1; d < daysMissed; d++) {
                int catchUpDay = lastDay + d;
                applyDayUpdate(level, worldData, record, catchUpDay, true /* isOfflineCatchUp */);
            }
            
            // 处理今天（在线模式：完整逻辑）
            applyDayUpdate(level, worldData, record, currentAbsDay, false /* isOfflineCatchUp */);
            
            // 更新时间戳
            record.setLastProcessedAbsDay(currentAbsDay);
        }

        tryReproduction(level, worldData, currentAbsDay);

        worldData.markChanged();
        AnimalEntitySyncService.syncAll(level);
        setDirty();
    }

    public void updatePerTenMinutes(ServerLevel level, int timeOfDay) {
        AnimalWorldData worldData = AnimalWorldData.get(level);
        boolean changed = false;

        changed |= tryPigDigUpTruffles(level, worldData, timeOfDay);

        if (timeOfDay < 1800) {
            if (changed) {
                worldData.markChanged();
                setDirty();
            }
            return;
        }

        boolean isWinter = StardewTimeManager.get().getCurrentSeason() == 3;
        boolean isRaining = com.stardew.craft.weather.WeatherManager.isRaining(level);

        for (FarmAnimalRecord record : worldData.getAnimals()) {
            AnimalProfile profile = resolveProfile(record.animalTypeId());
            AnimalBuildingRecord building = worldData.getBuilding(record.buildingId()).orElse(null);
            boolean outdoors = isAnimalOutdoors(level, record, building);

            int change = 0;
            if (outdoors) {
                // Parity: outdoors animals lose happiness in rain, winter, or after 19:00.
                change = (timeOfDay > 1900 || isRaining || isWinter) ? -profile.happinessDrain() : profile.happinessDrain();
            } else if (isWinter && building != null) {
                change = hasWorkingHeater(level, building) ? profile.happinessDrain() : -profile.happinessDrain();
            }

            if (change != 0) {
                record.addHappiness(change);
                changed = true;
            }
        }

        if (changed) {
            worldData.markChanged();
            setDirty();
        }
    }

    private boolean tryPigDigUpTruffles(ServerLevel level, AnimalWorldData worldData, int timeOfDay) {
        if (timeOfDay < 600 || timeOfDay >= 1900) {
            return false;
        }
        if (StardewTimeManager.get().getCurrentSeason() == 3 || com.stardew.craft.weather.WeatherManager.isRaining(level)) {
            return false;
        }

        boolean changed = false;
        StardewTimeManager time = StardewTimeManager.get();
        int absoluteDaysPlayed = (time.getCurrentYear() - 1) * (28 * 4) + time.getCurrentSeason() * 28 + time.getCurrentDay();

        for (FarmAnimalRecord record : worldData.getAnimals()) {
            if (!"pig".equals(record.animalTypeId()) || record.isBaby() || record.currentProduceId().isBlank()) {
                continue;
            }

            AnimalBuildingRecord building = worldData.getBuilding(record.buildingId()).orElse(null);
            if (!canPigDigNow(level, record, building)) {
                continue;
            }

            if (level.getRandom().nextDouble() >= PIG_TRUFFLE_FIND_CHANCE_PER_SLOT) {
                continue;
            }

            BaseCoopAnimalEntity pigEntity = findEntityByManagedId(level, record.animalId(), building);
            if (pigEntity == null) {
                continue;
            }

            ItemStack produce = resolveCurrentProduce(record);
            if (produce.isEmpty()) {
                continue;
            }

            if (!AnimalProducePlacementService.placeNearAnimal(level, worldData, record, pigEntity.blockPosition(),
                produce, 3 + level.getRandom().nextInt(3))) {
                continue;
            }

            // Parity: Animal Cracker doubles truffle output — place a second one nearby
            if (record.hasEatenAnimalCracker()) {
                AnimalProducePlacementService.placeNearAnimal(level, worldData, record, pigEntity.blockPosition(),
                    produce.copy(), 3 + level.getRandom().nextInt(3));
            }

            pigEntity.triggerForageAnimation();
            if (shouldConsumePigCurrentProduce(record, absoluteDaysPlayed, timeOfDay)) {
                record.setCurrentProduceId("");
                record.setProduceQuality(0);
            }
            changed = true;
        }

        return changed;
    }

    private boolean canPigDigNow(ServerLevel level, FarmAnimalRecord record, AnimalBuildingRecord building) {
        if (building == null) {
            return false;
        }
        if (StardewTimeManager.get().getCurrentSeason() == 3 || com.stardew.craft.weather.WeatherManager.isRaining(level)) {
            return false;
        }
        return isAnimalOutdoors(level, record, building);
    }

    private ItemStack resolveCurrentProduce(FarmAnimalRecord record) {
        ResourceLocation id = ResourceLocation.tryParse(record.currentProduceId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        QualityHelper.setQuality(stack, record.produceQuality());
        return stack;
    }

    private boolean shouldConsumePigCurrentProduce(FarmAnimalRecord record, int absoluteDaysPlayed, int timeOfDay) {
        long seed = record.animalId() / 2L + absoluteDaysPlayed + timeOfDay;
        RandomSource random = RandomSource.create(seed);
        return random.nextDouble() < (record.friendship() / 1500.0D);
    }

    private BaseCoopAnimalEntity findEntityByManagedId(ServerLevel level, long animalId, AnimalBuildingRecord building) {
        if (animalId <= 0L) {
            return null;
        }
        AABB searchBox = buildingSearchBox(building);
        for (BaseCoopAnimalEntity entity : level.getEntitiesOfClass(BaseCoopAnimalEntity.class, searchBox)) {
            if (entity.getManagedAnimalId() == animalId) {
                return entity;
            }
        }
        return null;
    }

    /**
     * 根据建筑边界构造搜索 AABB。如果 building 为 null，退回到全世界搜索。
     */
    private static AABB buildingSearchBox(AnimalBuildingRecord building) {
        if (building == null) {
            return new AABB(-30_000_000, -64, -30_000_000, 30_000_000, 320, 30_000_000);
        }
        return new AABB(
            building.minX() - ENTITY_SEARCH_EXPAND,
            building.minY() - 4,
            building.minZ() - ENTITY_SEARCH_EXPAND,
            building.maxX() + ENTITY_SEARCH_EXPAND,
            building.maxY() + 4,
            building.maxZ() + ENTITY_SEARCH_EXPAND
        );
    }

    /**
     * 应用每日更新到单个动物。
     * 
     * @param isOfflineCatchUp true 表示离线追赶模式（简化逻辑，假设室内已喂食，跳过产出放置）
     */
    private boolean applyDayUpdate(ServerLevel level, AnimalWorldData worldData, FarmAnimalRecord record, int absoluteDaysPlayed, boolean isOfflineCatchUp) {
        AnimalProfile profile = resolveProfile(record.animalTypeId());
        RandomSource random = randomForAnimalDay(record.animalId(), absoluteDaysPlayed);
        AnimalBuildingRecord building = worldData.getBuilding(record.buildingId()).orElse(null);
        BaseCoopAnimalEntity runtimeEntity = findEntityByManagedId(level, record.animalId(), building);
        StardewAnimalData publicData = runtimeEntity == null
                ? null
                : StardewAgricultureDataApi.animal(runtimeEntity);
        boolean hasHappinessProfession = hasBuildingOwnerProfession(building, profile.professionForHappinessBoost());
        boolean hasQualityProfession = hasBuildingOwnerProfession(building, profile.professionForQualityBoost());
        boolean hasFasterProduceProfession = hasBuildingOwnerProfession(building, profile.professionForFasterProduce());
        boolean animalOutdoors = false;
        double averageDailyLuck = isOfflineCatchUp ? 0.0 : computeAverageDailyLuck(level);
        boolean wasLeftOutLastNight = false;

        // 离线追赶模式：假设动物在室内，门关着，有饲料
        if (!isOfflineCatchUp && building != null) {
            animalOutdoors = isAnimalOutdoors(level, record, building);
            boolean doorOpen = AnimalDoorStateService.isAnyBoundaryDoorOpen(level, building);
            if (animalOutdoors && !doorOpen) {
                record.setMoodMessage(6);
                record.setHappiness(record.happiness() / 2);
                wasLeftOutLastNight = true;
            } else if (animalOutdoors) {
                // Parity: animals that remained outdoors overnight still lose happiness before being considered back home.
                record.setHappiness(record.happiness() / 2);
                // Force-teleport the entity back inside the building for next-day sync
                teleportAnimalInsideBuilding(level, record, building);
                animalOutdoors = false;
            } else if (!animalOutdoors && !doorOpen) {
                record.addHappiness(profile.happinessDrain() * 2);
            }
        } else if (isOfflineCatchUp && building != null) {
            // 离线追赶：假设门关着，动物在室内，给幸福度加成
            record.addHappiness(profile.happinessDrain() * 2);
        }

        record.incrementDaysSinceLastProduce();
        record.incrementDaysOwned();

        if (!record.wasPetToday() && !record.wasAutoPetToday()) {
            int friendshipPenalty = (int) (10 - record.friendship() / 200.0);
            record.addFriendship(-Math.max(0, friendshipPenalty));
            record.addHappiness(-50);
        }
        record.setWasPetToday(false);
        record.setWasAutoPetToday(false);

        // 自动抚摸器检查（离线追赶时跳过，因为无法确定设备状态）
        if (!isOfflineCatchUp && building != null && hasWorkingAutoPetter(level, building)) {
            applyAutoPetterEffect(record, profile, hasHappinessProfession);
        }

        // 喂食逻辑
        boolean fedToday = false;
        if (isOfflineCatchUp) {
            // 离线追赶：假设有饲料，直接喂饱
            record.setFullness(255);
            fedToday = true;
        } else if (record.fullness() < 200 && building != null && !animalOutdoors && consumeOneHayFromTrough(level, building)) {
            // 在线模式：从喂食槽消耗饲料
            record.setFullness(255);
            fedToday = true;
        }

        if (record.fullness() > 200 || random.nextDouble() < (record.fullness() - 30) / 170.0) {
            record.incrementAgeDays(1);
            record.addHappiness(profile.happinessDrain() * 2);
        }

        if (record.fullness() < 200) {
            record.addHappiness(-100);
            record.addFriendship(-20);
        }

        int produceSpeedBonus = (profile.friendshipForFasterProduce() >= 0
            && record.friendship() >= profile.friendshipForFasterProduce()) ? 1 : 0;
        if (hasFasterProduceProfession) {
            produceSpeedBonus += 1;
        }
        int baseDaysToProduce = publicData == null ? profile.daysToProduce() : publicData.produceIntervalDays();
        int daysToProduce = Math.max(1, baseDaysToProduce - produceSpeedBonus);
        boolean produceToday = record.daysSinceLastProduce() >= daysToProduce
            && random.nextDouble() < record.fullness() / 200.0
            && random.nextDouble() < record.happiness() / 70.0;

        boolean produced = false;
        ItemStack produceStack = ItemStack.EMPTY;
        int produceQuality = record.produceQuality();
        String produceId = "";

        if (produceToday && !record.isBaby()) {
            Item defaultProduce = publicData == null
                    ? (profile.produceSupplier() == null ? null : profile.produceSupplier().get())
                    : BuiltInRegistries.ITEM.get(publicData.produce());
            if (defaultProduce == Items.AIR) {
                defaultProduce = profile.produceSupplier() == null ? null : profile.produceSupplier().get();
            }
            if (defaultProduce != null) {
                produceStack = new ItemStack(defaultProduce);
            }

            if (!produceStack.isEmpty()) {
                // Always reset cooldown when producing — regardless of quality/deluxe rolls
                record.resetDaysSinceLastProduce();
                produceQuality = 0; // Default to normal quality

                if (random.nextDouble() < record.happiness() / 150.0) {
                    float happinessModifier = record.happiness() > 200
                        ? record.happiness() * 1.5f
                        : (record.happiness() <= 100 ? record.happiness() - 100 : 0f);

                    Item deluxeProduce = publicData != null || profile.deluxeProduceSupplier() == null
                            ? null
                            : profile.deluxeProduceSupplier().get();
                    if (deluxeProduce != null
                        && record.friendship() >= profile.deluxeProduceMinimumFriendship()
                        && random.nextDouble() < ((record.friendship() + happinessModifier) / profile.deluxeProduceCareDivisor()) + averageDailyLuck * profile.deluxeProduceLuckMultiplier()) {
                        produceStack = new ItemStack(deluxeProduce);
                    }

                    produceQuality = rollQuality(record.friendship(), record.happiness(), hasQualityProfession, random);
                }

                QualityHelper.setQuality(produceStack, produceQuality);
                produceId = getProduceId(produceStack);
            }
        }

        if (!produceStack.isEmpty()) {
            if (profile.harvestType() == AnimalHarvestType.HELD && produceToday) {
                record.setCurrentProduceId(produceId);
                record.setProduceQuality(produceQuality);
                produced = true;
            } else if (profile.harvestType() == AnimalHarvestType.DIG_UP && produceToday) {
                // Parity: pigs store ready produce first; actual truffle dig happens later during daytime behavior.
                record.setCurrentProduceId(produceId);
                record.setProduceQuality(produceQuality);
                produced = true;
            } else if (!isOfflineCatchUp) {
                // 在线模式：放置产出到建筑中
                if (AnimalProducePlacementService.placeInHome(level, worldData, record, produceStack)) {
                    // Parity: Animal Cracker spawns a second item separately, not a stack of 2
                    if (record.hasEatenAnimalCracker()) {
                        AnimalProducePlacementService.placeInHome(level, worldData, record, produceStack.copy());
                    }
                    produced = true;
                } else if (AnimalProducePlacementService.dropInHome(level, worldData, record, produceStack)) {
                    produced = true;
                }
            }
            // 离线追赶模式：跳过 placeInHome/dropInHome 类型的产出放置
            // 这些产出会在玩家重新上线后的正常处理中生成
        }

        if (!wasLeftOutLastNight) {
            if (record.fullness() < 30) {
                record.setMoodMessage(4);
            } else if (record.happiness() < 30) {
                record.setMoodMessage(3);
            } else if (record.happiness() < 200) {
                record.setMoodMessage(2);
            } else {
                record.setMoodMessage(1);
            }
        }

        record.setFullness(0);
        // 节日日喂食补偿（离线追赶时跳过，因为无法判断历史某天是否为节日）
        if (!isOfflineCatchUp && isFestivalDay(level)) {
            // Parity: festival days keep animals sufficiently fed after overnight update.
            record.setFullness(250);
            fedToday = true;
        }
        record.setWasFedToday(fedToday);
        return produced;
    }

    // --- Barn animal reproduction (SDV parity) ---

    private void tryReproduction(ServerLevel level, AnimalWorldData worldData, int absoluteDaysPlayed) {
        for (AnimalBuildingRecord building : worldData.getBuildings()) {
            if (!"barn".equals(building.buildingType().family())) {
                continue;
            }
            if (!building.hasCapacity()) {
                continue;
            }

            // Find best candidate: adult barn animal with reproduction enabled and highest friendship
            FarmAnimalRecord bestCandidate = null;
            for (FarmAnimalRecord record : worldData.getAnimals()) {
                if (!record.buildingId().equals(building.buildingId())) {
                    continue;
                }
                if (!"barn".equals(AnimalTypeCatalog.resolve(record.animalTypeId()).family())) {
                    continue;
                }
                if (record.isBaby() || !record.allowReproduction()) {
                    continue;
                }
                if (bestCandidate == null || record.friendship() > bestCandidate.friendship()) {
                    bestCandidate = record;
                }
            }

            if (bestCandidate == null) {
                continue;
            }

            // Parity: pregnancy chance = friendship / 1200.0
            RandomSource random = randomForAnimalDay(bestCandidate.animalId(), absoluteDaysPlayed);
            double chance = bestCandidate.friendship() / 1200.0;
            if (random.nextDouble() >= chance) {
                continue;
            }

            // Create baby of the same type
            worldData.createAnimal(
                bestCandidate.animalTypeId(),
                "",
                building.buildingId(),
                AnimalAcquisitionSource.PREGNANCY
            );

            // Notify building owner
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(building.ownerPlayerUuid());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                String parentName = bestCandidate.customName().isBlank()
                    ? bestCandidate.animalTypeId()
                    : bestCandidate.customName();
                owner.sendSystemMessage(Component.translatable(
                    "stardewcraft.animal.pregnancy.birth_notification", parentName));
            }
        }
    }

    private int rollQuality(int friendship, int happiness, boolean hasQualityProfession, RandomSource random) {
        double chance = friendship / 1000.0 - (1.0 - happiness / 225.0);
        if (hasQualityProfession) {
            chance += 0.33;
        }
        chance = Math.max(0.0, Math.min(1.0, chance));

        double roll = random.nextDouble();
        if (roll < chance * 0.25) {
            return QualityHelper.IRIDIUM;
        }
        if (roll < chance * 0.5) {
            return QualityHelper.GOLD;
        }
        if (roll < chance) {
            return QualityHelper.SILVER;
        }
        return QualityHelper.NORMAL;
    }

    private String getProduceId(ItemStack stack) {
        return stack.getItemHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    private AnimalProfile resolveProfile(String animalTypeId) {
        return PROFILES.getOrDefault(animalTypeId, DEFAULT_CHICKEN);
    }

    private RandomSource randomForAnimalDay(long animalId, int absoluteDaysPlayed) {
        long seed = 1469598103934665603L;
        seed = (seed ^ (animalId / 2L)) * 1099511628211L;
        seed = (seed ^ absoluteDaysPlayed) * 1099511628211L;
        return RandomSource.create(seed);
    }

    private boolean isFestivalDay(ServerLevel level) {
        return "Festival".equals(WeatherManager.getCurrentWeather(level));
    }

    private double computeAverageDailyLuck(ServerLevel level) {
        java.util.List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return 0.0;
        }

        double totalLuck = 0.0;
        for (ServerPlayer player : players) {
            totalLuck += PlayerStardewDataAPI.getDailyLuck(player);
        }
        return totalLuck / players.size();
    }

    private boolean consumeOneHayFromTrough(ServerLevel level, AnimalBuildingRecord building) {
        BlockPos autoTroughPos = null;
        for (BlockPos pos : iterateBuildingUtilityPositions(building)) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof FeedTroughBlockEntity trough) {
                ItemStack removed = trough.takeOneFromSelf(false);
                if (removed.isEmpty()) {
                    removed = trough.extractAutomation(1, false);
                }
                if (!removed.isEmpty()) {
                    return true;
                }
            }

            if (be instanceof AutoFeedTroughBlockEntity autoTrough) {
                if (autoTroughPos == null) {
                    autoTroughPos = pos;
                }
                ItemStack removed = autoTrough.takeOneFromSelf(false);
                if (removed.isEmpty()) {
                    removed = autoTrough.extractAutomation(1, false);
                }
                if (!removed.isEmpty()) {
                    return true;
                }
            }
        }

        if (autoTroughPos != null) {
            String ownerStr = building.ownerPlayerUuid();
            if (ownerStr != null && !ownerStr.isBlank()) {
                try {
                    java.util.UUID owner = java.util.UUID.fromString(ownerStr);
                    if (AutoFeedTroughBlockEntity.refillConnectedNetwork(level, autoTroughPos, owner, 1) > 0) {
                        for (BlockPos pos : iterateBuildingUtilityPositions(building)) {
                            BlockEntity be = level.getBlockEntity(pos);
                            if (!(be instanceof AutoFeedTroughBlockEntity autoTrough)) {
                                continue;
                            }
                            ItemStack removed = autoTrough.takeOneFromSelf(false);
                            if (removed.isEmpty()) {
                                removed = autoTrough.extractAutomation(1, false);
                            }
                            if (!removed.isEmpty()) {
                                return true;
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean isAnimalOutdoors(ServerLevel level, FarmAnimalRecord record, AnimalBuildingRecord building) {
        if (building == null) {
            return true;
        }

        // 如果建筑区块未加载，假设动物在室内（避免错误惩罚）
        if (!level.isLoaded(building.managerPos())) {
            return false;
        }

        BaseCoopAnimalEntity entity = findEntityByManagedId(level, record.animalId(), building);
        if (entity == null) {
            // 区块已加载但找不到实体 —— 可能是动物在别处，保守假设室内
            return false;
        }
        return !isEntityInsideBuilding(entity, building);
    }

    private void teleportAnimalInsideBuilding(ServerLevel level, FarmAnimalRecord record, AnimalBuildingRecord building) {
        if (building == null) {
            return;
        }
        BaseCoopAnimalEntity entity = findEntityByManagedId(level, record.animalId(), building);
        if (entity == null) {
            return;
        }
        double cx = (building.minX() + building.maxX()) / 2.0;
        double cy = building.minY() + 1.0;
        double cz = (building.minZ() + building.maxZ()) / 2.0;
        entity.moveTo(cx, cy, cz, entity.getYRot(), entity.getXRot());
    }

    private boolean hasWorkingAutoPetter(ServerLevel level, AnimalBuildingRecord building) {
        if (building == null) {
            return false;
        }
        return hasUtilityBlockEntityInBuilding(level, building, AutoPetterBlockEntity.class);
    }

    private boolean hasWorkingHeater(ServerLevel level, AnimalBuildingRecord building) {
        if (building == null) {
            return false;
        }
        return hasUtilityBlockEntityInBuilding(level, building, HeaterBlockEntity.class);
    }

    private boolean hasUtilityBlockEntityInBuilding(ServerLevel level, AnimalBuildingRecord building, Class<? extends BlockEntity> utilityType) {
        for (BlockPos pos : iterateBuildingUtilityPositions(building)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (utilityType.isInstance(be)) {
                return true;
            }
        }
        return false;
    }

    private Iterable<BlockPos> iterateBuildingUtilityPositions(AnimalBuildingRecord building) {
        java.util.LinkedHashSet<BlockPos> positions = new java.util.LinkedHashSet<>();
        if (building == null) {
            return positions;
        }

        for (int y = building.minY(); y <= building.maxY(); y++) {
            for (int z = building.minZ(); z <= building.maxZ(); z++) {
                for (int x = building.minX(); x <= building.maxX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    positions.add(pos);
                    if (!building.interiorAirCells().contains(pos.asLong())) {
                        continue;
                    }
                    for (Direction direction : Direction.values()) {
                        positions.add(pos.relative(direction));
                    }
                }
            }
        }

        return positions;
    }

    private boolean hasBuildingOwnerProfession(AnimalBuildingRecord building, int professionId) {
        if (building == null || professionId < 0) {
            return false;
        }
        ProfessionType profession = ProfessionType.fromId(professionId);
        if (profession == null) {
            return false;
        }

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(building.ownerPlayerUuid());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return PlayerDataManager.getPlayerData(ownerUuid).hasProfession(profession);
    }

    private void applyAutoPetterEffect(FarmAnimalRecord record, AnimalProfile profile, boolean hasHappinessProfession) {
        if (record.wasPetToday()) {
            return;
        }

        // Parity: auto-petter gives a fixed +7 friendship (reduced vs manual +15).
        record.addFriendship(7);
        record.setWasAutoPetToday(true);

        int happinessGain = Math.max(5, 30 + profile.happinessDrain());
        if (hasHappinessProfession) {
            happinessGain += 15;
        }
        record.addHappiness(happinessGain);
    }

    private boolean isEntityInsideBuilding(BaseCoopAnimalEntity entity, AnimalBuildingRecord building) {
        if (entity == null || building == null) {
            return false;
        }

        if (building.isInBounds(entity.blockPosition())) {
            return true;
        }

        AABB box = entity.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX - 1.0E-4D);
        int maxY = (int) Math.floor(box.maxY - 1.0E-4D);
        int maxZ = (int) Math.floor(box.maxZ - 1.0E-4D);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (building.isInBounds(cursor)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
