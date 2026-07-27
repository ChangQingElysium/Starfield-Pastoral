package com.stardew.craft.api.v1.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable, geometry-neutral view of one recognized tree at a world position. */
public record StardewTreeState(
        ResourceLocation typeId,
        BlockPos root,
        Part part,
        int visualStage,
        boolean mature,
        List<BlockPos> tapperSupports
) {
    public StardewTreeState {
        typeId = Objects.requireNonNull(typeId, "typeId");
        root = Objects.requireNonNull(root, "root").immutable();
        part = Objects.requireNonNull(part, "part");
        if (visualStage < 0) {
            throw new IllegalArgumentException("Tree visual stage cannot be negative");
        }
        Objects.requireNonNull(tapperSupports, "tapperSupports");
        LinkedHashSet<BlockPos> uniqueSupports = new LinkedHashSet<>();
        for (BlockPos support : tapperSupports) {
            uniqueSupports.add(Objects.requireNonNull(support, "tapper support").immutable());
        }
        tapperSupports = List.copyOf(new ArrayList<>(uniqueSupports));
    }

    public enum Part {
        SAPLING,
        ROOT,
        TRUNK,
        CANOPY,
        EXTENSION
    }
}
