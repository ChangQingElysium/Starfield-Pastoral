package com.stardew.craft.festival;

import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveFestivalConfirmStateTest {
    @Test
    void firstReadyPlayerWaitsForTheRestOfTheFestivalParticipants() {
        ActiveFestivalConfirmState state = new ActiveFestivalConfirmState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        ActiveFestivalConfirmState.VoteProgress firstVote = state.castVote(
                OpenFestivalConfirmPayload.Action.START_CONTEST,
                first,
                List.of(first, second));

        assertEquals(1, firstVote.votes());
        assertEquals(2, firstVote.participants());
        assertFalse(firstVote.complete());

        ActiveFestivalConfirmState.VoteProgress secondVote = state.castVote(
                OpenFestivalConfirmPayload.Action.START_CONTEST,
                second,
                List.of(first, second));
        assertTrue(secondVote.complete());
    }

    @Test
    void lateJoinerDoesNotExpandAnExistingVote() {
        ActiveFestivalConfirmState state = new ActiveFestivalConfirmState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID lateJoiner = UUID.randomUUID();

        assertFalse(state.castVote(
                OpenFestivalConfirmPayload.Action.START_CONTEST,
                first,
                List.of(first, second)).complete());
        ActiveFestivalConfirmState.VoteProgress afterJoin = state.castVote(
                OpenFestivalConfirmPayload.Action.START_CONTEST,
                lateJoiner,
                List.of(first, second, lateJoiner));

        assertEquals(1, afterJoin.votes());
        assertEquals(2, afterJoin.participants());
        assertFalse(afterJoin.complete());
    }

    @Test
    void disconnectedPlayerIsRemovedFromTheInFlightVoteSnapshot() {
        ActiveFestivalConfirmState state = new ActiveFestivalConfirmState();
        UUID first = UUID.randomUUID();
        UUID disconnected = UUID.randomUUID();

        assertFalse(state.castVote(
                OpenFestivalConfirmPayload.Action.START_DANCE,
                first,
                List.of(first, disconnected)).complete());

        state.clearPlayer(disconnected);

        assertEquals(List.of(first), state.voteParticipants(
                OpenFestivalConfirmPayload.Action.START_DANCE).stream().toList());
        ActiveFestivalConfirmState.VoteProgress remaining = state.castVote(
                OpenFestivalConfirmPayload.Action.START_DANCE,
                first,
                List.of(first));
        assertEquals(1, remaining.votes());
        assertEquals(1, remaining.participants());
        assertTrue(remaining.complete());
    }

    @Test
    void nonParticipantCannotTurnAWaitingVoteIntoACompleteVote() {
        ActiveFestivalConfirmState state = new ActiveFestivalConfirmState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        assertFalse(state.castVote(
                OpenFestivalConfirmPayload.Action.LUAU_START,
                first,
                List.of(first, second)).complete());
        ActiveFestivalConfirmState.VoteProgress outsiderVote = state.castVote(
                OpenFestivalConfirmPayload.Action.LUAU_START,
                outsider,
                List.of(first, second, outsider));

        assertEquals(1, outsiderVote.votes());
        assertEquals(2, outsiderVote.participants());
        assertFalse(outsiderVote.complete());
    }
}
