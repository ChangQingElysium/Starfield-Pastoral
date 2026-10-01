package com.stardew.craft.block.utility;

import com.stardew.craft.animal.runtime.FarmFeed;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RuntimeSiloManagerBlock extends ResidenceManagerBlock {
    public RuntimeSiloManagerBlock(Properties properties) { super(properties, "stardewcraft:block/silo_manager"); }
    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.HAY.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel server) {
            var home = FarmFeed.home(server, pos);
            if (home != null && BuildingService.canManage(serverPlayer, home)) {
                UtilityBuildings.refresh(server, home);
                home = BuildingWorldData.get(server.getServer()).find(home.id());
                int stored = home.phase() == BuildingRecord.Phase.READY && home.residence() == BuildingRecord.Residence.VALID
                        ? FarmFeed.store(server.getServer(), home.farmId(), stack.getCount()) : 0;
                stack.shrink(stored); BuildingManagerInteraction.open(serverPlayer, pos);
            } else BuildingPlacementService.message(serverPlayer, "permission");
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
