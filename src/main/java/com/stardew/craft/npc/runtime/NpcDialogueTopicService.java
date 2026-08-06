package com.stardew.craft.npc.runtime;

import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Source-backed entry points for non-cutscene active NPC dialogue topics. */
public final class NpcDialogueTopicService {
    private static final int WORLD_CHANGE_DURATION = 7;

    private NpcDialogueTopicService() {
    }

    public static void onNewDay(
            MinecraftServer server,
            String previousWeather,
            int previousYear
    ) {
        if (server == null || !isYearOneGreenRain(previousWeather, previousYear)) {
            return;
        }
        NpcDialogueEventData events = NpcDialogueEventData.get(server);
        for (var playerId : PlayerDataManager.get().getAllPlayerData().keySet()) {
            events.activate(playerId, "GreenRainFinished", 1);
        }
    }

    public static void onCommunityBoulderRemoved(ServerPlayer player) {
        activate(player, "cc_Boulder", WORLD_CHANGE_DURATION);
    }

    /** SDV WorldChangeEvent: greenhouse restoration activates cc_Greenhouse for 3 days. */
    public static void onGreenhouseRepaired(ServerPlayer player) {
        activate(player, "cc_Greenhouse", 3);
    }

    public static void onJojaDevelopmentFormViewed(ServerPlayer player) {
        activate(player, "joja_Begin", WORLD_CHANGE_DURATION);
    }

    public static void onMineFloorReached(ServerPlayer player, int floor) {
        String topic = mineAreaTopic(floor);
        if (!topic.isBlank()) {
            activate(player, topic, 4);
        }
    }

    static boolean isYearOneGreenRain(String weather, int year) {
        if (year != 1 || weather == null) {
            return false;
        }
        String normalized = weather.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("greenrain") || normalized.equals("green_rain");
    }

    static String mineAreaTopic(int floor) {
        if (floor >= 121) {
            return "mineArea_121";
        }
        if (floor >= 80) {
            return "mineArea_80";
        }
        if (floor >= 40) {
            return "mineArea_40";
        }
        return "";
    }

    private static void activate(ServerPlayer player, String topic, int duration) {
        if (player == null || topic == null || topic.isBlank()) {
            return;
        }
        NpcDialogueEventData.get(player.getServer())
                .activate(player.getUUID(), topic, duration);
    }
}
