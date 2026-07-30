package com.stardew.craft.api.v1.mapinteraction;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Objects;

/** Server-authoritative context for a right-click map interaction. */
public record StardewMapInteractionContext(
        ServerPlayer player,
        ServerLevel level,
        InteractionHand hand,
        BlockHitResult hit,
        @Nullable ResourceLocation definitionId
) {
    public StardewMapInteractionContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(hit, "hit");
        if (player.level() != level) {
            throw new IllegalArgumentException(
                    "Map interaction player must be in the supplied level");
        }
    }

    public StardewMapInteractionContext withDefinition(
            ResourceLocation id
    ) {
        return new StardewMapInteractionContext(
                player, level, hand, hit,
                Objects.requireNonNull(id, "id"));
    }
}
