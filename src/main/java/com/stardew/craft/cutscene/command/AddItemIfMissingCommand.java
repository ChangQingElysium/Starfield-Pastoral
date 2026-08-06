package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * Gives an item only when the player does not already carry that item.
 * This mirrors SDV gift-event end behaviors such as Garden Pot (900553).
 */
public final class AddItemIfMissingCommand implements EventCommand {
    private final String itemId;
    private final int count;

    public AddItemIfMissingCommand(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("add_item_if_missing", itemId + ":" + count);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
