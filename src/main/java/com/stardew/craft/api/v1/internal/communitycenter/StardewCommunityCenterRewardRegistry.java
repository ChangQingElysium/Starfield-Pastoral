package com.stardew.craft.api.v1.internal.communitycenter;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterRewards;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Internal reward resolver dispatch. */
public final class StardewCommunityCenterRewardRegistry {
    private static final OrderedExtensionRegistry<
            StardewCommunityCenterRewards.Resolver> RESOLVERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "community_center/reward"));

    private StardewCommunityCenterRewardRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewCommunityCenterRewards.Resolver resolver
    ) {
        RESOLVERS.register(id, priority, resolver);
    }

    public static ItemStack resolve(
            ServerPlayer player,
            int bundleId,
            String descriptor,
            ItemStack initial
    ) {
        ItemStack current = initial.copy();
        for (var registered : RESOLVERS.entries()) {
            try {
                ItemStack proposedReward = current;
                ItemStack candidate = RESOLVERS.invoke(
                        registered,
                        resolver -> resolver.resolve(
                                new StardewCommunityCenterRewards.Context(
                                        player,
                                        bundleId,
                                        descriptor,
                                        proposedReward)));
                if (candidate != null) {
                    current = candidate.copy();
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Community Center reward resolver {} failed for bundle {}",
                        registered.id(), bundleId, exception);
            }
        }
        return current;
    }
}
