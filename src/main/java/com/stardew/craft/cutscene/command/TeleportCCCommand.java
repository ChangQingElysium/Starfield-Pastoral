package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * teleport_cc: requests server to teleport the real player into the Community Center interior.
 * Blocks until the player has actually arrived (position check) and chunks are loaded.
 * JSON: {"cmd":"teleport_cc"}
 */
public class TeleportCCCommand implements EventCommand {

    /** CC interior Y level is ~70; town Y is ~-10. Use Y > 50 as arrival check. */
    private static final double CC_INTERIOR_Y_THRESHOLD = 50.0;
    private static final int MAX_WAIT_TICKS = 200; // 10 second timeout

    private boolean sent;
    private int waitTicks;
    private boolean done;

    @Override
    public void start(EventPlayer player) {
        sent = false;
        waitTicks = 0;
        done = false;
    }

    @Override
    public void tick(EventPlayer player) {
        if (done) return;

        if (!sent) {
            player.markRealPlayerMovedByServer();
            player.sendServerAction("teleport_cc", "");
            sent = true;
            return;
        }

        waitTicks++;

        // Check if player has arrived in CC interior
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && localPlayer.getY() > CC_INTERIOR_Y_THRESHOLD) {
            // Also verify chunk is loaded at player's position
            if (localPlayer.level().hasChunk(localPlayer.blockPosition().getX() >> 4, localPlayer.blockPosition().getZ() >> 4)) {
                done = true;
                return;
            }
        }

        if (waitTicks > MAX_WAIT_TICKS) {
            // Timeout — proceed anyway to avoid softlock
            done = true;
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }
}
