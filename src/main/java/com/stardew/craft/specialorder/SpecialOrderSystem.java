package com.stardew.craft.specialorder;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SpecialOrderSystem {
    private SpecialOrderSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpecialOrderDataLoader.ReloadListener());
    }
}
