package com.stardew.craft.block.decor;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stardew bed with an explicit ground footprint while retaining the model's original shape. */
public final class BedDecorBlock extends MapDecorStaticBlock {
    private final boolean doubleBed;

    public BedDecorBlock(Properties properties, String modelId, boolean doubleBed) {
        super(properties, modelId);
        this.doubleBed = doubleBed;
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cells = new LinkedHashSet<>();
        cells.add(CellOffset.ZERO);
        cells.add(new CellOffset(0, 0, 1));
        if (doubleBed) {
            cells.add(new CellOffset(-1, 0, 0));
            cells.add(new CellOffset(-1, 0, 1));
        }
        return cells;
    }
}
