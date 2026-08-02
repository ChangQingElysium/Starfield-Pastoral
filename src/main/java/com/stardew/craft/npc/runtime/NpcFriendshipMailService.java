package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.data.VanillaObjectCatalog;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Source-exact daily friendship gift letters from {@code Game1._newDayAfterFade}.
 *
 * <p>Vanilla selects one random friendship entry, then rolls
 * {@code floor(points / 250) * 10%}. It does not first filter the list to NPCs
 * who have a letter, so the selection step intentionally includes every
 * registered NPC profile.</p>
 */
public final class NpcFriendshipMailService {
    private static final String DATA_PATH =
            "/data/stardewcraft/npc/friendship_gift_mail.json";
    private static final Map<String, GiftMailRule> BY_NPC = loadRules();
    private static final Map<String, GiftMailRule> BY_MAIL = indexByMail(BY_NPC);

    private NpcFriendshipMailService() {
    }

    public static void onNewDay(ServerPlayer player) {
        if (player == null) return;
        clearDailyReceivedFlags(player);

        List<String> population = new ArrayList<>(
                NpcDataRegistry.capabilities().keySet());
        if (population.isEmpty()) return;

        NpcFriendshipDataManager friendships =
                NpcFriendshipDataManager.get(player.serverLevel());
        String selectedNpc = selectDailyNpc(
                population,
                npcId -> friendships.getPointsForNpc(
                        player.getUUID(), npcId),
                player.getRandom());
        if (selectedNpc == null) return;

        GiftMailRule rule = BY_NPC.get(selectedNpc);
        if (rule == null || !MailRegistry.contains(rule.mailId())) return;
        MailService.addRecurringFriendshipMail(player, rule.mailId());
    }

    private static void clearDailyReceivedFlags(ServerPlayer player) {
        PlayerStardewData data =
                PlayerDataManager.getPlayerData(player);
        boolean changed = false;
        for (GiftMailRule rule : BY_NPC.values()) {
            if (data.hasMailFlag(rule.mailId())) {
                data.removeMailFlag(rule.mailId());
                changed = true;
            }
        }
        if (changed) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    static String selectDailyNpc(
            List<String> population,
            ToIntFunction<String> friendshipPoints,
            RandomSource random
    ) {
        if (population == null || population.isEmpty()
                || friendshipPoints == null || random == null) {
            return null;
        }
        String selected = population.get(random.nextInt(population.size()));
        int hearts = Math.max(0, friendshipPoints.applyAsInt(selected)) / 250;
        return random.nextDouble() < hearts * 0.1D
                ? selected.toLowerCase(Locale.ROOT)
                : null;
    }

    /**
     * Resolve the vanilla {@code %item} payload when the letter is opened.
     * The choice is intentionally made at open time, matching LetterViewerMenu.
     */
    public static DynamicReward rollReward(String mailId, RandomSource random) {
        if (mailId == null || random == null) return DynamicReward.EMPTY;
        GiftMailRule rule = BY_MAIL.get(mailId.toLowerCase(Locale.ROOT));
        if (rule == null) return DynamicReward.EMPTY;

        if (!rule.objectChoices().isEmpty()) {
            ObjectChoice choice = rule.objectChoices().get(
                    random.nextInt(rule.objectChoices().size()));
            ItemStack stack = VanillaObjectCatalog.stackFor(
                    VanillaObjectCatalog.entryByKey(choice.objectKey()));
            if (stack.isEmpty()) {
                StardewCraft.LOGGER.warn(
                        "Friendship mail '{}' selected unresolved vanilla object {}",
                        mailId, choice.objectKey());
                return DynamicReward.EMPTY;
            }
            stack.setCount(choice.count());
            return new DynamicReward(stack, 0);
        }

        int money = rule.moneyMaxExclusive() > rule.moneyMin()
                ? rule.moneyMin() + random.nextInt(
                        rule.moneyMaxExclusive() - rule.moneyMin())
                : 0;
        return new DynamicReward(ItemStack.EMPTY, money);
    }

    public record DynamicReward(ItemStack item, int money) {
        private static final DynamicReward EMPTY =
                new DynamicReward(ItemStack.EMPTY, 0);
    }

    private static Map<String, GiftMailRule> loadRules() {
        try (InputStream in =
                     NpcFriendshipMailService.class.getResourceAsStream(
                             DATA_PATH)) {
            if (in == null) return Map.of();
            JsonArray array = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            Map<String, GiftMailRule> rules = new LinkedHashMap<>();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                String npcId = object.get("npc").getAsString()
                        .toLowerCase(Locale.ROOT);
                String mailId = object.get("mail").getAsString();
                List<ObjectChoice> choices = new ArrayList<>();
                if (object.has("object_choices")) {
                    for (JsonElement choiceElement
                            : object.getAsJsonArray("object_choices")) {
                        JsonObject choice = choiceElement.getAsJsonObject();
                        choices.add(new ObjectChoice(
                                choice.get("object").getAsString(),
                                choice.has("count")
                                        ? Math.max(1,
                                        choice.get("count").getAsInt())
                                        : 1));
                    }
                }
                GiftMailRule previous = rules.put(npcId, new GiftMailRule(
                        mailId,
                        List.copyOf(choices),
                        object.has("money_min")
                                ? Math.max(0,
                                object.get("money_min").getAsInt())
                                : 0,
                        object.has("money_max_exclusive")
                                ? Math.max(0,
                                object.get("money_max_exclusive").getAsInt())
                                : 0));
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate friendship mail NPC " + npcId);
                }
            }
            return Map.copyOf(rules);
        } catch (Exception exception) {
            StardewCraft.LOGGER.error(
                    "Failed to load vanilla friendship gift mail", exception);
            return Map.of();
        }
    }

    private static Map<String, GiftMailRule> indexByMail(
            Map<String, GiftMailRule> byNpc
    ) {
        Map<String, GiftMailRule> result = new LinkedHashMap<>();
        for (GiftMailRule rule : byNpc.values()) {
            result.put(rule.mailId().toLowerCase(Locale.ROOT), rule);
        }
        return Map.copyOf(result);
    }

    private record ObjectChoice(String objectKey, int count) {
    }

    private record GiftMailRule(
            String mailId,
            List<ObjectChoice> objectChoices,
            int moneyMin,
            int moneyMaxExclusive
    ) {
    }
}
