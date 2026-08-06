package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.cooking.CookingPlacedFoodBlock;
import com.stardew.craft.block.utility.OakRoundTableBlock;
import com.stardew.craft.block.utility.OakTableBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FestivalInteractionLockEvents {
    private FestivalInteractionLockEvents() {
    }

    private static boolean locked(Player player) {
        return player instanceof ServerPlayer serverPlayer
            && ActiveFestivalHandlers.getParticipating(serverPlayer)
                .map(ActiveFestivalHandler::blocksNpcInteractionDuringMainEvent)
                .orElse(false);
    }

    private static boolean fairWorldLocked(Player player) {
        return player instanceof ServerPlayer serverPlayer
            && FairFestivalService.isParticipant(serverPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event.getEntity()) || fairWorldLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (rejectBlockedFestivalFishing(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            return;
        }
        if (isAuthorizedFestivalFishingRodUse(event.getEntity(), event.getItemStack())) {
            return;
        }
        if (fairWorldLocked(event.getEntity())
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CookingPlacedFoodBlock) {
            event.setCanceled(true);
            return;
        }
        net.minecraft.world.level.block.Block clickedBlock = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (fairWorldLocked(event.getEntity())
                && (clickedBlock instanceof OakTableBlock || clickedBlock instanceof OakRoundTableBlock)
                && !FairFestivalService.isPlayerGrangeDisplayTable(event.getPos())) {
            event.setCanceled(true);
            return;
        }
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (rejectBlockedFestivalFishing(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            return;
        }
        if (isAuthorizedFestivalFishingRodUse(event.getEntity(), event.getItemStack())) {
            return;
        }
        if (locked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (locked(event.getEntity()) || fairWorldLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (locked(event.getPlayer()) || fairWorldLocked(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && (locked(player) || fairWorldLocked(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getEntity() instanceof Player player && (locked(player) || fairWorldLocked(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof Player player && (locked(player) || fairWorldLocked(player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)
                || !ActiveFestivalHandlers.blocksFishingDuringActiveFestival(serverPlayer)) {
            return;
        }
        com.stardew.craft.fishing.server.FishingSessionManager.get(serverPlayer.server).cancel(serverPlayer);
        if (serverPlayer.fishing != null && serverPlayer.fishing.isAlive()) {
            serverPlayer.fishing.discard();
        }
    }

    private static boolean rejectBlockedFestivalFishing(Player player, net.minecraft.world.item.ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || stack == null
                || !(stack.getItem() instanceof net.minecraft.world.item.FishingRodItem)
                || !ActiveFestivalHandlers.isParticipant(serverPlayer)
                || isAuthorizedFestivalFishingRodUse(serverPlayer, stack)) {
            return false;
        }
        serverPlayer.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("stardewcraft.fishing.blocked_during_festival"),
            true
        );
        return true;
    }

    private static boolean isAuthorizedFestivalFishingRodUse(Player player, net.minecraft.world.item.ItemStack stack) {
        return player instanceof ServerPlayer serverPlayer
            && stack != null
            && stack.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem
            && ((FestivalOfIceService.isFishingContestActive(serverPlayer)
                    && FestivalOfIceService.canStartFishingCast(serverPlayer)
                    && FestivalOfIceService.isUsableFishingContestRod(serverPlayer, stack))
                || (com.stardew.craft.festival.fair.FairFishingGameService.isFishingGameActive(serverPlayer)
                    && com.stardew.craft.festival.fair.FairFishingGameService.isUsableFishingGameRod(serverPlayer, stack)));
    }
}
