package com.stardew.craft.farming;

import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Owns target resolution and all server-side rules for applying crop fertilizer. */
public final class FertilizerApplicationService {
    private FertilizerApplicationService() {
    }

    public enum Status {
        APPLIED,
        NOT_FARMLAND,
        NO_PERMISSION,
        HAS_THIS_FERTILIZER,
        HAS_ANOTHER_FERTILIZER,
        CROP_ALREADY_SPROUTED
    }

    public record Target(BlockPos soilPos, boolean cropSprouted) {
        public Target {
            soilPos = Objects.requireNonNull(soilPos, "soilPos").immutable();
        }
    }

    public record Result(Status status, @Nullable BlockPos soilPos) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            soilPos = soilPos == null ? null : soilPos.immutable();
        }

        public boolean applied() {
            return status == Status.APPLIED;
        }
    }

    /**
     * Resolves clicks on farmland, vanilla crops, core crops and registered addon crop parts to
     * one authoritative farmland tile.
     */
    @Nullable
    public static Target resolveTarget(LevelReader level, BlockPos clickedPos) {
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.getBlock() instanceof FarmBlock) {
            BlockPos soilPos = clickedPos.immutable();
            return new Target(soilPos, isCropSproutedAbove(level, soilPos));
        }

        StardewCropState crop = StardewCropRuntime.inspect(level, clickedPos);
        if (crop != null) {
            BlockPos soilPos = selectNearestFarmland(level, clickedPos, crop.soilPositions());
            return soilPos == null ? null : new Target(soilPos, crop.visualStage() > 0);
        }

        if (clickedState.getBlock() instanceof CropBlock vanillaCrop) {
            BlockPos soilPos = clickedPos.below();
            if (level.getBlockState(soilPos).getBlock() instanceof FarmBlock) {
                return new Target(soilPos, vanillaCrop.getAge(clickedState) > 0);
            }
        }
        return null;
    }

    public static Result apply(
            ServerLevel level,
            @Nullable ServerPlayer player,
            BlockPos clickedPos,
            FertilizerType requested
    ) {
        Target target = resolveTarget(level, clickedPos);
        if (target == null) {
            return new Result(Status.NOT_FARMLAND, null);
        }
        BlockPos soilPos = target.soilPos();
        if (player != null && !canModify(player, level, soilPos)) {
            return new Result(Status.NO_PERMISSION, soilPos);
        }

        FertilizerManager manager = FertilizerManager.get(level);
        Status rule = checkRules(
                manager.getFertilizer(level, soilPos), requested, target.cropSprouted());
        if (rule != Status.APPLIED) {
            return new Result(rule, soilPos);
        }

        if (manager.tryApplyFertilizer(level, soilPos, requested)) {
            return new Result(Status.APPLIED, soilPos);
        }

        // Re-evaluate if the world changed between validation and persistence.
        if (!(level.getBlockState(soilPos).getBlock() instanceof FarmBlock)) {
            return new Result(Status.NOT_FARMLAND, soilPos);
        }
        FertilizerType existing = manager.getFertilizer(level, soilPos);
        return new Result(
                existing == requested
                        ? Status.HAS_THIS_FERTILIZER
                        : Status.HAS_ANOTHER_FERTILIZER,
                soilPos);
    }

    /** Mirrors HoeDirt.CheckApplyFertilizerRules from Stardew Valley 1.6. */
    static Status checkRules(
            @Nullable FertilizerType existing,
            FertilizerType requested,
            boolean cropSprouted
    ) {
        if (existing != null) {
            return existing == requested
                    ? Status.HAS_THIS_FERTILIZER
                    : Status.HAS_ANOTHER_FERTILIZER;
        }
        if (cropSprouted && !requested.canApplyToSproutedCrop()) {
            return Status.CROP_ALREADY_SPROUTED;
        }
        return Status.APPLIED;
    }

    public static Status checkRulesForAutomation(
            @Nullable FertilizerType existing,
            FertilizerType requested,
            boolean cropSprouted
    ) {
        return checkRules(existing, requested, cropSprouted);
    }

    @Nullable
    static BlockPos selectNearestSoil(BlockPos clickedPos, List<BlockPos> soilPositions) {
        BlockPos nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (BlockPos soilPos : soilPositions) {
            long dx = (long) soilPos.getX() - clickedPos.getX();
            long dy = (long) soilPos.getY() - clickedPos.getY();
            long dz = (long) soilPos.getZ() - clickedPos.getZ();
            long distance = dx * dx + dy * dy + dz * dz;
            if (nearest == null
                    || distance < nearestDistance
                    || (distance == nearestDistance && comparePositions(soilPos, nearest) < 0)) {
                nearest = soilPos;
                nearestDistance = distance;
            }
        }
        return nearest == null ? null : nearest.immutable();
    }

    @Nullable
    private static BlockPos selectNearestFarmland(
            LevelReader level,
            BlockPos clickedPos,
            List<BlockPos> soilPositions
    ) {
        return selectNearestSoil(
                clickedPos,
                soilPositions.stream()
                        .filter(pos -> level.getBlockState(pos).getBlock() instanceof FarmBlock)
                        .toList());
    }

    private static boolean isCropSproutedAbove(LevelReader level, BlockPos soilPos) {
        BlockPos abovePos = soilPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (aboveState.getBlock() instanceof CropBlock vanillaCrop) {
            return vanillaCrop.getAge(aboveState) > 0;
        }
        StardewCropState crop = StardewCropRuntime.inspect(level, abovePos);
        return crop != null
                && crop.soilPositions().contains(soilPos)
                && crop.visualStage() > 0;
    }

    private static boolean canModify(
            ServerPlayer player,
            ServerLevel level,
            BlockPos soilPos
    ) {
        if (player.isCreative() || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return true;
        }
        if (GreenhouseManager.isInGreenhouseInterior(level, soilPos)) {
            return FarmAreaProtectionEvents.canModifyGreenhouseAt(player, level, soilPos);
        }
        return FarmAreaProtectionEvents.canModifyAt(player, soilPos);
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int x = Integer.compare(left.getX(), right.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(left.getY(), right.getY());
        return y != 0 ? y : Integer.compare(left.getZ(), right.getZ());
    }
}
