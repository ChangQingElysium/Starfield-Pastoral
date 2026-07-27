package com.stardew.craft.farm;

import com.stardew.craft.api.v1.agriculture.StardewCropRemovalCause;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.farm.StardewFarmDebrisPlacements;
import com.stardew.craft.api.v1.farm.StardewFarmSnapshot;
import com.stardew.craft.api.v1.internal.farm.StardewFarmDebrisPlacementRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Original-style daily weed, stone and existing fallen-log spreading for player farms. */
@SuppressWarnings("null")
public final class FarmDebrisDailyService {
    private FarmDebrisDailyService() {
    }

    public static void onNewDay(ServerLevel level) {
        int season = StardewTimeManager.get().getCurrentSeason();
        if (season == 3) {
            return;
        }
        Set<UUID> processed = new HashSet<>();
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        for (var player : level.players()) {
            FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
            if (farm == null || !farm.isInitialized() || !processed.add(farm.getOwnerUUID())
                    || farm.hasActiveGoldClock()) {
                continue;
            }
            spreadFromExistingDebris(level, farm,
                    adjustedAttemptCount(level, season == 1 ? 30 : 20), season);
            if (StardewTimeManager.get().getCurrentDay() == 1) {
                spawnRandomDebris(level, farm, adjustedAttemptCount(level, 20), false, season);
                if (season == 0 && StardewTimeManager.get().getAbsoluteDay() > 1) {
                    spawnRandomDebris(level, farm, adjustedAttemptCount(level, 40), false, season);
                    spawnRandomDebris(level, farm, adjustedAttemptCount(level, 40), true, season);
                }
            }
        }
    }

    /** GameLocation.spawnWeedsAndStones applies both multipliers to every call. */
    private static int adjustedAttemptCount(ServerLevel level, int baseCount) {
        int attempts = WeatherManager.isRaining(level) ? baseCount * 2 : baseCount;
        return StardewTimeManager.get().getCurrentDay() == 1 ? attempts * 5 : attempts;
    }

    private static void spawnRandomDebris(ServerLevel level, FarmInstance farm, int attempts,
                                          boolean weedsOnly, int season) {
        RandomSource random = level.getRandom();
        StardewFarmSnapshot farmSnapshot = StardewFarmSnapshots.from(farm);
        for (int i = 0; i < attempts; i++) {
            BlockPos target = findRandomEmptyPlace(level, farm, random);
            if (target == null) {
                continue;
            }
            // Keep the source RNG order: choose the object outcome first, then
            // roll the independent 5% young-tree override.
            boolean debrisRoll = random.nextBoolean();
            BlockState placed;
            if (debrisRoll && !weedsOnly) {
                placed = randomDebrisState(random);
            } else {
                placed = ModBlocks.WILD_WEEDS.get().defaultBlockState()
                        .setValue(WildWeedsBlock.SEASON, season)
                        .setValue(WildWeedsBlock.VARIANT, random.nextInt(3));
            }
            if (random.nextDouble() < 0.05D) {
                com.stardew.craft.tree.WildTrees.Def[] trees = {
                        com.stardew.craft.tree.WildTrees.OAK,
                        com.stardew.craft.tree.WildTrees.MAPLE,
                        com.stardew.craft.tree.WildTrees.PINE
                };
                com.stardew.craft.tree.WildTrees.Def tree = trees[random.nextInt(trees.length)];
                // SDV creates a wild Tree at growth stage 0..2 here, never a
                // mature tree. This project has two pre-mature sapling blocks;
                // map stage 0 to sapling0 and stages 1/2 to sapling1.
                BlockState sapling = (random.nextInt(3) == 0
                        ? tree.sapling0().get() : tree.sapling1().get()).defaultBlockState();
                sapling = StardewFarmDebrisPlacementRegistry.resolve(
                        new StardewFarmDebrisPlacements.Context(
                                level,
                                farmSnapshot,
                                target,
                                sapling,
                                StardewFarmDebrisPlacements.Stage.YOUNG_TREE,
                                random
                        ));
                if (sapling.canSurvive(level, target)) {
                    level.setBlock(target, sapling, 3);
                    continue;
                }
            }
            placed = StardewFarmDebrisPlacementRegistry.resolve(
                    new StardewFarmDebrisPlacements.Context(
                            level,
                            farmSnapshot,
                            target,
                            placed,
                            StardewFarmDebrisPlacements.Stage.DEBRIS,
                            random
                    ));
            level.setBlock(target, placed, 3);
        }
    }

