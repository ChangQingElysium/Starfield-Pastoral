package com.stardew.craft.animal;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.model.AnimalDefinitionReloadListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

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
