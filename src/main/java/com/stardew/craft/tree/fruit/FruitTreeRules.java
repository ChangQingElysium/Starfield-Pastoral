package com.stardew.craft.tree.fruit;

import com.stardew.craft.api.v1.agriculture.StardewTreeData;
import com.stardew.craft.api.v1.internal.tree.StardewTreeRuntimeRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.tree.WildTreeSaplingBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class FruitTreeRules {
    private FruitTreeRules() {
    }

    public static boolean isValidGround(BlockState ground) {
        if (ground.getBlock() instanceof FarmBlock) {
            return true;
        }
        if (ground.getBlock() == ModBlocks.YELLOW_DIRT.get()) {
            return true;
        }
        return ground.is(BlockTags.DIRT);
    }

    public static boolean canPlantSapling(LevelReader level, BlockPos lowerPos) {
        if (!isValidGround(level.getBlockState(lowerPos.below()))) {
            return false;
        }
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(lowerPos.above());
        return lowerState.canBeReplaced()
                && upperState.canBeReplaced()
                && !isTooCloseToAnotherTree(level, lowerPos)
                && !isGrowthBlocked(level, lowerPos);
    }

    public static boolean isGrowthBlocked(LevelReader level, BlockPos lowerPos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos check = lowerPos.offset(dx, 0, dz);
                if (!isTileClearForGrowth(level.getBlockState(check)) || !isTileClearForGrowth(level.getBlockState(check.above()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean canFruitToday(Level level, BlockPos pos, FruitTreeType type) {
        if (level.isClientSide()) {
            return true;
        }
        return SeasonLocationRules.seedsIgnoreSeasonsHere(level, pos)
                || StardewTimeManager.get().getCurrentSeason() == type.season();
    }

    public static boolean canFruitToday(Level level, BlockPos pos, FruitTreeType type, StardewTreeData data) {
        if (level.isClientSide()) {
            return true;
        }
        if (SeasonLocationRules.seedsIgnoreSeasonsHere(level, pos)) {
            return true;
        }
        if (data == null || data.fruitSeasons().isEmpty()) {
            return StardewTimeManager.get().getCurrentSeason() == type.season();
        }
        String currentSeason = switch (StardewTimeManager.get().getCurrentSeason()) {
            case 0 -> "spring";
            case 1 -> "summer";
            case 2 -> "fall";
            case 3 -> "winter";
            default -> "";
        };
        return data.fruitSeasons().stream().anyMatch(currentSeason::equalsIgnoreCase);
    }

    public static boolean isWinterTreeHere(Level level, BlockPos pos) {
        return !SeasonLocationRules.seedsIgnoreSeasonsHere(level, pos)
                && StardewTimeManager.get().getCurrentSeason() == 3;
    }

    private static boolean isTileClearForGrowth(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isTooCloseToAnotherTree(LevelReader level, BlockPos lowerPos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos check = lowerPos.offset(dx, 0, dz);
                if (isTreeAt(level, check) || isTreeAt(level, check.above()) || isTreeAt(level, check.below())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTreeAt(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FruitTreeBlock || state.getBlock() instanceof FruitTreeSaplingBlock) {
            return true;
        }
        if (state.getBlock() instanceof WildTreeSaplingBlock) {
            return true;
        }
        return WildTrees.isAnyWildTreeTrunk0(state)
                || WildTrees.findByModernRoot(state) != null
                || StardewTreeRuntimeRegistry.inspectAddon(level, pos) != null;
    }
}
