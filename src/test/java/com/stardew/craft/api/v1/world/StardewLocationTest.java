package com.stardew.craft.api.v1.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewLocationTest {
    @Test
    void containmentIncludesBoundsAndRequiresMatchingDimension() {
        ResourceLocation dimension =
                ResourceLocation.fromNamespaceAndPath("example", "valley");
        StardewLocation location = new StardewLocation(
                ResourceLocation.fromNamespaceAndPath("example", "orchard"),
                dimension,
                new BlockPos(-5, 10, -8),
                new BlockPos(5, 20, 8),
                "",
                List.of("Orchard"),
                20);

        assertTrue(location.contains(dimension, new BlockPos(-5, 10, -8)));
        assertTrue(location.contains(dimension, new BlockPos(5, 20, 8)));
        assertFalse(location.contains(dimension, new BlockPos(6, 20, 8)));
        assertFalse(location.contains(
                ResourceLocation.fromNamespaceAndPath("example", "mines"),
                BlockPos.ZERO));
        assertFalse(location.indoor());
    }
}
