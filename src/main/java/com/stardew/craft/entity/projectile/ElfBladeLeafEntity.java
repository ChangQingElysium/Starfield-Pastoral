package com.stardew.craft.entity.projectile;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ElfBladeLeafEntity extends ThrowableProjectile {

    @SuppressWarnings("null")
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ElfBladeLeafEntity.class, EntityDataSerializers.INT);
    @SuppressWarnings("null")
    private static final EntityDataAccessor<Integer> ORBIT_INDEX = SynchedEntityData.defineId(ElfBladeLeafEntity.class, EntityDataSerializers.INT);
    private static final int STATE_ORBIT = 0;
    private static final int STATE_HOMING = 1;

    private static final double ORBIT_RADIUS = 0.85;
    private static final double ORBIT_SPEED = 0.22;
    private static final double ORBIT_BOB = 0.12;
    private static final double HOMING_SPEED = 1.15;
    private static final double TURN_RATE = 0.22;
    private static final int MAX_HOMING_TICKS = 60;

    private static final int TRAIL_MAX_AGE_ORBIT = 6;
    private static final int TRAIL_MAX_AGE_FIRED = 10;
    private static final int TRAIL_MAX_POINTS_ORBIT = 24;
    private static final int TRAIL_MAX_POINTS_FIRED = 32;

    private float damageMultiplier = 0.50f;
    private String skillId = "elf_blade_leaf";
    private UUID targetId = null;
    private long expireTick = 0L;
    private int homingTicks = 0;
    private WeaponDamageSnapshot releaseWeaponSnapshot;

    private final Deque<TrailPoint> trailPoints = new ArrayDeque<>();

    public ElfBladeLeafEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @SuppressWarnings("null")
    public ElfBladeLeafEntity(Level level, LivingEntity owner, float damageMultiplier, String skillId, int orbitIndex, long expireTick) {
        this(
            level,
            owner,
            damageMultiplier,
            skillId,
            orbitIndex,
            expireTick,
            null
        );
    }

    @SuppressWarnings("null")
    public ElfBladeLeafEntity(
        Level level,
        LivingEntity owner,
        float damageMultiplier,
        String skillId,
        int orbitIndex,
        long expireTick,
        WeaponDamageSnapshot releaseWeaponSnapshot
    ) {
        super(ModEntities.ELF_BLADE_LEAF.get(), owner, level);
        this.damageMultiplier = damageMultiplier;
        if (skillId != null) {
            this.skillId = skillId;
        }
        this.expireTick = expireTick;
        this.releaseWeaponSnapshot = releaseWeaponSnapshot;
        this.setNoGravity(true);
        this.entityData.set(ORBIT_INDEX, orbitIndex);
        this.entityData.set(STATE, STATE_ORBIT);
    }

    @Override
    @SuppressWarnings("null")
    protected void defineSynchedData() {
        this.entityData.define(STATE, STATE_ORBIT);
        this.entityData.define(ORBIT_INDEX, 0);
    }

    @SuppressWarnings("null")
    public int getOrbitIndex() {
        return this.entityData.get(ORBIT_INDEX);
    }

    @SuppressWarnings("null")
    public boolean isOrbiting() {
        return this.entityData.get(STATE) == STATE_ORBIT;
    }

    @SuppressWarnings("null")
    public void launchToTarget(LivingEntity target) {
        if (target == null) {
            return;
        }
        this.targetId = target.getUUID();
        this.entityData.set(STATE, STATE_HOMING);
        this.homingTicks = 0;
        if (this.level() instanceof ServerLevel level) level.playSound(null, getX(), getY(), getZ(),
                SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.28f, 1.75f);
    }

    @SuppressWarnings("null")
    @Override
    public void tick() {
        super.tick();

        LivingEntity owner = (LivingEntity) this.getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        if (this.entityData.get(STATE) == STATE_ORBIT) {
            if (!this.level().isClientSide && this.level().getGameTime() >= expireTick) {
                this.discard();
                return;
            }

            Vec3 center = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
            Vec3 orbit = center.add(orbitOffset(this.level().getGameTime(), getOrbitIndex()));
            this.setPos(orbit.x, orbit.y, orbit.z);
            this.setDeltaMovement(Vec3.ZERO);
            if (this.level().isClientSide) {
                ageTrailPoints();
                trailPoints.clear();
            }
            return;
        }

        if (!this.level().isClientSide) {
            homingTicks++;
            if (homingTicks > MAX_HOMING_TICKS) {
                this.discard();
                return;
            }

            LivingEntity target = getTarget();
            if (target != null) {
                Vec3 desired = target.getEyePosition().subtract(this.position()).normalize().scale(HOMING_SPEED);
                Vec3 current = this.getDeltaMovement();
                Vec3 newVel = current.add(desired.subtract(current).scale(TURN_RATE));
                double max = HOMING_SPEED * HOMING_SPEED;
                if (newVel.lengthSqr() > max) {
                    newVel = newVel.normalize().scale(HOMING_SPEED);
                }
                this.setDeltaMovement(newVel);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.96));
            }
        }

        if (this.level().isClientSide) {
            ageTrailPoints();
            recordTrailPoint();
        }
    }

    @Override
    @SuppressWarnings("null")
    protected boolean canHitEntity(@SuppressWarnings("null") Entity entity) {
        if (this.entityData.get(STATE) == STATE_ORBIT) {
            return false;
        }
        return super.canHitEntity(entity);
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

    /** Shared analytic orbit for simulation and fractional render-time sampling. */
    public static Vec3 orbitOffset(double tick, int index) {
        double angle = tick * ORBIT_SPEED + index * (Math.PI * 2.0 / 3.0);
        return new Vec3(Math.cos(angle) * ORBIT_RADIUS, Math.sin(angle * 2) * ORBIT_BOB,
                Math.sin(angle) * ORBIT_RADIUS);
    }

    private void recordTrailPoint() {
        Vec3 pos = position();
        TrailPoint last = trailPoints.peekLast();
        if (last != null && last.position.distanceToSqr(pos) > 16) trailPoints.clear();
        if (last != null && last.position.distanceToSqr(pos) < 0.000001) return;
        // One real position per tick: no forward prediction or sub-millimetre samples exhausting the history.
        addTrailPoint(pos);
    }

    @SuppressWarnings("null")
    private void addTrailPoint(Vec3 pos) {
        TrailPoint last = trailPoints.peekLast();
        float tex = last == null ? 0.0f : last.texcoord + (float) last.position.distanceTo(pos);
        trailPoints.addLast(new TrailPoint(pos, 0.0f, tex));
        trimTrail();
    }

    private void ageTrailPoints() {
        for (TrailPoint p : trailPoints) {
            p.age += 1.0f;
        }
        while (!trailPoints.isEmpty() && trailPoints.peekFirst().age > getTrailMaxAgeValue()) {
            trailPoints.removeFirst();
        }
    }

    @SuppressWarnings("null")
    private void trimTrail() {
        while (!trailPoints.isEmpty() && trailPoints.peekFirst().age > getTrailMaxAgeValue()) {
            trailPoints.removeFirst();
        }
        while (trailPoints.size() > getTrailMaxPointsValue()) {
            trailPoints.removeFirst();
        }
    }

    public Deque<TrailPoint> getTrailPoints() {
        return trailPoints;
    }

    @SuppressWarnings("null")
    public int getTrailMaxAgeValue() {
        return this.entityData.get(STATE) == STATE_ORBIT ? TRAIL_MAX_AGE_ORBIT : TRAIL_MAX_AGE_FIRED;
    }

    @SuppressWarnings("null")
    public int getTrailMaxPointsValue() {
        return this.entityData.get(STATE) == STATE_ORBIT ? TRAIL_MAX_POINTS_ORBIT : TRAIL_MAX_POINTS_FIRED;
    }

    public static final class TrailPoint {
        public final Vec3 position;
        public float age;
        public final float texcoord;

        public TrailPoint(Vec3 position, float age, float texcoord) {
            this.position = position;
            this.age = age;
            this.texcoord = texcoord;
        }
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitEntity(@SuppressWarnings("null") EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }

        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (!(owner instanceof Player player) || target == owner) {
            this.discard();
            return;
        }

        if (target instanceof LivingEntity livingTarget) {
            long nowTick = this.level().getGameTime();
            SkillContext context = SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(this.damageMultiplier)
                .build();
            if (releaseWeaponSnapshot == null) {
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
                    releaseWeaponSnapshot,
                    nowTick + 5,
                    WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            }


        }

        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitBlock(@SuppressWarnings("null") BlockHitResult result) {
        if (this.entityData.get(STATE) == STATE_ORBIT) {
            return;
        }
        super.onHitBlock(result);
        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    public void addAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", this.skillId == null ? "" : this.skillId);
        tag.putFloat("DamageMultiplier", this.damageMultiplier);
        tag.putInt("State", this.entityData.get(STATE));
        tag.putInt("OrbitIndex", this.entityData.get(ORBIT_INDEX));
        tag.putLong("ExpireTick", this.expireTick);
        tag.putInt("HomingTicks", this.homingTicks);
        if (this.targetId != null) {
            tag.putUUID("Target", this.targetId);
        }
        writeReleaseWeaponSnapshot(
            tag,
            releaseWeaponSnapshot,
            this.level().registryAccess()
        );
    }

    @SuppressWarnings("null")
    @Override
    public void readAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkillId");
        this.skillId = id == null || id.isEmpty() ? "elf_blade_leaf" : id;
        if (tag.contains("DamageMultiplier")) {
            this.damageMultiplier = tag.getFloat("DamageMultiplier");
        }
        if (tag.contains("State")) {
            this.entityData.set(STATE, tag.getInt("State"));
        }
        if (tag.contains("OrbitIndex")) {
            this.entityData.set(ORBIT_INDEX, tag.getInt("OrbitIndex"));
        }
        if (tag.contains("ExpireTick")) {
            this.expireTick = tag.getLong("ExpireTick");
        }
        if (tag.contains("HomingTicks")) {
            this.homingTicks = tag.getInt("HomingTicks");
        }
        if (tag.hasUUID("Target")) {
            this.targetId = tag.getUUID("Target");
        }
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
        tag.put("ReleaseWeapon", PortItemStacks.saveOptional(weapon, registries));
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
        ItemStack weapon = PortItemStacks.parseOptional(
            registries,
            tag.getCompound("ReleaseWeapon")
        );
        return weapon.isEmpty()
            ? null
            : WeaponDamageSnapshot.capture(weaponId, weapon);
    }
}
