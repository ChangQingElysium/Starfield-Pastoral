package com.stardew.craft.pet;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Invulnerable farm companion; has no taming, breeding, loot, leash or livestock sale path. */
public final class PetEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(PetEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CLIP = SynchedEntityData.defineId(PetEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(PetEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<ItemStack> HAT = SynchedEntityData.defineId(PetEntity.class, EntityDataSerializers.ITEM_STACK);
    final PetFeedback feedback = new PetFeedback(this);
    private final PetBrain brain = new PetBrain(this);
    private Vec3 desired = Vec3.ZERO;

    public PetEntity(EntityType<? extends PetEntity> type, Level level) {
        super(type, level); setPersistenceRequired(); setInvulnerable(true);
        moveControl = new MoveControl(this) {
            void napNow() { brain.napNow(); }
    @Override public void tick() {
                desired = Vec3.ZERO;
                if (operation != Operation.MOVE_TO || !movingClip()) return;
                operation = Operation.WAIT;
                Vec3 difference = new Vec3(wantedX - getX(), 0, wantedZ - getZ());
                double distance = difference.length();
                if (distance < .015) return;
                double step = variant().stride(clip().equals("sprint")) / PetBehaviors.get(variant()).clips().get(clip()) / 16 / 20;
                desired = difference.scale(Math.min(step, distance) / distance);
                float yaw = (float) (Math.atan2(-desired.x, desired.z) * 180 / Math.PI);
                setYRot(rotlerp(getYRot(), yaw, 15)); setYBodyRot(getYRot()); setYHeadRot(getYRot());
            }
        };
    }
    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, .2).add(Attributes.STEP_HEIGHT, 1).add(Attributes.FOLLOW_RANGE, 16); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(VARIANT, "stardewcraft:cat0"); builder.define(CLIP, "idle"); builder.define(START, 0L); builder.define(HAT, ItemStack.EMPTY);
    }
    public PetVariant variant() { return PetVariant.fromSaved(entityData.get(VARIANT)); }
    public String clip() { return entityData.get(CLIP); }
    public long clipStart() { return entityData.get(START); }
    public ItemStack hat() { return entityData.get(HAT); }
    public boolean movingClip() { return clip().equals("walk") || clip().equals("sprint"); }
    public void play(String clip) { entityData.set(CLIP, clip); entityData.set(START, level().getGameTime()); if (clip.equals("walk")) feedback.restartWalk(); }
    public void refresh(PetRecord record) {
        entityData.set(VARIANT, record.variant.id()); setCustomName(Component.literal(record.name));
        entityData.set(HAT, ItemStack.parseOptional(level().registryAccess(), record.hat));
    }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        var variant = variant();
        return variant.available() ? EntityDimensions.scalable(variant.species().width(), variant.species().height()) : EntityDimensions.scalable(.6f, .9f);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) { super.onSyncedDataUpdated(accessor); if (accessor.equals(VARIANT)) refreshDimensions(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void kill() { /* Pets are removed only by the confirmed Butterfly Powder action. */ }
    @Override public void die(DamageSource source) { }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override protected boolean canRide(Entity entity) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity other) { }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) PetService.interact(serverPlayer, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { /* Authored walk/impact frames own pet footsteps. */ }
    @Override public void travel(Vec3 input) {
        if (!(level() instanceof ServerLevel)) return;
        if (clip().equals("pounce")) {
            // The authored clip supplies the vertical arc; advance in the existing facing without a new path or turn.
            double elapsed = (level().getGameTime() - clipStart()) / 20.;
            double radians = Math.toRadians(getYRot());
            double speed = elapsed >= .1 && elapsed < .65 ? .055 : 0;
            Vec3 step = new Vec3(-Math.sin(radians) * speed, 0, Math.cos(radians) * speed);
            var ahead = net.minecraft.core.BlockPos.containing(position().add(step.scale(10)));
            desired = level().hasChunkAt(ahead) && level().getBlockState(ahead.below()).isFaceSturdy(level(), ahead.below(), net.minecraft.core.Direction.UP) ? step : Vec3.ZERO;
        }
        // Fixed authored stride, independent of vanilla ground friction. Entity.move still handles steps/collisions.
        double y = onGround() ? -.08 : Math.max(-.7, getDeltaMovement().y - .08);
        if (isInWater()) y = .04;
        Vec3 velocity = new Vec3(desired.x, y, desired.z);
        setDeltaMovement(velocity); move(MoverType.SELF, velocity);
        if (verticalCollision) setDeltaMovement(desired.x, 0, desired.z);
        calculateEntityAnimation(false);
    }
    void napNow() { brain.napNow(); }
    @Override public void tick() { super.tick(); if (level() instanceof ServerLevel) { brain.tick(); if (!isRemoved()) feedback.tick(); } }
    @Override public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) PetService.remember(server, this);
        super.remove(reason);
    }
}
