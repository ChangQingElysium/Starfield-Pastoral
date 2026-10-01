package com.stardew.craft.block.decor;

import com.stardew.craft.network.payload.OpenPrizeTicketMachinePayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.shop.PrizeTicketRewardService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class PrizeTicketMachineBlock extends MapDecorStaticBlock {
    public PrizeTicketMachineBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/prize_ticket_machine");
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hit) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PlayerStardewData data = PlayerDataManager.getPlayerData(serverPlayer);
            int claimed = data.getTicketPrizesClaimed();
            PacketDistributor.sendToPlayer(serverPlayer, new OpenPrizeTicketMachinePayload(
                claimed,
                PrizeTicketRewardService.getPreviewRewards(serverPlayer, claimed, 4)
            ));
        }
        return InteractionResult.SUCCESS;
    }
}