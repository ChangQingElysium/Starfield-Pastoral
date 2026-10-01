package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Renewable hardwood sites from the Forest and Four Corners source farms. */
public final class ForestFarmDailyService {
    private static final List<BlockPos> FOREST_STUMP_SITES = List.of(
            new BlockPos(76, 25, 113),
            new BlockPos(87, 25, 114),
            new BlockPos(80, 25, 117),
            new BlockPos(89, 25, 126),
            new BlockPos(81, 25, 131),
            new BlockPos(85, 25, 141),
            new BlockPos(79, 25, 162),
            new BlockPos(92, 25, 168),
            new BlockPos(83, 25, 169),
            new BlockPos(98, 25, 180),
            new BlockPos(87, 25, 181),
            new BlockPos(81, 25, 186)
    );
    private static final List<BlockPos> FOUR_CORNERS_STUMP_SITES = List.of(
            new BlockPos(78, 25, 132)
    );

    private ForestFarmDailyService() {
    }

    public static void onNewDay(ServerLevel level) {
        int restored = 0;
        int refreshedWeeds = 0;
        for (FarmInstance farm : FarmInstanceRegistry.get(level.getServer()).getAllFarms()) {
            if (!farm.isInitialized()) continue;
            List<BlockPos> sites;
            if (farm.getFarmLayoutId().equals(
                    StardewFarmLayoutRegistry.builtinId(FarmType.FOREST))) {
                sites = FOREST_STUMP_SITES;
            } else if (farm.getFarmLayoutId().equals(
                    StardewFarmLayoutRegistry.builtinId(FarmType.FOUR_CORNERS))) {
                sites = FOUR_CORNERS_STUMP_SITES;
                refreshedWeeds += refreshFourCornersWeeds(level, farm);
            } else {
                continue;
            }
            for (BlockPos local : sites) {
                if (restoreStump(level, farm, farm.getOrigin().offset(local))) {
                    restored++;
                }
            }
        }
        if (restored > 0) {
            StardewCraft.LOGGER.info(
                    "[FARM_DAILY] Restored {} renewable farm hardwood stumps", restored);
        }
        if (refreshedWeeds > 0) StardewCraft.LOGGER.info(
                "[FARM_DAILY] Refreshed {} Four Corners seasonal weeds", refreshedWeeds);
    }

    private static int refreshFourCornersWeeds(ServerLevel level, FarmInstance farm) {
        // Original x<36,y<34 northwest rule, scaled onto the authored 288x272 map.
        BlockPos origin = farm.getOrigin();
        List<BlockPos> weeds = new ArrayList<>();
        for (int x = 0; x <= 129; x++) {
            for (int z = 0; z <= 115; z++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;
                var cursor = new BlockPos.MutableBlockPos(
                        worldX, level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1, worldZ);
                while (cursor.getY() >= origin.getY()) {
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.BARRIER)) {
                        cursor.move(0, -1, 0);
                        continue;
                    }
                    if (state.is(ModBlocks.WILD_WEEDS.get())) weeds.add(cursor.immutable());
                    break;
                }
            }
        }
        Collections.shuffle(weeds, new java.util.Random(level.getRandom().nextLong()));
        int season = com.stardew.craft.port.PortJava.clamp(StardewTimeManager.get().getCurrentSeason(), 0, 3);
        int changed = 0;
        for (BlockPos pos : weeds.subList(0, Math.min(6, weeds.size()))) {
            BlockState state = level.getBlockState(pos);
            if (state.getValue(WildWeedsBlock.SEASON) != season
                    && level.setBlock(pos, state.setValue(WildWeedsBlock.SEASON, season), Block.UPDATE_ALL)) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean restoreStump(
            ServerLevel level,
            FarmInstance farm,
            BlockPos main
    ) {
        Block block = ModBlocks.LARGE_STUMP.get();
        if (level.getBlockState(main).is(block)) {
            return false;
        }
        if (!(block instanceof ResourceClumpBlock clump)) {
            return false;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos bottom = main.offset(dx, 0, dz);
                BlockPos top = bottom.above();
                if (!farm.contains(bottom) || !farm.contains(top)
                        || !level.isLoaded(bottom)
                        || !FarmDebrisPlacementRules.isCompletelyOpen(level, bottom)
                        || !FarmDebrisPlacementRules.isCompletelyOpen(level, top)
                        || !FarmDebrisPlacementRules.isNaturalFarmGround(
                                level.getBlockState(bottom.below()))) {
                    return false;
                }
            }
        }

        BlockState state = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
        if (!level.setBlock(main, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (!clump.placeExtensions(level, main, state)) {
            level.removeBlock(main, false);
            return false;
        }
        return true;
    }
}
