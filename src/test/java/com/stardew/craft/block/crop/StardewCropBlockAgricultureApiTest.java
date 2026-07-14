package com.stardew.craft.block.crop;

import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewCropData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewCropBlockAgricultureApiTest {
    @Test
    void harvestExperienceUsesWorldAwareCropProvider() {
        StardewAgricultureDataApi.registerCropProvider(
                ResourceLocation.fromNamespaceAndPath("agriculture_test", "harvest_consumer"),
                10_000,
                (level, pos, state) -> state.is(Blocks.BEETROOTS)
                        ? new StardewCropData(
                                List.of("fall"), List.of(1), -1, 77,
                                ResourceLocation.fromNamespaceAndPath("stardewcraft", "grab"),
                                ResourceLocation.withDefaultNamespace("beetroot"),
                                ResourceLocation.withDefaultNamespace("beetroot_seeds"))
                        : null);

        int experience = StardewCropBlock.getHarvestFarmingExperience(
                null, BlockPos.ZERO, Blocks.BEETROOTS.defaultBlockState());

        assertEquals(77, experience);
    }
}
