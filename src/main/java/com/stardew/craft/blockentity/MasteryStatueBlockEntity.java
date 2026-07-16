package com.stardew.craft.blockentity;

import com.stardew.craft.block.mastery.StatueOfBlessingsBlock;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

@SuppressWarnings("null")
public class MasteryStatueBlockEntity extends BlockEntity {
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_ACTIVATED_DAY = "ActivatedDay";

    @Nullable
    private UUID ownerUUID;
    private String ownerName = "";
    private int activatedDay = -1;

    public MasteryStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MASTERY_STATUE.get(), pos, state);
    }

    public void setOwner(ServerPlayer player) {
        ownerUUID = player.getUUID();
        ownerName = com.stardew.craft.player.PlayerDisplayName.get(player);
        setChanged();
        syncToClient();
    }

    public boolean hasOwner() {
        return ownerUUID != null;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void markBlessingsActivated(int day) {
        activatedDay = day;
        setChanged();
        syncToClient();
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof StatueOfBlessingsBlock) || !state.getValue(StatueOfBlessingsBlock.ACTIVATED)) {
            return;
        }
        int currentDay = StardewTimeManager.get().getAbsoluteDay();
        if (activatedDay >= 0 && currentDay > activatedDay) {
            StatueOfBlessingsBlock.setActivated(level, worldPosition, state, false);
            activatedDay = -1;
            setChanged();
            syncToClient();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUUID != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUUID);
            tag.putString(TAG_OWNER_NAME, ownerName);
        }
        tag.putInt(TAG_ACTIVATED_DAY, activatedDay);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_OWNER_UUID)) {
            ownerUUID = tag.getUUID(TAG_OWNER_UUID);
            ownerName = tag.getString(TAG_OWNER_NAME);
        } else {
            ownerUUID = null;
            ownerName = "";
        }
        activatedDay = tag.contains(TAG_ACTIVATED_DAY) ? tag.getInt(TAG_ACTIVATED_DAY) : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
