package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class GrandfatherClockBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements GeoBlockEntity {
    private static final RawAnimation TICK_ANIMATION = RawAnimation.begin().thenLoop("animation");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GrandfatherClockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRANDFATHER_CLOCK.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, s -> {
            s.setAndContinue(TICK_ANIMATION);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
