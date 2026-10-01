package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 传送触发方块的 BlockEntity — 存储传送目标 ID 和标记标签。
 * <p>
 * 数据通过 NBT 持久化，并同步到客户端（用于 PortalHintRenderer 读取）。
 */
@SuppressWarnings("null")
public class PortalTriggerBlockEntity extends BlockEntity {

    private static final String TAG_TARGET_ID = "TargetId";
    private static final String TAG_MARKER = "MarkerTag";

    private String targetId = "";
    private String markerTag = "";

    public PortalTriggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL_TRIGGER.get(), pos, state);
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId != null ? targetId : "";
        setChanged();
    }

    public String getMarkerTag() {
        return markerTag;
    }

    public void setMarkerTag(String markerTag) {
        this.markerTag = markerTag != null ? markerTag : "";
        setChanged();
    }

    /**
     * 同时设置 targetId 和 markerTag，然后同步到客户端。
     */
    public void configure(String targetId, String markerTag) {
        this.targetId = targetId != null ? targetId : "";
        this.markerTag = markerTag != null ? markerTag : "";
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && com.stardew.craft.core.ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                && com.stardew.craft.desert.DesertConstants.isLegacyBusTarget(targetId)) {
            // Defer writes until chunk loading has finished; never force-load the old sites.
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    // ── NBT 持久化 ──

    @Override
    protected void saveAdditional(CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_TARGET_ID, targetId);
        tag.putString(TAG_MARKER, markerTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        targetId = tag.getString(TAG_TARGET_ID);
        markerTag = tag.getString(TAG_MARKER);
    }

    // ── 客户端同步 ──

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
