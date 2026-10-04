package com.stardew.craft.block.decor;

import com.stardew.craft.desert.DesertBusService;
import com.stardew.craft.desert.DesertConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Both occupied cells open the existing bus confirmation, never charge on the initial click. */
public final class TicketMachineBlock extends MapDecorStaticBlock {
    public TicketMachineBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (DesertConstants.isInDesertRegion(level.dimension(), pos)
                || findMainPos(level, pos, state) == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            DesertBusService.beginBusRide(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
