package com.stardew.craft.quest;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;
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
