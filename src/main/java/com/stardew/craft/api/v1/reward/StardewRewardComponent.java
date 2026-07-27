package com.stardew.craft.api.v1.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** One immutable, client-display-safe component of a reward preview. */
public record StardewRewardComponent(
        Kind kind,
        ResourceLocation id,
        long amount,
        ItemStack icon,
        Component display,
        boolean runtimeDependent
) {
    public StardewRewardComponent {
        kind = Objects.requireNonNull(kind, "kind");
        id = Objects.requireNonNull(id, "id");
        if (amount < 0L) {
            throw new IllegalArgumentException(
                    "Reward component amount cannot be negative");
        }
        icon = Objects.requireNonNull(icon, "icon").copy();
        display = Objects.requireNonNull(display, "display").copy();
    }

    @Override
    public ItemStack icon() {
        return icon.copy();
    }

    @Override
    public Component display() {
        return display.copy();
    }

    public enum Kind {
        ITEM,
        CURRENCY,
        ACTION,
        OTHER
    }
}
