package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.farm.StardewFarmLifecycle;
import com.stardew.craft.api.v1.farm.StardewFarmLifecycleListener;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

/** Core dispatch bridge. Not part of the public compatibility surface. */
public final class StardewFarmLifecycleRegistry {
    private static final OrderedExtensionRegistry<
            StardewFarmLifecycleListener> LISTENERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "farm/lifecycle"));

    private StardewFarmLifecycleRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFarmLifecycleListener listener
    ) {
        LISTENERS.register(id, priority, listener);
    }

    public static void beforeCreate(StardewFarmLifecycle.CreateRequest request) {
        dispatch("beforeCreate", request, StardewFarmLifecycleListener::beforeCreate);
    }

    public static void afterCreate(StardewFarmLifecycle.FarmContext context) {
        dispatch("afterCreate", context, StardewFarmLifecycleListener::afterCreate);
    }

    public static void beforeTransfer(StardewFarmLifecycle.TransferRequest request) {
        dispatch("beforeTransfer", request, StardewFarmLifecycleListener::beforeTransfer);
    }

    public static void afterTransfer(StardewFarmLifecycle.TransferResult result) {
        dispatch("afterTransfer", result, StardewFarmLifecycleListener::afterTransfer);
    }

    public static void beforeDelete(StardewFarmLifecycle.FarmContext context) {
        dispatch("beforeDelete", context, StardewFarmLifecycleListener::beforeDelete);
    }

    public static void afterDelete(StardewFarmLifecycle.FarmContext context) {
        dispatch("afterDelete", context, StardewFarmLifecycleListener::afterDelete);
    }

    private static <T> void dispatch(
            String phase,
            T context,
            BiConsumer<StardewFarmLifecycleListener, T> callback
    ) {
        for (var registered : LISTENERS.entries()) {
            try {
                LISTENERS.invokeVoid(
                        registered,
                        listener -> callback.accept(listener, context));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew farm lifecycle listener {} failed during {}",
                        registered.id(),
                        phase,
                        exception
                );
            }
        }
    }
}
