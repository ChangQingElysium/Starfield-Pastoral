package com.stardew.craft.blockentity;

import com.stardew.craft.model.AnimatedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BushBlockEntity extends BlockEntity implements AnimatedModel {
    private static final String TAG_LAST_HARVEST_ABSOLUTE_DAY = "lastHarvestAbsoluteDay";

    private int lastHarvestAbsoluteDay = Integer.MIN_VALUE;

    public BushBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUSH.get(), pos, state);
    }

    public int getLastHarvestAbsoluteDay() {
        return lastHarvestAbsoluteDay;
    }

    public void setLastHarvestAbsoluteDay(int day) {
        lastHarvestAbsoluteDay = day;
        setChanged();
        Level currentLevel = getLevel();
        if (currentLevel != null) {
            currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
            if (currentLevel instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(worldPosition);
            }
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putInt(TAG_LAST_HARVEST_ABSOLUTE_DAY, lastHarvestAbsoluteDay);
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        lastHarvestAbsoluteDay = tag.contains(TAG_LAST_HARVEST_ABSOLUTE_DAY) ? tag.getInt(TAG_LAST_HARVEST_ABSOLUTE_DAY) : Integer.MIN_VALUE;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5D);
    }
}