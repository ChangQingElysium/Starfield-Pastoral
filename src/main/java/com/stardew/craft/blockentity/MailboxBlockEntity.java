package com.stardew.craft.blockentity;

import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
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

/**
 * 信箱方块实体 — 存储主人信息和邮件状态。
 */
@SuppressWarnings("null")
public class MailboxBlockEntity extends BlockEntity {

    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_SYSTEM = "SystemBlock";
    private static final String TAG_HAS_MAIL = "HasMail";

    @Nullable
    private UUID ownerUUID;
    private String ownerName = "";
    private boolean systemBlock;
    private boolean hasMail;

    private int tickCounter;

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAILBOX.get(), pos, state);
    }

    // ── Owner ──

    public void setOwner(ServerPlayer player) {
        this.ownerUUID = player.getUUID();
        this.ownerName = com.stardew.craft.player.PlayerDisplayName.get(player);
        setChanged();
        syncToClient();
    }

    public void setOwnerRaw(UUID uuid, String name) {
        this.ownerUUID = uuid;
        this.ownerName = name;
        setChanged();
    }

    public boolean hasOwner() {
        return ownerUUID != null;
    }

    public boolean isOwner(ServerPlayer player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    // ── System block ──

    public void setSystemBlock(boolean system) {
        this.systemBlock = system;
        setChanged();
    }

    public boolean isSystemBlock() {
        return systemBlock;
    }

    // ── Mail state ──

    public boolean hasMail() {
        return hasMail;
    }

    // ── Tick ──

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        tickCounter++;
        if (tickCounter < 40) return; // check every 2 seconds
        tickCounter = 0;

        boolean newHasMail = false;
        if (ownerUUID != null) {
            PlayerStardewData data = PlayerDataManager.get().getOrCreateData(ownerUUID);
            newHasMail = data.hasMailInMailbox();
        }

        if (newHasMail != hasMail) {
            hasMail = newHasMail;
            setChanged();
            syncToClient();
        }
    }

    // ── NBT ──

    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUUID != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUUID);
            tag.putString(TAG_OWNER_NAME, ownerName);
        }
        tag.putBoolean(TAG_SYSTEM, systemBlock);
        tag.putBoolean(TAG_HAS_MAIL, hasMail);
    }

    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(TAG_OWNER_UUID)) {
            ownerUUID = tag.getUUID(TAG_OWNER_UUID);
            ownerName = tag.getString(TAG_OWNER_NAME);
        } else {
            ownerUUID = null;
            ownerName = "";
        }
        systemBlock = tag.getBoolean(TAG_SYSTEM);
        hasMail = tag.getBoolean(TAG_HAS_MAIL);
    }

    // ── Client sync ──

    @Override
    public CompoundTag getUpdateTag(@SuppressWarnings("null") HolderLookup.Provider registries) {
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
