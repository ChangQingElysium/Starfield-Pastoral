package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.SupplyCrateBlock;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.saveddata.SavedData;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Beach Farm's original daily beach-spawn loop, adapted to the authored MC shoreline. */
public final class BeachFarmDailyService {
    private static final int SOURCE_MAP_CELLS = 110 * 110;
    // Property tiles which also pass Farm.DayUpdate's AlwaysFront rejection.
    private static final double BEACH_SPAWN_TILE_CHANCE = 802.0D / SOURCE_MAP_CELLS;
    private static final double SEASONAL_GRASS_TILE_CHANCE = 337.0D / SOURCE_MAP_CELLS;
    private static final int WINTER = 3;

    /** Farm.DayUpdate's six-way starting pool; seaweed occupies cases four and five. */
    private static final List<DeferredBlock<Block>> BEACH_BASE_FORAGE = List.of(
            ModBlocks.FORAGE_CORAL,
            ModBlocks.FORAGE_MUSSEL,
            ModBlocks.FORAGE_COCKLE,
            ModBlocks.FORAGE_OYSTER,
            ModBlocks.FORAGE_SEAWEED,
            ModBlocks.FORAGE_SEAWEED
    );

    private static final List<List<DeferredBlock<Block>>> BASIC_SEASONAL_FORAGE = List.of(
            List.of(ModBlocks.FORAGE_WILD_HORSERADISH, ModBlocks.FORAGE_DAFFODIL,
                    ModBlocks.FORAGE_LEEK, ModBlocks.FORAGE_DANDELION),
            List.of(ModBlocks.FORAGE_SPICE_BERRY, ModBlocks.FORAGE_SWEET_PEA,
                    ModBlocks.FORAGE_GRAPE),
            List.of(ModBlocks.FORAGE_COMMON_MUSHROOM, ModBlocks.FORAGE_WILD_PLUM,
                    ModBlocks.FORAGE_HAZELNUT, ModBlocks.FORAGE_BLACKBERRY)
    );

    private BeachFarmDailyService() {}

    public static void onNewDay(ServerLevel level) {
        RandomSource random = level.getRandom();
        CounterData counter = CounterData.get(level);
        int absoluteDay = StardewTimeManager.get().getAbsoluteDay();
        int season = StardewTimeManager.get().getCurrentSeason();
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(level.getServer());
        Set<UUID> activeOwners = FarmDailyProcessHelper.getOnlineFarmOwners(level);
        FarmSpawnLayoutData.Layout layout = FarmSpawnLayoutData.forType(FarmType.BEACH);
        List<BlockPos> shoreWater = layout.positions("shore_water");
        List<BlockPos> beachSpawn = layout.positions("beach_spawn");
        List<BlockPos> seasonalGrass = layout.positions("seasonal_grass");
        int crates = 0;
        int forage = 0;

        for (FarmInstance farm : registry.getAllFarms()) {
            UUID registryKey = registry.getRegistryKey(farm);
            if (!farm.isInitialized() || registryKey == null
                    || (!activeOwners.contains(farm.getOwnerUUID())
                    && !activeOwners.contains(registryKey))
                    || !farm.getFarmLayoutId().equals(
                    StardewFarmLayoutRegistry.builtinId(FarmType.BEACH))) continue;

            int safety = 0;
            // Farm.DayUpdate: one map-tile roll for every successful 90%
            // continuation roll (mean nine rolls per active Beach Farm day).
            while (random.nextDouble() < 0.90D && safety++ < 128) {
                double tileRoll = random.nextDouble();
                if (tileRoll < BEACH_SPAWN_TILE_CHANCE) {
                    DeferredBlock<Block> chosen = BEACH_BASE_FORAGE.get(
                            random.nextInt(BEACH_BASE_FORAGE.size()));
                    int spawnNumber = counter.next();
                    boolean crate = absoluteDay > 1
                            && (random.nextDouble() < 0.15D || spawnNumber % 4 == 0);
                    if (crate) {
                        if (placeCrate(level, farm, shoreWater, random)) crates++;
                        continue;
                    }
                    if (absoluteDay > 1) {
                        if (random.nextDouble() < 0.10D) {
                            chosen = ModBlocks.FORAGE_SEA_URCHIN;
                        } else if (random.nextDouble() < 0.05D) {
                            chosen = ModBlocks.FORAGE_NAUTILUS_SHELL;
                        } else if (random.nextDouble() < 0.02D) {
                            chosen = ModBlocks.FORAGE_RAINBOW_SHELL;
                        }
                    }
                    if (placeForage(level, farm, beachSpawn, chosen,
                            FarmDebrisPlacementRules.GroundKind.SAND, random)) forage++;
                } else if (season != WINTER
                        && tileRoll < BEACH_SPAWN_TILE_CHANCE + SEASONAL_GRASS_TILE_CHANCE) {
                    List<DeferredBlock<Block>> pool = BASIC_SEASONAL_FORAGE.get(season);
                    DeferredBlock<Block> chosen = pool.get(random.nextInt(pool.size()));
                    if (placeForage(level, farm, seasonalGrass, chosen, null, random)) forage++;
                }
            }
        }
        if (crates > 0 || forage > 0) {
            StardewCraft.LOGGER.info(
                    "[FARM_DAILY] Beach Farms spawned {} shoreline forage and floated {} supply crates",
                    forage, crates);
        }
    }

