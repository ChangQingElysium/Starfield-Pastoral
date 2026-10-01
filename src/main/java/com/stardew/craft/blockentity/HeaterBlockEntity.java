package com.stardew.craft.blockentity;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.block.utility.HeaterBlock;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public class HeaterBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    private static final String TAG_BUILDING_ID = "buildingId";
    private static final int CHECK_INTERVAL_TICKS = 20;

    private String buildingId = "";
    private boolean working;
    private int clientWorkingTicks;

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER.get(), pos, state);
    }

    @SuppressWarnings("null")
    public static void serverTick(Level level, BlockPos pos, BlockState state, HeaterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        be.refreshWorkingState(serverLevel, pos, state);
    }

    @SuppressWarnings("null")
    public static void clientTick(Level level, BlockPos pos, BlockState state, HeaterBlockEntity be) {
        boolean nowWorking = state.hasProperty(HeaterBlock.WORKING) && state.getValue(HeaterBlock.WORKING);
        if (nowWorking) {
            be.clientWorkingTicks++;
        } else {
            be.clientWorkingTicks = 0;
        }
    }

    public boolean isWorking() {
        return working;
    }

    public float getClientWorkingAnimationTicks(float partialTick) {
        return clientWorkingTicks + partialTick;
    }

    @SuppressWarnings("null")
    private void refreshWorkingState(ServerLevel level, BlockPos pos, BlockState state) {
        AnimalBuildingRecord building = resolveSupportedBuilding(level, pos);
        boolean isWinter = StardewTimeManager.get().getCurrentSeason() == 3;
        boolean workingNow = building != null && isWinter;

        if (working != workingNow) {
            working = workingNow;
            setChanged();
        }

        if (state.hasProperty(HeaterBlock.WORKING) && state.getValue(HeaterBlock.WORKING) != workingNow) {
            level.setBlock(pos, state.setValue(HeaterBlock.WORKING, workingNow), 3);
        }
    }

    private AnimalBuildingRecord resolveSupportedBuilding(ServerLevel level, BlockPos pos) {
        AnimalWorldData data = AnimalWorldData.get(level);
        if (!buildingId.isBlank()) {
            AnimalBuildingRecord existing = data.getBuilding(buildingId).orElse(null);
            if (existing != null
                && Objects.equals(existing.dimensionId(), level.dimension().location().toString())
                && existing.isWithinBoundingBox(pos)
                && isSupportedFamily(existing)) {
                return existing;
            }
        }

        for (AnimalBuildingRecord candidate : data.getBuildings()) {
            if (!Objects.equals(candidate.dimensionId(), level.dimension().location().toString())) {
                continue;
            }
            if (!candidate.isWithinBoundingBox(pos) || !isSupportedFamily(candidate)) {
                continue;
            }
            if (!Objects.equals(buildingId, candidate.buildingId())) {
                buildingId = candidate.buildingId();
                setChanged();
            }
            return candidate;
        }

        if (!buildingId.isBlank()) {
            buildingId = "";
            setChanged();
        }
        return null;
    }

    private boolean isSupportedFamily(AnimalBuildingRecord building) {
        String family = building.buildingType().family();
        return "coop".equalsIgnoreCase(family) || "barn".equalsIgnoreCase(family);
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
}