package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardClaimResult;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardDescriptor;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardContext;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardDecision;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardHandler;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardPreparation;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressCauses;
import com.stardew.craft.api.v1.progress.StardewProgressEvent;
import com.stardew.craft.api.v1.progress.StardewProgressEventType;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressScope;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionPhase;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered, failure-isolated reward resolution with session-level idempotency. */
public final class StardewFestivalRewardRegistry {
    private static final OrderedExtensionRegistry<
            StardewFestivalRewardHandler> HANDLERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "festival/rewards"));
    private static final OrderedExtensionRegistry<
            StardewFestivalRewardDescriptor> DESCRIPTORS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "festival/reward_descriptors"));

    private StardewFestivalRewardRegistry() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewFestivalRewardHandler handler
    ) {
        HANDLERS.register(
                registrationId,
                priority,
                Objects.requireNonNull(handler, "handler"));
    }

    public static void registerDescriptor(
            ResourceLocation registrationId,
            int priority,
            StardewFestivalRewardDescriptor descriptor
    ) {
        DESCRIPTORS.register(
                Objects.requireNonNull(registrationId, "registrationId"),
                priority,
                Objects.requireNonNull(descriptor, "descriptor"));
    }

    public static List<StardewFestivalRewardDescriptor> catalog(
            ResourceLocation festivalId
    ) {
        if (festivalId == null) {
            return List.of();
        }
        Map<ResourceLocation, StardewFestivalRewardDescriptor> result =
                new LinkedHashMap<>();
        for (var entry : DESCRIPTORS.entries()) {
            StardewFestivalRewardDescriptor descriptor =
                    entry.extension();
            if (descriptor.festivalId().equals(festivalId)) {
                result.putIfAbsent(
                        descriptor.rewardId(), descriptor);
            }
        }
        return List.copyOf(result.values());
    }

    public static StardewFestivalRewardDescriptor descriptor(
            StardewProgressKey key
    ) {
        for (var entry : DESCRIPTORS.entries()) {
            StardewFestivalRewardDescriptor descriptor =
                    entry.extension();
            if (com.stardew.craft.api.v1.festival
                    .StardewFestivalRewards.progressKey(
                            descriptor.festivalId(),
                            descriptor.rewardId()).equals(key)) {
                return descriptor;
            }
        }
        return null;
    }

    public static List<ResourceLocation> entries(
            ResourceLocation domain
    ) {
        Map<ResourceLocation, Boolean> ids = new LinkedHashMap<>();
        for (var entry : DESCRIPTORS.entries()) {
            StardewFestivalRewardDescriptor descriptor =
                    entry.extension();
            if (com.stardew.craft.api.v1.festival
                    .StardewFestivalRewards.progressDomain(
                            descriptor.festivalId()).equals(domain)) {
                ids.putIfAbsent(descriptor.rewardId(), Boolean.TRUE);
            }
        }
        return List.copyOf(ids.keySet());
    }

    public static StardewProgressSnapshot progressSnapshot(
            ServerPlayer player,
            StardewFestivalRewardDescriptor descriptor
    ) {
        FestivalDefinition definition = FestivalRegistry.get(
                descriptor.festivalId()).orElse(null);
        FestivalSessionState session = definition == null
                ? null : FestivalWorldData.get(player.serverLevel())
                        .getSession(definition.id()).orElse(null);
        boolean claimed = session != null
                && session.hasRewardClaim(
                        descriptor.rewardId(), player.getUUID());
        StardewProgressPhase phase;
        if (definition == null) {
            phase = StardewProgressPhase.UNAVAILABLE;
        } else if (claimed) {
            phase = StardewProgressPhase.COMPLETED;
        } else if (session == null) {
            phase = StardewProgressPhase.NOT_STARTED;
        } else if (session.phase() == FestivalSessionPhase.CLOSED) {
            phase = StardewProgressPhase.EXPIRED;
        } else if (isOpen(session)
                && session.participants().contains(player.getUUID())) {
            phase = StardewProgressPhase.AVAILABLE;
        } else {
            phase = StardewProgressPhase.UNAVAILABLE;
        }
        return new StardewProgressSnapshot(
                com.stardew.craft.api.v1.festival
                        .StardewFestivalRewards.progressKey(
                                descriptor.festivalId(),
                                descriptor.rewardId()),
                StardewProgressScope.PLAYER,
                phase,
                List.of(),
                definition != null,
                false,
                OptionalInt.empty());
    }

    public static StardewFestivalRewardClaimResult claim(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        if (player == null || festivalId == null || rewardId == null) {
            return result(
                    StardewFestivalRewardClaimResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        ClaimPreflight preflight = preflight(
                player, festivalId, rewardId);
        FestivalDefinition definition = preflight.definition();
        if (definition == null) {
            return result(
                    StardewFestivalRewardClaimResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalWorldData data = preflight.data();
        FestivalSessionState session = preflight.session();
        if (!isOpen(session)) {
            return result(
                    StardewFestivalRewardClaimResult.Status
                            .SESSION_NOT_OPEN,
                    null);
        }
        if (!session.participants().contains(player.getUUID())) {
            return result(
                    StardewFestivalRewardClaimResult.Status
                            .NOT_PARTICIPATING,
                    null);
        }
        if (session.hasRewardClaim(rewardId, player.getUUID())) {
            return result(
                    StardewFestivalRewardClaimResult.Status
                            .ALREADY_CLAIMED,
                    null);
        }
        StardewFestivalRewardContext context =
                new StardewFestivalRewardContext(
                        player,
                        definition.resourceId(),
                        StardewFestivalMechanicRegistry.mechanicId(
                                definition),
                        rewardId,
                        StardewFestivalSessions.snapshot(session));
        StardewFestivalRewardDescriptor descriptor = descriptor(
                com.stardew.craft.api.v1.festival
                        .StardewFestivalRewards.progressKey(
                                festivalId, rewardId));
        StardewProgressSnapshot before = descriptor == null
                ? null : progressSnapshot(player, descriptor);
        for (var registered : HANDLERS.entries()) {
            StardewFestivalRewardPreparation preparation;
            try {
                preparation = HANDLERS.invoke(
                        registered,
                        handler -> handler.prepare(context));
            } catch (RuntimeException exception) {
                logFailure(
                        registered.id(), context, "prepare", exception);
                continue;
            }
            if (preparation == null) {
                StardewCraft.LOGGER.error(
                        "Festival reward handler {} returned null for {}",
                        registered.id(), rewardId);
                continue;
            }
            if (preparation.decision()
                    == StardewFestivalRewardDecision.PASS) {
                continue;
            }
            if (preparation.decision()
                    == StardewFestivalRewardDecision.REJECT) {
                return result(
                        StardewFestivalRewardClaimResult.Status.REJECTED,
                        registered.id());
            }
            boolean granted;
            try {
                granted = preparation.grant().orElseThrow()
                        .grant(context);
            } catch (RuntimeException exception) {
                logFailure(
                        registered.id(), context, "grant", exception);
                granted = false;
            }
            if (!granted) {
                return result(
                        StardewFestivalRewardClaimResult.Status
                                .GRANT_FAILED,
                        registered.id());
            }
            session.addRewardClaim(rewardId, player.getUUID());
            data.setDirty();
            if (before != null) {
                StardewProgressSnapshot after =
                        progressSnapshot(player, descriptor);
                com.stardew.craft.api.v1.internal.progress
                        .StardewProgressRegistry.dispatch(
                                new StardewProgressEvent(
                                        StardewProgressEventType
                                                .REWARD_CLAIMED,
                                        player.serverLevel(),
                                        Optional.of(player.getUUID()),
                                        Optional.of(before),
                                        after,
                                        StardewProgressCauses
                                                .FESTIVAL_REWARD));
            }
            return result(
                    StardewFestivalRewardClaimResult.Status.CLAIMED,
                    registered.id());
        }
        return result(
                StardewFestivalRewardClaimResult.Status.REWARD_NOT_FOUND,
                null);
    }

    public static StardewRequirementReport requirements(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        return preflight(player, festivalId, rewardId).report();
    }

    public static boolean hasClaimed(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        if (player == null || festivalId == null || rewardId == null) {
            return false;
        }
        return FestivalRegistry.get(festivalId)
                .flatMap(definition -> FestivalWorldData
                        .get(player.serverLevel())
                        .getSession(definition.id()))
                .map(session -> session.hasRewardClaim(
                        rewardId, player.getUUID()))
                .orElse(false);
    }

    private static ClaimPreflight preflight(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation rewardId
    ) {
        FestivalDefinition definition = FestivalRegistry.get(festivalId)
                .orElse(null);
        FestivalWorldData data = FestivalWorldData.get(
                player.serverLevel());
        FestivalSessionState session = definition == null
                ? null : data.getSession(definition.id()).orElse(null);
        boolean exists = definition != null;
        boolean open = isOpen(session);
        boolean participating = session != null
                && session.participants().contains(player.getUUID());
        boolean unclaimed = session == null
                || !session.hasRewardClaim(
                        rewardId, player.getUUID());
        return new ClaimPreflight(
                definition,
                data,
                session,
                new StardewRequirementReport(List.of(
                        requirement(
                                StardewRequirementTypes.FESTIVAL_EXISTS,
                                exists,
                                Component.translatable(
                                        "stardewcraft.requirement.festival.exists",
                                        festivalId.toString())),
                        requirement(
                                StardewRequirementTypes
                                        .FESTIVAL_SESSION_OPEN,
                                open,
                                Component.translatable(
                                        "stardewcraft.requirement.festival.session_open")),
                        requirement(
                                StardewRequirementTypes
                                        .FESTIVAL_PARTICIPATING,
                                participating,
                                Component.translatable(
                                        "stardewcraft.requirement.festival.participating")),
                        requirement(
                                StardewRequirementTypes
                                        .FESTIVAL_REWARD_UNCLAIMED,
                                unclaimed,
                                Component.translatable(
                                        "stardewcraft.requirement.festival.reward_unclaimed",
                                        rewardId.toString())))));
    }

    private static StardewRequirement requirement(
            ResourceLocation type,
            boolean satisfied,
            Component description
    ) {
        return new StardewRequirement(
                type,
                satisfied
                        ? StardewRequirement.State.SATISFIED
                        : StardewRequirement.State.UNSATISFIED,
                description,
                true);
    }

    private record ClaimPreflight(
            FestivalDefinition definition,
            FestivalWorldData data,
            FestivalSessionState session,
            StardewRequirementReport report
    ) {
    }

    private static boolean isOpen(FestivalSessionState session) {
        return session != null
                && (session.phase() == FestivalSessionPhase.OPEN
                || session.phase() == FestivalSessionPhase.MAIN_EVENT);
    }

    private static StardewFestivalRewardClaimResult result(
            StardewFestivalRewardClaimResult.Status status,
            ResourceLocation handlerId
    ) {
        return new StardewFestivalRewardClaimResult(
                status, Optional.ofNullable(handlerId));
    }

    private static void logFailure(
            ResourceLocation registrationId,
            StardewFestivalRewardContext context,
            String operation,
            RuntimeException exception
    ) {
        StardewCraft.LOGGER.error(
                "Festival reward handler {} failed during {} for {} / {}",
                registrationId,
                operation,
                context.festivalId(),
                context.rewardId(),
                exception);
    }
}
