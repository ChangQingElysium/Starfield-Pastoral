package com.stardew.craft.client.gui.casino;

import com.stardew.craft.network.payload.CasinoGameStatePayload;
import net.minecraft.client.Minecraft;

public final class CasinoScreenRouter {
    private CasinoScreenRouter() {
    }

    public static void accept(CasinoGameStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.game() == CasinoGameStatePayload.GAME_CALICO_JACK) {
            if (minecraft.screen instanceof CalicoJackScreen screen
                    && screen.sessionId() == payload.sessionId()) {
                screen.acceptState(payload);
            } else {
                minecraft.setScreen(new CalicoJackScreen(payload));
            }
        } else if (payload.game() == CasinoGameStatePayload.GAME_SLOTS) {
            if (minecraft.screen instanceof SlotsScreen screen
                    && screen.sessionId() == payload.sessionId()) {
                screen.acceptState(payload);
            } else {
                minecraft.setScreen(new SlotsScreen(payload));
            }
        }
    }
}
