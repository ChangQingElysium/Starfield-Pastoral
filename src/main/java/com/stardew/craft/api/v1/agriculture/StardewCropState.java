package com.stardew.craft.api.v1.agriculture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Immutable, geometry-neutral view of one recognized crop at a world position. */
public record StardewCropState(
        ResourceLocation typeId,
        BlockPos root,
        Part part,
        int visualStage,
        boolean mature,
        List<BlockPos> soilPositions
) {
    public StardewCropState {
        typeId = Objects.requireNonNull(typeId, "typeId");
        root = Objects.requireNonNull(root, "root").immutable();
        part = Objects.requireNonNull(part, "part");
        if (visualStage < 0) {
            throw new IllegalArgumentException("Crop visual stage cannot be negative");
        }
        Objects.requireNonNull(soilPositions, "soilPositions");
        LinkedHashSet<BlockPos> uniqueSoils = new LinkedHashSet<>();
        for (BlockPos soil : soilPositions) {
            uniqueSoils.add(Objects.requireNonNull(soil, "soil position").immutable());
        }
        soilPositions = List.copyOf(new ArrayList<>(uniqueSoils));
    }

    public enum Part {
        ROOT,
        UPPER,
        EXTENSION
    }
}
