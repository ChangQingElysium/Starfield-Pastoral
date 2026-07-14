package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * set_flag: set a mail flag on the player (server-side).
 * JSON: {"cmd":"set_flag", "flag":"seenJunimoNote"}
 */
public class SetFlagCommand implements EventCommand {
    private final String flag;

    public SetFlagCommand(String flag) {
        this.flag = flag;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("set_flag", flag);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
}
