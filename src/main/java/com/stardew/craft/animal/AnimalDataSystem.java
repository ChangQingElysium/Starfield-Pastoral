package com.stardew.craft.animal;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.model.AnimalDefinitionReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Hooks the farm-animal data registry into server data-pack reloads. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class AnimalDataSystem {
    private AnimalDataSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(
                new AnimalDefinitionReloadListener());
    }
}
