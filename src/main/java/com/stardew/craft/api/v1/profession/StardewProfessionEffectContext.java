package com.stardew.craft.api.v1.profession;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** One profession effect invocation. Handlers must return the value for the next handler. */
public record StardewProfessionEffectContext(
        ResourceLocation profession,
        ResourceLocation operation,
        @Nullable ServerPlayer player,
        ItemStack stack,
        double value
) {
}
