package com.stardew.craft.shop;

import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * Source: SDV map {@code LockedDoorWarp} actions and
 * {@code GameLocation.lockedDoorWarp}.
 */
public final class ShopHoursService {
    private static final String TOWN_KEY_FLAG = "HasTownKey";
    private static final String PIERRE_EXTENDED_HOURS_EVENT = "191393";
    private static final String WILLY_EXTENDED_HOURS_FLAG = "willyHours";

    private static final Map<String, DoorHours> DOOR_HOURS = Map.ofEntries(
        Map.entry("elliott_cabin_enter", new DoorHours(1000, 1800, false, false)),
        Map.entry("pierre_house_enter", new DoorHours(900, 2100, true, true)),
        Map.entry("museum_enter", new DoorHours(800, 1800, false, true)),
        Map.entry("blacksmith_enter", new DoorHours(900, 1600, false, true)),
        Map.entry("saloon_enter", new DoorHours(1200, 2400, false, true)),
        Map.entry("clinic_enter", new DoorHours(900, 1500, false, true)),
        Map.entry("carpenter_shop_enter", new DoorHours(900, 2000, false, true)),
        Map.entry("marnie_ranch_enter", new DoorHours(900, 1800, false, false)),
        Map.entry("adventurer_guild_enter", new DoorHours(1400, 2600, false, false)),
        Map.entry("fish_shop_enter", new DoorHours(900, 1700, false, false)),
        Map.entry("oasis_enter", new DoorHours(900, 2350, false, true)),
        Map.entry("joja_mart_enter", new DoorHours(900, 2300, false, true))
    );

    private ShopHoursService() {
    }

    /** @return whether the interaction was consumed because the door is closed. */
    public static boolean blockClosedPortal(ServerPlayer player, String targetId) {
        Component reason = closedPortalReason(player, targetId);
        if (reason == null) return false;
        ObjectDialogueService.show(player, reason);
        return true;
    }

    /** Pure admission query for continuous portals; never opens a dialogue. */
    @javax.annotation.Nullable
    public static Component closedPortalReason(ServerPlayer player, String targetId) {
        if ("stardewcraft:elliott_cabin_enter".equals(targetId)) {
            targetId = "elliott_cabin_enter";
        }
        DoorHours hours = DOOR_HOURS.get(targetId);
        if (player == null || hours == null) {
            return null;
        }

        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        if (storesClosedForFestival()) {
            return Component.translatable("stardewcraft.shop.hours.locked");
        }

        StardewTimeManager time = StardewTimeManager.get();
        boolean hasTownKey = playerData.hasMailFlag(TOWN_KEY_FLAG);
        if (hours.closedOnWednesday()
            && isWednesday(time.getCurrentDay())
            && !EventSeenData.get(player.serverLevel()).hasAnyPlayerSeen(PIERRE_EXTENDED_HOURS_EVENT)
            && !hasTownKey) {
            return Component.translatable("stardewcraft.shop.hours.closed_wednesday");
        }

        int openClock = targetId.equals("fish_shop_enter")
            && playerData.hasMailFlag(WILLY_EXTENDED_HOURS_FLAG)
            ? 800
            : hours.openClock();

        if (hasTownKey || greenRainKeepsDoorOpen(player, time, hours)) {
            return insufficientFriendshipReason(player, targetId);
        }

        int currentMinutes = time.getCurrentTime();
        if (currentMinutes >= clockToMinutes(openClock)
            && currentMinutes < clockToMinutes(hours.closeClock())) {
            return insufficientFriendshipReason(player, targetId);
        }

        return Component.translatable(
            "stardewcraft.shop.hours.locked_range",
            timeComponent(openClock),
            timeComponent(hours.closeClock())
        );
    }

    @javax.annotation.Nullable
    private static Component insufficientFriendshipReason(ServerPlayer player, String targetId) {
        // GameLocation.lockedDoorWarp: minFriendship is waived while IsWinterHere().
        if (StardewTimeManager.get().getCurrentSeason() == 3) {
            return null;
        }
        if (!"elliott_cabin_enter".equals(targetId)
                || hasElliottCabinFriendship(NpcFriendshipDataManager.get(player.serverLevel()), player.getUUID())) {
            return null;
        }
        return Component.translatable(
                "stardewcraft.shop.hours.friends_only",
                Component.translatable("entity.stardewcraft.npc.elliott"));
    }

    // This cabin requires two hearts outside winter; the town key only bypasses opening hours.
    static boolean hasElliottCabinFriendship(NpcFriendshipDataManager friendships, UUID playerId) {
        return friendships.getPointsForNpc(playerId, "elliott") >= 500;
    }

    private static boolean storesClosedForFestival() {
        StardewTimeManager time = StardewTimeManager.get();
        return FestivalService.getActiveFestivalForDate(time.getCurrentDay(), time.getCurrentSeason())
            .map(festival -> festival.startTime() < 1900)
            .orElse(false);
    }

    private static boolean greenRainKeepsDoorOpen(ServerPlayer player, StardewTimeManager time, DoorHours hours) {
        return hours.greenRainOverride()
            && time.getCurrentYear() == 1
            && "GreenRain".equalsIgnoreCase(WeatherManager.getCurrentWeather(player.serverLevel()));
    }

    private static boolean isWednesday(int dayOfSeason) {
        return Math.floorMod(dayOfSeason - 1, 7) == 2;
    }

    private static int clockToMinutes(int clock) {
        return (clock / 100) * 60 + clock % 100;
    }

    private static Component timeComponent(int clock) {
        int hour24 = clock / 100;
        int minute = clock % 100;
        int normalizedHour = Math.floorMod(hour24, 24);
        int hour12 = normalizedHour % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String meridiemKey = normalizedHour < 12
            ? "stardewcraft.shop.hours.am"
            : "stardewcraft.shop.hours.pm";
        return Component.translatable(
            "stardewcraft.shop.hours.time",
            hour12,
            String.format("%02d", minute),
            Component.translatable(meridiemKey),
            normalizedHour
        );
    }

    private record DoorHours(
        int openClock,
        int closeClock,
        boolean closedOnWednesday,
        boolean greenRainOverride
    ) {
    }
}
