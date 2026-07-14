package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * set_cave_choice: applies a farm-cave type choice to the player's farm (server-side).
 * JSON: {"cmd":"set_cave_choice", "choice":"mushrooms"}  // or "fruit_bats" / "none"
 * State command — the choice sticks even if the cutscene is skipped.
 */
public class SetCaveChoiceCommand implements EventCommand {

    private final String choice;

    public SetCaveChoiceCommand(String choice) {
        this.choice = choice;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("set_cave_choice", choice);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
