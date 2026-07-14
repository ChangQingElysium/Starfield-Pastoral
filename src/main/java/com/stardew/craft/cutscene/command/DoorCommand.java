package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * door: opens or closes an existing server-side door block.
 * JSON: {"cmd":"door", "x":-19, "y":39, "z":32, "open":true}
 */
public class DoorCommand implements EventCommand {

    private final int x;
    private final int y;
    private final int z;
    private final boolean open;

    public DoorCommand(int x, int y, int z, boolean open) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.open = open;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("door", x + "," + y + "," + z + ":" + open);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
