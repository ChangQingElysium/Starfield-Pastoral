package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Source-parity trigger for the A Winter Mystery magnifying-glass scene. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNote31BushInteraction {
    private static final String MAGNIFYING_GLASS_EVENT_ID = "secret_note31_magnifying_glass";

    private SecretNote31BushInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!insideTargetBush(pos)) return;

        BlockState state = player.serverLevel().getBlockState(pos);
        if (!state.is(ModBlocks.SMALL_BUSH.get()) && !state.is(ModBlocks.BERRY_BUSH.get())) return;
        if (!EventSeenData.get(player.serverLevel()).hasSeen(
                player.getUUID(), SecretNote31FootprintTrail.BUS_STOP_EVENT_ID)) return;
        if (SecretNoteService.hasMagnifyingGlass(PlayerDataManager.getPlayerData(player))) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ServerCutsceneTracker.startEvent(player, MAGNIFYING_GLASS_EVENT_ID);
    }

    private static boolean insideTargetBush(BlockPos pos) {
        return pos.getX() >= -4 && pos.getX() <= -2
                && pos.getY() >= 66 && pos.getY() <= 67
                && pos.getZ() >= -66 && pos.getZ() <= -64;
    }
}
