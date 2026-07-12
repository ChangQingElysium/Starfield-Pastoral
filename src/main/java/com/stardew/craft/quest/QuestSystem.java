package com.stardew.craft.quest;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class QuestSystem {

    private QuestSystem() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestReloadListener());
    }

    private static final class QuestReloadListener extends SimplePreparableReloadListener<Void> {

        @Override
        protected Void prepare(ResourceManager resourceManager,
                               ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void nothing,
                             ResourceManager resourceManager,
                             ProfilerFiller profiler) {

            QuestDataLoader.load(resourceManager);
        }
    }
}