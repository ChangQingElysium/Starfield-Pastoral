package com.stardew.craft.blockentity;

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
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BushBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String TAG_LAST_HARVEST_ABSOLUTE_DAY = "lastHarvestAbsoluteDay";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int lastHarvestAbsoluteDay = Integer.MIN_VALUE;

    public BushBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUSH.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_LAST_HARVEST_ABSOLUTE_DAY, lastHarvestAbsoluteDay);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastHarvestAbsoluteDay = tag.contains(TAG_LAST_HARVEST_ABSOLUTE_DAY) ? tag.getInt(TAG_LAST_HARVEST_ABSOLUTE_DAY) : Integer.MIN_VALUE;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(@Nonnull net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5D);
    }
}