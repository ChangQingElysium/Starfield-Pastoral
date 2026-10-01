package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

/** Secret-note data-pack lifecycle. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNoteSystem {
    private SecretNoteSystem() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SecretNoteRegistry.ReloadListener());
    }
}
