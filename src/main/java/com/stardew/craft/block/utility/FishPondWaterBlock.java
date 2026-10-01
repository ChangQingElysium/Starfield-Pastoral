package com.stardew.craft.block.utility;

import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.service.FishPondColorSyncService;
import com.stardew.craft.fishpond.service.FishPondHusbandry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import javax.annotation.Nonnull;

public class FishPondWaterBlock extends LiquidBlock {

    public FishPondWaterBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public net.minecraft.world.item.ItemStack pickupBlock(net.minecraft.world.level.LevelAccessor level, BlockPos pos,
            BlockState state) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public void entityInside(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ItemEntity itemEntity) {
            FishPondWorldData worldData = FishPondWorldData.get(serverLevel);
            worldData.findPondContainingWater(serverLevel.dimension().location().toString(), pos)
                .ifPresent(pond -> {
                    if (FishPondHusbandry.absorbItemEntity(serverLevel, pond, itemEntity).changedState()) {
                        FishPondColorSyncService.broadcastSnapshot(serverLevel);
                    }
                });
        }
        super.entityInside(state, level, pos, entity);
    }
}