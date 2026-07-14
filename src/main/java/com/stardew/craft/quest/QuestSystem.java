package com.stardew.craft.quest;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import com.stardew.craft.quest.data.DailyQuestPoolRegistry;

/** Server hooks for the namespaced quest definition registry. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class QuestSystem {
    private QuestSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestDataLoader.ReloadListener());
        event.addListener(new DailyQuestPoolRegistry.ReloadListener());
    }
}
