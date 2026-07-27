package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.internal.festival.StardewFestivalActivityRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Registration, discovery and authoritative start facade for activities. */
public final class StardewFestivalActivities {
    private StardewFestivalActivities() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            ResourceLocation mechanicId,
            ResourceLocation activityId,
            StardewFestivalActivityHandler handler
    ) {
        StardewFestivalActivityRegistry.register(
                registrationId,
                priority,
                mechanicId,
                activityId,
                handler);
    }

    public static List<StardewFestivalActivityRegistration> registrations(
            ResourceLocation mechanicId
    ) {
        return StardewFestivalActivityRegistry.registrations(mechanicId);
    }

    public static StardewFestivalActivityResult start(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation activityId
    ) {
        return StardewFestivalActivityRegistry.start(
                player, festivalId, activityId);
    }

    /**
     * Stable progress domain derived from one festival without flattening
     * the festival/activity pair into a lossy string ID.
     */
    public static ResourceLocation progressDomain(
            ResourceLocation festivalId
    ) {
        if (festivalId == null) {
            throw new NullPointerException("festivalId");
        }
        return ResourceLocation.fromNamespaceAndPath(
                festivalId.getNamespace(),
                "festival_activity/" + festivalId.getPath());
    }

    public static StardewProgressKey progressKey(
            ResourceLocation festivalId,
            ResourceLocation activityId
    ) {
        if (activityId == null) {
            throw new NullPointerException("activityId");
        }
        return new StardewProgressKey(
                progressDomain(festivalId), activityId);
    }
}
