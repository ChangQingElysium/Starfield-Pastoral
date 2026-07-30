package com.stardew.craft.block.utility;

import com.stardew.craft.block.decor.MapDecorWallThinBlock;
import com.stardew.craft.qi.MrQiQuestAnchor;
import com.stardew.craft.qi.MrQiQuestInteractionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The wall-mounted lock-box that starts the original Mr. Qi scavenger hunt. */
public final class MrQiTunnelSafeBlock extends MapDecorWallThinBlock {
    public MrQiTunnelSafeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MrQiQuestInteractionService.interact(serverPlayer, MrQiQuestAnchor.TUNNEL_POWER_PANEL);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        InteractionResult result = useWithoutItem(state, level, pos, player, hit);
        return switch (result) {
            case SUCCESS -> ItemInteractionResult.sidedSuccess(level.isClientSide);
            case CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.CONSUME;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }
}
