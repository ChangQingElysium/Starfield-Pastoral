package com.stardew.craft.api.v1.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/** Complete geometry and presentation descriptor for one instanced farm layout. */
public record StardewFarmLayout(
        ResourceLocation id,
        boolean selectable,
        Component displayName,
        Component description,
        ResourceLocation iconTexture,
        ResourceLocation schematic,
        int originY,
        int width,
        int height,
        int length,
        BlockPos spawnOffset,
        float spawnYaw,
        BlockPos greenhouseOffset,
        BlockPos totemOffset,
        Entry entrySouth,
        Entry entryEast,
        Entry entryWest,
        @Nullable String biomeId,
        @Nullable BlockPos forageZoneMin,
        @Nullable BlockPos forageZoneMax,
        @Nullable Region caveBlackWall,
        @Nullable Region cavePortalWall,
        @Nullable Region caveClearBox,
        @Nullable BlockPos caveExitSpawn,
        float caveExitYaw
) {
    public StardewFarmLayout {
        id = Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName");
        description = Objects.requireNonNull(description, "description");
        iconTexture = Objects.requireNonNull(iconTexture, "iconTexture");
        schematic = Objects.requireNonNull(schematic, "schematic");
        if (width <= 0 || height <= 0 || length <= 0
                || width > 512 || length > 512) {
            throw new IllegalArgumentException(
                    "farm layout dimensions must be positive and fit one 512x512 slot");
        }
        spawnOffset = immutable(spawnOffset, "spawnOffset");
        greenhouseOffset = immutable(greenhouseOffset, "greenhouseOffset");
        totemOffset = immutable(totemOffset, "totemOffset");
        entrySouth = Objects.requireNonNull(entrySouth, "entrySouth");
        entryEast = Objects.requireNonNull(entryEast, "entryEast");
        entryWest = Objects.requireNonNull(entryWest, "entryWest");
        biomeId = biomeId == null || biomeId.isBlank()
                ? null : biomeId.trim();
        forageZoneMin = immutableNullable(forageZoneMin);
        forageZoneMax = immutableNullable(forageZoneMax);
        caveExitSpawn = immutableNullable(caveExitSpawn);
    }

    public BlockPos boundsMin() {
        return BlockPos.ZERO;
    }

    public BlockPos boundsMax() {
        return new BlockPos(width - 1, height - 1, length - 1);
    }

    private static BlockPos immutable(BlockPos value, String name) {
        return Objects.requireNonNull(value, name).immutable();
    }

    @Nullable
    private static BlockPos immutableNullable(@Nullable BlockPos value) {
        return value == null ? null : value.immutable();
    }

    public record Entry(
            BlockPos teleportOffset,
            float yaw,
            BlockPos exitMin,
            BlockPos exitMax
    ) {
        public Entry {
            teleportOffset = immutable(teleportOffset, "teleportOffset");
            exitMin = immutable(exitMin, "exitMin");
            exitMax = immutable(exitMax, "exitMax");
        }
    }

    public record Region(BlockPos min, BlockPos max) {
        public Region {
            min = immutable(min, "min");
            max = immutable(max, "max");
        }
    }
}
