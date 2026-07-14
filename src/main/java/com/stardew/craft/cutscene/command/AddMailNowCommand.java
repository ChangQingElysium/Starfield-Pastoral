package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

public class AddMailNowCommand implements EventCommand {
    private final String mailId;

    public AddMailNowCommand(String mailId) {
        this.mailId = mailId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("add_mail_now", mailId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
