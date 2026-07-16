package com.stardew.craft.fishing.server;

import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingQualityCalculatorTest {
	@Test
	void zeroClearWaterDistanceCannotProduceQualitySize() {
		assertEquals(0.0, FishingQualityCalculator.rollFishSize(0, 10, true, RandomSource.create(7L)));
	}

	@Test
	void clearWaterDistanceScalesTheSameRandomRoll() {
		double shallow = FishingQualityCalculator.rollFishSize(1, 0, false, RandomSource.create(42L));
		double deep = FishingQualityCalculator.rollFishSize(5, 0, false, RandomSource.create(42L));
		assertEquals(Math.min(1.0, shallow * 5.0), deep, 0.000001);
		assertTrue(deep >= shallow);
	}

	@Test
	void targetedBaitAppliesTheVanillaSizeMultiplier() {
		double normal = FishingQualityCalculator.rollFishSize(3, 4, false, RandomSource.create(99L));
		double favored = FishingQualityCalculator.rollFishSize(3, 4, true, RandomSource.create(99L));
		assertEquals(Math.min(1.0, normal * 1.2), favored, 0.000001);
	}

	@Test
	void initialQualityUsesVanillaThresholds() {
		assertEquals(QualityHelper.NORMAL, FishingQualityCalculator.initialQuality(0.3299));
		assertEquals(QualityHelper.SILVER, FishingQualityCalculator.initialQuality(0.33));
		assertEquals(QualityHelper.SILVER, FishingQualityCalculator.initialQuality(0.6599));
		assertEquals(QualityHelper.GOLD, FishingQualityCalculator.initialQuality(0.66));
	}

	@Test
	void stackedQualityBobbersAndPerfectApplyInVanillaOrder() {
		assertEquals(QualityHelper.GOLD,
				FishingQualityCalculator.finalQuality(QualityHelper.NORMAL, 2, false, false));
		assertEquals(QualityHelper.IRIDIUM,
				FishingQualityCalculator.finalQuality(QualityHelper.SILVER, 2, false, false));
		assertEquals(QualityHelper.GOLD,
				FishingQualityCalculator.finalQuality(QualityHelper.SILVER, 0, false, true));
		assertEquals(QualityHelper.IRIDIUM,
				FishingQualityCalculator.finalQuality(QualityHelper.GOLD, 0, false, true));
	}

	@Test
	void trainingRodAlwaysProducesNormalQuality() {
		assertEquals(QualityHelper.NORMAL,
				FishingQualityCalculator.finalQuality(QualityHelper.GOLD, 2, true, true));
	}
}
