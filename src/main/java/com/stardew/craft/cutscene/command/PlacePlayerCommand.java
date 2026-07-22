package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/** Places the real player at an authored endpoint when a cutscene ends or is skipped. */
public final class PlacePlayerCommand implements EventCommand {
    private final String payload;

    public PlacePlayerCommand(double x, double y, double z, float yaw, float pitch) {
        this.payload = x + "," + y + "," + z + "," + yaw + "," + pitch;
    }

    @Override
    public void start(EventPlayer player) {
        player.markRealPlayerMovedByServer();
        player.sendServerAction("place_player", payload);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
