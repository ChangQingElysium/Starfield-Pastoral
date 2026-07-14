package com.stardew.craft.animal.data;

import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    private final Map<String, Integer> hayByOwner = new LinkedHashMap<>();
    private long nextBuildingId = 1L;
    private long nextAnimalId = 1L;

    public String createBuilding(ServerLevel level,
                                 AnimalBuildingType buildingType,
                                 UUID ownerPlayerId,
                                 BlockPos managerPos,
                                 int range,
                                 String customName,
                                 int capacity) {
        String buildingId = buildingType.family() + "_" + nextBuildingId++;
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
        AnimalTypeCatalog.AnimalTypeSpec typeSpec = AnimalTypeCatalog.resolve(animalTypeId);
        long animalId = nextAnimalId++;
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
        if (record == null || !record.active()) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public Optional<AnimalBuildingRecord> getBuildingIncludingInactive(String buildingId) {
        return Optional.ofNullable(buildings.get(buildingId));
    }

    public Collection<AnimalBuildingRecord> getBuildings() {
        List<AnimalBuildingRecord> active = new ArrayList<>();
        for (AnimalBuildingRecord record : buildings.values()) {
            if (record.active()) {
                active.add(record);
            }
        }
        return Collections.unmodifiableList(active);
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
            true,
            existing.doorOpen(),
            Collections.emptySet(),
            Collections.emptySet(),
            new java.util.LinkedHashSet<>(existing.memberAnimalIds())
        );

        buildings.put(buildingId, moved);
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
            Collections.emptySet(),
            Collections.emptySet(),
            new java.util.LinkedHashSet<>(existing.memberAnimalIds())
        );
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

            buildings.put(existing.buildingId(), updated);
            hayByOwner.putIfAbsent(owner, 0);
            clampHayToCapacity(owner);
            setDirty();
            return existing.buildingId();
        }

        String buildingId = buildingType.family() + "_" + nextBuildingId++;
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
            if (!record.active()) {
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
            if (!record.active()) {
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
        if (source == null || target == null || !target.active()) {
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

        String animalFamily = AnimalTypeCatalog.resolve(animal.animalTypeId()).family();
        if (!animalFamily.equalsIgnoreCase(target.buildingType().family())) {
            return false;
        }
        if (!target.hasCapacity()) {
            return false;
        }

        source.removeAnimal(animalId);
        target.addAnimal(animalId);
        animal.setBuildingId(target.buildingId());
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
        tag.putLong("nextBuildingId", nextBuildingId);
        tag.putLong("nextAnimalId", nextAnimalId);

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
        AnimalWorldData data = new AnimalWorldData();
        data.nextBuildingId = tag.contains("nextBuildingId") ? tag.getLong("nextBuildingId") : 1L;
        data.nextAnimalId = tag.contains("nextAnimalId") ? tag.getLong("nextAnimalId") : 1L;

        if (tag.contains("buildings", Tag.TAG_LIST)) {
            ListTag buildingList = tag.getList("buildings", Tag.TAG_COMPOUND);
            for (int i = 0; i < buildingList.size(); i++) {
                CompoundTag buildingTag = buildingList.getCompound(i);
                AnimalBuildingRecord record = AnimalBuildingRecord.load(buildingTag);
                data.buildings.put(record.buildingId(), record);
            }
        }

        if (tag.contains("animals", Tag.TAG_LIST)) {
            ListTag animalList = tag.getList("animals", Tag.TAG_COMPOUND);
            for (int i = 0; i < animalList.size(); i++) {
                CompoundTag animalTag = animalList.getCompound(i);
                FarmAnimalRecord record = FarmAnimalRecord.load(animalTag);
                data.animals.put(record.animalId(), record);
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

        return data;
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
