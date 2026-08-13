package com.stardew.craft.cutscene.command;

import com.stardew.craft.client.cutscene.GreenTeaVisionClientState;
import com.stardew.craft.cutscene.runtime.EventPlayer;

/** Plays the original Caroline tea-vision interlude from Sunroom event 719926. */
public final class GreenTeaVisionCommand implements EventCommand {
    private boolean complete;

    @Override
    public void start(EventPlayer player) {
        complete = false;
        GreenTeaVisionClientState.start();
    }

    @Override
    public void tick(EventPlayer player) {
        GreenTeaVisionClientState.tick();
        complete = !GreenTeaVisionClientState.isActive();
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void onSkip(EventPlayer player) {
        GreenTeaVisionClientState.clear();
        complete = true;
    }
}
