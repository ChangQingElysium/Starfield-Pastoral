package com.stardew.craft.cutscene.server;

import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.data.EventPrecondition;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import com.stardew.craft.weather.WeatherTypeNormalizer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * Server-side precondition evaluator for interact_npc events.
 * Used by NpcInteractionService to check if a heart event should trigger
 * before opening normal dialogue.
 */
public final class ServerPreconditionEvaluator {

    private ServerPreconditionEvaluator() {}

    /**
     * Find the first untriggered interact_npc event for this NPC whose preconditions pass.
     * @return event ID, or null if none
     */
    public static String findPendingNpcEvent(ServerPlayer player, String npcId) {
        List<EventData> events = EventRegistry.getByNpc(npcId);
        if (events.isEmpty()) return null;

        ServerLevel level = (ServerLevel) player.level();
        EventSeenData seenData = EventSeenData.get(level);

        for (EventData event : events) {
            if (seenData.hasSeen(player.getUUID(), event.id())) continue;
            if (evaluate(player, level, event.preconditions())) {
                return event.id();
            }
        }
        return null;
    }

    /**
     * @return true if ALL preconditions pass on the server side.
     */
    public static boolean evaluate(ServerPlayer player, ServerLevel level,
                                   List<EventPrecondition> preconditions) {
        for (EventPrecondition p : preconditions) {
            if (!evaluateSingle(player, level, p)) return false;
        }
        return true;
    }

