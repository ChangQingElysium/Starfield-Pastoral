package com.stardew.craft.block.shape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModelVoxelShapeCacheTest {
    @AfterEach
    void clearCache() {
        ModelVoxelShapeCache.clearAll();
    }

    @Test
    void parentModelResolutionDoesNotRecursivelyUpdateConcurrentCache() {
        ModelVoxelShapeCache.clearAll();
        for (int i = 0; i < 11; i++) {
            ModelVoxelShapeCache.shapeFromModelId("stardewcraft:test/missing_" + i);
        }

        assertFalse(ModelVoxelShapeCache.shapeFromModelId("stardewcraft:test/child").isEmpty());
    }

    @Test
    void wizardBuildingsDeriveMultiBlockBoundsFromTheirGeoModels() {
        String[] models = {
                "junimo_hut", "earth_obelisk", "water_obelisk", "desert_obelisk", "island_obelisk"
        };
        for (String model : models) {
            String id = "stardewcraft:geo/block/utility/" + model + ".geo.json#aabb";
            ModelVoxelShapeCache.GeoBounds bounds = ModelVoxelShapeCache.geoBoundsFromModelId(id);
            assertNotNull(bounds, model);
            assertFalse(ModelVoxelShapeCache.shapeFromModelId(id).isEmpty(), model);
            assertTrue(bounds.maxX() - bounds.minX() > 16.0, model + " width");
            assertTrue(bounds.maxZ() - bounds.minZ() > 16.0, model + " depth");
        }
    }

    @Test
    void geckoCubeUsesBakedMirrorAndBlockCenterTranslation() {
        var shape = ModelVoxelShapeCache.shapeFromModelId(
                "stardewcraft:geo/test/centered_cube.geo.json");

        assertFalse(shape.isEmpty());
        var bounds = shape.bounds();
        assertEquals(0.0D, bounds.minX, 1.0E-6);
        assertEquals(0.0D, bounds.minY, 1.0E-6);
        assertEquals(0.0D, bounds.minZ, 1.0E-6);
        assertEquals(1.0D, bounds.maxX, 1.0E-6);
        assertEquals(1.0D, bounds.maxY, 1.0E-6);
        assertEquals(1.0D, bounds.maxZ, 1.0E-6);
    }

}
