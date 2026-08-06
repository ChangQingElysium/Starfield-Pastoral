package com.stardew.craft.animal.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class AnimalBuildingRecord {
    private final String buildingId;
    private String ownerPlayerUuid;
    private final AnimalBuildingType buildingType;
    private String customName;
    private final String dimensionId;
    private final BlockPos managerPos;
    private final int range;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int capacity;
    private final int hayCapacity;
    private final boolean active;
    private boolean doorOpen;
    private int lastAutoFeedProcessedAbsDay = -1;
    private long structureRevision = 1L;
    private ValidationState validationState;
    private String validationIssue = "";
    private final Set<Long> interiorAirCells;
    private final Set<Long> boundaryDoorCells;
    private final Set<Long> memberAnimalIds;

    public AnimalBuildingRecord(String buildingId,
                                String ownerPlayerUuid,
                                AnimalBuildingType buildingType,
                                String customName,
                                String dimensionId,
                                BlockPos managerPos,
                                int range,
                                int minX,
                                int minY,
                                int minZ,
                                int maxX,
                                int maxY,
                                int maxZ,
                                int capacity,
                                int hayCapacity,
                                boolean active,
                                boolean doorOpen,
                                Set<Long> interiorAirCells,
                                Set<Long> boundaryDoorCells,
                                Set<Long> memberAnimalIds) {
        this.buildingId = buildingId;
        this.ownerPlayerUuid = ownerPlayerUuid;
        this.buildingType = buildingType;
        this.customName = customName;
        this.dimensionId = dimensionId;
        this.managerPos = managerPos;
        this.range = range;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.capacity = capacity;
        this.hayCapacity = hayCapacity;
        this.active = active;
        this.validationState = active
                ? ValidationState.VALID
                : ValidationState.RELOCATING;
        this.doorOpen = doorOpen;
        this.interiorAirCells = interiorAirCells == null ? new LinkedHashSet<>() : new LinkedHashSet<>(interiorAirCells);
        this.boundaryDoorCells = boundaryDoorCells == null ? new LinkedHashSet<>() : new LinkedHashSet<>(boundaryDoorCells);
        this.memberAnimalIds = memberAnimalIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(memberAnimalIds);
    }

    public String buildingId() {
        return buildingId;
    }

    public String ownerPlayerUuid() {
        return ownerPlayerUuid;
    }

    public void setOwnerPlayerUuid(String ownerPlayerUuid) {
        if (ownerPlayerUuid == null || ownerPlayerUuid.isBlank()) {
            throw new IllegalArgumentException("ownerPlayerUuid must not be blank");
        }
        this.ownerPlayerUuid = ownerPlayerUuid;
    }

    public AnimalBuildingType buildingType() {
        return buildingType;
    }

    public String customName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public BlockPos managerPos() {
        return managerPos;
    }

    public int range() {
        return range;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int capacity() {
        return capacity;
    }

    public int hayCapacity() {
        return hayCapacity;
    }

    public boolean active() {
        return active;
    }

    public boolean isGameplayEnabled() {
        return active && validationState == ValidationState.VALID;
    }

    public AnimalBuildingCapabilities capabilities() {
        return AnimalBuildingCapabilities.from(this);
    }

    public long structureRevision() {
        return structureRevision;
    }

    public ValidationState validationState() {
        return validationState;
    }

    public String validationIssue() {
        return validationIssue;
    }

    public void markStructureValidated(long revision) {
        structureRevision = Math.max(1L, revision);
        validationState = ValidationState.VALID;
        validationIssue = "";
    }

    public void markStructureInvalid(String issue) {
        structureRevision = Math.max(
                1L, structureRevision + 1L);
        validationState = ValidationState.INVALID;
        validationIssue = issue == null ? "" : issue;
    }

    public void markRelocating() {
        validationState = ValidationState.RELOCATING;
        validationIssue = "";
    }

    public boolean doorOpen() {
        return doorOpen;
    }

    public void setDoorOpen(boolean doorOpen) {
        this.doorOpen = doorOpen;
    }

    public int lastAutoFeedProcessedAbsDay() {
        return lastAutoFeedProcessedAbsDay;
    }

    public void setLastAutoFeedProcessedAbsDay(int absoluteDay) {
        lastAutoFeedProcessedAbsDay = absoluteDay;
    }

    public Set<Long> memberAnimalIds() {
        return Collections.unmodifiableSet(memberAnimalIds);
    }

    public Set<Long> interiorAirCells() {
        return Collections.unmodifiableSet(interiorAirCells);
    }

    public Set<Long> boundaryDoorCells() {
        return Collections.unmodifiableSet(boundaryDoorCells);
    }

    public boolean isInBounds(BlockPos pos) {
        if (!interiorAirCells.isEmpty()) {
            return interiorAirCells.contains(pos.asLong());
        }
        return isWithinBoundingBox(pos);
    }

    public boolean isWithinBoundingBox(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    /**
     * The utility index inspects the validated interior and its immediately
     * adjacent cells. A block change in that volume must evict the daily
     * utility snapshot even when it is not a structural change.
     */
    public boolean isUtilityScanCell(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= minX - 1 && x <= maxX + 1
                && y >= minY - 1 && y <= maxY + 1
                && z >= minZ - 1 && z <= maxZ + 1;
    }

    public boolean isBoundaryDoor(BlockPos pos) {
        if (!boundaryDoorCells.isEmpty()) {
            return boundaryDoorCells.contains(pos.asLong());
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= minX - 1 && x <= maxX + 1
            && y >= minY - 1 && y <= maxY + 1
            && z >= minZ - 1 && z <= maxZ + 1;
    }

    public boolean isStructuralCell(BlockPos pos) {
        if (boundaryDoorCells.contains(pos.asLong())) {
            return true;
        }
        if (interiorAirCells.isEmpty()) {
            return isWithinBoundingBox(pos);
        }
        if (interiorAirCells.contains(pos.asLong())) {
            return false;
        }
        if (isWithinBoundingBox(pos)) {
            return true;
        }
        for (net.minecraft.core.Direction direction :
                net.minecraft.core.Direction.values()) {
            if (interiorAirCells.contains(
                    pos.relative(direction).asLong())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCapacity() {
        return memberAnimalIds.size() < capacity;
    }

    public void addAnimal(long animalId) {
        memberAnimalIds.add(animalId);
    }

    public void removeAnimal(long animalId) {
        memberAnimalIds.remove(animalId);
    }

    public void replaceMemberAnimalIds(
            java.util.Collection<Long> animalIds
    ) {
        memberAnimalIds.clear();
        if (animalIds != null) {
            memberAnimalIds.addAll(animalIds);
        }
    }

    @SuppressWarnings("null")
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("buildingId", buildingId);
        tag.putString("ownerPlayerUuid", ownerPlayerUuid);
        tag.putString("buildingType", buildingType.id());
        tag.putString("customName", customName);
        tag.putString("dimensionId", dimensionId);
        tag.put("managerPos", NbtUtils.writeBlockPos(managerPos));
        tag.putInt("range", range);
        tag.putInt("minX", minX);
        tag.putInt("minY", minY);
        tag.putInt("minZ", minZ);
        tag.putInt("maxX", maxX);
        tag.putInt("maxY", maxY);
        tag.putInt("maxZ", maxZ);
        tag.putInt("capacity", capacity);
        tag.putInt("hayCapacity", hayCapacity);
        tag.putBoolean("active", active);
        tag.putBoolean("doorOpen", doorOpen);
        tag.putInt("lastAutoFeedProcessedAbsDay", lastAutoFeedProcessedAbsDay);
        tag.putLong("structureRevision", structureRevision);
        tag.putString("validationState", validationState.name());
        if (!validationIssue.isBlank()) {
            tag.putString("validationIssue", validationIssue);
        }
        ListTag interiorTag = new ListTag();
        for (Long cell : interiorAirCells) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("cell", cell);
            interiorTag.add(cellTag);
        }
        tag.put("interiorAirCells", interiorTag);

        ListTag doorTag = new ListTag();
        for (Long door : boundaryDoorCells) {
            CompoundTag doorCellTag = new CompoundTag();
            doorCellTag.putLong("door", door);
            doorTag.add(doorCellTag);
        }
        tag.put("boundaryDoorCells", doorTag);

        ListTag membersTag = new ListTag();
        for (Long memberAnimalId : memberAnimalIds) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putLong("animalId", memberAnimalId);
            membersTag.add(memberTag);
        }
        tag.put("memberAnimalIds", membersTag);

        return tag;
    }

    public static AnimalBuildingRecord load(CompoundTag tag) {
        Set<Long> interiorAirCells = new LinkedHashSet<>();
        ListTag interiorTag = tag.getList("interiorAirCells", Tag.TAG_COMPOUND);
        for (int i = 0; i < interiorTag.size(); i++) {
            CompoundTag cellTag = interiorTag.getCompound(i);
            interiorAirCells.add(cellTag.getLong("cell"));
        }

        Set<Long> boundaryDoorCells = new LinkedHashSet<>();
        ListTag doorTag = tag.getList("boundaryDoorCells", Tag.TAG_COMPOUND);
        for (int i = 0; i < doorTag.size(); i++) {
            CompoundTag doorCellTag = doorTag.getCompound(i);
            boundaryDoorCells.add(doorCellTag.getLong("door"));
        }

        Set<Long> memberIds = new LinkedHashSet<>();
        ListTag membersTag = tag.getList("memberAnimalIds", Tag.TAG_COMPOUND);
        for (int i = 0; i < membersTag.size(); i++) {
            CompoundTag memberTag = membersTag.getCompound(i);
            memberIds.add(memberTag.getLong("animalId"));
        }

        AnimalBuildingRecord record = new AnimalBuildingRecord(
            tag.getString("buildingId"),
            tag.contains("ownerPlayerUuid") ? tag.getString("ownerPlayerUuid") : "",
            AnimalBuildingType.fromId(tag.getString("buildingType")),
            tag.getString("customName"),
            tag.getString("dimensionId"),
            NbtUtils.readBlockPos(tag, "managerPos").orElse(BlockPos.ZERO),
            tag.getInt("range"),
            tag.getInt("minX"),
            tag.getInt("minY"),
            tag.getInt("minZ"),
            tag.getInt("maxX"),
            tag.getInt("maxY"),
            tag.getInt("maxZ"),
            tag.getInt("capacity"),
            tag.contains("hayCapacity") ? tag.getInt("hayCapacity") : 0,
            !tag.contains("active") || tag.getBoolean("active"),
            tag.getBoolean("doorOpen"),
            interiorAirCells,
            boundaryDoorCells,
            memberIds
        );
        record.setLastAutoFeedProcessedAbsDay(
                tag.contains("lastAutoFeedProcessedAbsDay")
                        ? tag.getInt("lastAutoFeedProcessedAbsDay")
                        : -1
        );
        record.structureRevision = tag.contains("structureRevision")
                ? Math.max(1L, tag.getLong("structureRevision"))
                : 1L;
        if (tag.contains("validationState")) {
            String savedState = tag.getString("validationState");
            try {
                // Compatibility with saves produced while construction delays existed. A
                // physically validated building is immediately usable in the current rules.
                record.validationState = "CONSTRUCTING".equals(savedState)
                        ? (record.active
                                ? ValidationState.VALID
                                : ValidationState.RELOCATING)
                        : ValidationState.valueOf(savedState);
            } catch (IllegalArgumentException ignored) {
                record.validationState = record.active
                        ? ValidationState.VALID
                        : ValidationState.RELOCATING;
            }
        }
        record.validationIssue = tag.getString("validationIssue");
        return record;
    }

    public enum ValidationState {
        VALID,
        INVALID,
        RELOCATING
    }
}
