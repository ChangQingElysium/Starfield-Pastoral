package com.stardew.craft.api.v1.communitycenter;

import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterRewardRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Ordered reward transformations shared by manual claims and automatic area completion. */
public final class StardewCommunityCenterRewards {
    private StardewCommunityCenterRewards() {
    }

    public static void register(ResourceLocation id, int priority, Resolver resolver) {
        StardewCommunityCenterRewardRegistry.register(id, priority, resolver);
    }

    @FunctionalInterface
    public interface Resolver {
        /** Returns a replacement stack, or {@code null} to leave the current result unchanged. */
        ItemStack resolve(Context context);
    }

    public record Context(
            ServerPlayer player,
            int bundleId,
            String rewardDescriptor,
            ItemStack proposedReward
    ) {
        public Context {
            player = Objects.requireNonNull(player, "player");
            rewardDescriptor = Objects.requireNonNull(rewardDescriptor, "rewardDescriptor");
            proposedReward = Objects.requireNonNull(proposedReward, "proposedReward").copy();
        }

        @Override
        public ItemStack proposedReward() {
            return proposedReward.copy();
        }
    }
}
