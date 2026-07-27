package com.stardew.craft.api.v1.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/** Bounded query context for acquisition-source providers. */
public record StardewAcquisitionContext(
        ItemStack target,
        ResourceLocation targetItemId,
        @Nullable ServerPlayer player
) {
    public StardewAcquisitionContext {
        target = Objects.requireNonNull(target, "target").copy();
        targetItemId = Objects.requireNonNull(
                targetItemId, "targetItemId");
        if (target.isEmpty()
                || !BuiltInRegistries.ITEM.getKey(target.getItem())
                        .equals(targetItemId)) {
            throw new IllegalArgumentException(
                    "target stack and target item ID must match");
        }
    }

    @Override
    public ItemStack target() {
        return target.copy();
    }
}
