package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.StardewCollectivePauseScreen;
import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.network.payload.StardewPauseStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reports modal/non-gameplay client state without coupling every screen to the time system. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class StardewPauseClientState {

    private static final int HEARTBEAT_TICKS = 40;

    private static Boolean lastReported;
    private static Boolean lastReportedGuiOpen;
    private static int ticksSinceReport;

    private StardewPauseClientState() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        boolean inStardewTimeDimension = ModDimensions.STARDEW_VALLEY.equals(minecraft.level.dimension())
            || ModMiningDimensions.STARDEW_MINING.equals(minecraft.level.dimension());
        boolean nonGameplay = inStardewTimeDimension && isNonGameplayScreen(minecraft.screen);
        boolean guiOpen = minecraft.screen != null;
        ticksSinceReport++;
        if (lastReported == null
                || lastReported != nonGameplay
                || lastReportedGuiOpen == null
                || lastReportedGuiOpen != guiOpen
                || ticksSinceReport >= HEARTBEAT_TICKS) {
            report(nonGameplay, guiOpen);
        }
    }

    /** Immediate reply to a server probe before fullscreen item feedback. */
    public static void reportNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean inStardewTimeDimension =
                ModDimensions.STARDEW_VALLEY.equals(minecraft.level.dimension())
                        || ModMiningDimensions.STARDEW_MINING.equals(minecraft.level.dimension());
        report(
                inStardewTimeDimension && isNonGameplayScreen(minecraft.screen),
                minecraft.screen != null);
    }

    private static void report(boolean nonGameplay, boolean guiOpen) {
        PacketDistributor.sendToServer(new StardewPauseStatePayload(nonGameplay, guiOpen));
        lastReported = nonGameplay;
        lastReportedGuiOpen = guiOpen;
        ticksSinceReport = 0;
    }

    static boolean isNonGameplayScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        // Respect the screen's vanilla realtime contract so third-party container screens (for
        // example AE2 terminals) do not freeze the block entities that drive their own menus.
        // Explicit Stardew realtime controls remain an override for timed minigames and shows.
        return (screen.isPauseScreen() || screen instanceof StardewCollectivePauseScreen)
            && !(screen instanceof ChatScreen
            || screen instanceof ReceivingLevelScreen
            || screen instanceof LevelLoadingScreen
            || screen instanceof ProgressScreen
            || screen instanceof StardewRealtimeScreen);
    }

    private static void reset() {
        lastReported = null;
        lastReportedGuiOpen = null;
        ticksSinceReport = 0;
    }
}
