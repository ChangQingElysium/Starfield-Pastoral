package com.stardew.craft.entity.bomb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Integer tile circle from Stardew's Game1.getCircleOutlineGrid / GameLocation.explode. */
public final class BombBlastPattern {
    public record Tile(int x, int z) {}
    private static final Map<Integer, List<Tile>> CIRCLES = new ConcurrentHashMap<>();

    public static List<Tile> circle(int radius) {
        if (radius < 0) throw new IllegalArgumentException("Negative bomb radius");
        return CIRCLES.computeIfAbsent(radius, BombBlastPattern::build);
    }

    private static List<Tile> build(int radius) {
        boolean[][] outline = new boolean[2 * radius + 1][2 * radius + 1];
        int f = 1 - radius, dx = 1, dz = -2 * radius, x = 0, z = radius;
        mark(outline, radius, x, z);
        while (x < z) {
            if (f >= 0) { z--; dz += 2; f += dz; }
            x++; dx += 2; f += dx;
            mark(outline, radius, x, z);
        }
        List<Tile> tiles = new ArrayList<>();
        // The explosion processes the outline too, including the descending edge.
        for (int i = 0; i < outline.length; i++) {
            int inside = 0;
            for (int j = 0; j < outline.length; j++) {
                if (i == 0 || j == 0 || i == 2 * radius || j == 2 * radius) {
                    inside = outline[i][j] ? 1 : 0;
                } else if (outline[i][j]) {
                    inside += j <= radius ? 1 : -1;
                }
                if (outline[i][j] || inside >= 1) tiles.add(new Tile(i - radius, j - radius));
            }
        }
        return List.copyOf(tiles);
    }

    private static void mark(boolean[][] outline, int r, int x, int z) {
        outline[r+x][r+z] = outline[r-x][r+z] = true;
        outline[r+x][r-z] = outline[r-x][r-z] = true;
        outline[r+z][r+x] = outline[r-z][r+x] = true;
        outline[r+z][r-x] = outline[r-z][r-x] = true;
    }

    private BombBlastPattern() {}
}
