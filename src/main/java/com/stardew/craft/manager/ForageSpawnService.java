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
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredBlock;

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
 *   <li>Natural ground may be shaded by forest leaves; the heightmap excludes roofs and solid structures</li>
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
        List<ForageZone> zones = runtimeZones(level);
        for (ForageZone zone : zones) {
            if (zone.name.endsWith(":forest") && season == SPRING) {
                spawnSpringOnionClusters(level, random, zone);
            } else if (zone.name.endsWith(":beach")) {
                spawnBeachTidePools(level, random, zone, season);
            }
        }
        ForageInitData data = forageInitData(level);
        int absoluteDay = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        for (ForageZone zone : zones) {
            totalSpawned += spawnZoneOnce(level, zone, season, absoluteDay, data, random);
        }
        StardewCraft.LOGGER.info("[ForageSpawn] Day complete: total spawned = {}", totalSpawned);
    }

    /** Finish an unloaded zone's current day when a player reaches it, without replaying missed days. */
    public static void ensureZoneSpawned(ServerLevel level, net.minecraft.resources.ResourceLocation zoneId, int season) {
        ForageInitData data = forageInitData(level);
        int absoluteDay = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        if (data.wasSpawned(zoneId.toString(), absoluteDay)) return;
        for (ForageZone zone : runtimeZones(level)) {
            if (zone.name.equals(zoneId.toString())) {
                spawnZoneOnce(level, zone, season, absoluteDay, data, level.getRandom());
                return;
            }
        }
    }

    private static int spawnZoneOnce(ServerLevel level, ForageZone zone, int season, int absoluteDay,
                                     ForageInitData data, RandomSource random) {
        if (data.wasSpawned(zone.name, absoluteDay) || !hasLoadedChunks(level, zone)) return 0;
        int spawned = spawnZone(level, zone, season, random);
        data.markSpawned(zone.name, absoluteDay);
        return spawned;
    }

    private static boolean hasLoadedChunks(ServerLevel level, ForageZone zone) {
        for (ZoneRect rect : zone.rects) {
            for (int x = rect.minX >> 4; x <= rect.maxX >> 4; x++) {
                for (int z = rect.minZ >> 4; z <= rect.maxZ >> 4; z++) {
                    if (level.hasChunk(x, z)) return true;
                }
            }
        }
        return false;
    }

    private static int spawnZone(ServerLevel level, ForageZone zone, int season, RandomSource random) {
        // Filter entries for current season
        List<ForageEntry> possibleForage = new ArrayList<>();
        for (ForageEntry entry : zone.entries) {
            if (entry.matchesSeason(season)) {
                possibleForage.add(entry);
            }
        }
        if (possibleForage.isEmpty()) {
            StardewCraft.LOGGER.info("[ForageSpawn] {} zone: no forage entries for season {}", zone.name, season);
            return 0;
        }

        // Count existing forage blocks in zone (lightweight heightmap-based scan)
        int existingCount = countForageInZone(level, zone);
        if (existingCount >= zone.maxSpawnedAtOnce) {
            StardewCraft.LOGGER.info("[ForageSpawn] {} zone: already at max ({}/{})",
                    zone.name, existingCount, zone.maxSpawnedAtOnce);
            return 0;
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

        StardewCraft.LOGGER.info("[ForageSpawn] {} zone: spawned {} forage blocks", zone.name, spawned);
        return spawned;
    }

    // ======================== Forest spring onions / beach tide pools ========================

    /** Forest.DayUpdate: in spring, 7 clusters of up to 16 open tiles, each tile kept with 1 - 0.15 * distance. */
    private static void spawnSpringOnionClusters(ServerLevel level, RandomSource random, ForageZone zone) {
        for (int cluster = 0; cluster < 7; cluster++) {
            ZoneRect rect = pickRandomRect(zone, random);
            int originX = rect.minX + random.nextInt(rect.maxX - rect.minX + 1);
            int originZ = rect.minZ + random.nextInt(rect.maxZ - rect.minZ + 1);
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            List<BlockPos> open = new ArrayList<>();
            List<int[]> openColumns = new ArrayList<>();
            queue.add(new int[]{originX, originZ});
            seen.add(BlockPos.asLong(originX, 0, originZ));
            while (!queue.isEmpty() && open.size() < 16) {
                int[] column = queue.poll();
                BlockPos place = forageSite(level, zone, rect, column[0], column[1], SurfaceType.NATURAL);
                if (place == null) {
                    continue;
                }
                open.add(place);
                openColumns.add(column);
                for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = column[0] + offset[0];
                    int nz = column[1] + offset[1];
                    if (nx < rect.minX || nx > rect.maxX || nz < rect.minZ || nz > rect.maxZ
                            || !seen.add(BlockPos.asLong(nx, 0, nz))) {
                        continue;
                    }
                    queue.add(new int[]{nx, nz});
                }
            }
            for (int i = 0; i < open.size(); i++) {
                int[] column = openColumns.get(i);
                double distance = Math.hypot(column[0] - originX, column[1] - originZ);
                if (random.nextDouble() < 1.0 - distance * 0.15) {
                    BlockPos place = open.get(i);
                    BlockState existing = level.getBlockState(place);
                    if (!existing.isAir() && isReplaceablePlant(existing)) {
                        level.destroyBlock(place, false);
                    }
                    level.setBlock(place, ModBlocks.FORAGE_SPRING_ONION.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * Beach.DayUpdate tide pools: coral (80%) or sea urchin (20%) with chance 1, 1/2, 1/4...; in summer on
     * the 12th-14th a further 1.5, 1.5/1.1, ... chain. The project has no tide-pool rectangle, so the whole beach zone is used.
     */
    private static void spawnBeachTidePools(ServerLevel level, RandomSource random, ForageZone zone, int season) {
        double chance = 1.0;
        while (random.nextDouble() < chance) {
            placeTidePoolItem(level, random, zone);
            chance /= 2.0;
        }
        int day = com.stardew.craft.time.StardewTimeManager.get().getCurrentDay();
        if (season == SUMMER && day >= 12 && day <= 14) {
            chance = 1.5;
            while (random.nextDouble() < chance) {
                placeTidePoolItem(level, random, zone);
                chance /= 1.1;
            }
        }
    }

    private static void placeTidePoolItem(ServerLevel level, RandomSource random, ForageZone zone) {
        Block block = random.nextDouble() < 0.2 ? ModBlocks.FORAGE_SEA_URCHIN.get() : ModBlocks.FORAGE_CORAL.get();
        for (int attempt = 0; attempt < 30; attempt++) {
            ZoneRect rect = pickRandomRect(zone, random);
            int x = rect.minX + random.nextInt(rect.maxX - rect.minX + 1);
            int z = rect.minZ + random.nextInt(rect.maxZ - rect.minZ + 1);
            BlockPos place = forageSite(level, zone, rect, x, z, SurfaceType.SAND);
            if (place == null) {
                continue;
            }
            BlockState existing = level.getBlockState(place);
            if (!existing.isAir() && isReplaceablePlant(existing)) {
                level.destroyBlock(place, false);
            }
            level.setBlock(place, block.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
    }

    /** Placement position for forage in this column, or null when the column is not a valid site. */
    private static BlockPos forageSite(ServerLevel level, ForageZone zone, ZoneRect rect, int x, int z, SurfaceType surface) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        BlockState surfaceState = level.getBlockState(surfacePos);
        if (isReplaceablePlant(surfaceState)) {
            surfacePos = surfacePos.below();
            surfaceState = level.getBlockState(surfacePos);
        }
        if (!rect.containsSurfaceY(surfacePos.getY()) || !insidePreciseRegion(level, zone, surfacePos)) return null;
        if (surfaceState.isAir() || surfaceState.getFluidState().isSource()) return null;
        BlockPos placePos = surfacePos.above();
        return canPlaceForage(level, surfacePos, placePos, surface) ? placePos : null;
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
        return com.stardew.craft.port.PortJava.getLast(rects);
    }

    /**
     * Check if forage can be placed at placePos on top of surfacePos.
     */
    private static boolean canPlaceForage(ServerLevel level, BlockPos surfacePos, BlockPos placePos,
                                          SurfaceType surface) {
        BlockState surfaceState = level.getBlockState(surfacePos);
        BlockState placeState = level.getBlockState(placePos);

        // Fluids are replaceable block states, but they are never replaceable forage sites.
        if (!placeState.getFluidState().isEmpty()) return false;

        // Must be air or a replaceable plant (grass, flowers, ferns) at placement position
        if (!placeState.isAir() && !isReplaceablePlant(placeState)) return false;

        // Forest ground is spawnable under leaf canopies. Solid roofs are rejected by the heightmap
        // and natural-surface check at forageSite; beaches retain their open-sand requirement.
        if (surface == SurfaceType.SAND && !level.canSeeSky(placePos)) return false;

        return switch (surface) {
            // SDV uses the map's Back-layer "Spawnable" property, not only grass.
            // In this MC map, public valley spawnable ground may be grass or yellow/natural dirt.
            case NATURAL -> isNaturalForageSurface(surfaceState);
            case SAND -> surfaceState.is(Blocks.SAND) || surfaceState.is(ModBlocks.SAND.get());
        };
    }

    private static boolean isNaturalForageSurface(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.GrassBlock || (block == ModBlocks.YELLOW_DIRT.get() || block == ModBlocks.DIRT.get())) {
            return true;
        }
        return state.is(BlockTags.DIRT);
    }

    /**
     * Returns true if the block state is a weak decorative plant that forage can replace.
     * Includes short grass, tall grass, flowers, ferns, and double-tall plants.
     */
    private static boolean isReplaceablePlant(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        if (block instanceof ForageBlock) return false;
        // Our mod's wild weeds (杂草)
        if (block instanceof com.stardew.craft.block.nature.WildWeedsBlock) return true;
        // Short grass and fern
        if (block == Blocks.GRASS || block == Blocks.FERN) return true;
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
            Block block = level.getBlockState(pos).getBlock();
            // Spring-onion clusters and tide-pool items are ordinary objects in the original,
            // not spawned forage, so they never count against MaxSpawnedForageAtOnce.
            if (block instanceof ForageBlock && block != ModBlocks.FORAGE_SPRING_ONION.get()
                    && block != ModBlocks.FORAGE_CORAL.get() && block != ModBlocks.FORAGE_SEA_URCHIN.get()) {
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

        ForageInitData data = forageInitData(level);
        if (data.isInitialized()) return;

        StardewCraft.LOGGER.info("[ForageSpawn] Running first-day initial forage spawn (season={})", season);
        onNewDay(level, season);
        data.markInitialized();
    }

    public static class ForageInitData extends SavedData {
        private boolean initialized;
        private final java.util.Map<String, Integer> lastSpawnedDays = new java.util.HashMap<>();

        public ForageInitData() {}

        private ForageInitData(CompoundTag tag) {
            this.initialized = tag.getBoolean("Initialized");
            CompoundTag days = tag.getCompound("LastSpawnedDays");
            for (String zone : days.getAllKeys()) lastSpawnedDays.put(zone, days.getInt(zone));
        }

        public boolean isInitialized() { return initialized; }

        public void markInitialized() {
            this.initialized = true;
            setDirty();
        }

        public boolean wasSpawned(String zone, int absoluteDay) {
            return lastSpawnedDays.getOrDefault(zone, Integer.MIN_VALUE) == absoluteDay;
        }

        public void markSpawned(String zone, int absoluteDay) {
            lastSpawnedDays.put(zone, absoluteDay);
            setDirty();
        }

        @Override
        @Nonnull
        public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
            tag.putBoolean("Initialized", initialized);
            CompoundTag days = new CompoundTag();
            lastSpawnedDays.forEach(days::putInt);
            tag.put("LastSpawnedDays", days);
            return tag;
        }

        public static com.stardew.craft.port.PortSavedData.Factory<ForageInitData> factory() {
            return new com.stardew.craft.port.PortSavedData.Factory<>(ForageInitData::new, (tag, provider) -> new ForageInitData(tag));
        }
    }

    private static ForageInitData forageInitData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(ForageInitData.factory()), com.stardew.craft.port.PortSavedData.constructor(ForageInitData.factory()), INIT_DATA_ID);
    }

    // ======================== Forest Farm Forage ========================

    /**
     * SDV parity: Forest farm spawns seasonal forage in its dedicated forage zone daily.
     * Called once per day from StardewTimeManager, after the public-area onNewDay().
     *
     * <p>Items per season (equal 25% weight each):
     * <ul>
     *   <li>Spring: Wild Horseradish, Dandelion, Leek, Morel</li>
     *   <li>Summer: Spice Berry, Grape, Sweet Pea, Common Mushroom</li>
     *   <li>Fall: Chanterelle, Red Mushroom, Purple Mushroom, Common Mushroom</li>
     *   <li>Winter: no spawning</li>
     * </ul>
     */
    private static final List<List<DeferredBlock<Block>>> FOREST_FARM_FORAGE = List.of(
            // Spring
            List.of(ModBlocks.FORAGE_WILD_HORSERADISH, ModBlocks.FORAGE_DANDELION,
                    ModBlocks.FORAGE_LEEK, ModBlocks.FORAGE_MOREL),
            // Summer
            List.of(ModBlocks.FORAGE_SPICE_BERRY, ModBlocks.FORAGE_GRAPE,
                    ModBlocks.FORAGE_SWEET_PEA, ModBlocks.FORAGE_COMMON_MUSHROOM),
            // Fall
            List.of(ModBlocks.FORAGE_CHANTERELLE, ModBlocks.FORAGE_RED_MUSHROOM,
                    ModBlocks.FORAGE_PURPLE_MUSHROOM, ModBlocks.FORAGE_COMMON_MUSHROOM)
    );
    // Farm_Foraging.tmx candidates after Farm.DayUpdate's AlwaysFront check.
    private static final double FOREST_STRIP_TILE_CHANCE = 395.0D / (18 * 65);
    private static final double FOREST_GRASS_TILE_CHANCE = 1325.0D / (80 * 65);

    /**
     * Spawns seasonal forage on all forest-type farms (public area + each player's farm instance).
     * Called from StardewTimeManager.advanceDayWithSleepTime().
     */
    public static void onNewDayForestFarms(ServerLevel level, int season) {
        if (season == WINTER || season < 0 || season > 2) return;

        List<DeferredBlock<Block>> possibleForage = FOREST_FARM_FORAGE.get(season);
        if (possibleForage.isEmpty()) return;

        com.stardew.craft.farm.FarmInstanceRegistry registry =
                com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer());
        java.util.Set<java.util.UUID> activeOwners =
                com.stardew.craft.farm.FarmDailyProcessHelper.getOnlineFarmOwners(level);
        var spawnLayout = com.stardew.craft.farm.FarmSpawnLayoutData
                .forType(com.stardew.craft.farm.FarmType.FOREST);
        List<BlockPos> generalCandidates = spawnLayout.positions("general");
        List<BlockPos> forestStripCandidates = spawnLayout.positions("forest_strip");
        int totalSpawned = 0;

        for (com.stardew.craft.farm.FarmInstance farm : registry.getAllFarms()) {
            java.util.UUID registryKey = registry.getRegistryKey(farm);
            if (!farm.isInitialized() || registryKey == null
                    || (!activeOwners.contains(farm.getOwnerUUID())
                    && !activeOwners.contains(registryKey))
                    || !farm.getFarmLayoutId().equals(
                    com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
                            .builtinId(com.stardew.craft.farm.FarmType.FOREST))) continue;

            RandomSource random = level.getRandom();
            int spawned = 0;
            int safety = 0;
            while (random.nextDouble() < 0.75D && safety++ < 64) {
                // Half the original rolls target its dedicated forest strip;
                // the other half target any Grass tile. The larger MC map uses
                // schematic-derived candidate columns rather than a rectangle,
                // so every authored patch participates without loading the farm.
                boolean forestStrip = random.nextBoolean();
                double candidateChance = forestStrip
                        ? FOREST_STRIP_TILE_CHANCE : FOREST_GRASS_TILE_CHANCE;
                if (random.nextDouble() >= candidateChance) continue;
                List<BlockPos> candidates = forestStrip ? forestStripCandidates : generalCandidates;
                BlockPos local = candidates.get(random.nextInt(candidates.size()));
                BlockPos column = farm.getOrigin().offset(local.getX(), 0, local.getZ());
                com.stardew.craft.farm.FarmDailyProcessHelper.ensurePositionLoaded(level, column);
                int x = column.getX();
                int z = column.getZ();

                int surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos surfacePos = new BlockPos(x, surfaceY, z);
                BlockState surfaceState = level.getBlockState(surfacePos);
                if (isReplaceablePlant(surfaceState)) {
                    surfacePos = surfacePos.below();
                    surfaceState = level.getBlockState(surfacePos);
                }
                BlockPos placePos = surfacePos.above();
                if (surfaceState.isAir() || surfaceState.getFluidState().isSource()) continue;

                com.stardew.craft.farm.FarmDebrisPlacementRules.GroundKind ground =
                        com.stardew.craft.farm.FarmDebrisPlacementRules.groundKind(surfaceState);
                if (!forestStrip
                        && ground != com.stardew.craft.farm.FarmDebrisPlacementRules.GroundKind.GRASS
                        && ground != com.stardew.craft.farm.FarmDebrisPlacementRules.GroundKind.DARK_GRASS) {
                    continue;
                }
                if (!com.stardew.craft.farm.FarmDebrisPlacementRules.isNaturalFarmGround(surfaceState)
                        || !level.getBlockState(placePos).isAir()
                        || !canPlaceForage(level, surfacePos, placePos, SurfaceType.NATURAL)) {
                    continue;
                }

                DeferredBlock<Block> chosen = possibleForage.get(
                        random.nextInt(possibleForage.size()));
                level.setBlock(placePos, chosen.get().defaultBlockState(), Block.UPDATE_ALL);
                spawned++;
            }
            totalSpawned += spawned;
            StardewCraft.LOGGER.info("[ForageSpawn] Forest farm ({}): spawned {} forage on authored terrain",
                    farm.getOwnerName(), spawned);
        }

        if (totalSpawned > 0) {
            StardewCraft.LOGGER.info("[ForageSpawn] Forest farms total: {} forage spawned", totalSpawned);
        }
    }

}
