package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityContext;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityDecision;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityHandler;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityRegistration;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityResult;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionPhase;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalWorldData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered, failure-isolated server dispatch for festival activities. */
public final class StardewFestivalActivityRegistry {
    private static final OrderedExtensionRegistry<Entry> ACTIVITIES =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "festival/activities"));

    private StardewFestivalActivityRegistry() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            ResourceLocation mechanicId,
            ResourceLocation activityId,
            StardewFestivalActivityHandler handler
    ) {
        ACTIVITIES.register(
                registrationId,
                priority,
                new Entry(
                        Objects.requireNonNull(mechanicId, "mechanicId"),
                        Objects.requireNonNull(activityId, "activityId"),
                        Objects.requireNonNull(handler, "handler")));
    }

    public static List<StardewFestivalActivityRegistration> registrations(
            ResourceLocation mechanicId
    ) {
        if (mechanicId == null) {
            return List.of();
        }
        return ACTIVITIES.entries().stream()
                .filter(entry -> entry.extension().mechanicId()
                        .equals(mechanicId))
                .map(entry -> new StardewFestivalActivityRegistration(
                        entry.id(),
                        entry.priority(),
                        entry.extension().mechanicId(),
                        entry.extension().activityId()))
                .toList();
    }

    public static StardewFestivalActivityResult start(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation activityId
    ) {
        if (player == null || festivalId == null || activityId == null) {
            return result(
                    StardewFestivalActivityResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalDefinition definition = FestivalRegistry.get(festivalId)
                .orElse(null);
        if (definition == null) {
            return result(
                    StardewFestivalActivityResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalSessionState session = FestivalWorldData
                .get(player.serverLevel())
                .getSession(definition.id())
                .orElse(null);
        if (!isOpen(session)) {
            return result(
                    StardewFestivalActivityResult.Status
                            .SESSION_NOT_OPEN,
                    null);
        }
        if (!session.participants().contains(player.getUUID())) {
            return result(
                    StardewFestivalActivityResult.Status
                            .NOT_PARTICIPATING,
                    null);
        }
        if (!StardewFestivalAccess.isAtFestivalLocation(
                player, definition)) {
            return result(
                    StardewFestivalActivityResult.Status.WRONG_LOCATION,
                    null);
        }
        ResourceLocation mechanicId =
                StardewFestivalMechanicRegistry.mechanicId(definition);
        StardewFestivalActivityContext context =
                new StardewFestivalActivityContext(
                        player,
                        definition.resourceId(),
                        mechanicId,
                        activityId,
                        StardewFestivalSessions.snapshot(session));
        for (var registered : ACTIVITIES.entries()) {
            Entry activity = registered.extension();
            if (!activity.mechanicId().equals(mechanicId)
                    || !activity.activityId().equals(activityId)) {
                continue;
            }
            try {
                StardewFestivalActivityDecision decision =
                        ACTIVITIES.invoke(
                                registered,
                                entry -> entry.handler().start(context));
                if (decision == null
                        || decision
                        == StardewFestivalActivityDecision.PASS) {
                    continue;
                }
                return result(
                        decision
                                == StardewFestivalActivityDecision.STARTED
                                ? StardewFestivalActivityResult.Status.STARTED
                                : StardewFestivalActivityResult.Status.REJECTED,
                        registered.id());
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Festival activity handler {} failed for {} / {}",
                        registered.id(),
                        festivalId,
                        activityId,
                        exception);
            }
        }
        return result(
                StardewFestivalActivityResult.Status.ACTIVITY_NOT_FOUND,
                null);
    }

    private static boolean isOpen(FestivalSessionState session) {
        return session != null
                && (session.phase() == FestivalSessionPhase.OPEN
                || session.phase() == FestivalSessionPhase.MAIN_EVENT);
    }

    private static StardewFestivalActivityResult result(
            StardewFestivalActivityResult.Status status,
            ResourceLocation handlerId
    ) {
        return new StardewFestivalActivityResult(
                status, Optional.ofNullable(handlerId));
    }

    private record Entry(
            ResourceLocation mechanicId,
            ResourceLocation activityId,
            StardewFestivalActivityHandler handler
    ) {
    }
}
