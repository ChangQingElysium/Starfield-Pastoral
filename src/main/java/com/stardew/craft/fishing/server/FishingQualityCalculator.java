package com.stardew.craft.fishing.server;

import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Pure Stardew Valley fishing size and quality rules. */
final class FishingQualityCalculator {
	private FishingQualityCalculator() {
	}

	static double rollFishSize(int clearWaterDistance, int fishingLevel, boolean favoredBait, RandomSource random) {
		double fishSize = Mth.clamp(clearWaterDistance, 0, 5) / 5.0;
		int minimumSizeContribution = 1 + Math.max(0, fishingLevel) / 2;
		int upperBound = Math.max(6, minimumSizeContribution);
		int sizeContribution = upperBound <= minimumSizeContribution
				? minimumSizeContribution
				: minimumSizeContribution + random.nextInt(upperBound - minimumSizeContribution);
		fishSize *= sizeContribution / 5.0;
		if (favoredBait) {
			fishSize *= 1.2;
		}
		fishSize *= 1.0 + (random.nextInt(21) - 10) / 100.0;
		return Mth.clamp(fishSize, 0.0, 1.0);
	}

	static int initialQuality(double fishSize) {
		if (fishSize < 0.33) {
			return QualityHelper.NORMAL;
		}
		if (fishSize < 0.66) {
			return QualityHelper.SILVER;
		}
		return QualityHelper.GOLD;
	}

	static int finalQuality(int initialQuality, int qualityBobberCount, boolean trainingRod, boolean perfect) {
		int quality = Mth.clamp(initialQuality, QualityHelper.NORMAL, QualityHelper.IRIDIUM);
		for (int i = 0; i < Math.max(0, qualityBobberCount); i++) {
			quality = quality >= QualityHelper.GOLD ? QualityHelper.IRIDIUM : quality + 1;
		}
		if (trainingRod) {
			quality = QualityHelper.NORMAL;
		}
		if (perfect) {
			if (quality >= QualityHelper.GOLD) {
				quality = QualityHelper.IRIDIUM;
			} else if (quality >= QualityHelper.SILVER) {
				quality = QualityHelper.GOLD;
			}
		}
		return quality;
	}
}
