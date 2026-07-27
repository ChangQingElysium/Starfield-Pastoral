package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmCaveDailyHandlers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Core cave dispatch bridge. Not part of the public compatibility surface. */
public final class StardewFarmCaveDailyRegistry {
    private static final Map<ResourceLocation, RegisteredHandler> HANDLERS = new HashMap<>();
    private static final Map<ResourceLocation, RegisteredFruitProvider> FRUIT_PROVIDERS =
            new HashMap<>();
    private static volatile List<RegisteredHandler> handlerSnapshot = List.of();
    private static volatile List<RegisteredFruitProvider> fruitSnapshot = List.of();

    private StardewFarmCaveDailyRegistry() {
    }

    public static synchronized void registerHandler(
            ResourceLocation id,
            int priority,
            StardewFarmCaveDailyHandlers.Handler handler
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.containsKey(id)) {
            throw new IllegalStateException("Stardew farm cave handler already registered: " + id);
        }
        HANDLERS.put(id, new RegisteredHandler(id, priority, handler));
        handlerSnapshot = ordered(HANDLERS.values());
    }

    public static synchronized void registerFruitProvider(
            ResourceLocation id,
            int priority,
            Supplier<List<Block>> managedFruitBlocks,
            StardewFarmCaveDailyHandlers.FruitProvider provider
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(managedFruitBlocks, "managedFruitBlocks");
        Objects.requireNonNull(provider, "provider");
        if (FRUIT_PROVIDERS.containsKey(id)) {
            throw new IllegalStateException(
                    "Stardew farm cave fruit provider already registered: " + id);
        }
        FRUIT_PROVIDERS.put(id, new RegisteredFruitProvider(
                id, priority, managedFruitBlocks, provider));
        fruitSnapshot = ordered(FRUIT_PROVIDERS.values());
    }

    public static StardewFarmCaveDailyHandlers.Result runHandlers(
            StardewFarmCaveDailyHandlers.Context context
    ) {
        for (RegisteredHandler registered : handlerSnapshot) {
            try {
                if (registered.handler().handle(context)
                        == StardewFarmCaveDailyHandlers.Result.SKIP_DEFAULT) {
                    return StardewFarmCaveDailyHandlers.Result.SKIP_DEFAULT;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew farm cave handler {} failed for farm {}",
                        registered.id(),
                        context.farm().ownerUuid(),
                        exception
                );
            }
        }
        return StardewFarmCaveDailyHandlers.Result.PASS;
    }

    public static Block resolveFruit(StardewFarmCaveDailyHandlers.FruitContext initialContext) {
        Block resolved = initialContext.currentFruit();
        for (RegisteredFruitProvider registered : fruitSnapshot) {
            try {
                Block candidate = registered.provider().resolve(
                        new StardewFarmCaveDailyHandlers.FruitContext(
                                initialContext.level(),
                                initialContext.farm(),
                                initialContext.caveOrigin(),
                                initialContext.random(),
                                resolved
                        ));
                if (candidate != null) {
                    resolved = candidate;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew farm cave fruit provider {} failed for farm {}",
                        registered.id(),
                        initialContext.farm().ownerUuid(),
                        exception
                );
            }
        }
        return resolved;
    }

    public static Set<Block> managedFruitBlocks() {
        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        for (RegisteredFruitProvider registered : fruitSnapshot) {
            try {
                List<Block> provided = registered.managedFruitBlocks().get();
                if (provided != null) {
                    provided.stream().filter(Objects::nonNull).forEach(blocks::add);
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew farm cave fruit provider {} failed to list managed blocks",
                        registered.id(),
                        exception
                );
            }
        }
        return Set.copyOf(blocks);
    }

    private static <T extends Prioritized> List<T> ordered(Iterable<T> values) {
        ArrayList<T> ordered = new ArrayList<>();
        values.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(Prioritized::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        return List.copyOf(ordered);
    }

    private interface Prioritized {
        ResourceLocation id();
        int priority();
    }

    private record RegisteredHandler(
            ResourceLocation id,
            int priority,
            StardewFarmCaveDailyHandlers.Handler handler
    ) implements Prioritized {
    }

    private record RegisteredFruitProvider(
            ResourceLocation id,
            int priority,
            Supplier<List<Block>> managedFruitBlocks,
            StardewFarmCaveDailyHandlers.FruitProvider provider
    ) implements Prioritized {
    }
}
