package com.stardew.craft.fishing.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;

/** Maps Minecraft water surfaces to Stardew Valley's square-ring clear-water distance. */
final class FishingWaterDepthService {
	private FishingWaterDepthService() {
	}

	static int estimateClearWaterDistance(ServerLevel level, BlockPos bobberPos, int maxDepth) {
		BlockPos fluidPos = findFluidPos(level, bobberPos);
		FluidKind fluidKind = fluidKind(level.getFluidState(fluidPos));
		if (fluidKind == FluidKind.NONE) {
			return 0;
		}
		return estimateClearWaterDistance(maxDepth,
				(dx, dz) -> isMatchingSurfaceColumn(level, fluidPos.offset(dx, 0, dz), fluidKind));
	}

	static int estimateClearWaterDistance(int maxDepth, WaterCellPredicate isWater) {
		int limit = Math.max(0, maxDepth);
		for (int radius = 1; radius <= limit; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}
					if (!isWater.test(dx, dz)) {
						return radius - 1;
					}
				}
			}
		}
		return limit;
	}

	static boolean isDryLandCastOrigin(ServerPlayer player) {
		return player.onGround()
				&& !player.isInWaterOrBubble()
				&& !player.isInLava()
				&& !(player.getVehicle() instanceof Boat);
	}

	static int qualityDepth(int clearWaterDistance, boolean castFromDryLand) {
		return castFromDryLand ? Math.max(0, Math.min(5, clearWaterDistance)) : 0;
	}

	private static BlockPos findFluidPos(ServerLevel level, BlockPos bobberPos) {
		if (fluidKind(level.getFluidState(bobberPos)) != FluidKind.NONE) {
			return bobberPos;
		}
		BlockPos below = bobberPos.below();
		if (fluidKind(level.getFluidState(below)) != FluidKind.NONE) {
			return below;
		}
		BlockPos above = bobberPos.above();
		return fluidKind(level.getFluidState(above)) != FluidKind.NONE ? above : bobberPos;
	}

	private static boolean isMatchingSurfaceColumn(ServerLevel level, BlockPos pos, FluidKind expected) {
		if (!level.hasChunkAt(pos)) {
			// Stardew ignores tiles outside the map instead of treating them as shore.
			return true;
		}
		if (fluidKind(level.getFluidState(pos)) == expected) {
			return true;
		}
		// FishingHook.blockPosition() may resolve one block above the visible water surface.
		return level.getBlockState(pos).isAir()
				&& fluidKind(level.getFluidState(pos.below())) == expected;
	}

	private static FluidKind fluidKind(FluidState state) {
		if (state.is(FluidTags.WATER)) {
			return FluidKind.WATER;
		}
		if (state.is(FluidTags.LAVA)) {
			return FluidKind.LAVA;
		}
		return FluidKind.NONE;
	}

	@FunctionalInterface
	interface WaterCellPredicate {
		boolean test(int dx, int dz);
	}

	private enum FluidKind {
		NONE,
		WATER,
		LAVA
	}

}
