package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

public class MoonlightJelliesStageCommand implements EventCommand {
    private final String stage;
    private final int waitTicks;
    private boolean sent;
    private int ticks;

    public MoonlightJelliesStageCommand(String stage, int waitTicks) {
        this.stage = stage == null || stage.isBlank() ? "release" : stage;
        this.waitTicks = Math.max(0, waitTicks);
    }

    @Override
    public void start(EventPlayer player) {
        player.sendServerAction("moonlight_jellies_stage", stage);
        sent = true;
        ticks = 0;
    }

    @Override
    public void tick(EventPlayer player) {
        if (sent && ticks < waitTicks) {
            ticks++;
        }
    }

    @Override
    public boolean isComplete() {
        return sent && ticks >= waitTicks;
    }
}
