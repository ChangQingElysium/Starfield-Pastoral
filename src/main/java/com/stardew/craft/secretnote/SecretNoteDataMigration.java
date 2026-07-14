package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Removes progress for source note 19, which is intentionally absent from this project. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNoteDataMigration {
    private static final String OMITTED_NOTE_ID = "stardewcraft:19";

    private SecretNoteDataMigration() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.forgetSecretNote(OMITTED_NOTE_ID)) return;
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
}
