package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Secret-note data-pack lifecycle. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNoteSystem {
    private SecretNoteSystem() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SecretNoteRegistry.ReloadListener());
    }
}
