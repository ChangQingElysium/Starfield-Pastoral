package com.stardew.craft.item;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.function.Supplier;

/** Keeps permanent power flags, special-item state, and their physical keepsakes in sync. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PowerSpecialItemService {
    public static final String FOREST_MAGIC_ID = "stardewcraft:forest_magic";
    public static final String CLUB_CARD_ID = "stardewcraft:club_card";
    public static final String DARK_TALISMAN_ID = "stardewcraft:dark_talisman";
    public static final String MAGIC_INK_ID = "stardewcraft:magic_ink";
    public static final String SPRING_ONION_MASTERY_ID = "stardewcraft:spring_onion_mastery";
    public static final String KEY_TO_THE_TOWN_ID = "stardewcraft:key_to_the_town";

    public static final String FOREST_MAGIC_FLAG = "canReadJunimoText";
    public static final String CLUB_CARD_FLAG = "HasClubCard";
    public static final String DARK_TALISMAN_FLAG = "HasDarkTalisman";
    public static final String MAGIC_INK_FLAG = "HasMagicInk";
    public static final String SPRING_ONION_MASTERY_FLAG = "HasSpringOnionMastery";
    public static final String KEY_TO_THE_TOWN_FLAG = "HasTownKey";

    private PowerSpecialItemService() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            backfillUnlockedItems(player);
        }
    }

    public static void grantFromItem(ServerPlayer player, PowerSpecialItem item) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        boolean changed = false;
        if (!data.hasMailFlag(item.mailFlag())) {
            data.addMailFlag(item.mailFlag());
            changed = true;
        }
        if (!data.hasSpecialItem(item.specialItemId())) {
            data.addSpecialItem(item.specialItemId());
            changed = true;
        }
        if (changed) {
            saveAndSync(player, data);
        }
    }

    static void backfillUnlockedItems(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        boolean changed = false;
        for (Definition definition : definitions()) {
            if (!data.hasMailFlag(definition.mailFlag()) || data.hasSpecialItem(definition.specialItemId())) {
                continue;
            }
            data.addSpecialItem(definition.specialItemId());
            Item item = definition.item().get();
            if (!player.getInventory().contains(new ItemStack(item))) {
                ItemStack reward = new ItemStack(item);
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
                player.getInventory().setChanged();
            }
            changed = true;
        }
        if (changed) {
            saveAndSync(player, data);
        }
    }

    private static List<Definition> definitions() {
        return List.of(
                new Definition(FOREST_MAGIC_FLAG, FOREST_MAGIC_ID, ModItems.FOREST_MAGIC),
                new Definition(CLUB_CARD_FLAG, CLUB_CARD_ID, ModItems.CLUB_CARD),
                new Definition(DARK_TALISMAN_FLAG, DARK_TALISMAN_ID, ModItems.DARK_TALISMAN),
                new Definition(MAGIC_INK_FLAG, MAGIC_INK_ID, ModItems.MAGIC_INK),
                new Definition(SPRING_ONION_MASTERY_FLAG, SPRING_ONION_MASTERY_ID, ModItems.SPRING_ONION_MASTERY),
                new Definition(KEY_TO_THE_TOWN_FLAG, KEY_TO_THE_TOWN_ID, ModItems.KEY_TO_THE_TOWN)
        );
    }

    private static void saveAndSync(ServerPlayer player, PlayerStardewData data) {
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    private record Definition(String mailFlag, String specialItemId, Supplier<? extends Item> item) {
    }
}
