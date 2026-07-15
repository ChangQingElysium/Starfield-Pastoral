package com.stardew.craft.block.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegratedAabbDecorBlockTest {
    @Test
    void wholeAabbStaysOneBoxAndEveryCellMapsBackToTheSameWorldCollision() {
        var mainShape = IntegratedAabbDecorBlock.createWholeShapes(-16, 0, 4, 16, 32, 16)
            .get(Direction.NORTH);
        assertEquals(1, boxCount(mainShape));
        assertEquals(new AABB(-1, 0, 0.25, 1, 2, 1), mainShape.bounds());

        var extensionLocalShape = IntegratedAabbDecorBlock.shiftWholeShape(mainShape, 1, 0, 0);
        assertEquals(1, boxCount(extensionLocalShape));
        assertEquals(mainShape.bounds(), extensionLocalShape.bounds().move(-1, 0, 0));
    }

    private static int boxCount(net.minecraft.world.phys.shapes.VoxelShape shape) {
        int[] count = {0};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> count[0]++);
        return count[0];
    }
}
