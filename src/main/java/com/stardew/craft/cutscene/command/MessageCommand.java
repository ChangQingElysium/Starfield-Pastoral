package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * SDV event {@code message}: open an iconless bottom dialogue box and wait for dismissal.
 * JSON: {"cmd":"message", "text":"stardewcraft.event.some_message"}
 * Keys beginning with "stardewcraft.", "event.", or "message." are localized.
 */
public class MessageCommand implements EventCommand {
	private final ObjectDialogueCommand delegate;

    public MessageCommand(String text) {
		this.delegate = new ObjectDialogueCommand(text);
    }

    @Override
    public void start(EventPlayer player) {
		delegate.start(player);
    }

	@Override
	public void tick(EventPlayer player) {
		delegate.tick(player);
	}

	@Override
	public boolean isComplete() {
		return delegate.isComplete();
	}
}
