package com.stardew.craft.cooking.service;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CookingDataSystem {
    private CookingDataSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VanillaCookingRecipeData.ReloadListener());
    }
}
