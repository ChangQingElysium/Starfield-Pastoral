package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
@SuppressWarnings("null")
public class OfficeStoolBlockEntity extends BlockEntity {
    private static final String TAG_TOP_YAW = "TopYaw";
    private static final float SYNC_THRESHOLD_DEGREES = 1.0F;

    private float topYawDegrees = 180.0F;
    private float clientRenderYawDegrees = 180.0F;
    private boolean clientRenderYawInitialized = false;

    public OfficeStoolBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OFFICE_STOOL.get(), pos, state);
    }

    public float getTopYawDegrees() {
        return topYawDegrees;
    }

    public void setTopYawDegrees(float yawDegrees) {
        float normalized = normalizeYaw(yawDegrees);
        if (Math.abs(normalized - topYawDegrees) < SYNC_THRESHOLD_DEGREES) {
            return;
        }
        topYawDegrees = normalized;
        setChanged();

        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    public float getSmoothedClientRenderYaw(float targetYawDegrees) {
        float normalizedTarget = normalizeYaw(targetYawDegrees);
        var currentLevel = getLevel();
        if (currentLevel == null || !currentLevel.isClientSide) {
            return normalizedTarget;
        }

        if (!clientRenderYawInitialized) {
            clientRenderYawDegrees = normalizedTarget;
            clientRenderYawInitialized = true;
            return clientRenderYawDegrees;
        }

        float delta = Mth.wrapDegrees(normalizedTarget - clientRenderYawDegrees);
        // Clamp per-frame angular velocity to avoid visible snapping from network/tick updates.
        float maxStep = 12.0F;
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }

        clientRenderYawDegrees = normalizeYaw(clientRenderYawDegrees + delta);
        return clientRenderYawDegrees;
    }

    private static float normalizeYaw(float yawDegrees) {
        float wrapped = yawDegrees % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putFloat(TAG_TOP_YAW, topYawDegrees);
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        if (tag.contains(TAG_TOP_YAW, Tag.TAG_FLOAT)) {
            topYawDegrees = normalizeYaw(tag.getFloat(TAG_TOP_YAW));
        } else {
            topYawDegrees = 180.0F;
        }
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
