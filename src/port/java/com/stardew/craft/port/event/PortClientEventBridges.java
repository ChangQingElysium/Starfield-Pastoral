package com.stardew.craft.port.event;

import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** PORT(1.20.1): client-only Forge -> NeoForge event bridges (see {@link PortEventBridges}). */
public final class PortClientEventBridges {
    private PortClientEventBridges() {}

    static void install(IEventBus modBus) {
        IEventBus forge = MinecraftForge.EVENT_BUS;

        forge.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> forge.post(
                event.phase == TickEvent.Phase.START ? new ClientTickEvent.Pre() : new ClientTickEvent.Post()));

        forge.addListener(EventPriority.NORMAL, false, RenderGuiOverlayEvent.Pre.class, event -> {
            if (forge.post(new RenderGuiLayerEvent.Pre(event.getGuiGraphics(), event.getPartialTick(), event.getOverlay().id()))) {
                event.setCanceled(true);
            }
        });
        forge.addListener(EventPriority.NORMAL, false, RenderGuiOverlayEvent.Post.class, event -> forge.post(
                new RenderGuiLayerEvent.Post(event.getGuiGraphics(), event.getPartialTick(), event.getOverlay().id())));

        modBus.addListener(EventPriority.NORMAL, false, TextureStitchEvent.Post.class,
                event -> modBus.post(new TextureAtlasStitchedEvent(event.getAtlas())));

        // NeoForge fires both registration events on the mod bus during client loading; Forge has no equivalent
        // event, so they are posted on the main thread during client setup, before anything is rendered or opened.
        modBus.addListener(EventPriority.HIGHEST, false, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            modBus.post(new RegisterClientExtensionsEvent());
            modBus.post(new RegisterMenuScreensEvent());
        }));

        PortRenderFrameBridge.install(forge);
    }
}
