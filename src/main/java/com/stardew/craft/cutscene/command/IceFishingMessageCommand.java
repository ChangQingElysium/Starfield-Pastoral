package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.festival.client.IceFishingCutsceneClientState;

/** Player-specific Festival of Ice closing thought, rendered through SDV event {@code message}. */
public final class IceFishingMessageCommand implements EventCommand {
    private final String winnerText;
    private final String otherText;
    private MessageCommand delegate;

    public IceFishingMessageCommand(String winnerText, String otherText) {
        this.winnerText = winnerText;
        this.otherText = otherText;
    }

    @Override
    public void start(EventPlayer player) {
        String text = IceFishingCutsceneClientState.playerWon() ? winnerText : otherText;
        delegate = new MessageCommand(text);
        delegate.start(player);
    }

    @Override
    public void tick(EventPlayer player) {
        if (delegate != null) {
            delegate.tick(player);
        }
    }

    @Override
    public boolean isComplete() {
        return delegate != null && delegate.isComplete();
    }
}
