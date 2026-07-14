package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

public class EggFestivalStageCommand implements EventCommand {
    private final String stage;
    private final int waitTicks;
    private boolean sent;
    private int ticks;

    public EggFestivalStageCommand(String stage, int waitTicks) {
        this.stage = stage == null || stage.isBlank() ? "main" : stage;
        this.waitTicks = Math.max(0, waitTicks);
    }

    @Override
    public void start(EventPlayer player) {
        player.markRealPlayerMovedByServer();
        player.sendServerAction("egg_festival_blackout", stage);
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
