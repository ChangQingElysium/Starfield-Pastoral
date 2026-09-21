package com.stardew.craft.block.decor;

/** Stable map-decoration identities. Seasons and animation never replace these blocks. */
public enum NaturalDecorKind {
    WILDFLOWER_A("wildflower_cluster_a", "natural_flower", 1, Habitat.LAND, 16),
    WILDFLOWER_B("wildflower_cluster_b", "natural_flower", 1, Habitat.LAND, 16),
    GRASS("wild_grass_clump", "natural_grass", 2, Habitat.LAND, 16),
    LOW_FLOWER("low_flower_clump", "natural_flower", 1, Habitat.LAND, 13),
    BROADLEAF("broadleaf_clump", "natural_grass", 1, Habitat.LAND, 13),
    GROUND_SPRIG("seasonal_ground_sprig", "natural_flower", 1, Habitat.LAND, 10),
    AQUATIC_GRASS("aquatic_grass_clump", "natural_aquatic", 2, Habitat.UNDERWATER, 16),
    FLOATING_LEAF("floating_leaf", "natural_aquatic", 1, Habitat.SURFACE, 2),
    WATER_LILY("water_lily", "natural_aquatic", 1, Habitat.SURFACE, 7);

    public enum Habitat { LAND, UNDERWATER, SURFACE }
    public final String id;
    public final String typeKey;
    public final int variants;
    public final Habitat habitat;
    public final int height;

    NaturalDecorKind(String id, String type, int variants, Habitat habitat, int height) {
        this.id = id; this.typeKey = "stardewcraft.type." + type;
        this.variants = variants; this.habitat = habitat; this.height = height;
    }

    public boolean hiddenInWinter() {
        return switch (this) {
            case GRASS, LOW_FLOWER, BROADLEAF, GROUND_SPRIG, AQUATIC_GRASS -> true;
            default -> false;
        };
    }

}
