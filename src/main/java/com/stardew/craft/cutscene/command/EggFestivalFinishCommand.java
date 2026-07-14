package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;

public class EggFestivalFinishCommand implements EventCommand {
    private static final int MAX_WAIT_TICKS = 400;

    private boolean sent;
    private int ticks;
    private boolean done;

    @Override
    public void start(EventPlayer player) {
        sent = false;
        ticks = 0;
        done = false;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done) {
            return;
        }
        if (!sent) {
            player.markRealPlayerMovedByServer();
            player.sendServerAction("egg_festival_award_complete", "");
            sent = true;
            return;
        }
        ticks++;
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && !isInsideFestivalBounds(localPlayer.getX(), localPlayer.getY(), localPlayer.getZ())) {
            done = true;
            return;
        }
        if (ticks >= MAX_WAIT_TICKS) {
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    private static boolean isInsideFestivalBounds(double x, double y, double z) {
        return x >= -46.0D && x < 138.0D
            && y >= 64.0D && y < 85.0D
            && z >= -36.0D && z < 54.0D;
    }
}
