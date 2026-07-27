package com.stardew.craft.api.v1.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** Server-authoritative context captured before a timed machine consumes one input. */
public record StardewProductionContext(
        ResourceLocation machineId,
        ServerLevel level,
        BlockPos position,
        ItemStack input,
        Optional<ServerPlayer> player,
        boolean automation
) {
    public StardewProductionContext {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(input, "input");
        if (input.isEmpty()) {
            throw new IllegalArgumentException(
                    "production input must not be empty");
        }
        input = input.copy();
        player = Objects.requireNonNull(player, "player");
        player.ifPresent(value -> {
            if (value.serverLevel() != level) {
                throw new IllegalArgumentException(
                        "production player must be in the supplied level");
            }
        });
    }

    @Override
    public ItemStack input() {
        return input.copy();
    }
}
