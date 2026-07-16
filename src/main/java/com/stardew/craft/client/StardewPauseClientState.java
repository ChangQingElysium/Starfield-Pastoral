package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
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
    private static int ticksSinceReport;
    private static boolean wasInStardewTimeDimension;

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
        if (!inStardewTimeDimension) {
            if (wasInStardewTimeDimension) {
                PacketDistributor.sendToServer(new StardewPauseStatePayload(false));
            }
            reset();
            return;
        }

        wasInStardewTimeDimension = true;
        boolean nonGameplay = isNonGameplayScreen(minecraft.screen);
        ticksSinceReport++;
        if (lastReported == null || lastReported != nonGameplay || ticksSinceReport >= HEARTBEAT_TICKS) {
            PacketDistributor.sendToServer(new StardewPauseStatePayload(nonGameplay));
            lastReported = nonGameplay;
            ticksSinceReport = 0;
        }
    }

    static boolean isNonGameplayScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        // These screens are direct real-time controls, equivalent to SDV's BobberBar exception.
        return !(screen instanceof ChatScreen
            || screen instanceof ReceivingLevelScreen
            || screen instanceof LevelLoadingScreen
            || screen instanceof ProgressScreen
            || screen instanceof StardewRealtimeScreen);
    }

    private static void reset() {
        lastReported = null;
        ticksSinceReport = 0;
        wasInStardewTimeDimension = false;
    }
}
