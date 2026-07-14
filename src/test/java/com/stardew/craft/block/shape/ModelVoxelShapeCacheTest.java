package com.stardew.craft.block.shape;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
