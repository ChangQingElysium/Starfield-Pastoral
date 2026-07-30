package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewAnimalData;
import com.stardew.craft.api.v1.agriculture.StardewAnimalDailyContext;
import com.stardew.craft.api.v1.agriculture.StardewAnimalDailyHandlers;
import com.stardew.craft.api.v1.agriculture.StardewAnimalReproductionContext;
import com.stardew.craft.api.v1.agriculture.StardewAnimalReproductionRules;
import com.stardew.craft.api.v1.agriculture.StardewTruffleFoundContext;
import com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingDailyContext;
import com.stardew.craft.animal.model.AnimalPendingBirth;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.rule.AnimalDayReducer;
import com.stardew.craft.animal.rule.AnimalCatchUpRules;
import com.stardew.craft.animal.rule.AnimalParityRules;
import com.stardew.craft.animal.rule.AnimalNightEventRules;
import com.stardew.craft.animal.service.AnimalProducePlacementService;
import com.stardew.craft.animal.service.AnimalProduceStatService;
import com.stardew.craft.animal.service.AnimalTruffleCrabService;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import com.stardew.craft.animal.service.AnimalDoorStateService;
import com.stardew.craft.blockentity.AutoFeedTroughBlockEntity;
import com.stardew.craft.blockentity.AutoGrabberBlockEntity;
import com.stardew.craft.blockentity.AutoPetterBlockEntity;
import com.stardew.craft.blockentity.FeedTroughBlockEntity;
import com.stardew.craft.blockentity.HeaterBlockEntity;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.network.payload.OpenAnimalBirthNamingPayload;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import javax.annotation.Nonnull;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class AnimalGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_animal_growth_manager";
    private static final double PIG_TRUFFLE_FIND_CHANCE_PER_TICK = 0.0002D;
    private static final int APPROX_TICKS_PER_TEN_MINUTE_SLOT = 167;
    private static final int INITIAL_CATCH_UP_BUDGET = 64;
    private static final int TICK_CATCH_UP_BUDGET = 16;
    private static final double PIG_TRUFFLE_FIND_CHANCE_PER_SLOT = 1.0D
        - Math.pow(1.0D - PIG_TRUFFLE_FIND_CHANCE_PER_TICK, APPROX_TICKS_PER_TEN_MINUTE_SLOT);
    private int lastReproductionProcessedAbsDay = -1;
    private final Set<Long> promptedBirthEvents = new HashSet<>();
    private int lastEntityReconcileServerTick =
            Integer.MIN_VALUE;
    private final Map<String, CachedBuildingUtilities> buildingUtilityCache =
            new HashMap<>();
    private BuildingUtilityContext activeBuildingUtilityContext =
            BuildingUtilityContext.EMPTY;

    private record BuildingUtilityContext(
            AnimalBuildingDailyContext dailyContext,
            List<AutoGrabberBlockEntity> autoGrabbers,
            List<FeedTroughBlockEntity> feedTroughs,
            List<AutoFeedTroughBlockEntity> autoFeedTroughs,
            BlockPos firstAutoFeedTroughPos
    ) {
        private static final BuildingUtilityContext EMPTY =
                new BuildingUtilityContext(
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                );

        private boolean hasAutoGrabber() {
            return !autoGrabbers.isEmpty();
        }

        private boolean hasAutoPetter() {
            return dailyContext != null
                    && dailyContext.autoPetter();
        }

        private boolean hasHeater() {
            return dailyContext != null
                    && dailyContext.heater();
        }
    }

    private record CachedBuildingUtilities(
            int absoluteDay,
            long structureRevision,
            BuildingUtilityContext context
    ) {
    }

    public AnimalGrowthManager() {
    }

    public static AnimalGrowthManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AnimalGrowthManager::new, AnimalGrowthManager::load),
            DATA_NAME
        );
    }

    public static AnimalGrowthManager load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        AnimalGrowthManager manager = new AnimalGrowthManager();
        manager.lastReproductionProcessedAbsDay =
                tag.contains("lastReproductionProcessedAbsDay")
                        ? tag.getInt("lastReproductionProcessedAbsDay")
                        : -1;
        return manager;
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(
                "lastReproductionProcessedAbsDay",
                lastReproductionProcessedAbsDay
        );
        return tag;
    }

    public void growDaily(ServerLevel level) {
        continueCatchUp(level, INITIAL_CATCH_UP_BUDGET);
    }

    /**
     * Continues a large offline backlog without monopolizing one server tick.
     */
    public int continueCatchUp(ServerLevel level) {
        return continueCatchUp(level, TICK_CATCH_UP_BUDGET);
    }

    int continueCatchUp(ServerLevel level, int maxAnimalDays) {
        if (maxAnimalDays <= 0) {
            return 0;
        }
        AnimalWorldData worldData = AnimalWorldData.get(level);
        StardewTimeManager time = StardewTimeManager.get();
        int currentAbsDay = (time.getCurrentYear() - 1) * (28 * 4)
                + time.getCurrentSeason() * 28
                + time.getCurrentDay();
        completeDueBuildingConstruction(
                level, worldData, currentAbsDay);
        Map<String, BuildingUtilityContext> utilityContexts = new HashMap<>();
        int processed = 0;
        boolean progressed;

        do {
            progressed = false;
            for (FarmAnimalRecord record : worldData.getAnimals()) {
                if (processed >= maxAnimalDays) {
                    break;
                }
                AnimalBuildingRecord building =
                        worldData.getBuildingIncludingInactive(
                                record.buildingId()).orElse(null);
                if (building != null && !building.isGameplayEnabled()) {
                    continue;
                }
                if (building != null && !shouldProcessBuildingToday(level, building)) {
                    continue;
                }

                int lastDay = record.lastProcessedAbsDay();
                int checkpoint = AnimalCatchUpRules.initializeCheckpoint(
                        lastDay,
                        currentAbsDay
                );
                if (checkpoint != lastDay) {
                    record.setLastProcessedAbsDay(checkpoint);
                }
                AnimalCatchUpRules.Step step = AnimalCatchUpRules.nextStep(
                        checkpoint,
                        currentAbsDay
                ).orElse(null);
                if (step == null) {
                    continue;
                }

                int targetDay = step.targetAbsDay();
                boolean offlineCatchUp = step.offlineCatchUp();
                BuildingUtilityContext utilityContext = BuildingUtilityContext.EMPTY;
                if (building != null) {
                    ensureBuildingLoaded(level, building);
                    if (!offlineCatchUp && !level.isLoaded(building.managerPos())) {
                        continue;
                    }
                    if (level.isLoaded(building.managerPos())) {
                        utilityContext = utilityContexts.computeIfAbsent(
                                building.buildingId(),
                                ignored -> resolveBuildingUtilities(
                                        level, building, currentAbsDay)
                        );
                    }
                }
                applyDayUpdateWithUtilities(
                        level,
                        worldData,
                        record,
                        targetDay,
                        offlineCatchUp,
                        utilityContext
                );
                // Checkpoint each animal-day immediately. A later failure cannot replay every
                // already-settled historical day or duplicate its ledger products.
                record.setLastProcessedAbsDay(targetDay);
                completeAutomaticFeedPassIfReady(
                        level,
                        worldData,
                        building,
                        targetDay,
                        utilityContexts
                );
                processed++;
                progressed = true;
            }
        } while (progressed && processed < maxAnimalDays);

        // Empty Deluxe buildings have no animal checkpoint that can trigger their object pass.
        for (AnimalBuildingRecord building : worldData.getBuildings()) {
            completeAutomaticFeedPassIfReady(
                    level,
                    worldData,
                    building,
                    currentAbsDay,
                    utilityContexts
            );
        }

        if (processed > 0) {
            projectPendingProduce(level, worldData);
            worldData.markChanged();
            setDirty();
        }
        int serverTick = level.getServer().getTickCount();
        if (lastEntityReconcileServerTick
                        == Integer.MIN_VALUE
                || serverTick - lastEntityReconcileServerTick
                        >= 100) {
            AnimalEntitySyncService.syncAll(level);
            lastEntityReconcileServerTick = serverTick;
        }

        if (lastReproductionProcessedAbsDay < 0) {
            // Migration baseline: the old manager didn't persist this checkpoint, so don't replay
            // a possibly already-run current-day pregnancy event after upgrading.
            lastReproductionProcessedAbsDay = currentAbsDay;
            setDirty();
        } else if (allProcessableAnimalsCaughtUp(level, worldData, currentAbsDay)
                && lastReproductionProcessedAbsDay < currentAbsDay) {
            tryReproduction(level, worldData, currentAbsDay);
            lastReproductionProcessedAbsDay = currentAbsDay;
            worldData.markChanged();
            setDirty();
        }
        promptPendingBirths(level, worldData);
        return processed;
    }

    private void completeDueBuildingConstruction(
            ServerLevel level,
            AnimalWorldData worldData,
            int currentAbsDay
    ) {
        for (AnimalBuildingRecord building :
                worldData.completeDueConstructions(currentAbsDay)) {
            invalidateBuildingUtilityCache(building.buildingId());
            UUID owner = parseUuid(building.ownerPlayerUuid());
            ServerPlayer player = owner == null
                    ? null
                    : level.getServer().getPlayerList()
                            .getPlayer(owner);
            if (player == null) {
                continue;
            }
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                    player,
                    Component.translatable(
                            "stardewcraft.manager.construction.completed",
                            Component.translatable(
                                    "stardewcraft.manager.building."
                                            + building.buildingType()
                                                    .family()),
                            building.buildingType().tier()));
            String sourceName =
                    building.buildingType().family()
                            .equalsIgnoreCase("coop")
                            ? "Coop"
                            : "Barn";
            com.stardew.craft.quest.StardewQuestEvents
                    .fireBuildingExists(player, sourceName);
        }
    }

    public void allowBirthPromptRetry(long eventId) {
        promptedBirthEvents.remove(eventId);
    }

    private void promptPendingBirths(
            ServerLevel level,
            AnimalWorldData worldData
    ) {
        for (ServerPlayer player : level.getServer()
                .getPlayerList()
                .getPlayers()) {
            List<AnimalPendingBirth> pending =
                    worldData.getPendingBirthsForOwner(
                            player.getUUID().toString());
            if (pending.isEmpty()) {
                continue;
            }
            AnimalPendingBirth event = pending.getFirst();
            if (!promptedBirthEvents.add(event.eventId())) {
                continue;
            }
            FarmAnimalRecord parent = worldData
                    .getAnimal(event.parentAnimalId())
                    .orElse(null);
            String parentName = parent == null
                    || parent.customName().isBlank()
                    ? event.animalTypeId()
                    : parent.customName();
            PacketDistributor.sendToPlayer(
                    player,
                    new OpenAnimalBirthNamingPayload(
                            event.eventId(),
                            parentName,
                            event.animalTypeId()
                    )
            );
        }
    }

    private void projectPendingProduce(
            ServerLevel level,
            AnimalWorldData worldData
    ) {
        for (AnimalBuildingRecord building : worldData.getBuildings()) {
            if (!building.dimensionId().equals(level.dimension().location().toString())
                    || !shouldProcessBuildingToday(level, building)
                    || worldData.getAnimalProduceForBuilding(
                            building.buildingId()).isEmpty()) {
                continue;
            }
            ensureBuildingLoaded(level, building);
            if (!level.isLoaded(building.managerPos())) {
                continue;
            }
            AnimalProducePlacementService.projectPendingForBuilding(
                    level,
                    worldData,
                    building
            );
        }
    }

    private boolean allProcessableAnimalsCaughtUp(
            ServerLevel level,
            AnimalWorldData worldData,
            int currentAbsDay
    ) {
        for (FarmAnimalRecord record : worldData.getAnimals()) {
            AnimalBuildingRecord building =
                    worldData.getBuildingIncludingInactive(
                            record.buildingId()).orElse(null);
            if (building != null && !building.isGameplayEnabled()) {
                continue;
            }
            if (building != null && !shouldProcessBuildingToday(level, building)) {
                continue;
            }
            if (record.lastProcessedAbsDay() < currentAbsDay) {
                return false;
            }
        }
        return true;
    }

    private int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return (time.getCurrentYear() - 1) * (28 * 4)
                + time.getCurrentSeason() * 28
                + time.getCurrentDay();
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
        int currentAbsDay = currentAbsoluteDay();
        Map<String, BuildingUtilityContext> utilityContexts = new HashMap<>();

        for (FarmAnimalRecord record : worldData.getAnimals()) {
            if (record.lastProcessedAbsDay() < currentAbsDay) {
                continue;
            }
            FarmAnimalDefinition definition =
                    FarmAnimalDefinitions.find(record.animalTypeId());
            if (definition == null) {
                continue;
            }
            AnimalBuildingRecord building = worldData.getBuilding(record.buildingId()).orElse(null);
            // 十分钟状态更新依赖实体和设施方块；农场未加载时保留记录状态，
            // 避免把“读不到加热器/动物实体”误判成负面状态。
            if (building == null
                    || !shouldProcessBuildingToday(level, building)
                    || !level.isLoaded(building.managerPos())) {
                continue;
            }
            boolean outdoors = isAnimalOutdoors(level, record, building);

            int change = 0;
            if (outdoors) {
                // Parity: outdoors animals lose happiness in rain, winter, or after 19:00.
                change = (timeOfDay > 1900 || isRaining || isWinter)
                        ? -definition.happinessDrain()
                        : definition.happinessDrain();
            } else if (isWinter && record.happiness() > 150) {
                BuildingUtilityContext utilityContext = utilityContexts.computeIfAbsent(
                        building.buildingId(),
                        ignored -> resolveBuildingUtilities(
                                level, building, currentAbsDay));
                change = utilityContext.hasHeater()
                        ? definition.happinessDrain()
                        : -definition.happinessDrain();
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
            if (record.lastProcessedAbsDay() < absoluteDaysPlayed) {
                continue;
            }
            FarmAnimalDefinition definition = FarmAnimalDefinitions.find(record.animalTypeId());
            if (definition == null
                    || definition.harvestType() != FarmAnimalDefinition.HarvestType.DIG_UP
                    || record.isBaby()
                    || record.currentProduceId().isBlank()) {
                continue;
            }

            AnimalBuildingRecord building = worldData.getBuilding(record.buildingId()).orElse(null);
            if (!canPigDigNow(level, record, building)) {
                continue;
            }

            if (level.getRandom().nextDouble() >= PIG_TRUFFLE_FIND_CHANCE_PER_SLOT) {
                continue;
            }

            BaseCoopAnimalEntity pigEntity = findEntityByManagedId(level, record.animalId());
            if (pigEntity == null) {
                continue;
            }

            ItemStack produce = resolveCurrentProduce(record);
            if (produce.isEmpty()) {
                continue;
            }

            BlockPos truffleAnchor = pigEntity.blockPosition();
            StardewDeterministicRandom digRandom =
                    StardewDeterministicRandom.create(
                            record.animalId() / 2L,
                            absoluteDaysPlayed,
                            timeOfDay);
            boolean spawnTruffleCrab =
                    produce.is(ModItems.TRUFFLE.get())
                            && AnimalParityRules.spawnsTruffleCrab(
                                    true,
                                    digRandom.nextDouble());
            boolean replaced = StardewTruffleFoundHandlers.run(
                    new StardewTruffleFoundContext(
                            level,
                            record.animalId(),
                            record.animalTypeId(),
                            truffleAnchor,
                            produce
                    )
            ) == StardewTruffleFoundHandlers.Result.REPLACE_TRUFFLE;
            if (!replaced && spawnTruffleCrab) {
                replaced = AnimalTruffleCrabService.spawnNear(
                        level,
                        truffleAnchor);
            }
            if (!replaced && !AnimalProducePlacementService.placeNearAnimal(
                    level,
                    worldData,
                    record,
                    truffleAnchor,
                    produce,
                    3 + level.getRandom().nextInt(3))) {
                continue;
            }

            pigEntity.triggerForageAnimation();
            if (shouldConsumePigCurrentProduce(record, digRandom)) {
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
        // FarmAnimal.DigUpProduce creates a fresh object; animal product quality isn't copied.
        QualityHelper.setQuality(stack, QualityHelper.NORMAL);
        return stack;
    }

    private boolean shouldConsumePigCurrentProduce(
            FarmAnimalRecord record,
            StardewDeterministicRandom random
    ) {
        return !AnimalParityRules.keepsPigProduceAfterDig(
                record.friendship(), random.nextDouble());
    }

    private BaseCoopAnimalEntity findEntityByManagedId(ServerLevel level, long animalId) {
        return AnimalEntitySyncService.findLoaded(level, animalId);
    }

    /**
     * 应用每日更新到单个动物。
     * 
     * @param isOfflineCatchUp true 表示离线追赶模式（简化逻辑，假设室内已喂食，跳过产出放置）
     */
    private boolean applyDayUpdateWithUtilities(
            ServerLevel level,
            AnimalWorldData worldData,
            FarmAnimalRecord record,
            int absoluteDaysPlayed,
            boolean isOfflineCatchUp,
            BuildingUtilityContext utilityContext
    ) {
        BuildingUtilityContext previousContext =
                activeBuildingUtilityContext;
        activeBuildingUtilityContext = utilityContext;
        try {
            return applyDayUpdate(
                    level,
                    worldData,
                    record,
                    absoluteDaysPlayed,
                    isOfflineCatchUp
            );
        } finally {
            activeBuildingUtilityContext = previousContext;
        }
    }

    private boolean applyDayUpdate(
            ServerLevel level,
            AnimalWorldData worldData,
            FarmAnimalRecord record,
            int absoluteDaysPlayed,
            boolean isOfflineCatchUp
    ) {
        BuildingUtilityContext utilityContext =
                activeBuildingUtilityContext;
        FarmAnimalDefinition configuredDefinition =
                FarmAnimalDefinitions.find(record.animalTypeId());
        if (configuredDefinition == null) {
            // A removed data pack/addon must never mutate an existing animal
            // with white-chicken defaults. Legacy code-only addons can still
            // own the whole daily transition through their registered handler.
            Entity runtimeEntity =
                    findEntityByManagedId(level, record.animalId());
            StardewAnimalData legacyRuntimeData =
                    runtimeEntity == null
                            ? null
                            : StardewAgricultureDataApi.animal(
                                    runtimeEntity);
            record.incrementDaysSinceLastProduce();
            StardewAnimalDailyHandlers.run(
                    new StardewAnimalDailyContext(
                            level,
                            worldData,
                            record,
                            absoluteDaysPlayed,
                            isOfflineCatchUp));
            return legacyRuntimeData != null
                    && !record.currentProduceId().isBlank();
        }
        FarmAnimalDefinition definition = configuredDefinition;
        AnimalBuildingRecord building = worldData.getBuilding(record.buildingId()).orElse(null);
        boolean hasQualityProfession = hasAnimalOwnerProfession(
                record, building, definition.professionForQualityBoost());
        boolean hasFasterProduceProfession = hasAnimalOwnerProfession(
                record, building, definition.professionForFasterProduce());
        boolean animalOutdoors = false;
        double averageDailyLuck = isOfflineCatchUp
                ? 0.0
                : computeFarmAverageDailyLuck(level, record, building);
        AnimalDayReducer.HomeSituation homeSituation =
                AnimalDayReducer.HomeSituation.NO_HOME;
        if (building != null) {
            if (isOfflineCatchUp) {
                homeSituation = AnimalDayReducer.HomeSituation.INSIDE_DOOR_CLOSED;
            } else {
                animalOutdoors = isAnimalOutdoors(level, record, building);
                boolean doorOpen =
                        AnimalDoorStateService.isAnyBoundaryDoorOpen(level, building);
                if (animalOutdoors) {
                    homeSituation = doorOpen
                            ? AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_OPEN
                            : AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_CLOSED;
                } else {
                    homeSituation = doorOpen
                            ? AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN
                            : AnimalDayReducer.HomeSituation.INSIDE_DOOR_CLOSED;
                }
            }
        }

        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        definition,
                        reducerState(record),
                        homeSituation,
                        toSourceClockTime(StardewTimeManager.get().getCurrentTime())
                )
        );
        applyReducerState(record, begin.state());
        if (begin.returnedHomeEarly()) {
            teleportAnimalInsideBuilding(level, record, building);
            return false;
        }

        // Preserve the established extension point after cooldown/home processing and before
        // missed-pet, feed, growth and production rules.
        StardewAnimalDailyHandlers.Result addonDailyResult = StardewAnimalDailyHandlers.run(
                new StardewAnimalDailyContext(
                        level, worldData, record, absoluteDaysPlayed, isOfflineCatchUp));

        boolean insideAnimalHouse = building != null && !animalOutdoors;
        boolean hayConsumed = insideAnimalHouse
                && record.fullness() < 200
                && consumeOneHayFromTrough(utilityContext);

        ServerPlayer conditionPlayer = resolveAnimalOwner(level, record, building);
        FarmAnimalDefinition.ConditionEvaluator conditionEvaluator = condition ->
                StardewConditions.test(
                        condition,
                        new StardewConditionContext(level, conditionPlayer)
                ).result().orElse(false);
        List<AnimalDayReducer.ProduceCandidate> normalProduce =
                definition.produce()
                .stream()
                .filter(entry -> entry.condition() == null
                        || conditionEvaluator.test(entry.condition()))
                .map(entry -> new AnimalDayReducer.ProduceCandidate(
                        entry.itemId(),
                        entry.minimumFriendship()))
                .toList();
        List<AnimalDayReducer.ProduceCandidate> deluxeProduce =
                definition.deluxeProduce()
                .stream()
                .filter(entry -> entry.condition() == null
                        || conditionEvaluator.test(entry.condition()))
                .map(entry -> new AnimalDayReducer.ProduceCandidate(
                        entry.itemId(),
                        entry.minimumFriendship()))
                .toList();
        StardewDeterministicRandom sourceRandom = StardewDeterministicRandom.create(
                record.animalId() / 2L,
                absoluteDaysPlayed,
                0L
        );
        AnimalDayReducer.RandomPort random = new AnimalDayReducer.RandomPort() {
            @Override
            public double nextDouble() {
                return sourceRandom.nextDouble();
            }

            @Override
            public int nextInt(int bound) {
                return sourceRandom.nextInt(bound);
            }
        };
        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                new AnimalDayReducer.FinishInput(
                        definition,
                        reducerState(record),
                        begin.wasLeftOutLastNight(),
                        insideAnimalHouse,
                        hayConsumed,
                        isFestivalDay(absoluteDaysPlayed),
                        addonDailyResult
                                == StardewAnimalDailyHandlers.Result.SKIP_DEFAULT_PRODUCTION,
                        hasFasterProduceProfession,
                        hasQualityProfession,
                        averageDailyLuck,
                        null,
                        normalProduce,
                        deluxeProduce
                ),
                random
        );
        AnimalDayReducer.State stateAfterObjects = AnimalDayReducer.applyAutoPetter(
                definition,
                finish.state(),
                insideAnimalHouse,
                utilityContext.hasAutoPetter()
        );
        AnimalDayReducer.State stateAfterDayStarted =
                AnimalDayReducer.applyDayStarted(
                        stateAfterObjects,
                        definition.grassEatAmount()
                );
        applyReducerState(record, stateAfterDayStarted);

        boolean heldProduceCollected =
                collectHeldProduceWithAutoGrabber(
                        record,
                        definition,
                        building,
                        utilityContext
                );
        AnimalDayReducer.Production production = finish.production();
        if (production == null) {
            return heldProduceCollected;
        }
        if (production.delivery() == AnimalDayReducer.Delivery.HELD) {
            return true;
        }

        Item produceItem = BuiltInRegistries.ITEM.get(production.itemId());
        if (produceItem == Items.AIR) {
            return false;
        }
        ItemStack produceStack = new ItemStack(produceItem);
        QualityHelper.setQuality(produceStack, production.quality());
        if (record.hasEatenAnimalCracker()) {
            produceStack.setCount(2);
        }
        boolean submitted =
                AnimalProducePlacementService.submitProduce(
                level,
                worldData,
                record,
                absoluteDaysPlayed,
                produceStack,
                false
        );
        if (submitted) {
            AnimalProduceStatService.recordForOwner(
                    resolveAnimalOwnerId(record, building),
                    definition,
                    produceStack
            );
        }
        return submitted;
    }

    /**
     * Source {@code Object.DayUpdate} checks every animal's held produce, not
     * only animals which produced during the current reducer pass.
     */
    private boolean collectHeldProduceWithAutoGrabber(
            FarmAnimalRecord record,
            FarmAnimalDefinition definition,
            AnimalBuildingRecord building,
            BuildingUtilityContext utilityContext
    ) {
        if (!AnimalParityRules
                .shouldAutoCollectHeldProduce(
                        definition,
                        utilityContext.hasAutoGrabber(),
                        record.currentProduceId())) {
            return false;
        }
        ResourceLocation produceId =
                ResourceLocation.tryParse(
                        record.currentProduceId());
        if (produceId == null
                || !BuiltInRegistries.ITEM.containsKey(
                        produceId)) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(produceId);
        if (item == Items.AIR) {
            return false;
        }
        ItemStack heldStack = new ItemStack(item);
        QualityHelper.setQuality(
                heldStack, record.produceQuality());
        if (record.hasEatenAnimalCracker()) {
            heldStack.setCount(2);
        }
        if (!AnimalProducePlacementService
                .insertFullyIntoAutoGrabbers(
                        utilityContext.autoGrabbers(),
                        heldStack)) {
            // A full destination leaves the authoritative held produce intact.
            return false;
        }
        record.setCurrentProduceId("");
        record.setProduceQuality(QualityHelper.NORMAL);
        AnimalProduceStatService.recordForOwner(
                resolveAnimalOwnerId(record, building),
                definition,
                heldStack
        );
        AutoGrabberBlockEntity.recordCollectedForOwner(
                building == null
                        ? record.ownerPlayerUuid()
                        : building.ownerPlayerUuid(),
                heldStack.getCount()
        );
        return true;
    }

    private UUID resolveAnimalOwnerId(
            FarmAnimalRecord record,
            AnimalBuildingRecord building
    ) {
        UUID owner = parseUuid(
                record.ownerPlayerUuid());
        return owner != null || building == null
                ? owner
                : parseUuid(building.ownerPlayerUuid());
    }

    // --- Barn animal reproduction (SDV parity) ---

    private void tryReproduction(ServerLevel level, AnimalWorldData worldData, int absoluteDaysPlayed) {
        Map<UUID, List<AnimalBuildingRecord>> buildingsByFarm = new LinkedHashMap<>();
        for (AnimalBuildingRecord building : worldData.getBuildings()) {
            if (!shouldProcessBuildingToday(level, building)) {
                continue;
            }
            UUID buildingOwner = parseUuid(building.ownerPlayerUuid());
            if (buildingOwner == null) {
                continue;
            }
            UUID farmOwner = FarmInstanceRegistry.get(level.getServer())
                    .getOwnerForPlayer(buildingOwner);
            buildingsByFarm.computeIfAbsent(
                    farmOwner == null ? buildingOwner : farmOwner,
                    ignored -> new ArrayList<>()
            ).add(building);
        }

        for (Map.Entry<UUID, List<AnimalBuildingRecord>> farmEntry
                : buildingsByFarm.entrySet()) {
            RandomSource random = reproductionRandom(farmEntry.getKey(), absoluteDaysPlayed);
            // Utility.pickPersonalFarmEvent chooses QuestionEvent(2) for half of eligible nights.
            if (!random.nextBoolean()) {
                tryNightAnimalAttack(
                        level,
                        worldData,
                        farmEntry.getKey(),
                        farmEntry.getValue(),
                        absoluteDaysPlayed
                );
                continue;
            }

            for (AnimalBuildingRecord building : farmEntry.getValue()) {
                if (!building.buildingType().allowsAnimalPregnancy()
                        || building.memberAnimalIds().size()
                                + worldData.getPendingBirthCountForBuilding(
                                        building.buildingId())
                                >= building.capacity()) {
                    continue;
                }
                ensureBuildingLoaded(level, building);
                List<FarmAnimalRecord> residents = worldData.getAnimals().stream()
                        .filter(record -> record.buildingId().equals(building.buildingId()))
                        .toList();
                if (residents.isEmpty()
                        || random.nextDouble() >= residents.size() * 0.0055D) {
                    continue;
                }

                // QuestionEvent selects first, then validates. It doesn't retry a better animal.
                FarmAnimalRecord parent = residents.get(random.nextInt(residents.size()));
                FarmAnimalDefinition definition =
                        FarmAnimalDefinitions.find(parent.animalTypeId());
                boolean canGetPregnant = definition != null
                        && definition.canGetPregnant();
                if (parent.isBaby()
                        || !parent.allowReproduction()
                        || !canGetPregnant
                        || !StardewAnimalReproductionRules.allows(
                        new StardewAnimalReproductionContext(
                                level, building, parent, absoluteDaysPlayed))) {
                    break;
                }

                worldData.queueAnimalBirth(
                        farmEntry.getKey().toString(),
                        building.buildingId(),
                        parent.animalId(),
                        parent.animalTypeId(),
                        absoluteDaysPlayed
                );

                ServerPlayer owner = level.getServer().getPlayerList()
                        .getPlayer(farmEntry.getKey());
                if (owner != null) {
                    String parentName = parent.customName().isBlank()
                            ? parent.animalTypeId()
                            : parent.customName();
                    owner.sendSystemMessage(Component.translatable(
                            "stardewcraft.animal.pregnancy.birth_notification", parentName));
                }
                break;
            }
        }
    }

    private void tryNightAnimalAttack(
            ServerLevel level,
            AnimalWorldData worldData,
            UUID farmOwner,
            List<AnimalBuildingRecord> farmBuildings,
            int absoluteDaysPlayed
    ) {
        if (farmBuildings.isEmpty()) {
            return;
        }
        RandomSource random = RandomSource.create(
                farmOwner.getMostSignificantBits()
                        ^ farmOwner.getLeastSignificantBits()
                        ^ ((long) absoluteDaysPlayed * 0x9E3779B97F4A7C15L)
                        ^ 0x444F47534E494748L
        );
        // SoundInTheNightEvent(2).setUp discards half of selected dog events.
        if (random.nextBoolean()) {
            return;
        }

        AnimalBuildingRecord targetBuilding = null;
        List<FarmAnimalRecord> targetOutsideAnimals = List.of();
        for (AnimalBuildingRecord building : farmBuildings) {
            ensureBuildingLoaded(level, building);
            boolean doorOpen =
                    AnimalDoorStateService.isAnyBoundaryDoorOpen(level, building);
            if (!level.isLoaded(building.managerPos())) {
                continue;
            }
            List<FarmAnimalRecord> outsideAnimals =
                    building.memberAnimalIds().stream()
                            .map(id -> worldData.getAnimal(id).orElse(null))
                            .filter(java.util.Objects::nonNull)
                            .filter(record -> isAnimalOutdoors(
                                    level,
                                    record,
                                    building
                            ))
                            .toList();
            if (!AnimalNightEventRules.buildingCanBeAttacked(
                            doorOpen,
                            outsideAnimals.size())
                    || !AnimalNightEventRules.selectsBuilding(
                            random.nextDouble(),
                            farmBuildings.size())) {
                continue;
            }
            targetBuilding = building;
            targetOutsideAnimals = outsideAnimals;
            break;
        }
        if (targetBuilding == null || targetOutsideAnimals.isEmpty()) {
            return;
        }

        FarmAnimalRecord victim = targetOutsideAnimals.getFirst();
        BaseCoopAnimalEntity entity =
                findEntityByManagedId(level, victim.animalId());
        if (entity != null) {
            entity.discard();
        }
        worldData.removeAnimal(victim.animalId());

        for (AnimalBuildingRecord building : farmBuildings) {
            for (Long animalId : building.memberAnimalIds()) {
                FarmAnimalRecord survivor =
                        worldData.getAnimal(animalId).orElse(null);
                if (survivor != null
                        && isAnimalOutdoors(level, survivor, building)) {
                    survivor.setMoodMessage(5);
                }
            }
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(farmOwner);
        if (owner != null) {
            String victimName = victim.customName().isBlank()
                    ? victim.animalTypeId()
                    : victim.customName();
            owner.sendSystemMessage(Component.translatable(
                    "stardewcraft.animal.night_attack",
                    victimName
            ));
        }
        worldData.markChanged();
    }

    private boolean shouldProcessBuildingToday(ServerLevel level, AnimalBuildingRecord building) {
        try {
            return com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessFarmForPlayer(
                level, UUID.fromString(building.ownerPlayerUuid()));
        } catch (IllegalArgumentException exception) {
            StardewCraft.LOGGER.warn("[ANIMAL] Invalid building owner UUID for {}: {}",
                building.buildingId(), building.ownerPlayerUuid());
            return false;
        }
    }

    private void ensureBuildingLoaded(ServerLevel level, AnimalBuildingRecord building) {
        com.stardew.craft.farm.FarmDailyProcessHelper.ensureBoundsLoaded(
            level,
            new BlockPos(building.minX() - 1, building.minY() - 1, building.minZ() - 1),
            new BlockPos(building.maxX() + 1, building.maxY() + 1, building.maxZ() + 1)
        );
    }

    private ServerPlayer resolveAnimalOwner(
            ServerLevel level,
            FarmAnimalRecord record,
            AnimalBuildingRecord building
    ) {
        UUID owner = parseUuid(record.ownerPlayerUuid());
        if (owner == null && building != null) {
            owner = parseUuid(building.ownerPlayerUuid());
        }
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    private AnimalDayReducer.State reducerState(FarmAnimalRecord record) {
        return new AnimalDayReducer.State(
                record.ageDays(),
                record.daysOwned(),
                record.friendship(),
                record.happiness(),
                record.fullness(),
                record.daysSinceLastProduce(),
                record.wasPetToday(),
                record.wasAutoPetToday(),
                record.wasFedToday(),
                record.moodMessage(),
                record.currentProduceId(),
                record.produceQuality()
        );
    }

    private void applyReducerState(
            FarmAnimalRecord record,
            AnimalDayReducer.State state
    ) {
        record.incrementAgeDays(state.ageDays() - record.ageDays());
        while (record.daysOwned() < state.daysOwned()) {
            record.incrementDaysOwned();
        }
        record.addFriendship(state.friendship() - record.friendship());
        record.setHappiness(state.happiness());
        record.setFullness(state.fullness());
        record.setDaysSinceLastProduce(state.daysSinceLastProduce());
        record.setWasPetToday(state.wasPetToday());
        record.setWasAutoPetToday(state.wasAutoPetToday());
        record.setWasFedToday(state.wasFedToday());
        record.setMoodMessage(state.moodMessage());
        record.setCurrentProduceId(state.currentProduceId());
        record.setProduceQuality(state.produceQuality());
    }

    private int toSourceClockTime(int minutesSinceMidnight) {
        int hours = Math.max(0, minutesSinceMidnight) / 60;
        int minutes = Math.max(0, minutesSinceMidnight) % 60;
        return hours * 100 + minutes;
    }

    private RandomSource reproductionRandom(UUID farmOwner, int absoluteDaysPlayed) {
        long seed = farmOwner.getMostSignificantBits() ^ farmOwner.getLeastSignificantBits();
        seed = (seed ^ absoluteDaysPlayed) * 1099511628211L;
        return RandomSource.create(seed);
    }

    private boolean isFestivalDay(int absoluteDaysPlayed) {
        int zeroBasedDay = Math.max(0, absoluteDaysPlayed - 1);
        int season = zeroBasedDay % (28 * 4) / 28;
        int dayOfMonth = zeroBasedDay % 28 + 1;
        return FestivalService.isFestivalDay(dayOfMonth, season);
    }

    private double computeFarmAverageDailyLuck(
            ServerLevel level,
            FarmAnimalRecord record,
            AnimalBuildingRecord building
    ) {
        UUID animalOwner = parseUuid(record.ownerPlayerUuid());
        if (animalOwner == null && building != null) {
            animalOwner = parseUuid(building.ownerPlayerUuid());
        }
        if (animalOwner == null) {
            return 0.0;
        }

        FarmInstance farm = FarmInstanceRegistry.get(level.getServer())
                .getFarmForPlayer(animalOwner);
        List<UUID> playerIds = new ArrayList<>();
        if (farm == null) {
            playerIds.add(animalOwner);
        } else {
            playerIds.add(farm.getOwnerUUID());
            playerIds.addAll(farm.getMembers());
        }

        double totalLuck = 0.0;
        int onlinePlayers = 0;
        for (UUID playerId : playerIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                totalLuck += PlayerStardewDataAPI.getDailyLuck(player);
                onlinePlayers++;
            }
        }
        return onlinePlayers == 0 ? 0.0 : totalLuck / onlinePlayers;
    }

    private boolean consumeOneHayFromTrough(BuildingUtilityContext context) {
        for (FeedTroughBlockEntity trough : context.feedTroughs()) {
            ItemStack removed = trough.takeOneFromSelf(false);
            if (removed.isEmpty()) {
                removed = trough.extractAutomation(1, false);
            }
            if (!removed.isEmpty()) {
                return true;
            }
        }

        for (AutoFeedTroughBlockEntity autoTrough : context.autoFeedTroughs()) {
            ItemStack removed = autoTrough.takeOneFromSelf(false);
            if (removed.isEmpty()) {
                removed = autoTrough.extractAutomation(1, false);
            }
            if (!removed.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void completeAutomaticFeedPassIfReady(
            ServerLevel level,
            AnimalWorldData worldData,
            AnimalBuildingRecord building,
            int absoluteDay,
            Map<String, BuildingUtilityContext> utilityContexts
    ) {
        if (building == null
                || !building.buildingType().hasAutomaticFeed()
                || building.lastAutoFeedProcessedAbsDay() >= absoluteDay
                || !allBuildingAnimalsProcessedThrough(
                        worldData,
                        building,
                        absoluteDay)) {
            return;
        }
        ensureBuildingLoaded(level, building);
        if (!level.isLoaded(building.managerPos())) {
            return;
        }

        BuildingUtilityContext context = utilityContexts.computeIfAbsent(
                building.buildingId(),
                ignored -> resolveBuildingUtilities(
                        level, building, currentAbsoluteDay())
        );
        BlockPos origin = context.firstAutoFeedTroughPos();
        UUID owner = parseUuid(building.ownerPlayerUuid());
        if (origin != null && owner != null) {
            AutoFeedTroughBlockEntity.refillConnectedNetwork(
                    level,
                    origin,
                    owner,
                    Integer.MAX_VALUE
            );
        }
        building.setLastAutoFeedProcessedAbsDay(absoluteDay);
        worldData.markChanged();
    }

    private boolean allBuildingAnimalsProcessedThrough(
            AnimalWorldData worldData,
            AnimalBuildingRecord building,
            int absoluteDay
    ) {
        for (Long animalId : building.memberAnimalIds()) {
            FarmAnimalRecord member = worldData.getAnimal(animalId).orElse(null);
            if (member != null && member.lastProcessedAbsDay() < absoluteDay) {
                return false;
            }
        }
        return true;
    }

    private boolean isAnimalOutdoors(ServerLevel level, FarmAnimalRecord record, AnimalBuildingRecord building) {
        if (building == null) {
            return true;
        }

        // 如果建筑区块未加载，假设动物在室内（避免错误惩罚）
        if (!level.isLoaded(building.managerPos())) {
            return false;
        }

        BaseCoopAnimalEntity entity = findEntityByManagedId(level, record.animalId());
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
        BaseCoopAnimalEntity entity = findEntityByManagedId(level, record.animalId());
        if (entity == null) {
            return;
        }
        double cx = (building.minX() + building.maxX()) / 2.0;
        double cy = building.minY() + 1.0;
        double cz = (building.minZ() + building.maxZ()) / 2.0;
        entity.moveTo(cx, cy, cz, entity.getYRot(), entity.getXRot());
    }

    private BuildingUtilityContext scanBuildingUtilities(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        boolean hasAutoPetter = false;
        boolean hasHeater = false;
        List<AutoGrabberBlockEntity> autoGrabbers = new ArrayList<>();
        List<FeedTroughBlockEntity> feedTroughs = new ArrayList<>();
        List<AutoFeedTroughBlockEntity> autoFeedTroughs = new ArrayList<>();
        BlockPos firstAutoFeedTroughPos = null;
        for (BlockPos pos : iterateBuildingUtilityPositions(building)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AutoPetterBlockEntity) {
                hasAutoPetter = true;
            } else if (be instanceof AutoGrabberBlockEntity autoGrabber) {
                autoGrabbers.add(autoGrabber);
            } else if (be instanceof HeaterBlockEntity) {
                hasHeater = true;
            } else if (be instanceof FeedTroughBlockEntity trough) {
                feedTroughs.add(trough);
            } else if (be instanceof AutoFeedTroughBlockEntity autoTrough) {
                autoFeedTroughs.add(autoTrough);
                if (firstAutoFeedTroughPos == null) {
                    firstAutoFeedTroughPos = pos.immutable();
                }
            }
        }
        return new BuildingUtilityContext(
                new AnimalBuildingDailyContext(
                        building.buildingId(),
                        building.structureRevision(),
                        currentAbsoluteDay(),
                        building.capabilities(),
                        List.copyOf(building.memberAnimalIds()),
                        StardewTimeManager.get()
                                .getCurrentSeason() == 3,
                        com.stardew.craft.weather.WeatherManager
                                .isRaining(level),
                        hasAutoPetter,
                        hasHeater,
                        autoGrabbers.stream()
                                .map(BlockEntity::getBlockPos)
                                .toList(),
                        feedTroughs.stream()
                                .map(BlockEntity::getBlockPos)
                                .toList(),
                        autoFeedTroughs.stream()
                                .map(BlockEntity::getBlockPos)
                                .toList(),
                        AnimalWorldData.get(level)
                                .getAnimalProduceForBuilding(
                                        building.buildingId())
                                .size()),
                List.copyOf(autoGrabbers),
                List.copyOf(feedTroughs),
                List.copyOf(autoFeedTroughs),
                firstAutoFeedTroughPos
        );
    }

    private BuildingUtilityContext resolveBuildingUtilities(
            ServerLevel level,
            AnimalBuildingRecord building,
            int absoluteDay
    ) {
        CachedBuildingUtilities cached =
                buildingUtilityCache.get(building.buildingId());
        if (cached != null
                && cached.absoluteDay() == absoluteDay
                && cached.structureRevision()
                        == building.structureRevision()) {
            return cached.context();
        }
        BuildingUtilityContext context =
                scanBuildingUtilities(level, building);
        buildingUtilityCache.put(
                building.buildingId(),
                new CachedBuildingUtilities(
                        absoluteDay,
                        building.structureRevision(),
                        context));
        return context;
    }

    /**
     * Returns the same immutable, revision-aware utility snapshot used by animal daily updates.
     * Menus must consume this projection instead of rescanning blocks or deriving device state.
     */
    public AnimalBuildingDailyContext buildingDailyContext(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        if (level == null || building == null
                || !building.isGameplayEnabled()) {
            return null;
        }
        return resolveBuildingUtilities(
                level, building, currentAbsoluteDay())
                .dailyContext();
    }

    /**
     * Returns the cached, revision-aware auto-grabber handles used by the daily pass.
     *
     * <p>Produce projection can run more than once while a building catches up, so it must share
     * this snapshot instead of rescanning the full 3D building volume for every produced item.
     */
    public List<AutoGrabberBlockEntity> autoGrabbersForBuilding(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        if (level == null || building == null
                || !building.isGameplayEnabled()) {
            return List.of();
        }
        return resolveBuildingUtilities(
                level, building, currentAbsoluteDay())
                .autoGrabbers();
    }

    /**
     * Block changes are event-driven. This is deliberately transient: the
     * authoritative building revision and validation state remain in world
     * data, while block-entity references are rebuilt on demand.
     */
    public void invalidateBuildingUtilityCache(String buildingId) {
        if (buildingId != null) {
            buildingUtilityCache.remove(buildingId);
        }
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

    private boolean hasAnimalOwnerProfession(
            FarmAnimalRecord record,
            AnimalBuildingRecord building,
            int professionId
    ) {
        if (professionId < 0) {
            return false;
        }
        ProfessionType profession = ProfessionType.fromId(professionId);
        if (profession == null) {
            return false;
        }

        UUID ownerUuid = parseUuid(record.ownerPlayerUuid());
        if (ownerUuid == null && building != null) {
            ownerUuid = parseUuid(building.ownerPlayerUuid());
        }
        if (ownerUuid == null) {
            return false;
        }
        return PlayerDataManager.getPlayerData(ownerUuid).hasProfession(profession);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
