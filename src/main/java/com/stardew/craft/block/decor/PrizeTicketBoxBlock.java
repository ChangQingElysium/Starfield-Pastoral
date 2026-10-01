package com.stardew.craft.block.decor;

import com.stardew.craft.specialorder.SpecialOrderManager;
import com.stardew.craft.specialorder.SpecialOrderTicketService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nonnull;

/** Map-installed collection box; shares the board's per-player unlock visibility. */
public final class PrizeTicketBoxBlock extends SpecialOrdersBoardBlock {
    public PrizeTicketBoxBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/special_orders/prize_ticket_box/spring",
                1, 0, 3, 15, 22, 13);
    }

    @Override
    public InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hit) {
        if (findMainPos(level, pos, state) == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            if (!SpecialOrderManager.isUnlockedFor(serverPlayer)) return InteractionResult.PASS;
            SpecialOrderTicketService.claimOne(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
