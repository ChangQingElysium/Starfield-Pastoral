package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class WizardBuildingCatalogBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WizardBuildingCatalogBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIZARD_BUILDING_CATALOG.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Both catalog variants are static GeckoLib models.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0D);
    }
}
