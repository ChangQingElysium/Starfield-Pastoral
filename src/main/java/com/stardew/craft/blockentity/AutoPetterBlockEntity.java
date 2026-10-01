package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import com.stardew.craft.block.utility.AutoPetterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

public class AutoPetterBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements AnimatedModel {
    private static final String TAG_BUILDING_ID = "buildingId";
    private static final int CHECK_INTERVAL_TICKS = 20;

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
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!buildingId.isBlank()) {
            tag.putString(TAG_BUILDING_ID, buildingId);
        }
        tag.putBoolean("working", working);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buildingId = tag.getString(TAG_BUILDING_ID);
        working = tag.getBoolean("working");
    }

    @SuppressWarnings("null")
    @Override
    public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        return getBlockState().hasProperty(AutoPetterBlock.WORKING) && getBlockState().getValue(AutoPetterBlock.WORKING) ? ModelAnimation.loop("idle") : null;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}