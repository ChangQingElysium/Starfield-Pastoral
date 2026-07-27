package com.stardew.craft.api.v1.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Set;

/** Immutable identity and source data for one server-authoritative kill. */
public record StardewCombatKillContext(
        ServerPlayer player,
        LivingEntity target,
        DamageSource source,
        ResourceLocation targetType,
        Set<String> targetTags,
        ResourceKey<Level> dimension,
        BlockPos position
) {
    public StardewCombatKillContext {
        player = Objects.requireNonNull(player, "player");
        target = Objects.requireNonNull(target, "target");
        source = Objects.requireNonNull(source, "source");
        targetType = Objects.requireNonNull(targetType, "targetType");
        targetTags = Set.copyOf(targetTags);
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").immutable();
    }
}
