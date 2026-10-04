package com.stardew.craft.gingerisland;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The generated runtime catalog is independent of the ignored authoring directory. */
public final class GingerIslandAssets {
    public record BlockAsset(String id, String model, String kind, String type,
                             String state_property, Map<String, String> state_models, boolean passable,
                             String state_role, int variant_count, String catalog_owner,
                             Map<String, String> canonical_state, String placement_mode) {
        public boolean legacyAlias() { return catalog_owner != null; }
    }
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
                if (asset.kind().equals("state_decor") && (asset.state_role() == null
                        || !Set.of("appearance", "interaction", "progress", "environment").contains(asset.state_role())))
                    throw new IllegalStateException("Unreviewed Ginger Island state role: " + asset.id());
                if ((asset.kind().equals("volcano_floor") || asset.kind().equals("caldera_floor"))
                        && (asset.variant_count() < 1 || asset.variant_count() > 6))
                    throw new IllegalStateException("Invalid Ginger Island ground choices: " + asset.id());
                if (asset.placement_mode() != null && !Set.of("floor", "wall", "cliff_palm").contains(asset.placement_mode()))
                    throw new IllegalStateException("Invalid Ginger Island attachment: " + asset.id());
            }
            Map<String, BlockAsset> owners = catalog.blocks().stream().collect(
                    java.util.stream.Collectors.toMap(BlockAsset::id, asset -> asset));
            for (BlockAsset alias : catalog.blocks()) {
                if (!alias.legacyAlias()) {
                    if (alias.canonical_state() != null)
                        throw new IllegalStateException("Canonical state without owner: " + alias.id());
                    continue;
                }
                BlockAsset owner = owners.get(alias.catalog_owner());
                if (owner == null || owner.legacyAlias() || owner == alias || !alias.kind().equals("decor")
                        || !owner.kind().equals("state_decor") || !owner.type().equals(alias.type())
                        || alias.canonical_state() == null || alias.canonical_state().size() != 1
                        || !alias.canonical_state().containsKey(owner.state_property())
                        || !owner.state_models().containsKey(alias.canonical_state().get(owner.state_property())))
                    throw new IllegalStateException("Invalid Ginger Island legacy alias: " + alias.id());
            }
            return List.copyOf(catalog.blocks());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read Ginger Island catalog", error);
        }
    }
}
