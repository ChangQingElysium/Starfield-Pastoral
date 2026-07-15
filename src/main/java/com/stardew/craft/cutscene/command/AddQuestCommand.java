package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * add_quest: accept a quest by id (server-side).
 * JSON: {"cmd":"add_quest", "quest_id":"9"}
 */
public class AddQuestCommand implements EventCommand {
    private final String questId;

    public AddQuestCommand(String questId) {
        this.questId = questId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("add_quest", questId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
