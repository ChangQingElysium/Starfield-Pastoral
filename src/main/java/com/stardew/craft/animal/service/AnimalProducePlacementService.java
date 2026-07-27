package com.stardew.craft.animal.service;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.AnimalProduceLedgerEntry;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.AutoFeedTroughBlock;
import com.stardew.craft.block.utility.CoopManagerBlock;
import com.stardew.craft.block.utility.FeedTroughBlock;
import com.stardew.craft.block.utility.HayHopperBlock;
import com.stardew.craft.blockentity.AutoGrabberBlockEntity;
import com.stardew.craft.blockentity.AnimalProduceSpotBlockEntity;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.manager.AnimalGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public final class AnimalProducePlacementService {
    private AnimalProducePlacementService() {
    }

    public static boolean placeInHome(ServerLevel level,
                                      AnimalWorldData data,
                                      FarmAnimalRecord record,
                                      ItemStack produceStack) {
        return submitProduce(
                level,
                data,
                record,
                StardewTimeManager.get().getAbsoluteDay(),
                produceStack,
                true
        );
    }

    /**
     * Commits products to persistent storage before attempting any world projection.
     *
     * <p>The return value means the product was durably accepted by the ledger, not that a visible
     * floor spot was available.
     */
    public static boolean submitProduce(
            ServerLevel level,
            AnimalWorldData data,
            FarmAnimalRecord record,
            int producedAbsDay,
            ItemStack produceStack,
            boolean projectNow
    ) {
        if (produceStack == null || produceStack.isEmpty()) {
            return false;
        }

        AnimalBuildingRecord building = data.getBuilding(record.buildingId()).orElse(null);
        if (building == null) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(produceStack.getItem());
        if (itemId == null || produceStack.getItem() == Items.AIR) {
            return false;
        }
        List<AnimalProduceLedgerEntry> submitted = data.submitAnimalProduce(
                building.buildingId(),
                record.animalId(),
                producedAbsDay,
                itemId,
                QualityHelper.getQuality(produceStack),
                produceStack.getCount()
        );
        if (submitted.isEmpty()) {
            return false;
        }
        if (projectNow) {
            projectPendingForBuilding(level, data, building);
        }
        return true;
    }

    public static int projectPendingForBuilding(
            ServerLevel level,
            AnimalWorldData data,
            AnimalBuildingRecord building
    ) {
        if (building == null
                || !building.dimensionId().equals(level.dimension().location().toString())) {
            return 0;
        }

        List<AutoGrabberBlockEntity> autoGrabbers = findAutoGrabbers(level, building);
        List<AnimalProduceLedgerEntry> entries =
                data.getAnimalProduceForBuilding(building.buildingId());
        Map<Long, BlockPos> existingProjections =
                indexExistingInteriorProjections(level, building);
        int resolved = 0;
        for (AnimalProduceLedgerEntry initial : entries) {
            AnimalProduceLedgerEntry entry = reconcileProjection(
                    level,
                    data,
                    building,
                    initial,
                    existingProjections
            );
            if (entry == null) {
                continue;
            }
            if (entry.isProjected()) {
                if (collectProjectedIntoAutoGrabbers(
                        level,
                        data,
                        building,
                        entry,
                        autoGrabbers
                )) {
                    resolved++;
                }
                continue;
            }

            ItemStack produce =
                    stackForLedgerEntry(entry);
            if (produce.isEmpty()) {
                continue;
            }
            if (entry.autoCollectEligible()
                    && insertFullyIntoAutoGrabbers(autoGrabbers, produce)) {
                data.completeAnimalProduce(entry.entryId());
                AutoGrabberBlockEntity.recordCollectedForOwner(
                        building.ownerPlayerUuid(),
                        1
                );
                resolved++;
                continue;
            }

            if (entry.hasPreferredAnchor()
                    && !entry.preferredDimensionId().equals(
                            level.dimension().location().toString())) {
                continue;
            }
            BlockPos targetPos = entry.hasPreferredAnchor()
                    ? findAvailableTileNear(
                            level,
                            building,
                            entry.preferredPos(),
                            entry.preferredRadius(),
                            true
                    )
                    : findAvailableTile(level, building);
            if (targetPos == null
                    || !projectEntry(level, data, entry, targetPos, produce)) {
                continue;
            }
            resolved++;
        }
        return resolved;
    }

    /**
     * Converts world projections back into durable ledger-only entries before
     * a manager is rebound. The products are not collected or deleted and can
     * be projected into the validated destination structure later.
     */
    public static int releaseProjectionsForBuildingRelocation(
            ServerLevel level,
            AnimalWorldData data,
            AnimalBuildingRecord building
    ) {
        String dimensionId =
                level.dimension().location().toString();
        int released = 0;
        for (AnimalProduceLedgerEntry entry :
                data.getAnimalProduceForBuilding(
                        building.buildingId())) {
            if (!entry.isProjected()
                    || !dimensionId.equals(
                            entry.projectedDimensionId())) {
                continue;
            }
            BlockPos pos = entry.projectedPos();
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity
                    instanceof AnimalProduceSpotBlockEntity spot
                    && spot.getProduceLedgerEntryId()
                            == entry.entryId()) {
                level.removeBlock(pos, false);
            }
            if (data.releaseAnimalProduceProjection(
                    entry.entryId(), dimensionId, pos)) {
                released++;
            }
        }
        return released;
    }

    private static boolean collectProjectedIntoAutoGrabbers(
            ServerLevel level,
            AnimalWorldData data,
            AnimalBuildingRecord building,
            AnimalProduceLedgerEntry entry,
            List<AutoGrabberBlockEntity> autoGrabbers
    ) {
        if (!entry.autoCollectEligible()
                || autoGrabbers.isEmpty()
                || !entry.projectedDimensionId().equals(
                        level.dimension().location().toString())
                || !level.isLoaded(entry.projectedPos())) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(entry.projectedPos());
        if (!(blockEntity instanceof AnimalProduceSpotBlockEntity produceSpot)
                || produceSpot.getProduceLedgerEntryId() != entry.entryId()) {
            return false;
        }
        ItemStack produce = produceSpot.getProduceStack();
        if (produce.isEmpty()
                || !insertFullyIntoAutoGrabbers(autoGrabbers, produce)) {
            return false;
        }
        if (!data.completeAnimalProduce(entry.entryId())) {
            return false;
        }
        level.removeBlock(entry.projectedPos(), false);
        AutoGrabberBlockEntity.recordCollectedForOwner(
                building.ownerPlayerUuid(),
                1
        );
        return true;
    }

    public static int collectPendingInto(
            ServerLevel level,
            AnimalWorldData data,
            AnimalBuildingRecord building,
            AutoGrabberBlockEntity autoGrabber
    ) {
        int collected = 0;
        for (AnimalProduceLedgerEntry entry
                : data.getAnimalProduceForBuilding(building.buildingId())) {
            if (entry.isProjected() || !entry.autoCollectEligible()) {
                continue;
            }
            ItemStack produce =
                    stackForLedgerEntry(entry);
            if (produce.isEmpty()) {
                continue;
            }
            ItemStack remainder = autoGrabber.insertAutomation(produce, false);
            if (!remainder.isEmpty()) {
                break;
            }
            if (data.completeAnimalProduce(entry.entryId())) {
                collected++;
            }
        }
        return collected;
    }

    /**
     * Inserts atomically across the building's grabbers; a full destination never consumes only
     * part of a doubled Golden Animal Cracker stack.
     */
    public static boolean insertFullyIntoAutoGrabbers(
            List<AutoGrabberBlockEntity> autoGrabbers,
            ItemStack stack
    ) {
        if (stack.isEmpty()) {
            return true;
        }
        if (!routeThroughAutoGrabbers(autoGrabbers, stack, true).isEmpty()) {
            return false;
        }
        return routeThroughAutoGrabbers(autoGrabbers, stack, false).isEmpty();
    }

    private static ItemStack routeThroughAutoGrabbers(
            List<AutoGrabberBlockEntity> autoGrabbers,
            ItemStack stack,
            boolean simulate
    ) {
        ItemStack remaining = stack.copy();
        for (AutoGrabberBlockEntity autoGrabber : autoGrabbers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = autoGrabber.insertAutomation(remaining, simulate);
        }
        return remaining;
    }

    public static boolean dropInHome(ServerLevel level,
                                     AnimalWorldData data,
                                     FarmAnimalRecord record,
                                     ItemStack produceStack) {
        if (produceStack == null || produceStack.isEmpty()) {
            return false;
        }
        return placeInHome(level, data, record, produceStack);
    }

    public static boolean placeNearAnimal(ServerLevel level,
                                          AnimalWorldData data,
                                          FarmAnimalRecord record,
                                          BlockPos center,
                                          ItemStack produceStack,
                                          int maxRadius) {
        if (produceStack == null || produceStack.isEmpty()) {
            return false;
        }

        AnimalBuildingRecord building = data.getBuilding(record.buildingId()).orElse(null);
        if (building == null) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                produceStack.getItem());
        if (itemId == null || produceStack.getItem() == Items.AIR) {
            return false;
        }
        List<AnimalProduceLedgerEntry> submitted =
                data.submitAnimalProduceNear(
                        building.buildingId(),
                        record.animalId(),
                        StardewTimeManager.get().getAbsoluteDay(),
                        itemId,
                        QualityHelper.getQuality(produceStack),
                        produceStack.getCount(),
                        level.dimension().location().toString(),
                        center,
                        Math.max(1, maxRadius)
                );
        if (submitted.isEmpty()) {
            return false;
        }
        projectPendingForBuilding(level, data, building);
        return true;
    }

    private static BlockPos findAvailableTile(ServerLevel level, AnimalBuildingRecord building) {
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos pos
                : AnimalBuildingPositionIndex.interiorCandidates(building)) {
            if (!level.isEmptyBlock(pos)) {
                continue;
            }
            BlockPos below = pos.below();
            if (!hasValidProduceSupport(level, below)) {
                continue;
            }
            candidates.add(pos);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(level.random.nextInt(candidates.size()));
    }

    private static List<AutoGrabberBlockEntity> findAutoGrabbers(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        return AnimalGrowthManager.get(level)
                .autoGrabbersForBuilding(level, building);
    }

    private static AnimalProduceLedgerEntry reconcileProjection(
            ServerLevel level,
            AnimalWorldData data,
            AnimalBuildingRecord building,
            AnimalProduceLedgerEntry entry,
            Map<Long, BlockPos> existingProjections
    ) {
        if (!entry.isProjected()) {
            BlockPos existing = existingProjections.get(
                    entry.entryId());
            if (existing == null && entry.hasPreferredAnchor()) {
                existing = findExistingProjectionNear(
                        level,
                        entry,
                        entry.preferredPos(),
                        entry.preferredRadius());
            }
            if (existing != null
                    && data.markAnimalProduceProjected(
                    entry.entryId(),
                    level.dimension().location().toString(),
                    existing)) {
                return data.getAnimalProduce(
                        entry.entryId()).orElse(null);
            }
            return entry;
        }
        String dimensionId = level.dimension().location().toString();
        if (!entry.projectedDimensionId().equals(dimensionId)) {
            return entry;
        }

        BlockPos pos = entry.projectedPos();
        if (!level.isLoaded(pos)) {
            return entry;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnimalProduceSpotBlockEntity produceSpot
                && produceSpot.getProduceLedgerEntryId() == entry.entryId()) {
            return entry;
        }
        if (blockEntity instanceof AnimalProduceSpotBlockEntity produceSpot
                && produceSpot.getProduceLedgerEntryId() <= 0L
                && produceSpot.getAnimalId() == entry.animalId()
                && produceSpot.getBuildingId().equals(entry.buildingId())
                && ItemStack.isSameItemSameComponents(
                        produceSpot.getProduceStack(),
                        stackForLedgerEntry(entry))) {
            // Recover the narrow crash window where the chunk saved its projection before the
            // block entity received the ledger ID.
            produceSpot.setProduceLedgerEntryId(entry.entryId());
            return entry;
        }
        data.releaseAnimalProduceProjection(entry.entryId(), dimensionId, pos);
        return data.getAnimalProduce(entry.entryId()).orElse(null);
    }

    private static boolean projectEntry(
            ServerLevel level,
            AnimalWorldData data,
            AnimalProduceLedgerEntry entry,
            BlockPos targetPos,
            ItemStack produce
    ) {
        String dimensionId =
                level.dimension().location().toString();
        if (!data.markAnimalProduceProjected(
                entry.entryId(),
                dimensionId,
                targetPos)) {
            return false;
        }

        boolean blockPlaced = false;
        boolean projected = false;
        BlockState state = ModBlocks.ANIMAL_PRODUCE_SPOT.get().defaultBlockState();
        try {
            if (!level.setBlock(targetPos, state, 3)) {
                return false;
            }
            blockPlaced = true;
            BlockEntity blockEntity =
                    level.getBlockEntity(targetPos);
            if (!(blockEntity
                    instanceof AnimalProduceSpotBlockEntity produceSpot)) {
                return false;
            }

            produceSpot.setProduceStack(produce);
            produceSpot.setAnimalId(entry.animalId());
            produceSpot.setBuildingId(entry.buildingId());
            produceSpot.setProduceLedgerEntryId(
                    entry.entryId());
            produceSpot.setChanged();
            projected = true;
            return true;
        } finally {
            if (!projected) {
                if (blockPlaced) {
                    level.removeBlock(targetPos, false);
                }
                data.releaseAnimalProduceProjection(
                        entry.entryId(),
                        dimensionId,
                        targetPos);
            }
        }
    }

    private static Map<Long, BlockPos>
    indexExistingInteriorProjections(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        Map<Long, BlockPos> indexed = new HashMap<>();
        for (BlockPos pos :
                AnimalBuildingPositionIndex.interiorCandidates(
                        building)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity
                    instanceof AnimalProduceSpotBlockEntity spot
                    && spot.getProduceLedgerEntryId() > 0L) {
                indexed.putIfAbsent(
                        spot.getProduceLedgerEntryId(),
                        pos.immutable());
            }
        }
        return indexed;
    }

    private static BlockPos findExistingProjectionNear(
            ServerLevel level,
            AnimalProduceLedgerEntry entry,
            BlockPos center,
            int radius
    ) {
        int boundedRadius = Math.max(1, radius);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -boundedRadius;
                 dz <= boundedRadius;
                 dz++) {
                for (int dx = -boundedRadius;
                     dx <= boundedRadius;
                     dx++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockEntity blockEntity =
                            level.getBlockEntity(pos);
                    if (blockEntity
                            instanceof AnimalProduceSpotBlockEntity spot
                            && spot.getProduceLedgerEntryId()
                            == entry.entryId()) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    public static ItemStack stackForLedgerEntry(
            AnimalProduceLedgerEntry entry
    ) {
        Item item = BuiltInRegistries.ITEM.get(entry.itemId());
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        QualityHelper.setQuality(stack, entry.quality());
        return stack;
    }

    private static BlockPos findAvailableTileNear(ServerLevel level,
                                                  AnimalBuildingRecord building,
                                                  BlockPos center,
                                                  int maxRadius,
                                                  boolean allowOutdoorNearManager) {
        List<BlockPos> candidates = new ArrayList<>();
        int managerRangeSq = (building.range() + 2) * (building.range() + 2);

        for (int dy = -1; dy <= 1; dy++) {
            int y = center.getY() + dy;
            if (!allowOutdoorNearManager && (y < building.minY() + 1 || y > building.maxY())) {
                continue;
            }

            for (int radius = 1; radius <= maxRadius; radius++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }

                        BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        boolean insideHome = building.isInBounds(pos);
                        boolean nearManager = pos.distSqr(building.managerPos()) <= managerRangeSq;
                        if (!insideHome && !(allowOutdoorNearManager && nearManager)) {
                            continue;
                        }
                        BlockPos below = pos.below();
                        if (!level.isEmptyBlock(pos)) {
                            continue;
                        }
                        if (!hasValidProduceSupport(level, below)) {
                            continue;
                        }
                        if (isDisallowedSupportBlock(level.getBlockState(below).getBlock())) {
                            continue;
                        }
                        candidates.add(pos.immutable());
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(level.random.nextInt(candidates.size()));
    }

    private static boolean hasValidProduceSupport(ServerLevel level, BlockPos supportPos) {
        BlockState supportState = level.getBlockState(supportPos);
        if (isDisallowedSupportBlock(supportState.getBlock())) {
            return false;
        }
        return !supportState.isAir() && !supportState.getCollisionShape(level, supportPos).isEmpty();
    }

    private static boolean isDisallowedSupportBlock(Block block) {
        if (block instanceof FeedTroughBlock
            || block instanceof AutoFeedTroughBlock
            || block instanceof HayHopperBlock
            || block instanceof CoopManagerBlock) {
            return true;
        }

        String simple = block.getClass().getSimpleName();
        return simple.endsWith("ManagerBlock") || simple.contains("Manager");
    }
}
