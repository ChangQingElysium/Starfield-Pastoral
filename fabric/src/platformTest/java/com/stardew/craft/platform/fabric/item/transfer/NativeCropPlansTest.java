package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeCropPlansTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void wheatCarrotAndPotatoUseNativeTwoToFiveGrowthAndOneBoundedDraw() {
        for (var block : List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES)) {
            var crop = (CropBlock) block;
            var source = new LegacyRandomSource(41); var expected = new LegacyRandomSource(41);
            for (int i = 0; i < 100; i++) {
                int growth = expected.nextInt(4) + 2;
                assertEquals(crop.getStateForAge(growth), NativeCropPlans.bonemeal(crop.getStateForAge(0), source));
            }
            assertEquals(expected.nextLong(), source.nextLong());
        }
    }
    @Test void beetrootRetainsZeroGrowthResultsAndNativeIntegerDivision() {
        var crop = (CropBlock) Blocks.BEETROOTS;
        var source = new LegacyRandomSource(42); var expected = new LegacyRandomSource(42);
        boolean zero = false;
        for (int i = 0; i < 100; i++) {
            int growth = (expected.nextInt(4) + 2) / 3;
            zero |= growth == 0;
            assertEquals(crop.getStateForAge(growth), NativeCropPlans.bonemeal(crop.getStateForAge(0), source));
        }
        assertTrue(zero); assertEquals(expected.nextLong(), source.nextLong());
    }
    @Test void torchflowerUsesOneAgeWithoutDrawingAndBecomesTheRealFlower() {
        var crop = (CropBlock) Blocks.TORCHFLOWER_CROP;
        var source = new LegacyRandomSource(43); var expected = new LegacyRandomSource(43);
        assertEquals(crop.getStateForAge(1), NativeCropPlans.bonemeal(crop.getStateForAge(0), source));
        assertTrue(NativeCropPlans.bonemeal(crop.getStateForAge(1), source).is(Blocks.TORCHFLOWER));
        assertEquals(expected.nextLong(), source.nextLong());
    }
    @Test void cappedMaturityAndUnsupportedBonemealableBlocksHaveExplicitBoundaries() {
        var crop = (CropBlock) Blocks.WHEAT;
        assertThrows(IllegalArgumentException.class,
                () -> NativeCropPlans.bonemeal(crop.getStateForAge(crop.getMaxAge()), new LegacyRandomSource(1)));
        assertFalse(NativeCropPlans.supported(Blocks.OAK_SAPLING.defaultBlockState()));
        assertThrows(UnsupportedOperationException.class,
                () -> NativeCropPlans.bonemeal(Blocks.OAK_SAPLING.defaultBlockState(), new LegacyRandomSource(1)));
    }
}
