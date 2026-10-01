package com.stardew.craft.building.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.BuildingManagerModelBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public final class BuildingPlacementService {
    private BuildingPlacementService() {}
    public record Probe(String issue, BlockPos problem, java.util.List<BlockPos> problems,
                        BuildingBounds claim, BuildingBounds structure, BlockPos manager, FarmInstance farm) {
        public Probe {
            problems = java.util.List.copyOf(problems);
        }
        public boolean valid() { return issue.equals("valid"); }
    }

    public static Probe probe(ServerLevel level, ServerPlayer player, BlockPos anchor, Direction facing, boolean self, net.minecraft.resources.ResourceLocation familyId) {
        return probe(level, player, anchor, facing, self, familyId, null);
    }
    public static Probe probe(ServerLevel level, ServerPlayer player, BlockPos anchor, Direction facing, boolean self,
                              net.minecraft.resources.ResourceLocation familyId, BuildingRecord moving) {
        var family = PrefabDefinitions.get(familyId);
        var tier = family == null ? null : family.tier(moving == null ? 1 : moving.tier());
        var rotation = PrefabDefinitions.rotation(facing);
        var claim = self ? (moving == null ? UtilityBuildings.bounds(familyId, anchor) : UtilityBuildings.moveBounds(moving, anchor, facing)) : PrefabDefinitions.transform(family.reservation(), anchor, rotation);
        var structure = self ? claim : PrefabDefinitions.transform(tier.bounds(), anchor, rotation);
        BlockPos manager = self ? anchor : PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, rotation);
        var registry = FarmInstanceRegistry.get(level.getServer());
        // A move remains an operation on its original farm, including after rotation.
        // The internal template origin can fall outside the visible target; do not use it
        // to switch owners and misreport an out-of-bounds target as a permission failure.
        FarmInstance farm = placementFarm(registry,claim,moving);
        String failure = null;
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) failure = "dimension";
        else failure=sitePermission(farm,player.getUUID(),claim,moving);
        if(failure==null) {
            if(claim.min().getY()<=level.getMinBuildHeight() || claim.maxExclusive().getY()>level.getMaxBuildHeight())failure="bounds";
            else if(BuildingWorldData.get(level.getServer()).conflicting(level.dimension().location(),claim,moving==null?null:moving.id())!=null)failure="overlap";
            else if(!level.hasChunksAt(claim.min().below(),claim.maxInclusive()))failure="unloaded";
        }
        if (self && FishPondPrefabs.isPond(familyId)) failure="prefab_only";
        if (failure != null) return new Probe(failure, anchor, java.util.List.of(), claim, structure, manager, farm);
        if (self && moving == null && !level.getBlockState(anchor).canBeReplaced())
            return new Probe("manager_space", anchor, java.util.List.of(anchor), claim, structure, manager, farm);
        if (!self || moving != null) {
            SpaceIssue space = FishPondPrefabs.isPond(familyId)
                    ? FishPondPrefabs.checkSite(level, claim, anchor, facing, moving)
                    : checkSpace(level, structure, moving == null ? null : BuildingTransfer.contentBounds(moving), !self);
            if (space != null) return new Probe(space.issue(), space.pos(), space.positions(), claim, structure, manager, farm);
        }
        if (!self || moving != null) {
            var animals = com.stardew.craft.animal.runtime.LivestockWorldData.get(level.getServer());
            var occupied = new java.util.ArrayList<BlockPos>();
            for (var entity : level.getEntitiesOfClass(LivingEntity.class, aabb(structure))) {
                var animal = animals.find(entity.getUUID());
                boolean following = moving != null && animal != null && animal.home().equals(moving.id()) && BuildingTransfer.contentBounds(moving).contains(entity.blockPosition());
                if (!following && occupied.size() < 256) occupied.add(entity.blockPosition().immutable());
            }
            if (!occupied.isEmpty()) return new Probe("occupied", occupied.getFirst(), occupied, claim, structure, manager, farm);
        }
        return new Probe("valid", anchor, java.util.List.of(), claim, structure, manager, farm);
    }

    public static FarmInstance placementFarm(FarmInstanceRegistry registry,BuildingBounds claim,BuildingRecord moving) {
        UUID owner=moving==null?registry.getOwnerAt(claim.min()):registry.getOwnerBySlot(moving.farmSlot());
        return owner==null?null:registry.getFarm(owner);
    }
    public static String sitePermission(FarmInstance farm,UUID actor,BuildingBounds claim,BuildingRecord moving) {
        if(farm==null)return "farm";
        if(moving!=null && !farm.getInstanceId().equals(moving.farmId()))return "work_stale";
        if(!farm.isFarmer(actor) && !FarmPermissionManager.get().canModify(farm.getOwnerUUID(),actor))return "permission";
        if(!withinFarm(farm,claim))return "bounds";
        return null;
    }
    /** The farm terrain schematic's height is not a ceiling on buildings or farm ownership. */
    public static boolean withinFarm(FarmInstance farm,BuildingBounds claim) {
        var min=farm.getFarmBoundsMin();var max=farm.getFarmBoundsMax();
        return claim.min().getX()>=min.getX() && claim.min().getZ()>=min.getZ()
                && claim.maxInclusive().getX()<=max.getX() && claim.maxInclusive().getZ()<=max.getZ();
    }

    public record SpaceIssue(String issue, BlockPos pos, java.util.List<BlockPos> positions) {
        public SpaceIssue(String issue, BlockPos pos) { this(issue, pos, java.util.List.of(pos.immutable())); }
        public SpaceIssue {
            positions = java.util.List.copyOf(positions);
        }
    }
    /** Whether a block in the current building volume is passable empty cover. */
    static boolean isClearAirVolume(ServerLevel level, BlockPos pos,
                                    com.stardew.craft.floor.SurfaceFloorData floors) {
        var state = level.getBlockState(pos);
        return level.getBlockEntity(pos) == null
                && state.getCollisionShape(level, pos).isEmpty()
                && floors.at(pos) == null;
    }

    public static SpaceIssue checkSpace(ServerLevel level, BuildingBounds claim) { return checkSpace(level, claim, null); }
    public static SpaceIssue checkSpace(ServerLevel level, BuildingBounds claim, BuildingBounds vacated) {
        return checkSpace(level,claim,vacated,true);
    }
    public static SpaceIssue checkSpace(ServerLevel level,BuildingBounds claim,BuildingBounds vacated,boolean replacesGround) {
        var floors=com.stardew.craft.floor.SurfaceFloorData.get(level);
        String issue = null;
        var problems = new java.util.ArrayList<BlockPos>();
        // Prefabs include an embedded floor. Validate support separately so it can never
        // enter the air-volume scan. Self-built moves start above their support instead.
        int groundY = claim.min().getY() - (replacesGround ? 0 : 1);
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(claim.min().getX(), groundY, claim.min().getZ()),
                new BlockPos(claim.maxInclusive().getX(), groundY, claim.maxInclusive().getZ()))) {
            boolean own = vacated != null && vacated.contains(pos);
            if (replacesGround && own) continue;
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || (replacesGround
                    ? !state.isCollisionShapeFullBlock(level, pos)
                    : own || !state.isFaceSturdy(level, pos, Direction.UP))) {
                if (issue == null) issue = "ground";
                if (problems.size() < 256) problems.add(pos.immutable());
            }
            if (replacesGround && (level.getBlockEntity(pos) != null || floors.at(pos) != null)) {
                if (issue == null || issue.equals("ground")) issue = "ground_contents";
                if (problems.size() < 256 && !problems.contains(pos)) problems.add(pos.immutable());
            }
            if (!replacesGround && floors.at(pos) != null && (vacated == null || !vacated.contains(pos.above()))) {
                if (issue == null) issue = "air";
                if (problems.size() < 256) problems.add(pos.immutable());
            }
        }
        BlockPos airMin = new BlockPos(claim.min().getX(), groundY + 1, claim.min().getZ());
        if (airMin.getY() < claim.maxExclusive().getY()) {
            for (BlockPos pos : BlockPos.betweenClosed(airMin, claim.maxInclusive())) {
                if (vacated != null && vacated.contains(pos)) continue;
                // The farm ecology can leave passable ground cover (for example
                // pasture grass) in the reserved volume. It is not an obstacle
                // in SDV's placement rules; solid debris, containers and floor
                // covers still are.
                if (!isClearAirVolume(level, pos, floors)) {
                    if (issue == null) issue = "air";
                    if (problems.size() < 256) problems.add(pos.immutable());
                }
            }
        }
        return problems.isEmpty() ? null : new SpaceIssue(issue, problems.getFirst(), problems);
    }

    public static boolean placePrefab(ServerPlayer player, BlockPos anchor, Direction facing, UUID permit, net.minecraft.resources.ResourceLocation familyId) {
        ServerLevel level = player.serverLevel();
        Probe probe = probe(level, player, anchor, facing, false, familyId);
        if (!probe.valid()) { flashProblems(player, probe.problems()); message(player, probe.issue()); return false; }
        var data = BuildingWorldData.get(level.getServer());
        var farms = FarmInstanceRegistry.get(level.getServer());
        if (permit == null || !data.permitsPlacement(permit, probe.farm().getInstanceId(), familyId, player.getUUID(), farms)) {
            message(player, "permit"); return false;
        }
        if (data.hasActiveConstruction(probe.farm().getInstanceId())) { message(player, "robin_busy"); return false; }
        // Resolve every template block before consuming the blueprint or claiming space.
        try {
            for (var tier : PrefabDefinitions.get(familyId).tiers()) PrefabDefinitions.template(level, tier);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error("Coop template validation failed", exception);
            message(player, "asset_error"); return false;
        }
        BuildingRecord record = BuildingRecord.waiting(probe.farm().getInstanceId(), probe.farm().getSlotIndex(), familyId,
                BuildingRecord.Mode.PREFAB, level.dimension().location(), anchor, probe.manager(), facing, probe.claim());
        if (data.beginPrefab(record, permit, StardewTimeManager.get().getAbsoluteDay(), player.getUUID(), farms) != BuildingWorldData.Result.SUCCESS) {
            message(player, "permit"); return false;
        }
        // The persisted order owns unfinished work too; a reload retries this exact site's projection.
        try { scaffold(level, data.find(record.id())); }
        catch (RuntimeException exception) { StardewCraft.LOGGER.error("Construction site {} awaits retry", record.id(), exception); }
        message(player, "started");
        return true;
    }

    static void flashProblems(ServerPlayer player, java.util.List<BlockPos> problems) {
        if (problems.isEmpty()) return;
        net.minecraftforge.network.PacketDistributor.sendToPlayer(player,
                new com.stardew.craft.network.payload.BuildingObstructionFlashPayload(
                        player.level().dimension().location(), problems));
    }

    public static void scaffold(ServerLevel level, BuildingRecord record) {
        var tier = PrefabDefinitions.get(record.family()).tier(1);
        var rotation = PrefabDefinitions.rotation(record.facing());
        // Perimeter follows the current-tier footprint; reservation remains the whole family envelope.
        BuildingBounds footprint = PrefabDefinitions.transform(tier.bounds(), record.anchor(), rotation);
        int x0 = footprint.min().getX(), x1 = footprint.maxInclusive().getX();
        int z0 = footprint.min().getZ(), z1 = footprint.maxInclusive().getZ();
        var order = BuildingWorldData.get(level.getServer()).order(record.id());
        boolean showFloor = order != null && order.remainingDays() <= 1;
        if(showFloor) RisingConstruction.clear(level,record.id());
        int y = record.anchor().getY() + 1;
        BuildingProtection.internal(() -> {
            if (showFloor) {
                for (BlockPos pos : BlockPos.betweenClosed(record.claim().min(), record.claim().maxInclusive())) {
                    if (level.getBlockState(pos).is(ModBlocks.CONSTRUCTION_FENCE.get())) level.removeBlock(pos, false);
                }
                for (var cell : PrefabDefinitions.template(level, tier).cells()) if (cell.pos().getY() == tier.anchor().getY()) {
                    level.setBlock(PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation),
                            cell.state().rotate(rotation), Block.UPDATE_ALL);
                }
            }

            BlockPos signPos = record.anchor().offset(PrefabDefinitions.rotateCell(new BlockPos((tier.bounds().min().getX() + tier.bounds().maxInclusive().getX()) / 2, 1, tier.bounds().maxInclusive().getZ()), rotation));
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                Direction direction = x == x0 || x == x1 ? Direction.EAST : Direction.NORTH;
                if (corner) direction = x == x0 ? (z == z0 ? Direction.EAST : Direction.NORTH)
                        : (z == z0 ? Direction.SOUTH : Direction.WEST);
                BlockState state = ModBlocks.CONSTRUCTION_FENCE.get().defaultBlockState()
                        .setValue(ConstructionFenceBlock.PART, corner ? 1 : 0)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(ModBlocks.CONSTRUCTION_FENCE.get())) {
                    if(pos.equals(signPos))state=state.setValue(ConstructionFenceBlock.NOTICE,true).setValue(BlockStateProperties.HORIZONTAL_FACING,record.facing());
                    RisingConstruction.place(level,record,pos,state,order != null && !order.scaffoldReady());
                }
            }
            if (level.getBlockState(record.manager().below()).isAir()) {
                var floor = PrefabDefinitions.template(level, tier).cells().stream()
                        .filter(cell -> cell.pos().equals(tier.manager().below())).findFirst().orElseThrow();
                level.setBlock(record.manager().below(), floor.state().rotate(rotation), Block.UPDATE_ALL);
            }
            if (level.getBlockState(record.manager()).isAir()) level.setBlock(record.manager(), PrefabDefinitions.managerState(record.family(),record.facing()), Block.UPDATE_ALL);
        });
        BuildingWorldData.get(level.getServer()).markScaffold(record.id());
        ensureWorker(level, record);
    }

    public static void ensureWorker(ServerLevel level, BuildingRecord record) {
        var tier = PrefabDefinitions.get(record.family()).tier(record.tier());
        var workers = level.getEntitiesOfClass(RobinConstructionEntity.class, aabb(record.claim()), entity -> record.id().equals(entity.buildingId()));
        for (int i = 1; i < workers.size(); i++) workers.get(i).discard();
        var worker = workers.isEmpty() ? ModEntities.ROBIN_CONSTRUCTION.get().create(level) : workers.getFirst();
        if (worker == null) return;
        var order = BuildingWorldData.get(level.getServer()).order(record.id());
        BlockPos pos = record.anchor().offset(PrefabDefinitions.rotateCell(new BlockPos((tier.bounds().min().getX() + tier.bounds().maxInclusive().getX()) / 2, 1, tier.bounds().maxInclusive().getZ() - 1), PrefabDefinitions.rotation(record.facing())));
        // A marker on the front center (silo) can occupy the default worker cell.
        if(pos.equals(record.manager())) pos=pos.offset(new BlockPos(-1,0,0).rotate(PrefabDefinitions.rotation(record.facing())));
        boolean upgrading = record.phase() == BuildingRecord.Phase.UPGRADING;
        if (upgrading) {
            pos = BuildingLifecycleService.indoorWorkPosition(level, record);
            if (pos == null) { worker.discard(); return; }
        }
        worker.bind(record.id()); worker.setHigh(upgrading);
        worker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, record.facing().toYRot(), 0);
        if (workers.isEmpty()) level.addFreshEntity(worker);
    }

    public static void finish(ServerLevel level, BuildingRecord record) {
        var data = BuildingWorldData.get(level.getServer());
        var order = data.order(record.id());
        if (order == null || order.remainingDays() != 0) return;
        var tier = PrefabDefinitions.get(record.family()).tier(1);
        var template = PrefabDefinitions.template(level, tier);
        var rotation = PrefabDefinitions.rotation(record.facing());
        RisingConstruction.clear(level,record.id());
        // Keep actors out of newly written walls; use the reserved front approach at ground level.
        BlockPos safe = record.anchor().offset(PrefabDefinitions.rotateCell(new BlockPos(tier.bounds().min().getX(), 1, tier.bounds().maxExclusive().getZ()), rotation));
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, aabb(record.claim()))) {
            entity.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        }
        BuildingProtection.transfer(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(record.claim().min(), record.claim().maxInclusive())) {
                if (level.getBlockState(pos).is(ModBlocks.CONSTRUCTION_FENCE.get())) level.removeBlock(pos, false);
            }
            for (var cell : template.cells()) {
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation);
                BlockState state = cell.state().rotate(rotation);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (cell.blockEntity() != null && level.getBlockEntity(pos) != null) {
                    var tag = cell.blockEntity().copy();
                    tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
                    level.getBlockEntity(pos).loadWithComponents(tag, level.registryAccess());
                    level.getBlockEntity(pos).setChanged();
                }
            }
            for (var cell : template.cells()) {
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation);
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        });
        if (data.finishPrefab(record.id()) != BuildingWorldData.Result.SUCCESS) throw new IllegalStateException("Could not finish construction order");
        var ready = data.find(record.id());
        // Persist a usable completed state before client publication. If publication throws,
        // the order has already been consumed and otherwise leaves the building READY but
        // permanently UNCHECKED after a reload.
        data.assessResidence(record.id(), ready.revision(), 1);
        ready = data.find(record.id());
        publishManager(level, ready);
        FishPondPrefabs.bind(level, ready);
        level.getEntitiesOfClass(RobinConstructionEntity.class, aabb(record.claim()), worker -> record.id().equals(worker.buildingId()))
                .forEach(RobinConstructionEntity::discard);
    }

    /** The construction manager may already have the same state, so bulk placement sends no update.
     * Publish it explicitly after the surrounding structure is complete and prioritize its section mesh. */
    public static void publishManager(ServerLevel level, BuildingRecord record) {
        var pos = record.manager();
        var state = level.getBlockState(pos);
        if (!state.is(PrefabDefinitions.managerBlock(record.family())))
            throw new IllegalStateException("Completed building is missing its manager: " + record.id());
        net.minecraftforge.network.PacketDistributor.sendToPlayersTrackingChunk(level,
                new net.minecraft.world.level.ChunkPos(pos),
                new com.stardew.craft.network.payload.BuildingManagerReadyPayload(level.dimension().location(), pos, state));
    }

    /** Projects every authored cell of an already completed prefab. This is the
     * same block/BE projection used by Robin's completion path, without an
     * artificial construction order. Retained terrain in the template floor is
     * deliberately left untouched. */
    public static void projectCompletedPrefab(ServerLevel level, BuildingRecord record) {
        if (record.mode() != BuildingRecord.Mode.PREFAB
                || record.phase() != BuildingRecord.Phase.READY) {
            throw new IllegalArgumentException("Building is not a completed prefab");
        }
        var tier = PrefabDefinitions.get(record.family()).tier(record.tier());
        var template = PrefabDefinitions.template(level, tier);
        var rotation = PrefabDefinitions.rotation(record.facing());
        BuildingProtection.transfer(() -> {
            for (var cell : template.cells()) {
                BlockPos pos = PrefabDefinitions.world(
                        cell.pos(), tier.anchor(), record.anchor(), rotation);
                BlockState state = cell.state().rotate(rotation);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS
                        | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                if (cell.blockEntity() != null && level.getBlockEntity(pos) != null) {
                    var tag = cell.blockEntity().copy();
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    level.getBlockEntity(pos).loadWithComponents(tag, level.registryAccess());
                    level.getBlockEntity(pos).setChanged();
                }
            }
            for (var cell : template.cells()) {
                BlockPos pos = PrefabDefinitions.world(
                        cell.pos(), tier.anchor(), record.anchor(), rotation);
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
        });
        publishManager(level, record);
    }

    /** Repair a completed projection after loading older chunk data; existing inventories stay intact. */
    public static void reconcileCompleted(ServerLevel level, BuildingRecord record) {
        FishPondPrefabs.bind(level, record);
        var tier = PrefabDefinitions.get(record.family()).tier(record.tier());
        var template = PrefabDefinitions.template(level, tier);
        var rotation = PrefabDefinitions.rotation(record.facing());
        BuildingProtection.internal(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(record.claim().min(), record.claim().maxInclusive())) {
                if (level.getBlockState(pos).is(ModBlocks.CONSTRUCTION_FENCE.get())) level.removeBlock(pos, false);
            }
            for (var cell : template.cells()) {
                if (cell.state().isAir()) continue;
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), rotation);
                BlockState expected = cell.state().rotate(rotation);
                if (level.getBlockState(pos).getBlock() == expected.getBlock()) continue;
                level.setBlock(pos, expected, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (cell.blockEntity() != null && level.getBlockEntity(pos) != null) {
                    level.getBlockEntity(pos).loadWithComponents(cell.blockEntity().copy(), level.registryAccess());
                    level.getBlockEntity(pos).setChanged();
                }
            }
        });
    }

    public static AABB aabb(BuildingBounds bounds) {
        return new AABB(bounds.min().getX(), bounds.min().getY(), bounds.min().getZ(),
                bounds.maxExclusive().getX(), bounds.maxExclusive().getY(), bounds.maxExclusive().getZ());
    }
    public static void message(ServerPlayer player, String issue) {
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("building.stardewcraft." + issue));
    }
}
