package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * add_recipe: teach the player a crafting/cooking recipe (server-side).
 * JSON: {"cmd":"add_recipe", "recipe":"Furnace"}
 * State command — still applies when the cutscene is skipped.
 */
public class AddRecipeCommand implements EventCommand {

    private final String recipeId;

    public AddRecipeCommand(String recipeId) {
        this.recipeId = recipeId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("add_recipe", recipeId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
