package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/** Tracks whether vanilla already consumed the current right-click interaction. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
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
