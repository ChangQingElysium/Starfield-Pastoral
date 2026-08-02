package com.stardew.craft.entity.projectile;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class TemperedBilletProjectileEntity extends ThrowableProjectile {

    private static final double HOMING_RANGE = 14.0;
    private static final double SPEED = 1.1;
    private static final double TURN_RATE = 0.18;
    private static final int MAX_LIFE_TICKS = 60;

    private float damage = 10.0f;
    private String skillId = "tempered_billet";
    private UUID targetId = null;
    private WeaponDamageSnapshot releaseWeaponSnapshot;

    public TemperedBilletProjectileEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public TemperedBilletProjectileEntity(Level level, LivingEntity owner, float damage, String skillId, LivingEntity target) {
        this(level, owner, damage, skillId, target, null);
    }

    public TemperedBilletProjectileEntity(
            Level level,
            LivingEntity owner,
            float damage,
            String skillId,
            LivingEntity target,
            WeaponDamageSnapshot releaseWeaponSnapshot
    ) {
        super(ModEntities.TEMPERED_BILLET_PROJECTILE.get(), owner, level);
        this.damage = damage;
        if (skillId != null) {
            this.skillId = skillId;
        }
        this.releaseWeaponSnapshot = releaseWeaponSnapshot;
        this.setNoGravity(true);
        if (target != null) {
            this.targetId = target.getUUID();
        }
    }

    @Override
    protected void defineSynchedData(@SuppressWarnings("null") net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @SuppressWarnings("null")
    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.tickCount > MAX_LIFE_TICKS) {
                this.discard();
                return;
            }

            if (targetId == null) {
                findTarget();
            }

            LivingEntity target = getTarget();
            if (target == null) {
                findTarget();
                target = getTarget();
            }
            if (target != null) {
                Vec3 desired = target.getEyePosition().subtract(this.position()).normalize().scale(SPEED);
                Vec3 current = this.getDeltaMovement();
                Vec3 newVel = current.add(desired.subtract(current).scale(TURN_RATE));
                double max = SPEED * SPEED;
                if (newVel.lengthSqr() > max) {
                    newVel = newVel.normalize().scale(SPEED);
                }
                this.setDeltaMovement(newVel);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.96));
            }

            spawnTrailParticles();
        }
    }

    @SuppressWarnings("null")
    private void findTarget() {
        if (!(this.getOwner() instanceof Player owner)) {
            return;
        }
        Vec3 pos = this.position();
        double best = Double.MAX_VALUE;
        LivingEntity bestTarget = null;

        for (LivingEntity target : owner.level().getEntitiesOfClass(LivingEntity.class,
            owner.getBoundingBox().inflate(HOMING_RANGE, HOMING_RANGE * 0.6, HOMING_RANGE),
            entity -> entity.isPickable() && entity.isAlive() && entity != owner)) {
            double dist = target.distanceToSqr(pos.x, pos.y, pos.z);
            if (dist < best) {
                best = dist;
                bestTarget = target;
            }
        }

        if (bestTarget != null) {
            this.targetId = bestTarget.getUUID();
        }
    }

    @SuppressWarnings("null")
    private LivingEntity getTarget() {
        if (targetId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(targetId);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    @SuppressWarnings("null")
    private void spawnTrailParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 pos = this.position();
        serverLevel.sendParticles(ParticleTypes.FLAME,
            pos.x, pos.y + 0.05, pos.z,
            2, 0.12, 0.06, 0.12, 0.01);
        serverLevel.sendParticles(ParticleTypes.SMOKE,
            pos.x, pos.y + 0.05, pos.z,
            1, 0.08, 0.04, 0.08, 0.01);
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitEntity(@SuppressWarnings("null") EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();

        if (!(owner instanceof Player player)) {
            this.discard();
            return;
        }
        if (target == owner) {
            return;
        }

        if (target instanceof LivingEntity livingTarget) {
            SkillContext context = SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(1.0f)
                .build();
            long nowTick = this.level().getGameTime();
            if (this.releaseWeaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        livingTarget,
                        context,
                        nowTick + 5,
                        WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        livingTarget,
                        context,
                        this.releaseWeaponSnapshot,
                        nowTick + 5,
                        WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            }

        }

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.9f);
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 1.1f);
            serverLevel.sendParticles(ParticleTypes.LAVA,
                this.getX(), this.getY() + 0.2, this.getZ(),
                8, 0.3, 0.1, 0.3, 0.02);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.2, this.getZ(),
                12, 0.35, 0.15, 0.35, 0.02);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitBlock(@SuppressWarnings("null") BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = result.getLocation();
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                pos.x, pos.y + 0.1, pos.z,
                6, 0.2, 0.05, 0.2, 0.01);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    public void addAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", this.skillId == null ? "" : this.skillId);
        if (this.targetId != null) {
            tag.putUUID("Target", this.targetId);
        }
        tag.putFloat("Damage", this.damage);
        writeReleaseWeaponSnapshot(
                tag,
                this.releaseWeaponSnapshot,
                this.level().registryAccess()
        );
    }

    @SuppressWarnings("null")
    @Override
    public void readAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkillId");
        this.skillId = id == null || id.isEmpty() ? "tempered_billet" : id;
        if (tag.hasUUID("Target")) {
            this.targetId = tag.getUUID("Target");
        }
        this.damage = tag.getFloat("Damage");
        this.releaseWeaponSnapshot = readReleaseWeaponSnapshot(
                tag,
                this.level().registryAccess()
        );
    }

    static void writeReleaseWeaponSnapshot(
            CompoundTag tag,
            WeaponDamageSnapshot snapshot,
            HolderLookup.Provider registries
    ) {
        if (snapshot == null) {
            return;
        }
        ItemStack weapon = snapshot.weapon();
        if (weapon.isEmpty()) {
            return;
        }
        tag.putString("ReleaseWeaponId", snapshot.weaponId().toString());
        tag.put("ReleaseWeapon", weapon.saveOptional(registries));
    }

    static WeaponDamageSnapshot readReleaseWeaponSnapshot(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        if (!tag.contains("ReleaseWeapon", Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceLocation weaponId =
                ResourceLocation.tryParse(tag.getString("ReleaseWeaponId"));
        if (weaponId == null) {
            return null;
        }
        ItemStack weapon = ItemStack.parseOptional(
                registries,
                tag.getCompound("ReleaseWeapon")
        );
        return weapon.isEmpty()
                ? null
                : WeaponDamageSnapshot.capture(weaponId, weapon);
    }

}
