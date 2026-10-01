package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

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
