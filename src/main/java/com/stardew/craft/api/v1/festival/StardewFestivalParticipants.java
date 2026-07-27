package com.stardew.craft.api.v1.festival;

import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.festival.FestivalSessionPhase;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalType;
import com.stardew.craft.festival.FestivalWorldData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Common server-authoritative participant operations for all festival kinds. */
public final class StardewFestivalParticipants {
    private StardewFestivalParticipants() {
    }

    public static StardewFestivalParticipantResult join(
            ServerPlayer player,
            ResourceLocation festivalId
    ) {
        if (player == null || festivalId == null) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalDefinition definition = FestivalRegistry.get(festivalId)
                .orElse(null);
        if (definition == null) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalSessionState existing = FestivalWorldData
                .get(player.serverLevel())
                .getSession(definition.id())
                .orElse(null);
        if (existing != null
                && existing.participants().contains(player.getUUID())) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .ALREADY_PARTICIPATING,
                    existing);
        }

        FestivalSessionState session;
        if (definition.type() == FestivalType.ACTIVE) {
            session = FestivalService.openActiveFestival(
                    player, definition.id()).orElse(null);
        } else {
            session = joinPassive(player, definition);
        }
        if (session == null) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .SESSION_NOT_OPEN,
                    existing);
        }
        return result(
                StardewFestivalParticipantResult.Status.JOINED,
                session);
    }

    public static StardewFestivalParticipantResult leave(
            ServerLevel level,
            ResourceLocation festivalId,
            UUID playerId
    ) {
        if (level == null || festivalId == null || playerId == null) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalDefinition definition = FestivalRegistry.get(festivalId)
                .orElse(null);
        if (definition == null) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalSessionState session = data.getSession(definition.id())
                .orElse(null);
        if (session == null || !session.removeParticipant(playerId)) {
            return result(
                    StardewFestivalParticipantResult.Status
                            .NOT_PARTICIPATING,
                    session);
        }
        data.setDirty();
        return result(
                StardewFestivalParticipantResult.Status.LEFT,
                session);
    }

    public static boolean contains(
            ServerLevel level,
            ResourceLocation festivalId,
            UUID playerId
    ) {
        return participants(level, festivalId).contains(playerId);
    }

    public static Set<UUID> participants(
            ServerLevel level,
            ResourceLocation festivalId
    ) {
        if (level == null || festivalId == null) {
            return Set.of();
        }
        return FestivalRegistry.get(festivalId)
                .flatMap(definition -> FestivalWorldData.get(level)
                        .getSession(definition.id()))
                .map(FestivalSessionState::participants)
                .orElse(Set.of());
    }

    private static FestivalSessionState joinPassive(
            ServerPlayer player,
            FestivalDefinition definition
    ) {
        if (!FestivalService.isPassiveFestivalOpen(definition.id())) {
            return null;
        }
        FestivalWorldData data = FestivalWorldData.get(
                player.serverLevel());
        FestivalSessionState session = data.getSession(definition.id())
                .orElse(null);
        if (session == null
                || (session.phase() != FestivalSessionPhase.OPEN
                && session.phase() != FestivalSessionPhase.MAIN_EVENT)) {
            return null;
        }
        session.addParticipant(player.getUUID());
        data.setDirty();
        return session;
    }

    private static StardewFestivalParticipantResult result(
            StardewFestivalParticipantResult.Status status,
            FestivalSessionState session
    ) {
        return new StardewFestivalParticipantResult(
                status,
                Optional.ofNullable(session)
                        .map(StardewFestivalSessions::snapshot));
    }
}
