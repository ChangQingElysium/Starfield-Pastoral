package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LewisBasementEntranceEvents {
    private static final int MIN_X = 64;
    private static final int MAX_X = 72;
    private static final int MIN_Y = 51;
    private static final int MAX_Y = 58;
    private static final int MIN_Z = 19;
    private static final int MAX_Z = 27;

    private static final double TARGET_X = 67.5D;
    private static final double TARGET_Y = 44.0D;
    private static final double TARGET_Z = 20.5D;
    private static final float TARGET_YAW_SOUTH = 0.0F;

    private LewisBasementEntranceEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryEnterBasement(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryEnterBasement(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean tryEnterBasement(net.minecraft.world.entity.player.Player rawPlayer, ItemStack stack) {
        if (!(rawPlayer instanceof ServerPlayer player)) {
            return false;
        }
        if (!canEnterBasement(player, stack)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.9F, 0.85F);
        ModTeleport.to(player, level, TARGET_X, TARGET_Y, TARGET_Z, TARGET_YAW_SOUTH, 0.0F);
        LuckyPurpleShortsWorldEvents.onEnteredLewisBasement(player);
        level.playSound(null, BlockPos.containing(TARGET_X, TARGET_Y, TARGET_Z), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.9F, 0.95F);
        return true;
    }

    public static boolean canEnterBasement(
            ServerPlayer player,
            ItemStack stack
    ) {
        return player != null
                && stack != null
                && stack.is(ModItems.MINE_LADDER.get())
                && player.serverLevel().dimension()
                        == ModDimensions.STARDEW_VALLEY
                && isInLewisStaircaseArea(player.blockPosition());
    }

    private static boolean isInLewisStaircaseArea(BlockPos pos) {
        return pos.getX() >= MIN_X && pos.getX() <= MAX_X
                && pos.getY() >= MIN_Y && pos.getY() <= MAX_Y
                && pos.getZ() >= MIN_Z && pos.getZ() <= MAX_Z;
    }
}
