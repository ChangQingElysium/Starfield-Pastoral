package com.stardew.craft.entity.minecart;

import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 矿车站点实体 — 视觉上固定在地面，玩家无法推动/破坏。
 * 右键交互进入矿车选线界面（若玩家已完成 ccBoilerRoom 献祭）。
 *
 * <p>stationId 通过 SynchedEntityData 同步到客户端，让客户端在交互时
 * 能告诉服务端"这是哪个站点"，从而排除当前站点不出现在目的地列表里。
 */
@SuppressWarnings("null")
public class MinecartStationEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_STATION_ID =
            SynchedEntityData.defineId(MinecartStationEntity.class, EntityDataSerializers.STRING);

    private static final String TAG_STATION_ID = "StationId";

    public MinecartStationEntity(EntityType<? extends MinecartStationEntity> type, Level level) {
        super(type, level);
        // 禁用物理：不受重力 / 不被推动
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public MinecartStationEntity(Level level, BlockPos pos, String stationId) {
        this(ModEntities.MINECART_STATION.get(), level);
        this.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        this.setStationId(stationId);
    }

    public String getStationId() {
        return this.entityData.get(DATA_STATION_ID);
    }

    public void setStationId(String id) {
        this.entityData.set(DATA_STATION_ID, id == null ? "" : id);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATION_ID, "");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setStationId(tag.getString(TAG_STATION_ID));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(TAG_STATION_ID, getStationId());
    }

    // ── 不可破坏 / 不可推动 ──

    @Override
    public boolean isPickable() {
        return true; // 允许被射线拾取（右键能打到）
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 无敌：无视所有伤害
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true; // 阻挡射线/鼠标
    }

    @Override
    public void push(double dx, double dy, double dz) {
        // no-op — 不响应推动
    }

    @Override
    public void push(Entity other) {
        // no-op
    }

    @Override
    public void tick() {
        super.tick();
        // 钉死位置，防止任何潜在的偏移
        this.setDeltaMovement(Vec3.ZERO);
    }

    // ── 右键交互：打开矿车选线菜单 ──

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;

        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        PlayerStardewData data = PlayerDataManager.getPlayerData(sp);
        if (!data.hasMailFlag(CCStoryFlags.CC_BOILER_ROOM)) {
            ObjectDialogueService.show(sp, "stardewcraft.minecart.not_unlocked");
            return InteractionResult.CONSUME;
        }

        com.stardew.craft.minecart.MinecartMenuService.openFor(sp, getStationId());
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean shouldBeSaved() {
        // 保存到世界里，重启之后还在
        return true;
    }
}
