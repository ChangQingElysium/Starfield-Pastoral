package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * remove_quest: remove an active quest by id (server-side).
 * JSON: {"cmd":"remove_quest", "quest_id":"13"}
 */
public class RemoveQuestCommand implements EventCommand {
    private final String questId;

    public RemoveQuestCommand(String questId) {
        this.questId = questId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("remove_quest", questId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
