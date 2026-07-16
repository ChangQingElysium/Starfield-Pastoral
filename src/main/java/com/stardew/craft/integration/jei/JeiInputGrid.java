package com.stardew.craft.integration.jei;

/** Centers one to four JEI inputs inside the same 40x40 two-column footprint. */
final class JeiInputGrid {
    private static final int STEP = 22;

    private JeiInputGrid() {
    }

    static Position position(int inputCount, int index, int left, int top) {
        int count = Math.max(1, Math.min(4, inputCount));
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("input " + index + " of " + count);
        }
        return switch (count) {
            case 1 -> new Position(left + STEP / 2, top + STEP / 2);
            case 2 -> new Position(left + index * STEP, top + STEP / 2);
            case 3 -> index < 2
                    ? new Position(left + index * STEP, top)
                    : new Position(left + STEP / 2, top + STEP);
            default -> new Position(left + (index % 2) * STEP, top + (index / 2) * STEP);
        };
    }

    record Position(int x, int y) {
    }
}
