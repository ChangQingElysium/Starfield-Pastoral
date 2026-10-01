package com.stardew.craft.desert;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

/**
 * 沙漠公交站牌。
 * <p>
 * 右键交互统一委托给 {@link DesertBusService}，弹出确认对话框后执行完整的
 * 扣钱 + 黑屏 + 音效 + 传送序列。
 */
public class DesertBusStopBlock extends Block implements PortBlockInteraction {

    public DesertBusStopBlock(Properties properties) {
        super(properties);
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    @SuppressWarnings("null")
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        DesertBusService.beginBusRide((ServerPlayer) player);
        return InteractionResult.CONSUME;
    }
}

