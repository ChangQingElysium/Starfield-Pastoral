package com.stardew.craft.api.v1.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;

/** Immutable server-side logical-location transition. */
public record StardewLocationTransition(
        ServerPlayer player,
        @Nullable ResourceLocation previousLocation,
        @Nullable ResourceLocation currentLocation,
        ResourceLocation previousDimension,
        ResourceLocation currentDimension,
        BlockPos previousPosition,
        BlockPos currentPosition,
        Reason reason
) {
    public StardewLocationTransition {
        player = Objects.requireNonNull(player, "player");
        previousDimension = Objects.requireNonNull(
                previousDimension, "previousDimension");
        currentDimension = Objects.requireNonNull(
                currentDimension, "currentDimension");
        previousPosition = Objects.requireNonNull(
                previousPosition, "previousPosition").immutable();
        currentPosition = Objects.requireNonNull(
                currentPosition, "currentPosition").immutable();
        reason = Objects.requireNonNull(reason, "reason");
    }

    public boolean leftLocation() {
        return previousLocation != null
                && !previousLocation.equals(currentLocation);
    }

    public boolean enteredLocation() {
        return currentLocation != null
                && !currentLocation.equals(previousLocation);
    }

    public enum Reason {
        INITIAL,
        LOCATION_CHANGED,
        DIMENSION_CHANGED,
        LOGOUT
    }
}
