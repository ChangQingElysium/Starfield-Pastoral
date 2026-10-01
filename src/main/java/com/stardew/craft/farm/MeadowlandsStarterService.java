package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.runtime.LivestockCare;
import com.stardew.craft.animal.runtime.LivestockHomes;
import com.stardew.craft.animal.runtime.LivestockRecord;
import com.stardew.craft.animal.runtime.LivestockService;
import com.stardew.craft.animal.runtime.LivestockSpecies;
import com.stardew.craft.animal.runtime.LivestockWorldData;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.building.runtime.BuildingBounds;
import com.stardew.craft.building.runtime.BuildingPlacementService;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Installs the authored Meadowlands coop as a real completed Robin prefab. */
public final class MeadowlandsStarterService {
    private static final ResourceLocation STEP = new ResourceLocation(
            StardewCraft.MODID, "meadowlands_starter_coop");
    /** The raw tier-one template starts here in farm_8; the reservation anchor
     * is one block west and two blocks north, as declared by coop.json. */
    private static final BlockPos COOP_TEMPLATE_ORIGIN = new BlockPos(145, 24, 77);
    private static final BlockPos BLUE_GRASS_MIN = new BlockPos(133, 25, 70);
    private static final BlockPos BLUE_GRASS_MAX = new BlockPos(162, 25, 99);
    private static boolean bootstrapped;

    private MeadowlandsStarterService() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        StardewFarmInitializationSteps.register(
                STEP, 1, 95, StardewFarmInitializationSteps.FailurePolicy.STOP,
                context -> ensureCurrent(context.level(), context.farm().ownerUuid()));
    }

    public static void ensureCurrent(ServerLevel level, UUID owner) {
        FarmInstance farm = FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        if (farm == null || !farm.getFarmLayoutId().equals(
                StardewFarmLayoutRegistry.builtinId(FarmType.MEADOWLANDS))) return;

        var family = PrefabDefinitions.get(PrefabDefinitions.COOP);
        var tier = family.tier(1);
        BlockPos templateOrigin = farm.getOrigin().offset(COOP_TEMPLATE_ORIGIN);
        BlockPos anchor = templateOrigin.offset(tier.anchor());
        Rotation rotation = PrefabDefinitions.rotation(Direction.SOUTH);
        BuildingBounds claim = PrefabDefinitions.transform(
                family.reservation(), anchor, rotation);
        BlockPos manager = PrefabDefinitions.world(
                tier.manager(), tier.anchor(), anchor, rotation);
        preload(level, claim);

        String source = "meadowlands-starter-coop:" + farm.getInstanceId();
        BuildingRecord candidate = new BuildingRecord(
                stableId(source), farm.getInstanceId(), farm.getSlotIndex(),
                PrefabDefinitions.COOP, BuildingRecord.Mode.PREFAB,
                level.dimension().location(), anchor, manager, Direction.SOUTH,
                claim, BuildingRecord.Phase.READY, 1,
                BuildingRecord.Residence.VALID, 0, "");
        BuildingWorldData buildings = BuildingWorldData.get(level.getServer());
        var result = buildings.importCompletedPrefab(source, candidate);
        if (result != BuildingWorldData.Result.SUCCESS) {
            throw new IllegalStateException("Could not register Meadowlands starter coop: " + result);
        }
        BuildingRecord home = buildings.all().stream()
                .filter(record -> record.farmId().equals(farm.getInstanceId())
                        && record.family().equals(PrefabDefinitions.COOP)
                        && record.anchor().equals(anchor)
                        && record.phase() != BuildingRecord.Phase.MISSING)
                .findFirst().orElseThrow();

        // Re-project the full template once so the manager, block entities and
        // air volume are byte-for-byte equivalent to a Robin-completed coop.
        BuildingPlacementService.projectCompletedPrefab(level, home);
        seedBlueGrass(level, farm, home);
        ensureStarterChicken(level, farm, home, "white", "小白", LivestockSpecies.WHITE_CHICKEN);
        ensureStarterChicken(level, farm, home, "brown", "小棕", LivestockSpecies.BROWN_CHICKEN);
        LivestockService.project(level.getServer());
    }

    private static void ensureStarterChicken(ServerLevel level, FarmInstance farm,
            BuildingRecord home, String key, String name, LivestockSpecies species) {
        LivestockWorldData animals = LivestockWorldData.get(level.getServer());
        UUID id = stableId("meadowlands-starter-chicken:" + key + ":" + farm.getInstanceId());
        if (animals.find(id) != null) return;
        if (!LivestockHomes.accepts(level, home, species)
                || animals.occupancy(home.id()) >= LivestockHomes.capacity(level, home)) {
            throw new IllegalStateException("Starter coop is not a valid home for " + species.id());
        }
        animals.put(new LivestockRecord(
                id, farm.getOwnerUUID(), farm.getInstanceId(), home.id(), name,
                animals.allocateRandomId(), StardewTimeManager.get().getAbsoluteDay(),
                LivestockCare.purchased()).species(species));
    }

    private static void seedBlueGrass(ServerLevel level, FarmInstance farm, BuildingRecord home) {
        // The authored paddock receives a sparse, deterministic starter patch;
        // later daily growth follows the original 10%/20% blue-grass rolls.
        UUID farmId = farm.getInstanceId();
        for (int x = BLUE_GRASS_MIN.getX(); x <= BLUE_GRASS_MAX.getX(); x++) {
            for (int z = BLUE_GRASS_MIN.getZ(); z <= BLUE_GRASS_MAX.getZ(); z++) {
                if (Math.floorMod(stableId(farmId + ":blue:" + x + ":" + z).hashCode(), 7) != 0) continue;
                BlockPos local = new BlockPos(x, BLUE_GRASS_MIN.getY(), z);
                BlockPos pos = farm.getOrigin().offset(local);
                if (home.claim().contains(pos) || !level.getBlockState(pos).isAir()
                        || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                var state = ModBlocks.BLUE_PASTURE_GRASS.get().defaultBlockState()
                        .setValue(PastureGrassBlock.VARIANT, Math.floorMod(x * 31 + z, PastureGrassBlock.VISUAL_VARIANT_COUNT));
                if (state.canSurvive(level, pos)) level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        }
    }

    private static UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void preload(ServerLevel level, BuildingBounds bounds) {
        for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++) {
            for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }
}
