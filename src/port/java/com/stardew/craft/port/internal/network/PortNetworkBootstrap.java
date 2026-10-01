package com.stardew.craft.port.internal.network;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

/**
 * Fires the NeoForge-shaped {@code RegisterPayloadHandlersEvent} on the StardewCraft mod bus during
 * common setup (both physical sides, before Forge locks channel registration in NETWORK_LOCK), so the
 * existing {@code modEventBus.addListener(PacketHandler::register)} wiring and
 * {@code @EventBusSubscriber} payload listeners run unchanged.
 */
@Mod.EventBusSubscriber(modid = "stardewcraft", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortNetworkBootstrap {
    private PortNetworkBootstrap() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        IEventBus bus = ModList.get().getModContainerById("stardewcraft")
                .map(container -> ((FMLModContainer) container).getEventBus())
                .orElseThrow(() -> new IllegalStateException("StardewCraft mod container is missing"));
        PortNetwork.initialize(bus);
    }
}
