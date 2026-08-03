package com.stardew.craft.client;

import com.stardew.craft.client.gui.NightMarketMermaidScreen;
import com.stardew.craft.client.gui.StardewCollectivePauseScreen;
import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.client.gui.auction.AuctionBidScreen;
import com.stardew.craft.client.gui.common.StardewConfirmDialogScreen;
import com.stardew.craft.client.gui.common.StardewNpcDialogueScreen;
import com.stardew.craft.client.gui.common.StardewObjectDialogueScreen;
import com.stardew.craft.client.gui.menu.StardewGameMenuScreen;
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
    void thirdPartyRealtimeScreensKeepBlockEntitiesRunning() {
        assertFalse(StardewPauseClientState.isNonGameplayScreen(new TestContainerScreen()));
    }

    @Test
    void scopedStardewMenuPausesWithoutUsingVanillaGlobalPause() {
        TestScopedPauseScreen screen = new TestScopedPauseScreen();
        assertFalse(screen.isPauseScreen());
        assertTrue(StardewPauseClientState.isNonGameplayScreen(screen));
        assertTrue(StardewCollectivePauseScreen.class.isAssignableFrom(StardewGameMenuScreen.class));
    }

    @Test
    void dialogueScreensParticipateInCollectivePause() {
        assertTrue(StardewCollectivePauseScreen.class.isAssignableFrom(
                StardewNpcDialogueScreen.class));
        assertTrue(StardewCollectivePauseScreen.class.isAssignableFrom(
                StardewObjectDialogueScreen.class));
        assertTrue(StardewCollectivePauseScreen.class.isAssignableFrom(
                StardewConfirmDialogScreen.class));
    }

    @Test
    void gameplayWithoutAScreenKeepsSimulationRunning() {
        assertFalse(StardewPauseClientState.isNonGameplayScreen(null));
    }

    @Test
    void liveTimedAndRadialScreensDeclareTheRealtimeContract() {
        assertTrue(StardewRealtimeScreen.class.isAssignableFrom(AuctionBidScreen.class));
        assertTrue(StardewRealtimeScreen.class.isAssignableFrom(NightMarketMermaidScreen.class));
    }

    private static class TestScreen extends Screen {
        private TestScreen() {
            super(Component.empty());
        }
    }

    private static final class TestRealtimeScreen extends TestScreen implements StardewRealtimeScreen {
    }

    private static class TestContainerScreen extends TestScreen {
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final class TestScopedPauseScreen extends TestContainerScreen
            implements StardewCollectivePauseScreen {
    }
}
