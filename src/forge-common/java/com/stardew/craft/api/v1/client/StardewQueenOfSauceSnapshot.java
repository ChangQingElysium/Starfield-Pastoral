package com.stardew.craft.api.v1.client;

import java.util.Objects;

/** Today's personal TV cooking broadcast. Recipe IDs use the television's cooking unlock IDs. */
public record StardewQueenOfSauceSnapshot(String recipeId, boolean rerun,
        boolean recipeKnown, boolean watchedToday) {
    public StardewQueenOfSauceSnapshot {
        Objects.requireNonNull(recipeId, "recipeId");
        if (recipeId.isBlank()) throw new IllegalArgumentException("Blank cooking recipe ID");
    }

    /** Whether watching today's broadcast can teach this player a recipe. */
    public boolean canLearnRecipe() { return !recipeKnown && !watchedToday; }
}
