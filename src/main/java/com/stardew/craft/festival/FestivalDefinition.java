package com.stardew.craft.festival;

import com.stardew.craft.api.v1.condition.StardewCondition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record FestivalDefinition(
    ResourceLocation resourceId,
    String id,
    FestivalType type,
    String sourceName,
    String sourceDisplayToken,
    String sourceCondition,
    List<StardewCondition> availableWhen,
    int season,
    int startDay,
    int endDay,
    int startTime,
    int endTime,
    boolean showOnCalendar,
    boolean onlyShowStartMessageOnFirstDay,
    String startMessageToken,
    String startMessageKey,
    String announcementMailId,
    String locationKey,
    String mapOverlayId,
    Map<String, String> mapReplacements,
    List<String> shopIds,
    String mechanicId
) {
    public FestivalDefinition {
        if (resourceId == null) {
            throw new IllegalArgumentException("festival resource id must not be null");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("festival id must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("festival type must not be null");
        }
        if (season < 0 || season > 3) {
            throw new IllegalArgumentException("season must be 0..3");
        }
        if (startDay < 1 || startDay > 28 || endDay < startDay || endDay > 28) {
            throw new IllegalArgumentException("festival day range must be within 1..28");
        }
        sourceName = sourceName == null ? id : sourceName;
        sourceDisplayToken = sourceDisplayToken == null ? "" : sourceDisplayToken;
        sourceCondition = sourceCondition == null ? "" : sourceCondition;
        availableWhen = availableWhen == null ? List.of() : List.copyOf(availableWhen);
        startMessageToken = startMessageToken == null ? "" : startMessageToken;
        startMessageKey = startMessageKey == null ? "" : startMessageKey;
        announcementMailId = announcementMailId == null ? "" : announcementMailId;
        locationKey = locationKey == null ? "" : locationKey;
        mapOverlayId = mapOverlayId == null ? "" : mapOverlayId;
        mapReplacements = mapReplacements == null ? Map.of() : Map.copyOf(mapReplacements);
        shopIds = shopIds == null ? List.of() : List.copyOf(shopIds);
        mechanicId = mechanicId == null ? "" : mechanicId;
    }

    public boolean isDate(int season, int day) {
        return this.season == season && day >= startDay && day <= endDay;
    }

    public int dayOfFestival(int season, int day) {
        return isDate(season, day) ? day - startDay + 1 : -1;
    }
}
