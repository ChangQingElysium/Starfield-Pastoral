package com.stardew.craft.entity.monster;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.UUID;

@SuppressWarnings("null")
public class LuckyPurpleShortsMonsterEntity extends Monster {
    public static final String TAG_MARKER = "sd_lucky_purple_shorts_monster";

    private static final String TAG_TARGET = "TargetPlayer";
    private static final int MAX_TARGET_MISSING_TICKS = 200;

    private UUID targetPlayer;
    private int targetMissingTicks;

    public LuckyPurpleShortsMonsterEntity(EntityType<? extends LuckyPurpleShortsMonsterEntity> type, Level level) {
        super(type, level);
        this.addTag(TAG_MARKER);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ATTACK_DAMAGE, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    public static LuckyPurpleShortsMonsterEntity create(Level level, ServerPlayer target) {
        LuckyPurpleShortsMonsterEntity entity = new LuckyPurpleShortsMonsterEntity(ModEntities.LUCKY_PURPLE_SHORTS_MONSTER.get(), level);
        entity.setTargetPlayer(target);
        entity.setHealth(entity.getMaxHealth());
        entity.setPersistenceRequired();
        return entity;
    }

    public void setTargetPlayer(ServerPlayer target) {
        this.targetPlayer = target.getUUID();
        this.setTarget(target);
        this.targetMissingTicks = 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 1.0D, 64.0F));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            this.discard();
            return;
        }
        refreshTarget();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetPlayer != null) {
            tag.putUUID(TAG_TARGET, targetPlayer);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_TARGET)) {
            targetPlayer = tag.getUUID(TAG_TARGET);
        }
        this.addTag(TAG_MARKER);
    }

    private void refreshTarget() {
        if (targetPlayer == null || this.level().getServer() == null) {
            discardIfTargetMissing();
            return;
        }
        ServerPlayer player = this.level().getServer().getPlayerList().getPlayer(targetPlayer);
        if (player == null
                || player.level() != this.level()
                || !player.isAlive()
                || player.isSpectator()
                || player.isCreative()) {
            discardIfTargetMissing();
            return;
        }
        this.setTarget(player);
        this.targetMissingTicks = 0;
    }

    private void discardIfTargetMissing() {
        this.targetMissingTicks++;
        if (this.targetMissingTicks >= MAX_TARGET_MISSING_TICKS) {
            this.discard();
        }
    }
}
