package com.stardew.craft.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loader-independent rules for the authored farm spawn masks.
 *
 * <p>The mask files store horizontal runs as {@code [y, z, minX, maxX]}.
 * This class owns the format, bounds and expansion contract so a Forge port
 * does not need to copy the Minecraft/Gson resource loader just to preserve
 * the farm-map geometry.</p>
 */
public final class FarmSpawnLayoutRules {
    public static final int CURRENT_FORMAT = 1;

    private FarmSpawnLayoutRules() {
    }

    public record Size(int width, int height, int length) {
    }

    public record Run(int y, int z, int minX, int maxX) {
    }

    public record Position(int x, int y, int z) {
    }

    /** Validate the version marker before decoding any candidate runs. */
    public static void requireFormat(String path, int format) {
        if (format != CURRENT_FORMAT) {
            throw new IllegalStateException(
                    "Unsupported farm spawn layout " + path);
        }
    }

    /** Validate the authored schematic dimensions against the selected farm. */
    public static void requireSize(
            String path,
            String farmId,
            Size actual,
            Size expected
    ) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(expected, "expected");
        if (!actual.equals(expected)) {
            throw new IllegalStateException(
                    "Farm spawn layout size does not match " + farmId);
        }
    }

    /** Decode one JSON run while preserving the source parser's error contract. */
    public static Run decodeRun(String path, int[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length != 4) {
            throw new IllegalStateException("Malformed run in " + path);
        }
        return new Run(encoded[0], encoded[1], encoded[2], encoded[3]);
    }

    /**
     * Expand authored runs in source order after checking all coordinates.
     * Returned positions are immutable and contain inclusive endpoints.
     */
    public static List<Position> expandRuns(
            String path,
            Size size,
            List<Run> runs
    ) {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(runs, "runs");
        ArrayList<Position> positions = new ArrayList<>();
        for (Run run : runs) {
            positions.addAll(expandRun(path, size, run));
        }
        return List.copyOf(positions);
    }

    /** Expand one run; kept separate so the resource loader preserves failure order. */
    public static List<Position> expandRun(String path, Size size, Run run) {
        Objects.requireNonNull(size, "size");
        if (run == null) {
            throw new IllegalStateException("Malformed run in " + path);
        }
        int y = run.y();
        int z = run.z();
        int minX = run.minX();
        int maxX = run.maxX();
        if (minX < 0 || maxX < minX || maxX >= size.width()
                || y < 0 || y >= size.height()
                || z < 0 || z >= size.length()) {
            throw new IllegalStateException("Out-of-bounds run in " + path);
        }
        ArrayList<Position> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            positions.add(new Position(x, y, z));
        }
        return List.copyOf(positions);
    }
}
