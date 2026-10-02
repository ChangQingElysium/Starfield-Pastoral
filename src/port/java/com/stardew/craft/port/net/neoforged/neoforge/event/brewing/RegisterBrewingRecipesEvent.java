package com.stardew.craft.port.net.neoforged.neoforge.event.brewing;

import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code RegisterBrewingRecipesEvent} (game bus). Forge 1.20.1 keeps one global
 * {@link BrewingRecipeRegistry}, so the event is posted once during common setup (main thread) and
 * {@code getBuilder().addRecipe} registers there. NeoForge rebuilds brewing per registry access instead; the mod only
 * adds static recipes, so a single registration is equivalent.
 */
public class RegisterBrewingRecipesEvent extends Event {
    private final Builder builder = new Builder();

    public RegisterBrewingRecipesEvent() {}

    /** PORT(1.20.1): stands in for {@code PotionBrewing.Builder}; only {@code addRecipe} is used by the mod. */
    public Builder getBuilder() {
        return this.builder;
    }

    public static final class Builder {
        private Builder() {}

        public void addRecipe(IBrewingRecipe recipe) {
            BrewingRecipeRegistry.addRecipe(recipe);
        }
    }
}
