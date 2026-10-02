package com.stardew.craft.greenhouse;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.BuildingBounds;
import com.stardew.craft.building.runtime.BuildingProtection;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Exterior greenhouse integration and versioned old-farm migration. */
public final class GreenhouseBuildings {
    public static final ResourceLocation FAMILY =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "greenhouse");
    private static final ResourceLocation MIGRATION =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "greenhouse_building");
    private static final ResourceLocation PORTAL_RECOVERY =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "greenhouse_portal_recovery");
    private static final ResourceLocation RUINS_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_buildings/greenhouse_ruins");
    private static final ResourceLocation LEGACY_REPAIRED_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                    "farm_buildings/greenhouse_legacy_refurbished");
    private static final int EXTERIOR_VERSION = 1;
    private static final BlockPos MANAGER = new BlockPos(12, 1, 11);
    /** Authored oak door lower half. The trigger must never replace this block. */
    private static final BlockPos DOOR = new BlockPos(7, 1, 11);
    /** One block in front of the authored door, where the player enters. */
    private static final BlockPos PORTAL = new BlockPos(7, 1, 12);
    private static final BlockPos EXIT = PORTAL;
    private static final BlockPos BUILT_SIZE = new BlockPos(15, 11, 13);
    private static final BlockPos RUINS_SIZE = new BlockPos(15, 10, 13);
    private static final BlockPos LEGACY_REPAIRED_SIZE = new BlockPos(15, 9, 17);
    private static boolean bootstrapped;

    private GreenhouseBuildings() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        StardewFarmInitializationSteps.register(
                MIGRATION, EXTERIOR_VERSION, 100,
                StardewFarmInitializationSteps.FailurePolicy.STOP,
                context -> ensureCurrent(context.level(), context.farm().ownerUuid()));
        StardewFarmInitializationSteps.register(
                PORTAL_RECOVERY, 3, 101,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> recoverPortal(context.level(), context.farm().ownerUuid()));
    }

    /** New farms and old saves share the same authoritative installation path. */
    public static void ensureCurrent(ServerLevel level, UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm == null) return;
        GreenhouseManager manager = GreenhouseManager.get(level);
        boolean repaired = manager.isRepairedForPlayer(owner)
                || com.stardew.craft.communitycenter.state.CommunityCenterSavedData
                        .get(level).isAreaComplete(owner, 0);
        BuildingRecord existing = findForFarm(level, farm.getInstanceId());
        if (existing != null && existing.phase() == BuildingRecord.Phase.READY) {
            // A completed prefab is stronger evidence than the historical per-player
            // flags. This handles UUID changes and saves loaded before CC state.
            ensurePortal(level, existing);
            migrateInteriorSoil(level, owner);
            return;
        }
        if (manager.exteriorVersion(owner) >= EXTERIOR_VERSION) {
            BuildingRecord record = existing;
            // A READY building record is authoritative even when an older save
            // has a stale/mismatched greenhouse-manager owner UUID.
            if (record != null) ensurePortal(level, record);
            else if (repaired) installRepaired(level, farm, true);
            migrateInteriorSoil(level, owner);
            return;
        }
        if (manager.hasLegacyExterior(owner)) clearLegacyExterior(level, farm);
        if (repaired) installRepaired(level, farm, true);
        else placeRuins(level, farm);
        manager.markExteriorCurrent(owner, repaired);
        migrateInteriorSoil(level, owner);
        StardewCraft.LOGGER.info("[GREENHOUSE] {} greenhouse exterior migrated for {}",
                repaired ? "Repaired" : "Ruined", owner);
    }

    public static void ensurePlaced(ServerLevel level, UUID owner) {
        ensureCurrent(level, owner);
    }

    /** Reassert only the outdoor trigger for repaired greenhouses in existing farms. */
    private static void recoverPortal(ServerLevel level, UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm == null) return;
        BuildingRecord record = findForFarm(level, farm.getInstanceId());
        // Do not gate recovery on the manager's repaired map: old saves can
        // contain a valid READY building under a different historical UUID.
        if (record != null) {
            ensurePortal(level, record);
            return;
        }
        GreenhouseManager manager = GreenhouseManager.get(level);
        boolean repaired = manager.isRepairedForPlayer(owner)
                || com.stardew.craft.communitycenter.state.CommunityCenterSavedData
                        .get(level).isAreaComplete(owner, 0);
        if (!repaired) return;
        ensureCurrent(level, owner);
    }

    public static void repair(ServerLevel level, UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm == null) return;
        installRepaired(level, farm, false);
        GreenhouseManager.get(level).markExteriorCurrent(owner, true);
        migrateInteriorSoil(level, owner);
    }

    private static void placeRuins(ServerLevel level, FarmInstance farm) {
        if (findForFarm(level, farm.getInstanceId()) != null) return;
        var tier = ruinsTier();
        preload(level, PrefabDefinitions.transform(tier.bounds(), farm.getGreenhousePos(), Rotation.NONE));
        project(level, tier, farm.getGreenhousePos(), Direction.SOUTH);
    }

    private static void installRepaired(ServerLevel level, FarmInstance farm, boolean migration) {
        BuildingRecord existing = findForFarm(level, farm.getInstanceId());
        if (existing != null) {
            ensurePortal(level, existing);
            return;
        }
        var family = PrefabDefinitions.get(FAMILY);
        var tier = family.tier(1);
        BlockPos anchor = farm.getGreenhousePos();
        Rotation rotation = PrefabDefinitions.rotation(Direction.SOUTH);
        BuildingBounds claim = PrefabDefinitions.transform(family.reservation(), anchor, rotation);
        BlockPos manager = PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, rotation);
        preload(level, claim);
        BuildingRecord candidate = new BuildingRecord(
                UUID.randomUUID(), farm.getInstanceId(), farm.getSlotIndex(), FAMILY,
                BuildingRecord.Mode.PREFAB, level.dimension().location(), anchor, manager,
                Direction.SOUTH, claim, BuildingRecord.Phase.READY, 1,
                BuildingRecord.Residence.VALID, 0, "");
        var data = BuildingWorldData.get(level.getServer());
        String source = "greenhouse:" + farm.getInstanceId();
        var result = data.importCompletedPrefab(source, candidate);
        if (result != BuildingWorldData.Result.SUCCESS) {
            throw new IllegalStateException("Could not register greenhouse " + farm.getInstanceId()
                    + ": " + result);
        }
        BuildingRecord record = findForFarm(level, farm.getInstanceId());
        if (record == null) throw new IllegalStateException("Greenhouse registration disappeared");
        project(level, tier, record.anchor(), record.facing());
        ensurePortal(level, record);
        com.stardew.craft.building.runtime.BuildingPlacementService.publishManager(level, record);
        if (migration) {
            StardewCraft.LOGGER.info("[GREENHOUSE] Replaced legacy exterior for farm {} at {}",
                    farm.getInstanceId(), record.anchor());
        }
    }

    private static PrefabDefinitions.Tier ruinsTier() {
        return new PrefabDefinitions.Tier(
                1, RUINS_STRUCTURE, RUINS_SIZE, BlockPos.ZERO, MANAGER,
                PORTAL, new BuildingBounds(BlockPos.ZERO, RUINS_SIZE),
                new PrefabDefinitions.Facilities(0, 0, 0, 0),
                new PrefabDefinitions.Upgrade(0, 0, 0, 2));
    }

    private static PrefabDefinitions.Tier legacyRepairedTier() {
        return new PrefabDefinitions.Tier(
                1, LEGACY_REPAIRED_STRUCTURE, LEGACY_REPAIRED_SIZE, BlockPos.ZERO,
                BlockPos.ZERO, BlockPos.ZERO,
                new BuildingBounds(BlockPos.ZERO, LEGACY_REPAIRED_SIZE),
                new PrefabDefinitions.Facilities(0, 0, 0, 0),
                new PrefabDefinitions.Upgrade(0, 0, 0, 2));
    }

    /** Old exteriors were projected CW90; clear their complete volumes before writing the new south-facing asset. */
    private static void clearLegacyExterior(ServerLevel level, FarmInstance farm) {
        BlockPos origin = farm.getGreenhousePos();
        preload(level, new BuildingBounds(origin,
                origin.offset(LEGACY_REPAIRED_SIZE.getZ(), LEGACY_REPAIRED_SIZE.getY(),
                        LEGACY_REPAIRED_SIZE.getX())));
        InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(
                level, origin.offset(8, 0, 0));
        BuildingProtection.internal(() -> {
            clearLegacyTier(level, origin, ruinsTier());
            clearLegacyTier(level, origin, legacyRepairedTier());
        });
    }

    private static void clearLegacyTier(
            ServerLevel level, BlockPos origin, PrefabDefinitions.Tier tier) {
        for (var cell : PrefabDefinitions.template(level, tier).cells()) {
            if (cell.state().isAir()) continue;
            BlockPos local = cell.pos();
            BlockPos pos = origin.offset(
                    tier.size().getZ() - 1 - local.getZ(), local.getY(), local.getX());
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                            | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    private static void project(
            ServerLevel level, PrefabDefinitions.Tier tier, BlockPos anchor, Direction facing) {
        var template = PrefabDefinitions.template(level, tier);
        Rotation rotation = PrefabDefinitions.rotation(facing);
        BuildingProtection.internal(() -> {
            for (var cell : template.cells()) {
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), anchor, rotation);
                level.setBlock(pos, cell.state().rotate(rotation),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                                | Block.UPDATE_SUPPRESS_DROPS);
                if (cell.blockEntity() != null && level.getBlockEntity(pos) != null) {
                    CompoundTag tag = cell.blockEntity().copy();
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    level.getBlockEntity(pos).loadWithComponents(tag, level.registryAccess());
                    level.getBlockEntity(pos).setChanged();
                }
            }
            for (var cell : template.cells()) {
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), anchor, rotation);
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        });
    }

    private static void preload(ServerLevel level, BuildingBounds bounds) {
        for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++) {
            for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }

    public static BuildingRecord findForFarm(ServerLevel level, UUID farmId) {
        return BuildingWorldData.get(level.getServer()).all().stream()
                .filter(record -> record.family().equals(FAMILY)
                        && record.farmId().equals(farmId)
                        && record.dimension().equals(level.dimension().location())
                        && record.phase() != BuildingRecord.Phase.MISSING)
                .findFirst().orElse(null);
    }

    public static boolean isGreenhouse(ResourceLocation family) {
        return FAMILY.equals(family);
    }

    public static void validateAssets(ServerLevel level) {
        PrefabDefinitions.template(level, ruinsTier());
        PrefabDefinitions.template(level, legacyRepairedTier());
    }

    public static boolean isUnbuiltManager(ServerLevel level, BlockPos pos) {
        for (FarmInstance farm : FarmInstanceRegistry.get(level.getServer()).getAllFarms()) {
            if (findForFarm(level, farm.getInstanceId()) != null) continue;
            if (pos.equals(farm.getGreenhousePos().offset(MANAGER))) return true;
        }
        return false;
    }

    public static boolean protectsWholeClaim(BuildingRecord record) {
        return record != null && isGreenhouse(record.family());
    }

    public static BlockPos portal(BuildingRecord record) {
        var tier = PrefabDefinitions.get(FAMILY).tier(1);
        return PrefabDefinitions.world(PORTAL, tier.anchor(), record.anchor(),
                PrefabDefinitions.rotation(record.facing()));
    }

    public static BlockPos exit(BuildingRecord record) {
        var tier = PrefabDefinitions.get(FAMILY).tier(1);
        return PrefabDefinitions.world(EXIT, tier.anchor(), record.anchor(),
                PrefabDefinitions.rotation(record.facing()));
    }

    public static void ensurePortal(ServerLevel level, BuildingRecord record) {
        if (record != null && record.phase() == BuildingRecord.Phase.READY) {
            migrateExteriorSoil(level, record);
            restoreDoorIfLegacyPortal(level, record);
            InteriorSubspaceManager.spawnGreenhouseOutdoorPortalAt(level, portal(record));
        }
    }

    /** Only authored, projected soil cells belong to the prefab; its retained foundation does not. */
    private static void migrateExteriorSoil(ServerLevel level, BuildingRecord record) {
        if (!record.family().equals(FAMILY) || record.mode() != BuildingRecord.Mode.PREFAB
                || !record.dimension().equals(level.dimension().location())) return;
        var tier = PrefabDefinitions.get(FAMILY).tier(record.tier());
        Rotation rotation = PrefabDefinitions.rotation(record.facing());
        BuildingProtection.internal(() -> {
            for (var cell : PrefabDefinitions.template(level, tier).cells()) {
                if (!cell.state().is(ModBlocks.DIRT.get()) && !cell.state().is(ModBlocks.FARMLAND.get())) continue;
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation);
                BlockState state = level.getBlockState(pos), replacement = upgradedSoil(state);
                if (replacement != state) {
                    level.setBlock(pos, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        });
    }

    /**
     * The first prefab integration mistakenly put the trigger on the authored door. Restore
     * that door when upgrading an existing save, then place the trigger in front of it.
     */
    private static void restoreDoorIfLegacyPortal(ServerLevel level, BuildingRecord record) {
        BlockPos door = PrefabDefinitions.world(
                DOOR, PrefabDefinitions.get(FAMILY).tier(1).anchor(), record.anchor(),
                PrefabDefinitions.rotation(record.facing()));
        if (!level.getBlockState(door).is(ModBlocks.PORTAL_TRIGGER.get())) return;
        InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(level, door);
        var tier = PrefabDefinitions.get(FAMILY).tier(1);
        var template = PrefabDefinitions.template(level, tier);
        BuildingProtection.internal(() -> {
            for (var cell : template.cells()) {
                if (!cell.pos().equals(DOOR) && !cell.pos().equals(DOOR.above())) continue;
                BlockPos pos = PrefabDefinitions.world(
                        cell.pos(), tier.anchor(), record.anchor(),
                        PrefabDefinitions.rotation(record.facing()));
                level.setBlock(pos, cell.state().rotate(PrefabDefinitions.rotation(record.facing())),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        });
    }

    private static void migrateInteriorSoil(ServerLevel level, UUID owner) {
        PlayerInteriorAllocator allocator = PlayerInteriorAllocator.get(level);
        if (!allocator.isGHPlaced(owner)) return;
        migrateInteriorSoil(level, allocator.getGreenhouseOrigin(owner));
    }

    /** Upgrade soil in an existing interior without replaying its schematic or touching plants. */
    public static int migrateInteriorSoil(ServerLevel level, BlockPos origin) {
        int[] changed = {0};
        BuildingProtection.internal(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin, origin.offset(18, 10, 19))) {
                var state = level.getBlockState(pos);
                var replacement = upgradedSoil(state);
                if (replacement != state) {
                    level.setBlock(pos, replacement,
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    changed[0]++;
                }
            }
        });
        return changed[0];
    }

    private static BlockState upgradedSoil(BlockState state) {
        if (state.is(Blocks.DIRT) || state.is(ModBlocks.YELLOW_DIRT.get())) {
            return ModBlocks.DIRT.get().defaultBlockState();
        }
        if (state.is(Blocks.FARMLAND)) {
            return ModBlocks.FARMLAND.get().defaultBlockState()
                    .setValue(FarmBlock.MOISTURE, state.getValue(FarmBlock.MOISTURE));
        }
        return state;
    }
}
