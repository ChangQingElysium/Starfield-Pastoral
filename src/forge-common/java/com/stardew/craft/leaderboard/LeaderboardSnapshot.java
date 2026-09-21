package com.stardew.craft.leaderboard;

import java.util.List;

public record LeaderboardSnapshot(
    LeaderboardMetric metric,
    LeaderboardPeriod period,
    List<LeaderboardEntry> rows,
    LeaderboardEntry selfEntry,
    int totalPlayers,
    long generatedAtMillis
) {
}
