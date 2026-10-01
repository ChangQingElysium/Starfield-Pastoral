package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.AutoPetterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Objects;

public class AutoPetterBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements GeoBlockEntity {
    private static final String TAG_BUILDING_ID = "buildingId";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final int CHECK_INTERVAL_TICKS = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String buildingId = "";
    private boolean working;

    public AutoPetterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_PETTER.get(), pos, state);
    }

    @SuppressWarnings("null")
    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoPetterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        be.refreshWorkingState(serverLevel, pos, state);
    }

    public boolean isWorking() {
        return working;
    }

    @SuppressWarnings("null")
    private void refreshWorkingState(ServerLevel level, BlockPos pos, BlockState state) {
        var building = resolveSupportedBuilding(level, pos);
        boolean workingNow = building != null;
        if (working != workingNow) {
            working = workingNow;
            setChanged();
        }

        if (state.hasProperty(AutoPetterBlock.WORKING) && state.getValue(AutoPetterBlock.WORKING) != workingNow) {
            level.setBlock(pos, state.setValue(AutoPetterBlock.WORKING, workingNow), 3);
        }
    }

    private com.stardew.craft.building.runtime.BuildingRecord resolveSupportedBuilding(ServerLevel level, BlockPos pos) {
        var home = com.stardew.craft.animal.runtime.FarmFeed.home(level, pos);
        return com.stardew.craft.animal.runtime.LivestockHomes.accepts(home) ? home : null;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!buildingId.isBlank()) {
            tag.putString(TAG_BUILDING_ID, buildingId);
        }
        tag.putBoolean("working", working);
    }

    @SuppressWarnings("null")
    @Override
    public void load(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        buildingId = tag.getString(TAG_BUILDING_ID);
        working = tag.getBoolean("working");
    }

    @SuppressWarnings("null")
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            BlockState blockState = getBlockState();
            boolean shouldAnimate = blockState.hasProperty(AutoPetterBlock.WORKING) && blockState.getValue(AutoPetterBlock.WORKING);
            if (shouldAnimate) {
                state.setAndContinue(IDLE);
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
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