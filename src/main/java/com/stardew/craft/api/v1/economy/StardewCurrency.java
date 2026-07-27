package com.stardew.craft.api.v1.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Client-safe metadata for a server-authoritative currency.
 *
 * <p>The balance implementation is registered separately so menus and catalogs can inspect
 * currency metadata without receiving authority to mutate player state.
 */
public record StardewCurrency(
        ResourceLocation id,
        Component displayName,
        ItemStack icon,
        long maximumBalance
) {
    public StardewCurrency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        displayName = displayName.copy();
        icon = icon.copy();
        if (maximumBalance <= 0L) {
            throw new IllegalArgumentException("maximumBalance must be positive");
        }
    }

    @Override
    public ItemStack icon() {
        return icon.copy();
    }

    @Override
    public Component displayName() {
        return displayName.copy();
    }
}
