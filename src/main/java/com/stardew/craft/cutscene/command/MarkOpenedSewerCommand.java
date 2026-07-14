package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

public class MarkOpenedSewerCommand implements EventCommand {
    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("mark_opened_sewer", "");
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
