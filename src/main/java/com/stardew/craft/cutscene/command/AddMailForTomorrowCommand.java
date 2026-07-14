package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

public class AddMailForTomorrowCommand implements EventCommand {
    private final String mailId;

    public AddMailForTomorrowCommand(String mailId) {
        this.mailId = mailId;
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("add_mail_for_tomorrow", mailId);
    }

    @Override public void tick(EventPlayer player) {}
    @Override public boolean isComplete() { return true; }
    @Override public boolean isStateCommand() { return true; }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }
}
