package com.stardew.craft.platform.fabric.brewing;

import com.stardew.craft.platform.fabric.event.Event;
import net.minecraft.core.RegistryAccess;

public final class RegisterBrewingRecipesEvent extends Event {
    private final BrewingRecipes.Builder builder;
    private final RegistryAccess registryAccess;

    public RegisterBrewingRecipesEvent(BrewingRecipes.Builder builder, RegistryAccess registryAccess) {
        this.builder = java.util.Objects.requireNonNull(builder);
        this.registryAccess = java.util.Objects.requireNonNull(registryAccess);
    }

    public BrewingRecipes.Builder getBuilder() { return builder; }
    public RegistryAccess getRegistryAccess() { return registryAccess; }
}