    private static void spreadFromExistingDebris(ServerLevel level, FarmInstance farm,
                                                  int attempts, int season) {
        RandomSource random = level.getRandom();
        // In SDV the source is selected from the location's complete object
        // collection, not just from weeds/stones. Preserve that important
        // probability: selecting a chest, fence, or machine consumes the attempt.
        List<BlockPos> farmObjects = collectFarmObjects(level, farm);
        if (farmObjects.isEmpty()) {
            return;
        }
        for (int i = 0; i < attempts; i++) {
            int dx;
            int dz;
            do {
                dx = random.nextInt(3) - 1;
                dz = random.nextInt(3) - 1;
            } while (dx == 0 && dz == 0);

            // GameLocation chooses the offset before selecting the source
            // object from the live collection.
            BlockPos source = farmObjects.get(random.nextInt(farmObjects.size()));
            BlockState sourceState = level.getBlockState(source);
            Block sourceBlock = sourceState.getBlock();
            if (!isSpreadSource(sourceState)) {
                continue;
            }

            BlockPos target = findDebrisPlaceNear(level, farm, source.offset(dx, 0, dz));
            if (target == null || level.getBlockEntity(target) != null) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (!canDebrisReplace(level, target, targetState)) {
                continue;
            }

            BlockState placed;
            if (sourceBlock instanceof WildWeedsBlock) {
                // NextBool is evaluated before the source-type conditions in
                // the original expression, even though weeds always copy weeds.
                random.nextBoolean();
                placed = ModBlocks.WILD_WEEDS.get().defaultBlockState()
                        .setValue(WildWeedsBlock.SEASON, season)
                        .setValue(WildWeedsBlock.VARIANT, random.nextInt(3));
            } else {
                // SDV chooses among two twig and two stone IDs half the time.
                // Its twig outcomes are represented by existing horizontal logs.
                if (!random.nextBoolean()) {
                    continue;
                }
                placed = switch (random.nextInt(4)) {
                    case 0, 1 -> fallenLogState(random);
                    case 2 -> ModBlocks.EARTH_SHALE.get().defaultBlockState();
                    default -> ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState();
                };
            }
            if (StardewCropRuntime.inspect(level, target) != null
                    && !StardewCropRuntime.remove(
                            level, target, StardewCropRemovalCause.FARM_DEBRIS)) {
                continue;
            }
            clearTilledGroundBelow(level, target);
            level.setBlock(target, placed, 3);
            if (!farmObjects.contains(target)) {
                farmObjects.add(target.immutable());
            }
        }
    }

    private static List<BlockPos> collectFarmObjects(ServerLevel level, FarmInstance farm) {
        List<BlockPos> objects = new ArrayList<>();
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                BlockPos top = findTopBlock(level, x, z, min.getY(), max.getY());
                if (top == null) {
                    continue;
                }
                BlockState state = level.getBlockState(top);
                Block block = state.getBlock();
                // These are the project equivalents of SDV's Object collection.
                // Crops/trees/terrain are intentionally excluded, since SDV stores
                // those in terrainFeatures instead of objects.
                if (isSpreadSource(state)
                        || block instanceof FenceBlock
                        || level.getBlockEntity(top) != null) {
                    objects.add(top.immutable());
                }
            }
        }
        return objects;
    }

    @Nullable
    private static BlockPos findRandomEmptyPlace(ServerLevel level, FarmInstance farm, RandomSource random) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
        int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
        BlockPos top = findTopBlock(level, x, z, min.getY(), max.getY());
        if (top == null || !isDiggableFarmGround(level.getBlockState(top).getBlock())
                || level.getBlockState(top).is(Blocks.FARMLAND)) {
            return null;
        }
        BlockPos place = top.above();
        return level.getBlockState(place).isAir() ? place : null;
    }

    @Nullable
    private static BlockPos findDebrisPlaceNear(ServerLevel level, FarmInstance farm, BlockPos near) {
        if (!farm.contains(near)) {
            return null;
        }
        for (int y = near.getY() + 1; y >= near.getY() - 1; y--) {
            BlockPos place = new BlockPos(near.getX(), y, near.getZ());
            BlockState ground = level.getBlockState(place.below());
            if (isDiggableFarmGround(ground.getBlock())
                    && canDebrisReplace(level, place, level.getBlockState(place))) {
                return place;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findTopBlock(ServerLevel level, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isSpreadSource(BlockState state) {
        Block block = state.getBlock();
        return block instanceof WildWeedsBlock
                || isFarmStone(block)
                || isFarmLog(state);
    }

    private static boolean isDiggableFarmGround(Block block) {
        return block == ModBlocks.YELLOW_DIRT.get()
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.FARMLAND;
    }

    private static boolean canDebrisReplace(
            ServerLevel level,
            BlockPos position,
            BlockState state
    ) {
        Block block = state.getBlock();
        return state.isAir()
                || block instanceof WildWeedsBlock
                || block instanceof PastureGrassBlock
                || block instanceof StardewCropBlock
                || StardewCropRuntime.inspect(level, position) != null
                || isFarmStone(block)
                || isFarmLog(state);
    }

    /** Spreading debris destroys HoeDirt in SDV, exposing the farm's dirt tile. */
    private static void clearTilledGroundBelow(ServerLevel level, BlockPos place) {
        BlockPos ground = place.below();
        if (level.getBlockState(ground).is(Blocks.FARMLAND)) {
            level.setBlock(ground, ModBlocks.YELLOW_DIRT.get().defaultBlockState(), 3);
        }
    }

    /** SDV 294/295 are mapped to the project's existing horizontal log, not a new twig block. */
    private static BlockState fallenLogState(RandomSource random) {
        return ModBlocks.OAK_LOG.get().defaultBlockState().setValue(
                RotatedPillarBlock.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
    }

    private static BlockState randomDebrisState(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0, 1 -> fallenLogState(random);
            case 2 -> ModBlocks.EARTH_SHALE.get().defaultBlockState();
            default -> ModBlocks.MOSSY_SANDSTONE.get().defaultBlockState();
        };
    }

    private static boolean isFarmStone(Block block) {
        return block == ModBlocks.EARTH_SHALE.get() || block == ModBlocks.MOSSY_SANDSTONE.get();
    }

    private static boolean isFarmLog(BlockState state) {
        Block block = state.getBlock();
        return (block == ModBlocks.OAK_LOG.get()
                || block == ModBlocks.MAPLE_LOG.get()
                || block == ModBlocks.PINE_LOG.get())
                && state.hasProperty(RotatedPillarBlock.AXIS)
                && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y;
    }
}
