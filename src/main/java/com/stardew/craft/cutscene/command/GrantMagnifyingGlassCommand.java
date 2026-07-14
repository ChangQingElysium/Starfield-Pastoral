package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/** Grants the Special Item unlock used by the Secret Note system. */
public final class GrantMagnifyingGlassCommand implements EventCommand {
    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("grant_magnifying_glass", "");
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
