package com.stardew.craft.qi;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.PowerSpecialItem;
import com.stardew.craft.item.PowerSpecialItemService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.StardewQuest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side application service for the original Mr. Qi scavenger hunt.
 *
 * <p>This class deliberately contains no positions, blocks, models, NPCs, or cutscene behavior.
 * Authored world interactions call {@link #interact(ServerPlayer, MrQiQuestAnchor)} later.</p>
 */
public final class MrQiQuestService {
    private static final Set<String> TRACKED_FLAGS = Set.of(
            MrQiQuestRules.TUNNEL_FLAG,
            MrQiQuestRules.RAILROAD_FLAG,
            MrQiQuestRules.MAYOR_FRIDGE_FLAG,
            MrQiQuestRules.SAND_DRAGON_FLAG,
            MrQiQuestRules.LUMBER_PILE_FLAG
    );

    private static final Set<String> TRACKED_INVENTORY_ITEMS = Set.of(
            MrQiQuestRules.BATTERY_PACK_ID,
            MrQiQuestRules.RAINBOW_SHELL_ID,
            MrQiQuestRules.BEET_ID,
            MrQiQuestRules.SOLAR_ESSENCE_ID
    );

    private MrQiQuestService() {
    }

    public static MrQiQuestRules.Decision preview(ServerPlayer player, MrQiQuestAnchor anchor) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return MrQiQuestRules.evaluate(anchor, snapshot(player, data));
    }

    public static MrQiQuestRules.Decision interact(ServerPlayer player, MrQiQuestAnchor anchor) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        QuestManager quests = data.getQuestManager();
        MrQiQuestRules.Decision decision = MrQiQuestRules.evaluate(anchor, snapshot(player, data));
        if (decision.outcome() != MrQiQuestRules.Outcome.SUCCESS) {
            return decision;
        }
        if (!consume(player, decision.cost())) {
            return MrQiQuestRules.evaluate(anchor, snapshot(player, data));
        }

        data.addMailFlag(decision.flagToAdd());
        if (!decision.questToRemove().isEmpty()) {
            quests.removeQuest(decision.questToRemove(), player);
        }
        if (!decision.questToAdd().isEmpty()) {
            // SDV GameLocation.addQuest is unconditional here; it isn't a board/offering acceptance.
            StardewQuest nextQuest = QuestDataLoader.createQuest(decision.questToAdd());
            if (nextQuest != null) {
                quests.acceptQuest(nextQuest, player);
            }
        }
        if (decision.grantClubCard()) {
            grantClubCard(player);
        }

        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
        player.getInventory().setChanged();
        return decision;
    }

    private static MrQiQuestRules.Snapshot snapshot(ServerPlayer player, PlayerStardewData data) {
        Set<String> flags = new HashSet<>();
        for (String flag : TRACKED_FLAGS) {
            if (data.hasMailFlag(flag) || data.hasMailFlagForTomorrow(flag)) {
                flags.add(flag);
            }
        }

        ItemStack held = player.getMainHandItem();
        String heldItemId = held.isEmpty()
                ? ""
                : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

        Map<String, Integer> inventoryCounts = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (TRACKED_INVENTORY_ITEMS.contains(itemId)) {
                inventoryCounts.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
        return new MrQiQuestRules.Snapshot(flags, heldItemId, held.getCount(), inventoryCounts);
    }

    private static boolean consume(ServerPlayer player, MrQiQuestRules.ItemCost cost) {
        return switch (cost.source()) {
            case NONE -> true;
            case HELD_ITEM -> consumeHeld(player, cost.itemId(), cost.count());
            case INVENTORY -> consumeInventory(player, cost.itemId(), cost.count());
        };
    }

    private static boolean consumeHeld(ServerPlayer player, String itemId, int count) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()
                || held.getCount() < count
                || !BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(itemId)) {
            return false;
        }
        held.shrink(count);
        return true;
    }

    private static boolean consumeInventory(ServerPlayer player, String itemId, int count) {
        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()
                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                available += stack.getCount();
            }
        }
        if (available < count) {
            return false;
        }

        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.isEmpty()
                    || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return true;
    }

    private static void grantClubCard(ServerPlayer player) {
        if (player.getInventory().countItem(ModItems.CLUB_CARD.get()) == 0) {
            ItemStack reward = new ItemStack(ModItems.CLUB_CARD.get());
            if (!player.addItem(reward)) {
                player.drop(reward, false);
            }
        }
        if (ModItems.CLUB_CARD.get() instanceof PowerSpecialItem clubCard) {
            PowerSpecialItemService.grantFromItem(player, clubCard);
        }
    }
}
