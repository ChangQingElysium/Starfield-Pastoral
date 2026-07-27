package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.farm.StardewFarmDebrisPlacements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Core debris-placement dispatch bridge. Not part of the public compatibility surface. */
public final class StardewFarmDebrisPlacementRegistry {
    private static final OrderedExtensionRegistry<
            StardewFarmDebrisPlacements.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "farm/debris_placement"));

    private StardewFarmDebrisPlacementRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFarmDebrisPlacements.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static BlockState resolve(StardewFarmDebrisPlacements.Context initialContext) {
        BlockState resolved = initialContext.proposedState();
        for (var registered : PROVIDERS.entries()) {
            try {
                BlockState proposedState = resolved;
                BlockState candidate = PROVIDERS.invoke(
                        registered,
                        provider -> provider.resolve(
                                new StardewFarmDebrisPlacements.Context(
                                        initialContext.level(),
                                        initialContext.farm(),
                                        initialContext.position(),
                                        proposedState,
                                        initialContext.stage(),
                                        initialContext.random()
                                )));
                if (candidate != null) {
                    resolved = candidate;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew farm debris provider {} failed for farm {} at {}",
                        registered.id(),
                        initialContext.farm().ownerUuid(),
                        initialContext.position(),
                        exception
                );
            }
        }
        return resolved;
    }
}
