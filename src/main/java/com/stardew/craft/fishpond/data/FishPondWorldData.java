package com.stardew.craft.fishpond.data;

import com.stardew.craft.fishpond.model.FishPondRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FishPondWorldData extends SavedData {
    private static final String DATA_NAME = "stardew_fish_pond_world";

    private final Map<String, FishPondRecord> ponds = new LinkedHashMap<>();
    private long nextPondId = 1L;

    public String createOrUpdatePondAtManager(ServerLevel level,
                                              UUID ownerPlayerId,
                                              BlockPos managerPos,
                                              BlockPos bucketPos,
                                              java.util.Set<BlockPos> netPositions,
                                              java.util.Set<Long> waterCells,
                                              int minX,
                                              int minY,
                                              int minZ,
                                              int maxX,
                                              int maxY,
                                              int maxZ) {
        String dimensionId = level.dimension().location().toString();
        Optional<FishPondRecord> existingOpt = findPondByManager(dimensionId, ownerPlayerId, managerPos);

        if (existingOpt.isPresent()) {
            FishPondRecord existing = existingOpt.get();
            FishPondRecord updated = new FishPondRecord(
                existing.pondId(),
                existing.ownerPlayerUuid(),
                dimensionId,
                managerPos,
                bucketPos,
                netPositions,
                waterCells,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                existing.fishTypeId(),
                existing.currentPopulation(),
                existing.maxPopulation(),
                existing.outputItemId(),
                existing.outputCount(),
                existing.neededItemId(),
                existing.neededItemCount(),
                existing.hasCompletedRequest(),
                existing.lastUnlockedPopulationGate(),
                existing.daysSinceSpawn(),
                existing.waterColor(),
                existing.nettingStyle(),
                existing.goldenAnimalCracker(),
                existing.empty()
            );
            ponds.put(existing.pondId(), updated);
            setDirty();
            return existing.pondId();
        }

        return createPond(
            ownerPlayerId,
            dimensionId,
            managerPos,
            bucketPos,
            netPositions,
            waterCells,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ
        );
    }

    public String createPond(UUID ownerPlayerId,
                             String dimensionId,
                             BlockPos managerPos,
                             BlockPos bucketPos,
                             java.util.Set<BlockPos> netPositions,
                             java.util.Set<Long> waterCells,
                             int minX,
                             int minY,
                             int minZ,
                             int maxX,
                             int maxY,
                             int maxZ) {
        String pondId = "fish_pond_" + nextPondId++;
        FishPondRecord record = new FishPondRecord(
            pondId,
            ownerPlayerId.toString(),
            dimensionId,
            managerPos,
            bucketPos,
            netPositions,
            waterCells,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            "",
            0,
            0,
            "",
            0,
            "",
            0,
            false,
            0,
            0,
            -1,
            0,
            false,
            true
        );
        ponds.put(pondId, record);
        setDirty();
        return pondId;
    }

    /** Preserve stock, requests, produce and crackers while the prefab journal moves its geometry. */
    public void movePrefab(com.stardew.craft.building.runtime.BuildingRecord before,
                           com.stardew.craft.building.runtime.BuildingRecord after) {
        var existing=findPondByManagerAnyOwner(before.dimension().toString(),before.manager()).orElse(null);
        if(existing==null)return; // A durable transfer replay may already have moved the record.
        var tag=existing.save();
        java.util.function.Function<BlockPos,BlockPos> move=pos->com.stardew.craft.building.runtime.BuildingTransfer.destination(before,after,pos);
        tag.put("managerPos",com.stardew.craft.port.PortNbtUtils.writeBlockPos(after.manager()));
        tag.put("bucketPos",com.stardew.craft.port.PortNbtUtils.writeBlockPos(move.apply(existing.bucketPos())));
        var nets=new net.minecraft.nbt.ListTag();
        for(var pos:existing.netPositions()){var cell=new net.minecraft.nbt.CompoundTag();cell.put("Pos",com.stardew.craft.port.PortNbtUtils.writeBlockPos(move.apply(pos)));nets.add(cell);}
        tag.put("netPositions",nets);
        var water=new net.minecraft.nbt.ListTag();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(long packed:existing.waterCells()) {
            var pos=move.apply(BlockPos.of(packed));var cell=new net.minecraft.nbt.CompoundTag();cell.putLong("cell",pos.asLong());water.add(cell);
            minX=Math.min(minX,pos.getX());minY=Math.min(minY,pos.getY());minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX());maxY=Math.max(maxY,pos.getY());maxZ=Math.max(maxZ,pos.getZ());
        }
        tag.put("waterCells",water);tag.putInt("minX",minX);tag.putInt("minY",minY);tag.putInt("minZ",minZ);
        tag.putInt("maxX",maxX);tag.putInt("maxY",maxY);tag.putInt("maxZ",maxZ);
        ponds.put(existing.pondId(),FishPondRecord.load(tag));setDirty();
    }

    public Optional<FishPondRecord> getPond(String pondId) {
        return Optional.ofNullable(ponds.get(pondId));
    }

    public Collection<FishPondRecord> getPonds() {
        return Collections.unmodifiableCollection(new ArrayList<>(ponds.values()));
    }

    /** Repairs legacy ponds that were assigned to the player who clicked Build. */
    public int reconcileFarmOwnership(ServerLevel level) {
        return reconcileFarmOwnership(
                level.dimension().location().toString(),
                pos -> com.stardew.craft.core.FarmAreaResolver.getOwnerAt(pos));
    }

    int reconcileFarmOwnership(
            String dimensionId,
            java.util.function.Function<BlockPos, UUID> ownerAt
    ) {
        int repaired = 0;
        for (FishPondRecord pond : ponds.values()) {
            if (!dimensionId.equals(pond.dimensionId())) continue;
            UUID farmOwner = ownerAt.apply(pond.managerPos());
            if (farmOwner == null
                    || farmOwner.toString().equals(pond.ownerPlayerUuid())) {
                continue;
            }
            pond.setOwnerPlayerUuid(farmOwner.toString());
            repaired++;
        }
        if (repaired > 0) {
            setDirty();
        }
        return repaired;
    }

    public Optional<FishPondRecord> findPondByManager(String dimensionId, UUID ownerPlayerId, BlockPos managerPos) {
        String owner = ownerPlayerId.toString();
        for (FishPondRecord record : ponds.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (!owner.equals(record.ownerPlayerUuid())) {
                continue;
            }
            if (!managerPos.equals(record.managerPos())) {
                continue;
            }
            return Optional.of(record);
        }
        return Optional.empty();
    }

    public Optional<FishPondRecord> findPondByManagerAnyOwner(String dimensionId, BlockPos managerPos) {
        for (FishPondRecord record : ponds.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (!managerPos.equals(record.managerPos())) {
                continue;
            }
            return Optional.of(record);
        }
        return Optional.empty();
    }

    public Optional<FishPondRecord> findPondContainingWater(String dimensionId, BlockPos pos) {
        for (FishPondRecord record : ponds.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (record.containsWater(pos)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Optional<FishPondRecord> findPondByBucket(String dimensionId, BlockPos bucketPos) {
        for (FishPondRecord record : ponds.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (bucketPos.equals(record.bucketPos())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Optional<FishPondRecord> findPondProtectingPos(String dimensionId, BlockPos pos) {
        for (FishPondRecord record : ponds.values()) {
            if (!dimensionId.equals(record.dimensionId())) {
                continue;
            }
            if (record.managerPos().equals(pos)) {
                return Optional.of(record);
            }
            if (record.bucketPos().equals(pos)) {
                return Optional.of(record);
            }
            if (record.netPositions().contains(pos)) {
                return Optional.of(record);
            }
            if (record.containsWater(pos)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Optional<FishPondRecord> removePondByManager(String dimensionId, UUID ownerPlayerId, BlockPos managerPos) {
        Optional<FishPondRecord> existing = findPondByManager(dimensionId, ownerPlayerId, managerPos);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        ponds.remove(existing.get().pondId());
        setDirty();
        return existing;
    }

    public boolean removePond(String pondId) {
        if (ponds.remove(pondId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public void markChanged() {
        setDirty();
    }

    @Override
    @SuppressWarnings("null")
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        tag.putLong("nextPondId", nextPondId);

        ListTag pondsTag = new ListTag();
        for (FishPondRecord record : ponds.values()) {
            pondsTag.add(record.save());
        }
        tag.put("ponds", pondsTag);
        return tag;
    }

    public static FishPondWorldData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        FishPondWorldData data = new FishPondWorldData();
        data.nextPondId = tag.contains("nextPondId") ? tag.getLong("nextPondId") : 1L;
        if (tag.contains("ponds", Tag.TAG_LIST)) {
            ListTag pondsTag = tag.getList("ponds", Tag.TAG_COMPOUND);
            for (int i = 0; i < pondsTag.size(); i++) {
                FishPondRecord record = FishPondRecord.load(pondsTag.getCompound(i));
                data.ponds.put(record.pondId(), record);
            }
        }
        return data;
    }

    public static FishPondWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(FishPondWorldData::new, FishPondWorldData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(FishPondWorldData::new, FishPondWorldData::load)),
            DATA_NAME
        );
    }
}
