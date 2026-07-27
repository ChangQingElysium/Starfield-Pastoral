package com.stardew.craft.api.v1.npc;

/** Immutable player-to-NPC friendship state for rule evaluation and display. */
public record StardewNpcFriendshipSnapshot(
        int points,
        int giftsThisWeek,
        int lastGiftDayKey,
        int lastGiftWeekKey,
        int lastTalkDayKey,
        int firstMetDayKey,
        int dialogueDayKey,
        int dialogueInteractionsToday
) {
    public StardewNpcFriendshipSnapshot {
        if (points < 0) {
            throw new IllegalArgumentException("points must be non-negative");
        }
        if (giftsThisWeek < 0) {
            throw new IllegalArgumentException("giftsThisWeek must be non-negative");
        }
        if (dialogueInteractionsToday < 0) {
            throw new IllegalArgumentException(
                    "dialogueInteractionsToday must be non-negative");
        }
    }
}
