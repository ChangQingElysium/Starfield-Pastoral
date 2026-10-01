package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FlowerDanceDecorBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements AnimatedModel {

    public FlowerDanceDecorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOWER_DANCE_DECOR.get(), pos, state);
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0);
    }
}