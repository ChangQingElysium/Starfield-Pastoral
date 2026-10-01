package com.stardew.craft.client;

import com.stardew.craft.network.payload.LeaderboardSyncPayload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class LeaderboardClientCache {
    private static String metricId = "";
    private static String periodId = "total";
    private static int page;
    private static List<LeaderboardSyncPayload.Entry> rows = new ArrayList<>();
    private static LeaderboardSyncPayload.Entry selfEntry;
    private static int totalPlayers;
    private static long generatedAtMillis;
    private static String errorKey = "";
    private static boolean loading;

    private LeaderboardClientCache() {
    }

    public static void request(String requestedMetricId, String requestedPeriodId, int requestedPage) {
        metricId = requestedMetricId == null ? "" : requestedMetricId;
        periodId = requestedPeriodId == null || requestedPeriodId.isBlank() ? "total" : requestedPeriodId;
        page = Math.max(0, requestedPage);
        errorKey = "";
        loading = true;
    }

    public static void update(LeaderboardSyncPayload payload) {
        metricId = payload.metricId();
        periodId = payload.periodId();
        page = Math.max(0, payload.page());
        rows = new ArrayList<>(payload.rows());
        selfEntry = payload.selfEntry();
        totalPlayers = payload.totalPlayers();
        generatedAtMillis = payload.generatedAtMillis();
        errorKey = payload.errorKey() == null ? "" : payload.errorKey();
        loading = false;
    }

    public static boolean hasData(String requestedMetricId, String requestedPeriodId, int requestedPage) {
        String safePeriodId = requestedPeriodId == null || requestedPeriodId.isBlank() ? "total" : requestedPeriodId;
        return !hasError(requestedMetricId, safePeriodId, requestedPage)
                && !metricId.isBlank()
                && metricId.equals(requestedMetricId)
                && periodId.equals(safePeriodId)
                && page == Math.max(0, requestedPage)
                && generatedAtMillis > 0;
    }

    public static boolean isLoading(String requestedMetricId, String requestedPeriodId, int requestedPage) {
        String safePeriodId = requestedPeriodId == null || requestedPeriodId.isBlank() ? "total" : requestedPeriodId;
        return loading && metricId.equals(requestedMetricId) && periodId.equals(safePeriodId) && page == Math.max(0, requestedPage);
    }

    public static boolean hasError(String requestedMetricId, String requestedPeriodId, int requestedPage) {
        String safePeriodId = requestedPeriodId == null || requestedPeriodId.isBlank() ? "total" : requestedPeriodId;
        return !errorKey.isBlank() && metricId.equals(requestedMetricId) && periodId.equals(safePeriodId) && page == Math.max(0, requestedPage);
    }

    public static String getErrorKey() {
        return errorKey.isBlank() ? "stardewcraft.leaderboard.error" : errorKey;
    }

    public static List<LeaderboardSyncPayload.Entry> getRows() {
        return rows;
    }

    public static LeaderboardSyncPayload.Entry getSelfEntry() {
        return selfEntry;
    }

    public static int getTotalPlayers() {
        return totalPlayers;
    }

    public static long getGeneratedAtMillis() {
        return generatedAtMillis;
    }
}