    private static boolean placeCrate(ServerLevel level, FarmInstance farm,
                                      List<BlockPos> candidates, RandomSource random) {
        BlockPos local = candidates.get(random.nextInt(candidates.size()));
        BlockPos pos = farm.getOrigin().offset(local);
        FarmDailyProcessHelper.ensurePositionLoaded(level, pos);
        if (!validShoreWater(level, farm, pos)) return false;
        var state = ModBlocks.SUPPLY_CRATE.get().defaultBlockState()
                .setValue(SupplyCrateBlock.VARIANT, random.nextInt(3));
        return state.canSurvive(level, pos) && level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static boolean placeForage(ServerLevel level, FarmInstance farm,
                                       List<BlockPos> candidates, DeferredBlock<Block> forage,
                                       FarmDebrisPlacementRules.GroundKind requiredGround,
                                       RandomSource random) {
        BlockPos local = candidates.get(random.nextInt(candidates.size()));
        BlockPos column = farm.getOrigin().offset(local.getX(), 0, local.getZ());
        FarmDailyProcessHelper.ensurePositionLoaded(level, column);
        FarmDebrisPlacementRules.Surface surface = FarmDebrisPlacementRules.findBareSurface(
                level, farm, column.getX(), column.getZ());
        if (surface == null || requiredGround != null && surface.groundKind() != requiredGround) {
            return false;
        }
        if (requiredGround == null
                && surface.groundKind() != FarmDebrisPlacementRules.GroundKind.GRASS
                && surface.groundKind() != FarmDebrisPlacementRules.GroundKind.DARK_GRASS) {
            return false;
        }
        BlockPos place = surface.place();
        var state = forage.get().defaultBlockState();
        if (!level.canSeeSky(place) || !state.canSurvive(level, place)) return false;
        return level.setBlock(place, state, Block.UPDATE_ALL);
    }

    private static boolean validShoreWater(ServerLevel level, FarmInstance farm, BlockPos pos) {
        if (!farm.contains(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return false;
        var support = level.getBlockState(pos.below());
        var fluid = support.getFluidState();
        if (!(support.getBlock() instanceof LiquidBlock)
                || !fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
        for (int distance = 1; distance <= 2; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    var shore = level.getBlockState(pos.offset(dx, -1, dz));
                    String path = BuiltInRegistries.BLOCK.getKey(shore.getBlock()).getPath();
                    if (shore.is(Blocks.SAND) || path.contains("sand")) return true;
                }
            }
        }
        return false;
    }

    private static final class CounterData extends SavedData {
        private static final String NAME = "stardew_beach_farm_spawns";
        private int count;

        static CounterData get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(
                    com.stardew.craft.port.PortSavedData.loader(new Factory<>(CounterData::new, CounterData::load)), com.stardew.craft.port.PortSavedData.constructor(new Factory<>(CounterData::new, CounterData::load)), NAME);
        }

        int next() {
            count = Math.addExact(count, 1);
            setDirty();
            return count;
        }

        @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
            tag.putInt("Count", count);
            return tag;
        }

        static CounterData load(CompoundTag tag, HolderLookup.Provider registries) {
            CounterData data = new CounterData();
            data.count = Math.max(0, tag.getInt("Count"));
            return data;
        }
    }
}
