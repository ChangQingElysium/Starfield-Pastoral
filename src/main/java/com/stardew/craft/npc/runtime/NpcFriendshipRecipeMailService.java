package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Vanilla {@code Stats.checkForCookingAchievements} friendship-recipe mail.
 *
 * <p>The source walks CookingRecipes in source order and uses one shared
 * {@code NpcCooking} pending marker, so at most one recipe letter per NPC can
 * be queued on a given day. This implementation keeps the visible per-recipe
 * mail definitions, but preserves that exact ordering and pending behavior.</p>
 */
public final class NpcFriendshipRecipeMailService {
    private static final String DATA_PATH =
            "/data/stardewcraft/npc/friendship_recipe_mail.json";
    private static final List<RecipeMailRule> RULES = loadRules();

    private NpcFriendshipRecipeMailService() {
    }

    public static void onNewDay(ServerPlayer player) {
        if (player == null) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        clearDailyReceivedFlags(player, data);
        NpcFriendshipDataManager friendships =
                NpcFriendshipDataManager.get(player.serverLevel());

        List<RecipeMailRule> selected = selectDailyLetters(
                RULES,
                npcId -> friendships.getPointsForNpc(
                        player.getUUID(), npcId),
                data::isRecipeUnlocked,
                mailId -> data.getMailbox().contains(mailId)
                        || data.getMailForTomorrow().contains(mailId));
        for (RecipeMailRule rule : selected) {
            if (MailRegistry.contains(rule.mailId())) {
                MailService.addMailForTomorrow(
                        player, rule.mailId());
            }
        }
    }

    private static void clearDailyReceivedFlags(
            ServerPlayer player, PlayerStardewData data
    ) {
        boolean changed = false;
        for (RecipeMailRule rule : RULES) {
            if (data.hasMailFlag(rule.mailId())) {
                data.removeMailFlag(rule.mailId());
                changed = true;
            }
        }
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    static List<RecipeMailRule> selectDailyLetters(
            List<RecipeMailRule> rules,
            ToIntFunction<String> friendshipPoints,
            Predicate<String> recipeUnlocked,
            Predicate<String> mailPending
    ) {
        Set<String> blockedNpcs = new HashSet<>();
        for (RecipeMailRule rule : rules) {
            if (mailPending.test(rule.mailId())) {
                blockedNpcs.add(rule.npcId());
            }
        }

        List<RecipeMailRule> selected = new ArrayList<>();
        for (RecipeMailRule rule : rules) {
            if (blockedNpcs.contains(rule.npcId())
                    || friendshipPoints.applyAsInt(rule.npcId())
                    < rule.hearts() * 250
                    || recipeUnlocked.test(rule.recipeId())) {
                continue;
            }
            selected.add(rule);
            blockedNpcs.add(rule.npcId());
        }
        return List.copyOf(selected);
    }

    private static List<RecipeMailRule> loadRules() {
        try (InputStream in =
                     NpcFriendshipRecipeMailService.class
                             .getResourceAsStream(DATA_PATH)) {
            if (in == null) return List.of();
            JsonArray array = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            List<RecipeMailRule> rules = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                rules.add(new RecipeMailRule(
                        object.get("npc").getAsString()
                                .toLowerCase(Locale.ROOT),
                        Math.max(1, object.get("hearts").getAsInt()),
                        object.get("recipe").getAsString(),
                        object.get("mail").getAsString()));
            }
            return List.copyOf(rules);
        } catch (Exception exception) {
            StardewCraft.LOGGER.error(
                    "Failed to load friendship recipe mail rules",
                    exception);
            return List.of();
        }
    }

    record RecipeMailRule(
            String npcId,
            int hearts,
            String recipeId,
            String mailId
    ) {
    }
}
