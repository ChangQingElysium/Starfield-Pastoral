package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.payload.PlaySecretNote21BushEventPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source-parity state and playback for vanilla secret note 21's midnight bush event. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNote21Service {
    public static final String NOTE_ID = "stardewcraft:21";
    public static final String DONE_FLAG = "secretNote21_done";
    /** Project time is minutes since 00:00 and continues past midnight: 24:40 = 1480. */
    public static final int TRIGGER_TIME = 24 * 60 + 40;
    public static final BlockPos ACTOR_ORIGIN = new BlockPos(35, 64, 52);

    private SecretNote21Service() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !isTargetBush(event.getPos())
                || !player.serverLevel().getBlockState(event.getPos()).is(ModBlocks.BERRY_BUSH.get())) {
            return;
        }

        if (trigger(player, ACTOR_ORIGIN, StardewTimeManager.get().getCurrentTime())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static boolean isTargetBush(BlockPos pos) {
        return pos != null
                && pos.getX() >= 34 && pos.getX() <= 36
                && pos.getY() >= 64 && pos.getY() <= 65
                && pos.getZ() >= 51 && pos.getZ() <= 53;
    }

    public static boolean canTrigger(PlayerStardewData data, int currentTime) {
        return data != null
                && data.hasSeenSecretNote(NOTE_ID)
                && !data.hasMailFlag(DONE_FLAG)
                && currentTime == TRIGGER_TIME;
    }

    public static boolean canTrigger(
            ServerPlayer player,
            BlockPos pos
    ) {
        return player != null
                && ModDimensions.STARDEW_VALLEY.equals(
                        player.serverLevel().dimension())
                && isTargetBush(pos)
                && player.serverLevel().getBlockState(pos)
                        .is(ModBlocks.BERRY_BUSH.get())
                && canTrigger(
                        PlayerDataManager.getPlayerData(player),
                        StardewTimeManager.get().getCurrentTime());
    }

    /**
     * Starts the lightweight two-actor world event without entering the cutscene system.
     * The caller owns validation of the authored target bush and supplies its actor origin.
     */
    public static boolean trigger(ServerPlayer player, BlockPos actorOrigin, int currentTime) {
        if (player == null || actorOrigin == null
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return false;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!canTrigger(data, currentTime)) {
            return false;
        }

        data.addMailFlag(DONE_FLAG);
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
        PacketDistributor.sendToPlayer(player, new PlaySecretNote21BushEventPayload(actorOrigin));
        return true;
    }

    /** Plays only the visual sequence, without changing secret-note progress. */
    public static boolean debugPlay(ServerPlayer player) {
        if (player == null
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new PlaySecretNote21BushEventPayload(ACTOR_ORIGIN));
        return true;
    }
}
