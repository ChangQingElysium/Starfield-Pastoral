package com.stardew.craft.block.utility;

import com.stardew.craft.building.runtime.BuildingManagerInteraction;
import com.stardew.craft.building.runtime.BuildingPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The ruined greenhouse keeps its manager, but only a repaired greenhouse can be moved. */
public final class GreenhouseManagerBlock extends ResidenceManagerBlock {
    public GreenhouseManagerBlock(Properties properties) {
        super(properties, "stardewcraft:block/greenhouse_manager");
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!BuildingManagerInteraction.open(serverPlayer, pos)) {
                BuildingPlacementService.message(serverPlayer,
                        com.stardew.craft.greenhouse.GreenhouseBuildings.isUnbuiltManager(
                                serverPlayer.serverLevel(), pos)
                                ? "greenhouse_not_built"
                                : "work_stale");
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
