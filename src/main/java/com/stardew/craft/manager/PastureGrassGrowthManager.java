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
import net.minecraft.world.level.block.Blocks;
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
                new SavedData.Factory<>(PastureGrassGrowthManager::new,
                        (tag, provider) -> new PastureGrassGrowthManager()),
                DATA_NAME
        );
    }

    public void growDaily(ServerLevel level) {
        int season = StardewTimeManager.get().getCurrentSeason();
        if (season == 3) {
            cleanupWinterGrass(level);
            return;
        }

        List<FarmInstance> farms = onlineFarms(level);
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
        int numberOfNewWeeds = random.nextInt(5) + 1;
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
                    placeGrass(level, place, random.nextInt(2) + 1, random);
                }
            }
        }
    }

    /** HandleGrassGrowth's fifteen one-shot random placements on spring 1. */
    private static void spawnSpringGrass(ServerLevel level, FarmInstance farm) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 15; i++) {
            BlockPos place = findRandomGrassPlace(level, farm, random);
            if (place != null) {
                placeGrass(level, place, 4, random);
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
                            .setValue(PastureGrassBlock.VARIANT, random.nextInt(3))
                            .setValue(PastureGrassBlock.CLUMPS, random.nextInt(2) + 1);
                    if (isDiggableFarmGround(level.getBlockState(neighbor.below()).getBlock())
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

    private static void placeGrass(ServerLevel level, BlockPos pos, int clumps, RandomSource random) {
        BlockState grass = ModBlocks.PASTURE_GRASS.get().defaultBlockState()
                .setValue(PastureGrassBlock.VARIANT, random.nextInt(3))
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
        for (int y = max.getY(); y >= min.getY(); y--) {
            BlockPos ground = new BlockPos(x, y, z);
            if (!level.isLoaded(ground)) {
                return null;
            }
            BlockState groundState = level.getBlockState(ground);
            if (groundState.isAir()) {
                continue;
            }
            BlockPos place = ground.above();
            if (!isDiggableFarmGround(groundState.getBlock())
                    || !farm.contains(ground)
                    || !level.getBlockState(place).isAir()) {
                return null;
            }
            return place;
        }
        return null;
    }

    private static boolean isDiggableFarmGround(Block block) {
        return block == ModBlocks.YELLOW_DIRT.get()
                || block == Blocks.GRASS_BLOCK;
    }

    private static void cleanupWinterGrass(ServerLevel level) {
        for (BlockPos pos : collectNearbyPastureGrass(level)) {
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof PastureGrassBlock) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static List<FarmInstance> onlineFarms(ServerLevel level) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        Set<UUID> seen = new HashSet<>();
        List<FarmInstance> farms = new ArrayList<>();
        for (var player : level.players()) {
            FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
            if (farm != null && farm.isInitialized() && seen.add(farm.getOwnerUUID())) {
                farms.add(farm);
            }
        }
        return farms;
    }

    private static List<BlockPos> collectNearbyPastureGrass(ServerLevel level) {
        Set<Long> scannedChunks = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        for (FarmInstance farm : onlineFarms(level)) {
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
