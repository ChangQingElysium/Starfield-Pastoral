package com.stardew.craft.animal.data;

import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.model.AnimalProduceLedgerEntry;
import com.stardew.craft.animal.model.AnimalPendingBirth;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AnimalWorldData extends SavedData {
    private static final String DATA_NAME = "stardew_animal_world";

    private final Map<String, AnimalBuildingRecord> buildings = new LinkedHashMap<>();
    private final Map<Long, FarmAnimalRecord> animals = new LinkedHashMap<>();
    private final Map<Long, AnimalProduceLedgerEntry> produceLedger = new LinkedHashMap<>();
    private final Map<Long, AnimalPendingBirth> pendingBirths = new LinkedHashMap<>();
    private final Map<String, Integer> hayByOwner = new LinkedHashMap<>();
    private long nextBuildingId = 1L;
    private long nextAnimalId = 1L;
    private long nextProduceLedgerId = 1L;
    private long nextPendingBirthId = 1L;

    public String createBuilding(ServerLevel level,
                                 AnimalBuildingType buildingType,
                                 UUID ownerPlayerId,
                                 BlockPos managerPos,
                                 int range,
                                 String customName,
                                 int capacity) {
        String buildingId = buildingType.family()
                + "_" + allocateBuildingId();
        int maxCapacity = capacity > 0 ? capacity : buildingType.defaultCapacity();
        int hayCapacity = buildingType.hayCapacity();

        int minX = managerPos.getX() - range;
        int minY = managerPos.getY() - range;
        int minZ = managerPos.getZ() - range;
        int maxX = managerPos.getX() + range;
        int maxY = managerPos.getY() + range;
        int maxZ = managerPos.getZ() + range;

        AnimalBuildingRecord record = new AnimalBuildingRecord(
            buildingId,
            ownerPlayerId.toString(),
            buildingType,
            customName,
            level.dimension().location().toString(),
            managerPos.immutable(),
            range,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            maxCapacity,
            hayCapacity,
            true,
            false,
            Collections.emptySet(),
            Collections.emptySet(),
            new java.util.LinkedHashSet<>()
        );

        buildings.put(buildingId, record);
        hayByOwner.putIfAbsent(ownerPlayerId.toString(), 0);
        clampHayToCapacity(ownerPlayerId.toString());
        setDirty();
        return buildingId;
    }

    public void renameBuilding(String buildingId, String customName) {
        AnimalBuildingRecord record = requireBuilding(buildingId);
        record.setCustomName(customName);
        setDirty();
    }

    public void toggleDoor(String buildingId, boolean doorOpen) {
        AnimalBuildingRecord record = requireBuilding(buildingId);
        record.setDoorOpen(doorOpen);
        setDirty();
    }

    public void removeBuilding(String buildingId) {
        AnimalBuildingRecord record = requireBuilding(buildingId);
        if (!record.memberAnimalIds().isEmpty()) {
            throw new IllegalStateException("Building has animals bound: " + record.memberAnimalIds().size());
        }
        if (produceLedger.values().stream()
                .anyMatch(entry -> entry.buildingId().equals(buildingId))) {
            throw new IllegalStateException("Building has uncollected animal produce");
        }
        if (pendingBirths.values().stream()
                .anyMatch(event -> event.buildingId().equals(buildingId))) {
            throw new IllegalStateException("Building has a pending animal birth");
        }
        buildings.remove(buildingId);
        clampHayToCapacity(record.ownerPlayerUuid());
        setDirty();
    }

    public int getHayAmount(UUID ownerPlayerId) {
        int total = 0;
        for (String owner : hayStorageKeys(ownerPlayerId)) {
            total += hayByOwner.getOrDefault(owner, 0);
        }
        return total;
    }

    public int getHayCapacity(UUID ownerPlayerId) {
        int total = 0;
        Set<String> owners = hayStorageKeys(ownerPlayerId);
        for (AnimalBuildingRecord record : buildings.values()) {
            if (owners.contains(record.ownerPlayerUuid())) {
                total += Math.max(0, record.hayCapacity());
            }
        }
        return total;
    }

    public boolean hasAnySilo(UUID ownerPlayerId) {
        return getHayCapacity(ownerPlayerId) > 0;
    }

    public boolean hasAnyStoredHay() {
        for (int pieces : hayByOwner.values()) {
            if (pieces > 0) {
                return true;
            }
        }
        return false;
    }

    public int storeHay(UUID ownerPlayerId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        String owner = hayStorageOwner(ownerPlayerId);
        int capacity = getHayCapacity(ownerPlayerId);
        if (capacity <= 0) {
            return 0;
        }
        int current = getHayAmount(ownerPlayerId);
        int free = Math.max(0, capacity - current);
        int stored = Math.min(free, amount);
        if (stored > 0) {
            hayByOwner.put(owner, hayByOwner.getOrDefault(owner, 0) + stored);
            setDirty();
        }
        return stored;
    }

    public int takeHay(UUID ownerPlayerId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        int removedTotal = 0;
        for (String owner : hayStorageKeys(ownerPlayerId)) {
            if (remaining <= 0) {
                break;
            }
            int current = hayByOwner.getOrDefault(owner, 0);
            if (current <= 0) {
                continue;
            }
            int removed = Math.min(current, remaining);
            hayByOwner.put(owner, current - removed);
            remaining -= removed;
            removedTotal += removed;
        }
        if (removedTotal > 0) {
            setDirty();
        }
        return removedTotal;
    }

    private String hayStorageOwner(UUID ownerPlayerId) {
        UUID farmOwner = com.stardew.craft.farm.FarmInstanceRegistry.get().getOwnerForPlayer(ownerPlayerId);
        return (farmOwner == null ? ownerPlayerId : farmOwner).toString();
    }

    private Set<String> hayStorageKeys(UUID ownerPlayerId) {
        LinkedHashSet<String> owners = new LinkedHashSet<>();
        var registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        UUID farmOwner = registry.getOwnerForPlayer(ownerPlayerId);
        if (farmOwner != null) {
            var farm = registry.getFarm(farmOwner);
            if (farm != null) {
                for (UUID farmer : farm.getAllFarmers()) {
                    owners.add(farmer.toString());
                }
            } else {
                owners.add(farmOwner.toString());
            }
        }
        owners.add(ownerPlayerId.toString());
        return owners;
    }

    public int takeHayFromAnyOwner(int amount) {
        if (amount <= 0) {
            return 0;
        }
        for (Map.Entry<String, Integer> entry : hayByOwner.entrySet()) {
            int current = entry.getValue();
            if (current <= 0) {
                continue;
            }
            int removed = Math.min(current, amount);
            entry.setValue(current - removed);
            setDirty();
            return removed;
        }
        return 0;
    }

    public FarmAnimalRecord createAnimal(String animalTypeId,
                                         String customName,
                                         String buildingId,
                                         AnimalAcquisitionSource source) {
        AnimalBuildingRecord building = requireBuilding(buildingId);
        if (!building.hasCapacity()) {
            throw new IllegalStateException("Building capacity exceeded: " + building.buildingId());
        }

        StardewTimeManager time = StardewTimeManager.get();
        AnimalTypeCatalog.AnimalTypeSpec typeSpec =
                AnimalTypeCatalog.require(animalTypeId);
        long animalId = allocateAnimalId();
        FarmAnimalRecord animalRecord = new FarmAnimalRecord(
            animalId,
            animalTypeId,
            customName,
            buildingId,
            source,
            time.getCurrentDay(),
            time.getCurrentSeason(),
            time.getCurrentYear(),
            0,
            typeSpec.daysToMature()
        );
        animalRecord.setOwnerPlayerUuid(building.ownerPlayerUuid());

        animals.put(animalId, animalRecord);
        building.addAnimal(animalId);
        setDirty();
        return animalRecord;
    }

    public int advanceAnimalGrowthOneDay() {
        if (animals.isEmpty()) {
            return 0;
        }
        int maturedToday = 0;
        for (FarmAnimalRecord record : animals.values()) {
            boolean wasBaby = record.isBaby();
            record.incrementAgeDays(1);
            if (wasBaby && !record.isBaby()) {
                maturedToday++;
            }
        }
        setDirty();
        return maturedToday;
    }

    public Optional<AnimalBuildingRecord> getBuilding(String buildingId) {
        AnimalBuildingRecord record = buildings.get(buildingId);
        if (record == null || !record.isGameplayEnabled()) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public Optional<AnimalBuildingRecord> getBuildingIncludingInactive(String buildingId) {
        return Optional.ofNullable(buildings.get(buildingId));
    }

    public boolean markBuildingValidationFailed(
            String buildingId,
            String issue
    ) {
        AnimalBuildingRecord record = buildings.get(buildingId);
        if (record == null) {
            return false;
        }
        record.markStructureInvalid(issue);
        setDirty();
        return true;
    }

    public boolean beginBuildingConstruction(
            String buildingId,
            int completionAbsDay
    ) {
        AnimalBuildingRecord record = buildings.get(buildingId);
        if (record == null || !record.active()) {
            return false;
        }
        record.beginConstruction(completionAbsDay);
        setDirty();
        return true;
    }

    public List<AnimalBuildingRecord> completeDueConstructions(
            int currentAbsDay
    ) {
        ArrayList<AnimalBuildingRecord> completed =
                new ArrayList<>();
        for (AnimalBuildingRecord record : buildings.values()) {
            if (record.completeConstruction(currentAbsDay)) {
                checkpointPausedAnimals(
                        record, currentAbsDay);
                completed.add(record);
            }
        }
        if (!completed.isEmpty()) {
            setDirty();
        }
        return List.copyOf(completed);
    }

    public boolean checkpointPausedAnimalsAt(
            String buildingId,
            int currentAbsDay
    ) {
        AnimalBuildingRecord record = buildings.get(buildingId);
        if (record == null) {
            return false;
        }
        checkpointPausedAnimals(record, currentAbsDay);
        setDirty();
        return true;
    }

    private void checkpointPausedAnimals(
            AnimalBuildingRecord building,
            int currentAbsDay
    ) {
        for (Long animalId : building.memberAnimalIds()) {
            FarmAnimalRecord animal = animals.get(animalId);
            if (animal != null
                    && animal.lastProcessedAbsDay()
                            < currentAbsDay) {
                animal.setLastProcessedAbsDay(currentAbsDay);
            }
        }
    }

    public int invalidateStructuresAt(
            String dimensionId,
            BlockPos changedPos,
            String issue
    ) {
        int invalidated = 0;
        for (AnimalBuildingRecord record : buildings.values()) {
            if (!record.dimensionId().equals(dimensionId)
                    || !record.active()
                    || (record.validationState()
                            != AnimalBuildingRecord.ValidationState.VALID
                        && record.validationState()
                            != AnimalBuildingRecord.ValidationState.CONSTRUCTING)
                    || !record.isStructuralCell(changedPos)) {
                continue;
            }
            record.markStructureInvalid(issue);
            invalidated++;
        }
        if (invalidated > 0) {
            setDirty();
        }
        return invalidated;
    }

    public Collection<AnimalBuildingRecord> getBuildings() {
        List<AnimalBuildingRecord> active = new ArrayList<>();
        for (AnimalBuildingRecord record : buildings.values()) {
            if (record.isGameplayEnabled()) {
                active.add(record);
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * Administrative snapshot used by validation, relocation and cache
     * invalidation. Callers must still check {@link
     * AnimalBuildingRecord#isGameplayEnabled()} before running gameplay.
     */
    public Collection<AnimalBuildingRecord> getBuildingsIncludingInactive() {
        return Collections.unmodifiableList(
                new ArrayList<>(buildings.values()));
    }

    public Optional<AnimalBuildingRecord> findBuildingByManager(String dimensionId,
                                                                UUID ownerPlayerId,
                                                                String family,
                                                                BlockPos managerPos) {
        var registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        for (AnimalBuildingRecord record : buildings.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (!registry.canOperateBuilding(ownerPlayerId, record.ownerPlayerUuid())) {
                continue;
            }
            if (!family.equalsIgnoreCase(record.buildingType().family())) {
                continue;
            }
            if (!managerPos.equals(record.managerPos())) {
                continue;
            }
            return Optional.of(record);
        }
        return Optional.empty();
    }

    public Optional<AnimalBuildingRecord> findBuildingByManagerAnyOwner(String dimensionId,
                                                                         String family,
                                                                         BlockPos managerPos) {
        for (AnimalBuildingRecord record : buildings.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (!family.equalsIgnoreCase(record.buildingType().family())) {
                continue;
            }
            if (!managerPos.equals(record.managerPos())) {
                continue;
            }
            return Optional.of(record);
        }
        return Optional.empty();
    }

    public boolean moveBuildingManagerFromItem(String buildingId,
                                               UUID ownerPlayerId,
                                               String dimensionId,
                                               BlockPos newManagerPos,
                                               String family) {
        AnimalBuildingRecord existing = buildings.get(buildingId);
        if (existing == null) {
            return false;
        }
        return moveBuildingManagerFromItem(
            buildingId,
            ownerPlayerId,
            dimensionId,
            newManagerPos,
            family,
            existing.minX(),
            existing.minY(),
            existing.minZ(),
            existing.maxX(),
            existing.maxY(),
            existing.maxZ()
        );
    }

    public boolean moveBuildingManagerFromItem(String buildingId,
                                               UUID ownerPlayerId,
                                               String dimensionId,
                                               BlockPos newManagerPos,
                                               String family,
                                               int minX,
                                               int minY,
                                               int minZ,
                                               int maxX,
                                               int maxY,
                                               int maxZ) {
        AnimalBuildingRecord existing = buildings.get(buildingId);
        if (existing == null) {
            return false;
        }
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(ownerPlayerId, existing.ownerPlayerUuid())) {
            return false;
        }
        if (!dimensionId.equals(existing.dimensionId())) {
            return false;
        }
        if (!family.equalsIgnoreCase(existing.buildingType().family())) {
            return false;
        }

        int range = Math.max(
            Math.max(Math.abs(newManagerPos.getX() - minX), Math.abs(maxX - newManagerPos.getX())),
            Math.max(
                Math.max(Math.abs(newManagerPos.getY() - minY), Math.abs(maxY - newManagerPos.getY())),
                Math.max(Math.abs(newManagerPos.getZ() - minZ), Math.abs(maxZ - newManagerPos.getZ()))
            )
        );

        AnimalBuildingRecord moved = new AnimalBuildingRecord(
            existing.buildingId(),
            existing.ownerPlayerUuid(),
            existing.buildingType(),
            existing.customName(),
            existing.dimensionId(),
            newManagerPos.immutable(),
            range,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            existing.capacity(),
            existing.hayCapacity(),
            false,
            existing.doorOpen(),
            existing.interiorAirCells(),
            existing.boundaryDoorCells(),
            new java.util.LinkedHashSet<>(existing.memberAnimalIds())
        );
        moved.setLastAutoFeedProcessedAbsDay(
                existing.lastAutoFeedProcessedAbsDay());
        moved.markStructureValidated(existing.structureRevision());
        if (existing.hasPendingConstruction()) {
            moved.beginConstruction(
                    existing.constructionCompletesAbsDay());
        }
        moved.markRelocating();

        buildings.put(buildingId, moved);
        setDirty();
        return true;
    }

    public boolean rebindValidatedBuildingManager(
            String buildingId,
            UUID ownerPlayerId,
            String dimensionId,
            BlockPos newManagerPos,
            String family,
            long expectedStructureRevision,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            Set<Long> interiorAirCells,
            Set<Long> boundaryDoorCells,
            int capacity
    ) {
        AnimalBuildingRecord existing = buildings.get(buildingId);
        if (existing == null
                || !existing.isGameplayEnabled()
                || existing.structureRevision()
                        != expectedStructureRevision
                || !dimensionId.equals(existing.dimensionId())
                || !family.equalsIgnoreCase(
                        existing.buildingType().family())
                || !com.stardew.craft.farm.FarmInstanceRegistry
                        .get().canOperateBuilding(
                                ownerPlayerId,
                                existing.ownerPlayerUuid())) {
            return false;
        }
        int range = Math.max(
                Math.max(
                        Math.abs(newManagerPos.getX() - minX),
                        Math.abs(maxX - newManagerPos.getX())),
                Math.max(
                        Math.max(
                                Math.abs(newManagerPos.getY() - minY),
                                Math.abs(maxY - newManagerPos.getY())),
                        Math.max(
                                Math.abs(newManagerPos.getZ() - minZ),
                                Math.abs(maxZ - newManagerPos.getZ()))));
        AnimalBuildingRecord rebound = new AnimalBuildingRecord(
                existing.buildingId(),
                existing.ownerPlayerUuid(),
                existing.buildingType(),
                existing.customName(),
                existing.dimensionId(),
                newManagerPos.immutable(),
                range,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                Math.max(0, capacity),
                existing.hayCapacity(),
                true,
                existing.doorOpen(),
                interiorAirCells,
                boundaryDoorCells,
                new LinkedHashSet<>(existing.memberAnimalIds()));
        rebound.setLastAutoFeedProcessedAbsDay(
                existing.lastAutoFeedProcessedAbsDay());
        rebound.markStructureValidated(
                existing.structureRevision() + 1L);
        for (Long animalId : rebound.memberAnimalIds()) {
            FarmAnimalRecord animal = animals.get(animalId);
            if (animal != null) {
                animal.clearProjectionAnchor();
            }
        }
        buildings.put(buildingId, rebound);
        setDirty();
        return true;
    }

    public int deactivateBuildingForRelocation(String buildingId) {
        AnimalBuildingRecord existing = requireBuildingIncludingInactive(buildingId);
        AnimalBuildingRecord inactive = new AnimalBuildingRecord(
            existing.buildingId(),
            existing.ownerPlayerUuid(),
            existing.buildingType(),
            existing.customName(),
            existing.dimensionId(),
            existing.managerPos(),
            existing.range(),
            existing.minX(),
            existing.minY(),
            existing.minZ(),
            existing.maxX(),
            existing.maxY(),
            existing.maxZ(),
            existing.capacity(),
            existing.hayCapacity(),
            false,
            existing.doorOpen(),
            existing.interiorAirCells(),
            existing.boundaryDoorCells(),
            new java.util.LinkedHashSet<>(existing.memberAnimalIds())
        );
        inactive.setLastAutoFeedProcessedAbsDay(
                existing.lastAutoFeedProcessedAbsDay());
        inactive.markStructureValidated(existing.structureRevision());
        if (existing.hasPendingConstruction()) {
            inactive.beginConstruction(
                    existing.constructionCompletesAbsDay());
        }
        inactive.markRelocating();
        buildings.put(buildingId, inactive);
        setDirty();
        return inactive.memberAnimalIds().size();
    }

    public int demolishBuildingAndRemoveAnimals(String buildingId) {
        AnimalBuildingRecord existing = requireBuildingIncludingInactive(buildingId);
        int removedAnimals = 0;
        for (Long animalId : new ArrayList<>(existing.memberAnimalIds())) {
            if (animals.remove(animalId) != null) {
                removedAnimals++;
            }
        }
        produceLedger.values().removeIf(
                entry -> entry.buildingId().equals(buildingId));
        pendingBirths.values().removeIf(
                event -> event.buildingId().equals(buildingId));
        buildings.remove(buildingId);
        clampHayToCapacity(existing.ownerPlayerUuid());
        setDirty();
        return removedAnimals;
    }

    public String createOrUpdateBuildingAtManager(ServerLevel level,
                                                  AnimalBuildingType buildingType,
                                                  UUID ownerPlayerId,
                                                  BlockPos managerPos,
                                                  String customName,
                                                  int minX,
                                                  int minY,
                                                  int minZ,
                                                  int maxX,
                                                  int maxY,
                                                  int maxZ,
                                                  Set<Long> interiorAirCells,
                                                  Set<Long> boundaryDoorCells) {
        return createOrUpdateBuildingAtManager(level, buildingType, ownerPlayerId, managerPos, customName,
                minX, minY, minZ, maxX, maxY, maxZ, interiorAirCells, boundaryDoorCells,
                buildingType.defaultCapacity());
    }

    public String createOrUpdateBuildingAtManager(ServerLevel level,
                                                  AnimalBuildingType buildingType,
                                                  UUID ownerPlayerId,
                                                  BlockPos managerPos,
                                                  String customName,
                                                  int minX,
                                                  int minY,
                                                  int minZ,
                                                  int maxX,
                                                  int maxY,
                                                  int maxZ,
                                                  Set<Long> interiorAirCells,
                                                  Set<Long> boundaryDoorCells,
                                                  int capacity) {
        String owner = ownerPlayerId.toString();
        String dimensionId = level.dimension().location().toString();
        int resolvedCapacity = Math.max(0, capacity);

        Optional<AnimalBuildingRecord> existingOpt = findBuildingByManager(
            dimensionId,
            ownerPlayerId,
            buildingType.family(),
            managerPos
        );

        int range = Math.max(
            Math.max(Math.abs(managerPos.getX() - minX), Math.abs(maxX - managerPos.getX())),
            Math.max(
                Math.max(Math.abs(managerPos.getY() - minY), Math.abs(maxY - managerPos.getY())),
                Math.max(Math.abs(managerPos.getZ() - minZ), Math.abs(maxZ - managerPos.getZ()))
            )
        );

        if (existingOpt.isPresent()) {
            AnimalBuildingRecord existing = existingOpt.get();
            if (buildingType.tier() < existing.buildingType().tier()) {
                throw new IllegalArgumentException("Cannot downgrade building tier: " + existing.buildingId());
            }

            AnimalBuildingRecord updated = new AnimalBuildingRecord(
                existing.buildingId(),
                owner,
                buildingType,
                existing.customName() == null || existing.customName().isBlank() ? customName : existing.customName(),
                dimensionId,
                managerPos.immutable(),
                range,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                resolvedCapacity,
                buildingType.hayCapacity(),
                true,
                existing.doorOpen(),
                interiorAirCells,
                boundaryDoorCells,
                new java.util.LinkedHashSet<>(existing.memberAnimalIds())
            );
            updated.setLastAutoFeedProcessedAbsDay(
                    existing.lastAutoFeedProcessedAbsDay());
            updated.markStructureValidated(existing.structureRevision() + 1L);
            if (existing.hasPendingConstruction()) {
                updated.beginConstruction(
                        existing.constructionCompletesAbsDay());
            }

            buildings.put(existing.buildingId(), updated);
            hayByOwner.putIfAbsent(owner, 0);
            clampHayToCapacity(owner);
            setDirty();
            return existing.buildingId();
        }

        String buildingId = buildingType.family()
                + "_" + allocateBuildingId();
        AnimalBuildingRecord created = new AnimalBuildingRecord(
            buildingId,
            owner,
            buildingType,
            customName,
            dimensionId,
            managerPos.immutable(),
            range,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            resolvedCapacity,
            buildingType.hayCapacity(),
            true,
            false,
            interiorAirCells,
            boundaryDoorCells,
            new java.util.LinkedHashSet<>()
        );

        buildings.put(buildingId, created);
        hayByOwner.putIfAbsent(owner, 0);
        clampHayToCapacity(owner);
        setDirty();
        return buildingId;
    }

    public Optional<AnimalBuildingRecord> findBuildingAt(String dimensionId, BlockPos pos, UUID ownerPlayerId, Set<String> buildingFamilies) {
        String owner = ownerPlayerId.toString();
        for (AnimalBuildingRecord record : buildings.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (!owner.equals(record.ownerPlayerUuid())) {
                continue;
            }
            if (!buildingFamilies.contains(record.buildingType().family())) {
                continue;
            }
            if (!record.isGameplayEnabled()) {
                continue;
            }
            if (record.isInBounds(pos)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Optional<AnimalBuildingRecord> findBuildingAtAnyOwner(String dimensionId, BlockPos pos, Set<String> buildingFamilies) {
        for (AnimalBuildingRecord record : buildings.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (!buildingFamilies.contains(record.buildingType().family())) {
                continue;
            }
            if (!record.isGameplayEnabled()) {
                continue;
            }
            if (record.isInBounds(pos)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Collection<FarmAnimalRecord> getAnimals() {
        return animals.values();
    }

    public Optional<FarmAnimalRecord> getAnimal(long animalId) {
        return Optional.ofNullable(animals.get(animalId));
    }

    public boolean setAllowReproduction(long animalId, boolean allowReproduction) {
        FarmAnimalRecord record = animals.get(animalId);
        if (record == null) {
            return false;
        }
        record.setAllowReproduction(allowReproduction);
        setDirty();
        return true;
    }

    public boolean renameAnimal(long animalId, String customName) {
        FarmAnimalRecord record = animals.get(animalId);
        if (record == null) {
            return false;
        }
        record.setCustomName(customName == null ? "" : customName);
        setDirty();
        return true;
    }

    public boolean hasOtherAnimalWithName(long animalId, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.trim();
        for (FarmAnimalRecord record : animals.values()) {
            if (record.animalId() == animalId) {
                continue;
            }
            String existing = record.customName();
            if (existing == null || existing.isBlank()) {
                continue;
            }
            if (existing.trim().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyAnimalWithName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.trim();
        for (FarmAnimalRecord record : animals.values()) {
            String existing = record.customName();
            if (existing == null || existing.isBlank()) {
                continue;
            }
            if (existing.trim().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean moveAnimalToBuilding(long animalId, String targetBuildingId, String ownerPlayerUuid) {
        FarmAnimalRecord animal = animals.get(animalId);
        if (animal == null || targetBuildingId == null || targetBuildingId.isBlank()) {
            return false;
        }

        AnimalBuildingRecord source = buildings.get(animal.buildingId());
        AnimalBuildingRecord target = buildings.get(targetBuildingId);
        if (source == null || target == null
                || !source.isGameplayEnabled()
                || !target.isGameplayEnabled()) {
            return false;
        }
        if (source.buildingId().equals(target.buildingId())) {
            return false;
        }

        if (ownerPlayerUuid != null && !ownerPlayerUuid.isBlank()) {
            var registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
            if (!registry.canOperateBuilding(java.util.UUID.fromString(ownerPlayerUuid), source.ownerPlayerUuid())
                    || !registry.canOperateBuilding(java.util.UUID.fromString(ownerPlayerUuid), target.ownerPlayerUuid())) {
                return false;
            }
        }

        AnimalTypeCatalog.AnimalTypeSpec typeSpec =
                AnimalTypeCatalog.find(animal.animalTypeId());
        if (typeSpec == null) {
            return false;
        }
        String animalFamily = typeSpec.family();
        if (!animalFamily.equalsIgnoreCase(target.buildingType().family())) {
            return false;
        }
        if (!target.hasCapacity()) {
            return false;
        }

        source.removeAnimal(animalId);
        target.addAnimal(animalId);
        animal.setBuildingId(target.buildingId());
        animal.clearProjectionAnchor();
        setDirty();
        return true;
    }

    public boolean removeAnimal(long animalId) {
        FarmAnimalRecord removed = animals.remove(animalId);
        if (removed == null) {
            return false;
        }
        AnimalBuildingRecord building = buildings.get(removed.buildingId());
        if (building != null) {
            building.removeAnimal(animalId);
        }
        setDirty();
        return true;
    }

    public List<AnimalProduceLedgerEntry> submitAnimalProduce(
            String buildingId,
            long animalId,
            int producedAbsDay,
            ResourceLocation itemId,
            int quality,
            int count
    ) {
        if (!buildings.containsKey(buildingId)) {
            return List.of();
        }
        if (itemId == null || count <= 0) {
            return List.of();
        }

        List<AnimalProduceLedgerEntry> submitted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long entryId = allocateProduceLedgerId();
            AnimalProduceLedgerEntry entry = new AnimalProduceLedgerEntry(
                    entryId,
                    buildingId,
                    animalId,
                    producedAbsDay,
                    itemId,
                    quality,
                    "",
                    0L
            );
            produceLedger.put(entryId, entry);
            submitted.add(entry);
        }
        setDirty();
        return List.copyOf(submitted);
    }

    public List<AnimalProduceLedgerEntry> submitAnimalProduceNear(
            String buildingId,
            long animalId,
            int producedAbsDay,
            ResourceLocation itemId,
            int quality,
            int count,
            String dimensionId,
            BlockPos anchor,
            int radius
    ) {
        if (!buildings.containsKey(buildingId)
                || itemId == null
                || count <= 0
                || dimensionId == null
                || dimensionId.isBlank()
                || anchor == null
                || radius <= 0) {
            return List.of();
        }

        List<AnimalProduceLedgerEntry> submitted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long entryId = allocateProduceLedgerId();
            AnimalProduceLedgerEntry entry = new AnimalProduceLedgerEntry(
                    entryId,
                    buildingId,
                    animalId,
                    producedAbsDay,
                    itemId,
                    quality,
                    "",
                    0L,
                    false,
                    dimensionId,
                    anchor.asLong(),
                    radius
            );
            produceLedger.put(entryId, entry);
            submitted.add(entry);
        }
        setDirty();
        return List.copyOf(submitted);
    }

    public Optional<AnimalProduceLedgerEntry> getAnimalProduce(long entryId) {
        return Optional.ofNullable(produceLedger.get(entryId));
    }

    public AnimalPendingBirth queueAnimalBirth(
            String ownerPlayerUuid,
            String buildingId,
            long parentAnimalId,
            String animalTypeId,
            int createdAbsDay
    ) {
        AnimalPendingBirth event = new AnimalPendingBirth(
                allocatePendingBirthId(),
                ownerPlayerUuid,
                buildingId,
                parentAnimalId,
                animalTypeId,
                createdAbsDay
        );
        pendingBirths.put(event.eventId(), event);
        setDirty();
        return event;
    }

    public Optional<AnimalPendingBirth> getPendingBirth(long eventId) {
        return Optional.ofNullable(pendingBirths.get(eventId));
    }

    public List<AnimalPendingBirth> getPendingBirthsForOwner(String ownerPlayerUuid) {
        return pendingBirths.values().stream()
                .filter(event -> event.ownerPlayerUuid().equals(ownerPlayerUuid))
                .toList();
    }

    public int getPendingBirthCountForBuilding(String buildingId) {
        return (int) pendingBirths.values().stream()
                .filter(event -> event.buildingId().equals(buildingId))
                .count();
    }

    public boolean completePendingBirth(long eventId) {
        if (pendingBirths.remove(eventId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public List<AnimalProduceLedgerEntry> getAnimalProduceForBuilding(String buildingId) {
        return produceLedger.values().stream()
                .filter(entry -> entry.buildingId().equals(buildingId))
                .toList();
    }

    public Collection<AnimalProduceLedgerEntry> getAnimalProduceLedger() {
        return List.copyOf(produceLedger.values());
    }

    public boolean markAnimalProduceProjected(
            long entryId,
            String dimensionId,
            BlockPos pos
    ) {
        AnimalProduceLedgerEntry existing = produceLedger.get(entryId);
        if (existing == null || dimensionId == null || dimensionId.isBlank() || pos == null) {
            return false;
        }
        produceLedger.put(entryId, existing.withProjection(dimensionId, pos));
        setDirty();
        return true;
    }

    public boolean releaseAnimalProduceProjection(
            long entryId,
            String dimensionId,
            BlockPos pos
    ) {
        AnimalProduceLedgerEntry existing = produceLedger.get(entryId);
        if (existing == null || !existing.isProjected()) {
            return false;
        }
        if (!existing.projectedDimensionId().equals(dimensionId)
                || !existing.projectedPos().equals(pos)) {
            return false;
        }
        produceLedger.put(entryId, existing.withoutProjection());
        setDirty();
        return true;
    }

    public boolean completeAnimalProduce(long entryId) {
        if (produceLedger.remove(entryId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public void resetDailyPetFlags() {
        if (animals.isEmpty()) {
            return;
        }
        for (FarmAnimalRecord record : animals.values()) {
            record.setWasPetToday(false);
            record.setWasAutoPetToday(false);
        }
        setDirty();
    }

    public void markChanged() {
        setDirty();
    }

    private AnimalBuildingRecord requireBuilding(String buildingId) {
        AnimalBuildingRecord record = buildings.get(buildingId);
        if (record == null) {
            throw new IllegalArgumentException("Building not found: " + buildingId);
        }
        return record;
    }

    private AnimalBuildingRecord requireBuildingIncludingInactive(String buildingId) {
        AnimalBuildingRecord record = buildings.get(buildingId);
        if (record == null) {
            throw new IllegalArgumentException("Building not found: " + buildingId);
        }
        return record;
    }

    @Override
    @SuppressWarnings("null")
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        tag.putInt(
                AnimalWorldDataMigrations.VERSION_FIELD,
                AnimalWorldDataMigrations.CURRENT_VERSION);
        tag.putLong("nextBuildingId", nextBuildingId);
        tag.putLong("nextAnimalId", nextAnimalId);
        tag.putLong("nextProduceLedgerId", nextProduceLedgerId);
        tag.putLong("nextPendingBirthId", nextPendingBirthId);

        ListTag buildingList = new ListTag();
        for (AnimalBuildingRecord record : buildings.values()) {
            buildingList.add(record.save());
        }
        tag.put("buildings", buildingList);

        ListTag animalList = new ListTag();
        for (FarmAnimalRecord record : animals.values()) {
            animalList.add(record.save());
        }
        tag.put("animals", animalList);

        ListTag produceLedgerList = new ListTag();
        for (AnimalProduceLedgerEntry entry : produceLedger.values()) {
            produceLedgerList.add(entry.save());
        }
        tag.put("animalProduceLedger", produceLedgerList);

        ListTag pendingBirthList = new ListTag();
        for (AnimalPendingBirth event : pendingBirths.values()) {
            pendingBirthList.add(event.save());
        }
        tag.put("pendingAnimalBirths", pendingBirthList);

        ListTag hayList = new ListTag();
        for (Map.Entry<String, Integer> entry : hayByOwner.entrySet()) {
            CompoundTag hayTag = new CompoundTag();
            hayTag.putString("ownerPlayerUuid", entry.getKey());
            hayTag.putInt("pieces", entry.getValue());
            hayList.add(hayTag);
        }
        tag.put("hayByOwner", hayList);
        return tag;
    }

    public static AnimalWorldData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        AnimalWorldDataMigrations.MigrationResult migration =
                AnimalWorldDataMigrations.migrate(tag);
        tag = migration.tag();
        AnimalWorldData data = new AnimalWorldData();
        data.nextBuildingId = tag.contains("nextBuildingId")
                ? Math.max(1L, tag.getLong("nextBuildingId"))
                : 1L;
        data.nextAnimalId = tag.contains("nextAnimalId")
                ? Math.max(1L, tag.getLong("nextAnimalId"))
                : 1L;
        data.nextProduceLedgerId = tag.contains("nextProduceLedgerId")
                ? Math.max(1L, tag.getLong("nextProduceLedgerId"))
                : 1L;
        data.nextPendingBirthId = tag.contains("nextPendingBirthId")
                ? Math.max(1L, tag.getLong("nextPendingBirthId"))
                : 1L;

        if (tag.contains("buildings", Tag.TAG_LIST)) {
            ListTag buildingList = tag.getList("buildings", Tag.TAG_COMPOUND);
            for (int i = 0; i < buildingList.size(); i++) {
                CompoundTag buildingTag = buildingList.getCompound(i);
                AnimalBuildingRecord record = AnimalBuildingRecord.load(buildingTag);
                if (data.buildings.putIfAbsent(
                        record.buildingId(), record) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate animal building ID in save: "
                                    + record.buildingId());
                }
                data.nextBuildingId = Math.max(
                        data.nextBuildingId,
                        nextBuildingSequence(record.buildingId()));
            }
        }

        if (tag.contains("animals", Tag.TAG_LIST)) {
            ListTag animalList = tag.getList("animals", Tag.TAG_COMPOUND);
            for (int i = 0; i < animalList.size(); i++) {
                CompoundTag animalTag = animalList.getCompound(i);
                FarmAnimalRecord record = FarmAnimalRecord.load(animalTag);
                if (data.animals.putIfAbsent(
                        record.animalId(), record) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate farm animal ID in save: "
                                    + record.animalId());
                }
                data.nextAnimalId = Math.max(
                        data.nextAnimalId,
                        nextSequence(
                                record.animalId(),
                                "farm animal"));
            }
        }
        data.rebuildBuildingMembershipIndex();

        if (tag.contains("animalProduceLedger", Tag.TAG_LIST)) {
            ListTag ledgerList = tag.getList("animalProduceLedger", Tag.TAG_COMPOUND);
            for (int i = 0; i < ledgerList.size(); i++) {
                AnimalProduceLedgerEntry entry;
                try {
                    entry = AnimalProduceLedgerEntry.load(
                            ledgerList.getCompound(i));
                } catch (IllegalArgumentException ignored) {
                    // Keep the rest of the animal save usable if one ledger entry is corrupt.
                    continue;
                }
                if (data.produceLedger.putIfAbsent(
                        entry.entryId(), entry) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate animal produce ledger ID in save: "
                                    + entry.entryId());
                }
                data.nextProduceLedgerId =
                        Math.max(
                                data.nextProduceLedgerId,
                                nextSequence(
                                        entry.entryId(),
                                        "animal produce ledger"));
            }
        }

        if (tag.contains("pendingAnimalBirths", Tag.TAG_LIST)) {
            ListTag pendingBirthList =
                    tag.getList("pendingAnimalBirths", Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingBirthList.size(); i++) {
                AnimalPendingBirth event;
                try {
                    event = AnimalPendingBirth.load(
                            pendingBirthList.getCompound(i));
                } catch (IllegalArgumentException ignored) {
                    // Isolate a corrupt prompt without discarding animal/building state.
                    continue;
                }
                if (data.pendingBirths.putIfAbsent(
                        event.eventId(), event) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate pending animal birth ID in save: "
                                    + event.eventId());
                }
                data.nextPendingBirthId = Math.max(
                        data.nextPendingBirthId,
                        nextSequence(
                                event.eventId(),
                                "pending animal birth")
                );
            }
        }

        if (tag.contains("hayByOwner", Tag.TAG_LIST)) {
            ListTag hayList = tag.getList("hayByOwner", Tag.TAG_COMPOUND);
            for (int i = 0; i < hayList.size(); i++) {
                CompoundTag hayTag = hayList.getCompound(i);
                String owner = hayTag.getString("ownerPlayerUuid");
                int pieces = hayTag.getInt("pieces");
                data.hayByOwner.put(owner, Math.max(0, pieces));
            }
        }

        for (AnimalBuildingRecord record : data.buildings.values()) {
            if (!record.ownerPlayerUuid().isEmpty()) {
                data.hayByOwner.putIfAbsent(record.ownerPlayerUuid(), 0);
                data.clampHayToCapacity(record.ownerPlayerUuid());
            }
        }

        if (migration.changed()) {
            data.setDirty();
        }
        return data;
    }

    private static long nextBuildingSequence(
            String buildingId
    ) {
        if (buildingId == null) {
            return 1L;
        }
        int separator = buildingId.lastIndexOf('_');
        if (separator < 0
                || separator == buildingId.length() - 1) {
            return 1L;
        }
        try {
            long suffix = Long.parseLong(
                    buildingId.substring(separator + 1));
            return suffix < 0L
                    ? 1L
                    : nextSequence(suffix, "animal building");
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }

    private static long nextSequence(
            long current,
            String kind
    ) {
        if (current < 0L || current == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Invalid " + kind + " ID in save: " + current);
        }
        return current + 1L;
    }

    private long allocateBuildingId() {
        long allocated = nextBuildingId;
        nextBuildingId = nextSequence(
                allocated, "animal building");
        return allocated;
    }

    private long allocateAnimalId() {
        long allocated = nextAnimalId;
        nextAnimalId = nextSequence(
                allocated, "farm animal");
        return allocated;
    }

    private long allocateProduceLedgerId() {
        long allocated = nextProduceLedgerId;
        nextProduceLedgerId = nextSequence(
                allocated, "animal produce ledger");
        return allocated;
    }

    private long allocatePendingBirthId() {
        long allocated = nextPendingBirthId;
        nextPendingBirthId = nextSequence(
                allocated, "pending animal birth");
        return allocated;
    }

    private void rebuildBuildingMembershipIndex() {
        LinkedHashMap<String, LinkedHashSet<Long>> expected =
                new LinkedHashMap<>();
        for (String buildingId : buildings.keySet()) {
            expected.put(buildingId, new LinkedHashSet<>());
        }
        for (FarmAnimalRecord animal : animals.values()) {
            LinkedHashSet<Long> members =
                    expected.get(animal.buildingId());
            if (members != null) {
                members.add(animal.animalId());
            }
        }
        expected.forEach((buildingId, members) ->
                buildings.get(buildingId)
                        .replaceMemberAnimalIds(members));
    }

    private void clampHayToCapacity(String ownerUuid) {
        int capacity = 0;
        for (AnimalBuildingRecord record : buildings.values()) {
            if (ownerUuid.equals(record.ownerPlayerUuid())) {
                capacity += Math.max(0, record.hayCapacity());
            }
        }
        int current = hayByOwner.getOrDefault(ownerUuid, 0);
        if (current > capacity) {
            hayByOwner.put(ownerUuid, capacity);
        }
    }

    public static AnimalWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AnimalWorldData::new, AnimalWorldData::load),
            DATA_NAME
        );
    }
}
