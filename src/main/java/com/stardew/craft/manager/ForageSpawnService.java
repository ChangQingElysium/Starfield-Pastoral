package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewForageZoneDefinition;
import com.stardew.craft.api.v1.world.StardewRegion;
import com.stardew.craft.api.v1.world.StardewRegions;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.ForageBlock;
import com.stardew.craft.world.data.ForageZoneData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * SDV-parity forage spawning service.
 * Called once per day from StardewTimeManager.advanceDayWithSleepTime().
 *
 * <p>Replicates GameLocation.spawnObjects() logic:
 * <ul>
 *   <li>Each zone has MinDailyForageSpawn, MaxDailyForageSpawn, MaxSpawnedForageAtOnce</li>
 *   <li>Each forage entry has a season filter and a chance</li>
 *   <li>Random position within zone bounds; SDV uses 11 attempts, this map uses 30 for denser MC terrain</li>
 *   <li>Must be on top of a natural spawnable surface (public areas) or sand (Beach/Desert)</li>
 *   <li>Must be outdoors (sky visible) for non-beach zones</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class ForageSpawnService {

    private static final String INIT_DATA_ID = "stardewcraft_forage_init";

    private ForageSpawnService() {}

    // ======================== Forage Entry ========================

    private record ForageEntry(Supplier<? extends Block> block, int season, double chance) {
        /** season = -1 means all seasons */
        boolean matchesSeason(int currentSeason) {
            return season == -1 || season == currentSeason;
        }
    }

    // ======================== Zone Definition ========================

    /**
     * A rectangular region in the Stardew dimension where forage can spawn.
     */
    private record ZoneRect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int weight) {
        boolean containsSurfaceY(int y) {
            return y >= minY && y <= maxY;
        }
    }


    private record ForageZone(
            String name,
            List<ZoneRect> rects,
            List<ForageEntry> entries,
            int minDailySpawn,
            int maxDailySpawn,
            int maxSpawnedAtOnce,
            SurfaceType surface,
            StardewRegion preciseRegion
    ) {}

    /** 表面要求：NATURAL = 星露谷室外自然可刷地表；SAND = 必须露天沙子。 */
    private enum SurfaceType { NATURAL, SAND }

    // Season constants used by runtime data and forest-farm forage.
    private static final int SPRING = 0, SUMMER = 1, FALL = 2, WINTER = 3;

    // ======================== Main Entry Point ========================

    /**
     * Called once per day from StardewTimeManager. Replicates SDV GameLocation.spawnObjects().
     */
    public static void onNewDay(ServerLevel level, int season) {
        RandomSource random = level.getRandom();
        StardewCraft.LOGGER.info("[ForageSpawn] onNewDay called, season={}", season);

        int totalSpawned = 0;
        for (ForageZone zone : runtimeZones(level)) {
            // Filter entries for current season
            List<ForageEntry> possibleForage = new ArrayList<>();
            for (ForageEntry entry : zone.entries) {
                if (entry.matchesSeason(season)) {
                    possibleForage.add(entry);
                }
            }
            if (possibleForage.isEmpty()) {
                StardewCraft.LOGGER.info("[ForageSpawn] {} zone: no forage entries for season {}", zone.name, season);
                continue;
            }

            // Count existing forage blocks in zone (lightweight heightmap-based scan)
            int existingCount = countForageInZone(level, zone);
            if (existingCount >= zone.maxSpawnedAtOnce) {
                StardewCraft.LOGGER.info("[ForageSpawn] {} zone: already at max ({}/{})",
                        zone.name, existingCount, zone.maxSpawnedAtOnce);
                continue;
            }

            // Determine number to spawn (SDV: random between min and max inclusive)
            int numberToSpawn = zone.minDailySpawn + random.nextInt(
                    zone.maxDailySpawn - zone.minDailySpawn + 1);
            numberToSpawn = Math.min(numberToSpawn, zone.maxSpawnedAtOnce - existingCount);

            StardewCraft.LOGGER.info("[ForageSpawn] {} zone: existing={}, toSpawn={}, possibleEntries={}",
                    zone.name, existingCount, numberToSpawn, possibleForage.size());

            int spawned = 0;
            for (int i = 0; i < numberToSpawn; i++) {
                // SDV: up to 30 attempts per spawn (raised from 11 to compensate for
                // densely decorated terrain with grass/flowers occupying positions)
                for (int attempt = 0; attempt < 30; attempt++) {
                    // Pick random rect (with weight for beach second rect)
                    ZoneRect rect = pickRandomRect(zone, random);

                    // Random position within rect
                    int x = rect.minX + random.nextInt(rect.maxX - rect.minX + 1);
                    int z = rect.minZ + random.nextInt(rect.maxZ - rect.minZ + 1);

                    // Skip if chunk not loaded
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;

                    // Use heightmap that ignores leaves to find surface quickly
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos surfacePos = new BlockPos(x, surfaceY, z);

                    // If the heightmap surface is a replaceable plant (weeds, flowers, etc.),
                    // look below it for the real solid ground (e.g. grass_block).
                    BlockState surfaceState = level.getBlockState(surfacePos);
                    if (isReplaceablePlant(surfaceState)) {
                        surfacePos = surfacePos.below();
                        surfaceState = level.getBlockState(surfacePos);
                    }
                    if (!rect.containsSurfaceY(surfacePos.getY())) continue;
                    BlockPos placePos = surfacePos.above();
                    if (!insidePreciseRegion(level, zone, surfacePos)) {
                        continue;
                    }

                    // Validate surface block
                    if (surfaceState.isAir() || surfaceState.getFluidState().isSource()) continue;

                    // Check placement conditions
                    if (!canPlaceForage(level, surfacePos, placePos, zone.surface)) continue;

                    // Pick a random forage entry and apply chance
                    ForageEntry chosen = possibleForage.get(random.nextInt(possibleForage.size()));
                    if (random.nextDouble() > chosen.chance) continue;

                    // Remove any replaceable plant at the placement position before placing forage
                    BlockState existing = level.getBlockState(placePos);
                    if (!existing.isAir() && isReplaceablePlant(existing)) {
                        level.destroyBlock(placePos, false);
                    }

                    // Place the block
                    level.setBlock(placePos, chosen.block.get().defaultBlockState(), Block.UPDATE_ALL);
                    spawned++;
                    break; // success, move to next spawn slot
                }
            }

            totalSpawned += spawned;
            StardewCraft.LOGGER.info("[ForageSpawn] {} zone: spawned {} forage blocks", zone.name, spawned);
        }
        StardewCraft.LOGGER.info("[ForageSpawn] Day complete: total spawned = {}", totalSpawned);
    }

    // ======================== Helpers ========================

    private static List<ForageZone> runtimeZones(ServerLevel level) {
        List<ForageZone> result = new ArrayList<>();
        for (var registered : ForageZoneData.available(level)) {
            StardewForageZoneDefinition definition = registered.getValue();
            List<ZoneRect> rects = definition.areas().stream()
                    .map(area -> new ZoneRect(area.minX(), area.minY(), area.minZ(),
                            area.maxX(), area.maxY(), area.maxZ(), area.weight()))
                    .toList();
            List<ForageEntry> entries = new ArrayList<>();
            for (StardewForageZoneDefinition.Entry entry : definition.entries()) {
                if (!BuiltInRegistries.BLOCK.containsKey(entry.block())) {
                    StardewCraft.LOGGER.error("[Forage data] Zone {} references unknown block {}",
                            registered.getKey(), entry.block());
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.get(entry.block());
                for (String season : entry.seasons()) {
                    entries.add(new ForageEntry(() -> block, seasonIndex(season), entry.chance()));
                }
            }
            if (entries.isEmpty()) continue;
            result.add(new ForageZone(
                    registered.getKey().toString(),
                    rects,
                    List.copyOf(entries),
                    definition.minDailySpawn(),
                    definition.maxDailySpawn(),
                    definition.maxSpawnedAtOnce(),
                    definition.surface() == StardewForageZoneDefinition.Surface.SAND
                            ? SurfaceType.SAND : SurfaceType.NATURAL,
                    StardewRegions.get(registered.getKey())
                            .filter(region -> region.dimension().equals(
                                    level.dimension().location()))
                            .orElse(null)));
        }
        return List.copyOf(result);
    }

    private static int seasonIndex(String season) {
        return switch (season) {
            case "summer" -> SUMMER;
            case "fall" -> FALL;
            case "winter" -> WINTER;
            default -> SPRING;
        };
    }

    private static ZoneRect pickRandomRect(ForageZone zone, RandomSource random) {
        List<ZoneRect> rects = zone.rects;
        if (rects.size() == 1) return rects.get(0);
        int totalWeight = rects.stream().mapToInt(ZoneRect::weight).sum();
        int roll = random.nextInt(totalWeight);
        for (ZoneRect rect : rects) {
            roll -= rect.weight();
            if (roll < 0) return rect;
        }
        return rects.getLast();
    }

    /**
     * Check if forage can be placed at placePos on top of surfacePos.
     */
    private static boolean canPlaceForage(ServerLevel level, BlockPos surfacePos, BlockPos placePos,
                                          SurfaceType surface) {
        BlockState surfaceState = level.getBlockState(surfacePos);
        BlockState placeState = level.getBlockState(placePos);

        // Must be air or a replaceable plant (grass, flowers, ferns) at placement position
        if (!placeState.isAir() && !isReplaceablePlant(placeState)) return false;

        // Must see sky (outdoors check)
        if (!level.canSeeSky(placePos)) return false;

        return switch (surface) {
            // SDV uses the map's Back-layer "Spawnable" property, not only grass.
            // In this MC map, public valley spawnable ground may be grass or yellow/natural dirt.
            case NATURAL -> isNaturalForageSurface(surfaceState);
            case SAND -> surfaceState.is(Blocks.SAND);
        };
    }

    private static boolean isNaturalForageSurface(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.ARTIFACT_SPOT_DIRT.get()) {
            return false;
        }
        if (block == Blocks.GRASS_BLOCK || block == ModBlocks.YELLOW_DIRT.get()) {
            return true;
        }
        return state.is(BlockTags.DIRT);
    }

    /**
     * Returns true if the block state is a weak decorative plant that forage can replace.
     * Includes short grass, tall grass, flowers, ferns, and double-tall plants.
     */
    private static boolean isReplaceablePlant(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ForageBlock) return false;
        // Our mod's wild weeds (杂草)
        if (block instanceof com.stardew.craft.block.nature.WildWeedsBlock) return true;
        // Short grass and fern
        if (block == Blocks.SHORT_GRASS || block == Blocks.FERN) return true;
        // Tall grass and large fern
        if (block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) return true;
        // All vanilla small flowers (poppy, dandelion, cornflower, etc.)
        if (block instanceof FlowerBlock) return true;
        // Double-tall flowers (sunflower, lilac, rose bush, peony)
        if (block instanceof DoublePlantBlock) return true;
        // Generic bush check for any modded short plants
        if (block instanceof TallGrassBlock) return true;
        // Check if the block is replaceable by world generation (covers most decorative plants)
        return state.canBeReplaced();
    }

    /**
     * Count existing ForageBlock instances in a zone.
     * Only scans loaded chunks. The scan is exact so old unpicked forage still counts
     * against MaxSpawnedForageAtOnce instead of allowing extra seasonal spawns nearby.
     */
    private static int countForageInZone(ServerLevel level, ForageZone zone) {
        int count = 0;
        for (ZoneRect rect : zone.rects) {
            for (int x = rect.minX; x <= rect.maxX; x++) {
                for (int z = rect.minZ; z <= rect.maxZ; z++) {
                    // Skip unloaded chunks
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;

                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (!rect.containsSurfaceY(surfaceY)) continue;
                    if (!insidePreciseRegion(
                            level, zone,
                            new BlockPos(x, surfaceY, z))) {
                        continue;
                    }

                    count += countForageAtColumn(level, x, z);
                }
            }
        }
        return count;
    }

    private static boolean insidePreciseRegion(
            ServerLevel level,
            ForageZone zone,
            BlockPos position
    ) {
        return zone.preciseRegion == null
                || zone.preciseRegion.contains(
                        level.dimension().location(), position);
    }

    private static int countForageAtColumn(ServerLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int count = 0;
        for (int y = surfaceY - 1; y <= surfaceY + 3; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).getBlock() instanceof ForageBlock) {
                count++;
            }
        }
        return count;
    }

    // ======================== First-Day Initial Spawn ========================

    /**
     * Called on first entry into the Stardew dimension. Ensures forage exists on Day 1.
     * Uses SavedData to guarantee it only runs once per world.
     */
    public static void ensureInitialSpawn(ServerLevel level, int season) {
        if (!level.dimension().equals(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY)) return;

        ForageInitData data = level.getDataStorage().computeIfAbsent(
                ForageInitData.factory(), INIT_DATA_ID);
        if (data.isInitialized()) return;

        StardewCraft.LOGGER.info("[ForageSpawn] Running first-day initial forage spawn (season={})", season);
        onNewDay(level, season);
        data.markInitialized();
    }

    public static class ForageInitData extends SavedData {
        private boolean initialized;

        public ForageInitData() {}

        private ForageInitData(CompoundTag tag) {
            this.initialized = tag.getBoolean("Initialized");
        }

        public boolean isInitialized() { return initialized; }

        public void markInitialized() {
            this.initialized = true;
            setDirty();
        }

        @Override
        @Nonnull
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            tag.putBoolean("Initialized", initialized);
            return tag;
        }

        public static SavedData.Factory<ForageInitData> factory() {
            return new SavedData.Factory<>(ForageInitData::new, (tag, provider) -> new ForageInitData(tag));
        }
    }

    // ======================== Forest Farm Forage ========================

    /**
     * SDV parity: Forest farm spawns seasonal forage in its dedicated forage zone daily.
     * Called once per day from StardewTimeManager, after the public-area onNewDay().
     *
     * <p>Items per season (equal 25% weight each):
     * <ul>
     *   <li>Spring: Wild Horseradish, Daffodil, Leek, Dandelion</li>
     *   <li>Summer: Spice Berry, Sweet Pea, Fiddlehead Fern, Common Mushroom</li>
     *   <li>Fall: Wild Plum, Hazelnut, Blackberry, Chanterelle</li>
     *   <li>Winter: no spawning</li>
     * </ul>
     */
    private static final List<List<DeferredBlock<Block>>> FOREST_FARM_FORAGE = List.of(
            // Spring
            List.of(ModBlocks.FORAGE_WILD_HORSERADISH, ModBlocks.FORAGE_DAFFODIL,
                    ModBlocks.FORAGE_LEEK, ModBlocks.FORAGE_DANDELION),
            // Summer
            List.of(ModBlocks.FORAGE_SPICE_BERRY, ModBlocks.FORAGE_SWEET_PEA,
                    ModBlocks.FORAGE_FIDDLEHEAD_FERN, ModBlocks.FORAGE_COMMON_MUSHROOM),
            // Fall
            List.of(ModBlocks.FORAGE_WILD_PLUM, ModBlocks.FORAGE_HAZELNUT,
                    ModBlocks.FORAGE_BLACKBERRY, ModBlocks.FORAGE_CHANTERELLE)
    );

    private static final int FOREST_FARM_MIN_SPAWN = 1;
    private static final int FOREST_FARM_MAX_SPAWN = 4;
    private static final int FOREST_FARM_MAX_AT_ONCE = 6;

    /**
     * Spawns seasonal forage on all forest-type farms (public area + each player's farm instance).
     * Called from StardewTimeManager.advanceDayWithSleepTime().
     */
    public static void onNewDayForestFarms(ServerLevel level, int season) {
        if (season == WINTER || season < 0 || season > 2) return;

        List<DeferredBlock<Block>> possibleForage = FOREST_FARM_FORAGE.get(season);
        if (possibleForage.isEmpty()) return;

        com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        int totalSpawned = 0;

        for (com.stardew.craft.farm.FarmInstance farm : registry.getAllFarms()) {
            com.stardew.craft.api.v1.farm.StardewFarmLayout layout =
                    farm.getFarmLayout();
            if (layout == null || layout.forageZoneMin() == null || layout.forageZoneMax() == null) continue;

            BlockPos origin = farm.getOrigin();
            BlockPos zoneMin = origin.offset(layout.forageZoneMin());
            BlockPos zoneMax = origin.offset(layout.forageZoneMax());

            int minX = Math.min(zoneMin.getX(), zoneMax.getX());
            int maxX = Math.max(zoneMin.getX(), zoneMax.getX());
            int minZ = Math.min(zoneMin.getZ(), zoneMax.getZ());
            int maxZ = Math.max(zoneMin.getZ(), zoneMax.getZ());

            // Count existing forage in zone
            int existing = countForageInRect(level, minX, minZ, maxX, maxZ);
            if (existing >= FOREST_FARM_MAX_AT_ONCE) continue;

            RandomSource random = level.getRandom();
            int toSpawn = FOREST_FARM_MIN_SPAWN + random.nextInt(
                    FOREST_FARM_MAX_SPAWN - FOREST_FARM_MIN_SPAWN + 1);
            toSpawn = Math.min(toSpawn, FOREST_FARM_MAX_AT_ONCE - existing);

            int spawned = 0;
            for (int i = 0; i < toSpawn; i++) {
                for (int attempt = 0; attempt < 30; attempt++) {
                    int x = minX + random.nextInt(maxX - minX + 1);
                    int z = minZ + random.nextInt(maxZ - minZ + 1);
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;

                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos surfacePos = new BlockPos(x, surfaceY, z);
                    BlockState surfaceState = level.getBlockState(surfacePos);
                    if (isReplaceablePlant(surfaceState)) {
                        surfacePos = surfacePos.below();
                        surfaceState = level.getBlockState(surfacePos);
                    }
                    BlockPos placePos = surfacePos.above();

                    if (surfaceState.isAir() || surfaceState.getFluidState().isSource()) continue;
                    if (!canPlaceForage(level, surfacePos, placePos, SurfaceType.NATURAL)) continue;

                    // Equal probability among 4 items
                    DeferredBlock<Block> chosen = possibleForage.get(random.nextInt(possibleForage.size()));

                    BlockState existingState = level.getBlockState(placePos);
                    if (!existingState.isAir() && isReplaceablePlant(existingState)) {
                        level.destroyBlock(placePos, false);
                    }

                    level.setBlock(placePos, chosen.get().defaultBlockState(), Block.UPDATE_ALL);
                    spawned++;
                    break;
                }
            }
            totalSpawned += spawned;
            StardewCraft.LOGGER.info("[ForageSpawn] Forest farm ({}): spawned {} forage in zone",
                    farm.getOwnerName(), spawned);
        }

        if (totalSpawned > 0) {
            StardewCraft.LOGGER.info("[ForageSpawn] Forest farms total: {} forage spawned", totalSpawned);
        }
    }

    private static int countForageInRect(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                count += countForageAtColumn(level, x, z);
            }
        }
        return count;
    }
}
