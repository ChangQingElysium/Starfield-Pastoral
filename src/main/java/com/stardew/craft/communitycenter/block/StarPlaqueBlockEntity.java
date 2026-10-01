package com.stardew.craft.communitycenter.block;

import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 星盘 BlockEntity — 追踪并同步星星数量 (0-6)。
 * <p>
 * 每 100 tick 从 {@link CommunityCenterSavedData} 刷新已完成区域数,
 * 变化时自动通知客户端重新渲染。
 */
@SuppressWarnings("null")
public class StarPlaqueBlockEntity extends BlockEntity {

    private static final String TAG_STARS = "Stars";
    private static final int SYNC_INTERVAL = 100; // ticks

    private int numberOfStars = 0;
    private int syncTimer = 0;

    /**
     * Junimo 正在搬运星星时, serverTick 不自动同步, 等 Junimo 放完再更新。
     * 值为游戏 tick 时间戳, 在此之前不做自动同步。
     */
    private static long placementSuppressUntil = Long.MIN_VALUE;

    /** 标记: Junimo 已出发, 暂停 serverTick 自动同步 */
    public static void suppressSyncUntil(long gameTimeTick) {
        placementSuppressUntil = gameTimeTick;
    }

    /** 清除抑制, 恢复 serverTick 自动同步 */
    public static void clearSyncSuppression() {
        placementSuppressUntil = Long.MIN_VALUE;
    }

    public StarPlaqueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_PLAQUE.get(), pos, state);
    }

    public int getNumberOfStars() {
        return numberOfStars;
    }

    /** 由服务端调用: 设置星星数并同步到客户端 */
    public void setNumberOfStars(int stars) {
        if (this.numberOfStars != stars) {
            this.numberOfStars = Math.max(0, Math.min(6, stars));
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StarPlaqueBlockEntity be) {
        be.syncTimer++;
        if (be.syncTimer >= SYNC_INTERVAL) {
            be.syncTimer = 0;
            // Junimo 搬运中不自动同步, 等放完星再更新
            if (level.getGameTime() < placementSuppressUntil) return;
            CommunityCenterSavedData data = CommunityCenterSavedData.get();
            // 找到这个 StarPlaque 所在的 CC 属于哪个玩家
            java.util.UUID owner = null;
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                owner = com.stardew.craft.interior.PlayerInteriorAllocator.get(sl).findCCOwner(pos);
            }
            if (owner == null) owner = new java.util.UUID(0L, 0L);
            int completed = 0;
            for (int i = 0; i <= 5; i++) {
                if (data.isAreaComplete(owner, i)) completed++;
            }
            be.setNumberOfStars(completed);
        }
    }

    // ── NBT ──

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putInt(TAG_STARS, numberOfStars);
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        numberOfStars = tag.getInt(TAG_STARS);
    }

    // ── Client sync ──

    @Override
    public @Nonnull CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt(TAG_STARS, numberOfStars);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
