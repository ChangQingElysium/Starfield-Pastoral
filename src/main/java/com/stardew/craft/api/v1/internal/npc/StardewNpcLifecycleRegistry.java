package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcLifecycleEvent;
import com.stardew.craft.api.v1.npc.StardewNpcLifecycleListener;
import net.minecraft.resources.ResourceLocation;

/** Internal ordered, failure-isolated NPC lifecycle dispatch. */
public final class StardewNpcLifecycleRegistry {
    private static final OrderedExtensionRegistry<StardewNpcLifecycleListener>
            LISTENERS = new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "npc/lifecycle"));

    private StardewNpcLifecycleRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewNpcLifecycleListener listener
    ) {
        LISTENERS.register(id, priority, listener);
    }

    public static void dispatch(StardewNpcLifecycleEvent event) {
        for (var registered : LISTENERS.entries()) {
            try {
                LISTENERS.invokeVoid(
                        registered,
                        listener -> listener.onLifecycle(event));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC lifecycle listener {} failed for {} {} ({})",
                        registered.id(),
                        event.npcId(),
                        event.phase(),
                        event.reason(),
                        exception);
            }
        }
    }
}
