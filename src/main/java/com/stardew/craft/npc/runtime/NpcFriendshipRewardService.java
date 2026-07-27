package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.npc.StardewNpcFriendshipRewardRegistry;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NpcFriendshipRewardService {
    private static final String DATA_PATH = "/data/stardewcraft/npc/friendship_rewards.json";
    private static volatile List<RewardRule> cachedRules;

    private NpcFriendshipRewardService() {
    }

    public static boolean applyEligibleRewards(ServerPlayer player, String npcId, int points) {
        if (player == null || npcId == null || npcId.isBlank()) {
            return false;
        }
        String normalizedNpc = npcId.trim().toLowerCase(Locale.ROOT);
        boolean changed = false;
        for (RewardRule rule : rules()) {
            if (!rule.npcId().equals(normalizedNpc) || points < rule.points()) {
                continue;
            }
            changed |= applyReward(player, rule);
        }
        changed |= StardewNpcFriendshipRewardRegistry.apply(player, normalizedNpc, points);
        return changed;
    }

    public static boolean applyAllEligibleRewards(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        NpcFriendshipDataManager friendship = NpcFriendshipDataManager.get(player.serverLevel());
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : friendship.getPointsForPlayer(player.getUUID()).entrySet()) {
            changed |= applyEligibleRewards(player, entry.getKey(), entry.getValue());
        }
        return changed;
    }

    private static boolean applyReward(ServerPlayer player, RewardRule rule) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(rule.flag())) {
            return false;
        }

        boolean changed = false;
        data.addMailFlag(rule.flag());
        changed = true;

        for (String flag : rule.flags()) {
            if (!data.hasMailFlag(flag)) {
                data.addMailFlag(flag);
                changed = true;
            }
        }
        for (String mailId : rule.mailNow()) {
            MailService.addMail(player, mailId);
            changed = true;
        }
        for (String mailId : rule.mailForTomorrow()) {
            MailService.addMailForTomorrow(player, mailId);
            changed = true;
        }
        for (String recipeId : rule.recipes()) {
            if (data.unlockRecipe(recipeId)) {
                changed = true;
            }
        }
        if (!rule.unlockSources().isEmpty() && PlayerStardewDataAPI.applyUnlockSources(player, rule.unlockSources())) {
            changed = true;
        }
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    private static List<RewardRule> rules() {
        List<RewardRule> local = cachedRules;
        if (local != null) {
            return local;
        }
        synchronized (NpcFriendshipRewardService.class) {
            if (cachedRules == null) {
                cachedRules = loadRules();
            }
            return cachedRules;
        }
    }

    private static List<RewardRule> loadRules() {
        try (InputStream in = NpcFriendshipRewardService.class.getResourceAsStream(DATA_PATH)) {
            if (in == null) {
                return List.of();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonArray array;
                if (root != null && root.isJsonArray()) {
                    array = root.getAsJsonArray();
                } else if (root != null && root.isJsonObject() && root.getAsJsonObject().has("rewards")) {
                    array = root.getAsJsonObject().getAsJsonArray("rewards");
                } else {
                    return List.of();
                }

                List<RewardRule> output = new ArrayList<>();
                for (JsonElement element : array) {
                    if (element != null && element.isJsonObject()) {
                        RewardRule rule = parseRule(element.getAsJsonObject());
                        if (rule != null) {
                            output.add(rule);
                        }
                    }
                }
                StardewCraft.LOGGER.info("Loaded {} NPC friendship reward rules", output.size());
                return List.copyOf(output);
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.warn("Failed to load NPC friendship rewards: {}", e.getMessage());
            return List.of();
        }
    }

    private static RewardRule parseRule(JsonObject obj) {
        String npcId = getString(obj, "npc", "npc_id");
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        String normalizedNpc = npcId.trim().toLowerCase(Locale.ROOT);
        int points = obj.has("points") ? Math.max(0, obj.get("points").getAsInt()) : Math.max(0, getInt(obj, "hearts", 0) * NpcInteractionService.POINTS_PER_HEART);
        if (points <= 0) {
            return null;
        }
        String id = getString(obj, "id");
        String flag = getString(obj, "flag", "onceFlag", "once_flag");
        if (flag == null || flag.isBlank()) {
            flag = "friendship_reward:" + normalizedNpc + ":" + points + (id == null || id.isBlank() ? "" : ":" + id.trim().toLowerCase(Locale.ROOT));
        }
        return new RewardRule(
                normalizedNpc,
                points,
                flag,
                readStrings(obj, "flags", "setFlags", "set_flags"),
                readStrings(obj, "mailNow", "mail_now", "mail"),
                readStrings(obj, "mailForTomorrow", "mail_for_tomorrow", "mailTomorrow", "mail_tomorrow"),
                readStrings(obj, "recipes"),
                readStrings(obj, "unlockSources", "unlock_sources")
        );
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String getString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                String value = obj.get(key).getAsString();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static List<String> readStrings(JsonObject obj, String... keys) {
        List<String> output = new ArrayList<>();
        for (String key : keys) {
            if (!obj.has(key)) {
                continue;
            }
            JsonElement value = obj.get(key);
            if (value.isJsonPrimitive()) {
                addString(output, value.getAsString());
            } else if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive()) {
                        addString(output, element.getAsString());
                    }
                }
            }
        }
        return List.copyOf(output);
    }

    private static void addString(List<String> output, String value) {
        if (value != null && !value.isBlank()) {
            output.add(value.trim());
        }
    }

    private record RewardRule(
            String npcId,
            int points,
            String flag,
            List<String> flags,
            List<String> mailNow,
            List<String> mailForTomorrow,
            List<String> recipes,
            List<String> unlockSources
    ) {
    }
}
