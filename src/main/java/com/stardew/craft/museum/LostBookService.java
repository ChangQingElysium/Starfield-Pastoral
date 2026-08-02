package com.stardew.craft.museum;

import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.museum.StardewLostBookDefinition;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.network.payload.OpenMailPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.network.ObjectDialogueService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Discovery and per-player reading behavior for the museum library. */
public final class LostBookService {
    public static final String FIRST_BOOK_FLAG = "lostBookFound";

    private LostBookService() {
    }

    public static int foundCount(ServerPlayer player) {
        return LostBookWorldData.get(player.server).foundCount();
    }

    public static boolean canFindAnother(ServerPlayer player) {
        return foundCount(player) < LostBookRegistry.discoveryMaximum();
    }

    /** Consumes one used object-102 stack only after museum progress advances. */
    public static boolean receive(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || !stack.is(ModItems.LOST_BOOK.get())) {
            return false;
        }
        boolean found = find(player);
        if (!found) {
            return false;
        }
        stack.shrink(1);
        player.getInventory().setChanged();
        return true;
    }

    /** Records one received lost book in the world-shared library counter. */
    public static boolean find(ServerPlayer player) {
        if (player == null) return false;
        LostBookWorldData worldData = LostBookWorldData.get(player.server);
        if (!worldData.discoverNext(LostBookRegistry.discoveryMaximum())) return false;

        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        boolean firstForPlayer = !playerData.hasMailFlag(FIRST_BOOK_FLAG)
                && !playerData.hasMailFlagForTomorrow(FIRST_BOOK_FLAG);
        if (firstForPlayer) {
            com.stardew.craft.mail.MailService.addMailFlagForTomorrow(player, FIRST_BOOK_FLAG);
        }
        playerData.incrementStat("NotesFound", 1);
        ItemStack display = new ItemStack(ModItems.LOST_BOOK.get());
        ItemPickupHudPacket.sendTo(player, display, 1, false);
        player.playNotifySound(ModSounds.NEW_ARTIFACT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (firstForPlayer) {
            HoldUpItemPayload.sendTo(player, display);
        } else {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                    player, Component.translatable("stardewcraft.lost_book.found"));
        }
        player.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("stardewcraft.lost_book.chat", player.getDisplayName()), false);
        syncAllOnlinePlayers(player);
        return true;
    }

    public static boolean read(ServerPlayer player, ResourceLocation bookId) {
        StardewLostBookDefinition definition = LostBookRegistry.get(bookId);
        if (definition == null) return false;
        if (foundCount(player) < definition.unlockAt() || !isAvailable(player, definition)) {
            ObjectDialogueService.show(player, "stardewcraft.lost_book.missing");
            return true;
        }

        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        String flag = readFlag(bookId);
        if (!playerData.hasMailFlag(flag)) {
            playerData.addMailFlag(flag);
            PlayerDataEventHandler.syncPlayerData(player, playerData);
        }
        PacketDistributor.sendToPlayer(player, new OpenMailPayload(
                "lost_book:" + bookId,
                definition.text(),
                "",
                0,
                "",
                List.of(),
                0,
                "",
                "",
                false,
                0
        ));
        return true;
    }

    public static String readFlag(ResourceLocation bookId) {
        if ("stardewcraft".equals(bookId.getNamespace()) && bookId.getPath().matches("book_\\d+")) {
            return "lb_" + Integer.parseInt(bookId.getPath().substring("book_".length()));
        }
        return "lost_book_read:" + bookId;
    }

    private static boolean isAvailable(ServerPlayer player, StardewLostBookDefinition definition) {
        StardewConditionContext context = StardewConditionContext.forPlayer(player);
        return definition.availableWhen().stream().allMatch(condition ->
                StardewConditions.test(condition, context).result().orElse(false));
    }

    private static void syncAllOnlinePlayers(ServerPlayer finder) {
        for (ServerPlayer online : finder.server.getPlayerList().getPlayers()) {
            PlayerStardewData data = PlayerDataManager.getPlayerData(online);
            PlayerDataEventHandler.syncPlayerData(online, data);
        }
    }
}
