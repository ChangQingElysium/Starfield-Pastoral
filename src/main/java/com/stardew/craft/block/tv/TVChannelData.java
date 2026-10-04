package com.stardew.craft.block.tv;

import com.stardew.craft.network.payload.OpenTVScreenPayload;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Server-side computation of all TV channel content.
 * Mirrors StardewValley.Objects.TV logic as closely as possible.
 */
public final class TVChannelData {

    // Channel constants matching original TV.cs
    public static final int CHANNEL_WEATHER = 2;
    public static final int CHANNEL_FORTUNE = 3;
    public static final int CHANNEL_TIPS = 4;
    public static final int CHANNEL_COOKING = 5;
    public static final int CHANNEL_FISHING = 6;
    public static final int CHANNEL_CURSED = 666;

    private TVChannelData() {}

    /**
     * Builds the network payload with pure data for the given player.
     * All display text is resolved client-side via I18n translation keys.
     * Mirrors TV.cs checkForAction + getWeeklyRecipe logic exactly.
     */
    public static OpenTVScreenPayload buildPayload(
            ServerPlayer player, int daysPlayed, int dayOfWeek,
            int currentDay, int currentSeason,
            String tomorrowWeather, double dailyLuck,
            int blockX, int blockY, int blockZ) {

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);

        // --- Channel availability (matches TV.cs checkForAction) ---
        boolean tipsAvailable = dayOfWeek == 0 || dayOfWeek == 3;  // Mon/Thu
        boolean cookingAvailable = isCookingAvailable(daysPlayed, dayOfWeek);
        boolean fishingAvailable = data.hasMailFlag("pamNewChannel");
        boolean cursedAvailable = false;  // original: Fall 26 + childrenTurnedToDoves + !cursed_doll

        // --- Fortune opening variant (matches TV.cs getFortuneTellerOpening random) ---
        // Original uses Game1.random.Next(5) — non-deterministic per open
        int fortuneOpeningVariant = new Random().nextInt(5);

        // --- Tip index (matches TV.cs getTodaysTip) ---
        int tipIndex = daysPlayed % 224;

        // --- Cooking (matches TV.cs getWeeklyRecipe) ---
        boolean isRerun = dayOfWeek == 2;
        int whichWeek = (int) (daysPlayed % 224 / 7);
        if (daysPlayed % 224 == 0) whichWeek = 32;
        if (isRerun) {
            whichWeek = getRerunWeek(daysPlayed, data);
        }
        String cookingRecipeId = getCookingRecipeIdForDay(data, daysPlayed, dayOfWeek);
        boolean cookingAlreadyKnown = data.isRecipeUnlocked(cookingRecipeId)
                || data.hasWatchedQueenOfSauceOnDay(daysPlayed);

        return new OpenTVScreenPayload(
                tipsAvailable, cookingAvailable, fishingAvailable, cursedAvailable,
                tomorrowWeather,
                dailyLuck, fortuneOpeningVariant,
                tipIndex,
                whichWeek, cookingRecipeId, cookingAlreadyKnown, isRerun,
                currentDay, currentSeason, daysPlayed,
                blockX, blockY, blockZ
        );
    }

    public static String getCookingRecipeIdForDay(ServerPlayer player, int daysPlayed, int dayOfWeek) {
        return getCookingRecipeIdForDay(PlayerStardewDataAPI.getData(player), daysPlayed, dayOfWeek);
    }

    public static boolean isCookingAvailable(int daysPlayed, int dayOfWeek) {
        return dayOfWeek == 6 || (dayOfWeek == 2 && daysPlayed > 7); // Sun / Wed(rerun)
    }

    /** Read-only selection, shared by the TV unlock handler and the addon daily snapshot. */
    public static String getCookingRecipeIdForDay(PlayerStardewData data, int daysPlayed, int dayOfWeek) {
        String watchedRecipeId = data.getQueenOfSauceRecipeIdForDay(daysPlayed);
        if (!watchedRecipeId.isBlank()) {
            return watchedRecipeId;
        }

        boolean isRerun = dayOfWeek == 2;
        int whichWeek = (int) (daysPlayed % 224 / 7);
        if (daysPlayed % 224 == 0) {
            whichWeek = 32;
        }
        if (isRerun) {
            whichWeek = getRerunWeek(daysPlayed, data);
        }
        return getRecipeIdForWeek(whichWeek);
    }

    // ==================== Cooking (week → recipe ID, matching Tv_CookingChannel.json) ====================

    private static final String[] WEEK_RECIPE = {
        null,                    // index 0 unused
        "stir_fry",              // 1
        "coleslaw",              // 2
        "radish_salad",          // 3
        "omelet",                // 4
        "baked_fish",            // 5
        "pancakes",              // 6
        "maki_roll",             // 7
        "bread",                 // 8
        "tortilla",              // 9
        "trout_soup",            // 10
        "glazed_yams",           // 11
        "artichoke_dip",         // 12
        "plum_pudding",          // 13
        "chocolate_cake",        // 14
        "pumpkin_pie",           // 15
        "cranberry_candy",       // 16
        "pizza",                 // 17
        "hashbrowns",            // 18
        "complete_breakfast",    // 19
        "lucky_lunch",           // 20
        "carp_surprise",         // 21
        "maple_bar",             // 22
        "pink_cake",             // 23
        "roasted_hazelnuts",     // 24
        "fruit_salad",           // 25
        "blackberry_cobbler",    // 26
        "crab_cakes",            // 27
        "fiddlehead_risotto",    // 28
        "poppyseed_muffin",      // 29
        "lobster_bisque",        // 30
        "bruschetta",            // 31
        "shrimp_cocktail"        // 32
    };

    /** All 32 recipes taught by the Queen of Sauce show (vanilla Tv_CookingChannel). */
    public static java.util.List<String> allCookingRecipeIds() {
        return java.util.Arrays.stream(WEEK_RECIPE).filter(java.util.Objects::nonNull).toList();
    }

    static String getRecipeIdForWeek(int week) {
        if (week >= 1 && week <= 32) return WEEK_RECIPE[week];
        return WEEK_RECIPE[1];
    }

    private static int getRerunWeek(int daysPlayed, PlayerStardewData data) {
        int totalRerunWeeksAvailable = Math.min((daysPlayed - 3) / 7, 32);
        if (totalRerunWeeksAvailable < 1) totalRerunWeeksAvailable = 1;

        List<Integer> unknownWeeks = new ArrayList<>();
        for (int i = 1; i <= totalRerunWeeksAvailable; i++) {
            if (!data.isRecipeUnlocked(getRecipeIdForWeek(i))) {
                unknownWeeks.add(i);
            }
        }

        Random r = new Random(stableDaySeed(daysPlayed));
        if (unknownWeeks.isEmpty()) {
            return Math.max(1, 1 + r.nextInt(totalRerunWeeksAvailable));
        }
        return unknownWeeks.get(r.nextInt(unknownWeeks.size()));
    }

    // ==================== Utility ====================

    private static long stableDaySeed(int daysPlayed) {
        return (long) daysPlayed * 0x9E3779B97F4A7C15L;
    }
}
