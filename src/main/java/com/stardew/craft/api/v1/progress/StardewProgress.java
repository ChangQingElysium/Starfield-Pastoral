package com.stardew.craft.api.v1.progress;

import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Unified read-only facade over built-in and add-on progress systems. */
public final class StardewProgress {
    private StardewProgress() {
    }

    public static void registerProvider(
            ResourceLocation registrationId,
            int priority,
            ResourceLocation domain,
            StardewProgressProvider provider
    ) {
        StardewProgressRegistry.registerProvider(
                registrationId, priority, domain, provider);
    }

    @Nullable
    public static StardewProgressSnapshot inspect(
            ServerPlayer player,
            StardewProgressKey key
    ) {
        return StardewProgressRegistry.inspect(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(key, "key"));
    }

    /**
     * Stable, deduplicated snapshots advertised by one built-in or add-on domain.
     * Directly inspectable private entries need not be advertised by their provider.
     */
    public static List<StardewProgressSnapshot> list(
            ServerPlayer player,
            ResourceLocation domain
    ) {
        return StardewProgressRegistry.list(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(domain, "domain"));
    }

    /** Canonical key for one legacy-compatible Community Center bundle ID. */
    public static StardewProgressKey communityCenterBundle(int bundleId) {
        return StardewProgressRegistry.communityCenterBundleKey(bundleId);
    }

    /** Canonical key for one Community Center area ID. */
    public static StardewProgressKey communityCenterArea(int areaId) {
        return StardewProgressRegistry.communityCenterAreaKey(areaId);
    }

    /** Aggregate museum collection and pending-reward progress. */
    public static StardewProgressKey museumCollection() {
        return StardewProgressRegistry.museumCollectionKey();
    }

    /** Canonical key for a datapack-defined museum reward milestone. */
    public static StardewProgressKey museumReward(String rewardId) {
        return StardewProgressRegistry.museumRewardKey(
                Objects.requireNonNull(rewardId, "rewardId"));
    }

    /** Canonical key for one festival's current or most recent persisted session. */
    public static StardewProgressKey festival(ResourceLocation festivalId) {
        return StardewProgressRegistry.festivalKey(
                Objects.requireNonNull(festivalId, "festivalId"));
    }
}
