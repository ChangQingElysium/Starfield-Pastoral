package com.stardew.craft.fishpond.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FishPondRecord {
    private final String pondId;
    private String ownerPlayerUuid;
    private final String dimensionId;
    private final BlockPos managerPos;
    private final BlockPos bucketPos;
    private final Set<BlockPos> netPositions;
    private final Set<Long> waterCells;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private String fishTypeId;
    private int currentPopulation;
    private int maxPopulation;
    private String outputItemId;
    private int outputCount;
    private String neededItemId;
    private int neededItemCount;
    private boolean hasCompletedRequest;
    private int lastUnlockedPopulationGate;
    private int daysSinceSpawn;
    private int waterColor;
    private int nettingStyle;
    private boolean goldenAnimalCracker;
    private boolean empty;

    public FishPondRecord(String pondId,
                          String ownerPlayerUuid,
                          String dimensionId,
                          BlockPos managerPos,
                          BlockPos bucketPos,
                          Set<BlockPos> netPositions,
                          Set<Long> waterCells,
                          int minX,
                          int minY,
                          int minZ,
                          int maxX,
                          int maxY,
                          int maxZ,
                          String fishTypeId,
                          int currentPopulation,
                          int maxPopulation,
                          String outputItemId,
                          int outputCount,
                          String neededItemId,
                          int neededItemCount,
                          boolean hasCompletedRequest,
                          int lastUnlockedPopulationGate,
                          int daysSinceSpawn,
                          int waterColor,
                          int nettingStyle,
                          boolean goldenAnimalCracker,
                          boolean empty) {
        this.pondId = pondId;
        this.ownerPlayerUuid = ownerPlayerUuid;
        this.dimensionId = dimensionId;
        this.managerPos = managerPos.immutable();
        this.bucketPos = bucketPos.immutable();
        this.netPositions = netPositions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(netPositions);
        this.waterCells = waterCells == null ? new LinkedHashSet<>() : new LinkedHashSet<>(waterCells);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.fishTypeId = fishTypeId == null ? "" : fishTypeId;
        this.currentPopulation = currentPopulation;
        this.maxPopulation = maxPopulation;
        this.outputItemId = outputItemId == null ? "" : outputItemId;
        this.outputCount = outputCount;
        this.neededItemId = neededItemId == null ? "" : neededItemId;
        this.neededItemCount = neededItemCount;
        this.hasCompletedRequest = hasCompletedRequest;
        this.lastUnlockedPopulationGate = lastUnlockedPopulationGate;
        this.daysSinceSpawn = daysSinceSpawn;
        this.waterColor = waterColor;
        this.nettingStyle = nettingStyle;
        this.goldenAnimalCracker = goldenAnimalCracker;
        this.empty = empty;
    }

    public String pondId() {
        return pondId;
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

    public String dimensionId() {
        return dimensionId;
    }

    public BlockPos managerPos() {
        return managerPos;
    }

    public BlockPos bucketPos() {
        return bucketPos;
    }

    public Set<BlockPos> netPositions() {
        return Collections.unmodifiableSet(netPositions);
    }

    public Set<Long> waterCells() {
        return Collections.unmodifiableSet(waterCells);
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

    public String fishTypeId() {
        return fishTypeId;
    }

    public void setFishTypeId(String fishTypeId) {
        this.fishTypeId = fishTypeId == null ? "" : fishTypeId;
    }

    public int currentPopulation() {
        return currentPopulation;
    }

    public void setCurrentPopulation(int currentPopulation) {
        this.currentPopulation = currentPopulation;
    }

    public int maxPopulation() {
        return maxPopulation;
    }

    public void setMaxPopulation(int maxPopulation) {
        this.maxPopulation = maxPopulation;
    }

    public String outputItemId() {
        return outputItemId;
    }

    public void setOutputItemId(String outputItemId) {
        this.outputItemId = outputItemId == null ? "" : outputItemId;
    }

    public int outputCount() {
        return outputCount;
    }

    public void setOutputCount(int outputCount) {
        this.outputCount = outputCount;
    }

    public String neededItemId() {
        return neededItemId;
    }

    public void setNeededItemId(String neededItemId) {
        this.neededItemId = neededItemId == null ? "" : neededItemId;
    }

    public int neededItemCount() {
        return neededItemCount;
    }

    public void setNeededItemCount(int neededItemCount) {
        this.neededItemCount = neededItemCount;
    }

    public boolean hasCompletedRequest() {
        return hasCompletedRequest;
    }

    public void setHasCompletedRequest(boolean hasCompletedRequest) {
        this.hasCompletedRequest = hasCompletedRequest;
    }

    public int lastUnlockedPopulationGate() {
        return lastUnlockedPopulationGate;
    }

    public void setLastUnlockedPopulationGate(int lastUnlockedPopulationGate) {
        this.lastUnlockedPopulationGate = lastUnlockedPopulationGate;
    }

    public int daysSinceSpawn() {
        return daysSinceSpawn;
    }

    public void setDaysSinceSpawn(int daysSinceSpawn) {
        this.daysSinceSpawn = daysSinceSpawn;
    }

    public int waterColor() {
        return waterColor;
    }

    public void setWaterColor(int waterColor) {
        this.waterColor = waterColor;
    }

    public int nettingStyle() {
        return nettingStyle;
    }

    public void setNettingStyle(int nettingStyle) {
        this.nettingStyle = nettingStyle;
    }

    public boolean goldenAnimalCracker() {
        return goldenAnimalCracker;
    }

    public void setGoldenAnimalCracker(boolean goldenAnimalCracker) {
        this.goldenAnimalCracker = goldenAnimalCracker;
    }

    public boolean empty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public void clearPondContents() {
        outputItemId = "";
        outputCount = 0;
        neededItemId = "";
        neededItemCount = 0;
        hasCompletedRequest = false;
        fishTypeId = "";
        currentPopulation = 0;
        maxPopulation = 0;
        daysSinceSpawn = 0;
        lastUnlockedPopulationGate = 0;
        waterColor = -1;
        empty = true;
    }

    public void cycleNettingStyle() {
        nettingStyle = (nettingStyle + 1) % 4;
    }

    public boolean containsWater(BlockPos pos) {
        return waterCells.contains(pos.asLong());
    }

    public boolean containsPosInEnvelope(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean intersectsEnvelope(int otherMinX,
                                      int otherMinY,
                                      int otherMinZ,
                                      int otherMaxX,
                                      int otherMaxY,
                                      int otherMaxZ) {
        return minX <= otherMaxX && maxX >= otherMinX
            && minY <= otherMaxY && maxY >= otherMinY
            && minZ <= otherMaxZ && maxZ >= otherMinZ;
    }

    @SuppressWarnings("null")
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("pondId", pondId);
        tag.putString("ownerPlayerUuid", ownerPlayerUuid);
        tag.putString("dimensionId", dimensionId);
        tag.put("managerPos", NbtUtils.writeBlockPos(managerPos));
        tag.put("bucketPos", NbtUtils.writeBlockPos(bucketPos));
        tag.putInt("minX", minX);
        tag.putInt("minY", minY);
        tag.putInt("minZ", minZ);
        tag.putInt("maxX", maxX);
        tag.putInt("maxY", maxY);
        tag.putInt("maxZ", maxZ);
        tag.putString("fishTypeId", fishTypeId);
        tag.putInt("currentPopulation", currentPopulation);
        tag.putInt("maxPopulation", maxPopulation);
        tag.putString("outputItemId", outputItemId);
        tag.putInt("outputCount", outputCount);
        tag.putString("neededItemId", neededItemId);
        tag.putInt("neededItemCount", neededItemCount);
        tag.putBoolean("hasCompletedRequest", hasCompletedRequest);
        tag.putInt("lastUnlockedPopulationGate", lastUnlockedPopulationGate);
        tag.putInt("daysSinceSpawn", daysSinceSpawn);
        tag.putInt("waterColor", waterColor);
        tag.putInt("nettingStyle", nettingStyle);
        tag.putBoolean("goldenAnimalCracker", goldenAnimalCracker);
        tag.putBoolean("empty", empty);

        ListTag netsTag = new ListTag();
        for (BlockPos netPos : netPositions) {
            CompoundTag netTag = new CompoundTag();
            netTag.put("Pos", NbtUtils.writeBlockPos(netPos));
            netsTag.add(netTag);
        }
        tag.put("netPositions", netsTag);

        ListTag waterTag = new ListTag();
        for (Long cell : waterCells) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("cell", cell);
            waterTag.add(cellTag);
        }
        tag.put("waterCells", waterTag);
        return tag;
    }

    public static FishPondRecord load(CompoundTag tag) {
        Set<BlockPos> netPositions = new LinkedHashSet<>();
        ListTag netsTag = tag.getList("netPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < netsTag.size(); i++) {
            netPositions.add(NbtUtils.readBlockPos(netsTag.getCompound(i), "Pos").orElse(BlockPos.ZERO).immutable());
        }

        Set<Long> waterCells = new LinkedHashSet<>();
        ListTag waterTag = tag.getList("waterCells", Tag.TAG_COMPOUND);
        for (int i = 0; i < waterTag.size(); i++) {
            waterCells.add(waterTag.getCompound(i).getLong("cell"));
        }

        return new FishPondRecord(
            tag.getString("pondId"),
            tag.getString("ownerPlayerUuid"),
            tag.getString("dimensionId"),
            NbtUtils.readBlockPos(tag, "managerPos").orElse(BlockPos.ZERO).immutable(),
            NbtUtils.readBlockPos(tag, "bucketPos").orElse(BlockPos.ZERO).immutable(),
            netPositions,
            waterCells,
            tag.getInt("minX"),
            tag.getInt("minY"),
            tag.getInt("minZ"),
            tag.getInt("maxX"),
            tag.getInt("maxY"),
            tag.getInt("maxZ"),
            tag.getString("fishTypeId"),
            tag.getInt("currentPopulation"),
            tag.getInt("maxPopulation"),
            tag.getString("outputItemId"),
            tag.getInt("outputCount"),
            tag.getString("neededItemId"),
            tag.getInt("neededItemCount"),
            tag.getBoolean("hasCompletedRequest"),
            tag.getInt("lastUnlockedPopulationGate"),
            tag.getInt("daysSinceSpawn"),
            tag.getInt("waterColor"),
            tag.getInt("nettingStyle"),
            tag.getBoolean("goldenAnimalCracker"),
            tag.getBoolean("empty")
        );
    }
}
