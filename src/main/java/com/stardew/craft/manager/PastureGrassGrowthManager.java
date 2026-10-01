package com.stardew.craft.manager;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Original-style {@code Farm.spawnWeeds(false)} and {@code HandleGrassGrowth}. */
@SuppressWarnings("null")
public class PastureGrassGrowthManager extends SavedData {
    private static final String DATA_NAME = "stardew_pasture_grass_growth";

    public static PastureGrassGrowthManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(PastureGrassGrowthManager::new,
                        (tag, provider) -> new PastureGrassGrowthManager())), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(PastureGrassGrowthManager::new,
                        (tag, provider) -> new PastureGrassGrowthManager())),
                DATA_NAME
        );
    }

    public void growDaily(ServerLevel level) {
        int season = StardewTimeManager.get().getCurrentSeason();
        if (season == 3) {
            return;
        }

        List<FarmInstance> farms = activeFarms(level);
        growExistingGrassForDay(level);
        for (FarmInstance farm : farms) {
            spawnDailyGrass(level, farm);
        }

        // GameLocation.HandleGrassGrowth: the first spring day after year one
        // gets fifteen full grass tiles and forty growth iterations.
        if (season == 0 && StardewTimeManager.get().getCurrentDay() == 1
                && StardewTimeManager.get().getAbsoluteDay() > 1) {
            for (FarmInstance farm : farms) {
                spawnSpringGrass(level, farm);
            }
            growWeedGrass(level, 40);
        }

        // Farms spread grass once every non-winter day. Gold Clock does not
        // suppress this call in the original game.
        growWeedGrass(level, 1);
    }

    /** Farm.spawnWeeds(false), including its farm-only early-return roll. */
    private static void spawnDailyGrass(ServerLevel level, FarmInstance farm) {
        RandomSource random = level.getRandom();
        int numberOfNewWeeds = rollDailyGrassAttempts(random);
        if (StardewTimeManager.get().getCurrentSeason() == 0
                && StardewTimeManager.get().getCurrentDay() == 1) {
            numberOfNewWeeds *= 15;
        }

        for (int i = 0; i < numberOfNewWeeds; i++) {
            for (int tries = 0; tries < 3; tries++) {
                // The source consumes the random tile coordinates before it
                // rolls grass/tree, even when this attempt ultimately places
                // nothing. Keep that order for deterministic daily results.
                BlockPos place = findRandomGrassPlace(level, farm, random);
                boolean grass = random.nextDouble() < 0.15D;
                boolean treeRoll = !grass && random.nextDouble() < 0.35D;
                if (treeRoll) {
                    // The original Farm method does not place a tree here. A
                    // quarter of its tree rolls aborts spawnWeeds for the farm.
                    if (random.nextDouble() < 0.25D) {
                        return;
                    }
                    continue;
                }
                if (!grass) {
                    continue;
                }

                if (place != null) {
                    placeGrass(level, farm, place, random.nextInt(2) + 1, random, 0.10D);
                }
            }
        }
    }

    /** LocationData Farm_Standard: MinDailyWeeds=5, MaxDailyWeeds=11. */
    private static int rollDailyGrassAttempts(RandomSource random) {
        return random.nextInt(7) + 5;
    }

    /** Grass.dayUpdate: every non-winter partial grass tile gains 1-3 clumps. */
    private static void growExistingGrassForDay(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (BlockPos pos : collectNearbyPastureGrass(level)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState grass = level.getBlockState(pos);
            if (!(grass.getBlock() instanceof PastureGrassBlock)) {
                continue;
            }
            int clumps = grass.getValue(PastureGrassBlock.CLUMPS);
            if (clumps < 4) {
                level.setBlock(pos, grass.setValue(PastureGrassBlock.CLUMPS,
                        growClumpCountForDay(clumps, random)), Block.UPDATE_ALL);
            }
        }
    }

    private static int growClumpCountForDay(int clumps, RandomSource random) {
        return clumps >= 4 ? clumps : Math.min(4, clumps + random.nextInt(3) + 1);
    }

    /** HandleGrassGrowth's fifteen one-shot random placements on spring 1. */
    private static void spawnSpringGrass(ServerLevel level, FarmInstance farm) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 15; i++) {
            BlockPos place = findRandomGrassPlace(level, farm, random);
            if (place != null) {
                placeGrass(level, farm, place, 4, random, 0.20D);
            }
        }
    }

    /** Exact numberOfWeeds growth/spread loop from GameLocation.growWeedGrass. */
    private static void growWeedGrass(ServerLevel level, int iterations) {
        RandomSource random = level.getRandom();
        List<BlockPos> knownGrass = collectNearbyPastureGrass(level);
        Set<Long> knownPositions = new HashSet<>();
        for (BlockPos pos : knownGrass) {
            knownPositions.add(pos.asLong());
        }
        for (int iteration = 0; iteration < iterations; iteration++) {
            // The source takes a snapshot each iteration, so newly spread grass
            // participates starting with the next iteration only.
            List<BlockPos> snapshot = List.copyOf(knownGrass);
            for (BlockPos pos : snapshot) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState grass = level.getBlockState(pos);
                if (!(grass.getBlock() instanceof PastureGrassBlock)
                        || random.nextDouble() >= 0.65D) {
                    continue;
                }

                int clumps = grass.getValue(PastureGrassBlock.CLUMPS);
                if (clumps < 4) {
                    int grown = Math.min(4, clumps + random.nextInt(3));
                    if (grown != clumps) {
                        level.setBlock(pos, grass.setValue(PastureGrassBlock.CLUMPS, grown),
                                Block.UPDATE_ALL);
                    }
                    continue;
                }

                for (BlockPos neighbor : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
                    if (!level.isLoaded(neighbor) || random.nextDouble() >= 0.25D
                            || !level.getBlockState(neighbor).isAir()) {
                        continue;
                    }
                    BlockState spread = grass.getBlock().defaultBlockState()
                            .setValue(PastureGrassBlock.VARIANT,
                                    random.nextInt(PastureGrassBlock.VISUAL_VARIANT_COUNT))
                            .setValue(PastureGrassBlock.CLUMPS, random.nextInt(2) + 1);
                    if (canSpreadGrassAt(level, neighbor)
                            && spread.canSurvive(level, neighbor)) {
                        level.setBlock(neighbor, spread, Block.UPDATE_ALL);
                        if (knownPositions.add(neighbor.asLong())) {
                            knownGrass.add(neighbor.immutable());
                        }
                    }
                }
            }
        }
    }

    private static void placeGrass(ServerLevel level, FarmInstance farm, BlockPos pos,
            int clumps, RandomSource random, double meadowlandsBlueChance) {
        boolean meadowlands = farm.getFarmLayoutId().equals(
                com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
                        .builtinId(com.stardew.craft.farm.FarmType.MEADOWLANDS));
        Block grassBlock = meadowlands && random.nextDouble() < meadowlandsBlueChance
                ? ModBlocks.BLUE_PASTURE_GRASS.get() : ModBlocks.PASTURE_GRASS.get();
        BlockState grass = grassBlock.defaultBlockState()
                .setValue(PastureGrassBlock.VARIANT,
                        random.nextInt(PastureGrassBlock.VISUAL_VARIANT_COUNT))
                .setValue(PastureGrassBlock.CLUMPS, clumps);
        if (grass.canSurvive(level, pos)) {
            level.setBlock(pos, grass, Block.UPDATE_ALL);
        }
    }

    @Nullable
    private static BlockPos findRandomGrassPlace(ServerLevel level, FarmInstance farm, RandomSource random) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
        int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
        com.stardew.craft.farm.FarmDebrisPlacementRules.Surface surface =
                com.stardew.craft.farm.FarmDebrisPlacementRules.findBareFarmableSurface(
                        level, farm, x, z);
        return surface == null ? null : surface.place();
    }

    private static boolean isDiggableFarmGround(Block block) {
        return com.stardew.craft.farm.FarmDebrisPlacementRules.isNaturalFarmGround(block);
    }

    private static boolean canSpreadGrassAt(ServerLevel level, BlockPos place) {
        UUID owner = FarmInstanceRegistry.get().getOwnerAt(place);
        FarmInstance farm = owner == null ? null : FarmInstanceRegistry.get().getFarm(owner);
        return farm != null && farm.contains(place)
                && com.stardew.craft.farm.FarmDebrisPlacementRules.isCompletelyOpen(level, place)
                && com.stardew.craft.farm.FarmDebrisPlacementRules.isBareFarmableGround(
                        farm, level.getBlockState(place.below()));
    }

    private static List<FarmInstance> activeFarms(ServerLevel level) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        List<FarmInstance> farms = new ArrayList<>();
        for (UUID owner : com.stardew.craft.farm.FarmDailyProcessHelper.getOnlineFarmOwners(level)) {
            FarmInstance farm = registry.getFarm(owner);
            if (farm != null && farm.isInitialized()) {
                farms.add(farm);
            }
        }
        return farms;
    }

    private static List<BlockPos> collectNearbyPastureGrass(ServerLevel level) {
        Set<Long> scannedChunks = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        for (FarmInstance farm : activeFarms(level)) {
            BlockPos min = farm.getFarmBoundsMin();
            BlockPos max = farm.getFarmBoundsMax();
            int minCX = min.getX() >> 4;
            int maxCX = max.getX() >> 4;
            int minCZ = min.getZ() >> 4;
            int maxCZ = max.getZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    long key = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
                    if (!scannedChunks.add(key) || !level.hasChunk(cx, cz)) {
                        continue;
                    }

                    int minX = Math.max(min.getX(), cx << 4);
                    int maxX = Math.min(max.getX(), (cx << 4) + 15);
                    int minZ = Math.max(min.getZ(), cz << 4);
                    int maxZ = Math.min(max.getZ(), (cz << 4) + 15);
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            int top = level.getHeight(
                                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    x, z);
                            int minY = Math.max(min.getY(), top - 3);
                            int maxY = Math.min(max.getY(), top + 1);
                            for (int y = minY; y <= maxY; y++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (level.getBlockState(pos).getBlock() instanceof PastureGrassBlock) {
                                    results.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(@Nonnull net.minecraft.nbt.CompoundTag tag,
                                               @Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        return tag;
    }
}
