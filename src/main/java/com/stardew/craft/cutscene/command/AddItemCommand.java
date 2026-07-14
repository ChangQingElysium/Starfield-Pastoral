package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * add_item: gives item(s) to the player (server-side).
 * JSON: {"cmd":"add_item", "item":"stardewcraft:parsnip_seeds", "count":15}
 * This is a state command — it runs even when the event is skipped.
 */
public class AddItemCommand implements EventCommand {

    private final String itemId;
    private final int count;

    public AddItemCommand(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    @Override
    public void start(EventPlayer player) {
        // Format: "item_id:count"
        player.sendServerAction("add_item", itemId + ":" + count);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
