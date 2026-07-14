package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PlayerContentDataSystem {
    private PlayerContentDataSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StardewCraftingRecipeData.ReloadListener());
        event.addListener(new UnlockSourceData.ReloadListener());
    }
}
