package com.stardew.craft.fabric;

import com.stardew.craft.StardewCraftClient;
import com.stardew.craft.client.fishpond.ClientFishPondWaterColorCache;
import com.stardew.craft.fluid.ModFluids;
import com.stardew.craft.platform.fabric.client.event.EntityRenderersEvent;
import com.stardew.craft.platform.fabric.client.event.FabricClientPlayerEvents;
import com.stardew.craft.platform.fabric.client.event.FabricClientRegistrationEvents;
import com.stardew.craft.platform.fabric.client.event.RegisterClientReloadListenersEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterColorHandlersEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterKeyMappingsEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterMenuScreensEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterParticleProvidersEvent;
import com.stardew.craft.platform.fabric.client.render.FabricGuiRenderEvents;
import com.stardew.craft.platform.fabric.client.render.FabricLevelRenderEvents;
import com.stardew.craft.platform.fabric.datamap.client.FabricDataMapClient;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.client.FabricClientEvents;
import com.stardew.craft.platform.fabric.fluid.client.FishPondFluidRendering;
import com.stardew.craft.platform.fabric.lifecycle.FMLClientSetupEvent;
import com.stardew.craft.platform.fabric.network.FabricNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Runs in Loader's Minecraft constructor hook, before Options and the first resource reload. */
@Environment(EnvType.CLIENT)
public final class StardewCraftFabricClient implements ClientModInitializer {
    private static boolean attempted;

    @Override public void onInitializeClient() {
        StardewCraftFabric.requireInitialized();
        com.stardew.craft.platform.fabric.client.widget.SlotHighlights.install(com.stardew.craft.client.gui.common.StardewInventoryGui::renderSlotHighlight);
        com.stardew.craft.platform.fabric.client.event.registration.FabricRenderRegistrations.install(FabricEvents.MOD);
        com.stardew.craft.platform.fabric.client.event.registration.FabricSystemChatEvents.install(FabricEvents.GAME);
        com.stardew.craft.platform.fabric.client.decoration.FabricItemDecorations.install(FabricEvents.MOD);
        com.stardew.craft.platform.fabric.client.event.screen.FabricScreenEvents.install(FabricEvents.GAME);
        com.stardew.craft.platform.fabric.client.event.entityrender.FabricEntityRenderEvents.install(FabricEvents.GAME);
        if (attempted) throw new IllegalStateException("StardewCraft client initialization already attempted");
        attempted = true;
        com.stardew.craft.platform.fabric.client.model.FabricModelEvents.install(FabricEvents.MOD);
        com.stardew.craft.platform.fabric.client.extension.FabricClientExtensions.install(FabricEvents.MOD);
        // Physical-client subscribers were indexed before the common registry pass, without resolving server-side clients.
        new StardewCraftClient(FabricEvents.MOD);
        FabricClientRegistrationEvents.install(FabricEvents.MOD);
        FabricEvents.MOD.post(new RegisterKeyMappingsEvent());
        FabricEvents.MOD.post(new RegisterMenuScreensEvent());
        FabricEvents.MOD.post(new EntityRenderersEvent.RegisterLayerDefinitions());
        FabricEvents.MOD.post(new EntityRenderersEvent.RegisterRenderers());
        FabricEvents.MOD.post(new RegisterParticleProvidersEvent());
        FabricEvents.MOD.post(new RegisterColorHandlersEvent.Block());
        FabricEvents.MOD.post(new RegisterColorHandlersEvent.Item());
        FabricEvents.MOD.post(new RegisterClientReloadListenersEvent());
        com.stardew.craft.platform.fabric.client.event.tooltip.FabricTooltipEvents.install(FabricEvents.GAME);
        com.stardew.craft.platform.fabric.client.event.tooltip.FabricTooltipEvents.registerFactories(FabricEvents.MOD);
        com.stardew.craft.platform.fabric.client.event.tooltip.FabricPresentationEvents.install(FabricEvents.GAME);
        com.stardew.craft.platform.fabric.client.event.viewport.FabricViewportEvents.install(FabricEvents.GAME);
        new FMLClientSetupEvent().dispatchAndDrain(FabricEvents.MOD);

        FishPondFluidRendering.register(ModFluids.FISH_POND_WATER.get(), ModFluids.FLOWING_FISH_POND_WATER.get(),
                ClientFishPondWaterColorCache::get);
        FabricNetwork.initializeClient();
        FabricDataMapClient.install();
        FabricClientEvents.install();
        FabricClientPlayerEvents.install(FabricEvents.GAME);
        FabricLevelRenderEvents.install(FabricEvents.GAME);
        FabricGuiRenderEvents.install(FabricEvents.GAME);
        com.stardew.craft.platform.fabric.client.render.FabricGuiLayers.install(FabricEvents.GAME);
        com.stardew.craft.platform.fabric.client.event.input.FabricInputEvents.install(FabricEvents.GAME);
    }
}
