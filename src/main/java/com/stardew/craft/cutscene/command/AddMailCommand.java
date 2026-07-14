package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * add_mail: adds a mail flag on the player (server-side).
 * Alias for set_flag with a different semantic intent (SDV parity).
 * JSON: {"cmd":"add_mail", "id":"spring_2_1"}
 * This is a state command — it runs even when the event is skipped.
 */
public class AddMailCommand implements EventCommand {

    private final String mailId;

    public AddMailCommand(String mailId) {
        this.mailId = mailId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("set_flag", mailId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
