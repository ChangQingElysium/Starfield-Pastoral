package com.stardew.craft.api.v1.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Objects;

/** Server-side, read-only target context supplied to addon hint providers. */
public record StardewInteractionHintContext(
        ServerPlayer player,
        @Nullable Entity entity,
        BlockPos pos,
        BlockState blockState
) {
    public StardewInteractionHintContext {
        Objects.requireNonNull(player, "player");
        pos = Objects.requireNonNull(pos, "pos").immutable();
        Objects.requireNonNull(blockState, "blockState");
    }

    public boolean isEntityTarget() {
        return entity != null;
    }
}
