package com.stardew.craft.block.decor;

import java.util.Set;

/** Wall bookshelf with a fixed one-block placement footprint. */
public class BookshelfWallDecorBlock extends MapDecorWallStaticBlock {
    public BookshelfWallDecorBlock(Properties properties, String modelId,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        return Set.of(CellOffset.ZERO);
    }
}
