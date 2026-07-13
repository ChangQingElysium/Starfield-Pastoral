package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NightMarketNpcVisitService {
    private static final Map<String, VisitEntry> DAY_1_VISITS = Map.ofEntries(
        entry(1, "linus", 1600, 2330, "night_market_day1_linus", 2),
        entry(1, "jas", 1450, 2300, "night_market_day1_jas", 2),
        entry(1, "shane", 1500, 2300, "night_market_day1_shane", 2),
        entry(1, "emily", 1430, 2430, "night_market_day1_emily", 2),
        entry(1, "abigail", 1430, 2400, "night_market_day1_abigail", 2),
        entry(1, "penny", 1600, 2350, "night_market_day1_penny", 2),
        entry(1, "clint", 1700, 2400, "night_market_day1_clint", 1),
        entry(1, "harvey", 1700, 2400, "night_market_day1_harvey", 1),
        entry(1, "leah", 1600, 2340, "night_market_day1_leah", 0)
    );

    private static final Map<String, VisitEntry> DAY_2_VISITS = Map.ofEntries(
        entry(2, "vincent", 1630, 2330, "night_market_day2_vincent", 2),
        entry(2, "robin", 1700, 2330, "night_market_day2_robin", 1),
        entry(2, "demetrius", 1540, 2330, "night_market_day2_demetrius", 3),
        entry(2, "maru", 1540, 2330, "night_market_day2_maru", 3),
        entry(2, "sebastian", 1650, 2340, "night_market_day2_sebastian", 2),
        entry(2, "caroline", 1600, 2330, "night_market_day2_caroline", 3),
        entry(2, "haley", 1630, 2400, "night_market_day2_haley", 2),
        entry(2, "lewis", 1620, 2300, "night_market_day2_lewis", 2),
        entry(2, "marnie", 1600, 2340, "night_market_day2_marnie", 0)
    );

    private static final Map<String, VisitEntry> DAY_3_VISITS = Map.ofEntries(
        entry(3, "jodi", 1630, 2330, "night_market_day3_jodi", 2),
        entry(3, "alex", 1500, 2400, "night_market_day3_alex", 1),
        entry(3, "sam", 1700, 2400, "night_market_day3_sam", 3),
        entry(3, "george", 1620, 2340, "night_market_day3_george", 2),
        entry(3, "evelyn", 1630, 2340, "night_market_day3_evelyn", 2),
        entry(3, "elliott", 1650, 2500, "night_market_day3_elliott", 2)
    );

    private NightMarketNpcVisitService() {
    }

    public static VisitEntry currentVisit(String npcId, int scheduleClock) {
        if (!FestivalService.isPassiveFestivalDay(NightMarketPainterService.FESTIVAL_ID)) {
            return null;
        }
        VisitEntry visit = visitsForCurrentDay().get(normalize(npcId));
        if (visit == null || scheduleClock < visit.arrivalTime() || scheduleClock >= visit.departureTime()) {
            return null;
        }
        return visit;
    }

    public static String resolveDialogueKey(String npcId) {
        VisitEntry visit = currentVisit(npcId, FestivalService.currentTimeOfDay());
        if (visit == null) {
            return "";
        }
        return "stardewcraft.festival.night_market.dialogue."
            + visit.scheduleKey() + "." + normalize(npcId);
    }

    public static int forceRefreshNpcSchedules(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        Set<String> visitorIds = visitsForCurrentDay().keySet();
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);
        int moved = 0;
        for (String npcId : visitorIds) {
            if (NpcSpawnManager.forceNpcToCurrentSchedule(level, npcId)) {
                moved++;
            }
        }
        return moved;
    }

    private static Map<String, VisitEntry> visitsForCurrentDay() {
        return switch (FestivalService.getDayOfPassiveFestival(NightMarketPainterService.FESTIVAL_ID)) {
            case 1 -> DAY_1_VISITS;
            case 2 -> DAY_2_VISITS;
            case 3 -> DAY_3_VISITS;
            default -> Map.of();
        };
    }

    private static Map.Entry<String, VisitEntry> entry(
            int festivalDay,
            String npcId,
            int arrivalTime,
            int departureTime,
            String pointId,
            int facing) {
        return Map.entry(npcId, new VisitEntry(
            "winter_" + (14 + festivalDay), arrivalTime, departureTime, pointId, facing
        ));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record VisitEntry(
        String scheduleKey,
        int arrivalTime,
        int departureTime,
        String pointId,
        int facing
    ) {
    }
}
