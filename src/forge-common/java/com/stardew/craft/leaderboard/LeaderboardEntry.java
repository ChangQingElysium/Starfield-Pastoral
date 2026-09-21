package com.stardew.craft.leaderboard;

import java.util.UUID;

public record LeaderboardEntry(
    UUID playerId,
    String playerName,
    long value,
    int rank,
    boolean online,
    boolean self
) {
}
