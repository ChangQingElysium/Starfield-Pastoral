package com.stardew.craft.api.v1.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-authoritative context for batch, repeating, passive or environmental
 * machine cycles. Passive/environmental cycles may have an empty input.
 */
public record StardewMachineCycleContext(
        ResourceLocation machineId,
        ServerLevel level,
        BlockPos position,
        StardewMachineCycleKind kind,
        ItemStack input,
        Optional<ServerPlayer> player,
        boolean automation
) {
    public StardewMachineCycleContext {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(level, "level");
        position = Objects.requireNonNull(
                position, "position").immutable();
        Objects.requireNonNull(kind, "kind");
        input = Objects.requireNonNull(input, "input").copy();
        player = Objects.requireNonNull(player, "player");
        player.ifPresent(value -> {
            if (value.serverLevel() != level) {
                throw new IllegalArgumentException(
                        "machine-cycle player must be in the supplied level");
            }
        });
    }

    @Override
    public ItemStack input() {
        return input.copy();
    }
}
