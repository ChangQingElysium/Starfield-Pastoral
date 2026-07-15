package com.stardew.craft.block.decor;

import java.util.LinkedHashSet;
import java.util.Set;

/** Two-block-wide, two-block-tall bookshelf with an integer placement footprint. */
public class BookshelfStaticDecorBlock extends IntegratedAabbDecorBlock {
    public BookshelfStaticDecorBlock(Properties properties, String modelId,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cells = new LinkedHashSet<>();
        for (int x = -1; x <= 0; x++) {
            for (int y = 0; y < 2; y++) {
                cells.add(new CellOffset(x, y, 0));
            }
        }
        return cells;
    }
}
