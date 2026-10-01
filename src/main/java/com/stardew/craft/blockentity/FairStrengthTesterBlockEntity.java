package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FairStrengthTesterBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements AnimatedModel {

    public FairStrengthTesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FAIR_STRENGTH_TESTER.get(), pos, state);
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4.0D).expandTowards(0.0D, 3.0D, 0.0D);
    }
}
