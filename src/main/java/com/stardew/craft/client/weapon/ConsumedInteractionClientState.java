package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Tracks whether vanilla already consumed the current right-click interaction. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ConsumedInteractionClientState {
    private static boolean consumedThisTick;

    private ConsumedInteractionClientState() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        consumedThisTick = false;
    }

    public static void markConsumed() {
        consumedThisTick = true;
    }

    public static boolean wasConsumedThisTick() {
        return consumedThisTick;
    }
}
