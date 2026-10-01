package com.stardew.craft.cutscene.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Client-only actor entity that renders as the local player (vanilla PlayerModel).
 * Used when a cutscene event spawns an actor with npc_id "player".
 */
public class EventPlayerActorEntity extends Mob {

    public static final EntityDataAccessor<Boolean> DATA_IS_WALKING =
            SynchedEntityData.defineId(EventPlayerActorEntity.class, EntityDataSerializers.BOOLEAN);

    /** When true, arms are raised straight above the head (item-above-head pose). */
    private boolean holdingItemAboveHead = false;
    /** Client-authored unconscious pose used by combat rescue scenes. */
    private boolean collapsed = false;
    private int recoveryStartTick = -1;
    private boolean hospitalBed;
    private int hospitalRecoveryStartTick = -1;
    /** Client-authored vanilla-style item-use pose used by farmerEat scenes. */
    private boolean eatingItem = false;
    private UUID skinSourcePlayerId;
    private boolean slimSkinModel;

    private double walkTrackX, walkTrackZ;
    private boolean walkTrackInit = false;

    public EventPlayerActorEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setNoGravity(true);
    }

    /**
     * Override vanilla walk animation calculation.
     * Because noPhysics=true and positions are set externally via setPos(),
     * the vanilla xo/zo tracking sees 0 movement. We track deltas ourselves
     * so the vanilla PlayerModel animates limbs (including outer skin layer) correctly.
     */
    @Override
    public void calculateEntityAnimation(boolean isFlying) {
        if (!walkTrackInit) {
            walkTrackX = this.getX();
            walkTrackZ = this.getZ();
            walkTrackInit = true;
        }
        double dx = this.getX() - walkTrackX;
        double dz = this.getZ() - walkTrackZ;
        walkTrackX = this.getX();
        walkTrackZ = this.getZ();
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        this.updateWalkAnimation(dist);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_IS_WALKING, false);
    }

    public boolean isWalking() {
        return this.entityData.get(DATA_IS_WALKING);
    }

    public void setWalking(boolean walking) {
        this.entityData.set(DATA_IS_WALKING, walking);
    }

    public boolean isHoldingItemAboveHead() {
        return holdingItemAboveHead;
    }

    public void setHoldingItemAboveHead(boolean holding) {
        this.holdingItemAboveHead = holding;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public boolean isInHospitalBedScene() { return hospitalBed; }

    public void lieInHospitalBed() {
        hospitalBed=true;hospitalRecoveryStartTick=-1;
        collapsed=false;recoveryStartTick=-1;setWalking(false);
    }

    public void getUpFromHospitalBed() {
        if(hospitalBed && hospitalRecoveryStartTick<0) hospitalRecoveryStartTick=tickCount;
    }

    public float hospitalBedTime(float partialTick) {
        return hospitalRecoveryStartTick<0 ? -1 : tickCount-hospitalRecoveryStartTick+partialTick;
    }

    public void setCollapsed(boolean collapsed) {
        hospitalBed=false;hospitalRecoveryStartTick=-1;
        if (this.collapsed && !collapsed) recoveryStartTick = tickCount;
        if (collapsed) recoveryStartTick = -1;
        this.collapsed = collapsed;
        if (collapsed) {
            setWalking(false);
        }
    }

    /** -1 means no recovery. Uses the actor tick clock, never render-call count. */
    public float recoveryTime(float partialTick) {
        return recoveryStartTick < 0 ? -1 : tickCount - recoveryStartTick + partialTick;
    }

    public boolean isEatingItem() {
        return eatingItem;
    }

    public void setEatingItem(boolean eatingItem) {
        this.eatingItem = eatingItem;
    }

    public UUID getSkinSourcePlayerId() {
        return skinSourcePlayerId;
    }

    public void setSkinSourcePlayerId(UUID playerId) {
        this.skinSourcePlayerId = playerId;
    }

    public boolean isSlimSkinModel() {
        return slimSkinModel;
    }

    public void setSlimSkinModel(boolean slimSkinModel) {
        this.slimSkinModel = slimSkinModel;
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isInvulnerableTo(@javax.annotation.Nonnull DamageSource source) { return true; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }

    @Override
    public Component getName() {
        return Component.translatable("entity.stardewcraft.player");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get(), 1.0);
    }
}
