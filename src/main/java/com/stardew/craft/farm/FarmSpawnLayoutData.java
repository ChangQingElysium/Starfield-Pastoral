package com.stardew.craft.farm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Candidate columns derived from the authored farm schematics.
 *
 * <p>These are selection masks, not blocks to paste. Runtime validates the
 * current terrain before every spawn, so the same data works for farms which
 * already exist in a save and respects later player changes.</p>
 */
public final class FarmSpawnLayoutData {
    private static final Gson GSON = new Gson();

    private FarmSpawnLayoutData() {}

    public static Layout forType(FarmType type) {
        return switch (type) {
            case FOREST -> Holder.FOREST;
            case BEACH -> Holder.BEACH;
            default -> throw new IllegalArgumentException("No special spawn layout for " + type);
        };
    }

    public record Layout(Map<String, List<BlockPos>> candidates) {
        public Layout {
            candidates = Map.copyOf(candidates);
        }

        public List<BlockPos> positions(String name) {
            List<BlockPos> result = candidates.get(name);
            if (result == null) throw new IllegalArgumentException("Unknown farm spawn set: " + name);
            return result;
        }
    }

    private static final class Holder {
        private static final Layout FOREST = load(FarmType.FOREST);
        private static final Layout BEACH = load(FarmType.BEACH);
    }

    private static Layout load(FarmType type) {
        String path = "/data/stardewcraft/farm_spawn_layouts/" + type.getId() + ".json";
        try (InputStream stream = FarmSpawnLayoutData.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing farm spawn layout " + path);
            JsonObject root = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || root.get("format").getAsInt() != 1) {
                throw new IllegalStateException("Unsupported farm spawn layout " + path);
            }
            JsonArray size = root.getAsJsonArray("size");
            if (size.size() != 3
                    || size.get(0).getAsInt() != type.getLayout().schemWidth()
                    || size.get(1).getAsInt() != type.getLayout().schemHeight()
                    || size.get(2).getAsInt() != type.getLayout().schemLength()) {
                throw new IllegalStateException("Farm spawn layout size does not match " + type.getId());
            }

            Map<String, List<BlockPos>> candidates = new HashMap<>();
            JsonObject runs = root.getAsJsonObject("runs");
            for (var entry : runs.entrySet()) {
                java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
                for (var element : entry.getValue().getAsJsonArray()) {
                    JsonArray run = element.getAsJsonArray();
                    if (run.size() != 4) throw new IllegalStateException("Malformed run in " + path);
                    int y = run.get(0).getAsInt();
                    int z = run.get(1).getAsInt();
                    int minX = run.get(2).getAsInt();
                    int maxX = run.get(3).getAsInt();
                    if (minX < 0 || maxX < minX || maxX >= type.getLayout().schemWidth()
                            || y < 0 || y >= type.getLayout().schemHeight()
                            || z < 0 || z >= type.getLayout().schemLength()) {
                        throw new IllegalStateException("Out-of-bounds run in " + path);
                    }
                    for (int x = minX; x <= maxX; x++) positions.add(new BlockPos(x, y, z));
                }
                candidates.put(entry.getKey(), List.copyOf(positions));
            }
            return new Layout(candidates);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load " + path, exception);
        }
    }
}
