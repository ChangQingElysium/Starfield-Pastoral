package com.stardew.craft.gingerisland;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** The generated runtime catalog is independent of the ignored authoring directory. */
public final class GingerIslandAssets {
    public record BlockAsset(String id, String model, String kind, String type,
                             String state_property, Map<String, String> state_models) {}
    private record Catalog(int version, List<BlockAsset> blocks) {}
    private static final List<BlockAsset> BLOCKS = load();

    private GingerIslandAssets() {}
    public static List<BlockAsset> blocks() { return BLOCKS; }

    private static List<BlockAsset> load() {
        String path = "/data/stardewcraft/ginger_island/assets.json";
        try (var input = GingerIslandAssets.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing Ginger Island catalog: " + path);
            Catalog catalog = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Catalog.class);
            if (catalog.version() != 1 || catalog.blocks() == null) throw new IllegalStateException("Invalid Ginger Island catalog");
            var ids = new HashSet<String>();
            for (BlockAsset asset : catalog.blocks()) {
                if (!asset.id().matches("ginger_[a-z0-9_]+") || !ids.add(asset.id())
                        || !asset.model().startsWith("stardewcraft:block/ginger_island/"))
                    throw new IllegalStateException("Invalid or duplicate Ginger Island block: " + asset.id());
            }
            return List.copyOf(catalog.blocks());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read Ginger Island catalog", error);
        }
    }
}
