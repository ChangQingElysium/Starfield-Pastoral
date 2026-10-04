package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class WizardBuildingCatalogBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements AnimatedModel {

    public WizardBuildingCatalogBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIZARD_BUILDING_CATALOG.get(), pos, state);
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0D);
    }
}
