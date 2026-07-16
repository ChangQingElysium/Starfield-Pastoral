package com.stardew.craft.client;

import com.stardew.craft.client.gui.NightMarketMermaidScreen;
import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.client.gui.WarpWheelScreen;
import com.stardew.craft.client.gui.auction.AuctionBidScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewPauseClientStateTest {

    @Test
    void ordinaryScreensRequestCollectiveSimulationPause() {
        assertTrue(StardewPauseClientState.isNonGameplayScreen(new TestScreen()));
    }

    @Test
    void realtimeScreensKeepSimulationRunning() {
        assertFalse(StardewPauseClientState.isNonGameplayScreen(new TestRealtimeScreen()));
    }

    @Test
    void gameplayWithoutAScreenKeepsSimulationRunning() {
        assertFalse(StardewPauseClientState.isNonGameplayScreen(null));
    }

    @Test
    void liveTimedAndRadialScreensDeclareTheRealtimeContract() {
        assertTrue(StardewRealtimeScreen.class.isAssignableFrom(AuctionBidScreen.class));
        assertTrue(StardewRealtimeScreen.class.isAssignableFrom(NightMarketMermaidScreen.class));
        assertTrue(StardewRealtimeScreen.class.isAssignableFrom(WarpWheelScreen.class));
    }

    private static class TestScreen extends Screen {
        private TestScreen() {
            super(Component.empty());
        }
    }

    private static final class TestRealtimeScreen extends TestScreen implements StardewRealtimeScreen {
    }
}
