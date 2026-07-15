package com.stardew.craft.book;

import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Random;

public final class BooksellerSchedule {
    private static final int[][] POSSIBLE_DAYS = {
            {11, 12, 21, 22, 25},
            {9, 12, 18, 25, 27},
            {4, 7, 8, 9, 12, 19, 22, 25},
            {5, 11, 12, 19, 22, 24}
    };

    private BooksellerSchedule() {
    }

    public static boolean isToday(ServerLevel level) {
        StardewTimeManager time = StardewTimeManager.get();
        return isBooksellerDay(level, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
    }

    public static boolean isBooksellerDay(ServerLevel level, int year, int season, int day) {
        int[] days = daysForSeason(level, year, season);
        return days[0] == day || days[1] == day;
    }

    public static int[] daysForSeason(ServerLevel level, int year, int season) {
        int seasonIndex = Math.floorMod(season, POSSIBLE_DAYS.length);
        int[] possibleDays = POSSIBLE_DAYS[seasonIndex];
        Random random = new Random(createSeed(level, year, seasonIndex));
        int index = random.nextInt(possibleDays.length);
        return new int[] {
                possibleDays[index],
                possibleDays[(index + possibleDays.length / 2) % possibleDays.length]
        };
    }

    public static void onNewDay(ServerLevel level, List<ServerPlayer> players) {
        if (!isToday(level)) {
            return;
        }
        Component message = Component.translatable("stardewcraft.bookseller.in_town");
        for (ServerPlayer player : players) {
			com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, message);
        }
    }

    private static long createSeed(ServerLevel level, int year, int seasonIndex) {
        long seed = 0xCBF29CE484222325L;
        seed = mix(seed, (long) year * 11L);
        seed = mix(seed, level.getSeed());
        seed = mix(seed, seasonIndex);
        return seed;
    }

    private static long mix(long seed, long value) {
        long mixed = value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
        return seed ^ mixed;
    }
}
