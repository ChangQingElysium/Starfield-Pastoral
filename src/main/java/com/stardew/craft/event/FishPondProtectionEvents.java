package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class FishPondProtectionEvents {
    private FishPondProtectionEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }

        if (findProtectedPond(level, event.getPos()).isPresent()) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond.protected"), true);
        }
    }

    @SubscribeEvent
    public static void onToolModify(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }

        if (findProtectedPond(level, event.getPos()).isPresent()) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond.protected"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && player.isCreative()) {
            return;
        }

        if (findProtectedPond(level, event.getPos()).isPresent()) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond.protected"), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && player.isCreative()) {
            return;
        }

        for (var snapshot : event.getReplacedBlockSnapshots()) {
            if (findProtectedPond(level, snapshot.getPos()).isPresent()) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond.protected"), true);
                }
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }

        BlockPos targetPos = event.getPos();
        BlockState targetState = level.getBlockState(targetPos);
        BlockPos placePos = targetState.canBeReplaced() ? targetPos : targetPos.relative(event.getFace());

        if (event.getItemStack().getItem() instanceof BlockItem) {
            if (findProtectedPond(level, placePos).isPresent()) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond.protected"), true);
            }
            return;
        }

        if (event.getItemStack().getItem() instanceof BucketItem bucketItem && bucketItem.content != Fluids.EMPTY) {
            if (findProtectedPond(level, placePos).isPresent()) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond.protected"), true);
            }
        }
    }

    private static Optional<FishPondRecord> findProtectedPond(ServerLevel level, BlockPos pos) {
        return FishPondWorldData.get(level).findPondProtectingPos(level.dimension().location().toString(), pos);
    }
}
