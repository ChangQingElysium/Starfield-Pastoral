package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import com.stardew.craft.block.ModBlocks;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LuauFestivalDecorBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements GeoBlockEntity {
    private boolean footprintChecked;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LuauFestivalDecorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LUAU_FESTIVAL_DECOR.get(), pos, state);
    }

    public void repairCauldronFootprint() {
        if (footprintChecked || level == null || level.isClientSide) return;
        if (!level.hasChunksAt(worldPosition.offset(-4, 0, -4), worldPosition.offset(4, 2, 4))) return;
        // Existing buildings are never overwritten. Try once after the surrounding chunks are ready.
        footprintChecked = true;
        if (getBlockState().getBlock() instanceof com.stardew.craft.block.decor.MapDecorStaticBlock block) {
            block.placeExtensions(level, worldPosition, getBlockState());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String name = getBlockState().is(ModBlocks.WIZARD_CAULDRON.get()) ? "wizard_cauldron"
                : getBlockState().is(ModBlocks.LUAU_SOUP_POT.get()) ? "luau_soup_pot" : null;
        if (name != null) {
            RawAnimation simmer = RawAnimation.begin().thenLoop("animation." + name + ".simmer");
            controllers.add(new AnimationController<>(this, "simmer", 0, state -> state.setAndContinue(simmer)));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(5.0);
    }
}
