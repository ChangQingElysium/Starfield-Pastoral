package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.totem.TotemPoleTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 图腾柱方块实体 — 存储名称、全局ID、类型、激活状态。
 */
@SuppressWarnings("null")
public class TotemPoleBlockEntity extends BlockEntity {

    private static final String TAG_NAME = "poleName";
    private static final String TAG_ID = "poleId";
    private static final String TAG_TYPE = "totemType";
    private static final String TAG_ACTIVATED = "activated";
    private static final String TAG_SYSTEM = "systemPole";

    private String poleName = "";
    private int poleId = -1; // -1 表示未分配
    private TotemType totemType = TotemType.FARM;
    private boolean activated = false;
    private boolean systemPole = false;

    public TotemPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOTEM_POLE.get(), pos, state);
    }

    public TotemPoleBlockEntity(BlockPos pos, BlockState state, TotemType type) {
        super(ModBlockEntities.TOTEM_POLE.get(), pos, state);
        this.totemType = type;
    }

    /* ---------- Getter / Setter ---------- */

    public String getPoleName() {
        return poleName;
    }

    public int getPoleId() {
        return poleId;
    }

    public TotemType getTotemType() {
        return totemType;
    }

    public boolean isActivated() {
        return activated;
    }

    public boolean isSystemPole() {
        return systemPole;
    }

    /** 设置为系统柱（不可破坏，始终激活） */
    public void setSystemPole(boolean system) {
        this.systemPole = system;
        if (system) {
            this.activated = true;
        }
        setChanged();
    }

    /** 在服务端首次放置时调用，分配全局ID并注册到 Tracker */
    public void initOnPlace(ServerLevel serverLevel) {
        if (poleId >= 0) return; // 已分配
        TotemPoleTracker tracker = TotemPoleTracker.get(serverLevel);
        poleId = tracker.allocateId();
        tracker.register(poleId, new TotemPoleTracker.PoleEntry(
                getBlockPos(), poleName, totemType, systemPole
        ));
        setChanged();
        syncToClient();
    }

    /** 注册系统柱（使用固定ID） */
    public void initSystemPole(ServerLevel serverLevel, int id, String name) {
        // 如果 initOnPlace 已经分配了临时ID，先注销它
        if (this.poleId >= 0 && this.poleId != id) {
            TotemPoleTracker.get(serverLevel).unregister(this.poleId);
        }
        this.poleId = id;
        this.poleName = name;
        this.systemPole = true;
        this.activated = true;
        TotemPoleTracker tracker = TotemPoleTracker.get(serverLevel);
        tracker.register(id, new TotemPoleTracker.PoleEntry(
                getBlockPos(), name, totemType, true
        ));
        setChanged();
        syncToClient();
    }

    public void setPoleName(String name) {
        this.poleName = name;
        setChanged();
        if (level instanceof ServerLevel sl) {
            TotemPoleTracker.get(sl).updateName(poleId, name);
        }
        syncToClient();
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(com.stardew.craft.block.utility.totem.TotemPoleBlock.ACTIVATED)) {
                level.setBlock(getBlockPos(), state.setValue(
                        com.stardew.craft.block.utility.totem.TotemPoleBlock.ACTIVATED, activated), 3);
            }
        }
        syncToClient();
    }

    /** 方块被破坏时从 Tracker 中移除 */
    public void unregisterFromTracker() {
        if (poleId >= 0 && level instanceof ServerLevel sl) {
            TotemPoleTracker.get(sl).unregister(poleId);
        }
    }

    /* ---------- NBT ---------- */

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putString(TAG_NAME, poleName);
        tag.putInt(TAG_ID, poleId);
        tag.putString(TAG_TYPE, totemType.getId());
        tag.putBoolean(TAG_ACTIVATED, activated);
        tag.putBoolean(TAG_SYSTEM, systemPole);
    }

    @SuppressWarnings("null")
    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        poleName = tag.getString(TAG_NAME);
        poleId = tag.getInt(TAG_ID);
        totemType = TotemType.fromId(tag.getString(TAG_TYPE));
        activated = tag.getBoolean(TAG_ACTIVATED);
        systemPole = tag.getBoolean(TAG_SYSTEM);
    }

    /* ---------- 同步到客户端 ---------- */

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    @Nonnull
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
}
