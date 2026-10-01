package com.stardew.craft.blockentity;

import com.stardew.craft.block.decor.ScarecrowBlock;
import com.stardew.craft.manager.ScarecrowManager;
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
 * 稻草人 BlockEntity：仅记录已驱赶乌鸦数。
 * 注册时把自身位置注入 ScarecrowManager，便于乌鸦袭击系统快速空间查询。
 */
@SuppressWarnings("null")
public class ScarecrowBlockEntity extends BlockEntity {
    private int crowsScared = 0;

    public ScarecrowBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCARECROW.get(), pos, state);
    }

    public int getCrowsScared() { return crowsScared; }

    public void incrementCrowsScared() {
        this.crowsScared++;
        setChanged();
        if (level instanceof ServerLevel sl) {
            // 触发客户端同步
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl
                && getBlockState().getBlock() instanceof ScarecrowBlock scare) {
            ScarecrowManager.get(sl).register(sl, worldPosition, scare.getRadius());
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            ScarecrowManager.get(sl).unregister(sl, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        this.crowsScared = tag.getInt("CrowsScared");
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putInt("CrowsScared", crowsScared);
    }

    // ── 多人同步 ──
    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("CrowsScared", crowsScared);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
