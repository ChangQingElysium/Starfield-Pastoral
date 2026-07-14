package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

public class ApplyUnlockSourceCommand implements EventCommand {
    private final String sourceId;

    public ApplyUnlockSourceCommand(String sourceId) {
        this.sourceId = sourceId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("apply_unlock_source", sourceId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
