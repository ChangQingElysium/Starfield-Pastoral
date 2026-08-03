package com.stardew.craft.festival;

import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class ActiveFestivalConfirmState {
    private final EnumMap<OpenFestivalConfirmPayload.Action, Set<UUID>> dialogs = new EnumMap<>(OpenFestivalConfirmPayload.Action.class);
    private final EnumMap<OpenFestivalConfirmPayload.Action, Set<UUID>> votes = new EnumMap<>(OpenFestivalConfirmPayload.Action.class);
    private final EnumMap<OpenFestivalConfirmPayload.Action, Set<UUID>> voteParticipants = new EnumMap<>(OpenFestivalConfirmPayload.Action.class);

    public Set<UUID> dialogs(OpenFestivalConfirmPayload.Action action) {
        return dialogs.computeIfAbsent(action, ignored -> new LinkedHashSet<>());
    }

    public Set<UUID> votes(OpenFestivalConfirmPayload.Action action) {
        return votes.computeIfAbsent(action, ignored -> new LinkedHashSet<>());
    }

    public Set<UUID> voteParticipants(OpenFestivalConfirmPayload.Action action) {
        return voteParticipants.computeIfAbsent(action, ignored -> new LinkedHashSet<>());
    }

    /**
     * Casts a vote against the participant snapshot captured by the first voter.
     * Players who join the festival after voting starts do not expand an in-flight vote.
     */
    public VoteProgress castVote(OpenFestivalConfirmPayload.Action action,
                                 UUID voter,
                                 Collection<UUID> onlineParticipants) {
        Set<UUID> participantSnapshot = voteParticipants(action);
        Set<UUID> currentParticipants = onlineParticipants == null
                ? Set.of()
                : new LinkedHashSet<>(onlineParticipants);
        if (participantSnapshot.isEmpty()) {
            participantSnapshot.addAll(currentParticipants);
        }

        Set<UUID> currentVotes = votes(action);
        currentVotes.retainAll(participantSnapshot);
        if (voter != null && participantSnapshot.contains(voter)) {
            currentVotes.add(voter);
        }

        int eligible = 0;
        int accepted = 0;
        for (UUID participant : currentParticipants) {
            if (!participantSnapshot.contains(participant)) {
                continue;
            }
            eligible++;
            if (currentVotes.contains(participant)) {
                accepted++;
            }
        }
        return new VoteProgress(accepted, eligible);
    }

    public record VoteProgress(int votes, int participants) {
        public boolean complete() {
            return participants == 0 || votes >= participants;
        }
    }

    public boolean prompt(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        if (player == null || action == null) {
            return false;
        }
        if (dialogs(action).add(player.getUUID())) {
            sendPrompt(player, action);
            return true;
        }
        return false;
    }

    /**
     * Re-sends a prompt after an explicit player interaction. The original
     * packet may have been replaced by another server-opened screen while the
     * server still remembers the dialog as open.
     */
    public void reprompt(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        if (player == null || action == null) {
            return;
        }
        dialogs(action).add(player.getUUID());
        sendPrompt(player, action);
    }

    private static void sendPrompt(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        PacketDistributor.sendToPlayer(player, new OpenFestivalConfirmPayload(action));
    }

    public void closeDialog(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        if (player == null || action == null) {
            return;
        }
        dialogs(action).remove(player.getUUID());
    }

    public void clearDialog(OpenFestivalConfirmPayload.Action action) {
        if (action != null) {
            dialogs(action).clear();
        }
    }

    public void clearVote(OpenFestivalConfirmPayload.Action action) {
        if (action == null) {
            return;
        }
        votes(action).clear();
        voteParticipants(action).clear();
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        clearPlayerDialogs(playerId);
        for (Set<UUID> set : votes.values()) {
            set.remove(playerId);
        }
        for (Set<UUID> set : voteParticipants.values()) {
            set.remove(playerId);
        }
    }

    public void clearPlayerDialogs(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (Set<UUID> set : dialogs.values()) {
            set.remove(playerId);
        }
    }

    public void clearDialogs() {
        for (Set<UUID> set : dialogs.values()) {
            set.clear();
        }
    }

    public void clearVotes() {
        for (Set<UUID> set : votes.values()) {
            set.clear();
        }
        for (Set<UUID> set : voteParticipants.values()) {
            set.clear();
        }
    }

    public void clearAll() {
        clearDialogs();
        clearVotes();
    }

    public boolean hasDialog(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        return player != null && action != null && dialogs(action).contains(player.getUUID());
    }

    public boolean hasAnyDialog(ServerPlayer player, Collection<OpenFestivalConfirmPayload.Action> actions) {
        if (player == null || actions == null || actions.isEmpty()) {
            return false;
        }
        UUID playerId = player.getUUID();
        for (OpenFestivalConfirmPayload.Action action : actions) {
            if (dialogs(action).contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    public boolean handlesConfirmation(ServerPlayer player,
                                       OpenFestivalConfirmPayload.Action action,
                                       Collection<OpenFestivalConfirmPayload.Action> actions,
                                       Predicate<ServerPlayer> participantCheck,
                                       BooleanSupplier activeDayCheck) {
        if (player == null || action == null || actions == null || !actions.contains(action)) {
            return false;
        }
        if (participantCheck != null && participantCheck.test(player)) {
            return true;
        }
        if (hasAnyDialog(player, actions)) {
            return true;
        }
        return activeDayCheck != null && activeDayCheck.getAsBoolean();
    }
}
