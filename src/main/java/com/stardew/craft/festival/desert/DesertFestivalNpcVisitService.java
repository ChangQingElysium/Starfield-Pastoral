package com.stardew.craft.festival.desert;

import com.stardew.craft.festival.FestivalService;

import java.util.Locale;
import java.util.Map;

public final class DesertFestivalNpcVisitService {
    public static final int CLOSE_TIME = 2400;

    private static final Map<String, VisitEntry> GENERAL_VISITS = Map.of(
        "emily", visit("DesertFestival", 800, "desert_festival_visit_emily", 2, ""),
        "harvey", visit("DesertFestival", 700, "desert_festival_visit_harvey", 2, ""),
        "sandy", visit("DesertFestival", 830, "desert_festival_visit_sandy", 2, ""),
        "willy", visit("DesertFestival", 640, "desert_festival_visit_willy", 3, "")
    );

    private static final Map<String, VisitEntry> DAY_1_VISITS = Map.ofEntries(
        Map.entry("abigail", visit("DesertFestival_1", 830, "desert_festival_visit_abigail", 2, "")),
        Map.entry("alex", visit("DesertFestival_1", 900, "desert_festival_visit_alex", 2, "")),
        Map.entry("caroline", visit("DesertFestival_1", 900, "desert_festival_visit_caroline", 2, "")),
        Map.entry("elliott", visit("DesertFestival_1", 900, "desert_festival_visit_elliott", 2, "")),
        Map.entry("gus", visit("DesertFestival_1", 900, "desert_festival_visit_gus", 3, "")),
        Map.entry("haley", visit("DesertFestival_1", 900, "desert_festival_visit_haley", 2, "square_5_3")),
        Map.entry("leah", visit("DesertFestival_1", 900, "desert_festival_visit_leah", 1, "")),
        Map.entry("lewis", visit("DesertFestival_1", 800, "desert_festival_visit_lewis_day1", 2, "")),
        Map.entry("pierre", visit("DesertFestival_1", 1700, "desert_festival_visit_pierre", 2, "")),
        Map.entry("sam", visit("DesertFestival_1", 830, "desert_festival_visit_sam", 2, "")),
        Map.entry("sebastian", visit("DesertFestival_1", 830, "desert_festival_visit_sebastian", 2, "sebastian_smoking"))
    );

    private static final Map<String, VisitEntry> DAY_2_VISITS = Map.ofEntries(
        Map.entry("clint", visit("DesertFestival_2", 910, "desert_festival_visit_clint", 1, "")),
        Map.entry("demetrius", visit("DesertFestival_2", 910, "desert_festival_visit_demetrius", 2, "")),
        Map.entry("haley", visit("DesertFestival_2", 900, "desert_festival_visit_haley", 2, "haley_desert_festival")),
        Map.entry("lewis", visit("DesertFestival_2", 800, "desert_festival_visit_lewis_day2", 2, "")),
        Map.entry("linus", visit("DesertFestival_2", 700, "desert_festival_visit_linus", 1, "dialogue")),
        Map.entry("maru", visit("DesertFestival_2", 1000, "desert_festival_visit_maru", 1, "square_1_5_1")),
        Map.entry("pam", visit("DesertFestival_2", 900, "desert_festival_visit_pam", 2, "")),
        Map.entry("penny", visit("DesertFestival_2", 1000, "desert_festival_visit_penny", 2, "")),
        Map.entry("robin", visit("DesertFestival_2", 900, "desert_festival_visit_robin", 0, ""))
    );

    private static final Map<String, VisitEntry> DAY_3_VISITS = Map.ofEntries(
        Map.entry("evelyn", visit("DesertFestival_3", 1000, "desert_festival_visit_evelyn", 0, "")),
        Map.entry("george", visit("DesertFestival_3", 1000, "desert_festival_visit_george", 0, "")),
        Map.entry("jas", visit("DesertFestival_3", 900, "desert_festival_visit_jas", 2, "")),
        Map.entry("jodi", visit("DesertFestival_3", 900, "desert_festival_visit_jodi", 2, "")),
        Map.entry("kent", visit("DesertFestival_3", 900, "desert_festival_visit_kent", 2, "")),
        Map.entry("lewis", visit("DesertFestival_3", 800, "desert_festival_visit_lewis_day3", 2, "")),
        Map.entry("marnie", visit("DesertFestival_3", 900, "desert_festival_visit_marnie", 2, "")),
        Map.entry("shane", visit("DesertFestival_3", 900, "desert_festival_visit_shane", 2, "")),
        Map.entry("vincent", visit("DesertFestival_3", 900, "desert_festival_visit_vincent", 1, "square_1_3_1"))
    );

    private DesertFestivalNpcVisitService() {
    }

    public static VisitEntry currentVisit(String npcId, int scheduleClock) {
        if (!DesertFestivalService.isFestivalDay() || scheduleClock >= CLOSE_TIME) {
            return null;
        }
        VisitEntry daySpecific = daySpecificVisit(npcId);
        if (daySpecific != null
            && "george".equals(normalize(npcId))
            && "DesertFestival_3".equals(daySpecific.scheduleKey())
            && scheduleClock >= 2250) {
            return null;
        }
        if (daySpecific != null && scheduleClock >= daySpecific.arrivalTime()) {
            return daySpecific;
        }
        VisitEntry general = GENERAL_VISITS.get(normalize(npcId));
        if (general != null && scheduleClock >= general.arrivalTime()) {
            return general;
        }
        return null;
    }

    public static boolean hasDaySpecificVisitToday(String npcId) {
        return DesertFestivalService.isFestivalDay() && daySpecificVisit(npcId) != null;
    }

    private static VisitEntry daySpecificVisit(String npcId) {
        int day = Math.max(1, FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID));
        String normalized = normalize(npcId);
        return switch (day) {
            case 1 -> DAY_1_VISITS.get(normalized);
            case 2 -> DAY_2_VISITS.get(normalized);
            case 3 -> DAY_3_VISITS.get(normalized);
            default -> null;
        };
    }

    private static VisitEntry visit(String scheduleKey, int arrivalTime, String pointId, int facing, String behavior) {
        return new VisitEntry(scheduleKey, arrivalTime, pointId, facing, behavior == null ? "" : behavior);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record VisitEntry(String scheduleKey, int arrivalTime, String pointId, int facing, String behavior) {
    }
}
