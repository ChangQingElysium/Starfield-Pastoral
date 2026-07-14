package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * remove_item: removes item(s) from the player server-side.
 * JSON: {"cmd":"remove_item", "item":"stardewcraft:cave_carrot", "count":1}
 */
public class RemoveItemCommand implements EventCommand {

    private final String itemId;
    private final int count;

    public RemoveItemCommand(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("remove_item", itemId + ":" + count);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
