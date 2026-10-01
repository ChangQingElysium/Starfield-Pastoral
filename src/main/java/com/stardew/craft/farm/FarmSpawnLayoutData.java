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
            if (root == null) {
                throw new IllegalStateException("Unsupported farm spawn layout " + path);
            }
            FarmSpawnLayoutRules.requireFormat(path, root.get("format").getAsInt());
            JsonArray size = root.getAsJsonArray("size");
            if (size.size() != 3) {
                throw new IllegalStateException("Farm spawn layout size does not match " + type.getId());
            }
            FarmSpawnLayoutRules.Size actualSize = new FarmSpawnLayoutRules.Size(
                    size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt());
            FarmSpawnLayoutRules.Size expectedSize = new FarmSpawnLayoutRules.Size(
                    type.getLayout().schemWidth(), type.getLayout().schemHeight(),
                    type.getLayout().schemLength());
            FarmSpawnLayoutRules.requireSize(path, type.getId(), actualSize, expectedSize);

            Map<String, List<BlockPos>> candidates = new HashMap<>();
            JsonObject runs = root.getAsJsonObject("runs");
            for (var entry : runs.entrySet()) {
                java.util.ArrayList<FarmSpawnLayoutRules.Position> positions = new java.util.ArrayList<>();
                for (var element : entry.getValue().getAsJsonArray()) {
                    JsonArray run = element.getAsJsonArray();
                    int[] encoded = new int[run.size()];
                    for (int index = 0; index < run.size(); index++) {
                        encoded[index] = run.get(index).getAsInt();
                    }
                    FarmSpawnLayoutRules.Run decoded = FarmSpawnLayoutRules.decodeRun(path, encoded);
                    positions.addAll(FarmSpawnLayoutRules.expandRun(path, actualSize, decoded));
                }
                candidates.put(entry.getKey(), positions.stream()
                        .map(position -> new BlockPos(position.x(), position.y(), position.z()))
                        .toList());
            }
            return new Layout(candidates);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load " + path, exception);
        }
    }
}
