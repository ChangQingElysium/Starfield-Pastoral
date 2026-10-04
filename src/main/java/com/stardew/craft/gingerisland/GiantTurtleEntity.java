package com.stardew.craft.gingerisland;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Persistent scene actor, separate from pet turtles, NPC schedules and monster combat. */
public final class GiantTurtleEntity extends Entity implements AnimatedModel {
    public static final float WIDTH = GiantTurtleBounds.WIDTH;
    public static final float HEIGHT = GiantTurtleBounds.HEIGHT;
    private static final EntityDataAccessor<Boolean> WALKING =
            SynchedEntityData.defineId(GiantTurtleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> ANIMATION_EPOCH =
            SynchedEntityData.defineId(GiantTurtleEntity.class, EntityDataSerializers.LONG);
    private String gateKey = "";

    public GiantTurtleEntity(EntityType<? extends GiantTurtleEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WALKING, false);
        builder.define(ANIMATION_EPOCH, 0L);
    }

    public boolean isWalking() { return entityData.get(WALKING); }

    /** Future island events own travel; this only changes the continuous authored pose. */
    public void setWalking(boolean walking) {
        if (isWalking() == walking) return;
        entityData.set(WALKING, walking);
        entityData.set(ANIMATION_EPOCH, level().getGameTime());
    }

    public String gateKey() { return gateKey; }
    public void setGateKey(String key) { gateKey = key == null ? "" : key; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(WALKING, tag.getBoolean("Walking"));
        entityData.set(ANIMATION_EPOCH, tag.getLong("AnimationEpoch"));
        setGateKey(tag.getString("GateKey"));
        setNoGravity(true);
        setBoundingBox(makeBoundingBox());
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Walking", isWalking());
        tag.putLong("AnimationEpoch", entityData.get(ANIMATION_EPOCH));
        if (!gateKey.isEmpty()) tag.putString("GateKey", gateKey);
    }

    @Override public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        double age = Math.max(0, (level().getGameTime() - entityData.get(ANIMATION_EPOCH)
                + (double) partialTick) / 20);
        return ModelAnimation.loop(isWalking() ? "walk" : "idle").withTime(age);
    }

    @Override public int modelTransitionTicks() { return 6; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void push(double x, double y, double z) { }
    @Override public void push(Entity other) { }
    @Override public void move(MoverType type, Vec3 movement) { }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        setBoundingBox(makeBoundingBox());
    }

    @Override public void setYRot(float yaw) {
        super.setYRot(yaw);
        setBoundingBox(makeBoundingBox());
    }

    @Override protected AABB makeBoundingBox() {
        return GiantTurtleBounds.at(getX(), getY(), getZ(), getYRot());
    }
}
