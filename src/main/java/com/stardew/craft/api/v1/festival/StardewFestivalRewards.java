package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.internal.festival.StardewFestivalRewardRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;

/**
 * Display catalog registration and authoritative claim facade for festival
 * rewards.
 *
 * <p>Descriptors are discoverable metadata only. Callers must use
 * {@link #claim(ServerPlayer, ResourceLocation, ResourceLocation)} for the
 * authoritative participation, session, handler, and idempotency checks.
 */
public final class StardewFestivalRewards {
    private StardewFestivalRewards() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewFestivalRewardHandler handler
    ) {
        StardewFestivalRewardRegistry.register(
                registrationId, priority, handler);
    }

    public static void registerDescriptor(
            ResourceLocation registrationId,
            int priority,
            StardewFestivalRewardDescriptor descriptor
    ) {
        StardewFestivalRewardRegistry.registerDescriptor(
                registrationId, priority, descriptor);
    }

    public static List<StardewFestivalRewardDescriptor> catalog(
            ResourceLocation festivalId
    ) {
        return StardewFestivalRewardRegistry.catalog(festivalId);
    }

    public static ResourceLocation progressDomain(
            ResourceLocation festivalId
    ) {
        if (festivalId == null) {
            throw new NullPointerException("festivalId");
        }
        return ResourceLocation.fromNamespaceAndPath(
                festivalId.getNamespace(),
                "festival_reward/" + festivalId.getPath());
    }

    public static StardewProgressKey progressKey(
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        if (rewardId == null) {
            throw new NullPointerException("rewardId");
        }
        return new StardewProgressKey(
                progressDomain(festivalId), rewardId);
    }

    public static StardewFestivalRewardClaimResult claim(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        return StardewFestivalRewardRegistry.claim(
                player, festivalId, rewardId);
    }

    /**
     * Explains the common preconditions known before invoking a reward
     * handler. A satisfied report does not replace {@link #claim}; handlers
     * may still apply reward-specific dynamic rules.
     */
    public static StardewRequirementReport requirements(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        return StardewFestivalRewardRegistry.requirements(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(festivalId, "festivalId"),
                Objects.requireNonNull(rewardId, "rewardId"));
    }

    public static boolean hasClaimed(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        return StardewFestivalRewardRegistry.hasClaimed(
                player, festivalId, rewardId);
    }
}
