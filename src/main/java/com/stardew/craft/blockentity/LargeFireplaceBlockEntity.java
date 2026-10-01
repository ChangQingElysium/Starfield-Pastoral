package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class LargeFireplaceBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements AnimatedModel {

    public LargeFireplaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LARGE_FIREPLACE.get(), pos, state);
    }

    @Override
    public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        return ModelAnimation.loop("idle");
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
