package com.stardew.craft.cutscene.runtime;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import com.stardew.craft.npc.animation.SamActivity;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Lightweight, temporary actor entity for cutscenes and photography commands.
 * Reuses the real NPC's current model based on npcId, including native animation.
 * Has no AI, collision, gameplay interaction, or persistence.
 */
public class EventActorEntity extends Mob implements AnimatedModel {

    private static final String NBT_NPC_ID = "NpcId";

    public static final EntityDataAccessor<String> DATA_NPC_ID =
            SynchedEntityData.defineId(EventActorEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Boolean> DATA_IS_WALKING =
            SynchedEntityData.defineId(EventActorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> DATA_GUITAR =
            SynchedEntityData.defineId(EventActorEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private Vec3 scriptedWalkTarget;
    private double scriptedWalkSpeed;

    public EventActorEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_NPC_ID, "");
        this.entityData.define(DATA_IS_WALKING, false);
        this.entityData.define(DATA_GUITAR, new CompoundTag());
    }

    public String getNpcId() {
        return this.entityData.get(DATA_NPC_ID);
    }

    public void setNpcId(String id) {
        this.entityData.set(DATA_NPC_ID, normalizeNpcId(id));
    }

    public static String normalizeNpcId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        String ownNamespace = StardewCraft.MODID + ":";
        if (normalized.startsWith(ownNamespace)) {
            normalized = normalized.substring(ownNamespace.length());
        }
        return normalized.matches("[a-z0-9_./-]+") ? normalized : "";
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(NBT_NPC_ID, getNpcId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_NPC_ID)) {
            setNpcId(tag.getString(NBT_NPC_ID));
        }
    }

    public boolean isWalking() {
        return this.entityData.get(DATA_IS_WALKING);
    }

    public void setWalking(boolean walking) {
        if (walking && getNativeActivity() != null) clearCustomAnimation();
        this.entityData.set(DATA_IS_WALKING, walking);
    }

    public void walkTo(Vec3 target, double speedBlocksPerTick) {
        if (target == null) {
            stopWalking();
            return;
        }
        this.scriptedWalkTarget = target;
        this.scriptedWalkSpeed = Math.max(0.001D, speedBlocksPerTick);
        this.setWalking(this.position().distanceToSqr(target) > 1.0E-8D);
    }

    public void stopWalking() {
        this.scriptedWalkTarget = null;
        this.scriptedWalkSpeed = 0.0D;
        this.setWalking(false);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.scriptedWalkTarget != null) {
            tickScriptedWalk();
        }
    }

    private void tickScriptedWalk() {
        Vec3 current = this.position();
        Vec3 target = this.scriptedWalkTarget;
        this.setWalking(true);
        Vec3 next = nextWalkPosition(current, target, scriptedWalkSpeed);
        double dx = target.x - current.x;
        double dz = target.z - current.z;
        if (dx != 0.0D || dz != 0.0D) {
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            this.setYRot(yaw);
            this.setYHeadRot(yaw);
            this.setYBodyRot(yaw);
        }
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(Vec3.ZERO);
        if (next.distanceToSqr(target) <= 1.0E-8D) {
            stopWalking();
        }
    }

    static Vec3 nextWalkPosition(Vec3 current, Vec3 target, double speedBlocksPerTick) {
        Vec3 delta = target.subtract(current);
        double distance = delta.length();
        if (distance <= speedBlocksPerTick || distance <= 1.0E-8D) {
            return target;
        }
        return current.add(delta.scale(speedBlocksPerTick / distance));
    }

    public void setCustomAnimation(String animName, boolean loop) {
        String shortName = animName.startsWith("animation." + getNpcId() + ".")
                ? animName.substring(("animation." + getNpcId() + ".").length()) : animName;
        if ("idle".equals(shortName) || "walk".equals(shortName)) {
            clearCustomAnimation();
            setWalking("walk".equals(shortName));
            return;
        }
        var guitar = new CompoundTag();
        guitar.putString("animation", animName);
        guitar.putLong("start", level().getGameTime());
        guitar.putBoolean("loop", loop);
        var action = SamActivity.fromAnimation(animName);
        if ("sam".equals(getNpcId()) && action != null) {
            stopWalking();
            guitar.putString("activity", action.asset());
            guitar.putString("clip", action.holdClip().equals(animName) ? action.holdClip() : action.playClip());
        }
        entityData.set(DATA_GUITAR, guitar);
    }

    public void clearCustomAnimation() {
        entityData.set(DATA_GUITAR, new CompoundTag());
    }

    public boolean hasCustomAnimation() {
        return !getCustomAnimationName().isEmpty();
    }

    public String getCustomAnimationName() { return entityData.get(DATA_GUITAR).getString("animation"); }
    public long getCustomAnimationStartTick() { return entityData.get(DATA_GUITAR).getLong("start"); }
    public boolean isCustomAnimationLooping() { return entityData.get(DATA_GUITAR).getBoolean("loop"); }
    public SamActivity getNativeActivity() { return SamActivity.fromAnimation(entityData.get(DATA_GUITAR).getString("activity")); }
    public String getNativeActivityClip() { return entityData.get(DATA_GUITAR).getString("clip"); }
    public long getNativeActivityStartTick() { return entityData.get(DATA_GUITAR).getLong("start"); }
    public boolean isNativeActivityLooping() { return entityData.get(DATA_GUITAR).getBoolean("loop"); }
    public boolean isPlayingGuitar() { return getNativeActivity() == SamActivity.GUITAR; }
    public long getGuitarStartTick() { return entityData.get(DATA_GUITAR).getLong("start"); }
    public boolean isGuitarLooping() { return entityData.get(DATA_GUITAR).getBoolean("loop"); }

    // ─── Native model animation ───

    @Override
    public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        if (hasCustomAnimation()) {
            ModelAnimation animation = isCustomAnimationLooping() ? ModelAnimation.loop(getCustomAnimationName())
                    : ModelAnimation.play(getCustomAnimationName());
            double age = Math.max(0, ((double) level().getGameTime() - getCustomAnimationStartTick() + partialTick) / 20.0);
            return animation.withTime(age);
        }
        return ModelAnimation.loop(isWalking() ? "walk" : "idle");
    }

    // ─── Override to be inert ───

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
        String npcId = getNpcId();
        if (npcId != null && !npcId.isEmpty()) {
            return Component.translatable("entity.stardewcraft.npc." + npcId);
        }
        return super.getName();
    }

    @Override
    public InteractionResult mobInteract(@javax.annotation.Nonnull net.minecraft.world.entity.player.Player player,
                                         @javax.annotation.Nonnull InteractionHand hand) {
        if (this.level().isClientSide || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }
}
