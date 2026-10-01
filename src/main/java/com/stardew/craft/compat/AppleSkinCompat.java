package com.stardew.craft.compat;

import com.stardew.craft.client.hud.StardewPlayerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import squeek.appleskin.api.event.HUDOverlayEvent;

public final class AppleSkinCompat {
    private AppleSkinCompat() {
    }

    @SubscribeEvent
    public static void onAppleSkinSaturationOverlay(HUDOverlayEvent.Saturation event) {
        cancelInStardewHud(event);
    }

    @SubscribeEvent
    public static void onAppleSkinExhaustionOverlay(HUDOverlayEvent.Exhaustion event) {
        cancelInStardewHud(event);
    }

    @SubscribeEvent
    public static void onAppleSkinHungerRestoredOverlay(HUDOverlayEvent.HungerRestored event) {
        cancelInStardewHud(event);
    }

    @SubscribeEvent
    public static void onAppleSkinHealthRestoredOverlay(HUDOverlayEvent.HealthRestored event) {
        cancelInStardewHud(event);
    }

    private static void cancelInStardewHud(HUDOverlayEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && StardewPlayerHud.shouldRenderCustomHUD(player)) {
            event.setCanceled(true);
        }
    }
}
