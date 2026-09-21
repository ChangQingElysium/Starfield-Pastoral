package com.stardew.craft.api.v1.client;

/** Stardew calendar date: year starts at 1, season is 0–3, day is 1–28. */
public record StardewCalendarDate(int year, int season, int day) {
    public StardewCalendarDate {
        if (year < 1 || season < 0 || season > 3 || day < 1 || day > 28) {
            throw new IllegalArgumentException("Invalid Stardew calendar date");
        }
    }

    public StardewCalendarDate plusDays(int days) {
        long absolute = ((long) year - 1) * 112 + season * 28 + day - 1 + days;
        if (absolute < 0) throw new IllegalArgumentException("Date precedes year 1");
        return new StardewCalendarDate(Math.toIntExact(absolute / 112 + 1),
                (int) (absolute % 112 / 28), (int) (absolute % 28 + 1));
    }
}
