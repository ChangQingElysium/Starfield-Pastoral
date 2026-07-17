package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/** Persists the Bear's Knowledge unlock even when the cutscene is skipped. */
public final class GrantBearKnowledgeCommand implements EventCommand {
    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("grant_bear_knowledge", "");
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
