package com.stardew.craft.fishing.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishingWaterDepthServiceTest {
	@Test
	void adjacentCardinalOrDiagonalShoreHasZeroClearWaterDistance() {
		assertEquals(0, depthWithLandAt(1, 0));
		assertEquals(0, depthWithLandAt(1, 1));
	}

	@Test
	void squareRingsMatchStardewClearWaterDistance() {
		assertEquals(1, depthWithLandAt(2, 0));
		assertEquals(2, depthWithLandAt(3, 3));
		assertEquals(4, depthWithLandAt(5, -2));
	}

	@Test
	void openWaterReachesTheVanillaMaximum() {
		assertEquals(5, FishingWaterDepthService.estimateClearWaterDistance(5, (dx, dz) -> true));
	}

	@Test
	void swimmingAndBoatCastsDoNotReceiveDeepWaterQualityCredit() {
		assertEquals(5, FishingWaterDepthService.qualityDepth(5, true));
		assertEquals(0, FishingWaterDepthService.qualityDepth(5, false));
	}

	private static int depthWithLandAt(int landX, int landZ) {
		return FishingWaterDepthService.estimateClearWaterDistance(
				5, (dx, dz) -> dx != landX || dz != landZ);
	}
}
