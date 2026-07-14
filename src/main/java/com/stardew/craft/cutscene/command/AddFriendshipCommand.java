package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * add_friendship: adds friendship points for an NPC (server-side).
 * JSON: {"cmd":"add_friendship", "npc":"abigail", "points":250}
 * This is a state command — it runs even when the event is skipped.
 */
public class AddFriendshipCommand implements EventCommand {

    private final String npcId;
    private final int points;

    public AddFriendshipCommand(String npcId, int points) {
        this.npcId = npcId;
        this.points = points;
    }

    @Override
    public void start(EventPlayer player) {
        // Format: "npc_id:points"
        player.sendServerAction("add_friendship", npcId + ":" + points);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player); // still apply when skipped
    }
}
