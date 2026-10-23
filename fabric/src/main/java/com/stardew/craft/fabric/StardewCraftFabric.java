package com.stardew.craft.fabric;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.platform.fabric.bootstrap.SubscriberIndex;
import com.stardew.craft.platform.fabric.capability.RegisterCapabilitiesEvent;
import com.stardew.craft.platform.fabric.datamap.DataMapSync;
import com.stardew.craft.platform.fabric.datamap.RegisterDataMapTypesEvent;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.FabricPlayerEvents;
import com.stardew.craft.platform.fabric.event.FabricServerEvents;
import com.stardew.craft.platform.fabric.event.FabricWorldEvents;
import com.stardew.craft.platform.fabric.lifecycle.EntityAttributeCreationEvent;
import com.stardew.craft.platform.fabric.lifecycle.FMLCommonSetupEvent;
import com.stardew.craft.platform.fabric.menu.FabricMenuDataSync;
import com.stardew.craft.platform.fabric.network.FabricNetwork;
import com.stardew.craft.platform.fabric.registry.RegistryBootstrap;
import com.stardew.craft.platform.fabric.server.FabricServerLifecycle;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;

/** Common loader entrypoint. Failure in any required bootstrap prevents a partially initialized mod. */
public final class StardewCraftFabric implements ModInitializer {
    private static State state = State.NEW;

    @Override public void onInitialize() {
        if (state != State.NEW) throw new IllegalStateException("StardewCraft common initialization already attempted: " + state);
        state = State.INITIALIZING;
        try {
            // The constructor retains all built-in API/domain bootstrap, registry binding and three config scopes.
            new StardewCraft(FabricEvents.MOD);
            new SubscriberIndex().register(FabricLoader.getInstance().getEnvironmentType(), FabricEvents.GAME, FabricEvents.MOD);

            com.stardew.craft.platform.fabric.chunk.TicketControllers.install(FabricEvents.MOD);

            // Factories: block sounds/effects/particles/types are independent of content blocks/items.
            // Native pond fluid holds a lazy block supplier, while FishPondWaterBlock requires the source fluid.
            // Block items, BE valid-block sets and creative icons require their preceding registrations.
            RegistryBootstrap.bootstrap(FabricEvents.MOD, Registries.SOUND_EVENT, Registries.MOB_EFFECT,
                    Registries.PARTICLE_TYPE, Registries.ENTITY_TYPE, Registries.FLUID, Registries.BLOCK,
                    Registries.ITEM, Registries.BLOCK_ENTITY_TYPE, Registries.MENU, Registries.CREATIVE_MODE_TAB);
            FabricEvents.MOD.post(new EntityAttributeCreationEvent());
            FabricEvents.MOD.post(new RegisterCapabilitiesEvent());
            FabricEvents.MOD.post(new RegisterDataMapTypesEvent());

            FabricNetwork.initialize(event -> {
                FabricEvents.MOD.post(event);
                FabricMenuDataSync.registerPayloads(event);
                DataMapSync.registerPayloads(event);
                com.stardew.craft.platform.fabric.light.AuxiliaryLights.registerPayloads(event);
            }, event -> {
                FabricEvents.MOD.post(event);
                DataMapSync.registerConfigurationTask(event);
            });
            DataMapSync.installServerLifecycle();

            // Install the server reference before the pre-createLevels region installer callback.
            FabricServerLifecycle.install();
            FabricServerEvents.install();
            com.stardew.craft.platform.fabric.event.enchant.FabricEnchantingEvents.install(FabricEvents.GAME);
            com.stardew.craft.platform.fabric.event.sleep.FabricSleepEvents.install(FabricEvents.GAME);
            FabricPlayerEvents.install();
            FabricWorldEvents.install();
            com.stardew.craft.platform.fabric.event.entity.living.spawn.MobSpawnHooks.initialize();
            com.stardew.craft.platform.fabric.event.entity.living.FabricLivingDamageEvents.install(FabricEvents.GAME);
            // GAME's indexed AddReloadListenerEvent subscribers are published by the native per-resource mixin.
            new FMLCommonSetupEvent().dispatchAndDrain(FabricEvents.MOD);
            state = State.COMPLETE;
        } catch (RuntimeException | Error failure) {
            state = State.FAILED;
            throw failure;
        }
    }

    static void requireInitialized() {
        if (state != State.COMPLETE) throw new IllegalStateException("Common initialization must complete before client initialization: " + state);
    }

    private enum State { NEW, INITIALIZING, COMPLETE, FAILED }
}
