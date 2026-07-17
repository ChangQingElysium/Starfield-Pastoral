package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/** Source-parity state and sell-price rule unlocked by vanilla event 2120303. */
public final class SecretNote23Service {
    public static final String NOTE_ID = "stardewcraft:23";
    public static final String SPECIAL_ITEM_ID = "stardewcraft:bear_knowledge";
    public static final double BERRY_PRICE_MULTIPLIER = 3.0;

    private static final Set<ResourceLocation> KNOWLEDGE_BERRIES = Set.of(
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "salmonberry"),
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "blackberry")
    );

    private SecretNote23Service() {
    }

    public static boolean hasBearKnowledge(PlayerStardewData data) {
        return data != null && (data.hasMailFlag(SecretNoteStoryFlags.BEAR_KNOWLEDGE)
                || data.hasSpecialItem(SPECIAL_ITEM_ID));
    }

    /** Keeps legacy mail state and SDV-style special-item state in sync. */
    public static boolean grantBearKnowledge(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        boolean changed = false;
        if (!data.hasMailFlag(SecretNoteStoryFlags.BEAR_KNOWLEDGE)) {
            data.addMailFlag(SecretNoteStoryFlags.BEAR_KNOWLEDGE);
            changed = true;
        }
        if (!data.hasSpecialItem(SPECIAL_ITEM_ID)) {
            data.addSpecialItem(SPECIAL_ITEM_ID);
            changed = true;
        }
        if (changed) {
            PlayerDataManager.get().savePlayerData(player.getUUID(), data);
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        return changed;
    }

    public static double sellPriceMultiplier(boolean hasBearKnowledge, ResourceLocation itemId) {
        return hasBearKnowledge && KNOWLEDGE_BERRIES.contains(itemId)
                ? BERRY_PRICE_MULTIPLIER
                : 1.0;
    }
}
