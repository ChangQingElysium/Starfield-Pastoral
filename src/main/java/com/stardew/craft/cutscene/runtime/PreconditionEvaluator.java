package com.stardew.craft.cutscene.runtime;

import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.NpcFriendshipClientCache;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.cutscene.data.EventPrecondition;
import com.stardew.craft.cutscene.network.ClientEventSeenCache;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.ClientWeatherCache;
import com.stardew.craft.weather.WeatherTypeNormalizer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Locale;

/**
 * Evaluates event preconditions on the client side.
 * All required data is available in client caches
 * (friendship, time, weather, skills, mail, seen events).
 */
@OnlyIn(Dist.CLIENT)
public final class PreconditionEvaluator {

    private PreconditionEvaluator() {}

    /**
     * @return true if ALL preconditions pass.
     */
    public static boolean evaluate(List<EventPrecondition> preconditions) {
        for (EventPrecondition p : preconditions) {
            if (!evaluateSingle(p)) return false;
        }
        return true;
    }

    private static boolean evaluateSingle(EventPrecondition p) {
        return switch (p.type()) {
            case "friendship" -> checkFriendship(p);
            case "saw_event" -> ClientEventSeenCache.hasSeen(p.getString("id"));
            case "not_saw_event" -> !ClientEventSeenCache.hasSeen(p.getString("id"));
            case "time" -> checkTime(p);
            case "season" -> checkSeason(p);
            case "weather" -> checkWeather(p);
            case "day_of_week" -> checkDayOfWeek(p);
            case "day_of_month" -> checkDayOfMonth(p);
            case "days_played" -> checkDaysPlayed(p);
            case "year" -> checkYear(p);
            case "player_farm_age_days" -> checkPlayerFarmAgeDays(p);
            case "has_item" -> checkHasItem(p);
            case "has_secret_note" -> ClientPlayerDataCache.hasSeenSecretNote(p.getString("id"));
            case "has_museum_donation" -> checkHasMuseumDonation();
            case "museum_empty" -> com.stardew.craft.client.ClientMuseumDonationCache.isEmpty();
            case "money" -> ClientPlayerDataCache.getMoney() >= p.getInt("min", 0);
            case "skill" -> checkSkill(p);
            case "mail" -> ClientPlayerDataCache.hasMailFlag(p.getString("id"));
            case "not_mail" -> !ClientPlayerDataCache.hasMailFlag(p.getString("id"));
            case "flag" -> ClientPlayerDataCache.hasMailFlag(p.getString("id"));
            case "not_flag" -> !ClientPlayerDataCache.hasMailFlag(p.getString("id"));
            case "is_host" -> ClientPlayerDataCache.isStoryHost();
            // Future: dating, married, community_center_done
            default -> true; // unknown conditions pass by default
        };
    }

    private static boolean checkFriendship(EventPrecondition p) {
        String npcId = p.getString("npc");
        int minPoints = p.getInt("min", 0);
        if (npcId == null) return false;
        NpcFriendshipClientCache.Entry entry = NpcFriendshipClientCache.findByNpcId(npcId.toLowerCase(Locale.ROOT));
        return entry != null && entry.points() >= minPoints;
    }

    private static boolean checkTime(EventPrecondition p) {
        StardewTimeManager tm = StardewTimeHud.getClientTimeCache();
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

    private static boolean checkSeason(EventPrecondition p) {
        StardewTimeManager tm = StardewTimeHud.getClientTimeCache();
        String expected = p.getString("season");
        if (expected == null) return true;
        int idx = tm.getCurrentSeason();
        String current = (idx >= 0 && idx < SEASON_NAMES.length) ? SEASON_NAMES[idx] : "";
        return expected.equalsIgnoreCase(current);
    }

    private static boolean checkWeather(EventPrecondition p) {
        String expected = p.getString("weather");
        if (expected == null) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        ResourceKey<Level> dim = mc.level.dimension();
        String current = ClientWeatherCache.getCurrentWeather(dim);
        return WeatherTypeNormalizer.normalize(expected)
                .equalsIgnoreCase(WeatherTypeNormalizer.normalize(current));
    }

    private static boolean checkDayOfWeek(EventPrecondition p) {
        StardewTimeManager tm = StardewTimeHud.getClientTimeCache();
        int dayOfMonth = tm.getCurrentDay(); // 1-28
        int dayOfWeek = ((dayOfMonth - 1) % 7); // 0=Mon, 1=Tue, ..., 6=Sun
        String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String today = DAYS[dayOfWeek];

        // Check if today is in the "days" JSON array
        var raw = p.raw();
        if (!raw.has("days")) return true;
        var arr = raw.getAsJsonArray("days");
        for (var e : arr) {
            if (today.equalsIgnoreCase(e.getAsString())) return true;
        }
        return false;
    }

    private static boolean checkDayOfMonth(EventPrecondition p) {
        return StardewTimeHud.getClientTimeCache().getCurrentDay() == p.getInt("day", -1);
    }

    private static boolean checkDaysPlayed(EventPrecondition p) {
        // SDV parity: Game1.stats.DaysPlayed counts from day 1.
        // total = (year-1)*112 + season*28 + day
        StardewTimeManager tm = StardewTimeHud.getClientTimeCache();
        int total = (tm.getCurrentYear() - 1) * (28 * 4)
                + tm.getCurrentSeason() * 28
                + tm.getCurrentDay();
        int min = p.getInt("min", 0);
        int max = p.getInt("max", Integer.MAX_VALUE);
        return total >= min && total <= max;
    }

    private static boolean checkYear(EventPrecondition p) {
        StardewTimeManager tm = StardewTimeHud.getClientTimeCache();
        int current = tm.getCurrentYear();
        int exact = p.getInt("year", -1);
        if (exact > 0) {
            return current == exact;
        }
        int min = p.getInt("min", 1);
        int max = p.getInt("max", Integer.MAX_VALUE);
        return current >= min && current <= max;
    }

    private static boolean checkPlayerFarmAgeDays(EventPrecondition p) {
        int firstJoinDay = ClientPlayerDataCache.getFirstJoinDay();
        if (firstJoinDay < 0) {
            return false;
        }
        int currentAbsoluteDay = StardewTimeHud.getClientTimeCache().getAbsoluteDay();
        int age = currentAbsoluteDay - firstJoinDay;
        int min = p.getInt("min", 0);
        int max = p.getInt("max", Integer.MAX_VALUE);
        return age >= min && age <= max;
    }

    private static boolean checkSkill(EventPrecondition p) {
        String skillName = p.getString("skill");
        int minLevel = p.getInt("min", 0);
        if (skillName == null) return true;
        try {
            SkillType skill = SkillType.valueOf(skillName.toUpperCase(Locale.ROOT));
            return ClientPlayerDataCache.getSkillLevel(skill) >= minLevel;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean checkHasItem(EventPrecondition p) {
        String itemId = p.getString("item");
        int count = p.getInt("count", 1);
        if (itemId == null || itemId.isBlank() || count <= 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item != Items.AIR && mc.player.getInventory().countItem(item) >= count;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean checkHasMuseumDonation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            var stack = mc.player.getInventory().getItem(slot);
            if (!com.stardew.craft.museum.MuseumDonationItems.isDonatable(stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!com.stardew.craft.client.ClientMuseumDonationCache.isDonated(itemId)) return true;
        }
        return false;
    }
}
