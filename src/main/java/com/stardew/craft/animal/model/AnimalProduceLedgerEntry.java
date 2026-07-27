package com.stardew.craft.animal.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Persistent source of truth for one uncollected animal product.
 *
 * <p>A projected produce-spot block is only a view of this entry. Removing or unloading that
 * projection must not remove the product; only a successful player/auto-grabber transfer completes
 * the entry.
 */
public record AnimalProduceLedgerEntry(
        long entryId,
        String buildingId,
        long animalId,
        int producedAbsDay,
        ResourceLocation itemId,
        int quality,
        String projectedDimensionId,
        long projectedBlockPos,
        boolean autoCollectEligible,
        String preferredDimensionId,
        long preferredBlockPos,
        int preferredRadius
) {
    public AnimalProduceLedgerEntry(
            long entryId,
            String buildingId,
            long animalId,
            int producedAbsDay,
            ResourceLocation itemId,
            int quality,
            String projectedDimensionId,
            long projectedBlockPos
    ) {
        this(
                entryId,
                buildingId,
                animalId,
                producedAbsDay,
                itemId,
                quality,
                projectedDimensionId,
                projectedBlockPos,
                true,
                "",
                0L,
                0
        );
    }

    public AnimalProduceLedgerEntry {
        if (entryId <= 0L) {
            throw new IllegalArgumentException("entryId must be positive");
        }
        buildingId = requireText(buildingId, "buildingId");
        if (animalId <= 0L) {
            throw new IllegalArgumentException("animalId must be positive");
        }
        producedAbsDay = Math.max(0, producedAbsDay);
        Objects.requireNonNull(itemId, "itemId");
        quality = Math.max(0, quality);
        projectedDimensionId = Objects.requireNonNullElse(projectedDimensionId, "");
        if (projectedDimensionId.isBlank()) {
            projectedDimensionId = "";
            projectedBlockPos = 0L;
        }
        preferredDimensionId = Objects.requireNonNullElse(
                preferredDimensionId,
                ""
        );
        preferredRadius = Math.max(0, preferredRadius);
        if (preferredDimensionId.isBlank() || preferredRadius == 0) {
            preferredDimensionId = "";
            preferredBlockPos = 0L;
            preferredRadius = 0;
        }
    }

    public boolean isProjected() {
        return !projectedDimensionId.isEmpty();
    }

    public BlockPos projectedPos() {
        if (!isProjected()) {
            throw new IllegalStateException("Ledger entry is not projected: " + entryId);
        }
        return BlockPos.of(projectedBlockPos);
    }

    public boolean hasPreferredAnchor() {
        return !preferredDimensionId.isEmpty();
    }

    public BlockPos preferredPos() {
        if (!hasPreferredAnchor()) {
            throw new IllegalStateException(
                    "Ledger entry has no preferred anchor: " + entryId);
        }
        return BlockPos.of(preferredBlockPos);
    }

    public AnimalProduceLedgerEntry withProjection(String dimensionId, BlockPos pos) {
        return new AnimalProduceLedgerEntry(
                entryId,
                buildingId,
                animalId,
                producedAbsDay,
                itemId,
                quality,
                requireText(dimensionId, "dimensionId"),
                Objects.requireNonNull(pos, "pos").asLong(),
                autoCollectEligible,
                preferredDimensionId,
                preferredBlockPos,
                preferredRadius
        );
    }

    public AnimalProduceLedgerEntry withoutProjection() {
        if (!isProjected()) {
            return this;
        }
        return new AnimalProduceLedgerEntry(
                entryId,
                buildingId,
                animalId,
                producedAbsDay,
                itemId,
                quality,
                "",
                0L,
                autoCollectEligible,
                preferredDimensionId,
                preferredBlockPos,
                preferredRadius
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("entryId", entryId);
        tag.putString("buildingId", buildingId);
        tag.putLong("animalId", animalId);
        tag.putInt("producedAbsDay", producedAbsDay);
        tag.putString("itemId", itemId.toString());
        tag.putInt("quality", quality);
        tag.putBoolean("autoCollectEligible", autoCollectEligible);
        if (hasPreferredAnchor()) {
            tag.putString("preferredDimensionId", preferredDimensionId);
            tag.putLong("preferredBlockPos", preferredBlockPos);
            tag.putInt("preferredRadius", preferredRadius);
        }
        if (isProjected()) {
            tag.putString("projectedDimensionId", projectedDimensionId);
            tag.putLong("projectedBlockPos", projectedBlockPos);
        }
        return tag;
    }

    public static AnimalProduceLedgerEntry load(CompoundTag tag) {
        ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("itemId"));
        if (itemId == null) {
            throw new IllegalArgumentException(
                    "Invalid animal produce ledger item: " + tag.getString("itemId"));
        }
        return new AnimalProduceLedgerEntry(
                tag.getLong("entryId"),
                tag.getString("buildingId"),
                tag.getLong("animalId"),
                tag.getInt("producedAbsDay"),
                itemId,
                tag.getInt("quality"),
                tag.getString("projectedDimensionId"),
                tag.getLong("projectedBlockPos"),
                !tag.contains("autoCollectEligible")
                        || tag.getBoolean("autoCollectEligible"),
                tag.getString("preferredDimensionId"),
                tag.getLong("preferredBlockPos"),
                tag.getInt("preferredRadius")
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