    private static boolean evaluateSingle(ServerPlayer player, ServerLevel level, EventPrecondition p) {
        return switch (p.type()) {
            case "friendship" -> checkFriendship(player, level, p);
            case "saw_event" -> {
                EventSeenData seen = EventSeenData.get(level);
                yield seen.hasSeen(player.getUUID(), p.getString("id"));
            }
            case "not_saw_event" -> {
                EventSeenData seen = EventSeenData.get(level);
                yield !seen.hasSeen(player.getUUID(), p.getString("id"));
            }
            case "time" -> checkTime(level, p);
            case "season" -> checkSeason(level, p);
            case "weather" -> checkWeather(level, p);
            case "day_of_week" -> checkDayOfWeek(level, p);
            case "day_of_month" -> checkDayOfMonth(level, p);
            case "days_played" -> checkDaysPlayed(p);
            case "year" -> checkYear(p);
            case "player_farm_age_days" -> checkPlayerFarmAgeDays(player, p);
            case "has_item" -> checkHasItem(player, p);
            case "has_museum_donation" -> checkHasMuseumDonation(player, level);
            case "museum_empty" -> com.stardew.craft.museum.MuseumDonationData.get(level)
                    .getDonatedItems(player.getUUID()).isEmpty();
            case "money" -> com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) >= p.getInt("min", 0);
            case "skill" -> checkSkill(player, p);
            case "mail", "flag" -> {
                PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                yield data.hasMailFlag(p.getString("id"));
            }
            case "not_mail", "not_flag" -> {
                PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                yield !data.hasMailFlag(p.getString("id"));
            }
            case "is_host" -> isStoryHost(player);
            default -> StardewConditions.decodeLegacy(p.type(), p.raw())
                    .flatMap(condition -> StardewConditions.test(
                            condition,
                            new StardewConditionContext(level, player)))
                    .result()
                    .orElse(false);
        };
    }

    private static boolean isStoryHost(ServerPlayer player) {
        java.util.UUID ownerUuid = com.stardew.craft.farm.FarmInstanceRegistry.get().getOwnerForPlayer(player.getUUID());
        return ownerUuid == null || ownerUuid.equals(player.getUUID());
    }

    private static boolean checkFriendship(ServerPlayer player, ServerLevel level, EventPrecondition p) {
        String npcId = p.getString("npc");
        int minPoints = p.getInt("min", 0);
        if (npcId == null) return false;
        // Read-only lookup: avoid getOrCreate here since precondition checks run on every
        // NPC interaction and would otherwise create empty entries that bloat the save.
        NpcFriendshipDataManager fm = NpcFriendshipDataManager.get(level);
        return fm.getPointsForNpc(player.getUUID(), npcId.toLowerCase(Locale.ROOT)) >= minPoints;
    }

    private static boolean checkTime(ServerLevel level, EventPrecondition p) {
        StardewTimeManager tm = StardewTimeManager.get();
        int current = tm.getCurrentTime();
        // JSON 用星露谷 HHMM 格式（600=6:00, 1300=13:00, 2600=次日2:00），
        // 这里转成模组内部的“自午夜起累计分钟数”再比较。
        int min = hhmmToMinutes(p.getInt("min", 0));
        int max = hhmmToMinutes(p.getInt("max", 2600));
        return current >= min && current <= max;
    }

    private static int hhmmToMinutes(int hhmm) {
        return (hhmm / 100) * 60 + (hhmm % 100);
    }

    private static final String[] SEASON_NAMES = {"spring", "summer", "fall", "winter"};

    private static boolean checkSeason(ServerLevel level, EventPrecondition p) {
        String expected = p.getString("season");
        if (expected == null) return true;
        int idx = StardewTimeManager.get().getCurrentSeason();
        String current = (idx >= 0 && idx < SEASON_NAMES.length) ? SEASON_NAMES[idx] : "";
        return expected.equalsIgnoreCase(current);
    }

    private static boolean checkWeather(ServerLevel level, EventPrecondition p) {
        String expected = p.getString("weather");
        if (expected == null) return true;
        return WeatherTypeNormalizer.normalize(expected)
                .equalsIgnoreCase(WeatherTypeNormalizer.normalize(WeatherManager.getCurrentWeather(level)));
    }

    private static boolean checkDayOfWeek(ServerLevel level, EventPrecondition p) {
        int dayOfMonth = StardewTimeManager.get().getCurrentDay();
        int dayOfWeek = ((dayOfMonth - 1) % 7);
        String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String today = DAYS[dayOfWeek];
        var raw = p.raw();
        if (!raw.has("days")) return true;
        var arr = raw.getAsJsonArray("days");
        for (var e : arr) {
            if (today.equalsIgnoreCase(e.getAsString())) return true;
        }
        return false;
    }

    private static boolean checkDayOfMonth(ServerLevel level, EventPrecondition p) {
        return StardewTimeManager.get().getCurrentDay() == p.getInt("day", -1);
    }

    private static boolean checkDaysPlayed(EventPrecondition p) {
        // SDV parity: Game1.stats.DaysPlayed counts from day 1.
        StardewTimeManager tm = StardewTimeManager.get();
        int total = (tm.getCurrentYear() - 1) * (28 * 4)
                + tm.getCurrentSeason() * 28
                + tm.getCurrentDay();
        int min = p.getInt("min", 0);
        int max = p.getInt("max", Integer.MAX_VALUE);
        return total >= min && total <= max;
    }

    private static boolean checkYear(EventPrecondition p) {
        int current = StardewTimeManager.get().getCurrentYear();
        int exact = p.getInt("year", -1);
        if (exact > 0) {
            return current == exact;
        }
        int min = p.getInt("min", 1);
        int max = p.getInt("max", Integer.MAX_VALUE);
        return current >= min && current <= max;
    }

    private static boolean checkPlayerFarmAgeDays(ServerPlayer player, EventPrecondition p) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int firstJoinDay = data.getFirstJoinDay();
        if (firstJoinDay < 0) {
            return false;
        }
        int age = StardewTimeManager.get().getAbsoluteDay() - firstJoinDay;
        int min = p.getInt("min", 0);
        int max = p.getInt("max", Integer.MAX_VALUE);
        return age >= min && age <= max;
    }

    private static boolean checkSkill(ServerPlayer player, EventPrecondition p) {
        String skillName = p.getString("skill");
        int minLevel = p.getInt("min", 0);
        if (skillName == null) return true;
        try {
            SkillType skill = SkillType.valueOf(skillName.toUpperCase(Locale.ROOT));
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            return data.getSkillLevel(skill) >= minLevel;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean checkHasItem(ServerPlayer player, EventPrecondition p) {
        String itemId = p.getString("item");
        int count = p.getInt("count", 1);
        if (itemId == null || itemId.isBlank() || count <= 0) return false;
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item != Items.AIR && player.getInventory().countItem(item) >= count;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean checkHasMuseumDonation(ServerPlayer player, ServerLevel level) {
        var data = com.stardew.craft.museum.MuseumDonationData.get(level);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!com.stardew.craft.museum.MuseumDonationItems.isDonatable(stack)) continue;
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!data.isDonated(player.getUUID(), itemId)) return true;
        }
        return false;
    }
}
