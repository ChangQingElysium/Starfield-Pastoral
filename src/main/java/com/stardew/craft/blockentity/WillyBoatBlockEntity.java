package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** One renderer at the boat's MAIN cell; the moored boat needs no ticker or extra saved state. */
public final class WillyBoatBlockEntity extends BlockEntity {
    public static final double HEAVE_AMPLITUDE = .01;
    public static final double ROLL_AMPLITUDE_DEGREES = .15;
    public static final int HEAVE_PERIOD_TICKS = 150;
    public static final int ROLL_PERIOD_TICKS = 220;

    public WillyBoatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WILLY_BOAT.get(), pos, state);
    }

    public record Motion(double heave, float rollDegrees) {}

    /** Absolute shared world time preserves phase through repair, reload and different frame rates. */
    public static Motion sampleMotion(long gameTicks, float partialTick, BlockPos anchor) {
        long seed = anchor.asLong() * 0x9E3779B97F4A7C15L;
        double phase = (seed >>> 48) * (Math.PI * 2 / 65536);
        double heaveTime = (Math.floorMod(gameTicks, HEAVE_PERIOD_TICKS) + partialTick)
                * (Math.PI * 2 / HEAVE_PERIOD_TICKS);
        double rollTime = (Math.floorMod(gameTicks, ROLL_PERIOD_TICKS) + partialTick)
                * (Math.PI * 2 / ROLL_PERIOD_TICKS);
        return new Motion(Math.sin(heaveTime + phase) * HEAVE_AMPLITUDE,
                (float) (Math.sin(rollTime + phase + Math.PI / 3) * ROLL_AMPLITUDE_DEGREES));
    }

    public Motion motion(float partialTick) {
        // The waterline MAIN is normally dry; submerged extensions below it
        // retain the source water. Land-built boats must not bob in the air.
        return level == null || !level.getFluidState(worldPosition.below()).is(FluidTags.WATER)
                ? new Motion(0, 0) : sampleMotion(level.getGameTime(), partialTick, worldPosition);
    }

    public AABB getRenderBoundingBox() {
        if (level == null) return new AABB(worldPosition);
        var shape = getBlockState().getShape(level, worldPosition);
        // The authored collision is deliberately simpler than the visible railings and plank.
        // One block of margin includes those details as well as the sub-pixel visual motion.
        return shape.isEmpty() ? new AABB(worldPosition) : shape.bounds()
                .move(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).inflate(1);
    }
}
